package com.openggf.tests.trace;

import com.openggf.Engine;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameMode;
import com.openggf.game.GroundMode;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.objects.Aiz2BossEndSequenceState;
import com.openggf.game.sonic3k.objects.S3kResultsScreenObjectInstance;
import com.openggf.game.sonic3k.objects.S3kSignpostInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectSnapshot;
import com.openggf.level.objects.TouchResponseDebugHit;
import com.openggf.level.objects.TouchResponseDebugState;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.s3k.S3kCheckpointProbe;
import com.openggf.trace.DivergenceGroup;
import com.openggf.trace.DivergenceReport;
import com.openggf.trace.EngineDiagnostics;
import com.openggf.trace.EngineNearbyObject;
import com.openggf.trace.EngineNearbyObjectFormatter;
import com.openggf.trace.ToleranceConfig;
import com.openggf.trace.TouchResponseDebugHitFormatter;
import com.openggf.trace.TraceBinder;
import com.openggf.trace.TraceCharacterState;
import com.openggf.trace.TraceData;
import com.openggf.trace.TraceEvent;
import com.openggf.trace.TraceEventFormatter;
import com.openggf.trace.TraceExecutionPhase;
import com.openggf.trace.TraceFrame;
import com.openggf.trace.TraceHistoryHydration;
import com.openggf.trace.TraceMetadata;
import com.openggf.trace.TraceObjectSnapshotBinder;
import com.openggf.trace.TraceReplayBootstrap;
import com.openggf.trace.replay.TraceReplaySessionBootstrap;
import com.openggf.tests.trace.s3k.S3kElasticWindowController;
import com.openggf.tests.trace.s3k.S3kRequiredCheckpointGuard;
import com.openggf.tests.trace.s3k.S3kReplayCheckpointDetector;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for trace replay tests. Subclasses provide game/zone/act/path;
 * this class handles level loading, BK2 playback, per-frame comparison, and report output.
 *
 * <p>Originally used JUnit 4 because the ROM fixture was exposed as a JUnit 4 rule.
 */
public abstract class AbstractTraceReplayTest {
    /** Which game ROM this test requires. */
    protected abstract SonicGame game();

    /** Zone index (0-based). */
    protected abstract int zone();

    /** Act index (0-based). */
    protected abstract int act();

    /** Path to the trace directory containing metadata.json, physics.csv, and optionally a .bk2. */
    protected abstract Path traceDirectory();

    /** Override to supply custom tolerances. */
    protected ToleranceConfig tolerances() {
        return ToleranceConfig.DEFAULT;
    }

    /** Override to force a specific pre-trace oscillation frame count. Return -1 to use metadata. */
    protected int overridePreTraceOscFrames() { return -1; }

    /** Override to change report output directory. */
    protected Path reportOutputDir() {
        return Path.of("target/trace-reports");
    }

    @Test
    public void replayMatchesTrace() throws Exception {
        // 0. Skip if trace directory or required files are missing
        Path traceDir = traceDirectory();
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);
        Assumptions.assumeTrue(Files.exists(traceDir.resolve("metadata.json")), "metadata.json not found in " + traceDir);
        Assumptions.assumeTrue(Files.exists(traceDir.resolve("physics.csv")), "physics.csv not found in " + traceDir);

        // 1. Find BK2 file in trace directory (check before loading trace data)
        Path bk2Path = findBk2File(traceDir);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + traceDir);

        // 2. Load trace data
        TraceData trace = TraceData.load(traceDir);
        TraceMetadata meta = trace.metadata();
        boolean requiresFreshLevelLoad =
                TraceReplayBootstrap.requiresFreshLevelLoadForTraceReplay(trace);

        // 3. Validate test configuration matches metadata
        validateMetadata(meta);
        TraceReplaySessionBootstrap.prepareConfiguration(trace, meta);

        // 4. Load level and create fixture
        SharedLevel sharedLevel = requiresFreshLevelLoad
                ? null
                : SharedLevel.load(game(), zone(), act());
        try {
            HeadlessTestFixture.Builder fixtureBuilder = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withRecordingStartFrame(TraceReplayBootstrap.recordingStartFrameForTraceReplay(trace));
            if (sharedLevel != null) {
                fixtureBuilder.withSharedLevel(sharedLevel);
            } else {
                fixtureBuilder.withZoneAndAct(zone(), act());
            }
            if (shouldApplyMetadataStartPosition(trace, meta)) {
                fixtureBuilder
                        .startPosition(meta.startX(), meta.startY())
                        .startPositionIsCentre();
            }
            HeadlessTestFixture fixture = fixtureBuilder.build();

            if (GameServices.debugOverlay() != null) {
                GameServices.debugOverlay().setEnabled(DebugOverlayToggle.TOUCH_RESPONSE, true);
            }

            // 4. Shared replay bootstrap: vblank seed, oscillation
            //    pre-advance, object-snapshot hydration, replay start state.
            TraceReplaySessionBootstrap.BootstrapResult boot =
                    TraceReplaySessionBootstrap.applyBootstrap(trace, fixture,
                            overridePreTraceOscFrames());
            TraceObjectSnapshotBinder.Result hydration = boot.hydration();
            TraceReplayBootstrap.ReplayStartState replayStart = boot.replayStart();
            ObjectManager om = GameServices.level().getObjectManager();
            List<TraceEvent.ObjectStateSnapshot> preTraceSnapshots =
                    trace.preTraceObjectSnapshots();
            if (!preTraceSnapshots.isEmpty() && om != null) {
                System.out.printf(
                        "Hydrated %d/%d pre-trace object snapshots (%d warnings)%n",
                        hydration.matched(), hydration.attempted(),
                        hydration.warnings().size());
                for (String warning : hydration.warnings()) {
                    System.out.println("  WARN: " + warning);
                }
            }

            // 5. Run frame-by-frame comparison
            TraceBinder binder = new TraceBinder(tolerances());
            int firstSubDivFrame = -1;

            if ("s3k".equals(meta.game())) {
                replayS3kTrace(trace, meta, fixture, binder, replayStart);
            } else {
                int startTraceIndex = replayStart.startingTraceIndex();
                for (int i = startTraceIndex; i < trace.frameCount(); i++) {
                    TraceFrame expected = trace.getFrame(i);

                    // Drive replay from recorded ROM counters instead of inferring
                    // lag from unchanged physics state.
                    int bk2Input;
                    TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                    TraceExecutionPhase phase =
                        TraceReplayBootstrap.phaseForReplay(trace, previous, expected);
                    if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                        bk2Input = fixture.skipFrameFromRecording();
                    } else {
                        bk2Input = fixture.stepFrameFromRecording();
                    }

                    if (!binder.validateInput(expected, bk2Input)) {
                        fail(String.format(
                            "Input alignment error at trace frame %d: " +
                            "BK2 input=0x%04X, trace input=0x%04X. " +
                            "Check bk2_frame_offset in metadata.json.",
                            i, bk2Input, expected.input()));
                    }

                    // ROM stores centre coordinates at $D008/$D00C. With startPositionIsCentre(),
                    // the sprite's xPixel/yPixel are set to the correct top-left position,
                    // so getCentreX()/getCentreY() now return the actual ROM centre values.
                    var sprite = fixture.sprite();

                    // Capture engine-side diagnostic state for context window
                    EngineDiagnostics engineDiag = captureEngineDiagnostics(sprite);
                    TraceCharacterState actualSidekick = captureFirstSidekickState();
                    String secondaryCharacterLabel = meta.recordedSidekicks().isEmpty()
                            ? "sidekick"
                            : meta.recordedSidekicks().getFirst();
                    String romDiag = combineDiagnostics(
                            expected.hasExtendedData() ? expected.formatDiagnostics() : "",
                            formatCharacterDiagnostics(secondaryCharacterLabel, expected.sidekick()));
                    romDiag = combineDiagnostics(
                            romDiag,
                            TraceEventFormatter.summariseFrameEvents(trace.getEventsForFrame(i)));
                    String engineDiagText = combineDiagnostics(
                            engineDiag.format(),
                            formatCharacterDiagnostics(secondaryCharacterLabel, actualSidekick));

                    binder.compareFrame(expected,
                        sprite.getCentreX(), sprite.getCentreY(),
                        sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                        sprite.getAngle(),
                        sprite.getAir(), sprite.getRolling(),
                        sprite.getGroundMode().ordinal(), romDiag,
                        EngineDiagnostics.formattedOnly(engineDiagText),
                        secondaryCharacterLabel, actualSidekick);

                    // Track first subpixel divergence (before it becomes pixel-level)
                    if (firstSubDivFrame < 0 && expected.xSub() > 0) {
                        int engXSub = sprite.getXSubpixelRaw();
                        int romXSub = expected.xSub();
                        int engYSub = sprite.getYSubpixelRaw();
                        int romYSub = expected.ySub();
                        if (engXSub != romXSub || engYSub != romYSub) {
                            firstSubDivFrame = expected.frame();
                            System.out.printf("FIRST SUB DIVERGENCE at frame %d: xsub ROM=0x%04X ENG=0x%04X " +
                                "ysub ROM=0x%04X ENG=0x%04X cx=0x%04X cy=0x%04X xs=%d/%d ys=%d/%d air=%b/%b%n",
                                expected.frame(), romXSub, engXSub, romYSub, engYSub,
                                sprite.getCentreX(), sprite.getCentreY(),
                                sprite.getXSpeed(), expected.xSpeed(),
                                sprite.getYSpeed(), expected.ySpeed(),
                                sprite.getAir(), expected.air());
                        }
                    }
                }
            }

            // 6. Build report
            DivergenceReport report = "s3k".equals(meta.game())
                    ? binder.buildReport(trace)
                    : binder.buildReport();

            // 7. Write report if there are any divergences
            if (report.hasErrors() || report.hasWarnings()) {
                writeReport(report, meta);
            }

            // 8. Log summary
            System.out.println(report.toSummary());

            // 9. Assert no errors
            if (report.hasErrors()) {
                DivergenceGroup firstError = report.errors().get(0);
                System.err.println("\n=== Context window around first error ===");
                System.err.println(report.getContextWindow(firstError.startFrame(), 10));
                fail(report.toSummary());
            }
        } finally {
            if (sharedLevel != null) {
                sharedLevel.dispose();
            } else {
                TestEnvironment.resetAll();
            }
        }
    }

    private void validateMetadata(TraceMetadata meta) {
        String expectedGameId = switch (game()) {
            case SONIC_1 -> "s1";
            case SONIC_2 -> "s2";
            case SONIC_3K -> "s3k";
        };
        assertEquals(expectedGameId, meta.game(), "Metadata game mismatch (test says " + game()
            + " but metadata says " + meta.game() + ")");
    }

    private boolean shouldApplyMetadataStartPosition(TraceData trace, TraceMetadata meta) {
        // Power-on traces can begin before the level is actually live. In those
        // cases start_x/start_y reflect whatever was left in Player_1 RAM at the
        // recorder start, not the first replayable in-level position.
        return TraceReplayBootstrap.shouldApplyMetadataStartPositionForTraceReplay(trace);
    }

    private void replayS3kTrace(TraceData trace, TraceMetadata meta,
                                HeadlessTestFixture fixture, TraceBinder binder,
                                TraceReplayBootstrap.ReplayStartState replayStart) {
        int driveTraceIndex = replayStart.startingTraceIndex();
        TraceFrame previousDriveFrame = replayStart.hasSeededTraceState()
                ? trace.getFrame(replayStart.seededTraceIndex())
                : driveTraceIndex > 0 ? trace.getFrame(driveTraceIndex - 1) : null;
        S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();
        S3kElasticWindowController controller =
                new S3kElasticWindowController(loadCheckpointFrames(trace));
        S3kRequiredCheckpointGuard checkpointGuard = new S3kRequiredCheckpointGuard();
        int firstSubDivFrame = -1;
        int firstOscDivFrame = -1;
        Boolean lastEventsFg5 = null;
        boolean hasPerFrameOsc = meta.hasPerFrameOscillationState();

        boolean hydrateCpuState = meta.hasPerFrameCpuState();
        String cpuStateCharacter = meta.recordedSidekicks().isEmpty()
                ? null
                : meta.recordedSidekicks().getFirst();

        if (replayStart.hasSeededTraceState()) {
            TraceFrame seededFrame = trace.getFrame(replayStart.seededTraceIndex());
            if (hydrateCpuState && cpuStateCharacter != null) {
                applyRecordedFirstSidekickCpuState(
                        trace.cpuStateForFrame(replayStart.seededTraceIndex(), cpuStateCharacter));
            }
            applyRecordedFirstSidekickState(seededFrame.sidekick());
            TraceReplayBootstrap.ReplayPrimaryState seededPrimary =
                    TraceReplayBootstrap.capturePrimaryReplayStateForComparison(
                            trace, seededFrame, fixture.sprite());
            EngineDiagnostics engineDiag = captureEngineDiagnostics(fixture.sprite());
            String romDiag = combineDiagnostics(
                    seededFrame.hasExtendedData() ? seededFrame.formatDiagnostics() : "",
                    TraceEventFormatter.summariseFrameEvents(
                            trace.getEventsForFrame(replayStart.seededTraceIndex())));
            TraceCharacterState seededSidekick = captureFirstSidekickState();
            String secondaryCharacterLabel = meta.recordedSidekicks().isEmpty()
                    ? "sidekick"
                    : meta.recordedSidekicks().getFirst();

            binder.compareFrame(seededFrame,
                    seededPrimary.x(), seededPrimary.y(),
                    seededPrimary.xSpeed(), seededPrimary.ySpeed(), seededPrimary.gSpeed(),
                    seededPrimary.angle(),
                    seededPrimary.air(), seededPrimary.rolling(),
                    seededPrimary.groundMode(), romDiag,
                    engineDiag,
                    secondaryCharacterLabel,
                    seededSidekick);

            for (int frame = 0; frame <= replayStart.seededTraceIndex(); frame++) {
                for (TraceEvent event : trace.getEventsForFrame(frame)) {
                    if (event instanceof TraceEvent.Checkpoint traceCheckpoint) {
                        detector.seedCheckpoint(traceCheckpoint.name());
                        controller.onEntryFrameValidated(traceCheckpoint);
                        controller.onEngineCheckpoint(traceCheckpoint);
                    }
                }
            }
            controller.alignCursorToTraceIndex(replayStart.startingTraceIndex());
        } else if (driveTraceIndex > 0) {
            // Legacy warmup path: we skipped past every trace checkpoint that
            // preceded strictStart (e.g. intro_begin at frame 0 for AIZ1).
            // Seed the detector and open the relevant elastic windows so the
            // intro cutscene is compared loosely rather than driving strict
            // comparison against the frozen pre-gameplay player snapshot.
            // The controller auto-closes each window once the drive cursor
            // passes its recorded exit frame, so the engine's checkpoint
            // timing (e.g. title-card overlay activation) does not have to
            // align exactly with the trace for the replay loop to progress.
            for (int frame = 0; frame < driveTraceIndex; frame++) {
                for (TraceEvent event : trace.getEventsForFrame(frame)) {
                    if (event instanceof TraceEvent.Checkpoint traceCheckpoint) {
                        detector.seedCheckpoint(traceCheckpoint.name());
                        controller.onEntryFrameValidated(traceCheckpoint);
                        controller.onEngineCheckpoint(traceCheckpoint);
                    }
                }
            }
            controller.alignCursorToTraceIndex(driveTraceIndex);
        }

        // Align SpriteManager.frameCounter with ROM Level_frame_counter so Tails-CPU
        // AI gates that read (Level_frame_counter & MASK) fire on the same trace
        // frames as the ROM (sonic3k.asm:26761 loc_13E7C dx-256-frame check,
        // sonic3k.asm:26775 loc_13E9C 64-frame jump-cadence check, etc.).
        //
        // The trace records each frame's gfc. The first iteration steps fc by one,
        // so for AI on iter K=K_start to see fc == T_K_start.gfc (== ROM's
        // Level_frame_counter at T_K_start logic), we pre-set fc =
        // T_(K_start-1).gfc here. The trace's gfc increments monotonically per
        // recorded gameplay frame and stays put on lag/VBLANK_ONLY frames, while
        // the engine's fc++/skip behaviour matches that exact pattern, so this
        // single pre-set keeps the counters synced for the entire replay.
        //
        // Concrete examples:
        //   * CNZ:  T_0.gfc=1   (gameplay starts immediately) → fc 0→1, then iter K=1
        //                                 step fc 1→2 = ROM.gfc(T_1)=2 ✓
        //   * AIZ:  T_289.gfc=0 (still inside intro)         → fc 0→0  (no change),
        //                                 then iter K=290 step fc 0→1 = ROM.gfc(T_290)=1 ✓
        if (previousDriveFrame != null && previousDriveFrame.gameplayFrameCounter() >= 0
                && GameServices.sprites() != null) {
            GameServices.sprites().setFrameCounter(previousDriveFrame.gameplayFrameCounter());
        }
        while (driveTraceIndex < trace.frameCount()) {
            TraceFrame driveFrame = trace.getFrame(driveTraceIndex);
            if (previousDriveFrame != null) {
                if (hydrateCpuState && cpuStateCharacter != null) {
                    // Pin the engine SidekickCpuController to the ROM-recorded
                    // state at the end of the previous frame BEFORE applying
                    // the sprite reseed: applyRecordedFirstSidekickState reads
                    // cpu.getState() to gate object_control preservation, so
                    // hydrating CPU state first ensures the gate sees ROM state
                    // rather than engine-drifted state.
                    applyRecordedFirstSidekickCpuState(
                            trace.cpuStateForFrame(previousDriveFrame.frame(), cpuStateCharacter));
                }
                applyRecordedFirstSidekickState(previousDriveFrame.sidekick());
            }
            TraceExecutionPhase phase =
                    TraceReplayBootstrap.phaseForReplay(trace, previousDriveFrame, driveFrame);
            int bk2Input = phase == TraceExecutionPhase.VBLANK_ONLY
                    ? fixture.skipFrameFromRecording()
                    : fixture.stepFrameFromRecording();

            S3kCheckpointProbe probe = captureS3kProbe(driveFrame.frame(), fixture.sprite());
            var titleCardProvider = GameServices.module().getTitleCardProvider();
            boolean titleCardOverlayActive = titleCardProvider != null && titleCardProvider.isOverlayActive();
            boolean titleCardReleaseControl = titleCardProvider != null && titleCardProvider.shouldReleaseControl();
            if (lastEventsFg5 == null || lastEventsFg5 != probe.eventsFg5()) {
                System.out.printf("S3K probe frame=%d levelStarted=%b eventsFg5=%b fire=%b zone=%s act=%s apparent=%s moveLock=%d ctrlLocked=%b objCtrl=%b hidden=%b tcOverlay=%b tcRelease=%b%n",
                        driveFrame.frame(),
                        probe.levelStarted(),
                        probe.eventsFg5(),
                        probe.fireTransitionActive(),
                        probe.actualZoneId(),
                        probe.actualAct(),
                        probe.apparentAct(),
                        probe.moveLock(),
                        probe.ctrlLocked(),
                        probe.objectControlled(),
                        probe.hidden(),
                        titleCardOverlayActive,
                        titleCardReleaseControl);
                lastEventsFg5 = probe.eventsFg5();
            }
            TraceEvent.Checkpoint engineCheckpoint = detector.observe(probe);
            if (engineCheckpoint != null) {
                System.out.printf("S3K engine checkpoint frame=%d name=%s zone=%s act=%s apparent=%s levelStarted=%b moveLock=%d ctrlLocked=%b objCtrl=%b hidden=%b eventsFg5=%b tcOverlay=%b tcRelease=%b%n",
                        driveFrame.frame(),
                        engineCheckpoint.name(),
                        probe.actualZoneId(),
                        probe.actualAct(),
                        probe.apparentAct(),
                        probe.levelStarted(),
                        probe.moveLock(),
                        probe.ctrlLocked(),
                        probe.objectControlled(),
                        probe.hidden(),
                        probe.eventsFg5(),
                        titleCardOverlayActive,
                        titleCardReleaseControl);
            }
            controller.onEngineTick();
            controller.assertWithinDriftBudget();

            // Diagnostic-only: when the trace carries per-frame oscillation
            // snapshots (v6.1+ S3K recorder), compare engine OscillationManager
            // state to ROM Oscillating_table state at this frame and print the
            // first divergence. Engine must produce the correct phase
            // natively — never hydrate from these values.
            if (hasPerFrameOsc && firstOscDivFrame < 0) {
                TraceEvent.OscillationState romOsc = trace.oscillationStateForFrame(driveFrame.frame());
                if (romOsc != null && romOsc.oscTable() != null && romOsc.oscTable().length == 0x42) {
                    byte[] eng = com.openggf.game.OscillationManager.snapshotRomFormatBytes();
                    byte[] rom = romOsc.oscTable();
                    if (eng.length == rom.length) {
                        for (int b = 0; b < eng.length; b++) {
                            if (eng[b] != rom[b]) {
                                firstOscDivFrame = driveFrame.frame();
                                System.out.printf("FIRST OSC DIVERGENCE at frame %d (idx=%d): byte %d ROM=0x%02X ENG=0x%02X%n",
                                        driveFrame.frame(), b, b, rom[b] & 0xFF, eng[b] & 0xFF);
                                break;
                            }
                        }
                    }
                }
            }

            if (controller.isStrictComparisonEnabled()) {
                int strictTraceIndex = controller.strictTraceIndex();
                TraceFrame expected = trace.getFrame(strictTraceIndex);
                TraceReplayBootstrap.ReplayPrimaryState actualPrimary =
                        TraceReplayBootstrap.capturePrimaryReplayStateForComparison(
                                trace, expected, fixture.sprite());
                EngineDiagnostics engineDiag = captureEngineDiagnostics(fixture.sprite());
                String romDiag = combineDiagnostics(
                        expected.hasExtendedData() ? expected.formatDiagnostics() : "",
                        TraceEventFormatter.summariseFrameEvents(trace.getEventsForFrame(strictTraceIndex)));
                TraceCharacterState actualSidekick = captureFirstSidekickState();
                String secondaryCharacterLabel = meta.recordedSidekicks().isEmpty()
                        ? "sidekick"
                        : meta.recordedSidekicks().getFirst();

                binder.compareFrame(expected,
                        actualPrimary.x(), actualPrimary.y(),
                        actualPrimary.xSpeed(), actualPrimary.ySpeed(), actualPrimary.gSpeed(),
                        actualPrimary.angle(),
                        actualPrimary.air(), actualPrimary.rolling(),
                        actualPrimary.groundMode(), romDiag,
                        engineDiag,
                        secondaryCharacterLabel,
                        actualSidekick);

                // Keep strict comparisons honest: the sidekick state above is
                // the engine-produced result for this frame. Re-seed only after
                // recording the comparison so sidekick drift is reported, while
                // later Sonic comparisons stay isolated from accumulated Tails
                // divergence. Hydrate CPU first so object_control preservation
                // gate sees the ROM-recorded routine before sprite reseed.
                if (hydrateCpuState && cpuStateCharacter != null) {
                    applyRecordedFirstSidekickCpuState(
                            trace.cpuStateForFrame(driveFrame.frame(), cpuStateCharacter));
                }
                applyRecordedFirstSidekickState(driveFrame.sidekick());

                TraceEvent.Checkpoint traceCheckpoint = trace.latestCheckpointAtOrBefore(strictTraceIndex);
                if (traceCheckpoint != null && traceCheckpoint.frame() == strictTraceIndex) {
                    checkpointGuard.validateStrictEntry(
                            strictTraceIndex,
                            traceCheckpoint,
                            engineCheckpoint,
                            detector.requiredCheckpointNamesReached());
                    controller.onEntryFrameValidated(traceCheckpoint);
                }

                if (firstSubDivFrame < 0 && expected.xSub() > 0) {
                    int engXSub = actualPrimary.xSub();
                    int romXSub = expected.xSub();
                    int engYSub = actualPrimary.ySub();
                    int romYSub = expected.ySub();
                    if (engXSub != romXSub || engYSub != romYSub) {
                        firstSubDivFrame = expected.frame();
                        System.out.printf("FIRST SUB DIVERGENCE at frame %d: xsub ROM=0x%04X ENG=0x%04X " +
                                        "ysub ROM=0x%04X ENG=0x%04X cx=0x%04X cy=0x%04X xs=%d/%d ys=%d/%d air=%b/%b%n",
                                expected.frame(), romXSub, engXSub, romYSub, engYSub,
                                actualPrimary.x(), actualPrimary.y(),
                                actualPrimary.xSpeed(), expected.xSpeed(),
                                actualPrimary.ySpeed(), expected.ySpeed(),
                                actualPrimary.air(), expected.air());
                    }
                }
            } else {
                if (hydrateCpuState && cpuStateCharacter != null) {
                    applyRecordedFirstSidekickCpuState(
                            trace.cpuStateForFrame(driveFrame.frame(), cpuStateCharacter));
                }
                applyRecordedFirstSidekickState(driveFrame.sidekick());
            }

            boolean strictBeforeCheckpoint = controller.isStrictComparisonEnabled();
            int naturalNextDriveIndex = driveTraceIndex + 1;
            if (engineCheckpoint != null) {
                controller.onEngineCheckpoint(engineCheckpoint);
                int skippedTraceFrames = controller.driveTraceIndex() - naturalNextDriveIndex;
                if (skippedTraceFrames > 0) {
                    fixture.advanceRecordingCursor(skippedTraceFrames);
                }
            }

            if (engineCheckpoint != null
                    && !strictBeforeCheckpoint
                    && controller.isStrictComparisonEnabled()) {
                driveTraceIndex = controller.driveTraceIndex();
                previousDriveFrame = driveTraceIndex > 0
                        ? trace.getFrame(driveTraceIndex - 1)
                        : null;
                continue;
            }

            controller.advanceDriveCursor();
            driveTraceIndex = controller.driveTraceIndex();
            previousDriveFrame = driveFrame;
        }
    }

    private Map<String, Integer> loadCheckpointFrames(TraceData trace) {
        Map<String, Integer> frames = new LinkedHashMap<>();
        for (int frame = 0; frame < trace.frameCount(); frame++) {
            for (TraceEvent event : trace.getEventsForFrame(frame)) {
                if (event instanceof TraceEvent.Checkpoint checkpoint) {
                    frames.putIfAbsent(checkpoint.name(), checkpoint.frame());
                }
            }
        }
        return frames;
    }

    private S3kCheckpointProbe captureS3kProbe(int replayFrame, AbstractPlayableSprite sprite) {
        boolean resultsActive = GameServices.level().getObjectManager().getActiveObjects().stream()
                .anyMatch(S3kResultsScreenObjectInstance.class::isInstance);
        boolean signpostActive = S3kSignpostInstance.getActiveSignpost() != null;
        boolean eventsFg5 =
                GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager
                        && manager.isEventsFg5();
        boolean fireTransitionActive =
                GameServices.module().getLevelEventProvider() instanceof Sonic3kLevelEventManager manager
                        && manager.isFireTransitionActive();
        boolean hczTransitionActive =
                Aiz2BossEndSequenceState.isCutsceneOverrideObjectsActive() && !resultsActive;
        var titleCardProvider = GameServices.module().getTitleCardProvider();
        boolean titleCardOverlayActive = titleCardProvider != null && titleCardProvider.isOverlayActive();
        Integer traceGameMode = resolveS3kTraceGameMode(titleCardOverlayActive);

        return new S3kCheckpointProbe(
                replayFrame,
                GameServices.level().getCurrentZone(),
                GameServices.level().getCurrentAct(),
                GameServices.level().getApparentAct(),
                traceGameMode,
                sprite.getMoveLockTimer(),
                sprite.isControlLocked(),
                sprite.isObjectControlled(),
                sprite.isHidden(),
                eventsFg5,
                fireTransitionActive,
                hczTransitionActive,
                signpostActive,
                resultsActive,
                GameServices.camera().isLevelStarted(),
                titleCardOverlayActive);
    }

    private Integer resolveS3kTraceGameMode(boolean titleCardOverlayActive) {
        Engine engine = Engine.getInstance();
        GameMode currentMode = engine != null ? engine.getCurrentGameMode() : null;
        boolean levelStarted = GameServices.camera() != null && GameServices.camera().isLevelStarted();
        if (currentMode == null) {
            return levelStarted ? 0x0C : 0x04;
        }
        return switch (currentMode) {
            case LEVEL, TITLE_CARD -> levelStarted ? 0x0C : 0x04;
            case SPECIAL_STAGE -> 0x10;
            case SPECIAL_STAGE_RESULTS -> 0x14;
            case TITLE_SCREEN, MASTER_TITLE_SCREEN -> 0x00;
            case LEVEL_SELECT -> 0x08;
            case DATA_SELECT -> 0x18;
            case CREDITS_TEXT, CREDITS_DEMO, TRY_AGAIN_END, ENDING_CUTSCENE, EDITOR, BONUS_STAGE -> null;
        };
    }

    private Path findBk2File(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files
                .filter(p -> p.toString().endsWith(".bk2"))
                .findFirst()
                .orElse(null);
        }
    }

    private static String combineDiagnostics(String base, String extra) {
        if (base == null || base.isEmpty()) {
            return extra == null ? "" : extra;
        }
        if (extra == null || extra.isEmpty()) {
            return base;
        }
        return base + " | " + extra;
    }

    private static String formatCharacterDiagnostics(String label, TraceCharacterState state) {
        if (state == null) {
            return "";
        }
        return state.formatDiagnostics(label);
    }

    private void applyPreTracePlayerHistory(TraceEvent.PlayerHistorySnapshot snapshot,
            AbstractPlayableSprite sprite) {
        if (snapshot == null || sprite == null) {
            return;
        }
        sprite.hydrateRecordedHistory(
                TraceHistoryHydration.centreHistoryToTopLeft(snapshot.xHistory(), sprite.getWidth()),
                TraceHistoryHydration.centreHistoryToTopLeft(snapshot.yHistory(), sprite.getHeight()),
                snapshot.inputHistory(),
                snapshot.statusHistory(),
                TraceHistoryHydration.romHistoryPosToEngineLatestSlot(snapshot.historyPos()));
    }

    private void applyPreTraceSidekickSnapshot(List<TraceEvent.ObjectStateSnapshot> snapshots) {
        TraceEvent.ObjectStateSnapshot sidekickSnapshot = snapshots.stream()
                .filter(snapshot -> snapshot.slot() == 1)
                .findFirst()
                .orElse(null);
        if (sidekickSnapshot == null) {
            return;
        }

        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return;
        }

        AbstractPlayableSprite sidekick = spriteManager.getSidekicks().getFirst();
        hydrateSidekickFromSnapshot(sidekick, sidekickSnapshot.fields());
    }

    private TraceCharacterState captureFirstSidekickState() {
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return null;
        }
        return captureCharacterState(spriteManager.getSidekicks().getFirst());
    }

    private void applyRecordedFirstSidekickState(TraceCharacterState state) {
        if (state == null) {
            return;
        }
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return;
        }

        AbstractPlayableSprite sidekick = spriteManager.getSidekicks().getFirst();
        if (!state.present()) {
            sidekick.setHidden(true);
            sidekick.setDead(true);
            sidekick.setCentreX((short) 0);
            sidekick.setCentreY((short) 0);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setSubpixelRaw(0, 0);
            return;
        }

        sidekick.setHidden(false);
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        sidekick.setControlLocked(false);
        // Do NOT reset move_lock here. ROM Player_SlopeRepel (sonic3k.asm:23907)
        // sets move_lock=30 on its first slip activation and then decrements it
        // for the next 30 frames, during which the routine early-returns without
        // touching ground_vel. Resetting move_lock to 0 each frame defeats that
        // ROM-preserved counter and causes the engine to re-apply the −$80
        // slope-repel impulse every frame, which was the root cause of the
        // CNZ1 F318 `tails_g_speed` divergence (ROM preserves move_lock across
        // frames so SlopeRepel's second-and-later frames are no-ops).
        //
        // Preserve object_control only when the engine's CPU controller is in
        // SPAWNING state AND the engine still has Tails parked at the ROM
        // despawn marker (#$7F00). ROM sub_13ECA (sonic3k.asm:26800) writes
        // object_control=$81 atomically with x_pos=#$7F00 / y_pos=#$0 to enter
        // Tails_Catch_Up_Flying / Tails_FlySwim_Unknown after a despawn. Bit 7
        // of object_control suppresses the entire sprite-movement dispatch in
        // the ROM (Obj_Routines reads object_control before dispatching to the
        // per-character movement handler), so clearing it every frame defeats
        // the engine's flight-physics suppression and lets
        // PlayableSpriteMovement.modeNormal() run full ground physics (slope
        // decomposition flips y_speed from −0x33 to +0x05) — that was the
        // CNZ1 F826 `tails_y_speed` divergence. The trace CSV does NOT capture
        // object_control, so we infer it from the engine's own
        // SidekickCpuController state plus the ROM-atomic x==marker signal.
        // The gate intentionally does not extend past the marker frames: in
        // AIZ the engine's state machine parks in SPAWNING for tens of
        // thousands of frames after upstream divergence (with Tails at a real
        // x), so the marker-only gate keeps the AIZ replay from accumulating
        // object_control-suppressed frames the recording does not expect. Same
        // precedent as the move_lock preservation directly above.
        SidekickCpuController cpu = sidekick.getCpuController();
        int xBeforeReseed = sidekick.getCentreX() & 0xFFFF;
        int despawnX =
                com.openggf.game.PhysicsFeatureSet.SIDEKICK_DESPAWN_X_S3K & 0xFFFF;
        // Preserve object_control for either the engine's SPAWNING state (legacy
        // transitional state we use during respawn flow) or CATCH_UP_FLIGHT /
        // FLIGHT_AUTO_RECOVERY (ROM Tails_CPU_routine 0x02/0x04 — sub_13ECA at
        // sonic3k.asm:26800 writes object_control=$81 atomically with x_pos=$7F00 /
        // y_pos=0 and Tails_CPU_routine=2). The dispatcher early-returns on the
        // ROM's pollers (Tails_Catch_Up_Flying / Tails_FlySwim_Unknown) until an
        // exit event fires, leaving Tails parked at the marker with object_control
        // bit 7 set so the per-character movement handler is suppressed. Clearing
        // object_control here lets PlayableSpriteMovement.modeNormal() run a full
        // ground physics step on the hydrated marker position, which re-introduces
        // the F826 tails_y_speed -0x33 -> +0x05 divergence the v6 trace exposes
        // after the F1758 partial fix.
        int targetX = state.x() & 0xFFFF;
        boolean atDespawnMarker = xBeforeReseed == despawnX || targetX == despawnX;
        boolean preserveObjectControl = cpu != null
                && atDespawnMarker
                && (cpu.getState() == SidekickCpuController.State.SPAWNING
                    || cpu.getState() == SidekickCpuController.State.CATCH_UP_FLIGHT
                    || cpu.getState() == SidekickCpuController.State.FLIGHT_AUTO_RECOVERY);
        if (!preserveObjectControl) {
            sidekick.setObjectControlled(false);
        }
        sidekick.setHurt(state.routine() == 0x04);
        sidekick.setCentreX(state.x());
        sidekick.setCentreY(state.y());
        sidekick.setXSpeed(state.xSpeed());
        sidekick.setYSpeed(state.ySpeed());
        sidekick.setGSpeed(state.gSpeed());
        sidekick.setAngle(state.angle());
        sidekick.setDirection((state.statusByte() & 0x01) != 0
                ? com.openggf.physics.Direction.LEFT
                : com.openggf.physics.Direction.RIGHT);
        sidekick.setAir(state.air());
        sidekick.setRolling(state.rolling());
        sidekick.setOnObject((state.statusByte() & 0x08) != 0);
        sidekick.setRollingJump((state.statusByte() & 0x10) != 0);
        sidekick.setPushing((state.statusByte() & 0x20) != 0);
        sidekick.setGroundMode(groundModeFromOrdinal(state.groundMode()));
        sidekick.setSubpixelRaw(state.xSub(), state.ySub());
        sidekick.resetPositionHistory();

        // Do not rewrite the CPU controller state here. CNZ's opening
        // Sonic+Tails carry is driven by the sidekick CPU routine; the trace
        // state supplies frame-boundary position/speed, not a replacement CPU
        // routine stream.
    }

    /**
     * Hydrates the first sidekick's {@link SidekickCpuController} from a v6+
     * per-frame {@link TraceEvent.CpuState} event. This mirrors what
     * {@link #applyRecordedFirstSidekickState} does for the sprite-side
     * physics state: it pins the engine CPU controller to the ROM-recorded
     * authoritative state at the end of the previous frame so the AI tick
     * during the next frame's engine step uses the same inputs the ROM saw,
     * eliminating CPU-state drift as a source of trace divergence.
     *
     * <p>Pre-conditions:
     * <ul>
     * <li>The trace metadata declares {@code cpu_state_per_frame} support
     *   (caller checks {@link TraceMetadata#hasPerFrameCpuState()}).</li>
     * <li>{@code cpuState} is the {@link TraceEvent.CpuState} event for the
     *   trace frame whose sidekick state is being applied (typically the
     *   previous-drive-frame event when the engine is about to step into the
     *   next frame).</li>
     * </ul>
     *
     * <p>If {@code cpuState} is {@code null} (e.g. older trace, or the event
     * is missing for that frame), this method is a no-op.
     */
    private void applyRecordedFirstSidekickCpuState(TraceEvent.CpuState cpuState) {
        if (cpuState == null) {
            return;
        }
        SpriteManager spriteManager = GameServices.sprites();
        if (spriteManager == null || spriteManager.getSidekicks().isEmpty()) {
            return;
        }
        SidekickCpuController controller =
                spriteManager.getSidekicks().getFirst().getCpuController();
        if (controller == null) {
            return;
        }
        controller.hydrateFromRomCpuStatePerFrame(
                cpuState.cpuRoutine(),
                cpuState.idleTimer(),
                cpuState.flightTimer(),
                cpuState.autoFlyTimer(),
                cpuState.autoJumpFlag(),
                cpuState.ctrl2Held(),
                cpuState.ctrl2Pressed());
    }

    private static GroundMode groundModeFromOrdinal(int ordinal) {
        GroundMode[] values = GroundMode.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return GroundMode.GROUND;
        }
        return values[ordinal];
    }

    private TraceCharacterState captureCharacterState(AbstractPlayableSprite sprite) {
        ObjectManager om = GameServices.level() != null
                ? GameServices.level().getObjectManager() : null;
        int standOnSlot = -1;
        if (om != null) {
            ObjectInstance ridingObj = om.getRidingObject(sprite);
            if (ridingObj instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= 0) {
                standOnSlot = aoi.getSlotIndex();
            }
        }

        int statusByte = 0;
        if (sprite.getDirection() == com.openggf.physics.Direction.LEFT) {
            statusByte |= 0x01;
        }
        if (sprite.getAir()) statusByte |= 0x02;
        if (sprite.getRolling()) statusByte |= 0x04;
        if (sprite.isOnObject()) statusByte |= 0x08;

        int routine = sprite.isHurt() ? 0x04 : 0x02;

        return new TraceCharacterState(true,
                sprite.getCentreX(),
                sprite.getCentreY(),
                sprite.getXSpeed(),
                sprite.getYSpeed(),
                sprite.getGSpeed(),
                sprite.getAngle(),
                sprite.getAir(),
                sprite.getRolling(),
                sprite.getGroundMode().ordinal(),
                sprite.getXSubpixelRaw(),
                sprite.getYSubpixelRaw(),
                routine,
                statusByte,
                standOnSlot);
    }

    private void hydrateSidekickFromSnapshot(AbstractPlayableSprite sidekick,
            RomObjectSnapshot snapshot) {
        sidekick.setControlLocked(false);
        sidekick.setObjectControlled(false);
        sidekick.setMoveLockTimer(0);
        sidekick.setHurt(false);
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setXSpeed((short) snapshot.xVel());
        sidekick.setYSpeed((short) snapshot.yVel());
        sidekick.setGSpeed((short) snapshot.signedWordAt(0x14));
        sidekick.setAngle((byte) snapshot.angle());
        sidekick.setDirection((snapshot.status() & 0x01) != 0
                ? com.openggf.physics.Direction.LEFT
                : com.openggf.physics.Direction.RIGHT);
        sidekick.setAir((snapshot.status() & 0x02) != 0);
        sidekick.setRolling((snapshot.status() & 0x04) != 0);
        sidekick.setOnObject((snapshot.status() & 0x08) != 0);
        sidekick.setCentreX((short) snapshot.xPos());
        sidekick.setCentreY((short) snapshot.yPos());
        sidekick.setSubpixelRaw(snapshot.xSub(), snapshot.ySub());
        sidekick.resetPositionHistory();

        SidekickCpuController controller = sidekick.getCpuController();
        if (controller != null) {
            controller.setInitialState(SidekickCpuController.State.NORMAL);
        }
    }



    /**
     * Capture engine-side diagnostic state for the context window.
     * These values are NOT compared for pass/fail â€” they appear alongside
     * ROM trace diagnostics for human cross-referencing.
     */
    private EngineDiagnostics captureEngineDiagnostics(AbstractPlayableSprite sprite) {
        // Routine: S1 uses 0=init, 2=control, 4=hurt, 6=death
        int routine = sprite.isHurt() ? 0x04 : 0x02;

        // Riding object: which SST slot is the player standing on?
        int standOnSlot = -1;
        int standOnType = -1;
        ObjectManager om = GameServices.level() != null
                ? GameServices.level().getObjectManager() : null;
        if (om != null) {
            ObjectInstance ridingObj = om.getRidingObject(sprite);
            if (ridingObj instanceof AbstractObjectInstance aoi && aoi.getSlotIndex() >= 0) {
                standOnSlot = aoi.getSlotIndex();
                standOnType = aoi.getSpawn() != null ? aoi.getSpawn().objectId() : -1;
            }
        }

        // Ring count
        int rings = sprite.getRingCount();

        // Status byte (replicate ROM's status encoding)
        int statusByte = 0;
        if (sprite.getDirection() == com.openggf.physics.Direction.LEFT)
            statusByte |= 0x01;
        if (sprite.getAir()) statusByte |= 0x02;
        if (sprite.getRolling()) statusByte |= 0x04;
        if (sprite.isOnObject()) statusByte |= 0x08;

        // Camera X for cross-reference with ROM trace
        int camX = GameServices.camera() != null ? GameServices.camera().getX() : -1;

        // Placement cursor state for ROMâ†”engine comparison
        int cursorIdx = -1, leftCursorIdx = -1, fwdCtr = -1, bwdCtr = -1;
        if (om != null) {
            int[] cursor = om.getPlacementCursorState();
            if (cursor != null) {
                cursorIdx = cursor[0];
                leftCursorIdx = cursor[1];
                fwdCtr = cursor[2];
                bwdCtr = cursor[3];
            }
        }

        // Subpixels for cross-referencing with ROM trace sub=(xsub,ysub)
        int xSub = sprite.getXSubpixelRaw();
        int ySub = sprite.getYSubpixelRaw();

        String solidEvent = "";
        if (om != null) {
            TouchResponseDebugState touchState = om.getTouchResponseDebugState();
            if (touchState != null) {
                solidEvent = combineDiagnostics(solidEvent, String.format(
                        "touchBox @%04X,%04X h=%d yr=%d crouch=%d",
                        touchState.getPlayerX() & 0xFFFF,
                        touchState.getPlayerY() & 0xFFFF,
                        touchState.getPlayerHeight(),
                        touchState.getPlayerYRadius(),
                        touchState.isCrouching() ? 1 : 0));
            }
            if (touchState != null && !touchState.getHits().isEmpty()) {
                solidEvent = combineDiagnostics(solidEvent,
                        TouchResponseDebugHitFormatter.summariseOverlaps(touchState.getHits()));
                solidEvent = combineDiagnostics(solidEvent,
                        TouchResponseDebugHitFormatter.summariseNearbyScans(
                                touchState.getHits(),
                                sprite.getCentreX(),
                                sprite.getCentreY()));
            }

            List<EngineNearbyObject> nearbyObjects = new ArrayList<>();
            for (ObjectInstance instance : om.getActiveObjects()) {
                if (!(instance instanceof AbstractObjectInstance aoi)) {
                    continue;
                }
                ObjectSpawn spawn = aoi.getSpawn();
                if (spawn == null || spawn.objectId() == 0) {
                    continue;
                }
                int currentX = aoi.getX();
                int currentY = aoi.getY();
                int dx = Math.abs(currentX - sprite.getCentreX());
                int dy = Math.abs(currentY - sprite.getCentreY());
                if (dx > 160 || dy > 160) {
                    continue;
                }
                TouchResponseProvider provider =
                        instance instanceof TouchResponseProvider trp ? trp : null;
                nearbyObjects.add(new EngineNearbyObject(
                        aoi.getSlotIndex(),
                        spawn.objectId(),
                        aoi.getName(),
                        currentX,
                        currentY,
                        spawn.x(),
                        spawn.y(),
                        provider != null,
                        provider != null ? provider.getCollisionFlags() : -1,
                        provider != null ? aoi.getPreUpdateCollisionFlags() : -1,
                        aoi.getPreUpdateX(),
                        aoi.getPreUpdateY(),
                        aoi.isSkipTouchThisFrame(),
                        aoi.isSkipSolidContactThisFrame(),
                        aoi.isOnScreenForTouch(),
                        aoi.traceDebugDetails()));
            }
            nearbyObjects.sort(Comparator.comparingInt(EngineNearbyObject::slot));
            solidEvent = combineDiagnostics(solidEvent,
                    EngineNearbyObjectFormatter.summarise(nearbyObjects));
        }

        return new EngineDiagnostics(routine, standOnSlot, standOnType, rings, statusByte, camX,
                cursorIdx, leftCursorIdx, fwdCtr, bwdCtr, solidEvent, xSub, ySub);
    }

    private void writeReport(DivergenceReport report, TraceMetadata meta) {
        try {
            Path outDir = reportOutputDir();
            Files.createDirectories(outDir);

            String prefix = meta.game() + "_" + meta.zone() + meta.act();
            Path jsonPath = outDir.resolve(prefix + "_report.json");
            Files.writeString(jsonPath, report.toJson());

            if (report.hasErrors()) {
                DivergenceGroup firstError = report.errors().get(0);
                Path contextPath = outDir.resolve(prefix + "_context.txt");
                Files.writeString(contextPath,
                    report.getContextWindow(firstError.startFrame(), 20));
            }
        } catch (IOException e) {
            System.err.println("Warning: failed to write report: " + e.getMessage());
        }
    }
}
