package com.openggf.game;

/**
 * Mode for level-load profile execution.
 */
public enum LevelLoadMode {
    /**
     * Full level load path (default).
     */
    FULL,

    /**
     * In-place reload path that preserves runtime player/checkpoint state.
     */
    SEAMLESS_RELOAD
}
