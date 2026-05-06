package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.game.PlayableEntity;
import com.openggf.game.rewind.GenericRewindEligibility;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the default {@link AbstractObjectInstance#captureRewindState()} /
 * {@link AbstractObjectInstance#restoreRewindState(PerObjectRewindSnapshot)} round-trip.
 *
 * <p>Uses a minimal concrete subclass ({@code TestObject}) with no-op overrides.
 * The constructor does NOT go through {@link ObjectManager}, so
 * {@code services()} is intentionally unused here — only the base-class field
 * surface is exercised.
 */
class TestAbstractObjectInstanceRewindCapture {

    // ------------------------------------------------------------------
    // Minimal concrete subclass
    // ------------------------------------------------------------------

    private static final class TestObject extends AbstractObjectInstance {

        TestObject(ObjectSpawn spawn) {
            super(spawn, "TestObject");
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ObjectSpawn spawn(int x, int y) {
        return new ObjectSpawn(x, y, 0x01, 0, 0, false, 0);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void roundTrip_defaultFieldValues() {
        TestObject obj = new TestObject(spawn(100, 200));

        PerObjectRewindSnapshot snap = obj.captureRewindState();

        // Default state immediately after construction
        assertFalse(snap.destroyed());
        assertFalse(snap.destroyedRespawnable());
        assertFalse(snap.hasDynamicSpawn());
        assertFalse(snap.preUpdateValid());
        assertEquals(-1, snap.preUpdateCollisionFlags());
        assertFalse(snap.skipTouchThisFrame());
        assertTrue(snap.solidContactFirstFrame(), "solidContactFirstFrame starts true");
        assertEquals(-1, snap.slotIndex(), "slotIndex starts -1 when not through ObjectManager");
        assertEquals(-1, snap.respawnStateIndex());
    }

    @Test
    void roundTrip_destroyedFlag() {
        TestObject obj = new TestObject(spawn(0, 0));
        obj.setDestroyed(true);

        PerObjectRewindSnapshot snap = obj.captureRewindState();
        assertTrue(snap.destroyed());

        // mutate further
        obj.setDestroyed(false);
        assertFalse(obj.isDestroyed());

        // restore
        obj.restoreRewindState(snap);
        assertTrue(obj.isDestroyed());
    }

    @Test
    void roundTrip_destroyedRespawnableFlag() {
        TestObject obj = new TestObject(spawn(0, 0));
        obj.setDestroyedByOffscreen();

        PerObjectRewindSnapshot snap = obj.captureRewindState();
        assertTrue(snap.destroyed());
        assertTrue(snap.destroyedRespawnable());

        obj.setDestroyed(false); // clears both
        assertFalse(obj.isDestroyedRespawnable());

        obj.restoreRewindState(snap);
        assertTrue(obj.isDestroyed());
        assertTrue(obj.isDestroyedRespawnable());
    }

    @Test
    void roundTrip_dynamicSpawnPosition() {
        TestObject obj = new TestObject(spawn(50, 60));
        // updateDynamicSpawn is protected; exercise it via a small local subclass that
        // exposes it, or access via reflective call. Use reflection to keep test lean.
        // Actually updateDynamicSpawn is protected — create a helper subclass.
        // We instead use a positionable subclass.
        TestObjectWithPosition posObj = new TestObjectWithPosition(spawn(50, 60));
        posObj.moveTo(300, 400);

        PerObjectRewindSnapshot snap = posObj.captureRewindState();
        assertTrue(snap.hasDynamicSpawn());
        assertEquals(300, snap.dynamicSpawnX());
        assertEquals(400, snap.dynamicSpawnY());

        posObj.moveTo(999, 999);
        assertEquals(999, posObj.getX());

        posObj.restoreRewindState(snap);
        assertEquals(300, posObj.getX());
        assertEquals(400, posObj.getY());
    }

    @Test
    void roundTrip_slotAndRespawnIndex() {
        TestObject obj = new TestObject(spawn(0, 0));
        obj.setSlotIndex(42);
        obj.setRespawnStateIndex(7);

        PerObjectRewindSnapshot snap = obj.captureRewindState();
        assertEquals(42, snap.slotIndex());
        assertEquals(7, snap.respawnStateIndex());

        obj.setSlotIndex(99);
        obj.setRespawnStateIndex(99);

        obj.restoreRewindState(snap);
        assertEquals(42, obj.getSlotIndex());
        assertEquals(7, obj.getRespawnStateIndex());
    }

    @Test
    void roundTrip_skipTouchAndSolidContactFlags() {
        TestObject obj = new TestObject(spawn(0, 0));
        obj.setSkipTouchThisFrame(true);

        PerObjectRewindSnapshot snap = obj.captureRewindState();
        assertTrue(snap.skipTouchThisFrame());
        // solidContactFirstFrame is true by default at construction
        assertTrue(snap.solidContactFirstFrame());

        // Simulate a frame passing — clears both flags
        obj.snapshotPreUpdatePosition();
        assertFalse(obj.isSkipTouchThisFrame());
        assertFalse(obj.isSkipSolidContactThisFrame());

        obj.restoreRewindState(snap);
        assertTrue(obj.isSkipTouchThisFrame());
        assertTrue(obj.isSkipSolidContactThisFrame());
    }

    @Test
    void captureAndRestoreIsSelfConsistentWithNoMutation() {
        TestObject obj = new TestObject(spawn(10, 20));
        obj.setSlotIndex(5);

        PerObjectRewindSnapshot snap1 = obj.captureRewindState();
        obj.restoreRewindState(snap1);
        PerObjectRewindSnapshot snap2 = obj.captureRewindState();

        // Both snapshots should agree on every field
        assertEquals(snap1.destroyed(), snap2.destroyed());
        assertEquals(snap1.hasDynamicSpawn(), snap2.hasDynamicSpawn());
        assertEquals(snap1.slotIndex(), snap2.slotIndex());
        assertEquals(snap1.solidContactFirstFrame(), snap2.solidContactFirstFrame());
    }

    // ------------------------------------------------------------------
    // Helper subclass that exposes updateDynamicSpawn
    // ------------------------------------------------------------------

    private static final class TestObjectWithPosition extends AbstractObjectInstance {

        TestObjectWithPosition(ObjectSpawn spawn) {
            super(spawn, "TestObjectWithPosition");
        }

        void moveTo(int x, int y) {
            updateDynamicSpawn(x, y);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }

    private static final class TestObjectWithGenericState extends AbstractObjectInstance {
        private int phase;

        TestObjectWithGenericState(ObjectSpawn spawn) {
            super(spawn, "TestObjectWithGenericState");
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }

    @Test
    void eligibleClassCapturesAndRestoresGenericSidecar() {
        GenericRewindEligibility.registerForTestOrMigration(TestObjectWithGenericState.class);
        try {
            TestObjectWithGenericState obj = new TestObjectWithGenericState(spawn(0, 0));
            obj.phase = 7;

            PerObjectRewindSnapshot snap = obj.captureRewindState();
            assertNotNull(snap.genericState());

            obj.phase = 2;
            obj.restoreRewindState(snap);

            assertEquals(7, obj.phase);
        } finally {
            GenericRewindEligibility.clearForTest();
        }
    }
}
