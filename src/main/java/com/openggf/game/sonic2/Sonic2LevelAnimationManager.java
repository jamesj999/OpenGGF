package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.level.Level;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;

import java.io.IOException;

/**
 * Combined level animation manager for Sonic 2.
 * Delegates to pattern animation and palette cycling helpers.
 */
public final class Sonic2LevelAnimationManager implements AnimatedPatternManager, AnimatedPaletteManager {
    private final Sonic2PatternAnimator patternAnimator;
    private final Sonic2PaletteCycler paletteCycler;

    public Sonic2LevelAnimationManager(Rom rom, Level level, int zoneIndex) throws IOException {
        this.patternAnimator = new Sonic2PatternAnimator(rom, level, zoneIndex);
        this.paletteCycler = new Sonic2PaletteCycler(rom, level, zoneIndex);
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
