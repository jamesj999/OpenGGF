package com.openggf.game.rewind;

/**
 * Strip cache between RewindController and KeyframeStore. Expands one
 * segment of {@code intervalFrames} on demand by stepping forward from
 * a keyframe and capturing per-frame snapshots. Subsequent backward
 * steps within the expanded segment are O(1) array lookups.
 *
 * <p>v1 keeps at most one expanded segment ("currentSegment"); a
 * follow-up plan will extend this to a small ring of expanded segments
 * around the rewind cursor.
 */
public final class SegmentCache {

    /** Drives the engine forward one frame and returns a fresh snapshot. */
    @FunctionalInterface
    public interface Stepper {
        CompositeSnapshot stepAndCapture();
    }

    private final int intervalFrames;
    private int currentBaseFrame = -1;
    private CompositeSnapshot[] strip = null;
    private int validUpTo = -1;   // strip[validUpTo] is the last valid entry

    public SegmentCache(int intervalFrames) {
        if (intervalFrames <= 0) {
            throw new IllegalArgumentException(
                    "intervalFrames must be > 0, got " + intervalFrames);
        }
        this.intervalFrames = intervalFrames;
    }

    /** Drops any currently-cached segment. */
    public void invalidate() {
        currentBaseFrame = -1;
        strip = null;
        validUpTo = -1;
    }

    /**
     * Returns the snapshot at frame F, expanding segment [K, K+interval)
     * (where K = (F / interval) * interval) if necessary. If F lies in a
     * different segment than the currently-cached one, the cache is
     * dropped and re-expanded from the new segment's keyframe (using
     * {@code restoreKeyframe} to bring the engine back to K, then
     * {@code stepper} to advance).
     */
    public CompositeSnapshot snapshotAt(
            int frame,
            CompositeSnapshot keyframeAt,   // base keyframe of segment containing F
            int keyframeFrame,
            Runnable restoreKeyframe,       // restores engine state from keyframeAt
            Stepper stepper) {
        if (frame < keyframeFrame) {
            throw new IllegalArgumentException(
                    "frame " + frame + " < keyframe " + keyframeFrame);
        }
        // If we've cached this segment already, lookup is O(1).
        if (currentBaseFrame == keyframeFrame
                && strip != null
                && (frame - keyframeFrame) <= validUpTo) {
            return strip[frame - keyframeFrame];
        }
        // Otherwise expand the segment.
        currentBaseFrame = keyframeFrame;
        strip = new CompositeSnapshot[intervalFrames];
        strip[0] = keyframeAt;
        validUpTo = 0;
        restoreKeyframe.run();
        for (int offset = 1; offset <= (frame - keyframeFrame); offset++) {
            strip[offset] = stepper.stepAndCapture();
            validUpTo = offset;
        }
        return strip[frame - keyframeFrame];
    }
}
