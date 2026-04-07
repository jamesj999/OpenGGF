package com.openggf.game.sonic3k.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;

import static com.openggf.level.scroll.M68KMath.negWord;
import static com.openggf.level.scroll.M68KMath.packScrollWords;

/**
 * Pachinko / Glowing Spheres bonus stage scroll handler.
 *
 * <p>ROM reference: {@code Pachinko_BGScroll} in sonic3k.asm.
 * Background vertical scroll follows camera Y at 1/8 speed. Background horizontal
 * scroll uses an anchored 5/8 parallax curve around screen center offset $A0.
 */
public class SwScrlPachinko extends AbstractZoneScrollHandler {
    private static final int VISIBLE_LINES = 224;

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        resetScrollTracking();

        short fgScroll = negWord(cameraX);
        int bgWorldX = 0x60 + (((cameraX - 0xA0) * 5) >> 3);
        short bgScroll = negWord(bgWorldX);
        vscrollFactorBG = (short) (cameraY >> 3);

        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);
        for (int line = 0; line < VISIBLE_LINES; line++) {
            horizScrollBuf[line] = packed;
        }
    }
}
