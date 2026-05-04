package com.openggf.tests.trace;
import com.openggf.trace.*;

import com.openggf.data.Rom;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.game.GameServices;
import com.openggf.game.sonic1.credits.DemoInputPlayer;
import com.openggf.game.sonic1.credits.Sonic1CreditsDemoBootstrap;
import com.openggf.game.sonic1.credits.Sonic1CreditsDemoData;
import com.openggf.game.sonic1.objects.Sonic1JunctionObjectInstance;
import com.openggf.game.sonic1.objects.Sonic1PoleThatBreaksObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
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
 * <p>Originally used JUnit 4 because the ROM fixture was exposed as a JUnit 4 rule.
 * Subclasses should add {@code @RequiresRom(SonicGame.SONIC_1)} â€” the abstract
 * class intentionally does NOT carry that annotation so subclasses control it.
 */
public abstract class AbstractCreditsDemoTraceReplayTest {
    /** Credits demo index (0-7). */
    protected abstract int creditsDemoIndex();

    /** Path to the trace directory containing metadata.json and physics.csv(.gz). */
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
        assertTrue(idx >= 0 && idx < Sonic1CreditsDemoData.DEMO_CREDITS, "creditsDemoIndex must be 0-7");

        // 0. Skip if trace directory or required files are missing
        Path traceDir = traceDirectory();
        Assumptions.assumeTrue(Files.isDirectory(traceDir), "Trace directory not found: " + traceDir);
        Assumptions.assumeTrue(Files.exists(traceDir.resolve("metadata.json")), "metadata.json not found in " + traceDir);
        Assumptions.assumeTrue(hasTracePayload(traceDir, "physics.csv"), "physics.csv(.gz) not found in " + traceDir);

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

        SonicConfigurationService config = SonicConfigurationService.getInstance();
        Object oldMainCharacter = config.getConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE);
        Object oldSidekickCharacter = config.getConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
        config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE, "sonic");
        config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE, "");

        SharedLevel sharedLevel = SharedLevel.load(SonicGame.SONIC_1, zone, act);
        try {
            HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .startPosition(startX, startY)
                .startPositionIsCentre()
                .build();
            if (GameServices.debugOverlay() != null) {
                GameServices.debugOverlay().setEnabled(DebugOverlayToggle.TOUCH_RESPONSE, true);
            }

            initialiseDemoPlayerState(fixture);

            // 3a. Demo-specific state: LZ (credit 3) needs lamppost/water setup.
            //     Uses ROM-derived constants from Sonic1CreditsDemoData ONLY;
            //     no trace data is read here per the trace-replay
            //     comparison-only invariant (see CLAUDE.md "Trace Replay Tests").
            if (idx == 3) {
                Sonic1CreditsDemoBootstrap.applyLzLampostState(
                        fixture.sprite(), fixture.camera());
            }
            resetStreamingWindows(fixture);
            // Settle the per-demo starting animation pose deterministically
            // from constants (NOT from trace.getEventsForFrame(0) — that
            // would be hydration). All other player flags match the engine's
            // post-init defaults already.
            Sonic1CreditsDemoBootstrap.applyStartingPose(idx, fixture.sprite());
            primeFrameZeroObjectState();

            // 4. Determine frame limit: min of trace frames and demo timer
            int frameLimit = Math.min(trace.frameCount(),
                Sonic1CreditsDemoData.DEMO_TIMER[idx]);

            // 5. Run frame-by-frame comparison
            TraceBinder binder = new TraceBinder(tolerances());

            for (int i = 0; i < frameLimit; i++) {
                TraceFrame expected = trace.getFrame(i);
                TraceFrame previous = i > 0 ? trace.getFrame(i - 1) : null;
                TraceExecutionPhase phase =
                    TraceReplayBootstrap.phaseForReplay(trace, previous, expected);
                if (phase == TraceExecutionPhase.VBLANK_ONLY
                        && shouldPromoteObjectControlledDemoFrame(idx, fixture.sprite())) {
                    phase = TraceExecutionPhase.FULL_LEVEL_FRAME;
                }

                // On VBlank-only frames, the ROM's main loop didn't complete, so
                // neither physics nor demo input advanced. Skip both to
                // keep the engine and demo-input cursor aligned with the
                // ROM's cursor across the replay.
                if (phase == TraceExecutionPhase.VBLANK_ONLY) {
                    // Still compare â€” engine state should match previous
                    // trace frame since neither side advanced.
                    var sprite = fixture.sprite();
                    EngineDiagnostics engineDiag = captureEngineDiagnostics(sprite);
                    String romDiag = combineDiagnostics(
                        expected.hasExtendedData() ? expected.formatDiagnostics() : "",
                        TraceEventFormatter.summariseFrameEvents(trace.getEventsForFrame(i)));
                    binder.compareFrame(expected,
                        sprite.getCentreX(), sprite.getCentreY(),
                        sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                        sprite.getAngle(),
                        sprite.getAir(), sprite.getRolling(),
                        sprite.getGroundMode().ordinal(), romDiag,
                        engineDiag);
                    continue;
                }

                // Advance demo input and get current input mask
                demoPlayer.advanceFrame();
                int inputMask = demoPlayer.getInputMask();

                // Decompose input mask into individual button states
                boolean up    = (inputMask & AbstractPlayableSprite.INPUT_UP) != 0;
                boolean down  = (inputMask & AbstractPlayableSprite.INPUT_DOWN) != 0;
                boolean left  = (inputMask & AbstractPlayableSprite.INPUT_LEFT) != 0;
                boolean right = (inputMask & AbstractPlayableSprite.INPUT_RIGHT) != 0;
                boolean jump  = (inputMask & AbstractPlayableSprite.INPUT_JUMP) != 0;

                // Emulate S1 REV01 demo-mode bug: MoveSonicInDemo (FixBugs=off,
                // Revision!=0 path) sets d2=0 instead of reading old held state,
                // so v_jpadpress1 = new_held every frame. Sonic_Control copies
                // this into v_jpadpress2, and Sonic_Jump sees the jump button
                // as "pressed" on every frame it's held. This means Sonic
                // re-jumps every time he lands with jump still held.
                // See docs/s1disasm/_inc/MoveSonicInDemo.asm:66-76.
                fixture.sprite().setForcedJumpPress(jump);

                // Step engine with demo input
                fixture.stepFrame(up, down, left, right, jump);

                // Capture engine-side diagnostic state for context window
                var sprite = fixture.sprite();
                EngineDiagnostics engineDiag = captureEngineDiagnostics(sprite);
                String romDiag = combineDiagnostics(
                    expected.hasExtendedData() ? expected.formatDiagnostics() : "",
                    TraceEventFormatter.summariseFrameEvents(trace.getEventsForFrame(i)));

                binder.compareFrame(expected,
                    sprite.getCentreX(), sprite.getCentreY(),
                    sprite.getXSpeed(), sprite.getYSpeed(), sprite.getGSpeed(),
                    sprite.getAngle(),
                    sprite.getAir(), sprite.getRolling(),
                    sprite.getGroundMode().ordinal(), romDiag,
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
            config.setConfigValue(SonicConfiguration.MAIN_CHARACTER_CODE,
                    oldMainCharacter != null ? oldMainCharacter : "sonic");
            config.setConfigValue(SonicConfiguration.SIDEKICK_CHARACTER_CODE,
                    oldSidekickCharacter != null ? oldSidekickCharacter : "tails");
        }
    }

    private void initialiseDemoPlayerState(HeadlessTestFixture fixture) {
        AbstractPlayableSprite player = fixture.sprite();
        player.setRingCount(0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setControlLocked(false);
        player.setForcedInputMask(0);
    }

    private void resetStreamingWindows(HeadlessTestFixture fixture) {
        int cameraX = fixture.camera().getX();
        if (GameServices.level().getObjectManager() != null) {
            GameServices.level().getObjectManager().reset(cameraX);
        }
        if (GameServices.level().getRingManager() != null) {
            GameServices.level().getRingManager().reset(cameraX);
        }
    }

    /**
     * Credits traces record frame-zero object state after the first ExecuteObjects pass.
     * The headless fixture respawns objects in their fresh constructor state, so prime
     * them once before replaying frame 0 to avoid a one-frame phase lag in object timers.
     */
    private void primeFrameZeroObjectState() {
        GameServices.level().updateObjectPositionsWithoutTouches();
    }

    private boolean shouldPromoteObjectControlledDemoFrame(int demoIndex,
                                                           AbstractPlayableSprite player) {
        if (player == null || !player.isObjectControlled()) {
            return false;
        }
        return switch (demoIndex) {
            case 3 -> hasActiveObject(Sonic1PoleThatBreaksObjectInstance.class);
            case 5 -> hasActiveObject(Sonic1JunctionObjectInstance.class);
            default -> false;
        };
    }

    private boolean hasActiveObject(Class<? extends AbstractObjectInstance> type) {
        ObjectManager objectManager = GameServices.level() != null
                ? GameServices.level().getObjectManager()
                : null;
        if (objectManager == null) {
            return false;
        }
        for (ObjectInstance instance : objectManager.getActiveObjects()) {
            if (type.isInstance(instance)) {
                return true;
            }
        }
        return false;
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
        if (sprite.isInWater()) statusByte |= 0x40;

        // Camera X/Y for ROM-trace cross-reference and camera_x/camera_y
        // comparison in TraceBinder.
        int camX = GameServices.camera() != null ? GameServices.camera().getX() & 0xFFFF : -1;
        int camY = GameServices.camera() != null ? GameServices.camera().getY() & 0xFFFF : -1;

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

        return new EngineDiagnostics(routine, standOnSlot, standOnType, rings, statusByte,
                camX, camY, cursorIdx, leftCursorIdx, fwdCtr, bwdCtr, solidEvent, xSub, ySub);
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

    private static String combineDiagnostics(String base, String extra) {
        if (base == null || base.isEmpty()) {
            return extra == null ? "" : extra;
        }
        if (extra == null || extra.isEmpty()) {
            return base;
        }
        return base + " | " + extra;
    }

    private static boolean hasTracePayload(Path dir, String fileName) {
        return Files.exists(dir.resolve(fileName))
                || Files.exists(dir.resolve(fileName + ".gz"));
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


