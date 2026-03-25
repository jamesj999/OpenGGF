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
    public void createGameplay_allManagersNonNull() {
        GameRuntime runtime = RuntimeManager.createGameplay();
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
    public void createGameplay_wrapsExistingSingletons() {
        GameRuntime runtime = RuntimeManager.createGameplay();
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
    public void gameServices_runtimeOwnedAccessors_throwWhenRuntimeMissing() {
        RuntimeManager.setCurrent(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, GameServices::camera);
        assertTrue(ex.getMessage().contains("GameServices.camera() requires an active GameRuntime"));
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
        GameRuntime runtime = RuntimeManager.createGameplay();
        // Should not throw — exercises the reverse-order teardown
        runtime.destroy();
    }
}
