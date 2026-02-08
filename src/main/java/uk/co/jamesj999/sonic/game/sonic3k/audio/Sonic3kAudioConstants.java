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
    public static final int MUS_MINIBOSS_S3 = 0x2E;
    public static final int MUS_DATA_SELECT = 0x2F;
    public static final int MUS_FINAL_BOSS = 0x30;
    public static final int MUS_DROWNING = 0x31;
    public static final int MUS_ENDING = 0x32;

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
    // SFX ID range
    // -----------------------------------------------------------------------

    /** First SFX ID. */
    public static final int SFX_ID_BASE = 0xA0;

    /** Last SFX ID. */
    public static final int SFX_ID_MAX = 0xFF;

    private Sonic3kAudioConstants() {
    }
}
