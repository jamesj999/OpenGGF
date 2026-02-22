package com.openggf.game.sonic1.scroll;

import com.openggf.level.scroll.ZoneScrollHandler;
import static com.openggf.level.scroll.M68KMath.*;

/**
 * ROM-accurate implementation of Deform_SYZ (Spring Yard Zone scroll routine).
 * Reference: s1disasm/_inc/DeformLayers.asm - Deform_SYZ
 *
 * SYZ has uniform horizontal scrolling with a custom vertical parallax multiplier:
 * <ul>
 *   <li>BG X: 25% speed (scrshiftx &lt;&lt; 6 = 64/256)</li>
 *   <li>BG Y: ~18.75% speed (scrshifty * 48 = 48/256)</li>
 *   <li>H-scroll: uniform (all 224 lines same)</li>
 * </ul>
 *
 * From disassembly:
 * <pre>
 *   d5 = scrshifty &lt;&lt; 4          ; * 16
 *   d1 = d5                        ; save
 *   d5 = d5 &lt;&lt; 1                  ; * 32
 *   d5 = d5 + d1                   ; * 48
 * </pre>
 *
 * BgScroll_SYZ initial setup:
 * <pre>
 *   bgscreenposy = (screenposy &lt;&lt; 4 * 3) &gt;&gt; 8 = screenposy * 48 / 256
 * </pre>
 */
public class SwScrlSyz implements ZoneScrollHandler {

    private int minScrollOffset;
    private int maxScrollOffset;
    private short vscrollFactorBG;

    // Persistent BG camera (16.16 fixed point)
    private long bgXPos;
    private long bgYPos;

    private int lastCameraX;
    private int lastCameraY;
    private boolean initialized = false;

    public void init(int cameraX, int cameraY) {
        // BgScrollSpeed: bgscreenposx = screenposx
        bgXPos = (long) cameraX << 16;
        // BgScroll_SYZ: bgscreenposy = (screenposy << 4) * 3 >> 8 = screenposy * 48 / 256
        int initialBgY = (cameraY * 48) >> 8;
        bgYPos = (long) initialBgY << 16;
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        initialized = true;
    }

    @Override
    public void update(int[] horizScrollBuf,
            int cameraX,
            int cameraY,
            int frameCounter,
            int actId) {
        if (!initialized) {
            init(cameraX, cameraY);
        }

        minScrollOffset = Integer.MAX_VALUE;
        maxScrollOffset = Integer.MIN_VALUE;

        int deltaX = cameraX - lastCameraX;
        int deltaY = cameraY - lastCameraY;
        lastCameraX = cameraX;
        lastCameraY = cameraY;

        // ScrollBlock1: d4 = scrshiftx << 6 = deltaX * 64 * 256
        bgXPos += (long) deltaX * 64 * 256;

        // d5 = scrshifty * 48 = deltaY * 48 * 256
        bgYPos += (long) deltaY * 48 * 256;

        int bgX = (int) (bgXPos >> 16);
        int bgY = (int) (bgYPos >> 16);

        vscrollFactorBG = (short) bgY;

        // Uniform h-scroll
        short fgScroll = negWord(cameraX);
        short bgScroll = negWord(bgX);

        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);

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
