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

If no input source is given, the game boots from the default state and runs
with no input (useful for credits demos that use ROM-internal input data).

Usage:
  python s1_trace_recorder.py --movie my_recording.bk2
  python s1_trace_recorder.py --bizhawk-bk2 path/to/bizhawk.bk2
  python s1_trace_recorder.py --state GreenHillZone.Act1 --output-dir trace_output/

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

# Import shared infrastructure (same directory)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from trace_core import (
    GenesisRAM, TraceRecorder, BizhawkBK2, write_metadata,
    ADDR_GAME_MODE, ADDR_ZONE, ADDR_ACT, ADDR_CTRL1,
    PLAYER_BASE, OFF_X_POS, OFF_Y_POS, OFF_CTRL_LOCK,
    GAMEMODE_LEVEL, ZONE_NAMES,
)

GAME_NAME = 'SonicTheHedgehog-Genesis-v0'
MOVIE_SAFETY_MARGIN = 30  # frames past movie end before auto-exit


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
    return p.parse_args()


def create_env_from_movie(movie_path):
    """Create env from a stable-retro BK2 movie, returning (env, movie)."""
    movie = stable_retro.Movie(movie_path)
    env = stable_retro.make(
        game=movie.get_game(),
        state=None,
        use_restricted_actions=stable_retro.Actions.ALL,
        players=movie.players,
    )
    env.initial_state = movie.get_state()
    env.reset()
    return env, movie


def create_env_from_bizhawk_bk2(bk2_path, state_name=None):
    """Create env for BizHawk BK2 replay (boot from power-on or savestate)."""
    bk2 = BizhawkBK2(bk2_path)

    # BizHawk BK2s start from power-on. Use State.NONE for clean boot.
    if state_name:
        state = state_name
    else:
        state = stable_retro.State.NONE

    env = stable_retro.make(
        game=GAME_NAME,
        state=state,
        use_restricted_actions=stable_retro.Actions.ALL,
    )
    env.reset()
    return env, bk2


def create_env_from_state(state_name):
    """Create env from a named savestate (no movie)."""
    env = stable_retro.make(
        game=GAME_NAME,
        state=state_name or stable_retro.State.DEFAULT,
        use_restricted_actions=stable_retro.Actions.ALL,
    )
    env.reset()
    return env, None


def main():
    args = parse_args()

    # Set up environment and input source
    movie = None       # stable-retro Movie object (or None)
    bk2_input = None   # BizhawkBK2 object (or None)

    if args.movie:
        print(f"Loading stable-retro movie: {args.movie}")
        env, movie = create_env_from_movie(args.movie)
    elif args.bizhawk_bk2:
        print(f"Loading BizHawk BK2: {args.bizhawk_bk2}")
        env, bk2_input = create_env_from_bizhawk_bk2(
            args.bizhawk_bk2, args.state)
        print(f"  Parsed {bk2_input.frame_count} input frames")
    else:
        print(f"Booting from state: {args.state or 'default'}")
        env, _ = create_env_from_state(args.state)

    num_buttons = env.action_space.shape[0]
    no_input = np.zeros(num_buttons, dtype=np.int8)

    mem = GenesisRAM()
    recorder = TraceRecorder(args.output_dir)

    # State machine
    started = False
    finished = False
    emu_frame = 0
    bk2_frame_offset = 0
    start_x = start_y = 0
    start_zone_id = 0
    start_zone_name = "unknown"
    start_act = 0

    print("S1 Trace Recorder (stable-retro) loaded.")
    print("Waiting for level gameplay (game_mode=0x0C, controls unlocked)...")

    while not finished:
        # Determine input for this frame
        action = no_input
        if movie:
            if not movie.step():
                print(f"Movie ended at emu frame {emu_frame}. Finalising.")
                finished = True
                break
            keys = []
            for p in range(movie.players):
                for i in range(num_buttons):
                    keys.append(movie.get_key(i, p))
            action = np.array(keys[:num_buttons], dtype=np.int8)
        elif bk2_input:
            action = bk2_input[emu_frame]

        # Step the emulator
        env.step(action)
        ram = env.get_ram()
        mem.update(ram)

        game_mode = mem.u8(ADDR_GAME_MODE)

        if not started:
            ctrl_lock = mem.u16(PLAYER_BASE + OFF_CTRL_LOCK)
            if game_mode == GAMEMODE_LEVEL and ctrl_lock == 0:
                started = True
                # Store the NEXT frame as bk2_frame_offset (matching
                # BizHawk convention where this points to the first
                # recorded frame's input, not the detection frame)
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

                # Skip this detection frame (same as Lua: return without
                # recording so frame 0 captures post-movement state)
                emu_frame += 1
                continue

            emu_frame += 1
            continue

        # Recording active -- check for level end
        if game_mode != GAMEMODE_LEVEL:
            print(f"Left level gameplay at trace frame {recorder.trace_frame}. "
                  "Finalising.")
            finished = True
            break

        # Check for BK2 exhaustion
        if bk2_input and emu_frame >= bk2_input.frame_count + MOVIE_SAFETY_MARGIN:
            print(f"BK2 input exhausted at trace frame {recorder.trace_frame}. "
                  "Finalising.")
            finished = True
            break

        # Check max frame limit
        if args.max_frames and recorder.trace_frame >= args.max_frames:
            print(f"Reached max frame limit ({args.max_frames}). Finalising.")
            finished = True
            break

        # Record this frame
        recorder.record_frame(mem)

        # Periodic metadata update
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
