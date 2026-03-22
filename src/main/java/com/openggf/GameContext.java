package com.openggf;

import com.openggf.camera.Camera;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.StaticFixup;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
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

    private static int generation = 0;

    private final int capturedGeneration;
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
        this.capturedGeneration = generation;
        this.camera = camera;
        this.levelManager = levelManager;
        this.spriteManager = spriteManager;
        this.collisionSystem = collisionSystem;
        this.graphicsManager = graphicsManager;
        this.timerManager = timerManager;
        this.waterSystem = waterSystem;
    }

    private void checkFresh() {
        if (capturedGeneration != generation) {
            throw new IllegalStateException(
                    "Stale GameContext: captured generation " + capturedGeneration
                    + " but current generation is " + generation
                    + ". Do not hold GameContext references across forTesting() calls.");
        }
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
     * Resets all critical singletons using the current game's
     * {@link LevelInitProfile}, then returns a fresh production context.
     * <p>
     * The profile is captured from the CURRENT game module before
     * {@link GameModuleRegistry#reset()} is called, ensuring each game's
     * teardown cleans up its own state (S1 resets S1 event manager,
     * S3K resets AizPlaneIntroInstance, etc.).
     * <p>
     * <b>Warning:</b> Callers should not hold references to singleton instances
     * across a {@code forTesting()} call. {@link CollisionSystem} and
     * {@link FadeManager} are destroyed and recreated (via {@code resetInstance()}),
     * so any previously captured references become stale.
     */
    public static GameContext forTesting() {
        // Invalidate any previously issued GameContext instances.
        generation++;

        // CRITICAL: Capture the current game's profile BEFORE resetting the module.
        // After reset(), the module reverts to Sonic2GameModule (the default).
        // We need the PREVIOUS game's teardown to clean up its own state.
        LevelInitProfile profile = GameModuleRegistry.getCurrent().getLevelInitProfile();

        // Phase 0: Reset game module (shared across all games)
        GameModuleRegistry.reset();

        // Execute game-specific teardown steps (replaces phases 2-8)
        for (InitStep step : profile.levelTeardownSteps()) {
            step.execute();
        }

        // Apply static fixups (replaces Phase 8)
        for (StaticFixup fixup : profile.postTeardownFixups()) {
            fixup.apply();
        }

        return production();
    }

    public Camera camera() { checkFresh(); return camera; }
    public LevelManager levelManager() { checkFresh(); return levelManager; }
    public SpriteManager spriteManager() { checkFresh(); return spriteManager; }
    public CollisionSystem collisionSystem() { checkFresh(); return collisionSystem; }
    public GraphicsManager graphicsManager() { checkFresh(); return graphicsManager; }
    public TimerManager timerManager() { checkFresh(); return timerManager; }
    public WaterSystem waterSystem() { checkFresh(); return waterSystem; }
}
