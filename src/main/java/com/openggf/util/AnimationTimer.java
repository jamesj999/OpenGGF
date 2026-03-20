package com.openggf.util;

/**
 * Reusable frame-counting animation timer.
 * <p>
 * Counts ticks and advances the current frame index when the tick count
 * reaches {@code duration}, wrapping around after {@code frameCount}.
 * <p>
 * Replaces the manual {@code timer++; if (timer >= DURATION) { timer=0; frame = (frame+1) % N; }}
 * boilerplate found in many badnik and object classes.
 * <p>
 * <b>Note on {@code >} vs {@code >=}:</b> This timer uses {@code >=} internally.
 * If the original code used {@code timer > threshold}, pass {@code threshold + 1}
 * as the duration to preserve identical timing.
 */
public final class AnimationTimer {

    private int timer;
    private int frame;
    private final int duration;
    private final int frameCount;

    /**
     * Creates a new animation timer.
     *
     * @param duration   number of ticks before advancing to the next frame
     * @param frameCount total number of frames (wraps from frameCount-1 back to 0)
     */
    public AnimationTimer(int duration, int frameCount) {
        this.duration = duration;
        this.frameCount = frameCount;
    }

    /**
     * Advances the internal tick counter. When the counter reaches {@code duration},
     * the frame index advances (wrapping at {@code frameCount}) and the counter resets.
     *
     * @return {@code true} if the frame index changed on this tick
     */
    public boolean tick() {
        timer++;
        if (timer >= duration) {
            timer = 0;
            frame = (frame + 1) % frameCount;
            return true;
        }
        return false;
    }

    /**
     * Returns the current frame index (0-based).
     */
    public int getFrame() {
        return frame;
    }

    /**
     * Resets both the tick counter and frame index to zero.
     */
    public void reset() {
        timer = 0;
        frame = 0;
    }

    /**
     * Sets the current frame index directly (does not reset the tick counter).
     *
     * @param frame the frame index to set
     */
    public void setFrame(int frame) {
        this.frame = frame;
    }
}
