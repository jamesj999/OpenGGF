package com.openggf.tests.trace.s3k;

import com.openggf.game.GameServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kCnzTraceReplay extends AbstractTraceReplayTest {

    private static final Path TRACE_DIR = Path.of("src/test/resources/traces/s3k/cnz");
    private static final int FRAME_FIRST_CARRY_RIGHT_PULSE = 31;
    private static final int FRAME_DELAYED_RIGHT_REACHES_TAILS = 123;
    private static final int FRAME_FIRST_MAIN_JUMP = 142;
    private static final int FRAME_HORIZONTAL_SPRING_LANDING_HANDOFF = 3649;

    @Override
    protected SonicGame game() {
        return SonicGame.SONIC_3K;
    }

    @Override
    protected int zone() {
        return 0x03; // CNZ
    }

    @Override
    protected int act() {
        // 0-based: 0 = Act 1. The recorder's metadata.json writes "act": 1
        // (1-based). AbstractTraceReplayTest.validateMetadata does not
        // cross-check the act, so the asymmetry is harmless; documenting
        // here so the next reader doesn't chase the off-by-one.
        return 0x00;
    }

    @Override
    protected Path traceDirectory() {
        return TRACE_DIR;
    }

    @Test
    void traceReplayDoesNotPulseCarryRightOnFrame1() throws Exception {
        try (BootstrappedCnzReplay replay = bootstrappedCnzReplay()) {
            driveReplayToTraceFrame(replay.trace(), replay.fixture(), replay.replayStart(), 1);

            AbstractPlayableSprite tails = GameServices.sprites().getRegisteredSidekicks().getFirst();
            assertEquals(SidekickCpuController.State.CARRYING, tails.getCpuController().getState(),
                    "Frame 1: ROM Tails_CPU_routine has reached $0E carrying");
            assertEquals(traceFrame(replay.trace(), 1).sidekick().xSpeed(),
                    tails.getXSpeed(),
                    "Frame 1: loc_13FFA reads Level_frame_counter low byte=$02, so no RIGHT pulse yet");
        }
    }

    @Test
    void traceReplayAppliesFirstCarryRightPulseOnFrame31() throws Exception {
        try (BootstrappedCnzReplay replay = bootstrappedCnzReplay()) {
            driveReplayToTraceFrame(
                    replay.trace(),
                    replay.fixture(),
                    replay.replayStart(),
                    FRAME_FIRST_CARRY_RIGHT_PULSE);

            AbstractPlayableSprite tails = GameServices.sprites().getRegisteredSidekicks().getFirst();
            assertEquals(SidekickCpuController.State.CARRYING, tails.getCpuController().getState(),
                    "Frame 31: ROM Tails_CPU_routine is $0E carrying");
            assertEquals(traceFrame(replay.trace(), FRAME_FIRST_CARRY_RIGHT_PULSE).sidekick().xSpeed(),
                    tails.getXSpeed(),
                    "Frame 31: loc_13FFA should pulse RIGHT when "
                            + "(Level_frame_counter+1)&$1F == 0, then Tails_InputAcceleration_Freespace "
                            + "raises x_vel to $118");
        }
    }

    @Test
    void traceReplayAppliesDelayedRightInputToTailsOnFrame123() throws Exception {
        try (BootstrappedCnzReplay replay = bootstrappedCnzReplay()) {
            driveReplayToTraceFrame(
                    replay.trace(),
                    replay.fixture(),
                    replay.replayStart(),
                    FRAME_DELAYED_RIGHT_REACHES_TAILS);

            AbstractPlayableSprite tails = GameServices.sprites().getRegisteredSidekicks().getFirst();
            assertEquals(SidekickCpuController.State.NORMAL, tails.getCpuController().getState(),
                    "Frame 123: ROM Tails_CPU_routine is $06 normal follow");
            assertEquals(traceFrame(replay.trace(), FRAME_DELAYED_RIGHT_REACHES_TAILS).sidekick().xSpeed(),
                    tails.getXSpeed(),
                    "Frame 123: loc_13D4A should replay Sonic's delayed RIGHT input through "
                            + "Tails_InputAcceleration_Freespace");
        }
    }

    @Test
    void traceReplayAppliesFirstMainJumpOnFrame142() throws Exception {
        try (BootstrappedCnzReplay replay = bootstrappedCnzReplay()) {
            driveReplayToTraceFrame(
                    replay.trace(),
                    replay.fixture(),
                    replay.replayStart(),
                    FRAME_FIRST_MAIN_JUMP);

            TraceFrame expected = traceFrame(replay.trace(), FRAME_FIRST_MAIN_JUMP);
            AbstractPlayableSprite sonic = replay.fixture().sprite();
            assertEquals(expected.input(), AbstractPlayableSprite.INPUT_RIGHT | AbstractPlayableSprite.INPUT_JUMP,
                    "Frame 142 trace row should carry RIGHT+jump input");
            assertEquals(expected.x(), sonic.getCentreX() & 0xFFFF,
                    "Sonic_Jump consumes Ctrl_1_pressed_logical before ground SpeedToPos");
            assertEquals(expected.ySpeed(), sonic.getYSpeed(),
                    "Sonic_Jump should apply the level-ground -$680 y_vel on the first pressed frame");
            assertEquals(expected.air(), sonic.getAir(),
                    "Sonic_Jump should set Status_InAir on the first pressed frame");
            assertEquals(expected.rolling(), sonic.getRolling(),
                    "Sonic_Jump should enter roll/jump radii when jumping from standing");
        }
    }

    @Test
    void traceReplayHorizontalSpringLandingHandoffMatchesFrame3649() throws Exception {
        try (BootstrappedCnzReplay replay = bootstrappedCnzReplay()) {
            driveReplayToTraceFrame(
                    replay.trace(),
                    replay.fixture(),
                    replay.replayStart(),
                    FRAME_HORIZONTAL_SPRING_LANDING_HANDOFF);

            TraceFrame expected = traceFrame(replay.trace(), FRAME_HORIZONTAL_SPRING_LANDING_HANDOFF);
            AbstractPlayableSprite tails = GameServices.sprites().getRegisteredSidekicks().getFirst();
            assertEquals(expected.sidekick().x(), tails.getCentreX() & 0xFFFF,
                    "Frame 3649: ROM skips the horizontal spring side push and reaches "
                            + "sub_2326C's proactive trigger from outside the side box");
            assertEquals(expected.sidekick().xSpeed(), tails.getXSpeed(),
                    "Frame 3649: proactive horizontal spring trigger applies the left spring velocity");
        }
    }

    private static BootstrappedCnzReplay bootstrappedCnzReplay() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        var configSnapshot = TraceReplaySessionBootstrap.snapshotGameplayConfig();
        TraceReplaySessionBootstrap.prepareConfiguration(trace, trace.metadata());

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0x03, 0x00);
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .withRecording(TRACE_DIR.resolve("s3k-cnz-sonic-tails.bk2"))
                .withRecordingStartFrame(TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace))
                .startPosition(trace.metadata().startX(), trace.metadata().startY())
                .startPositionIsCentre()
                .build();

        TraceReplaySessionBootstrap.BootstrapResult boot =
                TraceReplaySessionBootstrap.applyBootstrap(trace, fixture, -1);
        TraceReplayBootstrap.ReplayStartState replayStart = boot.replayStart();
        TraceFrame previousDriveFrame = replayStart.hasSeededTraceState()
                ? trace.getFrame(replayStart.seededTraceIndex())
                : null;
        TraceReplaySessionBootstrap.alignFrameCountersForReplayStart(
                previousDriveFrame,
                trace.getFrame(replayStart.startingTraceIndex()));
        return new BootstrappedCnzReplay(trace, sharedLevel, fixture, replayStart,
                configSnapshot);
    }

    private static TraceFrame traceFrame(TraceData trace, int frame) {
        return trace.getFrame(frame);
    }

    private static void driveReplayToTraceFrame(TraceData trace,
                                                HeadlessTestFixture fixture,
                                                TraceReplayBootstrap.ReplayStartState replayStart,
                                                int targetTraceFrame) {
        TraceFrame previousDriveFrame = replayStart.hasSeededTraceState()
                ? trace.getFrame(replayStart.seededTraceIndex())
                : null;
        for (int traceIndex = replayStart.startingTraceIndex();
             traceIndex <= targetTraceFrame;
             traceIndex++) {
            TraceFrame driveFrame = trace.getFrame(traceIndex);
            TraceExecutionPhase phase =
                    TraceReplayBootstrap.phaseForReplay(trace, previousDriveFrame, driveFrame);
            if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                fixture.skipFrameFromRecording();
            } else {
                fixture.stepFrameFromRecording();
            }
            previousDriveFrame = driveFrame;
        }
    }

    private record BootstrappedCnzReplay(TraceData trace,
                                         SharedLevel sharedLevel,
                                         HeadlessTestFixture fixture,
                                         TraceReplayBootstrap.ReplayStartState replayStart,
                                         TraceReplaySessionBootstrap.ConfigSnapshot configSnapshot)
            implements AutoCloseable {
        @Override
        public void close() {
            sharedLevel.dispose();
            TraceReplaySessionBootstrap.restoreGameplayConfig(configSnapshot);
        }
    }
}
