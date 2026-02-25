package com.openggf.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

/**
 * Test that Sonic detaches from the HTZ rising lava platform when it descends
 * into solid terrain (ROM: DropOnFloor, s2.asm:35810).
 *
 * Sonic at debug position X=6857 Y=1403 in HTZ1 earthquake zone.
 * Platform rises -> Sonic up to ~Y=1337. On descent, Sonic should not clip below
 * the terrain floor (Y should stay within 30px of the settled position).
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestHtzDropOnFloor {

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    private static final int HTZ_ZONE = 4;
    private static final int HTZ_ACT = 0;

    private Sonic sprite;
    private HeadlessTestRunner testRunner;
    private Camera camera;

    /** Clear the Sonic2LevelEventManager singleton via reflection so it's recreated with the new Camera. */
    private static void resetLevelEventManager() throws Exception {
        Field instanceField = Sonic2LevelEventManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    @Before
    public void setUp() throws Exception {
        GraphicsManager.resetInstance();
        Camera.resetInstance();
        // Reset Sonic2LevelEventManager singleton so it picks up the new Camera instance.
        // Without this, the event manager holds a stale Camera reference from before reset.
        resetLevelEventManager();
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        sprite = new Sonic(mainCode, (short) 6857, (short) 1403);

        SpriteManager.getInstance().addSprite(sprite);

        camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        LevelManager.getInstance().loadZoneAndAct(HTZ_ZONE, HTZ_ACT);
        GroundSensor.setLevelManager(LevelManager.getInstance());

        // Teleport Sonic AFTER level load (level load resets to start position)
        sprite.setX((short) 6857);
        sprite.setY((short) 1403);

        // Snap camera to Sonic's position — camera must be in earthquake trigger zone
        // (X >= 0x1800 = 6144, Y >= 0x400 = 1024) for the earthquake to activate naturally.
        camera.updatePosition(true);

        testRunner = new HeadlessTestRunner(sprite);
    }

    @Test
    public void sonicDetachesFromLavaPlatformAtFloor() {
        Sonic2LevelEventManager lem = Sonic2LevelEventManager.getInstance();

        // Settle: let earthquake trigger and Sonic land on terrain
        for (int i = 0; i < 20; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        // Verify earthquake activated
        assertTrue("Earthquake should have triggered (camera in zone)",
                lem.getCameraBgYOffset() > 0 || lem.getEventRoutine() >= 2);

        int baseY = sprite.getY();
        int maxY = baseY;
        boolean detectedClip = false;

        // Run 1200 frames — enough for full oscillation cycles.
        // Platform rises (Sonic rides up to ~Y=1337) then descends.
        // DropOnFloor should detach Sonic when platform pushes into terrain.
        for (int frame = 0; frame < 1200; frame++) {
            testRunner.stepFrame(false, false, false, false, false);

            int y = sprite.getY();
            if (y > maxY) maxY = y;

            if (y > baseY + 30) {
                detectedClip = true;
                break;
            }
        }

        assertFalse("Sonic should not clip through floor when riding descending platform "
                + "(Y should stay <= " + (baseY + 30) + " but reached " + maxY + ")", detectedClip);
    }

    /**
     * ROM: Obj30 subtype 4 uses jsrto (JSR) for DropOnFloor at s2.asm:49149,
     * which returns and falls through to Obj30_HurtSupportedPlayers (line 49151).
     * Sonic should take lava damage when standing on the subtype 4 platform.
     */
    @Test
    public void sonicTakesDamageFromLavaSubtype4() {
        // Give Sonic rings so hurt doesn't kill him
        sprite.setRingCount(10);

        // Reposition to the subtype 4 platform area
        sprite.setX((short) 7502);
        sprite.setY((short) 1329);
        camera.updatePosition(true);

        // Settle: let earthquake trigger
        for (int i = 0; i < 20; i++) {
            testRunner.stepFrame(false, false, false, false, false);
        }

        boolean wasHurt = false;

        // Run 600 frames — Sonic should land on subtype 4 lava and get hurt
        for (int frame = 0; frame < 600; frame++) {
            testRunner.stepFrame(false, false, false, false, false);

            if (sprite.isHurt()) {
                wasHurt = true;
                break;
            }
        }

        assertTrue("Sonic should take damage from subtype 4 lava at (7502, 1329)", wasHurt);
    }
}
