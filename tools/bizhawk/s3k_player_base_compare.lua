local OUTPUT_PATH = "trace_output/s3k_player_base_compare.txt"

local TARGET_FRAMES = { 563, 1897, 2048, 4063 }
local BASES = { 0xB000, 0xB400 }
local SLOT_SIZE = 0x4A
local SLOT_COUNT = 6

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

local ADDR_CAMERA_X = 0xEE78
local ADDR_CAMERA_Y = 0xEE7C
local ADDR_ZONE_ACT = 0xFE10

local function dump_slot(out, base, slot, camera_x, camera_y)
    local addr = base + (slot * SLOT_SIZE)
    local x = mainmemory.read_u16_be(addr + OFF_X_POS)
    local y = mainmemory.read_u16_be(addr + OFF_Y_POS)
    out:write(string.format(
        "  slot=%02d addr=%04X code=%08X rtn=%02X char=%02X x=%04X y=%04X dx=%04X dy=%04X xv=%6d yv=%6d gv=%6d stat=%02X ctrl=%02X move=%04X\n",
        slot,
        addr,
        mainmemory.read_u32_be(addr + OFF_CODE),
        mainmemory.read_u8(addr + OFF_ROUTINE),
        mainmemory.read_u8(addr + OFF_CHARACTER_ID),
        x,
        y,
        math.abs(x - camera_x),
        math.abs(y - camera_y),
        mainmemory.read_s16_be(addr + OFF_X_VEL),
        mainmemory.read_s16_be(addr + OFF_Y_VEL),
        mainmemory.read_s16_be(addr + OFF_GROUND_VEL),
        mainmemory.read_u8(addr + OFF_STATUS),
        mainmemory.read_u8(addr + OFF_OBJECT_CONTROL),
        mainmemory.read_u16_be(addr + OFF_MOVE_LOCK)))
end

local out = assert(io.open(OUTPUT_PATH, "w"))
out:write("S3K player base compare\n")

for _, target in ipairs(TARGET_FRAMES) do
    while emu.framecount() < target do
        if movie.isloaded() and movie.mode() == "FINISHED" then
            out:write("movie finished before target frame\n")
            out:close()
            client.exit()
            return
        end
        emu.frameadvance()
    end

    local camera_x = mainmemory.read_u16_be(ADDR_CAMERA_X)
    local camera_y = mainmemory.read_u16_be(ADDR_CAMERA_Y)
    out:write(string.rep("=", 80) .. "\n")
    out:write(string.format(
        "frame=%d camera=(%04X,%04X) zone_act=%04X\n",
        emu.framecount(),
        camera_x,
        camera_y,
        mainmemory.read_u16_be(ADDR_ZONE_ACT)))
    for _, base in ipairs(BASES) do
        out:write(string.format("base=%04X\n", base))
        for slot = 0, SLOT_COUNT - 1 do
            dump_slot(out, base, slot, camera_x, camera_y)
        end
    end
    out:flush()
end

out:close()
client.exit()
