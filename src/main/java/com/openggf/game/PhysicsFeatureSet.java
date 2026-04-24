package com.openggf.game;

import com.openggf.game.sonic3k.constants.Sonic3kConstants;

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
        /** Ring touch collision half-width in pixels.
         *  S1/S2: 6 (obColType $47, size index $07). S3K: 8 (x_radius in Obj_Attracted_Ring). */
        int ringCollisionWidth,
        /** Ring touch collision half-height in pixels.
         *  S1/S2: 6 (obColType $47, size index $07). S3K: 8 (y_radius in Obj_Attracted_Ring). */
        int ringCollisionHeight,
        /** Whether lightning shield ring attraction is active.
         *  S3K: true. S1/S2: false (no elemental shields). */
        boolean lightningShieldEnabled,
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
        boolean slopeRepelChecksOnObject,
        /** Whether slope repel uses S3K's shallow-slope kick instead of S1/S2's
         *  immediate fall/ground_vel clear.
         *  S3K: true (sonic3k.asm:23907-23948). S1/S2: false. */
        boolean slopeRepelUsesS3kSlipKick,
        /** Whether landing in pinball mode preserves the rolling state.
         *  S2: true (s2.asm:37754 skips the roll-clear block when pinball_mode is set).
         *  S3K: false (sonic3k.asm:24348 clears Status_Roll before the floor-tail flags). */
        boolean pinballLandingPreservesRoll,
        /** Whether top-solid landing accepts the exact edge-contact boundary.
         *  Engine terms: allow a new landing when {@code distY == 0}.
         *  S1/S3K: true for current shared platform parity.
         *  S2: false — {@code PlatformObject_ChkYRange} only falls through for
         *  {@code d0 in [-16,-1]}, excluding the {@code d0 == 0} boundary
         *  (s2.asm:35692-35703). */
        boolean topSolidLandingAllowsZeroDist,
        /** Whether an airborne underside hit on a solid object clears ground speed.
         *  Engine terms: when the player is in air and hits the object's bottom,
         *  also zero {@code gSpeed}/{@code ground_vel} in addition to {@code ySpeed}.
         *  S3K: true â€” {@code SolidObjectFull_Offset_1P} clears {@code ground_vel(a1)}
         *  on the {@code Status_InAir} underside path before resolving bottom contact
         *  (s3.asm:34170-34177 / sonic3k.asm:48000-48007).
         *  S1/S2: false â€” their underside solid paths only zero {@code y_vel}
         *  and preserve inertia/ground velocity
         *  (s1disasm/_incObj/sub SolidObject.asm:238-250, s2.asm:35307-35318). */
        boolean airBottomSolidHitClearsGroundSpeed,
        /** Whether full-solid underside overlap uses the player's current y-radius
         *  on both halves of the vertical collision box.
         *  Current parity: S1=true for rolling glass-block behavior, S2/S3K=false
         *  to preserve the taller underside box used by existing solid/spring traces. */
        boolean fullSolidBottomOverlapUsesCurrentYRadiusOnly,
        /** Maximum vertical scroll speed for airborne + fast-ground camera paths.
         *  S1/S2: 16 (0x10) pixels/frame (s2.asm:18190 ".doScroll_fast").
         *  S3K:   24 (0x18) pixels/frame (sonic3k.asm:loc_1C1B0; s2.asm:18189 "S3K uses 24 instead of 16"). */
        int fastScrollCap,
        /** Whether the boss/special-enemy hit bounce also negates ground velocity.
         *  S3K: true for the {@code boss_hitcount2} path in Touch_Enemy
         *  (sonic3k.asm:20913-20915 negates x_vel, y_vel, and ground_vel).
         *  S1/S2: false; S2's corresponding boss and collision_property paths
         *  only negate x_vel/y_vel (s2.asm:84815-84829). */
        boolean bossHitNegatesGroundSpeed,
        /** Whether stage rings are collected through the object-touch-response
         *  pipeline (Obj25 Ring in S1) instead of the RingManager bounding-box
         *  sweep (Touch_Rings_Test in S2/S3K).
         *  S1: true (s1disasm 25 Rings.asm: ring is a touch-response object).
         *  S2/S3K: false (s2.asm:25034, sonic3k.asm Touch_Rings_Test: sweep in bookkeeping). */
        boolean stageRingsUseObjectTouchCollection,
        /** Pixel threshold that gates the sidekick CPU follow-AI's input override.
         *  When {@code |delayedSonicX - tailsX| >= threshold}, the CPU forces
         *  {@code Ctrl_2_logical} LEFT or RIGHT based on the sign; below the
         *  threshold, the recorded delayed input is passed through unmodified,
         *  so Tails can still skid/brake when Sonic's input disagrees with the
         *  pure chase direction.
         *  S2:  0x10 (s2.asm:38952 TailsCPU_Normal_FollowLeft,
         *             s2.asm:38967 TailsCPU_Normal_FollowRight).
         *  S3K: 0x30 (sonic3k.asm:26712 loc_13DF2,
         *             sonic3k.asm:26729 loc_13E26). */
        int sidekickFollowSnapThreshold,
        /** Off-screen marker X-position written by the sidekick CPU despawn routine.
         *  S3K: 0x7F00 (sonic3k.asm:26806 sub_13ECA writes {@code #$7F00, x_pos(a0)}).
         *  S2:  0x4000 — engine placeholder preserved for parity with existing S2
         *  behaviour and traces; S2's TailsCPU respawn instead resets to Sonic's
         *  position (s2.asm: TailsCPU_RespawnTails) so this value is largely
         *  inert there.
         *  S1:  0x4000 — S1 has no Tails CPU sidekick, value is unreachable;
         *  kept symmetric with S2 to avoid an "unused" sentinel. */
        int sidekickDespawnX
) {
    /** S1: no delay - camera pans immediately (s1.asm: Sonic_LookUp directly modifies v_lookshift). */
    public static final short LOOK_SCROLL_DELAY_NONE = 0;
    /** S2/S3K: 120-frame (2-second) delay before camera pans (s2.asm:36402-36405). */
    public static final short LOOK_SCROLL_DELAY_S2 = 0x78;

    /** S1/S2: airborne/fast-ground camera scroll cap: 16 pixels/frame. */
    public static final int FAST_SCROLL_CAP_S2 = 16;
    /** S3K: airborne/fast-ground camera scroll cap: 24 pixels/frame. */
    public static final int FAST_SCROLL_CAP_S3K = 24;

    /** S1: floor check every 4 frames (s1disasm 25 & 37 Rings.asm: andi.b #3,d0). */
    public static final int RING_FLOOR_CHECK_MASK_S1 = 0x03;
    /** S2/S3K: floor check every 8 frames (s2.asm:25067 / sonic3k.asm: andi.b #7,d0). */
    public static final int RING_FLOOR_CHECK_MASK_S2 = 0x07;

    /** S1/S2: ring collision half-size 6px (obColType $47, size index $07). */
    public static final int RING_COLLISION_SIZE_S1 = 6;
    /** S1/S2: ring collision half-size 6px (obColType $47, size index $07). */
    public static final int RING_COLLISION_SIZE_S2 = 6;
    /** S3K: ring collision half-size 8px (x_radius/y_radius in Obj_Attracted_Ring). */
    public static final int RING_COLLISION_SIZE_S3K = 8;

    /** S2: sidekick CPU follow-AI overrides leader's delayed input when |dx| >= 0x10
     *  (s2.asm:38952 TailsCPU_Normal_FollowLeft, s2.asm:38967 TailsCPU_Normal_FollowRight). */
    public static final int SIDEKICK_FOLLOW_SNAP_S2 = 0x10;
    /** S3K: sidekick CPU follow-AI overrides leader's delayed input when |dx| >= 0x30
     *  (sonic3k.asm:26712 loc_13DF2, sonic3k.asm:26729 loc_13E26). */
    public static final int SIDEKICK_FOLLOW_SNAP_S3K = 0x30;

    /** S1/S2: engine placeholder for the sidekick despawn off-screen X marker.
     *  S1 has no CPU sidekick (value is unreachable). S2's TailsCPU respawn does
     *  not consume this marker — it resets Tails to Sonic's position — so the
     *  current 0x4000 value is preserved to avoid disturbing S2 traces. */
    public static final int SIDEKICK_DESPAWN_X_S2 = 0x4000;
    /** S3K: ROM sub_13ECA writes {@code #$7F00, x_pos(a0)} as the off-screen
     *  marker X for despawned Tails (sonic3k.asm:26806). */
    public static final int SIDEKICK_DESPAWN_X_S3K = Sonic3kConstants.TAILS_CPU_DESPAWN_X;

    /** Sonic 1: no spindash, single collision path, fixed AnglePos threshold, instant look scroll, water shimmer,
     *  always caps ground speed on input (s1disasm/_incObj/01 Sonic.asm:554-558),
     *  no angle diff cardinal snap (s1disasm Sonic_Angle directly applies sensor angle),
     *  simple edge balance: single animation, always faces edge (s1disasm/_incObj/01 Sonic.asm:354-375). */
    public static final PhysicsFeatureSet SONIC_1 = new PhysicsFeatureSet(
            false, null, CollisionModel.UNIFIED, true, LOOK_SCROLL_DELAY_NONE, true, true, false, false, false, false,
            RING_FLOOR_CHECK_MASK_S1, RING_COLLISION_SIZE_S1, RING_COLLISION_SIZE_S1, false,
            null, (short) 0, true, false, false, false, false, true, false, true, FAST_SCROLL_CAP_S2, false, true,
            SIDEKICK_FOLLOW_SNAP_S2, SIDEKICK_DESPAWN_X_S2);

    /** Sonic 2: spindash with standard speed table (s2.asm:37294), dual collision paths, delayed look scroll,
     *  preserves high ground speed on input (s2.asm:36610-36616),
     *  angle diff cardinal snap (s2.asm Sonic_Angle:42658-42664),
     *  extended edge balance: 4 states with precarious/facing-away checks (s2.asm:36246-36373). */
    public static final PhysicsFeatureSet SONIC_2 = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    }, CollisionModel.DUAL_PATH, false, LOOK_SCROLL_DELAY_S2, false, false, false, false, true, true,
            RING_FLOOR_CHECK_MASK_S2, RING_COLLISION_SIZE_S2, RING_COLLISION_SIZE_S2, false,
            null, (short) 0, true, false, true, false, true, false, false, false, FAST_SCROLL_CAP_S2, false, false,
            SIDEKICK_FOLLOW_SNAP_S2, SIDEKICK_DESPAWN_X_S2);

    /** Sonic 3&K: spindash with same speed table as S2, dual collision paths, delayed look scroll,
     *  preserves high ground speed on input, elemental shields,
     *  angle diff cardinal snap (inherited from S2 Sonic_Angle),
     *  extended edge balance (inherited from S2),
     *  Super spindash table (sonic3k.asm:23743 word_11D04),
     *  duck while moving below 0x100 (sonic3k.asm:23236). */
    public static final PhysicsFeatureSet SONIC_3K = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    }, CollisionModel.DUAL_PATH, false, LOOK_SCROLL_DELAY_S2, false, false, true, true, true, true,
            RING_FLOOR_CHECK_MASK_S2, RING_COLLISION_SIZE_S3K, RING_COLLISION_SIZE_S3K, true,
            new short[]{
            0x0B00, 0x0B80, 0x0C00, 0x0C80, 0x0D00, 0x0D80, 0x0E00, 0x0E80, 0x0F00
    }, (short) 0x100, true, true, false, true, false, true, true, false, FAST_SCROLL_CAP_S3K, true, false,
            SIDEKICK_FOLLOW_SNAP_S3K, SIDEKICK_DESPAWN_X_S3K);

    /** Returns true when the game supports dual collision paths (primary/secondary). */
    public boolean hasDualCollisionPaths() {
        return collisionModel == CollisionModel.DUAL_PATH;
    }
}
