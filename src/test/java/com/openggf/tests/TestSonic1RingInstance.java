package com.openggf.tests;

import com.openggf.game.sonic1.objects.Sonic1RingInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpawn;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1RingInstance {

    // ── Static-property tests ──────────────────────────────────────────────

    @Test
    public void testRingCollisionFlagsBeforeCollection() {
        assertEquals(0x47, Sonic1RingInstance.RING_COLLISION_FLAGS);
    }

    @Test
    public void testImplementsTouchResponseProvider() {
        assertTrue(TouchResponseProvider.class.isAssignableFrom(Sonic1RingInstance.class));
    }

    // ── Construction-state tests ───────────────────────────────────────────

    /** Parent constructor (layout entry) starts in INIT state: no collision flags yet. */
    @Test
    public void testParentConstructorStartsInInitState() {
        Sonic1RingInstance ring = buildParentRing(100, 100);
        // INIT state: getCollisionFlags() returns 0 (only ANIMATE returns RING_COLLISION_FLAGS)
        assertEquals("Parent ring should have no collision flags in INIT state",
                0, ring.getCollisionFlags());
    }

    /**
     * Parent constructor with a single-element spawn list (the ring is its own spawn).
     * After one update (INIT→ANIMATE), collision flags become active.
     */
    @Test
    public void testSingleSpawnRingAnimatesAfterFirstUpdate() {
        RingSpawn spawn = new RingSpawn(50, 50);
        Sonic1RingInstance ring = buildParentRingFromSpawns(200, 200, List.of(spawn));
        // After INIT→ANIMATE, the ring should still be alive (no children to spawn,
        // no ringManager collected, so it stays ANIMATE).
        withContext(new StubObjectServices(), () -> ring.update(1, null));
        assertFalse("Single-spawn ring should not be destroyed after first update",
                ring.isDestroyed());
        assertEquals("Should be in ANIMATE state with full collision flags",
                Sonic1RingInstance.RING_COLLISION_FLAGS, ring.getCollisionFlags());
    }

    /**
     * Parent constructor with more than one ring spawn has a non-empty child list
     * internally. After the first update (which calls spawnChildren), the parent
     * transitions to ANIMATE and is not destroyed.
     */
    @Test
    public void testMultiSpawnParentTransitionsToAnimateWithChildren() {
        List<RingSpawn> spawns = List.of(
                new RingSpawn(50, 50),
                new RingSpawn(58, 50),
                new RingSpawn(66, 50)
        );
        Sonic1RingInstance ring = buildParentRingFromSpawns(50, 50, spawns);
        assertEquals("Before update: INIT → no collision flags", 0, ring.getCollisionFlags());

        withContext(new StubObjectServices(), () -> ring.update(1, null));

        assertFalse("Parent ring should not be destroyed after INIT→ANIMATE", ring.isDestroyed());
        assertEquals("Parent should be in ANIMATE state after first update",
                Sonic1RingInstance.RING_COLLISION_FLAGS, ring.getCollisionFlags());
    }

    // ── State transition tests ─────────────────────────────────────────────

    /** After one update with no RingManager, INIT transitions to ANIMATE. */
    @Test
    public void testInitTransitionsToAnimateOnFirstUpdate() {
        Sonic1RingInstance ring = buildParentRing(100, 100);
        assertEquals("Before update: INIT → no collision flags", 0, ring.getCollisionFlags());

        withContext(new StubObjectServices(), () -> ring.update(1, null));

        assertEquals("After first update: ANIMATE → collision flags active",
                Sonic1RingInstance.RING_COLLISION_FLAGS, ring.getCollisionFlags());
        assertFalse("Ring should not be destroyed after INIT→ANIMATE", ring.isDestroyed());
    }

    /** When ringManager.isCollected() returns true, ANIMATE transitions to SPARKLE (no collision). */
    @Test
    public void testAnimateStaysWhenNotCollected() {
        RingSpawn ringSpawn = new RingSpawn(100, 100);
        RingManager rm = new RingManager(List.of(ringSpawn), null, null, null);

        // Ring is not collected via RingManager, so it should remain in ANIMATE.
        Sonic1RingInstance ring = buildParentRingFromSpawns(100, 100, List.of(ringSpawn));

        ObjectServices svc = new StubObjectServices() {
            @Override public RingManager ringManager() { return rm; }
        };

        // First update: INIT → ANIMATE
        withContext(svc, () -> ring.update(1, null));
        // Second update: ANIMATE stays ANIMATE because ring not collected
        withContext(svc, () -> ring.update(2, null));

        assertEquals("Ring not collected: should stay ANIMATE",
                Sonic1RingInstance.RING_COLLISION_FLAGS, ring.getCollisionFlags());
        assertFalse("Ring not collected: should not be destroyed", ring.isDestroyed());
    }

    /** When ringManager is null in SPARKLE state, ring is destroyed immediately. */
    @Test
    public void testSparkleDestroysWhenRingManagerNull() {
        // Force the ring into SPARKLE state via reflection
        Sonic1RingInstance ring = buildParentRing(100, 100);
        forceState(ring, "SPARKLE");

        withContext(new StubObjectServices(), () -> ring.update(1, null));

        assertTrue("Ring in SPARKLE with null ringManager should be destroyed", ring.isDestroyed());
    }

    /** In SPARKLE state, collision flags return 0 (ring is no longer collidable). */
    @Test
    public void testSparkleStateHasNoCollisionFlags() {
        Sonic1RingInstance ring = buildParentRing(100, 100);
        forceState(ring, "SPARKLE");

        assertEquals("SPARKLE state should return 0 collision flags",
                0, ring.getCollisionFlags());
    }

    // ── Helper methods ─────────────────────────────────────────────────────

    private static void withContext(ObjectServices svc, Runnable action) {
        setConstructionContext(svc);
        try {
            action.run();
        } finally {
            clearConstructionContext();
        }
    }

    private static Sonic1RingInstance buildParentRing(int x, int y) {
        RingSpawn spawn = new RingSpawn(x, y);
        return buildParentRingFromSpawns(x, y, List.of(spawn));
    }

    private static Sonic1RingInstance buildParentRingFromSpawns(int x, int y, List<RingSpawn> spawns) {
        ObjectSpawn os = new ObjectSpawn(x, y, 0x25, 0x00, 0, false, 0);
        Sonic1RingInstance[] holder = new Sonic1RingInstance[1];
        withContext(new StubObjectServices(), () -> {
            holder[0] = new Sonic1RingInstance(os, spawns);
            holder[0].setServices(new StubObjectServices());
        });
        return holder[0];
    }

    @SuppressWarnings("unchecked")
    private static void setConstructionContext(ObjectServices svc) {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).set(svc);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearConstructionContext() {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static void forceState(Sonic1RingInstance ring, String stateName) {
        try {
            Field stateField = Sonic1RingInstance.class.getDeclaredField("state");
            stateField.setAccessible(true);
            Class<?> stateClass = stateField.getType();
            Object[] constants = stateClass.getEnumConstants();
            for (Object constant : constants) {
                if (constant.toString().equals(stateName)) {
                    stateField.set(ring, constant);
                    return;
                }
            }
            throw new IllegalArgumentException("Unknown state: " + stateName);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
