package com.openggf.game.sonic1.objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1LavaGeyserOutOfRange {

    private static final class CountingMakerRegistry implements ObjectRegistry {
        int createCalls;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            createCalls++;
            return new Sonic1LavaGeyserMakerObjectInstance(spawn);
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "LavaGeyserMaker";
        }
    }

    @Before
    public void setUp() {
        RuntimeManager.createGameplay();
        GameServices.camera().resetState();
        Camera camera = GameServices.camera();
        camera.setX((short) 0);
        camera.setY((short) 0);
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @After
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void makerIsNotRecreatedEachFrameWhenOnlyYIsOffscreen() {
        ObjectSpawn spawn = new ObjectSpawn(0x140, 0x700, 0x4C, 1, 0, false, 0);
        CountingMakerRegistry registry = new CountingMakerRegistry();
        ObjectManager manager = new ObjectManager(List.of(spawn), registry, 0, null, null);

        manager.reset(0);
        for (int i = 0; i < 5; i++) {
            manager.update(0, null, null, i + 1);
        }

        assertEquals("Maker should be created once and persist in-range on X", 1, registry.createCalls);
        assertEquals("Maker should remain active (no Y-based out_of_range deletion)", 1,
                manager.getActiveObjects().size());
    }

    @Test
    public void makerDeletesWhenXIsOutOfRange() {
        ObjectSpawn farSpawn = new ObjectSpawn(0x3E8, 0x700, 0x4C, 1, 0, false, 0);
        Sonic1LavaGeyserMakerObjectInstance maker = new Sonic1LavaGeyserMakerObjectInstance(farSpawn);
        maker.setServices(new TestObjectServices().withCamera(GameServices.camera()));

        maker.update(1, null);

        assertTrue("Maker should delete when outside ROM out_of_range X window", maker.isDestroyed());
    }

    /**
     * Regression: lavafall (subtype != 0) third piece must NOT re-run initializeHead().
     * Without the fix, each frame's ensureInitialized() on the third piece would spawn
     * another body + third piece, cascading exponentially and crashing the engine.
     */
    @Test
    public void lavafallThirdPieceDoesNotCascadeSpawn() {
        // Place camera so the geyser is in range
        GameServices.camera().setX((short) 0x100);
        GameServices.camera().setY((short) 0x300);

        // Track all objects added via addDynamicObject
        List<ObjectInstance> spawnedObjects = new ArrayList<>();
        ObjectSpawn lavafallSpawn = new ObjectSpawn(0x180, 0x400, 0x4C, 1, 0, false, 0);
        CountingMakerRegistry registry = new CountingMakerRegistry();
        ObjectManager manager = new ObjectManager(List.of(lavafallSpawn), registry, 0, null, null);
        manager.reset(0);

        // Run enough frames for the maker to trigger and the geyser to initialize.
        // Frame 0: maker spawns (routine 2, timer=0 → expires immediately)
        // Frame 1: maker at routine 4 (ChkType) → routine 6 (lavafall)
        // Frame 2: maker at routine 6 (MakeLava) → spawns LavaGeyser head
        // Frame 3: LavaGeyser head initializeHead() → spawns body + third piece
        // Frame 4+: if bug present, third piece would cascade-spawn more objects each frame
        for (int i = 0; i < 10; i++) {
            manager.update(0, null, null, i + 1);
        }

        // Count active objects: should be bounded. With the bug, this would be 20+
        // and growing exponentially. Correct count: 1 maker + 1 head + 1 body + 1 third = 4
        // (some may have been destroyed by now, but total should be small)
        int totalObjects = manager.getActiveObjects().size();
        assertTrue("Lavafall should not cascade-spawn objects (found " + totalObjects + ")",
                totalObjects <= 6);
    }

    @Test
    public void geyserPiecePersistenceUsesXRangeNotYVisibility() {
        ObjectSpawn bodySpawn = new ObjectSpawn(0x180, 0x700, 0x4D, 0, 0, false, 0);
        Sonic1LavaGeyserObjectInstance body = new Sonic1LavaGeyserObjectInstance(
                bodySpawn, Sonic1LavaGeyserObjectInstance.Role.BODY, null, null, false);
        body.setServices(new TestObjectServices().withCamera(GameServices.camera()));

        assertTrue("Body piece should remain persistent when X is in range, even if Y is off-screen",
                body.isPersistent());

        GameServices.camera().setX((short) 0x600);

        assertFalse("Body piece should become non-persistent when X leaves out_of_range window",
                body.isPersistent());
    }
}
