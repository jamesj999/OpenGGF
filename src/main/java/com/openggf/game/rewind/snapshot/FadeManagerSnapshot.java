package com.openggf.game.rewind.snapshot;

import com.openggf.graphics.FadeManager;

/**
 * Immutable capture of FadeManager state for rewind snapshots.
 * Captures fade phase, frame counter, color values, and hold duration.
 * Note: Callbacks (onFadeComplete) are transient and not captured.
 */
public record FadeManagerSnapshot(
        FadeManager.FadeState state,
        int frameCount,
        float fadeR,
        float fadeG,
        float fadeB,
        float fadeAlpha,
        FadeManager.FadeType fadeType,
        int holdDuration,
        int holdFrameCount,
        int effectiveFPC,
        float effectiveIncrement,
        int effectiveDuration) {
}
