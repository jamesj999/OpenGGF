package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * S3K Obj $97 - MegaChopper (HCZ).
 *
 * <p>ROM reference: {@code Obj_MegaChopper} in {@code sonic3k.asm}
 * (loc_87F88..sub_881FE).
 */
public final class MegaChopperBadnikInstance extends AbstractS3kBadnikInstance
        implements TouchResponseListener {

    private static final int COLLISION_SIZE_INDEX = 0x17;   // ObjDat_MegaChopper flags $D7
    // Engine-special category equivalent of the ROM's $D7 Touch_Special route.
    private static final int SPECIAL_COLLISION_FLAGS = 0x40 | COLLISION_SIZE_INDEX;
    private static final int PRIORITY_BUCKET = 5;           // ObjDat_MegaChopper priority $280

    private static final int CHASE_SPEED = 0x200;
    private static final int CHASE_ACCEL = 0x08;
    private static final int LEAP_SPEED_X = 0x200;
    private static final int LEAP_SPEED_Y = -0x400;
    private static final int RELEASE_SPEED_Y = -0x200;
    private static final int LIGHT_GRAVITY = 0x20;          // MoveSprite_LightGravity
    private static final int NORMAL_GRAVITY = 0x38;         // MoveSprite / MoveChkDel

    private static final int TOUCH_Y_RANGE = 0x10;
    private static final int WATER_SURFACE_MARGIN = 8;
    private static final int BOB_INTERVAL_MASK = 0x07;
    private static final int PLAYER_MAIN = 1;
    private static final int PLAYER_SIDEKICK = 2;

    private static final int ANIM_DELAY = 2;
    private static final int FRAME_SWIM_A = 0;
    private static final int FRAME_SWIM_B = 1;
    private static final int FRAME_CARRY_ALT = 2;

    private static final int DRAIN_TIMER_START = 60;
    private static final int DRAIN_TIMER_RESET = 59;
    private static final int SHAKE_WINDOW_START = 60;
    private static final int SHAKE_CHANGES_START = 5;

    private enum State {
        SWIM,
        LEAP,
        CARRY,
        RELEASED
    }

    private State state = State.SWIM;
    private int animationTimer;

    private int pendingCollisionProperty;
    private AbstractPlayableSprite pendingMainPlayer;
    private AbstractPlayableSprite pendingSidekickPlayer;

    private AbstractPlayableSprite capturedPlayer;
    private int childDx;
    private int childDy;
    private int carryFacingBit;
    private int ringDrainTimer;
    private int shakeWindowTimer;
    private int remainingShakeChanges;
    private int lastDirectionBits;

    public MegaChopperBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "MegaChopper",
                Sonic3kObjectArtKeys.HCZ_MEGA_CHOPPER, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        this.mappingFrame = FRAME_SWIM_A;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        if (destroyed) {
            return;
        }

        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;

        switch (state) {
            case SWIM -> updateSwim(frameCounter, player);
            case LEAP -> updateLeap(player);
            case CARRY -> updateCarry(frameCounter);
            case RELEASED -> updateReleased();
        }
    }

    @Override
    public int getCollisionFlags() {
        if (destroyed) {
            return 0;
        }
        return SPECIAL_COLLISION_FLAGS;
    }

    @Override
    public boolean requiresContinuousTouchCallbacks() {
        return true;
    }

    @Override
    public void onTouchResponse(PlayableEntity playerEntity, TouchResponseResult result, int frameCounter) {
        if (!(playerEntity instanceof AbstractPlayableSprite player) || destroyed) {
            return;
        }
        if (isMainPlayer(player)) {
            pendingMainPlayer = player;
            pendingCollisionProperty = (pendingCollisionProperty + PLAYER_MAIN) & 0xFF;
        } else {
            pendingSidekickPlayer = player;
            pendingCollisionProperty = (pendingCollisionProperty + PLAYER_SIDEKICK) & 0xFF;
        }
    }

    @Override
    public void onUnload() {
        capturedPlayer = null;
    }

    private void updateSwim(int frameCounter, AbstractPlayableSprite player) {
        processPendingCollisionProperty();
        if (destroyed || state != State.SWIM) {
            return;
        }

        AbstractPlayableSprite target = findNearestTarget(player);
        tickSwimAnimation();
        applyVerticalBob(frameCounter, target);

        if (shouldLeapAtPlayer(target)) {
            enterLeap(target);
            return;
        }

        chaseHorizontally(target);
        moveWithVelocity();
    }

    private void updateLeap(AbstractPlayableSprite player) {
        processPendingCollisionProperty();
        if (destroyed || state != State.LEAP) {
            return;
        }

        tickSwimAnimation();

        int waterLevel = resolveWaterLevel();
        if (yVelocity < 0 || currentY < waterLevel) {
            moveWithGravity(LIGHT_GRAVITY);
            return;
        }

        yVelocity -= LIGHT_GRAVITY;
        if (yVelocity <= 0) {
            // ROM loc_88056: only clears y_vel, x_vel is preserved so the
            // MegaChopper continues chasing at speed after re-entering the water.
            state = State.SWIM;
            yVelocity = 0;
            return;
        }

        moveWithVelocity();
    }

    private void updateCarry(int frameCounter) {
        mappingFrame = ((frameCounter >> 2) & 1) == 0 ? FRAME_SWIM_A : FRAME_CARRY_ALT;
        if (capturedPlayer == null) {
            startReleasedFlight();
            return;
        }

        if (capturedPlayer.isDebugMode()
                || capturedPlayer.getDead()
                || capturedPlayer.isHurt()
                || capturedPlayer.getAnimationId() == Sonic3kAnimationIds.ROLL.id()
                || capturedPlayer.getAnimationId() == Sonic3kAnimationIds.SPINDASH.id()
                || processShakeEscape(capturedPlayer)) {
            startReleasedFlight();
            return;
        }

        syncCarryFacing(capturedPlayer);
        currentX = capturedPlayer.getCentreX() + childDx;

        int followDy = childDy;
        if (childDy < 0 && capturedPlayer.getAnimationId() == Sonic3kAnimationIds.DUCK.id()) {
            followDy += 0x10;
        }
        currentY = capturedPlayer.getCentreY() + followDy;

        ringDrainTimer--;
        if (ringDrainTimer >= 0) {
            return;
        }

        ringDrainTimer = DRAIN_TIMER_RESET;
        int ringCount = capturedPlayer.getRingCount();
        if (ringCount <= 0) {
            capturedPlayer.applyCrushDeath();
            startReleasedFlight();
            return;
        }

        capturedPlayer.addRings(-1);
        ObjectServices svc = tryServices();
        if (svc != null) {
            svc.playSfx(Sonic3kSfx.RING_RIGHT.id);
        }
    }

    private void updateReleased() {
        moveWithGravity(NORMAL_GRAVITY);
    }

    private void processPendingCollisionProperty() {
        if (pendingCollisionProperty == 0) {
            return;
        }

        int selector = pendingCollisionProperty & 0x03;
        pendingCollisionProperty = 0;

        AbstractPlayableSprite player = resolveCollisionPlayer(selector);
        if (player == null || player.getDead()) {
            return;
        }

        int deltaY = currentY - player.getCentreY();
        if (deltaY < -TOUCH_Y_RANGE || deltaY >= TOUCH_Y_RANGE) {
            return;
        }

        if (isPlayerAttackingRom(player)) {
            defeat(player);
            return;
        }

        enterCarry(player);
    }

    private void enterLeap(AbstractPlayableSprite player) {
        state = State.LEAP;
        xVelocity = LEAP_SPEED_X;
        if (player != null && player.getCentreX() < currentX) {
            xVelocity = -xVelocity;
        }
        facingLeft = xVelocity < 0;
        yVelocity = LEAP_SPEED_Y;
    }

    private void enterCarry(AbstractPlayableSprite player) {
        state = State.CARRY;
        capturedPlayer = player;
        childDx = clampSignedByte(currentX - player.getCentreX());
        childDy = clampSignedByte(currentY - player.getCentreY());
        carryFacingBit = isPlayerFacingRight(player) ? 1 : 0;
        xVelocity = 0;
        yVelocity = 0;
        ringDrainTimer = DRAIN_TIMER_START;
        shakeWindowTimer = 0;
        remainingShakeChanges = 0;
        lastDirectionBits = 0;
        // ROM: bclr #0 then bset if child_dx < 0 — face TOWARD the player.
        facingLeft = childDx >= 0;
    }

    private void startReleasedFlight() {
        state = State.RELEASED;
        mappingFrame = FRAME_CARRY_ALT;
        xVelocity = facingLeft ? -LEAP_SPEED_X : LEAP_SPEED_X;
        yVelocity = RELEASE_SPEED_Y;
        capturedPlayer = null;
    }

    private boolean processShakeEscape(AbstractPlayableSprite player) {
        shakeWindowTimer--;
        if (shakeWindowTimer < 0) {
            remainingShakeChanges = SHAKE_CHANGES_START;
            shakeWindowTimer = SHAKE_WINDOW_START;
        }

        int directionBits = 0;
        if (player.isLeftPressed()) {
            directionBits |= 0x04;
        }
        if (player.isRightPressed()) {
            directionBits |= 0x08;
        }
        if (directionBits == 0) {
            return false;
        }

        int previousBits = lastDirectionBits;
        lastDirectionBits = directionBits;
        if (((previousBits & 0x0C) ^ directionBits) == 0) {
            return false;
        }

        remainingShakeChanges--;
        return remainingShakeChanges < 0;
    }

    private void tickSwimAnimation() {
        animationTimer++;
        if (animationTimer <= ANIM_DELAY) {
            return;
        }
        animationTimer = 0;
        mappingFrame = (mappingFrame == FRAME_SWIM_A) ? FRAME_SWIM_B : FRAME_SWIM_A;
    }

    private void applyVerticalBob(int frameCounter, AbstractPlayableSprite player) {
        if ((frameCounter & BOB_INTERVAL_MASK) != 0 || player == null) {
            return;
        }
        // ROM Find_SonicTails: d1=0 when object at or below player → bob UP (-1),
        // d1=2 when object above player → bob DOWN (+1).  Never returns 0.
        currentY += player.getCentreY() > currentY ? 1 : -1;
    }

    private void chaseHorizontally(AbstractPlayableSprite player) {
        if (player == null) {
            xVelocity = 0;
            return;
        }
        int accel = player.getCentreX() < currentX ? -CHASE_ACCEL : CHASE_ACCEL;
        int nextVelocity = xVelocity + accel;
        if (nextVelocity >= -CHASE_SPEED && nextVelocity <= CHASE_SPEED) {
            xVelocity = nextVelocity;
        }
        facingLeft = xVelocity < 0;
    }

    private boolean shouldLeapAtPlayer(AbstractPlayableSprite player) {
        if (player == null) {
            return false;
        }
        int waterLevel = resolveWaterLevel();
        if (waterLevel == Integer.MAX_VALUE) {
            return false;
        }
        return currentY <= waterLevel + WATER_SURFACE_MARGIN && !player.isInWater();
    }

    private int resolveWaterLevel() {
        ObjectServices svc = tryServices();
        if (svc == null) {
            return Integer.MAX_VALUE;
        }
        WaterSystem waterSystem = svc.waterSystem();
        if (waterSystem == null) {
            return Integer.MAX_VALUE;
        }
        return waterSystem.getWaterLevelY(svc.featureZoneId(), svc.featureActId());
    }

    private AbstractPlayableSprite findNearestTarget(AbstractPlayableSprite mainPlayer) {
        AbstractPlayableSprite nearest = mainPlayer;
        int nearestDistance = mainPlayer != null ? Math.abs(currentX - mainPlayer.getCentreX()) : Integer.MAX_VALUE;

        ObjectServices svc = tryServices();
        if (svc == null) {
            return nearest;
        }

        for (PlayableEntity sidekickEntity : svc.sidekicks()) {
            if (!(sidekickEntity instanceof AbstractPlayableSprite sidekick) || sidekick.getDead()) {
                continue;
            }
            int distance = Math.abs(currentX - sidekick.getCentreX());
            if (distance < nearestDistance) {
                nearest = sidekick;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private AbstractPlayableSprite resolveCollisionPlayer(int selector) {
        AbstractPlayableSprite main = pendingMainPlayer;
        ObjectServices svc = tryServices();
        if (main == null && svc != null && svc.camera() != null) {
            main = svc.camera().getFocusedSprite();
        }

        AbstractPlayableSprite sidekick = pendingSidekickPlayer;
        if (sidekick == null && svc != null) {
            for (PlayableEntity entity : svc.sidekicks()) {
                if (entity instanceof AbstractPlayableSprite candidate) {
                    sidekick = candidate;
                    break;
                }
            }
        }

        pendingMainPlayer = null;
        pendingSidekickPlayer = null;

        return switch (selector) {
            case PLAYER_MAIN, 3 -> main;
            case PLAYER_SIDEKICK -> sidekick != null ? sidekick : main;
            default -> null;
        };
    }

    private boolean isMainPlayer(AbstractPlayableSprite player) {
        ObjectServices svc = tryServices();
        return svc == null
                || svc.camera() == null
                || svc.camera().getFocusedSprite() == player;
    }

    private boolean isPlayerFacingRight(AbstractPlayableSprite player) {
        return (player.getStatusHistory(0) & 0x01) != 0;
    }

    private void syncCarryFacing(AbstractPlayableSprite player) {
        int playerFacingBit = isPlayerFacingRight(player) ? 1 : 0;
        if ((playerFacingBit ^ carryFacingBit) == 0) {
            return;
        }
        facingLeft = !facingLeft;
        carryFacingBit ^= 0x01;
        childDx = -childDx;
    }

    private boolean isPlayerAttackingRom(AbstractPlayableSprite player) {
        if (player.getInvincibleFrames() > 0
                || player.getAnimationId() == Sonic3kAnimationIds.SPINDASH.id()
                || player.getAnimationId() == Sonic3kAnimationIds.ROLL.id()) {
            return true;
        }

        String code = player.getCode();
        if ("tails".equals(code)) {
            return player.getDoubleJumpFlag() != 0 && !player.isInWater();
        }
        if ("knuckles".equals(code)) {
            int flag = player.getDoubleJumpFlag();
            return flag == 1 || flag == 3;
        }
        return false;
    }

    private int clampSignedByte(int value) {
        return Math.max(-128, Math.min(127, value));
    }

    private void moveWithGravity(int gravity) {
        int oldYVelocity = yVelocity;
        yVelocity += gravity;

        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos24 += xVelocity;
        yPos24 += oldYVelocity;
        currentX = xPos24 >> 8;
        currentY = yPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
        ySubpixel = yPos24 & 0xFF;
    }
}
