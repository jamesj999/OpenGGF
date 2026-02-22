package com.openggf.game.sonic3k.audio;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for all Sonic 3 &amp; Knuckles music IDs and display names.
 * <p>IDs from S3K disassembly music pointer list ordering. Includes both S&amp;K driver
 * tracks (0x01-0x33) and S3 driver variants (0x100+).
 */
public enum Sonic3kMusic {
    // S&K driver tracks (0x01-0x33)
    AIZ1(0x01, "Angel Island Zone Act 1"),
    AIZ2(0x02, "Angel Island Zone Act 2"),
    HCZ1(0x03, "Hydrocity Zone Act 1"),
    HCZ2(0x04, "Hydrocity Zone Act 2"),
    MGZ1(0x05, "Marble Garden Zone Act 1"),
    MGZ2(0x06, "Marble Garden Zone Act 2"),
    CNZ1(0x07, "Carnival Night Zone Act 1"),
    CNZ2(0x08, "Carnival Night Zone Act 2"),
    FBZ1(0x09, "Flying Battery Zone Act 1"),
    FBZ2(0x0A, "Flying Battery Zone Act 2"),
    ICZ1(0x0B, "IceCap Zone Act 1"),
    ICZ2(0x0C, "IceCap Zone Act 2"),
    LBZ1(0x0D, "Launch Base Zone Act 1"),
    LBZ2(0x0E, "Launch Base Zone Act 2"),
    MHZ1(0x0F, "Mushroom Hill Zone Act 1"),
    MHZ2(0x10, "Mushroom Hill Zone Act 2"),
    SOZ1(0x11, "Sandopolis Zone Act 1"),
    SOZ2(0x12, "Sandopolis Zone Act 2"),
    LRZ1(0x13, "Lava Reef Zone Act 1"),
    LRZ2(0x14, "Lava Reef Zone Act 2"),
    SSZ(0x15, "Sky Sanctuary Zone"),
    DEZ1(0x16, "Death Egg Zone Act 1"),
    DEZ2(0x17, "Death Egg Zone Act 2"),
    MINIBOSS(0x18, "Mini-Boss"),
    BOSS(0x19, "Boss"),
    DDZ(0x1A, "Doomsday Zone"),
    PACHINKO(0x1B, "Bonus Stage - Pachinko"),
    SPECIAL_STAGE(0x1C, "Special Stage"),
    SLOTS(0x1D, "Bonus Stage - Slots"),
    GUMBALL(0x1E, "Bonus Stage - Gumball"),
    KNUCKLES(0x1F, "Knuckles' Theme"),
    AZURE_LAKE(0x20, "Azure Lake (Competition)"),
    BALLOON_PARK(0x21, "Balloon Park (Competition)"),
    DESERT_PALACE(0x22, "Desert Palace (Competition)"),
    CHROME_GADGET(0x23, "Chrome Gadget (Competition)"),
    ENDLESS_MINE(0x24, "Endless Mine (Competition)"),
    TITLE(0x25, "Title Screen"),
    CREDITS_S3(0x26, "Credits (Sonic 3)"),
    GAME_OVER(0x27, "Game Over"),
    CONTINUE(0x28, "Continue"),
    ACT_CLEAR(0x29, "Act Clear"),
    EXTRA_LIFE(0x2A, "Extra Life"),
    EMERALD(0x2B, "Chaos Emerald"),
    INVINCIBILITY(0x2C, "Invincibility"),
    COMPETITION_MENU(0x2D, "Competition Menu"),
    /**
     * Mini-Boss (S3 slot). In the S&amp;K driver table (default), this ID plays the
     * S&amp;K miniboss theme (same track as {@link #MINIBOSS} 0x18). In the S3
     * driver table, this ID plays the original Sonic 3 miniboss arrangement.
     */
    MINIBOSS_S3(0x2E, "Mini-Boss (Sonic 3)"),
    DATA_SELECT(0x2F, "Data Select"),
    FINAL_BOSS(0x30, "Final Boss"),
    DROWNING(0x31, "Drowning"),
    ENDING(0x32, "Ending"),
    /** S&amp;K Staff Roll (only present in S&amp;K driver table, not S3). */
    CREDITS_SK(0x33, "Credits (S&K)"),

    // S3-specific track variants (from the S3 driver in the combined ROM)
    // Uses literal 0x100 since enum constants can't reference static fields
    ICZ1_S3(0x100 | 0x0B, "IceCap Zone Act 1 (S3)"),
    ICZ2_S3(0x100 | 0x0C, "IceCap Zone Act 2 (S3)"),
    LBZ1_S3(0x100 | 0x0D, "Launch Base Zone Act 1 (S3)"),
    LBZ2_S3(0x100 | 0x0E, "Launch Base Zone Act 2 (S3)"),
    KNUCKLES_S3(0x100 | 0x1F, "Knuckles' Theme (S3)"),
    TITLE_S3(0x100 | 0x25, "Title Screen (S3)"),
    CREDITS_S3_ALT(0x100 | 0x26, "Credits (S3)"),
    ACT_CLEAR_S3(0x100 | 0x29, "Act Clear (S3)"),
    EXTRA_LIFE_S3(0x100 | 0x2A, "Extra Life (S3)"),
    INVINCIBILITY_S3(0x100 | 0x2C, "Invincibility (S3)"),
    COMPETITION_MENU_S3(0x100 | 0x2D, "Competition Menu (S3)"),
    MINIBOSS_S3_ALT(0x100 | 0x2E, "Mini-Boss (S3)"),
    FINAL_BOSS_S3(0x100 | 0x30, "Final Boss (S3)");

    /** Offset added to a base music ID to request the S3 driver variant. */
    public static final int S3_MUSIC_ID_BASE = 0x100;

    /** Native music ID. */
    public final int id;

    /** Human-readable display name. */
    public final String displayName;

    private static final Map<Integer, String> TITLE_MAP;
    private static final Map<Integer, Sonic3kMusic> BY_ID;

    static {
        Map<Integer, String> titles = new LinkedHashMap<>();
        Map<Integer, Sonic3kMusic> lookup = new HashMap<>();
        for (Sonic3kMusic music : values()) {
            titles.put(music.id, music.displayName);
            lookup.put(music.id, music);
        }
        TITLE_MAP = Collections.unmodifiableMap(titles);
        BY_ID = Collections.unmodifiableMap(lookup);
    }

    Sonic3kMusic(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** ID-to-name map for the SoundTestCatalog. */
    public static Map<Integer, String> titleMap() {
        return TITLE_MAP;
    }

    /** Look up enum constant by native ID, or null if not a music ID. */
    public static Sonic3kMusic fromId(int id) {
        return BY_ID.get(id);
    }
}
