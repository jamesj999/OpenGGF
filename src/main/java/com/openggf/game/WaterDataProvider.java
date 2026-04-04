package com.openggf.game;

import com.openggf.data.Rom;
import com.openggf.level.Palette;

/**
 * Provides water configuration for a specific game (S1/S2/S3K).
 * Returned by {@link GameModule#getWaterDataProvider()}.
 * <p>
 * Each game implements this to supply zone/act-specific water heights,
 * underwater palettes, and dynamic water handlers from its ROM data.
 */
public interface WaterDataProvider {
    boolean hasWater(int zoneId, int actId, PlayerCharacter character);

    /**
     * Check for water considering seamless act transition state.
     * ROM: CheckLevelForWater (sonic3k.asm:9754-9759) checks Apparent_zone_and_act.
     * During seamless transitions, Apparent != Current, which enables water in cases
     * that a direct load (level select) would disable (e.g. AIZ2 Knuckles).
     *
     * @param seamlessTransition true when called during a seamless act transition
     */
    default boolean hasWater(int zoneId, int actId, PlayerCharacter character,
                             boolean seamlessTransition) {
        return hasWater(zoneId, actId, character);
    }

    int getStartingWaterLevel(int zoneId, int actId);
    Palette[] getUnderwaterPalette(Rom rom, int zoneId, int actId, PlayerCharacter character);
    DynamicWaterHandler getDynamicHandler(int zoneId, int actId, PlayerCharacter character);
    default int getWaterSpeed(int zoneId, int actId) { return 1; }
}
