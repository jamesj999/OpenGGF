package com.openggf.game.sonic3k.scroll;

import com.openggf.level.scroll.ZoneScrollHandler;
import static com.openggf.level.scroll.M68KMath.*;

/**
 * Default fallback scroll handler for Sonic 3&K zones that don't have
 * a zone-specific implementation yet. Provides simple parallax:
 * BG X at 1/4 FG speed, BG Y at 1/4 FG speed.
 */
public class SwScrlS3kDefault implements ZoneScrollHandler {

    private short vscrollFactorBG;
    private int minScrollOffset;
    private int maxScrollOffset;

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        minScrollOffset = Integer.MAX_VALUE;
        maxScrollOffset = Integer.MIN_VALUE;

        short fgScroll = negWord(cameraX);
        short bgScroll = asrWord(fgScroll, 2); // BG at 1/4 FG speed

        vscrollFactorBG = asrWord(cameraY, 2); // BG Y at 1/4 FG speed

        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);

        for (int line = 0; line < VISIBLE_LINES; line++) {
            horizScrollBuf[line] = packed;
        }
    }

    private void trackOffset(short fgScroll, short bgScroll) {
        int offset = bgScroll - fgScroll;
        if (offset < minScrollOffset) {
            minScrollOffset = offset;
        }
        if (offset > maxScrollOffset) {
            maxScrollOffset = offset;
        }
    }

    @Override
    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }

    @Override
    public int getMinScrollOffset() {
        return minScrollOffset;
    }

    @Override
    public int getMaxScrollOffset() {
        return maxScrollOffset;
    }
}
