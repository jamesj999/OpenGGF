------------------------------------------------------------------------------
-- s3k_trace_recorder.lua
-- BizHawk Lua script for recording Sonic 3 & Knuckles frame-by-frame physics
-- state during BK2 movie playback.
--
-- Usage:
--   1. Open BizHawk with a locked-on Sonic 3 & Knuckles ROM
--   2. Load a BK2 movie file
--   3. Tools > Lua Console > load this script
--   4. Play the movie -- recording starts automatically when gameplay begins
--   5. Stop the movie or close the script to finalise output files
--
-- v2.0 changes: added subpixel, routine, camera, rings, status_byte columns
-- to physics.csv for faster divergence debugging. Object proximity tracking
-- logs nearby objects every frame instead of only new appearances every 4.
-- v2.1 changes: scan the full OST, emit slot_dump events on object appearance
-- for slot allocation comparison, add frame counters to physics.csv, and log
-- richer aux events for object/routine diagnostics.
-- v2.2 changes: add stand_on_obj to physics.csv and richer routine-change
-- snapshots for hurt/death debugging.
-- v3.0-s3k changes: rename gameplay counter column to
-- gameplay_frame_counter and add vblank_counter plus lag_counter.
------------------------------------------------------------------------------

-----------------
--- Constants ---
-----------------

local OUTPUT_DIR = "trace_output/"
local HEADLESS = true
local MOVIE_FRAME_SAFETY_MARGIN = 30

-- Sonic 3 & Knuckles 68K RAM addresses (BizHawk mainmemory strips $FF0000)
local ADDR_GAME_MODE        = 0xF600
local ADDR_CTRL1            = 0xF604
local ADDR_CTRL1_DUP        = 0xF602
local ADDR_CTRL1_LOCKED     = 0xF7CA
local ADDR_RING_COUNT       = 0xFE24
local ADDR_CAMERA_X         = 0xEE7A
local ADDR_CAMERA_Y         = 0xEE7E
local ADDR_ZONE             = 0xFE14
local ADDR_ACT              = 0xFE15
local ADDR_PLAYER_MODE      = 0xFF08

-- Player_1 ($FFFFB400) uses 32-bit positions: high word = pixel, low word = subpixel.
local PLAYER_BASE           = 0xB400
local OFF_X_POS             = 0x10
local OFF_X_SUB             = 0x12
local OFF_Y_POS             = 0x14
local OFF_Y_SUB             = 0x16
local OFF_X_VEL             = 0x18
local OFF_Y_VEL             = 0x1A
local OFF_INERTIA           = 0x1C
local OFF_RADIUS_Y          = 0x1E
local OFF_RADIUS_X          = 0x1F
local OFF_ANIM_ID           = 0x20
local OFF_STATUS            = 0x2A
local OFF_ROUTINE           = 0x05
local OFF_ANGLE             = 0x26
local OFF_STICK_CONVEX      = 0x3C
local OFF_STAND_ON_OBJ      = 0x42   -- interact: RAM address of stood-on object
local OFF_CTRL_LOCK         = 0x32

local ROUTINE_HURT          = 0x04
local ROUTINE_DEATH         = 0x06

local STATUS_FACING_LEFT    = 0x01
local STATUS_IN_AIR         = 0x02
local STATUS_ROLLING        = 0x04
local STATUS_ON_OBJECT      = 0x08
local STATUS_ROLL_JUMP      = 0x10
local STATUS_PUSHING        = 0x20
local STATUS_UNDERWATER     = 0x40

-- Object table: S3K OST starts at Player_1 and uses $4A-byte entries.
local OBJ_TABLE_START       = 0xB400
local OBJ_SLOT_SIZE         = 0x4A
local OBJ_TOTAL_SLOTS       = 110
local OBJ_DYNAMIC_START     = 3
local OBJ_DYNAMIC_COUNT     = 90

local ADDR_FRAMECOUNT       = 0xFE08
local ADDR_VBLA_WORD        = 0xFE12
local ADDR_LAG_FRAME_COUNT  = 0xF628

local INPUT_UP    = 0x01
local INPUT_DOWN  = 0x02
local INPUT_LEFT  = 0x04
local INPUT_RIGHT = 0x08
local INPUT_JUMP  = 0x10

local GAMEMODE_LEVEL = 0x0C

local ZONE_NAMES = {
    [0x00] = "aiz",
    [0x01] = "hcz",
    [0x02] = "mgz",
    [0x03] = "cnz",
    [0x04] = "fbz",
    [0x05] = "icz",
    [0x06] = "lbz",
    [0x07] = "mhz",
    [0x08] = "soz",
    [0x09] = "lrz",
    [0x0A] = "ssz",
    [0x0B] = "dez",
    [0x0C] = "ddz",
    [0x0D] = "hpz",
}

local SNAPSHOT_INTERVAL = 60
local OBJECT_PROXIMITY = 160

-----------------
--- State     ---
-----------------

local started = false
local finished = false
local trace_frame = 0
local bk2_frame_offset = 0
local start_x = 0
local start_y = 0
local start_zone_id = 0
local start_zone_name = "unknown"
local start_act = 0

local prev_status = 0
local prev_routine = 0
local prev_ctrl_lock = 0
local prev_player_mode = nil

local known_objects = {}

local physics_file = nil
local aux_file = nil

-----------------
--- Helpers   ---
-----------------

local function read_speed(base, offset)
    return mainmemory.read_s16_be(base + offset)
end

local function rom_joypad_to_mask(raw)
    local mask = raw & 0x0F
    if (raw & 0x70) ~= 0 then
        mask = mask + INPUT_JUMP
    end
    return mask
end

local function hex(val, width)
    width = width or 4
    if val < 0 then
        val = val + 0x10000
    end
    return string.format("%0" .. width .. "X", val)
end

local function angle_to_ground_mode(angle)
    if angle <= 0x1F or angle >= 0xE0 then return 0 end
    if angle >= 0x20 and angle <= 0x5F then return 1 end
    if angle >= 0x60 and angle <= 0x9F then return 2 end
    return 3
end

local function write_aux(json_str)
    if aux_file then
        aux_file:write(json_str .. "\n")
        aux_file:flush()
    end
end

local function read_object_code(slot)
    local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
    return mainmemory.read_u32_be(addr)
end

local function interact_addr_to_slot(addr)
    if addr == 0 then
        return 0
    end
    local max_addr = OBJ_TABLE_START + (OBJ_TOTAL_SLOTS * OBJ_SLOT_SIZE)
    if addr < OBJ_TABLE_START or addr >= max_addr then
        return 0
    end
    local delta = addr - OBJ_TABLE_START
    if (delta % OBJ_SLOT_SIZE) ~= 0 then
        return 0
    end
    return math.floor(delta / OBJ_SLOT_SIZE)
end

local function read_stand_on_slot()
    local interact_addr = mainmemory.read_u16_be(PLAYER_BASE + OFF_STAND_ON_OBJ)
    return interact_addr_to_slot(interact_addr)
end

-----------------
--- Recording ---
-----------------

local function open_files()
    physics_file = io.open(OUTPUT_DIR .. "physics.csv", "w")
    aux_file = io.open(OUTPUT_DIR .. "aux_state.jsonl", "w")

    physics_file:write("frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode,"
        .. "x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte,gameplay_frame_counter,stand_on_obj,"
        .. "vblank_counter,lag_counter\n")
    physics_file:flush()
end

local function write_metadata()
    local meta_file = io.open(OUTPUT_DIR .. "metadata.json", "w")
    meta_file:write("{\n")
    meta_file:write('  "game": "s3k",\n')
    meta_file:write('  "zone": "' .. start_zone_name .. '",\n')
    meta_file:write('  "zone_id": ' .. start_zone_id .. ',\n')
    meta_file:write('  "act": ' .. (start_act + 1) .. ',\n')
    meta_file:write('  "bk2_frame_offset": ' .. bk2_frame_offset .. ',\n')
    meta_file:write('  "trace_frame_count": ' .. trace_frame .. ',\n')
    meta_file:write('  "start_x": "0x' .. hex(start_x) .. '",\n')
    meta_file:write('  "start_y": "0x' .. hex(start_y) .. '",\n')
    meta_file:write('  "recording_date": "' .. os.date("%Y-%m-%d") .. '",\n')
    meta_file:write('  "lua_script_version": "3.0-s3k",\n')
    meta_file:write('  "trace_schema": 3,\n')
    meta_file:write('  "csv_version": 4,\n')
    meta_file:write('  "rom_checksum": "",\n')
    meta_file:write('  "notes": ""\n')
    meta_file:write("}\n")
    meta_file:close()
    print(string.format("Metadata written. Zone: %s Act %d, Trace frames: %d",
        start_zone_name, start_act + 1, trace_frame))
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

local function build_slot_dump()
    local entries = {}
    local dynamic_end = OBJ_DYNAMIC_START + OBJ_DYNAMIC_COUNT
    for slot = OBJ_DYNAMIC_START, dynamic_end - 1 do
        local obj_code = read_object_code(slot)
        if obj_code ~= 0 then
            entries[#entries + 1] = string.format("[%d,\"0x%08X\"]", slot, obj_code)
        end
    end
    return "[" .. table.concat(entries, ",") .. "]"
end

local function scan_objects(player_x, player_y)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local any_appeared = false

    for slot = 1, OBJ_TOTAL_SLOTS - 1 do
        local addr = OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
        local obj_code = mainmemory.read_u32_be(addr)
        local prev_code = known_objects[slot] or 0

        if obj_code ~= 0 and obj_code ~= prev_code then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            write_aux(string.format(
                '{"frame":%d,"vfc":%d,"event":"object_appeared","slot":%d,"object_type":"0x%08X","x":"0x%04X","y":"0x%04X"}',
                trace_frame, vfc, slot, obj_code, obj_x, obj_y))
            any_appeared = true
        end

        if obj_code == 0 and prev_code ~= 0 then
            write_aux(string.format(
                '{"frame":%d,"vfc":%d,"event":"object_removed","slot":%d,"object_type":"0x%08X"}',
                trace_frame, vfc, slot, prev_code))
        end

        if obj_code ~= 0 then
            local obj_x = mainmemory.read_u16_be(addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(addr + OFF_Y_POS)
            local dx = math.abs(obj_x - player_x)
            local dy = math.abs(obj_y - player_y)
            if dx <= OBJECT_PROXIMITY and dy <= OBJECT_PROXIMITY then
                local obj_status = mainmemory.read_u8(addr + OFF_STATUS)
                local obj_routine = mainmemory.read_u8(addr + OFF_ROUTINE)
                write_aux(string.format(
                    '{"frame":%d,"vfc":%d,"event":"object_near","slot":%d,"type":"0x%08X",'
                    .. '"x":"0x%04X","y":"0x%04X","routine":"0x%02X","status":"0x%02X"}',
                    trace_frame, vfc, slot, obj_code, obj_x, obj_y, obj_routine, obj_status))
            end
        end

        known_objects[slot] = obj_code
    end

    if any_appeared then
        local dump = build_slot_dump()
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"slot_dump","slots":%s}',
            trace_frame, vfc, dump))
    end
end

local function write_state_snapshot()
    local ctrl_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local anim_id = mainmemory.read_u8(PLAYER_BASE + OFF_ANIM_ID)
    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local routine = mainmemory.read_u8(PLAYER_BASE + OFF_ROUTINE)
    local y_radius = mainmemory.read_s8(PLAYER_BASE + OFF_RADIUS_Y)
    local x_radius = mainmemory.read_s8(PLAYER_BASE + OFF_RADIUS_X)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    write_aux(string.format(
        '{"frame":%d,"vfc":%d,"event":"state_snapshot","control_locked":%s,"anim_id":%d,'
        .. '"status_byte":"0x%02X","routine":"0x%02X","y_radius":%d,"x_radius":%d,'
        .. '"on_object":%s,"pushing":%s,"underwater":%s,'
        .. '"roll_jumping":%s}',
        trace_frame,
        vfc,
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

local function emit_player_mode_event()
    local player_mode = mainmemory.read_u16_be(ADDR_PLAYER_MODE)
    if prev_player_mode == nil or player_mode ~= prev_player_mode then
        local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"player_mode_set","mode":%d}',
            trace_frame, vfc, player_mode))
        prev_player_mode = player_mode
    end
end

local function check_mode_changes(status, routine)
    local vfc = mainmemory.read_u16_be(ADDR_FRAMECOUNT)

    local was_air = (prev_status & STATUS_IN_AIR) ~= 0
    local is_air = (status & STATUS_IN_AIR) ~= 0
    if was_air ~= is_air then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"air","from":%d,"to":%d}',
            trace_frame, vfc, was_air and 1 or 0, is_air and 1 or 0))
        write_state_snapshot()
    end

    local was_rolling = (prev_status & STATUS_ROLLING) ~= 0
    local is_rolling = (status & STATUS_ROLLING) ~= 0
    if was_rolling ~= is_rolling then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"rolling","from":%d,"to":%d}',
            trace_frame, vfc, was_rolling and 1 or 0, is_rolling and 1 or 0))
    end

    local was_on_obj = (prev_status & STATUS_ON_OBJECT) ~= 0
    local is_on_obj = (status & STATUS_ON_OBJECT) ~= 0
    if was_on_obj ~= is_on_obj then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"on_object","from":%d,"to":%d}',
            trace_frame, vfc, was_on_obj and 1 or 0, is_on_obj and 1 or 0))
    end

    local ctrl_lock = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
    local was_locked = prev_ctrl_lock > 0
    local is_locked = ctrl_lock > 0
    if was_locked ~= is_locked then
        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"mode_change","field":"control_locked","from":%d,"to":%d}',
            trace_frame, vfc, was_locked and 1 or 0, is_locked and 1 or 0))
    end
    prev_ctrl_lock = ctrl_lock

    if routine ~= prev_routine then
        local stand_on_obj = read_stand_on_slot()
        local sonic_x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
        local sonic_y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
        local sonic_xvel = mainmemory.read_s16_be(PLAYER_BASE + OFF_X_VEL)
        local sonic_yvel = mainmemory.read_s16_be(PLAYER_BASE + OFF_Y_VEL)
        local sonic_inertia = mainmemory.read_s16_be(PLAYER_BASE + OFF_INERTIA)

        local obj_context = ""
        if stand_on_obj > 0 and stand_on_obj < OBJ_TOTAL_SLOTS then
            local obj_addr = OBJ_TABLE_START + (stand_on_obj * OBJ_SLOT_SIZE)
            local obj_code = mainmemory.read_u32_be(obj_addr)
            local obj_x = mainmemory.read_u16_be(obj_addr + OFF_X_POS)
            local obj_y = mainmemory.read_u16_be(obj_addr + OFF_Y_POS)
            local obj_routine = mainmemory.read_u8(obj_addr + OFF_ROUTINE)
            obj_context = string.format(
                ',"stand_obj_slot":%d,"stand_obj_type":"0x%08X","stand_obj_x":"0x%04X",'
                .. '"stand_obj_y":"0x%04X","stand_obj_routine":"0x%02X"',
                stand_on_obj, obj_code, obj_x, obj_y, obj_routine)
        end

        write_aux(string.format(
            '{"frame":%d,"vfc":%d,"event":"routine_change","from":"0x%02X","to":"0x%02X",'
            .. '"sonic_x":"0x%04X","sonic_y":"0x%04X","x_vel":%d,"y_vel":%d,"inertia":%d,'
            .. '"status":"0x%02X","stand_on_obj":%d%s}',
            trace_frame, vfc, prev_routine, routine,
            sonic_x, sonic_y, sonic_xvel, sonic_yvel, sonic_inertia,
            status, stand_on_obj, obj_context))

        if routine == ROUTINE_HURT or routine == ROUTINE_DEATH then
            write_state_snapshot()
        end
    end
    prev_routine = routine
end

-----------------
--- Main Loop ---
-----------------

local function on_frame_end()
    local game_mode = mainmemory.read_u8(ADDR_GAME_MODE)

    if not started then
        if finished then return end
        local ctrl_lock_timer = mainmemory.read_u16_be(PLAYER_BASE + OFF_CTRL_LOCK)
        local ctrl_locked = mainmemory.read_u8(ADDR_CTRL1_LOCKED)
        if game_mode == GAMEMODE_LEVEL and ctrl_lock_timer == 0 and ctrl_locked == 0 then
            started = true
            bk2_frame_offset = emu.framecount()
            start_x = mainmemory.read_u16_be(PLAYER_BASE + OFF_X_POS)
            start_y = mainmemory.read_u16_be(PLAYER_BASE + OFF_Y_POS)
            start_zone_id = mainmemory.read_u8(ADDR_ZONE)
            start_act = mainmemory.read_u8(ADDR_ACT)
            start_zone_name = ZONE_NAMES[start_zone_id] or string.format("unknown_%02x", start_zone_id)

            open_files()
            write_metadata()
            print(string.format("Trace recording started at BizHawk frame %d, zone %s act %d, pos (%04X, %04X)",
                bk2_frame_offset, start_zone_name, start_act + 1, start_x, start_y))
            if movie.isloaded() then
                print(string.format("Movie length: %d frames", movie.length()))
            end
        end
        return
    end

    if game_mode ~= GAMEMODE_LEVEL then
        print("Left level gameplay at trace frame " .. trace_frame .. ". Finalising.")
        finished = true
        return
    end

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

    local camera_x = mainmemory.read_u16_be(ADDR_CAMERA_X)
    local camera_y = mainmemory.read_u16_be(ADDR_CAMERA_Y)
    local rings = mainmemory.read_u16_be(ADDR_RING_COUNT)

    local air = (status & STATUS_IN_AIR) ~= 0
    local rolling = (status & STATUS_ROLLING) ~= 0
    local ground_mode = air and 0 or angle_to_ground_mode(angle)

    local raw_input = mainmemory.read_u8(ADDR_CTRL1)
    local input_mask = rom_joypad_to_mask(raw_input)

    local function uhex(val)
        if val < 0 then return val + 0x10000 end
        return val
    end

    local gameplay_frame_counter = mainmemory.read_u16_be(ADDR_FRAMECOUNT)
    local stand_on_obj = read_stand_on_slot()
    local vblank_counter = mainmemory.read_u16_be(ADDR_VBLA_WORD)
    local lag_counter = mainmemory.read_u16_be(ADDR_LAG_FRAME_COUNT)

    physics_file:write(string.format(
        "%04X,%04X,%04X,%04X,%04X,%04X,%04X,%02X,%d,%d,%d,%04X,%04X,%02X,%04X,%04X,%04X,%02X,%04X,%02X,%04X,%04X\n",
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
        status,
        gameplay_frame_counter,
        stand_on_obj,
        vblank_counter,
        lag_counter))

    if trace_frame % 60 == 0 then
        physics_file:flush()
    end
    if trace_frame % 300 == 0 then
        write_metadata()
    end

    emit_player_mode_event()
    check_mode_changes(status, routine)
    prev_status = status

    if trace_frame % SNAPSHOT_INTERVAL == 0 then
        write_state_snapshot()
    end

    scan_objects(x, y)

    trace_frame = trace_frame + 1
end

os.execute("mkdir \"" .. OUTPUT_DIR .. "\" 2>NUL")

local HEADLESS_VISIBLE = false
if HEADLESS then
    emu.limitframerate(false)
    client.speedmode(6400)
    if not HEADLESS_VISIBLE then
        client.invisibleemulation(true)
    end
end

print("S3K Trace Recorder v3.0-s3k loaded. Waiting for level gameplay (Game_Mode=0x0C, controls unlocked)...")

while true do
    on_frame_end()

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

    if client.ispaused() then
        client.unpause()
    end

    emu.frameadvance()
end
