package com.openggf.data;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.game.GameModuleRegistry;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Singleton manager for ROM access across the engine.
 *
 * Provides centralized ROM lifecycle management:
 * - Opens the ROM once on first access
 * - Provides thread-safe access to ROM data
 * - Closes the ROM on engine shutdown
 *
 * Usage:
 * <pre>
 * Rom rom = GameServices.rom().getRom();
 * byte[] data = rom.readBytes(offset, length);
 * </pre>
 */
public class RomManager implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(RomManager.class.getName());

    private static RomManager instance;

    private final SonicConfigurationService configService = SonicConfigurationService.getInstance();
    private Rom rom;
    private boolean initialized = false;
    private final Map<String, Rom> secondaryRoms = new HashMap<>();

    private RomManager() {
    }

    /**
     * Gets the singleton instance of RomManager.
     */
    public static synchronized RomManager getInstance() {
        if (instance == null) {
            instance = new RomManager();
        }
        return instance;
    }

    /**
     * Gets the ROM instance, opening it if necessary.
     *
     * @return The open ROM instance
     * @throws IOException If the ROM cannot be opened
     */
    public synchronized Rom getRom() throws IOException {
        if (!initialized || rom == null || !rom.isOpen()) {
            openRom();
        }
        return rom;
    }

    /**
     * Injects a pre-opened ROM instance. Useful for tests that open
     * specific ROMs directly rather than relying on config resolution.
     */
    public synchronized void setRom(Rom rom) {
        // Do NOT close the previous ROM here — its lifecycle is managed
        // by whoever created it (e.g., RomCache in tests). Closing it here
        // would invalidate cached ROM instances shared across tests.
        this.rom = rom;
        this.initialized = rom != null && rom.isOpen();
    }

    /**
     * Checks if the ROM is currently open and available.
     */
    public synchronized boolean isRomAvailable() {
        return initialized && rom != null && rom.isOpen();
    }

    /**
     * Returns the resolved filesystem path of the currently loaded ROM,
     * or null if no ROM is loaded.
     *
     * @return the absolute path string, or null
     */
    public synchronized String getRomPath() {
        return (rom != null) ? rom.getFilePath() : null;
    }

    /**
     * Opens the ROM file using the configured filename.
     *
     * @throws IOException If the ROM cannot be opened
     */
    private void openRom() throws IOException {
        // Close existing ROM if any
        if (rom != null) {
            rom.close();
        }

        String romFilename = resolveRomForGame(configService.getString(SonicConfiguration.DEFAULT_ROM));
        if (romFilename == null || romFilename.isEmpty()) {
            throw new IOException("ROM filename not configured (DEFAULT_ROM not set or per-game ROM key empty)");
        }

        LOGGER.info("Opening ROM: " + romFilename);

        rom = new Rom();
        if (!rom.open(romFilename)) {
            rom = null;
            throw new IOException("Failed to open ROM file: " + romFilename);
        }

        initialized = true;
        LOGGER.info("ROM opened successfully: " + rom.readDomesticName());

        // Auto-detect game type and set appropriate module
        GameModuleRegistry.detectAndSetModule(rom);
    }

    /**
     * Resolves the ROM filename for a given game identifier.
     *
     * @param gameId "s1", "s2", or "s3k"
     * @return the configured ROM filename for that game
     */
    public static String resolveRomForGame(String gameId) {
        SonicConfigurationService svc = SonicConfigurationService.getInstance();
        return switch (gameId != null ? gameId.toLowerCase() : "s2") {
            case "s1" -> svc.getString(SonicConfiguration.SONIC_1_ROM);
            case "s3k" -> svc.getString(SonicConfiguration.SONIC_3K_ROM);
            default -> svc.getString(SonicConfiguration.SONIC_2_ROM);
        };
    }

    /**
     * Closes the ROM and releases resources.
     * Should be called on engine shutdown.
     */
    @Override
    public synchronized void close() {
        if (rom != null) {
            LOGGER.info("Closing ROM via RomManager");
            rom.close();
            rom = null;
        }
        for (Rom secondary : secondaryRoms.values()) {
            if (secondary != null) {
                secondary.close();
            }
        }
        secondaryRoms.clear();
        initialized = false;
    }

    /**
     * Resets the singleton instance (primarily for testing).
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}

