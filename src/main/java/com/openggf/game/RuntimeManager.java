package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.game.animation.AnimatedTileChannelGraph;
import com.openggf.game.mutation.ZoneLayoutMutationPipeline;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.game.solid.DefaultSolidExecutionRegistry;
import com.openggf.game.solid.SolidExecutionRegistry;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.zone.ZoneRuntimeRegistry;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

import java.util.Objects;

/**
 * Static holder for the current {@link GameRuntime}.
 * <p>
 * This is the <em>one true singleton</em> for gameplay state. All runtime-owned
 * managers are accessed through {@code RuntimeManager.getCurrent().getFoo()}
 * (or indirectly via {@link GameServices} which delegates here).
 * <p>
 * <b>Lifecycle:</b>
 * <ul>
 *   <li>{@code Engine.initializeGame()} calls {@link #createGameplay()}</li>
 *   <li>{@code GameLoop} reads from {@link #getCurrent()}</li>
 *   <li>{@code Engine.cleanup()} calls {@link #destroyCurrent()}</li>
 *   <li>Tests call {@link #destroyCurrent()} + {@link #createGameplay()} for reset</li>
 * </ul>
 */
public final class RuntimeManager {

    private static GameRuntime current;
    private static EngineServices engineServices;

    private RuntimeManager() {}

    public static synchronized void configureEngineServices(EngineServices services) {
        engineServices = Objects.requireNonNull(services, "services");
    }

    public static synchronized EngineServices getEngineServices() {
        return requireConfiguredEngineServices();
    }

    public static synchronized EngineServices currentEngineServices() {
        return requireConfiguredEngineServices();
    }

    /**
     * Returns the current gameplay runtime, or {@code null} if none exists
     * (e.g. during master title screen before any game is loaded).
     */
    public static synchronized GameRuntime getCurrent() {
        return getCurrent(requireConfiguredEngineServices());
    }

    public static synchronized GameRuntime getCurrent(EngineServices services) {
        Objects.requireNonNull(services, "services");
        // If the active gameplay mode has changed under us (e.g. session was
        // re-opened), drop the now-stale runtime. Otherwise return what's
        // there — callers must explicitly invoke createGameplay() to build
        // a fresh runtime; we no longer lazy-create on read, since that
        // mid-flow side effect can re-attach fresh managers (replacing
        // camera/sprite/etc) to a still-referenced gameplay mode and
        // surprise callers holding manager refs across the transition.
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (current != null
                && (current.getGameplayModeContext() != gameplayMode
                || current.getEngineServices() != services)) {
            current.destroy();
            current = null;
        }
        return current;
    }

    /**
     * Returns the currently active runtime without creating or rebinding one.
     */
    public static synchronized GameRuntime getActiveRuntime() {
        return current;
    }

    /**
     * Resolves the bootstrap/default gameplay module without creating or consulting
     * a runtime. This preserves registry-driven construction paths used before a
     * sprite is attached to gameplay state.
     */
    public static synchronized GameModule resolveBootstrapGameModule() {
        return GameModuleRegistry.getBootstrapDefault();
    }

    /**
     * Resolves the gameplay module for the active runtime when present, otherwise
     * falls back to the bootstrap default used by pre-runtime construction paths.
     */
    public static synchronized GameModule resolveCurrentOrBootstrapGameModule() {
        GameModule module = resolveModuleFromRuntime(current);
        return module != null ? module : resolveBootstrapGameModule();
    }

    /**
     * Sets the current runtime. Package-private for testing;
     * production code should use {@link #createGameplay()}.
     */
    public static synchronized void setCurrent(GameRuntime runtime) {
        current = runtime;
    }

    /**
     * Creates a new gameplay runtime from a freshly constructed manager graph
     * and sets it as current.
     *
     * @return the newly created runtime
     */
    public static synchronized GameRuntime createGameplay() {
        EngineServices services = requireConfiguredEngineServices();
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode == null) {
            GameModule defaultModule = GameModuleRegistry.getCurrent();
            gameplayMode = SessionManager.openGameplaySession(defaultModule);
        }
        return createGameplay(gameplayMode, services);
    }

    /**
     * Creates a new gameplay runtime bound to the provided gameplay mode context
     * and sets it as current.
     *
     * @param gameplayMode active gameplay mode context backing the runtime
     * @return the newly created runtime
     */
    public static synchronized GameRuntime createGameplay(GameplayModeContext gameplayMode) {
        return createGameplay(gameplayMode, requireConfiguredEngineServices());
    }

    public static synchronized GameRuntime createGameplay(GameplayModeContext gameplayMode, EngineServices services) {
        Objects.requireNonNull(services, "services");
        if (gameplayMode == null) {
            throw new NullPointerException("gameplayMode");
        }
        Camera camera = new Camera();
        TimerManager timers = new TimerManager();
        GameStateManager gameState = new GameStateManager();
        GameModule sessionModule = gameplayMode.getWorldSession() != null
                ? gameplayMode.getWorldSession().getGameModule()
                : null;
        if (sessionModule != null) {
            gameState.configureSpecialStageProgress(
                    sessionModule.getSpecialStageCycleCount(),
                    sessionModule.getChaosEmeraldCount());
        }
        FadeManager fadeManager = new FadeManager();
        GameModule currentModule = GameModuleRegistry.getCurrent();
        GameRng rng = new GameRng(currentModule != null
                ? currentModule.rngFlavour()
                : GameRng.Flavour.S1_S2);
        SolidExecutionRegistry solidExecutionRegistry = new DefaultSolidExecutionRegistry();
        gameplayMode.attachGameplayManagers(camera, timers, gameState, fadeManager, rng, solidExecutionRegistry);

        WaterSystem waterSystem = new WaterSystem();
        ParallaxManager parallaxManager = new ParallaxManager();
        TerrainCollisionManager terrainCollisionManager = new TerrainCollisionManager();
        CollisionSystem collisionSystem = new CollisionSystem(terrainCollisionManager);
        SpriteManager spriteManager = new SpriteManager();
        LevelManager levelManager = new LevelManager(
                camera, spriteManager, parallaxManager, collisionSystem, waterSystem, gameState, services,
                gameplayMode.getWorldSession());
        gameplayMode.attachLevelManagers(waterSystem, parallaxManager, terrainCollisionManager,
                collisionSystem, spriteManager, levelManager);

        ZoneRuntimeRegistry zoneRuntimeRegistry = new ZoneRuntimeRegistry();
        PaletteOwnershipRegistry paletteOwnershipRegistry = new PaletteOwnershipRegistry();
        AnimatedTileChannelGraph animatedTileChannelGraph = new AnimatedTileChannelGraph();
        SpecialRenderEffectRegistry specialRenderEffectRegistry = new SpecialRenderEffectRegistry();
        AdvancedRenderModeController advancedRenderModeController = new AdvancedRenderModeController();
        ZoneLayoutMutationPipeline zoneLayoutMutationPipeline = new ZoneLayoutMutationPipeline();
        gameplayMode.attachSharedRegistries(zoneRuntimeRegistry, paletteOwnershipRegistry,
                animatedTileChannelGraph, specialRenderEffectRegistry,
                advancedRenderModeController, zoneLayoutMutationPipeline);

        GameRuntime runtime = new GameRuntime(services, gameplayMode.getWorldSession(), gameplayMode);
        current = runtime;
        return runtime;
    }

    /**
     * Destroys the current runtime (calling {@link GameRuntime#destroy()})
     * and sets the current reference to {@code null}.
     */
    public static synchronized void destroyCurrent() {
        if (current != null) {
            current.destroy();
            current = null;
        }
    }

    private static GameModule resolveModuleFromRuntime(GameRuntime runtime) {
        if (runtime == null) {
            return null;
        }
        LevelManager levelManager = runtime.getLevelManager();
        if (levelManager != null && levelManager.getGameModule() != null) {
            return levelManager.getGameModule();
        }
        if (runtime.getWorldSession() != null && runtime.getWorldSession().getGameModule() != null) {
            return runtime.getWorldSession().getGameModule();
        }
        return null;
    }

    private static EngineServices requireConfiguredEngineServices() {
        if (engineServices == null) {
            throw new IllegalStateException(
                    "EngineServices have not been configured. Configure RuntimeManager before using default runtime accessors.");
        }
        return engineServices;
    }
}
