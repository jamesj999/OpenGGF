package com.openggf.game.sonic1.objects;

import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.ObjectSpawn;

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
        Camera.getInstance().resetState();
        Camera camera = Camera.getInstance();
        camera.setX((short) 0);
        camera.setY((short) 0);
        AbstractObjectInstance.updateCameraBounds(0, 0, 320, 224, 0);
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

        maker.update(1, null);

        assertTrue("Maker should delete when outside ROM out_of_range X window", maker.isDestroyed());
    }

    @Test
    public void geyserPiecePersistenceUsesXRangeNotYVisibility() {
        ObjectSpawn bodySpawn = new ObjectSpawn(0x180, 0x700, 0x4D, 0, 0, false, 0);
        Sonic1LavaGeyserObjectInstance body = new Sonic1LavaGeyserObjectInstance(
                bodySpawn, Sonic1LavaGeyserObjectInstance.Role.BODY, null, null, false);

        assertTrue("Body piece should remain persistent when X is in range, even if Y is off-screen",
                body.isPersistent());

        Camera.getInstance().setX((short) 0x600);

        assertFalse("Body piece should become non-persistent when X leaves out_of_range window",
                body.isPersistent());
    }
}
