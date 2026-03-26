package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TestTraceDataParsing {

    private static final Path SYNTHETIC_3FRAMES =
        Path.of("src/test/resources/traces/synthetic/basic_3frames");

    @Test
    public void testMetadataLoading() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        TraceMetadata meta = data.metadata();

        assertEquals("s1", meta.game());
        assertEquals("ghz", meta.zone());
        assertEquals(1, meta.act());
        assertEquals(100, meta.bk2FrameOffset());
        assertEquals(3, meta.traceFrameCount());
        assertEquals((short) 0x0050, meta.startX());
        assertEquals((short) 0x03B0, meta.startY());
    }

    @Test
    public void testFrameCount() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        assertEquals(3, data.frameCount());
    }

    @Test
    public void testFrameParsing() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);

        TraceFrame frame0 = data.getFrame(0);
        assertEquals(0, frame0.frame());
        assertEquals(0, frame0.input());
        assertEquals((short) 0x0050, frame0.x());
        assertEquals((short) 0x03B0, frame0.y());
        assertEquals((short) 0, frame0.xSpeed());
        assertFalse(frame0.air());
        assertFalse(frame0.rolling());
        assertEquals(0, frame0.groundMode());

        TraceFrame frame1 = data.getFrame(1);
        assertEquals(0x0008, frame1.input());
        assertEquals((short) 0x0051, frame1.x());
        assertEquals((short) 0x000C, frame1.xSpeed());
        assertEquals((short) 0x000C, frame1.gSpeed());

        TraceFrame frame2 = data.getFrame(2);
        assertEquals((byte) 0x10, frame2.angle());
    }

    @Test
    public void testAuxEventsParsing() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);

        List<TraceEvent> frame0Events = data.getEventsForFrame(0);
        assertEquals(1, frame0Events.size());
        assertInstanceOf(TraceEvent.StateSnapshot.class, frame0Events.get(0));

        List<TraceEvent> frame2Events = data.getEventsForFrame(2);
        assertEquals(1, frame2Events.size());
        TraceEvent.ModeChange mc = (TraceEvent.ModeChange) frame2Events.get(0);
        assertEquals("angle", mc.field());
        assertEquals(0, mc.from());
        assertEquals(16, mc.to());
    }

    @Test
    public void testNoEventsForFrame() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        List<TraceEvent> events = data.getEventsForFrame(1);
        assertTrue(events.isEmpty());
    }

    @Test
    public void testEventRange() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_3FRAMES);
        List<TraceEvent> events = data.getEventsInRange(0, 2);
        assertEquals(2, events.size());
    }
}
