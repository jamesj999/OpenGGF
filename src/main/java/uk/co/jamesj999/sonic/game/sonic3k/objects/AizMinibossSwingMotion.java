package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.level.objects.boss.BossStateContext;

/**
 * Shared swing motion utility for AIZ miniboss objects (0x90 and 0x91).
 * Implements Swing_UpAndDown from ROM: oscillates y_vel between -speed and +speed.
 */
public class AizMinibossSwingMotion {
    private int speed;
    private int accel;

    /** Initialize with Swing_Setup1 parameters: speed=0xC0, accel=0x10. */
    public void setup1() {
        this.speed = 0xC0;
        this.accel = 0x10;
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
