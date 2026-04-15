package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.boss.BossStateContext;

/**
 * Shared swing motion utility for AIZ miniboss objects (0x90 and 0x91).
 * Implements Swing_Setup1, Swing_UpAndDown, and Swing_UpAndDown_Count from ROM.
 *
 * ROM references:
 * - Swing_Setup1: sets max speed ($3E) = $C0, y_vel = $C0, accel ($40) = $10, clears direction bit
 * - Swing_UpAndDown: oscillates y_vel using direction flag (bit 0 of $38), returns d3=1 on peak
 * - Swing_UpAndDown_Count: calls Swing_UpAndDown, decrements $39 counter on each peak,
 *   returns d0=1 when counter goes negative
 */
public class AizMinibossSwingMotion {
    private int maxSpeed;
    private int accel;
    private boolean goingDown; // ROM: bit 0 of $38(a0)

    /** Count of remaining half-cycles; goes negative when expired. */
    private int cycleCounter;

    /**
     * ROM: Swing_Setup1
     * Sets max speed = $C0, y_vel = $C0, accel = $10, direction = up (decelerating).
     */
    public void setup1(BossStateContext state) {
        this.maxSpeed = 0xC0;
        this.accel = 0x10;
        this.goingDown = false; // ROM: bclr #0,$38(a0)
        state.yVel = maxSpeed;
    }

    /**
     * Set the half-cycle counter for Swing_UpAndDown_Count mode.
     * ROM: move.b #N,$39(a0)
     */
    public void setCycleCounter(int count) {
        this.cycleCounter = count;
    }

    /**
     * ROM: Swing_UpAndDown — oscillate y_vel between -maxSpeed and +maxSpeed.
     * Returns true if a half-cycle peak was reached (d3=1 in ROM).
     *
     * <p>At each peak the ROM applies a bounce-back correction: the velocity
     * overshoots the limit, the direction flips, and one accel step is subtracted
     * (d0 negated then added). This matches the exact waveform.
     */
    public boolean update(BossStateContext state) {
        boolean peakReached = false;

        if (!goingDown) {
            // Going up: decelerate from positive, through zero, to negative peak
            int vel = state.yVel - accel;
            if (vel <= -maxSpeed) {
                // Top peak reached
                goingDown = true;
                vel = -maxSpeed;
                // ROM applies bounce-back: neg.w d0; add.w d0,d1
                // but since we clamp to -maxSpeed the net effect is the same
                // as starting the next half from exactly -maxSpeed.
                peakReached = true;
            }
            state.yVel = vel;
        } else {
            // Going down: accelerate from negative peak toward positive peak
            int vel = state.yVel + accel;
            if (vel >= maxSpeed) {
                // Bottom peak reached — ROM: bclr #0, neg d0, add d0 to d1
                goingDown = false;
                // ROM bounce-back: vel = maxSpeed + accel (overshoot), then subtract accel
                // net = maxSpeed. But the next frame starts the UP pass from maxSpeed.
                vel = maxSpeed;
                peakReached = true;
            }
            state.yVel = vel;
        }

        return peakReached;
    }

    /**
     * ROM: Swing_UpAndDown_Count — update swing and count half-cycles.
     *
     * <p>Returns a result indicating whether the count has expired.
     * When the cycle counter goes negative, the caller should check
     * {@code state.yVel} to decide when to transition (ROM checks d1 < 0).
     *
     * @return {@link CountResult#CONTINUE} while swinging,
     *         {@link CountResult#EXPIRED} once the counter goes negative.
     */
    public CountResult updateAndCount(BossStateContext state) {
        boolean peak = update(state);
        if (!peak) {
            return CountResult.CONTINUE;
        }
        cycleCounter--;
        if (cycleCounter < 0) {
            return CountResult.EXPIRED;
        }
        return CountResult.CONTINUE;
    }

    public enum CountResult {
        CONTINUE,
        EXPIRED
    }
}
