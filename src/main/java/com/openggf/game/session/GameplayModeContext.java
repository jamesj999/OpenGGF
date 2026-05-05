package com.openggf.game.session;

import com.openggf.camera.Camera;
import com.openggf.game.BonusStageProvider;
import com.openggf.game.GameMode;
import com.openggf.game.GameRng;
import com.openggf.game.GameStateManager;
import com.openggf.game.NoOpBonusStageProvider;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.mutation.ZoneLayoutMutationPipeline;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

import java.util.Optional;
import java.util.Objects;

public final class GameplayModeContext implements ModeContext {
    private final WorldSession worldSession;
    private final int spawnX;
    private final int spawnY;
    private final EditorPlaytestStash resumeStash;

    private Camera camera;
    private TimerManager timerManager;
    private GameStateManager gameStateManager;
    private FadeManager fadeManager;
    private GameRng rng;
    private SolidExecutionRegistry solidExecutionRegistry;

    private WaterSystem waterSystem;
    private ParallaxManager parallaxManager;
    private TerrainCollisionManager terrainCollisionManager;
    private CollisionSystem collisionSystem;
    private SpriteManager spriteManager;
    private LevelManager levelManager;

    private ZoneRuntimeRegistry zoneRuntimeRegistry;
    private PaletteOwnershipRegistry paletteOwnershipRegistry;
    private AnimatedTileChannelGraph animatedTileChannelGraph;
    private SpecialRenderEffectRegistry specialRenderEffectRegistry;
    private AdvancedRenderModeController advancedRenderModeController;
    private ZoneLayoutMutationPipeline zoneLayoutMutationPipeline;

    private BonusStageProvider activeBonusStageProvider = NoOpBonusStageProvider.INSTANCE;

    public GameplayModeContext(WorldSession worldSession) {
        this(worldSession, 0, 0, null);
    }

    public GameplayModeContext(WorldSession worldSession, int spawnX, int spawnY) {
        this(worldSession, spawnX, spawnY, null);
    }

    public GameplayModeContext(WorldSession worldSession,
                               int spawnX,
                               int spawnY,
                               EditorPlaytestStash resumeStash) {
        this.worldSession = Objects.requireNonNull(worldSession, "worldSession");
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        this.resumeStash = resumeStash;
    }

    public WorldSession getWorldSession() {
        return worldSession;
    }

    public int getSpawnX() {
        return spawnX;
    }

    public int getSpawnY() {
        return spawnY;
    }

    public boolean hasResumeStash() {
        return resumeStash != null;
    }

    public Optional<EditorPlaytestStash> getResumeStash() {
        return Optional.ofNullable(resumeStash);
    }

    /**
     * Attaches the core disposable gameplay-scoped managers — those without
     * inter-manager construction-order dependencies. Called by
     * {@code RuntimeManager.createGameplay}, and again by
     * {@code RuntimeManager.resumeParked} or test paths that recycle a mode
     * context after destroying its runtime. Re-attachment replaces existing
     * references.
     */
    public void attachGameplayManagers(Camera camera,
                                       TimerManager timerManager,
                                       GameStateManager gameStateManager,
                                       FadeManager fadeManager,
                                       GameRng rng,
                                       SolidExecutionRegistry solidExecutionRegistry) {
        this.camera = Objects.requireNonNull(camera, "camera");
        this.timerManager = Objects.requireNonNull(timerManager, "timerManager");
        this.gameStateManager = Objects.requireNonNull(gameStateManager, "gameStateManager");
        this.fadeManager = Objects.requireNonNull(fadeManager, "fadeManager");
        this.rng = Objects.requireNonNull(rng, "rng");
        this.solidExecutionRegistry = Objects.requireNonNull(solidExecutionRegistry, "solidExecutionRegistry");
    }

    /**
     * Attaches the level-coupled disposable managers — water, parallax, the
     * terrain/collision pair, sprite manager, and the LevelManager itself.
     * These have construction-order dependencies on each other and on the core
     * managers, so the caller is responsible for constructing them in the
     * correct order before this attach call.
     */
    public void attachLevelManagers(WaterSystem waterSystem,
                                    ParallaxManager parallaxManager,
                                    TerrainCollisionManager terrainCollisionManager,
                                    CollisionSystem collisionSystem,
                                    SpriteManager spriteManager,
                                    LevelManager levelManager) {
        this.waterSystem = Objects.requireNonNull(waterSystem, "waterSystem");
        this.parallaxManager = Objects.requireNonNull(parallaxManager, "parallaxManager");
        this.terrainCollisionManager = Objects.requireNonNull(terrainCollisionManager, "terrainCollisionManager");
        this.collisionSystem = Objects.requireNonNull(collisionSystem, "collisionSystem");
        this.spriteManager = Objects.requireNonNull(spriteManager, "spriteManager");
        this.levelManager = Objects.requireNonNull(levelManager, "levelManager");
    }

    /**
     * Attaches the runtime-shared registries used by zone-specific behavior:
     * zone-typed runtime state, palette ownership arbitration, animated tile
     * channels, special render effects, advanced render mode overrides, and
     * the zone layout mutation pipeline. Each currently mixes durable world
     * data with per-frame mutation state; the world/gameplay split inside
     * these registries is deferred to a later migration phase.
     */
    public void attachSharedRegistries(ZoneRuntimeRegistry zoneRuntimeRegistry,
                                       PaletteOwnershipRegistry paletteOwnershipRegistry,
                                       AnimatedTileChannelGraph animatedTileChannelGraph,
                                       SpecialRenderEffectRegistry specialRenderEffectRegistry,
                                       AdvancedRenderModeController advancedRenderModeController,
                                       ZoneLayoutMutationPipeline zoneLayoutMutationPipeline) {
        this.zoneRuntimeRegistry = Objects.requireNonNull(zoneRuntimeRegistry, "zoneRuntimeRegistry");
        this.paletteOwnershipRegistry = Objects.requireNonNull(paletteOwnershipRegistry, "paletteOwnershipRegistry");
        this.animatedTileChannelGraph = Objects.requireNonNull(animatedTileChannelGraph, "animatedTileChannelGraph");
        this.specialRenderEffectRegistry = Objects.requireNonNull(specialRenderEffectRegistry, "specialRenderEffectRegistry");
        this.advancedRenderModeController = Objects.requireNonNull(advancedRenderModeController, "advancedRenderModeController");
        this.zoneLayoutMutationPipeline = Objects.requireNonNull(zoneLayoutMutationPipeline, "zoneLayoutMutationPipeline");
    }

    public Camera getCamera() {
        return camera;
    }

    public TimerManager getTimerManager() {
        return timerManager;
    }

    public GameStateManager getGameStateManager() {
        return gameStateManager;
    }

    public FadeManager getFadeManager() {
        return fadeManager;
    }

    public GameRng getRng() {
        return rng;
    }

    public SolidExecutionRegistry getSolidExecutionRegistry() {
        return solidExecutionRegistry;
    }

    public WaterSystem getWaterSystem() {
        return waterSystem;
    }

    public ParallaxManager getParallaxManager() {
        return parallaxManager;
    }

    public TerrainCollisionManager getTerrainCollisionManager() {
        return terrainCollisionManager;
    }

    public CollisionSystem getCollisionSystem() {
        return collisionSystem;
    }

    public SpriteManager getSpriteManager() {
        return spriteManager;
    }

    public LevelManager getLevelManager() {
        return levelManager;
    }

    public ZoneRuntimeRegistry getZoneRuntimeRegistry() {
        return zoneRuntimeRegistry;
    }

    public PaletteOwnershipRegistry getPaletteOwnershipRegistry() {
        return paletteOwnershipRegistry;
    }

    public AnimatedTileChannelGraph getAnimatedTileChannelGraph() {
        return animatedTileChannelGraph;
    }

    public SpecialRenderEffectRegistry getSpecialRenderEffectRegistry() {
        return specialRenderEffectRegistry;
    }

    public AdvancedRenderModeController getAdvancedRenderModeController() {
        return advancedRenderModeController;
    }

    public ZoneLayoutMutationPipeline getZoneLayoutMutationPipeline() {
        return zoneLayoutMutationPipeline;
    }

    /**
     * Returns the active bonus stage provider, or
     * {@link NoOpBonusStageProvider#INSTANCE} when no bonus stage is active.
     * Owned here (gameplay-scoped) so callers can resolve it via
     * {@link com.openggf.game.session.SessionManager#getCurrentGameplayMode()}
     * without consulting {@code RuntimeManager.getCurrent()}, which has
     * mode-transition side effects.
     */
    public BonusStageProvider getActiveBonusStageProvider() {
        return activeBonusStageProvider;
    }

    public void setActiveBonusStageProvider(BonusStageProvider provider) {
        this.activeBonusStageProvider = provider != null ? provider : NoOpBonusStageProvider.INSTANCE;
    }

    @Override
    public GameMode getGameMode() {
        return GameMode.LEVEL;
    }

    @Override
    public void destroy() {
        // Tear down all managers in reverse construction order. Idempotent:
        // each manager's reset is a no-op when fields are null (e.g., when
        // destroy is invoked during a partial setup). Called from both
        // SessionManager.destroyCurrentMode() and (delegated) GameRuntime.destroy()
        // so that the parked-runtime path keeps the same teardown semantics.
        if (zoneLayoutMutationPipeline != null) {
            zoneLayoutMutationPipeline.clear();
        }
        if (solidExecutionRegistry != null) {
            solidExecutionRegistry.clearTransientState();
        }
        if (animatedTileChannelGraph != null) {
            animatedTileChannelGraph.clear();
        }
        if (specialRenderEffectRegistry != null) {
            specialRenderEffectRegistry.clear();
        }
        if (advancedRenderModeController != null) {
            advancedRenderModeController.clear();
        }
        if (paletteOwnershipRegistry != null) {
            paletteOwnershipRegistry.beginFrame();
        }
        if (zoneRuntimeRegistry != null) {
            zoneRuntimeRegistry.clear();
        }
        if (levelManager != null) {
            levelManager.resetState();
        }
        if (spriteManager != null) {
            spriteManager.resetState();
        }
        if (collisionSystem != null) {
            collisionSystem.resetState();
        }
        if (terrainCollisionManager != null) {
            terrainCollisionManager.resetState();
        }
        if (parallaxManager != null) {
            parallaxManager.resetState();
        }
        if (waterSystem != null) {
            waterSystem.reset();
        }
        if (fadeManager != null) {
            fadeManager.cancel();
        }
        if (gameStateManager != null) {
            gameStateManager.resetState();
        }
        if (timerManager != null) {
            timerManager.resetState();
        }
        if (camera != null) {
            camera.resetState();
        }
    }

    /**
     * Resets session-progress counters to "fresh gameplay" defaults — score,
     * rings, lives, emeralds, timer, and (via LevelManager) checkpoint state.
     * Per the runtime ownership migration design
     * (docs/superpowers/specs/2026-04-07-runtime-ownership-migration-design.md),
     * editor exit must reinitialize gameplay session state as fresh gameplay,
     * not resumed state. Call this from the exit-editor flow after a new
     * gameplay mode context is wired up.
     */
    public void initializeFreshGameplayState() {
        if (gameStateManager != null) {
            gameStateManager.resetState();
        }
        if (timerManager != null) {
            timerManager.resetState();
        }
        if (levelManager != null) {
            com.openggf.game.RespawnState checkpoint = levelManager.getCheckpointState();
            if (checkpoint != null) {
                checkpoint.clear();
            }
        }
    }
}
