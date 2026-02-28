package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.Palette;

/**
 * Water data provider for Sonic the Hedgehog 1.
 * <p>
 * S1 water heights are hardcoded in the assembly (LZWaterFeatures.asm lines 49-52)
 * rather than stored in a ROM table like S2. Only Labyrinth Zone (all 3 acts) and
 * Scrap Brain Zone Act 3 (SBZ3, which reuses LZ water mechanics) have water.
 */
public class Sonic1WaterDataProvider implements WaterDataProvider {

    // Water heights from LZWaterFeatures.asm WaterHeight table (lines 49-52)
    private static final int[] LZ_HEIGHTS = {
            Sonic1Constants.WATER_HEIGHT_LZ1,  // 0x00B8 - Act 1
            Sonic1Constants.WATER_HEIGHT_LZ2,  // 0x0328 - Act 2
            Sonic1Constants.WATER_HEIGHT_LZ3   // 0x0900 - Act 3
    };
    private static final int SBZ3_HEIGHT = Sonic1Constants.WATER_HEIGHT_SBZ3; // 0x0228

    @Override
    public boolean hasWater(int zoneId, int actId, PlayerCharacter character) {
        if (zoneId == Sonic1Constants.ZONE_LZ && actId >= 0 && actId <= 2) {
            return true;
        }
        if (zoneId == Sonic1Constants.ZONE_SBZ && actId == 2) {
            return true;
        }
        return false;
    }

    @Override
    public int getStartingWaterLevel(int zoneId, int actId) {
        if (zoneId == Sonic1Constants.ZONE_LZ && actId >= 0 && actId < LZ_HEIGHTS.length) {
            return LZ_HEIGHTS[actId];
        }
        if (zoneId == Sonic1Constants.ZONE_SBZ && actId == 2) {
            return SBZ3_HEIGHT;
        }
        return 0x0600; // Off-screen default
    }

    @Override
    public Palette[] getUnderwaterPalette(Rom rom, int zoneId, int actId, PlayerCharacter character) {
        // Deferred -- existing WaterSystem.loadS1UnderwaterPalette() handles palette loading
        return null;
    }

    @Override
    public DynamicWaterHandler getDynamicHandler(int zoneId, int actId, PlayerCharacter character) {
        // S1 water is static (no dynamic handlers); level events set targets directly
        return null;
    }
}
