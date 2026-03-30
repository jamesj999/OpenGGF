package com.openggf.tests.trace;

import com.openggf.game.GameServices;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.SharedLevel;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Abstract base class for trace replay tests. Subclasses provide game/zone/act/path;
 * this class handles level loading, BK2 playback, per-frame comparison, and report output.
 *
 * <p>Uses JUnit 4 because {@link RequiresRomRule} is a JUnit 4 {@code TestRule}.
 */
public abstract class AbstractTraceReplayTest {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

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
        Assume.assumeTrue("Trace directory not found: " + traceDir,
            Files.isDirectory(traceDir));
        Assume.assumeTrue("metadata.json not found in " + traceDir,
            Files.exists(traceDir.resolve("metadata.json")));
        Assume.assumeTrue("physics.csv not found in " + traceDir,
            Files.exists(traceDir.resolve("physics.csv")));

        // 1. Find BK2 file in trace directory (check before loading trace data)
        Path bk2Path = findBk2File(traceDir);
        Assume.assumeTrue("No .bk2 file found in " + traceDir, bk2Path != null);

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
            //     v_vbla_byte at trace start. The ROM's v_vbla_byte counts ALL
            //     VBlanks since power-on. Objects with timing gates like
            //     (v_vbla_byte + d7) & 7 (Batbrain drop check) depend on this.
            //     The bk2FrameOffset equals v_vbla_byte at trace start.
            //     ObjectManager.update() increments vblaCounter BEFORE passing
            //     it to objects, so we initialize one below the offset so the
            //     first increment lands on bk2FrameOffset exactly.
            ObjectManager om = GameServices.level().getObjectManager();
            if (om != null) {
                om.initVblaCounter(meta.bk2FrameOffset() - 1);
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

            // 5. Run frame-by-frame comparison
            TraceBinder binder = new TraceBinder(tolerances());

            for (int i = 0; i < trace.frameCount(); i++) {
                TraceFrame expected = trace.getFrame(i);

                // Lag frame detection: skip physics only for frames where the
                // ROM truly didn't complete Level_MainLoop. The heuristic
                // requires BOTH identical physics state AND non-zero speed/air,
                // avoiding false positives when Sonic is standing still.
                int bk2Input;
                if (trace.isLagFrame(i)) {
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

                var frameResult = binder.compareFrame(expected,
                    sprite.getCentreX(), sprite.getCentreY(),
                    sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                    sprite.getAngle(),
                    sprite.getAir(), sprite.getRolling(),
                    sprite.getGroundMode().ordinal(),
                    engineDiag);


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
        assertEquals("Metadata game mismatch (test says " + game()
            + " but metadata says " + meta.game() + ")",
            expectedGameId, meta.game());
    }

    private Path findBk2File(Path dir) throws IOException {
        try (var files = Files.list(dir)) {
            return files
                .filter(p -> p.toString().endsWith(".bk2"))
                .findFirst()
                .orElse(null);
        }
    }



    /**
     * Capture engine-side diagnostic state for the context window.
     * These values are NOT compared for pass/fail — they appear alongside
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

        return new EngineDiagnostics(routine, standOnSlot, standOnType, rings, statusByte, "");
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
