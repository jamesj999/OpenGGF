local OUTPUT_PATH = "trace_output/s3k_player_search.txt"

local TARGET_FRAMES = { 1897, 2048 }

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

local function read_candidate(addr)
    return {
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
        character_id = mainmemory.read_u8(addr + OFF_CHARACTER_ID)
    }
end

local function is_probable_code(code)
    return code ~= 0 and code ~= 0xFFFFFFFF and code < 0x400000
end

local function score_candidate(candidate, camera_x, camera_y)
    if not is_probable_code(candidate.code) then
        return nil
    end

    local score = 0
    local dx = math.abs(candidate.x - camera_x)
    local dy = math.abs(candidate.y - camera_y)

    if candidate.character_id <= 2 then
        score = score + 3
    end
    if candidate.routine == 0x06 or candidate.routine == 0x04 then
        score = score + 2
    end
    if candidate.move_lock < 0x0100 then
        score = score + 1
    end
    if candidate.object_control < 0x90 then
        score = score + 1
    end
    if dx <= 0x0200 then
        score = score + 2
    elseif dx <= 0x0400 then
        score = score + 1
    end
    if dy <= 0x0200 then
        score = score + 2
    elseif dy <= 0x0400 then
        score = score + 1
    end
    if candidate.x ~= 0 or candidate.y ~= 0 then
        score = score + 1
    end

    if score < 5 then
        return nil
    end
    return score
end

local function candidate_summary(candidate, score, camera_x, camera_y)
    return string.format(
        "score=%02d addr=%04X code=%08X rtn=%02X char=%02X x=%04X y=%04X dx=%04X dy=%04X xv=%6d yv=%6d gv=%6d stat=%02X ctrl=%02X move=%04X",
        score,
        candidate.addr,
        candidate.code,
        candidate.routine,
        candidate.character_id,
        candidate.x,
        candidate.y,
        math.abs(candidate.x - camera_x),
        math.abs(candidate.y - camera_y),
        candidate.x_vel,
        candidate.y_vel,
        candidate.g_vel,
        candidate.status,
        candidate.object_control,
        candidate.move_lock)
end

local function scan_frame(frame, out)
    while emu.framecount() < frame do
        if movie.isloaded() and movie.mode() == "FINISHED" then
            out:write("movie finished before target frame\n")
            out:close()
            client.exit()
            return false
        end
        emu.frameadvance()
    end

    local camera_x = mainmemory.read_u16_be(ADDR_CAMERA_X)
    local camera_y = mainmemory.read_u16_be(ADDR_CAMERA_Y)

    local matches = {}
    for addr = 0, 0xFFB5, 2 do
        local candidate = read_candidate(addr)
        local score = score_candidate(candidate, camera_x, camera_y)
        if score ~= nil then
            matches[#matches + 1] = {
                score = score,
                text = candidate_summary(candidate, score, camera_x, camera_y)
            }
        end
    end

    table.sort(matches, function(a, b)
        if a.score ~= b.score then
            return a.score > b.score
        end
        return a.text < b.text
    end)

    out:write(string.rep("=", 80) .. "\n")
    out:write(string.format("frame=%d camera=(%04X,%04X) candidates=%d\n", emu.framecount(), camera_x, camera_y, #matches))
    local limit = math.min(#matches, 80)
    for i = 1, limit do
        out:write(matches[i].text .. "\n")
    end
    out:flush()
    return true
end

local out = assert(io.open(OUTPUT_PATH, "w"))
out:write("S3K player search\n")

for _, frame in ipairs(TARGET_FRAMES) do
    if not scan_frame(frame, out) then
        return
    end
end

out:close()
client.exit()
