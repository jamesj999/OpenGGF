package uk.co.jamesj999.sonic.tests;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.LevelEventManager;
import uk.co.jamesj999.sonic.graphics.FadeManager;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.ParallaxManager;
import uk.co.jamesj999.sonic.level.WaterSystem;
import uk.co.jamesj999.sonic.physics.CollisionSystem;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.timer.TimerManager;

/**
 * Centralized test state reset. Called before each annotated test
 * (via {@link uk.co.jamesj999.sonic.tests.rules.RequiresRomRule})
 * to prevent singleton state from leaking between tests.
 */
public final class TestEnvironment {
    private TestEnvironment() {}

    /**
     * Resets all singleton state to a clean baseline.
     * Order matters: game module first (affects what other singletons do),
     * then subsystems from outer (audio, level) to inner (camera, timers).
     */
    public static void resetAll() {
        // Phase 1: Game module (affects what other singletons do)
        GameModuleRegistry.reset();

        // Phase 2: Audio (clears ROM-specific SMPS loader cache)
        AudioManager.getInstance().resetState();

        // Phase 3: Level subsystems
        LevelEventManager.getInstance().resetState();
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
    }
}
