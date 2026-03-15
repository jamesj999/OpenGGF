package com.openggf.game.sonic3k.specialstage;

/**
 * Constants for the Sonic 3&K Blue Ball special stage.
 * All values from docs/skdisasm/sonic3k.asm and sonic3k.constants.asm.
 */
public final class Sonic3kSpecialStageConstants {
    private Sonic3kSpecialStageConstants() {}

    // ==================== Cell Types (layout buffer values) ====================

    /** Empty cell - no collision. */
    public static final int CELL_EMPTY = 0x00;
    /** Red sphere - fail condition when player enters at aligned position. */
    public static final int CELL_RED = 0x01;
    /** Blue sphere - collect to progress. Main goal. */
    public static final int CELL_BLUE = 0x02;
    /** Bumper - bounces player backward. */
    public static final int CELL_BUMPER = 0x03;
    /** Ring - collectible, created from blue sphere conversion. */
    public static final int CELL_RING = 0x04;
    /** Spring - launches player into air with spring jump. */
    public static final int CELL_SPRING = 0x05;
    /** Ring animation frame 1 (during collection fade). */
    public static final int CELL_RING_ANIM_1 = 0x06;
    /** Ring animation frame 2. */
    public static final int CELL_RING_ANIM_2 = 0x07;
    /** Ring animation frame 3. */
    public static final int CELL_RING_ANIM_3 = 0x08;
    /** Ring animation frame 4 (final before disappearing). */
    public static final int CELL_RING_ANIM_4 = 0x09;
    /** Touched - temporary marker during blue sphere conversion. */
    public static final int CELL_TOUCHED = 0x0A;
    /** Chaos Emerald - placed at end when all spheres collected. */
    public static final int CELL_CHAOS_EMERALD = 0x0B;
    /** Super Emerald - same as chaos emerald but for SK special stage flag. */
    public static final int CELL_SUPER_EMERALD = 0x0D;

    // ==================== Grid Dimensions ====================

    /** Grid is 32x32 cells. */
    public static final int GRID_SIZE = 32;
    /** Grid cell count. */
    public static final int GRID_CELL_COUNT = GRID_SIZE * GRID_SIZE; // 1024
    /** Grid coordinate mask (wraps at 32). */
    public static final int GRID_MASK = 0x1F;
    /** Layout buffer total size (with padding before and after). */
    public static final int LAYOUT_BUFFER_SIZE = 0x600;
    /** Offset of the 32x32 grid data within the layout buffer. */
    public static final int LAYOUT_GRID_OFFSET = 0x100;
    /** Size of the grid data within the buffer. */
    public static final int LAYOUT_GRID_SIZE = 0x400;

    // ==================== Physics Constants ====================

    /** Initial maximum speed. */
    public static final int INITIAL_RATE = 0x1000;
    /** Speed increment per timer expiry. */
    public static final int RATE_INCREMENT = 0x400;
    /** Maximum possible speed. */
    public static final int MAX_RATE = 0x2000;
    /** Frames between speed increases (30 seconds at 60fps). */
    public static final int RATE_TIMER_NORMAL = 30 * 60;
    /** Frames between speed increases in Blue Spheres standalone mode. */
    public static final int RATE_TIMER_BLUE_SPHERES = 45 * 60;
    /** Acceleration per frame when up pressed. */
    public static final int ACCELERATION = 0x200;
    /** Normal jump velocity. */
    public static final int JUMP_VELOCITY = -0x100000;
    /** Spring jump velocity. */
    public static final int SPRING_JUMP_VELOCITY = -0xE8000;
    /** Turn increment per frame (positive = left). */
    public static final int TURN_LEFT = 4;
    /** Turn increment per frame (negative = right). */
    public static final int TURN_RIGHT = -4;
    /** Normal jump flag value. */
    public static final int JUMP_NORMAL = 0x80;
    /** Spring jump flag value. */
    public static final int JUMP_SPRING = 0x81;
    /** Jump request flag (set when button pressed, consumed at next aligned position). */
    public static final int JUMP_REQUEST = 1;
    /** Cell alignment mask: position must have these bits zero for interactions. */
    public static final int CELL_ALIGN_MASK = 0xE0;
    /** Angle alignment mask: angle must have low 6 bits zero for jumping. */
    public static final int ANGLE_ALIGN_MASK = 0x3F;

    // ==================== Angle Constants ====================

    /** North direction. */
    public static final int ANGLE_NORTH = 0x00;
    /** West direction. */
    public static final int ANGLE_WEST = 0x40;
    /** South direction. */
    public static final int ANGLE_SOUTH = 0x80;
    /** East direction. */
    public static final int ANGLE_EAST = 0xC0;
    /** Angle quadrant mask. */
    public static final int ANGLE_QUADRANT_MASK = 0xC0;
    /** Bit 6 of angle determines which axis is forward. */
    public static final int ANGLE_AXIS_BIT = 0x40;

    // ==================== Direction Tables ====================

    /**
     * 8-way direction offsets for sphere-to-ring conversion.
     * Grid index deltas for all 8 neighbors (NW,N,NE,W,E,SW,S,SE).
     * From SStage_8_Directions (sonic3k.asm:13216).
     */
    public static final int[] DIRECTIONS_8 = {
            -0x21, -0x20, -0x1F,
            -1,            1,
            0x1F,  0x20,  0x21
    };

    /**
     * 4-way direction offsets for DFS loop detection.
     * Left, Up, Right, Down, Left, Up (with wraparound entries).
     * From SStage_4_Directions (sonic3k.asm:13225).
     */
    public static final int[] DIRECTIONS_4 = {
            -1, -0x20, 1, 0x20, -1, -0x20
    };

    /**
     * Grid traversal direction tables for perspective rendering.
     * 4 quadrants (one per 90-degree angle range), each with 6 words.
     * From word_98B0 (sonic3k.asm:12229).
     * Each entry: [col_base, row_start, col_step, col_mask, row_step, row_mask]
     */
    public static final int[][] PERSPECTIVE_DIRECTION_TABLES = {
            { 0x18, 6,  1, 0x1F, -1, 0x1F },   // Angle 0x00-0x3F (North)
            { 8,    6, -1, 0x1F, -1, 0x1F },    // Angle 0x40-0x7F (West)
            { 8,   0x1A, -1, 0x1F, 1, 0x1F },   // Angle 0x80-0xBF (South)
            { 0x18, 0x1A, 1, 0x1F, 1, 0x1F },   // Angle 0xC0-0xFF (East)
    };

    // ==================== Animation Tables ====================

    /**
     * Walking animation frame sequence (12 frames, looping).
     * From byte_91E8 (sonic3k.asm:11592).
     */
    public static final int[] ANIM_WALKING = {
            2, 6, 7, 8, 7, 6, 2, 3, 4, 5, 4, 3, 1, 0
    };

    /**
     * P1 jumping animation frame sequence.
     * From byte_91F6 (sonic3k.asm:11594).
     */
    public static final int[] ANIM_JUMP_P1 = {
            9, 0xB, 0xA, 0xB, 9, 0xB, 0xA, 0xB, 9, 0xB, 0xA, 0xB, 0xB, 0
    };

    /**
     * P2 (Tails) jumping animation frame sequence.
     * From byte_9204 (sonic3k.asm:11596).
     */
    public static final int[] ANIM_JUMP_P2 = {
            9, 0xA, 0xB, 9, 0xA, 0xB, 9, 0xA, 0xB, 9, 0xA, 0xB, 0xB, 0
    };

    // ==================== Collision Response Queue ====================

    /** Maximum collision response entries. */
    public static final int COLLISION_QUEUE_SIZE = 32;
    /** Collision response entry size in bytes. */
    public static final int COLLISION_ENTRY_SIZE = 8;
    /** Response type: ring collection animation. */
    public static final int RESPONSE_RING = 1;
    /** Response type: blue sphere collection animation. */
    public static final int RESPONSE_BLUE_SPHERE = 2;
    /** Ring animation timer reset value (6 frames between transitions). */
    public static final int RING_ANIM_TIMER = 5;
    /** Blue sphere animation timer reset value (9 frames). */
    public static final int BLUE_SPHERE_ANIM_TIMER = 9;
    /**
     * Ring animation cell sequence. Cell values cycle through these before clearing.
     * From byte_9E2E (sonic3k.asm:12801).
     */
    public static final int[] RING_ANIM_CELLS = { 6, 7, 8, 9, 0 };

    // ==================== Stage Completion ====================

    /** Clear timer threshold for state advance. */
    public static final int CLEAR_TIMER_COMPLETE = 0x100;
    /** Clear timer acceleration threshold 1. */
    public static final int CLEAR_TIMER_ACCEL_1 = 0x40;
    /** Clear timer acceleration threshold 2. */
    public static final int CLEAR_TIMER_ACCEL_2 = 0x80;
    /** Emerald countdown timer initial value. */
    public static final int EMERALD_TIMER_INIT = 120;
    /** Speed applied during clear sequence. */
    public static final int CLEAR_VELOCITY = 0x800;
    /** Banner display duration in frames (3 seconds). */
    public static final int BANNER_DISPLAY_FRAMES = 3 * 60;
    /** Banner slide speed in pixels per frame. */
    public static final int BANNER_SLIDE_SPEED = 0x10;
    /** Banner max slide distance. */
    public static final int BANNER_MAX_OFFSET = 0xC0;

    // ==================== Screen / Rendering ====================

    /** Screen center X for player sprite. */
    public static final int PLAYER_SCREEN_CENTER_X = 0xA0;
    /** Screen center Y for player sprite. */
    public static final int PLAYER_SCREEN_CENTER_Y = 0x70;
    /** Visible grid cells in each direction for perspective rendering. */
    public static final int PERSPECTIVE_GRID_SIZE = 16;
    /** Number of perspective frames (moving). */
    public static final int PERSPECTIVE_MOVING_FRAMES = 16;
    /** Maximum sprites per frame (VDP sprite table limit). */
    public static final int MAX_SPRITES = 0x50;
    /** Banner center X position. */
    public static final int BANNER_CENTER_X = 0x120;
    /** Banner center Y position (Get Blue Spheres text). */
    public static final int BANNER_Y = 0xE8;
    /** HUD icons position X. */
    public static final int HUD_ICONS_X = 0x120;
    /** HUD icons position Y. */
    public static final int HUD_ICONS_Y = 0x94;

    // ==================== Shadow Positions ====================

    /** P1 shadow Y position. */
    public static final int SHADOW_P1_Y = 0x116;
    /** P1 shadow X position. */
    public static final int SHADOW_P1_X = 0x110;
    /** P2 shadow Y position. */
    public static final int SHADOW_P2_Y = 0x127;
    /** P2 shadow X position. */
    public static final int SHADOW_P2_X = 0x110;

    // ==================== Art Tile Bases ====================
    // From sonic3k.constants.asm:1097-1107

    public static final int ART_TILE_GET_BLUE_SPHERES = 0x055F;
    public static final int ART_TILE_ICONS = 0x0589;
    public static final int ART_TILE_BG = 0x059B;
    public static final int ART_TILE_RING = 0x05A7;
    public static final int ART_TILE_EMERALD = 0x05A7;
    public static final int ART_TILE_SPHERE = 0x0680;
    public static final int ART_TILE_DIGITS = 0x0781;
    public static final int ART_TILE_SHADOW = 0x07A0;
    public static final int ART_TILE_PLAYER2_TAIL = 0x07B0;
    public static final int ART_TILE_PLAYER1 = 0x07D4;
    public static final int ART_TILE_PLAYER2 = 0x07EB;

    // ==================== Sprite Mapping Table ====================
    /**
     * Number of sprite type entries in the MapPtr_A10A table.
     * Each entry is 8 bytes: mapping pointer (4), art_tile (2), flags (2).
     * From MapPtr_A10A (sonic3k.asm:13255).
     */
    public static final int SPRITE_TYPE_COUNT = 14;

    // ==================== Extra Life Thresholds ====================

    /** Ring count for first continue. */
    public static final int EXTRA_LIFE_THRESHOLD_CONTINUE = 50;
    /** Ring count for first extra life. */
    public static final int EXTRA_LIFE_THRESHOLD_1 = 100;
    /** Ring count for second extra life. */
    public static final int EXTRA_LIFE_THRESHOLD_2 = 200;

    // ==================== Tails AI ====================

    /** Tails CPU idle timer (frames of P2 inactivity before AI takes over). */
    public static final int TAILS_CPU_IDLE_TIMEOUT = 600;
    /** Tails position delay entries (how far behind Tails follows P1). */
    public static final int TAILS_POS_DELAY = 4;

    // ==================== Stage Count ====================

    /** Number of special stages. */
    public static final int STAGE_COUNT = 8;
    /** Number of chaos emeralds (stages 0-6 award emeralds; stage 7 is extra). */
    public static final int EMERALD_COUNT = 7;

    // ==================== HUD ====================

    /** Number precision table for 3-digit display: 100, 10, 1. */
    public static final int[] HUD_PRECISION = { 100, 10, 1 };
}
