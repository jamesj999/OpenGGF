package com.openggf.sprites.playable;

import com.openggf.level.LevelManager;

public interface CustomPlayablePhysics {
    void tickCustomPhysics(boolean up, boolean down, boolean left, boolean right,
                           boolean jump, boolean test, boolean speedUp, boolean slowDown,
                           LevelManager levelManager, int frameCounter);
}
