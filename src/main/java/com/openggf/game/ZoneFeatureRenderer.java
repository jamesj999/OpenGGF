package com.openggf.game;

/**
 * Optional zone-specific renderer for custom visual effects.
 * Returned by {@link ZoneFeatureProvider#getFeatureRenderer()}.
 */
public interface ZoneFeatureRenderer {
    void cleanup();

    ZoneFeatureRenderer NONE = new ZoneFeatureRenderer() {
        @Override public void cleanup() {}
    };
}
