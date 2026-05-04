package com.openggf.tests.trace;

import com.openggf.trace.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestTraceBinder {

    @Test
    public void testExactMatchReturnsNoError() {
        TraceFrame frame = TraceFrame.of(0, 0x0000,
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
    public void testDefaultPositionDivergenceIsError() {
        TraceFrame frame = TraceFrame.of(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0051, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0);

        assertTrue(result.hasDivergence());
        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("x").severity());
    }

    @Test
    public void testPositionDivergenceError() {
        TraceFrame frame = TraceFrame.of(0, 0x0000,
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
        TraceFrame frame = TraceFrame.of(0, 0x0000,
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
        TraceFrame frame = TraceFrame.of(0, 0x0000,
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
        TraceFrame frame = TraceFrame.of(0, 0x0008,
            (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
            (byte) 0, false, false, 0);
        assertTrue(binder.validateInput(frame, 0x0008));
    }

    @Test
    public void testInputValidationMismatch() {
        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        TraceFrame frame = TraceFrame.of(0, 0x0008,
            (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
            (byte) 0, false, false, 0);
        assertFalse(binder.validateInput(frame, 0x0004));
    }

    @Test
    void testSidekickStateMismatchIsReported() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            new TraceCharacterState(true,
                (short) 0x0040, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null,
            new TraceCharacterState(true,
                (short) 0x0142, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("sidekick_x").severity());
    }

    @Test
    void testNamedCharacterLabelIsUsedForSecondaryComparisons() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            new TraceCharacterState(true,
                (short) 0x0040, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, null, "tails",
            new TraceCharacterState(true,
                (short) 0x0142, (short) 0x03A0,
                (short) 0x0010, (short) 0x0000, (short) 0x0010,
                (byte) 0x00, false, false, 0,
                0, 0, 0x02, 0x00, 0x00));

        assertTrue(result.hasError());
        assertTrue(result.fields().containsKey("tails_x"));
        assertEquals(Severity.ERROR, result.fields().get("tails_x").severity());
    }

    @Test
    void testRingCountMismatchIsWarningWhenConfiguredWarnOnly() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0, 0, 7, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(new ToleranceConfig(
            1, 1, 1, 1, true, 1, 1, ToleranceConfig.RingCountMode.WARN_ONLY));
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, 8, 0, 0,
                -1, -1, -1, -1, "", 0, 0));

        assertTrue(result.hasDivergence());
        assertFalse(result.hasError());
        assertEquals(Severity.WARNING, result.fields().get("rings").severity());
    }

    @Test
    void testDefaultRingCountMismatchIsError() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0, 0, 7, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(ToleranceConfig.DEFAULT);
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, 8, 0, 0,
                -1, -1, -1, -1, "", 0, 0));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("rings").severity());
    }

    @Test
    void testWithRingCountModeFactoryDowngradesToWarning() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0, 0, 7, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(
            ToleranceConfig.DEFAULT.withRingCountMode(ToleranceConfig.RingCountMode.WARN_ONLY));
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, 8, 0, 0,
                -1, -1, -1, -1, "", 0, 0));

        assertFalse(result.hasError());
        assertEquals(Severity.WARNING, result.fields().get("rings").severity());
    }

    @Test
    void testRingCountMismatchIsErrorWhenConfiguredForceError() {
        TraceFrame frame = new TraceFrame(0, 0x0000,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            0, 0, 0x02, 0, 0, 7, 0, 1, -1, -1, -1, null);

        TraceBinder binder = new TraceBinder(new ToleranceConfig(
            1, 1, 1, 1, true, 1, 1, ToleranceConfig.RingCountMode.FORCE_ERROR));
        FrameComparison result = binder.compareFrame(frame,
            (short) 0x0050, (short) 0x03B0,
            (short) 0x0000, (short) 0x0000, (short) 0x0000,
            (byte) 0x00, false, false, 0,
            null, new EngineDiagnostics(0x02, -1, -1, 8, 0, 0,
                -1, -1, -1, -1, "", 0, 0));

        assertTrue(result.hasError());
        assertEquals(Severity.ERROR, result.fields().get("rings").severity());
    }
}
