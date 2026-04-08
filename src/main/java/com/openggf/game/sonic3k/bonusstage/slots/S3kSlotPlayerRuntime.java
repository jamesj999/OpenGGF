package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;

public final class S3kSlotPlayerRuntime {
    private static final short GROUND_ACCEL = S3kSlotBonusPlayer.GROUND_ACCEL;
    private static final short GROUND_DECEL = S3kSlotBonusPlayer.GROUND_DECEL;
    private static final short GROUND_REVERSAL_DECEL = S3kSlotBonusPlayer.GROUND_REVERSAL_DECEL;
    private static final short GROUND_MAX_SPEED = S3kSlotBonusPlayer.GROUND_MAX_SPEED;
    private static final short AIR_GRAVITY = 0x2A;

    private final S3kSlotStageState stageState;
    private final S3kSlotCollisionSystem collisionSystem;

    public S3kSlotPlayerRuntime(S3kSlotStageState stageState, S3kSlotCollisionSystem collisionSystem) {
        this.stageState = stageState;
        this.collisionSystem = collisionSystem;
    }

    public static S3kSlotPlayerRuntime fromRomData() {
        S3kSlotStageState state = S3kSlotStageState.bootstrap();
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();
        return new S3kSlotPlayerRuntime(state, new S3kSlotCollisionSystem(buffers, state));
    }

    public S3kSlotStageState stageState() {
        return stageState;
    }

    public void initialize(AbstractPlayableSprite player) {
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
    }

    public void syncFromController(S3kSlotStageController controller) {
        if (controller == null) {
            return;
        }
        stageState.setStatTable(controller.rawStatTable());
        stageState.setScalarIndex1(controller.scalarIndex());
        stageState.setPaletteCycleEnabled(controller.isPaletteCycleEnabled());
    }

    public void advanceRotation(boolean objectControlled) {
        int delta = objectControlled ? (stageState.scalarIndex1() << 4) : stageState.scalarIndex1();
        stageState.setStatTable((stageState.rawStatTable() + delta) & 0xFFFF);
    }

    public void tick(AbstractPlayableSprite player, boolean left, boolean right, boolean jump, int frameCounter) {
        short originalX = player.getX();
        short originalY = player.getY();

        stageState.clearCollision();
        player.setMovementInputActive(left != right);
        player.setAngle((byte) stageState.angle());

        if (jump && player.isJumpJustPressed() && !player.getAir()) {
            launchJump(player);
        }

        if (player.getAir()) {
            applyAirMotionWithCollision(player);
        } else {
            applyGroundMotionWithCollision(player, left, right);
        }

        player.updateSensors(originalX, originalY);
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

    private void applyGroundMotionWithCollision(AbstractPlayableSprite player, boolean left, boolean right) {
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

        short preX = player.getX();
        short preY = player.getY();
        player.setXSpeed((short) (deltaX >> 8));
        player.setYSpeed((short) (deltaY >> 8));
        player.move(player.getXSpeed(), player.getYSpeed());
        player.setJumping(false);

        S3kSlotCollisionSystem.Collision collision = collisionSystem.checkCollision(player.getX(), player.getY());
        if (collision.solid()) {
            player.setX(preX);
            player.setY(preY);
            player.setGSpeed((short) 0);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
        }
    }

    private void applyAirMotionWithCollision(AbstractPlayableSprite player) {
        int statAngle = stageState.angle() & 0xFC;
        int sin = TrigLookupTable.sinHex(statAngle);
        int cos = TrigLookupTable.cosHex(statAngle);

        long accX = ((long) player.getXSpeed() << 8) + (long) sin * AIR_GRAVITY;
        long accY = ((long) player.getYSpeed() << 8) + (long) cos * AIR_GRAVITY;

        short preX = player.getX();
        short preY = player.getY();

        short tryXSpeed = (short) (accX >> 8);
        player.move(tryXSpeed, (short) 0);
        S3kSlotCollisionSystem.Collision colX = collisionSystem.checkCollision(player.getX(), player.getY());
        boolean xCollided = colX.solid();
        if (xCollided) {
            player.setX(preX);
            accX = 0;
            player.setAir(false);
            stageState.setBounceTimer(4);
        }

        short preY2 = player.getY();
        short tryYSpeed = (short) (accY >> 8);
        player.move((short) 0, tryYSpeed);
        S3kSlotCollisionSystem.Collision colY = collisionSystem.checkCollision(player.getX(), player.getY());
        boolean yCollided = colY.solid();
        if (yCollided) {
            player.setY(preY2);
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
}
