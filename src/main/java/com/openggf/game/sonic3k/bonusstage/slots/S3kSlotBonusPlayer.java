package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.level.LevelManager;
import com.openggf.physics.TrigLookupTable;
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
            applyAirMotion(player, controller);
        } else {
            applyGroundMotion(player, left, right, controller);
        }

        player.move(player.getXSpeed(), player.getYSpeed());
        player.updateSensors(originalX, originalY);
    }

    private static void applyGroundMotion(AbstractPlayableSprite player, boolean left, boolean right,
                                          S3kSlotStageController controller) {
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

        // ROM sub_4BABC lines 98818-98827: project gSpeed through rotation angle
        // addi.b #$20,d0 / andi.b #$C0,d0 / neg.b d0 → snap to quadrant, negate
        int statAngle = controller != null ? controller.angle() & 0xFF : 0;
        int projAngle = (-((statAngle + 0x20) & 0xC0)) & 0xFF;
        int sin = TrigLookupTable.sinHex(projAngle);
        int cos = TrigLookupTable.cosHex(projAngle);
        player.setXSpeed((short) ((cos * gSpeed) >> 8));
        player.setYSpeed((short) ((sin * gSpeed) >> 8));
        player.setJumping(false);
    }

    private static void applyAirMotion(AbstractPlayableSprite player,
                                       S3kSlotStageController controller) {
        // ROM sub_4BCB0 lines 99005-99070: angle-dependent gravity (no left/right air control)
        // andi.b #$FC,d0 — mask to 2-bit quadrant precision
        int statAngle = controller != null ? controller.angle() & 0xFC : 0;
        int sin = TrigLookupTable.sinHex(statAngle);
        int cos = TrigLookupTable.cosHex(statAngle);

        // x_vel_extended + sin * gravity_factor (0x2A)
        // y_vel_extended + cos * gravity_factor (0x2A)
        // ext.l d4 / asl.l #8,d4: extend velocity to 24.8 fixed-point
        // asr.l #8,d0: extract back to velocity
        long newX = ((long) player.getXSpeed() << 8) + (long) sin * 0x2A;
        long newY = ((long) player.getYSpeed() << 8) + (long) cos * 0x2A;
        player.setXSpeed((short) (newX >> 8));
        player.setYSpeed((short) (newY >> 8));
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
