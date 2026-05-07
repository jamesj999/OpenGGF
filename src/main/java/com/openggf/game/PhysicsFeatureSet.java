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
        /** Whether Sonic's S3K shield-move entry clears Status_RollJump before ability dispatch.
         *  S3K: true (sonic3k.asm:23401-23403). S1/S2: false; their
         *  Sonic_JumpHeight routines have no shield-move roll-jump clear
         *  (s1disasm/_incObj/01 Sonic.asm:999-1025, s2.asm:37067-37097). */
        boolean jumpRepressClearsRollJumpBeforeAbility,
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
        /** Whether ground-wall collision only sets Status_Push when the player is
         *  facing into the contacted wall.
         *  S3K: true for Sonic/Tails (sonic3k.asm:22752-22756,22768-22772,
         *  27997-28001,28013-28017). S1/S2: false; their corresponding wall
         *  response paths set Status_Push unconditionally. */
        boolean groundWallPushRequiresFacingIntoWall,
        /** Whether the character animation routine clears push status when anim
         *  differs from prev_anim.
         *  S2/S3K: true (s2.asm:38033-38038,40879-40884;
         *  sonic3k.asm:29359-29364,29681-29686).
         *  S1: false in the original build; the clear exists only under FixBugs
         *  (s1disasm/_incObj/01 Sonic.asm:2055-2065). */
        boolean animationChangeClearsPush,
        /** Whether air control preserves speeds above max (super speed from springs/ramps).
         *  S3K: true (sonic3k.asm:23110-23120 — undo acceleration, keep original if already past max).
         *  S1/S2: false (s1:01 Sonic.asm:740-750, s2.asm:36837-36840 — unconditional cap). */
        boolean airSuperspeedPreserved,
        /** Whether Player_SlopeResist can create ground velocity from rest.
         *  S3K: true when abs(slope effect) >= $0D (sonic3k.asm:23848-23856).
         *  S1/S2: false; their Sonic/Tails slope-resist routines return immediately
         *  when inertia/ground_vel is zero (s1disasm/_incObj/01 Sonic.asm:1043-1044,
         *  s2.asm:37369-37370,40224-40225). */
        boolean slopeResistStartsFromRest,
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
         *  S2: true (s2.asm:37770-37771 skips the roll-clear block when pinball_mode is set).
         *  S3K: true (sonic3k.asm:24325-24327 Player_TouchFloor_Check_Spindash branches
         *  directly to loc_121D8 when spin_dash_flag is set, skipping Status_Roll clear). */
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
        /** Whether the airborne right-wall path continues into the overhead terrain
         *  separation check instead of returning immediately.
         *  S3K: true -- {@code Tails_DoLevelCollision} right-wall path clears
         *  {@code x_vel}, copies {@code y_vel} to {@code ground_vel}, then falls
         *  through to {@code sub_11FEE} (docs/skdisasm/sonic3k.asm:29068-29085).
         *  S1/S2: false -- the equivalent right-wall paths return immediately
         *  after the wall hit (docs/s1disasm/_incObj/01 Sonic.asm:1674-1684;
         *  docs/s2disasm/s2.asm:40574-40581). */
        boolean airRightWallHitContinuesIntoCeilingSeparation,
        /** Whether the airborne left-wall path continues into ceiling/floor
         *  collision after separating from the wall.
         *  S3K: true -- {@code Tails_DoLevelCollision} and
         *  {@code SonicKnux_DoLevelCollision} clear {@code x_vel}, copy
         *  {@code y_vel} to {@code ground_vel}, then continue to
         *  {@code sub_11FEE/sub_11FD6}
         *  (docs/skdisasm/sonic3k.asm:28959-29019, 24163-24223).
         *  S2: false -- Sonic/Tails return immediately after the left-wall hit
         *  (docs/s2disasm/s2.asm:37618-37625, 40473-40480). */
        boolean airLeftWallHitContinuesIntoCeilingSeparation,
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
         * Whether the regular full-solid helper skips Player 2 collision when
         * the sidekick's own {@code render_flags.on_screen} bit is clear.
         * <p>S2: {@code SolidObject} tests Sidekick render_flags and returns
         * before adding the P2 standing bit when off-screen (docs/s2disasm/s2.asm:34800-34804).
         * <p>S3K: {@code SolidObjectFull} performs the same Player_2 gate
         * (docs/skdisasm/sonic3k.asm:41006-41010). This is separate from the
         * object on-screen gate: in MGZ1 F1449 Tails is left of camera, so ROM
         * skips the spike's P2 solid pass and preserves the velocity produced
         * by {@code Tails_Control}.
         * <p>S1: {@code false}; no CPU sidekick uses this path.
         */
        boolean solidObjectRequiresSidekickOnScreen,
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
         * Whether the sidekick CPU may bridge a transient engine-side push clear
         * with a short grace window while evaluating the ROM loc_13DD0 push
         * bypass. S3K AIZ object ordering needs this to preserve the visible
         * Status_Push continuity through the delayed Stat_table gate
         * (sonic3k.asm:26702-26705). S1/S2 keep the direct live-push path.
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
         * Whether clearing the roll flag on floor contact adjusts centre Y by
         * the player's current {@code y_radius - default_y_radius} instead of
         * the fixed classic Sonic five-pixel lift.
         *
         * <p>S3K: {@code true}. {@code Player_TouchFloor} saves
         * {@code y_radius(a0)}, restores {@code default_y_radius(a0)}, then
         * applies {@code old_y_radius - default_y_radius} to {@code y_pos(a0)}
         * when {@code Status_Roll} was set (sonic3k.asm:24341-24363). This is
         * visible after already-rolling jumps because {@code Sonic_Jump}
         * restores default radii before branching to {@code Sonic_RollJump}
         * (sonic3k.asm:23335-23358), so landing clears roll without a 5 px
         * centre-Y lift.
         *
         * <p>S1/S2: {@code false}. Their reset-on-floor paths clear the ball
         * state and apply a fixed upward 5 px {@code y_pos} adjustment
         * (s1disasm/_incObj/01 Sonic.asm:1391-1398,
         * s2.asm:37755-37761).
         */
        boolean landingRollClearUsesCurrentYRadiusDelta,
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
        boolean levelBoundaryRightStrict,
        /**
         * Whether the bottom level-boundary kill plane test compares the
         * sprite's ROM-centre Y ({@code getCentreY()}) instead of the
         * engine top-left Y ({@code getY()}).
         *
         * <p>All three games' bottom-boundary checks read the player's
         * {@code y_pos(a0)} word, which is ROM-centre Y:
         * <ul>
         *   <li>S1 {@code Sonic_LevelBound .bottom}:
         *       {@code cmp.w obY(a0),d0 / blt.s .bottom}
         *       (s1disasm/_incObj/01 Sonic.asm:1014).</li>
         *   <li>S2 {@code Sonic_LevelBound Sonic_Boundary_CheckBottom}:
         *       {@code cmp.w y_pos(a0),d0 / blt.s Sonic_Boundary_Bottom}
         *       (s2.asm:36950).</li>
         *   <li>S3K {@code Player_LevelBound Player_Boundary_CheckBottom}:
         *       {@code cmp.w y_pos(a0),d0 / blt.s Player_Boundary_Bottom}
         *       (sonic3k.asm:23195).</li>
         *   <li>S3K {@code Tails_Check_Screen_Boundaries loc_14F30}:
         *       {@code cmp.w y_pos(a0),d0 / blt.s loc_14F56}
         *       (sonic3k.asm:28430-28431).</li>
         * </ul>
         *
         * <p>The engine has historically compared {@code getY()} (top-left)
         * here. For Sonic ({@code height_pixels = 0x28 = 40}) that is a
         * 20-px gap; for Tails ({@code height_pixels = 0x18 = 24}) it is
         * 12-px. Switching to {@code getCentreY()} matches ROM exactly
         * (the ensuing {@code Kill_Character} writes y_vel = -$700, x_vel
         * = 0, ground_vel = 0 at sonic3k.asm:21141-21151 are already
         * replicated by {@code AbstractPlayableSprite.applyDeath} and
         * {@code SidekickCpuController.beginLevelBoundaryKill}).
         *
         * <p>S3K: {@code true}. Required to fix AIZ trace replay F7171
         * sidekick boundary kill (Tails {@code y_pos = 0x047E} versus the
         * ROM kill-plane the engine missed by 12 px).
         *
         * <p>S1/S2: {@code false} for now. The S1 GHZ/MZ1 and S2 EHZ trace
         * baselines were recorded against the engine's existing top-left
         * compare; flipping the global default in the same change risks
         * unrelated regressions. Once S3K AIZ/CNZ progress past their
         * current blockers, S1/S2 should be re-recorded or re-validated
         * and the flag flipped to {@code true} for all three games (ROM
         * parity for S1 and S2 is already established by the cites above).
         */
        boolean levelBoundaryUsesCentreY,
        /**
         * Whether the {@code SolidObject_cont} top branch (engine
         * {@code resolveContactInternal} top path, distY in [0, $F]) applies
         * its position lift even when {@code y_vel < 0} (player moving
         * upward), and only suppresses the standing/landing state effects.
         *
         * <p>S3K: {@code true}. ROM {@code loc_1E154}
         * (sonic3k.asm:41606-41632) writes {@code subq.w #1, y_pos(a1)} and
         * {@code sub.w d3, y_pos(a1)} unconditionally before testing
         * {@code tst.w y_vel(a1) / bmi.s loc_1E198} (line 41625-41626).
         * When {@code y_vel < 0} ROM skips {@code RideObject_SetRide} and
         * returns the "no contact" code (d4=0), but the position lift has
         * already been applied. CNZ trace F7614 exercises this exact case:
         * Tails has just executed {@code Tails_Jump} (sonic3k.asm:28519+)
         * which sets {@code y_vel = -$680} on the same frame
         * {@code Obj_Spring_Horizontal}'s {@code SolidObjectFull2_1P} call
         * reaches {@code loc_1E154} for the spring at @0x0E38,0x04D0 with
         * {@code d1=$13, d2=$E, d3=1}; the +2 px lift the ROM produces
         * (combined with {@code Tails_Jump}'s rolling-radius +1) is the
         * difference between ROM y=0x04B3 and engine y=0x04B1.
         *
         * <p>S1/S2: {@code false}. ROM {@code Solid_Landed}
         * (s1disasm/_incObj/sub SolidObject.asm:267-286) and
         * {@code SolidObject_Landed} (s2.asm:35368-35388) both test
         * {@code y_vel} BEFORE writing the lift: {@code tst.w y_vel(a1) /
         * bmi.s SolidObject_Miss} (s2.asm:35379-35380). When upward, they
         * return "no contact" without applying any position change. The
         * engine's existing {@code y_speed < 0 -> return null} matches S1/S2
         * exactly.
         */
        boolean solidObjectTopBranchAlwaysLiftsOnUpwardVelocity,
        /**
         * Whether CPU sidekick NORMAL skips the follow/despawn subroutine while
         * the sidekick is in the hurt/object routine.
         *
         * <p>S3K: {@code true}. The Tails object dispatcher sends routine 4 to
         * the hurt/object path, not {@code Tails_Control}
         * (docs/skdisasm/sonic3k.asm:26091-26096,26159-26190). The off-screen
         * timeout increment is inside {@code sub_13EFC}, reached from
         * {@code Tails_Control} (docs/skdisasm/sonic3k.asm:26816-26833), so
         * hurt-routine frames must not advance the normal CPU despawn timer.
         * MGZ trace F1910 exercises this: ROM Tails is still in the local
         * terrain context while the engine had already timed out to the
         * despawn marker.
         *
         * <p>S1/S2: {@code false}. S1 has no CPU Tails sidekick. S2 keeps its
         * current baseline until its separate {@code Tails_respawn_counter}
         * semantics are revalidated against the S2 dispatcher.
         */
        boolean sidekickNormalCpuSkipsHurtRoutine,
        /**
         * Whether {@code Ctrl_1_locked} latches the previous frame's logical
         * pad state ({@code Ctrl_1_logical}) by short-circuiting the
         * raw-pad-to-logical copy in {@code Sonic_Control}.
         *
         * <p>S3K: {@code true}. ROM {@code Sonic_Control}
         * (sonic3k.asm:21541-21545 {@code loc_10760}) does
         * <pre>
         *   tst.b   (Ctrl_1_locked).w
         *   bne.s   loc_10780              ; if locked, SKIP the copy
         *   move.w  (Ctrl_1).w,(Ctrl_1_logical).w
         * </pre>
         * The previous frame's logical pad state therefore persists while
         * controls are locked. {@code Sonic_RecordPos}
         * (sonic3k.asm:22132) writes that latched value into
         * {@code Stat_table}, which {@code Tails_CPU_Control}
         * (sonic3k.asm:26683-26689) reads with a $40-frame delay. The
         * engine's {@code SpriteManager.publishInputState} zeroes the
         * effective inputs while {@code controlLocked=true}, so without
         * the latch the engine writes the zeros into
         * {@code logicalInputState} and corrupts the sidekick CPU's
         * delayed-input read for the AIZ F7381 / CNZ F7919 windows.
         *
         * <p>S2: {@code false}. ROM {@code Obj01_Control}
         * (s2.asm:35933-35935) has the same {@code Control_Locked} short-
         * circuit, but the engine's S2 trace baselines (EHZ) and the
         * existing {@code setControlLocked(true)} call sites
         * ({@code FlipperObjectInstance}, {@code CPZSpinTubeObjectInstance},
         * {@code Sonic2DeathEggRobotInstance}, {@code SignpostObjectInstance})
         * were calibrated against the engine's "lock = zero logical" semantic
         * for animation gating. Flipping the flag universally regressed S2
         * EHZ from PASS to F5121 (commit f3347ea89, REVERTED in 9793e4617);
         * the latch is therefore S3K-only on this branch and S2 must be
         * re-validated before flipping.
         *
         * <p>S1: {@code false}. ROM uses a separate {@code Ctrl_Lock_byte}
         * variable (s1disasm/_incObj/01 Sonic.asm); preserve baseline.
         */
        boolean controlLockLatchesLogicalInput,
        /** Whether Sonic_Water skips the water-exit y_vel doubling when upward
         *  velocity is already faster than -$400.
         *
         *  <p>S2/S3K: true. {@code cmpi.w #-$400,d0 / blt.s} skips
         *  {@code asl y_vel(a0)} on fast upward exits
         *  (s2.asm:36120-36124, sonic3k.asm:22267-22270).
         *
         *  <p>S1: false. S1's {@code Sonic_Water} applies
         *  {@code asl.w obVelY(a0)} on exit without this velocity gate
         *  (s1disasm/_incObj/01 Sonic.asm:246-248). */
        boolean waterExitBoostSkipsFastUpwardVelocity,
        /** Whether {@code Sonic_SlopeResist} can apply slope force when ground
         *  velocity is zero (i.e. when the player is stationary on a slope).
         *
         *  <p>S3K: {@code true}. {@code Player_SlopeResist}
         *  (sonic3k.asm:23830-23856) branches {@code beq.s loc_11DDC} on the
         *  zero-inertia case, then applies the force only when
         *  {@code |slope_force| >= $D} ({@code cmpi.w #$D,d1 / blo.s}).
         *  This is what kicks a stationary player into motion on a steep
         *  slope.
         *
         *  <p>S1/S2: {@code false}. {@code Sonic_SlopeResist}
         *  (s1disasm/_incObj/01 Sonic.asm:1243-1244;
         *  s2.asm:37394-37395) returns unconditionally on
         *  {@code tst.w inertia(a0) / beq.s return_1ADCA}, leaving the
         *  player at rest. {@code Tails_SlopeResist} (s2.asm:40249-40250)
         *  matches Sonic. Required for S2 EHZ trace replay frame 3644
         *  where Tails decelerates to {@code g_speed = 0} at angle 0xD0
         *  and ROM keeps her stationary while the engine was sliding her
         *  back down the slope. */
        boolean slopeResistAppliesAtZeroInertia,
        /** Whether {@code Object_respawn_table} bit 7 stays permanently
         *  set after a player kill, preventing the spawn from re-triggering
         *  for the rest of the level.
         *
         *  <p>S3K: {@code true}. {@code Touch_EnemyNormal}
         *  (sonic3k.asm:20953 {@code bset #7,status(a1)}) sets the
         *  destroyed bit on kill; the badnik is then converted to
         *  {@code Obj_Explosion} which never re-enters the alive-offscreen
         *  {@code Sprite_OnScreen_Test} clear path, so the bit persists
         *  until level reset. The cursor helpers also set the bit on spawn
         *  at {@code loc_1BA40 / loc_1BA64}.
         *
         *  <p>S1/S2: {@code false}. ROM only latches respawn-tracked spawns
         *  via the {@code remember_state} status bit; non-remembered spawns
         *  re-trigger when the cursor passes them again
         *  (s2.asm:33402 {@code tst.b 2(a0); bpl.s +}). */
        boolean permanentRespawnTableLatch,
        /** Whether the frame loop schedules object execution AFTER player
         *  physics (with inline solid checkpoints visible to subsequent
         *  objects), instead of before.
         *
         *  <p>This controls {@code LevelManager.objectsExecuteAfterPlayerPhysics()},
         *  which {@code LevelFrameStep} and {@code GameLoop} read to decide
         *  whether to run {@code physics} then {@code objects} (true) or
         *  {@code objects} then {@code physics} (false). It is independent
         *  of {@code collisionModel}: S1 (UNIFIED) and S2/S3K (DUAL_PATH)
         *  all use the post-physics ordering on this branch per the
         *  2026-04-18-solid-ordering-rom-accuracy plan.
         *
         *  <p>S1/S2/S3K: {@code true}. */
        boolean objectsExecuteAfterPlayerPhysics,
        /** Fixed object slot index used for shield power-ups, or {@code -1}
         *  when shields are dynamically allocated through the normal slot pool.
         *
         *  <p>S1: 6. ROM Variables.asm hardcodes {@code v_shieldobj = v_objspace
         *  + object_size*6}, so the shield object always lives at object slot 6
         *  ({@link com.openggf.level.objects.DefaultPowerUpSpawner} routes
         *  shield objects through {@code addDynamicObjectAtSlot}).
         *
         *  <p>S2/S3K: -1. Shields share the dynamic slot pool with other
         *  spawned objects; no fixed slot reservation. */
        int shieldObjectFixedSlotIndex,
        /** Whether {@link com.openggf.level.objects.AbstractObjectInstance#isOnScreenForTouch()}
         *  must additionally gate on the BuildSprites Y-margin (the
         *  {@code .assumeHeight} 32-px band around the visible 224-line
         *  viewport).
         *
         *  <p>S1: {@code true}. ROM {@code ReactToItem}
         *  ({@code docs/s1disasm/_incObj/sub ReactToItem.asm:26-27}) reads
         *  {@code obRender(a1) / bpl.s .next} and skips objects whose bit 7
         *  has been cleared by {@code BuildSprites}
         *  ({@code docs/s1disasm/_inc/BuildSprites.asm:71-78}, the
         *  {@code .assumeHeight} branch when {@code obRender} bit 4 is clear).
         *  That bit clears for any object whose {@code obY - cameraY + 0x80}
         *  falls outside {@code [0x60, 0x180)}, equivalently
         *  {@code obY in [cameraY - 32, cameraY + 256)}. The engine mirrors
         *  this by extending the touch on-screen test to a Y check with a
         *  32-px margin. SYZ3 credits demo F253 ring s43 at (0x186E, 0x0662)
         *  with camera (0x17C2, 0x0556) is the regression case.
         *
         *  <p>S2: {@code false}. ROM S2 {@code Touch_Loop}
         *  ({@code docs/s2disasm/s2.asm} ~line 84502-84551) walks every
         *  active object directly and does NOT consult a render-flag bit
         *  before invoking the per-object touch response. The S2 EHZ trace
         *  baseline was recorded against the engine's pre-Task-3 X-only gate.
         *
         *  <p>S3K: {@code false}. ROM S3K does not iterate at all in the
         *  touch path: {@code TouchResponse} ({@code docs/skdisasm/sonic3k.asm:20655})
         *  consumes a pre-built {@code Collision_response_list} that
         *  {@code ExecuteObjects} populates upstream. The render-flag gate
         *  semantics live at list-add time, not at touch time, so an extra
         *  Y check inside the engine's touch loop wrongly filters objects
         *  that ROM already had on the response list. Restoring the
         *  pre-Task-3 X-only gate for S3K returns MGZ trace replay's
         *  first-fail to its prior baseline (frame 2395 instead of frame
         *  1659 with the universal Y check). */
        boolean touchResponseUsesRenderFlagYGate
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
            false, null, CollisionModel.UNIFIED, true, LOOK_SCROLL_DELAY_NONE, true, true, false, false, false, false, false,
            RING_FLOOR_CHECK_MASK_S1, RING_COLLISION_SIZE_S1, RING_COLLISION_SIZE_S1, false,
            null, (short) 0, true, false /* groundWallPushRequiresFacingIntoWall: S1 wall response sets push unconditionally (s1disasm/_incObj/01 Sonic.asm:551-568) */, false /* animationChangeClearsPush: S1 clear is FixBugs-only (s1disasm/_incObj/01 Sonic.asm:2055-2065) */, false,
            false /* slopeResistStartsFromRest: S1 Sonic_SlopeResist returns on zero inertia (s1disasm/_incObj/01 Sonic.asm:1043-1044) */,
            false, false, false, true, false, false, false, true, FAST_SCROLL_CAP_S2, false, true,
            SIDEKICK_FOLLOW_SNAP_S2, SIDEKICK_DESPAWN_X_S2, SIDEKICK_FOLLOW_LEAD_OFFSET_NONE, true /* sidekickSpawningRequiresGroundedLeader: S1 has no Tails CPU */, false /* useScreenYWrapValueForVisibility: S1 keeps 32-margin */,
            true /* sidekickDespawnUsesObjectIdMismatch: S1 has no Tails CPU; symmetric with S2 */,
            SIDEKICK_FLY_LAND_BLOCKERS_NONE, false /* sidekickFlyLandRequiresLeaderAlive: S1 has no CPU sidekick */, false /* solidObjectOffscreenGate: keep current S1 trace baseline */,
            false /* solidObjectRequiresSidekickOnScreen: S1 has no CPU sidekick */,
            false /* sidekickDespawnUsesRidingInstanceLoss: S1 has no Tails CPU */,
            false /* sidekickRespawnEntersCatchUpFlight: S1 has no Tails CPU */,
            false /* sidekickPushBypassUsesGraceStatus: S1 has no Tails CPU */,
            false /* sidekickClearsStalePushVelocityBeforeGroundMove: S1 has no Tails CPU */,
            false /* sidekickCpuUsesLevelFrameCounter: S1 has no Tails CPU */,
            false /* landingRollClearUsesCurrentYRadiusDelta: S1 Sonic_ResetOnFloor applies fixed subq.w #5, obY(a0) when clearing ball state */,
            false /* levelBoundaryRightStrict: S1 uses bls.s (non-strict, predicted >= right) at s1disasm/_incObj/01 Sonic.asm:998 */,
            false /* levelBoundaryUsesCentreY: S1 ROM uses centre-Y at s1disasm/_incObj/01 Sonic.asm:1014, but S1 trace baselines (GHZ/MZ1) were calibrated against engine top-left compare; defer flip until S1 traces are re-validated */,
            false /* solidObjectTopBranchAlwaysLiftsOnUpwardVelocity: S1 Solid_Landed (s1disasm/_incObj/sub SolidObject.asm:278-289) tests y_vel before any lift and returns Solid_Miss when upward */,
            false /* sidekickNormalCpuSkipsHurtRoutine: S1 has no Tails CPU */,
            false /* controlLockLatchesLogicalInput: S1 uses separate Ctrl_Lock_byte; preserve baseline */,
            false /* waterExitBoostSkipsFastUpwardVelocity: S1 exits water with unconditional asl.w obVelY(a0) */,
            false /* slopeResistAppliesAtZeroInertia: S1 Sonic_SlopeResist (s1disasm/_incObj/01 Sonic.asm:1243-1244) returns unconditionally when inertia=0 */,
            false /* permanentRespawnTableLatch: S1 ObjectsManager_Main only latches remembered spawns; non-remembered spawns re-trigger when cursor passes */,
            true /* objectsExecuteAfterPlayerPhysics: S1 uses post-physics object ordering per 2026-04-18-solid-ordering-rom-accuracy plan */,
            6 /* shieldObjectFixedSlotIndex: S1 Variables.asm v_shieldobj = v_objspace + object_size*6 */,
            true /* touchResponseUsesRenderFlagYGate: S1 ReactToItem (s1disasm/_incObj/sub ReactToItem.asm:26-27) reads obRender bit 7, cleared by BuildSprites (s1disasm/_inc/BuildSprites.asm:71-78) on Y-out-of-band */);

    /** Sonic 2: spindash with standard speed table (s2.asm:37294), dual collision paths, delayed look scroll,
     *  preserves high ground speed on input (s2.asm:36610-36616),
     *  angle diff cardinal snap (s2.asm Sonic_Angle:42658-42664),
     *  extended edge balance: 4 states with precarious/facing-away checks (s2.asm:36246-36373). */
    public static final PhysicsFeatureSet SONIC_2 = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    }, CollisionModel.DUAL_PATH, false, LOOK_SCROLL_DELAY_S2, false, false, false, false, false, true, true,
            RING_FLOOR_CHECK_MASK_S2, RING_COLLISION_SIZE_S2, RING_COLLISION_SIZE_S2, false,
            null, (short) 0, true, false /* groundWallPushRequiresFacingIntoWall: S2 Sonic/Tails set push unconditionally in wall response (s2.asm:36536-36547,39506-39519) */, true /* animationChangeClearsPush: S2 Sonic/Tails animation clears pushing on anim change (s2.asm:38033-38038,40879-40884) */, false,
            false /* slopeResistStartsFromRest: S2 Sonic/Tails_SlopeResist returns on zero inertia (s2.asm:37369-37370,40224-40225) */,
            true, false, true, false, false, false, false, false, FAST_SCROLL_CAP_S2, false, false,
            SIDEKICK_FOLLOW_SNAP_S2, SIDEKICK_DESPAWN_X_S2, SIDEKICK_FOLLOW_LEAD_OFFSET_NONE, true, false /* useScreenYWrapValueForVisibility: S2 keeps 32-margin */,
            true /* sidekickDespawnUsesObjectIdMismatch: S2 cmp.b id(a3),d0 in TailsCPU_CheckDespawn (s2.asm:39067) */,
            SIDEKICK_FLY_LAND_BLOCKERS_S2, false /* sidekickFlyLandRequiresLeaderAlive: S2 TailsCPU_Flying_Part2 has no Sonic-routine check */, false /* solidObjectOffscreenGate: keep current S2 trace baseline */,
            true /* solidObjectRequiresSidekickOnScreen: S2 SolidObject skips off-screen Sidekick (s2.asm:34800-34804) */,
            false /* sidekickDespawnUsesRidingInstanceLoss: S2 8-bit-id mismatch path already covers the freed-slot case (id of a freed slot is also 0) */,
            false /* sidekickRespawnEntersCatchUpFlight: S2 TailsCPU_Spawning inlines the 64-frame trigger and warp; engine keeps SPAWNING flow */,
            false /* sidekickPushBypassUsesGraceStatus: S2 TailsCPU_Normal uses live Status_Push only (s2.asm:38943-38946) */,
            false /* sidekickClearsStalePushVelocityBeforeGroundMove: S2 TailsCPU_Normal writes Ctrl_2_Logical without clearing velocity (s2.asm:38943-39027) */,
            false /* sidekickCpuUsesLevelFrameCounter: preserve existing S2 trace cadence */,
            false /* landingRollClearUsesCurrentYRadiusDelta: S2 Sonic_ResetOnFloor applies fixed subq.w #5, y_pos(a0) when clearing rolling */,
            false /* levelBoundaryRightStrict: S2 uses bls.s (non-strict, predicted >= right) at s2.asm:36933 */,
            false /* levelBoundaryUsesCentreY: S2 ROM uses centre-Y at s2.asm:36950, but S2 EHZ trace baseline was calibrated against engine top-left compare; defer flip until S2 traces are re-validated */,
            false /* solidObjectTopBranchAlwaysLiftsOnUpwardVelocity: S2 SolidObject_Landed (s2.asm:35379-35380) tests y_vel before lift and branches to SolidObject_Miss when upward */,
            false /* sidekickNormalCpuSkipsHurtRoutine: keep S2's separate Tails_respawn_counter baseline until dispatcher parity is revalidated */,
            false /* controlLockLatchesLogicalInput: ROM Obj01_Control (s2.asm:35933-35935) has the short-circuit, but engine S2 setControlLocked sites (FlipperObjectInstance, CPZSpinTubeObjectInstance, Sonic2DeathEggRobotInstance, SignpostObjectInstance) and EHZ trace baseline expect post-lock zero state for animation gating; universal latch regressed S2 EHZ to F5121 (commit f3347ea89, reverted in 9793e4617); flip after S2 traces are re-validated */,
            true /* waterExitBoostSkipsFastUpwardVelocity: S2 Sonic_Water skips asl y_vel when y_vel < -$400 (s2.asm:36120-36124) */,
            false /* slopeResistAppliesAtZeroInertia: S2 Sonic_SlopeResist/Tails_SlopeResist (s2.asm:37394-37395, 40249-40250) return unconditionally on tst.w inertia(a0)/beq when stationary. Required for EHZ trace F3644 Tails-on-loop divergence. */,
            false /* permanentRespawnTableLatch: S2 ObjectsManager_Main only latches remembered spawns (s2.asm:33402 tst.b 2(a0); bpl.s +); non-remembered spawns re-trigger when cursor passes */,
            true /* objectsExecuteAfterPlayerPhysics: S2 DUAL_PATH uses post-physics object ordering with inline solid checkpoints */,
            -1 /* shieldObjectFixedSlotIndex: S2 shields use the dynamic slot pool, no fixed v_shieldobj slot */,
            false /* touchResponseUsesRenderFlagYGate: S2 Touch_Loop (s2.asm ~84502-84551) walks active objects without consulting the render flag; preserve pre-Task-3 X-only baseline */);

    /** Sonic 3&K: spindash with same speed table as S2, dual collision paths, delayed look scroll,
     *  preserves high ground speed on input, elemental shields,
     *  angle diff cardinal snap (inherited from S2 Sonic_Angle),
     *  extended edge balance (inherited from S2),
     *  Super spindash table (sonic3k.asm:23743 word_11D04),
     *  duck while moving below 0x100 (sonic3k.asm:23236). */
    public static final PhysicsFeatureSet SONIC_3K = new PhysicsFeatureSet(true, new short[]{
            0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00
    }, CollisionModel.DUAL_PATH, false, LOOK_SCROLL_DELAY_S2, false, false, true, true, true, true, true,
            RING_FLOOR_CHECK_MASK_S2, RING_COLLISION_SIZE_S3K, RING_COLLISION_SIZE_S3K, true,
            new short[]{
            0x0B00, 0x0B80, 0x0C00, 0x0C80, 0x0D00, 0x0D80, 0x0E00, 0x0E80, 0x0F00
    }, (short) 0x100, true, true /* groundWallPushRequiresFacingIntoWall: S3K wall response gates Status_Push on Status_Facing (sonic3k.asm:22752-22756,22768-22772,27997-28001,28013-28017) */, true /* animationChangeClearsPush: S3K Tails/Tails2P animation clears Status_Push on anim change (sonic3k.asm:29359-29364,29681-29686) */, true,
            true /* slopeResistStartsFromRest: S3K Player_SlopeResist applies abs(slope effect) >= $0D even when ground_vel is zero (sonic3k.asm:23848-23856) */,
            false, true, false, true, true, true, true, false, FAST_SCROLL_CAP_S3K, true, false,
            SIDEKICK_FOLLOW_SNAP_S3K, SIDEKICK_DESPAWN_X_S3K, SIDEKICK_FOLLOW_LEAD_OFFSET_S3K, false, true /* useScreenYWrapValueForVisibility: S3K Render_Sprites height_pixels=0x18 */,
            false /* sidekickDespawnUsesObjectIdMismatch: S3K cmp.w (a3),d0 in sub_13EFC (sonic3k.asm:26823) compares routine-pointer high word; all gameplay objects share the same high word so the check almost never fires */,
            SIDEKICK_FLY_LAND_BLOCKERS_S3K, true /* sidekickFlyLandRequiresLeaderAlive: sonic3k.asm:26629 cmpi.b #6,(Player_1+routine).w / bhs.s loc_13D42 */, true /* solidObjectOffscreenGate: ROM SolidObject_cont uses render_flags bit 7 to skip side-push for off-screen objects (sonic3k.asm:41390 loc_1DF88) */,
            true /* solidObjectRequiresSidekickOnScreen: S3K SolidObjectFull skips off-screen Player_2 before collision (sonic3k.asm:41006-41010) */,
            true /* sidekickDespawnUsesRidingInstanceLoss: S3K sub_13EFC reads (a3)=0 when slot freed by Delete_Referenced_Sprite (sonic3k.asm:36116-36124); engine tracks ObjectInstance reference because latchedSolidObjectId is sticky across destruction */,
            true /* sidekickRespawnEntersCatchUpFlight: ROM sub_13ECA writes Tails_CPU_routine = 2 (sonic3k.asm:26803), which dispatches to Tails_Catch_Up_Flying (sonic3k.asm:26474) on the next frame */,
            true /* sidekickPushBypassUsesGraceStatus: preserve ROM-visible transient push continuity for S3K object ordering (sonic3k.asm:26702-26705) */,
            true /* sidekickClearsStalePushVelocityBeforeGroundMove: only for S3K's AIZ object-order push-grace bridge; live Status_Push and MGZ grace continuation still run Tails_InputAcceleration_Path deceleration/projection before collision clears ground_vel (sonic3k.asm:26702-26705,26775-26785,27947-28017) */,
            true /* sidekickCpuUsesLevelFrameCounter: S3K Tails CPU gates read Level_frame_counter directly (sonic3k.asm:26474-26531; LevelLoop increments it before Process_Sprites at sonic3k.asm:7884-7894) */,
            true /* landingRollClearUsesCurrentYRadiusDelta: S3K Player_TouchFloor applies saved y_radius - default_y_radius to y_pos, so already-restored roll-jump radii produce no 5 px lift (sonic3k.asm:23335-23358,24341-24363). */,
            true /* levelBoundaryRightStrict: S3K uses blo.s (strict, predicted > right) at sonic3k.asm:23186 -- see PhysicsFeatureSet javadoc for AIZ F4768 cite */,
            true /* levelBoundaryUsesCentreY: S3K Player_LevelBound (sonic3k.asm:23195) and Tails_Check_Screen_Boundaries (sonic3k.asm:28430-28431) both compare y_pos(a0) (centre-Y); engine getY() is top-left, off by 12 px for Tails / 20 px for Sonic. Required for AIZ trace F7171 sidekick boundary kill. */,
            true /* solidObjectTopBranchAlwaysLiftsOnUpwardVelocity: S3K loc_1E154 (sonic3k.asm:41606-41632) writes subq.w #1, y_pos(a1) and sub.w d3, y_pos(a1) BEFORE tst.w y_vel(a1) / bmi.s loc_1E198 — the lift is unconditional, only the standing/RideObject_SetRide is gated on y_vel >= 0. CNZ F7614 Tails_Jump (y_vel=-0x680) on Obj_Spring_Horizontal at 0x0E38,0x04D0 produces a +2 px lift the engine was missing. */,
            true /* sidekickNormalCpuSkipsHurtRoutine: S3K Tails_Index routine 4 bypasses Tails_Control, so sub_13EFC does not tick during hurt/object frames (sonic3k.asm:26091-26096,26159-26190,26816-26833). MGZ F1910 keeps Tails local instead of despawning. */,
            true /* controlLockLatchesLogicalInput: S3K Sonic_Control (sonic3k.asm:21541-21545 loc_10760) skips move.w (Ctrl_1).w,(Ctrl_1_logical).w when Ctrl_1_locked != 0, latching the previous frame's logical pad state. Required so Sonic_RecordPos (sonic3k.asm:22132) writes the latched value into Stat_table for Tails_CPU_Control's $40-frame-delayed read (sonic3k.asm:26683-26689). */,
            true /* waterExitBoostSkipsFastUpwardVelocity: S3K Sonic_Water skips asl y_vel when y_vel < -$400 (sonic3k.asm:22267-22270) */,
            true /* slopeResistAppliesAtZeroInertia: S3K Player_SlopeResist (sonic3k.asm:23830-23856) branches to loc_11DDC on inertia=0 and applies slope force when |force| >= $D, kicking stationary player into motion */,
            true /* permanentRespawnTableLatch: S3K Touch_EnemyNormal (sonic3k.asm:20953 bset #7,status(a1)) sets the destroyed bit on kill; badnik becomes Obj_Explosion which never re-enters Sprite_OnScreen_Test clear path, so bit persists until level reset */,
            true /* objectsExecuteAfterPlayerPhysics: S3K DUAL_PATH uses post-physics object ordering with inline solid checkpoints */,
            -1 /* shieldObjectFixedSlotIndex: S3K shields use the dynamic slot pool, no fixed v_shieldobj slot */,
            false /* touchResponseUsesRenderFlagYGate: S3K TouchResponse (sonic3k.asm:20655) consumes a pre-built Collision_response_list; render-flag gating happens upstream during list build, not at touch time. Adding a Y check inside the engine touch loop drops objects ROM had on the response list (MGZ trace replay first-fail moves from f2395 to f1659). */);

    /** Returns true when the game supports dual collision paths (primary/secondary). */
    public boolean hasDualCollisionPaths() {
        return collisionModel == CollisionModel.DUAL_PATH;
    }
}
