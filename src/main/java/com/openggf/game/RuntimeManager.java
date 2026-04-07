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
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

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
    private static GameplayModeContext suppressedGameplayMode;

    private RuntimeManager() {}

    /**
     * Returns the current gameplay runtime, or {@code null} if none exists
     * (e.g. during master title screen before any game is loaded).
     */
    public static synchronized GameRuntime getCurrent() {
        GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
        if (current != null) {
            if (gameplayMode != null && current.getGameplayModeContext() != gameplayMode) {
                current.destroy();
                current = null;
            } else {
                return current;
            }
        }
        if (gameplayMode != null && gameplayMode != suppressedGameplayMode) {
            return createGameplay(gameplayMode);
        }
        return current;
    }

    /**
     * Sets the current runtime. Package-private for testing;
     * production code should use {@link #createGameplay()}.
     */
    public static synchronized void setCurrent(GameRuntime runtime) {
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
            GameModule defaultModule = new Sonic2GameModule();
            GameModuleRegistry.setCurrent(defaultModule);
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
        if (gameplayMode == null) {
            throw new NullPointerException("gameplayMode");
        }
        suppressedGameplayMode = null;
        Camera camera = new Camera();
        TimerManager timers = new TimerManager();
        GameStateManager gameState = new GameStateManager();
        FadeManager fadeManager = new FadeManager();
        WaterSystem waterSystem = new WaterSystem();
        ParallaxManager parallaxManager = new ParallaxManager();
        TerrainCollisionManager terrainCollisionManager = new TerrainCollisionManager();
        CollisionSystem collisionSystem = new CollisionSystem(terrainCollisionManager);
        SpriteManager spriteManager = new SpriteManager();
        LevelManager levelManager = new LevelManager(
                camera, spriteManager, parallaxManager, collisionSystem, waterSystem, gameState);

        GameRuntime runtime = new GameRuntime(gameplayMode.getWorldSession(), gameplayMode,
                camera, timers, gameState, fadeManager,
                waterSystem, parallaxManager, terrainCollisionManager,
                collisionSystem, spriteManager, levelManager);
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
        suppressedGameplayMode = SessionManager.getCurrentGameplayMode();
    }
}
