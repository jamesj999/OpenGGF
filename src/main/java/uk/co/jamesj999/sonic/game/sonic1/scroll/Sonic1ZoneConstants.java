package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.game.ScrollHandlerProvider.ZoneConstants;

/**
 * Zone index constants for Sonic 1.
 */
public class Sonic1ZoneConstants implements ZoneConstants {
    public static final Sonic1ZoneConstants INSTANCE = new Sonic1ZoneConstants();

    public static final int ZONE_GHZ = 0;  // Green Hill
    public static final int ZONE_LZ  = 1;  // Labyrinth
    public static final int ZONE_MZ  = 2;  // Marble
    public static final int ZONE_SLZ = 3;  // Star Light
    public static final int ZONE_SYZ = 4;  // Spring Yard
    public static final int ZONE_SBZ = 5;  // Scrap Brain
    public static final int ZONE_FZ  = 6;  // Final Zone

    public static final int ZONE_COUNT = 7;

    private static final String[] ZONE_NAMES = {
            "Green Hill",
            "Labyrinth",
            "Marble",
            "Star Light",
            "Spring Yard",
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
