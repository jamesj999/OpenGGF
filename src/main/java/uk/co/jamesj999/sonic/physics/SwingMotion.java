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
        boolean changed = false;

        if (!directionDown) {
            // Swinging up: subtract acceleration
            velocity -= acceleration;
            if (velocity <= -maxVelocity) {
                directionDown = true;
                changed = true;
            }
        } else {
            // Swinging down: add acceleration
            velocity += acceleration;
            if (velocity >= maxVelocity) {
                directionDown = false;
                changed = true;
            }
        }

        return new Result(velocity, directionDown, changed);
    }
}
