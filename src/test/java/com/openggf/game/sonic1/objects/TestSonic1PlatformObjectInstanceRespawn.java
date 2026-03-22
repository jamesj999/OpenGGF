package com.openggf.game.sonic1.objects;

import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1PlatformObjectInstanceRespawn {

    private static final class StubLevelManager extends LevelManager {
        private ObjectManager objectManager;

        @Override
        public int getCurrentZone() {
            return Sonic1Constants.ZONE_GHZ;
        }

        @Override
        public ObjectManager getObjectManager() {
            return objectManager;
        }

        void setObjectManager(ObjectManager objectManager) {
            this.objectManager = objectManager;
        }
    }

    @Before
    public void setUp() {
        Camera.getInstance().resetState();
    }

    @Test
    public void fallingPlatformDoesNotRespawnImmediatelyWhileStillInWindow() {
        Camera camera = Camera.getInstance();
        camera.setX((short) 0);
        camera.setMaxY((short) 0);

        ObjectSpawn spawn = new ObjectSpawn(100, 300, 0x18, 0x04, 0, false, 0);
        StubLevelManager levelManager = new StubLevelManager();

        ObjectRegistry registry = new ObjectRegistry() {
            @Override
            public ObjectInstance create(ObjectSpawn objectSpawn) {
                return new Sonic1PlatformObjectInstance(objectSpawn);
            }

            @Override
            public void reportCoverage(List<ObjectSpawn> spawns) {
            }

            @Override
            public String getPrimaryName(int objectId) {
                return "Platform";
            }
        };

        ObjectManager manager = new ObjectManager(List.of(spawn), registry, 0, null, null);
        levelManager.setObjectManager(manager);

        manager.reset(0);
        manager.update(0, null, null, 1);

        assertEquals(0, manager.getActiveObjects().size());
        assertFalse(manager.getActiveSpawns().contains(spawn));

        // Staying in the same camera window must not instantly recreate the platform.
        manager.update(0, null, null, 2);
        assertEquals(0, manager.getActiveObjects().size());

        // After leaving the window and coming back, normal respawn should be allowed.
        // With deferred placement, placement.update() streams spawns at end of each frame
        // and syncActiveSpawns() creates instances at start of the next frame.
        camera.setMaxY((short) 1000);
        manager.update(1400, null, null, 3);  // Streams spawn out of window
        manager.update(0, null, null, 4);     // Streams spawn back into window
        manager.update(0, null, null, 5);     // syncActiveSpawns creates the instance
        assertTrue(manager.getActiveSpawns().contains(spawn));
        assertEquals(1, manager.getActiveObjects().size());
    }
}
