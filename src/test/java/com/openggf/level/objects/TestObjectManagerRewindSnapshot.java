package com.openggf.level.objects;

import com.openggf.game.RuntimeManager;
import com.openggf.game.rewind.RewindSnapshottable;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.EngineContext;
import com.openggf.graphics.GLCommand;
import com.openggf.game.PlayableEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-style tests for {@link ObjectManager#rewindSnapshottable()}.
 *
 * <p>Exercises the capture/restore round-trip for placement-managed objects.
 * Uses a minimal {@link ObjectRegistry} that creates simple
 * {@link AbstractObjectInstance} subclasses whose mutable fields can be
 * asserted after restore.
 */
class TestObjectManagerRewindSnapshot {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    // ------------------------------------------------------------------
    // Minimal concrete object class for testing
    // ------------------------------------------------------------------

    /** Minimal subclass with a trackable "moved" position. */
    private static final class TrackableObject extends AbstractObjectInstance {

        TrackableObject(ObjectSpawn spawn) {
            super(spawn, "TrackableObject");
            updateDynamicSpawn(spawn.x(), spawn.y());
        }

        /** Simulates moving the object to a new position. */
        void moveTo(int x, int y) {
            updateDynamicSpawn(x, y);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // no-op
        }
    }

    // ------------------------------------------------------------------
    // Minimal registry
    // ------------------------------------------------------------------

    private static final class TrackingRegistry implements ObjectRegistry {
        final Map<ObjectSpawn, TrackableObject> instances = new IdentityHashMap<>();
        int createCount;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            createCount++;
            TrackableObject obj = new TrackableObject(spawn);
            instances.put(spawn, obj);
            return obj;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {}

        @Override
        public String getPrimaryName(int objectId) {
            return "TrackableObject";
        }

        @Override
        public ObjectSlotLayout objectSlotLayout() {
            return ObjectSlotLayout.SONIC_2;
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ObjectSpawn spawn(int x, int y) {
        return new ObjectSpawn(x, y, 0x01, 0, 0, false, 0);
    }

    private static ObjectManager makeManager(List<ObjectSpawn> spawns, TrackingRegistry registry) {
        return new ObjectManager(spawns, registry, 0, null, null);
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @Test
    void captureRestoreRoundTrip_singleObject() {
        ObjectSpawn sp = spawn(100, 200);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);

        // Advance one frame to materialize the spawn
        manager.reset(0);
        manager.update(0, null, null, 1);

        // Verify object was created
        assertEquals(1, registry.createCount);
        TrackableObject original = registry.instances.get(sp);
        assertNotNull(original);

        // Move object and capture snapshot
        original.moveTo(300, 400);
        RewindSnapshottable<ObjectManagerSnapshot> snap = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshot = snap.capture();

        // Verify snapshot has one slot entry with correct position
        assertEquals(1, snapshot.slots().size());
        ObjectManagerSnapshot.PerSlotEntry entry = snapshot.slots().get(0);
        assertEquals(300, entry.state().dynamicSpawnX());
        assertEquals(400, entry.state().dynamicSpawnY());
        assertTrue(entry.state().hasDynamicSpawn());

        // Advance the object further
        original.moveTo(999, 999);
        assertEquals(999, original.getX());

        // Restore
        snap.restore(snapshot);

        // After restore, find the restored instance
        Collection<ObjectInstance> active = manager.getActiveObjects();
        assertEquals(1, active.size());
        ObjectInstance restored = active.iterator().next();
        assertNotNull(restored);

        // The restored object should be at the captured position (300, 400)
        assertEquals(300, restored.getX());
        assertEquals(400, restored.getY());
    }

    @Test
    void captureRestoreRoundTrip_scalarsPreserved() {
        ObjectSpawn sp = spawn(0, 0);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);

        manager.reset(0);
        // Run 5 frames so counters advance
        for (int i = 1; i <= 5; i++) {
            manager.update(0, null, null, i);
        }

        int fc = manager.getFrameCounter();
        int vc = manager.getVblaCounter();

        RewindSnapshottable<ObjectManagerSnapshot> snap = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshot = snap.capture();

        assertEquals(fc, snapshot.frameCounter());
        assertEquals(vc, snapshot.vblaCounter());

        // Run more frames to advance counters
        for (int i = 6; i <= 10; i++) {
            manager.update(0, null, null, i);
        }
        assertNotEquals(fc, manager.getFrameCounter());

        // Restore
        snap.restore(snapshot);
        assertEquals(fc, manager.getFrameCounter());
        assertEquals(vc, manager.getVblaCounter());
    }

    @Test
    void captureNoMutateRestoreIsIdempotent() {
        ObjectSpawn sp = spawn(50, 60);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);

        manager.reset(0);
        manager.update(0, null, null, 1);

        RewindSnapshottable<ObjectManagerSnapshot> snap = manager.rewindSnapshottable();
        ObjectManagerSnapshot s1 = snap.capture();
        snap.restore(s1);
        ObjectManagerSnapshot s2 = snap.capture();

        // Scalars should match after no-mutation restore cycle
        assertEquals(s1.frameCounter(), s2.frameCounter());
        assertEquals(s1.vblaCounter(), s2.vblaCounter());
        assertEquals(s1.slots().size(), s2.slots().size());
    }

    @Test
    void restoreRecreatesObjectAtCapturedPosition() {
        ObjectSpawn sp = spawn(10, 20);
        TrackingRegistry registry = new TrackingRegistry();
        ObjectManager manager = makeManager(List.of(sp), registry);

        manager.reset(0);
        manager.update(0, null, null, 1);

        // Capture while object is at spawn position
        RewindSnapshottable<ObjectManagerSnapshot> snap = manager.rewindSnapshottable();
        ObjectManagerSnapshot snapshotAtSpawn = snap.capture();
        assertEquals(1, snapshotAtSpawn.slots().size());

        // Restore — should recreate the object at spawn position
        snap.restore(snapshotAtSpawn);

        Collection<ObjectInstance> active = manager.getActiveObjects();
        assertEquals(1, active.size(), "Object should be present after restore");
        ObjectInstance restored = active.iterator().next();
        assertEquals(10, restored.getX(), "X should match captured spawn X");
        assertEquals(20, restored.getY(), "Y should match captured spawn Y");
    }
}
