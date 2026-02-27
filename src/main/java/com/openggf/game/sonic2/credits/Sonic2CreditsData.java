package com.openggf.game.sonic2.credits;

/**
 * Constants for the Sonic 2 ending and credits sequence.
 * Values from docs/s2disasm/s2.asm (EndgameCredits, ShowCreditsScreen).
 */
public class Sonic2CreditsData {
    // --- Credits text ---
    public static final int TOTAL_CREDITS = 21;
    public static final int SLIDE_DURATION_60FPS = 0x18E;  // ~6.6s per slide
    public static final int SLIDE_DURATION_50FPS = 0x144;
    public static final int FADE_DURATION = 0x16;           // fade in/out frames

    // --- Logo flash ---
    public static final int LOGO_HOLD_FRAMES = 0x257;       // total hold after credits
    public static final int LOGO_INITIAL_PAUSE = 0x3B;      // pause before flash starts
    public static final int LOGO_FLASH_FRAMES = 0x5E;       // flash animation duration
    public static final int PALETTE_CYCLE_FRAME_COUNT = 9;   // number of palette frames in Ending Cycle.bin
    public static final int PALETTE_CYCLE_BYTES_PER_FRAME = 24; // 6 longwords = 12 colors

    // --- VRAM tile bases (from ROM art loading) ---
    public static final int ARTTILE_CREDIT_TEXT = 0x0500;
    public static final int ARTTILE_CREDIT_TEXT_CREDSCR = 0x0001; // credits screen tile base
    public static final int ARTTILE_ENDING_CHARACTER = 0x0019;
    public static final int ARTTILE_ENDING_FINAL_TORNADO = 0x0156;
    public static final int ARTTILE_ENDING_PICS = 0x0328;
    public static final int ARTTILE_ENDING_MINI_TORNADO = 0x0493;
    public static final int ARTTILE_CLOUDS = 0x0594;

    // --- Cutscene timing (frames) ---
    public static final int PHOTO_HOLD_FRAMES = 0x180;      // per photo display time
    public static final int SKY_FALL_FRAMES = 0xC0;
    public static final int PLANE_HOLD_FRAMES_60FPS = 0x480;
    public static final int PLANE_HOLD_FRAMES_50FPS = 0x3D0;

    // --- Cutscene object speeds (subpixels, 256 = 1px) ---
    public static final int PLANE_X_SPEED = 0x100;
    public static final int PLANE_Y_SPEED = -0x80;
    public static final int PLANE_TARGET_X = 0xA0;
    public static final int CLOUD_SPEED_FAST = -0x300;
    public static final int CLOUD_SPEED_MED = -0x200;
    public static final int CLOUD_SPEED_SLOW = -0x100;

    private Sonic2CreditsData() {} // utility class
}
