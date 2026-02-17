package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.animation.AnimatedPaletteManager;
import uk.co.jamesj999.sonic.level.animation.AnimatedPatternManager;

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
