#!/usr/bin/env python3
"""
Sonic 1 Trace Recorder for stable-retro.

Equivalent of tools/bizhawk/s1_trace_recorder.lua (v2.2).
Records frame-by-frame Sonic 1 physics state during gameplay and produces
output files in the same format consumed by the Java trace replay tests:

  - physics.csv     (20 columns, csv_version=4)
  - aux_state.jsonl (object/mode/routine events)
  - metadata.json   (recording session info)

Input sources:
  --movie <path.bk2>        Replay a stable-retro BK2 movie
  --bizhawk-bk2 <path.bk2>  Replay a BizHawk BK2 movie (auto-converted)
  --state <name>            Boot from a named savestate (e.g. GreenHillZone.Act1)

BizHawk BK2 alignment:
  --bk2-offset N            BizHawk's gameplay start frame (from metadata.json
                            bk2_frame_offset). Boot uses BK2 as-is; once
                            gameplay starts, inputs jump to BK2 frame N.
                            GHZ1=840, MZ1=1075.

Usage:
  python s1_trace_recorder.py --movie my_recording.bk2
  python s1_trace_recorder.py --bizhawk-bk2 bizhawk.bk2 --bk2-offset 840
  python s1_trace_recorder.py --state GreenHillZone.Act1

Prerequisites:
  pip install stable-retro numpy
  python -m stable_retro.import /path/to/directory/containing/rom/
"""

import argparse
import os
import sys

import numpy as np

try:
    import stable_retro
except ImportError:
    print("ERROR: stable-retro is not installed.", file=sys.stderr)
    print("  pip install stable-retro", file=sys.stderr)
    print("  python -m stable_retro.import /path/to/roms/", file=sys.stderr)
    sys.exit(1)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from trace_core import (
    GenesisRAM, TraceRecorder, BizhawkBK2, write_metadata,
    ADDR_GAME_MODE, ADDR_ZONE, ADDR_ACT,
    PLAYER_BASE, OFF_X_POS, OFF_Y_POS, OFF_CTRL_LOCK,
    GAMEMODE_LEVEL, ZONE_NAMES,
)

GAME_NAME = 'SonicTheHedgehog-Genesis-v0'
MOVIE_SAFETY_MARGIN = 30


def parse_args():
    p = argparse.ArgumentParser(
        description="Record Sonic 1 physics trace using stable-retro")
    src = p.add_mutually_exclusive_group()
    src.add_argument('--movie', metavar='BK2',
                     help='Replay a stable-retro BK2 movie file')
    src.add_argument('--bizhawk-bk2', metavar='BK2',
                     help='Replay a BizHawk BK2 movie file (auto-converted)')
    p.add_argument('--state', metavar='NAME', default=None,
                   help='Boot from a named savestate (e.g. GreenHillZone.Act1)')
    p.add_argument('--output-dir', default='trace_output',
                   help='Output directory (default: trace_output)')
    p.add_argument('--max-frames', type=int, default=0,
                   help='Maximum trace frames to record (0=unlimited)')
    p.add_argument('--bk2-offset', type=int, default=0,
                   help='BizHawk gameplay start frame (from metadata.json '
                        'bk2_frame_offset). During boot, BK2 plays as-is; '
                        'once gameplay starts, inputs jump to this frame. '
                        'GHZ1=840, MZ1=1075. 0=no alignment (default).')
    return p.parse_args()


def _make_env(game=GAME_NAME, state=None, **kwargs):
    return stable_retro.make(
        game=game, state=state,
        use_restricted_actions=stable_retro.Actions.ALL,
        render_mode=None, **kwargs)


def main():
    args = parse_args()

    movie = None
    bk2_input = None
    bizhawk_gameplay_start = args.bk2_offset  # BizHawk's bk2_frame_offset

    if args.movie:
        print(f"Loading stable-retro movie: {args.movie}")
        movie = stable_retro.Movie(args.movie)
        env = _make_env(game=movie.get_game(), state=None, players=movie.players)
        env.initial_state = movie.get_state()
        env.reset()
    elif args.bizhawk_bk2:
        print(f"Loading BizHawk BK2: {args.bizhawk_bk2}")
        bk2_input = BizhawkBK2(args.bizhawk_bk2)
        print(f"  Parsed {bk2_input.frame_count} input frames")
        if bizhawk_gameplay_start:
            print(f"  BizHawk gameplay start: BK2 frame {bizhawk_gameplay_start}")
        state = args.state if args.state else stable_retro.State.NONE
        env = _make_env(state=state)
        env.reset()
    else:
        print(f"Booting from state: {args.state or 'default'}")
        state = args.state if args.state else stable_retro.State.DEFAULT
        env = _make_env(state=state)
        env.reset()

    num_buttons = env.action_space.shape[0]
    no_input = np.zeros(num_buttons, dtype=np.int8)
    mem = GenesisRAM()
    recorder = TraceRecorder(args.output_dir)

    started = False
    finished = False
    emu_frame = 0
    bk2_frame_offset = 0
    start_x = start_y = 0
    start_zone_id = 0
    start_zone_name = "unknown"
    start_act = 0
    # stable-retro's genesis_plus_gx enters game_mode 0x8C (level + loading
    # flag) very early, with ctrl_lock stuck at 0.  The real gameplay start
    # is when Sonic's routine advances to 0x02 (Sonic_Control) — this means
    # the player object has been fully initialized with position, angle, and
    # collision state.  We wait for level mode + routine == 0x02.
    saw_x_zero = False

    print("S1 Trace Recorder (stable-retro) loaded.")
    print("Waiting for level gameplay...")

    while not finished:
        # --- Input selection ---
        action = no_input
        if movie:
            if not movie.step():
                print(f"Movie ended at emu frame {emu_frame}. Finalising.")
                break
            keys = [movie.get_key(i, 0) for i in range(num_buttons)]
            action = np.array(keys, dtype=np.int8)
        elif bk2_input:
            if not started:
                action = bk2_input[emu_frame]
            else:
                bk2_idx = bizhawk_gameplay_start + recorder.trace_frame
                action = bk2_input[bk2_idx]

        # --- Step ---
        env.step(action)
        ram = env.get_ram()
        mem.update(ram)
        gm = mem.u8(ADDR_GAME_MODE)

        # --- Gameplay detection ---
        if not started:
            x = mem.u16(PLAYER_BASE + OFF_X_POS)
            routine = mem.u8(PLAYER_BASE + 0x24)  # OFF_ROUTINE
            in_level = (gm & 0x7F) == GAMEMODE_LEVEL
            if in_level and x == 0:
                saw_x_zero = True
            # Detect when Sonic is fully initialized: position set AND
            # routine advanced to 0x02 (Sonic_Control).  This matches the
            # BizHawk detection frame where angle/routine are already valid.
            if in_level and saw_x_zero and x > 0 and routine == 0x02:
                started = True
                bk2_frame_offset = emu_frame + 1

                start_x = mem.u16(PLAYER_BASE + OFF_X_POS)
                start_y = mem.u16(PLAYER_BASE + OFF_Y_POS)
                start_zone_id = mem.u8(ADDR_ZONE)
                start_act = mem.u8(ADDR_ACT)
                start_zone_name = ZONE_NAMES.get(
                    start_zone_id, f"unknown_{start_zone_id:02X}")

                recorder.open()
                write_metadata(
                    os.path.join(args.output_dir, "metadata.json"),
                    zone=start_zone_name, zone_id=start_zone_id,
                    act=start_act + 1, bk2_frame_offset=bk2_frame_offset,
                    trace_frame_count=0,
                    start_x=start_x, start_y=start_y,
                )
                print(f"Recording started at emu frame {bk2_frame_offset}, "
                      f"zone {start_zone_name} act {start_act + 1}, "
                      f"pos ({start_x:04X}, {start_y:04X})")
                emu_frame += 1
                continue

            emu_frame += 1
            continue

        # --- Recording ---
        if (gm & 0x7F) != GAMEMODE_LEVEL:
            print(f"Left level gameplay at trace frame {recorder.trace_frame}.")
            break

        if bk2_input:
            bk2_idx = bizhawk_gameplay_start + recorder.trace_frame
            if bk2_idx >= bk2_input.frame_count + MOVIE_SAFETY_MARGIN:
                print(f"BK2 input exhausted at trace frame {recorder.trace_frame}.")
                break

        if args.max_frames and recorder.trace_frame >= args.max_frames:
            print(f"Reached max frame limit ({args.max_frames}).")
            break

        recorder.record_frame(mem)

        if recorder.trace_frame % 300 == 0:
            write_metadata(
                os.path.join(args.output_dir, "metadata.json"),
                zone=start_zone_name, zone_id=start_zone_id,
                act=start_act + 1, bk2_frame_offset=bk2_frame_offset,
                trace_frame_count=recorder.trace_frame,
                start_x=start_x, start_y=start_y,
            )

        emu_frame += 1

    # Finalise
    print("Recording complete. Writing final output...")
    write_metadata(
        os.path.join(args.output_dir, "metadata.json"),
        zone=start_zone_name, zone_id=start_zone_id,
        act=start_act + 1, bk2_frame_offset=bk2_frame_offset,
        trace_frame_count=recorder.trace_frame,
        start_x=start_x, start_y=start_y,
    )
    recorder.close()
    print(f"Trace finalised: {start_zone_name} act {start_act + 1}, "
          f"{recorder.trace_frame} frames.")
    env.close()


if __name__ == '__main__':
    main()
