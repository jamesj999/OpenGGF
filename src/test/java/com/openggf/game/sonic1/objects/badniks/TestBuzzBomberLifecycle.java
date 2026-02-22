package com.openggf.game.sonic1.objects.badniks;

import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test: a flying Buzz Bomber must not be removed by the spawn
 * windowing system while it is still near the camera viewport.
 * <p>
 * Root cause: ObjectManager.Placement checks the <em>original spawn X</em>
 * against the camera window.  When the camera moves backward (delta&lt;0),
 * {@code refreshWindow()} rebuilds the active set and any spawn whose X is
 * beyond {@code cameraX + LOAD_AHEAD} (640) is dropped.  A Buzz Bomber that
 * has flown far from its spawn can still be on-screen when this happens.
 * <p>
 * Fix: Sonic1BuzzBomberBadnikInstance overrides {@code isPersistent()} to
 * return {@code true} while its <em>current</em> position is within a margin
 * of the camera, preventing premature removal.
 */
public class TestBuzzBomberLifecycle {

    /**
     * Simulates the key Buzz Bomber behaviour relevant to this bug:
     * <ul>
     *   <li>The object moves away from its spawn (flies left).</li>
     *   <li>{@code isPersistent()} returns true while near the camera (the fix).</li>
     * </ul>
     */
    private static final class FlyingObject extends AbstractObjectInstance {
        private int currentX;
        private final int margin;

        FlyingObject(ObjectSpawn spawn, int margin) {
            super(spawn, "FlyingTest");
            this.currentX = spawn.x();
            this.margin = margin;
        }

        void setCurrentX(int x) {
            this.currentX = x;
        }

        @Override
        public int getX() {
            return currentX;
        }

        @Override
        public int getY() {
            return spawn.y();
        }

        @Override
        public boolean isPersistent() {
            // Mirrors the Buzz Bomber fix: persist while near the camera viewport
            return !isDestroyed() && isOnScreenX(margin);
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {}
    }

    /**
     * Non-persistent control object – should be removed when spawn leaves window.
     */
    private static final class StaticObject extends AbstractObjectInstance {
        StaticObject(ObjectSpawn spawn) {
            super(spawn, "StaticTest");
        }

        @Override
        public int getX() {
            return spawn.x();
        }

        @Override
        public int getY() {
            return spawn.y();
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {}
    }

    private static final class TestRegistry implements ObjectRegistry {
        FlyingObject flyingInstance;
        private final int persistentObjectId;
        private final int flyingMargin;

        TestRegistry(int persistentObjectId, int flyingMargin) {
            this.persistentObjectId = persistentObjectId;
            this.flyingMargin = flyingMargin;
        }

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            if (spawn.objectId() == persistentObjectId) {
                flyingInstance = new FlyingObject(spawn, flyingMargin);
                return flyingInstance;
            }
            return new StaticObject(spawn);
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {}

        @Override
        public String getPrimaryName(int objectId) {
            return "Test";
        }
    }

    @Before
    public void setUp() {
        // Ensure Camera singleton exists with clean state (screen 320×224 from config)
        Camera.resetInstance();
    }

    /**
     * Core regression scenario: camera moves backward while a flying object
     * (Buzz Bomber) is still near the viewport.
     * <p>
     * Timeline:
     * <ol>
     *   <li>Camera at X=400 → spawn at X=1000 enters window (load-ahead 640).</li>
     *   <li>Object "flies left" to X=600 (on-screen).</li>
     *   <li>Camera backs up to X=200 → spawn at 1000 exceeds new windowEnd (840).</li>
     *   <li>Without fix: object removed. With fix: isPersistent() keeps it alive.</li>
     * </ol>
     */
    @Test
    public void testFlyingObjectSurvivesCameraBacktrack() {
        // Buzz Bomber-like object at X=1000
        ObjectSpawn flyingSpawn = new ObjectSpawn(1000, 400, 0x22, 0, 0, false, 0);
        // A static reference object at X=100 (always in window for these camera positions)
        ObjectSpawn staticSpawn = new ObjectSpawn(100, 400, 0x01, 0, 0, false, 0);

        List<ObjectSpawn> spawns = List.of(staticSpawn, flyingSpawn);
        TestRegistry registry = new TestRegistry(0x22, 160);
        ObjectManager manager = new ObjectManager(spawns, registry, 0, null, null);

        // --- Frame 1: Camera at X=400, windowEnd = 400+640 = 1040.
        //     Spawn at X=1000 ≤ 1040 → object created.
        Camera camera = Camera.getInstance();
        camera.setX((short) 400);
        manager.reset(400);
        manager.update(400, null, null, 1);
        assertEquals("Both objects should be active", 2, manager.getActiveObjects().size());

        // --- The flying object moves left to X=600 (simulates buzz bomber flight).
        registry.flyingInstance.setCurrentX(600);

        // --- Frame 2: Camera backs up to X=200.
        //     Window refreshes: windowEnd = 200+640 = 840.
        //     Spawn at X=1000 > 840 → spawn drops out of active set.
        //     But isPersistent() should save the object:
        //       cameraBounds = [200, ?, 520, ?]
        //       isOnScreenX(160) for X=600: 600 ≤ 520+160=680 → true
        camera.setX((short) 200);
        manager.update(200, null, null, 2);

        assertEquals("Flying object must survive camera backtrack while near viewport",
                2, manager.getActiveObjects().size());
        assertTrue("Flying instance specifically must still be active",
                manager.getActiveObjects().contains(registry.flyingInstance));
    }

    /**
     * Verify that a persistent flying object IS eventually removed when it
     * moves truly far from the camera (MarkObjGone equivalent).
     */
    @Test
    public void testFlyingObjectRemovedWhenFarFromCamera() {
        ObjectSpawn flyingSpawn = new ObjectSpawn(1000, 400, 0x22, 0, 0, false, 0);
        List<ObjectSpawn> spawns = List.of(flyingSpawn);
        TestRegistry registry = new TestRegistry(0x22, 160);
        ObjectManager manager = new ObjectManager(spawns, registry, 0, null, null);

        // Camera at X=400, spawn at 1000 in window → object created.
        Camera camera = Camera.getInstance();
        camera.setX((short) 400);
        manager.reset(400);
        manager.update(400, null, null, 1);
        assertEquals(1, manager.getActiveObjects().size());

        // Object flies left to X=600.
        registry.flyingInstance.setCurrentX(600);

        // Camera backs up far to X=0.
        //   cameraBounds = [0, ?, 320, ?]
        //   isOnScreenX(160) for X=600: 600 ≤ 320+160=480? NO (600>480) → not persistent
        //   Spawn 1000 > windowEnd 640 → spawn out of window, not persistent → removed.
        camera.setX((short) 0);
        manager.update(0, null, null, 2);

        assertEquals("Flying object should be removed when far from camera",
                0, manager.getActiveObjects().size());
    }

    /**
     * Control test: without the isPersistent override (margin=0, beyond screen),
     * a flying object at X=600 is removed when camera backs up enough that the
     * spawn leaves the window – even though the object is on-screen.
     * This demonstrates the bug that the fix addresses.
     */
    @Test
    public void testNonPersistentObjectRemovedOnCameraBacktrack() {
        // Use margin=0 so isPersistent effectively returns false when off exact screen
        // Actually use objectId 0x01 so the registry creates a StaticObject (not persistent)
        ObjectSpawn staticSpawn = new ObjectSpawn(1000, 400, 0x01, 0, 0, false, 0);
        List<ObjectSpawn> spawns = List.of(staticSpawn);
        TestRegistry registry = new TestRegistry(0x22, 160); // 0x22 doesn't match 0x01
        ObjectManager manager = new ObjectManager(spawns, registry, 0, null, null);

        Camera camera = Camera.getInstance();
        camera.setX((short) 400);
        manager.reset(400);
        manager.update(400, null, null, 1);
        assertEquals("Object should be created", 1, manager.getActiveObjects().size());

        // Camera backs up → spawn leaves window → non-persistent object removed
        camera.setX((short) 200);
        manager.update(200, null, null, 2);

        assertEquals("Non-persistent object should be removed when spawn leaves window",
                0, manager.getActiveObjects().size());
    }
}
