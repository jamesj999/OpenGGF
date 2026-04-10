package com.openggf.tests;

import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.RuntimeManager;
import com.openggf.game.StaticFixup;
import com.openggf.game.session.SessionManager;
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
     * <p>
     * Replaces the former {@code GameContext.forTesting()} method.
     */
    public static void resetAll() {
        SonicConfigurationService.getInstance().resetToDefaults();

        // CRITICAL: Capture the current game's profile BEFORE resetting the module.
        // After reset(), the module reverts to Sonic2GameModule (the default).
        // We need the PREVIOUS game's teardown to clean up its own state.
        LevelInitProfile profile = GameModuleRegistry.getCurrent().getLevelInitProfile();

        RuntimeManager.destroyCurrent();
        SessionManager.clear();

        // Phase 0: Reset game module (shared across all games)
        GameModuleRegistry.reset();

        // Execute game-specific teardown steps
        for (InitStep step : profile.levelTeardownSteps()) {
            step.execute();
        }

        // Apply static fixups
        for (StaticFixup fixup : profile.postTeardownFixups()) {
            fixup.apply();
        }

        // Ensure a runtime exists after reset so GameServices and
        // DefaultObjectServices can delegate through RuntimeManager.
        RuntimeManager.createGameplay();
    }

    /**
     * Resets per-test state without touching the loaded level data or game module.
     */
    public static void resetPerTest() {
        // Ensure a runtime exists so GameServices can delegate through RuntimeManager.
        // The first test in a JVM fork may not have run resetAll() yet.
        if (RuntimeManager.getCurrent() == null) {
            RuntimeManager.createGameplay();
        }
        LevelInitProfile profile = GameModuleRegistry.getCurrent().getLevelInitProfile();
        for (InitStep step : profile.perTestResetSteps()) {
            step.execute();
        }
    }
}
