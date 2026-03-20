package com.openggf.tests;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.sonic2.objects.ResultsScreenObjectInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidObjectParams;
import com.openggf.level.objects.SolidObjectProvider;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Grouped headless tests for Sonic 2 EHZ Act 1.
 *
 * Level data is loaded once via {@code @BeforeClass}; sprite, camera, and game
 * state are reset per test via {@link HeadlessTestFixture}.
 *
 * Merged from:
 * <ul>
 *   <li>TestHeadlessWallCollision</li>
 *   <li>TestHeadlessStaticObjectPushStability</li>
 *   <li>TestSidekickCpuController</li>
 *   <li>TestSignpostWalkOff</li>
 * </ul>
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestS2Ehz1Headless {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;
    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, ZONE_EHZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;
    private Sonic sprite;
    private Sonic sonic;

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        sprite = (Sonic) fixture.sprite();
        sonic = sprite;
    }

    // ========== From TestHeadlessWallCollision ==========

    @Test
    public void testGroundCollisionAndWalking() throws Exception {
        // Position at EHZ1 level start (center coordinates, matching loadCurrentLevel behavior).
        // The original test relied on loadZoneAndAct placing the sprite here with air=false.
        sprite.setCentreX((short) 96);
        sprite.setCentreY((short) 655);
        sprite.setAir(false);

        // Verify initial state
        assertFalse("Sprite should start on ground after level load", sprite.getAir());

        // Let Sonic settle onto the ground (5 frames with no input)
        for (int frame = 0; frame < 5; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        // Verify sprite is still on ground after settling
        assertFalse("Sprite should remain on ground after settling frames", sprite.getAir());

        // Record position before walking
        short initialX = sprite.getX();

        // Walk left for 5 frames
        for (int frame = 0; frame < 5; frame++) {
            fixture.stepFrame(false, false, true, false, false);
        }

        // Verify sprite stayed on ground while walking
        assertFalse("Sprite should remain on ground while walking left", sprite.getAir());

        // Verify X position decreased (moved left)
        short finalX = sprite.getX();
        assertTrue("Sprite should have moved left (X decreased). Initial=" + initialX + ", Final=" + finalX,
                finalX < initialX);

        // Verify gSpeed is negative (moving left)
        assertTrue("Ground speed should be negative when walking left", sprite.getGSpeed() < 0);
    }

    // ========== From TestHeadlessStaticObjectPushStability ==========

    private static final int TESTBED_X = 0x0180;
    private static final int TESTBED_FLOOR_Y = 0x0140;
    private static final int TESTBED_SPAWN_Y = TESTBED_FLOOR_Y - 0x60;
    private static final int LANDING_TIMEOUT_FRAMES = 120;
    private static final int CONTACT_TIMEOUT_FRAMES = 90;
    private static final int CONTACT_WARMUP_FRAMES = 10;
    private static final int STABILITY_FRAMES = 60;
    private static final int FLOOR_HALF_WIDTH = 0x90;
    private static final int FLOOR_HALF_HEIGHT = 0x10;
    private static final int WALL_HALF_WIDTH = 0x18;
    private static final int WALL_HALF_HEIGHT = 0x18;
    private static final int OBJECT_GAP = 4;
    private static final int PUSH_START_OFFSET = 0x30;

    @Test
    public void testNoJitterWhenPushingStaticObjectToRight() {
        assertNoPushJitter(true);
    }

    @Test
    public void testNoJitterWhenPushingStaticObjectToLeft() {
        assertNoPushJitter(false);
    }

    private void assertNoPushJitter(boolean pushRight) {
        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        TESTBED_X,
                        TESTBED_FLOOR_Y,
                        new SolidObjectParams(FLOOR_HALF_WIDTH, FLOOR_HALF_HEIGHT, FLOOR_HALF_HEIGHT),
                        true));

        sprite.setCentreX((short) (TESTBED_X + (pushRight ? -PUSH_START_OFFSET : PUSH_START_OFFSET)));
        sprite.setCentreY((short) TESTBED_SPAWN_Y);
        sprite.setAir(true);

        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        boolean landed = false;
        for (int frame = 0; frame < LANDING_TIMEOUT_FRAMES; frame++) {
            fixture.stepFrame(false, false, false, false, false);
            if (!sprite.getAir()) {
                landed = true;
                break;
            }
        }
        assertTrue("Sonic should land on the static floor testbed", landed);

        int objectX = sprite.getCentreX()
                + (pushRight ? WALL_HALF_WIDTH + OBJECT_GAP : -(WALL_HALF_WIDTH + OBJECT_GAP));
        int objectY = sprite.getCentreY();

        LevelManager.getInstance().getObjectManager()
                .addDynamicObject(new StaticSolidObject(
                        objectX,
                        objectY,
                        new SolidObjectParams(WALL_HALF_WIDTH, WALL_HALF_HEIGHT, WALL_HALF_HEIGHT),
                        false));

        boolean pressingLeft = !pushRight;
        boolean contactReached = false;
        for (int frame = 0; frame < CONTACT_TIMEOUT_FRAMES; frame++) {
            fixture.stepFrame(false, false, pressingLeft, pushRight, false);
            if (sprite.getPushing()) {
                contactReached = true;
                break;
            }
        }
        assertTrue("Sonic should reach side-pushing contact (" + directionName(pushRight) + ")", contactReached);

        for (int frame = 0; frame < CONTACT_WARMUP_FRAMES; frame++) {
            fixture.stepFrame(false, false, pressingLeft, pushRight, false);
        }

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int transitionCount = 0;
        Integer previousX = null;
        for (int frame = 0; frame < STABILITY_FRAMES; frame++) {
            fixture.stepFrame(false, false, pressingLeft, pushRight, false);
            int x = sprite.getX();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            if (previousX != null && previousX != x) {
                transitionCount++;
            }
            previousX = x;
            assertFalse("Sonic should stay grounded while pushing (" + directionName(pushRight) + ")", sprite.getAir());
        }

        assertEquals("Sonic X position should stay stable while pushing static object (" + directionName(pushRight)
                        + "), minX=" + minX + ", maxX=" + maxX + ", transitions=" + transitionCount,
                minX, maxX);
        assertEquals("Sonic X should not oscillate while pushing static object (" + directionName(pushRight) + ")",
                0, transitionCount);
    }

    private static String directionName(boolean pushRight) {
        return pushRight ? "toward right-side object" : "toward left-side object";
    }

    private static final class StaticSolidObject extends AbstractObjectInstance implements SolidObjectProvider {
        private final int x;
        private final int y;
        private final SolidObjectParams params;
        private final boolean topSolidOnly;

        private StaticSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly) {
            super(new ObjectSpawn(x, y, 0xFE, 0, 0, false, y), "TestStaticSolidObject");
            this.x = x;
            this.y = y;
            this.params = params;
            this.topSolidOnly = topSolidOnly;
        }

        @Override
        public int getX() {
            return x;
        }

        @Override
        public int getY() {
            return y;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return params;
        }

        @Override
        public boolean isTopSolidOnly() {
            return topSolidOnly;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No-op for headless collision tests.
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            // Static object.
        }
    }

    // ========== From TestSidekickCpuController ==========

    private Tails tails;
    private SidekickCpuController controller;

    private void createTailsForTest() {
        sprite.setX((short) 100);
        sprite.setY((short) 624);
        tails = new Tails("tails", (short) 60, (short) 624);
        tails.setCpuControlled(true);
        controller = new SidekickCpuController(tails, sprite);
        tails.setCpuController(controller);
        SpriteManager.getInstance().addSprite(tails);
    }

    // -- State Transition Tests --

    @Test
    public void testInitialState() {
        createTailsForTest();
        assertEquals("Controller should start in INIT state",
                SidekickCpuController.State.INIT, controller.getState());
    }

    @Test
    public void testInitTransitionsToNormal() {
        createTailsForTest();
        controller.update(0);
        assertEquals("INIT should immediately transition to NORMAL",
                SidekickCpuController.State.NORMAL, controller.getState());
    }

    @Test
    public void testResetClearsState() {
        createTailsForTest();
        // Run a few updates to change state
        controller.update(0);
        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());

        controller.reset();
        assertEquals("Reset should return to INIT",
                SidekickCpuController.State.INIT, controller.getState());
        assertFalse("Reset should clear all inputs", controller.getInputLeft());
        assertFalse(controller.getInputRight());
        assertFalse(controller.getInputJump());
    }

    // -- Input Generation Tests --

    @Test
    public void testNoInputWhenCloseToTarget() {
        createTailsForTest();
        // Settle both sprites on ground
        for (int i = 0; i < 20; i++) {
            fixture.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Place Tails very close to Sonic's delayed position
        short sonicDelayedX = sonic.getCentreX(17);
        short sonicDelayedY = sonic.getCentreY(17);
        tails.setX((short) (sonicDelayedX - tails.getWidth() / 2));
        tails.setY((short) (sonicDelayedY - tails.getHeight() / 2));

        controller.update(fixture.frameCount());

        assertFalse("No left input when close to target", controller.getInputLeft());
        assertFalse("No right input when close to target", controller.getInputRight());
    }

    @Test
    public void testInputRightWhenTargetIsRight() {
        createTailsForTest();
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            fixture.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Move Sonic far to the right of Tails
        // Walk Sonic right for many frames to create distance
        for (int i = 0; i < 30; i++) {
            fixture.stepFrame(false, false, false, true, false);
            // Don't move Tails so gap opens up
        }

        // Now update Tails AI - Sonic's delayed position should be to the right
        controller.update(fixture.frameCount());

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
        createTailsForTest();
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            fixture.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Place Tails far to the right of Sonic
        tails.setX((short) (sonic.getCentreX() + 100));
        tails.setY(sonic.getY());

        controller.update(fixture.frameCount());

        assertTrue("Should input left when target is to the left",
                controller.getInputLeft());
        assertFalse("Should not input right when target is left",
                controller.getInputRight());
    }

    @Test
    public void testJumpWhenSonicIsAboveAndFarAway() {
        createTailsForTest();
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            fixture.stepFrame(false, false, false, false, false);
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
        createTailsForTest();
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            fixture.stepFrame(false, false, false, false, false);
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
        createTailsForTest();
        // Settle sprites and populate history
        for (int i = 0; i < 20; i++) {
            fixture.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Place Tails far away but already airborne
        tails.setX((short) (sonic.getCentreX() + 200));
        tails.setY(sonic.getY());
        tails.setAir(true);

        controller.update(fixture.frameCount());

        assertFalse("Should not trigger jump when already airborne",
                controller.getInputJump());
    }

    // -- Despawn / Respawn Tests --

    @Test
    public void testDespawnWhenOffScreenTooLong() {
        createTailsForTest();
        // Get to NORMAL state first
        controller.update(0);
        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());

        // Move Tails far off-screen and mark airborne (fell off-screen)
        // Must be airborne to avoid triggering PANIC (stuck detection) before despawn
        forceDespawn();

        assertEquals("Should transition to SPAWNING after being off-screen for 300 frames",
                SidekickCpuController.State.SPAWNING, controller.getState());
    }

    @Test
    public void testRespawnWhenSonicIsGrounded() {
        createTailsForTest();
        controller.update(0);
        forceDespawn();
        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState());

        // Ensure Sonic is safely grounded
        sonic.setAir(false);
        sonic.setDead(false);

        controller.update(311);
        assertEquals("Respawn should still wait off the 64-frame cadence",
                SidekickCpuController.State.SPAWNING, controller.getState());
        controller.update(320);

        // After respawn, should be in APPROACHING state
        assertEquals("Should transition to APPROACHING after respawn",
                SidekickCpuController.State.APPROACHING, controller.getState());
        assertTrue("isApproaching() should return true in APPROACHING state",
                controller.isApproaching());
    }

    @Test
    public void testSpawningWaitsIfSonicIsDead() {
        createTailsForTest();
        controller.update(0);
        forceDespawn();
        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState());

        // Set Sonic as dead
        sonic.setDead(true);
        controller.update(320);

        assertEquals("Should stay in SPAWNING while Sonic is dead",
                SidekickCpuController.State.SPAWNING, controller.getState());
    }

    @Test
    public void testSpawningWaitsIfSonicIsAirborne() {
        createTailsForTest();
        controller.update(0);
        forceDespawn();
        assertEquals(SidekickCpuController.State.SPAWNING, controller.getState());

        // Set Sonic as airborne
        sonic.setAir(true);
        sonic.setDead(false);
        controller.update(320);

        assertEquals("Should stay in SPAWNING while Sonic is airborne",
                SidekickCpuController.State.SPAWNING, controller.getState());
    }

    // -- Flying State Tests --

    @Test
    public void testFlyingStateBypassesNormalPhysics() {
        createTailsForTest();
        assertFalse("isApproaching() should be false initially", controller.isApproaching());

        // Get to APPROACHING state via despawn/respawn
        controller.update(0);
        forceDespawn();

        sonic.setAir(false);
        sonic.setDead(false);
        controller.update(320);

        assertEquals(SidekickCpuController.State.APPROACHING, controller.getState());
        assertTrue("isApproaching() should return true", controller.isApproaching());
    }

    @Test
    public void testFlyingToNormalWhenReachedTarget() {
        createTailsForTest();
        // Get to APPROACHING state
        controller.update(0);
        forceDespawn();

        sonic.setAir(false);
        sonic.setDead(false);
        controller.update(320);
        assertEquals(SidekickCpuController.State.APPROACHING, controller.getState());

        // Place Tails right at Sonic's delayed position (17-frame delay)
        short targetX = sonic.getCentreX(17);
        short targetY = sonic.getCentreY(17);
        tails.setX((short) (targetX - tails.getWidth() / 2));
        tails.setY((short) (targetY - tails.getHeight() / 2));

        // Ensure Sonic is grounded for landing condition
        sonic.setAir(false);

        controller.update(312);

        assertEquals("Should transition from APPROACHING to NORMAL when at target and Sonic grounded",
                SidekickCpuController.State.NORMAL, controller.getState());
        assertFalse("isApproaching() should be false after landing",
                controller.isApproaching());
    }

    // -- Panic State Tests --

    @Test
    public void testPanicTriggersWhenMoveLockedAndStopped() {
        createTailsForTest();
        // Get to NORMAL state
        controller.update(0);
        assertEquals(SidekickCpuController.State.NORMAL, controller.getState());

        // ROM: PANIC trigger is move_lock && zero inertia, not an idle timer.
        tails.setGSpeed((short) 0);
        tails.setMoveLockTimer(10);
        controller.update(1);

        assertEquals("Should enter PANIC state when move lock is active and inertia is zero",
                SidekickCpuController.State.PANIC, controller.getState());
    }

    @Test
    public void testPanicFacesTowardSonic() {
        createTailsForTest();
        // Get to PANIC state
        controller.update(0);
        tails.setGSpeed((short) 0);
        tails.setMoveLockTimer(1);
        controller.update(1);
        assertEquals(SidekickCpuController.State.PANIC, controller.getState());

        // Place Sonic to the right of Tails
        tails.setX((short) (sonic.getX() - 50));

        tails.setMoveLockTimer(0);
        controller.update(2);

        assertEquals("Should face right toward Sonic during panic",
                com.openggf.physics.Direction.RIGHT, tails.getDirection());
    }

    @Test
    public void testPanicHoldsDownAndReleasesSpindash() {
        createTailsForTest();
        // Get to PANIC state
        controller.update(0);
        tails.setGSpeed((short) 0);
        tails.setMoveLockTimer(1);
        controller.update(1);
        assertEquals(SidekickCpuController.State.PANIC, controller.getState());

        tails.setMoveLockTimer(0);
        controller.update(2);
        assertTrue("Should hold down during panic", controller.getInputDown());

        controller.update(128);

        assertEquals("Should return to NORMAL on the 128-frame boundary",
                SidekickCpuController.State.NORMAL, controller.getState());
    }

    @Test
    public void testSonicDeathTransitionsToApproaching() {
        createTailsForTest();
        controller.update(0);

        sonic.setDead(true);
        controller.update(1);

        // enterApproachingState() now delegates to the respawn strategy,
        // which repositions the sidekick to its fly-in start position.
        assertEquals(SidekickCpuController.State.APPROACHING, controller.getState());
        // Tails strategy positions 192px above the leader
        assertEquals(sonic.getCentreY() - 192, tails.getCentreY());
    }

    // -- findSonic Tests --

    @Test
    public void testFindsSonicAsNonCpuControlledSprite() {
        createTailsForTest();
        // controller.update should automatically find Sonic
        controller.update(0);

        // If Sonic wasn't found, all inputs would be cleared and state unchanged
        // Since state transitions to NORMAL, Sonic was found
        assertEquals("Should find Sonic and transition to NORMAL",
                SidekickCpuController.State.NORMAL, controller.getState());
    }

    @Test
    public void testClearsInputsWhenNoLeader() {
        createTailsForTest();
        // Explicitly clear leader — simulates a disconnected sidekick.
        // With explicit leader assignment (no scan), null leader = idle.
        controller.setLeader(null);
        controller.update(0);

        // Should stay in INIT (leader is null, update() returns early)
        assertEquals("Should stay in INIT when no leader set",
                SidekickCpuController.State.INIT, controller.getState());
        assertFalse(controller.getInputLeft());
        assertFalse(controller.getInputRight());
        assertFalse(controller.getInputJump());
    }

    // -- Input Replay Tests --

    @Test
    public void testInputReplayFromSonicHistory() {
        createTailsForTest();
        // Record Sonic's input history by walking right for 20+ frames
        // so that the 17-frame delayed input has right pressed
        for (int i = 0; i < 25; i++) {
            fixture.stepFrame(false, false, false, true, false);
            stepTailsFrame();
        }

        // Place Tails close to target so AI override doesn't force direction
        short targetX = sonic.getCentreX(17);
        short targetY = sonic.getCentreY(17);
        tails.setX((short) (targetX - tails.getWidth() / 2));
        tails.setY((short) (targetY - tails.getHeight() / 2));
        tails.setAir(false);

        controller.update(fixture.frameCount());

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
        createTailsForTest();
        // Run idle frames to settle
        for (int i = 0; i < 20; i++) {
            fixture.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Make Sonic jump (record jump input)
        fixture.stepFrame(false, false, false, false, true);
        stepTailsFrame();

        // Continue for 16 more frames (not stepping Tails on the last one)
        for (int i = 0; i < 16; i++) {
            fixture.stepFrame(false, false, false, false, false);
            stepTailsFrame();
        }

        // Step Sonic one more frame but DON'T step Tails yet
        fixture.stepFrame(false, false, false, false, false);

        // Now the jump input is exactly 17 frames behind in Sonic's history.
        // Update Tails' controller fresh to see the replayed jump.
        tails.setAir(false);
        controller.update(fixture.frameCount());

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
        createTailsForTest();
        // Walk Sonic right for 20 frames
        for (int i = 0; i < 20; i++) {
            fixture.stepFrame(false, false, false, true, false);
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
        createTailsForTest();
        AbstractPlayableSprite sidekick = SpriteManager.getInstance().getSidekicks().getFirst();
        assertNotNull("getSidekicks() should return Tails", sidekick);
        assertTrue("Sidekick should be CPU controlled", sidekick.isCpuControlled());
        assertSame("Sidekick should be our Tails instance", tails, sidekick);
    }

    // -- Tails Helper Methods --

    /**
     * Forces Tails into SPAWNING state by simulating off-screen for 300+ frames.
     * Sets Tails as airborne to avoid PANIC (stuck detection at 120 frames).
     * Also sets Sonic as airborne to prevent SPAWNING from immediately transitioning
     * to APPROACHING (which happens when Sonic is safely grounded).
     */
    private void forceDespawn() {
        tails.setX((short) -500);
        tails.setY((short) -500);
        tails.setAir(true); // Airborne prevents stuck detection -> PANIC

        for (int i = 1; i <= 310; i++) {
            // Keep Sonic airborne so SPAWNING doesn't auto-transition to APPROACHING
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
        controller.update(fixture.frameCount());
        if (!controller.isApproaching()) {
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

    // ========== From TestSignpostWalkOff ==========

    // Start position: close to signpost but before it triggers.
    // EHZ1 signpost is near X=0x2A20. Start a bit earlier so we walk into it.
    private static final short START_X = 0x29A0;
    private static final short START_Y = 0x02A0;

    // Maximum frames to simulate
    private static final int MAX_FRAMES = 600;

    @Test
    public void testSignpostWalkOffAndResultsScreen() {
        // Reposition sprite for this test (after level load, set to desired test position)
        sprite.setCentreX(START_X);
        sprite.setCentreY(START_Y);
        fixture.camera().updatePosition(true);

        System.out.println("=== Signpost Walk-Off Regression Test ===");
        System.out.println("Start position: (" + START_X + ", " + START_Y + ")");
        System.out.println("Actual initial position: (" + sprite.getX() + ", " + sprite.getY() + ")");
        System.out.println();

        boolean signpostTriggered = false;
        boolean walkedOffScreen = false;
        boolean resultsSpawned = false;

        Camera camera = fixture.camera();

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            // Walk right
            fixture.stepFrame(false, false, false, true, false);

            // Check if signpost triggered (forceInputRight becomes true)
            if (!signpostTriggered && sprite.isForceInputRight()) {
                signpostTriggered = true;
                System.out.println("Frame " + (frame + 1) + ": Signpost triggered at X=" + sprite.getX());
            }

            // After signpost triggers, check if Sonic has walked off screen
            if (signpostTriggered) {
                int screenRightEdge = camera.getX() + camera.getWidth();
                if (sprite.getX() > screenRightEdge) {
                    walkedOffScreen = true;
                    System.out.println("Frame " + (frame + 1) + ": Sonic walked off screen at X=" + sprite.getX()
                            + " (screen right=" + screenRightEdge + ")");
                }

                // Check if results screen was spawned
                if (!resultsSpawned && LevelManager.getInstance().getObjectManager() != null) {
                    for (ObjectInstance obj : LevelManager.getInstance().getObjectManager().getActiveObjects()) {
                        if (obj instanceof ResultsScreenObjectInstance) {
                            resultsSpawned = true;
                            System.out.println("Frame " + (frame + 1) + ": Results screen spawned");
                            break;
                        }
                    }
                }

                // Both conditions met - we can stop early
                if (walkedOffScreen && resultsSpawned) {
                    break;
                }
            }
        }

        System.out.println();
        System.out.println("Final position: (" + sprite.getX() + ", " + sprite.getY() + ")");
        System.out.println("Signpost triggered: " + signpostTriggered);
        System.out.println("Walked off screen: " + walkedOffScreen);
        System.out.println("Results spawned: " + resultsSpawned);

        assertTrue("Signpost should have triggered (forceInputRight=true) but Sonic never reached it. "
                + "Final X=" + sprite.getX(), signpostTriggered);
        assertTrue("Sonic should walk off the right edge of the screen after signpost, "
                + "but final X=" + sprite.getX() + " is still on screen. "
                + "This indicates the forced-right input is being cancelled by controlLocked.",
                walkedOffScreen);
        assertTrue("A ResultsScreenObjectInstance should have been spawned after walk-off",
                resultsSpawned);
    }
}
