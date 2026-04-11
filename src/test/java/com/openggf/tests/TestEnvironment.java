package com.openggf.tests;

import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameModule;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.EngineServices;
import com.openggf.game.GameServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.InitStep;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.RuntimeManager;
import com.openggf.game.StaticFixup;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.tests.rules.SonicGame;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;

/**
 * Centralized test state reset. Called before each annotated test
 * (via the active test fixture)
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
        resetToBootstrapBaseline();

        // Ensure a runtime exists after reset so GameServices and
        // DefaultObjectServices can delegate through RuntimeManager.
        RuntimeManager.createGameplay();
    }

    /**
     * Rebuilds the gameplay runtime around the module selected from the target ROM
     * and installs that ROM into the shared {@link RomManager}.
     */
    public static void configureRomFixture(Rom rom) {
        Objects.requireNonNull(rom, "rom");

        resetAll();
        GameModuleRegistry.detectAndSetModule(rom);
        recreateGameplayRuntime();
        RomManager.getInstance().setRom(rom);
    }

    /**
     * Rebuilds the gameplay runtime around an explicitly selected module without
     * requiring a real ROM.
     */
    public static void configureGameModuleFixture(GameModule module) {
        Objects.requireNonNull(module, "module");

        resetAll();
        GameModuleRegistry.setCurrent(module);
        recreateGameplayRuntime();
    }

    /**
     * Rebuilds the gameplay runtime around a requested game module selected by
     * test enum, constructing the module only after engine services are ready.
     */
    public static void configureGameModuleFixture(SonicGame game) {
        Objects.requireNonNull(game, "game");

        resetAll();
        GameModuleRegistry.setCurrent(moduleFor(game));
        recreateGameplayRuntime();
    }

    private static void resetToBootstrapBaseline() {
        SonicConfigurationService.getInstance().resetToDefaults();
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());

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
    }

    private static void recreateGameplayRuntime() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        RuntimeManager.createGameplay();
    }

    /**
     * Resets per-test state without touching the loaded level data or game module.
     */
    public static void resetPerTest() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
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

    public static Rom currentRom() {
        try {
            return GameServices.rom().getRom();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read active test ROM", e);
        }
    }

    private static GameModule moduleFor(SonicGame game) {
        return switch (game) {
            case SONIC_1 -> new Sonic1GameModule();
            case SONIC_2 -> new Sonic2GameModule();
            case SONIC_3K -> new Sonic3kGameModule();
        };
    }
}


