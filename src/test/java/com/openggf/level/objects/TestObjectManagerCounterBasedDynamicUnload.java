package com.openggf.level.objects;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.RuntimeManager;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestObjectManagerCounterBasedDynamicUnload {

    private ObjectManager objectManager;

    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
        GameServices.camera().resetState();
        GameServices.camera().setX((short) 0);
        GameServices.camera().setY((short) 0);

        ObjectServices services = new StubObjectServices() {
            @Override
            public com.openggf.camera.Camera camera() {
                return GameServices.camera();
            }
        };

        objectManager = new ObjectManager(List.of(), new ObjectRegistry() {
            @Override
            public ObjectInstance create(ObjectSpawn spawn) {
                return null;
            }

            @Override
            public void reportCoverage(List<ObjectSpawn> spawns) {
            }

            @Override
            public String getPrimaryName(int objectId) {
                return "Test";
            }
        }, 0, null, null, null, GameServices.camera(), services);
        objectManager.enableCounterBasedRespawn();
        objectManager.reset(0);
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void counterBasedUpdateRemovesOutOfRangeDynamicObjects() {
        TestDynamicObject object = new TestDynamicObject(new ObjectSpawn(0x2000, 0x0100, 0x01, 0, 0, false, 0));
        objectManager.addDynamicObject(object);

        assertTrue(objectManager.getActiveObjects().contains(object), "Sanity check: dynamic object should be tracked before update");

        objectManager.update(0, null, List.of(), 1);

        assertFalse(objectManager.getActiveObjects().contains(object),
                "Counter-based S1 updates should unload out-of-range dynamic objects to preserve slot parity");
    }

    @Test
    public void counterBasedUpdateIgnoresSpawnlessFallbackDynamicsForOutOfRangeChecks() {
        SpawnlessFallbackObject object = new SpawnlessFallbackObject();
        objectManager.addDynamicObject(object);

        assertTrue(objectManager.getActiveObjects().contains(object),
                "Sanity check: spawnless fallback object should be tracked before update");

        assertDoesNotThrow(() -> objectManager.update(0, null, List.of(), 1),
                "Fallback dynamic objects without spawn-backed positions should not enter S1 out_of_range unload");

        assertTrue(objectManager.getActiveObjects().contains(object),
                "Spawnless fallback objects should remain active when counter-based unload only applies to spawned objects");
        assertTrue(object.updated, "Fallback object should still be updated normally");
    }

    @Test
    public void counterBasedObjPosLoadDoesNotExecuteFreshlyLoadedObjectsUntilNextFrame() {
        ObjectSpawn spawn = new ObjectSpawn(0x0100, 0x0100, 0x31, 0, 0, false, 0);
        TrackingRegistry registry = new TrackingRegistry();
        objectManager = new ObjectManager(List.of(spawn), registry, 0, null, null,
                null, GameServices.camera(), new StubObjectServices() {
                    @Override
                    public com.openggf.camera.Camera camera() {
                        return GameServices.camera();
                    }
                });
        objectManager.enableCounterBasedRespawn();
        objectManager.reset(0);

        objectManager.update(0, null, List.of(), 1);

        TrackingObjectInstance instance = registry.instance;
        assertNotNull(instance, "ObjPosLoad-equivalent pass should instantiate in-window spawns");
        assertEquals(0, instance.updateCount,
                "Freshly loaded S1 counter-based objects should not execute until the next ExecuteObjects pass");

        objectManager.update(0, null, List.of(), 2);

        assertEquals(1, instance.updateCount,
                "Object should execute on the following frame once it is present in the slot-ordered pass");
    }

    private static final class TestDynamicObject extends AbstractObjectInstance {
        private TestDynamicObject(ObjectSpawn spawn) {
            super(spawn, "TestDynamicObject");
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }

    private static final class SpawnlessFallbackObject implements ObjectInstance {
        private boolean updated;

        @Override
        public ObjectSpawn getSpawn() {
            return null;
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            updated = true;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public void appendDebugRenderCommands(DebugRenderContext ctx) {
        }
    }

    private static final class TrackingRegistry implements ObjectRegistry {
        private TrackingObjectInstance instance;

        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            instance = new TrackingObjectInstance(spawn);
            return instance;
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "Tracking";
        }
    }

    private static final class TrackingObjectInstance extends AbstractObjectInstance {
        private int updateCount;

        private TrackingObjectInstance(ObjectSpawn spawn) {
            super(spawn, "TrackingObject");
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            updateCount++;
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
