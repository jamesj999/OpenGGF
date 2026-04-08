package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.sprites.playable.Sonic;

public final class S3kSlotBonusPlayer extends Sonic implements CustomPlayablePhysics {
    private final S3kSlotStageController controller;

    public S3kSlotBonusPlayer(String code, short x, short y, S3kSlotStageController controller) {
        super(code, x, y);
        this.controller = controller;
        setHighPriority(true);
    }

    @Override
    public void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                                  boolean jump, boolean test, boolean speedUp, boolean slowDown,
                                  LevelManager levelManager, int frameCounter) {
        if (controller != null) {
            controller.tickPlayer(this, left, right, jump, frameCounter);
        }
    }
}
