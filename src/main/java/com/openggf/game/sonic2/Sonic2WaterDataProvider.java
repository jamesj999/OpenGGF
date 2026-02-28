package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.level.Palette;

/**
 * Water data provider for Sonic 2.
 * Supplies zone/act-specific water heights, underwater palettes, and dynamic handlers.
 * <p>
 * Sonic 2 water zones:
 * <ul>
 *   <li>CPZ (Chemical Plant Zone) - Mega Mack (purple liquid), rises in Act 2</li>
 *   <li>ARZ (Aquatic Ruin Zone) - Standard water</li>
 *   <li>HTZ (Hill Top Zone) - Lava (acts like water for drowning, off-screen)</li>
 * </ul>
 * <p>
 * Starting water heights are hardcoded from the ROM's Water_Height table
 * (s2disasm). Full ROM-based extraction remains in {@link com.openggf.level.WaterSystem}
 * for backward compatibility.
 */
public class Sonic2WaterDataProvider implements WaterDataProvider {

    // S2 ROM zone IDs (from Sonic2ZoneConstants)
    private static final int ZONE_ARZ = Sonic2ZoneConstants.ROM_ZONE_ARZ; // 0x0F
    private static final int ZONE_CPZ = Sonic2ZoneConstants.ROM_ZONE_CPZ; // 0x0D
    private static final int ZONE_HTZ = Sonic2ZoneConstants.ROM_ZONE_HTZ; // 0x07

    // Default off-screen water level for zones where water is not visible
    // (e.g., HTZ lava which is below the level)
    private static final int DEFAULT_OFFSCREEN_WATER_LEVEL = 0x0600;

    @Override
    public boolean hasWater(int zoneId, int actId, PlayerCharacter character) {
        return zoneId == ZONE_ARZ || zoneId == ZONE_CPZ || zoneId == ZONE_HTZ;
    }

    @Override
    public int getStartingWaterLevel(int zoneId, int actId) {
        // CPZ Act 2: ROM Water_Height table at 0x459A = 0x0710
        if (zoneId == ZONE_CPZ && actId == 1) return 0x0710;
        // ARZ Act 1: ROM Water_Height table at 0x45A0 = 0x0410
        if (zoneId == ZONE_ARZ && actId == 0) return 0x0410;
        // ARZ Act 2: ROM Water_Height table at 0x45A2 = 0x0510
        if (zoneId == ZONE_ARZ && actId == 1) return 0x0510;
        // HTZ and other zones: off-screen default
        return DEFAULT_OFFSCREEN_WATER_LEVEL;
    }

    @Override
    public Palette[] getUnderwaterPalette(Rom rom, int zoneId, int actId, PlayerCharacter character) {
        // Deferred - existing WaterSystem handles palette loading
        return null;
    }

    @Override
    public DynamicWaterHandler getDynamicHandler(int zoneId, int actId, PlayerCharacter character) {
        // S2 dynamic water (CPZ2 rising Mega Mack) is handled by existing
        // LevelEventManager / WaterSystem interaction
        return null;
    }
}
