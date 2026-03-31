------------------------------------------------------------------------------
-- hook_speedtopos.lua
-- Uses event.onmemoryexecute to hook the move.l d3,obY instruction in
-- SpeedToPos/ObjectMoveAndFall to see the exact Y value being written.
-- Also hooks known addresses to trace the frame flow.
------------------------------------------------------------------------------

local TARGET_EMU_FRAME = 5110

-- ROM addresses (verified from disassembly binary)
local ADDR_OBJMOVEFALL_WRITE_Y = 0x0DC66  -- move.l d3,0xC(a0) in ObjectMoveAndFall
local ADDR_SPEEDTOPOS_WRITE_Y  = 0x0DC92  -- move.l d3,0xC(a0) in SpeedToPos (next func)
-- Also: the RTS at end of ObjectMoveAndFall
local ADDR_OBJMOVEFALL_RTS     = 0x0DC6A

local PLAYER_BASE = 0xD000
local output = {}
local active = false

local function log(s) table.insert(output, s) end

local function readY()
    local cy = mainmemory.read_u16_be(PLAYER_BASE + 0x0C)
    local ysub = mainmemory.read_u16_be(PLAYER_BASE + 0x0E)
    local yvel = mainmemory.read_s16_be(PLAYER_BASE + 0x12)
    return cy, ysub, yvel
end

-- Hook: just AFTER Y is written by ObjectMoveAndFall
event.onmemoryexecute(function()
    if not active then return end
    local cy, ysub, yvel = readY()
    log(string.format("  HOOK ObjMoveFall_WriteY @0x%05X: cy=0x%04X ysub=0x%04X yvel=%d",
        ADDR_OBJMOVEFALL_WRITE_Y, cy, ysub, yvel))
end, ADDR_OBJMOVEFALL_WRITE_Y)

-- Hook: RTS of ObjectMoveAndFall (to see Y after the function returns)
event.onmemoryexecute(function()
    if not active then return end
    local cy, ysub, yvel = readY()
    log(string.format("  HOOK ObjMoveFall_RTS @0x%05X: cy=0x%04X ysub=0x%04X yvel=%d",
        ADDR_OBJMOVEFALL_RTS, cy, ysub, yvel))
end, ADDR_OBJMOVEFALL_RTS)

-- Hook: SpeedToPos (no gravity version) Y write
-- Find it: starts at 0x0DC6C, write Y at +0x26 = 0x0DC92
event.onmemoryexecute(function()
    if not active then return end
    local cy, ysub, yvel = readY()
    log(string.format("  HOOK SpeedToPos_WriteY @0x%05X: cy=0x%04X ysub=0x%04X yvel=%d",
        ADDR_SPEEDTOPOS_WRITE_Y, cy, ysub, yvel))
end, ADDR_SPEEDTOPOS_WRITE_Y)

print("Hooks registered. Waiting for frame " .. TARGET_EMU_FRAME)

while true do
    local f = emu.framecount()

    if f >= TARGET_EMU_FRAME - 2 and f <= TARGET_EMU_FRAME + 2 then
        local cy, ysub, yvel = readY()
        log(string.format("EMU %d: cy=0x%04X ysub=0x%04X yvel=%d", f, cy, ysub, yvel))
    end

    if f == TARGET_EMU_FRAME - 1 then
        active = true
        log("=== hooks active ===")
    end
    if f == TARGET_EMU_FRAME then
        active = false
        log("=== hooks inactive ===")
    end

    if f == TARGET_EMU_FRAME + 2 then
        local file = io.open("y_hooks.txt", "w")
        for _, line in ipairs(output) do
            file:write(line .. "\n")
        end
        file:close()
        print("Written " .. #output .. " lines to y_hooks.txt")
        client.exit()
        break
    end

    emu.frameadvance()
end
