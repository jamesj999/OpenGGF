package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS2SyntheticV3Fixture {

    private static final Path SYNTHETIC_S2_V3 =
            Path.of("src/test/resources/traces/synthetic/s2_execution_v3_2frames");

    @Test
    void parsesS2MetadataAndMonotonicCounters() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_S2_V3);

        assertEquals(3, data.metadata().traceSchema());
        assertEquals("s2", data.metadata().game());

        TraceFrame f0 = data.getFrame(0);
        TraceFrame f1 = data.getFrame(1);

        assertTrue(f1.gameplayFrameCounter() > f0.gameplayFrameCounter());
        assertTrue(f1.vblankCounter() > f0.vblankCounter());
        assertEquals(0, f0.lagCounter());
        assertEquals(0, f1.lagCounter());
    }
}
