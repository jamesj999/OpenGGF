package com.openggf.level.rings;

import com.openggf.graphics.GraphicsManager;
import com.openggf.level.render.SpriteSheet;
import com.openggf.level.Pattern;

import java.util.List;

/**
 * Holds ring patterns and frame mappings.
 */
public class RingSpriteSheet implements SpriteSheet<RingFrame> {
    private final Pattern[] patterns;
    private final List<RingFrame> frames;
    private final int paletteIndex;
    private final int frameDelay;
    private final int sparkleFrameDelay;
    private final int spinFrameCount;
    private final int sparkleFrameCount;

    public RingSpriteSheet(Pattern[] patterns, List<RingFrame> frames, int paletteIndex, int frameDelay,
                           int spinFrameCount, int sparkleFrameCount) {
        this(patterns, frames, paletteIndex, frameDelay, frameDelay, spinFrameCount, sparkleFrameCount);
    }

    /**
     * @param frameDelay         VBlanks per spin animation frame (SynchroAnimate rate)
     * @param sparkleFrameDelay  VBlanks per sparkle animation frame (AnimateSprite rate).
     *                           In S1, spin uses 8 (timer=7) but sparkle uses 6 (Ani_Ring delay=5).
     */
    public RingSpriteSheet(Pattern[] patterns, List<RingFrame> frames, int paletteIndex, int frameDelay,
                           int sparkleFrameDelay, int spinFrameCount, int sparkleFrameCount) {
        this.patterns = patterns;
        this.frames = frames;
        this.paletteIndex = paletteIndex;
        this.frameDelay = frameDelay;
        this.sparkleFrameDelay = sparkleFrameDelay;
        int totalFrames = frames != null ? frames.size() : 0;
        int safeSpin = Math.max(0, Math.min(spinFrameCount, totalFrames));
        int safeSparkle = Math.max(0, Math.min(sparkleFrameCount, totalFrames - safeSpin));
        this.spinFrameCount = safeSpin;
        this.sparkleFrameCount = safeSparkle;
    }

    public Pattern[] getPatterns() {
        return patterns;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public int getSpinFrameCount() {
        return spinFrameCount;
    }

    public int getSparkleFrameCount() {
        return sparkleFrameCount;
    }

    public int getSparkleStartIndex() {
        return spinFrameCount;
    }

    public RingFrame getFrame(int index) {
        return frames.get(index);
    }

    public int getPaletteIndex() {
        return paletteIndex;
    }

    public int getFrameDelay() {
        return frameDelay;
    }

    /**
     * Returns the VBlanks-per-frame rate for sparkle animation.
     * <p>
     * In the ROM, the sparkle animation uses {@code AnimateSprite} with the
     * {@code Ani_Ring} script, which has its own delay byte (5 in S1 = 6 VBlanks).
     * This is different from the spin animation rate driven by {@code SynchroAnimate}
     * (timer=7 in S1 = 8 VBlanks). When not explicitly set, defaults to {@link #getFrameDelay()}.
     */
    public int getSparkleFrameDelay() {
        return sparkleFrameDelay;
    }

    public void cachePatterns(GraphicsManager graphicsManager, int basePatternIndex) {
        if (!graphicsManager.isGlInitialized()) {
            return;
        }
        for (int i = 0; i < patterns.length; i++) {
            graphicsManager.cachePatternTexture(patterns[i], basePatternIndex + i);
        }
    }
}
