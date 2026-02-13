package com.openggf.game;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.profile.ProfileGenerator;
import uk.co.jamesj999.sonic.game.profile.ProfileLoader;
import uk.co.jamesj999.sonic.game.profile.RomAddressResolver;
import uk.co.jamesj999.sonic.game.profile.RomChecksumUtil;
import uk.co.jamesj999.sonic.game.profile.RomProfile;
import uk.co.jamesj999.sonic.game.profile.scanner.RomPatternScanner;
import uk.co.jamesj999.sonic.game.profile.scanner.ScanResult;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2GameModule;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
     * Initializes the {@link RomAddressResolver} with profile-based and default addresses,
     * then runs the ROM pattern scanner to discover additional addresses. The scanner fills
     * gaps between profile and default layers without overriding profile values.
     *
     * @param rom    the loaded ROM (used for checksum computation and scanning)
     * @param module the active game module (provides default offsets and scan patterns)
     */
    private static void initializeResolver(Rom rom, GameModule module) {
        try {
            // Build defaults from the module's hardcoded constants
            Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(module.getDefaultOffsets());

            // Attempt profile loading: user override file first, then classpath by checksum
            RomProfile profile = null;
            byte[] romBytes = null;
            try {
                ProfileLoader loader = new ProfileLoader();

                // 1. Try user override: <rom-path>.profile.json next to the ROM file
                String romPath = rom.getFilePath();
                if (romPath != null) {
                    Path userOverridePath = Path.of(romPath + ".profile.json");
                    profile = loader.loadFromFile(userOverridePath);
                    if (profile != null) {
                        LOGGER.info("Loaded user override profile: " + userOverridePath);
                    }
                }

                // 2. Fall back to shipped classpath profile by ROM checksum
                if (profile == null) {
                    romBytes = rom.readAllBytes();
                    String checksum = RomChecksumUtil.sha256(romBytes);
                    LOGGER.info("ROM checksum: " + checksum);

                    profile = loader.loadFromClasspath(checksum);
                    if (profile != null) {
                        LOGGER.info("Loaded shipped profile: " + profile.getMetadata().name());
                    } else {
                        LOGGER.fine("No shipped profile found for checksum: " + checksum);
                    }
                } else {
                    // Still need romBytes for the scanner below
                    romBytes = rom.readAllBytes();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to load profile, using defaults only", e);
            }

            // Initialize the resolver with profile + defaults
            RomAddressResolver resolver = RomAddressResolver.getInstance();
            resolver.initialize(profile, defaults);

            // Run pattern scanner to discover additional addresses
            if (romBytes != null) {
                try {
                    RomPatternScanner scanner = new RomPatternScanner();
                    module.registerScanPatterns(scanner);

                    Set<String> alreadyResolved = resolver.getResolvedKeys();
                    Map<String, ScanResult> scanResults = scanner.scan(romBytes, alreadyResolved);

                    if (!scanResults.isEmpty()) {
                        Map<String, Integer> scannedAddresses = new LinkedHashMap<>();
                        for (Map.Entry<String, ScanResult> entry : scanResults.entrySet()) {
                            scannedAddresses.put(entry.getKey(), entry.getValue().value());
                        }
                        resolver.addScannedAddresses(scannedAddresses);
                        LOGGER.info("ROM scanner discovered " + scanResults.size() + " address(es)");
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "ROM pattern scan failed, continuing with profile/defaults", e);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize ROM address resolver", e);
        }
    }
}

