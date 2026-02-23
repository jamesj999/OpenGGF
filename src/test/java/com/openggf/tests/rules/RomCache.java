package com.openggf.tests.rules;

import com.openggf.data.Rom;
import com.openggf.tests.RomTestUtils;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * Static ROM cache shared across all test classes in the JVM.
 * Opens each ROM at most once and caches the result.
 * A sentinel {@code UNAVAILABLE} object distinguishes "not attempted" from "attempted but missing".
 */
final class RomCache {
    private static final Rom UNAVAILABLE = new Rom();
    private static final Map<SonicGame, Rom> cache = new EnumMap<>(SonicGame.class);

    private RomCache() {
    }

    /**
     * Gets the ROM for the given game, loading it on first access.
     *
     * @return the loaded ROM, or null if the ROM is unavailable
     */
    static synchronized Rom getRom(SonicGame game) {
        Rom cached = cache.get(game);
        if (cached == UNAVAILABLE) {
            return null;
        }
        if (cached != null) {
            return cached;
        }

        // First access — attempt to load
        Rom rom = loadRom(game);
        cache.put(game, rom != null ? rom : UNAVAILABLE);
        return rom;
    }

    private static Rom loadRom(SonicGame game) {
        try {
            File romFile = switch (game) {
                case SONIC_1 -> RomTestUtils.ensureSonic1RomAvailable();
                case SONIC_2 -> RomTestUtils.ensureSonic2RomAvailable();
                case SONIC_3K -> RomTestUtils.ensureSonic3kRomAvailable();
            };
            Rom rom = new Rom();
            if (!rom.open(romFile.getAbsolutePath())) {
                return null;
            }
            return rom;
        } catch (AssertionError | Exception e) {
            // ROM not available — RomTestUtils asserts on missing ROMs
            return null;
        }
    }
}
