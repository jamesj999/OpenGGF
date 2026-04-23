local OUTPUT_PATH = "trace_output/s3k_domain_probe.txt"

local TARGET_FRAME = 1897

local PROBE_ADDRS = {
    { name = "camera_x", addr = 0xEE78, kind = "u16" },
    { name = "zone_act", addr = 0xFE10, kind = "u16" },
    { name = "player_code", addr = 0xB400, kind = "u32" },
    { name = "player_x", addr = 0xB410, kind = "u16" },
    { name = "player_y", addr = 0xB414, kind = "u16" },
    { name = "player_move_lock", addr = 0xB432, kind = "u16" }
}

local BUS_PROBE_ADDRS = {
    { name = "camera_x", addr = 0xFFEE78, kind = "u16" },
    { name = "zone_act", addr = 0xFFFE10, kind = "u16" },
    { name = "player_code", addr = 0xFFB400, kind = "u32" },
    { name = "player_x", addr = 0xFFB410, kind = "u16" },
    { name = "player_y", addr = 0xFFB414, kind = "u16" },
    { name = "player_move_lock", addr = 0xFFB432, kind = "u16" }
}

local function read_value(domain, addr, kind)
    memory.usememorydomain(domain)
    if kind == "u8" then
        return string.format("%02X", memory.read_u8(addr))
    end
    if kind == "u16" then
        return string.format("%04X", memory.read_u16_be(addr))
    end
    if kind == "u32" then
        return string.format("%08X", memory.read_u32_be(addr))
    end
    return "?"
end

local function read_mainmemory(addr, kind)
    if kind == "u8" then
        return string.format("%02X", mainmemory.read_u8(addr))
    end
    if kind == "u16" then
        return string.format("%04X", mainmemory.read_u16_be(addr))
    end
    if kind == "u32" then
        return string.format("%08X", mainmemory.read_u32_be(addr))
    end
    return "?"
end

local out = assert(io.open(OUTPUT_PATH, "w"))
out:write("S3K domain probe\n")

local domains = memory.getmemorydomainlist()
out:write("domains:\n")
for _, domain in ipairs(domains) do
    out:write("  " .. domain .. "\n")
end
out:flush()

while emu.framecount() < TARGET_FRAME do
    if movie.isloaded() and movie.mode() == "FINISHED" then
        out:write("movie finished before target frame\n")
        out:close()
        client.exit()
        return
    end
    emu.frameadvance()
end

out:write(string.format("\nframe=%d\n", emu.framecount()))
out:write("[mainmemory]\n")
for _, probe in ipairs(PROBE_ADDRS) do
    local ok, value = pcall(read_mainmemory, probe.addr, probe.kind)
    if ok then
        out:write(string.format("  %s @ %04X = %s\n", probe.name, probe.addr, value))
    else
        out:write(string.format("  %s @ %04X = <error: %s>\n", probe.name, probe.addr, tostring(value)))
    end
end
for _, domain in ipairs(domains) do
    out:write("[" .. domain .. "]\n")
    local active_probes = PROBE_ADDRS
    if domain == "M68K BUS" then
        active_probes = BUS_PROBE_ADDRS
    end
    for _, probe in ipairs(active_probes) do
        local ok, value = pcall(read_value, domain, probe.addr, probe.kind)
        if ok then
            out:write(string.format("  %s @ %X = %s\n", probe.name, probe.addr, value))
        else
            out:write(string.format("  %s @ %X = <error: %s>\n", probe.name, probe.addr, tostring(value)))
        end
    end
end

out:close()
client.exit()
