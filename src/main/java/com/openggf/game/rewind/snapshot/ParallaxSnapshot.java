package com.openggf.game.rewind.snapshot;

/**
 * Snapshot of {@link com.openggf.level.ParallaxManager} per-frame mutable
 * state. Static zone configuration (zone/act, providerLoaded, vscrollFactor*)
 * is excluded — it is set at zone load and treated as immutable for rewind.
 */
public record ParallaxSnapshot(
        int currentShakeOffsetX,
        int currentShakeOffsetY,
        int cachedBgCameraX,
        int cachedBgPeriodWidth,
        int[] hScroll,
        short[] vScrollPerLineBG,
        short[] vScrollPerColumnBG,
        short[] vScrollPerColumnFG,
        boolean hasPerLineVScrollBG,
        boolean hasPerColumnVScrollBG,
        boolean hasPerColumnVScrollFG,
        int minScroll,
        int maxScroll,
        short vscrollFactorFG,
        short vscrollFactorBG
) {}
