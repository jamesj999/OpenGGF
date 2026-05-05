package com.openggf.game;

import com.openggf.game.session.EditorCursorState;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.TestEnvironment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestGameServicesNullableAccessors {
    @BeforeEach void setUp() { TestEnvironment.resetAll(); }
    @AfterEach void tearDown() { RuntimeManager.destroyCurrent(); SessionManager.clear(); }

    @Test
    void nullableAccessorsReturnNullWithoutRuntime() {
        // Post-migration: GameServices accessors resolve through the gameplay
        // mode context, so clearing the session is required (not just the runtime).
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        assertFalse(GameServices.hasRuntime());
        assertNull(GameServices.runtimeOrNull());
        assertNull(GameServices.cameraOrNull());
        assertNull(GameServices.levelOrNull());
        assertNull(GameServices.gameStateOrNull());
        assertNull(GameServices.timersOrNull());
        assertNull(GameServices.rngOrNull());
        assertNull(GameServices.parallaxOrNull());
        assertNull(GameServices.fadeOrNull());
        assertNull(GameServices.spritesOrNull());
        assertNull(GameServices.collisionOrNull());
        assertNull(GameServices.terrainCollisionOrNull());
        assertNull(GameServices.waterOrNull());
        assertNull(GameServices.bonusStageOrNull());
        assertNull(GameServices.animatedTileChannelGraphOrNull());
        assertNull(GameServices.specialRenderEffectRegistryOrNull());
        assertNull(GameServices.advancedRenderModeControllerOrNull());
    }

    @Test
    void nullableAccessorsReturnManagersWhenRuntimeExists() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        assertTrue(GameServices.hasRuntime());
        assertSame(runtime, GameServices.runtimeOrNull());
        assertSame(runtime.getCamera(), GameServices.cameraOrNull());
        assertSame(runtime.getLevelManager(), GameServices.levelOrNull());
        assertSame(runtime.getGameState(), GameServices.gameStateOrNull());
        assertSame(runtime.getTimers(), GameServices.timersOrNull());
        assertSame(runtime.getRng(), GameServices.rngOrNull());
        assertSame(runtime.getParallaxManager(), GameServices.parallaxOrNull());
        assertSame(runtime.getFadeManager(), GameServices.fadeOrNull());
        assertSame(runtime.getSpriteManager(), GameServices.spritesOrNull());
        assertSame(runtime.getCollisionSystem(), GameServices.collisionOrNull());
        assertSame(runtime.getTerrainCollisionManager(), GameServices.terrainCollisionOrNull());
        assertSame(runtime.getWaterSystem(), GameServices.waterOrNull());
        assertSame(runtime.getActiveBonusStageProvider(), GameServices.bonusStageOrNull());
        assertSame(runtime.getAnimatedTileChannelGraph(), GameServices.animatedTileChannelGraphOrNull());
        assertSame(runtime.getSpecialRenderEffectRegistry(), GameServices.specialRenderEffectRegistryOrNull());
        assertSame(runtime.getAdvancedRenderModeController(), GameServices.advancedRenderModeControllerOrNull());
    }

    @Test
    void strictAccessorsStillThrowWithoutRuntime() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        assertThrows(IllegalStateException.class, GameServices::camera);
        assertThrows(IllegalStateException.class, GameServices::level);
        assertThrows(IllegalStateException.class, GameServices::gameState);
        assertThrows(IllegalStateException.class, GameServices::timers);
        assertThrows(IllegalStateException.class, GameServices::rng);
        assertThrows(IllegalStateException.class, GameServices::parallax);
        assertThrows(IllegalStateException.class, GameServices::fade);
        assertThrows(IllegalStateException.class, GameServices::sprites);
        assertThrows(IllegalStateException.class, GameServices::collision);
        assertThrows(IllegalStateException.class, GameServices::terrainCollision);
        assertThrows(IllegalStateException.class, GameServices::water);
        assertThrows(IllegalStateException.class, GameServices::bonusStage);
        assertThrows(IllegalStateException.class, GameServices::animatedTileChannelGraph);
        assertThrows(IllegalStateException.class, GameServices::specialRenderEffectRegistry);
        assertThrows(IllegalStateException.class, GameServices::advancedRenderModeController);
    }

    /**
     * Predicate-equivalence invariant: {@link GameServices#hasRuntime()} must
     * agree with the underlying {@code gameplayModeOrNull() != null} check
     * across state transitions. Editor mode entry now does a proper teardown
     * (RuntimeManager.destroyCurrent) rather than parking, so the parked-
     * runtime case is no longer applicable; the invariant still holds across
     * the remaining lifecycle states.
     */
    @Test
    void hasRuntimeAgreesWithGameplayModeAcrossLifecycle() {
        // 1. No runtime, no session.
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        assertEquals(GameServices.cameraOrNull() != null, GameServices.hasRuntime(),
                "no-runtime: hasRuntime() must match gameplay mode availability");
        assertFalse(GameServices.hasRuntime());

        // 2. Active gameplay runtime.
        GameRuntime runtime = RuntimeManager.createGameplay();
        assertNotNull(runtime);
        assertEquals(GameServices.cameraOrNull() != null, GameServices.hasRuntime(),
                "active: hasRuntime() must match gameplay mode availability");
        assertTrue(GameServices.hasRuntime());

        // 3. Editor mode active — the runtime is destroyed but the gameplay
        //    mode context remains in SessionManager until enterEditorMode
        //    swaps it out. After enterEditorMode, both runtime and gameplay
        //    mode are gone (only the editor mode is current).
        RuntimeManager.destroyCurrent();
        SessionManager.enterEditorMode(new EditorCursorState(0, 0));
        assertEquals(GameServices.cameraOrNull() != null, GameServices.hasRuntime(),
                "editor: hasRuntime() must match gameplay mode availability");
        assertFalse(GameServices.hasRuntime(), "editor: no gameplay mode is active");

        // 4. Resumed from editor — fresh runtime + gameplay mode.
        GameplayModeContext resumedMode = SessionManager.resumeGameplayFromEditor();
        RuntimeManager.createGameplay(resumedMode);
        assertEquals(GameServices.cameraOrNull() != null, GameServices.hasRuntime(),
                "post-resume: hasRuntime() must agree with gameplay mode availability");
        assertTrue(GameServices.hasRuntime(), "post-resume: gameplay should be active");

        // 5. Fully torn down.
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        assertEquals(GameServices.cameraOrNull() != null, GameServices.hasRuntime(),
                "post-destroy: hasRuntime() must match gameplay mode availability");
        assertFalse(GameServices.hasRuntime());
    }

    /**
     * {@link GameServices#bonusStage()} must NOT call {@code RuntimeManager.getCurrent()}.
     * That method has a side effect: if the current runtime's gameplay mode
     * doesn't match the SessionManager's current mode, it destroys the runtime.
     * Repeated bonusStage() calls should be safe and stable.
     */
    @Test
    void bonusStageDoesNotDestroyLiveRuntimeOnRepeatedCalls() {
        GameRuntime runtime = RuntimeManager.createGameplay();
        GameplayModeContext mode = SessionManager.getCurrentGameplayMode();
        assertNotNull(mode);
        assertSame(runtime, RuntimeManager.getActiveRuntime());

        // First call returns NoOp default.
        BonusStageProvider firstCall = GameServices.bonusStage();
        assertNotNull(firstCall);

        // Runtime must still be live and unchanged.
        assertSame(runtime, RuntimeManager.getActiveRuntime(),
                "bonusStage() must not swap or destroy the active runtime");
        assertSame(mode, SessionManager.getCurrentGameplayMode());

        // Many repeated calls remain stable.
        for (int i = 0; i < 5; i++) {
            BonusStageProvider repeated = GameServices.bonusStage();
            assertSame(firstCall, repeated, "bonusStage() must return the same provider when unchanged");
            assertSame(runtime, RuntimeManager.getActiveRuntime(),
                    "bonusStage() must not destroy the active runtime on repeated calls");
        }
    }
}


