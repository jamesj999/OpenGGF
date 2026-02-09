package uk.co.jamesj999.sonic.game.sonic3k.audio;

/**
 * Music and SFX IDs for Sonic 3 &amp; Knuckles.
 * Music IDs are indices into the music pointer/bank lists in the Z80 driver data.
 */
public final class Sonic3kAudioConstants {

    // -----------------------------------------------------------------------
    // Music IDs (from S3K disassembly music pointer list ordering)
    // -----------------------------------------------------------------------

    public static final int MUS_AIZ1 = 0x01;
    public static final int MUS_AIZ2 = 0x02;
    public static final int MUS_HCZ1 = 0x03;
    public static final int MUS_HCZ2 = 0x04;
    public static final int MUS_MGZ1 = 0x05;
    public static final int MUS_MGZ2 = 0x06;
    public static final int MUS_CNZ1 = 0x07;
    public static final int MUS_CNZ2 = 0x08;
    public static final int MUS_FBZ1 = 0x09;
    public static final int MUS_FBZ2 = 0x0A;
    public static final int MUS_ICZ1 = 0x0B;
    public static final int MUS_ICZ2 = 0x0C;
    public static final int MUS_LBZ1 = 0x0D;
    public static final int MUS_LBZ2 = 0x0E;
    public static final int MUS_MHZ1 = 0x0F;
    public static final int MUS_MHZ2 = 0x10;
    public static final int MUS_SOZ1 = 0x11;
    public static final int MUS_SOZ2 = 0x12;
    public static final int MUS_LRZ1 = 0x13;
    public static final int MUS_LRZ2 = 0x14;
    public static final int MUS_SSZ = 0x15;
    public static final int MUS_DEZ1 = 0x16;
    public static final int MUS_DEZ2 = 0x17;
    public static final int MUS_MINIBOSS = 0x18;
    public static final int MUS_BOSS = 0x19;
    public static final int MUS_DDZ = 0x1A;
    public static final int MUS_PACHINKO = 0x1B;
    public static final int MUS_SPECIAL_STAGE = 0x1C;
    public static final int MUS_SLOTS = 0x1D;
    public static final int MUS_GUMBALL = 0x1E;
    public static final int MUS_KNUCKLES = 0x1F;
    public static final int MUS_AZURE_LAKE = 0x20;
    public static final int MUS_BALLOON_PARK = 0x21;
    public static final int MUS_DESERT_PALACE = 0x22;
    public static final int MUS_CHROME_GADGET = 0x23;
    public static final int MUS_ENDLESS_MINE = 0x24;
    public static final int MUS_TITLE = 0x25;
    public static final int MUS_CREDITS_S3 = 0x26;
    public static final int MUS_GAME_OVER = 0x27;
    public static final int MUS_CONTINUE = 0x28;
    public static final int MUS_ACT_CLEAR = 0x29;
    public static final int MUS_EXTRA_LIFE = 0x2A;
    public static final int MUS_EMERALD = 0x2B;
    public static final int MUS_INVINCIBILITY = 0x2C;
    public static final int MUS_COMPETITION_MENU = 0x2D;
    /**
     * Mini-Boss (S3 slot). In the S&K driver table (default), this ID plays the
     * S&K miniboss theme (same track as {@link #MUS_MINIBOSS} 0x18). In the S3
     * driver table ({@code loadS3Music}), this ID plays the original Sonic 3
     * miniboss theme — a different arrangement. S3 zones should use
     * {@code Sonic3kSmpsLoader.loadS3Music(MUS_MINIBOSS_S3)} for the authentic
     * S3 track.
     */
    public static final int MUS_MINIBOSS_S3 = 0x2E;
    public static final int MUS_DATA_SELECT = 0x2F;
    public static final int MUS_FINAL_BOSS = 0x30;
    public static final int MUS_DROWNING = 0x31;
    public static final int MUS_ENDING = 0x32;

    /** S&K Staff Roll (only present in S&K driver table, not S3). */
    public static final int MUS_CREDITS_SK = 0x33;

    // -----------------------------------------------------------------------
    // S3-specific track variants (loaded from the S3 driver tables)
    //
    // The combined S3&K ROM contains two Z80 drivers with separate music
    // banks. These IDs use an offset of 0x100 so the loader can distinguish
    // them from S&K IDs and route to the S3 driver tables automatically.
    // -----------------------------------------------------------------------

    /** Offset added to a base music ID to request the S3 driver variant. */
    public static final int S3_MUSIC_ID_BASE = 0x100;

    /** S3 Title Screen (original composition, differs from S&K). */
    public static final int MUS_TITLE_S3 = S3_MUSIC_ID_BASE | MUS_TITLE;

    /** S3 Knuckles' Theme (original composition). */
    public static final int MUS_KNUCKLES_S3 = S3_MUSIC_ID_BASE | MUS_KNUCKLES;

    /** S3 Extra Life jingle (original composition). */
    public static final int MUS_EXTRA_LIFE_S3 = S3_MUSIC_ID_BASE | MUS_EXTRA_LIFE;

    /** S3 Invincibility (original composition). */
    public static final int MUS_INVINCIBILITY_S3 = S3_MUSIC_ID_BASE | MUS_INVINCIBILITY;

    /** S3 Competition Menu (original composition). */
    public static final int MUS_COMPETITION_MENU_S3 = S3_MUSIC_ID_BASE | MUS_COMPETITION_MENU;

    /**
     * S3 Mini-Boss (original composition). In the S&K driver table, base ID
     * 0x2E is a duplicate of 0x18 (S&K miniboss). This S3 variant plays the
     * distinct Sonic 3 miniboss arrangement.
     */
    public static final int MUS_MINIBOSS_S3_ALT = S3_MUSIC_ID_BASE | MUS_MINIBOSS_S3;

    /** S3 Act Clear jingle (original composition). */
    public static final int MUS_ACT_CLEAR_S3 = S3_MUSIC_ID_BASE | MUS_ACT_CLEAR;

    /** S3 Credits (original composition, differs from S&K credits). */
    public static final int MUS_CREDITS_S3_ALT = S3_MUSIC_ID_BASE | MUS_CREDITS_S3;

    /** S3 IceCap Zone Act 1 (original composition). */
    public static final int MUS_ICZ1_S3 = S3_MUSIC_ID_BASE | MUS_ICZ1;

    /** S3 IceCap Zone Act 2 (original composition). */
    public static final int MUS_ICZ2_S3 = S3_MUSIC_ID_BASE | MUS_ICZ2;

    /** S3 Launch Base Zone Act 1 (original composition). */
    public static final int MUS_LBZ1_S3 = S3_MUSIC_ID_BASE | MUS_LBZ1;

    /** S3 Launch Base Zone Act 2 (original composition). */
    public static final int MUS_LBZ2_S3 = S3_MUSIC_ID_BASE | MUS_LBZ2;

    /** S3 Final Boss (original composition). */
    public static final int MUS_FINAL_BOSS_S3 = S3_MUSIC_ID_BASE | MUS_FINAL_BOSS;

    // -----------------------------------------------------------------------
    // System commands (sound queue IDs)
    // -----------------------------------------------------------------------

    /** Fade out current music. */
    public static final int CMD_FADE_OUT = 0xE0;

    /** Play "SEGA" PCM sample. */
    public static final int CMD_SEGA = 0xE1;

    /** Speed up current music (speed shoes on). */
    public static final int CMD_SPEED_UP = 0xE2;

    /** Slow down current music (speed shoes off). */
    public static final int CMD_SLOW_DOWN = 0xE3;

    /** Stop all sound and music. */
    public static final int CMD_STOP_ALL = 0xE4;

    // -----------------------------------------------------------------------
    // SFX ID range (native 68K queue IDs)
    //
    // S3K SFX use native 68K queue IDs 0x33-0xDB (169 entries), matching the
    // Z80 SFX pointer table 1:1. playMusic() and playSfx() are separate
    // methods with separate loaders, so there is no conflict with music IDs
    // (which go up to 0x33). System commands (0xE0-0xE4) are handled by
    // handleSystemCommand() before reaching the SFX loader.
    // -----------------------------------------------------------------------

    /** First SFX ID (sfx_RingRight, native 68K queue ID 0x33, Z80 SFX table index 0). */
    public static final int SFX_ID_BASE = 0x33;

    /** Last standard SFX ID (native 68K queue ID 0xDB, Z80 SFX table index 168).
     *  Continuous SFX (cfx_*) beyond 0xDB use a separate driver mechanism. */
    public static final int SFX_ID_MAX = 0xDB;

    private Sonic3kAudioConstants() {
    }
}
