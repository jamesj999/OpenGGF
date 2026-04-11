package com.openggf.game.sonic1.objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @BeforeEach
    public void setUp() {
        RuntimeManager.createGameplay();
        GameServices.camera().resetState();
        Camera camera = GameServices.camera();
        camera.setX((short) 0);
        camera.setY((short) 0);
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
    }

    @AfterEach
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

        assertEquals(1, registry.createCalls, "Maker should be created once and persist in-range on X");
        assertEquals(1, manager.getActiveObjects().size(), "Maker should remain active (no Y-based out_of_range deletion)");
    }

    @Test
    public void makerDeletesWhenXIsOutOfRange() {
        ObjectSpawn farSpawn = new ObjectSpawn(0x3E8, 0x700, 0x4C, 1, 0, false, 0);
        Sonic1LavaGeyserMakerObjectInstance maker = new Sonic1LavaGeyserMakerObjectInstance(farSpawn);
        maker.setServices(new TestObjectServices().withCamera(GameServices.camera()));

        maker.update(1, null);

        assertTrue(maker.isDestroyed(), "Maker should delete when outside ROM out_of_range X window");
    }

    /**
     * Regression: lavafall (subtype != 0) third piece must NOT re-run initializeHead().
     * Without the fix, each frame's ensureInitialized() on the third piece would spawn
     * another body + third piece, cascading exponentially and crashing the engine.
     *
     * Tests the LavaGeyser HEAD directly with a live ObjectManager, bypassing the
     * maker (which requires a non-null player sprite for proximity checks).
     */
    @Test
    public void lavafallThirdPieceDoesNotCascadeSpawn() {
        GameServices.camera().setX((short) 0x100);
        GameServices.camera().setY((short) 0x300);

        // Build an ObjectManager with services wired back to itself so that
        // services().objectManager().addDynamicObject() actually works.
        CountingMakerRegistry registry = new CountingMakerRegistry();
        final ObjectManager[] managerRef = new ObjectManager[1];
        TestObjectServices services = new TestObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return managerRef[0];
            }
        };
        services.withCamera(GameServices.camera());
        ObjectManager manager = new ObjectManager(
                List.of(), registry, 0, null, null,
                null, GameServices.camera(), services);
        managerRef[0] = manager;

        // Directly add a lavafall HEAD (subtype 1) â€” this is what the maker spawns.
        // Its first update() calls initializeHead() which spawns body + third piece.
        ObjectSpawn headSpawn = new ObjectSpawn(0x180, 0x400, 0x4D, 1, 0, false, 0);
        Sonic1LavaGeyserObjectInstance head = new Sonic1LavaGeyserObjectInstance(
                headSpawn, Sonic1LavaGeyserObjectInstance.Role.HEAD,
                null, null, false);
        manager.addDynamicObject(head);

        // Run 10 frames. With the bug each frame adds 2+ objects exponentially.
        // Without the bug: 1 head + 1 body + 1 third piece = 3 total children,
        // and no further growth.
        for (int i = 0; i < 10; i++) {
            manager.update(0, null, null, i + 1);
        }

        int totalObjects = manager.getActiveObjects().size();
        assertTrue(totalObjects <= 5, "Lavafall should not cascade-spawn objects (found " + totalObjects
                + ", expected <= 5)");
    }

    @Test
    public void geyserPiecePersistenceUsesXRangeNotYVisibility() {
        ObjectSpawn bodySpawn = new ObjectSpawn(0x180, 0x700, 0x4D, 0, 0, false, 0);
        Sonic1LavaGeyserObjectInstance body = new Sonic1LavaGeyserObjectInstance(
                bodySpawn, Sonic1LavaGeyserObjectInstance.Role.BODY, null, null, false);
        body.setServices(new TestObjectServices().withCamera(GameServices.camera()));

        assertTrue(body.isPersistent(), "Body piece should remain persistent when X is in range, even if Y is off-screen");

        GameServices.camera().setX((short) 0x600);

        assertFalse(body.isPersistent(), "Body piece should become non-persistent when X leaves out_of_range window");
    }
}


