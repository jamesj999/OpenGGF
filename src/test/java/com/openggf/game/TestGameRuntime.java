package com.openggf.game;

import com.openggf.game.session.EngineContext;
import com.openggf.camera.Camera;
import com.openggf.game.animation.AnimatedTileChannel;
import com.openggf.game.animation.AnimatedTileCachePolicy;
import com.openggf.game.animation.DestinationPlan;
import com.openggf.game.mutation.MutationEffects;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link GameRuntime} and {@link RuntimeManager} lifecycle.
 */
public class TestGameRuntime {

    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.configureEngineServices(EngineContext.fromLegacySingletonsForBootstrap());
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
        GameModuleRegistry.reset();
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
        assertNotNull(runtime.getSpecialRenderEffectRegistry(), "specialRenderEffectRegistry");
        assertNotNull(runtime.getAdvancedRenderModeController(), "advancedRenderModeController");
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
        assertSame(runtime.getSpecialRenderEffectRegistry(), GameServices.specialRenderEffectRegistry());
        assertSame(runtime.getAdvancedRenderModeController(), GameServices.advancedRenderModeController());

        assertNotNull(runtime.getCamera());
        assertNotNull(runtime.getSpriteManager());
        assertNotNull(runtime.getGameState());
        assertNotNull(runtime.getTimers());
        assertNotNull(runtime.getFadeManager());
        assertNotNull(runtime.getTerrainCollisionManager());
        assertNotNull(runtime.getWaterSystem());
        assertNotNull(runtime.getParallaxManager());
        assertNotNull(runtime.getSpecialRenderEffectRegistry());
        assertNotNull(runtime.getAdvancedRenderModeController());
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
    public void runtimeManager_getCurrent_throwsWhenEngineServicesMissing() throws Exception {
        RuntimeManager.setCurrent(null);

        Field engineServicesField = RuntimeManager.class.getDeclaredField("engineServices");
        engineServicesField.setAccessible(true);
        Object configuredRoot = engineServicesField.get(null);
        try {
            engineServicesField.set(null, null);
            IllegalStateException ex = assertThrows(IllegalStateException.class, RuntimeManager::getCurrent);
            assertTrue(ex.getMessage().contains("EngineContext have not been configured."));
        } finally {
            engineServicesField.set(null, configuredRoot);
        }
    }

    @Test
    public void gameServices_gameplayScopedAccessors_throwWhenGameplayModeMissing() {
        // After the runtime ownership migration, GameServices accessors resolve
        // through SessionManager.getCurrentGameplayMode(), not through the
        // GameRuntime façade — so fully clearing the session is required to
        // exercise the "no active gameplay" error path.
        com.openggf.game.session.SessionManager.clear();

        IllegalStateException ex = assertThrows(IllegalStateException.class, GameServices::camera);
        assertTrue(ex.getMessage().contains("GameServices.camera() requires an active gameplay mode"));

        IllegalStateException worldSessionEx = assertThrows(IllegalStateException.class, GameServices::worldSession);
        assertTrue(worldSessionEx.getMessage().contains("requires an active WorldSession"));

        IllegalStateException moduleEx = assertThrows(IllegalStateException.class, GameServices::module);
        assertTrue(moduleEx.getMessage().contains("requires an active WorldSession"));
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

        // After the runtime ownership migration, GameServices throws only when
        // the active gameplay mode is gone — destroyCurrent leaves the
        // gameplay-mode managers attached (cleared but present). Clearing the
        // session simulates a full editor-mode-style teardown.
        com.openggf.game.session.SessionManager.clear();
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

    @Test
    public void gameServices_animatedTileChannelGraphDelegatesToRuntimeAndClearsOnDestroy() {
        GameRuntime runtime = RuntimeManager.createGameplay();

        assertSame(runtime.getAnimatedTileChannelGraph(), GameServices.animatedTileChannelGraph());
        assertSame(runtime.getAnimatedTileChannelGraph(), GameServices.animatedTileChannelGraphOrNull());

        runtime.getAnimatedTileChannelGraph().install(List.of(
                new AnimatedTileChannel(
                        "graph-lifecycle",
                        () -> true,
                        ctx -> 1,
                        DestinationPlan.single(0x120),
                        AnimatedTileCachePolicy.ALWAYS,
                        ctx -> { })));
        assertEquals(1, runtime.getAnimatedTileChannelGraph().channels().size());

        RuntimeManager.destroyCurrent();
        // Post-migration: GameServices accessors throw only when the gameplay
        // mode is gone — destroyCurrent leaves cleared managers attached.
        com.openggf.game.session.SessionManager.clear();

        assertNull(GameServices.animatedTileChannelGraphOrNull());
        assertThrows(IllegalStateException.class, GameServices::animatedTileChannelGraph);
        assertEquals(0, runtime.getAnimatedTileChannelGraph().channels().size());
    }

    @Test
    public void gameServices_specialRenderEffectRegistryDelegatesToRuntimeAndClearsOnDestroy() {
        GameRuntime runtime = RuntimeManager.createGameplay();

        assertSame(runtime.getSpecialRenderEffectRegistry(), GameServices.specialRenderEffectRegistry());
        assertSame(runtime.getSpecialRenderEffectRegistry(), GameServices.specialRenderEffectRegistryOrNull());

        runtime.getSpecialRenderEffectRegistry().register(new SpecialRenderEffect() {
            @Override
            public SpecialRenderEffectStage stage() {
                return SpecialRenderEffectStage.AFTER_BACKGROUND;
            }

            @Override
            public void render(SpecialRenderEffectContext context) {
                // no-op
            }
        });

        assertFalse(runtime.getSpecialRenderEffectRegistry().isEmpty());

        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();

        assertNull(GameServices.specialRenderEffectRegistryOrNull());
        assertThrows(IllegalStateException.class, GameServices::specialRenderEffectRegistry);
        assertTrue(runtime.getSpecialRenderEffectRegistry().isEmpty());
    }

    @Test
    public void gameServices_zoneLayoutMutationPipelineDelegatesToRuntimeAndClearsOnDestroy() {
        GameRuntime runtime = RuntimeManager.createGameplay();

        assertSame(runtime.getZoneLayoutMutationPipeline(), GameServices.zoneLayoutMutationPipeline());
        assertSame(runtime.getZoneLayoutMutationPipeline(), GameServices.zoneLayoutMutationPipelineOrNull());

        runtime.getZoneLayoutMutationPipeline().queue(context -> MutationEffects.redrawAllTilemaps());
        assertFalse(runtime.getZoneLayoutMutationPipeline().isEmpty());

        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();

        assertNull(GameServices.zoneLayoutMutationPipelineOrNull());
        assertThrows(IllegalStateException.class, GameServices::zoneLayoutMutationPipeline);
        assertTrue(runtime.getZoneLayoutMutationPipeline().isEmpty());
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


