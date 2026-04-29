package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAizGiantRideVineObjectInstance {

    @BeforeEach
    void setUp() {
        GraphicsManager.getInstance().initHeadless();
    }

    @AfterEach
    void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    void reservesRomChildSlotsAfterParentSlot() {
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return holder[0];
            }
        };
        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0x1D00);
        when(camera.getY()).thenReturn((short) 0x0300);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);
        ObjectManager manager = new ObjectManager(
                List.of(), new Sonic3kObjectRegistry(), 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;

        ObjectSpawn vineSpawn = new ObjectSpawn(0x1DE0, 0x0360, 0x0C, 0x09, 0, false, 0);
        AizGiantRideVineObjectInstance vine = new AizGiantRideVineObjectInstance(vineSpawn);
        manager.addDynamicObjectAtSlot(vine, 28);
        manager.addDynamicObjectAtSlot(new MarkerObject(new ObjectSpawn(0x1DE0, 0x0360, 0, 0, 0, false, 0)), 29);

        manager.update(0x1D00, null, null, 1);

        assertEquals(40, manager.allocateSlotAfter(vine.getSlotIndex()),
                "ROM Obj_AIZGiantRideVine allocates first/segment/handle child SST slots after the parent");
    }

    private static final class MarkerObject extends AbstractObjectInstance {
        private MarkerObject(ObjectSpawn spawn) {
            super(spawn, "Marker");
        }

        @Override
        public void update(int frameCounter, com.openggf.game.PlayableEntity player) {
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }
}
