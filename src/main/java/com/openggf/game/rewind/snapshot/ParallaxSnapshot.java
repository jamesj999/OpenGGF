package com.openggf.game.rewind.snapshot;

/**
 * Snapshot of {@link com.openggf.level.ParallaxManager} per-frame mutable
 * state. Static zone configuration (zone/act, providerLoaded, vscrollFactor*)
 * is excluded — it is set at zone load and treated as immutable for rewind.
 */
public record ParallaxSnapshot() {
    public ParallaxSnapshot(
            int currentShakeOffsetX,
            int currentShakeOffsetY,
            int cachedBgCameraX,
            int cachedBgPeriodWidth,
            boolean hasPerLineVScrollBG,
            boolean hasPerColumnVScrollBG,
            boolean hasPerColumnVScrollFG,
            int minScroll,
            int maxScroll,
            short vscrollFactorFG,
            short vscrollFactorBG) {
        this();
    }

    public ParallaxSnapshot(
            int currentShakeOffsetX,
            int currentShakeOffsetY,
            int cachedBgCameraX,
            int cachedBgPeriodWidth,
            int[] ignoredHScroll,
            short[] ignoredVScrollPerLineBG,
            short[] ignoredVScrollPerColumnBG,
            short[] ignoredVScrollPerColumnFG,
            boolean hasPerLineVScrollBG,
            boolean hasPerColumnVScrollBG,
            boolean hasPerColumnVScrollFG,
            int minScroll,
            int maxScroll,
            short vscrollFactorFG,
            short vscrollFactorBG) {
        this(currentShakeOffsetX, currentShakeOffsetY, cachedBgCameraX, cachedBgPeriodWidth,
                hasPerLineVScrollBG, hasPerColumnVScrollBG, hasPerColumnVScrollFG,
                minScroll, maxScroll, vscrollFactorFG, vscrollFactorBG);
    }
}
