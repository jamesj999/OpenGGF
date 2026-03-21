package com.openggf.game.sonic3k;

import com.openggf.game.AbstractZoneRegistry;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.level.LevelData;

import java.util.List;

/**
 * Zone registry for Sonic 3 &amp; Knuckles.
 * Defines all 13 zones with their acts, names, and per-act music.
 */
public class Sonic3kZoneRegistry extends AbstractZoneRegistry {

    // Zone names for title cards
    private static final String[] ZONE_NAMES = {
            "ANGEL ISLAND",
            "HYDROCITY",
            "MARBLE GARDEN",
            "CARNIVAL NIGHT",
            "FLYING BATTERY",
            "ICECAP",
            "LAUNCH BASE",
            "MUSHROOM HILL",
            "SANDOPOLIS",
            "LAVA REEF",
            "SKY SANCTUARY",
            "DEATH EGG",
            "THE DOOMSDAY"
    };

    // Music IDs per zone/act - S3K has different music per act for most zones
    private static final int[][] ZONE_MUSIC = {
            {Sonic3kMusic.AIZ1.id, Sonic3kMusic.AIZ2.id},
            {Sonic3kMusic.HCZ1.id, Sonic3kMusic.HCZ2.id},
            {Sonic3kMusic.MGZ1.id, Sonic3kMusic.MGZ2.id},
            {Sonic3kMusic.CNZ1.id, Sonic3kMusic.CNZ2.id},
            {Sonic3kMusic.FBZ1.id, Sonic3kMusic.FBZ2.id},
            {Sonic3kMusic.ICZ1.id, Sonic3kMusic.ICZ2.id},
            {Sonic3kMusic.LBZ1.id, Sonic3kMusic.LBZ2.id},
            {Sonic3kMusic.MHZ1.id, Sonic3kMusic.MHZ2.id},
            {Sonic3kMusic.SOZ1.id, Sonic3kMusic.SOZ2.id},
            {Sonic3kMusic.LRZ1.id, Sonic3kMusic.LRZ2.id},
            {Sonic3kMusic.SSZ.id, Sonic3kMusic.SSZ.id},
            {Sonic3kMusic.DEZ1.id, Sonic3kMusic.DEZ2.id},
            {Sonic3kMusic.DDZ.id}
    };

    private static final Sonic3kZoneRegistry INSTANCE = new Sonic3kZoneRegistry();

    private Sonic3kZoneRegistry() {
        // Zone structure: outer list = zones, inner list = acts
        super(List.of(
                List.of(LevelData.S3K_ANGEL_ISLAND_1, LevelData.S3K_ANGEL_ISLAND_2),
                List.of(LevelData.S3K_HYDROCITY_1, LevelData.S3K_HYDROCITY_2),
                List.of(LevelData.S3K_MARBLE_GARDEN_1, LevelData.S3K_MARBLE_GARDEN_2),
                List.of(LevelData.S3K_CARNIVAL_NIGHT_1, LevelData.S3K_CARNIVAL_NIGHT_2),
                List.of(LevelData.S3K_FLYING_BATTERY_1, LevelData.S3K_FLYING_BATTERY_2),
                List.of(LevelData.S3K_ICECAP_1, LevelData.S3K_ICECAP_2),
                List.of(LevelData.S3K_LAUNCH_BASE_1, LevelData.S3K_LAUNCH_BASE_2),
                List.of(LevelData.S3K_MUSHROOM_HILL_1, LevelData.S3K_MUSHROOM_HILL_2),
                List.of(LevelData.S3K_SANDOPOLIS_1, LevelData.S3K_SANDOPOLIS_2),
                List.of(LevelData.S3K_LAVA_REEF_1, LevelData.S3K_LAVA_REEF_2),
                List.of(LevelData.S3K_SKY_SANCTUARY_1, LevelData.S3K_SKY_SANCTUARY_2),
                List.of(LevelData.S3K_DEATH_EGG_1, LevelData.S3K_DEATH_EGG_2),
                List.of(LevelData.S3K_DOOMSDAY)
        ), ZONE_NAMES);
    }

    public static Sonic3kZoneRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    public int[] getStartPosition(int zoneIndex, int actIndex) {
        if (zoneIndex < 0 || zoneIndex >= zones.size()) {
            return new int[]{0x60, 0x280};
        }
        List<LevelData> acts = zones.get(zoneIndex);
        if (actIndex < 0 || actIndex >= acts.size()) {
            return new int[]{0x60, 0x280};
        }
        LevelData level = acts.get(actIndex);
        return new int[]{level.getStartXPos(), level.getStartYPos()};
    }

    @Override
    public int getMusicId(int zoneIndex, int actIndex) {
        if (zoneIndex < 0 || zoneIndex >= ZONE_MUSIC.length) {
            return -1;
        }
        int[] acts = ZONE_MUSIC[zoneIndex];
        if (actIndex < 0 || actIndex >= acts.length) {
            // Fall back to first act's music
            return acts[0];
        }
        return acts[actIndex];
    }
}
