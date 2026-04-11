package com.openggf.level.objects;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void testRememberedObjectDoesNotRespawnOnCameraReturn() {
        // Simulates: break a block, scroll away, scroll back - block should NOT reappear
        ObjectSpawn spawn = new ObjectSpawn(500, 0, 0x32, 0, 0, true, 0x8000);
        ObjectManager.Placement manager = new ObjectManager.Placement(List.of(spawn));

        // Camera starts at 0 - spawn at x=500 is within load-ahead range (0x280=640)
        manager.reset(0);
        assertTrue(manager.getActiveSpawns().contains(spawn), "Spawn should be in active window initially");

        // Break the block: mark as remembered (single-arg - used by Placement directly)
        manager.markRemembered(spawn);
        assertTrue(manager.getActiveSpawns().contains(spawn), "Spawn should still be in active (single-arg doesn't remove)");
        assertTrue(manager.isRemembered(spawn), "Spawn should be remembered");

        // Scroll far right - spawn goes out of window
        manager.update(2000);
        assertFalse(manager.getActiveSpawns().contains(spawn), "Spawn should have left the window");
        assertTrue(manager.isRemembered(spawn), "Spawn should still be remembered");

        // Scroll back to original position - spawn should NOT reappear
        manager.update(0);
        assertFalse(manager.getActiveSpawns().contains(spawn), "Remembered spawn should NOT reappear on camera return");
        assertTrue(manager.isRemembered(spawn), "Spawn should still be remembered after return");

        // Try again: scroll to have spawn in range
        manager.update(300);
        assertFalse(manager.getActiveSpawns().contains(spawn), "Remembered spawn should NOT reappear when scrolling into range");
    }

    @Test
    public void testRememberedObjectStaysInActiveWhenCameraDoesNotMove() {
        // BUG SCENARIO: break a block, camera stays at same X (player falls vertically)
        // The spawn stays in the active window the whole time. After the object is
        // destroyed, syncActiveSpawns will see the spawn still in activeSpawns,
        // the old instance gone from activeObjects, and will create a NEW instance.
        // This is the "broken blocks reappear" bug.
        ObjectSpawn spawn = new ObjectSpawn(500, 0, 0x32, 0, 0, true, 0x8000);
        ObjectManager.Placement manager = new ObjectManager.Placement(List.of(spawn));

        manager.reset(0);
        assertTrue(manager.getActiveSpawns().contains(spawn), "Spawn should be in active window");

        // Mark as remembered using single-arg (as if the object broke)
        // IMPORTANT: The single-arg version does NOT remove from active
        manager.markRemembered(spawn);
        assertTrue(manager.isRemembered(spawn), "Spawn is remembered");

        // Camera doesn't move (player falls vertically)
        manager.update(0);

        // The spawn is still in the active window AND remembered.
        // syncActiveSpawns should check isRemembered before creating a new instance.
        // But since the spawn is still in active, it will be iterated.
        assertTrue(manager.getActiveSpawns().contains(spawn), "Spawn is still in active (camera didn't move)");
        assertTrue(manager.isRemembered(spawn), "Spawn is still remembered");
    }

    @Test
    public void testMarkRememberedWithDifferentReferenceViaEqualsFallback() {
        // Tests the equals-based fallback in getSpawnIndex:
        // When markRemembered is called with a spawn reference that is structurally
        // equal but NOT identity-equal to the canonical reference in the spawns list,
        // the fallback should still find the correct index and set the remembered bit.
        ObjectSpawn canonical = new ObjectSpawn(500, 0, 0x32, 0, 0, true, 0x8000);
        ObjectManager.Placement manager = new ObjectManager.Placement(List.of(canonical));

        manager.reset(0);
        assertTrue(manager.getActiveSpawns().contains(canonical), "Spawn should be in active window");

        // Create a structurally equal but identity-different spawn
        ObjectSpawn differentRef = new ObjectSpawn(500, 0, 0x32, 0, 0, true, 0x8000);
        assertTrue(canonical.equals(differentRef), "Spawns should be equal by value");
        assertFalse(canonical == differentRef, "Spawns should NOT be identity-equal");

        // Mark remembered using the different reference - should work via fallback
        manager.markRemembered(differentRef);
        assertTrue(manager.isRemembered(canonical), "Spawn should be remembered even with different reference");
        assertTrue(manager.isRemembered(differentRef), "Spawn should also be remembered when queried with different ref");

        // Scroll away and back - spawn should NOT reappear
        manager.update(2000);
        assertFalse(manager.getActiveSpawns().contains(canonical), "Spawn should have left the window");

        manager.update(0);
        assertFalse(manager.getActiveSpawns().contains(canonical), "Remembered spawn should NOT reappear on camera return");
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


