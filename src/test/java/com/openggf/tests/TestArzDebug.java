// Quick debug - run longer to see what happens
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
@RequiresRom(SonicGame.SONIC_2)
public class TestArzDebug {
    @Rule public RequiresRomRule romRule = new RequiresRomRule();
    private Sonic sprite;
    private HeadlessTestRunner testRunner;
    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();
        SonicConfigurationService cs = SonicConfigurationService.getInstance();
        sprite = new Sonic(cs.getString(SonicConfiguration.MAIN_CHARACTER_CODE), (short)2468, (short)841);
        SpriteManager.getInstance().addSprite(sprite);
        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);
        LevelManager.getInstance().loadZoneAndAct(2, 0);
        GroundSensor.setLevelManager(LevelManager.getInstance());
        sprite.setX((short)2468);
        sprite.setY((short)841);
        camera.updatePosition(true);
        LevelManager.getInstance().getObjectManager().reset(camera.getX());
        testRunner = new HeadlessTestRunner(sprite);
    }
    @Test
    public void debugRun() {
        // Run left to hit spring
        for (int f = 0; f < 600; f++) {
            testRunner.stepFrame(false, false, true, false, false);
            if (sprite.getGSpeed() > 0x200) {
                System.out.printf("SPRING at frame %d: X=%d Y=%d GSpeed=%d%n", f, sprite.getX(), sprite.getY(), sprite.getGSpeed());
                break;
            }
        }
        // Now run with no input for 600 frames, track everything
        int minY = sprite.getY(), maxAngle = 0;
        for (int f = 0; f < 600; f++) {
            testRunner.stepFrame(false, false, false, false, false);
            int angle = sprite.getAngle() & 0xFF;
            if (sprite.getY() < minY) minY = sprite.getY();
            if (angle > 0x20 && angle < 0xE0 && angle > maxAngle) maxAngle = angle;
            if (f < 80 || f % 30 == 0 || sprite.getGSpeed() == 0) {
                System.out.printf("f%d: X=%d Y=%d G=%d X=%d Y=%d Air=%b Ang=0x%02X Mode=%s%n",
                    f, sprite.getX(), sprite.getY(), sprite.getGSpeed(), sprite.getXSpeed(), sprite.getYSpeed(),
                    sprite.getAir(), angle, sprite.getGroundMode());
            }
            if (sprite.getGSpeed() == 0 && !sprite.getAir() && f > 5) {
                System.out.printf("*** STOPPED at frame %d%n", f);
                break;
            }
        }
        System.out.printf("Min Y reached: %d, Max non-flat angle: 0x%02X%n", minY, maxAngle);
    }
}
