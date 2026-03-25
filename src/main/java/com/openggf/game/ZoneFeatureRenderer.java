package com.openggf.game;

/**
 * Optional zone-specific renderer for custom visual effects.
 * Returned by {@link ZoneFeatureProvider#getFeatureRenderer()}.
 */
public interface ZoneFeatureRenderer {
    void cleanup();

    /**
     * Creates a debug render command for the art viewer.
     * Only meaningful for renderers with inspectable visual state (e.g., slot machine faces).
     *
     * @param screenX        screen X position
     * @param screenY        screen Y position
     * @param paletteTexId   palette texture ID
     * @param frameIndex     selected frame/face index
     * @return a GLCommand for debug rendering, or null if not supported
     */
    default com.openggf.graphics.GLCommand createDebugRenderCommand(
            int screenX, int screenY, int paletteTexId, int frameIndex) {
        return null;
    }

    /**
     * Returns the number of debug frames/faces available for inspection.
     * Returns 0 if this renderer does not support debug viewing.
     */
    default int getDebugFrameCount() {
        return 0;
    }

    /**
     * Returns a display name for the given debug frame index.
     * Used by debug art viewers to label individual frames.
     *
     * @param frameIndex the frame/face index
     * @return display name, or null if not available
     */
    default String getDebugFrameName(int frameIndex) {
        return null;
    }

    /**
     * Returns a reward/description string for the given debug frame index.
     * Used by debug art viewers for additional info display.
     *
     * @param frameIndex the frame/face index
     * @return description, or null if not available
     */
    default String getDebugFrameDescription(int frameIndex) {
        return null;
    }

    ZoneFeatureRenderer NONE = new ZoneFeatureRenderer() {
        @Override public void cleanup() {}
    };
}
