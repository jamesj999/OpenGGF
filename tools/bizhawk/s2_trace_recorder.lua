-- s2_trace_recorder.lua
-- BizHawk Lua script for recording Sonic 2 REV01 frame-by-frame physics
-- state during BK2 movie playback.
--
-- Usage:
--   1. Open BizHawk with Sonic 2 REV01 ROM
--   2. Load a BK2 movie file
--   3. Tools > Lua Console > load this script
--   4. Play the movie -- recording starts automatically when gameplay begins
--   5. Stop the movie or close the script to finalise output files
--
-- v2.0 changes: added subpixel, routine, camera, rings, status_byte columns
-- to physics.csv for faster divergence debugging. Object proximity tracking
-- logs nearby objects every frame instead of only new appearances every 4.
-- v2.1 changes: scan all 128 SST slots (was 63), emit slot_dump events on
-- object appearance for slot allocation comparison, add v_framecount to
-- physics.csv and aux events for ROM↔engine frame cross-referencing.
-- v2.2 changes: add standonobject (offset 0x3D) to physics.csv — which object
-- slot Sonic is riding on. Add routine_change events to aux with full Sonic
-- state + interacting object context (critical for hurt/bounce diagnosis).
-- v3.0-s2 changes: rename v_framecount to gameplay_frame_counter and add
-- vblank_counter plus lag_counter for counter-driven replay phase selection.
-- v4.0-s2 changes: emit per-slot object_state_snapshot events at frame -1
-- (pre-trace) so the engine can hydrate badnik/object state machines to
-- match what the ROM advanced during title-card/level-init iterations.
-- v5.0-s2 changes: append first-sidekick (Tails) state to each physics row so
-- replay can detect world-state drift caused by the sidekick before Sonic
-- diverges downstream.
-- v6.0-s2 changes: record explicit named character blocks for both Sonic and
-- Tails. Shared frame counters remain top-level, while per-character physics
-- fields become symmetric in the CSV.
-- v7.0-s2 changes: emit a pre-trace tails cpu_state_snapshot so replay can
-- hydrate the sidekick AI counters/state accumulated before frame 0.
-- v8.0-s2 changes: add character-scoped aux events and nearby-object scans
-- for both Sonic and Tails so replay debugging can see which character first
-- interacted with the world.
------------------------------------------------------------------------------

-----------------
--- Constants ---
-----------------

-- Output directory (relative to BizHawk working dir)
local OUTPUT_DIR = "trace_output/"

-- Headless mode: run at maximum speed, auto-exit when done.
-- Enable when running via CLI: EmuHawk.exe --chromeless --lua ... --movie ... rom.gen
local HEADLESS = true

-- Movie frame limit: set to 0 for automatic detection from movie.length().
-- When the BK2 movie ends but game_mode is still 0x0C (e.g. waiting for
-- results screen), the emulator would loop forever. This safety limit
-- ensures the script finalises and exits.
local MOVIE_FRAME_SAFETY_MARGIN = 30   -- frames past movie end before auto-exit

-- S2 REV01 68K RAM addresses (mainmemory domain = $FF0000 base stripped)
local ADDR_GAME_MODE       = 0xF600
local ADDR_CTRL1           = 0xF604   -- byte: Ctrl_1_Held (raw held input)
local ADDR_CTRL1_DUP       = 0xF602   -- byte: Ctrl_1_Held_Logical
local ADDR_RING_COUNT      = 0xFE20   -- word: Ring_count
local ADDR_CAMERA_X        = 0xEE00   -- long: Camera_X_pos
local ADDR_CAMERA_Y        = 0xEE04   -- long: Camera_Y_pos
local ADDR_ZONE            = 0xFE10   -- byte: Current_Zone
local ADDR_ACT             = 0xFE11   -- byte: Current_Act

-- Player object base ($FFFFB000 = MainCharacter)
local PLAYER_BASE          = 0xB000
local OFF_X_POS            = 0x08   -- word: centre X
local OFF_X_SUB            = 0x0A   -- word: X subpixel (16-bit fraction)
local OFF_Y_POS            = 0x0C   -- word: centre Y
local OFF_Y_SUB            = 0x0E   -- word: Y subpixel (16-bit fraction)
local OFF_X_VEL            = 0x10   -- signed word: X velocity
local OFF_Y_VEL            = 0x12   -- signed word: Y velocity
local OFF_INERTIA          = 0x14   -- signed word: ground speed
local OFF_RADIUS_Y         = 0x16   -- signed byte: Y radius (hitbox half-height)
local OFF_RADIUS_X         = 0x17   -- signed byte: X radius (hitbox half-width)
local OFF_ANIM_FRAME_DISP  = 0x1A   -- byte
local OFF_ANIM_FRAME       = 0x1B   -- byte
local OFF_ANIM_ID          = 0x1C   -- byte
local OFF_ANIM_TIMER       = 0x1E   -- byte
local OFF_STATUS           = 0x22   -- byte: status flags
local OFF_ROUTINE          = 0x24   -- byte: player movement routine
local OFF_ANGLE            = 0x26   -- byte: terrain angle
local OFF_STICK_CONVEX     = 0x38   -- byte
local OFF_STAND_ON_OBJ     = 0x3D   -- byte: interact — SST index Sonic stands on (0=none)
local OFF_CTRL_LOCK        = 0x2E   -- word: move_lock timer

-- S2 player routine values (obRoutine byte → table index):
--   0 = Obj01_Init
--   2 = Obj01_Control
--   4 = Obj01_Hurt
--   6 = Obj01_Dead
local ROUTINE_HURT         = 0x04
local ROUTINE_DEATH        = 0x06

-- Status flag bits
local STATUS_FACING_LEFT   = 0x01
local STATUS_IN_AIR        = 0x02
local STATUS_ROLLING       = 0x04
local STATUS_ON_OBJECT     = 0x08
local STATUS_ROLL_JUMP     = 0x10
local STATUS_PUSHING       = 0x20
local STATUS_UNDERWATER    = 0x40

-- ObjPosLoad cursor state (for ROM↔engine cursor comparison)
local ADDR_OPL_ROUTINE     = 0xF76C   -- byte: v_opl_routine (0=OPL_Main, 2=OPL_Next)
local ADDR_OPL_SCREEN      = 0xF76E   -- word: v_opl_screen (last processed camera chunk)
local ADDR_OPL_DATA_FWD    = 0xF770   -- long: v_opl_data (forward cursor ROM pointer)
local ADDR_OPL_DATA_BWD    = 0xF774   -- long: v_opl_data+4 (backward cursor ROM pointer)
local ADDR_OBJSTATE         = 0xFC00   -- byte[192]: v_objstate array (verified from ROM lea instruction)
-- v_objstate[0] = forward counter, v_objstate[1] = backward counter
local ADDR_SONIC_STAT_RECORD_BUF = 0xE400
local ADDR_SONIC_POS_RECORD_BUF  = 0xE500
local ADDR_SONIC_POS_RECORD_INDEX = 0xEED2
local ADDR_TAILS_CONTROL_COUNTER = 0xF702
local ADDR_TAILS_RESPAWN_COUNTER = 0xF704
local ADDR_TAILS_CPU_ROUTINE     = 0xF708
local ADDR_TAILS_CPU_TARGET_X    = 0xF70A
local ADDR_TAILS_CPU_TARGET_Y    = 0xF70C
local ADDR_TAILS_INTERACT_ID     = 0xF70E
local ADDR_TAILS_CPU_JUMPING     = 0xF70F

-- Object table (S2 SST: 128 slots of $40 bytes at $FFFFB000)
local OBJ_TABLE_START      = 0xB000
local OBJ_SLOT_SIZE        = 0x40
local OBJ_TOTAL_SLOTS      = 128  -- total SST slots (0-127)
local OBJ_DYNAMIC_START    = 16   -- first dynamic slot (Dynamic_Object_RAM)
local OBJ_DYNAMIC_COUNT    = 112  -- dynamic slots 16-127
local SIDEKICK_BASE        = OBJ_TABLE_START + OBJ_SLOT_SIZE  -- slot 1 = Tails/sidekick

-- Frame counter (v_framecount at $FFFE04, word — increments each Level_MainLoop)
-- NOTE: 0xFE0C is v_vbla_count (longword, VBlank interrupt counter — different!)
local ADDR_FRAMECOUNT      = 0xFE04
local ADDR_VBLA_WORD       = 0xFE0E

-- Genesis joypad bitmask (matching engine convention)
local INPUT_UP    = 0x01
local INPUT_DOWN  = 0x02
local INPUT_LEFT  = 0x04
local INPUT_RIGHT = 0x08
local INPUT_JUMP  = 0x10

-- Game mode values
local GAMEMODE_LEVEL = 0x0C

-- Zone ID to short name mapping (matches s2.constants.asm)
local ZONE_NAMES = {
    [0x00] = "ehz",
    [0x01] = "unknown_01",
    [0x02] = "wz",
    [0x03] = "unknown_03",
    [0x04] = "mtz",
    [0x05] = "mtz",
    [0x06] = "wfz",
    [0x07] = "htz",
    [0x08] = "hpz",
    [0x09] = "unknown_09",
    [0x0A] = "ooz",
    [0x0B] = "mcz",
    [0x0C] = "cnz",
    [0x0D] = "cpz",
    [0x0E] = "dez",
    [0x0F] = "arz",
    [0x10] = "scz",
}

-- Snapshot interval (frames between full state snapshots in aux file)
local SNAPSHOT_INTERVAL = 60

-- Object proximity radius (pixels) for per-frame nearby object logging
local OBJECT_PROXIMITY = 160

-----------------
--- State     ---
-----------------

local started = false
local finished = false   -- once true, never re-arm
local trace_frame = 0
local bk2_frame_offset = 0
local start_x = 0
local start_y = 0
local start_zone_id = 0
local start_zone_name = "unknown"
local start_act = 0

local prev_character_state = {
    sonic = { status = 0, routine = 0, ctrl_lock = 0 },
    tails = { status = 0, routine = 0, ctrl_lock = 0 },
}
local prev_opl_screen = -1  -- track OPL chunk transitions

-- Object tracking: slot -> last known type ID
local known_objects = {}

-- File handles
local physics_file = nil
local aux_file = nil

-----------------
--- Helpers   ---
-----------------

-- Read a 16-bit signed value (big-endian)
local function read_speed(base, offset)
    return mainmemory.read_s16_be(base + offset)
end

-- Convert raw ROM joypad byte (Ctrl_1_Held) to engine input bitmask.
-- ROM bits: 0=Up 1=Down 2=Left 3=Right 4=B 5=C 6=A 7=Start
-- Bits 0-3 already match INPUT_UP/DOWN/LEFT/RIGHT; collapse A/B/C to JUMP.
local function rom_joypad_to_mask(raw)
    local mask = raw & 0x0F                        -- directions (bits 0-3)
    if (raw & 0x70) ~= 0 then mask = mask + INPUT_JUMP end  -- A|B|C -> JUMP
    return mask
end

-- Format a number as hex with specified width
local function hex(val, width)
    width = width or 4
    if val < 0 then
        val = val + 0x10000
    end
    return string.format("%0" .. width .. "X", val)
end

-- Get ground mode from angle (offset quadrants matching ROM thresholds).
-- Floor wraps: 0xE0-0xFF and 0x00-0x1F are both mode 0.
local function angle_to_ground_mode(angle)
    if angle <= 0x1F or angle >= 0xE0 then return 0 end   -- floor
    if angle >= 0x20 and angle <= 0x5F then return 1 end   -- right wall
    if angle >= 0x60 and angle <= 0x9F then return 2 end   -- ceiling
    return 3                                                 -- left wall
end

-- Write a JSONL line to aux file
local function write_aux(json_str)
    if aux_file then
        aux_file:write(json_str .. "\n")
        aux_file:flush()
    end
end

-----------------
--- Recording ---
-----------------

local function open_files()
    physics_file = io.open(OUTPUT_DIR .. "physics.csv", "w")
    aux_file = io.open(OUTPUT_DIR .. "aux_state.jsonl", "w")

    -- v6 header: shared execution counters plus explicit Sonic/Tails state blocks.
    physics_file:write("frame,input,camera_x,camera_y,rings,gameplay_frame_counter,"
        .. "vblank_counter,lag_counter,sonic_present,sonic_x,sonic_y,sonic_x_speed,"
        .. "sonic_y_speed,sonic_g_speed,sonic_angle,sonic_air,sonic_rolling,"
        .. "sonic_ground_mode,sonic_x_sub,sonic_y_sub,sonic_routine,sonic_status_byte,"
        .. "sonic_stand_on_obj,tails_present,tails_x,tails_y,tails_x_speed,"
        .. "tails_y_speed,tails_g_speed,tails_angle,tails_air,tails_rolling,"
        .. "tails_ground_mode,tails_x_sub,tails_y_sub,tails_routine,"
        .. "tails_status_byte,tails_stand_on_obj\n")
    physics_file:flush()
end

local function write_metadata()
    -- Use zone/act captured at recording start (not current RAM which may have advanced)
    local meta_file = io.open(OUTPUT_DIR .. "metadata.json", "w")
    meta_file:write("{\n")
    meta_file:write('  "game": "s2",\n')
    meta_file:write('  "zone": "' .. start_zone_name .. '",\n')
    meta_file:write('  "zone_id": ' .. start_zone_id .. ',\n')
    meta_file:write('  "act": ' .. (start_act + 1) .. ',\n')
    meta_file:write('  "bk2_frame_offset": ' .. bk2_frame_offset .. ',\n')
    meta_file:write('  "trace_frame_count": ' .. trace_frame .. ',\n')
    meta_file:write('  "start_x": "0x' .. hex(start_x) .. '",\n')
    meta_file:write('  "start_y": "0x' .. hex(start_y) .. '",\n')
    meta_file:write('  "characters": ["sonic", "tails"],\n')
    meta_file:write('  "main_character": "sonic",\n')
    meta_file:write('  "sidekicks": ["tails"],\n')
    meta_file:write('  "recording_date": "' .. os.date("%Y-%m-%d") .. '",\n')
    meta_file:write('  "lua_script_version": "8.0-s2",\n')
    meta_file:write('  "trace_schema": 8,\n')
    meta_file:write('  "csv_version": 6,\n')
    meta_file:write('  "rom_checksum": "",\n')
    meta_file:write('  "notes": ""\n')
    meta_file:write("}\n")
    meta_file:close()
    print(string.format("Metadata written. Zone: %s Act %d, Trace frames: %d",
        start_zone_name, start_act + 1, trace_frame))
end

local function read_character_trace_state(base)
    local present = mainmemory.read_u8(base) ~= 0
    if not present then
        return {
            present = 0,
            x = 0,
            y = 0,
            x_speed = 0,
            y_speed = 0,
            g_speed = 0,
            angle = 0,
            air = 0,
            rolling = 0,
            ground_mode = 0,
            x_sub = 0,
            y_sub = 0,
            routine = 0,
            status = 0,
            stand_on_obj = 0,
        }
    end

    local status = mainmemory.read_u8(base + OFF_STATUS)
    local angle = mainmemory.read_u8(base + OFF_ANGLE)
    local air = (status & STATUS_IN_AIR) ~= 0
    local rolling = (status & STATUS_ROLLING) ~= 0

    return {
        present = 1,
        x = mainmemory.read_u16_be(base + OFF_X_POS),
        y = mainmemory.read_u16_be(base + OFF_Y_POS),
        x_speed = read_speed(base, OFF_X_VEL),
        y_speed = read_speed(base, OFF_Y_VEL),
        g_speed = read_speed(base, OFF_INERTIA),
        angle = angle,
        air = air and 1 or 0,
        rolling = rolling and 1 or 0,
        ground_mode = air and 0 or angle_to_ground_mode(angle),
        x_sub = mainmemory.read_u16_be(base + OFF_X_SUB),
        y_sub = mainmemory.read_u16_be(base + OFF_Y_SUB),
        routine = mainmemory.read_u8(base + OFF_ROUTINE),
        status = status,
        stand_on_obj = mainmemory.read_u8(base + OFF_STAND_ON_OBJ),
    }
end

local function close_files()
    if physics_file then
        physics_file:close()
        physics_file = nil
    end
    if aux_file then
        aux_file:close()
        aux_file = nil
    end
end

-- Build a compact summary of ALL occupied dynamic slots (16-127).
-- Returns a JSON array string: [[slot,typeId], ...] for each non-empty slot.
local function build_slot_dump()
    local entries = {}
    for slot = OBJ_DYNAMIC_START, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_id = mainmemory.read_u8(addr)
        if obj_id ~= 0 then
            entries[#entries + 1] = string.format("[%d,\"0x%02X\"]", slot, obj_id)
        end
    end
    return "[" .. table.concat(entries, ",") .. "]"
end

-- Dump the 64-byte SST slot at `addr` as a JSON object of byte fields,
-- keyed by raw offset ("off_00".."off_3F"), plus a handful of semantic
-- word aliases for readability. The engine side composes any word it
-- needs from the consecutive byte entries, so every per-object variable
-- at $2A-$3F is recoverable without per-object Lua knowledge.
local function build_object_fields(addr)
    local parts = {}
    -- Raw bytes 0x00..0x3F (64 bytes). The Java parser composes big-endian
    -- words on demand from consecutive byte offsets.
    for off = 0, OBJ_SLOT_SIZE - 1 do
        local val = mainmemory.read_u8(addr + off)
        parts[#parts + 1] = string.format('"off_%02X":"0x%02X"', off, val)
    end
    -- Semantic word aliases for the universal SST header (helps humans
    -- reading the aux file; also lets the engine skip byte composition
    -- for hot fields).
    parts[#parts + 1] = string.format('"x_pos":"0x%04X"',
        mainmemory.read_u16_be(addr + OFF_X_POS))
    parts[#parts + 1] = string.format('"x_sub":"0x%04X"',
        mainmemory.read_u16_be(addr + OFF_X_SUB))
    parts[#parts + 1] = string.format('"y_pos":"0x%04X"',
        mainmemory.read_u16_be(addr + OFF_Y_POS))
    parts[#parts + 1] = string.format('"y_sub":"0x%04X"',
        mainmemory.read_u16_be(addr + OFF_Y_SUB))
    local x_vel_raw = mainmemory.read_s16_be(addr + OFF_X_VEL)
    if x_vel_raw < 0 then x_vel_raw = x_vel_raw + 0x10000 end
    parts[#parts + 1] = string.format('"x_vel":"0x%04X"', x_vel_raw)
    local y_vel_raw = mainmemory.read_s16_be(addr + OFF_Y_VEL)
    if y_vel_raw < 0 then y_vel_raw = y_vel_raw + 0x10000 end
    parts[#parts + 1] = string.format('"y_vel":"0x%04X"', y_vel_raw)
    -- Semantic byte aliases (duplicate with off_XX but readable).
    parts[#parts + 1] = string.format('"id":"0x%02X"',
        mainmemory.read_u8(addr))
    parts[#parts + 1] = string.format('"render_flags":"0x%02X"',
        mainmemory.read_u8(addr + 0x01))
    parts[#parts + 1] = string.format('"status":"0x%02X"',
        mainmemory.read_u8(addr + OFF_STATUS))
    parts[#parts + 1] = string.format('"routine":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ROUTINE))
    parts[#parts + 1] = string.format('"routine_secondary":"0x%02X"',
        mainmemory.read_u8(addr + 0x25))
    parts[#parts + 1] = string.format('"mapping_frame":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ANIM_FRAME_DISP))
    parts[#parts + 1] = string.format('"anim":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ANIM_ID))
    parts[#parts + 1] = string.format('"anim_frame":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ANIM_FRAME))
    parts[#parts + 1] = string.format('"anim_frame_timer":"0x%02X"',
        mainmemory.read_u8(addr + OFF_ANIM_TIMER))
    parts[#parts + 1] = string.format('"subtype":"0x%02X"',
        mainmemory.read_u8(addr + 0x28))
    return "{" .. table.concat(parts, ",") .. "}"
end

-- Emit one object_state_snapshot event per occupied SST slot at
-- detection time (before trace frame 0). The engine uses these during
-- trace replay to hydrate spawned object state machines so they match
-- the ROM's pre-trace progress (e.g. Coconuts mid-climb).
local function write_object_snapshots()
    if not aux_file then return end
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local count = 0
    -- Scan slots 1-127. Skip 0 (Sonic) since the engine hydrates the main
    -- player from metadata.start_x/start_y directly. Slot 1 (Tails/sidekick)
    -- is included so replay can restore the sidekick's pre-trace SST state.
    for slot = 1, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_id = mainmemory.read_u8(addr)
        if obj_id ~= 0 then
            write_aux(string.format(
                '{"frame":-1,"vfc":%d,"event":"object_state_snapshot",'
                .. '"slot":%d,"object_type":"0x%02X","fields":%s}',
                vfc, slot, obj_id, build_object_fields(addr)))
            count = count + 1
        end
    end
    print(string.format("Wrote %d pre-trace object_state_snapshot events.", count))
end

local function write_player_history_snapshot()
    if not aux_file then return end
    local x_entries = {}
    local y_entries = {}
    local input_entries = {}
    local status_entries = {}
    for i = 0, 63 do
        local offset = i * 4
        x_entries[#x_entries + 1] = tostring(mainmemory.read_u16_be(ADDR_SONIC_POS_RECORD_BUF + offset))
        y_entries[#y_entries + 1] = tostring(mainmemory.read_u16_be(ADDR_SONIC_POS_RECORD_BUF + offset + 2))
        input_entries[#input_entries + 1] = tostring(mainmemory.read_u16_be(ADDR_SONIC_STAT_RECORD_BUF + offset))
        status_entries[#status_entries + 1] = tostring(mainmemory.read_u8(ADDR_SONIC_STAT_RECORD_BUF + offset + 2))
    end

    write_aux(string.format(
        '{"frame":-1,"vfc":%d,"event":"player_history_snapshot","history_pos":%d,'
            .. '"x_history":[%s],"y_history":[%s],"input_history":[%s],"status_history":[%s]}',
        mainmemory.read_u16_be(ADDR_FRAMECOUNT),
        mainmemory.read_u16_be(ADDR_SONIC_POS_RECORD_INDEX) & 0xFF,
        table.concat(x_entries, ","),
        table.concat(y_entries, ","),
        table.concat(input_entries, ","),
        table.concat(status_entries, ",")))
end

local function write_tails_cpu_snapshot()
    if not aux_file then return end

    write_aux(string.format(
        '{"frame":-1,"vfc":%d,"event":"cpu_state_snapshot","character":"tails",'
            .. '"control_counter":%d,"respawn_counter":%d,"cpu_routine":%d,'
            .. '"target_x":"0x%04X","target_y":"0x%04X","interact_id":"0x%02X","jumping":%d}',
        mainmemory.read_u16_be(ADDR_FRAMECOUNT),
        mainmemory.read_u16_be(ADDR_TAILS_CONTROL_COUNTER),
        mainmemory.read_u16_be(ADDR_TAILS_RESPAWN_COUNTER),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_ROUTINE),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_X),
        mainmemory.read_u16_be(ADDR_TAILS_CPU_TARGET_Y),
        mainmemory.read_u8(ADDR_TAILS_INTERACT_ID),
        mainmemory.read_u8(ADDR_TAILS_CPU_JUMPING)))
end

-- Scan all object slots (1-127). Log appearances, disappearances, proximity,
-- and emit a full slot_dump when any dynamic object appears.
local function scan_objects(subjects)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local any_appeared = false

    for slot = 1, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_id = mainmemory.read_u8(addr)

        local prev_id = known_objects[slot] or 0

        -- Object appeared in this slot
        if obj_id ~= 0 and obj_id ~= prev_id then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            write_aux(string.format(
                '{"frame":%d,"vfc":%d,"event":"object_appeared","slot":%d,"object_type":"0x%02X","x":"0x%04X","y":"0x%04X"}',
                trace_frame, vfc, slot, obj_id, obj_x, obj_y))
            any_appeared = true
        end

        -- Object disappeared from this slot
        if obj_id == 0 and prev_id ~= 0 then
            write_aux(string.format(
                '{"frame":%d,"vfc":%d,"event":"object_removed","slot":%d,"object_type":"0x%02X"}',
                trace_frame, vfc, slot, prev_id))
        end

        -- Proximity check: log active objects near Sonic and Tails every frame.
        -- Skip the subject's own SST slot so Tails doesn't spam near-self events.
        if obj_id ~= 0 then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            local obj_status = mainmemory.read_u8(addr + OFF_STATUS)
            local obj_routine = mainmemory.read_u8(addr + OFF_ROUTINE)
            for _, subject in ipairs(subjects) do
                if subject.present ~= 0 and slot ~= subject.slot then
                    local dx = math.abs(obj_x - subject.x)
                    local dy = math.abs(obj_y - subject.y)
                    if dx <= OBJECT_PROXIMITY and dy <= OBJECT_PROXIMITY then
                        write_aux(string.format(
                            '{"frame":%d,"vfc":%d,"event":"object_near","character":"%s","slot":%d,"type":"0x%02X",'
                            .. '"x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X"}',
                            trace_frame, vfc, subject.character, slot, obj_id, obj_x, obj_y,
                            obj_routine, obj_status))
                    end
                end
            end
        end

        known_objects[slot] = obj_id
    end

    -- Emit a full dynamic-slot snapshot whenever any object appeared this frame.
    -- This lets us compare the engine's slot allocation against ROM's FindFreeObj.
    if any_appeared then
        local dump = build_slot_dump()
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"slot_dump","slots":%s}',
            trace_frame, vfc, dump))
    end
end

local function write_state_snapshot(character, base)
    if mainmemory.read_u8(base) == 0 then
        return
    end

    local ctrl_lock = mainmemory.read_u16_be(base + OFF_CTRL_LOCK)
    local anim_id = mainmemory.read_u8(base + OFF_ANIM_ID)
    local status = mainmemory.read_u8(base + OFF_STATUS)
    local routine = mainmemory.read_u8(base + OFF_ROUTINE)
    local y_radius = mainmemory.read_s8(base + OFF_RADIUS_Y)
    local x_radius = mainmemory.read_s8(base + OFF_RADIUS_X)
    local raw_input = mainmemory.read_u8(ADDR_CTRL1)
    local logical_input = mainmemory.read_u8(ADDR_CTRL1_DUP)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"state_snapshot","character":"%s","control_locked":%s,"anim_id":%d,'
        .. '"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,'
        .. '"raw_input":"0x%02X","raw_input_mask":"0x%02X","logical_input":"0x%02X","logical_input_mask":"0x%02X",'
        .. '"on_object":%s,"pushing":%s,"underwater":%s,'
        .. '"roll_jumping":%s}',
        trace_frame,
        vfc,
        character,
        ctrl_lock > 0 and "true" or "false",
        anim_id,
        status,
        routine,
        y_radius,
        x_radius,
        raw_input,
        rom_joypad_to_mask(raw_input),
        logical_input,
        rom_joypad_to_mask(logical_input),
        ((status & STATUS_ON_OBJECT) ~= 0) and "true" or "false",
        ((status & STATUS_PUSHING) ~= 0) and "true" or "false",
        ((status & STATUS_UNDERWATER) ~= 0) and "true" or "false",
        ((status & STATUS_ROLL_JUMP) ~= 0) and "true" or "false"
    ))
end

local function check_mode_changes(character, base, state, status, routine)
    if mainmemory.read_u8(base) == 0 then
        state.status = 0
        state.routine = 0
        state.ctrl_lock = 0
        return
    end

    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    local was_air = (state.status & STATUS_IN_AIR) ~= 0
    local is_air = (status & STATUS_IN_AIR) ~= 0
    if was_air ~= is_air then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","character":"%s","field":"air","from":%d,"to":%d}',
            trace_frame, vfc, character, was_air and 1 or 0, is_air and 1 or 0))
        write_state_snapshot(character, base)
    end

    local was_rolling = (state.status & STATUS_ROLLING) ~= 0
    local is_rolling = (status & STATUS_ROLLING) ~= 0
    if was_rolling ~= is_rolling then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","character":"%s","field":"rolling","from":%d,"to":%d}',
            trace_frame, vfc, character, was_rolling and 1 or 0, is_rolling and 1 or 0))
    end

    local was_on_obj = (state.status & STATUS_ON_OBJECT) ~= 0
    local is_on_obj = (status & STATUS_ON_OBJECT) ~= 0
    if was_on_obj ~= is_on_obj then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","character":"%s","field":"on_object","from":%d,"to":%d}',
            trace_frame, vfc, character, was_on_obj and 1 or 0, is_on_obj and 1 or 0))
    end

    local ctrl_lock = mainmemory.read_u16_be(base + OFF_CTRL_LOCK)
    local was_locked = state.ctrl_lock > 0
    local is_locked = ctrl_lock > 0
    if was_locked ~= is_locked then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","character":"%s","field":"control_locked","from":%d,"to":%d}',
            trace_frame, vfc, character, was_locked and 1 or 0, is_locked and 1 or 0))
    end
    state.ctrl_lock = ctrl_lock

    -- Routine transition detection (S2 obRoutine raw values: 0=init, 2=control,
    -- 4=hurt, 6=death).
    -- Emit a rich event with full Sonic state and the object Sonic is standing on
    -- (if any). Especially valuable for hurt transitions (2→4).
    if routine ~= state.routine then
        local stand_on_obj = mainmemory.read_u8(base + OFF_STAND_ON_OBJ)
        local sonic_x = mainmemory.read_u16_be(base + OFF_X_POS)
        local sonic_y = mainmemory.read_u16_be(base + OFF_Y_POS)
        local sonic_xvel = mainmemory.read_s16_be(base + OFF_X_VEL)
        local sonic_yvel = mainmemory.read_s16_be(base + OFF_Y_VEL)
        local sonic_inertia = mainmemory.read_s16_be(base + OFF_INERTIA)

        -- If Sonic is standing on an object, read that object's type and position
        local obj_context = ""
        if stand_on_obj > 0 and stand_on_obj < OBJ_TOTAL_SLOTS then
            local obj_addr = OBJ_TABLE_START + (stand_on_obj * OBJ_SLOT_SIZE)
            local obj_id = mainmemory.read_u8(obj_addr)
            local obj_x = mainmemory.read_u16_be(obj_addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(obj_addr + OFF_Y_POS)
            local obj_routine = mainmemory.read_u8(obj_addr + OFF_ROUTINE)
            obj_context = string.format(
                ',"stand_obj_slot":%d,"stand_obj_type":"0x%02X","stand_obj_x":"0x%04X",'
                .. '"stand_obj_y":"0x%04X","stand_obj_routine":"0x%02X"',
                stand_on_obj, obj_id, obj_x, obj_y, obj_routine)
        end

        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"routine_change","character":"%s","from":"0x%02X","to":"0x%02X",'
            .. '"x":"0x%04X","y":"0x%04X","x_vel":%d,"y_vel":%d,"inertia":%d,'
            .. '"status":"0x%02X","stand_on_obj":%d%s}',
            trace_frame, vfc, character, state.routine, routine,
            sonic_x, sonic_y, sonic_xvel, sonic_yvel, sonic_inertia,
            status, stand_on_obj, obj_context))

        -- On hurt/death transitions, also emit a full state snapshot for maximum context.
        if routine == ROUTINE_HURT or routine == ROUTINE_DEATH then
            write_state_snapshot(character, base)
        end
    end
    state.routine = routine
    state.status = status
end

-----------------
--- Main Loop ---
-----------------

local function on_frame_end()
    local game_mode = mainmemory.read_u8(ADDR_GAME_MODE)

    if not started then
        if finished then return end
        -- Start when: level gameplay active AND player control lock timer is 0.
        -- The control lock timer (move_lock, word at MainCharacter+$2E) is set during the title
        -- card and counts down to 0 when Sonic can first move. Using the player object's
        -- lock timer is correct; the old raw-input check waited for "no buttons held"
        -- which delayed recording if the player was already pressing a direction.
        local ctrl_lock_timer = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
        if game_mode == GAMEMODE_LEVEL and ctrl_lock_timer == 0 then
            started = true
            -- emu.framecount() returns the frame that just completed. Since we
            -- skip the detection frame (return below without recording), frame 0
            -- is recorded one emu.frameadvance() later. BK2 input for that frame
            -- is at index emu.framecount() (not -1), because the advance runs
            -- one more frame before on_frame_end() captures frame 0.
            bk2_frame_offset = emu.framecount()
            start_x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
            start_y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)

            -- Capture zone/act NOW at start, not at end when RAM may have advanced
            start_zone_id = mainmemory.read_u8(ADDR_ZONE)
            start_act = mainmemory.read_u8(ADDR_ACT)
            start_zone_name = ZONE_NAMES[start_zone_id] or string.format("unknown_%02x", start_zone_id)

            open_files()
            -- Write metadata immediately so it exists even if the process is killed
            write_metadata()
            -- Schema v4: capture full SST state at the instant gameplay begins
            -- but before trace frame 0 is recorded. The engine hydrates object
            -- state machines from these snapshots so they mirror the ROM's
            -- pre-trace progress (title-card + level-init iterations).
            write_player_history_snapshot()
            write_tails_cpu_snapshot()
            write_object_snapshots()
            print(string.format("Trace recording started at BizHawk frame %d, zone %s act %d, pos (%04X, %04X)",
                bk2_frame_offset, start_zone_name, start_act + 1, start_x, start_y))
            if movie.isloaded() then
                print(string.format("Movie length: %d frames", movie.length()))
            end
        end
        -- Return without recording frame 0. The next emu.frameadvance() runs
        -- one frame of movement, and the NEXT on_frame_end() call writes
        -- frame 0 with post-movement state. This avoids a "dead frame"
        -- where input is present but speeds are 0 (ROM hasn't processed
        -- Sonic's movement yet on the frame where controls first unlock).
        return
    end

    if game_mode ~= GAMEMODE_LEVEL then
        print("Left level gameplay at trace frame " .. trace_frame .. ". Finalising.")
        finished = true
        return
    end

    -- Stop exactly when the trace would need an input frame past the end of
    -- the loaded BK2. BizHawk's movie mode can lag behind in chromeless runs,
    -- which lets the recorder append no-input tail frames that replay cannot
    -- consume later.
    if HEADLESS and movie.isloaded() then
        local movie_length = movie.length()
        if movie_length > 0 and (bk2_frame_offset + trace_frame) >= movie_length then
            print(string.format(
                "Reached BK2 end at trace frame %d (bk2 offset %d, movie length %d). Finalising.",
                trace_frame, bk2_frame_offset, movie_length))
            finished = true
            return
        end
        if movie.mode() == "FINISHED" then
            print(string.format(
                "Movie playback finished at trace frame %d (emu frame %d). Finalising.",
                trace_frame, emu.framecount()))
            finished = true
            return
        end
    end

    -- Primary physics state
    local x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
    local y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
    local x_sub = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_SUB)
    local y_sub = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_SUB)
    local x_speed = read_speed(PLAYER_BASE, OFF_X_VEL)
    local y_speed = read_speed(PLAYER_BASE, OFF_Y_VEL)
    local g_speed = read_speed(PLAYER_BASE, OFF_INERTIA)
    local angle = mainmemory.read_u8(PLAYER_BASE + OFF_ANGLE)
    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local routine = mainmemory.read_u8(PLAYER_BASE + OFF_ROUTINE)

    -- Camera position (pixel words from 32-bit values)
    local camera_x = mainmemory.read_u16_be(ADDR_CAMERA_X)
    local camera_y = mainmemory.read_u16_be(ADDR_CAMERA_Y)

    -- Ring count
    local rings = mainmemory.read_u16_be(ADDR_RING_COUNT)

    local air = (status & STATUS_IN_AIR) ~= 0
    local rolling = (status & STATUS_ROLLING) ~= 0
    local ground_mode = air and 0 or angle_to_ground_mode(angle)

    -- Read held input directly from RAM (works during movie playback;
    -- joypad.get() returns physical controller state which is zero in headless)
    local raw_input = mainmemory.read_u8(ADDR_CTRL1)
    local input_mask = rom_joypad_to_mask(raw_input)

    -- Format helper for unsigned 16-bit hex
    local function uhex(val)
        if val < 0 then return val + 0x10000 end
        return val
    end

    -- gameplay_frame_counter ticks only when Level_MainLoop completes.
    local gameplay_frame_counter = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    -- standonobject: SST slot index of object Sonic is standing on (0 = none)
    local stand_on_obj = mainmemory.read_u8(PLAYER_BASE + OFF_STAND_ON_OBJ)

    -- vblank_counter ticks every VBlank. Sonic 2 does not expose a dedicated
    -- lag counter, so write 0 as a diagnostic placeholder in schema v3.
    local vblank_counter = mainmemory.read_u16_be(ADDR_VBLA_WORD)
    local lag_counter = 0
    local sidekick = read_character_trace_state(SIDEKICK_BASE)

    -- v6 CSV: shared execution counters plus explicit Sonic/Tails state blocks.
    physics_file:write(string.format(
        "%04X,%04X,%04X,%04X,%04X,%04X,%04X,%04X,%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X,"
            .. "%d,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%02X,%02X\n",
        trace_frame, input_mask,
        camera_x, camera_y,
        rings,
        gameplay_frame_counter,
        vblank_counter,
        lag_counter,
        1,
        x,
        y,
        uhex(x_speed),
        uhex(y_speed),
        uhex(g_speed),
        angle,
        air and 1 or 0,
        rolling and 1 or 0,
        ground_mode,
        x_sub,
        y_sub,
        routine,
        status,
        stand_on_obj,
        sidekick.present,
        sidekick.x,
        sidekick.y,
        uhex(sidekick.x_speed),
        uhex(sidekick.y_speed),
        uhex(sidekick.g_speed),
        sidekick.angle,
        sidekick.air,
        sidekick.rolling,
        sidekick.ground_mode,
        sidekick.x_sub,
        sidekick.y_sub,
        sidekick.routine,
        sidekick.status,
        sidekick.stand_on_obj))
    -- Flush periodically instead of every frame to reduce I/O overhead.
    -- Also update metadata every 300 frames (~5 sec) so a killed process
    -- still has a valid (if slightly stale) metadata.json.
    if trace_frame % 60 == 0 then
        physics_file:flush()
    end
    if trace_frame % 300 == 0 then
        write_metadata()
    end

    check_mode_changes("sonic", PLAYER_BASE, prev_character_state.sonic, status, routine)
    check_mode_changes("tails", SIDEKICK_BASE, prev_character_state.tails,
        sidekick.status, sidekick.routine)

    if trace_frame % SNAPSHOT_INTERVAL == 0
            or (trace_frame >= 5104 and trace_frame <= 5106) then
        write_state_snapshot("sonic", PLAYER_BASE)
        write_state_snapshot("tails", SIDEKICK_BASE)
    end

    -- Object scanning: every frame for proximity, every 4 frames for full scan
    -- Proximity logging runs every frame so we never miss collision-relevant objects.
    scan_objects({
        { character = "sonic", slot = 0, present = 1, x = x, y = y },
        { character = "tails", slot = 1, present = sidekick.present, x = sidekick.x, y = sidekick.y },
    })

    -- OPL cursor state: emit event on chunk transitions for ROM↔engine comparison.
    -- v_opl_screen changes only when OPL_Next processes a new chunk.
    local opl_screen = mainmemory.read_u16_be(ADDR_OPL_SCREEN)
    if opl_screen ~= prev_opl_screen then
        local fwd_ptr = mainmemory.read_u32_be(ADDR_OPL_DATA_FWD)
        local bwd_ptr = mainmemory.read_u32_be(ADDR_OPL_DATA_BWD)
        local fwd_counter = mainmemory.read_u8(ADDR_OBJSTATE)
        local bwd_counter = mainmemory.read_u8(ADDR_OBJSTATE + 1)
        local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
        local dir = "R"
        if prev_opl_screen >= 0 and opl_screen < prev_opl_screen then
            dir = "L"
        end
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"cursor_state","opl_screen":"0x%04X",'
            .. '"fwd_ptr":"0x%08X","bwd_ptr":"0x%08X","fwd_ctr":%d,"bwd_ctr":%d,"dir":"%s"}',
            trace_frame, vfc, opl_screen, fwd_ptr, bwd_ptr, fwd_counter, bwd_counter, dir))
        prev_opl_screen = opl_screen
    end

    trace_frame = trace_frame + 1
end

-- Create output directory at load time (avoids cmd.exe pause during gameplay)
os.execute("mkdir \"" .. OUTPUT_DIR .. "\" 2>NUL")

-- Run at maximum speed in headless mode.
-- emu.limitframerate(false) removes the 60fps cap.
-- client.speedmode(6400) sets emulator speed to 6400% as backup.
-- invisibleemulation(true) skips rendering for additional speedup.
-- Set HEADLESS_VISIBLE = true to keep the window visible for progress feedback.
local HEADLESS_VISIBLE = false
if HEADLESS then
    emu.limitframerate(false)
    client.speedmode(6400)
    if not HEADLESS_VISIBLE then
        client.invisibleemulation(true)
    end
end

-- Main loop using explicit frame-advance.
-- This pattern keeps the script in control of the event loop so we can:
--   1. Detect movie-end pauses (BizHawk pauses when a movie finishes)
--   2. Cleanly flush and close all files BEFORE calling client.exit()
-- The onframeend callback pattern doesn't work because callbacks stop
-- firing when BizHawk pauses, and client.exit() can kill the process
-- before file I/O completes.
print("S2 Trace Recorder v3.0-s2 loaded. Waiting for level gameplay (Game_Mode=0x0C, controls unlocked)...")

while true do
    on_frame_end()

    -- If recording is done, finalise files and exit from INSIDE the loop.
    -- Code after the loop may never execute because client.exit() kills
    -- the process immediately.
    if finished then
        print("Recording complete. Writing final output...")
        if physics_file then physics_file:flush() end
        write_metadata()
        close_files()
        print(string.format("Trace finalised: %s act %d, %d frames.",
            start_zone_name, start_act + 1, trace_frame))
        if HEADLESS then
            client.exit()
        end
        break
    end

    -- If paused (e.g. BizHawk pauses on movie end), unpause so we get
    -- another iteration to detect the FINISHED state and exit cleanly.
    if client.ispaused() then
        client.unpause()
    end

    emu.frameadvance()
end
