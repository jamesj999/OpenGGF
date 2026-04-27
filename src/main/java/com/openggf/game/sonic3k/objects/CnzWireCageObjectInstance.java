package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Direction;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Object 0x4E - CNZ wire cage.
 *
 * <p>Ports the S&K-side {@code Obj_CNZWireCage} player interaction routine
 * ({@code sub_338C4}). The visible cage is level art; this object latches a
 * player into object control and carries them around the cage rim using
 * {@code GetSineCosine}.
 */
public final class CnzWireCageObjectInstance extends AbstractObjectInstance {

    private static final int COOLDOWN_AFTER_RELEASE = 0x10;
    private static final int MIN_SPEED_TO_CONTINUE = 0x300;
    private static final int MIN_AIR_ATTACH_SPEED = 0x400;
    private static final int JUMP_RELEASE_X_SPEED = 0x800;
    private static final int JUMP_RELEASE_Y_SPEED = -0x200;
    private static final int EDGE_RELEASE_X_SPEED = 0x100;
    private static final int CAPTURE_ANIMATION = 0;
    private static final int RELEASE_ANIMATION = 1;
    private static final int[] CAPTURE_FRAMES = {
            0x76, 0x76, 0x77, 0x77, 0x6C, 0x6C, 0x6D, 0x6D, 0x6E, 0x6E, 0x6F, 0x6F,
            0x70, 0x70, 0x71, 0x71, 0x72, 0x72, 0x73, 0x73, 0x74, 0x74, 0x75, 0x75
    };
    private static final int[] RELEASE_FRAMES = {
            0x5E, 0x5E, 0x5E, 0x5F, 0x5F, 0x5F, 0x5F, 0x60, 0x60, 0x60, 0x60, 0x60,
            0x61, 0x61, 0x61, 0x61, 0x5C, 0x5C, 0x5C, 0x5C, 0x5D, 0x5D, 0x5D, 0x5D
    };

    private final int verticalOffset;
    private final int verticalRange;
    private final int verticalVelocity;
    private final Map<AbstractPlayableSprite, CageState> riders = new IdentityHashMap<>();

    /**
     * Tracks whether the leader (Player 1) has been the cage's primary rider
     * since the cage object instance was constructed. Once the leader has
     * been latched onto this cage and subsequently released, the cage's
     * per-frame {@code sub_338C4} call for the sidekick falls through to
     * the capture-attempt path because ROM's d6 register is no longer
     * corrupted by {@code Perform_Player_DPLC} (which only ran while the
     * leader was actively rotating in {@code loc_33A6A} or
     * {@code loc_33BAA}).
     *
     * <p>With d6 = {@code p2_standing_bit} = 4 (the correct value), the
     * cage's {@code btst d6,status(a0)} test fails (the cage's status byte
     * has the original capture bit set at position 1 due to the d6
     * corruption-by-1 from the {@code FixBugs}-disabled
     * {@code addq.b #1,d6} sequence at {@code sonic3k.asm:69843}).
     * The cage falls through to {@code loc_338D8} which immediately exits
     * at {@code tst.b object_control(a1)} because the sidekick's
     * {@code object_control} byte still carries the cage's capture marker
     * (bits 6+1, set by {@code loc_3397A}). Net effect: ROM cage does
     * nothing for the sidekick once the leader has released, leaving the
     * sidekick frozen in {@code Status_OnObj} with stale velocities.
     *
     * <p>The engine doesn't model the {@code FixBugs}-off d6 corruption
     * directly. Instead, when the leader has released this cage, we treat
     * the sidekick's residual latch as a "stuck-frozen" state matching ROM
     * and skip the cooldown-branch input check (which would otherwise fire
     * a release that ROM never produces). Confirmed at CNZ1 trace F2222
     * where ROM Tails stays at {@code y_speed=-0x02EA} from F2218 through
     * F2256 (when {@code Tails_CPU} despawn-and-respawn fires) but the
     * engine fired the cage's {@code -0x200} release at F2222.
     */
    private boolean leaderHasReleased;

    public CnzWireCageObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CNZWireCage");
        this.verticalOffset = (spawn.subtype() & 0xFF) << 3;
        this.verticalRange = verticalOffset << 1;
        this.verticalVelocity = (spawn.renderFlags() & 0x01) != 0 ? 1 : -1;
    }

    /**
     * ROM parity: the cage's per-frame routine ends with
     * {@code jmp (Delete_Sprite_If_Not_In_Range).l} (sonic3k.asm:69867 ->
     * 37301-37317), which falls through to {@code Delete_Current_Sprite}
     * (sonic3k.asm:36108-36124) once the cage has drifted $200+ pixels past
     * the camera's coarse-back chunk. {@code Delete_Current_Sprite} zeros
     * the cage's entire SST. Any sprite still latched onto this cage's slot
     * (e.g. a Tails CPU sidekick stuck in {@code object_control = 0x43} due
     * to the leader-released frozen state) reads zero from
     * {@code (a3)} on its next {@code sub_13EFC} call (sonic3k.asm:26824),
     * fails the {@code Tails_CPU_interact} compare, and warps to
     * {@code (0x7F00, 0)} via {@code sub_13ECA} (sonic3k.asm:26800).
     * <p>
     * Engine analog: {@link com.openggf.level.objects.ObjectManager}'s
     * {@code unloadCounterBasedOutOfRange} removes this instance from the
     * active object list and calls {@code onUnload()}, but the cage's
     * {@code destroyed} flag is never flipped by its own routine because
     * {@code Delete_Sprite_If_Not_In_Range} models reap-by-camera-distance,
     * not self-destruction. The sticky
     * {@link com.openggf.sprites.playable.AbstractPlayableSprite#getLatchedSolidObjectInstance()}
     * reference held by the sidekick therefore stays non-null and equal
     * to {@code lastRidingInstance} across the unload, so the
     * {@link com.openggf.sprites.playable.SidekickCpuController}'s riding-
     * instance-loss check at {@code currentRidingInstance.isDestroyed()}
     * needs the unload itself to flip {@code destroyed}. Mark this instance
     * destroyed at unload time to ROM-mirror the SST zeroing and unblock
     * the freed-slot despawn at CNZ1 trace F2262.
     */
    @Override
    public void onUnload() {
        setDestroyed(true);
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite leader = null;
        boolean leaderDplcClobberedD6 = false;
        if (playerEntity instanceof AbstractPlayableSprite player) {
            leader = player;
            CageState leaderState = riders.get(player);
            boolean wasLeaderLatched = leaderState != null && leaderState.latched;
            int leaderMappingFrameBefore = player.getMappingFrame();
            processPlayer(frameCounter, player, false, false);
            leaderDplcClobberedD6 = wasLeaderLatched
                    && player.getMappingFrame() != leaderMappingFrameBefore;
            CageState updated = riders.get(player);
            if (wasLeaderLatched && (updated == null || !updated.latched)) {
                // Leader just released this cage on this frame. ROM-side, this
                // is the moment Perform_Player_DPLC stops running for the
                // leader; from now on the d6 register stays uncorrupted at 3
                // (then +1 = 4 for the sidekick), so sub_338C4's
                // btst d6,status(a0) test fails for the sidekick because the
                // cage's status bit was originally set at position 1 (not 4).
                // Cf. sonic3k.asm:69873-69878 / 69895-69897.
                leaderHasReleased = true;
            }
        }

        ObjectServices services = tryServices();
        if (services == null) {
            return;
        }
        for (PlayableEntity sidekick : services.sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sprite && sprite != leader) {
                processPlayer(frameCounter, sprite, true, leaderDplcClobberedD6);
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // The CNZ cage is drawn as level art; this object owns player control.
    }

    private void processPlayer(int frameCounter, AbstractPlayableSprite player, boolean isSidekick,
                               boolean leaderDplcClobberedD6) {
        CageState state = riders.computeIfAbsent(player, ignored -> new CageState());
        if (state.latched) {
            if (isSidekick && leaderDplcClobberedD6) {
                /*
                 * FixBugs-off ROM leaves d6 dirty after Player 1's
                 * Perform_Player_DPLC loads a new cage mapping frame
                 * (sonic3k.asm:69842-69847, 70041, 25215-25242). The
                 * addq.b #1,d6 sequence then tests bit 1 instead of the P2
                 * standing bit, so sub_338C4 falls through to the capture
                 * path and exits at tst.b object_control(a1), leaving Tails'
                 * cage state untouched for this frame.
                 */
                restoreObjectLatchIfTerrainClearedIt(player);
                return;
            }
            continueRide(frameCounter, player, state, isSidekick);
            return;
        }

        if (state.cooldown > 0) {
            state.cooldown--;
            if (state.cooldown == 0) {
                player.setControlLocked(false);
            }
            return;
        }

        // ROM parity (sonic3k.asm:69873-69921, sub_338C4 path): once the leader
        // has released the cage, Perform_Player_DPLC stops corrupting the d6
        // register that gates the cage's btst-against-cage-status capture
        // test. d6 stays uncorrupted at 3 (then +1 = 4 for the sidekick),
        // but the cage's status bit was set at position 1 — so the test
        // fails for the sidekick and ROM never re-captures Tails after
        // Sonic released. Mirror by skipping tryLatch entirely for the
        // sidekick once leaderHasReleased is set. Without this, the engine
        // re-captures Tails when she lands inside the cage's bounding box,
        // re-arming setSuppressGroundWallCollision(true) which suppresses
        // the wall-correction loop in PlayableSpriteMovement.doGroundMove
        // and lets her clip past CNZ's flat-ground left-wall sensor at
        // x=0x1C35, y=0x09B0. CNZ1 trace F3901 reproduces the lingering
        // suppress flag: ROM has tails_x_speed=-0x00E8 (wall correction
        // applied), engine had 0x0018 (no correction).
        if (isSidekick && leaderHasReleased) {
            // Also clear any stale per-frame suppress lingering from a prior
            // capture. The flag was set by an earlier latch() before
            // leaderHasReleased flipped; release() clears it but only when
            // an actual release path runs, which the stuck-frozen branch
            // suppresses. Clear it here so the wall sensor can fire on the
            // next ground tick.
            player.setSuppressGroundWallCollision(false);
            return;
        }

        tryLatch(player, state);
    }

    private void tryLatch(AbstractPlayableSprite player, CageState state) {
        if (!canTryCapture(player)) {
            return;
        }

        int horizontalRadius = player.getYRadius() + 0x44;
        int horizontal = player.getCentreX() - spawn.x() + horizontalRadius;
        int horizontalLimit = horizontalRadius << 1;
        if (horizontal < 0 || horizontal >= horizontalLimit) {
            return;
        }

        int vertical = player.getCentreY() - spawn.y() + verticalOffset;
        if (vertical < 0 || vertical >= verticalRange) {
            return;
        }

        int adjustedVertical = vertical - 0x10;
        if (adjustedVertical < 0) {
            player.setCentreYPreserveSubpixel((short) (player.getCentreY() - adjustedVertical));
        }

        // ROM (sonic3k.asm:69905-69921) at loc_33922 branches on the
        // player's Status_InAir bit when cage runs. ROM runs player physics
        // first then objects (slot order), but ROM physics doesn't always
        // ground the player at the cage rim — sub_33C34 calls
        // Player_TouchFloor as part of cage capture (sonic3k.asm:70170).
        // The engine's terrain collision can be more aggressive about
        // grounding the player on the cage's invisible solid-controller
        // area, masking the ROM "still airborne when cage ran" signal.
        //
        // Treat the player as airborne for the capture branch if EITHER
        // the pre-physics snapshot was airborne OR the post-physics state
        // is still airborne. This keeps unit tests that bypass the
        // physics tick (and call setAir(true) directly) working, and
        // mirrors ROM at F1757 where Tails was still airborne when ROM
        // cage ran (engine's terrain pass had grounded her by then —
        // CNZ1 trace F1758 divergence).
        boolean wasAirborne = player.wasPrePhysicsAir() || player.getAir();
        int captureAngle = player.wasPrePhysicsAir()
                ? (player.getPrePhysicsAngle() & 0xFF)
                : (player.getAngle() & 0xFF);
        int captureGSpeed = player.wasPrePhysicsAir()
                ? player.getPrePhysicsGSpeed()
                : player.getGSpeed();
        boolean touchFloorDuringLatch = false;
        if (wasAirborne) {
            if (captureAngle == 0) {
                // ROM loc_3394C: bset #0, object_control(a1) - physics gated next frame.
                beginLatchedCooldown(player, state);
                touchFloorDuringLatch = true;
            } else {
                if (Math.abs(captureGSpeed) < MIN_AIR_ATTACH_SPEED) {
                    // ROM loc_33940 → bhs.s loc_33958 path - low-speed
                    // recapture re-enters loc_3394C and sets bit 0.
                    beginLatchedCooldown(player, state);
                    touchFloorDuringLatch = true;
                }
            }
        }

        latch(player, state, touchFloorDuringLatch);
    }

    private boolean canTryCapture(AbstractPlayableSprite player) {
        if (player == null || player.getDead() || player.isHurt() || player.isDebugMode()) {
            return false;
        }
        if (player.isObjectControlled() || player.isControlLocked()) {
            return false;
        }
        return !player.isOnObject()
                || player.getLatchedSolidObjectId() == 0
                || player.getLatchedSolidObjectId() == Sonic3kObjectIds.CNZ_WIRE_CAGE;
    }

    private void beginLatchedCooldown(AbstractPlayableSprite player, CageState state) {
        state.cooldown = 1;
        player.setControlLocked(true);
        player.setObjectControlled(true);
        // ROM Obj_CNZWireCage sets bits 6 and 1 of object_control (sonic3k.asm:69937-69938),
        // and bit 0 in the air-recapture branch (sonic3k.asm:69921 loc_3394C). None of
        // those is bit 7, so ROM keeps Tails_CPU_Control running each frame — that is
        // what lets the auto-jump trigger fire at the cage and feed Ctrl_2_logical=$78
        // to loc_33ADE for the cage's launch-with-A/B/C path. (CNZ1 trace F1791.)
        player.setObjectControlAllowsCpu(true);
    }

    private void latch(AbstractPlayableSprite player, CageState state, boolean touchFloorDuringLatch) {
        state.latched = true;
        if (!touchFloorDuringLatch) {
            state.cooldown = 0;
        }

        if (player.isOnObject()) {
            ObjectServices services = tryServices();
            if (services != null && services.objectManager() != null) {
                services.objectManager().clearRidingObject(player);
            }
        }
        if (touchFloorDuringLatch && player.getAir()) {
            touchFloorForAirLatch(player);
        } else if (player.getAir()) {
            player.setXSpeed((short) 0);
        }

        player.setOnObject(true);
        player.setAir(false);
        player.setLatchedSolidObject(Sonic3kObjectIds.CNZ_WIRE_CAGE, this);
        player.setObjectMappingFrameControl(true);
        player.setSuppressGroundWallCollision(true);
        player.setControlLocked(false);
        player.setAnimationId(CAPTURE_ANIMATION);
        player.setForcedAnimationId(-1);
        player.setPushing(false);
        player.setRenderFlips(false, false);

        if (player.getCentreX() >= spawn.x()) {
            state.phase = 0x00;
            state.rideAngle = 0x40;
            player.setAngle((byte) state.rideAngle);
            player.setDirection(Direction.RIGHT);
        } else {
            state.phase = 0x80;
            state.rideAngle = 0xC0;
            player.setAngle((byte) state.rideAngle);
            player.setDirection(Direction.LEFT);
        }
    }

    private void touchFloorForAirLatch(AbstractPlayableSprite player) {
        player.setXSpeed((short) 0);

        if (player.getRolling()) {
            int oldYRadius = player.getYRadius();
            int standYRadius = player.getStandYRadius();
            int centreY = player.getCentreY();
            player.setRolling(false);
            player.restoreDefaultRadii();
            int delta = oldYRadius - standYRadius;
            if ((((player.getAngle() & 0xFF) + 0x40) & 0x80) != 0) {
                delta = -delta;
            }
            player.setCentreYPreserveSubpixel((short) (centreY + delta));
        }

        player.setAir(false);
        player.setPushing(false);
        player.setJumping(false);
    }

    private void continueRide(int frameCounter, AbstractPlayableSprite player, CageState state,
                              boolean isSidekick) {
        if (player == null || player.getDead() || player.isHurt() || player.isDebugMode()
                || player.getLatchedSolidObjectId() != Sonic3kObjectIds.CNZ_WIRE_CAGE) {
            release(player, state, COOLDOWN_AFTER_RELEASE);
            return;
        }

        // ROM parity: once the leader has released this cage, sub_338C4's
        // d6-driven btst-against-cage-status test is no longer satisfied for
        // the sidekick (Perform_Player_DPLC stops corrupting d6 to 1). The
        // ROM falls through to the capture-attempt path which exits at
        // tst.b object_control(a1). Net effect: ROM cage doesn't release
        // the sidekick on jump press, doesn't continue the ride, and just
        // leaves the sidekick frozen with stale velocities. CNZ1 trace
        // F2200-F2256 confirms this. Mirror by skipping the
        // cooldown-branch input check and the release-ride machinery for
        // the sidekick once the leader is gone.
        if (isSidekick && leaderHasReleased) {
            // Keep object_control* flags true so the sidekick CPU controller
            // and physics continue to treat the player as cage-locked
            // (matching ROM's persistent obj_ctrl=$43 marker on Tails). The
            // sidekick stays in stuck-frozen state, awaiting the
            // Tails_CPU_flight_timer despawn-and-respawn that frees her.
            player.setObjectControlled(true);
            player.setObjectControlAllowsCpu(true);
            // Clear the latched solid object id so wall-collision suppression
            // doesn't carry over from the cage capture state. This branch
            // mirrors ROM's "frozen sidekick" behaviour where the cage no
            // longer governs Tails' physics, so engine wall-collision must
            // not be suppressed by the stale cage latch. CNZ1 trace F3901
            // reproduces: Tails landed on flat terrain inside the cage's
            // bounding box; ROM detects a left-wall sensor hit (Status_Push
            // set, x_vel=-0x00E8), engine had wall-collision suppress
            // lingering from the prior latch and clipped past the wall.
            player.setSuppressGroundWallCollision(false);
            return;
        }

        if (state.cooldown != 0) {
            if (tryJumpRelease(frameCounter, player, state)) {
                return;
            }
            player.setObjectControlled(true);
            // See beginLatchedCooldown: ROM cage uses bits 1+6 (and 0 on
            // air-recapture), never bit 7, so the sidekick CPU keeps running.
            player.setObjectControlAllowsCpu(true);
            restoreObjectLatchIfTerrainClearedIt(player);
            updateReleaseRide(player, state);
            return;
        }

        if (player.getAir()) {
            if (player.isJumping()) {
                releaseWithJumpImpulse(frameCounter, player, state);
                return;
            }
            restoreObjectLatchIfTerrainClearedIt(player);
        }

        if (Math.abs(player.getGSpeed()) < MIN_SPEED_TO_CONTINUE) {
            state.cooldown = 1;
            player.setControlLocked(true);
            player.setObjectControlled(true);
            // See beginLatchedCooldown: ROM cage uses bits 1+6 (and 0 on
            // air-recapture), never bit 7, so the sidekick CPU keeps running.
            player.setObjectControlAllowsCpu(true);
            updateReleaseRide(player, state);
            return;
        }

        if (player.getAir()) {
            releaseWithJumpImpulse(frameCounter, player, state);
            return;
        }

        int vertical = player.getCentreY() - spawn.y() + verticalOffset;
        if (vertical < 0 || vertical >= verticalRange) {
            release(player, state, COOLDOWN_AFTER_RELEASE);
            return;
        }

        updateCaptureRide(player, state, vertical);
    }

    private void restoreObjectLatchIfTerrainClearedIt(AbstractPlayableSprite player) {
        /*
         * ROM sub_33C34 stores this object in interact(a1), sets Status_OnObj,
         * and keeps Status_InAir clear. The engine terrain pass can briefly
         * mark the player airborne because this invisible controller is not a
         * generic SolidObject. If the latch still belongs to this cage and no
         * jump is active, restore the object-owned status before loc_339A0.
         *
         * Exception: when {@code Player_SlopeRepel} (sonic3k.asm:23907) has
         * just slipped the player on the CURRENT physics tick (set {@code
         * Status_InAir = 1} and {@code move_lock = 30} because |gSpeed|
         * dropped below $280 at a steep angle), the air state is the
         * legitimate ROM slip-detach for low-speed wall climbs. Preserve it
         * so the cage's released-path (loc_33ADE -> loc_33B1E -> bne
         * loc_33B62) can honour the in_air flag and run a proper release.
         * Use the {@code slopeRepelJustSlipped} per-tick flag rather than
         * {@code move_lock > 0} -- {@code move_lock} can be non-zero from a
         * slip many frames in the past (still counting down) when the cage
         * recaptures the player, in which case the air bit is from the
         * engine's terrain probe (stale) rather than a fresh slip and SHOULD
         * be restored. The CNZ1 trace F1740 fix originally used move_lock,
         * but F1758 (cage recapture 18 frames after the F1740 slip while
         * move_lock=12 was still counting down) showed that condition was
         * too coarse.
         */
        if (player.isSlopeRepelJustSlipped()) {
            return;
        }
        if (player.getLatchedSolidObjectId() == Sonic3kObjectIds.CNZ_WIRE_CAGE && !player.isJumping()) {
            player.setAir(false);
            player.setOnObject(true);
        }
    }

    private boolean tryJumpRelease(int frameCounter, AbstractPlayableSprite player, CageState state) {
        if (!player.isJumpJustPressed()) {
            return false;
        }
        releaseWithJumpImpulse(frameCounter, player, state);
        return true;
    }

    private void releaseWithJumpImpulse(int frameCounter, AbstractPlayableSprite player, CageState state) {
        int xSpeed = player.getCentreX() >= spawn.x() ? JUMP_RELEASE_X_SPEED : -JUMP_RELEASE_X_SPEED;
        player.setAir(true);
        player.setJumping(false);
        player.setXSpeed((short) xSpeed);
        player.setYSpeed((short) JUMP_RELEASE_Y_SPEED);
        player.setDirection(xSpeed < 0 ? Direction.LEFT : Direction.RIGHT);
        release(player, state, COOLDOWN_AFTER_RELEASE);
    }

    private void updateCaptureRide(AbstractPlayableSprite player, CageState state, int vertical) {
        int nextY = player.getCentreY();
        if (verticalVelocity >= 0 || vertical > 0x10) {
            nextY += verticalVelocity;
            player.setCentreYPreserveSubpixel((short) nextY);
        }

        int clampedVertical = vertical - 0x10;
        if (clampedVertical < 0) {
            player.setCentreYPreserveSubpixel((short) (player.getCentreY() - clampedVertical));
        }

        applyCageX(player, state);
        player.setOnObject(true);
        player.setAir(false);
        player.setObjectMappingFrameControl(true);
        player.setSuppressGroundWallCollision(true);
        player.setLatchedSolidObject(Sonic3kObjectIds.CNZ_WIRE_CAGE, this);
        player.setAngle((byte) rideAngle(state));
        player.setHighPriority((state.phase & 0xFF) >= 0x80);
        applyMappingFrame(player, state.phase, CAPTURE_FRAMES);
    }

    private void updateReleaseRide(AbstractPlayableSprite player, CageState state) {
        if (!player.getAir()) {
            int vertical = player.getCentreY() - spawn.y() + verticalOffset;
            if (vertical < verticalRange) {
                updateReleasePosition(player, state, vertical);
                return;
            }
            if (vertical == verticalRange && ((state.phase & 0x7F) == 0)) {
                int xSpeed = (state.phase & 0x80) != 0 ? -EDGE_RELEASE_X_SPEED : EDGE_RELEASE_X_SPEED;
                player.setXSpeed((short) xSpeed);
                player.setGSpeed((short) xSpeed);
                player.setYSpeed((short) 0);
                player.setAir(true);
            }
        }
        release(player, state, COOLDOWN_AFTER_RELEASE);
    }

    private void updateReleasePosition(AbstractPlayableSprite player, CageState state, int vertical) {
        if (verticalVelocity >= 0 || vertical > 0x10) {
            player.setCentreYPreserveSubpixel((short) (player.getCentreY() + verticalVelocity));
        }
        applyCageX(player, state);
        player.setOnObject(true);
        player.setAir(false);
        player.setSuppressGroundWallCollision(true);
        player.setAngle((byte) rideAngle(state));
        player.setHighPriority((state.phase & 0xFF) >= 0x80);
        applyMappingFrame(player, state.phase, RELEASE_FRAMES);
    }

    private void applyCageX(AbstractPlayableSprite player, CageState state) {
        int phaseBefore = state.phase & 0xFF;
        state.phase = (state.phase + 4) & 0xFF;
        int cosine = TrigLookupTable.cosHex(phaseBefore);
        int x = spawn.x() + (cosine >> 2) + ((player.getYRadius() * cosine) >> 8);
        player.setCentreXPreserveSubpixel((short) x);
    }

    private int rideAngle(CageState state) {
        return state.rideAngle;
    }

    private void applyMappingFrame(AbstractPlayableSprite player, int phase, int[] frames) {
        int index = (((-(phase + 0x40)) & 0xFF) / 0x0B);
        if (index < 0) {
            index = 0;
        } else if (index >= frames.length) {
            index = frames.length - 1;
        }
        player.setMappingFrame(frames[index]);
    }

    private void release(AbstractPlayableSprite player, CageState state, int cooldown) {
        state.latched = false;
        state.cooldown = cooldown;

        if (player == null) {
            return;
        }

        short centreX = player.getCentreX();
        short centreY = player.getCentreY();
        player.setAngle((byte) 0);
        player.setRolling(false);
        // ROM (sonic3k.asm:69986-69987 loc_33A0E and sonic3k.asm:70095-70096
        // loc_33B62) hardcodes y_radius=$13 (19) and x_radius=9 on cage release,
        // regardless of character. Tails's default standing y_radius is $F (15)
        // (sonic3k.asm:26103 Tails_Init), but ROM cage's release does NOT
        // restore Tails-specific defaults — it writes Sonic-style 19/9 to the
        // player's radii. The taller hitbox lets Tails detect terrain landing
        // sooner after the cage's A/B/C launch, matching ROM at CNZ1 trace
        // F1815 (foot at y=0x0742 with y_radius=19 hits floor; foot at y=0x073E
        // with y_radius=15 misses the same floor by 1 pixel).
        player.applyCustomRadii(9, 19);
        player.setCentreXPreserveSubpixel(centreX);
        player.setCentreYPreserveSubpixel(centreY);
        player.setAnimationId(RELEASE_ANIMATION);
        player.setObjectMappingFrameControl(false);
        player.setSuppressGroundWallCollision(false);
        player.setOnObject(false);
        player.setControlLocked(false);
        player.setHighPriority(false);

        ObjectServices services = tryServices();
        if (services != null && services.objectManager() != null) {
            services.objectManager().clearRidingObject(player);
        }
        player.setObjectControlled(false);
    }

    private static final class CageState {
        private boolean latched;
        private int phase;
        private int rideAngle;
        private int cooldown;
    }
}
