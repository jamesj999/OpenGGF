package com.openggf;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

/**
 * Plain holder bundling critical-path managers for production and test use.
 * <p>
 * Two factory methods:
 * <ul>
 *   <li>{@link #production()} - wraps existing singletons (zero state change)</li>
 *   <li>{@link #forTesting()} - resets all critical singletons in correct order,
 *       then returns a production context</li>
 * </ul>
 */
public final class GameContext {

    private final Camera camera;
    private final LevelManager levelManager;
    private final SpriteManager spriteManager;
    private final CollisionSystem collisionSystem;
    private final GraphicsManager graphicsManager;
    private final TimerManager timerManager;
    private final WaterSystem waterSystem;

    private GameContext(Camera camera, LevelManager levelManager,
                        SpriteManager spriteManager, CollisionSystem collisionSystem,
                        GraphicsManager graphicsManager, TimerManager timerManager,
                        WaterSystem waterSystem) {
        this.camera = camera;
        this.levelManager = levelManager;
        this.spriteManager = spriteManager;
        this.collisionSystem = collisionSystem;
        this.graphicsManager = graphicsManager;
        this.timerManager = timerManager;
        this.waterSystem = waterSystem;
    }

    /**
     * Wraps the current singletons without changing any state.
     */
    public static GameContext production() {
        return new GameContext(
                Camera.getInstance(),
                LevelManager.getInstance(),
                SpriteManager.getInstance(),
                CollisionSystem.getInstance(),
                GraphicsManager.getInstance(),
                TimerManager.getInstance(),
                WaterSystem.getInstance()
        );
    }

    /**
     * Resets all critical singletons in the correct order (matching
     * {@code TestEnvironment.resetAll()}), then returns a fresh production context.
     */
    public static GameContext forTesting() {
        // Phase 1: Game module (affects what other singletons do)
        GameModuleRegistry.reset();

        // Phase 2: Audio (clears ROM-specific SMPS loader cache)
        AudioManager.getInstance().resetState();

        // Phase 3: Level subsystems
        Sonic2LevelEventManager.getInstance().resetState();
        ParallaxManager.getInstance().resetState();
        LevelManager.getInstance().resetState();

        // Phase 4: Sprites
        SpriteManager.getInstance().resetState();

        // Phase 5: Physics
        CollisionSystem.resetInstance();

        // Phase 6: Camera and graphics
        Camera.getInstance().resetState();
        GraphicsManager.getInstance().resetState();
        FadeManager.resetInstance();

        // Phase 7: Game state and timers
        GameServices.gameState().resetSession();
        TimerManager.getInstance().resetState();
        WaterSystem.getInstance().reset();

        // Phase 8: Static field fixups
        GroundSensor.setLevelManager(LevelManager.getInstance());
        AizPlaneIntroInstance.setSidekickSuppressed(false);

        return production();
    }

    public Camera camera() { return camera; }
    public LevelManager levelManager() { return levelManager; }
    public SpriteManager spriteManager() { return spriteManager; }
    public CollisionSystem collisionSystem() { return collisionSystem; }
    public GraphicsManager graphicsManager() { return graphicsManager; }
    public TimerManager timerManager() { return timerManager; }
    public WaterSystem waterSystem() { return waterSystem; }
}
