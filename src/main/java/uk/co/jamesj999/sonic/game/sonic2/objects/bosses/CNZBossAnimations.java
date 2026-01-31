package uk.co.jamesj999.sonic.game.sonic2.objects.bosses;

/**
 * CNZ Boss animation scripts (Ani_obj51).
 * <p>
 * ROM Reference: s2.asm:66612 (Ani_obj51)
 * <p>
 * Animation format:
 * - Byte 0: Frame duration (ticks per frame)
 * - Following bytes: Frame indices, terminated by command
 * - $FF = loop from start
 * - $FE = loop from specific offset
 * - $FD = change animation to value, then loop
 * - $FC = increment routine counter
 */
public final class CNZBossAnimations {

    private CNZBossAnimations() {
        // Constants only
    }

    /**
     * Animation 0 (byte_320B0): Idle face
     * Duration: $F (15 ticks)
     * Frames: 1, loop
     */
    public static final int[] ANIM_0_IDLE = {0x0F, 1, -1}; // $FF = loop

    /**
     * Animation 1 (byte_320B3): Propeller variant 1
     * Duration: $F (15 ticks)
     * Frames: 4, $FF (cmd), 5, $FC (cmd), 2 (arg)
     */
    public static final int[] ANIM_1_PROPELLER1 = {0x0F, 4, -1, 5, -4, 2};

    /**
     * Animation 2 (byte_320B9): Propeller variant 2
     * Duration: $F (15 ticks)
     * Frames: 2, $FF (cmd), 3, $FC (cmd), 2 (arg)
     */
    public static final int[] ANIM_2_PROPELLER2 = {0x0F, 2, -1, 3, -4, 2};

    /**
     * Animation 3 (byte_320BF): Defeated face
     * Duration: 7 ticks
     * Frames: 6, 7, loop
     */
    public static final int[] ANIM_3_DEFEATED = {0x07, 6, 7, -1};

    /**
     * Animation 4 (byte_320C3): Lower collision mode (electrodes)
     * Duration: 1 tick
     * Frames: $C (12), $D (13), $E (14), loop
     */
    public static final int[] ANIM_4_LOWER_COLLISION = {0x01, 0x0C, 0x0D, 0x0E, -1};

    /**
     * Animation 5 (byte_320C8): Laughing face
     * Duration: 7 ticks
     * Frames: 8, 9 (repeats), then $FD cmd (change anim to 3)
     */
    public static final int[] ANIM_5_LAUGH = {0x07, 8, 9, 8, 9, 8, 9, 8, 9, -3, 3};

    /**
     * Animation 6 (byte_320D3): Hurt face
     * Duration: 7 ticks
     * Frames: $A (10) repeated, then $FD cmd (change anim to 3)
     */
    public static final int[] ANIM_6_HURT = {0x07, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A, 0x0A, -3, 3};

    /**
     * Animation 7 (byte_320DD): Electric ball explosion
     * Duration: 3 ticks
     * Frames: $13 (19), $14 (20), loop
     */
    public static final int[] ANIM_7_BALL_EXPLODE = {0x03, 0x13, 0x14, -1};

    /**
     * Animation 8 (byte_320E1): Normal face (idle variant)
     * Duration: 1 tick
     * Frames: 0, loop
     */
    public static final int[] ANIM_8_FACE_NORMAL = {0x01, 0, -1};

    /**
     * Animation 9 (byte_320E4): Zap mode (wide electricity field)
     * Duration: 1 tick
     * Frames: $F (15), $10 (16), $11 (17), loop
     */
    public static final int[] ANIM_9_ZAP_MODE = {0x01, 0x0F, 0x10, 0x11, -1};

    /**
     * All animation scripts indexed by animation ID.
     */
    public static final int[][] SCRIPTS = {
            ANIM_0_IDLE,
            ANIM_1_PROPELLER1,
            ANIM_2_PROPELLER2,
            ANIM_3_DEFEATED,
            ANIM_4_LOWER_COLLISION,
            ANIM_5_LAUGH,
            ANIM_6_HURT,
            ANIM_7_BALL_EXPLODE,
            ANIM_8_FACE_NORMAL,
            ANIM_9_ZAP_MODE
    };

    /**
     * Get animation script by ID.
     *
     * @param animId Animation ID (0-9)
     * @return Animation script data, or empty array if invalid
     */
    public static int[] getScript(int animId) {
        if (animId < 0 || animId >= SCRIPTS.length) {
            return new int[0];
        }
        return SCRIPTS[animId];
    }

    /**
     * Initial Boss_AnimationArray values (ROM: loc_319D6).
     * 5 entries × 2 bytes each:
     * - Entry 0: anim=8, frame=0 (main sprite - Eggman face)
     * - Entry 1: anim=1, frame=0 (sub2 - propeller)
     * - Entry 2: anim=$10, frame=0 (timing)
     * - Entry 3: anim=3, frame=0 (sub3 - electrode)
     * - Entry 4: anim=2, frame=0 (sub5 - electrode)
     */
    public static final int[] INITIAL_ANIM_ARRAY = {
            8, 0,     // Entry 0
            1, 0,     // Entry 1
            0x10, 0,  // Entry 2
            3, 0,     // Entry 3
            2, 0      // Entry 4
    };
}
