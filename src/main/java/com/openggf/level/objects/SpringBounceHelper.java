package com.openggf.level.objects;

/**
 * Shared spring bounce constants and utility methods used across S1, S2, and S3K spring objects.
 * <p>
 * ROM reference (all games): Spring_Powers / Obj41_Strengths / Obj_Spring_Strengths
 * <pre>
 *   dc.w -$1000, -$A00
 * </pre>
 * Bit 1 of subtype selects: 0 = red (index 0), 1 = yellow (index 1).
 * <p>
 * This helper provides the shared strength constants and simple bounce primitives.
 * Game-specific quirks (S3K down-spring velocity cap, S3K horizontal approach zone,
 * S3K reverse gravity, S2 diagonal position nudges, etc.) remain in each game's
 * spring implementation.
 */
public final class SpringBounceHelper {

    private SpringBounceHelper() {
        // Utility class - no instantiation
    }

    // --- Strength constants (negative = upward in Y-down coordinate system) ---

    /**
     * Red spring strength: -0x1000.
     * ROM: Spring_Powers index 0 / Obj41_Strengths index 0
     */
    public static final int STRENGTH_RED = -0x1000;

    /**
     * Yellow spring strength: -0x0A00.
     * ROM: Spring_Powers index 1 / Obj41_Strengths index 1
     */
    public static final int STRENGTH_YELLOW = -0x0A00;

    /**
     * Standard control lock duration applied after a spring bounce (frames).
     * ROM: move.w #$F,move_lock(a1) / move.w #$F,objoff_3E(a1)
     * Used by all standard springs in S1, S2, and S3K.
     */
    public static final int CONTROL_LOCK_FRAMES = 15;

    // --- Strength resolution ---

    /**
     * Returns the spring strength based on subtype bit 1.
     * <p>
     * ROM pattern (all games):
     * <pre>
     *   andi.w #2,d0       ; isolate bit 1
     *   move.w Strengths(pc,d0.w),objoff_30(a0)
     * </pre>
     *
     * @param subtype the spring object's subtype byte
     * @return {@link #STRENGTH_RED} if bit 1 is clear, {@link #STRENGTH_YELLOW} if set
     */
    public static int strengthFromSubtype(int subtype) {
        return (subtype & 0x02) != 0 ? STRENGTH_YELLOW : STRENGTH_RED;
    }

    /**
     * Returns the spring strength for a red/yellow flag.
     *
     * @param red true for red spring, false for yellow
     * @return {@link #STRENGTH_RED} or {@link #STRENGTH_YELLOW}
     */
    public static int strength(boolean red) {
        return red ? STRENGTH_RED : STRENGTH_YELLOW;
    }
}
