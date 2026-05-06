package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestObjectManagerVerticalPlacement {

    @Test
    void nonCounterPlacementDefersObjectUntilCameraYWindowIncludesSpawn() {
        Camera camera = new Camera(SonicConfigurationService.getInstance());
        camera.setY((short) 0x0923);

        ObjectSpawn spawn = new ObjectSpawn(0x17C0, 0x0860, 0x41, 0, 0, false, 0x0860);
        ObjectManager manager = new ObjectManager(
                List.of(spawn),
                new TestRegistry(),
                -1,
                null,
                null,
                null,
                camera,
                new StubObjectServices() {
                    @Override
                    public Camera camera() {
                        return camera;
                    }
                });

        manager.reset(0x186B);
        assertEquals(0, manager.getActiveObjects().size());

        camera.setY((short) 0x08F6);
        manager.update(0x181F, null, List.of(), 0, false);

        assertEquals(1, manager.getActiveObjects().size());
    }

    @Test
    void signedRawYWordBypassesVerticalFilterLikeRom() {
        ObjectSpawn alwaysLoad = new ObjectSpawn(0x17C0, 0x0860, 0x41, 0, 0, false, 0x8860);
        ObjectSpawn gated = new ObjectSpawn(0x17C0, 0x0860, 0x41, 0, 0, false, 0x0860);

        assertTrue(ObjectManager.isNonCounterSpawnVerticallyEligible(alwaysLoad, 0x0923, 0));
        assertFalse(ObjectManager.isNonCounterSpawnVerticallyEligible(gated, 0x0923, 0));
        assertTrue(ObjectManager.isNonCounterSpawnVerticallyEligible(gated, 0x08F6, 0));
    }

    @Test
    void negativeCameraMinYStillAppliesNonWrappingVerticalBand() {
        ObjectSpawn mgzBridge = new ObjectSpawn(0x04F0, 0x0834, 0x0F, 0x12, 0, false, 0x0834);

        assertFalse(ObjectManager.isNonCounterSpawnVerticallyEligible(mgzBridge, 0x045D, 0xFF00, 0x1000));
        assertFalse(ObjectManager.isNonCounterSpawnVerticallyEligible(mgzBridge, 0x0602, 0xFF00, 0x1000));
        assertTrue(ObjectManager.isNonCounterSpawnVerticallyEligible(mgzBridge, 0x06B0, 0xFF00, 0x1000));
    }

    @Test
    void negativeCameraMinYUsesSplitBandOnlyAcrossWrapBoundary() {
        ObjectSpawn nearTop = new ObjectSpawn(0x04F0, 0x0180, 0x0F, 0, 0, false, 0x0180);
        ObjectSpawn nearBottom = new ObjectSpawn(0x04F0, 0x0F90, 0x0F, 0, 0, false, 0x0F90);
        ObjectSpawn middle = new ObjectSpawn(0x04F0, 0x0834, 0x0F, 0, 0, false, 0x0834);

        assertTrue(ObjectManager.isNonCounterSpawnVerticallyEligible(nearTop, 0x0040, 0xFF00, 0x1000));
        assertTrue(ObjectManager.isNonCounterSpawnVerticallyEligible(nearBottom, 0x0040, 0xFF00, 0x1000));
        assertFalse(ObjectManager.isNonCounterSpawnVerticallyEligible(middle, 0x0040, 0xFF00, 0x1000));
    }

    private static final class TestRegistry implements ObjectRegistry {
        @Override
        public ObjectInstance create(ObjectSpawn spawn) {
            return new DummyObject(spawn);
        }

        @Override
        public void reportCoverage(List<ObjectSpawn> spawns) {
        }

        @Override
        public String getPrimaryName(int objectId) {
            return "Dummy";
        }

        @Override
        public ObjectSlotLayout objectSlotLayout() {
            return ObjectSlotLayout.SONIC_3K;
        }
    }

    private static final class DummyObject extends AbstractObjectInstance {
        private DummyObject(ObjectSpawn spawn) {
            super(spawn, "Dummy");
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }
    }
}
