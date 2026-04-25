package com.openggf.sprites.playable;

import com.openggf.game.CanonicalAnimation;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.physics.Direction;

/**
 * Tails-specific respawn strategy: flies in from above the leader's position,
 * homing in horizontally and vertically until reaching the target.
 */
public class TailsRespawnStrategy implements SidekickRespawnStrategy {

    private static final int RESPAWN_Y_OFFSET = 192;
    private static final int MAX_FLY_ACCEL = 12;
    private final int flyAnimId;
    /** S2 fallback if no PhysicsFeatureSet is resolved (legacy unit-test sidekicks). */
    private static final int FLY_LAND_BLOCKERS_FALLBACK = PhysicsFeatureSet.SIDEKICK_FLY_LAND_BLOCKERS_S2;
    /** Sonic OST routine value at/above which the leader is considered dead/dying.
     *  ROM: {@code cmpi.b #6,(Player_1+routine).w / bhs.s loc_13D42} (sonic3k.asm:26629-26630). */
    private static final int LEADER_DEAD_ROUTINE_THRESHOLD = 6;

    private final SidekickCpuController controller;

    public TailsRespawnStrategy(SidekickCpuController controller) {
        this.controller = controller;
        this.flyAnimId = controller.resolveAnimationId(CanonicalAnimation.FLY);
    }

    @Override
    public boolean beginApproach(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        sidekick.setCentreXPreserveSubpixel(leader.getCentreX());
        sidekick.setCentreYPreserveSubpixel((short) (leader.getCentreY() - RESPAWN_Y_OFFSET));
        // ROM Tails_Catch_Up_Flying loc_13B50 (sonic3k.asm:26503-26506) zeroes
        // x_vel, y_vel, and ground_vel via `moveq #0,d0` followed by three
        // `move.w d0,*_vel(a0)` writes immediately after the position teleport.
        // Without this the engine retains the stale velocity from before the
        // despawn (objectControlled blocked applyGravity / move handlers from
        // touching them for the entire 60-frame parked-at-marker window),
        // surfacing as the AIZ trace F2465 tails_x_speed -0x01F9 mismatch.
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setAir(true);
        sidekick.setDead(false);
        sidekick.setHurt(false);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);
        return true;
    }

    @Override
    public boolean updateApproaching(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader,
                                     int frameCounter) {
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);

        int targetX = leader.getCentreX(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES);
        int targetY = controller.clampTargetYToWater(
                leader.getCentreY(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES));
        int sidekickX = sidekick.getCentreX();
        int sidekickY = sidekick.getCentreY();

        int dx = targetX - sidekickX;
        int dy = targetY - sidekickY;
        int remainingDx = dx;
        if (dx != 0) {
            int move = Math.abs(dx) / 16;
            move = Math.min(move, MAX_FLY_ACCEL);
            // ROM uses "mvabs.b x_vel(a1),d1" here. On 68000 this reads the first
            // byte of the big-endian word, i.e. the signed high byte of x_vel.
            move += Math.abs(leader.getXSpeed() >> 8);
            move += 1;
            move = Math.min(move, Math.abs(dx));
            if (dx > 0) {
                sidekick.setDirection(Direction.RIGHT);
                sidekick.setX((short) (sidekick.getX() + move));
                remainingDx = dx - move;
            } else {
                sidekick.setDirection(Direction.LEFT);
                sidekick.setX((short) (sidekick.getX() - move));
                remainingDx = dx + move;
            }
        }

        if (dy > 0) {
            sidekick.setY((short) (sidekick.getY() + 1));
        } else if (dy < 0) {
            sidekick.setY((short) (sidekick.getY() - 1));
        }

        byte recordedStatus = leader.getStatusHistory(SidekickCpuController.ROM_FOLLOW_DELAY_FRAMES);
        // ROM TailsCPU_Flying exits when:
        // - the post-horizontal residual d0 is zero (horizontal catch-up may finish
        //   in this frame after applying the x_vel-based speed bonus), and
        // - the pre-vertical residual d1 was already zero (vertical +/-1 movement
        //   does NOT allow same-frame completion because d1 is tested before the move).
        //
        // The status-blocker mask AND leader-alive check differ per game:
        //   * S2 (s2.asm:38872-38873) andi.b #$D2,d2 / bne return — bits 1+4+6+7
        //     (in_air|roll_jump|underwater|bit7). NO leader-routine check; transitions
        //     to NORMAL even if Sonic is hurt or dead.
        //   * S3K (sonic3k.asm:26625, 26629-26630) andi.b #$80,d2 (bit 7 only) AND
        //     cmpi.b #6,(Player_1+routine).w / bhs (skip if Sonic dead).
        // Resolved through PhysicsFeatureSet so each game's ROM behavior is preserved.
        PhysicsFeatureSet fs = sidekick.getPhysicsFeatureSet();
        int statusBlockerMask = fs != null
                ? fs.sidekickFlyLandStatusBlockerMask()
                : FLY_LAND_BLOCKERS_FALLBACK;
        boolean requireLeaderAlive = fs != null && fs.sidekickFlyLandRequiresLeaderAlive();
        if ((recordedStatus & statusBlockerMask) == 0 && remainingDx == 0 && dy == 0) {
            if (requireLeaderAlive && leader.getDead()) {
                return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public void onApproachComplete(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        sidekick.setHighPriority(leader.isHighPriority());
        sidekick.setTopSolidBit(leader.getTopSolidBit());
        sidekick.setLrbSolidBit(leader.getLrbSolidBit());
    }
}
