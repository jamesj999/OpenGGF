package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.game.ZoneRegistry;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kAudioConstants;
import uk.co.jamesj999.sonic.level.LevelData;

import java.util.List;

/**
 * Zone registry for Sonic 3 &amp; Knuckles.
 * Defines all 13 zones with their acts, names, and per-act music.
 */
public class Sonic3kZoneRegistry implements ZoneRegistry {
    private static final Sonic3kZoneRegistry INSTANCE = new Sonic3kZoneRegistry();

    // Zone structure: outer list = zones, inner list = acts
    private final List<List<LevelData>> zones = List.of(
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
    );

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
            {Sonic3kAudioConstants.MUS_AIZ1, Sonic3kAudioConstants.MUS_AIZ2},
            {Sonic3kAudioConstants.MUS_HCZ1, Sonic3kAudioConstants.MUS_HCZ2},
            {Sonic3kAudioConstants.MUS_MGZ1, Sonic3kAudioConstants.MUS_MGZ2},
            {Sonic3kAudioConstants.MUS_CNZ1, Sonic3kAudioConstants.MUS_CNZ2},
            {Sonic3kAudioConstants.MUS_FBZ1, Sonic3kAudioConstants.MUS_FBZ2},
            {Sonic3kAudioConstants.MUS_ICZ1, Sonic3kAudioConstants.MUS_ICZ2},
            {Sonic3kAudioConstants.MUS_LBZ1, Sonic3kAudioConstants.MUS_LBZ2},
            {Sonic3kAudioConstants.MUS_MHZ1, Sonic3kAudioConstants.MUS_MHZ2},
            {Sonic3kAudioConstants.MUS_SOZ1, Sonic3kAudioConstants.MUS_SOZ2},
            {Sonic3kAudioConstants.MUS_LRZ1, Sonic3kAudioConstants.MUS_LRZ2},
            {Sonic3kAudioConstants.MUS_SSZ, Sonic3kAudioConstants.MUS_SSZ},
            {Sonic3kAudioConstants.MUS_DEZ1, Sonic3kAudioConstants.MUS_DEZ2},
            {Sonic3kAudioConstants.MUS_DDZ}
    };

    private Sonic3kZoneRegistry() {
    }

    public static Sonic3kZoneRegistry getInstance() {
        return INSTANCE;
    }

    @Override
    public int getZoneCount() {
        return zones.size();
    }

    @Override
    public int getActCount(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= zones.size()) {
            return 0;
        }
        return zones.get(zoneIndex).size();
    }

    @Override
    public String getZoneName(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= ZONE_NAMES.length) {
            return "UNKNOWN";
        }
        return ZONE_NAMES[zoneIndex];
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
    public List<LevelData> getLevelDataForZone(int zoneIndex) {
        if (zoneIndex < 0 || zoneIndex >= zones.size()) {
            return List.of();
        }
        return zones.get(zoneIndex);
    }

    @Override
    public List<List<LevelData>> getAllZones() {
        return zones;
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
