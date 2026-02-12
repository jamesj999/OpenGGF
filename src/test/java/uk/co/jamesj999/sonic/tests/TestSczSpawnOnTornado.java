package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.tests.rules.RequiresRom;
import uk.co.jamesj999.sonic.tests.rules.RequiresRomRule;
import uk.co.jamesj999.sonic.tests.rules.SonicGame;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for SCZ spawn stability:
 * Sonic should settle onto ObjB2 (Tornado) instead of falling to death.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestSczSpawnOnTornado {

    private static final int ZONE_SCZ = 8;
    private static final int ACT_1 = 0;

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private AbstractPlayableSprite sprite;
    private HeadlessTestRunner runner;
    private LevelManager levelManager;

    @Before
    public void setUp() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        String mainCharacter = config.getString(SonicConfiguration.MAIN_CHARACTER_CODE);

        GraphicsManager.getInstance().initHeadless();

        // LevelManager load path expects playable sprite to exist first.
        sprite = new Sonic(mainCharacter, (short) 100, (short) 100);
        SpriteManager.getInstance().addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(ZONE_SCZ, ACT_1);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);

        runner = new HeadlessTestRunner(sprite);
    }

    @Test
    public void sonicLandsOnTornadoAfterSkyChaseSpawn() {
        assertNotNull("ObjectManager should be initialized", levelManager.getObjectManager());
        boolean hasTornadoSpawn = levelManager.getActiveObjectSpawns().stream()
                .anyMatch(spawn -> spawn.objectId() == Sonic2ObjectIds.TORNADO);
        assertTrue("SCZ spawn window should contain ObjB2 Tornado", hasTornadoSpawn);

        boolean rodeTornado = false;
        for (int i = 0; i < 180; i++) {
            runner.stepFrame(false, false, false, false, false);
            if (levelManager.getObjectManager().isRidingObject(sprite)) {
                rodeTornado = true;
                break;
            }
            if (sprite.getDead()) {
                break;
            }
        }

        assertFalse("Sonic should not die before landing on Tornado", sprite.getDead());
        assertTrue("Sonic should establish riding contact with Tornado after SCZ spawn", rodeTornado);
    }

    @Test
    public void sonicStaysOnTornadoWhenRunningRight() {
        boolean rodeTornado = false;
        for (int i = 0; i < 180; i++) {
            runner.stepFrame(false, false, false, false, false);
            if (levelManager.getObjectManager().isRidingObject(sprite)) {
                rodeTornado = true;
                break;
            }
        }

        assertTrue("Precondition: Sonic should be riding Tornado before movement test", rodeTornado);

        for (int i = 0; i < 120; i++) {
            runner.stepFrame(false, false, false, true, false);
            assertFalse("Sonic should not die while running on Tornado", sprite.getDead());
        }

        assertTrue("Sonic should still be riding Tornado after running right",
                levelManager.getObjectManager().isRidingObject(sprite));
    }
}
