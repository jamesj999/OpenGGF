local ADDR_GAME_MODE = 0xF600
local ADDR_FRAMECOUNT = 0xFE04
local ADDR_CAMERA_X = 0xEE00
local ADDR_CAMERA_Y = 0xEE04
local ADDR_TAILS_RESPAWN_COUNTER = 0xF704
local ADDR_TAILS_CPU_ROUTINE = 0xF708
local ADDR_TAILS_INTERACT_ID = 0xF70E

local OBJ_TABLE_START = 0xB000
local OBJ_SLOT_SIZE = 0x40
local PLAYER_BASE = 0xB000
local SIDEKICK_BASE = OBJ_TABLE_START + OBJ_SLOT_SIZE

local OFF_RENDER_FLAGS = 0x01
local OFF_X_POS = 0x08
local OFF_Y_POS = 0x0C
local OFF_STATUS = 0x22
local OFF_ROUTINE = 0x24
local OFF_INTERACT = 0x3D

local GAMEMODE_LEVEL = 0x0C
local RENDER_FLAG_ON_SCREEN = 0x80

local START_FRAME = 5490
local END_FRAME = 5520
local OUTPUT_PATH =
    "C:/Users/farre/IdeaProjects/sonic-engine/.worktrees/feature/ai-s2-s3k-trace-recorder-v3/tools/bizhawk/trace_output/debug_s2_tails_despawn.txt"

emu.limitframerate(false)
client.speedmode(6400)
client.invisibleemulation(true)

local out = assert(io.open(OUTPUT_PATH, "w"))

local function u8(addr)
    return mainmemory.read_u8(addr)
end

local function u16(addr)
    return mainmemory.read_u16_be(addr)
end

local function dump_frame()
    local gameplay_frame = u16(ADDR_FRAMECOUNT)
    if gameplay_frame < START_FRAME or gameplay_frame > END_FRAME then
        return
    end

    local tails_render = u8(SIDEKICK_BASE + OFF_RENDER_FLAGS)
    local tails_status = u8(SIDEKICK_BASE + OFF_STATUS)

    out:write(string.format(
        "gfc=%04X cam=(%04X,%04X) sonic=(%04X,%04X) tails=(%04X,%04X) "
            .. "tailsRender=%02X onScreen=%d tailsStatus=%02X tailsRoutine=%02X "
            .. "tailsInteract=%02X cpuRespawn=%04X cpuRoutine=%04X cpuInteract=%02X",
        gameplay_frame,
        u16(ADDR_CAMERA_X),
        u16(ADDR_CAMERA_Y),
        u16(PLAYER_BASE + OFF_X_POS),
        u16(PLAYER_BASE + OFF_Y_POS),
        u16(SIDEKICK_BASE + OFF_X_POS),
        u16(SIDEKICK_BASE + OFF_Y_POS),
        tails_render,
        (tails_render & RENDER_FLAG_ON_SCREEN) ~= 0 and 1 or 0,
        tails_status,
        u8(SIDEKICK_BASE + OFF_ROUTINE),
        u8(SIDEKICK_BASE + OFF_INTERACT),
        u16(ADDR_TAILS_RESPAWN_COUNTER),
        u16(ADDR_TAILS_CPU_ROUTINE),
        u8(ADDR_TAILS_INTERACT_ID)
    ))
    out:write("\n")
    out:flush()
end

while true do
    if u8(ADDR_GAME_MODE) == GAMEMODE_LEVEL then
        dump_frame()
        if u16(ADDR_FRAMECOUNT) > END_FRAME then
            out:close()
            client.exit()
        end
    end
    emu.frameadvance()
end
