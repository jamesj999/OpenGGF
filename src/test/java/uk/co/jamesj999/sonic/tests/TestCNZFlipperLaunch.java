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
 * Headless integration test for CNZ horizontal flipper launch.
 *
 * This test verifies that the CNZ horizontal flipper (subtype 0x01) launches Sonic correctly.
 *
 * Bug Description:
 * - Location: Casino Night Zone Act 1, position (612, 857)
 * - Issue: When Sonic triggers a horizontal flipper by pushing against it, he moves upward
 *   for 5-7 frames (Y: 857→833, YSpeed reaching -2235), then suddenly his YSpeed resets to
 *   near zero (~+49) and he lands on something (Air=NO) despite being mid-launch on a ramp.
 *   The E(L) sensor appears to incorrectly detect ground on the curved surface.
 * - Expected: Flipper should launch Sonic with Y position reaching below 700
 *
 * This test previously failed due to an E(L) sensor ground detection bug on curved surfaces.
 * The underlying issue has since been fixed and this test now passes.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestCNZFlipperLaunch {

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    // Zone constants
    private static final int ZONE_CNZ = 3;  // Casino Night Zone
    private static final int ACT_1 = 0;

    // Starting position (on top of vertical flipper)
    private static final short START_X = 612;
    private static final short START_Y = 857;

    // Target Y position - pass if Sonic ever reaches below this
    private static final int TARGET_Y = 700;

    // Maximum frames to run before failing
    private static final int MAX_FRAMES = 30;

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
     * Test that the CNZ horizontal flipper launches Sonic to the expected height.
     *
     * The horizontal flipper (subtype 0x01) auto-launches when Sonic pushes against it.
     * We walk Sonic into the flipper to trigger the push, then track the minimum Y position.
     * If Sonic reaches below TARGET_Y (700), the test passes.
     */
    @Test
    public void testFlipperLaunchReachesTargetHeight() throws Exception {
        System.out.println("=== CNZ Horizontal Flipper Launch Test ===");
        System.out.println("Requested start position: (" + START_X + ", " + START_Y + ")");
        System.out.println("Actual initial position: (" + sprite.getX() + ", " + sprite.getY() + ")");
        System.out.println("Initial air state: " + sprite.getAir());
        System.out.println("Initial YSpeed: " + sprite.getYSpeed());
        System.out.println("Target Y: < " + TARGET_Y);
        System.out.println();

        int minY = sprite.getY();  // Track minimum Y position (highest point reached)

        // Print header for frame-by-frame diagnostics
        System.out.println("Frame | X Pos | Y Pos | YSpeed | Air | MinY");
        System.out.println("------|-------|-------|--------|-----|-----");

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            // Walk right into the flipper to trigger push-based launch
            testRunner.stepFrame(false, false, false, true, false);

            // Get current state
            short currentX = sprite.getX();
            short currentY = sprite.getY();
            short ySpeed = sprite.getYSpeed();
            boolean inAir = sprite.getAir();

            // Update minimum Y (remember: lower Y = higher on screen)
            if (currentY < minY) {
                minY = currentY;
            }

            // Print diagnostic info
            System.out.printf("%5d | %5d | %5d | %6d | %3s | %4d%n",
                    frame + 1, currentX, currentY, ySpeed, inAir ? "YES" : "NO", minY);

            // Early exit if we've reached the target
            if (minY < TARGET_Y) {
                System.out.println();
                System.out.println("SUCCESS: Reached target height at frame " + (frame + 1));
                break;
            }
        }

        System.out.println();
        System.out.println("Final minimum Y: " + minY);
        System.out.println("Target was: < " + TARGET_Y);

        // Assert that Sonic reached the target height
        assertTrue(
                "Flipper should launch Sonic to Y < " + TARGET_Y +
                ", but minimum Y reached was " + minY +
                ". This indicates the flipper launch is not working correctly.",
                minY < TARGET_Y
        );
    }
}
