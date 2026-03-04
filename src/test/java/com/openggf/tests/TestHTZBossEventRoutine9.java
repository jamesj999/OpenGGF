package com.openggf.tests;

import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2LevelEventManager;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

/**
 * Regression tests for HTZ Act 2 level event routine 9 (post-boss camera behavior).
 */
public class TestHTZBossEventRoutine9 {

    private Camera camera;
    private Sonic2LevelEventManager levelEvents;

    @Before
    public void setUp() throws Exception {
        resetSonic2LevelEventManagerSingleton();
        Camera.resetInstance();
        GameServices.gameState().resetSession();

        camera = Camera.getInstance();
        levelEvents = Sonic2LevelEventManager.getInstance();
        levelEvents.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 1); // HTZ Act 2
        levelEvents.setEventRoutine(18); // Routine 9
    }

    @Test
    public void routine9DoesNothingWhileBossStillActive() {
        GameServices.gameState().setCurrentBossId(3); // HTZ boss active

        camera.setX((short) 0x30E8);
        camera.setMinX((short) 0x2EE0);
        camera.setMinY((short) 0x42A);
        camera.setMaxY((short) 0x432); // also sets maxY target

        levelEvents.update();

        assertEquals(0x2EE0, camera.getMinX());
        assertEquals(0x42A, camera.getMinY());
        assertEquals(0x432, camera.getMaxYTarget());
    }

    @Test
    public void routine9UnlocksAfterBossDefeatAndEasesVerticalBounds() {
        GameServices.gameState().setCurrentBossId(0); // Boss defeated

        camera.setX((short) 0x30E0);
        camera.setMinX((short) 0x2EE0);
        camera.setMinY((short) 0x42A);
        camera.setMaxY((short) 0x432); // also sets maxY target

        levelEvents.update();

        assertEquals((short) 0x30E0, camera.getMinX());
        assertEquals((short) 0x428, camera.getMinY());
        assertEquals((short) 0x430, camera.getMaxYTarget());
    }

    private static void resetSonic2LevelEventManagerSingleton() throws Exception {
        Field instanceField = Sonic2LevelEventManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
