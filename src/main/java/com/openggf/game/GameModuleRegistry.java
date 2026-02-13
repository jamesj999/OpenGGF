package com.openggf.game;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.profile.ProfileGenerator;
import uk.co.jamesj999.sonic.game.profile.ProfileLoader;
import uk.co.jamesj999.sonic.game.profile.RomAddressResolver;
import uk.co.jamesj999.sonic.game.profile.RomChecksumUtil;
import uk.co.jamesj999.sonic.game.profile.RomProfile;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2GameModule;

import java.util.Map;
import java.util.logging.Level;
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

    // Default to Sonic 2 for backward compatibility
    private static GameModule current = new Sonic2GameModule();

    private GameModuleRegistry() {
    }

    /**
     * Gets the currently active game module.
     *
     * @return the current game module
     */
    public static synchronized GameModule getCurrent() {
        return current;
    }

    /**
     * Sets the current game module.
     *
     * @param module the module to set as current (ignored if null)
     */
    public static synchronized void setCurrent(GameModule module) {
        if (module != null) {
            LOGGER.info("Setting game module: " + module.getIdentifier());
            current = module;
            GameStateManager.getInstance().configureSpecialStageProgress(
                    module.getSpecialStageCycleCount(),
                    module.getChaosEmeraldCount());
        }
    }

    /**
     * Auto-detects the game from the ROM and sets the appropriate module.
     * Falls back to Sonic 2 if detection fails. After module selection,
     * initializes the {@link RomAddressResolver} with profile and default addresses.
     *
     * @param rom the ROM to detect
     * @return true if detection succeeded, false if using fallback
     */
    public static boolean detectAndSetModule(Rom rom) {
        boolean detected = RomDetectionService.getInstance().detectAndSetModule(rom);
        if (!detected) {
            LOGGER.warning("ROM detection failed, using default Sonic 2 module");
            setCurrent(new Sonic2GameModule());
        }
        initializeResolver(rom, current);
        return detected;
    }

    /**
     * Resets the registry to the default Sonic 2 module.
     * Useful for testing or reinitialization.
     */
    public static void reset() {
        setCurrent(new Sonic2GameModule());
        LOGGER.fine("Game module registry reset to Sonic 2 default");
    }

    /**
     * Initializes the {@link RomAddressResolver} with profile-based and default addresses.
     * Computes the ROM's SHA-256 checksum, attempts to load a matching shipped profile
     * from the classpath, and falls back to the module's hardcoded defaults.
     *
     * @param rom    the loaded ROM (used for checksum computation)
     * @param module the active game module (provides default offsets)
     */
    private static void initializeResolver(Rom rom, GameModule module) {
        try {
            // Build defaults from the module's hardcoded constants
            Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(module.getDefaultOffsets());

            // Attempt profile loading by ROM checksum
            RomProfile profile = null;
            try {
                byte[] romBytes = rom.readAllBytes();
                String checksum = RomChecksumUtil.sha256(romBytes);
                LOGGER.info("ROM checksum: " + checksum);

                ProfileLoader loader = new ProfileLoader();
                profile = loader.loadFromClasspath(checksum);
                if (profile != null) {
                    LOGGER.info("Loaded shipped profile: " + profile.getMetadata().name());
                } else {
                    LOGGER.fine("No shipped profile found for checksum: " + checksum);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to compute ROM checksum or load profile, using defaults only", e);
            }

            // Initialize the resolver with whatever we have
            RomAddressResolver.getInstance().initialize(profile, defaults);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize ROM address resolver", e);
        }
    }
}

