package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import static uk.co.jamesj999.sonic.level.scroll.M68KMath.*;

/**
 * ROM-accurate implementation of Deform_MZ (Marble Zone scroll routine).
 * Reference: s1disasm/_inc/DeformLayers.asm - Deform_MZ
 *
 * MZ has a pseudo-3D effect where the background is static at the bottom of the
 * level but rises as Sonic climbs higher:
 * <ul>
 *   <li>BG X: scrolls at 75% of camera speed (scrshiftx * 192 = 192/256)</li>
 *   <li>BG Y: no per-frame accumulation (d5 = 0)</li>
 *   <li>BG2 Y: rises when cameraY &gt; 0x1C8 (formula: 0x200 + (cameraY - 0x1C8) * 3/4)</li>
 *   <li>H-scroll: uniform (all 224 lines same)</li>
 * </ul>
 *
 * From BgScrollSpeed: BgScroll_MZ is a NOP (rts), meaning BG Y is not set
 * from camera Y initially. Instead, bg2screenposy is computed each frame.
 */
public class SwScrlMz implements ZoneScrollHandler {

    private int minScrollOffset;
    private int maxScrollOffset;
    private short vscrollFactorBG;

    // Persistent BG X position (16.16 fixed point)
    // ScrollBlock1: d4 = scrshiftx * 192 (asl.l #6, d1=d4, asl.l #1 d4, add d1)
    private long bgXPos;

    private int lastCameraX;
    private boolean initialized = false;

    public void init(int cameraX) {
        // BgScrollSpeed sets bgscreenposx = screenposx
        bgXPos = (long) cameraX << 16;
        lastCameraX = cameraX;
        initialized = true;
    }

    @Override
    public void update(int[] horizScrollBuf,
            int cameraX,
            int cameraY,
            int frameCounter,
            int actId) {
        if (!initialized) {
            init(cameraX);
        }

        minScrollOffset = Integer.MAX_VALUE;
        maxScrollOffset = Integer.MIN_VALUE;

        // Compute delta
        int deltaX = cameraX - lastCameraX;
        lastCameraX = cameraX;

        // ScrollBlock1: d4 = scrshiftx * 192
        // scrshiftx * 64 * 3 = deltaX * 256 * 192 = deltaX * 49152
        bgXPos += (long) deltaX * 49152;

        int bgX = (int) (bgXPos >> 16);

        // bg2screenposy calculation:
        // If screenposy > 0x1C8: bg2Y = 0x200 + (screenposy - 0x1C8) * 3/4
        // Otherwise: bg2Y = 0x200
        int bg2Y = 0x200;
        int yOffset = cameraY - 0x1C8;
        if (yOffset > 0) {
            // d1 = yOffset, d2 = yOffset, d1 = yOffset*2 + yOffset = yOffset*3, asr #2 = yOffset*3/4
            bg2Y += (yOffset * 3) >> 2;
        }

        vscrollFactorBG = (short) bg2Y;

        // FG and BG scroll values
        short fgScroll = negWord(cameraX);
        short bgScroll = negWord(bgX);

        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);

        // Uniform: all 224 lines have the same value
        for (int i = 0; i < VISIBLE_LINES; i++) {
            horizScrollBuf[i] = packed;
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
