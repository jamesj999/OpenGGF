package com.openggf.game.sonic3k;

import com.openggf.game.AbstractZoneRegistry;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.level.LevelData;

import java.util.List;

/**
 * Zone registry for Sonic 3 &amp; Knuckles.
 * Defines all zones from AIZ (0) through the bonus stages (19-21),
 * including competition zones (13-17) and reserved zone 18.
 *
 * <p>The outer list index matches the ROM zone ID, so bonus stages
 * at zone 0x13 (19) can be loaded via {@code loadZoneAndAct(19, 0)}.
 */
public class Sonic3kZoneRegistry extends AbstractZoneRegistry {

    // Zone names for title cards — indexed by zone ID (0-21)
    // Competition zones and bonus stages use descriptive names
    private static final String[] ZONE_NAMES = {
            "ANGEL ISLAND",         // 0
            "HYDROCITY",            // 1
            "MARBLE GARDEN",        // 2
            "CARNIVAL NIGHT",       // 3
            "FLYING BATTERY",       // 4
            "ICECAP",               // 5
            "LAUNCH BASE",          // 6
            "MUSHROOM HILL",        // 7
            "SANDOPOLIS",           // 8
            "LAVA REEF",            // 9
            "SKY SANCTUARY",        // 10
            "DEATH EGG",            // 11
            "THE DOOMSDAY",         // 12
            "AZURE LAKE",           // 13 (competition)
            "BALLOON PARK",         // 14 (competition)
            "DESERT PALACE",        // 15 (competition)
            "CHROME GADGET",        // 16 (competition)
            "ENDLESS MINE",         // 17 (competition)
            "",                     // 18 (reserved/unused)
            "GUMBALL",              // 19 (bonus stage)
            "GLOWING SPHERES",      // 20 (bonus stage)
            "SLOT MACHINE"          // 21 (bonus stage)
    };

    // Music IDs per zone/act - S3K has different music per act for most zones.
    // Competition zones and bonus stages use a single music ID per zone.
    // Bonus stage music is normally set by the coordinator, but is listed here
    // for completeness and fallback.
    private static final int[][] ZONE_MUSIC = {
            {Sonic3kMusic.AIZ1.id, Sonic3kMusic.AIZ2.id},  // 0  AIZ
            {Sonic3kMusic.HCZ1.id, Sonic3kMusic.HCZ2.id},  // 1  HCZ
            {Sonic3kMusic.MGZ1.id, Sonic3kMusic.MGZ2.id},  // 2  MGZ
            {Sonic3kMusic.CNZ1.id, Sonic3kMusic.CNZ2.id},  // 3  CNZ
            {Sonic3kMusic.FBZ1.id, Sonic3kMusic.FBZ2.id},  // 4  FBZ
            {Sonic3kMusic.ICZ1.id, Sonic3kMusic.ICZ2.id},  // 5  ICZ
            {Sonic3kMusic.LBZ1.id, Sonic3kMusic.LBZ2.id},  // 6  LBZ
            {Sonic3kMusic.MHZ1.id, Sonic3kMusic.MHZ2.id},  // 7  MHZ
            {Sonic3kMusic.SOZ1.id, Sonic3kMusic.SOZ2.id},  // 8  SOZ
            {Sonic3kMusic.LRZ1.id, Sonic3kMusic.LRZ2.id},  // 9  LRZ
            {Sonic3kMusic.SSZ.id, Sonic3kMusic.SSZ.id},     // 10 SSZ
            {Sonic3kMusic.DEZ1.id, Sonic3kMusic.DEZ2.id},   // 11 DEZ
            {Sonic3kMusic.DDZ.id},                          // 12 DDZ
            {-1},                                           // 13 ALZ (competition)
            {-1},                                           // 14 BPZ (competition)
            {-1},                                           // 15 DPZ (competition)
            {-1},                                           // 16 CGZ (competition)
            {-1},                                           // 17 EMZ (competition)
            {-1},                                           // 18 (reserved)
            {0x1E},                                         // 19 Gumball
            {0x1B},                                         // 20 Glowing Spheres / Pachinko
            {0x1D}                                          // 21 Slot Machine
    };

    private static final Sonic3kZoneRegistry INSTANCE = new Sonic3kZoneRegistry();

    private Sonic3kZoneRegistry() {
        // Zone structure: outer list = zones (indexed by ROM zone ID),
        // inner list = acts. Competition zones and bonus stages have 1 act each.
        super(List.of(
                List.of(LevelData.S3K_ANGEL_ISLAND_1, LevelData.S3K_ANGEL_ISLAND_2),   // 0  AIZ
                List.of(LevelData.S3K_HYDROCITY_1, LevelData.S3K_HYDROCITY_2),         // 1  HCZ
                List.of(LevelData.S3K_MARBLE_GARDEN_1, LevelData.S3K_MARBLE_GARDEN_2), // 2  MGZ
                List.of(LevelData.S3K_CARNIVAL_NIGHT_1, LevelData.S3K_CARNIVAL_NIGHT_2),// 3  CNZ
                List.of(LevelData.S3K_FLYING_BATTERY_1, LevelData.S3K_FLYING_BATTERY_2),// 4  FBZ
                List.of(LevelData.S3K_ICECAP_1, LevelData.S3K_ICECAP_2),               // 5  ICZ
                List.of(LevelData.S3K_LAUNCH_BASE_1, LevelData.S3K_LAUNCH_BASE_2),     // 6  LBZ
                List.of(LevelData.S3K_MUSHROOM_HILL_1, LevelData.S3K_MUSHROOM_HILL_2), // 7  MHZ
                List.of(LevelData.S3K_SANDOPOLIS_1, LevelData.S3K_SANDOPOLIS_2),       // 8  SOZ
                List.of(LevelData.S3K_LAVA_REEF_1, LevelData.S3K_LAVA_REEF_2),         // 9  LRZ
                List.of(LevelData.S3K_SKY_SANCTUARY_1, LevelData.S3K_SKY_SANCTUARY_2), // 10 SSZ
                List.of(LevelData.S3K_DEATH_EGG_1, LevelData.S3K_DEATH_EGG_2),         // 11 DEZ
                List.of(LevelData.S3K_DOOMSDAY),                                        // 12 DDZ
                List.of(LevelData.S3K_AZURE_LAKE),                                      // 13 ALZ
                List.of(LevelData.S3K_BALLOON_PARK),                                    // 14 BPZ
                List.of(LevelData.S3K_DESERT_PALACE),                                   // 15 DPZ
                List.of(LevelData.S3K_CHROME_GADGET),                                   // 16 CGZ
                List.of(LevelData.S3K_ENDLESS_MINE),                                    // 17 EMZ
                List.of(LevelData.S3K_RESERVED_18),                                     // 18 (reserved)
                List.of(LevelData.S3K_GUMBALL),                                         // 19 Gumball
                List.of(LevelData.S3K_GLOWING_SPHERE),                                  // 20 Glowing Spheres
                List.of(LevelData.S3K_SLOT_MACHINE)                                     // 21 Slot Machine
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
