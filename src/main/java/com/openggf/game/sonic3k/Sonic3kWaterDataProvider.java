package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.ThresholdTableWaterHandler;
import com.openggf.game.ThresholdTableWaterHandler.WaterThreshold;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.Palette;

import java.util.List;

/**
 * Water data provider for Sonic 3 &amp; Knuckles.
 * <p>
 * Water zones from CheckLevelForWater (sonic3k.asm:9751):
 * <ul>
 *   <li>Zone 0 (AIZ) - Angel Island, both acts</li>
 *   <li>Zone 1 (HCZ) - Hydrocity, both acts</li>
 *   <li>Zone 6 (LBZ) - Launch Base, both acts</li>
 * </ul>
 * <p>
 * Starting heights from StartingWaterHeights.bin (32 big-endian words).
 * <p>
 * Dynamic handlers from DynamicWaterHeight_Index (sonic3k.asm:8609).
 */
public class Sonic3kWaterDataProvider implements WaterDataProvider {

    /**
     * Starting water heights indexed by zone (0-12), each with 2 acts.
     * From StartingWaterHeights.bin. Non-water zones use 0x0600 (off-screen).
     */
    private static final int[][] STARTING_HEIGHTS = {
        { 0x0504, 0x0528 }, // Zone 0: AIZ
        { 0x0500, 0x0700 }, // Zone 1: HCZ
        { 0x0600, 0x0600 }, // Zone 2: MGZ (no water)
        { 0x0600, 0x0600 }, // Zone 3: CNZ (no water)
        { 0x0600, 0x0600 }, // Zone 4: FBZ (no water)
        { 0x0600, 0x0600 }, // Zone 5: ICZ (no water)
        { 0x0AD8, 0x0A80 }, // Zone 6: LBZ
        { 0x0600, 0x0600 }, // Zone 7: MHZ (no water)
        { 0x0600, 0x0600 }, // Zone 8: SOZ (no water)
        { 0x0600, 0x0600 }, // Zone 9: LRZ (no water)
        { 0x0600, 0x0600 }, // Zone 10: SSZ (no water)
        { 0x0600, 0x0600 }, // Zone 11: DEZ (no water)
        { 0x0600, 0x0600 }, // Zone 12: DDZ (no water)
    };

    @Override
    public boolean hasWater(int zoneId, int actId, PlayerCharacter character) {
        return zoneId == Sonic3kZoneIds.ZONE_AIZ
            || zoneId == Sonic3kZoneIds.ZONE_HCZ
            || zoneId == Sonic3kZoneIds.ZONE_LBZ;
    }

    @Override
    public int getStartingWaterLevel(int zoneId, int actId) {
        if (zoneId < 0 || zoneId >= STARTING_HEIGHTS.length) {
            return 0x0600; // Default off-screen
        }
        int act = Math.min(actId, STARTING_HEIGHTS[zoneId].length - 1);
        if (act < 0) {
            act = 0;
        }
        return STARTING_HEIGHTS[zoneId][act];
    }

    @Override
    public Palette[] getUnderwaterPalette(Rom rom, int zoneId, int actId, PlayerCharacter character) {
        // Underwater palette loading deferred to Task 10
        return null;
    }

    @Override
    public DynamicWaterHandler getDynamicHandler(int zoneId, int actId, PlayerCharacter character) {
        // AIZ1: static water (ROM handler is rts)
        if (zoneId == Sonic3kZoneIds.ZONE_AIZ && actId == 0) {
            return null;
        }

        // AIZ2: custom state machine
        if (zoneId == Sonic3kZoneIds.ZONE_AIZ && actId == 1) {
            return new Aiz2DynamicWaterHandler();
        }

        // HCZ1: threshold table
        if (zoneId == Sonic3kZoneIds.ZONE_HCZ && actId == 0) {
            return new ThresholdTableWaterHandler(List.of(
                new WaterThreshold(0x8500, 0x0900),
                new WaterThreshold(0x8680, 0x2A00),
                new WaterThreshold(0x86A0, 0x3500)
            ));
        }

        // HCZ2: character-dependent thresholds
        if (zoneId == Sonic3kZoneIds.ZONE_HCZ && actId == 1) {
            if (character == PlayerCharacter.KNUCKLES) {
                return new ThresholdTableWaterHandler(List.of(
                    new WaterThreshold(0x0700, 0x4100)
                ));
            }
            // Sonic/Tails
            return new ThresholdTableWaterHandler(List.of(
                new WaterThreshold(0x0700, 0x3E00)
            ));
        }

        // LBZ1: threshold table
        if (zoneId == Sonic3kZoneIds.ZONE_LBZ && actId == 0) {
            return new ThresholdTableWaterHandler(List.of(
                new WaterThreshold(0x8B00, 0x0E00),
                new WaterThreshold(0x8A00, 0x1980),
                new WaterThreshold(0x8AC8, 0x2C00)
            ));
        }

        // LBZ2: Knuckles only
        if (zoneId == Sonic3kZoneIds.ZONE_LBZ && actId == 1) {
            if (character == PlayerCharacter.KNUCKLES) {
                return new ThresholdTableWaterHandler(List.of(
                    new WaterThreshold(0x8FF0, 0x0D80)
                ));
            }
            // Sonic/Tails: no dynamic water
            return null;
        }

        return null;
    }
}
