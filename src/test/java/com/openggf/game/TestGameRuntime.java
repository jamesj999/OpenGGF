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

    /**
     * Integration test: full create → mutate via GameServices → destroy → re-create
     * lifecycle. Verifies that state set through the first runtime does not leak
     * into the second runtime after a destroy/recreate cycle.
     */
    @Test
    public void fullLifecycle_destroyAndRecreate_stateIsIsolated() {
        // Phase 1: Create first runtime, mutate state through GameServices
        GameRuntime rt1 = RuntimeManager.createGameplay();
        assertNotNull(rt1);

        // Mutate state through the runtime-owned managers
        GameServices.gameState().addScore(99999);
        GameServices.camera().setX((short) 1000);

        // Verify GameServices delegates to the correct runtime
        assertSame(rt1.getCamera(), GameServices.camera());
        assertSame(rt1.getGameState(), GameServices.gameState());
        assertEquals(99999, GameServices.gameState().getScore());
        assertEquals(1000, GameServices.camera().getX());

        // Phase 2: Destroy current runtime
        RuntimeManager.destroyCurrent();
        assertNull("Runtime should be null after destroy", RuntimeManager.getCurrent());

        // GameServices should throw after destroy
        assertThrows(IllegalStateException.class, GameServices::camera);
        assertThrows(IllegalStateException.class, GameServices::gameState);

        // Phase 3: Create second runtime — state should be reset
        GameRuntime rt2 = RuntimeManager.createGameplay();
        assertNotNull(rt2);
        assertNotSame("New runtime should be a different lifecycle", rt1, rt2);

        // Managers accessible through GameServices again
        assertNotNull(GameServices.camera());
        assertNotNull(GameServices.gameState());

        // State should have been reset by the destroy() call on rt1.
        // Score reverts to 0, camera to origin, lives to 3.
        assertEquals("Score should be reset after destroy/recreate",
                0, GameServices.gameState().getScore());
        assertEquals("Camera X should be reset after destroy/recreate",
                0, GameServices.camera().getX());
        assertEquals("Lives should be reset after destroy/recreate",
                3, GameServices.gameState().getLives());
    }

    /**
     * Integration test: GameServices accessor methods all succeed after
     * a single createGameplay() call (no prior runtime exists).
     */
    @Test
    public void gameServices_allAccessors_succeedAfterCreate() {
        RuntimeManager.setCurrent(null);

        GameRuntime runtime = RuntimeManager.createGameplay();

        // Every runtime-owned accessor should return non-null
        assertNotNull("camera", GameServices.camera());
        assertNotNull("level", GameServices.level());
        assertNotNull("gameState", GameServices.gameState());
        assertNotNull("timers", GameServices.timers());
        assertNotNull("fade", GameServices.fade());
        assertNotNull("sprites", GameServices.sprites());
        assertNotNull("collision", GameServices.collision());
        assertNotNull("terrainCollision", GameServices.terrainCollision());
        assertNotNull("parallax", GameServices.parallax());
        assertNotNull("water", GameServices.water());

        // Engine globals (non-runtime-owned) should also work
        assertNotNull("rom", GameServices.rom());
        assertNotNull("audio", GameServices.audio());
        assertNotNull("debugOverlay", GameServices.debugOverlay());
    }

    /**
     * Integration test: destroyCurrent is idempotent — calling it twice
     * or when no runtime exists should not throw.
     */
    @Test
    public void destroyCurrent_idempotent() {
        RuntimeManager.setCurrent(null);
        // Should not throw when no runtime exists
        RuntimeManager.destroyCurrent();
        assertNull(RuntimeManager.getCurrent());

        // Create, destroy twice
        RuntimeManager.createGameplay();
        RuntimeManager.destroyCurrent();
        RuntimeManager.destroyCurrent();
        assertNull(RuntimeManager.getCurrent());
    }
}
