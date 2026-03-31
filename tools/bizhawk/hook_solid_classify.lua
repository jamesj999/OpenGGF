-- Hook the SolidObject classification branch to see d1 (absDistY) and d5 (absDistX)
local TARGET_EMU_FRAME = 5110
local PLAYER_BASE = 0xD000
local CLASSIFY_ADDR = 0x101D0  -- cmp.w d1,d5 in SolidObject (second candidate)
local output = {}
local active = false

local function log(s) table.insert(output, s) end

event.onmemoryexecute(function()
    if not active then return end
    local d1 = emu.getregister("M68K D1") or 0
    local d5 = emu.getregister("M68K D5") or 0
    local a0 = emu.getregister("M68K A0") or 0
    local a1 = emu.getregister("M68K A1") or 0
    local slot = ((a0 % 0x10000) - 0xD000) / 0x40
    local cy = mainmemory.read_u16_be(PLAYER_BASE + 0x0C)
    local cx = mainmemory.read_u16_be(PLAYER_BASE + 0x08)
    -- d5 = absDistX, d1 = absDistY (both 16-bit)
    local absDistX = d5 % 0x10000
    local absDistY = d1 % 0x10000
    local result = absDistX > absDistY and "TopBottom" or "Side"
    log(string.format("  CLASSIFY slot=%d: d5(absX)=%d d1(absY)=%d → %s | sonicCX=0x%04X sonicCY=0x%04X",
        slot, absDistX, absDistY, result, cx, cy))
end, CLASSIFY_ADDR)

print("Hooking SolidObject classify at 0x" .. string.format("%05X", CLASSIFY_ADDR))

while true do
    local f = emu.framecount()
    if f == TARGET_EMU_FRAME - 1 then
        active = true
        log("=== classify hooks active ===")
    end
    if f == TARGET_EMU_FRAME then
        active = false
        log("=== classify hooks inactive ===")
        local file = io.open("solid_classify.txt", "w")
        for _, line in ipairs(output) do
            file:write(line .. "\n")
        end
        file:close()
        print("Written " .. #output .. " lines")
        client.exit()
        break
    end
    emu.frameadvance()
end
