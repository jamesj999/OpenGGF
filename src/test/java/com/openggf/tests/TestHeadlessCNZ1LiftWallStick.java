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
 * Regression test for CNZ1 wall sticking while riding lifts.
 * <p>
 * When Sonic rides a CNZ Big Block (ObjD4) near a terrain wall and presses
 * left, the riding state should remain stable. Before the fix, re-evaluating
 * the ridden object in the contact loop caused false side detection
 * (absDistX <= absDistY) at platform edges near walls, clearing isOnObject
 * each alternating frame.
 * <p>
 * ROM reference: s2.asm:34806 — SolidObject checks btst d6,status(a0) and
 * branches to MvSonicOnPtfm (carrying only) when already standing, skipping
 * overlap re-evaluation.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestHeadlessCNZ1LiftWallStick {

    private static final int ZONE_CNZ = 3;
    private static final int ACT_1 = 0;

    // Position near a wall where Big Block lifts operate in CNZ1
    private static final int START_CENTRE_X = 3311;
    private static final int START_CENTRE_Y = 1305;

    private static final int SETTLE_FRAMES = 30;
    private static final int HOLD_LEFT_FRAMES = 40;

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();
    private static String mainCharCode;

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

        LevelManager.getInstance().loadZoneAndAct(ZONE_CNZ, ACT_1);
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

    @Test
    public void testRidingStateDoesNotOscillateNearWall() {
        sprite.setCentreX((short) START_CENTRE_X);
        sprite.setCentreY((short) START_CENTRE_Y);
        sprite.setAir(false);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        Camera.getInstance().updatePosition(true);

        // Let Sonic settle onto ground/platform
        for (int i = 0; i < SETTLE_FRAMES; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        // Record if Sonic is on an object after settling
        boolean wasOnObject = sprite.isOnObject();

        // Hold left near the wall and track on-object state changes
        int oscillationCount = 0;
        boolean previousOnObject = wasOnObject;
        int lastX = sprite.getCentreX();
        int maxXJitter = 0;

        for (int frame = 0; frame < HOLD_LEFT_FRAMES; frame++) {
            testRunner.stepFrame(false, false, true, false, false);

            boolean currentOnObject = sprite.isOnObject();
            if (currentOnObject != previousOnObject) {
                oscillationCount++;
            }
            previousOnObject = currentOnObject;

            // Track X position stability
            int currentX = sprite.getCentreX();
            int jitter = Math.abs(currentX - lastX);
            if (jitter > maxXJitter) {
                maxXJitter = jitter;
            }
            lastX = currentX;
        }

        // The riding state should not oscillate rapidly. A few transitions are
        // acceptable (e.g., initial landing, walking off edge), but rapid
        // on/off toggling (>4 transitions in 40 frames) indicates the bug.
        assertTrue("isOnObject() oscillated " + oscillationCount + " times in "
                + HOLD_LEFT_FRAMES + " frames — riding state is unstable near wall. "
                + "Expected <= 4 transitions.",
                oscillationCount <= 4);
    }
}
