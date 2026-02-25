package com.openggf.tests;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.*;

/**
 * Headless integration test for HTZ Act 2 spring loop bug.
 *
 * <p>This test reproduces a scenario where Sonic enters a spring loop but fails
 * to trigger a spring he's facing opposite to, causing him to stop moving.
 *
 * <p>Test scenario:
 * <ol>
 *   <li>Spawn Sonic at position X=8473, Y=1465 in HTZ Act 2</li>
 *   <li>Hold Right for 1 frame to initiate movement</li>
 *   <li>Let simulation run for 300 frames with no input</li>
 *   <li>Pass condition: Sonic's GSpeed is non-zero (still bouncing between springs)</li>
 *   <li>Fail condition: Sonic's GSpeed is 0 (stopped, spring not triggered)</li>
 * </ol>
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestHtzSpringLoop {

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();
    private static String mainCharCode;

    // Test position for HTZ Act 2 spring loop area (from debug overlay - decimal values)
    private static final short START_X = (short) 8475;  // X position from debug overlay
    private static final short START_Y = (short) 1465;  // Y position from debug overlay

    // Zone/Act indices for loadZoneAndAct (not ROM zone IDs)
    // HTZ is zone index 4 in Sonic2ZoneRegistry (EHZ=0, CPZ=1, ARZ=2, CNZ=3, HTZ=4)
    private static final int HTZ_ZONE_INDEX = 4;
    private static final int ACT_2_INDEX = 1;

    @BeforeClass
    public static void loadLevel() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        SonicConfigurationService cs = SonicConfigurationService.getInstance();
        mainCharCode = cs.getString(SonicConfiguration.MAIN_CHARACTER_CODE);

        Sonic temp = new Sonic(mainCharCode, (short) 0, (short) 0);
        SpriteManager.getInstance().addSprite(temp);
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(temp);
        camera.setFrozen(false);

        LevelManager.getInstance().loadZoneAndAct(HTZ_ZONE_INDEX, ACT_2_INDEX);
        GroundSensor.setLevelManager(LevelManager.getInstance());
    }

    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    @Before
    public void setUp() {
        TestEnvironment.resetPerTest();
        sprite = new Sonic(mainCharCode, (short) 0, (short) 0);
        SpriteManager.getInstance().addSprite(sprite);
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        Level level = LevelManager.getInstance().getCurrentLevel();
        if (level != null) {
            camera.setMinX((short) level.getMinX());
            camera.setMaxX((short) level.getMaxX());
            camera.setMinY((short) level.getMinY());
            camera.setMaxY((short) level.getMaxY());
        }

        camera.updatePosition(true);
        testRunner = new HeadlessTestRunner(sprite);
    }

    /**
     * Tests that Sonic maintains movement in a spring loop.
     *
     * <p>In the original game, Sonic should bounce between springs indefinitely
     * as long as he has enough speed. This test verifies that springs trigger
     * correctly even when Sonic is facing the opposite direction.
     */
    @Test
    public void testSpringLoopMaintainsMovement() {
        // Position sprite at the spring loop test location
        sprite.setX(START_X);
        sprite.setY(START_Y);
        Camera.getInstance().updatePosition(true);

        // Reset the object manager's spawn window to the new camera position
        // so objects near our test position are spawned
        LevelManager.getInstance().getObjectManager().reset(Camera.getInstance().getX());

        // Log initial state
        logState("Initial");

        // Verify we're at the correct starting position
        assertEquals("Initial X position should match START_X", START_X, sprite.getX());
        assertEquals("Initial Y position should match START_Y", START_Y, sprite.getY());

        // Step 1 frame holding right to initiate movement
        testRunner.stepFrame(false, false, false, true, false);
        logState("After 1 frame right");

        // Let simulation run 300 frames with no input
        // Track when GSpeed hits 0 to diagnose the bug
        short prevGSpeed = sprite.getGSpeed();
        short prevXSpeed = sprite.getXSpeed();
        for (int i = 0; i < 300; i++) {
            testRunner.stepIdleFrames(1);
            int frame = i + 2; // Frame number (1 was the initial right press)

            short gSpeed = sprite.getGSpeed();
            short xSpeed = sprite.getXSpeed();

            // Log when GSpeed transitions to 0 (this is when the bug occurs)
            if (gSpeed == 0 && prevGSpeed != 0) {
                System.out.printf("*** GSPEED HIT 0 at frame %d: X=%d, XSpeed=%d->%d, GSpeed=%d->%d, Air=%b%n",
                    frame, sprite.getX(), prevXSpeed, xSpeed, prevGSpeed, gSpeed, sprite.getAir());
            }

            // Log every frame for first 60 frames to see the spring interaction
            if (frame <= 60) {
                logState("Frame " + frame);
            } else if (frame % 50 == 0) {
                logState("Frame " + frame);
            }

            prevGSpeed = gSpeed;
            prevXSpeed = xSpeed;
        }

        logState("Final");

        // Verify Sonic is still moving
        short gSpeed = sprite.getGSpeed();
        assertNotEquals("Sonic should still be moving after 300 frames in spring loop. " +
            "GSpeed=0 indicates spring was not triggered.", 0, gSpeed);
    }

    /**
     * Helper method to log sprite state for debugging.
     */
    private void logState(String label) {
        System.out.printf("%s: X=%d (0x%04X), Y=%d (0x%04X), GSpeed=%d, XSpeed=%d, YSpeed=%d, Air=%b, Facing=%s%n",
            label,
            sprite.getX(), sprite.getX() & 0xFFFF,
            sprite.getY(), sprite.getY() & 0xFFFF,
            sprite.getGSpeed(),
            sprite.getXSpeed(),
            sprite.getYSpeed(),
            sprite.getAir(),
            sprite.getDirection());
    }
}
