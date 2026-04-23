local ADDR_GAMEMODE       = 0xFE10
local ADDR_FRAMECOUNT     = 0xFE04
local ADDR_CTRL1_HELD     = 0xF604
local ADDR_CTRL1_LOGICAL  = 0xF602
local ADDR_CONTROL_LOCKED = 0xF7CC
local ADDR_CAMERA_MAX_X   = 0xEECA
local ADDR_CAMERA_X       = 0xEE00

local PLAYER_BASE         = 0xB000
local OFF_ID              = 0x00
local OFF_ROUTINE         = 0x05
local OFF_STATUS          = 0x22
local OFF_X               = 0x08
local OFF_Y               = 0x0C
local OFF_X_SPEED         = 0x10
local OFF_Y_SPEED         = 0x12
local OFF_G_SPEED         = 0x14
local OFF_MOVE_LOCK       = 0x2E
local OFF_ANGLE           = 0x26

local GAMEMODE_LEVEL      = 0x0C

local START_FRAME = 5036
local END_FRAME = 5108

local OUTPUT_PATH = "C:/Users/farre/IdeaProjects/sonic-engine/.worktrees/feature/ai-s2-s3k-trace-recorder-v3/tools/bizhawk/trace_output/debug_s2_endact_inputs.txt"

emu.limitframerate(false)
client.speedmode(6400)
client.invisibleemulation(true)

local out = io.open(OUTPUT_PATH, "w")

local function u16(addr)
    return mainmemory.read_u16_be(addr)
end

local function s16(addr)
    local value = mainmemory.read_u16_be(addr)
    if value >= 0x8000 then
        value = value - 0x10000
    end
    return value
end

local function hex16(value)
    if value < 0 then
        value = value + 0x10000
    end
    return string.format("%04X", value)
end

local function dump_frame()
    local gameplay_frame = u16(ADDR_FRAMECOUNT)
    if gameplay_frame < START_FRAME or gameplay_frame > END_FRAME then
        return
    end

    local status = mainmemory.read_u8(PLAYER_BASE + OFF_STATUS)
    local air = (status & 0x02) ~= 0 and 1 or 0
    local rolling = (status & 0x04) ~= 0 and 1 or 0
    local control_locked = mainmemory.read_u8(ADDR_CONTROL_LOCKED)
    local raw = mainmemory.read_u16_be(ADDR_CTRL1_HELD) & 0x00FF
    local logical = mainmemory.read_u16_be(ADDR_CTRL1_LOGICAL) & 0x00FF

    out:write(string.format(
        "gfc=%04X raw=%02X logical=%02X lock=%02X cam=%04X camMax=%04X "
            .. "id=%02X rtn=%02X status=%02X air=%d roll=%d angle=%02X x=%04X y=%04X "
            .. "xspd=%s yspd=%s gspd=%s moveLock=%04X",
        gameplay_frame,
        raw,
        logical,
        control_locked,
        u16(ADDR_CAMERA_X),
        u16(ADDR_CAMERA_MAX_X),
        mainmemory.read_u8(PLAYER_BASE + OFF_ID),
        mainmemory.read_u8(PLAYER_BASE + OFF_ROUTINE),
        status,
        air,
        rolling,
        mainmemory.read_u8(PLAYER_BASE + OFF_ANGLE),
        u16(PLAYER_BASE + OFF_X),
        u16(PLAYER_BASE + OFF_Y),
        hex16(s16(PLAYER_BASE + OFF_X_SPEED)),
        hex16(s16(PLAYER_BASE + OFF_Y_SPEED)),
        hex16(s16(PLAYER_BASE + OFF_G_SPEED)),
        u16(PLAYER_BASE + OFF_MOVE_LOCK)
    ))
    out:write("\n")
    out:flush()
end

while true do
    if mainmemory.read_u8(ADDR_GAMEMODE) == GAMEMODE_LEVEL then
        dump_frame()
        if u16(ADDR_FRAMECOUNT) > END_FRAME then
            out:flush()
            out:close()
            client.exit()
        end
    end
    emu.frameadvance()
end
