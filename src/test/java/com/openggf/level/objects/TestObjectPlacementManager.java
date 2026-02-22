package com.openggf.level.objects;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestObjectPlacementManager {
    @Test
    public void testWindowingAndRememberedObjects() {
        ObjectSpawn spawnA = new ObjectSpawn(0, 0, 0x01, 0, 0, false, 0);
        ObjectSpawn spawnB = new ObjectSpawn(800, 0, 0x02, 0, 0, false, 0);

        ObjectManager.Placement manager = new ObjectManager.Placement(List.of(spawnA, spawnB));
        manager.reset(0);

        assertTrue(manager.getActiveSpawns().contains(spawnA));
        assertFalse(manager.getActiveSpawns().contains(spawnB));

        manager.update(500);
        assertTrue(manager.getActiveSpawns().contains(spawnB));

        manager.markRemembered(spawnB);
        // Spawn stays in active window but is marked as remembered
        assertTrue(manager.getActiveSpawns().contains(spawnB));
        assertTrue(manager.isRemembered(spawnB));

        manager.update(2000);
        // Spawn scrolled out of window
        assertFalse(manager.getActiveSpawns().contains(spawnB));
        // But still remembered
        assertTrue(manager.isRemembered(spawnB));

        manager.update(0);
        // Spawn not in window at camera X=0
        assertFalse(manager.getActiveSpawns().contains(spawnB));
    }

    @Test
    public void testRemoveFromActiveRequiresWindowExitBeforeRespawn() {
        ObjectSpawn spawn = new ObjectSpawn(500, 0, 0x1A, 0, 0, false, 0);
        ObjectManager.Placement manager = new ObjectManager.Placement(List.of(spawn));

        manager.reset(0);
        assertTrue(manager.getActiveSpawns().contains(spawn));

        // Object destroyed while still in window: should not respawn immediately.
        manager.removeFromActive(spawn);
        assertFalse(manager.getActiveSpawns().contains(spawn));
        manager.update(0);
        assertFalse(manager.getActiveSpawns().contains(spawn));

        // Move forward far enough that spawn leaves the active window.
        // This goes through trimActive(), which clears the temporary destroyed lock.
        manager.update(1400);
        assertFalse(manager.getActiveSpawns().contains(spawn));

        // Returning camera into range should allow respawn.
        manager.update(200);
        assertTrue(manager.getActiveSpawns().contains(spawn));
    }
}
