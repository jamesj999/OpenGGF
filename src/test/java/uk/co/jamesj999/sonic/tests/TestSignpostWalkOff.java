package uk.co.jamesj999.sonic.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.game.sonic2.objects.ResultsScreenObjectInstance;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;

import java.io.File;
import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Regression test for the signpost walk-off bug.
 *
 * After clearing a level (passing the signpost), Sonic should be forced to walk
 * right off-screen before the results tally appears. This broke when commit 6a358151
 * added an objControlLocked check to PlayableSpriteMovement that zeros left/right input,
 * cancelling the forced-right input that SpriteManager had already applied.
 *
 * The fix separates the ROM's two distinct lock mechanisms:
 * - Control_Locked (global): prevents joypad read but allows forced input (signpost walk-off)
 * - obj_control bit 0 (per-object): skips movement entirely (flippers, spin tubes)
 */
public class TestSignpostWalkOff {

    // EHZ Act 1
    private static final int ZONE_EHZ = 0;
    private static final int ACT_1 = 0;

    // Start position: close to signpost but before it triggers.
    // EHZ1 signpost is near X=0x2A20. Start a bit earlier so we walk into it.
    private static final short START_X = 0x29A0;
    private static final short START_Y = 0x02A0;

    // Maximum frames to simulate
    private static final int MAX_FRAMES = 600;

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
        sprite = new Sonic(mainCode, (short) 0, (short) 0);

        // Add sprite to SpriteManager
        SpriteManager spriteManager = SpriteManager.getInstance();
        spriteManager.addSprite(sprite);

        // Set camera focus - must be done BEFORE level load
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        // Load EHZ Act 1
        LevelManager.getInstance().loadZoneAndAct(ZONE_EHZ, ACT_1);

        // Now set the sprite to our desired test position (AFTER level load)
        sprite.setX(START_X);
        sprite.setY(START_Y);

        // Ensure GroundSensor uses the current LevelManager instance
        GroundSensor.setLevelManager(LevelManager.getInstance());

        // Fix camera position after level load
        camera.updatePosition(true);

        // Create the headless test runner
        testRunner = new HeadlessTestRunner(sprite);
    }

    @After
    public void tearDown() throws Exception {
        GraphicsManager.resetInstance();

        LevelManager levelManager = LevelManager.getInstance();
        Field levelField = LevelManager.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(levelManager, null);

        SpriteManager.getInstance().clearAllSprites();

        if (rom != null) {
            rom.close();
        }
    }

    /**
     * Test that Sonic walks off-screen after passing the signpost.
     *
     * Steps:
     * 1. Walk right until signpost triggers (forceInputRight becomes true)
     * 2. Continue stepping frames - signpost's walk-off forces right
     * 3. Assert Sonic walked past the right edge of the camera
     * 4. Assert a ResultsScreenObjectInstance was spawned
     */
    @Test
    public void testSignpostWalkOffAndResultsScreen() {
        System.out.println("=== Signpost Walk-Off Regression Test ===");
        System.out.println("Start position: (" + START_X + ", " + START_Y + ")");
        System.out.println("Actual initial position: (" + sprite.getX() + ", " + sprite.getY() + ")");
        System.out.println();

        boolean signpostTriggered = false;
        boolean walkedOffScreen = false;
        boolean resultsSpawned = false;

        Camera camera = Camera.getInstance();

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            // Walk right
            testRunner.stepFrame(false, false, false, true, false);

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
