package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
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
    private static GameRuntime parked;
    private static GameplayModeContext suppressedGameplayMode;
    private static EngineServices engineServices = EngineServices.fromLegacySingletonsForBootstrap();

    private RuntimeManager() {}

    public static synchronized void configureEngineServices(EngineServices services) {
        engineServices = Objects.requireNonNull(services, "services");
    }

    public static synchronized EngineServices getEngineServices() {
        return engineServices;
    }

    public static synchronized EngineServices currentEngineServices() {
        return getEngineServices();
    }

    /**
     * Returns the current gameplay runtime, or {@code null} if none exists
     * (e.g. during master title screen before any game is loaded).
     */
    public static synchronized GameRuntime getCurrent() {
        return getCurrent(engineServices);
    }

    public static synchronized GameRuntime getCurrent(EngineServices services) {
        Objects.requireNonNull(services, "services");
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (current != null) {
            if (current.getGameplayModeContext() != gameplayMode) {
                current.destroy();
                current = null;
            } else {
                return current;
            }
        }
        if (gameplayMode != null && gameplayMode != suppressedGameplayMode) {
            return createGameplay(gameplayMode, services);
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
        destroyParkedRuntimeIfSupersededBy(runtime);
        current = runtime;
        suppressedGameplayMode = runtime == null ? SessionManager.getCurrentGameplayMode() : null;
    }

    /**
     * Creates a new gameplay runtime from a freshly constructed manager graph
     * and sets it as current.
     *
     * @return the newly created runtime
     */
    public static synchronized GameRuntime createGameplay() {
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (gameplayMode == null) {
            GameModule defaultModule = GameModuleRegistry.getCurrent();
            gameplayMode = SessionManager.openGameplaySession(defaultModule);
        }
        return createGameplay(gameplayMode);
    }

    /**
     * Creates a new gameplay runtime bound to the provided gameplay mode context
     * and sets it as current.
     *
     * @param gameplayMode active gameplay mode context backing the runtime
     * @return the newly created runtime
     */
    public static synchronized GameRuntime createGameplay(GameplayModeContext gameplayMode) {
        return createGameplay(gameplayMode, engineServices);
    }

    public static synchronized GameRuntime createGameplay(GameplayModeContext gameplayMode, EngineServices services) {
        Objects.requireNonNull(services, "services");
        if (gameplayMode == null) {
            throw new NullPointerException("gameplayMode");
        }
        destroyParkedRuntimeIfSupersededBy(null);
        suppressedGameplayMode = null;
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
        WaterSystem waterSystem = new WaterSystem();
        ParallaxManager parallaxManager = new ParallaxManager();
        TerrainCollisionManager terrainCollisionManager = new TerrainCollisionManager();
        CollisionSystem collisionSystem = new CollisionSystem(terrainCollisionManager);
        SpriteManager spriteManager = new SpriteManager();
        LevelManager levelManager = new LevelManager(
                camera, spriteManager, parallaxManager, collisionSystem, waterSystem, gameState);
        GameModule currentModule = GameModuleRegistry.getCurrent();
        GameRng rng = new GameRng(currentModule != null
                ? currentModule.rngFlavour()
                : GameRng.Flavour.S1_S2);

        GameRuntime runtime = new GameRuntime(services, gameplayMode.getWorldSession(), gameplayMode,
                camera, timers, gameState, fadeManager,
                waterSystem, parallaxManager, terrainCollisionManager,
                collisionSystem, spriteManager, levelManager, rng);
        current = runtime;
        return runtime;
    }

    /**
     * Detaches the active gameplay runtime without destroying it so editor mode can take over.
     */
    public static synchronized void parkCurrent() {
        if (current == null) {
            return;
        }
        parked = current;
        current = null;
        suppressedGameplayMode = SessionManager.getCurrentGameplayMode();
    }

    /**
     * Rebinds a previously parked runtime to the resumed gameplay mode, or creates a fresh runtime
     * if nothing is parked.
     */
    public static synchronized GameRuntime resumeParked(GameplayModeContext gameplayMode) {
        if (parked == null) {
            return createGameplay(gameplayMode);
        }
        if (parked.getWorldSession() != gameplayMode.getWorldSession()) {
            destroyParkedRuntimeIfSupersededBy(null);
            return createGameplay(gameplayMode);
        }
        parked.updateGameplayModeContext(gameplayMode);
        current = parked;
        parked = null;
        suppressedGameplayMode = null;
        return current;
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
        destroyParkedRuntimeIfSupersededBy(null);
        suppressedGameplayMode = SessionManager.getCurrentGameplayMode();
    }

    private static void destroyParkedRuntimeIfSupersededBy(GameRuntime replacement) {
        if (parked != null && parked != replacement) {
            parked.destroy();
        }
        if (parked != replacement) {
            parked = null;
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
}
