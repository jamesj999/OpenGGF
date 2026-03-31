------------------------------------------------------------------------------
-- trace_y_instructions.lua
-- Captures all reads/writes to Sonic's obY ($FFD00C-$FFD00F) during a
-- specific frame by polling the value every few instructions.
-- Since event.onmemorywrite doesn't fire reliably in chromeless mode,
-- this uses a different approach: snapshot Y before and after each
-- emu.frameadvance(), and if Y changed, log it.
--
-- Additionally, logs the Y value at multiple points WITHIN the frame
-- by using event.onmemoryexecute at known ROM addresses.
------------------------------------------------------------------------------

local TARGET_EMU_FRAME = 5110  -- trace frame 4034 (EMU = trace + offset + 1)

-- Known S1 ROM addresses (from disassembly)
local ADDR_SPEEDTOPOS     = nil  -- Will search for it
local ADDR_SONIC_HURT     = nil
local ADDR_SOLID_OBJECT   = nil

-- Player RAM
local PLAYER_OBY    = 0xD00C
local PLAYER_OYSUB  = 0xD00E
local PLAYER_YVEL   = 0xD012
local PLAYER_ROUTINE = 0xD024

local output = {}

local function log(msg)
    table.insert(output, msg)
end

local function readState()
    local cy = mainmemory.read_u16_be(PLAYER_OBY)
    local ysub = mainmemory.read_u16_be(PLAYER_OYSUB)
    local yspd = mainmemory.read_s16_be(PLAYER_YVEL)
    local rtn = mainmemory.read_u8(PLAYER_ROUTINE)
    return cy, ysub, yspd, rtn
end

local function fmtState(label, cy, ysub, yspd, rtn)
    return string.format("%s: cy=0x%04X ysub=0x%04X yspd=%d rtn=%d", label, cy, ysub, yspd, rtn)
end

-- Hook into known ROM routines to snapshot Y at key moments
local hooks = {}

local function addHook(addr, name)
    if addr then
        local id = event.onmemoryexecute(function()
            local cy, ysub, yspd, rtn = readState()
            log(fmtState("  EXEC " .. name .. string.format(" @0x%06X", addr), cy, ysub, yspd, rtn))
        end, addr)
        table.insert(hooks, id)
    end
end

print("Waiting for frame " .. TARGET_EMU_FRAME .. "...")

-- Search for SpeedToPos ROM address by looking for the pattern in known S1 locations
-- SpeedToPos is called by many routines. Let's hook Sonic_Hurt and Sonic_Control instead.
-- From the disassembly: Sonic_Hurt is at Sonic_Index + offset for routine 4
-- Sonic object code starts around 0x12C58 (varies by ROM version)
-- Rather than search, let's hook the specific addresses we care about:

-- Actually, let's use a simpler approach: just snapshot Y at the START and END
-- of the frame, and also at 100-instruction intervals within the frame.

local prevCy, prevYsub, prevYspd, prevRtn

while true do
    local f = emu.framecount()

    if f == TARGET_EMU_FRAME - 1 then
        prevCy, prevYsub, prevYspd, prevRtn = readState()
        log(fmtState("FRAME_START (pre-advance)", prevCy, prevYsub, prevYspd, prevRtn))
    end

    emu.frameadvance()
    f = emu.framecount()

    if f == TARGET_EMU_FRAME then
        local cy, ysub, yspd, rtn = readState()
        log(fmtState("FRAME_END", cy, ysub, yspd, rtn))

        if prevCy and cy ~= prevCy then
            log(string.format("  Y PIXEL CHANGED: 0x%04X -> 0x%04X (delta=%d)", prevCy, cy, cy - prevCy))
        elseif prevCy then
            log("  Y PIXEL UNCHANGED")
        end
        if prevYsub and ysub ~= prevYsub then
            log(string.format("  Y SUB CHANGED: 0x%04X -> 0x%04X", prevYsub, ysub))
        end

        -- Now step instruction-by-instruction through the NEXT frame to see
        -- exactly when Y changes. This is the hurt frame.
        log("")
        log("=== Instruction-level trace for frame " .. (TARGET_EMU_FRAME + 1) .. " ===")

        local startCy, startYsub = readState()
        local lastCy, lastYsub = startCy, startYsub
        local instrCount = 0
        local maxInstrs = 50000  -- safety limit
        local startFrameCount = emu.framecount()

        while emu.framecount() == startFrameCount and instrCount < maxInstrs do
            emu.setislagged(false)  -- prevent lag detection
            -- Step one instruction
            -- BizHawk doesn't have single-step in Lua for Genesis.
            -- Instead, advance the frame and check after.
            break  -- Can't single-step in BizHawk Lua for Genesis
        end

        -- Alternative: just log state at the END of the next frame
        emu.frameadvance()
        local endCy, endYsub, endYspd, endRtn = readState()
        log(fmtState("NEXT_FRAME_END (frame " .. (TARGET_EMU_FRAME + 1) .. ")", endCy, endYsub, endYspd, endRtn))
        if endCy ~= cy then
            log(string.format("  Y CHANGED DURING NEXT FRAME: 0x%04X -> 0x%04X", cy, endCy))
        end

        -- Write output
        local outFile = io.open("y_trace.txt", "w")
        for _, line in ipairs(output) do
            outFile:write(line .. "\n")
            print(line)
        end
        outFile:close()
        print("Written to y_trace.txt")
        client.exit()
        break
    end

    -- Log state for nearby frames
    if f >= TARGET_EMU_FRAME - 3 and f < TARGET_EMU_FRAME then
        local cy, ysub, yspd, rtn = readState()
        log(fmtState(string.format("EMU %d", f), cy, ysub, yspd, rtn))
        prevCy, prevYsub, prevYspd, prevRtn = cy, ysub, yspd, rtn
    end
end
