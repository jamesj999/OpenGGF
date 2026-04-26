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

    public CnzWireCageObjectInstance(ObjectSpawn spawn) {
        super(spawn, "CNZWireCage");
        this.verticalOffset = (spawn.subtype() & 0xFF) << 3;
        this.verticalRange = verticalOffset << 1;
        this.verticalVelocity = (spawn.renderFlags() & 0x01) != 0 ? 1 : -1;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (playerEntity instanceof AbstractPlayableSprite player) {
            processPlayer(frameCounter, player);
        }

        ObjectServices services = tryServices();
        if (services == null) {
            return;
        }
        for (PlayableEntity sidekick : services.sidekicks()) {
            if (sidekick instanceof AbstractPlayableSprite sprite) {
                processPlayer(frameCounter, sprite);
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // The CNZ cage is drawn as level art; this object owns player control.
    }

    private void processPlayer(int frameCounter, AbstractPlayableSprite player) {
        CageState state = riders.computeIfAbsent(player, ignored -> new CageState());
        if (state.latched) {
            continueRide(frameCounter, player, state);
            return;
        }

        if (state.cooldown > 0) {
            state.cooldown--;
            if (state.cooldown == 0) {
                player.setControlLocked(false);
            }
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

    private void continueRide(int frameCounter, AbstractPlayableSprite player, CageState state) {
        if (player == null || player.getDead() || player.isHurt() || player.isDebugMode()
                || player.getLatchedSolidObjectId() != Sonic3kObjectIds.CNZ_WIRE_CAGE) {
            release(player, state, COOLDOWN_AFTER_RELEASE);
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
