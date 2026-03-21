package com.openggf.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.objects.HtzGroundFireObjectInstance;
import com.openggf.game.sonic2.objects.bosses.HTZBossFlamethrower;
import com.openggf.game.sonic2.objects.bosses.HTZBossLavaBall;
import com.openggf.game.sonic2.objects.bosses.HTZBossSmokeParticle;
import com.openggf.game.sonic2.objects.bosses.LavaBubbleObjectInstance;
import com.openggf.game.sonic2.objects.bosses.Sonic2HTZBossInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

import java.lang.reflect.Field;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockedStatic;

/**
 * Regression tests for HTZ boss child objects and fire/smoke rendering setup.
 */
public class TestHTZBossChildObjects {

    private Field levelManagerField;
    private LevelManager originalLevelManager;
    private LevelManager mockLevelManager;

    @Before
    public void setUp() throws Exception {
        levelManagerField = LevelManager.class.getDeclaredField("levelManager");
        levelManagerField.setAccessible(true);
        originalLevelManager = (LevelManager) levelManagerField.get(null);
        mockLevelManager = mock(LevelManager.class);
    }

    @After
    public void tearDown() throws Exception {
        levelManagerField.set(null, originalLevelManager);
    }

    @Test
    public void lavaBubbleUsesSolRendererAndFireFrames() throws Exception {

        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);

        when(mockLevelManager.getObjectRenderManager()).thenReturn(renderManager);
        when(renderManager.getRenderer(Sonic2ObjectArtKeys.SOL)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);

        levelManagerField.set(null, mockLevelManager);

        LavaBubbleObjectInstance bubble = new LavaBubbleObjectInstance(100, 200);
        assertEquals(Sonic2ObjectIds.LAVA_BUBBLE, bubble.getSpawn().objectId());

        bubble.appendRenderCommands(new ArrayList<>());
        verify(renderManager).getRenderer(Sonic2ObjectArtKeys.SOL);
        verify(renderManager, never()).getRenderer(Sonic2ObjectArtKeys.HTZ_BOSS);
        verify(renderer).drawFrameIndex(eq(3), eq(100), eq(200), eq(false), eq(false));

        for (int i = 0; i < 8; i++) {
            bubble.update(i, null);
        }
        bubble.appendRenderCommands(new ArrayList<>());
        verify(renderer).drawFrameIndex(eq(4), eq(100), eq(200), eq(false), eq(false));
    }

    @Test
    public void htzSmokeSpawnSubtypeAndPriorityMatchRom() {
        HTZBossSmokeParticle smoke = new HTZBossSmokeParticle(10, 20, com.openggf.game.GameServices.level());
        assertEquals(0x08, smoke.getSpawn().subtype());
        assertEquals(1, smoke.getPriorityBucket());
    }

    @Test
    public void flamethrowerUsesHtzChildTileBaseOffset() throws Exception {

        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);

        when(mockLevelManager.getObjectRenderManager()).thenReturn(renderManager);
        when(renderManager.getRenderer(Sonic2ObjectArtKeys.HTZ_BOSS)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);

        levelManagerField.set(null, mockLevelManager);

        Sonic2HTZBossInstance parent = new Sonic2HTZBossInstance(
                new ObjectSpawn(0x3040, 0x0580, Sonic2ObjectIds.HTZ_BOSS, 0, 0, false, 0));
        HTZBossFlamethrower flamethrower = new HTZBossFlamethrower(parent, 0x3040, 0x0564, false);

        flamethrower.appendRenderCommands(new ArrayList<>());
        verify(renderer).drawPatternIndex(eq(0xC1), eq(0x2FCC), eq(0x0560), eq(0));
    }

    @Test
    public void lavaBallUsesHtzChildTileBaseOffsetBeforeLanding() throws Exception {

        ObjectRenderManager renderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer renderer = mock(PatternSpriteRenderer.class);

        when(mockLevelManager.getObjectRenderManager()).thenReturn(renderManager);
        when(renderManager.getRenderer(Sonic2ObjectArtKeys.HTZ_BOSS)).thenReturn(renderer);
        when(renderer.isReady()).thenReturn(true);

        levelManagerField.set(null, mockLevelManager);

        Sonic2HTZBossInstance parent = new Sonic2HTZBossInstance(
                new ObjectSpawn(0x3040, 0x0580, Sonic2ObjectIds.HTZ_BOSS, 0, 0, false, 0));
        HTZBossLavaBall lavaBall = new HTZBossLavaBall(parent, 0x3040, 0x0580, true, false);

        lavaBall.appendRenderCommands(new ArrayList<>());
        verify(renderer).drawPatternIndex(eq(0xC3), eq(0x3038), eq(0x0578), eq(0));
        verify(renderer).drawPatternIndex(eq(0xC5), eq(0x3040), eq(0x0578), eq(0));
        verify(renderer).drawPatternIndex(eq(0xC4), eq(0x3038), eq(0x0580), eq(0));
        verify(renderer).drawPatternIndex(eq(0xC6), eq(0x3040), eq(0x0580), eq(0));
    }

    @Test
    public void lavaBallTransformsToGroundFireWhenLandingOnSurface() throws Exception {

        ObjectManager objectManager = mock(ObjectManager.class);
        when(mockLevelManager.getObjectManager()).thenReturn(objectManager);
        levelManagerField.set(null, mockLevelManager);

        Sonic2HTZBossInstance parent = new Sonic2HTZBossInstance(
                new ObjectSpawn(0x3040, 0x0580, Sonic2ObjectIds.HTZ_BOSS, 0, 0, false, 0));
        HTZBossLavaBall lavaBall = new HTZBossLavaBall(parent, 0x3040, 0x0580, true, false);
        parent.getState().lastUpdatedFrame = 1;

        try (MockedStatic<ObjectTerrainUtils> terrain = mockStatic(ObjectTerrainUtils.class)) {
            terrain.when(() -> ObjectTerrainUtils.checkFloorDist(anyInt(), anyInt(), anyInt()))
                    .thenReturn(new TerrainCheckResult(-2, (byte) 0, 0));
            lavaBall.update(1, null);
        }

        assertTrue(lavaBall.isDestroyed());
        // ROM: lava ball transforms to Obj20 routine $A (fire trail spawner)
        verify(objectManager).addDynamicObject(argThat(obj -> obj instanceof HtzGroundFireObjectInstance));
    }

    @Test
    public void htzBossHazardsAreTouchResponseProviders() {
        Sonic2HTZBossInstance parent = new Sonic2HTZBossInstance(
                new ObjectSpawn(0x3040, 0x0580, Sonic2ObjectIds.HTZ_BOSS, 0, 0, false, 0));

        HTZBossFlamethrower flamethrower = new HTZBossFlamethrower(parent, 0x3040, 0x0564, false);
        HTZBossLavaBall lavaBall = new HTZBossLavaBall(parent, 0x3040, 0x0580, true, false);
        LavaBubbleObjectInstance bubble = new LavaBubbleObjectInstance(0x3040, 0x0580);

        assertTrue(flamethrower instanceof TouchResponseProvider);
        assertTrue(lavaBall instanceof TouchResponseProvider);
        assertTrue(bubble instanceof TouchResponseProvider);

        assertEquals(0, flamethrower.getCollisionProperty());
        assertEquals(0, lavaBall.getCollisionProperty());
        assertEquals(0, bubble.getCollisionProperty());
    }
}
