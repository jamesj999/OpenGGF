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
    public void testRememberedObjectDoesNotRespawnOnCameraReturn() {
        // Simulates: break a block, scroll away, scroll back - block should NOT reappear
        ObjectSpawn spawn = new ObjectSpawn(500, 0, 0x32, 0, 0, true, 0x8000);
        ObjectManager.Placement manager = new ObjectManager.Placement(List.of(spawn));

        // Camera starts at 0 - spawn at x=500 is within load-ahead range (0x280=640)
        manager.reset(0);
        assertTrue("Spawn should be in active window initially", manager.getActiveSpawns().contains(spawn));

        // Break the block: mark as remembered (single-arg - used by Placement directly)
        manager.markRemembered(spawn);
        assertTrue("Spawn should still be in active (single-arg doesn't remove)", manager.getActiveSpawns().contains(spawn));
        assertTrue("Spawn should be remembered", manager.isRemembered(spawn));

        // Scroll far right - spawn goes out of window
        manager.update(2000);
        assertFalse("Spawn should have left the window", manager.getActiveSpawns().contains(spawn));
        assertTrue("Spawn should still be remembered", manager.isRemembered(spawn));

        // Scroll back to original position - spawn should NOT reappear
        manager.update(0);
        assertFalse("Remembered spawn should NOT reappear on camera return", manager.getActiveSpawns().contains(spawn));
        assertTrue("Spawn should still be remembered after return", manager.isRemembered(spawn));

        // Try again: scroll to have spawn in range
        manager.update(300);
        assertFalse("Remembered spawn should NOT reappear when scrolling into range", manager.getActiveSpawns().contains(spawn));
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
        assertTrue("Spawn should be in active window", manager.getActiveSpawns().contains(spawn));

        // Mark as remembered using single-arg (as if the object broke)
        // IMPORTANT: The single-arg version does NOT remove from active
        manager.markRemembered(spawn);
        assertTrue("Spawn is remembered", manager.isRemembered(spawn));

        // Camera doesn't move (player falls vertically)
        manager.update(0);

        // The spawn is still in the active window AND remembered.
        // syncActiveSpawns should check isRemembered before creating a new instance.
        // But since the spawn is still in active, it will be iterated.
        assertTrue("Spawn is still in active (camera didn't move)", manager.getActiveSpawns().contains(spawn));
        assertTrue("Spawn is still remembered", manager.isRemembered(spawn));
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
        assertTrue("Spawn should be in active window", manager.getActiveSpawns().contains(canonical));

        // Create a structurally equal but identity-different spawn
        ObjectSpawn differentRef = new ObjectSpawn(500, 0, 0x32, 0, 0, true, 0x8000);
        assertTrue("Spawns should be equal by value", canonical.equals(differentRef));
        assertFalse("Spawns should NOT be identity-equal", canonical == differentRef);

        // Mark remembered using the different reference - should work via fallback
        manager.markRemembered(differentRef);
        assertTrue("Spawn should be remembered even with different reference", manager.isRemembered(canonical));
        assertTrue("Spawn should also be remembered when queried with different ref", manager.isRemembered(differentRef));

        // Scroll away and back - spawn should NOT reappear
        manager.update(2000);
        assertFalse("Spawn should have left the window", manager.getActiveSpawns().contains(canonical));

        manager.update(0);
        assertFalse("Remembered spawn should NOT reappear on camera return", manager.getActiveSpawns().contains(canonical));
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
