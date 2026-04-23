package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceRecorderCounterAddresses {

    private static final Path TOOLS_DIR = Path.of("tools", "bizhawk");

    @Test
    void sonic1RecorderUsesDisassemblyBackedExecutionCounters() throws IOException {
        String script = Files.readString(TOOLS_DIR.resolve("s1_trace_recorder.lua"));

        assertTrue(script.contains("local ADDR_FRAMECOUNT      = 0xFE04"));
        assertTrue(script.contains("local ADDR_VBLA_WORD       = 0xFE0E"));
    }

    @Test
    void sonic2RecorderUsesDisassemblyBackedExecutionCounters() throws IOException {
        String script = Files.readString(TOOLS_DIR.resolve("s2_trace_recorder.lua"));

        assertTrue(script.contains("local ADDR_FRAMECOUNT      = 0xFE04"));
        assertTrue(script.contains("local ADDR_VBLA_WORD       = 0xFE0C"));
    }

    @Test
    void sonic3kRecorderUsesDisassemblyBackedExecutionCounters() throws IOException {
        String script = Files.readString(TOOLS_DIR.resolve("s3k_trace_recorder.lua"));

        assertTrue(script.contains("local ADDR_FRAMECOUNT       = 0xFE08"));
        assertTrue(script.contains("local ADDR_VBLA_WORD        = 0xFE12"));
        assertTrue(script.contains("local ADDR_LAG_FRAME_COUNT  = 0xF628"));
    }
}
