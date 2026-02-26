package com.openggf.game;

/**
 * Represents the sequential phases of a game's ending sequence.
 * Used by {@link EndingProvider} implementations to track progression
 * through cutscenes, credits text, demo playback, and post-credits screens.
 */
public enum EndingPhase {
    /** Initial cutscene (e.g., Tornado flyby in S2, island sequence in S1) */
    CUTSCENE,

    /** Scrolling credits text on black background */
    CREDITS_TEXT,

    /** Demo playback interleaved with credits (S1 style) or standalone */
    CREDITS_DEMO,

    /** Post-credits screen (TRY AGAIN / END in S1, or final results in S2) */
    POST_CREDITS,

    /** Entire ending sequence is complete; return to title screen */
    FINISHED
}
