package com.openggf.trace.live;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveTraceComparatorTest {

    @Test
    void skipIncrementsLagCounter() {
        LiveTraceComparator c = new LiveTraceComparator(
                stubTrace(List.of(
                        TraceFrame.executionTestFrame(0, 10, 0x100, 0),
                        TraceFrame.executionTestFrame(1, 10, 0x100, 1))),
                ToleranceConfig.DEFAULT,
                0,
                () -> null);
        Bk2FrameInput empty = new Bk2FrameInput(0, 0, 0, false, "0");
        c.afterFrameAdvanced(empty, true);
        assertEquals(1, c.laggedFrames());
        assertEquals(0, c.errorCount());
    }

    @Test
    void shouldSkipGameplayTickDelegatesToPhase() {
        // First two frames share the same gameplay_frame_counter → second is VBLANK_ONLY
        LiveTraceComparator c = new LiveTraceComparator(
                stubTrace(List.of(
                        TraceFrame.executionTestFrame(0, 10, 0x100, 0),
                        TraceFrame.executionTestFrame(1, 11, 0x100, 1))),
                ToleranceConfig.DEFAULT,
                0,
                () -> null);
        Bk2FrameInput empty = new Bk2FrameInput(1, 0, 0, false, "0");
        // Advance our internal cursor past index 0 first:
        c.afterFrameAdvanced(new Bk2FrameInput(0, 0, 0, false, "0"), false);
        assertTrue(c.shouldSkipGameplayTick(empty));
    }

    private static TraceData stubTrace(List<TraceFrame> frames) {
        return TraceData.ofFrames(TraceMetadata.forTest("s2", 0, 0), frames);
    }
}
