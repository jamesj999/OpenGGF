package com.openggf.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic1.objects.Sonic1PushBlockObjectInstance;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.util.Collection;

import static org.junit.Assert.*;

/**
 * Regression test for MZ Act 2 push block gap.
 * <p>
 * In MZ2, a push block (33:81 R) sits above a gap. The player must push it
 * far enough rightward that Sonic falls through the gap underneath. The push
 * must continue even when Sonic's floor sensors momentarily straddle the gap
 * boundary (causing brief airborne frames), matching the ROM where the push
 * handler uses d0 (displacement) rather than the pushing status flag.
 */
@RequiresRom(SonicGame.SONIC_1)
public class TestHeadlessMZ2PushBlockGap {

    private static final int ZONE_MZ = 1;
    private static final int ACT_2 = 1;

    private static final int START_CENTRE_X = 634;
    private static final int START_CENTRE_Y = 1337;

    private static final int MAX_FRAMES = 600;
    private static final int MIN_FALL_DISTANCE = 32;

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Sonic sprite;
    private HeadlessTestRunner testRunner;

    @Before
    public void setUp() throws Exception {
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        sprite = new Sonic(mainCode, (short) 0, (short) 0);

        SpriteManager.getInstance().addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        LevelManager.getInstance().loadZoneAndAct(ZONE_MZ, ACT_2);
        GroundSensor.setLevelManager(LevelManager.getInstance());
        camera.updatePosition(true);

        testRunner = new HeadlessTestRunner(sprite);
    }

    @Test
    public void testPushBlockClearsGapAndSonicFalls() {
        sprite.setCentreX((short) START_CENTRE_X);
        sprite.setCentreY((short) START_CENTRE_Y);
        sprite.setAir(false);
        sprite.setXSpeed((short) 0);
        sprite.setYSpeed((short) 0);
        sprite.setGSpeed((short) 0);

        Camera.getInstance().updatePosition(true);

        // Let Sonic settle onto ground
        for (int i = 0; i < 10; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        Sonic1PushBlockObjectInstance block = findNearestPushBlock();
        assertNotNull("Should find a push block near Sonic", block);

        int blockStartX = block.getX();
        int startY = sprite.getCentreY();

        // Hold right to push the block
        for (int frame = 0; frame < MAX_FRAMES; frame++) {
            testRunner.stepFrame(false, false, false, true, false);

            int currentY = sprite.getCentreY();
            if (currentY > startY + MIN_FALL_DISTANCE) {
                // Sonic fell through the gap
                return;
            }
        }

        fail("Sonic should have fallen through the gap after pushing the block in MZ2, "
                + "but his Y only reached " + sprite.getCentreY()
                + " (started at " + startY + ", needed drop of " + MIN_FALL_DISTANCE + "px). "
                + "Block: " + blockStartX + " -> " + block.getX()
                + " (moved " + (block.getX() - blockStartX) + "px)");
    }

    private Sonic1PushBlockObjectInstance findNearestPushBlock() {
        LevelManager lm = LevelManager.getInstance();
        if (lm == null || lm.getObjectManager() == null) return null;

        Collection<ObjectInstance> objects = lm.getObjectManager().getActiveObjects();
        Sonic1PushBlockObjectInstance nearest = null;
        int nearestDist = Integer.MAX_VALUE;

        for (ObjectInstance obj : objects) {
            if (obj instanceof Sonic1PushBlockObjectInstance pb) {
                int dist = Math.abs(pb.getX() - sprite.getCentreX())
                         + Math.abs(pb.getY() - sprite.getCentreY());
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = pb;
                }
            }
        }
        return nearest;
    }
}
