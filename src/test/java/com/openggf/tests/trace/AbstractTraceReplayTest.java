package com.openggf.tests.trace;

import com.openggf.debug.DebugOverlayToggle;
import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseDebugHit;
import com.openggf.level.objects.TouchResponseDebugState;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

        // 3. Validate test configuration matches metadata
        validateMetadata(meta);

        // 4. Load level and create fixture
        SharedLevel sharedLevel = SharedLevel.load(game(), zone(), act());
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition(meta.startX(), meta.startY())
                .startPositionIsCentre()
                .withRecording(bk2Path)
                .withRecordingStartFrame(meta.bk2FrameOffset())
                .build();

            // 4a. ROM parity: Initialize ObjectManager's frame counter to match
            //     v_vbla_byte at trace start. Schema v3 traces record the ROM
            //     VBlank counter directly; older traces fall back to the
            //     historical BK2-offset heuristic.
            ObjectManager om = GameServices.level().getObjectManager();
            if (om != null) {
                om.initVblaCounter(trace.initialVblankCounter() - 1);
            }
            if (GameServices.debugOverlay() != null) {
                GameServices.debugOverlay().setEnabled(DebugOverlayToggle.TOUCH_RESPONSE, true);
            }




            // 4b. Pre-advance oscillation to match ROM phase.
            //      The ROM runs OscillateNumDo during Level_MainLoop frames
            //      that occur BEFORE the trace recording starts (between level
            //      load and the Lua script trigger). The engine must advance
            //      the oscillation by the same number of frames.
            int preTraceOsc = overridePreTraceOscFrames() >= 0
                    ? overridePreTraceOscFrames()
                    : meta.preTraceOscillationFrames();
            if (preTraceOsc > 0) {
                for (int i = 0; i < preTraceOsc; i++) {
                    // Use negative frame counters to avoid collisions with the
                    // real frame counter that starts at 0 during the test loop.
                    com.openggf.game.OscillationManager.update(-(preTraceOsc - i));
                }
            }

            // 4c. Hydrate object state machines from pre-trace ROM SST snapshots.
            //     Schema v4+ aux files include one object_state_snapshot event
            //     per occupied SST slot at frame -1. This restores routine/timer
            //     state that the ROM accumulated during title-card and level-init
            //     iterations before the Lua recorder began emitting trace frames.
            //
            //     For S2/S3K, ObjectManager defers spawn→instance conversion
            //     until the first update() tick. Preload them now so the binder
            //     has real engine instances to match against.
            List<TraceEvent.ObjectStateSnapshot> preTraceSnapshots =
                    trace.preTraceObjectSnapshots();
            if (!preTraceSnapshots.isEmpty() && om != null) {
                om.preloadInitialSpawnsForHydration();
                TraceObjectSnapshotBinder.Result hydration =
                        TraceObjectSnapshotBinder.apply(om, preTraceSnapshots);
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

            for (int i = 0; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);

                // Drive replay from recorded ROM counters instead of inferring
                // lag from unchanged physics state.
                int bk2Input;
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                    TraceExecutionModel.forGame(meta.game()).phaseFor(previous, expected);
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
                String romDiag = combineDiagnostics(
                        expected.hasExtendedData() ? expected.formatDiagnostics() : "",
                        TraceEventFormatter.summariseFrameEvents(trace.getEventsForFrame(i)));

                var frameResult = binder.compareFrame(expected,
                    sprite.getCentreX(), sprite.getCentreY(),
                    sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                    sprite.getAngle(),
                    sprite.getAir(), sprite.getRolling(),
                    sprite.getGroundMode().ordinal(), romDiag,
                    engineDiag);

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

            // 6. Build report
            DivergenceReport report = binder.buildReport();

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
            sharedLevel.dispose();
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
                        aoi.isOnScreenForTouch()));
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


