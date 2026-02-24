package com.openggf.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import com.openggf.sprites.playable.GroundMode;

import static org.junit.Assert.*;

/**
 * Headless regression test for ARZ1 spring-to-loop traversal.
 *
 * <p>Sonic starts at (2468, 841) in ARZ Act 1, runs left until he hits a
 * horizontal spring, then must maintain enough speed to traverse the full
 * loop to the right. The test verifies that:
 * <ol>
 *   <li>gSpeed is not incorrectly reset to 0 after the spring bounce</li>
 *   <li>Sonic traverses the full 360-degree loop (enters CEILING mode and
 *       returns to GROUND mode)</li>
 * </ol>
 *
 * <p>ROM reference: In the original game, the spring provides enough
 * momentum for Sonic to run through the terrain curve without falling off.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestArzSpringLoop {

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    private static final short START_X = 2468;
    private static final short START_Y = 841;

    // ARZ is zone index 2 in Sonic2ZoneRegistry
    private static final int ARZ_ZONE_INDEX = 2;
    private static final int ACT_1_INDEX = 0;

    // Maximum frames to allow for running left + spring + loop traversal
    private static final int MAX_FRAMES = 600;

    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        sprite = new Sonic(mainCode, START_X, START_Y);

        SpriteManager spriteManager = SpriteManager.getInstance();
        spriteManager.addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        LevelManager.getInstance().loadZoneAndAct(ARZ_ZONE_INDEX, ACT_1_INDEX);
        GroundSensor.setLevelManager(LevelManager.getInstance());

        // Position must be set AFTER level load (loadZoneAndAct resets position)
        sprite.setX(START_X);
        sprite.setY(START_Y);

        camera.updatePosition(true);
        LevelManager.getInstance().getObjectManager().reset(camera.getX());

        testRunner = new HeadlessTestRunner(sprite);
    }

    @Test
    public void testSpringLaunchAndLoopTraversal() {
        logState("Initial");

        // Phase 1: Run left until we hit the spring.
        // The spring will reverse our direction (gSpeed goes from negative to positive).
        boolean hitSpring = false;
        int springFrame = -1;

        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            testRunner.stepFrame(false, false, true, false, false); // hold left

            short gSpeed = sprite.getGSpeed();

            if (frame < 10 || frame % 20 == 0) {
                logState("Run-left frame " + frame);
            }

            // Detect spring hit: gSpeed switches from negative/zero to positive
            if (!hitSpring && gSpeed > 0x200) {
                hitSpring = true;
                springFrame = frame;
                logState("*** SPRING HIT at frame " + frame);
                break;
            }
        }

        assertTrue("Sonic should hit a spring while running left (gSpeed should become positive). "
                + "Final gSpeed=" + sprite.getGSpeed() + ", X=" + sprite.getX(),
                hitSpring);

        // Phase 2: After the spring, let Sonic proceed to the right with no input.
        // Track ground mode transitions to verify full 360-degree loop traversal:
        // GROUND -> RIGHTWALL -> CEILING -> LEFTWALL -> GROUND
        boolean enteredCeiling = false;
        boolean returnedToGround = false;
        boolean gSpeedWasReset = false;
        int resetFrame = -1;
        int loopCompleteFrame = -1;
        short prevGSpeed = sprite.getGSpeed();

        for (int frame = springFrame + 1; frame < MAX_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, false, false); // no input

            short gSpeed = sprite.getGSpeed();
            GroundMode mode = sprite.getGroundMode();

            if (frame <= springFrame + 30 || frame % 20 == 0) {
                logState("Post-spring frame " + frame);
            }

            // Track loop progression through ground modes
            if (mode == GroundMode.CEILING) {
                enteredCeiling = true;
            }
            if (enteredCeiling && mode == GroundMode.GROUND && !sprite.getAir()) {
                returnedToGround = true;
                loopCompleteFrame = frame;
                logState("*** LOOP COMPLETED at frame " + frame);
                break;
            }

            // Detect gSpeed reset to 0 while still in the loop (the bug we're testing for)
            if (!returnedToGround && gSpeed == 0 && prevGSpeed != 0 && !sprite.getAir()) {
                gSpeedWasReset = true;
                resetFrame = frame;
                logState("*** GSPEED RESET TO 0 at frame " + frame);
                break;
            }

            prevGSpeed = gSpeed;
        }

        logState("Final");

        assertFalse("gSpeed should not be reset to 0 during loop traversal (was reset at frame "
                + resetFrame + "). This indicates a physics bug preventing loop completion.",
                gSpeedWasReset);

        assertTrue("Sonic should enter CEILING mode during the loop traversal.",
                enteredCeiling);

        assertTrue("Sonic should return to GROUND mode after completing the full 360-degree loop. "
                + "Loop complete frame=" + loopCompleteFrame + ", final X=" + sprite.getX()
                + ", final mode=" + sprite.getGroundMode(),
                returnedToGround);
    }

    private void logState(String label) {
        System.out.printf("%s: X=%d, Y=%d, GSpeed=%d, XSpeed=%d, YSpeed=%d, Air=%b, Angle=0x%02X, Mode=%s, Facing=%s%n",
                label,
                sprite.getX(), sprite.getY(),
                sprite.getGSpeed(), sprite.getXSpeed(), sprite.getYSpeed(),
                sprite.getAir(),
                sprite.getAngle() & 0xFF,
                sprite.getGroundMode(),
                sprite.getDirection());
    }
}
