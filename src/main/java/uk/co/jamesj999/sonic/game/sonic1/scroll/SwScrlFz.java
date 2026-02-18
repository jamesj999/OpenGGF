package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import static uk.co.jamesj999.sonic.level.scroll.M68KMath.*;

/**
 * Final Zone scroll routine.
 * Reference: s1disasm/_inc/DeformLayers (JP1).asm - Deform_SBZ / Deform_SBZ2
 *            s1disasm/_inc/LevelSizeLoad &amp; BgScrollSpeed.asm - BgScroll_SBZ
 *
 * In the ROM, Final Zone has v_zone = 5 (SBZ) and v_act = 2. Deform_SBZ
 * checks act and branches to Deform_SBZ2 (simple uniform scroll) when
 * act != 0. This is the same path used by SBZ Act 2.
 *
 * Deform_SBZ2:
 * <ul>
 *   <li>BG X: 25% speed (scrshiftx &lt;&lt; 6 = 64/256)</li>
 *   <li>BG Y: 12.5% speed (scrshifty &lt;&lt; 4 &lt;&lt; 1 = 32/256)</li>
 *   <li>H-scroll: uniform (all 224 lines same)</li>
 * </ul>
 *
 * BgScroll_SBZ initial setup (16.16 fixed point):
 * <pre>
 *   bgscreenposy.l = (word)(screenposy &lt;&lt; 4) &lt;&lt; 1
 *   integer part = high word of result
 * </pre>
 */
public class SwScrlFz implements ZoneScrollHandler {

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
        // BgScrollSpeed default: bgscreenposx = screenposx (word write to high word)
        bgXPos = (long) cameraX << 16;
        // BgScroll_SBZ in 68k (16.16 fixed point):
        //   move.w screenposy,d0  ; d0.w = cameraY, upper word = 0
        //   asl.w  #4,d0          ; word-only shift left 4
        //   asl.l  #1,d0          ; long shift left 1
        //   move.l d0,bgscreenposy ; store full 32-bit as 16.16
        // Integer part = high word of result (NOT cameraY*32/256)
        int wordShifted = (cameraY << 4) & 0xFFFF;  // asl.w #4
        bgYPos = ((long) wordShifted) << 1;          // asl.l #1 → 16.16 fixed point
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

        // Deform_SBZ2: d4 = scrshiftx << 6 = deltaX * 64 * 256
        bgXPos += (long) deltaX * 64 * 256;

        // Deform_SBZ2: d5 = scrshifty << 5 = deltaY * 32 * 256
        bgYPos += (long) deltaY * 32 * 256;

        int bgX = (int) (bgXPos >> 16);
        int bgY = (int) (bgYPos >> 16);

        vscrollFactorBG = (short) bgY;

        // Uniform h-scroll (Deform_SBZ2: all 224 lines same)
        short fgScroll = negWord(cameraX);
        short bgScroll = negWord(bgX);

        int packed = packScrollWords(fgScroll, bgScroll);
        int offset = bgScroll - fgScroll;
        if (offset < minScrollOffset) minScrollOffset = offset;
        if (offset > maxScrollOffset) maxScrollOffset = offset;

        for (int i = 0; i < VISIBLE_LINES; i++) {
            horizScrollBuf[i] = packed;
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
