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
import com.openggf.game.rewind.EngineStepper;
import com.openggf.game.rewind.InMemoryKeyframeStore;
import com.openggf.game.rewind.InputSource;
import com.openggf.game.rewind.PlaybackController;
import com.openggf.game.rewind.RewindController;
import com.openggf.game.rewind.RewindRegistry;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.rewind.snapshot.OscillationStaticAdapter;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.rings.RingManager;
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

    private RewindRegistry rewindRegistry;
    private RewindController rewindController;
    private PlaybackController playbackController;

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

        this.rewindRegistry = new RewindRegistry();
        this.rewindRegistry.register(camera);
        this.rewindRegistry.register(gameStateManager);
        this.rewindRegistry.register(rng);
        this.rewindRegistry.register(timerManager);
        this.rewindRegistry.register(fadeManager);
        this.rewindRegistry.register(new OscillationStaticAdapter());
        // Register solid-execution adapter (no-op if not DefaultSolidExecutionRegistry)
        if (solidExecutionRegistry instanceof DefaultSolidExecutionRegistry dser) {
            this.rewindRegistry.register(dser);
        }
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

        if (rewindRegistry != null) {
            rewindRegistry.deregister("parallax");
            rewindRegistry.deregister("water");
            rewindRegistry.register(parallaxManager);
            rewindRegistry.register(waterSystem);
        }
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

        if (rewindRegistry != null) {
            rewindRegistry.deregister("zone-runtime");
            rewindRegistry.deregister("palette-ownership");
            rewindRegistry.deregister("animated-tile-channels");
            rewindRegistry.deregister("special-render");
            rewindRegistry.deregister("advanced-render-mode");
            rewindRegistry.deregister("mutation-pipeline");
            rewindRegistry.register(zoneRuntimeRegistry);
            rewindRegistry.register(paletteOwnershipRegistry);
            rewindRegistry.register(animatedTileChannelGraph);
            rewindRegistry.register(specialRenderEffectRegistry);
            rewindRegistry.register(advancedRenderModeController);
            rewindRegistry.register(zoneLayoutMutationPipeline);
        }
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

    // ── Rewind framework ─────────────────────────────────────────────────

    /**
     * Returns the {@link RewindRegistry} for this gameplay session. The six
     * always-available atomic adapters (camera, game-state, rng, timers,
     * fade, oscillation) are registered automatically by
     * {@link #attachGameplayManagers}. Level and object-manager adapters are
     * added post-load via {@link #registerLevelAdapters}.
     */
    public RewindRegistry getRewindRegistry() {
        return rewindRegistry;
    }

    /**
     * Registers (or re-registers) the level and object-manager adapters with
     * the rewind registry. Safe to call multiple times — existing entries are
     * deregistered first to avoid duplicate-key errors.
     * <p>
     * Must be called by {@link LevelManager} after both the level data and
     * the {@link com.openggf.level.objects.ObjectManager} are ready (i.e.
     * after {@code initObjectSystem()} completes). If
     * {@code levelManager.getObjectManager()} is null the object-manager
     * adapter is skipped.
     */
    public void registerLevelAdapters(LevelManager levelManager) {
        if (rewindRegistry == null) {
            return;
        }
        rewindRegistry.deregister("level");
        rewindRegistry.deregister("object-manager");
        rewindRegistry.deregister("level-event");
        rewindRegistry.register(levelManager.levelRewindSnapshottable());
        if (levelManager.getObjectManager() != null) {
            rewindRegistry.register(levelManager.getObjectManager().rewindSnapshottable());
        }
        // Register level-event manager adapter (available after gameModule is set).
        if (levelManager.getGameModule() != null) {
            LevelEventProvider lep = levelManager.getGameModule().getLevelEventProvider();
            if (lep instanceof AbstractLevelEventManager alem) {
                rewindRegistry.register(alem);
            }
        }
    }

    /**
     * Registers the {@link RingManager} rewind adapter after ring data is
     * available (Phase H of level load, after {@link #registerLevelAdapters}).
     * Safe to call with a null argument — it is silently ignored.
     */
    public void registerRingAdapter(RingManager ringManager) {
        if (rewindRegistry == null || ringManager == null) {
            return;
        }
        rewindRegistry.deregister("rings");
        rewindRegistry.register(ringManager);
    }

    /**
     * Registers a {@link com.openggf.level.animation.AnimatedPatternManager} that also
     * implements {@link com.openggf.game.rewind.RewindSnapshottable} with the rewind
     * registry. Called from {@link com.openggf.level.LevelManager#initAnimatedContent()}.
     * Safe to call with a null argument — it is silently ignored.
     */
    public void registerPatternAnimatorAdapter(
            com.openggf.level.animation.AnimatedPatternManager mgr) {
        if (rewindRegistry == null || mgr == null) {
            return;
        }
        if (mgr instanceof com.openggf.game.rewind.RewindSnapshottable<?> snap) {
            rewindRegistry.deregister("pattern-animator");
            rewindRegistry.register(snap);
        }
    }

    /**
     * Constructs and installs a {@link RewindController} and
     * {@link PlaybackController} backed by this context's registry. Replaces
     * any previously installed controllers.
     *
     * @throws IllegalStateException if {@link #attachGameplayManagers} has
     *         not been called yet (registry is null)
     */
    public PlaybackController installPlaybackController(
            InputSource inputs,
            EngineStepper stepper,
            int keyframeInterval) {
        if (rewindRegistry == null) {
            throw new IllegalStateException(
                    "rewindRegistry not initialised — call attachGameplayManagers first");
        }
        this.rewindController = new RewindController(
                rewindRegistry,
                new InMemoryKeyframeStore(),
                inputs,
                stepper,
                keyframeInterval);
        this.playbackController = new PlaybackController(rewindController);
        return playbackController;
    }

    /** Returns the installed {@link RewindController}, or {@code null} if not yet installed. */
    public RewindController getRewindController() {
        return rewindController;
    }

    /** Returns the installed {@link PlaybackController}, or {@code null} if not yet installed. */
    public PlaybackController getPlaybackController() {
        return playbackController;
    }

    // ── Bonus stage provider ─────────────────────────────────────────────

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
        // Manager teardown is driven by GameRuntime.destroy() via
        // tearDownManagers() rather than this method, because the editor flow
        // calls SessionManager.destroyCurrentMode() (which routes here) when
        // entering editor mode while the runtime is parked — at that point
        // the parked runtime still expects its managers to be alive on resume.
        // Once parking is replaced by a proper world-preserving teardown, the
        // distinction collapses and tearDownManagers() can become this method
        // body directly.
    }

    /**
     * Tears down all attached managers in reverse construction order.
     * Idempotent: each manager's reset is a no-op when its field is null
     * (e.g., when destroy is invoked during a partial setup). Called by
     * {@link com.openggf.game.GameRuntime#destroy()} only — see {@link #destroy()}
     * for why {@code SessionManager.destroyCurrentMode} does not trigger
     * this teardown.
     */
    public void tearDownManagers() {
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
        rewindController = null;
        playbackController = null;
        rewindRegistry = null;
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
