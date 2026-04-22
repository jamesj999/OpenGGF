package com.openggf.sprites.playable;

import com.openggf.game.CanonicalAnimation;
import com.openggf.physics.Direction;

/**
 * Tails-specific respawn strategy: flies in from above the leader's position,
 * homing in horizontally and vertically until reaching the target.
 */
public class TailsRespawnStrategy implements SidekickRespawnStrategy {

    private static final int RESPAWN_Y_OFFSET = 192;
    private static final int MAX_FLY_ACCEL = 12;
    private final int flyAnimId;
    private static final int FLY_LAND_BLOCKERS = 0xD2;

    private final SidekickCpuController controller;

    public TailsRespawnStrategy(SidekickCpuController controller) {
        this.controller = controller;
        this.flyAnimId = controller.resolveAnimationId(CanonicalAnimation.FLY);
    }

    @Override
    public boolean beginApproach(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        sidekick.setCentreXPreserveSubpixel(leader.getCentreX());
        sidekick.setCentreYPreserveSubpixel((short) (leader.getCentreY() - RESPAWN_Y_OFFSET));
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
        if ((recordedStatus & FLY_LAND_BLOCKERS) == 0 && remainingDx == 0 && dy == 0) {
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
