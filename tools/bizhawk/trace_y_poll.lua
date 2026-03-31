------------------------------------------------------------------------------
-- trace_y_poll.lua
-- Snapshot Sonic Y at start and end of the target frame, plus use
-- event.onmemorywrite to detect writes to the FULL obY long word.
-- Uses the "mainmemory" domain with 4 separate byte watches.
------------------------------------------------------------------------------

local TARGET_EMU_FRAME = 5110

local PLAYER_BASE = 0xD000
local OFF_Y = 0x0C
local OFF_YSUB = 0x0E
local OFF_YVEL = 0x12
local OFF_ROUTINE = 0x24

local output = {}
local watching = false

local function log(s) table.insert(output, s) end

local function readY()
    return mainmemory.read_u16_be(PLAYER_BASE + OFF_Y),
           mainmemory.read_u16_be(PLAYER_BASE + OFF_YSUB),
           mainmemory.read_s16_be(PLAYER_BASE + OFF_YVEL),
           mainmemory.read_u8(PLAYER_BASE + OFF_ROUTINE)
end

-- Register write watches on all 4 bytes of obY long word
for offset = 0, 3 do
    local addr = PLAYER_BASE + OFF_Y + offset
    event.onmemorywrite(function()
        if watching then
            local cy, ysub, yvel, rtn = readY()
            log(string.format("  WRITE @0x%04X: cy=0x%04X ysub=0x%04X yvel=%d rtn=%d",
                addr, cy, ysub, yvel, rtn))
        end
    end, addr)
end

print("Polling Y writes at EMU frame " .. TARGET_EMU_FRAME)

while true do
    local f = emu.framecount()

    -- Log state for nearby frames
    if f >= TARGET_EMU_FRAME - 2 and f <= TARGET_EMU_FRAME + 2 then
        local cy, ysub, yvel, rtn = readY()
        log(string.format("EMU %d END: cy=0x%04X ysub=0x%04X yvel=%d rtn=%d", f, cy, ysub, yvel, rtn))
    end

    if f == TARGET_EMU_FRAME - 1 then
        watching = true
        log("--- watching writes during frame " .. TARGET_EMU_FRAME .. " ---")
    end

    if f == TARGET_EMU_FRAME then
        watching = false
        log("--- end watch ---")
    end

    if f == TARGET_EMU_FRAME + 2 then
        local file = io.open("y_poll.txt", "w")
        for _, line in ipairs(output) do
            file:write(line .. "\n")
        end
        file:close()
        print("Written " .. #output .. " lines to y_poll.txt")
        client.exit()
        break
    end

    emu.frameadvance()
end
