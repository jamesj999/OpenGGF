package uk.co.jamesj999.sonic.game.sonic1.audio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for all Sonic 1 music IDs and display names.
 * <p>IDs from s1disasm Constants.asm, names from sound test labels.
 */
public enum Sonic1Music {
    GHZ(0x81, "Green Hill Zone"),
    LZ(0x82, "Labyrinth Zone"),
    MZ(0x83, "Marble Zone"),
    SLZ(0x84, "Star Light Zone"),
    SYZ(0x85, "Spring Yard Zone"),
    SBZ(0x86, "Scrap Brain Zone"),
    INVINCIBILITY(0x87, "Invincibility"),
    EXTRA_LIFE(0x88, "Extra Life"),
    SPECIAL_STAGE(0x89, "Special Stage"),
    TITLE(0x8A, "Title Screen"),
    ENDING(0x8B, "Ending"),
    BOSS(0x8C, "Boss"),
    FZ(0x8D, "Final Zone"),
    GOT_THROUGH(0x8E, "Act Clear"),
    GAME_OVER(0x8F, "Game Over"),
    CONTINUE(0x90, "Continue"),
    CREDITS(0x91, "Credits"),
    DROWNING(0x92, "Drowning"),
    CHAOS_EMERALD(0x93, "Chaos Emerald");

    /** Native music ID. */
    public final int id;

    /** Human-readable display name. */
    public final String displayName;

    /** First music ID (GHZ). */
    public static final int ID_BASE = 0x81;

    /** Last music ID (Chaos Emerald). */
    public static final int ID_MAX = 0x93;

    private static final Map<Integer, String> TITLE_MAP;
    private static final Sonic1Music[] BY_ID;

    static {
        Map<Integer, String> m = new LinkedHashMap<>();
        Sonic1Music[] lookup = new Sonic1Music[ID_MAX - ID_BASE + 1];
        for (Sonic1Music music : values()) {
            m.put(music.id, music.displayName);
            lookup[music.id - ID_BASE] = music;
        }
        TITLE_MAP = Collections.unmodifiableMap(m);
        BY_ID = lookup;
    }

    Sonic1Music(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** ID-to-name map for the SoundTestCatalog. */
    public static Map<Integer, String> titleMap() {
        return TITLE_MAP;
    }

    /** Look up enum constant by native ID, or null if out of range. */
    public static Sonic1Music fromId(int id) {
        int index = id - ID_BASE;
        if (index >= 0 && index < BY_ID.length) {
            return BY_ID[index];
        }
        return null;
    }
}
