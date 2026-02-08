package uk.co.jamesj999.sonic.game.sonic3k.scroll;

import uk.co.jamesj999.sonic.game.ScrollHandlerProvider.ZoneConstants;

/**
 * Zone index constants for Sonic 3 &amp; Knuckles.
 * Internal list indices matching Sonic3kZoneRegistry order.
 */
public class Sonic3kZoneConstants implements ZoneConstants {
    public static final Sonic3kZoneConstants INSTANCE = new Sonic3kZoneConstants();

    public static final int ZONE_AIZ = 0;   // Angel Island
    public static final int ZONE_HCZ = 1;   // Hydrocity
    public static final int ZONE_MGZ = 2;   // Marble Garden
    public static final int ZONE_CNZ = 3;   // Carnival Night
    public static final int ZONE_FBZ = 4;   // Flying Battery
    public static final int ZONE_ICZ = 5;   // IceCap
    public static final int ZONE_LBZ = 6;   // Launch Base
    public static final int ZONE_MHZ = 7;   // Mushroom Hill
    public static final int ZONE_SOZ = 8;   // Sandopolis
    public static final int ZONE_LRZ = 9;   // Lava Reef
    public static final int ZONE_SSZ = 10;  // Sky Sanctuary
    public static final int ZONE_DEZ = 11;  // Death Egg
    public static final int ZONE_DDZ = 12;  // Doomsday

    public static final int ZONE_COUNT = 13;

    private static final String[] ZONE_NAMES = {
            "Angel Island",
            "Hydrocity",
            "Marble Garden",
            "Carnival Night",
            "Flying Battery",
            "IceCap",
            "Launch Base",
            "Mushroom Hill",
            "Sandopolis",
            "Lava Reef",
            "Sky Sanctuary",
            "Death Egg",
            "Doomsday"
    };

    private Sonic3kZoneConstants() {
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
