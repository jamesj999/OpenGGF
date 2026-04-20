package com.openggf.game.sonic1.objects;

import com.openggf.camera.Camera;
import com.openggf.game.OscillationManager;
import com.openggf.game.solid.SolidCheckpointBatch;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.StubObjectServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestSonic1MovingBlockObjectInstance {

    @BeforeEach
    void setUp() {
        OscillationManager.resetForSonic1();
    }

    @Test
    void movingBlockResolvesCheckpointAgainstUpdatedPosition() throws Exception {
        ProbeMovingBlock block = new ProbeMovingBlock(new ObjectSpawn(0x1110, 0x0458, 0x52, 0x01, 0, false, 0));
        ObjectManager manager = buildManager(block);
        TestPlayableSprite player = new TestPlayableSprite();

        setPrivateInt(block, "x", 0x1140);

        int expectedUpdatedX = 0x1110;

        manager.update(0, player, List.of(), 0, false, true, false);

        assertEquals(expectedUpdatedX, block.checkpointX,
                "Moving block should run checkpoint collision against the post-MBlock_Move X");
        assertEquals(expectedUpdatedX, block.getX(),
                "Moving block update should still end at the moved X");
    }

    private static void setPrivateInt(Object instance, String fieldName, int value) throws Exception {
        Field field = instance.getClass().getSuperclass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(instance, value);
    }

    private static ObjectManager buildManager(ProbeMovingBlock block) {
        ObjectManager[] holder = new ObjectManager[1];
        ObjectServices services = new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return holder[0];
            }
        };

        Camera camera = mock(Camera.class);
        when(camera.getX()).thenReturn((short) 0);
        when(camera.getY()).thenReturn((short) 0);
        when(camera.getWidth()).thenReturn((short) 320);
        when(camera.getHeight()).thenReturn((short) 224);
        when(camera.isVerticalWrapEnabled()).thenReturn(false);

        ObjectManager manager = new ObjectManager(
                List.of(), null, 0, null, null, null, camera, services);
        holder[0] = manager;
        manager.addDynamicObject(block);
        return manager;
    }

    private static final class ProbeMovingBlock extends Sonic1MovingBlockObjectInstance {
        private int checkpointX = Integer.MIN_VALUE;

        private ProbeMovingBlock(ObjectSpawn spawn) {
            super(spawn);
        }

        @Override
        protected SolidCheckpointBatch checkpointAll() {
            checkpointX = getX();
            return new SolidCheckpointBatch(this, Map.of());
        }
    }
}
