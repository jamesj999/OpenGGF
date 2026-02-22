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

import static org.junit.Assert.*;

/**
 * Reproduces a physics bug in the S1 SBZ1 ending credits demo sequence.
 *
 * In the real game, Sonic traverses a narrow tube/passage area smoothly.
 * In the engine, when Sonic reaches Y=889 (0x379), all speeds (xSpeed, ySpeed,
 * gSpeed) are incorrectly reset to 0, causing Sonic to get stuck.
 *
 * The demo input is replayed from Sonic1CreditsDemoData (credit 5: SBZ Act 1).
 * Start position: X=0x1570, Y=0x016C.
 *
 * Demo input sequence (from ROM at 0x5E4C):
 *   Idle 37f, Left 82f, Idle 37f, Left 35f, Idle 231f, Left 104f, Idle 13f, Right 110f
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestSbz1CreditsDemoBug {

    private static final int ZONE_SBZ = 5;
    private static final int ACT_1 = 0;
    private static final short START_X = 0x1570;
    private static final short START_Y = 0x016C;

    /** Y position (decimal) where the bug manifests. */
    private static final int BUG_Y = 889;

    /**
     * Demo input pairs: [buttonMask, duration].
     * Button bits: 0x04=Left, 0x08=Right, 0x00=None.
     */
    private static final int[][] DEMO_INPUTS = {
        {0x00, 0x25}, // Idle 37 frames
        {0x04, 0x52}, // Left 82 frames
        {0x00, 0x25}, // Idle 37 frames
        {0x04, 0x23}, // Left 35 frames
        {0x00, 0xE7}, // Idle 231 frames
        {0x04, 0x68}, // Left 104 frames
        {0x00, 0x0D}, // Idle 13 frames
        {0x08, 0x6E}, // Right 110 frames
    };

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        // Create sprite at dummy position; will be repositioned after level load
        sprite = new Sonic(mainCode, (short) 0, (short) 0);

        SpriteManager.getInstance().addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        LevelManager.getInstance().loadZoneAndAct(ZONE_SBZ, ACT_1);
        GroundSensor.setLevelManager(LevelManager.getInstance());

        // Set the credits demo start position AFTER level load
        // (loadZoneAndAct overrides player position with level default)
        sprite.setCentreX(START_X);
        sprite.setCentreY(START_Y);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);
        sprite.setAir(true);  // Start airborne to settle onto terrain

        camera.updatePosition(true);

        testRunner = new HeadlessTestRunner(sprite);

        // Let Sonic settle onto the ground
        for (int i = 0; i < 30; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }
        System.out.printf("After settle: X=%d Y=%d air=%b%n",
                sprite.getX(), sprite.getY(), sprite.getAir());
    }

    @Test
    public void testSonicDoesNotGetStuckInSbz1Tube() {
        int totalFrame = 0;
        boolean reachedBugArea = false;
        boolean bugTriggered = false;
        int bugFrame = -1;

        for (int[] pair : DEMO_INPUTS) {
            int buttons = pair[0];
            int duration = pair[1];

            boolean left = (buttons & 0x04) != 0;
            boolean right = (buttons & 0x08) != 0;
            boolean up = (buttons & 0x01) != 0;
            boolean down = (buttons & 0x02) != 0;
            boolean jump = (buttons & 0x70) != 0;

            for (int f = 0; f < duration; f++) {
                testRunner.stepFrame(up, down, left, right, jump);
                totalFrame++;

                short y = sprite.getY();
                short xSpeed = sprite.getXSpeed();
                short ySpeed = sprite.getYSpeed();
                short gSpeed = sprite.getGSpeed();

                // Check if we're in the bug area (Y near 889)
                if (Math.abs(y - BUG_Y) < 10) {
                    reachedBugArea = true;

                    // The bug: all speeds simultaneously zero when they shouldn't be
                    if (xSpeed == 0 && ySpeed == 0 && gSpeed == 0 && !sprite.getDead()) {
                        // Only flag as bug if Sonic was previously moving (not just standing idle at start)
                        if (totalFrame > 50) {
                            bugTriggered = true;
                            bugFrame = totalFrame;
                            System.out.printf("BUG TRIGGERED at frame %d: X=%d Y=%d xSpeed=%d ySpeed=%d gSpeed=%d air=%b angle=%d rolling=%b pushing=%b tunnelMode=%b%n",
                                    totalFrame, sprite.getX(), y, xSpeed, ySpeed, gSpeed,
                                    sprite.getAir(), sprite.getAngle() & 0xFF,
                                    sprite.getRolling(), sprite.getPushing(), sprite.isTunnelMode());
                        }
                    }
                }

                // Diagnostic: print state every 10 frames during key movement phases
                if (totalFrame % 20 == 0 || (Math.abs(y - BUG_Y) < 20)) {
                    System.out.printf("Frame %4d: X=%5d Y=%5d xSpd=%6d ySpd=%6d gSpd=%6d air=%b angle=0x%02X rolling=%b pushing=%b tunnel=%b onObj=%b ctrl=%b objCtrl=%b%n",
                            totalFrame, sprite.getX(), y, xSpeed, ySpeed, gSpeed,
                            sprite.getAir(), sprite.getAngle() & 0xFF,
                            sprite.getRolling(), sprite.getPushing(), sprite.isTunnelMode(),
                            sprite.isOnObject(), sprite.isControlLocked(), sprite.isObjectControlled());
                }
            }
        }

        assertTrue("Demo should reach the Y=889 area in SBZ1", reachedBugArea);
        assertFalse("Sonic should NOT have all speeds reset to 0 at Y=" + BUG_Y
                + " (bug triggered at frame " + bugFrame + ")", bugTriggered);
    }
}
