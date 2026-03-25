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

    private RuntimeManager() {}

    /**
     * Returns the current gameplay runtime, or {@code null} if none exists
     * (e.g. during master title screen before any game is loaded).
     */
    public static GameRuntime getCurrent() {
        return current;
    }

    /**
     * Sets the current runtime. Package-private for testing;
     * production code should use {@link #createGameplay()}.
     */
    public static synchronized void setCurrent(GameRuntime runtime) {
        current = runtime;
    }

    /**
     * Creates a new gameplay runtime from the current singletons and sets it
     * as current. This is the transitional factory — once singletons are removed,
     * this method will construct managers directly.
     *
     * @return the newly created runtime
     */
    public static synchronized GameRuntime createGameplay() {
        Camera camera = Camera.getInstance();
        TimerManager timers = TimerManager.getInstance();
        GameStateManager gameState = GameStateManager.getInstance();
        FadeManager fadeManager = FadeManager.getInstance();
        WaterSystem waterSystem = WaterSystem.getInstance();
        ParallaxManager parallaxManager = ParallaxManager.getInstance();
        TerrainCollisionManager terrainCollisionManager = TerrainCollisionManager.getInstance();
        CollisionSystem collisionSystem = CollisionSystem.getInstance();
        SpriteManager spriteManager = SpriteManager.getInstance();
        LevelManager levelManager = LevelManager.getInstance();

        GameRuntime runtime = new GameRuntime(camera, timers, gameState, fadeManager,
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
    }
}
