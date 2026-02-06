package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import static uk.co.jamesj999.sonic.level.scroll.M68KMath.*;

/**
 * ROM-accurate implementation of Deform_SLZ (Star Light Zone scroll routine).
 * Reference: s1disasm/_inc/DeformLayers.asm - Deform_SLZ + Deform_SLZ_2
 *
 * SLZ has a night sky background with banded parallax creating a pseudo-3D effect.
 * Uses ScrollBlock2 (accumulates both X and Y without block tracking for X).
 *
 * <p>BG camera:
 * <ul>
 *   <li>BG X: 50% speed (scrshiftx &lt;&lt; 7 = 128/256)</li>
 *   <li>BG Y: 50% speed + 0xC0 offset (BgScroll_SLZ: screenposy/2 + 0xC0)</li>
 * </ul>
 *
 * <p>Deform_SLZ_2 builds a scroll buffer with 4 bands:
 * <ol>
 *   <li>28 lines: Perspective interpolation from full speed to 1/8 speed</li>
 *   <li>5 lines: 1/8 speed</li>
 *   <li>5 lines: 1/4 speed</li>
 *   <li>30 lines: 1/2 speed</li>
 * </ol>
 * Total: 68 entries in the scroll buffer.
 *
 * <p>The main Deform_SLZ routine tiles this 68-entry buffer across the screen
 * at 16-line intervals, indexed by the BG Y scroll position.
 */
public class SwScrlSlz implements ZoneScrollHandler {

    private static final int SCROLL_BUFFER_SIZE = 68;
    private static final int LINES_PER_GROUP = 16;

    private int minScrollOffset;
    private int maxScrollOffset;
    private short vscrollFactorBG;

    // Persistent BG camera (16.16 fixed point)
    private long bgXPos;
    private long bgYPos;

    private int lastCameraX;
    private int lastCameraY;
    private boolean initialized = false;

    // Pre-allocated scroll buffer (Deform_SLZ_2 output)
    private final short[] scrollBuffer = new short[SCROLL_BUFFER_SIZE];

    public void init(int cameraX, int cameraY) {
        // BgScrollSpeed sets bgscreenposx = screenposx
        bgXPos = (long) cameraX << 16;
        // BgScroll_SLZ: bgscreenposy = screenposy / 2 + 0xC0
        bgYPos = (long) ((cameraY >> 1) + 0xC0) << 16;
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

        // ScrollBlock2: d4 = scrshiftx << 7, d5 = scrshifty << 7
        bgXPos += (long) deltaX * 128 * 256;
        bgYPos += (long) deltaY * 128 * 256;

        int bgY = (int) (bgYPos >> 16);
        vscrollFactorBG = (short) bgY;

        // FG scroll (high word of packed h-scroll entries)
        short fgScroll = negWord(cameraX);

        // ==================== Build scroll buffer (Deform_SLZ_2) ====================
        // d2 = -screenposx (negated camera X)
        int d2 = -cameraX;

        // Band 1: 28 lines (0x1B + 1) - perspective interpolation
        // From d2 (full speed) to d2>>3 (1/8 speed)
        // Increment: ((d2>>3 - d2) * 16 / 28) << 8, applied as 16.16 fixed point
        {
            int startVal = d2;
            int endVal = d2 >> 3;
            int diff = endVal - startVal;

            // disasm: d0 = (d2>>3 - d2), asl.l #4 → diff * 16, divs.w #$1C → /28
            // then asl.l #4, asl.l #8 → total shift of 12 more bits = 16.16 increment
            long increment = 0;
            if (diff != 0) {
                // (diff << 4) / 28 then << 12 = (diff << 16) / 28
                increment = ((long) diff << 16) / 28;
            }

            long current = (long) startVal << 16;
            for (int i = 0; i < 28; i++) {
                scrollBuffer[i] = (short) (current >> 16);
                current += increment;
            }
        }

        // Band 2: 5 lines - 1/8 speed
        {
            short val = (short) (d2 >> 3);
            for (int i = 28; i < 33; i++) {
                scrollBuffer[i] = val;
            }
        }

        // Band 3: 5 lines - 1/4 speed
        {
            short val = (short) (d2 >> 2);
            for (int i = 33; i < 38; i++) {
                scrollBuffer[i] = val;
            }
        }

        // Band 4: 30 lines (0x1D + 1) - 1/2 speed
        {
            short val = (short) (d2 >> 1);
            for (int i = 38; i < 68; i++) {
                scrollBuffer[i] = val;
            }
        }

        // ==================== Tile scroll buffer across screen ====================
        // The scroll buffer represents a virtual 68-entry background strip.
        // The BG Y position determines which part is visible.
        //
        // From disasm: offset = ((bgscreenposy - 0xC0) & 0x3F0) >> 3
        // Each buffer entry covers 16 screen lines (groups of 16).
        // The Y alignment within the first group is handled by the jump table.

        int yOffset = ((bgY - 0xC0) & 0x3F0) >> 3;
        int subAlign = bgY & 0xF; // Sub-16-line alignment

        int lineIndex = 0;

        // First partial group (0 to 16-subAlign lines)
        int firstGroupLines = LINES_PER_GROUP - subAlign;
        if (firstGroupLines > 0 && firstGroupLines < LINES_PER_GROUP) {
            int bufIdx = yOffset % SCROLL_BUFFER_SIZE;
            short bgScroll = scrollBuffer[bufIdx];
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll);
            int limit = Math.min(VISIBLE_LINES, firstGroupLines);
            for (; lineIndex < limit; lineIndex++) {
                horizScrollBuf[lineIndex] = packed;
            }
            yOffset++;
        }

        // Full 16-line groups
        while (lineIndex < VISIBLE_LINES) {
            int bufIdx = yOffset % SCROLL_BUFFER_SIZE;
            short bgScroll = scrollBuffer[bufIdx];
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll);

            int limit = Math.min(VISIBLE_LINES, lineIndex + LINES_PER_GROUP);
            for (; lineIndex < limit; lineIndex++) {
                horizScrollBuf[lineIndex] = packed;
            }
            yOffset++;
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
