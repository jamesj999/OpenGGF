-- Watch for writes to Sonic's obY ($FFD00C) during a specific frame.
-- This will tell us exactly WHAT modifies Sonic's Y position.

-- Try multiple frames around the target
local TARGET_EMU_FRAME = 5110  -- trace frame 4034 (adjusted: EMU = trace + offset + 1)
local PLAYER_OBY_ADDR = 0xD00C  -- obY high word (centre Y pixel)
local PLAYER_OBY_SUB  = 0xD00E  -- obY low word (subpixel)

local watching = false
local writes = {}

-- Register callback for writes to obY (2 bytes)
local function on_y_write(addr, value, flags)
    if watching then
        local pc = emu.getregister("M68K PC") or 0
        local cy = mainmemory.read_u16_be(PLAYER_OBY_ADDR)
        table.insert(writes, string.format(
            "  WRITE addr=0x%04X val=0x%04X PC=0x%06X cy_now=0x%04X frame=%d",
            addr, value, pc, cy, emu.framecount()))
    end
end

-- Watch all 4 bytes of the Y position (long word)
event.onmemorywrite(on_y_write, PLAYER_OBY_ADDR)
event.onmemorywrite(on_y_write, PLAYER_OBY_ADDR + 1)
event.onmemorywrite(on_y_write, PLAYER_OBY_SUB)
event.onmemorywrite(on_y_write, PLAYER_OBY_SUB + 1)

print("Watching for writes to obY at frame " .. TARGET_EMU_FRAME .. "...")

while true do
    local f = emu.framecount()

    -- Log state for frames around the target
    if f >= TARGET_EMU_FRAME - 2 and f <= TARGET_EMU_FRAME + 3 then
        local cy = mainmemory.read_u16_be(PLAYER_OBY_ADDR)
        local ysub = mainmemory.read_u16_be(PLAYER_OBY_SUB)
        local yspd = mainmemory.read_s16_be(0xD012)
        local routine = mainmemory.read_u8(0xD024)
        local status = mainmemory.read_u8(0xD022)
        local stateStr = string.format("EMU %d: cy=0x%04X ysub=0x%04X yspd=%d rtn=%d status=0x%02X",
            f, cy, ysub, yspd, routine, status)
        print(stateStr)
        table.insert(writes, stateStr)
    end

    if f == TARGET_EMU_FRAME - 1 then
        watching = true
    end

    if f == TARGET_EMU_FRAME + 1 then
        watching = false
        -- Write to file
        local f = io.open("y_writes.txt", "w")
        f:write("Y writes during frame " .. TARGET_EMU_FRAME .. ":\n")
        for _, w in ipairs(writes) do
            f:write(w .. "\n")
        end
        f:close()
        print("Written to trace_output/y_writes.txt")
        client.exit()
        break
    end

    emu.frameadvance()
end
