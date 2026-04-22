package com.openggf.tests.trace.s3k;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.AizLrzRockObjectInstance;
import com.openggf.game.sonic3k.objects.RockDebrisChild;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSignpostInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.TraceData;
import com.openggf.tests.trace.TraceEvent;
import com.openggf.tests.trace.TraceReplayBootstrap;
import com.openggf.tests.trace.TraceExecutionModel;
import com.openggf.tests.trace.TraceExecutionPhase;
import com.openggf.tests.trace.TraceFrame;
import com.openggf.tests.trace.TraceMetadata;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

class DebugS3kAizReplayWindowProbe {

    private static final Path TRACE_DIR =
            Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun");
    private static final int[] SAMPLE_TRACE_FRAMES = {1500, 1651, 3000, 5000};
    private static final int OFFSET_SWEEP_RADIUS = 12;
    private static final int MAX_TRACE_FRAME = 6000;
    private static final int START_WINDOW_START = 1538;
    private static final int START_WINDOW_END = 1572;
    private static final int MONKEY_WINDOW_START = 1788;
    private static final int MONKEY_WINDOW_END = 1845;
    private static final int[] LEGACY_BOOTSTRAP_SAMPLES = {
            1651, 1800, 2000, 2200, 2400, 2600, 2800, 3000, 5000, 5610
    };
    private static final int LEGACY_ROUTE_WINDOW_START = 1980;
    private static final int LEGACY_ROUTE_WINDOW_END = 2420;
    private static final int ROCK_WINDOW_START = 2094;
    private static final int ROCK_WINDOW_END = 2099;

    @Test
    void scanOffsetsAroundFixtureStart() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);

        Path bk2Path;
        try (var files = Files.list(TRACE_DIR)) {
            bk2Path = files.filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + TRACE_DIR);

        TraceData trace = TraceData.load(TRACE_DIR);
        TraceMetadata meta = trace.metadata();

        int rawOffset = meta.bk2FrameOffset();
        int minOffset = Math.max(0, rawOffset - OFFSET_SWEEP_RADIUS);
        int maxOffset = rawOffset + OFFSET_SWEEP_RADIUS;
        System.out.printf(
                "S3K AIZ replay offset sweep: rawOffset=%d range=[%d,%d] maxTraceFrame=%d%n",
                rawOffset, minOffset, maxOffset, MAX_TRACE_FRAME);

        for (int offset = minOffset; offset <= maxOffset; offset++) {
            SweepResult result = runProbe(trace, offset);
            System.out.println(result.format());
        }
    }

    @Test
    void dumpMonkeyWindowAtRawOffset() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);
        TraceData trace = TraceData.load(TRACE_DIR);
        int rawOffset = trace.metadata().bk2FrameOffset();

        dumpWindow(trace, rawOffset, MONKEY_WINDOW_START, MONKEY_WINDOW_END);
    }

    @Test
    void dumpGameplayStartWindowAtRawOffset() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);
        TraceData trace = TraceData.load(TRACE_DIR);
        int rawOffset = trace.metadata().bk2FrameOffset();

        dumpWindow(trace, rawOffset, START_WINDOW_START, START_WINDOW_END);
    }

    @Test
    void dumpGameplayStartWindowWithIntroBootstrap() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);
        TraceData trace = TraceData.load(TRACE_DIR);
        int rawOffset = trace.metadata().bk2FrameOffset();

        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            dumpWindowWithMetadataStart(trace, rawOffset, START_WINDOW_START, START_WINDOW_END);
        } finally {
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
        }
    }

    @Test
    void scanLegacyBootstrapProgression() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);
        TraceData trace = TraceData.load(TRACE_DIR);
        int rawOffset = trace.metadata().bk2FrameOffset();

        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            dumpLegacyBootstrapSamples(trace, rawOffset);
        } finally {
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
        }
    }

    @Test
    void dumpLegacyBootstrapRouteWindow() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);
        TraceData trace = TraceData.load(TRACE_DIR);
        int rawOffset = trace.metadata().bk2FrameOffset();
        int startFrame = Integer.getInteger("s3k.aiz.window.start", LEGACY_ROUTE_WINDOW_START);
        int endFrame = Integer.getInteger("s3k.aiz.window.end", LEGACY_ROUTE_WINDOW_END);

        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            dumpLegacyBootstrapWindow(trace, rawOffset, startFrame, endFrame);
        } finally {
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
        }
    }

    @Test
    void scanLegacyBootstrapForFirstLargeDrift() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);
        TraceData trace = TraceData.load(TRACE_DIR);
        int rawOffset = trace.metadata().bk2FrameOffset();
        int startFrame = Integer.getInteger("s3k.aiz.scan.start", 1800);
        int endFrame = Integer.getInteger("s3k.aiz.scan.end", 6000);
        int xBudget = Integer.getInteger("s3k.aiz.scan.xBudget", 0x20);
        int yBudget = Integer.getInteger("s3k.aiz.scan.yBudget", 0x20);
        int camBudget = Integer.getInteger("s3k.aiz.scan.camBudget", 0x20);

        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            scanLegacyBootstrapForFirstLargeDrift(
                    trace, rawOffset, startFrame, endFrame, xBudget, yBudget, camBudget);
        } finally {
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
        }
    }

    @Test
    void dumpLegacyBootstrapRockWindow() throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR), "Trace directory not found: " + TRACE_DIR);
        TraceData trace = TraceData.load(TRACE_DIR);
        int rawOffset = trace.metadata().bk2FrameOffset();
        int startFrame = Integer.getInteger("s3k.aiz.rock.start", ROCK_WINDOW_START);
        int endFrame = Integer.getInteger("s3k.aiz.rock.end", ROCK_WINDOW_END);

        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            dumpLegacyBootstrapRockWindow(trace, rawOffset, startFrame, endFrame);
        } finally {
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
        }
    }

    private void dumpWindow(TraceData trace, int rawOffset, int startFrame, int endFrame) throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .withRecording(findBk2File())
                    .withRecordingStartFrame(rawOffset)
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            int preTraceOsc = trace.metadata().preTraceOscillationFrames();
            for (int i = 0; i < preTraceOsc; i++) {
                com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
            }

            TraceFrame previous = null;
            for (int i = 0; i <= endFrame; i++) {
                TraceFrame current = trace.getFrame(i);
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
                int bk2Input = phase == TraceExecutionPhase.VBLANK_ONLY
                        ? fixture.skipFrameFromRecording()
                        : fixture.stepFrameFromRecording();

                if (current.frame() >= startFrame) {
                    System.out.printf(
                            "frame=%d phase=%s expIn=%04X bk2In=%04X exp=(%04X,%04X) act=(%04X,%04X) expSpd=(%04X,%04X,%04X) actSpd=(%04X,%04X,%04X) cam=(%04X,%04X) expCam=(%04X,%04X)%n",
                            current.frame(),
                            phase,
                            current.input(),
                            bk2Input,
                            current.x() & 0xFFFF,
                            current.y() & 0xFFFF,
                            fixture.sprite().getCentreX() & 0xFFFF,
                            fixture.sprite().getCentreY() & 0xFFFF,
                            current.xSpeed() & 0xFFFF,
                            current.ySpeed() & 0xFFFF,
                            current.gSpeed() & 0xFFFF,
                            fixture.sprite().getXSpeed() & 0xFFFF,
                            fixture.sprite().getYSpeed() & 0xFFFF,
                            fixture.sprite().getGSpeed() & 0xFFFF,
                            GameServices.camera().getX() & 0xFFFF,
                            GameServices.camera().getY() & 0xFFFF,
                            current.cameraX() & 0xFFFF,
                            current.cameraY() & 0xFFFF);
                }
                previous = current;
            }
        } finally {
            sharedLevel.dispose();
        }
    }

    private void dumpWindowWithMetadataStart(
            TraceData trace, int rawOffset, int startFrame, int endFrame) throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .withRecording(findBk2File())
                    .withRecordingStartFrame(rawOffset)
                    .startPosition(trace.metadata().startX(), trace.metadata().startY())
                    .startPositionIsCentre()
                    .build();

            runDumpLoop(trace, fixture, startFrame, endFrame);
        } finally {
            sharedLevel.dispose();
        }
    }

    private void runDumpLoop(
            TraceData trace, HeadlessTestFixture fixture, int startFrame, int endFrame) {
        ObjectManager objectManager = GameServices.level().getObjectManager();
        if (objectManager != null) {
            objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
        }

        int preTraceOsc = trace.metadata().preTraceOscillationFrames();
        for (int i = 0; i < preTraceOsc; i++) {
            com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
        }

        TraceFrame previous = null;
        for (int i = 0; i <= endFrame; i++) {
            TraceFrame current = trace.getFrame(i);
            TraceExecutionPhase phase =
                    TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
            int bk2Input = phase == TraceExecutionPhase.VBLANK_ONLY
                    ? fixture.skipFrameFromRecording()
                    : fixture.stepFrameFromRecording();

            if (current.frame() >= startFrame) {
                System.out.printf(
                        "frame=%d phase=%s expIn=%04X bk2In=%04X exp=(%04X,%04X) act=(%04X,%04X) expSpd=(%04X,%04X,%04X) actSpd=(%04X,%04X,%04X) cam=(%04X,%04X) expCam=(%04X,%04X)%n",
                        current.frame(),
                        phase,
                        current.input(),
                        bk2Input,
                        current.x() & 0xFFFF,
                        current.y() & 0xFFFF,
                        fixture.sprite().getCentreX() & 0xFFFF,
                        fixture.sprite().getCentreY() & 0xFFFF,
                        current.xSpeed() & 0xFFFF,
                        current.ySpeed() & 0xFFFF,
                        current.gSpeed() & 0xFFFF,
                        fixture.sprite().getXSpeed() & 0xFFFF,
                        fixture.sprite().getYSpeed() & 0xFFFF,
                        fixture.sprite().getGSpeed() & 0xFFFF,
                        GameServices.camera().getX() & 0xFFFF,
                        GameServices.camera().getY() & 0xFFFF,
                        current.cameraX() & 0xFFFF,
                        current.cameraY() & 0xFFFF);
            }
            previous = current;
        }
    }

    private SweepResult runProbe(TraceData trace, int offset) throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .withRecording(findBk2File())
                    .withRecordingStartFrame(offset)
                    .build();

            ObjectManager objectManager = GameServices.level().getObjectManager();
            if (objectManager != null) {
                objectManager.initVblaCounter(trace.initialVblankCounter() - 1);
            }

            int preTraceOsc = trace.metadata().preTraceOscillationFrames();
            for (int i = 0; i < preTraceOsc; i++) {
                com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
            }

            S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();
            TraceEvent.Checkpoint latestCheckpoint = null;
            TraceFrame previous = null;
            Sample[] samples = new Sample[SAMPLE_TRACE_FRAMES.length];

            int limit = Math.min(MAX_TRACE_FRAME, trace.frameCount() - 1);
            for (int i = 0; i <= limit; i++) {
                TraceFrame current = trace.getFrame(i);
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                S3kCheckpointProbe probe = captureProbe(current.frame(), fixture);
                TraceEvent.Checkpoint engineCheckpoint = detector.observe(probe);
                if (engineCheckpoint != null) {
                    latestCheckpoint = engineCheckpoint;
                }

                for (int sampleIndex = 0; sampleIndex < SAMPLE_TRACE_FRAMES.length; sampleIndex++) {
                    if (SAMPLE_TRACE_FRAMES[sampleIndex] == current.frame()) {
                        samples[sampleIndex] = new Sample(
                                current.frame(),
                                fixture.sprite().getCentreX(),
                                fixture.sprite().getCentreY(),
                                GameServices.camera().getX(),
                                GameServices.camera().getY(),
                                probe.eventsFg5(),
                                probe.fireTransitionActive(),
                                probe.actualAct(),
                                latestCheckpoint != null ? latestCheckpoint.name() : "<none>");
                    }
                }

                if (latestCheckpoint != null && "aiz2_main_gameplay".equals(latestCheckpoint.name())) {
                    break;
                }
                previous = current;
            }

            return new SweepResult(offset, latestCheckpoint != null ? latestCheckpoint.name() : "<none>", samples);
        } finally {
            sharedLevel.dispose();
        }
    }

    private void dumpLegacyBootstrapSamples(TraceData trace, int rawOffset) throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .withRecording(findBk2File())
                    .withRecordingStartFrame(rawOffset)
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

            S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();
            for (int frame = 0; frame <= replayStart.seededTraceIndex(); frame++) {
                for (TraceEvent event : trace.getEventsForFrame(frame)) {
                    if (event instanceof TraceEvent.Checkpoint checkpoint) {
                        detector.seedCheckpoint(checkpoint.name());
                    }
                }
            }

            TraceFrame previous = trace.getFrame(replayStart.seededTraceIndex());
            for (int traceIndex = replayStart.startingTraceIndex(); traceIndex < trace.frameCount(); traceIndex++) {
                TraceFrame current = trace.getFrame(traceIndex);
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                S3kCheckpointProbe probe = captureProbe(current.frame(), fixture);
                TraceEvent.Checkpoint engineCheckpoint = detector.observe(probe);
                if (engineCheckpoint != null) {
                    System.out.printf(
                            "legacy-bootstrap checkpoint frame=%d name=%s zone=%s act=%s apparent=%s moveLock=%d ctrlLocked=%b levelStarted=%b fire=%b overlay=%b%n",
                            current.frame(),
                            engineCheckpoint.name(),
                            probe.actualZoneId(),
                            probe.actualAct(),
                            probe.apparentAct(),
                            probe.moveLock(),
                            probe.ctrlLocked(),
                            probe.levelStarted(),
                            probe.fireTransitionActive(),
                            probe.titleCardOverlayActive());
                }

                for (int sampleFrame : LEGACY_BOOTSTRAP_SAMPLES) {
                    if (current.frame() == sampleFrame) {
                        System.out.printf(
                                "legacy-bootstrap sample frame=%d x=%04X y=%04X cam=(%04X,%04X) zone=%s act=%s apparent=%s moveLock=%d ctrlLocked=%b levelStarted=%b fire=%b overlay=%b checkpoint=%s%n",
                                current.frame(),
                                fixture.sprite().getCentreX() & 0xFFFF,
                                fixture.sprite().getCentreY() & 0xFFFF,
                                GameServices.camera().getX() & 0xFFFF,
                                GameServices.camera().getY() & 0xFFFF,
                                probe.actualZoneId(),
                                probe.actualAct(),
                                probe.apparentAct(),
                                probe.moveLock(),
                                probe.ctrlLocked(),
                                probe.levelStarted(),
                                probe.fireTransitionActive(),
                                probe.titleCardOverlayActive(),
                                engineCheckpoint != null ? engineCheckpoint.name() : "<none>");
                    }
                }

                previous = current;
                if (current.frame() >= LEGACY_BOOTSTRAP_SAMPLES[LEGACY_BOOTSTRAP_SAMPLES.length - 1]) {
                    break;
                }
            }
        } finally {
            sharedLevel.dispose();
        }
    }

    private void dumpLegacyBootstrapWindow(
            TraceData trace, int rawOffset, int startFrame, int endFrame) throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .withRecording(findBk2File())
                    .withRecordingStartFrame(rawOffset)
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

            TraceFrame previous = trace.getFrame(replayStart.seededTraceIndex());
            for (int traceIndex = replayStart.startingTraceIndex(); traceIndex <= endFrame; traceIndex++) {
                TraceFrame current = trace.getFrame(traceIndex);
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
                int bk2Input = phase == TraceExecutionPhase.VBLANK_ONLY
                        ? fixture.skipFrameFromRecording()
                        : fixture.stepFrameFromRecording();

                if (current.frame() >= startFrame) {
                    System.out.printf(
                            "legacy-window frame=%d phase=%s expIn=%04X bk2In=%04X exp=(%04X,%04X) act=(%04X,%04X) expSpd=(%04X,%04X,%04X) actSpd=(%04X,%04X,%04X) expCam=(%04X,%04X) actCam=(%04X,%04X) hDelay=%d layer=%d bits=%02X/%02X pri=%b onObj=%b switches=%s objs=%s%n",
                            current.frame(),
                            phase,
                            current.input(),
                            bk2Input,
                            current.x() & 0xFFFF,
                            current.y() & 0xFFFF,
                            fixture.sprite().getCentreX() & 0xFFFF,
                            fixture.sprite().getCentreY() & 0xFFFF,
                            current.xSpeed() & 0xFFFF,
                            current.ySpeed() & 0xFFFF,
                            current.gSpeed() & 0xFFFF,
                            fixture.sprite().getXSpeed() & 0xFFFF,
                            fixture.sprite().getYSpeed() & 0xFFFF,
                            fixture.sprite().getGSpeed() & 0xFFFF,
                            current.cameraX() & 0xFFFF,
                            current.cameraY() & 0xFFFF,
                            GameServices.camera().getX() & 0xFFFF,
                            GameServices.camera().getY() & 0xFFFF,
                            GameServices.camera().getHorizScrollDelay(),
                            fixture.sprite().getLayer(),
                            fixture.sprite().getTopSolidBit() & 0xFF,
                            fixture.sprite().getLrbSolidBit() & 0xFF,
                            fixture.sprite().isHighPriority(),
                            fixture.sprite().isOnObject(),
                            nearbyPathSwaps(fixture.sprite()),
                            nearbyActiveObjects(fixture.sprite()));
                }

                previous = current;
            }
        } finally {
            sharedLevel.dispose();
        }
    }

    private void scanLegacyBootstrapForFirstLargeDrift(
            TraceData trace,
            int rawOffset,
            int startFrame,
            int endFrame,
            int xBudget,
            int yBudget,
            int camBudget) throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .withRecording(findBk2File())
                    .withRecordingStartFrame(rawOffset)
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

            TraceFrame previous = trace.getFrame(replayStart.seededTraceIndex());
            boolean found = false;
            for (int traceIndex = replayStart.startingTraceIndex(); traceIndex <= endFrame; traceIndex++) {
                TraceFrame current = trace.getFrame(traceIndex);
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
                int bk2Input = phase == TraceExecutionPhase.VBLANK_ONLY
                        ? fixture.skipFrameFromRecording()
                        : fixture.stepFrameFromRecording();

                int dx = Math.abs(fixture.sprite().getCentreX() - current.x());
                int dy = Math.abs(fixture.sprite().getCentreY() - current.y());
                int dCamX = Math.abs((GameServices.camera().getX() & 0xFFFF) - current.cameraX());
                int dCamY = Math.abs((GameServices.camera().getY() & 0xFFFF) - current.cameraY());
                if (current.frame() >= startFrame
                        && (dx > xBudget || dy > yBudget || dCamX > camBudget || dCamY > camBudget)) {
                    System.out.printf(
                            "legacy-drift frame=%d phase=%s expIn=%04X bk2In=%04X " +
                                    "exp=(%04X,%04X) act=(%04X,%04X) d=(%d,%d) " +
                                    "expCam=(%04X,%04X) actCam=(%04X,%04X) dCam=(%d,%d) " +
                                    "events=%s switches=%s objs=%s%n",
                            current.frame(),
                            phase,
                            current.input(),
                            bk2Input,
                            current.x() & 0xFFFF,
                            current.y() & 0xFFFF,
                            fixture.sprite().getCentreX() & 0xFFFF,
                            fixture.sprite().getCentreY() & 0xFFFF,
                            dx,
                            dy,
                            current.cameraX() & 0xFFFF,
                            current.cameraY() & 0xFFFF,
                            GameServices.camera().getX() & 0xFFFF,
                            GameServices.camera().getY() & 0xFFFF,
                            dCamX,
                            dCamY,
                            describeTraceEvents(trace.getEventsForFrame(traceIndex)),
                            nearbyPathSwaps(fixture.sprite()),
                            nearbyActiveObjects(fixture.sprite()));
                    int windowStart = Math.max(replayStart.startingTraceIndex(), current.frame() - 6);
                    int windowEnd = Math.min(endFrame, current.frame() + 6);
                    dumpLegacyBootstrapWindow(trace, rawOffset, windowStart, windowEnd);
                    found = true;
                    break;
                }

                previous = current;
            }

            if (!found) {
                System.out.printf(
                        "legacy-drift none start=%d end=%d budgets=(x=%d,y=%d,cam=%d)%n",
                        startFrame,
                        endFrame,
                        xBudget,
                        yBudget,
                        camBudget);
            }
        } finally {
            sharedLevel.dispose();
        }
    }

    private void dumpLegacyBootstrapRockWindow(
            TraceData trace, int rawOffset, int startFrame, int endFrame) throws Exception {
        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                    .withSharedLevel(sharedLevel)
                    .withRecording(findBk2File())
                    .withRecordingStartFrame(rawOffset)
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

            TraceFrame previous = trace.getFrame(replayStart.seededTraceIndex());
            for (int traceIndex = replayStart.startingTraceIndex(); traceIndex <= endFrame; traceIndex++) {
                TraceFrame current = trace.getFrame(traceIndex);
                TraceExecutionPhase phase =
                        TraceExecutionModel.forGame(trace.metadata().game()).phaseFor(previous, current);
                int bk2Input = phase == TraceExecutionPhase.VBLANK_ONLY
                        ? fixture.skipFrameFromRecording()
                        : fixture.stepFrameFromRecording();

                if (current.frame() >= startFrame) {
                    System.out.printf(
                            "rock-window frame=%d phase=%s expIn=%04X bk2In=%04X exp=(%04X,%04X) act=(%04X,%04X) hDelay=%d rock=%s%n",
                            current.frame(),
                            phase,
                            current.input(),
                            bk2Input,
                            current.x() & 0xFFFF,
                            current.y() & 0xFFFF,
                            fixture.sprite().getCentreX() & 0xFFFF,
                            fixture.sprite().getCentreY() & 0xFFFF,
                            GameServices.camera().getHorizScrollDelay(),
                            describeRockState(GameServices.level().getObjectManager()));
                }

                previous = current;
            }
        } finally {
            sharedLevel.dispose();
        }
    }

    private static Path findBk2File() throws Exception {
        try (var files = Files.list(TRACE_DIR)) {
            return files.filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static String nearbyPathSwaps(com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        ObjectManager objectManager = GameServices.level() != null
                ? GameServices.level().getObjectManager()
                : null;
        if (sprite == null || objectManager == null) {
            return "-";
        }
        int spriteX = sprite.getCentreX() & 0xFFFF;
        int spriteY = sprite.getCentreY() & 0xFFFF;
        return objectManager.getActiveSpawns().stream()
                .filter(spawn -> spawn.objectId() == 0x02)
                .sorted(java.util.Comparator.comparingInt(spawn -> {
                    int dx = Math.abs((spawn.x() & 0xFFFF) - spriteX);
                    int dy = Math.abs((spawn.y() & 0xFFFF) - spriteY);
                    return dx + dy;
                }))
                .limit(3)
                .map(spawn -> String.format("(%04X,%04X sub=%02X side=%d)",
                        spawn.x() & 0xFFFF,
                        spawn.y() & 0xFFFF,
                        spawn.subtype() & 0xFF,
                        objectManager.getPlaneSwitcherSideState(spawn)))
                .reduce((left, right) -> left + " | " + right)
                .orElse("-");
    }

    private static String nearbyActiveObjects(com.openggf.sprites.playable.AbstractPlayableSprite sprite) {
        ObjectManager objectManager = GameServices.level() != null
                ? GameServices.level().getObjectManager()
                : null;
        if (sprite == null || objectManager == null) {
            return "-";
        }
        int spriteX = sprite.getCentreX() & 0xFFFF;
        int spriteY = sprite.getCentreY() & 0xFFFF;
        return objectManager.getActiveObjects().stream()
                .filter(instance -> instance != null
                        && !instance.isDestroyed()
                        && instance.getSpawn() != null)
                .sorted(java.util.Comparator.comparingInt(instance -> {
                    int dx = Math.abs((instance.getX() & 0xFFFF) - spriteX);
                    int dy = Math.abs((instance.getY() & 0xFFFF) - spriteY);
                    return dx + dy;
                }))
                .limit(4)
                .map(instance -> {
                    var spawn = instance.getSpawn();
                    return String.format("%s(id=%02X sub=%02X spawn=%04X,%04X pos=%04X,%04X)",
                            instance.getClass().getSimpleName(),
                            spawn.objectId() & 0xFF,
                            spawn.subtype() & 0xFF,
                            spawn.x() & 0xFFFF,
                            spawn.y() & 0xFFFF,
                            instance.getX() & 0xFFFF,
                            instance.getY() & 0xFFFF);
                })
                .reduce((left, right) -> left + " | " + right)
                .orElse("-");
    }

    private static String describeRockState(ObjectManager objectManager) {
        if (objectManager == null) {
            return "<no-object-manager>";
        }

        AizLrzRockObjectInstance rock = objectManager.getActiveObjects().stream()
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

        if (rock == null) {
            return "intact=<none> debris=" + debrisCount;
        }

        return String.format(
                "intact=x=%04X y=%04X breaking=%s standing=%s pushing=%s preAnim=%02X preRoll=%s preX=%04X preY=%04X debris=%d",
                rock.getX() & 0xFFFF,
                rock.getY() & 0xFFFF,
                readBoolean(rock, "breaking"),
                readBoolean(rock, "playerStandingOnRock"),
                readBoolean(rock, "playerPushingSide"),
                readInt(rock, "savedPreContactAnimationId") & 0xFF,
                readBoolean(rock, "savedPreContactRolling"),
                readInt(rock, "savedPreContactXSpeed") & 0xFFFF,
                readInt(rock, "savedPreContactYSpeed") & 0xFFFF,
                debrisCount);
    }

    private static boolean readBoolean(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getBoolean(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static int readInt(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(target);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static String describeTraceEvents(java.util.List<TraceEvent> events) {
        if (events == null || events.isEmpty()) {
            return "-";
        }
        return events.stream()
                .map(event -> switch (event) {
                    case TraceEvent.Checkpoint checkpoint ->
                            "checkpoint:" + checkpoint.name();
                    case TraceEvent.CollisionEvent collision ->
                            "collision:" + collision.type();
                    case TraceEvent.ModeChange modeChange ->
                            "mode:" + modeChange.field() + "=" + modeChange.to();
                    case TraceEvent.RoutineChange routineChange ->
                            "routine:" + routineChange.to();
                    case TraceEvent.ObjectAppeared objectAppeared ->
                            "spawn:" + objectAppeared.objectType();
                    case TraceEvent.ObjectRemoved objectRemoved ->
                            "remove:" + objectRemoved.objectType();
                    case TraceEvent.ObjectNear objectNear ->
                            "near:" + objectNear.objectType();
                    case TraceEvent.ZoneActState zoneActState ->
                            "zone:" + zoneActState.actualZoneId() + "/" + zoneActState.actualAct();
                    default -> event.getClass().getSimpleName();
                })
                .distinct()
                .limit(6)
                .reduce((left, right) -> left + " | " + right)
                .orElse("-");
    }

    private static S3kCheckpointProbe captureProbe(int replayFrame, HeadlessTestFixture fixture) {
        boolean resultsActive = GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(S3kResultsScreenObjectInstance.class::isInstance);
        boolean signpostActive = S3kSignpostInstance.getActiveSignpost() != null;
        boolean eventsFg5 =
                GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager
                        && manager.isEventsFg5();
        boolean fireTransitionActive =
                GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager
                        && manager.isFireTransitionActive();
        var titleCardProvider = GameServices.module().getTitleCardProvider();
        boolean titleCardOverlayActive = titleCardProvider != null && titleCardProvider.isOverlayActive();

        return new S3kCheckpointProbe(
                replayFrame,
                GameServices.level().getCurrentZone(),
                GameServices.level().getCurrentAct(),
                GameServices.level().getApparentAct(),
                0x0C,
                fixture.sprite().getMoveLockTimer(),
                fixture.sprite().isControlLocked(),
                eventsFg5,
                fireTransitionActive,
                false,
                signpostActive,
                resultsActive,
                GameServices.camera().isLevelStarted(),
                titleCardOverlayActive);
    }

    private record Sample(
            int frame,
            int x,
            int y,
            int cameraX,
            int cameraY,
            boolean eventsFg5,
            boolean fireActive,
            Integer actualAct,
            String latestCheckpoint) {

        private String format() {
            return String.format(
                    "f=%d x=%04X y=%04X cam=(%04X,%04X) act=%s fg5=%b fire=%b latest=%s",
                    frame,
                    x & 0xFFFF,
                    y & 0xFFFF,
                    cameraX & 0xFFFF,
                    cameraY & 0xFFFF,
                    actualAct,
                    eventsFg5,
                    fireActive,
                    latestCheckpoint);
        }
    }

    private record SweepResult(int offset, String latestCheckpoint, Sample[] samples) {
        private String format() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("offset=%d latest=%s", offset, latestCheckpoint));
            for (Sample sample : samples) {
                if (sample != null) {
                    sb.append(" | ").append(sample.format());
                }
            }
            return sb.toString();
        }
    }
}
