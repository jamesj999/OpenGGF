package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;

import java.util.Locale;

public interface S3kSlotBonusPlayer extends CustomPlayablePhysics {
    short GROUND_ACCEL = 0x0C;           // sub_4BB54 line 98856: subi.w #$C,d0
    short GROUND_DECEL = 0x0C;           // sub_4BABC line 98801: subi.w #$C,d0
    short GROUND_REVERSAL_DECEL = 0x40;  // sub_4BB54 line 98867: subi.w #$40,d0
    short GROUND_MAX_SPEED = 0x800;      // sub_4BB54 line 98857: cmpi.w #-$800,d0
    short AIR_ACCEL = 0x18;
    short AIR_MAX_SPEED = 0x300;

    String getCode();

    void setAngle(byte angle);

    static AbstractPlayableSprite create(String mainCode, short x, short y, S3kSlotStageController controller) {
        return switch (normalize(mainCode)) {
            case "tails" -> new TailsSlotBonusPlayer(mainCode, x, y, controller);
            case "knuckles" -> new KnucklesSlotBonusPlayer(mainCode, x, y, controller);
            default -> new SonicSlotBonusPlayer(mainCode, x, y, controller);
        };
    }

    static void tickController(S3kSlotBonusPlayer player, S3kSlotStageController controller,
                               boolean left, boolean right, boolean jump, int frameCounter) {
        if (controller != null) {
            controller.tickPlayer(player, left, right, jump, frameCounter);
        }
    }

    static void tickAndMove(AbstractPlayableSprite player, S3kSlotStageController controller,
                            boolean left, boolean right, boolean jump, int frameCounter) {
        short originalX = player.getX();
        short originalY = player.getY();
        tickController((S3kSlotBonusPlayer) player, controller, left, right, jump, frameCounter);
        player.setMovementInputActive(left != right);

        if (player.getAir()) {
            applyAirMotion(player, left, right);
        } else {
            applyGroundMotion(player, left, right);
        }

        player.move(player.getXSpeed(), player.getYSpeed());
        player.updateSensors(originalX, originalY);
    }

    private static void applyGroundMotion(AbstractPlayableSprite player, boolean left, boolean right) {
        int gSpeed = player.getGSpeed();
        if (left == right) {
            // No input: friction (sub_4BABC lines 98798-98816)
            if (gSpeed > 0) {
                gSpeed = Math.max(0, gSpeed - GROUND_DECEL);
            } else if (gSpeed < 0) {
                gSpeed = Math.min(0, gSpeed + GROUND_DECEL);
            }
        } else if (left) {
            if (gSpeed > 0) {
                // Reversing: heavy decel (sub_4BB54 line 98867: subi.w #$40,d0)
                gSpeed -= GROUND_REVERSAL_DECEL;
                if (gSpeed < 0) gSpeed = 0;
            } else {
                // Accelerating left (sub_4BB54 line 98856: subi.w #$C,d0)
                gSpeed = Math.max(-GROUND_MAX_SPEED, gSpeed - GROUND_ACCEL);
            }
            player.setDirection(com.openggf.physics.Direction.LEFT);
        } else {
            if (gSpeed < 0) {
                // Reversing: heavy decel (sub_4BB84 line 98895: addi.w #$40,d0)
                gSpeed += GROUND_REVERSAL_DECEL;
                if (gSpeed > 0) gSpeed = 0;
            } else {
                // Accelerating right (sub_4BB84 line 98884: addi.w #$C,d0)
                gSpeed = Math.min(GROUND_MAX_SPEED, gSpeed + GROUND_ACCEL);
            }
            player.setDirection(com.openggf.physics.Direction.RIGHT);
        }
        player.setGSpeed((short) gSpeed);
        player.setXSpeed((short) gSpeed);
        player.setYSpeed((short) 0);
        player.setJumping(false);
    }

    private static void applyAirMotion(AbstractPlayableSprite player, boolean left, boolean right) {
        int xSpeed = player.getXSpeed();
        if (left && !right) {
            xSpeed = Math.max(-AIR_MAX_SPEED, xSpeed - AIR_ACCEL);
            player.setDirection(com.openggf.physics.Direction.LEFT);
        } else if (right && !left) {
            xSpeed = Math.min(AIR_MAX_SPEED, xSpeed + AIR_ACCEL);
            player.setDirection(com.openggf.physics.Direction.RIGHT);
        }

        int ySpeed = player.getYSpeed() + Math.round(player.getGravity());
        player.setXSpeed((short) xSpeed);
        player.setYSpeed((short) ySpeed);
    }

    private static String normalize(String mainCode) {
        return mainCode == null ? "" : mainCode.trim().toLowerCase(Locale.ROOT);
    }

    final class SonicSlotBonusPlayer extends Sonic implements S3kSlotBonusPlayer {
        private final S3kSlotStageController controller;

        SonicSlotBonusPlayer(String code, short x, short y, S3kSlotStageController controller) {
            super(code, x, y);
            this.controller = controller;
            setHighPriority(true);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            S3kSlotBonusPlayer.tickAndMove(this, controller, left, right, jump, frameCounter);
        }
    }

    final class TailsSlotBonusPlayer extends Tails implements S3kSlotBonusPlayer {
        private final S3kSlotStageController controller;

        TailsSlotBonusPlayer(String code, short x, short y, S3kSlotStageController controller) {
            super(code, x, y);
            this.controller = controller;
            setHighPriority(true);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            S3kSlotBonusPlayer.tickAndMove(this, controller, left, right, jump, frameCounter);
        }
    }

    final class KnucklesSlotBonusPlayer extends Knuckles implements S3kSlotBonusPlayer {
        private final S3kSlotStageController controller;

        KnucklesSlotBonusPlayer(String code, short x, short y, S3kSlotStageController controller) {
            super(code, x, y);
            this.controller = controller;
            setHighPriority(true);
        }

        @Override
        public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                      boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                      LevelManager levelManager, int frameCounter) {
            S3kSlotBonusPlayer.tickAndMove(this, controller, left, right, jump, frameCounter);
        }
    }
}
