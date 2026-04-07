package com.openggf.game;

import com.openggf.data.Rom;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * Central registry for the current game module.
 *
 * <p>The game module defines game-specific behavior including:
 * <ul>
 *   <li>Object registry and IDs</li>
 *   <li>Audio profile and sound mappings</li>
 *   <li>Zone registry and level data</li>
 *   <li>Special stage and bonus stage providers</li>
 *   <li>Scroll handlers and zone features</li>
 * </ul>
 *
 * <p>Use {@link #detectAndSetModule(Rom)} to auto-detect the game from a ROM,
 * or {@link #setCurrent(GameModule)} to set it manually.
 */
public final class GameModuleRegistry {
    private static final Logger LOGGER = Logger.getLogger(GameModuleRegistry.class.getName());

    // Default to Sonic 2 for backward compatibility during bootstrap before a world session exists.
    private static GameModule bootstrapDefault = new Sonic2GameModule();

    private GameModuleRegistry() {
    }

    /**
     * Gets the currently active game module.
     *
     * @return the current game module
     */
    public static synchronized GameModule getCurrent() {
        if (SessionManager.getCurrentWorldSession() != null) {
            return SessionManager.requireCurrentGameModule();
        }
        return bootstrapDefault;
    }

    /**
     * Sets the bootstrap default game module used before a world session exists.
     *
     * @param module the module to set as current (ignored if null)
     */
    public static synchronized void setCurrent(GameModule module) {
        if (module != null) {
            LOGGER.info("Setting bootstrap game module: " + module.getIdentifier());
            bootstrapDefault = module;
            GameStateManager.getInstance().configureSpecialStageProgress(
                    module.getSpecialStageCycleCount(),
                    module.getChaosEmeraldCount());
        }
    }

    /**
     * Auto-detects the game from the ROM and sets the appropriate module.
     * Falls back to Sonic 2 if detection fails.
     *
     * @param rom the ROM to detect
     * @return true if detection succeeded, false if using fallback
     */
    public static boolean detectAndSetModule(Rom rom) {
        Optional<GameModule> detectedModule = RomDetectionService.getInstance().detectAndCreateModule(rom);
        if (detectedModule.isPresent()) {
            setCurrent(detectedModule.get());
            return true;
        }

        LOGGER.warning("ROM detection failed, using default Sonic 2 module");
        setCurrent(new Sonic2GameModule());
        return false;
    }

    /**
     * Resets the registry to the default Sonic 2 module.
     * Useful for testing or reinitialization.
     */
    public static void reset() {
        bootstrapDefault = new Sonic2GameModule();
        LOGGER.fine("Game module registry reset to Sonic 2 default");
    }
}
