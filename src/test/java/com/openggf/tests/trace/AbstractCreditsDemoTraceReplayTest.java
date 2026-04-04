package com.openggf.tests.trace;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.credits.DemoInputPlayer;
import com.openggf.game.sonic1.credits.Sonic1CreditsDemoData;
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
 * Abstract base class for credits demo trace replay tests. Subclasses provide
 * the demo index (0-7) and trace directory; this class handles level loading,
 * ROM demo input replay via {@link DemoInputPlayer}, per-frame comparison
 * against recorded trace data, and divergence report output.
 *
 * <p>Unlike {@link AbstractTraceReplayTest} which uses BK2 movie input, this
 * class reads pre-recorded demo input directly from the ROM. The credits demos
 * are short (510-540 frame) gameplay sequences that play during the Sonic 1
 * ending credits.
 *
 * <p>Uses JUnit 4 because {@link RequiresRomRule} is a JUnit 4 {@code TestRule}.
 * Subclasses should add {@code @RequiresRom(SonicGame.SONIC_1)} — the abstract
 * class intentionally does NOT carry that annotation so subclasses control it.
 */
public abstract class AbstractCreditsDemoTraceReplayTest {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    /** Credits demo index (0-7). */
    protected abstract int creditsDemoIndex();

    /** Path to the trace directory containing metadata.json and physics.csv. */
    protected abstract Path traceDirectory();

    /** Override to supply custom tolerances. */
    protected ToleranceConfig tolerances() {
        return ToleranceConfig.DEFAULT;
    }

    /** Override to change report output directory. */
    protected Path reportOutputDir() {
        return Path.of("target/trace-reports");
    }

    @Test
    public void replayMatchesTrace() throws Exception {
        int idx = creditsDemoIndex();
        assertTrue("creditsDemoIndex must be 0-7", idx >= 0 && idx < Sonic1CreditsDemoData.DEMO_CREDITS);

        // 0. Skip if trace directory or required files are missing
        Path traceDir = traceDirectory();
        Assume.assumeTrue("Trace directory not found: " + traceDir,
            Files.isDirectory(traceDir));
        Assume.assumeTrue("metadata.json not found in " + traceDir,
            Files.exists(traceDir.resolve("metadata.json")));
        Assume.assumeTrue("physics.csv not found in " + traceDir,
            Files.exists(traceDir.resolve("physics.csv")));

        // 1. Load trace data
        TraceData trace = TraceData.load(traceDir);

        // 2. Load demo input from ROM
        Rom rom = GameServices.rom().getRom();
        int demoAddr = Sonic1CreditsDemoData.DEMO_DATA_ADDR[idx];
        int demoSize = Sonic1CreditsDemoData.DEMO_DATA_SIZE[idx];
        byte[] demoData = rom.readBytes(demoAddr, demoSize);
        DemoInputPlayer demoPlayer = new DemoInputPlayer(demoData);

        // 3. Load level and create fixture
        int zone = Sonic1CreditsDemoData.DEMO_ZONE[idx];
        int act = Sonic1CreditsDemoData.DEMO_ACT[idx];
        short startX = (short) Sonic1CreditsDemoData.START_X[idx];
        short startY = (short) Sonic1CreditsDemoData.START_Y[idx];

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_1, zone, act);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition(startX, startY)
                .startPositionIsCentre()
                .build();

            // 4. Determine frame limit: min of trace frames and demo timer
            int frameLimit = Math.min(trace.frameCount(),
                Sonic1CreditsDemoData.DEMO_TIMER[idx]);

            // 5. Run frame-by-frame comparison
            TraceBinder binder = new TraceBinder(tolerances());

            for (int i = 0; i < frameLimit; i++) {
                TraceFrame expected = trace.getFrame(i);

                // Advance demo input and get current input mask
                demoPlayer.advanceFrame();
                int inputMask = demoPlayer.getInputMask();

                // Decompose input mask into individual button states
                boolean up    = (inputMask & AbstractPlayableSprite.INPUT_UP) != 0;
                boolean down  = (inputMask & AbstractPlayableSprite.INPUT_DOWN) != 0;
                boolean left  = (inputMask & AbstractPlayableSprite.INPUT_LEFT) != 0;
                boolean right = (inputMask & AbstractPlayableSprite.INPUT_RIGHT) != 0;
                boolean jump  = (inputMask & AbstractPlayableSprite.INPUT_JUMP) != 0;

                // Step engine with demo input
                fixture.stepFrame(up, down, left, right, jump);

                // Capture engine-side diagnostic state for context window
                var sprite = fixture.sprite();
                EngineDiagnostics engineDiag = captureEngineDiagnostics(sprite);

                binder.compareFrame(expected,
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
                writeReport(report, idx);
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

        // Camera X for cross-reference with ROM trace
        int camX = GameServices.camera() != null ? GameServices.camera().getX() : -1;

        // Placement cursor state for ROM<->engine comparison
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

        return new EngineDiagnostics(routine, standOnSlot, standOnType, rings, statusByte, camX,
                cursorIdx, leftCursorIdx, fwdCtr, bwdCtr, "", xSub, ySub);
    }

    private void writeReport(DivergenceReport report, int demoIndex) {
        try {
            Path outDir = reportOutputDir();
            Files.createDirectories(outDir);

            String prefix = String.format("s1_credits_%02d_%s%d",
                demoIndex,
                zoneSlug(demoIndex),
                Sonic1CreditsDemoData.DEMO_ACT[demoIndex] + 1);

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

    /**
     * Returns a short zone slug for report filenames.
     */
    private static String zoneSlug(int demoIndex) {
        return switch (Sonic1CreditsDemoData.DEMO_ZONE[demoIndex]) {
            case 0 -> "ghz";
            case 1 -> "mz";
            case 2 -> "syz";
            case 3 -> "lz";
            case 4 -> "slz";
            case 5 -> "sbz";
            default -> "unk";
        };
    }
}
