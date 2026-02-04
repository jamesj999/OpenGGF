package uk.co.jamesj999.sonic.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.GroundMode;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Headless integration test for CNZ ceiling state exit.
 *
 * This test verifies that Sonic properly exits ceiling state when walking off
 * the end of a ceiling in Casino Night Zone Act 1.
 *
 * Bug Description:
 * - Location: Casino Night Zone Act 1, starting at position (4198, 1716)
 * - Issue: When Sonic rolls up a wall and enters ceiling state, he should exit
 *   ceiling state when walking off the end of the ceiling. Currently, he stays
 *   in ceiling mode even when standing still on the ground below.
 * - Sequence: Charge vertical launcher spring by holding jump for 130 frames,
 *   release to launch upward at high speed, pass ForcedSpin object around Y ~1220,
 *   roll/run up onto ceiling, triggering ceiling state.
 *
 * Pass/Fail Criteria:
 * - FAIL if Sonic is in ceiling state (GroundMode.CEILING) while Y > 858
 *   (Note: larger Y = lower on screen, so Y > 858 means below the ceiling area)
 * - FAIL if Sonic remains in ceiling state for more than 3 seconds (180 frames)
 * - PASS if Sonic enters ceiling state during the sequence and properly exits it
 *
 * This test is expected to FAIL until the underlying bug is fixed.
 */
public class TestCNZCeilingStateExit {

    // Zone constants
    private static final int ZONE_CNZ = 3;  // Casino Night Zone (level select ID)
    private static final int ACT_1 = 0;

    // Starting position - first spring in the chain
    // The bug only triggers when chaining two launcher springs:
    // 1. First spring at (3630, 1827) - diagonal spring (subtype 0x81)
    // 2. Player goes around a loop
    // 3. Lands on second spring at (4208, 1776) - vertical spring
    // 4. Launch from second spring triggers the bug
    private static final short START_X = 3621;  // Near first diagonal spring
    private static final short START_Y = 1820;

    // Test parameters
    private static final int CHARGE_FRAMES = 130;           // Frames to hold jump to charge spring
    private static final int CEILING_Y_THRESHOLD = 858;     // Impossible Y - if in ceiling at Y < this, bug detected
    private static final int MAX_CEILING_FRAMES = 180;      // 3 seconds at 60fps - bug threshold
    private static final int MAX_TEST_FRAMES = 600;         // Total frames to run after launch

    private Rom rom;
    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    @Before
    public void setUp() throws Exception {
        // Reset singletons that might have stale state from other tests
        GraphicsManager.resetInstance();
        Camera.resetInstance();

        // Load ROM
        File romFile = RomTestUtils.ensureRomAvailable();
        rom = new Rom();
        rom.open(romFile.getAbsolutePath());
        GameModuleRegistry.detectAndSetModule(rom);

        // Initialize headless graphics (no GL context needed)
        GraphicsManager.getInstance().initHeadless();

        // Create Sonic sprite at a temporary position (will be moved after level load)
        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        sprite = new Sonic(mainCode, (short) 0, (short) 0);  // Temporary position

        // Add sprite to SpriteManager
        SpriteManager spriteManager = SpriteManager.getInstance();
        spriteManager.addSprite(sprite);

        // Set camera focus - must be done BEFORE level load
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        // Load CNZ Act 1 (zone 3, act 0)
        LevelManager.getInstance().loadZoneAndAct(ZONE_CNZ, ACT_1);

        // Set the sprite to our desired test position (AFTER level load)
        sprite.setX(START_X);
        sprite.setY(START_Y);

        // Enable pinball mode (required for spring interaction in CNZ)
        sprite.setPinballMode(true);

        // Ensure GroundSensor uses the current LevelManager instance
        GroundSensor.setLevelManager(LevelManager.getInstance());

        // Fix camera position
        camera.updatePosition(true);

        // Create the headless test runner
        testRunner = new HeadlessTestRunner(sprite);
    }

    @After
    public void tearDown() throws Exception {
        // Reset GraphicsManager
        GraphicsManager.resetInstance();

        // Reset LevelManager - set level field to null
        LevelManager levelManager = LevelManager.getInstance();
        Field levelField = LevelManager.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(levelManager, null);

        // Clear SpriteManager
        SpriteManager.getInstance().clearAllSprites();

        // Close ROM
        if (rom != null) {
            rom.close();
        }
    }

    /**
     * Test that Sonic properly exits ceiling state when walking off a ceiling.
     *
     * The test sequence (two-spring chain required to trigger the bug):
     * 1. Charge and launch from first diagonal spring at (3621, 1860)
     * 2. Player goes around a loop
     * 3. Player lands on second vertical spring at (4208, 1776)
     * 4. Charge and launch from second spring
     * 5. Sonic enters ceiling state
     * 6. Sonic should exit ceiling state when walking off the ceiling end
     *
     * The bug: Sonic stays in ceiling mode even when standing on the ground below.
     * This only triggers when chaining the two springs in sequence.
     */
    @Test
    public void testCeilingStateExitsWhenWalkingOffCeiling() throws Exception {
        System.out.println("=== CNZ Ceiling State Exit Test (Two-Spring Chain) ===");
        System.out.println("Start position: (" + START_X + ", " + START_Y + ")");
        System.out.println("Pinball mode: " + sprite.getPinballMode());
        System.out.println("Initial ground mode: " + sprite.getGroundMode());
        System.out.println();

        // Run a few frames first to trigger object spawning
        System.out.println("Running initial frames to spawn objects...");
        Camera camera = Camera.getInstance();

        for (int i = 0; i < 5; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        // Debug: Print active launcher springs
        var objectManager = LevelManager.getInstance().getObjectManager();
        if (objectManager != null) {
            System.out.println("Total active objects: " + objectManager.getActiveObjects().size());
            System.out.println("Active launcher springs (obj 0x85):");
            for (ObjectInstance obj : objectManager.getActiveObjects()) {
                if (obj.getSpawn().objectId() == 0x85) {
                    System.out.println("  - at (" + obj.getSpawn().x() + ", " + obj.getSpawn().y() +
                            ") subtype=0x" + Integer.toHexString(obj.getSpawn().subtype()) +
                            " class=" + obj.getClass().getSimpleName());
                }
            }
        }

        // Reposition sprite above the first spring
        sprite.setX(START_X);
        sprite.setY(START_Y);
        sprite.setAir(true);
        sprite.setYSpeed((short) 0);
        sprite.setPinballMode(true);
        System.out.println("Repositioned player to: (" + sprite.getX() + ", " + sprite.getY() + ") (in air)");
        System.out.println();

        // Tracking variables
        boolean enteredCeilingState = false;
        boolean ceilingAtImpossibleY = false;
        int ceilingFrameCount = 0;
        int totalFrames = 0;
        int minYReached = Integer.MAX_VALUE;
        int springLaunchCount = 0;

        // ============================================
        // PHASE 1: Charge and launch from FIRST spring
        // ============================================
        System.out.println("=== PHASE 1: First Spring (diagonal at ~3621, 1860) ===");
        System.out.println("Charging for " + CHARGE_FRAMES + " frames...");

        for (int frame = 0; frame < CHARGE_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, true);  // Hold jump
            totalFrames++;
        }
        System.out.println("Releasing first spring at frame " + totalFrames);
        System.out.printf("Position: (%d, %d), ObjCtrl=%s%n",
                sprite.getX(), sprite.getY(), sprite.isObjectControlled() ? "Y" : "N");

        // ============================================
        // PHASE 2: Travel through loop, land on second spring
        // ============================================
        System.out.println();
        System.out.println("=== PHASE 2: Traveling to second spring ===");
        System.out.println("Frame | X Pos | Y Pos | YSpeed | GroundMode | Air | ObjCtrl");
        System.out.println("------|-------|-------|--------|------------|-----|-------");

        boolean reachedSecondSpring = false;
        int secondSpringX = 4208;
        int maxTravelFrames = 300;  // Max frames to reach second spring

        for (int frame = 0; frame < maxTravelFrames && !reachedSecondSpring; frame++) {
            testRunner.stepFrame(false, false, false, false, false);  // No input
            totalFrames++;

            // Update camera to follow player - this is needed for object spawning
            camera.updatePosition(false);

            short ySpeed = sprite.getYSpeed();
            if (ySpeed < -1000) {
                springLaunchCount++;
            }

            // Check if we've landed on the second spring (object controlled near X=4208)
            if (sprite.isObjectControlled() && Math.abs(sprite.getX() - secondSpringX) < 50) {
                reachedSecondSpring = true;
                System.out.println(">>> Reached second spring at frame " + totalFrames);
            }

            // Print periodically
            if (frame < 20 || (frame + 1) % 20 == 0 || sprite.isObjectControlled()) {
                System.out.printf("%5d | %5d | %5d | %6d | %10s | %3s | %s%n",
                        totalFrames, sprite.getX(), sprite.getY(), ySpeed,
                        sprite.getGroundMode(), sprite.getAir() ? "YES" : "NO",
                        sprite.isObjectControlled() ? "YES" : "NO");
            }
        }

        if (!reachedSecondSpring) {
            System.out.println("Did not land directly on second spring - repositioning player");
            System.out.println("Current position: (" + sprite.getX() + ", " + sprite.getY() + ")");

            // The second spring is at (4208, 1776), standing surface at Y = 1776 - 46 = 1730
            // Reposition player above the spring to fall onto it
            sprite.setX((short) 4208);
            sprite.setY((short) 1710);  // Above the spring's standing surface
            sprite.setAir(true);
            sprite.setYSpeed((short) 0);
            // Keep pinball mode and rolling state from the first launch
            sprite.setPinballMode(true);
            System.out.println("Repositioned to: (" + sprite.getX() + ", " + sprite.getY() + ") above second spring");

            // Update camera to ensure second spring is spawned
            camera.updatePosition(true);

            // Check if second spring is now active
            System.out.println("Active launcher springs after camera update:");
            for (ObjectInstance obj : objectManager.getActiveObjects()) {
                if (obj.getSpawn().objectId() == 0x85) {
                    System.out.println("  - at (" + obj.getSpawn().x() + ", " + obj.getSpawn().y() +
                            ") subtype=0x" + Integer.toHexString(obj.getSpawn().subtype()));
                }
            }

            // Run a few frames to land on the spring
            for (int i = 0; i < 30 && !sprite.isObjectControlled(); i++) {
                testRunner.stepFrame(false, false, false, false, false);
                camera.updatePosition(false);
                totalFrames++;
            }
            System.out.println("After landing: (" + sprite.getX() + ", " + sprite.getY() + "), ObjCtrl=" +
                    (sprite.isObjectControlled() ? "Y" : "N"));
        }

        // ============================================
        // PHASE 3: Charge and launch from SECOND spring
        // ============================================
        System.out.println();
        System.out.println("=== PHASE 3: Second Spring (vertical at ~4208, 1776) ===");
        System.out.println("Charging for " + CHARGE_FRAMES + " frames...");

        for (int frame = 0; frame < CHARGE_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, true);  // Hold jump
            totalFrames++;
        }
        System.out.println("Releasing second spring at frame " + totalFrames);
        System.out.printf("Position: (%d, %d), ObjCtrl=%s%n",
                sprite.getX(), sprite.getY(), sprite.isObjectControlled() ? "Y" : "N");

        // ============================================
        // PHASE 4: Monitor ceiling state after second launch
        // ============================================
        System.out.println();
        System.out.println("=== PHASE 4: Monitoring ceiling state ===");
        System.out.println("Frame | X Pos | Y Pos | YSpeed | GroundMode | Air | Angle | CeilFrames");
        System.out.println("------|-------|-------|--------|------------|-----|-------|----------");

        for (int frame = 0; frame < MAX_TEST_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
            totalFrames++;

            short currentY = sprite.getY();
            short currentYSpeed = sprite.getYSpeed();
            GroundMode groundMode = sprite.getGroundMode();
            boolean inCeilingState = (groundMode == GroundMode.CEILING);
            short gSpeed = sprite.getGSpeed();

            if (currentYSpeed < -1000) {
                springLaunchCount++;
            }

            if (currentY < minYReached) {
                minYReached = currentY;
            }

            if (inCeilingState) {
                if (!enteredCeilingState) {
                    enteredCeilingState = true;
                    System.out.println(">>> ENTERED CEILING STATE at frame " + totalFrames +
                            ", Y=" + currentY + ", gSpeed=" + gSpeed);
                }
                ceilingFrameCount++;

                if (currentY < CEILING_Y_THRESHOLD) {
                    ceilingAtImpossibleY = true;
                    System.out.println(">>> BUG DETECTED: In ceiling state at Y=" + currentY +
                            " (above threshold " + CEILING_Y_THRESHOLD + " - impossible position!)");
                }
            } else if (enteredCeilingState && ceilingFrameCount > 0) {
                System.out.println(">>> EXITED CEILING STATE at frame " + totalFrames +
                        ", Y=" + currentY + ", gSpeed=" + gSpeed + " after " + ceilingFrameCount + " frames");
            }

            if (frame < 50 || (frame + 1) % 20 == 0 || inCeilingState) {
                System.out.printf("%5d | %5d | %5d | %6d | %10s | %3s | 0x%02X | %5d%n",
                        totalFrames, sprite.getX(), currentY, currentYSpeed,
                        groundMode, sprite.getAir() ? "YES" : "NO",
                        sprite.getAngle() & 0xFF, ceilingFrameCount);
            }

            if (ceilingFrameCount > MAX_CEILING_FRAMES) {
                System.out.println();
                System.out.println(">>> TEST STOPPING: Ceiling state exceeded " + MAX_CEILING_FRAMES + " frames");
                break;
            }

            if (enteredCeilingState && !inCeilingState && ceilingFrameCount > 0) {
                System.out.println();
                System.out.println(">>> Ceiling state properly exited");
                break;
            }
        }

        System.out.println();
        System.out.println("=== Test Results ===");
        System.out.println("Spring launches detected: " + springLaunchCount);
        System.out.println("Minimum Y reached: " + minYReached);
        System.out.println("Entered ceiling state: " + enteredCeilingState);
        System.out.println("Total frames in ceiling state: " + ceilingFrameCount);
        System.out.println("Ceiling at impossible Y (< " + CEILING_Y_THRESHOLD + "): " + ceilingAtImpossibleY);
        System.out.println("Final position: (" + sprite.getX() + ", " + sprite.getY() + ")");
        System.out.println("Final ground mode: " + sprite.getGroundMode());
        System.out.println("Final gSpeed: " + sprite.getGSpeed());
        System.out.println();

        // Assertions
        assertTrue(
                "Should enter ceiling state during test sequence. " +
                        "Spring launches: " + springLaunchCount + ", min Y reached: " + minYReached + ". " +
                        "If this fails, the two-spring chain may not be working correctly.",
                enteredCeilingState
        );

        assertFalse(
                "Should NOT be in ceiling state at Y < " + CEILING_Y_THRESHOLD + ". " +
                        "This indicates Sonic is stuck in ceiling state and moving to impossible positions.",
                ceilingAtImpossibleY
        );

        assertTrue(
                "Ceiling state should not exceed " + MAX_CEILING_FRAMES + " frames (3 seconds). " +
                        "Sonic should either slow down and drop off, or roll off the end of the ceiling. " +
                        "This is the ceiling state exit bug.",
                ceilingFrameCount <= MAX_CEILING_FRAMES
        );
    }
}
