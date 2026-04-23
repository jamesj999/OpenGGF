local OUTPUT_PATH = "trace_output/s3k_handoff_diag.txt"
local WINDOW_FE_START = 0xFE00
local WINDOW_FE_LEN = 0x30
local WINDOW_EE_START = 0xEE50
local WINDOW_EE_LEN = 0x50
local PREVIEW_FRAMES = 360

local function hex_bytes(addr, len)
    local parts = {}
    for i = 0, len - 1 do
        parts[#parts + 1] = string.format("%02X", mainmemory.read_u8(addr + i))
    end
    return table.concat(parts, " ")
end

local function write_line(handle, line)
    handle:write(line .. "\n")
    handle:flush()
end

os.execute('mkdir "trace_output" 2>NUL')

local out = assert(io.open(OUTPUT_PATH, "w"))
write_line(out, "S3K handoff diagnostic")

local movie_length = 0
if movie.isloaded() then
    movie_length = movie.length()
end
local capture_start = math.max(0, movie_length - PREVIEW_FRAMES)
write_line(out, string.format("movie_length=%d capture_start=%d", movie_length, capture_start))

while true do
    local frame = emu.framecount()
    if movie.isloaded() and frame >= capture_start then
        local game_mode = mainmemory.read_u8(0xF600)
        local p1_x = mainmemory.read_u16_be(0xB010)
        local p1_y = mainmemory.read_u16_be(0xB014)
        local move_lock = mainmemory.read_u16_be(0xB032)
        local ctrl_locked = mainmemory.read_u8(0xF7CA)
        write_line(out, string.format(
                "frame=%d game_mode=%02X p1=(%04X,%04X) move_lock=%04X ctrl1_locked=%02X",
                frame, game_mode, p1_x, p1_y, move_lock, ctrl_locked))
        write_line(out, "FE: " .. hex_bytes(WINDOW_FE_START, WINDOW_FE_LEN))
        write_line(out, "EE: " .. hex_bytes(WINDOW_EE_START, WINDOW_EE_LEN))
    end

    if movie.isloaded() and movie.mode() == "FINISHED" then
        write_line(out, "movie finished")
        out:close()
        client.exit()
        break
    end

    emu.frameadvance()
end
