package com.openggf.debug.playback;

/**
 * Immutable, parsed input for a single BK2 frame.
 *
 * @param frameIndex     zero-based movie frame index
 * @param p1InputMask    OpenGGF input mask for P1 (direction + jump)
 * @param p1StartPressed whether P1 Start is pressed on this frame
 * @param rawLine        original input-log line for diagnostics
 */
public record Bk2FrameInput(int frameIndex, int p1InputMask, boolean p1StartPressed, String rawLine) {
}
