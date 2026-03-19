package com.openggf.sprites.playable;

import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.physics.Direction;

/**
 * Tails-specific respawn strategy: flies in from above the leader's position,
 * homing in horizontally and vertically until reaching the target.
 */
public class TailsRespawnStrategy implements SidekickRespawnStrategy {

    private static final int RESPAWN_Y_OFFSET = 192;
    private static final int MAX_FLY_ACCEL = 12;
    private static final int FLY_ANIM_ID = Sonic2AnimationIds.FLY.id();
    private static final int FOLLOW_DELAY_FRAMES = 17;
    private static final int FLY_LAND_BLOCKERS = 0xD2;

    private final SidekickCpuController controller;

    public TailsRespawnStrategy(SidekickCpuController controller) {
        this.controller = controller;
    }

    @Override
    public void beginApproach(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        sidekick.setCentreX(leader.getCentreX());
        sidekick.setCentreY((short) (leader.getCentreY() - RESPAWN_Y_OFFSET));
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setAir(true);
        sidekick.setDead(false);
        sidekick.setHurt(false);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setForcedAnimationId(FLY_ANIM_ID);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);
    }

    @Override
    public boolean updateApproaching(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader,
                                     int frameCounter) {
        sidekick.setForcedAnimationId(FLY_ANIM_ID);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);

        int targetX = leader.getCentreX(FOLLOW_DELAY_FRAMES);
        int targetY = controller.clampTargetYToWater(leader.getCentreY(FOLLOW_DELAY_FRAMES));
        int sidekickX = sidekick.getCentreX();
        int sidekickY = sidekick.getCentreY();

        int dx = targetX - sidekickX;
        if (dx != 0) {
            int move = Math.abs(dx) / 16;
            move = Math.min(move, MAX_FLY_ACCEL);
            move += Math.abs(leader.getXSpeed()) / 256;
            move += 1;
            move = Math.min(move, Math.abs(dx));
            if (dx > 0) {
                sidekick.setDirection(Direction.RIGHT);
                sidekick.setX((short) (sidekick.getX() + move));
                sidekick.setXSpeed((short) (move * 256));
            } else {
                sidekick.setDirection(Direction.LEFT);
                sidekick.setX((short) (sidekick.getX() - move));
                sidekick.setXSpeed((short) (-move * 256));
            }
        } else {
            sidekick.setXSpeed((short) 0);
        }

        int dy = targetY - sidekickY;
        if (dy > 0) {
            sidekick.setY((short) (sidekick.getY() + 1));
            sidekick.setYSpeed((short) 0x100);
        } else if (dy < 0) {
            sidekick.setY((short) (sidekick.getY() - 1));
            sidekick.setYSpeed((short) -0x100);
        } else {
            sidekick.setYSpeed((short) 0);
        }

        int remainingDx = targetX - sidekick.getCentreX();
        int remainingDy = targetY - sidekick.getCentreY();
        byte recordedStatus = leader.getStatusHistory(FOLLOW_DELAY_FRAMES);
        if ((recordedStatus & FLY_LAND_BLOCKERS) == 0 && remainingDx == 0 && remainingDy == 0) {
            return true;
        }
        return false;
    }
}
