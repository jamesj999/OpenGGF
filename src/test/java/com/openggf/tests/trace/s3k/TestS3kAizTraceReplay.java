package com.openggf.tests.trace.s3k;
import com.openggf.trace.*;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameServices;
import com.openggf.level.objects.ObjectManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceReplayBootstrap;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kAizTraceReplay extends AbstractTraceReplayTest {
    private static final int GIANT_RIDE_VINE_WINDOW_START = 2876;
    private static final int GIANT_RIDE_VINE_WINDOW_END = 2892;


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
        return Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun");
    }

    @Test
    public void cameraMatchesTraceThroughFirstDelayedScrollBurst() throws Exception {
        Path traceDir = traceDirectory();
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);

        Path bk2Path = findBk2File(traceDir);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, bk2Path);
            TraceReplayBootstrap.ReplayStartState replayStart = primeReplayFixture(trace, fixture);

            TraceFrame previous = replayStart.hasSeededTraceState()
                    ? trace.getFrame(replayStart.seededTraceIndex())
                    : null;
            for (int traceIndex = replayStart.startingTraceIndex(); traceIndex <= 1979; traceIndex++) {
                TraceFrame current = trace.getFrame(traceIndex);
                TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, previous, current);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                if (traceIndex >= 1974) {
                    assertEquals(
                            current.cameraX() & 0xFFFF,
                            GameServices.camera().getX() & 0xFFFF,
                            "camera X mismatch at trace frame " + traceIndex);
                }
                previous = current;
            }
        } finally {
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
            TestEnvironment.resetAll();
        }
    }

    @Test
    public void playerMatchesTraceThroughFirstGiantRideVineWindow() throws Exception {
        Path traceDir = traceDirectory();
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);

        Path bk2Path = findBk2File(traceDir);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        TraceData trace = TraceData.load(traceDir);
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldSkip = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        Object oldMain = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekick = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);

        try {
            config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, false);
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "tails");

            HeadlessTestFixture fixture = buildReplayFixture(trace, bk2Path);
            TraceReplayBootstrap.ReplayStartState replayStart = primeReplayFixture(trace, fixture);

            TraceFrame previous = replayStart.hasSeededTraceState()
                    ? trace.getFrame(replayStart.seededTraceIndex())
                    : null;
            for (int traceIndex = replayStart.startingTraceIndex();
                 traceIndex <= GIANT_RIDE_VINE_WINDOW_END;
                 traceIndex++) {
                TraceFrame current = trace.getFrame(traceIndex);
                TraceExecutionPhase phase = TraceReplayBootstrap.phaseForReplay(trace, previous, current);
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    fixture.skipFrameFromRecording();
                } else {
                    fixture.stepFrameFromRecording();
                }

                if (traceIndex >= GIANT_RIDE_VINE_WINDOW_START) {
                    assertEquals(
                            current.x() & 0xFFFF,
                            fixture.sprite().getCentreX() & 0xFFFF,
                            "player X mismatch at trace frame " + traceIndex);
                    assertEquals(
                            current.y() & 0xFFFF,
                            fixture.sprite().getCentreY() & 0xFFFF,
                            "player Y mismatch at trace frame " + traceIndex);
                }
                previous = current;
            }
        } finally {
            config.setConfigValue(
                    SonicConfiguration.S3K_SKIP_INTROS,
                    oldSkip != null ? oldSkip : false);
            config.setConfigValue(
                    SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMain != null ? oldMain : "sonic");
            config.setConfigValue(
                    SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekick != null ? oldSidekick : "tails");
            TestEnvironment.resetAll();
        }
    }

    private HeadlessTestFixture buildReplayFixture(TraceData trace, Path bk2Path) throws Exception {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(zone(), act())
                .withRecording(bk2Path)
                .withRecordingStartFrame(TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace))
                .build();

        ObjectManager objectManager = GameServices.level().getObjectManager();
        if (objectManager != null) {
            objectManager.initVblaCounter(
                    TraceReplayBootstrap.initialVblankCounterForTraceReplay(trace) - 1);
        }

        int preTraceOsc = trace.metadata().preTraceOscillationFrames();
        for (int i = 0; i < preTraceOsc; i++) {
            com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
        }
        return fixture;
    }

    private TraceReplayBootstrap.ReplayStartState primeReplayFixture(TraceData trace, HeadlessTestFixture fixture) {
        TraceReplayBootstrap.applyPreTraceState(trace, fixture);
        return TraceReplayBootstrap.applyReplayStartStateForTraceReplay(trace, fixture);
    }

    private Path findBk2File(Path traceDir) throws IOException {
        try (var files = Files.list(traceDir)) {
            return files.filter(path -> path.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
    }
}
