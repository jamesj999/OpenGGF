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
import com.openggf.level.render.PatternSpriteRenderer;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestS1FlamethrowerObjectRendering {

    private Field levelManagerField;
    private LevelManager originalLevelManager;
    private LevelManager mockLevelManager;
    private GameRuntime originalRuntime;

    @Before
    public void setUp() throws Exception {
        // Save original state
        levelManagerField = LevelManager.class.getDeclaredField("levelManager");
        levelManagerField.setAccessible(true);
        originalLevelManager = (LevelManager) levelManagerField.get(null);
        originalRuntime = RuntimeManager.getCurrent();

        mockLevelManager = mock(LevelManager.class);
    }

    @After
    public void tearDown() throws Exception {
        levelManagerField.set(null, originalLevelManager);
        RuntimeManager.setCurrent(originalRuntime);
    }

    @Test
    public void appendRenderCommandsUsesSpawnFlipBits() throws Exception {
        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);

        when(mockLevelManager.getObjectRenderManager()).thenReturn(renderManager);
        when(renderManager.getRenderer(ObjectArtKeys.SBZ_FLAMETHROWER)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);

        // Install mock via both singleton field and RuntimeManager
        levelManagerField.set(null, mockLevelManager);
        // Clear the runtime so GameServices.level() falls back to LevelManager.getInstance()
        RuntimeManager.setCurrent(null);

        ObjectSpawn spawn = new ObjectSpawn(0x1234, 0x0560,
                Sonic1ObjectIds.FLAMETHROWER, 0x43, 0x03, false, 0);
        Sonic1FlamethrowerObjectInstance flamethrower = new Sonic1FlamethrowerObjectInstance(spawn);
        flamethrower.appendRenderCommands(new ArrayList<>());

        verify(renderer).drawFrameIndex(anyInt(), eq(0x1234), eq(0x0560), eq(true), eq(true));
    }
}
