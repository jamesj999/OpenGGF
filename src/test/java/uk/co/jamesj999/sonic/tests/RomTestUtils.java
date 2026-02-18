package uk.co.jamesj999.sonic.tests;

import org.junit.Assert;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public final class RomTestUtils {
    private RomTestUtils() {
    }

    // Sonic 2 (default / backward-compatible)
    private static final String ROM_FILENAME = "Sonic The Hedgehog 2 (W) (REV01) [!].gen";
    private static final String ROM_URL = "http://bluetoaster.net/secretfolder/Sonic%20The%20Hedgehog%202%20%28W%29%20%28REV01%29%20%5B!%5D.gen";
    private static final String ROM_PATH_PROPERTY = "sonic.rom.path";
    private static final String ROM_PATH_ENV = "SONIC_ROM_PATH";

    // Per-game ROM filenames
    private static final String S1_ROM_FILENAME = "Sonic The Hedgehog (W) (REV01) [!].gen";
    private static final String S2_ROM_FILENAME = "Sonic The Hedgehog 2 (W) (REV01) [!].gen";
    private static final String S3K_ROM_FILENAME = "Sonic and Knuckles & Sonic 3 (W) [!].gen";

    // Per-game system properties
    private static final String S1_ROM_PATH_PROPERTY = "sonic1.rom.path";
    private static final String S2_ROM_PATH_PROPERTY = "sonic2.rom.path";
    private static final String S3K_ROM_PATH_PROPERTY = "s3k.rom.path";

    // Per-game env vars
    private static final String S1_ROM_PATH_ENV = "SONIC_1_ROM_PATH";
    private static final String S2_ROM_PATH_ENV = "SONIC_2_ROM_PATH";
    private static final String S3K_ROM_PATH_ENV = "SONIC_3K_ROM_PATH";

    /**
     * Ensures the Sonic 2 ROM is available (backward-compatible).
     * Auto-downloads if not found locally.
     */
    public static File ensureRomAvailable() {
        File romFile = findLocalRom();
        if (romFile == null) {
            romFile = downloadRom();
        }

        Assert.assertTrue("ROM download should create the expected file", romFile.exists());
        Assert.assertTrue("ROM file should not be empty", romFile.length() > 0);
        return romFile;
    }

    /**
     * Ensures the Sonic 1 ROM is available.
     * Lookup order: system property, env var, config value, default filename.
     * No auto-download; returns null-safe File that must exist.
     */
    public static File ensureSonic1RomAvailable() {
        return findGameRom(S1_ROM_PATH_PROPERTY, S1_ROM_PATH_ENV,
                SonicConfiguration.SONIC_1_ROM, S1_ROM_FILENAME);
    }

    /**
     * Ensures the Sonic 2 ROM is available (explicit game-specific variant).
     * Lookup order: system property, env var, config value, default filename.
     * Falls back to auto-download if not found.
     */
    public static File ensureSonic2RomAvailable() {
        File rom = findGameRomOrNull(S2_ROM_PATH_PROPERTY, S2_ROM_PATH_ENV,
                SonicConfiguration.SONIC_2_ROM, S2_ROM_FILENAME);
        if (rom != null) {
            return rom;
        }
        // Fall back to existing auto-download behavior for Sonic 2
        return ensureRomAvailable();
    }

    /**
     * Ensures the Sonic 3&K ROM is available.
     * Lookup order: system property, env var, config value, default filename.
     * No auto-download; returns null-safe File that must exist.
     */
    public static File ensureSonic3kRomAvailable() {
        return findGameRom(S3K_ROM_PATH_PROPERTY, S3K_ROM_PATH_ENV,
                SonicConfiguration.SONIC_3K_ROM, S3K_ROM_FILENAME);
    }

    /**
     * Finds a game-specific ROM. Asserts that the file exists and is non-empty.
     */
    private static File findGameRom(String sysProp, String envVar,
                                     SonicConfiguration configKey, String defaultFilename) {
        File rom = findGameRomOrNull(sysProp, envVar, configKey, defaultFilename);
        Assert.assertNotNull("ROM not found. Provide via -D" + sysProp +
                ", " + envVar + " env var, or place " + defaultFilename + " in working directory.", rom);
        Assert.assertTrue("ROM file should not be empty: " + rom.getAbsolutePath(), rom.length() > 0);
        return rom;
    }

    /**
     * Tries to find a game-specific ROM, returning null if not found (no assertion).
     */
    private static File findGameRomOrNull(String sysProp, String envVar,
                                           SonicConfiguration configKey, String defaultFilename) {
        // 1. System property
        String path = System.getProperty(sysProp);
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) return f;
        }

        // 2. Environment variable
        path = System.getenv(envVar);
        if (path != null && !path.isEmpty()) {
            File f = new File(path);
            if (f.exists()) return f;
        }

        // 3. Config value from SonicConfigurationService
        try {
            String configValue = SonicConfigurationService.getInstance().getString(configKey);
            if (configValue != null && !configValue.isEmpty()) {
                File f = new File(configValue);
                if (f.exists()) return f;
            }
        } catch (Exception ignored) {
            // Config service may not be available in all test contexts
        }

        // 4. Default filename in working directory
        File f = new File(defaultFilename);
        return f.exists() ? f : null;
    }

    private static File findLocalRom() {
        String configuredPath = System.getProperty(ROM_PATH_PROPERTY);
        if (configuredPath == null || configuredPath.isEmpty()) {
            configuredPath = System.getenv(ROM_PATH_ENV);
        }

        if (configuredPath != null && !configuredPath.isEmpty()) {
            File configuredRom = new File(configuredPath);
            Assert.assertTrue("Configured ROM path does not exist: " + configuredRom.getAbsolutePath(), configuredRom.exists());
            Assert.assertTrue("Configured ROM file should not be empty", configuredRom.length() > 0);
            return configuredRom;
        }

        File romFile = new File(ROM_FILENAME);
        return romFile.exists() ? romFile : null;
    }

    private static File downloadRom() {
        File romFile = new File(ROM_FILENAME);
        try (InputStream in = new BufferedInputStream(new URL(ROM_URL).openStream());
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(romFile))) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Assert.fail("Failed to download ROM from " + ROM_URL + ": " + e.getMessage() +
                    ". Provide the ROM locally via -D" + ROM_PATH_PROPERTY + " or the " + ROM_PATH_ENV + " env var.");
        }
        return romFile;
    }
}
