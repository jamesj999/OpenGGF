package com.openggf.game.sonic3k.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import static com.openggf.level.scroll.M68KMath.*;

/**
 * Default fallback scroll handler for Sonic 3&K zones that don't have
 * a zone-specific implementation yet. Provides simple parallax:
 * BG X at 1/4 FG speed, BG Y at 1/4 FG speed.
 */
public class SwScrlS3kDefault extends AbstractZoneScrollHandler {

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        resetScrollTracking();

        short fgScroll = negWord(cameraX);
        short bgScroll = asrWord(fgScroll, 2); // BG at 1/4 FG speed

        vscrollFactorBG = asrWord(cameraY, 2); // BG Y at 1/4 FG speed

        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);

        for (int line = 0; line < VISIBLE_LINES; line++) {
            horizScrollBuf[line] = packed;
        }
    }

}
