package uk.co.jamesj999.sonic.physics;

/**
 * Port of Swing_UpAndDown (sonic3k.asm:177851).
 * Oscillating motion utility for pendulum/bobbing objects.
 *
 * The object swings between +max and -max velocity, reversing direction
 * at each peak. Used by AIZ intro plane, swinging platforms, etc.
 */
public final class SwingMotion {
    private SwingMotion() {}

    public record Result(int velocity, boolean directionDown, boolean directionChanged) {}

    /**
     * Update swing motion for one frame.
     *
     * @param acceleration per-frame acceleration magnitude (ROM: $40(a0))
     * @param velocity     current velocity (ROM: y_vel)
     * @param maxVelocity  peak velocity magnitude (ROM: $3E(a0))
     * @param directionDown true=swinging down/positive, false=swinging up/negative (ROM: bit 0 of $38)
     * @return updated velocity, direction, and whether direction changed this frame
     */
    public static Result update(int acceleration, int velocity, int maxVelocity, boolean directionDown) {
        int d0 = acceleration;
        int d1 = velocity;
        int d2 = maxVelocity;
        boolean changed = false;

        // ROM: if bit0 clear, apply upward acceleration first.
        if (!directionDown) {
            d0 = -d0;
            d1 += d0;
            d2 = -d2;
            if (d1 <= d2) {
                // Hit upper bound: flip direction and cancel the overshoot step.
                directionDown = true;
                d0 = -d0;
                d2 = -d2;
                changed = true;
            } else {
                return new Result(d1, false, false);
            }
        }

        // Downward phase (also entered immediately after an upper-bound flip).
        d1 += d0;
        if (d1 >= d2) {
            // Hit lower bound: flip direction and cancel the overshoot step.
            directionDown = false;
            d0 = -d0;
            d1 += d0;
            changed = true;
        }

        return new Result(d1, directionDown, changed);
    }
}
