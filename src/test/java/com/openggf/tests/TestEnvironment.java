package com.openggf.tests;

import com.openggf.GameContext;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.tests.rules.RequiresRomRule;

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
        GameContext.forTesting();
    }

    /**
     * Resets per-test state without touching the loaded level data or game module.
     * <p>
     * Uses the current game's {@link LevelInitProfile#perTestResetSteps()} to
     * clear transient gameplay state (event routines, sprites, camera, collision,
     * fade, game-state counters, timers, water) so each test starts from a clean
     * slate, but preserves:
     * <ul>
     *   <li>Game module registration</li>
     *   <li>Audio manager state (ROM-specific SMPS loader cache)</li>
     *   <li>Level manager data (level layout, chunks, patterns)</li>
     *   <li>Graphics manager state (OpenGL/shader initialization)</li>
     *   <li>GroundSensor static reference (stays valid)</li>
     * </ul>
     */
    public static void resetPerTest() {
        LevelInitProfile profile = GameModuleRegistry.getCurrent().getLevelInitProfile();
        for (InitStep step : profile.perTestResetSteps()) {
            step.execute();
        }
    }
}
