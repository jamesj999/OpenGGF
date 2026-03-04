package com.openggf.tests;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.LevelManager;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for SCZ spawn stability:
 * Sonic should settle onto ObjB2 (Tornado) instead of falling to death.
 *
 * Level data is loaded once via {@link SharedLevel#load} in {@code @BeforeClass};
 * sprite, camera, and game state are reset per test via {@link HeadlessTestFixture}.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestSczSpawnOnTornado {

    private static final int ZONE_SCZ = 8;
    private static final int ACT_1 = 0;

    @ClassRule public static RequiresRomRule romRule = new RequiresRomRule();

    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        sharedLevel = SharedLevel.load(SonicGame.SONIC_2, ZONE_SCZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        if (sharedLevel != null) sharedLevel.dispose();
    }

    private HeadlessTestFixture fixture;
    private LevelManager levelManager;

    @Before
    public void setUp() {
        fixture = HeadlessTestFixture.builder()
                .withSharedLevel(sharedLevel)
                .build();
        levelManager = LevelManager.getInstance();
    }

    @Test
    public void sonicLandsOnTornadoAfterSkyChaseSpawn() {
        assertNotNull("ObjectManager should be initialized", levelManager.getObjectManager());
        boolean hasTornadoSpawn = levelManager.getActiveObjectSpawns().stream()
                .anyMatch(spawn -> spawn.objectId() == Sonic2ObjectIds.TORNADO);
        assertTrue("SCZ spawn window should contain ObjB2 Tornado", hasTornadoSpawn);

        boolean rodeTornado = false;
        for (int i = 0; i < 180; i++) {
            fixture.stepFrame(false, false, false, false, false);
            if (levelManager.getObjectManager().isRidingObject(fixture.sprite())) {
                rodeTornado = true;
                break;
            }
            if (fixture.sprite().getDead()) {
                break;
            }
        }

        assertFalse("Sonic should not die before landing on Tornado", fixture.sprite().getDead());
        assertTrue("Sonic should establish riding contact with Tornado after SCZ spawn", rodeTornado);
    }

    @Test
    public void sonicStaysOnTornadoWhenRunningRight() {
        boolean rodeTornado = false;
        for (int i = 0; i < 180; i++) {
            fixture.stepFrame(false, false, false, false, false);
            if (levelManager.getObjectManager().isRidingObject(fixture.sprite())) {
                rodeTornado = true;
                break;
            }
        }

        assertTrue("Precondition: Sonic should be riding Tornado before movement test", rodeTornado);

        for (int i = 0; i < 120; i++) {
            fixture.stepFrame(false, false, false, true, false);
            assertFalse("Sonic should not die while running on Tornado", fixture.sprite().getDead());
        }

        assertTrue("Sonic should still be riding Tornado after running right",
                levelManager.getObjectManager().isRidingObject(fixture.sprite()));
    }
}
