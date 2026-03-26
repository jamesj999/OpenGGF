package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class TestDivergenceReport {

    @Test
    void testEmptyReportHasNoErrors() {
        DivergenceReport report = new DivergenceReport(List.of());
        assertFalse(report.hasErrors());
        assertFalse(report.hasWarnings());
        assertTrue(report.errors().isEmpty());
    }

    @Test
    void testSingleErrorFrame() {
        FrameComparison frame = makeComparison(5, "air", Severity.ERROR, "0", "1");
        DivergenceReport report = new DivergenceReport(List.of(frame));

        assertTrue(report.hasErrors());
        assertEquals(1, report.errors().size());
        DivergenceGroup group = report.errors().get(0);
        assertEquals("air", group.field());
        assertEquals(5, group.startFrame());
        assertEquals(5, group.endFrame());
    }

    @Test
    void testConsecutiveFramesGrouped() {
        FrameComparison f1 = makeComparison(10, "x", Severity.ERROR, "0x0050", "0x0150");
        FrameComparison f2 = makeComparison(11, "x", Severity.ERROR, "0x0052", "0x0152");
        FrameComparison f3 = makeComparison(12, "x", Severity.ERROR, "0x0054", "0x0154");

        DivergenceReport report = new DivergenceReport(List.of(f1, f2, f3));
        assertEquals(1, report.errors().size());
        DivergenceGroup group = report.errors().get(0);
        assertEquals(10, group.startFrame());
        assertEquals(12, group.endFrame());
        assertEquals(3, group.frameSpan());
    }

    @Test
    void testWarningsAndErrorsSeparated() {
        FrameComparison f1 = makeComparison(0, "x", Severity.WARNING, "0x0050", "0x0051");
        FrameComparison f2 = makeComparison(1, "air", Severity.ERROR, "0", "1");

        DivergenceReport report = new DivergenceReport(List.of(f1, f2));
        assertTrue(report.hasErrors());
        assertTrue(report.hasWarnings());
        assertEquals(1, report.errors().size());
        assertEquals(1, report.warnings().size());
    }

    @Test
    void testSummaryOutput() {
        FrameComparison f1 = makeComparison(5, "air", Severity.ERROR, "0", "1");
        DivergenceReport report = new DivergenceReport(List.of(f1));
        String summary = report.toSummary();

        assertTrue(summary.contains("1 error"));
        assertTrue(summary.contains("frame 5"));
    }

    @Test
    void testContextWindow() {
        FrameComparison f0 = makeMatchComparison(0, (short) 0x50, (short) 0x3B0);
        FrameComparison f1 = makeMatchComparison(1, (short) 0x51, (short) 0x3B0);
        FrameComparison f2 = makeComparison(2, "air", Severity.ERROR, "0", "1");
        FrameComparison f3 = makeComparison(3, "air", Severity.ERROR, "0", "1");
        FrameComparison f4 = makeMatchComparison(4, (short) 0x54, (short) 0x3B0);

        DivergenceReport report = new DivergenceReport(List.of(f0, f1, f2, f3, f4));
        String context = report.getContextWindow(2, 2);

        assertNotNull(context);
        assertTrue(context.contains("Frame"));
    }

    private FrameComparison makeComparison(int frame, String field,
            Severity severity, String expected, String actual) {
        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        fields.put(field, new FieldComparison(field, expected, actual, severity,
            severity == Severity.MATCH ? 0 : 1));
        return new FrameComparison(frame, fields);
    }

    private FrameComparison makeMatchComparison(int frame, short x, short y) {
        Map<String, FieldComparison> fields = new LinkedHashMap<>();
        String xHex = String.format("0x%04X", x & 0xFFFF);
        String yHex = String.format("0x%04X", y & 0xFFFF);
        fields.put("x", new FieldComparison("x", xHex, xHex, Severity.MATCH, 0));
        fields.put("y", new FieldComparison("y", yHex, yHex, Severity.MATCH, 0));
        fields.put("air", new FieldComparison("air", "0", "0", Severity.MATCH, 0));
        return new FrameComparison(frame, fields);
    }
}
