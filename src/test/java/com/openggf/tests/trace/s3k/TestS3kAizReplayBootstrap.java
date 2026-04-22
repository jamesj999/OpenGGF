package com.openggf.tests.trace.s3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.sonic3k.objects.AizLrzRockObjectInstance;
import com.openggf.game.sonic3k.objects.FloatingPlatformObjectInstance;
import com.openggf.game.sonic3k.objects.RockDebrisChild;
import com.openggf.game.sonic3k.objects.badniks.MonkeyDudeBadnikInstance;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.TraceData;
import com.openggf.tests.trace.TraceExecutionModel;
import com.openggf.tests.trace.TraceExecutionPhase;
import com.openggf.tests.trace.TraceEvent;
import com.openggf.tests.trace.TraceFrame;
import com.openggf.tests.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAizReplayBootstrap {

    private static final Path TRACE_DIR =
            Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun");
    private static final String PRE_TRACE_OSC_OVERRIDE_PROPERTY = "s3k.aiz.preTraceOscOverride";

    @Test
    void resumesLegacyS3kAizReplayAtRecordedGameplayStartAnchor() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int gameplayStartFrame = findCheckpointFrame(trace, "gameplay_start");
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .withRecording(TRACE_DIR.resolve("s3-aiz1-2-sonictails.bk2"))
                    .withRecordingStartFrame(trace.metadata().bk2FrameOffset())
                    .startPosition(trace.metadata().startX(), trace.metadata().startY())
                    .startPositionIsCentre()
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            int preTraceOsc = trace.metadata().preTraceOscillationFrames();
            for (int i = 0; i < preTraceOsc; i++) {
                com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
            }

            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            var gameplayStart = trace.getFrame(gameplayStartFrame);
            assertEquals(gameplayStartFrame + 1, replayStart.startingTraceIndex());
            assertEquals(gameplayStartFrame, replayStart.seededTraceIndex());
            assertEquals(gameplayStart.x(), fixture.sprite().getCentreX());
            assertEquals(gameplayStart.y(), fixture.sprite().getCentreY());
            assertEquals(gameplayStart.xSpeed(), fixture.sprite().getXSpeed());
            assertEquals(gameplayStart.ySpeed(), fixture.sprite().getYSpeed());
            assertEquals(gameplayStart.gSpeed(), fixture.sprite().getGSpeed());
            assertEquals(gameplayStart.air(), fixture.sprite().getAir());
            assertEquals(gameplayStart.cameraX(), GameServices.camera().getX() & 0xFFFF);
            assertEquals(gameplayStart.cameraY(), GameServices.camera().getY() & 0xFFFF);
            assertFalse(fixture.sprite().isControlLocked());
            assertTrue(GameServices.camera().isLevelStarted());
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void reachesRecordedGameplayStartAnchorForLegacyS3kAizTrace() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int gameplayStartFrame = findCheckpointFrame(trace, "gameplay_start");
        int nextTraceFrame = gameplayStartFrame + 1;
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .withRecording(TRACE_DIR.resolve("s3-aiz1-2-sonictails.bk2"))
                    .withRecordingStartFrame(trace.metadata().bk2FrameOffset())
                    .startPosition(trace.metadata().startX(), trace.metadata().startY())
                    .startPositionIsCentre()
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            int preTraceOsc = trace.metadata().preTraceOscillationFrames();
            for (int i = 0; i < preTraceOsc; i++) {
                com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
            }

            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            fixture.stepFrameFromRecording();

            var expected = trace.getFrame(nextTraceFrame);
            assertEquals(nextTraceFrame, replayStart.startingTraceIndex());
            assertEquals(expected.x(), fixture.sprite().getCentreX());
            assertEquals(expected.y(), fixture.sprite().getCentreY());
            assertEquals(expected.xSpeed(), fixture.sprite().getXSpeed());
            assertEquals(expected.ySpeed(), fixture.sprite().getYSpeed());
            assertEquals(expected.gSpeed(), fixture.sprite().getGSpeed());
            assertEquals(expected.cameraX(), GameServices.camera().getX() & 0xFFFF);
            assertEquals(expected.cameraY(), GameServices.camera().getY() & 0xFFFF);
            assertFalse(fixture.sprite().isControlLocked());
            assertTrue(GameServices.camera().isLevelStarted());
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void clearsIntroOverlayAndForcedInputAtGameplayStartAnchor() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            TitleCardProvider titleCardProvider = GameServices.module().getTitleCardProvider();
            ObjectManager objectManager = GameServices.level().getObjectManager();
            ObjectInstance ridingObject = objectManager != null
                    ? objectManager.getRidingObject(fixture.sprite())
                    : null;
            assertEquals(0, fixture.sprite().getForcedInputMask());
            assertFalse(fixture.sprite().isForceInputRight());
            assertFalse(fixture.sprite().hasSpeedShoes());
            assertFalse(fixture.sprite().isSuperSonic());
            assertEquals(0x0C, fixture.sprite().getRunAccel());
            assertFalse(fixture.sprite().isOnObject());
            assertNull(ridingObject);
            assertFalse(titleCardProvider != null && titleCardProvider.isOverlayActive());
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void matchesFirstGroundAccelerationAfterGameplayStartAnchor() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int gameplayStartFrame = findCheckpointFrame(trace, "gameplay_start");
        int preAccelerationFrame = 0x0606;
        int firstAccelerationFrame = 0x0607;
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            int lastInput = advanceReplayToTraceFrame(trace, fixture, replayStart, preAccelerationFrame);

            TraceFrame preAccelerationExpected = trace.getFrame(preAccelerationFrame);
            assertFrameMatches(preAccelerationExpected, fixture, lastInput);

            lastInput = stepReplayFrame(trace, fixture, preAccelerationFrame);

            TraceFrame expected = trace.getFrame(firstAccelerationFrame);
            assertFrameMatches(expected, fixture, lastInput);
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void raisesFireTransitionSignalOnRecordedLegacyCheckpointFrame() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int fireTransitionFrame = findCheckpointFrame(trace, "aiz1_fire_transition_begin");
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            int lastInput = advanceReplayToTraceFrame(trace, fixture, replayStart, fireTransitionFrame);
            TraceFrame expected = trace.getFrame(fireTransitionFrame);
            Sonic3kLevelEventManager events =
                    (Sonic3kLevelEventManager) GameServices.module().getLevelEventProvider();

            assertFrameMatches(expected, fixture, lastInput);
            assertTrue(events.isEventsFg5(), describeSpriteState(fixture, expected, lastInput));
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void matchesLegacyReplayShortlyAfterFireTransitionCheckpoint() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int probeFrame = 1800;
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            int lastInput = advanceReplayToTraceFrame(trace, fixture, replayStart, probeFrame);
            TraceFrame expected = trace.getFrame(probeFrame);

            assertFrameMatches(expected, fixture, lastInput);
            assertEquals(expected.cameraX(), GameServices.camera().getX() & 0xFFFF, describeSpriteState(fixture, expected, lastInput));
            assertEquals(expected.cameraY(), GameServices.camera().getY() & 0xFFFF, describeSpriteState(fixture, expected, lastInput));
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void keepsMonkeyDudeBodyAtLegacyHeightBeforeRecordedStomp() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int probeFrame = 1833;
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            int lastInput = advanceReplayToTraceFrame(trace, fixture, replayStart, probeFrame);
            TraceFrame expected = trace.getFrame(probeFrame);

            assertFrameMatches(expected, fixture, lastInput);

            ObjectManager objectManager = GameServices.level().getObjectManager();
            MonkeyDudeBadnikInstance monkey = objectManager.getActiveObjects().stream()
                    .filter(MonkeyDudeBadnikInstance.class::isInstance)
                    .map(MonkeyDudeBadnikInstance.class::cast)
                    .filter(instance -> Math.abs(instance.getX() - fixture.sprite().getCentreX()) <= 0x10)
                    .min((a, b) -> Integer.compare(
                            Math.abs(a.getX() - fixture.sprite().getCentreX()),
                            Math.abs(b.getX() - fixture.sprite().getCentreX())))
                    .orElse(null);

            assertNotNull(monkey, describeSpriteState(fixture, expected, lastInput));
            assertEquals(0x1838, monkey.getX(), describeSpriteState(fixture, expected, lastInput));
            assertEquals(0x0410, monkey.getY(), describeSpriteState(fixture, expected, lastInput));
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void keepsRollingHitboxAtFirstPostSpringAirGSpeedResetFrame() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int probeFrame = 2006;
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            int lastInput = advanceReplayToTraceFrame(trace, fixture, replayStart, probeFrame);
            TraceFrame expected = trace.getFrame(probeFrame);

            assertTrue(fixture.sprite().getAir(), describeSpriteState(fixture, expected, lastInput));
            assertTrue(fixture.sprite().getRolling(), describeSpriteState(fixture, expected, lastInput));
            assertEquals(7, fixture.sprite().getXRadius(), describeSpriteState(fixture, expected, lastInput));
            assertEquals(14, fixture.sprite().getYRadius(), describeSpriteState(fixture, expected, lastInput));
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void matchesLegacyReplayAtFirstPostSpringAirGSpeedResetFrame() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int probeFrame = 2006;
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            int lastInput = advanceReplayToTraceFrame(trace, fixture, replayStart, probeFrame);
            TraceFrame expected = trace.getFrame(probeFrame);
            String state = describeSpriteState(fixture, expected, lastInput);

            assertFrameMatches(expected, fixture, lastInput);
            assertEquals(expected.ySpeed(), fixture.sprite().getYSpeed(), state);
            assertEquals(expected.angle() & 0xFF, fixture.sprite().getAngle() & 0xFF, state);
            assertEquals(expected.air(), fixture.sprite().getAir(), state);
            assertEquals(expected.rolling(), fixture.sprite().getRolling(), state);
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void breaksRecordedAizRollRockIntoDebrisAtPostFireContactFrame() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int probeFrame = 2097;
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            int lastInput = advanceReplayToTraceFrame(trace, fixture, replayStart, probeFrame);
            TraceFrame expected = trace.getFrame(probeFrame);

            ObjectManager objectManager = GameServices.level().getObjectManager();
            AizLrzRockObjectInstance intactRock = objectManager.getActiveObjects().stream()
                    .filter(AizLrzRockObjectInstance.class::isInstance)
                    .map(AizLrzRockObjectInstance.class::cast)
                    .filter(instance -> instance.getSpawn() != null
                            && instance.getSpawn().x() == 0x1980
                            && instance.getSpawn().y() == 0x0424)
                    .findFirst()
                    .orElse(null);
            long debrisCount = objectManager.getActiveObjects().stream()
                    .filter(RockDebrisChild.class::isInstance)
                    .filter(instance -> Math.abs(instance.getX() - 0x1980) <= 0x20
                            && Math.abs(instance.getY() - 0x0424) <= 0x20)
                    .count();

            assertFrameMatches(expected, fixture, lastInput);
            assertNull(intactRock, describeSpriteState(fixture, expected, lastInput));
            assertTrue(debrisCount >= 4, describeSpriteState(fixture, expected, lastInput)
                    + ", debrisCount=" + debrisCount);
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void keepsTracedAizFloatingPlatformAtRecordedWorldPositionBeforeFalseLandingWindow() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int probeFrame = 2269;
        TraceEvent.ObjectNear tracedPlatform = trace.getEventsForFrame(probeFrame).stream()
                .filter(TraceEvent.ObjectNear.class::isInstance)
                .map(TraceEvent.ObjectNear.class::cast)
                .filter(event -> event.slot() == 8)
                .filter(event -> "0x000255F4".equalsIgnoreCase(event.objectType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No traced floating platform event at frame " + probeFrame));
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            int lastInput = advanceReplayToTraceFrame(trace, fixture, replayStart, probeFrame);

            FloatingPlatformObjectInstance platform = GameServices.level().getObjectManager()
                    .getActiveObjects().stream()
                    .filter(FloatingPlatformObjectInstance.class::isInstance)
                    .map(FloatingPlatformObjectInstance.class::cast)
                    .filter(instance -> instance.getX() == tracedPlatform.x())
                    .findFirst()
                    .orElse(null);

            assertNotNull(platform, describeSpriteState(fixture, trace.getFrame(probeFrame), lastInput));
            assertEquals(tracedPlatform.x(), (short) platform.getX(),
                    describeSpriteState(fixture, trace.getFrame(probeFrame), lastInput));
            assertEquals(tracedPlatform.y(), (short) platform.getY(),
                    describeSpriteState(fixture, trace.getFrame(probeFrame), lastInput)
                            + ", tracedPlatformY=" + String.format("%04X", tracedPlatform.y() & 0xFFFF)
                            + ", actualPlatformY=" + String.format("%04X", platform.getY() & 0xFFFF));
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    @Test
    void matchesLegacyReplayShortlyBeforeAiz2ReloadResume() throws Exception {
        TraceData trace = TraceData.load(TRACE_DIR);
        int probeFrame = 5000;
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, sharedLevel);
            TraceReplayBootstrap.applyPreTraceState(trace, fixture);
            TraceReplayBootstrap.ReplayStartState replayStart =
                    TraceReplayBootstrap.applyReplayStartState(trace, fixture);

            int lastInput = advanceReplayToTraceFrame(trace, fixture, replayStart, probeFrame);
            TraceFrame expected = trace.getFrame(probeFrame);

            assertFrameMatches(expected, fixture, lastInput);
            assertEquals(expected.cameraX(), GameServices.camera().getX() & 0xFFFF, describeSpriteState(fixture, expected, lastInput));
            assertEquals(expected.cameraY(), GameServices.camera().getY() & 0xFFFF, describeSpriteState(fixture, expected, lastInput));
        } finally {
            sharedLevel.dispose();
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
        }
    }

    private static int advanceReplayToTraceFrame(TraceData trace,
                                                 HeadlessTestFixture fixture,
                                                 TraceReplayBootstrap.ReplayStartState replayStart,
                                                 int targetTraceFrame) {
        int lastInput = 0;
        for (int traceIndex = replayStart.startingTraceIndex(); traceIndex <= targetTraceFrame; traceIndex++) {
            lastInput = stepReplayFrame(trace, fixture, traceIndex - 1);
        }
        return lastInput;
    }

    private static int stepReplayFrame(TraceData trace,
                                       HeadlessTestFixture fixture,
                                       int previousTraceFrame) {
        TraceFrame previous = trace.getFrame(previousTraceFrame);
        TraceFrame current = trace.getFrame(previousTraceFrame + 1);
        TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, previous, current);
        if (phase == TraceExecutionPhase.VBLANK_ONLY) {
            return fixture.skipFrameFromRecording();
        }
        return fixture.stepFrameFromRecording();
    }

    private static void assertFrameMatches(TraceFrame expected,
                                           HeadlessTestFixture fixture,
                                           int actualInput) {
        String state = describeSpriteState(fixture, expected, actualInput);
        assertEquals(expected.x(), fixture.sprite().getCentreX(), state);
        assertEquals(expected.y(), fixture.sprite().getCentreY(), state);
        assertEquals(expected.xSpeed(), fixture.sprite().getXSpeed(), state);
        assertEquals(expected.gSpeed(), fixture.sprite().getGSpeed(), state);
        assertEquals(expected.xSub(), fixture.sprite().getXSubpixelRaw(), state);
        assertEquals(expected.ySub(), fixture.sprite().getYSubpixelRaw(), state);
    }

    private static String describeSpriteState(HeadlessTestFixture fixture,
                                             TraceFrame expected,
                                             int actualInput) {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        ObjectInstance ridingObject = objectManager != null
                ? objectManager.getRidingObject(fixture.sprite())
                : null;
        return "state[traceFrame=" + expected.frame()
                + ", expectedInput=" + String.format("%04X", expected.input())
                + ", actualInput=" + String.format("%04X", actualInput)
                + ", xSpeed=" + String.format("%04X", fixture.sprite().getXSpeed() & 0xFFFF)
                + ", ySpeed=" + String.format("%04X", fixture.sprite().getYSpeed() & 0xFFFF)
                + ", gSpeed=" + String.format("%04X", fixture.sprite().getGSpeed() & 0xFFFF)
                + ", xSub=" + String.format("%04X", fixture.sprite().getXSubpixelRaw())
                + ", ySub=" + String.format("%04X", fixture.sprite().getYSubpixelRaw())
                + ", air=" + fixture.sprite().getAir()
                + ", rolling=" + fixture.sprite().getRolling()
                + ", rollingJump=" + fixture.sprite().getRollingJump()
                + ", jumping=" + fixture.sprite().isJumping()
                + ", sliding=" + fixture.sprite().isSliding()
                + ", springing=" + fixture.sprite().getSpringing()
                + ", ctrl=" + fixture.sprite().isControlLocked()
                + ", objCtrl=" + fixture.sprite().isObjectControlled()
                + ", forcedMask=" + fixture.sprite().getForcedInputMask()
                + ", moveLock=" + fixture.sprite().getMoveLockTimer()
                + ", onObj=" + fixture.sprite().isOnObject()
                + ", riding=" + (ridingObject != null
                ? ridingObject.getClass().getSimpleName()
                : "<none>")
                + ", gMode=" + fixture.sprite().getGroundMode()
                + ", angle=" + (fixture.sprite().getAngle() & 0xFF)
                + ", dbl=" + fixture.sprite().getDoubleJumpFlag()
                + ", pinball=" + fixture.sprite().getPinballMode()
                + "]";
    }

    private static HeadlessTestFixture buildReplayFixture(TraceData trace, SharedLevel sharedLevel)
            throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .withRecording(TRACE_DIR.resolve("s3-aiz1-2-sonictails.bk2"))
                .withRecordingStartFrame(trace.metadata().bk2FrameOffset())
                .startPosition(trace.metadata().startX(), trace.metadata().startY())
                .startPositionIsCentre()
                .build();

        ObjectManager objectManager = GameServices.level().getObjectManager();
        if (objectManager != null) {
            objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
        }

        int overridePreTraceOsc = Integer.getInteger(
                PRE_TRACE_OSC_OVERRIDE_PROPERTY,
                Integer.MIN_VALUE);
        int preTraceOsc = overridePreTraceOsc != Integer.MIN_VALUE
                ? overridePreTraceOsc
                : trace.metadata().preTraceOscillationFrames();
        for (int i = 0; i < preTraceOsc; i++) {
            com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
        }
        return fixture;
    }

    private static int findCheckpointFrame(TraceData trace, String checkpointName) {
        for (int frame = 0; frame < trace.frameCount(); frame++) {
            for (TraceEvent event : trace.getEventsForFrame(frame)) {
                if (event instanceof TraceEvent.Checkpoint checkpoint
                        && checkpointName.equals(checkpoint.name())) {
                    return frame;
                }
            }
        }
        fail("Checkpoint not present in trace: " + checkpointName);
        return -1;
    }
}
