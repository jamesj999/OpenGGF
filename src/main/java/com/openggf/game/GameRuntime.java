package com.openggf.game;

import com.openggf.game.session.EngineContext;
import com.openggf.camera.Camera;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.mutation.ZoneLayoutMutationPipeline;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.WorldSession;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectManager;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.level.rings.RingManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

import java.util.Objects;

/**
 * Thin coordinator for the active gameplay session. Holds engine services,
 * the world session, and the gameplay mode context; everything else
 * (including the active bonus stage provider, now owned by
 * {@link GameplayModeContext}) is delegated. Constructed and managed by
 * {@link RuntimeManager}.
 * <p>
 * Following the runtime ownership migration
 * (docs/superpowers/specs/2026-04-07-runtime-ownership-migration-design.md
 * Phase 3), this class no longer owns its own manager fields. Disposable
 * gameplay managers — Camera, TimerManager, GameStateManager, FadeManager,
 * GameRng, SolidExecutionRegistry, WaterSystem, ParallaxManager,
 * TerrainCollisionManager, CollisionSystem, SpriteManager, LevelManager —
 * and the runtime-shared registries — ZoneRuntimeRegistry,
 * PaletteOwnershipRegistry, AnimatedTileChannelGraph,
 * SpecialRenderEffectRegistry, AdvancedRenderModeController,
 * ZoneLayoutMutationPipeline — are owned by {@link GameplayModeContext}.
 * The getters here remain as delegating pass-throughs so existing callers
 * continue to work; new code should prefer resolving these from the gameplay
 * mode context directly via {@link #getGameplayModeContext()}.
 * <p>
 * <b>Not owned anywhere in the session graph</b> (engine globals that stay
 * as singletons): GraphicsManager, AudioManager, RomManager,
 * SonicConfigurationService, PerformanceProfiler, DebugOverlayManager,
 * DebugRenderer, GameModuleRegistry.
 * <p>
 * <b>Future direction:</b> with field ownership now off this class, a
 * follow-up could fold {@code engineServices} onto {@link GameplayModeContext}
 * and let {@link RuntimeManager} track the mode context directly — making this
 * façade redundant. That elimination is
 * deferred because the 50+ call sites that read managers via
 * {@code RuntimeManager.getCurrent().getX()} would need to be migrated to
 * {@code SessionManager.getCurrentGameplayMode().getX()} (or moved through
 * {@code GameServices}). It's mechanical, not architectural.
 *
 * @see RuntimeManager
 * @see GameServices
 */
public final class GameRuntime {

    private final EngineContext engineServices;
    private final WorldSession worldSession;
    private GameplayModeContext gameplayMode;

    /**
     * Package-private constructor — only {@link RuntimeManager} creates these.
     * Both the disposable managers and the shared registries must already be
     * attached to {@code gameplayMode}.
     */
    GameRuntime(EngineContext engineServices,
                WorldSession worldSession,
                GameplayModeContext gameplayMode) {
        this.engineServices = Objects.requireNonNull(engineServices, "engineServices");
        this.worldSession = worldSession;
        this.gameplayMode = Objects.requireNonNull(gameplayMode, "gameplayMode");
        ensureGameplayModeReady(gameplayMode);
    }

    private static void ensureGameplayModeReady(GameplayModeContext gameplayMode) {
        if (gameplayMode.getCamera() == null) {
            throw new IllegalStateException(
                    "GameplayModeContext must have core gameplay managers attached before GameRuntime construction.");
        }
        if (gameplayMode.getLevelManager() == null) {
            throw new IllegalStateException(
                    "GameplayModeContext must have level managers attached before GameRuntime construction.");
        }
        if (gameplayMode.getZoneRuntimeRegistry() == null) {
            throw new IllegalStateException(
                    "GameplayModeContext must have shared registries attached before GameRuntime construction.");
        }
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public EngineContext getEngineServices() { return engineServices; }
    public WorldSession getWorldSession() { return worldSession; }
    public GameplayModeContext getGameplayModeContext() { return gameplayMode; }

    // The following accessors delegate to GameplayModeContext, which now owns
    // both the disposable gameplay-scoped managers and the runtime-shared
    // registries. Callers may migrate to using GameplayModeContext directly;
    // both paths return the same instances.

    public Camera getCamera() { return gameplayMode.getCamera(); }

    public TimerManager getTimers() { return gameplayMode.getTimerManager(); }

    public GameStateManager getGameState() { return gameplayMode.getGameStateManager(); }

    public FadeManager getFadeManager() { return gameplayMode.getFadeManager(); }

    public GameRng getRng() { return gameplayMode.getRng(); }

    public SolidExecutionRegistry getSolidExecutionRegistry() { return gameplayMode.getSolidExecutionRegistry(); }

    public WaterSystem getWaterSystem() { return gameplayMode.getWaterSystem(); }

    public ParallaxManager getParallaxManager() { return gameplayMode.getParallaxManager(); }

    public TerrainCollisionManager getTerrainCollisionManager() { return gameplayMode.getTerrainCollisionManager(); }

    public CollisionSystem getCollisionSystem() { return gameplayMode.getCollisionSystem(); }

    public SpriteManager getSpriteManager() { return gameplayMode.getSpriteManager(); }

    public LevelManager getLevelManager() { return gameplayMode.getLevelManager(); }

    public ZoneRuntimeRegistry getZoneRuntimeRegistry() { return gameplayMode.getZoneRuntimeRegistry(); }

    public PaletteOwnershipRegistry getPaletteOwnershipRegistry() { return gameplayMode.getPaletteOwnershipRegistry(); }

    public AnimatedTileChannelGraph getAnimatedTileChannelGraph() { return gameplayMode.getAnimatedTileChannelGraph(); }

    public SpecialRenderEffectRegistry getSpecialRenderEffectRegistry() { return gameplayMode.getSpecialRenderEffectRegistry(); }

    public AdvancedRenderModeController getAdvancedRenderModeController() { return gameplayMode.getAdvancedRenderModeController(); }

    public ZoneLayoutMutationPipeline getZoneLayoutMutationPipeline() { return gameplayMode.getZoneLayoutMutationPipeline(); }

    /**
     * Delegates to {@link GameplayModeContext#getActiveBonusStageProvider()}.
     * The provider lives on the gameplay mode context (gameplay-scoped lifetime);
     * this façade method is kept for source compatibility with existing callers.
     */
    public BonusStageProvider getActiveBonusStageProvider() {
        return gameplayMode.getActiveBonusStageProvider();
    }

    /**
     * Delegates to {@link GameplayModeContext#setActiveBonusStageProvider(BonusStageProvider)}.
     */
    public void setActiveBonusStageProvider(BonusStageProvider provider) {
        gameplayMode.setActiveBonusStageProvider(provider);
    }

    /**
     * Rebinds this runtime to a resumed gameplay mode context after an editor detour.
     * The new gameplay mode must already have the same gameplay-scoped managers
     * attached (transferred from the parked context by {@link RuntimeManager#resumeParked}).
     */
    public void updateGameplayModeContext(GameplayModeContext gameplayMode) {
        Objects.requireNonNull(gameplayMode, "gameplayMode");
        ensureGameplayModeReady(gameplayMode);
        this.gameplayMode = gameplayMode;
    }

    /**
     * Clears per-frame transient state that must not survive parking, resume, or teardown.
     */
    public void clearTransientFrameState() {
        gameplayMode.getZoneLayoutMutationPipeline().clear();
        gameplayMode.getSolidExecutionRegistry().clearTransientState();
    }

    // ── Convenience: LevelManager-owned sub-managers ─────────────────────

    /** Returns the ObjectManager from LevelManager (created during level load). */
    public ObjectManager getObjectManager() { return gameplayMode.getLevelManager().getObjectManager(); }

    /** Returns the RingManager from LevelManager (created during level load). */
    public RingManager getRingManager() { return gameplayMode.getLevelManager().getRingManager(); }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Tears down all managers via {@link GameplayModeContext#tearDownManagers()}.
     * Called by {@link RuntimeManager#destroyCurrent()}. The actual teardown
     * implementation lives on {@code GameplayModeContext}; this façade just
     * routes the runtime-driven path. {@code SessionManager.destroyCurrentMode}
     * intentionally does NOT trigger teardown — see {@link GameplayModeContext#destroy()}.
     */
    public void destroy() {
        gameplayMode.tearDownManagers();
    }
}
