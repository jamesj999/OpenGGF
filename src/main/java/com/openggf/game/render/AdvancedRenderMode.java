package com.openggf.game.render;

/**
 * Runtime-owned contributor for frame-local render-mode state.
 */
public interface AdvancedRenderMode {
    /**
     * Stable identifier for debugging and tests.
     */
    String id();

    /**
     * Contributes render-mode state for the current frame.
     */
    void contribute(AdvancedRenderModeContext context, AdvancedRenderFrameState.Builder builder);
}
