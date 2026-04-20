package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestS2SyntheticV3Fixture {

    private static final Path SYNTHETIC_S2_EXECUTION_V3 =
        Path.of("src/test/resources/traces/synthetic/s2_execution_v3_2frames");

    @Test
    void testS2SyntheticExecutionTraceParsesViaSharedTraceApi() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_S2_EXECUTION_V3);
        TraceMetadata metadata = data.metadata();
        TraceFrame frame0 = data.getFrame(0);
        TraceFrame frame1 = data.getFrame(1);

        assertEquals("s2", metadata.game());
        assertEquals("ehz", metadata.zone());
        assertEquals(0, metadata.zoneId());
        assertEquals(1, metadata.act());
        assertEquals("3.0-s2", metadata.luaScriptVersion());
        assertEquals(3, metadata.traceSchema());
        assertEquals(2, data.frameCount());

        assertTrue(frame1.gameplayFrameCounter() > frame0.gameplayFrameCounter());
        assertTrue(frame1.vblankCounter() > frame0.vblankCounter());
        assertEquals(0x2000, frame0.gameplayFrameCounter());
        assertEquals(0x0200, frame0.vblankCounter());
        assertEquals(0, frame0.lagCounter());
        assertEquals(0x2001, frame1.gameplayFrameCounter());
        assertEquals(0x0201, frame1.vblankCounter());
        assertEquals(0, frame1.lagCounter());
        assertEquals(0x0200, data.initialVblankCounter());

        assertEquals(1, data.getEventsForFrame(0).size());
        TraceEvent.RoutineChange routineChange = assertInstanceOf(
            TraceEvent.RoutineChange.class, data.getEventsForFrame(0).get(0));
        assertEquals(0, routineChange.frame());
        assertEquals("00", routineChange.from());
        assertEquals("02", routineChange.to());
    }
}
