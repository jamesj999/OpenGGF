package com.openggf.game;

/**
 * Feature flags gating optional physics mechanics per game.
 *
 * <p>Controls whether mechanics like spindash and collision path switching
 * are available in the current game. When a mechanic is disabled, the
 * movement kernel skips it entirely.
 */
public record PhysicsFeatureSet(
        boolean spindashEnabled,
        short[] spindashSpeedTable,
        CollisionModel collisionModel,
        boolean fixedAnglePosThreshold,
        short lookScrollDelay,
        boolean waterShimmerEnabled,
        boolean inputAlwaysCapsGroundSpeed,
        boolean elementalShieldsEnabled,
        boolean angleDiffCardinalSnap,
        boolean extendedEdgeBalance
) {
    /** S1: no delay - camera pans immediately (s1.asm: Sonic_LookUp directly modifies v_lookshift). */
    public static final short LOOK_SCROLL_DELAY_NONE = 0;
    /** S2/S3K: 120-frame (2-second) delay before camera pans (s2.asm:36402-36405). */
    public static final short LOOK_SCROLL_DELAY_S2 = 0x78;

    /** Sonic 1: no spindash, single collision path, fixed AnglePos threshold, instant look scroll, water shimmer,
     *  always caps ground speed on input (s1disasm/_incObj/01 Sonic.asm:554-558),
     *  no angle diff cardinal snap (s1disasm Sonic_Angle directly applies sensor angle),
     *  simple edge balance: single animation, always faces edge (s1disasm/_incObj/01 Sonic.asm:354-375). */
    public static final PhysicsFeatureSet SONIC_1 = new PhysicsFeatureSet(
            false, null, CollisionModel.UNIFIED, true, LOOK_SCROLL_DELAY_NONE, true, true, false, false, false);

    /** Sonic 2: spindash with standard speed table (s2.asm:37294), dual collision paths, delayed look scroll,
     *  preserves high ground speed on input (s2.asm:36610-36616),
     *  angle diff cardinal snap (s2.asm Sonic_Angle:42658-42664),
     *  extended edge balance: 4 states with precarious/facing-away checks (s2.asm:36246-36373). */
    public static final PhysicsFeatureSet SONIC_2 = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    }, CollisionModel.DUAL_PATH, false, LOOK_SCROLL_DELAY_S2, false, false, false, true, true);

    /** Sonic 3&K: spindash with same speed table as S2, dual collision paths, delayed look scroll,
     *  preserves high ground speed on input, elemental shields,
     *  angle diff cardinal snap (inherited from S2 Sonic_Angle),
     *  extended edge balance (inherited from S2). */
    public static final PhysicsFeatureSet SONIC_3K = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    }, CollisionModel.DUAL_PATH, false, LOOK_SCROLL_DELAY_S2, false, false, true, true, true);

    /** Returns true when the game supports dual collision paths (primary/secondary). */
    public boolean hasDualCollisionPaths() {
        return collisionModel == CollisionModel.DUAL_PATH;
    }
}
