local OUTPUT_PATH = "trace_output/s3k_slot_scan.txt"

local TARGET_FRAMES = {
    563,   -- trace frame 0
    1897,  -- trace frame 1334 (gameplay_start)
    2048,  -- trace frame 1485 (early post-gameplay window)
    4063,  -- trace frame 3500
    5563,  -- trace frame 5000
}

local OBJ_TABLE_START = 0xB400
local OBJ_SLOT_SIZE = 0x4A
local OBJ_TOTAL_SLOTS = 110

local OFF_CODE = 0x00
local OFF_ROUTINE = 0x05
local OFF_X_POS = 0x10
local OFF_Y_POS = 0x14
local OFF_X_VEL = 0x18
local OFF_Y_VEL = 0x1A
local OFF_GROUND_VEL = 0x1C
local OFF_STATUS = 0x2A
local OFF_OBJECT_CONTROL = 0x2E
local OFF_MOVE_LOCK = 0x32
local OFF_CHARACTER_ID = 0x38

local ADDR_GAME_MODE = 0xF600
local ADDR_CAMERA_X = 0xEE78
local ADDR_CAMERA_Y = 0xEE7C
local ADDR_ZONE = 0xFE10
local ADDR_ACT = 0xFE11
local ADDR_APPARENT_ACT = 0xEE4F
local ADDR_PLAYER_MODE = 0xFF08
local ADDR_LEVEL_STARTED = 0xF711
local ADDR_CTRL1_LOCKED = 0xF7CA
local ADDR_EVENTS_FG_5 = 0xEEC6

local function write_line(handle, line)
    handle:write(line .. "\n")
    handle:flush()
end

local function slot_addr(slot)
    return OBJ_TABLE_START + (slot * OBJ_SLOT_SIZE)
end

local function read_slot(slot)
    local addr = slot_addr(slot)
    return {
        slot = slot,
        addr = addr,
        code = mainmemory.read_u32_be(addr + OFF_CODE),
        routine = mainmemory.read_u8(addr + OFF_ROUTINE),
        x = mainmemory.read_u16_be(addr + OFF_X_POS),
        y = mainmemory.read_u16_be(addr + OFF_Y_POS),
        x_vel = mainmemory.read_s16_be(addr + OFF_X_VEL),
        y_vel = mainmemory.read_s16_be(addr + OFF_Y_VEL),
        g_vel = mainmemory.read_s16_be(addr + OFF_GROUND_VEL),
        status = mainmemory.read_u8(addr + OFF_STATUS),
        object_control = mainmemory.read_u8(addr + OFF_OBJECT_CONTROL),
        move_lock = mainmemory.read_u16_be(addr + OFF_MOVE_LOCK),
        character_id = mainmemory.read_u8(addr + OFF_CHARACTER_ID),
    }
end

local function slot_summary(entry)
    return string.format(
        "slot=%02d addr=%04X code=%08X rtn=%02X x=%04X y=%04X xv=%6d yv=%6d gv=%6d stat=%02X ctrl=%02X move=%04X char=%02X",
        entry.slot, entry.addr, entry.code, entry.routine, entry.x, entry.y,
        entry.x_vel, entry.y_vel, entry.g_vel, entry.status, entry.object_control,
        entry.move_lock, entry.character_id)
end

local function should_log_slot(entry, camera_x)
    if entry.slot <= 4 then
        return true
    end
    if entry.character_id <= 2 then
        return true
    end
    if entry.code == 0 then
        return false
    end
    if entry.x == 0 and entry.y == 0 then
        return false
    end
    local dx = math.abs(entry.x - camera_x)
    return dx <= 0x0200
end

local out = assert(io.open(OUTPUT_PATH, "w"))
write_line(out, "S3K slot scan")

local target_index = 1

while true do
    local frame = emu.framecount()
    local target = TARGET_FRAMES[target_index]

    if target ~= nil and frame >= target then
        local camera_x = mainmemory.read_u16_be(ADDR_CAMERA_X)
        local camera_y = mainmemory.read_u16_be(ADDR_CAMERA_Y)
        write_line(out, string.rep("=", 80))
        write_line(out, string.format(
            "emu_frame=%d game_mode=%02X zone=%02X act=%02X apparent=%02X player_mode=%04X level_started=%02X ctrl1_locked=%02X events_fg_5=%04X cam=(%04X,%04X)",
            frame,
            mainmemory.read_u8(ADDR_GAME_MODE),
            mainmemory.read_u8(ADDR_ZONE),
            mainmemory.read_u8(ADDR_ACT),
            mainmemory.read_u8(ADDR_APPARENT_ACT),
            mainmemory.read_u16_be(ADDR_PLAYER_MODE),
            mainmemory.read_u8(ADDR_LEVEL_STARTED),
            mainmemory.read_u8(ADDR_CTRL1_LOCKED),
            mainmemory.read_u16_be(ADDR_EVENTS_FG_5),
            camera_x,
            camera_y))

        for slot = 0, OBJ_TOTAL_SLOTS - 1 do
            local entry = read_slot(slot)
            if should_log_slot(entry, camera_x) then
                write_line(out, slot_summary(entry))
            end
        end

        target_index = target_index + 1
        if TARGET_FRAMES[target_index] == nil then
            out:close()
            client.exit()
            break
        end
    end

    if movie.isloaded() and movie.mode() == "FINISHED" then
        write_line(out, "movie finished before all targets")
        out:close()
        client.exit()
        break
    end

    emu.frameadvance()
end
