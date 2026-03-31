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
    local cx = mainmemory.read_u16_be(PLAYER_BASE + 0x08)
    return cy, ysub, yvel, cx
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
-- Also log which SST slot is active (a0 register low bits → slot index)
event.onmemoryexecute(function()
    if not active then return end
    local cy, ysub, yvel = readY()
    -- Read a0 register to identify which object is calling SpeedToPos
    local a0 = emu.getregister("M68K A0") or 0
    -- Strip to 16-bit RAM address (68K mirrors: $FFD000 or $FFFFD000)
    local a0_16 = a0 % 0x10000
    local slot = -1
    if a0_16 >= 0xD000 and a0_16 < 0xE000 then
        slot = (a0_16 - 0xD000) / 0x40
    end
    local cx2 = mainmemory.read_u16_be(PLAYER_BASE + 0x08)
    log(string.format("  STP slot=%d cx=0x%04X cy=0x%04X yvel=%d",
        slot, cx2, cy, yvel))
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
