package com.openggf.game.sonic2.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.M68KMath;
import com.openggf.level.scroll.compose.ScrollEffectComposer;

/**
 * ROM-accurate implementation of SwScrl_MTZ (Metropolis Zone scroll routine).
 * Reference: s2.asm SwScrl_MTZ
 *
 * MTZ uses a simple uniform parallax:
 * - All 224 scanlines share the same BG scroll (Camera_X_pos >> 3, i.e. 1/8 speed)
 * - BG Y scrolls at 1/4 camera speed (Camera_Y_pos >> 2)
 *
 * The original routine uses Camera_X_pos_diff << 5 which tracks at 1/8 speed,
 * matching the asr.w #3 shortcut used here.
 */
public class SwScrlMtz extends AbstractZoneScrollHandler {

    private final BackgroundCamera bgCamera;
    private final ScrollEffectComposer composer = new ScrollEffectComposer();

    public SwScrlMtz(BackgroundCamera bgCamera) {
        this.bgCamera = bgCamera;
    }

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        resetScrollTracking();
        composer.reset();

        short fgScroll = M68KMath.negWord(cameraX);
        // BG X scrolls at 1/8 camera speed
        int offset = cameraX - (cameraX >> 3);
        short bgScroll = (short) (fgScroll + offset);

        // MTZ BG Y scrolls at 1/4 camera speed
        composer.setVscrollFactorBG((short) (cameraY >> 2));
        composer.fillPackedScrollWords(0, M68KMath.VISIBLE_LINES, fgScroll, bgScroll);

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        vscrollFactorBG = composer.getVscrollFactorBG();
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
    }
}
