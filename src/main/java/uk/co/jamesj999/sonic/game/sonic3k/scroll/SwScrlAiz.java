package uk.co.jamesj999.sonic.game.sonic3k.scroll;

import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import static uk.co.jamesj999.sonic.level.scroll.M68KMath.*;

/**
 * Stub scroll handler for Angel Island Zone (AIZ).
 * Provides simple parallax: BG scrolls at half camera speed horizontally.
 * This is a placeholder until the full ROM-accurate routine is implemented.
 */
public class SwScrlAiz implements ZoneScrollHandler {

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

        // During intro, ROM AIZ1_IntroDeform uses Events_fg_1 / 2 for BG parallax.
        // Once intro is done (introScrollOffset == 0), normal half-speed parallax.
        int introOffset = AizPlaneIntroInstance.getIntroScrollOffset();
        short bgScroll;
        if (introOffset < 0) {
            bgScroll = asrWord(introOffset, 1);
        } else {
            bgScroll = asrWord(fgScroll, 1);
        }

        vscrollFactorBG = 0;

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
