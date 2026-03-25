package com.openggf.game.sonic2;

import com.openggf.data.PaletteLoader;
import com.openggf.data.Rom;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.Palette;

import java.util.logging.Logger;

/**
 * Water data provider for Sonic 2.
 * Supplies zone/act-specific water heights, underwater palettes, and dynamic handlers.
 * <p>
 * Sonic 2 water zones (from s2.asm Level_InitWater):
 * <ul>
 *   <li>CPZ Act 2 (Chemical Plant Zone) - Mega Mack (purple liquid), rises</li>
 *   <li>ARZ (Aquatic Ruin Zone) - Standard water</li>
 *   <li>HPZ (Hidden Palace Zone) - Standard water (unused zone)</li>
 * </ul>
 * <p>
 * HTZ (Hill Top Zone) does NOT use the water system. Its lava is a background
 * visual effect controlled by the earthquake event system.
 * <p>
 * Starting water heights are from the ROM's Water_Height table (s2disasm).
 * Underwater palette ROM addresses from SCHG documentation.
 */
public class Sonic2WaterDataProvider implements WaterDataProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic2WaterDataProvider.class.getName());

    // S2 ROM zone IDs (from Sonic2ZoneConstants)
    private static final int ZONE_ARZ = Sonic2ZoneConstants.ROM_ZONE_ARZ; // 0x0F
    private static final int ZONE_CPZ = Sonic2ZoneConstants.ROM_ZONE_CPZ; // 0x0D

    // ROM addresses for underwater palettes (from SCHG)
    private static final int CPZ_UNDERWATER_PALETTE_ADDR = 0x2E62;
    private static final int ARZ_UNDERWATER_PALETTE_ADDR = 0x2FA2;

    @Override
    public boolean hasWater(int zoneId, int actId, PlayerCharacter character) {
        // Water_flag is set for CPZ Act 2, ARZ, and HPZ only (s2.asm Level_InitWater).
        // HTZ has lava but it is a background visual effect, not water.
        if (zoneId == ZONE_CPZ) {
            return actId == 1;
        }
        return zoneId == ZONE_ARZ;
    }

    @Override
    public int getStartingWaterLevel(int zoneId, int actId) {
        // CPZ Act 2: ROM Water_Height table at 0x459A = 0x0710
        if (zoneId == ZONE_CPZ && actId == 1) return 0x0710;
        // ARZ Act 1: ROM Water_Height table at 0x45A0 = 0x0410
        if (zoneId == ZONE_ARZ && actId == 0) return 0x0410;
        // ARZ Act 2: ROM Water_Height table at 0x45A2 = 0x0510
        if (zoneId == ZONE_ARZ && actId == 1) return 0x0510;
        return 0;
    }

    @Override
    public Palette[] getUnderwaterPalette(Rom rom, int zoneId, int actId, PlayerCharacter character) {
        int paletteAddr;
        if (zoneId == ZONE_CPZ) {
            paletteAddr = CPZ_UNDERWATER_PALETTE_ADDR;
        } else if (zoneId == ZONE_ARZ) {
            paletteAddr = ARZ_UNDERWATER_PALETTE_ADDR;
        } else {
            return null; // No underwater palette for other zones
        }

        try {
            return PaletteLoader.loadFullPalette(rom, paletteAddr);
        } catch (Exception e) {
            LOGGER.warning(String.format(
                    "Failed to load underwater palette for zone %d act %d at 0x%X: %s",
                    zoneId, actId, paletteAddr, e.getMessage()));
            return null;
        }
    }

    @Override
    public DynamicWaterHandler getDynamicHandler(int zoneId, int actId, PlayerCharacter character) {
        // S2 dynamic water (CPZ2 rising Mega Mack) is handled by existing
        // LevelEventManager / WaterSystem interaction
        return null;
    }
}
