package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for {@link GameRuntime} and {@link RuntimeManager} lifecycle.
 */
public class TestGameRuntime {

    @After
    public void tearDown() {
        // Clean up any runtime left by tests
        RuntimeManager.setCurrent(null);
    }

    @Test
    public void createFromSingletons_allManagersNonNull() {
        GameRuntime runtime = GameRuntime.createFromSingletons();
        assertNotNull("camera", runtime.getCamera());
        assertNotNull("timers", runtime.getTimers());
        assertNotNull("gameState", runtime.getGameState());
        assertNotNull("fadeManager", runtime.getFadeManager());
        assertNotNull("waterSystem", runtime.getWaterSystem());
        assertNotNull("parallaxManager", runtime.getParallaxManager());
        assertNotNull("terrainCollisionManager", runtime.getTerrainCollisionManager());
        assertNotNull("collisionSystem", runtime.getCollisionSystem());
        assertNotNull("spriteManager", runtime.getSpriteManager());
        assertNotNull("levelManager", runtime.getLevelManager());
    }

    @Test
    public void createFromSingletons_wrapsExistingSingletons() {
        GameRuntime runtime = GameRuntime.createFromSingletons();
        assertSame(Camera.getInstance(), runtime.getCamera());
        assertSame(LevelManager.getInstance(), runtime.getLevelManager());
        assertSame(SpriteManager.getInstance(), runtime.getSpriteManager());
        assertSame(GameStateManager.getInstance(), runtime.getGameState());
        assertSame(TimerManager.getInstance(), runtime.getTimers());
        assertSame(FadeManager.getInstance(), runtime.getFadeManager());
        assertSame(CollisionSystem.getInstance(), runtime.getCollisionSystem());
        assertSame(TerrainCollisionManager.getInstance(), runtime.getTerrainCollisionManager());
        assertSame(WaterSystem.getInstance(), runtime.getWaterSystem());
        assertSame(ParallaxManager.getInstance(), runtime.getParallaxManager());
    }

    @Test
    public void runtimeManager_getCurrent_returnsNullBeforeCreate() {
        RuntimeManager.setCurrent(null);
        assertNull(RuntimeManager.getCurrent());
    }

    @Test
    public void runtimeManager_createAndDestroy_lifecycle() {
        RuntimeManager.setCurrent(null);
        assertNull("Before create", RuntimeManager.getCurrent());

        GameRuntime runtime = RuntimeManager.createGameplay();
        assertNotNull("After create", RuntimeManager.getCurrent());
        assertSame(runtime, RuntimeManager.getCurrent());

        RuntimeManager.destroyCurrent();
        assertNull("After destroy", RuntimeManager.getCurrent());
    }

    @Test
    public void runtimeManager_createGameplay_setsAsCurrent() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        assertSame(runtime, RuntimeManager.getCurrent());
    }

    @Test
    public void destroy_doesNotThrow() {
        GameRuntime runtime = GameRuntime.createFromSingletons();
        // Should not throw — exercises the reverse-order teardown
        runtime.destroy();
    }
}
