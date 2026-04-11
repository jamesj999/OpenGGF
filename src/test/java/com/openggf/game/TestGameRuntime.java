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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GameRuntime} and {@link RuntimeManager} lifecycle.
 */
public class TestGameRuntime {

    @AfterEach
    public void tearDown() {
        // Clean up any runtime left by tests
        RuntimeManager.setCurrent(null);
    }

    @Test
    public void createGameplay_allManagersNonNull() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        assertNotNull(runtime.getCamera(), "camera");
        assertNotNull(runtime.getTimers(), "timers");
        assertNotNull(runtime.getGameState(), "gameState");
        assertNotNull(runtime.getFadeManager(), "fadeManager");
        assertNotNull(runtime.getWaterSystem(), "waterSystem");
        assertNotNull(runtime.getParallaxManager(), "parallaxManager");
        assertNotNull(runtime.getTerrainCollisionManager(), "terrainCollisionManager");
        assertNotNull(runtime.getCollisionSystem(), "collisionSystem");
        assertNotNull(runtime.getSpriteManager(), "spriteManager");
        assertNotNull(runtime.getLevelManager(), "levelManager");
    }

    @Test
    public void createGameplay_createsFreshRuntimeGraph() {
        GameRuntime runtime = RuntimeManager.createGameplay();

        assertSame(runtime.getCamera(), GameServices.camera());
        assertSame(runtime.getLevelManager(), GameServices.level());
        assertSame(runtime.getSpriteManager(), GameServices.sprites());
        assertSame(runtime.getGameState(), GameServices.gameState());
        assertSame(runtime.getTimers(), GameServices.timers());
        assertSame(runtime.getFadeManager(), GameServices.fade());
        assertSame(runtime.getCollisionSystem(), GameServices.collision());
        assertSame(runtime.getTerrainCollisionManager(), GameServices.terrainCollision());
        assertSame(runtime.getWaterSystem(), GameServices.water());
        assertSame(runtime.getParallaxManager(), GameServices.parallax());

        assertNotNull(runtime.getCamera());
        assertNotNull(runtime.getSpriteManager());
        assertNotNull(runtime.getGameState());
        assertNotNull(runtime.getTimers());
        assertNotNull(runtime.getFadeManager());
        assertNotNull(runtime.getTerrainCollisionManager());
        assertNotNull(runtime.getWaterSystem());
        assertNotNull(runtime.getParallaxManager());
    }

    @Test
    public void runtimeOwnedManagersDoNotExposeLegacySingletonCompatibilityAccessors() {
        assertNoLegacySingletonAccessor(Camera.class);
        assertNoLegacySingletonAccessor(LevelManager.class);
        assertNoLegacySingletonAccessor(SpriteManager.class);
        assertNoLegacySingletonAccessor(GameStateManager.class);
        assertNoLegacySingletonAccessor(TimerManager.class);
        assertNoLegacySingletonAccessor(FadeManager.class);
        assertNoLegacySingletonAccessor(CollisionSystem.class);
        assertNoLegacySingletonAccessor(TerrainCollisionManager.class);
        assertNoLegacySingletonAccessor(WaterSystem.class);
        assertNoLegacySingletonAccessor(ParallaxManager.class);
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

        IllegalStateException worldSessionEx = assertThrows(IllegalStateException.class, GameServices::worldSession);
        assertTrue(worldSessionEx.getMessage().contains("GameServices.worldSession() requires an active GameRuntime"));

        IllegalStateException moduleEx = assertThrows(IllegalStateException.class, GameServices::module);
        assertTrue(moduleEx.getMessage().contains("requires an active GameRuntime"));
    }

    @Test
    public void runtimeManager_createAndDestroy_lifecycle() {
        RuntimeManager.setCurrent(null);
        assertNull(RuntimeManager.getCurrent(), "Before create");

        GameRuntime runtime = RuntimeManager.createGameplay();
        assertNotNull(RuntimeManager.getCurrent(), "After create");
        assertSame(runtime, RuntimeManager.getCurrent());

        RuntimeManager.destroyCurrent();
        assertNull(RuntimeManager.getCurrent(), "After destroy");
    }

    @Test
    public void runtimeManager_createGameplay_setsAsCurrent() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        assertSame(runtime, RuntimeManager.getCurrent());
    }

    @Test
    public void destroy_doesNotThrow() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        // Should not throw â€” exercises the reverse-order teardown
        runtime.destroy();
    }

    /**
     * Integration test: full create â†’ mutate via GameServices â†’ destroy â†’ re-create
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
        assertNull(RuntimeManager.getCurrent(), "Runtime should be null after destroy");

        // GameServices should throw after destroy
        assertThrows(IllegalStateException.class, GameServices::camera);
        assertThrows(IllegalStateException.class, GameServices::gameState);

        // Phase 3: Create second runtime â€” state should be reset
        GameRuntime rt2 = RuntimeManager.createGameplay();
        assertNotNull(rt2);
        assertNotSame(rt1, rt2, "New runtime should be a different lifecycle");

        // Managers accessible through GameServices again
        assertNotNull(GameServices.camera());
        assertNotNull(GameServices.gameState());

        // State should have been reset by the destroy() call on rt1.
        // Score reverts to 0, camera to origin, lives to 3.
        assertEquals(0, GameServices.gameState().getScore(), "Score should be reset after destroy/recreate");
        assertEquals(0, GameServices.camera().getX(), "Camera X should be reset after destroy/recreate");
        assertEquals(3, GameServices.gameState().getLives(), "Lives should be reset after destroy/recreate");
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
        assertNotNull(GameServices.camera(), "camera");
        assertNotNull(GameServices.level(), "level");
        assertNotNull(GameServices.gameState(), "gameState");
        assertNotNull(GameServices.timers(), "timers");
        assertNotNull(GameServices.fade(), "fade");
        assertNotNull(GameServices.sprites(), "sprites");
        assertNotNull(GameServices.collision(), "collision");
        assertNotNull(GameServices.terrainCollision(), "terrainCollision");
        assertNotNull(GameServices.parallax(), "parallax");
        assertNotNull(GameServices.water(), "water");
        assertNotNull(GameServices.worldSession(), "worldSession");
        assertNotNull(GameServices.module(), "module");

        // Engine globals (non-runtime-owned) should also work
        assertNotNull(GameServices.rom(), "rom");
        assertNotNull(GameServices.audio(), "audio");
        assertNotNull(GameServices.configuration(), "configuration");
        assertNotNull(GameServices.debugOverlay(), "debugOverlay");
        assertNotNull(GameServices.graphics(), "graphics");
        assertNotNull(GameServices.profiler(), "profiler");
        assertNotNull(GameServices.playbackDebug(), "playbackDebug");
        assertNotNull(GameServices.romDetection(), "romDetection");
        assertNotNull(GameServices.crossGameFeatures(), "crossGameFeatures");
    }

    @Test
    public void gameServices_worldSessionAndModule_delegateToRuntimeWorldOwnership() {
        RuntimeManager.setCurrent(null);

        GameRuntime runtime = RuntimeManager.createGameplay();

        assertSame(runtime.getWorldSession(), GameServices.worldSession());
        assertSame(runtime.getWorldSession().getGameModule(), GameServices.module());
    }

    /**
     * Integration test: destroyCurrent is idempotent â€” calling it twice
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

    private static void assertNoLegacySingletonAccessor(Class<?> type) {
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals("getInstance") && Modifier.isStatic(method.getModifiers())) {
                fail(type.getSimpleName() + " should not expose static getInstance()");
            }
        }
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType().equals(type)) {
                fail(type.getSimpleName() + " should not keep static runtime-owned state via field '" + field.getName() + "'");
            }
        }
    }
}


