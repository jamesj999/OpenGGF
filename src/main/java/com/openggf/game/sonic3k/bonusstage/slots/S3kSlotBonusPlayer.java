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

    /**
     * Main per-frame physics tick matching ROM loc_4BA4E (lines 98737-98779).
     * Collision checks are integrated inline with movement, not as a post-hoc step.
     *
     * @param layout the 32x32 grid layout for collision checks (may be null to skip collision)
     */
    static void tickAndMove(AbstractPlayableSprite player, S3kSlotStageController controller,
                            boolean left, boolean right, boolean jump, int frameCounter) {
        tickAndMove(player, controller, left, right, jump, frameCounter, null);
    }

    static void tickAndMove(AbstractPlayableSprite player, S3kSlotStageController controller,
                            boolean left, boolean right, boolean jump, int frameCounter,
                            byte[] layout) {
        short originalX = player.getX();
        short originalY = player.getY();
        tickController((S3kSlotBonusPlayer) player, controller, left, right, jump, frameCounter);
        player.setMovementInputActive(left != right);

        if (player.getAir()) {
            applyAirMotionWithCollision(player, controller, layout);
        } else {
            applyGroundMotionWithCollision(player, left, right, controller, layout);
        }

        player.updateSensors(originalX, originalY);
    }

    /**
     * ROM sub_4BABC (lines 98784-98843): Ground movement with inline collision rollback.
     * Computes gSpeed from input, projects through rotation angle, moves, checks collision,
     * rolls back on solid hit.
     */
    private static void applyGroundMotionWithCollision(AbstractPlayableSprite player,
                                                       boolean left, boolean right,
                                                       S3kSlotStageController controller,
                                                       byte[] layout) {
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
                if (gSpeed < 0) gSpeed = 0;
            } else {
                gSpeed = Math.max(-GROUND_MAX_SPEED, gSpeed - GROUND_ACCEL);
            }
            player.setDirection(com.openggf.physics.Direction.LEFT);
        } else {
            if (gSpeed < 0) {
                gSpeed += GROUND_REVERSAL_DECEL;
                if (gSpeed > 0) gSpeed = 0;
            } else {
                gSpeed = Math.min(GROUND_MAX_SPEED, gSpeed + GROUND_ACCEL);
            }
            player.setDirection(com.openggf.physics.Direction.RIGHT);
        }
        player.setGSpeed((short) gSpeed);

        // Project gSpeed through rotation angle (lines 98818-98827)
        int statAngle = controller != null ? controller.angle() & 0xFF : 0;
        int projAngle = (-((statAngle + 0x20) & 0xC0)) & 0xFF;
        int sin = TrigLookupTable.sinHex(projAngle);
        int cos = TrigLookupTable.cosHex(projAngle);
        int deltaX = cos * gSpeed;  // 24.8 fixed-point
        int deltaY = sin * gSpeed;

        // Move: add.l d1,x_pos(a0) / add.l d0,y_pos(a0) (lines 98825-98827)
        short preX = player.getX();
        short preY = player.getY();
        player.setXSpeed((short) (deltaX >> 8));
        player.setYSpeed((short) (deltaY >> 8));
        player.move(player.getXSpeed(), player.getYSpeed());
        player.setJumping(false);

        // Collision check + rollback (lines 98828-98837)
        if (layout != null) {
            S3kSlotGridCollision.Result col = S3kSlotGridCollision.check(
                    layout, player.getX(), player.getY());
            if (col.solid()) {
                // Rollback: sub.l d1,x_pos / sub.l d0,y_pos / move.w #0,ground_vel
                player.setX(preX);
                player.setY(preY);
                player.setGSpeed((short) 0);
                player.setXSpeed((short) 0);
                player.setYSpeed((short) 0);
                // Store tile ID for interaction dispatch (ROM: $30(a0) set by sub_4BDA2)
                if (col.special() && controller != null) {
                    controller.setLastCollision(col.tileId(), col.layoutIndex());
                }
            }
        }
    }

    /**
     * ROM sub_4BCB0 (lines 99005-99070): Air movement with angle-dependent gravity
     * and two-axis separate collision checks with per-axis rollback.
     *
     * <p>Flow: compute gravity → try X → collision check → if solid: rollback X,
     * zero x_vel, set grounded, bounceTimer=4 → try Y → collision check → if solid:
     * rollback Y, zero y_vel → if no collision and bounceTimer expired: set airborne.
     */
    private static void applyAirMotionWithCollision(AbstractPlayableSprite player,
                                                    S3kSlotStageController controller,
                                                    byte[] layout) {
        int statAngle = controller != null ? controller.angle() & 0xFC : 0;
        int sin = TrigLookupTable.sinHex(statAngle);
        int cos = TrigLookupTable.cosHex(statAngle);

        // Gravity + velocity in 24.8 fixed-point (lines 99011-99020)
        long accX = ((long) player.getXSpeed() << 8) + (long) sin * 0x2A;  // d0
        long accY = ((long) player.getYSpeed() << 8) + (long) cos * 0x2A;  // d1

        if (layout == null) {
            // No layout = no collision, just apply velocity
            player.setXSpeed((short) (accX >> 8));
            player.setYSpeed((short) (accY >> 8));
            player.move(player.getXSpeed(), player.getYSpeed());
            return;
        }

        // ===== COLLISION CHECK #1: X-AXIS (lines 99021-99035) =====
        short preX = player.getX();
        short preY = player.getY();
        // Try X movement only
        short tryXSpeed = (short) (accX >> 8);
        player.move(tryXSpeed, (short) 0);

        S3kSlotGridCollision.Result colX = S3kSlotGridCollision.check(
                layout, player.getX(), player.getY());

        boolean xCollided = colX.solid();
        if (xCollided) {
            player.setX(preX);
            accX = 0;
            player.setAir(false);
            if (controller != null) controller.setBounceTimer(4);
            if (colX.special() && controller != null) {
                controller.setLastCollision(colX.tileId(), colX.layoutIndex());
            }
        }

        // ===== COLLISION CHECK #2: Y-AXIS (lines 99029-99045 or 99038-99045) =====
        short preY2 = player.getY();
        short tryYSpeed = (short) (accY >> 8);
        player.move((short) 0, tryYSpeed);

        S3kSlotGridCollision.Result colY = S3kSlotGridCollision.check(
                layout, player.getX(), player.getY());

        boolean yCollided = colY.solid();
        if (yCollided) {
            player.setY(preY2);
            accY = 0;
            if (!xCollided) {
                player.setAir(false);
                if (controller != null) controller.setBounceTimer(4);
            }
            if (colY.special() && controller != null) {
                controller.setLastCollision(colY.tileId(), colY.layoutIndex());
            }
        }

        // Extract final velocities: asr.l #8 (lines 99049-99052 or 99057-99060)
        player.setXSpeed((short) (accX >> 8));
        player.setYSpeed((short) (accY >> 8));

        // Bounce timer logic (lines 99061-99069):
        // If no collision at all and bounce timer expired → become airborne
        if (!xCollided && !yCollided && controller != null) {
            if (controller.bounceTimer() > 0) {
                // Timer still active — stay grounded (don't set airborne)
                controller.tickBounceTimer();
            } else {
                // No collision, no timer → airborne
                player.setAir(true);
            }
        }
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
            S3kSlotBonusPlayer.tickAndMove(this, controller, left, right, jump, frameCounter,
                    controller.activeLayout());
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
            S3kSlotBonusPlayer.tickAndMove(this, controller, left, right, jump, frameCounter,
                    controller.activeLayout());
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
            S3kSlotBonusPlayer.tickAndMove(this, controller, left, right, jump, frameCounter,
                    controller.activeLayout());
        }
    }
}
