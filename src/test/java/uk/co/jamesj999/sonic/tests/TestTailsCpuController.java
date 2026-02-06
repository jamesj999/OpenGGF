package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.sprites.playable.Tails;
import uk.co.jamesj999.sonic.sprites.playable.TailsCpuController;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import static org.junit.Assert.*;

/**
 * Tests for TailsCpuController AI state machine.
 *
 * Verifies state transitions, input generation, following behavior,
 * despawn/respawn, and panic recovery.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestTailsCpuController {

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sonic;
    private Tails tails;
    private TailsCpuController controller;
    private HeadlessTestRunner sonicRunner;

    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);

        // Create Sonic at EHZ1 spawn position
        sonic = new Sonic(mainCode, (short) 100, (short) 624);

        // Create Tails near Sonic
        tails = new Tails("tails", (short) 60, (short) 624);
        tails.setCpuControlled(true);
        controller = new TailsCpuController(tails);
        tails.setCpuController(controller);

        SpriteManager spriteManager = SpriteManager.getInstance();
        spriteManager.addSprite(sonic);
        spriteManager.addSprite(tails);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sonic);
        camera.setFrozen(false);

        LevelManager.getInstance().loadZoneAndAct(0, 0);
        GroundSensor.setLevelManager(LevelManager.getInstance());
        camera.updatePosition(true);

        sonicRunner = new HeadlessTestRunner(sonic);
    }

    // -- State Transition Tests --

    @Test
    public void testInitialState() {
        assertEquals("Controller should start in INIT state",
                TailsCpuController.State.INIT, controller.getState());
    }

    @Test
    public void testInitTransitionsToNormal() {
        controller.update(0);
        assertEquals("INIT should immediately transition to NORMAL",
                TailsCpuController.State.NORMAL, controller.getState());
    }

    @Test
    public void testResetClearsState() {
        // Run a few updates to change state
        controller.update(0);
        assertEquals(TailsCpuController.State.NORMAL, controller.getState());

        controller.reset();
        assertEquals("Reset should return to INIT",
                TailsCpuController.State.INIT, controller.getState());
        assertFalse("Reset should clear all inputs", controller.getInputLeft());
        assertFalse(controller.getInputRight());
        assertFalse(controller.getInputJump());
    }

    // -- Input Generation Tests --

    @Test
    public void testNoInputWhenCloseToTarget() {
        // Settle both sprites on ground
        for (int i = 0; i < 20; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Place Tails very close to Sonic's delayed position
        short sonicDelayedX = sonic.getCentreX(17);
        short sonicDelayedY = sonic.getCentreY(17);
        tails.setX((short) (sonicDelayedX - tails.getWidth() / 2));
        tails.setY((short) (sonicDelayedY - tails.getHeight() / 2));

        controller.update(sonicRunner.getFrameCounter());

        assertFalse("No left input when close to target", controller.getInputLeft());
        assertFalse("No right input when close to target", controller.getInputRight());
    }

    @Test
    public void testInputRightWhenTargetIsRight() {
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Move Sonic far to the right of Tails
        // Walk Sonic right for many frames to create distance
        for (int i = 0; i < 30; i++) {
            sonicRunner.stepFrame(false, false, false, true, false);
            // Don't move Tails so gap opens up
        }

        // Now update Tails AI - Sonic's delayed position should be to the right
        controller.update(sonicRunner.getFrameCounter());

        // Sonic moved right, so the delayed position (16 frames ago) should be
        // somewhat to the right of Tails
        short tailsX = tails.getCentreX();
        short targetX = sonic.getCentreX(16);

        if (targetX - tailsX > 16) {
            assertTrue("Should input right when target is to the right",
                    controller.getInputRight());
            assertFalse("Should not input left when target is right",
                    controller.getInputLeft());
        }
    }

    @Test
    public void testInputLeftWhenTargetIsLeft() {
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Place Tails far to the right of Sonic
        tails.setX((short) (sonic.getCentreX() + 100));
        tails.setY(sonic.getY());

        controller.update(sonicRunner.getFrameCounter());

        assertTrue("Should input left when target is to the left",
                controller.getInputLeft());
        assertFalse("Should not input right when target is left",
                controller.getInputRight());
    }

    @Test
    public void testJumpWhenSonicIsAboveAndFarAway() {
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Place Tails well below AND far from Sonic. ROM requires Sonic to be
        // >= 32px above Tails (vertical check always required). On 256-frame
        // boundaries the distance check is skipped, so any horizontal gap works.
        tails.setX((short) (sonic.getCentreX() + 200));
        tails.setY((short) (sonic.getY() + 100));
        tails.setAir(false);

        // Frame 256: (256 & 0xFF) == 0 skips distance gate, (256 & 0x3F) == 0 passes timing
        controller.update(256);

        assertTrue("Should trigger AI jump when Sonic is above by >= 32px on 256-frame boundary",
                controller.getInputJump());
    }

    @Test
    public void testNoJumpWhenSameHeightDespiteLargeGap() {
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Place Tails far from Sonic horizontally (>64px gap) but at SAME HEIGHT.
        // ROM: AI jump ALWAYS requires Sonic to be >= 32px above Tails.
        // Horizontal distance alone never triggers a jump.
        tails.setX((short) (sonic.getCentreX() + 200));
        tails.setY(sonic.getY());
        tails.setAir(false);

        // Even on a 256-frame boundary (skips distance gate), vertical check fails
        controller.update(256);

        assertFalse("Should NOT jump when at same height, even with large horizontal gap",
                controller.getInputJump());
    }

    @Test
    public void testNoJumpWhenAirborne() {
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Place Tails far away but already airborne
        tails.setX((short) (sonic.getCentreX() + 200));
        tails.setY(sonic.getY());
        tails.setAir(true);

        controller.update(sonicRunner.getFrameCounter());

        assertFalse("Should not trigger jump when already airborne",
                controller.getInputJump());
    }

    // -- Despawn / Respawn Tests --

    @Test
    public void testDespawnWhenOffScreenTooLong() {
        // Get to NORMAL state first
        controller.update(0);
        assertEquals(TailsCpuController.State.NORMAL, controller.getState());

        // Move Tails far off-screen and mark airborne (fell off-screen)
        // Must be airborne to avoid triggering PANIC (stuck detection) before despawn
        forceDespawn();

        assertEquals("Should transition to SPAWNING after being off-screen for 300 frames",
                TailsCpuController.State.SPAWNING, controller.getState());
    }

    @Test
    public void testRespawnWhenSonicIsGrounded() {
        controller.update(0);
        forceDespawn();
        assertEquals(TailsCpuController.State.SPAWNING, controller.getState());

        // Ensure Sonic is safely grounded
        sonic.setAir(false);
        sonic.setDead(false);

        controller.update(311);

        // After respawn, should be in FLYING state
        assertEquals("Should transition to FLYING after respawn",
                TailsCpuController.State.FLYING, controller.getState());
        assertTrue("isFlying() should return true in FLYING state",
                controller.isFlying());
    }

    @Test
    public void testSpawningWaitsIfSonicIsDead() {
        controller.update(0);
        forceDespawn();
        assertEquals(TailsCpuController.State.SPAWNING, controller.getState());

        // Set Sonic as dead
        sonic.setDead(true);
        controller.update(311);

        assertEquals("Should stay in SPAWNING while Sonic is dead",
                TailsCpuController.State.SPAWNING, controller.getState());
    }

    @Test
    public void testSpawningWaitsIfSonicIsAirborne() {
        controller.update(0);
        forceDespawn();
        assertEquals(TailsCpuController.State.SPAWNING, controller.getState());

        // Set Sonic as airborne
        sonic.setAir(true);
        sonic.setDead(false);
        controller.update(311);

        assertEquals("Should stay in SPAWNING while Sonic is airborne",
                TailsCpuController.State.SPAWNING, controller.getState());
    }

    // -- Flying State Tests --

    @Test
    public void testFlyingStateBypassesNormalPhysics() {
        assertFalse("isFlying() should be false initially", controller.isFlying());

        // Get to FLYING state via despawn/respawn
        controller.update(0);
        forceDespawn();

        sonic.setAir(false);
        sonic.setDead(false);
        controller.update(311);

        assertEquals(TailsCpuController.State.FLYING, controller.getState());
        assertTrue("isFlying() should return true", controller.isFlying());
    }

    @Test
    public void testFlyingToNormalWhenReachedTarget() {
        // Get to FLYING state
        controller.update(0);
        forceDespawn();

        sonic.setAir(false);
        sonic.setDead(false);
        controller.update(311);
        assertEquals(TailsCpuController.State.FLYING, controller.getState());

        // Place Tails right at Sonic's delayed position (17-frame delay)
        short targetX = sonic.getCentreX(17);
        short targetY = sonic.getCentreY(17);
        tails.setX((short) (targetX - tails.getWidth() / 2));
        tails.setY((short) (targetY - tails.getHeight() / 2));

        // Ensure Sonic is grounded for landing condition
        sonic.setAir(false);

        controller.update(312);

        assertEquals("Should transition from FLYING to NORMAL when at target and Sonic grounded",
                TailsCpuController.State.NORMAL, controller.getState());
        assertFalse("isFlying() should be false after landing",
                controller.isFlying());
    }

    // -- Panic State Tests --

    @Test
    public void testPanicTriggersWhenStuck() {
        // Get to NORMAL state
        controller.update(0);
        assertEquals(TailsCpuController.State.NORMAL, controller.getState());

        // Simulate stuck: grounded, zero gSpeed, not rolling, for 121+ frames
        tails.setGSpeed((short) 0);
        tails.setAir(false);

        for (int i = 1; i <= 125; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            controller.update(i);
            // Keep Tails stuck
            tails.setGSpeed((short) 0);
            tails.setAir(false);
        }

        assertEquals("Should enter PANIC state after being stuck for >120 frames",
                TailsCpuController.State.PANIC, controller.getState());
    }

    @Test
    public void testPanicFacesTowardSonic() {
        // Get to PANIC state
        controller.update(0);
        tails.setGSpeed((short) 0);
        tails.setAir(false);

        for (int i = 1; i <= 125; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            controller.update(i);
            tails.setGSpeed((short) 0);
            tails.setAir(false);
        }
        assertEquals(TailsCpuController.State.PANIC, controller.getState());

        // Place Sonic to the right of Tails
        tails.setX((short) (sonic.getX() - 50));

        controller.update(126);

        assertTrue("Should face right toward Sonic during panic",
                controller.getInputRight());
    }

    @Test
    public void testPanicHoldsDownAndReleasesSpindash() {
        // Get to PANIC state
        controller.update(0);
        tails.setGSpeed((short) 0);
        tails.setAir(false);

        for (int i = 1; i <= 125; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            controller.update(i);
            tails.setGSpeed((short) 0);
            tails.setAir(false);
        }
        assertEquals(TailsCpuController.State.PANIC, controller.getState());

        // ROM: Panic holds down throughout and releases spindash every 128 frames
        controller.update(126);
        assertTrue("Should hold down during panic", controller.getInputDown());

        // Advance through 128 frames of panic (panicCounter wraps at 128)
        for (int i = 127; i < 126 + 127; i++) {
            controller.update(i);
        }
        // At panicCounter == 128, spindash releases and returns to NORMAL
        controller.update(126 + 127);

        assertEquals("Should return to NORMAL after 128-frame spindash cycle",
                TailsCpuController.State.NORMAL, controller.getState());
    }

    // -- findSonic Tests --

    @Test
    public void testFindsSonicAsNonCpuControlledSprite() {
        // controller.update should automatically find Sonic
        controller.update(0);

        // If Sonic wasn't found, all inputs would be cleared and state unchanged
        // Since state transitions to NORMAL, Sonic was found
        assertEquals("Should find Sonic and transition to NORMAL",
                TailsCpuController.State.NORMAL, controller.getState());
    }

    @Test
    public void testClearsInputsWhenNoSonicFound() {
        // Remove Sonic from SpriteManager
        SpriteManager.getInstance().clearAllSprites();
        // Re-add only Tails
        SpriteManager.getInstance().addSprite(tails);

        // Reset controller to clear cached Sonic reference
        controller.reset();
        controller.update(0);

        // Should stay in INIT (can't transition without Sonic)
        assertEquals("Should stay in INIT when no Sonic found",
                TailsCpuController.State.INIT, controller.getState());
        assertFalse(controller.getInputLeft());
        assertFalse(controller.getInputRight());
        assertFalse(controller.getInputJump());
    }

    // -- Input Replay Tests --

    @Test
    public void testInputReplayFromSonicHistory() {
        // Record Sonic's input history by walking right for 20+ frames
        // so that the 17-frame delayed input has right pressed
        for (int i = 0; i < 25; i++) {
            sonicRunner.stepFrame(false, false, false, true, false);
            stepTailsFrame();
        }

        // Place Tails close to target so AI override doesn't force direction
        short targetX = sonic.getCentreX(17);
        short targetY = sonic.getCentreY(17);
        tails.setX((short) (targetX - tails.getWidth() / 2));
        tails.setY((short) (targetY - tails.getHeight() / 2));
        tails.setAir(false);

        controller.update(sonicRunner.getFrameCounter());

        // Sonic's recorded input from 17 frames ago should have RIGHT pressed
        // (since Sonic was walking right for all 25 frames)
        short replayedInput = sonic.getInputHistory(17);
        boolean replayedRight = (replayedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        assertTrue("Sonic's recorded input from 17 frames ago should have RIGHT pressed",
                replayedRight);
        assertTrue("Tails should replay Sonic's right input",
                controller.getInputRight());
    }

    @Test
    public void testInputReplayJump() {
        // Run idle frames to settle
        for (int i = 0; i < 20; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Make Sonic jump (record jump input)
        sonicRunner.stepFrame(false, false, false, false, true);
        stepTailsFrame();

        // Continue for 16 more frames (not stepping Tails on the last one)
        for (int i = 0; i < 16; i++) {
            sonicRunner.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Step Sonic one more frame but DON'T step Tails yet
        sonicRunner.stepFrame(false, false, false, false, false);

        // Now the jump input is exactly 17 frames behind in Sonic's history.
        // Update Tails' controller fresh to see the replayed jump.
        tails.setAir(false);
        controller.update(sonicRunner.getFrameCounter());

        short replayedInput = sonic.getInputHistory(17);
        boolean replayedJump = (replayedInput & AbstractPlayableSprite.INPUT_JUMP) != 0;
        assertTrue("Sonic's recorded input from 17 frames ago should have JUMP pressed",
                replayedJump);
        assertTrue("Tails should replay Sonic's jump input",
                controller.getInputJump());
    }

    // -- Position History / Delay Tests --

    @Test
    public void testPositionHistoryRecording() {
        // Walk Sonic right for 20 frames
        for (int i = 0; i < 20; i++) {
            sonicRunner.stepFrame(false, false, false, true, false);
        }

        short currentX = sonic.getCentreX();
        short delayedX = sonic.getCentreX(16);

        // After walking right for 20 frames, current position should be
        // to the right of the 16-frame-delayed position
        assertTrue("Current position should be further right than delayed position. " +
                        "Current=" + currentX + ", Delayed(16)=" + delayedX,
                currentX > delayedX);
    }

    // -- SpriteManager Integration Test --

    @Test
    public void testSpriteManagerGetSidekick() {
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekick();
        assertNotNull("getSidekick() should return Tails", sidekick);
        assertTrue("Sidekick should be CPU controlled", sidekick.isCpuControlled());
        assertSame("Sidekick should be our Tails instance", tails, sidekick);
    }

    // -- Helper Methods --

    /**
     * Forces Tails into SPAWNING state by simulating off-screen for 300+ frames.
     * Sets Tails as airborne to avoid PANIC (stuck detection at 120 frames).
     * Also sets Sonic as airborne to prevent SPAWNING from immediately transitioning
     * to FLYING (which happens when Sonic is safely grounded).
     */
    private void forceDespawn() {
        tails.setX((short) -500);
        tails.setY((short) -500);
        tails.setAir(true); // Airborne prevents stuck detection → PANIC

        for (int i = 1; i <= 310; i++) {
            // Keep Sonic airborne so SPAWNING doesn't auto-transition to FLYING
            sonic.setAir(true);
            controller.update(i);
            // Keep Tails off-screen and airborne each frame
            tails.setX((short) -500);
            tails.setY((short) -500);
            tails.setAir(true);
        }
    }

    /**
     * Steps Tails through one frame of AI + physics (simplified).
     * Mimics what SpriteManager does for CPU-controlled sprites.
     */
    private void stepTailsFrame() {
        controller.update(sonicRunner.getFrameCounter());
        if (!controller.isFlying()) {
            tails.getMovementManager().handleMovement(
                    controller.getInputUp(),
                    controller.getInputDown(),
                    controller.getInputLeft(),
                    controller.getInputRight(),
                    controller.getInputJump(),
                    false, false, false);
        }
        tails.tickStatus();
        tails.endOfTick();
    }
}
