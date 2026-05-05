package com.openggf.tests.trace.s3k;

import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kMgzF498AirRollPhysics {
    private static final Path TRACE_DIR = Path.of("src/test/resources/traces/s3k/mgz");
    private static final Path BK2_PATH = TRACE_DIR.resolve("s3k-mgz-sonic-tails.bk2");
    private static final int TARGET_FRAME = 0x01F2;

    @Test
    void airborneRollingFrame498UsesRomAirVelocityOrdering() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());

        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(BK2_PATH)
                .withRecordingStartFrame(TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace))
                .withZoneAndAct(0x02, 0x00)
                .startPosition(trace.metadata().startX(), trace.metadata().startY())
                .startPositionIsCentre()
                .build();

        TraceReplaySessionBootstrap.BootstrapResult boot =
                TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);
        TraceReplayBootstrap.ReplayStartState replayStart = boot.replayStart();
        int driveTraceIndex = replayStart.startingTraceIndex();
        TraceFrame previousDriveFrame = replayStart.hasSeededTraceState()
                ? trace.getFrame(replayStart.seededTraceIndex())
                : driveTraceIndex > 0 ? trace.getFrame(driveTraceIndex - 1) : null;

        TraceReplaySessionBootstrap.alignFrameCountersForReplayStart(
                previousDriveFrame,
                driveTraceIndex < trace.frameCount() ? trace.getFrame(driveTraceIndex) : null);

        while (driveTraceIndex <= TARGET_FRAME) {
            TraceFrame expected = trace.getFrame(driveTraceIndex);
            TraceExecutionPhase phase =
                    TraceReplayBootstrap.phaseForReplay(trace, previousDriveFrame, expected);
            if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                fixture.skipFrameFromRecording();
            } else {
                fixture.stepFrameFromRecording();
            }
            previousDriveFrame = expected;
            driveTraceIndex++;
        }

        TraceFrame expected = trace.getFrame(TARGET_FRAME);
        var sprite = fixture.sprite();
        assertEquals(expected.x(), sprite.getCentreX(), "MGZ F498 x");
        assertEquals(expected.y(), sprite.getCentreY(), "MGZ F498 y");
        assertEquals(expected.xSpeed(), sprite.getXSpeed(), "MGZ F498 x_speed");
        assertEquals(expected.ySpeed(), sprite.getYSpeed(), "MGZ F498 y_speed");
        assertEquals(expected.gSpeed(), sprite.getGSpeed(), "MGZ F498 g_speed");
        assertEquals(expected.air(), sprite.getAir(), "MGZ F498 air");
        assertEquals(expected.rolling(), sprite.getRolling(), "MGZ F498 rolling");
    }
}
