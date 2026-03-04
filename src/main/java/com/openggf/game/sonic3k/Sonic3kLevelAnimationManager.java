package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;

/**
 * Combined level animation manager for Sonic 3 &amp; Knuckles.
 * Delegates to the pattern animator and palette cycler.
 */
public final class Sonic3kLevelAnimationManager implements AnimatedPatternManager, AnimatedPaletteManager {
    private final Sonic3kPatternAnimator patternAnimator;
    private final Sonic3kPaletteCycler paletteCycler;

    public Sonic3kLevelAnimationManager(RomByteReader reader, Level level,
                                        int zoneIndex, int actIndex, boolean isSkipIntro) {
        this.patternAnimator = new Sonic3kPatternAnimator(reader, level,
                zoneIndex, actIndex, isSkipIntro);
        this.paletteCycler = new Sonic3kPaletteCycler(reader, level, zoneIndex, actIndex);
    }

    @Override
    public void update() {
        if (patternAnimator != null) {
            patternAnimator.update();
        }
        if (paletteCycler != null) {
            paletteCycler.update();
        }
    }
}
