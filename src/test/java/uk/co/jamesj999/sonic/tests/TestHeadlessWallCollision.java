package uk.co.jamesj999.sonic.tests;

import org.junit.After;
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

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Headless integration test for ground collision and walking in EHZ1.
 *
 * This test verifies:
 * 1. Sonic stays on the ground at the spawn position
 * 2. Walking left moves Sonic in the correct direction
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestHeadlessWallCollision {

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    @Before
    public void setUp() throws Exception {
        // Reset singletons that might have stale state from other tests
        GraphicsManager.resetInstance();
        Camera.resetInstance();

        // Initialize headless graphics (no GL context needed)
        GraphicsManager.getInstance().initHeadless();

        // Create Sonic sprite at initial position
        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        sprite = new Sonic(mainCode, (short) 100, (short) 624);

        // Add sprite to SpriteManager
        SpriteManager spriteManager = SpriteManager.getInstance();
        spriteManager.addSprite(sprite);

        // Set camera focus - must be done BEFORE level load
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        // Reset camera to ensure clean state (frozen may be left over from death in other tests)
        camera.setFrozen(false);

        // Load EHZ1 (zone 0, act 0)
        LevelManager.getInstance().loadZoneAndAct(0, 0);

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
    }

    @Test
    public void testGroundCollisionAndWalking() throws Exception {
        // Verify initial state
        assertFalse("Sprite should start on ground after level load", sprite.getAir());

        // Let Sonic settle onto the ground (5 frames with no input)
        for (int frame = 0; frame < 5; frame++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        // Verify sprite is still on ground after settling
        assertFalse("Sprite should remain on ground after settling frames", sprite.getAir());

        // Record position before walking
        short initialX = sprite.getX();

        // Walk left for 5 frames
        for (int frame = 0; frame < 5; frame++) {
            testRunner.stepFrame(false, false, true, false, false);
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
}
