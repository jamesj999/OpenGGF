package com.openggf.tests;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.timer.TimerManager;

/**
 * Centralized test state reset. Called before each annotated test
 * (via {@link RequiresRomRule})
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
    }

    /**
     * Resets per-test state without touching the loaded level data or game module.
     * <p>
     * Use this in {@code @Before} methods when the level and game module are
     * loaded once per class (via {@code @BeforeClass} / {@code @ClassRule}) and
     * shared across tests. It clears transient gameplay state (event routines,
     * sprites, camera, collision, fade, game-state counters, timers, water) so
     * each test starts from a clean slate, but preserves:
     * <ul>
     *   <li>{@link GameModuleRegistry} - game module stays loaded</li>
     *   <li>{@link AudioManager} - ROM-specific SMPS loader cache stays warm</li>
     *   <li>{@link LevelManager} - level layout, chunks, patterns stay loaded</li>
     *   <li>{@link GraphicsManager} - OpenGL/shader state stays initialized</li>
     *   <li>{@link GroundSensor#setLevelManager} - static reference stays valid</li>
     * </ul>
     */
    public static void resetPerTest() {
        // Level event state (boss routines, dynamic boundaries)
        Sonic2LevelEventManager.getInstance().resetState();
        ParallaxManager.getInstance().resetState();

        // Sprites (clears all registered sprites)
        SpriteManager.getInstance().resetState();

        // Physics (fresh collision system instance)
        CollisionSystem.resetInstance();

        // Camera and fade (position, freeze flag, fade state)
        Camera.getInstance().resetState();
        FadeManager.resetInstance();

        // Game state counters and timers
        GameServices.gameState().resetSession();
        TimerManager.getInstance().resetState();
        WaterSystem.getInstance().reset();
    }
}
