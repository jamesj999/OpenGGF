package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestTraceBinder {

    @Test
    public void testExactMatchReturnsNoError() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        assertFalse(result.hasDivergence());
        assertFalse(result.hasError());
    }

    @Test
    public void testPositionDivergenceWarning() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0051, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        assertTrue(result.hasDivergence());
        assertFalse(result.hasError());
        assertEquals(Severity.WARNING, result.fields().get("x").severity());
    }

    @Test
    public void testPositionDivergenceError() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0150, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("x").severity());
    }

    @Test
    public void testAirFlagMismatchIsError() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, true, false, 0);

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("air").severity());
    }

    @Test
    public void testSpeedSignChangeIsError() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0010, (short) 0x0000, (short) 0x0010,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) -0x0010, (short) 0x0000, (short) -0x0010,
            (byte) 0x00, false, false, 0);

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("x_speed").severity());
    }

    @Test
    public void testInputValidationMatch() {
        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        TraceFrame frame = new TraceFrame(0, 0x0008,
            (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
            (byte) 0, false, false, 0);
        assertTrue(binder.validateInput(frame, 0x0008));
    }

    @Test
    public void testInputValidationMismatch() {
        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        TraceFrame frame = new TraceFrame(0, 0x0008,
            (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
            (byte) 0, false, false, 0);
        assertFalse(binder.validateInput(frame, 0x0004));
    }
}
