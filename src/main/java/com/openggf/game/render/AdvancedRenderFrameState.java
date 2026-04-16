package com.openggf.game.render;

import java.util.Arrays;

/**
 * Immutable frame-local render-mode state consumed by LevelManager.
 */
public record AdvancedRenderFrameState(boolean enableForegroundHeatHaze,
                                       boolean enablePerLineForegroundScroll,
                                       short[] foregroundPerColumnVScrollOverride) {
    private static final AdvancedRenderFrameState DISABLED =
            new AdvancedRenderFrameState(false, false, null);

    public AdvancedRenderFrameState {
        foregroundPerColumnVScrollOverride = foregroundPerColumnVScrollOverride != null
                ? Arrays.copyOf(foregroundPerColumnVScrollOverride, foregroundPerColumnVScrollOverride.length)
                : null;
    }

    public static AdvancedRenderFrameState disabled() {
        return DISABLED;
    }

    @Override
    public short[] foregroundPerColumnVScrollOverride() {
        return foregroundPerColumnVScrollOverride != null
                ? Arrays.copyOf(foregroundPerColumnVScrollOverride, foregroundPerColumnVScrollOverride.length)
                : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private boolean enableForegroundHeatHaze;
        private boolean enablePerLineForegroundScroll;
        private short[] foregroundPerColumnVScrollOverride;

        public Builder enableForegroundHeatHaze() {
            this.enableForegroundHeatHaze = true;
            return this;
        }

        public Builder enablePerLineForegroundScroll() {
            this.enablePerLineForegroundScroll = true;
            return this;
        }

        public Builder setForegroundPerColumnVScrollOverride(short[] foregroundPerColumnVScrollOverride) {
            this.foregroundPerColumnVScrollOverride = foregroundPerColumnVScrollOverride != null
                    ? Arrays.copyOf(foregroundPerColumnVScrollOverride, foregroundPerColumnVScrollOverride.length)
                    : null;
            return this;
        }

        public AdvancedRenderFrameState build() {
            if (!enableForegroundHeatHaze
                    && !enablePerLineForegroundScroll
                    && foregroundPerColumnVScrollOverride == null) {
                return DISABLED;
            }
            return new AdvancedRenderFrameState(
                    enableForegroundHeatHaze,
                    enablePerLineForegroundScroll,
                    foregroundPerColumnVScrollOverride);
        }
    }
}
