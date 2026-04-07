package com.openggf.level.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.camera.Camera;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TestObjectManagerChildSlotAllocation {

    @Before
    public void setUp() {
        GraphicsManager.getInstance().initHeadless();
    }

    @After
    public void tearDown() {
        GraphicsManager.getInstance().resetState();
    }

    @Test
    public void childSpawnedDuringUpdate_allocatesAfterParentSlot() {
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
                List.of(), null, 0, null, null,
                GraphicsManager.getInstance(), camera, services);
        holder[0] = manager;

        ParentObject parent = new ParentObject(new ObjectSpawn(0, 0, 0, 0, 0, false, 0));
        manager.addDynamicObjectAtSlot(parent, 40);

        manager.update(0, null, null, 1);

        assertNotNull(parent.child);
        assertTrue(parent.child.getSlotIndex() > parent.getSlotIndex());
        assertEquals(1, parent.child.updateCount);
    }

    private static final class ParentObject extends AbstractObjectInstance {
        private ChildObject child;

        private ParentObject(ObjectSpawn spawn) {
            super(spawn, "Parent");
        }

        @Override
        public void update(int frameCounter, PlayableEntity player) {
            if (child == null) {
                child = spawnChild(() -> new ChildObject(buildSpawnAt(spawn.x(), spawn.y())));
            }
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
        }
    }

    private static final class ChildObject extends AbstractObjectInstance {
        private int updateCount;

        private ChildObject(ObjectSpawn spawn) {
            super(spawn, "Child");
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
