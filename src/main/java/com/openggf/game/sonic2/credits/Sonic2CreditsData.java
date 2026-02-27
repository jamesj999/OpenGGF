package com.openggf.game.sonic2.credits;

/**
 * Constants for the Sonic 2 ending and credits sequence.
 * Values from docs/s2disasm/s2.asm (EndgameCredits, ShowCreditsScreen, ObjCA-ObjCF).
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

    // ========================================================================
    // Cutscene timing (frames) — ObjCA routine timers
    // ========================================================================

    /** Photo fade wait 1: $180 frames (60fps), $100 (PAL). ObjCA routine 2 timer. */
    public static final int PALETTE_WAIT_1_60FPS = 0x180;
    public static final int PALETTE_WAIT_1_50FPS = 0x100;

    /** Photo fade wait 2: $80 frames. ObjCA routine 6 timer. */
    public static final int PALETTE_WAIT_2 = 0x80;

    /** CHARACTER_APPEAR hold before spawning second ObjC9: $80 frames. */
    public static final int CHARACTER_APPEAR_HOLD = 0x80;

    /** CAMERA_SCROLL duration: $C0 frames (Sonic/Super), $880/$660 Tails. */
    public static final int CAMERA_SCROLL_SONIC_60FPS = 0xC0;
    public static final int CAMERA_SCROLL_TAILS_60FPS = 0x660;
    public static final int CAMERA_SCROLL_TAILS_50FPS = 0x880;

    /** ObjCC spawn delay into MAIN_ENDING: $100 frames. */
    public static final int OBJCC_SPAWN_DELAY = 0x100;

    /** ObjCC State 2 (birds+tornado hold): $480 frames (60fps), $3D0 (PAL). */
    public static final int PLANE_HOLD_FRAMES_60FPS = 0x480;
    public static final int PLANE_HOLD_FRAMES_50FPS = 0x3D0;

    /** Global frame threshold for credits trigger: $1140 (60fps), $E40 (PAL). */
    public static final int CREDITS_TRIGGER_60FPS = 0x1140;
    public static final int CREDITS_TRIGGER_50FPS = 0xE40;

    /** ObjCC State 4 rotation: 28 steps, 2 frames each = 56 frames total. */
    public static final int ROTATION_STEPS = 28;
    public static final int ROTATION_FRAME_DELAY = 2;

    /** ObjCC State 6 timer: $60 frames. */
    public static final int DEPARTURE_TIMER = 0x60;

    /** ObjCC State 8 camera pan: 7 steps, 2 frames each = 14 frames total. */
    public static final int CAMERA_PAN_STEPS = 7;
    public static final int CAMERA_PAN_FRAME_DELAY = 2;

    /** Super Sonic final (State $A): 8 position steps. */
    public static final int SUPER_FINAL_STEPS = 8;

    // Legacy aliases (used by existing code)
    public static final int PHOTO_HOLD_FRAMES = PALETTE_WAIT_1_60FPS;
    public static final int SKY_FALL_FRAMES = 0xC0;

    // ========================================================================
    // Cutscene object speeds (subpixels, 256 = 1px)
    // ========================================================================

    /** ObjCC approach velocity: x_vel=$100, y_vel=-$80. */
    public static final int PLANE_X_SPEED = 0x100;
    public static final int PLANE_Y_SPEED = -0x80;
    /** ObjCC target X for approach stop. */
    public static final int PLANE_TARGET_X = 0xA0;

    /** ObjCB cloud speeds (vertical mode, before plane arrives). */
    public static final int CLOUD_SPEED_FAST = -0x300;
    public static final int CLOUD_SPEED_MED = -0x200;
    public static final int CLOUD_SPEED_SLOW = -0x100;

    // ========================================================================
    // ObjCD Bird constants
    // ========================================================================

    /** Maximum number of birds to spawn. ROM: objoff_35 = $14. */
    public static final int BIRD_SPAWN_COUNT = 0x14;

    /** Bird initial x_vel (fly right). */
    public static final int BIRD_X_VEL = 0x100;
    /** Bird initial y_vel magnitude (random +-). */
    public static final int BIRD_Y_VEL = 0x20;

    /** Bird state 0 (initial fly-right) duration. */
    public static final int BIRD_STATE0_FRAMES = 0xC0;
    /** Bird state 1 (homing) duration. */
    public static final int BIRD_STATE1_FRAMES = 0x180;
    /** Bird state 2 (exit left) duration. */
    public static final int BIRD_STATE2_FRAMES = 0xC0;

    /** Bird animation speed (Ani_objCD: speed 5). */
    public static final int BIRD_ANIM_SPEED = 5;

    // ========================================================================
    // ROM position/frame data tables
    // ========================================================================

    /**
     * word_A656: 28 (X,Y) pairs for tornado rotation path (ObjCC State 4).
     * Each entry is a screen position the tornado moves to during the rotation sequence.
     */
    public static final int[][] TORNADO_PATH = {
            {0xA0, 0x70}, {0xB0, 0x70}, {0xB6, 0x71}, {0xBC, 0x72},
            {0xC4, 0x74}, {0xC8, 0x75}, {0xCA, 0x76}, {0xCC, 0x77},
            {0xCE, 0x78}, {0xD0, 0x79}, {0xD2, 0x7A}, {0xD4, 0x7B},
            {0xD6, 0x7C}, {0xD9, 0x7E}, {0xDC, 0x81}, {0xDE, 0x84},
            {0xE1, 0x87}, {0xE4, 0x8B}, {0xE7, 0x8F}, {0xEC, 0x94},
            {0xF0, 0x99}, {0xF5, 0x9D}, {0xF9, 0xA4}, {0x100, 0xAC},
            {0x108, 0xB8}, {0x112, 0xC4}, {0x11F, 0xD3}, {0x12C, 0xFA}
    };

    /**
     * byte_A602: 28 frame indices for Sonic during rotation (Ending_Routine=0).
     * Indexes into ObjCF_MapUnc_ADA2 mapping table.
     */
    public static final int[] TORNADO_FRAMES_SONIC = {
            7, 7, 7, 7, 8, 8, 8, 8, 8, 8, 8, 9, 9, 9,
            0xA, 0xA, 0xA, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB
    };

    /**
     * byte_A61E: 28 frame indices for Super Sonic during rotation (Ending_Routine=2).
     */
    public static final int[] TORNADO_FRAMES_SUPER = {
            0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2,
            3, 3, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4
    };

    /**
     * byte_A63A: 28 frame indices for Tails during rotation (Ending_Routine=4).
     */
    public static final int[] TORNADO_FRAMES_TAILS = {
            0x18, 0x18, 0x18, 0x18, 0x19, 0x19, 0x19, 0x19, 0x19, 0x19, 0x19, 9, 9, 9,
            0xA, 0xA, 0xA, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB, 0xB
    };

    /**
     * word_A766: 30 (X,Y) pairs for Super Sonic departure path (ObjCC State 6).
     */
    public static final int[][] SUPER_SONIC_PATH = {
            {0xC0, 0x90}, {0xBE, 0x8E}, {0xBC, 0x8C}, {0xBA, 0x8A},
            {0xB8, 0x88}, {0xB6, 0x86}, {0xB4, 0x84}, {0xB2, 0x82},
            {0xB0, 0x80}, {0xAE, 0x7E}, {0xAC, 0x7C}, {0xAA, 0x7A},
            {0xA8, 0x78}, {0xA6, 0x76}, {0xA4, 0x74}, {0xA2, 0x72},
            {0xA0, 0x70}, {0x9E, 0x6E}, {0x9C, 0x6C}, {0x9A, 0x6A},
            {0x98, 0x68}, {0x96, 0x66}, {0x94, 0x64}, {0x92, 0x62},
            {0x90, 0x60}, {0x8E, 0x5E}, {0x8C, 0x5C}, {0x8A, 0x5A},
            {0x88, 0x58}, {0x86, 0x56}
    };

    /**
     * byte_A748: 30 frame indices for Super Sonic during departure.
     */
    public static final int[] SUPER_SONIC_FRAMES = {
            4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
            4, 4, 4, 4, 4, 4, 4, 4, 4, 4,
            4, 4, 4, 4, 4, 4, 4, 4, 4, 4
    };

    /**
     * word_A822: 7 (dX, dY) camera pan delta pairs (ObjCC State 8).
     * Applied to scroll offsets with 2-frame delay per step.
     */
    public static final int[][] CAMERA_PAN_DELTAS = {
            {-0x3A, 0x88}, {-0x0C, 0x22}, {-0x08, 0x10}, {-0x04, 0x08},
            {-0x02, 0x04}, {-0x01, 0x02}, {-0x01, 0x02}
    };

    /**
     * word_A874: 8 (X,Y) pairs for Super Sonic final position (ObjCC State $A).
     */
    public static final int[][] SUPER_FINAL_PATH = {
            {0x60, 0x88}, {0x50, 0x68}, {0x44, 0x46}, {0x3C, 0x36},
            {0x36, 0x2A}, {0x33, 0x24}, {0x31, 0x20}, {0x30, 0x1E}
    };

    /**
     * ObjCE character jump deltas (4-frame tick, 2 pairs).
     * Sonic: (-8, 0) then (-$44, -$38).
     */
    public static final int[][] CHAR_JUMP_DELTAS_SONIC = {
            {-8, 0}, {-0x44, -0x38}
    };

    /**
     * Tails: (-8, 0) then (-$50, -$40).
     */
    public static final int[][] CHAR_JUMP_DELTAS_TAILS = {
            {-8, 0}, {-0x50, -0x40}
    };

    /**
     * ObjCB cloud Y velocities (before plane). ROM: dc.w -$300, -$200, -$100, -$300.
     */
    public static final int[] CLOUD_Y_VELS = {-0x300, -0x200, -0x100, -0x300};

    /**
     * ObjCB cloud frame selection. ROM: dc.b 0, 1, 2, 0.
     */
    public static final int[] CLOUD_FRAMES = {0, 1, 2, 0};

    private Sonic2CreditsData() {} // utility class
}
