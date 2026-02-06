package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.game.ZoneRegistry;
import uk.co.jamesj999.sonic.level.LevelData;

import java.util.List;

/**
 * Zone registry for Sonic the Hedgehog 1.
 * Defines all 6 main zones plus Final Zone / Scrap Brain Act 3.
 */
public class Sonic1ZoneRegistry implements ZoneRegistry {
    private static final Sonic1ZoneRegistry INSTANCE = new Sonic1ZoneRegistry();

    private final List<List<LevelData>> zones = List.of(
            List.of(LevelData.S1_GREEN_HILL_1, LevelData.S1_GREEN_HILL_2, LevelData.S1_GREEN_HILL_3),
            List.of(LevelData.S1_LABYRINTH_1, LevelData.S1_LABYRINTH_2, LevelData.S1_LABYRINTH_3),
            List.of(LevelData.S1_MARBLE_1, LevelData.S1_MARBLE_2, LevelData.S1_MARBLE_3),
            List.of(LevelData.S1_STAR_LIGHT_1, LevelData.S1_STAR_LIGHT_2, LevelData.S1_STAR_LIGHT_3),
            List.of(LevelData.S1_SPRING_YARD_1, LevelData.S1_SPRING_YARD_2, LevelData.S1_SPRING_YARD_3),
            List.of(LevelData.S1_SCRAP_BRAIN_1, LevelData.S1_SCRAP_BRAIN_2, LevelData.S1_SCRAP_BRAIN_3),
            List.of(LevelData.S1_FINAL_ZONE)
    );

    private static final String[] ZONE_NAMES = {
            "GREEN HILL",
            "LABYRINTH",
            "MARBLE",
            "STAR LIGHT",
            "SPRING YARD",
            "SCRAP BRAIN",
            "FINAL"
    };

    private static final int[] ZONE_MUSIC = {
            0x81, // Green Hill (MUS_GHZ)
            0x82, // Labyrinth (MUS_LZ)
            0x83, // Marble (MUS_MZ)
            0x84, // Star Light (MUS_SLZ)
            0x85, // Spring Yard (MUS_SYZ)
            0x86, // Scrap Brain (MUS_SBZ)
            0x8D  // Final Zone (MUS_FZ)
    };

    private Sonic1ZoneRegistry() {
    }

    public static Sonic1ZoneRegistry getInstance() {
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
            return new int[]{0x0080, 0x00A8};
        }
        List<LevelData> acts = zones.get(zoneIndex);
        if (actIndex < 0 || actIndex >= acts.size()) {
            return new int[]{0x0080, 0x00A8};
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
        return ZONE_MUSIC[zoneIndex];
    }
}
