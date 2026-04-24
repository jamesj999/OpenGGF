package com.openggf.tests.trace;

import com.openggf.trace.*;

import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceReplayStartPositionPolicy {

    @Test
    void s3kEndToEndTraceUsesLiveIntroSpawnInsteadOfRecordedFrameZeroPosition() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));
        TraceMetadata metadata = trace.metadata();

        AbstractTraceReplayTest subject = new AbstractTraceReplayTest() {
            @Override
            protected SonicGame game() {
                return SonicGame.SONIC_3K;
            }

            @Override
            protected int zone() {
                return 0;
            }

            @Override
            protected int act() {
                return 0;
            }

            @Override
            protected Path traceDirectory() {
                return Path.of("unused");
            }
        };

        Method method = AbstractTraceReplayTest.class.getDeclaredMethod(
                "shouldApplyMetadataStartPosition",
                TraceData.class,
                TraceMetadata.class);
        method.setAccessible(true);

        boolean shouldApply = (boolean) method.invoke(subject, trace, metadata);

        assertFalse(
                shouldApply,
                "The legacy S3K AIZ full-run trace starts from power-on state, so replay must "
                        + "keep the engine's live intro spawn instead of applying frame-zero "
                        + "start_x/start_y from stale Player_1 RAM.");
    }

    @Test
    void s3kEndToEndTraceWarmsUpToFirstStrictIntroFrame() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));

        assertTrue(TraceReplayBootstrap.shouldUseLegacyS3kAizIntroWarmup(trace));
        assertFalse(TraceReplayBootstrap.shouldSeedFrameZeroForTraceReplay(trace));
        assertEquals(0, TraceReplayBootstrap.replaySeedTraceIndexForTraceReplay(trace));
        // The strict start lands on the frame after the first ZoneActState with
        // game_mode=0x0C (first live LEVEL frame). For this trace the recorder
        // starts at the very first SEGA/title frame, so the first gm=0x0C event
        // is the AIZ1 intro-object activation at trace frame 289 → strict start 290.
        assertEquals(290, TraceReplayBootstrap.strictStartTraceIndexForTraceReplay(trace));
    }

    @Test
    void s3kGameplayTraceSeedsRecordedFrameZeroBeforeDrivingReplay() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/cnz"));

        assertFalse(trace.preTraceObjectSnapshots().isEmpty(),
                "CNZ records object snapshots for randomised balloon bob phases.");
        assertFalse(TraceReplayBootstrap.shouldUseLegacyS3kAizIntroWarmup(trace));
        assertEquals(0, TraceReplayBootstrap.replaySeedTraceIndexForTraceReplay(trace));
        assertTrue(TraceReplayBootstrap.shouldSeedFrameZeroForTraceReplay(trace));
        assertEquals(trace.metadata().bk2FrameOffset() + 1,
                TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace),
                "A seeded frame-0 replay resumes from trace frame 1, so the BK2 cursor "
                        + "must also advance past the already-restored frame 0 input.");
    }

    @Test
    void s3kGameplayTraceStillSeedsFrameZeroWhenObjectSnapshotsExist() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/cnz"));

        assertFalse(trace.preTraceObjectSnapshots().isEmpty());
        assertTrue(TraceReplayBootstrap.shouldSeedReplayStartStateForTraceReplay(trace, 0),
                "CNZ needs both pre-trace object hydration and primary frame-0 hydration.");
    }
}
