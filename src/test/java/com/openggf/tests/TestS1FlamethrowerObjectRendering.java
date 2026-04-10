package com.openggf.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.objects.Sonic1FlamethrowerObjectInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.render.PatternSpriteRenderer;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestS1FlamethrowerObjectRendering {

    private Field objectRenderManagerField;
    private ObjectRenderManager originalRenderManager;
    private GameRuntime originalRuntime;
    private LevelManager runtimeLevelManager;

    @Before
    public void setUp() throws Exception {
        originalRuntime = RuntimeManager.getCurrent();
        RuntimeManager.destroyCurrent();
        runtimeLevelManager = RuntimeManager.createGameplay().getLevelManager();
        objectRenderManagerField = LevelManager.class.getDeclaredField("objectRenderManager");
        objectRenderManagerField.setAccessible(true);
        originalRenderManager = (ObjectRenderManager) objectRenderManagerField.get(runtimeLevelManager);
    }

    @After
    public void tearDown() throws Exception {
        objectRenderManagerField.set(runtimeLevelManager, originalRenderManager);
        RuntimeManager.setCurrent(originalRuntime);
    }

    @Test
    public void appendRenderCommandsUsesSpawnFlipBits() throws Exception {
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);

        when(renderManager.getRenderer(ObjectArtKeys.SBZ_FLAMETHROWER)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);

        objectRenderManagerField.set(runtimeLevelManager, renderManager);

        ObjectSpawn spawn = new ObjectSpawn(0x1234, 0x0560,
                Sonic1ObjectIds.FLAMETHROWER, 0x43, 0x03, false, 0);
        Sonic1FlamethrowerObjectInstance flamethrower = new Sonic1FlamethrowerObjectInstance(spawn);
        flamethrower.setServices(new TestObjectServices().withLevelManager(runtimeLevelManager));
        flamethrower.appendRenderCommands(new ArrayList<>());

        verify(renderer).drawFrameIndex(anyInt(), eq(0x1234), eq(0x0560), eq(true), eq(true));
    }
}
