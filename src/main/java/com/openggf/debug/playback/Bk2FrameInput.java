package com.openggf.debug.playback;

/**
 * Immutable, parsed input for a single BK2 frame.
 *
 * <p>BK2 movies record both controllers; this record carries P1 and P2 state
 * separately so trace-replay harnesses can drive sidekick CPU input from the
 * recorded P2 column (e.g. S3K Tails CPU's {@code Ctrl_2_logical} button-press
 * trigger paths).
 *
 * @param frameIndex     zero-based movie frame index
 * @param p1InputMask    OpenGGF input mask for P1 (direction + jump)
 * @param p1ActionMask   per-button action mask for P1 (A=0x01, B=0x02, C=0x04)
 * @param p1StartPressed whether P1 Start is pressed on this frame
 * @param p2InputMask    OpenGGF input mask for P2 (direction + jump)
 * @param p2ActionMask   per-button action mask for P2 (A=0x01, B=0x02, C=0x04)
 * @param p2StartPressed whether P2 Start is pressed on this frame
 * @param rawLine        original input-log line for diagnostics
 */
public record Bk2FrameInput(
        int frameIndex,
        int p1InputMask,
        int p1ActionMask,
        boolean p1StartPressed,
        int p2InputMask,
        int p2ActionMask,
        boolean p2StartPressed,
        String rawLine) {

    /**
     * Backwards-compatible constructor for callers that only care about P1
     * (no P2 column present, or BK2 came from a movie format that predates
     * the two-controller parser). P2 fields default to zero/false.
     */
    public Bk2FrameInput(int frameIndex, int p1InputMask, int p1ActionMask, boolean p1StartPressed, String rawLine) {
        this(frameIndex, p1InputMask, p1ActionMask, p1StartPressed, 0, 0, false, rawLine);
    }
}
