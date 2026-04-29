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
         *  S1/S2: 6 (obColType $47, size index $07). S3K normal placed rings:
         *  6 (sonic3k.asm:18473-18474 Test_Ring_Collisions d1=6, d6=$C). */
        int ringCollisionWidth,
        /** Ring touch collision half-height in pixels.
         *  S1/S2: 6 (obColType $47, size index $07). S3K normal placed rings:
         *  6 (sonic3k.asm:18473-18474 Test_Ring_Collisions d1=6, d6=$C). */
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
        int sidekickDespawnX,
        /** Pixel offset subtracted from the sidekick CPU follow-AI's leader-x
         *  history target so the sidekick steers a fixed bias to the LEFT of
         *  the leader.
         *  <p>S3K: 0x20 (sonic3k.asm:26694 {@code subi.w #$20, d2}). The offset
         *  is suppressed when the leader is riding an object
         *  ({@code Status_OnObj} bit set, sonic3k.asm:26690-26691) or is moving
         *  faster than the follower could chase ({@code ground_vel >= $400},
         *  sonic3k.asm:26692-26693).
         *  <p>S1/S2: 0 — S2's {@code TailsCPU_Normal} reads the recorded
         *  Sonic position directly into {@code d2} and subtracts {@code x_pos}
         *  with no intermediate adjustment (s2.asm:38933, 38945); S1 has no
         *  CPU sidekick. */
        int sidekickFollowLeadOffset,
        /**
         * Whether the sidekick CPU's SPAWNING-state catch-up trigger requires
         * the leader to be on the ground / not in water / not roll-jumping
         * before respawning.
         * <p>S2: {@code true} — {@code TailsCPU_Spawning} (s2.asm:38751-38762)
         * checks {@code Status_OnGround}, {@code Status_Underwater},
         * {@code Status_RollJump} and skips respawn while any are blocking.
         * <p>S3K: {@code false} — {@code Tails_Catch_Up_Flying}
         * (sonic3k.asm:26474-26486) does NOT check those; it only honours the
         * 64-frame {@code (Level_frame_counter & $3F) == 0} gate, the leader's
         * {@code object_control} bit 7, and the leader's {@code Status_Super}
         * bit. The engine's {@code SPAWNING} state needs the leader-grounded
         * checks gated off for S3K so the catch-up handover fires when ROM
         * does (e.g. CNZ where Sonic is airborne the whole time after the
         * carry release).
         * <p>S1: {@code true} (no Tails CPU; value unreachable).
         */
        boolean sidekickSpawningRequiresGroundedLeader,
        /**
         * Whether the engine's render-flag visibility test should use the S3K
         * Render_Sprites Y-margin of {@code height_pixels = 0x18 = 24} (matching
         * sonic3k.asm:36336-36370) instead of the legacy engine default of 32.
         * <p>S3K: {@code true}. ROM's check is
         * {@code (relY + 24) & Screen_Y_wrap_value >= 2*24 + 224 = 272}; with
         * the default {@code Screen_Y_wrap_value = 0xFFFF} this reduces to
         * {@code relY in [-24, 248)}.
         * <p>S1/S2: {@code false}. They have no {@code Screen_Y_wrap_value}
         * mechanism and the existing 32-margin matches their trace baselines.
         */
        boolean useScreenYWrapValueForVisibility,
        /**
         * Whether the sidekick CPU despawn check fires on an object-id mismatch
         * (Tails moves between two distinct object types while off-screen).
         * <p>S2: {@code true} — {@code TailsCPU_CheckDespawn}
         * (s2.asm:39051-39068) does {@code cmp.b id(a3),d0}, comparing the
         * cached {@code Tails_interact_ID} byte to the current object's
         * {@code id} byte. Different object types have different ID bytes, so
         * the engine's per-id mismatch check models this faithfully.
         * <p>S3K: {@code false} — {@code sub_13EFC} (sonic3k.asm:26823) does
         * {@code cmp.w (a3),d0}, comparing the cached {@code Tails_CPU_interact}
         * word to the FIRST WORD of the object's structure (the high word of
         * its routine pointer). All S3K gameplay objects live in ROM
         * {@code 0x0001xxxx-0x0007xxxx}, so the high word is identical
         * (typically {@code 0x0003}) for virtually every object encountered in
         * normal play — the ROM check therefore almost never fires. The CNZ1
         * trace F1685 barber-pole-to-wire-cage transition (object IDs
         * {@code 0x4D} → {@code 0x4E}, both routines at {@code 0x000338xx}) is
         * a concrete example: ROM cached/current high words are both
         * {@code 0x0003}, no despawn; engine compares 8-bit object IDs and
         * spuriously despawns Tails.
         * <p>S1: unreachable (no Tails CPU). {@code true} keeps symmetry with
         * S2.
         */
        boolean sidekickDespawnUsesObjectIdMismatch,
        /**
         * Mask applied to the recorded leader status byte during the sidekick
         * CPU's "fly-back to leader" exit gate (engine APPROACHING state -&gt;
         * NORMAL). When any masked bit is set in the recorded leader status,
         * the sidekick stays in flight; when all masked bits are clear AND
         * position residuals are zero, the sidekick lands and resumes ground
         * follow AI.
         * <p>S2:  {@code 0xD2} (s2.asm:38872-38873 {@code andi.b #$D2,d2}).
         * Bits 1+4+6+7 = in_air + roll_jump + underwater + bit7. Effectively
         * blocks landing while Sonic is airborne.
         * <p>S3K: {@code 0x80} (sonic3k.asm:26625 {@code andi.b #$80,d2}).
         * Bit 7 only. Bit 7 is not a standard Sonic status flag, so this gate
         * almost never blocks in practice — landing is decided by position
         * residuals + leader-alive only.
         * <p>S1: 0 (no CPU sidekick; value unreachable).
         */
        int sidekickFlyLandStatusBlockerMask,
        /**
         * Whether the sidekick CPU's fly-back exit gate also requires the
         * leader's sprite routine to be {@code &lt; 6} (alive, not dead/death-
         * sequence) before transitioning back to NORMAL.
         * <p>S3K: {@code true} (sonic3k.asm:26629-26630
         * {@code cmpi.b #6,(Player_1+routine).w / bhs.s loc_13D42}).
         * <p>S2:  {@code false} — TailsCPU_Flying_Part2 transitions to NORMAL
         * without checking Sonic's routine (s2.asm:38870-38883).
         * <p>S1:  {@code false} (no CPU sidekick).
         */
        boolean sidekickFlyLandRequiresLeaderAlive,
        /**
         * Whether {@code SolidObject_cont}'s on-screen gate
         * (s2.asm:35140-35145 SolidObject_OnScreenTest, sonic3k.asm:41390-41392
         * loc_1DF88, s1disasm/_incObj/sub SolidObject.asm:124-126
         * Solid_ChkEnter / 86-87 SolidObject2F) skips side / top / bottom contact
         * resolution when the object's render_flags bit 7 is clear (i.e. the
         * camera has scrolled the object's bounding box off-screen).  Off-screen
         * spike-vs-Tails interactions in AIZ depend on this gate to preserve
         * Tails's velocity at trace F2667 when slot 22's spike center is past
         * the camera's left edge.
         *
         * <p>S3K: {@code true} -- AIZ trace replay relies on the gate to match
         * ROM behaviour. S1/S2: {@code false} for now to keep current trace
         * baselines stable while the gate's on-screen semantic (engine
         * cameraBounds.contains vs ROM Render_Sprites bounding-box overlap) is
         * tuned.
         */
        boolean solidObjectOffscreenGate,
        /**
         * Whether the sidekick CPU's despawn check fires when the riding
         * object has been deleted while the sidekick is off-screen. ROM
         * {@code sub_13EFC} (sonic3k.asm:26816) reads the FIRST WORD of the
         * object's SST via {@code (a3)}; when {@code Delete_Referenced_Sprite}
         * (sonic3k.asm:36116-36124) zeros the SST that word becomes 0 and the
         * subsequent {@code cmp.w (a3),d0} mismatch falls into
         * {@code sub_13ECA} (sonic3k.asm:26800), warping Tails to the
         * despawn marker.
         *
         * <p>S3K: {@code true}. Engine analog tracks the
         * {@link com.openggf.level.objects.ObjectInstance} reference Tails was
         * riding; when that instance is gone or destroyed on a subsequent
         * off-screen frame, despawn fires. Required for AIZ trace replay
         * F6255 — Tails rides the AIZ collapsing platform
         * ({@code Obj_CollapsingPlatform} routine {@code loc_20594},
         * sonic3k.asm:44814) which removes itself once its falling-fragment
         * lifecycle completes; ROM despawns Tails the next frame, engine
         * left her drifting through several thousand frames of cascading
         * mismatches.
         *
         * <p>S2: {@code false}. S2's {@code TailsCPU_CheckDespawn}
         * (s2.asm:39051-39068) does the {@code cmp.b id(a3),d0} 8-bit-id
         * compare, which already covers the freed-slot case (id of a freed
         * slot is also 0) — keeping {@code sidekickDespawnUsesObjectIdMismatch}
         * is sufficient there.
         *
         * <p>S1: {@code false}. No CPU sidekick.
         */
        boolean sidekickDespawnUsesRidingInstanceLoss,
        /**
         * Whether the sidekick CPU's despawn-marker write transitions to the
         * {@code CATCH_UP_FLIGHT} state (ROM routine 0x02) instead of the
         * legacy {@code SPAWNING} respawn-strategy state.
         * <p>S3K: {@code true}. ROM {@code sub_13ECA} (sonic3k.asm:26800-26809)
         * writes {@code Tails_CPU_routine = 2}; the next frame the dispatcher
         * runs {@code Tails_Catch_Up_Flying} (sonic3k.asm:26474), which on
         * its 64-frame trigger warps Tails to {@code (Sonic.x, Sonic.y - 0xC0)}
         * and enters routine 0x04 ({@code Tails_FlySwim_Unknown} =
         * {@code FLIGHT_AUTO_RECOVERY}). The engine maps that ROM flow by
         * placing the sidekick directly into {@code CATCH_UP_FLIGHT} after
         * applying the despawn marker, so the per-frame
         * {@code updateCatchUpFlight} handler reproduces the 64-frame /
         * Ctrl_2 trigger ROM-side.
         * <p>S2: {@code false}. S2 has no separate routine-2 catch-up state;
         * its {@code TailsCPU_Spawning} (s2.asm:38755-38782) inlines the
         * 64-frame trigger and the warp-to-Sonic, then jumps directly to
         * routine 0x04. The engine keeps the existing {@code SPAWNING} flow
         * for S2, which mirrors that inlined trigger via
         * {@link #sidekickSpawningRequiresGroundedLeader}.
         * <p>S1: {@code false}. No CPU sidekick.
         */
        boolean sidekickRespawnEntersCatchUpFlight,
        /**
         * Whether the sidekick follow-AI push bypass may keep using the engine's
         * transient push status for a short grace window after object ordering
         * clears the current-frame flag.
         * <p>S3K: {@code true}. The ROM gate at sonic3k.asm:26702-26705 tests
         * Tails' current Status_Push bit before comparing the delayed leader
         * status; the engine's S3K object ordering can clear that transient bit
         * before the CPU controller runs, so this preserves the ROM-visible push
         * continuity for AIZ/CNZ parity.
         * <p>S2: {@code false}. S2's equivalent gate at s2.asm:38943-38946 uses
         * the live Tails status only; carrying the S3K grace into S2 suppresses
         * normal FollowLeft/FollowRight nudges and regresses EHZ traces.
         * <p>S1: {@code false}. No CPU sidekick.
         */
        boolean sidekickPushBypassUsesGraceStatus,
        /**
         * Whether a CPU sidekick with stale push status and no fresh left/right
         * input clears ground velocity before the ground movement step.
         * <p>S3K: {@code true}. The engine uses this to mirror the S3K
         * ground projection/push-collision path that can leave a one-frame
         * collision {@code x_vel} while clearing {@code ground_vel}
         * (sonic3k.asm:27947-28017).
         * <p>S2: {@code false}. S2 {@code TailsCPU_Normal} only uses the live
         * push bit to choose whether to bypass follow steering, then writes
         * {@code Ctrl_2_Logical}; it does not clear velocity before Tails'
         * ground movement dispatch (s2.asm:38943-39027).
         * <p>S1: {@code false}. No CPU sidekick.
         */
        boolean sidekickClearsStalePushVelocityBeforeGroundMove,
        /**
         * Whether the sidekick CPU's frame-gated catch-up/jump checks read the
         * runtime's stored {@code Level_frame_counter} instead of the caller's
         * update argument. S3K Tails CPU checks {@code (Level_frame_counter).w}
         * directly while running from {@code Process_Sprites}; the level loop
         * increments that counter before sprite processing
         * (sonic3k.asm:7884-7894), and AIZ explicitly releases Tails into
         * routine 2 by writing {@code Tails_CPU_routine = 2}
         * (sonic3k.asm:38898-38900). Engine inline sidekick updates can receive
         * a fallback frame argument one tick ahead of the stored ROM-visible
         * counter, which makes the 64-frame catch-up gate fire one row early.
         *
         * <p>S3K: {@code true}. S1/S2: {@code false} to preserve their current
         * sidekick trace cadence.
         */
        boolean sidekickCpuUsesLevelFrameCounter,
        /**
         * Whether the level-boundary right-side check uses the strict
         * "predicted &gt; right" comparison ({@code blo.s}) instead of the
         * "predicted &gt;= right" ({@code bls.s}) comparison.
         * <p>S3K: {@code true}. ROM {@code Player_LevelBound}
         * (sonic3k.asm:23185-23186) does {@code cmp.w d1,d0 / blo.s
         * Player_Boundary_Sides} where {@code d0 = Camera_max_X_pos + 0x128}
         * and {@code d1 = predictedX}; {@code blo} (Branch if Lower) is
         * unsigned and triggers when {@code d0 < d1}, i.e. when
         * {@code predictedX > rightBoundary}. When {@code predictedX ==
         * rightBoundary}, the boundary clamp does NOT fire and Sonic's
         * acceleration is preserved. The engine boundary check must mirror
         * the strict comparison so the right-edge "wall stop then accelerate"
         * sequence does not lose a frame's acceleration. AIZ trace F4768
         * exercises this exact case: at the wall-touch frame Sonic's
         * predicted x equals the right boundary, ROM keeps {@code xs=0x000C}
         * after {@code Sonic_Move}, the engine was zeroing it and accumulating
         * a 12-subpixel lag that cascaded forward to F6736.
         * <p>S2: {@code false}. ROM {@code Sonic_LevelBound}
         * (s2.asm:36932-36933) uses {@code bls.s Sonic_Boundary_Sides} which
         * is {@code <=} unsigned: triggers when {@code predictedX >=
         * rightBoundary}. The non-strict comparison clamps Sonic on the equal
         * frame. Engine non-strict check is correct here.
         * <p>S1: {@code false}. ROM {@code Sonic_LevelBound}
         * (s1disasm/_incObj/01 Sonic.asm:998 {@code bls.s .sides}) matches
         * S2 — non-strict.
         */
        boolean levelBoundaryRightStrict
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
    /** S3K: normal placed-ring collision half-size 6px
     *  (sonic3k.asm:18473-18474 Test_Ring_Collisions d1=6, d6=$C). */
    public static final int RING_COLLISION_SIZE_S3K = 6;

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

    /** S1/S2: no follow lead-offset; sidekick CPU steers directly toward the
     *  recorded leader position (S1 has no CPU sidekick, S2 reads {@code d2}
     *  from the position record without bias — s2.asm:38933, 38945). */
    public static final int SIDEKICK_FOLLOW_LEAD_OFFSET_NONE = 0;
    /** S3K: 0x20-pixel left-of-leader bias on the recorded x history before
     *  the {@code sub.w x_pos(a0), d2} step (sonic3k.asm:26694
     *  {@code subi.w #$20, d2}). */
    public static final int SIDEKICK_FOLLOW_LEAD_OFFSET_S3K = 0x20;

    /** S2: sidekick CPU fly-land status-blocker mask. Bits 1+4+6+7 =
     *  in_air + roll_jump + underwater + bit7. {@code andi.b #$D2,d2} at
     *  s2.asm:38872-38873. */
    public static final int SIDEKICK_FLY_LAND_BLOCKERS_S2 = 0xD2;
    /** S3K: sidekick CPU fly-land status-blocker mask. Bit 7 only.
     *  {@code andi.b #$80,d2} at sonic3k.asm:26625. */
    public static final int SIDEKICK_FLY_LAND_BLOCKERS_S3K = 0x80;
    /** S1: no CPU sidekick — value unreachable. */
    public static final int SIDEKICK_FLY_LAND_BLOCKERS_NONE = 0;

    /** Sonic 1: no spindash, single collision path, fixed AnglePos threshold, instant look scroll, water shimmer,
     *  always caps ground speed on input (s1disasm/_incObj/01 Sonic.asm:554-558),
     *  no angle diff cardinal snap (s1disasm Sonic_Angle directly applies sensor angle),
     *  simple edge balance: single animation, always faces edge (s1disasm/_incObj/01 Sonic.asm:354-375). */
    public static final PhysicsFeatureSet SONIC_1 = new PhysicsFeatureSet(
            false, null, CollisionModel.UNIFIED, true, LOOK_SCROLL_DELAY_NONE, true, true, false, false, false, false,
            RING_FLOOR_CHECK_MASK_S1, RING_COLLISION_SIZE_S1, RING_COLLISION_SIZE_S1, false,
            null, (short) 0, true, false, false, false, false, true, false, true, FAST_SCROLL_CAP_S2, false, true,
            SIDEKICK_FOLLOW_SNAP_S2, SIDEKICK_DESPAWN_X_S2, SIDEKICK_FOLLOW_LEAD_OFFSET_NONE, true /* sidekickSpawningRequiresGroundedLeader: S1 has no Tails CPU */, false /* useScreenYWrapValueForVisibility: S1 keeps 32-margin */,
            true /* sidekickDespawnUsesObjectIdMismatch: S1 has no Tails CPU; symmetric with S2 */,
            SIDEKICK_FLY_LAND_BLOCKERS_NONE, false /* sidekickFlyLandRequiresLeaderAlive: S1 has no CPU sidekick */, false /* solidObjectOffscreenGate: keep current S1 trace baseline */,
            false /* sidekickDespawnUsesRidingInstanceLoss: S1 has no Tails CPU */,
            false /* sidekickRespawnEntersCatchUpFlight: S1 has no Tails CPU */,
            false /* sidekickPushBypassUsesGraceStatus: S1 has no Tails CPU */,
            false /* sidekickClearsStalePushVelocityBeforeGroundMove: S1 has no Tails CPU */,
            false /* sidekickCpuUsesLevelFrameCounter: S1 has no Tails CPU */,
            false /* levelBoundaryRightStrict: S1 uses bls.s (non-strict, predicted >= right) at s1disasm/_incObj/01 Sonic.asm:998 */);

    /** Sonic 2: spindash with standard speed table (s2.asm:37294), dual collision paths, delayed look scroll,
     *  preserves high ground speed on input (s2.asm:36610-36616),
     *  angle diff cardinal snap (s2.asm Sonic_Angle:42658-42664),
     *  extended edge balance: 4 states with precarious/facing-away checks (s2.asm:36246-36373). */
    public static final PhysicsFeatureSet SONIC_2 = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    }, CollisionModel.DUAL_PATH, false, LOOK_SCROLL_DELAY_S2, false, false, false, false, true, true,
            RING_FLOOR_CHECK_MASK_S2, RING_COLLISION_SIZE_S2, RING_COLLISION_SIZE_S2, false,
            null, (short) 0, true, false, true, false, true, false, false, false, FAST_SCROLL_CAP_S2, false, false,
            SIDEKICK_FOLLOW_SNAP_S2, SIDEKICK_DESPAWN_X_S2, SIDEKICK_FOLLOW_LEAD_OFFSET_NONE, true, false /* useScreenYWrapValueForVisibility: S2 keeps 32-margin */,
            true /* sidekickDespawnUsesObjectIdMismatch: S2 cmp.b id(a3),d0 in TailsCPU_CheckDespawn (s2.asm:39067) */,
            SIDEKICK_FLY_LAND_BLOCKERS_S2, false /* sidekickFlyLandRequiresLeaderAlive: S2 TailsCPU_Flying_Part2 has no Sonic-routine check */, false /* solidObjectOffscreenGate: keep current S2 trace baseline */,
            false /* sidekickDespawnUsesRidingInstanceLoss: S2 8-bit-id mismatch path already covers the freed-slot case (id of a freed slot is also 0) */,
            false /* sidekickRespawnEntersCatchUpFlight: S2 TailsCPU_Spawning inlines the 64-frame trigger and warp; engine keeps SPAWNING flow */,
            false /* sidekickPushBypassUsesGraceStatus: S2 TailsCPU_Normal uses live Status_Push only (s2.asm:38943-38946) */,
            false /* sidekickClearsStalePushVelocityBeforeGroundMove: S2 TailsCPU_Normal writes Ctrl_2_Logical without clearing velocity (s2.asm:38943-39027) */,
            false /* sidekickCpuUsesLevelFrameCounter: preserve existing S2 trace cadence */,
            false /* levelBoundaryRightStrict: S2 uses bls.s (non-strict, predicted >= right) at s2.asm:36933 */);

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
            SIDEKICK_FOLLOW_SNAP_S3K, SIDEKICK_DESPAWN_X_S3K, SIDEKICK_FOLLOW_LEAD_OFFSET_S3K, false, true /* useScreenYWrapValueForVisibility: S3K Render_Sprites height_pixels=0x18 */,
            false /* sidekickDespawnUsesObjectIdMismatch: S3K cmp.w (a3),d0 in sub_13EFC (sonic3k.asm:26823) compares routine-pointer high word; all gameplay objects share the same high word so the check almost never fires */,
            SIDEKICK_FLY_LAND_BLOCKERS_S3K, true /* sidekickFlyLandRequiresLeaderAlive: sonic3k.asm:26629 cmpi.b #6,(Player_1+routine).w / bhs.s loc_13D42 */, true /* solidObjectOffscreenGate: ROM SolidObject_cont uses render_flags bit 7 to skip side-push for off-screen objects (sonic3k.asm:41390 loc_1DF88) */,
            true /* sidekickDespawnUsesRidingInstanceLoss: S3K sub_13EFC reads (a3)=0 when slot freed by Delete_Referenced_Sprite (sonic3k.asm:36116-36124); engine tracks ObjectInstance reference because latchedSolidObjectId is sticky across destruction */,
            true /* sidekickRespawnEntersCatchUpFlight: ROM sub_13ECA writes Tails_CPU_routine = 2 (sonic3k.asm:26803), which dispatches to Tails_Catch_Up_Flying (sonic3k.asm:26474) on the next frame */,
            true /* sidekickPushBypassUsesGraceStatus: preserve ROM-visible transient push continuity for S3K object ordering (sonic3k.asm:26702-26705) */,
            true /* sidekickClearsStalePushVelocityBeforeGroundMove: S3K ground projection/push path clears ground_vel while preserving collision x_vel (sonic3k.asm:27947-28017) */,
            true /* sidekickCpuUsesLevelFrameCounter: S3K Tails CPU gates read Level_frame_counter directly (sonic3k.asm:26474-26531; LevelLoop increments it before Process_Sprites at sonic3k.asm:7884-7894) */,
            true /* levelBoundaryRightStrict: S3K uses blo.s (strict, predicted > right) at sonic3k.asm:23186 — see PhysicsFeatureSet javadoc for AIZ F4768 cite */);

    /** Returns true when the game supports dual collision paths (primary/secondary). */
    public boolean hasDualCollisionPaths() {
        return collisionModel == CollisionModel.DUAL_PATH;
    }
}
