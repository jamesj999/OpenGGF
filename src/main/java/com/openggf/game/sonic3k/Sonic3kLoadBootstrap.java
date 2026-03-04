package com.openggf.game.sonic3k;

/**
 * Represents level-load bootstrap behavior used by the S3K loader/event pipeline.
 *
 * <p>This exists to support ROM-structured transitions where the level starts with one
 * resource profile and then pivots to another profile after scripted events.
 *
 * <p>The optional {@code introStartPosition} carries the player spawn coordinates
 * when a zone intro sequence is active. This is data-driven per zone so adding
 * a new intro is just one entry in {@link Sonic3kBootstrapResolver}.
 */
public record Sonic3kLoadBootstrap(Mode mode, int[] introStartPosition) {
    public static final Sonic3kLoadBootstrap NORMAL = new Sonic3kLoadBootstrap(Mode.NORMAL, null);

    public enum Mode {
        NORMAL,      // No intro for this zone, or zone doesn't have one
        INTRO,       // Intro active — use introStartPosition for player spawn
        SKIP_INTRO   // Zone has an intro but it's being skipped
    }

    public boolean hasIntroStartPosition() {
        return mode == Mode.INTRO && introStartPosition != null;
    }

    public boolean isSkipIntro() {
        return mode == Mode.SKIP_INTRO;
    }
}
