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
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import static org.junit.Assert.*;

/**
 * Headless integration test for CNZ forced spin tunnel entry.
 *
 * This test verifies that Sonic can enter and travel through a forced spin tunnel
 * in Casino Night Zone Act 1.
 *
 * Bug Description:
 * - Location: Casino Night Zone Act 1, position (7787, 921)
 * - Expected behavior: Sonic should fold into a roll when entering the forced spin
 *   opening and travel through the tunnel
 * - Actual behavior: Sonic bounces off the tunnel entrance instead of entering
 * - Pass criteria: After holding right for 100 frames, Sonic's X position should exceed 7915
 *
 * This test is expected to FAIL until the underlying bug is fixed.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestCNZForcedSpinTunnel {

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    // Zone constants
    private static final int ZONE_CNZ = 3;  // Casino Night Zone
    private static final int ACT_1 = 0;

    // Starting position (approaching the forced spin tunnel)
    private static final short START_X = 7787;
    private static final short START_Y = 921;

    // Target X position - pass if Sonic reaches beyond this (traveled through tunnel)
    private static final int TARGET_X = 7915;

    // Maximum frames to run before failing
    private static final int MAX_FRAMES = 100;

    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    @Before
    public void setUp() throws Exception {
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
        // Reset camera to ensure clean state (frozen may be left over from death in other tests)
        camera.setFrozen(false);

        // Load CNZ Act 1 (zone 3, act 0)
        // Note: This will set sprite position to level's default start position
        LevelManager.getInstance().loadZoneAndAct(ZONE_CNZ, ACT_1);

        // Now set the sprite to our desired test position (AFTER level load)
        sprite.setX(START_X);
        sprite.setY(START_Y);

        // Ensure GroundSensor uses the current LevelManager instance
        // (static field may be stale from earlier tests)
        GroundSensor.setLevelManager(LevelManager.getInstance());

        // Fix camera position - loadCurrentLevel sets bounds AFTER updatePosition,
        // so the camera may have been clamped incorrectly. Force update again now
        // that bounds are set.
        camera.updatePosition(true);

        // Create the headless test runner
        testRunner = new HeadlessTestRunner(sprite);
    }

    /**
     * Test that Sonic can enter and travel through the forced spin tunnel.
     *
     * The forced spin tunnel should cause Sonic to automatically roll when entering.
     * We walk Sonic into the tunnel entrance and verify he passes through to the other side.
     * If Sonic's X position exceeds TARGET_X (7915), the test passes.
     */
    @Test
    public void testForcedSpinTunnelEntry() throws Exception {
        System.out.println("=== CNZ Forced Spin Tunnel Test ===");
        System.out.println("Requested start position: (" + START_X + ", " + START_Y + ")");
        System.out.println("Actual initial position: (" + sprite.getX() + ", " + sprite.getY() + ")");
        System.out.println("Initial air state: " + sprite.getAir());
        System.out.println("Initial XSpeed: " + sprite.getXSpeed());
        System.out.println("Target X: > " + TARGET_X);
        System.out.println();

        int maxX = sprite.getX();  // Track maximum X position reached

        // Print header for frame-by-frame diagnostics
        System.out.println("Frame | X Pos | Y Pos | XSpeed | YSpeed | Air | Rolling | MaxX");
        System.out.println("------|-------|-------|--------|--------|-----|---------|-----");

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            // Walk right into the tunnel entrance
            testRunner.stepFrame(false, false, false, true, false);

            // Get current state
            short currentX = sprite.getX();
            short currentY = sprite.getY();
            short xSpeed = sprite.getXSpeed();
            short ySpeed = sprite.getYSpeed();
            boolean inAir = sprite.getAir();
            boolean rolling = sprite.getRolling();

            // Update maximum X
            if (currentX > maxX) {
                maxX = currentX;
            }

            // Print diagnostic info
            System.out.printf("%5d | %5d | %5d | %6d | %6d | %3s | %7s | %4d%n",
                    frame + 1, currentX, currentY, xSpeed, ySpeed,
                    inAir ? "YES" : "NO", rolling ? "YES" : "NO", maxX);

            // Early exit if we've reached the target
            if (currentX > TARGET_X) {
                System.out.println();
                System.out.println("SUCCESS: Passed through tunnel at frame " + (frame + 1));
                break;
            }
        }

        System.out.println();
        System.out.println("Final maximum X: " + maxX);
        System.out.println("Target was: > " + TARGET_X);

        // Assert that Sonic passed through the tunnel
        assertTrue(
                "Sonic should pass through the forced spin tunnel to X > " + TARGET_X +
                ", but maximum X reached was " + maxX +
                ". This indicates Sonic bounced off the tunnel entrance instead of entering.",
                maxX > TARGET_X
        );
    }
}
