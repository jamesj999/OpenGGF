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
        boolean instaShieldEnabled,
        boolean angleDiffCardinalSnap,
        boolean extendedEdgeBalance,
        /** Bitmask for scattered ring floor-check frequency.
         *  S1: 0x03 (every 4 frames, andi.b #3,d0). S2/S3K: 0x07 (every 8 frames, andi.b #7,d0). */
        int ringFloorCheckMask,
        /** Spindash speed table used when in Super/Hyper form.
         *  S3K: word_11D04 (sonic3k.asm:23743), higher speeds ($B00-$F00). S1/S2: null (use normal table). */
        short[] superSpindashSpeedTable,
        /** Speed threshold below which pressing down enters crouch instead of roll.
         *  S3K: 0x100 (sonic3k.asm:23236). S1/S2: 0 (disabled — crouch only when standing still).
         *  When &gt; 0, also overrides {@code minStartRollSpeed} as the roll initiation threshold. */
        short movingCrouchThreshold,
        /** Whether CalcRoomInFront (ground wall collision) runs during ground/roll movement.
         *  S2/S3K: true (s2.asm:36476 CalcRoomInFront called at end of Sonic_Move and Obj01_RollSpeed).
         *  S1: false (s1disasm/_incObj/01 Sonic.asm: Sonic_MdNormal has no CalcRoomInFront). */
        boolean groundWallCollisionEnabled,
        /** Whether air control preserves speeds above max (super speed from springs/ramps).
         *  S3K: true (sonic3k.asm:23110-23120 — undo acceleration, keep original if already past max).
         *  S1/S2: false (s1:01 Sonic.asm:740-750, s2.asm:36837-36840 — unconditional cap). */
        boolean airSuperspeedPreserved,
        /** Whether Sonic_SlopeRepel checks isOnObject before applying slope slip.
         *  S2/S3K: true — btst #sta_onObj,status(a0) / bne.s return (s2.asm:37432).
         *  S1: false — no isOnObject check (s1disasm/_incObj/01 Sonic.asm:1107-1135).
         *  When false, slope repel can trigger while standing on object surfaces. */
        boolean slopeRepelChecksOnObject
) {
    /** S1: no delay - camera pans immediately (s1.asm: Sonic_LookUp directly modifies v_lookshift). */
    public static final short LOOK_SCROLL_DELAY_NONE = 0;
    /** S2/S3K: 120-frame (2-second) delay before camera pans (s2.asm:36402-36405). */
    public static final short LOOK_SCROLL_DELAY_S2 = 0x78;

    /** S1: floor check every 4 frames (s1disasm 25 & 37 Rings.asm: andi.b #3,d0). */
    public static final int RING_FLOOR_CHECK_MASK_S1 = 0x03;
    /** S2/S3K: floor check every 8 frames (s2.asm:25067 / sonic3k.asm: andi.b #7,d0). */
    public static final int RING_FLOOR_CHECK_MASK_S2 = 0x07;

    /** Sonic 1: no spindash, single collision path, fixed AnglePos threshold, instant look scroll, water shimmer,
     *  always caps ground speed on input (s1disasm/_incObj/01 Sonic.asm:554-558),
     *  no angle diff cardinal snap (s1disasm Sonic_Angle directly applies sensor angle),
     *  simple edge balance: single animation, always faces edge (s1disasm/_incObj/01 Sonic.asm:354-375). */
    public static final PhysicsFeatureSet SONIC_1 = new PhysicsFeatureSet(
            false, null, CollisionModel.UNIFIED, true, LOOK_SCROLL_DELAY_NONE, true, true, false, false, false, false,
            RING_FLOOR_CHECK_MASK_S1, null, (short) 0, true, false, false);

    /** Sonic 2: spindash with standard speed table (s2.asm:37294), dual collision paths, delayed look scroll,
     *  preserves high ground speed on input (s2.asm:36610-36616),
     *  angle diff cardinal snap (s2.asm Sonic_Angle:42658-42664),
     *  extended edge balance: 4 states with precarious/facing-away checks (s2.asm:36246-36373). */
    public static final PhysicsFeatureSet SONIC_2 = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    }, CollisionModel.DUAL_PATH, false, LOOK_SCROLL_DELAY_S2, false, false, false, false, true, true,
            RING_FLOOR_CHECK_MASK_S2, null, (short) 0, true, false, true);

    /** Sonic 3&K: spindash with same speed table as S2, dual collision paths, delayed look scroll,
     *  preserves high ground speed on input, elemental shields,
     *  angle diff cardinal snap (inherited from S2 Sonic_Angle),
     *  extended edge balance (inherited from S2),
     *  Super spindash table (sonic3k.asm:23743 word_11D04),
     *  duck while moving below 0x100 (sonic3k.asm:23236). */
    public static final PhysicsFeatureSet SONIC_3K = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    }, CollisionModel.DUAL_PATH, false, LOOK_SCROLL_DELAY_S2, false, false, true, true, true, true,
            RING_FLOOR_CHECK_MASK_S2, new short[]{
            0x0B00, 0x0B80, 0x0C00, 0x0C80, 0x0D00, 0x0D80, 0x0E00, 0x0E80, 0x0F00
    }, (short) 0x100, true, true, true);

    /** Returns true when the game supports dual collision paths (primary/secondary). */
    public boolean hasDualCollisionPaths() {
        return collisionModel == CollisionModel.DUAL_PATH;
    }
}
