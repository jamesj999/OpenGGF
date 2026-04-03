#!/usr/bin/env python3
"""
Sonic 1 Credits Trace Recorder for stable-retro.

Equivalent of tools/bizhawk/s1_credits_trace_recorder.lua (v1.2).
Forces Sonic 1 into the ending credits and records the built-in ending
demos as trace directories.  The ROM contains its own input data for
these replays -- no external movie file is needed.

Output root:
  trace_output/credits_demos/
  trace_output/credits_demos/manifest.json
  trace_output/credits_demos/00_ghz1_credits_demo_1/
  trace_output/credits_demos/01_mz2_credits_demo/
  ...

Usage:
  python s1_credits_trace_recorder.py                     # Record all 8 demos
  python s1_credits_trace_recorder.py --target 3          # Record LZ3 only
  python s1_credits_trace_recorder.py --target all        # Explicit all
  python s1_credits_trace_recorder.py --force-mode direct # Force from title

Prerequisites:
  pip install stable-retro numpy
  python -m stable_retro.import /path/to/directory/containing/rom/

NOTE: This script requires writing to specific RAM addresses to force
credits mode.  It installs an extended data.json ('trace_data.json') into
the stable-retro game integration directory on first run.
"""

import argparse
import json
import os
import sys
import tempfile

import numpy as np

try:
    import stable_retro
except ImportError:
    print("ERROR: stable-retro is not installed.", file=sys.stderr)
    print("  pip install stable-retro", file=sys.stderr)
    sys.exit(1)

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from trace_core import (
    GenesisRAM, TraceRecorder, write_metadata, write_credits_manifest,
    ADDR_GAME_MODE, ADDR_CTRL1, ADDR_GENERICTIMER, ADDR_ZONE, ADDR_ACT,
    ADDR_LIVES, ADDR_RING_COUNT, ADDR_TIME, ADDR_SCORE,
    ADDR_WATER_ROUTINE, ADDR_WATER_STATE,
    ADDR_DEMO_FLAG, ADDR_DEMO_NUM, ADDR_CREDITS_NUM,
    ADDR_LAST_LAMP, ADDR_LAMP_X, ADDR_LAMP_Y, ADDR_LAMP_RINGS,
    ADDR_LAMP_TIME, ADDR_LAMP_LIMIT_BTM,
    ADDR_LAMP_SCR_X, ADDR_LAMP_SCR_Y,
    ADDR_LAMP_BG1_X, ADDR_LAMP_BG1_Y, ADDR_LAMP_BG2_X, ADDR_LAMP_BG2_Y,
    ADDR_LAMP_BG3_X, ADDR_LAMP_BG3_Y,
    ADDR_LAMP_WATER_POS, ADDR_LAMP_WATER_ROUT, ADDR_LAMP_WATER_STAT,
    PLAYER_BASE, OFF_X_POS, OFF_Y_POS, OFF_ROUTINE, OFF_CTRL_LOCK,
    GAMEMODE_TITLE, GAMEMODE_DEMO, GAMEMODE_LEVEL, GAMEMODE_CREDITS,
    ZONE_NAMES,
)

GAME_NAME = 'SonicTheHedgehog-Genesis-v0'
RAM_BASE = 0xFF0000  # 68K work RAM base for absolute address calculation

# Credits demo definitions (matching Lua DEMO_* tables)
DEMO_SLUGS = {
    0: "ghz1_credits_demo_1",
    1: "mz2_credits_demo",
    2: "syz3_credits_demo",
    3: "lz3_credits_demo",
    4: "slz3_credits_demo",
    5: "sbz1_credits_demo",
    6: "sbz2_credits_demo",
    7: "ghz1_credits_demo_2",
}

DEMO_ZONE_ACT_WORDS = {
    0: 0x0000,  # GHZ1
    1: 0x0201,  # MZ2
    2: 0x0402,  # SYZ3
    3: 0x0102,  # LZ3
    4: 0x0302,  # SLZ3
    5: 0x0500,  # SBZ1
    6: 0x0501,  # SBZ2
    7: 0x0000,  # GHZ1 second demo
}

DEMO_TIMER_FRAMES = {
    0: 540, 1: 540, 2: 540, 3: 510,
    4: 540, 5: 540, 6: 540, 7: 540,
}

FINAL_CREDITS_DEMO_INDEX = 7

# Extended data.json entries for RAM writes.
# Each tuple is (offset_from_FF0000, type_string).
# _build_trace_data_json() adds RAM_BASE (0xFF0000) to produce
# the absolute 68K addresses that stable-retro expects.
_TRACE_VARS = {
    # Original data.json variables
    "score": (0xFE26, ">u4"),       # v_score
    "lives": (0xFE12, "|u1"),       # v_lives
    "rings": (0xFE20, ">u2"),       # v_rings
    "x": (0xD008, ">i2"),           # player centre X ($FFD008)
    "y": (0xD00C, ">u2"),           # player centre Y ($FFD00C)
    "zone": (0xFE10, "|u1"),        # v_zone
    "act": (0xFE11, "|u1"),         # v_act
    "screen_x": (0xF700, ">u2"),    # v_screenposx
    "screen_y": (0xF704, ">u2"),    # v_screenposy
    # Additional variables for credits mode forcing
    "game_mode": (0xF600, "|u1"),
    "demo_flag": (0xFFF2, ">u2"),
    "demo_num": (0xFFF4, ">u2"),
    "credits_num": (0xFFF6, ">u2"),
    "generic_timer": (0xF614, ">u2"),
    "water_routine": (0xF64D, "|u1"),
    "water_state": (0xF64E, "|u1"),
    "zone_word": (0xFE10, ">u2"),  # Write zone+act as a word
    "time_long": (0xFE22, ">u4"),
    "score_long": (0xFE26, ">u4"),
    # Lamp state
    "last_lamp": (0xFD2E, "|u1"),
    "last_lamp_1": (0xFD2F, "|u1"),
    "lamp_x": (0xFD30, ">u2"),
    "lamp_y": (0xFD32, ">u2"),
    "lamp_rings": (0xFD34, ">u2"),
    "lamp_time": (0xFD36, ">u4"),
    "lamp_time_hi": (0xFD3A, "|u1"),
    "lamp_time_lo": (0xFD3B, "|u1"),
    "lamp_limit_btm": (0xFD3C, ">u2"),
    "lamp_scr_x": (0xFD3E, ">u2"),
    "lamp_scr_y": (0xFD40, ">u2"),
    "lamp_bg1_x": (0xFD42, ">u2"),
    "lamp_bg1_y": (0xFD44, ">u2"),
    "lamp_bg2_x": (0xFD46, ">u2"),
    "lamp_bg2_y": (0xFD48, ">u2"),
    "lamp_bg3_x": (0xFD4A, ">u2"),
    "lamp_bg3_y": (0xFD4C, ">u2"),
    "lamp_water_pos": (0xFD4E, ">u2"),
    "lamp_water_rout": (0xFD50, "|u1"),
    "lamp_water_stat": (0xFD51, "|u1"),
}


def _build_trace_data_json():
    """Build the extended data.json content for trace recording."""
    info = {}
    for name, (offset, dtype) in _TRACE_VARS.items():
        info[name] = {
            "address": RAM_BASE + offset,
            "type": dtype,
        }
    return {"info": info}


def install_trace_data(game_dir):
    """Write trace_data.json to the game integration directory."""
    path = os.path.join(game_dir, "trace_data.json")
    if os.path.exists(path):
        return path
    data = _build_trace_data_json()
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print(f"Installed trace_data.json to {path}")
    return path


def find_game_dir():
    """Find the stable-retro game integration directory for Sonic 1."""
    try:
        rom_path = stable_retro.data.get_romfile_path(
            GAME_NAME, stable_retro.data.Integrations.DEFAULT)
        return os.path.dirname(rom_path)
    except FileNotFoundError:
        # Try to find the data directory manually
        data_root = os.path.join(os.path.dirname(stable_retro.__file__),
                                 "data", "stable", GAME_NAME)
        if os.path.isdir(data_root):
            return data_root
        raise FileNotFoundError(
            f"Sonic 1 ROM not found. Run: python -m stable_retro.import /path/to/roms/")


def parse_args():
    p = argparse.ArgumentParser(
        description="Record Sonic 1 credits demo traces using stable-retro")
    p.add_argument('--target', default='all',
                   help='Demo index (0-7) or "all" (default: all)')
    p.add_argument('--force-mode', default='redirect_level',
                   choices=['redirect_level', 'direct', 'none'],
                   help='How to enter credits mode (default: redirect_level)')
    p.add_argument('--output-dir', default='trace_output/credits_demos',
                   help='Output root directory')
    p.add_argument('--force-delay', type=int, default=30,
                   help='Frames to wait on title before forcing (default: 30)')
    p.add_argument('--prestart-timeout', type=int, default=2400,
                   help='Max frames waiting for demo start (default: 2400)')
    p.add_argument('--max-trace-frames', type=int, default=2000,
                   help='Max trace frames per demo (default: 2000)')
    return p.parse_args()


class CreditsRecorder:
    """Manages the credits demo recording lifecycle."""

    def __init__(self, args):
        self.args = args
        self.output_root = args.output_dir

        if args.target.lower() == 'all':
            self.target_all = True
            self.target_index = 0
        else:
            self.target_all = False
            self.target_index = int(args.target)
            if not 0 <= self.target_index <= 7:
                raise ValueError("--target must be 0-7 or 'all'")

        if not self.target_all and args.force_mode == 'direct':
            pass  # direct mode with single target is fine
        if self.target_all and args.force_mode == 'direct':
            raise ValueError("--target all is not supported with --force-mode direct")

        # Environment setup
        game_dir = find_game_dir()
        install_trace_data(game_dir)

        self.env = stable_retro.make(
            game=GAME_NAME,
            state=stable_retro.State.DEFAULT,
            info='trace_data',
            use_restricted_actions=stable_retro.Actions.ALL,
        )
        self.env.reset()
        self.num_buttons = self.env.action_space.shape[0]
        self.no_input = np.zeros(self.num_buttons, dtype=np.int8)

        self.mem = GenesisRAM()
        self.recorder = TraceRecorder(self.output_root)

        # State machine
        self.demo_forced = False
        self.title_frames_seen = 0
        self.recording_segment = False
        self.waiting_frames = 0
        self.current_demo_index = None
        self.segment_start_emu = 0
        self.start_x = self.start_y = 0
        self.start_zone_id = 0
        self.start_zone_name = "unknown"
        self.start_act = 0
        self.captured_demos = set()
        self.manifest_entries = []

    def run(self):
        os.makedirs(self.output_root, exist_ok=True)
        print(f"S1 Credits Trace Recorder (stable-retro) loaded.")
        print(f"Target: {'all' if self.target_all else self.target_index}")
        print(f"Force mode: {self.args.force_mode}")

        emu_frame = 0
        while True:
            self.env.step(self.no_input)
            ram = self.env.get_ram()
            self.mem.update(ram)

            done = self._on_frame(emu_frame)
            if done:
                break
            emu_frame += 1

        if self.recording_segment:
            self._finish_segment()
        write_credits_manifest(
            os.path.join(self.output_root, "manifest.json"),
            self.manifest_entries)
        print("Credits demo recording complete.")
        self.env.close()

    def _on_frame(self, emu_frame):
        game_mode = self.mem.u8(ADDR_GAME_MODE)
        base_mode = game_mode & 0x7F

        # Phase 1: Force credits mode
        if not self.demo_forced:
            return self._handle_forcing(base_mode, emu_frame)

        # Phase 2: Wait for demo segment to become active
        if not self.recording_segment:
            return self._handle_waiting(game_mode, emu_frame)

        # Phase 3: Record the active segment
        return self._handle_recording(game_mode, emu_frame)

    def _handle_forcing(self, base_mode, emu_frame):
        if self.args.force_mode == 'none':
            if (base_mode == GAMEMODE_CREDITS or
                    self.mem.u16(ADDR_DEMO_FLAG) == 0x8001):
                self.demo_forced = True
                self.waiting_frames = 0
                print(f"Detected credits flow at emu frame {emu_frame}.")
            return False

        if self.args.force_mode == 'redirect_level':
            if base_mode == GAMEMODE_LEVEL:
                self._redirect_to_credits()
                self.demo_forced = True
                self.waiting_frames = 0
                print(f"Redirected level to GM_Credits at emu frame {emu_frame}.")
            return False

        # direct mode
        if base_mode == GAMEMODE_TITLE:
            self.title_frames_seen += 1
            if self.title_frames_seen >= self.args.force_delay:
                self._force_target_demo()
                self.demo_forced = True
                self.waiting_frames = 0
                print(f"Forced credits demo {self.target_index} at emu frame {emu_frame}.")
        return False

    def _handle_waiting(self, game_mode, emu_frame):
        self.waiting_frames += 1
        if self.waiting_frames % 120 == 0:
            print(f"  Waiting for demo gameplay: frame {emu_frame}, "
                  f"mode=0x{game_mode:02X}, credits={self.mem.u16(ADDR_CREDITS_NUM)}")
        if self.waiting_frames > self.args.prestart_timeout:
            raise TimeoutError(
                f"Timed out waiting for credits demo "
                f"(mode=0x{game_mode:02X}, "
                f"credits={self.mem.u16(ADDR_CREDITS_NUM)})")

        demo_index = self._ready_to_start(game_mode)
        if demo_index is not None:
            self._start_segment(demo_index, emu_frame)
        return False

    def _handle_recording(self, game_mode, emu_frame):
        base_mode = game_mode & 0x7F
        if base_mode != GAMEMODE_DEMO or game_mode != GAMEMODE_DEMO:
            self._finish_segment()
            return self._capture_complete()

        if self.recorder.trace_frame >= self.args.max_trace_frames:
            raise RuntimeError(
                f"Credits demo {self.current_demo_index} exceeded "
                f"{self.args.max_trace_frames} frames")

        self.recorder.record_frame(self.mem)

        if self.recorder.trace_frame % 300 == 0:
            self._write_segment_metadata()
            print(f"  Demo {self.current_demo_index}: "
                  f"{self.recorder.trace_frame} frames recorded")
        return False

    # -----------------------------------------------------------------
    # RAM writes for forcing credits mode
    # -----------------------------------------------------------------

    def _set_val(self, name, value):
        """Write a value to RAM via stable-retro's data interface."""
        self.env.data.set_value(name, value)

    def _redirect_to_credits(self):
        self._set_val("demo_flag", 0)
        self._set_val("credits_num", 0)
        self._set_val("game_mode", GAMEMODE_CREDITS)

    def _force_target_demo(self):
        zone_act = DEMO_ZONE_ACT_WORDS[self.target_index]
        self._set_val("demo_flag", 0x8001)
        self._set_val("demo_num", 0)
        self._set_val("credits_num", self.target_index + 1)
        self._set_val("zone_word", zone_act)
        self._set_val("lives", 3)
        self._set_val("rings", 0)
        self._set_val("time_long", 0)
        self._set_val("score_long", 0)
        self._set_val("water_routine", 0)
        self._set_val("water_state", 0)
        self._zero_lamp_state()
        if self.target_index == 3:  # LZ3
            self._apply_lz_lamp_state()
            self._set_val("water_routine", 1)
            self._set_val("water_state", 1)
        self._set_val("game_mode", GAMEMODE_DEMO)

    def _zero_lamp_state(self):
        for name in ("last_lamp", "last_lamp_1", "lamp_x", "lamp_y",
                      "lamp_rings", "lamp_time", "lamp_time_hi", "lamp_time_lo",
                      "lamp_limit_btm", "lamp_scr_x", "lamp_scr_y",
                      "lamp_bg1_x", "lamp_bg1_y", "lamp_bg2_x", "lamp_bg2_y",
                      "lamp_bg3_x", "lamp_bg3_y",
                      "lamp_water_pos", "lamp_water_rout", "lamp_water_stat"):
            self._set_val(name, 0)

    def _apply_lz_lamp_state(self):
        self._set_val("last_lamp", 1)
        self._set_val("last_lamp_1", 1)
        self._set_val("lamp_x", 0x0A00)
        self._set_val("lamp_y", 0x062C)
        self._set_val("lamp_rings", 13)
        self._set_val("lamp_time", 0)
        self._set_val("lamp_time_hi", 0)
        self._set_val("lamp_time_lo", 0)
        self._set_val("lamp_limit_btm", 0x0800)
        self._set_val("lamp_scr_x", 0x0957)
        self._set_val("lamp_scr_y", 0x05CC)
        self._set_val("lamp_bg1_x", 0x04AB)
        self._set_val("lamp_bg1_y", 0x03A6)
        self._set_val("lamp_bg2_x", 0x0000)
        self._set_val("lamp_bg2_y", 0x028C)
        self._set_val("lamp_bg3_x", 0x0000)
        self._set_val("lamp_bg3_y", 0x0000)
        self._set_val("lamp_water_pos", 0x0308)
        self._set_val("lamp_water_rout", 1)
        self._set_val("lamp_water_stat", 1)

    # -----------------------------------------------------------------
    # Segment lifecycle
    # -----------------------------------------------------------------

    def _get_active_credits_demo_index(self):
        if self.mem.u16(ADDR_DEMO_FLAG) != 0x8001:
            return None
        credits_num = self.mem.u16(ADDR_CREDITS_NUM)
        if credits_num <= 0:
            return None
        idx = credits_num - 1
        if not 0 <= idx <= FINAL_CREDITS_DEMO_INDEX:
            return None
        return idx

    def _should_capture(self, idx):
        if idx is None or idx in self.captured_demos:
            return False
        return self.target_all or idx == self.target_index

    def _capture_complete(self):
        if self.target_all:
            return len(self.captured_demos) > FINAL_CREDITS_DEMO_INDEX
        return self.target_index in self.captured_demos

    def _ready_to_start(self, game_mode):
        if game_mode != GAMEMODE_DEMO:
            return None
        idx = self._get_active_credits_demo_index()
        if not self._should_capture(idx):
            return None

        routine = self.mem.u8(PLAYER_BASE + OFF_ROUTINE)
        timer = self.mem.u16(ADDR_GENERICTIMER)
        expected_za = DEMO_ZONE_ACT_WORDS.get(idx, -1)
        expected_timer = DEMO_TIMER_FRAMES.get(idx, 540)
        actual_za = self.mem.u16(ADDR_ZONE)
        x = self.mem.u16(PLAYER_BASE + OFF_X_POS)
        y = self.mem.u16(PLAYER_BASE + OFF_Y_POS)

        if (actual_za == expected_za and routine >= 0x02
                and expected_timer - 120 <= timer <= expected_timer
                and not (x == 0 and y == 0)):
            return idx
        return None

    def _start_segment(self, demo_index, emu_frame):
        self.recording_segment = True
        self.current_demo_index = demo_index
        self.segment_start_emu = emu_frame

        self.start_x = self.mem.u16(PLAYER_BASE + OFF_X_POS)
        self.start_y = self.mem.u16(PLAYER_BASE + OFF_Y_POS)
        self.start_zone_id = self.mem.u8(ADDR_ZONE)
        self.start_act = self.mem.u8(ADDR_ACT)
        self.start_zone_name = ZONE_NAMES.get(
            self.start_zone_id, f"unknown_{self.start_zone_id:02X}")

        seg_dir = self._segment_dir(demo_index)
        self.recorder = TraceRecorder(seg_dir)
        self.recorder.open()
        self._write_segment_metadata()

        slug = DEMO_SLUGS.get(demo_index, "unknown")
        print(f"Recording credits demo {demo_index} -> {seg_dir} "
              f"(zone {self.start_zone_name} act {self.start_act + 1})")

    def _finish_segment(self):
        idx = self.current_demo_index
        self.recorder.close()
        self._write_segment_metadata()

        slug = DEMO_SLUGS.get(idx, "unknown")
        self.manifest_entries.append({
            "credits_demo_index": idx,
            "slug": slug,
            "directory": f"{idx:02d}_{slug}",
            "zone": self.start_zone_name,
            "act": self.start_act + 1,
            "trace_frame_count": self.recorder.trace_frame,
        })
        write_credits_manifest(
            os.path.join(self.output_root, "manifest.json"),
            self.manifest_entries)

        print(f"Finalised credits demo {idx} ({slug}) with "
              f"{self.recorder.trace_frame} trace frames.")

        self.captured_demos.add(idx)
        self.recording_segment = False
        self.current_demo_index = None
        self.waiting_frames = 0

        if not self._capture_complete() and self.target_all:
            print("Waiting for next credits demo in sequence...")

    def _segment_dir(self, idx):
        slug = DEMO_SLUGS.get(idx, f"credits_demo_{idx:02d}")
        return os.path.join(self.output_root, f"{idx:02d}_{slug}")

    def _write_segment_metadata(self):
        idx = self.current_demo_index if self.current_demo_index is not None \
            else self.target_index
        slug = DEMO_SLUGS.get(idx, "unknown")
        seg_dir = self._segment_dir(idx)
        write_metadata(
            os.path.join(seg_dir, "metadata.json"),
            zone=self.start_zone_name, zone_id=self.start_zone_id,
            act=self.start_act + 1, bk2_frame_offset=0,
            trace_frame_count=self.recorder.trace_frame,
            start_x=self.start_x, start_y=self.start_y,
            script_version="credits-retro-1.0",
            extra_fields={
                "trace_type": "credits_demo",
                "input_source": "rom_ending_demo",
                "credits_demo_index": idx,
                "credits_demo_slug": slug,
                "emu_frame_start": self.segment_start_emu,
            },
        )


def main():
    args = parse_args()
    recorder = CreditsRecorder(args)
    recorder.run()


if __name__ == '__main__':
    main()
