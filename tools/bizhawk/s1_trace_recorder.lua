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
--
-- v2.0 changes: added subpixel, routine, camera, rings, status_byte columns
-- to physics.csv for faster divergence debugging. Object proximity tracking
-- logs nearby objects every frame instead of only new appearances every 4.
------------------------------------------------------------------------------

-----------------
--- Constants ---
-----------------

-- Output directory (relative to BizHawk working dir)
local OUTPUT_DIR = "trace_output/"

-- Headless mode: skip rendering for maximum speed, auto-exit when done.
-- Enable when running via CLI: EmuHawk.exe --chromeless --lua ... --movie ... rom.gen
local HEADLESS = true

-- S1 REV01 68K RAM addresses (mainmemory domain = $FF0000 base stripped)
local ADDR_GAME_MODE       = 0xF600
local ADDR_CTRL1_LOCKED    = 0xF604
local ADDR_CTRL1           = 0xF602
local ADDR_RING_COUNT      = 0xFE20   -- word: ring count (BCD)
local ADDR_CAMERA_X        = 0xF700   -- long: v_screenposx (camera X pixel:sub)
local ADDR_CAMERA_Y        = 0xF704   -- long: v_screenposy (camera Y pixel:sub)
local ADDR_ZONE            = 0xFE10   -- byte: current zone number (v_zone)
local ADDR_ACT             = 0xFE11   -- byte: current act number (v_act)

-- Player object base ($FFD000)
local PLAYER_BASE          = 0xD000
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
local OFF_CTRL_LOCK        = 0x3E   -- word: control lock timer

-- Player routine values (obRoutine / 2):
--   0 = init, 1 = normal/ground, 2 = air/jump, 3 = roll, 4 = hurt, 5 = death
-- The raw byte is the actual obRoutine value (0,2,4,6,8,0A).

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
local OBJ_SLOT_COUNT       = 63

-- Genesis joypad bitmask (matching engine convention)
local INPUT_UP    = 0x01
local INPUT_DOWN  = 0x02
local INPUT_LEFT  = 0x04
local INPUT_RIGHT = 0x08
local INPUT_JUMP  = 0x10

-- Game mode values
local GAMEMODE_LEVEL = 0x0C

-- Zone ID to short name mapping (matches s1disasm Constants.asm)
local ZONE_NAMES = {
    [0] = "ghz",   -- Green Hill Zone
    [1] = "lz",    -- Labyrinth Zone
    [2] = "mz",    -- Marble Zone
    [3] = "slz",   -- Star Light Zone
    [4] = "syz",   -- Spring Yard Zone
    [5] = "sbz",   -- Scrap Brain Zone
    [6] = "endz",  -- Ending Zone
    [7] = "ss",    -- Special Stage
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

    -- v2.0 header: original fields + subpixel, routine, camera, rings, status_byte
    physics_file:write("frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,"
        .. "x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte\n")
    physics_file:flush()
end

local function write_metadata()
    local zone_id = mainmemory.read_u8(ADDR_ZONE)
    local act_id = mainmemory.read_u8(ADDR_ACT)
    local zone_name = ZONE_NAMES[zone_id] or string.format("unknown_%02x", zone_id)

    local meta_file = io.open(OUTPUT_DIR .. "metadata.json", "w")
    meta_file:write("{\n")
    meta_file:write('  "game": "s1",\n')
    meta_file:write('  "zone": "' .. zone_name .. '",\n')
    meta_file:write('  "zone_id": ' .. zone_id .. ',\n')
    meta_file:write('  "act": ' .. (act_id + 1) .. ',\n')
    meta_file:write('  "bk2_frame_offset": ' .. bk2_frame_offset .. ',\n')
    meta_file:write('  "trace_frame_count": ' .. trace_frame .. ',\n')
    meta_file:write('  "start_x": "0x' .. hex(start_x) .. '",\n')
    meta_file:write('  "start_y": "0x' .. hex(start_y) .. '",\n')
    meta_file:write('  "recording_date": "' .. os.date("%Y-%m-%d") .. '",\n')
    meta_file:write('  "lua_script_version": "2.0",\n')
    meta_file:write('  "csv_version": 2,\n')
    meta_file:write('  "rom_checksum": "",\n')
    meta_file:write('  "notes": ""\n')
    meta_file:write("}\n")
    meta_file:close()
    print(string.format("Metadata written. Zone: %s Act %d, Trace frames: %d",
        zone_name, act_id + 1, trace_frame))
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

-- Scan all object slots. Log appearances, disappearances, and proximity to player.
local function scan_objects(player_x, player_y)
    for slot = 1, OBJ_SLOT_COUNT do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_id = mainmemory.read_u8(addr)

        local prev_id = known_objects[slot] or 0

        -- Object appeared in this slot
        if obj_id ~= 0 and obj_id ~= prev_id then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            write_aux(string.format(
                '{"frame":%d,"event":"object_appeared","slot":%d,"object_type":"0x%02X","x":"0x%04X","y":"0x%04X"}',
                trace_frame, slot, obj_id, obj_x, obj_y))
        end

        -- Object disappeared from this slot
        if obj_id == 0 and prev_id ~= 0 then
            write_aux(string.format(
                '{"frame":%d,"event":"object_removed","slot":%d,"object_type":"0x%02X"}',
                trace_frame, slot, prev_id))
        end

        -- Proximity check: log active objects near the player every frame.
        -- This captures the exact position of objects involved in collisions
        -- without needing to add temporary diagnostic code to the engine.
        if obj_id ~= 0 then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            local dx = math.abs(obj_x - player_x)
            local dy = math.abs(obj_y - player_y)
            if dx <= OBJECT_PROXIMITY and dy <= OBJECT_PROXIMITY then
                local obj_status = mainmemory.read_u8(addr + OFF_STATUS)
                local obj_routine = mainmemory.read_u8(addr + OFF_ROUTINE)
                write_aux(string.format(
                    '{"frame":%d,"event":"object_near","slot":%d,"type":"0x%02X",'
                    .. '"x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X"}',
                    trace_frame, slot, obj_id, obj_x, obj_y, obj_routine, obj_status))
            end
        end

        known_objects[slot] = obj_id
    end
end

local function write_state_snapshot()
    local ctrl_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local anim_id = mainmemory.read_u8(PLAYER_BASE + OFF_ANIM_ID)
    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local routine = mainmemory.read_u8(PLAYER_BASE + OFF_ROUTINE)
    local y_radius = mainmemory.read_s8(PLAYER_BASE + OFF_RADIUS_Y)
    local x_radius = mainmemory.read_s8(PLAYER_BASE + OFF_RADIUS_X)

    write_aux(string.format(
        '{"frame":%d,"event":"state_snapshot","control_locked":%s,"anim_id":%d,'
        .. '"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,'
        .. '"on_object":%s,"pushing":%s,"underwater":%s,'
        .. '"roll_jumping":%s}',
        trace_frame,
        ctrl_lock > 0 and "true" or "false",
        anim_id,
        status,
        routine,
        y_radius,
        x_radius,
        ((status & STATUS_ON_OBJECT) ~= 0) and "true" or "false",
        ((status & STATUS_PUSHING) ~= 0) and "true" or "false",
        ((status & STATUS_UNDERWATER) ~= 0) and "true" or "false",
        ((status & STATUS_ROLL_JUMP) ~= 0) and "true" or "false"
    ))
end

local function check_mode_changes(status)
    local was_air = (prev_status & STATUS_IN_AIR) ~= 0
    local is_air = (status & STATUS_IN_AIR) ~= 0
    if was_air ~= is_air then
        write_aux(string.format(
            '{"frame":%d,"event":"mode_change","field":"air","from":%d,"to":%d}',
            trace_frame, was_air and 1 or 0, is_air and 1 or 0))
        write_state_snapshot()
    end

    local was_rolling = (prev_status & STATUS_ROLLING) ~= 0
    local is_rolling = (status & STATUS_ROLLING) ~= 0
    if was_rolling ~= is_rolling then
        write_aux(string.format(
            '{"frame":%d,"event":"mode_change","field":"rolling","from":%d,"to":%d}',
            trace_frame, was_rolling and 1 or 0, is_rolling and 1 or 0))
    end

    local was_on_obj = (prev_status & STATUS_ON_OBJECT) ~= 0
    local is_on_obj = (status & STATUS_ON_OBJECT) ~= 0
    if was_on_obj ~= is_on_obj then
        write_aux(string.format(
            '{"frame":%d,"event":"mode_change","field":"on_object","from":%d,"to":%d}',
            trace_frame, was_on_obj and 1 or 0, is_on_obj and 1 or 0))
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
        if finished then return end
        if game_mode == GAMEMODE_LEVEL and ctrl_locked == 0 then
            started = true
            bk2_frame_offset = emu.framecount()
            start_x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
            start_y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)

            open_files()
            local zone_id = mainmemory.read_u8(ADDR_ZONE)
            local act_id = mainmemory.read_u8(ADDR_ACT)
            local zone_name = ZONE_NAMES[zone_id] or string.format("unknown_%02x", zone_id)
            print(string.format("Trace recording started at BizHawk frame %d, zone %s act %d, pos (%04X, %04X)",
                bk2_frame_offset, zone_name, act_id + 1, start_x, start_y))
        end
        return
    end

    if game_mode ~= GAMEMODE_LEVEL then
        print("Left level gameplay at trace frame " .. trace_frame .. ". Finalising.")
        write_metadata()
        close_files()
        started = false
        finished = true
        if HEADLESS then
            print("Headless mode: exiting.")
            client.exit()
        end
        return
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

    local joy = joypad.get(1)
    local input_mask = joypad_to_mask(joy)

    -- Format helper for unsigned 16-bit hex
    local function uhex(val)
        if val < 0 then return val + 0x10000 end
        return val
    end

    -- v2.0 CSV: original 11 fields + 6 new diagnostic fields
    physics_file:write(string.format(
        "%04X,%04X,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%04X,%04X,%04X,%02X\n",
        trace_frame, input_mask, x, y,
        uhex(x_speed), uhex(y_speed), uhex(g_speed),
        angle,
        air and 1 or 0,
        rolling and 1 or 0,
        ground_mode,
        x_sub, y_sub,
        routine,
        camera_x, camera_y,
        rings,
        status))
    -- Flush periodically instead of every frame to reduce I/O overhead
    if trace_frame % 60 == 0 then
        physics_file:flush()
    end

    check_mode_changes(status)
    prev_status = status

    if trace_frame % SNAPSHOT_INTERVAL == 0 then
        write_state_snapshot()
    end

    -- Object scanning: every frame for proximity, every 4 frames for full scan
    -- Proximity logging runs every frame so we never miss collision-relevant objects.
    scan_objects(x, y)

    trace_frame = trace_frame + 1
end

-- Create output directory at load time (avoids cmd.exe pause during gameplay)
os.execute("mkdir \"" .. OUTPUT_DIR .. "\" 2>NUL")

-- Skip rendering for max speed in headless mode
if HEADLESS then
    client.invisibleemulation(true)
end

event.onframeend(on_frame_end, "S1TraceRecorder")
print("S1 Trace Recorder v2.0 loaded. Waiting for level gameplay (Game_Mode=0x0C, controls unlocked)...")

event.onexit(function()
    if started then
        write_metadata()
        close_files()
        print("Script exiting. Trace finalised at frame " .. trace_frame)
    end
end, "S1TraceRecorderExit")
