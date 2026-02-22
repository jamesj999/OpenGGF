package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.boss.BossStateContext;

/**
 * Shared swing motion utility for AIZ miniboss objects (0x90 and 0x91).
 * Implements Swing_Setup1 / Swing_UpAndDown from ROM.
 */
public class AizMinibossSwingMotion {
    private int speed;
    private int accel;

    /**
     * ROM: Swing_Setup1
     * - speed = 0xC0
     * - y_vel = speed
     * - accel = 0x10
     */
    public void setup1(BossStateContext state) {
        this.speed = 0xC0;
        this.accel = 0x10;
        state.yVel = speed;
    }

    /**
     * Oscillate y_vel between -speed and +speed.
     * ROM: Swing_UpAndDown - adds accel to yVel, negates accel at bounds.
     */
    public void update(BossStateContext state) {
        state.yVel += accel;
        if (accel > 0 && state.yVel >= speed) {
            state.yVel = speed;
            accel = -accel;
        } else if (accel < 0 && state.yVel <= -speed) {
            state.yVel = -speed;
            accel = -accel;
        }
    }
}
