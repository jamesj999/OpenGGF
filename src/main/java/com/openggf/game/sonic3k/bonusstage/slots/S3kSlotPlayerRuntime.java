package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

public final class S3kSlotPlayerRuntime {
    private static final int DEBUG_MOVE_SPEED = 3;
    private static final short GROUND_ACCEL = S3kSlotBonusPlayer.GROUND_ACCEL;
    private static final short GROUND_DECEL = S3kSlotBonusPlayer.GROUND_DECEL;
    private static final short GROUND_REVERSAL_DECEL = S3kSlotBonusPlayer.GROUND_REVERSAL_DECEL;
    private static final short GROUND_MAX_SPEED = S3kSlotBonusPlayer.GROUND_MAX_SPEED;
    private static final short AIR_GRAVITY = 0x2A;
    private static final int POSITION_SHIFT = 16;
    private static final int SPEED_TO_POSITION_SHIFT = 8;

    private final S3kSlotStageState stageState;
    private final S3kSlotCollisionSystem collisionSystem;
    private int slotOriginX;
    private int slotOriginY;
    private S3kSlotExitSequence exitSequence;
    private boolean debugActive;
    private int debugSavedStatTable;
    private int debugSavedScalarIndex1;

    public S3kSlotPlayerRuntime(S3kSlotStageState stageState, S3kSlotCollisionSystem collisionSystem) {
        this.stageState = stageState;
        this.collisionSystem = collisionSystem;
    }

    public S3kSlotStageState stageState() {
        return stageState;
    }

    public void initialize(AbstractPlayableSprite player) {
        int initialOriginX = player.getCentreX();
        int initialOriginY = player.getCentreY();
        stageState.clearCollision();
        stageState.setBounceTimer(0);
        player.setAir(true);
        player.setRolling(true);
        player.setGSpeed((short) 0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setControlLocked(false);
        player.setObjectControlled(false);
        player.setOnObject(false);
        slotOriginX = initialOriginX << POSITION_SHIFT;
        slotOriginY = initialOriginY << POSITION_SHIFT;
        exitSequence = null;
        debugActive = false;
        debugSavedStatTable = 0;
        debugSavedScalarIndex1 = 0;
        syncPlayerToSlotOrigin(player);
    }

    public void syncFromController(S3kSlotStageController controller) {
        if (controller == null) {
            return;
        }
        stageState.setStatTable(controller.rawStatTable());
        stageState.setScalarIndex1(controller.scalarIndex());
        stageState.setPaletteCycleEnabled(controller.isPaletteCycleEnabled());
    }

    public void resetSlotOrigin(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        captureSlotOriginFromPlayer(player);
    }

    public void advanceRotation(boolean objectControlled) {
        int delta = objectControlled ? (stageState.scalarIndex1() << 4) : stageState.scalarIndex1();
        stageState.setStatTable((stageState.rawStatTable() + delta) & 0xFFFF);
    }

    public void tick(AbstractPlayableSprite player, boolean up, boolean down,
                     boolean left, boolean right, boolean jump, int frameCounter) {
        short originalX = player.getX();
        short originalY = player.getY();

        stageState.clearCollision();
        captureExternalSlotOriginIfNeeded(player);
        boolean wasDebugActive = debugActive;
        syncDebugState(player);
        player.setMovementInputActive(player.isDebugMode()
                ? (up || down || left || right)
                : (left != right));
        player.setAngle((byte) stageState.angle());

        if (wasDebugActive && !debugActive && !player.isDebugMode()) {
            syncPlayerToSlotOrigin(player);
            player.updateSensors(originalX, originalY);
            return;
        }

        if (exitSequence != null) {
            if (debugActive) {
                leaveDebugMode(player);
            }
            syncPlayerToSlotOrigin(player);
            player.updateSensors(originalX, originalY);
            return;
        }

        if (debugActive) {
            tickDebugMove(player, up, down, left, right);
            syncPlayerToSlotOrigin(player);
            player.updateSensors(originalX, originalY);
            return;
        }

        if (player.isObjectControlled()) {
            captureSlotOriginFromPlayer(player);
            advanceRotation(true);
            syncPlayerToSlotOrigin(player);
            player.updateSensors(originalX, originalY);
            return;
        }

        boolean startedAir = player.getAir();
        if (!startedAir && jump && player.isJumpJustPressed()) {
            launchJump(player);
        }

        applyMoveWithCollision(player, left, right);
        applyAirMotionWithCollision(player);
        applyVelocityStep(player);
        advanceRotation(false);
        syncPlayerToSlotOrigin(player);

        player.updateSensors(originalX, originalY);
    }

    public int slotOriginX() {
        return slotOriginX;
    }

    public int slotOriginY() {
        return slotOriginY;
    }

    public void startGoalExit(AbstractPlayableSprite player) {
        if (exitSequence != null) {
            return;
        }
        player.setControlLocked(true);
        player.setObjectControlled(false);
        player.setAir(true);
        player.setRolling(true);
        player.setOnObject(false);
        exitSequence = new S3kSlotExitSequence(new S3kSlotStageController(stageState));
    }

    public boolean isExiting() {
        return exitSequence != null;
    }

    public boolean isExitFading() {
        return exitSequence != null && exitSequence.isFading();
    }

    public boolean isExitComplete() {
        return exitSequence != null && exitSequence.isComplete();
    }

    public S3kSlotExitSequence activeExitSequence() {
        return exitSequence;
    }

    public void tickExitFrame(AbstractPlayableSprite player) {
        if (exitSequence == null || player == null) {
            return;
        }
        short originalX = player.getX();
        short originalY = player.getY();
        player.setAngle((byte) stageState.angle());
        exitSequence.tick();
        syncPlayerToSlotOrigin(player);
        player.updateSensors(originalX, originalY);
    }

    public boolean isDebugActive() {
        return debugActive;
    }

    private void syncDebugState(AbstractPlayableSprite player) {
        if (player.isDebugMode()) {
            if (!debugActive) {
                enterDebugMode(player);
            }
            stageState.setStatTable(0);
            stageState.setScalarIndex1(0);
            player.setAngle((byte) 0);
        } else if (debugActive) {
            leaveDebugMode(player);
        }
    }

    private void enterDebugMode(AbstractPlayableSprite player) {
        captureSlotOriginFromPlayer(player);
        debugSavedStatTable = stageState.rawStatTable();
        debugSavedScalarIndex1 = stageState.scalarIndex1();
        debugActive = true;
        stageState.setStatTable(0);
        stageState.setScalarIndex1(0);
        resetDebugMovementState(player);
    }

    private void leaveDebugMode(AbstractPlayableSprite player) {
        debugActive = false;
        stageState.setStatTable(debugSavedStatTable);
        stageState.setScalarIndex1(debugSavedScalarIndex1);
        resetDebugMovementState(player);
        player.setRolling(true);
        player.setAngle((byte) stageState.angle());
    }

    private void tickDebugMove(AbstractPlayableSprite player, boolean up, boolean down, boolean left, boolean right) {
        if (left) {
            slotOriginX -= DEBUG_MOVE_SPEED << POSITION_SHIFT;
            player.setDirection(com.openggf.physics.Direction.LEFT);
        }
        if (right) {
            slotOriginX += DEBUG_MOVE_SPEED << POSITION_SHIFT;
            player.setDirection(com.openggf.physics.Direction.RIGHT);
        }
        if (up) {
            slotOriginY -= DEBUG_MOVE_SPEED << POSITION_SHIFT;
        }
        if (down) {
            slotOriginY += DEBUG_MOVE_SPEED << POSITION_SHIFT;
        }
        resetDebugMovementState(player);
    }

    private void resetDebugMovementState(AbstractPlayableSprite player) {
        player.setGSpeed((short) 0);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setAir(true);
        player.setJumping(false);
        player.setRolling(false);
        player.setOnObject(false);
        stageState.clearCollision();
    }

    private void launchJump(AbstractPlayableSprite player) {
        int angle = (-((stageState.angle() & 0xFC)) - 0x40) & 0xFF;
        player.setXSpeed((short) ((TrigLookupTable.cosHex(angle) * 0x680) >> 8));
        player.setYSpeed((short) ((TrigLookupTable.sinHex(angle) * 0x680) >> 8));
        player.setAir(true);
        if (GameServices.audio() != null) {
            GameServices.audio().playSfx(Sonic3kSfx.JUMP.id);
        }
    }

    private void applyMoveWithCollision(AbstractPlayableSprite player, boolean left, boolean right) {
        int gSpeed = player.getGSpeed();
        if (left == right) {
            if (gSpeed > 0) {
                gSpeed = Math.max(0, gSpeed - GROUND_DECEL);
            } else if (gSpeed < 0) {
                gSpeed = Math.min(0, gSpeed + GROUND_DECEL);
            }
        } else if (left) {
            if (gSpeed > 0) {
                gSpeed -= GROUND_REVERSAL_DECEL;
                if (gSpeed < 0) {
                    gSpeed = 0;
                }
            } else {
                gSpeed = Math.max(-GROUND_MAX_SPEED, gSpeed - GROUND_ACCEL);
            }
            player.setDirection(com.openggf.physics.Direction.LEFT);
        } else {
            if (gSpeed < 0) {
                gSpeed += GROUND_REVERSAL_DECEL;
                if (gSpeed > 0) {
                    gSpeed = 0;
                }
            } else {
                gSpeed = Math.min(GROUND_MAX_SPEED, gSpeed + GROUND_ACCEL);
            }
            player.setDirection(com.openggf.physics.Direction.RIGHT);
        }
        player.setGSpeed((short) gSpeed);

        int statAngle = stageState.angle() & 0xFF;
        int projAngle = (-((statAngle + 0x20) & 0xC0)) & 0xFF;
        int sin = TrigLookupTable.sinHex(projAngle);
        int cos = TrigLookupTable.cosHex(projAngle);
        int deltaX = cos * gSpeed;
        int deltaY = sin * gSpeed;

        int preOriginX = slotOriginX;
        int preOriginY = slotOriginY;
        slotOriginX += deltaX;
        slotOriginY += deltaY;
        player.setJumping(false);

        S3kSlotCollisionSystem.Collision collision = collisionSystem.checkCollision(
                slotOriginX >> POSITION_SHIFT, slotOriginY >> POSITION_SHIFT);
        if (collision.solid()) {
            slotOriginX = preOriginX;
            slotOriginY = preOriginY;
            player.setGSpeed((short) 0);
        }
    }

    private void applyAirMotionWithCollision(AbstractPlayableSprite player) {
        int statAngle = stageState.angle() & 0xFC;
        int sin = TrigLookupTable.sinHex(statAngle);
        int cos = TrigLookupTable.cosHex(statAngle);

        long accX = ((long) player.getXSpeed() << 8) + (long) sin * AIR_GRAVITY;
        long accY = ((long) player.getYSpeed() << 8) + (long) cos * AIR_GRAVITY;

        int probeOriginX = slotOriginX;
        int probeOriginY = slotOriginY;

        probeOriginX += (int) accX;
        S3kSlotCollisionSystem.Collision colX = collisionSystem.checkCollision(
                probeOriginX >> POSITION_SHIFT, probeOriginY >> POSITION_SHIFT);
        boolean xCollided = colX.solid();
        if (xCollided) {
            accX = 0;
            player.setAir(false);
            stageState.setBounceTimer(4);
        }

        int probeOriginY2 = slotOriginY;
        probeOriginY2 += (int) accY;
        S3kSlotCollisionSystem.Collision colY = collisionSystem.checkCollision(
                (xCollided ? slotOriginX : probeOriginX) >> POSITION_SHIFT, probeOriginY2 >> POSITION_SHIFT);
        boolean yCollided = colY.solid();
        if (yCollided) {
            accY = 0;
            if (!xCollided) {
                player.setAir(false);
                stageState.setBounceTimer(4);
            }
        }

        player.setXSpeed((short) (accX >> 8));
        player.setYSpeed((short) (accY >> 8));

        if (!xCollided && !yCollided) {
            if (stageState.bounceTimer() > 0) {
                stageState.tickBounceTimer();
            } else {
                player.setAir(true);
            }
        }
    }

    /**
     * ROM MoveSprite2: after slot-specific ground and gravity handling, the updated
     * x_vel/y_vel still advance the player's fixed-point origin for the frame.
     */
    private void applyVelocityStep(AbstractPlayableSprite player) {
        slotOriginX += player.getXSpeed() << SPEED_TO_POSITION_SHIFT;
        slotOriginY += player.getYSpeed() << SPEED_TO_POSITION_SHIFT;
    }

    private void captureSlotOriginFromPlayer(AbstractPlayableSprite player) {
        slotOriginX = player.getCentreX() << POSITION_SHIFT;
        slotOriginY = player.getCentreY() << POSITION_SHIFT;
    }

    private void captureExternalSlotOriginIfNeeded(AbstractPlayableSprite player) {
        int playerX = player.getCentreX();
        int playerY = player.getCentreY();
        if (Math.abs((slotOriginX >> POSITION_SHIFT) - playerX) > 1
                || Math.abs((slotOriginY >> POSITION_SHIFT) - playerY) > 1) {
            captureSlotOriginFromPlayer(player);
        }
    }

    private void syncPlayerToSlotOrigin(AbstractPlayableSprite player) {
        player.setCentreX((short) (slotOriginX >> POSITION_SHIFT));
        player.setCentreY((short) (slotOriginY >> POSITION_SHIFT));
    }
}
