package com.openggf.game.sonic2;

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
 * Sonic 2 water zones:
 * <ul>
 *   <li>CPZ (Chemical Plant Zone) - Mega Mack (purple liquid), rises in Act 2</li>
 *   <li>ARZ (Aquatic Ruin Zone) - Standard water</li>
 *   <li>HTZ (Hill Top Zone) - Lava (acts like water for drowning, off-screen)</li>
 * </ul>
 * <p>
 * Starting water heights are from the ROM's Water_Height table (s2disasm).
 * Underwater palette ROM addresses from SCHG documentation.
 */
public class Sonic2WaterDataProvider implements WaterDataProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic2WaterDataProvider.class.getName());

    private static final int PALETTE_SIZE_BYTES = 128; // 64 colors * 2 bytes per color

    // S2 ROM zone IDs (from Sonic2ZoneConstants)
    private static final int ZONE_ARZ = Sonic2ZoneConstants.ROM_ZONE_ARZ; // 0x0F
    private static final int ZONE_CPZ = Sonic2ZoneConstants.ROM_ZONE_CPZ; // 0x0D
    private static final int ZONE_HTZ = Sonic2ZoneConstants.ROM_ZONE_HTZ; // 0x07

    // ROM addresses for underwater palettes (from SCHG)
    private static final int CPZ_UNDERWATER_PALETTE_ADDR = 0x2E62;
    private static final int ARZ_UNDERWATER_PALETTE_ADDR = 0x2FA2;

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
        int paletteAddr;
        if (zoneId == ZONE_CPZ) {
            paletteAddr = CPZ_UNDERWATER_PALETTE_ADDR;
        } else if (zoneId == ZONE_ARZ) {
            paletteAddr = ARZ_UNDERWATER_PALETTE_ADDR;
        } else {
            return null; // No underwater palette for HTZ or other zones
        }

        try {
            byte[] paletteData = rom.readBytes(paletteAddr, PALETTE_SIZE_BYTES);
            Palette[] palettes = new Palette[4];
            for (int i = 0; i < 4; i++) {
                byte[] lineData = new byte[32];
                System.arraycopy(paletteData, i * 32, lineData, 0, 32);
                palettes[i] = new Palette();
                palettes[i].fromSegaFormat(lineData);
            }
            return palettes;
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
