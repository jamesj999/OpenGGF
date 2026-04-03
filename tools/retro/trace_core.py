"""
Shared tracing infrastructure for Sonic 1 stable-retro trace recording.

Provides RAM reading utilities, output writers, and object scanning logic
that produce output identical to the BizHawk Lua trace recorder scripts
(s1_trace_recorder.lua v2.2, s1_credits_trace_recorder.lua v1.2).

Output files:
  - physics.csv     (v2.2 format, csv_version=4, 20 columns)
  - aux_state.jsonl (event stream with object tracking)
  - metadata.json   (recording session metadata)
"""

import json
import os
import zipfile
from datetime import date

import numpy as np

# =====================================================================
# S1 REV01 Memory Map
# Offsets from $FF0000 -- same as BizHawk mainmemory domain addresses.
# stable-retro get_ram() returns 64KB starting at index 0 = $FF0000,
# so these offsets are used directly as array indices.
# =====================================================================

# Game state
ADDR_GAME_MODE = 0xF600
ADDR_CTRL1 = 0xF604       # v_jpadhold1 (held input from joypad)
ADDR_CTRL1_DUP = 0xF602   # v_jpadhold2 (game-logic copy, zeroed when locked)
ADDR_GENERICTIMER = 0xF614
ADDR_WATER_ROUTINE = 0xF64D
ADDR_WATER_STATE = 0xF64E

# Camera
ADDR_CAMERA_X = 0xF700    # long: v_screenposx (pixel:subpixel)
ADDR_CAMERA_Y = 0xF704    # long: v_screenposy (pixel:subpixel)

# ObjPosLoad cursor state
ADDR_OPL_SCREEN = 0xF76E  # word: last processed camera chunk
ADDR_OPL_DATA_FWD = 0xF770  # long: forward cursor ROM pointer
ADDR_OPL_DATA_BWD = 0xF774  # long: backward cursor ROM pointer
ADDR_OBJSTATE = 0xFC00    # byte[192]: v_objstate array

# Level state
ADDR_FRAMECOUNT = 0xFE02  # word: v_framecount (Level_MainLoop counter)
ADDR_ZONE = 0xFE10
ADDR_ACT = 0xFE11
ADDR_LIVES = 0xFE12
ADDR_RING_COUNT = 0xFE20
ADDR_TIME = 0xFE22
ADDR_SCORE = 0xFE26

# Demo / credits
ADDR_DEMO_FLAG = 0xFFF2
ADDR_DEMO_NUM = 0xFFF4
ADDR_CREDITS_NUM = 0xFFF6

# Lamp state (LZ)
ADDR_LAST_LAMP = 0xFD2E
ADDR_LAMP_X = 0xFD30
ADDR_LAMP_Y = 0xFD32
ADDR_LAMP_RINGS = 0xFD34
ADDR_LAMP_TIME = 0xFD36
ADDR_LAMP_LIMIT_BTM = 0xFD3C
ADDR_LAMP_SCR_X = 0xFD3E
ADDR_LAMP_SCR_Y = 0xFD40
ADDR_LAMP_BG1_X = 0xFD42
ADDR_LAMP_BG1_Y = 0xFD44
ADDR_LAMP_BG2_X = 0xFD46
ADDR_LAMP_BG2_Y = 0xFD48
ADDR_LAMP_BG3_X = 0xFD4A
ADDR_LAMP_BG3_Y = 0xFD4C
ADDR_LAMP_WATER_POS = 0xFD4E
ADDR_LAMP_WATER_ROUT = 0xFD50
ADDR_LAMP_WATER_STAT = 0xFD51

# Player object base ($FFD000)
PLAYER_BASE = 0xD000
OFF_X_POS = 0x08       # word: centre X
OFF_X_SUB = 0x0A       # word: X subpixel
OFF_Y_POS = 0x0C       # word: centre Y
OFF_Y_SUB = 0x0E       # word: Y subpixel
OFF_X_VEL = 0x10       # signed word: X velocity
OFF_Y_VEL = 0x12       # signed word: Y velocity
OFF_INERTIA = 0x14     # signed word: ground speed
OFF_RADIUS_Y = 0x16    # signed byte: Y radius
OFF_RADIUS_X = 0x17    # signed byte: X radius
OFF_ANIM_ID = 0x1C     # byte: animation ID
OFF_STATUS = 0x22      # byte: status flags
OFF_ROUTINE = 0x24     # byte: player movement routine
OFF_ANGLE = 0x26       # byte: terrain angle
OFF_STAND_ON_OBJ = 0x3D  # byte: SST slot Sonic stands on (0=none)
OFF_CTRL_LOCK = 0x3E   # word: control lock timer

# Status flag bits
STATUS_IN_AIR = 0x02
STATUS_ROLLING = 0x04
STATUS_ON_OBJECT = 0x08
STATUS_ROLL_JUMP = 0x10
STATUS_PUSHING = 0x20
STATUS_UNDERWATER = 0x40

# Object table (SST)
OBJ_TABLE_START = 0xD000
OBJ_SLOT_SIZE = 0x40
OBJ_TOTAL_SLOTS = 128
OBJ_DYNAMIC_START = 32

# Input mask (engine convention)
INPUT_UP = 0x01
INPUT_DOWN = 0x02
INPUT_LEFT = 0x04
INPUT_RIGHT = 0x08
INPUT_JUMP = 0x10

# Game modes
GAMEMODE_TITLE = 0x04
GAMEMODE_DEMO = 0x08
GAMEMODE_LEVEL = 0x0C
GAMEMODE_CREDITS = 0x1C

# Recording parameters
SNAPSHOT_INTERVAL = 60
OBJECT_PROXIMITY = 160

ZONE_NAMES = {
    0: "ghz", 1: "lz", 2: "mz", 3: "slz",
    4: "syz", 5: "sbz", 6: "endz", 7: "ss",
}


# =====================================================================
# RAM Reading
# =====================================================================

class GenesisRAM:
    """Read big-endian values from a Genesis work RAM numpy array (64KB).

    stable-retro's env.get_ram() returns the 64KB work RAM as a uint8
    numpy array where index 0 = 68K address $FF0000.  The indices match
    BizHawk mainmemory domain offsets exactly.
    """

    def __init__(self, ram_array=None):
        self._ram = ram_array

    def update(self, ram_array):
        self._ram = ram_array

    def u8(self, addr):
        return int(self._ram[addr])

    def s8(self, addr):
        v = int(self._ram[addr])
        return v - 256 if v > 127 else v

    def u16(self, addr):
        return (int(self._ram[addr]) << 8) | int(self._ram[addr + 1])

    def s16(self, addr):
        v = self.u16(addr)
        return v - 0x10000 if v > 0x7FFF else v

    def u32(self, addr):
        return ((int(self._ram[addr]) << 24) | (int(self._ram[addr + 1]) << 16) |
                (int(self._ram[addr + 2]) << 8) | int(self._ram[addr + 3]))


# =====================================================================
# Helpers
# =====================================================================

def rom_joypad_to_mask(raw):
    """Convert raw ROM joypad byte (v_jpadhold1) to engine input bitmask.

    ROM bits: 0=Up 1=Down 2=Left 3=Right 4=B 5=C 6=A 7=Start
    Bits 0-3 match INPUT_UP/DOWN/LEFT/RIGHT; A|B|C collapse to JUMP.
    """
    mask = raw & 0x0F
    if raw & 0x70:
        mask |= INPUT_JUMP
    return mask


def angle_to_ground_mode(angle):
    """Convert terrain angle byte (0-255) to ground mode (0-3).

    Mode 0: floor   (0xE0-0xFF, 0x00-0x1F)
    Mode 1: right wall (0x20-0x5F)
    Mode 2: ceiling (0x60-0x9F)
    Mode 3: left wall  (0xA0-0xDF)
    """
    if angle <= 0x1F or angle >= 0xE0:
        return 0
    if angle <= 0x5F:
        return 1
    if angle <= 0x9F:
        return 2
    return 3


# =====================================================================
# BizHawk BK2 Parser
# =====================================================================

class BizhawkBK2:
    """Parse a BizHawk BK2 movie file (ZIP) and extract per-frame inputs.

    BK2 is a ZIP archive containing Input Log.txt with per-frame button
    states.  Each frame line: |<chars>| where each char is '.' (off) or
    a letter (on).  Button order comes from the LogKey header.

    Converts to stable-retro action arrays (12 buttons for Genesis).
    """

    # stable-retro Genesis button order (from env.buttons)
    RETRO_BUTTONS = ['B', 'A', 'MODE', 'START', 'UP', 'DOWN', 'LEFT', 'RIGHT',
                     'C', 'X', 'Y', 'Z']

    # Map BizHawk button names (case-insensitive) to stable-retro indices
    _BK2_TO_RETRO = {
        'up': 4, 'down': 5, 'left': 6, 'right': 7,
        'a': 1, 'b': 0, 'c': 8, 'start': 3,
    }

    def __init__(self, path):
        self.path = path
        self._frames = []
        self._parse()

    def _parse(self):
        with zipfile.ZipFile(self.path, 'r') as zf:
            raw = zf.read('Input Log.txt').decode('utf-8')

        lines = raw.splitlines()
        button_names = []
        reading_input = False

        for line in lines:
            stripped = line.strip()
            if stripped == '[Input]':
                reading_input = True
                continue
            if stripped == '[/Input]':
                break
            if not reading_input:
                continue

            if stripped.startswith('LogKey:'):
                button_names = self._parse_logkey(stripped)
                continue

            if stripped.startswith('|') and stripped.endswith('|'):
                action = self._parse_input_line(stripped, button_names)
                self._frames.append(action)

    def _parse_logkey(self, line):
        """Extract button names from LogKey line.

        BizHawk Genesis 3-button LogKey format:
          LogKey:#Genesis 3-Button Controller P1 Up|Down|Left|Right|B|C|A|Start|
        The first segment includes the #controller-type prefix with P1 and
        the first button name inline.  Subsequent segments are bare button
        names.  Console buttons (#Reset, #Power) are prefixed with '#'.
        """
        key_part = line.split('LogKey:', 1)[1]
        segments = [s.strip() for s in key_part.split('|')]
        names = []
        for seg in segments:
            if not seg:
                continue
            if seg.startswith('#'):
                # Controller header block — extract trailing button name(s)
                # after the last 'P1 ' occurrence.
                # e.g. '#Genesis 3-Button Controller P1 Up' → 'Up'
                if 'P1 ' in seg:
                    trailing = seg.rsplit('P1 ', 1)[1].strip()
                    for btn in trailing.split():
                        if not btn.startswith('#'):
                            names.append(btn.lower())
                # Skip console buttons like '#Reset', '#Power'
                continue
            if seg.startswith('P1 '):
                names.append(seg[3:].strip().lower())
            elif seg.startswith('P2 '):
                continue  # Skip P2 for single-player
            else:
                names.append(seg.strip().lower())
        return names

    def _parse_input_line(self, line, button_names):
        """Convert a BK2 input line to a stable-retro action array."""
        content = line.strip('|')
        action = np.zeros(len(self.RETRO_BUTTONS), dtype=np.int8)
        for i, ch in enumerate(content):
            if ch != '.' and i < len(button_names):
                name = button_names[i]
                retro_idx = self._BK2_TO_RETRO.get(name, -1)
                if retro_idx >= 0:
                    action[retro_idx] = 1
        return action

    def __len__(self):
        return len(self._frames)

    def __getitem__(self, idx):
        if idx < len(self._frames):
            return self._frames[idx]
        return np.zeros(len(self.RETRO_BUTTONS), dtype=np.int8)

    @property
    def frame_count(self):
        return len(self._frames)


# =====================================================================
# Core Trace Recorder
# =====================================================================

class TraceRecorder:
    """Core recording engine producing physics.csv + aux_state.jsonl.

    Output matches BizHawk s1_trace_recorder.lua v2.2 format exactly:
    - CSV: 20 columns, hex-encoded, no header quoting
    - JSONL: compact JSON (no whitespace), one event per line
    """

    CSV_HEADER = ("frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,"
                  "ground_mode,x_sub,y_sub,routine,camera_x,camera_y,rings,"
                  "status_byte,v_framecount,stand_on_obj\n")

    def __init__(self, output_dir):
        self.output_dir = output_dir
        self.trace_frame = 0
        self.prev_status = 0
        self.prev_routine = 0
        self.prev_ctrl_lock = 0
        self.prev_opl_screen = -1
        self.known_objects = {}
        self._csv = None
        self._aux = None

    def open(self):
        os.makedirs(self.output_dir, exist_ok=True)
        self._csv = open(os.path.join(self.output_dir, "physics.csv"), "w")
        self._aux = open(os.path.join(self.output_dir, "aux_state.jsonl"), "w")
        self._csv.write(self.CSV_HEADER)
        self._csv.flush()

    def close(self):
        if self._csv:
            self._csv.flush()
            self._csv.close()
            self._csv = None
        if self._aux:
            self._aux.flush()
            self._aux.close()
            self._aux = None

    def reset_state(self):
        """Reset per-segment tracking state (for multi-segment recording)."""
        self.trace_frame = 0
        self.prev_status = 0
        self.prev_routine = 0
        self.prev_ctrl_lock = 0
        self.prev_opl_screen = -1
        self.known_objects = {}

    def _aux_event(self, event):
        """Write a JSONL event line matching Lua string.format output."""
        if self._aux:
            self._aux.write(json.dumps(event, separators=(',', ':')) + "\n")

    # -----------------------------------------------------------------
    # Frame recording
    # -----------------------------------------------------------------

    def record_frame(self, mem):
        """Record one frame of physics data from current RAM state."""
        x = mem.u16(PLAYER_BASE + OFF_X_POS)
        y = mem.u16(PLAYER_BASE + OFF_Y_POS)
        x_sub = mem.u16(PLAYER_BASE + OFF_X_SUB)
        y_sub = mem.u16(PLAYER_BASE + OFF_Y_SUB)
        x_speed = mem.s16(PLAYER_BASE + OFF_X_VEL)
        y_speed = mem.s16(PLAYER_BASE + OFF_Y_VEL)
        g_speed = mem.s16(PLAYER_BASE + OFF_INERTIA)
        angle = mem.u8(PLAYER_BASE + OFF_ANGLE)
        status = mem.u8(PLAYER_BASE + OFF_STATUS)
        routine = mem.u8(PLAYER_BASE + OFF_ROUTINE)
        camera_x = mem.u16(ADDR_CAMERA_X)
        camera_y = mem.u16(ADDR_CAMERA_Y)
        rings = mem.u16(ADDR_RING_COUNT)
        v_framecount = mem.u16(ADDR_FRAMECOUNT)
        stand_on_obj = mem.u8(PLAYER_BASE + OFF_STAND_ON_OBJ)
        raw_input = mem.u8(ADDR_CTRL1)
        input_mask = rom_joypad_to_mask(raw_input)

        air = bool(status & STATUS_IN_AIR)
        rolling = bool(status & STATUS_ROLLING)
        gm = 0 if air else angle_to_ground_mode(angle)

        # CSV row matching Lua format:
        #   %04X,%04X,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,
        #   %04X,%04X,%02X,%04X,%04X,%04X,%02X,%04X,%02X
        self._csv.write(
            f"{self.trace_frame:04X},{input_mask:04X},{x:04X},{y:04X},"
            f"{x_speed & 0xFFFF:04X},{y_speed & 0xFFFF:04X},{g_speed & 0xFFFF:04X},"
            f"{angle:02X},{1 if air else 0},{1 if rolling else 0},{gm},"
            f"{x_sub:04X},{y_sub:04X},{routine:02X},{camera_x:04X},{camera_y:04X},"
            f"{rings:04X},{status:02X},{v_framecount:04X},{stand_on_obj:02X}\n"
        )

        if self.trace_frame % 60 == 0:
            self._csv.flush()

        self._check_mode_changes(mem, status, routine)
        self.prev_status = status

        if self.trace_frame % SNAPSHOT_INTERVAL == 0:
            self._write_state_snapshot(mem)

        self._scan_objects(mem, x, y)
        self._check_opl(mem, v_framecount)

        self.trace_frame += 1

    # -----------------------------------------------------------------
    # Mode / routine change detection
    # -----------------------------------------------------------------

    def _check_mode_changes(self, mem, status, routine):
        vfc = mem.u16(ADDR_FRAMECOUNT)

        was_air = bool(self.prev_status & STATUS_IN_AIR)
        is_air = bool(status & STATUS_IN_AIR)
        if was_air != is_air:
            self._aux_event({
                "frame": self.trace_frame, "vfc": vfc,
                "event": "mode_change", "field": "air",
                "from": int(was_air), "to": int(is_air),
            })
            self._write_state_snapshot(mem)

        was_rolling = bool(self.prev_status & STATUS_ROLLING)
        is_rolling = bool(status & STATUS_ROLLING)
        if was_rolling != is_rolling:
            self._aux_event({
                "frame": self.trace_frame, "vfc": vfc,
                "event": "mode_change", "field": "rolling",
                "from": int(was_rolling), "to": int(is_rolling),
            })

        was_on_obj = bool(self.prev_status & STATUS_ON_OBJECT)
        is_on_obj = bool(status & STATUS_ON_OBJECT)
        if was_on_obj != is_on_obj:
            self._aux_event({
                "frame": self.trace_frame, "vfc": vfc,
                "event": "mode_change", "field": "on_object",
                "from": int(was_on_obj), "to": int(is_on_obj),
            })

        ctrl_lock = mem.u16(PLAYER_BASE + OFF_CTRL_LOCK)
        was_locked = self.prev_ctrl_lock > 0
        is_locked = ctrl_lock > 0
        if was_locked != is_locked:
            self._aux_event({
                "frame": self.trace_frame, "vfc": vfc,
                "event": "mode_change", "field": "control_locked",
                "from": int(was_locked), "to": int(is_locked),
            })
        self.prev_ctrl_lock = ctrl_lock

        if routine != self.prev_routine:
            self._emit_routine_change(mem, status, routine, vfc)
        self.prev_routine = routine

    def _emit_routine_change(self, mem, status, routine, vfc):
        stand_on_obj = mem.u8(PLAYER_BASE + OFF_STAND_ON_OBJ)
        sonic_x = mem.u16(PLAYER_BASE + OFF_X_POS)
        sonic_y = mem.u16(PLAYER_BASE + OFF_Y_POS)
        sonic_xvel = mem.s16(PLAYER_BASE + OFF_X_VEL)
        sonic_yvel = mem.s16(PLAYER_BASE + OFF_Y_VEL)
        sonic_inertia = mem.s16(PLAYER_BASE + OFF_INERTIA)

        event = {
            "frame": self.trace_frame, "vfc": vfc,
            "event": "routine_change",
            "from": f"0x{self.prev_routine:02X}",
            "to": f"0x{routine:02X}",
            "sonic_x": f"0x{sonic_x:04X}",
            "sonic_y": f"0x{sonic_y:04X}",
            "x_vel": sonic_xvel,
            "y_vel": sonic_yvel,
            "inertia": sonic_inertia,
            "status": f"0x{status:02X}",
            "stand_on_obj": stand_on_obj,
        }

        if 0 < stand_on_obj < OBJ_TOTAL_SLOTS:
            obj_addr = OBJ_TABLE_START + stand_on_obj * OBJ_SLOT_SIZE
            event["stand_obj_slot"] = stand_on_obj
            event["stand_obj_type"] = f"0x{mem.u8(obj_addr):02X}"
            event["stand_obj_x"] = f"0x{mem.u16(obj_addr + OFF_X_POS):04X}"
            event["stand_obj_y"] = f"0x{mem.u16(obj_addr + OFF_Y_POS):04X}"
            event["stand_obj_routine"] = f"0x{mem.u8(obj_addr + OFF_ROUTINE):02X}"

        self._aux_event(event)

        # On hurt/death transitions, emit a full state snapshot
        if routine in (0x04, 0x06):
            self._write_state_snapshot(mem)

    # -----------------------------------------------------------------
    # State snapshot
    # -----------------------------------------------------------------

    def _write_state_snapshot(self, mem):
        ctrl_lock = mem.u16(PLAYER_BASE + OFF_CTRL_LOCK)
        anim_id = mem.u8(PLAYER_BASE + OFF_ANIM_ID)
        status = mem.u8(PLAYER_BASE + OFF_STATUS)
        routine = mem.u8(PLAYER_BASE + OFF_ROUTINE)
        y_radius = mem.s8(PLAYER_BASE + OFF_RADIUS_Y)
        x_radius = mem.s8(PLAYER_BASE + OFF_RADIUS_X)
        vfc = mem.u16(ADDR_FRAMECOUNT)

        self._aux_event({
            "frame": self.trace_frame, "vfc": vfc,
            "event": "state_snapshot",
            "control_locked": ctrl_lock > 0,
            "anim_id": anim_id,
            "status_byte": f"0x{status:02X}",
            "routine": f"0x{routine:02X}",
            "y_radius": y_radius,
            "x_radius": x_radius,
            "on_object": bool(status & STATUS_ON_OBJECT),
            "pushing": bool(status & STATUS_PUSHING),
            "underwater": bool(status & STATUS_UNDERWATER),
            "roll_jumping": bool(status & STATUS_ROLL_JUMP),
        })

    # -----------------------------------------------------------------
    # Object scanning
    # -----------------------------------------------------------------

    def _scan_objects(self, mem, player_x, player_y):
        vfc = mem.u16(ADDR_FRAMECOUNT)
        any_appeared = False

        for slot in range(1, OBJ_TOTAL_SLOTS):
            addr = OBJ_TABLE_START + slot * OBJ_SLOT_SIZE
            obj_id = mem.u8(addr)
            prev_id = self.known_objects.get(slot, 0)

            # Object appeared
            if obj_id != 0 and obj_id != prev_id:
                obj_x = mem.u16(addr + OFF_X_POS)
                obj_y = mem.u16(addr + OFF_Y_POS)
                self._aux_event({
                    "frame": self.trace_frame, "vfc": vfc,
                    "event": "object_appeared", "slot": slot,
                    "object_type": f"0x{obj_id:02X}",
                    "x": f"0x{obj_x:04X}", "y": f"0x{obj_y:04X}",
                })
                any_appeared = True

            # Object removed
            if obj_id == 0 and prev_id != 0:
                self._aux_event({
                    "frame": self.trace_frame, "vfc": vfc,
                    "event": "object_removed", "slot": slot,
                    "object_type": f"0x{prev_id:02X}",
                })

            # Proximity check
            if obj_id != 0:
                obj_x = mem.u16(addr + OFF_X_POS)
                obj_y = mem.u16(addr + OFF_Y_POS)
                dx = abs(obj_x - player_x)
                dy = abs(obj_y - player_y)
                if dx <= OBJECT_PROXIMITY and dy <= OBJECT_PROXIMITY:
                    obj_status = mem.u8(addr + OFF_STATUS)
                    obj_routine = mem.u8(addr + OFF_ROUTINE)
                    self._aux_event({
                        "frame": self.trace_frame, "vfc": vfc,
                        "event": "object_near", "slot": slot,
                        "type": f"0x{obj_id:02X}",
                        "x": f"0x{obj_x:04X}", "y": f"0x{obj_y:04X}",
                        "routine": f"0x{obj_routine:02X}",
                        "status": f"0x{obj_status:02X}",
                    })

            self.known_objects[slot] = obj_id

        # Emit a full slot dump when any dynamic object appeared
        if any_appeared:
            slots = []
            for s in range(OBJ_DYNAMIC_START, OBJ_TOTAL_SLOTS):
                a = OBJ_TABLE_START + s * OBJ_SLOT_SIZE
                oid = mem.u8(a)
                if oid != 0:
                    slots.append([s, f"0x{oid:02X}"])
            self._aux_event({
                "frame": self.trace_frame, "vfc": vfc,
                "event": "slot_dump", "slots": slots,
            })

    # -----------------------------------------------------------------
    # OPL cursor state
    # -----------------------------------------------------------------

    def _check_opl(self, mem, v_framecount):
        opl_screen = mem.u16(ADDR_OPL_SCREEN)
        if opl_screen != self.prev_opl_screen:
            fwd_ptr = mem.u32(ADDR_OPL_DATA_FWD)
            bwd_ptr = mem.u32(ADDR_OPL_DATA_BWD)
            fwd_counter = mem.u8(ADDR_OBJSTATE)
            bwd_counter = mem.u8(ADDR_OBJSTATE + 1)
            direction = "R"
            if self.prev_opl_screen >= 0 and opl_screen < self.prev_opl_screen:
                direction = "L"
            self._aux_event({
                "frame": self.trace_frame, "vfc": v_framecount,
                "event": "cursor_state",
                "opl_screen": f"0x{opl_screen:04X}",
                "fwd_ptr": f"0x{fwd_ptr:08X}",
                "bwd_ptr": f"0x{bwd_ptr:08X}",
                "fwd_ctr": fwd_counter,
                "bwd_ctr": bwd_counter,
                "dir": direction,
            })
            self.prev_opl_screen = opl_screen


# =====================================================================
# Metadata writer
# =====================================================================

def write_metadata(path, *, game="s1", zone="unknown", zone_id=0, act=1,
                   bk2_frame_offset=0, trace_frame_count=0,
                   start_x=0, start_y=0, script_version="retro-1.0",
                   csv_version=4, extra_fields=None):
    """Write metadata.json matching the Lua format field order.

    Standard (main recorder) field order matches s1_trace_recorder.lua:
      game, zone, zone_id, act, bk2_frame_offset, trace_frame_count,
      start_x, start_y, recording_date, lua_script_version, csv_version,
      rom_checksum, notes

    Credits recorder inserts extra fields after game (trace_type,
    input_source, credits_demo_index, credits_demo_slug, emu_frame_start).
    """
    from collections import OrderedDict
    data = OrderedDict()
    data["game"] = game

    # Credits-specific fields go right after "game" (matching Lua order)
    if extra_fields:
        for k in ("trace_type", "input_source",
                  "credits_demo_index", "credits_demo_slug"):
            if k in extra_fields:
                data[k] = extra_fields[k]

    data["zone"] = zone
    data["zone_id"] = zone_id
    data["act"] = act
    data["bk2_frame_offset"] = bk2_frame_offset
    data["trace_frame_count"] = trace_frame_count
    data["start_x"] = f"0x{start_x:04X}"
    data["start_y"] = f"0x{start_y:04X}"

    if extra_fields and "emu_frame_start" in extra_fields:
        data["emu_frame_start"] = extra_fields["emu_frame_start"]

    data["recording_date"] = str(date.today())
    data["lua_script_version"] = script_version
    data["csv_version"] = csv_version
    data["rom_checksum"] = ""
    data["notes"] = ""

    # Any remaining extra fields not handled above
    if extra_fields:
        for k, v in extra_fields.items():
            if k not in data:
                data[k] = v

    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def write_credits_manifest(path, entries):
    """Write manifest.json for a credits demo collection."""
    data = {
        "game": "s1",
        "trace_type": "credits_demo_collection",
        "recording_date": str(date.today()),
        "entries": entries,
    }
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
