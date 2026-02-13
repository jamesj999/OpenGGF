package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.game.ScrollHandlerProvider.ZoneConstants;

/**
 * Zone index constants for Sonic 1 (gameplay progression order).
 * These match the zone registry ordering used by LevelManager.currentZone.
 * For ROM zone IDs, see {@link uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants}.
 */
public class Sonic1ZoneConstants implements ZoneConstants {
    public static final Sonic1ZoneConstants INSTANCE = new Sonic1ZoneConstants();

    public static final int ZONE_GHZ = 0;  // Green Hill
    public static final int ZONE_MZ  = 1;  // Marble
    public static final int ZONE_SYZ = 2;  // Spring Yard
    public static final int ZONE_LZ  = 3;  // Labyrinth
    public static final int ZONE_SLZ = 4;  // Star Light
    public static final int ZONE_SBZ = 5;  // Scrap Brain
    public static final int ZONE_FZ  = 6;  // Final Zone

    public static final int ZONE_COUNT = 7;

    private static final String[] ZONE_NAMES = {
            "Green Hill",
            "Marble",
            "Spring Yard",
            "Labyrinth",
            "Star Light",
            "Scrap Brain",
            "Final"
    };

    private Sonic1ZoneConstants() {
    }

    @Override
    public int getZoneCount() {
        return ZONE_COUNT;
    }

    @Override
    public String getZoneName(int index) {
        if (index >= 0 && index < ZONE_NAMES.length) {
            return ZONE_NAMES[index];
        }
        return "Unknown";
    }
}
