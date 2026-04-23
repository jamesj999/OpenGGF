package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TestTraceExecutionModel {

    @Test
    void sonic1CounterDelta_fullLevelFrame() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 0);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0121, 0x3457, 0);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void sonic1VblankDeltaWithoutGameplayDelta_vblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 0);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0121, 0x3456, 0);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void sonic1NoCounterDelta_defaultsToFullFrame() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 0);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0120, 0x3456, 0);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void sonic2VblankDeltaWithoutGameplayDelta_vblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x0220, 0x1456, 0);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x0221, 0x1456, 0);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s2").phaseFor(previous, current));
    }

    @Test
    void sonic3kLagCounterDelta_vblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x2000, 0x0100, 3);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x2001, 0x0100, 4);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s3k").phaseFor(previous, current));
    }

    @Test
    void sonic3kLagCounterAloneDoesNotSelectVblankOnly() {
        TraceFrame previous = TraceFrame.executionTestFrame(0, 0x2000, 0x0100, 3);
        TraceFrame current = TraceFrame.executionTestFrame(1, 0x2000, 0x0100, 4);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s3k").phaseFor(previous, current));
    }

    @Test
    void legacyS3kAizIntroFramesReplayAsFullFramesBeforeGameplayStart() throws Exception {
        TraceData trace = TraceData.load(
                Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));
        TraceFrame previous = trace.getFrame(0);
        TraceFrame current = trace.getFrame(1);

        assertEquals(previous.gameplayFrameCounter(), current.gameplayFrameCounter());
        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceReplayBootstrap.phaseForReplay(trace, previous, current));
    }

    @Test
    void firstFrameDefaultsToFullLevelFrame() {
        TraceFrame current = TraceFrame.executionTestFrame(0, 0x0120, 0x3456, 0);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s1").phaseFor(null, current));
    }

    @Test
    void legacyTraceWithoutVblankCounter_fallsBackToStateHeuristic() {
        TraceFrame previous = new TraceFrame(0, 0,
                (short) 0x0050, (short) 0x03B0,
                (short) 0x000C, (short) 0x0000, (short) 0x000C,
                (byte) 0x00, false, false, 0,
                0, 0, -1, -1, -1, -1, -1,
                0, -1, -1, -1);
        TraceFrame current = new TraceFrame(1, 0,
                (short) 0x0050, (short) 0x03B0,
                (short) 0x000C, (short) 0x0000, (short) 0x000C,
                (byte) 0x00, false, false, 0,
                0, 0, -1, -1, -1, -1, -1,
                0, -1, -1, -1);

        assertEquals(TraceExecutionPhase.VBLANK_ONLY,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void legacyTraceWithoutVblankCounter_usesStateChangeForFullFrame() {
        TraceFrame previous = new TraceFrame(0, 0,
                (short) 0x0050, (short) 0x03B0,
                (short) 0x000C, (short) 0x0000, (short) 0x000C,
                (byte) 0x00, false, false, 0,
                0, 0, -1, -1, -1, -1, -1,
                0, -1, -1, -1);
        TraceFrame current = new TraceFrame(1, 0,
                (short) 0x0051, (short) 0x03B0,
                (short) 0x000C, (short) 0x0000, (short) 0x000C,
                (byte) 0x00, false, false, 0,
                0, 0, -1, -1, -1, -1, -1,
                0, -1, -1, -1);

        assertEquals(TraceExecutionPhase.FULL_LEVEL_FRAME,
                TraceExecutionModel.forGame("s1").phaseFor(previous, current));
    }

    @Test
    void unsupportedGameThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> TraceExecutionModel.forGame("bad"));
    }
}
