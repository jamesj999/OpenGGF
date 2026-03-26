------------------------------------------------------------------------------
-- s1_trace_recorder.lua
-- BizHawk Lua script for recording Sonic 1 REV01 frame-by-frame physics
-- state during BK2 movie playback.
--
-- Usage:
--   1. Open BizHawk with Sonic 1 REV01 ROM
--   2. Load a BK2 movie file
--   3. Tools > Lua Console > load this script
--   4. Play the movie -- recording starts automatically when gameplay begins
--   5. Stop the movie or close the script to finalise output files
------------------------------------------------------------------------------

-----------------
--- Constants ---
-----------------

-- Output directory (relative to BizHawk working dir)
local OUTPUT_DIR = "trace_output/"

-- S1 REV01 68K RAM addresses (mainmemory domain = $FF0000 base stripped)
local ADDR_GAME_MODE       = 0xF600
local ADDR_CTRL1_LOCKED    = 0xF604
local ADDR_CTRL1           = 0xF602

-- Player object base ($FFD000)
local PLAYER_BASE          = 0xD000
local OFF_X_POS            = 0x08   -- word: centre X
local OFF_X_SUB            = 0x0A   -- byte: X subpixel
local OFF_Y_POS            = 0x0C   -- word: centre Y
local OFF_Y_SUB            = 0x0E   -- byte: Y subpixel
local OFF_X_VEL            = 0x10   -- signed byte + subpixel byte
local OFF_Y_VEL            = 0x12   -- signed byte + subpixel byte
local OFF_INERTIA          = 0x14   -- signed byte + subpixel byte (ground speed)
local OFF_RADIUS_Y         = 0x16   -- signed byte
local OFF_RADIUS_X         = 0x17   -- signed byte
local OFF_ANIM_FRAME_DISP  = 0x1A   -- byte
local OFF_ANIM_FRAME       = 0x1B   -- byte
local OFF_ANIM_ID          = 0x1C   -- byte
local OFF_ANIM_TIMER       = 0x1E   -- byte
local OFF_STATUS           = 0x22   -- byte: status flags
local OFF_ANGLE            = 0x26   -- byte: terrain angle
local OFF_STICK_CONVEX     = 0x38   -- byte
local OFF_CTRL_LOCK        = 0x3E   -- word: control lock timer

-- Status flag bits
local STATUS_FACING_LEFT   = 0x01
local STATUS_IN_AIR        = 0x02
local STATUS_ROLLING       = 0x04
local STATUS_ON_OBJECT     = 0x08
local STATUS_ROLL_JUMP     = 0x10
local STATUS_PUSHING       = 0x20
local STATUS_UNDERWATER    = 0x40

-- Object table
local OBJ_TABLE_START      = 0xD000
local OBJ_SLOT_SIZE        = 0x40

-- Genesis joypad bitmask (matching engine convention)
local INPUT_UP    = 0x01
local INPUT_DOWN  = 0x02
local INPUT_LEFT  = 0x04
local INPUT_RIGHT = 0x08
local INPUT_JUMP  = 0x10

-- Game mode values
local GAMEMODE_LEVEL = 0x0C

-- Snapshot interval (frames between full state snapshots in aux file)
local SNAPSHOT_INTERVAL = 60

-----------------
--- State     ---
-----------------

local started = false
local trace_frame = 0
local bk2_frame_offset = 0
local start_x = 0
local start_y = 0

local prev_status = 0
local prev_ctrl_lock = 0

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

-- Convert joypad.get() table to engine input bitmask
local function joypad_to_mask(joy)
    local mask = 0
    if joy["Up"]    then mask = mask + INPUT_UP end
    if joy["Down"]  then mask = mask + INPUT_DOWN end
    if joy["Left"]  then mask = mask + INPUT_LEFT end
    if joy["Right"] then mask = mask + INPUT_RIGHT end
    if joy["A"] or joy["B"] or joy["C"] then mask = mask + INPUT_JUMP end
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

-- Get ground mode from angle (derived from angle quadrant)
local function angle_to_ground_mode(angle)
    if angle >= 0x00 and angle <= 0x3F then return 0 end
    if angle >= 0x40 and angle <= 0x7F then return 1 end
    if angle >= 0x80 and angle <= 0xBF then return 2 end
    return 3
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
    os.execute("mkdir \"" .. OUTPUT_DIR .. "\" 2>NUL")

    physics_file = io.open(OUTPUT_DIR .. "physics.csv", "w")
    aux_file = io.open(OUTPUT_DIR .. "aux_state.jsonl", "w")

    physics_file:write("frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode\n")
    physics_file:flush()
end

local function write_metadata()
    local meta_file = io.open(OUTPUT_DIR .. "metadata.json", "w")
    meta_file:write("{\n")
    meta_file:write('  "game": "s1",\n')
    meta_file:write('  "zone": "ghz",\n')
    meta_file:write('  "act": 1,\n')
    meta_file:write('  "bk2_frame_offset": ' .. bk2_frame_offset .. ',\n')
    meta_file:write('  "trace_frame_count": ' .. trace_frame .. ',\n')
    meta_file:write('  "start_x": "0x' .. hex(start_x) .. '",\n')
    meta_file:write('  "start_y": "0x' .. hex(start_y) .. '",\n')
    meta_file:write('  "recording_date": "' .. os.date("%Y-%m-%d") .. '",\n')
    meta_file:write('  "lua_script_version": "1.0",\n')
    meta_file:write('  "rom_checksum": "",\n')
    meta_file:write('  "notes": ""\n')
    meta_file:write("}\n")
    meta_file:close()
    print("Metadata written. Trace frames: " .. trace_frame)
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

local function scan_objects()
    for slot = 1, 63 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_id = mainmemory.read_u8(addr)

        local prev_id = known_objects[slot] or 0
        if obj_id ~= 0 and obj_id ~= prev_id then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            write_aux(string.format(
                '{"frame":%d,"event":"object_appeared","object_type":"0x%02X","x":"0x%04X","y":"0x%04X"}',
                trace_frame, obj_id, obj_x, obj_y))
        end
        known_objects[slot] = obj_id
    end
end

local function write_state_snapshot()
    local ctrl_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local anim_id = mainmemory.read_u8(PLAYER_BASE + OFF_ANIM_ID)
    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)

    write_aux(string.format(
        '{"frame":%d,"event":"state_snapshot","control_locked":%s,"anim_id":%d,'
        .. '"status_byte":"0x%02X","on_object":%s,"pushing":%s,"underwater":%s,'
        .. '"roll_jumping":%s}',
        trace_frame,
        ctrl_lock > 0 and "true" or "false",
        anim_id,
        status,
        (bit.band(status, STATUS_ON_OBJECT) ~= 0) and "true" or "false",
        (bit.band(status, STATUS_PUSHING) ~= 0) and "true" or "false",
        (bit.band(status, STATUS_UNDERWATER) ~= 0) and "true" or "false",
        (bit.band(status, STATUS_ROLL_JUMP) ~= 0) and "true" or "false"
    ))
end

local function check_mode_changes(status)
    local was_air = bit.band(prev_status, STATUS_IN_AIR) ~= 0
    local is_air = bit.band(status, STATUS_IN_AIR) ~= 0
    if was_air ~= is_air then
        write_aux(string.format(
            '{"frame":%d,"event":"mode_change","field":"air","from":%d,"to":%d}',
            trace_frame, was_air and 1 or 0, is_air and 1 or 0))
        write_state_snapshot()
    end

    local was_rolling = bit.band(prev_status, STATUS_ROLLING) ~= 0
    local is_rolling = bit.band(status, STATUS_ROLLING) ~= 0
    if was_rolling ~= is_rolling then
        write_aux(string.format(
            '{"frame":%d,"event":"mode_change","field":"rolling","from":%d,"to":%d}',
            trace_frame, was_rolling and 1 or 0, is_rolling and 1 or 0))
    end

    local ctrl_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local was_locked = prev_ctrl_lock > 0
    local is_locked = ctrl_lock > 0
    if was_locked ~= is_locked then
        write_aux(string.format(
            '{"frame":%d,"event":"mode_change","field":"control_locked","from":%d,"to":%d}',
            trace_frame, was_locked and 1 or 0, is_locked and 1 or 0))
    end
    prev_ctrl_lock = ctrl_lock
end

-----------------
--- Main Loop ---
-----------------

local function on_frame_end()
    local game_mode = mainmemory.read_u8(ADDR_GAME_MODE)
    local ctrl_locked = mainmemory.read_u8(ADDR_CTRL1_LOCKED)

    if not started then
        if game_mode == GAMEMODE_LEVEL and ctrl_locked == 0 then
            started = true
            bk2_frame_offset = emu.framecount()
            start_x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
            start_y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)

            open_files()
            print(string.format("Trace recording started at BizHawk frame %d, pos (%04X, %04X)",
                bk2_frame_offset, start_x, start_y))
        end
        return
    end

    if game_mode ~= GAMEMODE_LEVEL then
        print("Left level gameplay at trace frame " .. trace_frame .. ". Finalising.")
        write_metadata()
        close_files()
        started = false
        return
    end

    local x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
    local y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
    local x_speed = read_speed(PLAYER_BASE, OFF_X_VEL)
    local y_speed = read_speed(PLAYER_BASE, OFF_Y_VEL)
    local g_speed = read_speed(PLAYER_BASE, OFF_INERTIA)
    local angle = mainmemory.read_u8(PLAYER_BASE + OFF_ANGLE)
    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)

    local air = bit.band(status, STATUS_IN_AIR) ~= 0
    local rolling = bit.band(status, STATUS_ROLLING) ~= 0
    local ground_mode = air and 0 or angle_to_ground_mode(angle)

    local joy = joypad.get(1)
    local input_mask = joypad_to_mask(joy)

    physics_file:write(string.format("%04X,%04X,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d\n",
        trace_frame, input_mask, x, y,
        x_speed < 0 and (x_speed + 0x10000) or x_speed,
        y_speed < 0 and (y_speed + 0x10000) or y_speed,
        g_speed < 0 and (g_speed + 0x10000) or g_speed,
        angle,
        air and 1 or 0,
        rolling and 1 or 0,
        ground_mode))
    physics_file:flush()

    check_mode_changes(status)
    prev_status = status

    if trace_frame % SNAPSHOT_INTERVAL == 0 then
        write_state_snapshot()
    end

    scan_objects()

    trace_frame = trace_frame + 1
end

event.onframeend(on_frame_end, "S1TraceRecorder")
print("S1 Trace Recorder loaded. Waiting for level gameplay to begin...")

event.onexit(function()
    if started then
        write_metadata()
        close_files()
        print("Script exiting. Trace finalised at frame " .. trace_frame)
    end
end, "S1TraceRecorderExit")
