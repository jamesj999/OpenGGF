package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import static uk.co.jamesj999.sonic.level.scroll.M68KMath.*;

/**
 * ROM-accurate implementation of Deform_GHZ (Green Hill Zone scroll routine).
 * Reference: s1disasm/_inc/DeformLayers.asm - Deform_GHZ
 *
 * GHZ uses three background sections with perspective interpolation:
 * <ul>
 *   <li>Section 1 (0x70-d4 lines): BG1 - clouds/sky at ~37.5% camera speed</li>
 *   <li>Section 2 (40 lines): BG2 - distant hills at 50% camera speed</li>
 *   <li>Section 3 (0x48+d4 lines): Perspective interpolation from BG2 to near-FG</li>
 * </ul>
 *
 * The three sections always total exactly 224 lines.
 *
 * BG camera speeds (from ScrollBlock1 / ScrollBlock4):
 * <ul>
 *   <li>BG1 X: scrshiftx * 96 (accumulates at 96/256 = 37.5% of camera speed)</li>
 *   <li>BG2 X: scrshiftx * 128 (accumulates at 128/256 = 50% of camera speed)</li>
 * </ul>
 *
 * BG2 Y position: 0x26 - ((cameraY &amp; 0x7FF) &gt;&gt; 5)
 * This creates a depth effect where the background rises as the camera descends.
 */
public class SwScrlGhz implements ZoneScrollHandler {

    private int minScrollOffset;
    private int maxScrollOffset;
    private short vscrollFactorBG;

    // Persistent BG camera positions (16.16 fixed point, matching original RAM)
    // v_bgscreenposx (BG1): accumulated via ScrollBlock1 at rate scrshiftx * 96
    private long bg1XPos;
    // v_bg2screenposx (BG2): accumulated via ScrollBlock4 at rate scrshiftx * 128
    private long bg2XPos;

    private int lastCameraX;
    private boolean initialized = false;

    /**
     * Initialize BG camera positions.
     * Replicates BgScrollSpeed initial setup: bgscreenposx = screenposx.
     */
    public void init(int cameraX) {
        // BgScrollSpeed sets bgscreenposx = bg2screenposx = screenposx (high word)
        bg1XPos = (long) cameraX << 16;
        bg2XPos = (long) cameraX << 16;
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

        // Compute camera movement delta (equivalent to v_scrshiftx)
        int deltaX = cameraX - lastCameraX;
        lastCameraX = cameraX;

        // ScrollBlock1: bg1XPos += scrshiftx * 96
        // scrshiftx = deltaX << 8 (8.8 fixed point)
        // d4 = scrshiftx * 96 = deltaX * 96 * 256 = deltaX * 24576
        bg1XPos += (long) deltaX * 24576;

        // ScrollBlock4: bg2XPos += scrshiftx * 128
        // d0 = scrshiftx << 7 = deltaX * 128 * 256 = deltaX * 32768
        bg2XPos += (long) deltaX * 32768;

        // Extract integer pixel positions (high word of 16.16)
        int bg1X = (int) (bg1XPos >> 16);
        int bg2X = (int) (bg2XPos >> 16);

        // bg2screenposy = 0x26 - (screenposy & 0x7FF) >> 5
        int bg2Y = 0x26 - ((cameraY & 0x7FF) >> 5);
        vscrollFactorBG = (short) bg2Y;

        // d4 in the disassembly is bg2screenposy, used to split sections
        int d4 = bg2Y;

        // FG scroll = -screenposx (constant for all lines)
        short fgScroll = negWord(cameraX);

        int lineIndex = 0;

        // ==================== Section 1: Sky/BG1 ====================
        // (0x70 - d4) lines with BG = -bg1X
        int section1Count = 0x70 - d4;
        if (section1Count > 0) {
            short bgScroll = negWord(bg1X);
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll);
            int limit = Math.min(VISIBLE_LINES, lineIndex + section1Count);
            for (; lineIndex < limit; lineIndex++) {
                horizScrollBuf[lineIndex] = packed;
            }
        }

        // ==================== Section 2: BG2 (distant hills) ====================
        // 40 lines (0x28) with BG = -bg2X
        {
            short bgScroll = negWord(bg2X);
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll);
            int limit = Math.min(VISIBLE_LINES, lineIndex + 40);
            for (; lineIndex < limit; lineIndex++) {
                horizScrollBuf[lineIndex] = packed;
            }
        }

        // ==================== Section 3: Perspective interpolation ====================
        // (0x48 + d4) lines, interpolating from bg2X to (cameraX - 0x200)
        // over 0x68 (104) conceptual divisions.
        //
        // From disassembly:
        //   d2 = (screenposx - 0x200) - bg2screenposx (distance to interpolate)
        //   increment = (d2 << 8) / 0x68 << 8 (16.16 fixed point per line)
        //   start = bg2screenposx (16.16)
        int section3Count = 0x48 + d4;
        if (section3Count > 0 && lineIndex < VISIBLE_LINES) {
            // Start value: bg2screenposx (negated for scroll direction)
            int startVal = bg2X;
            // End value: screenposx - 0x200
            int endVal = cameraX - 0x200;

            // Calculate per-line increment in 16.16 fixed point
            int diff = endVal - startVal;
            long increment16 = 0;
            if (diff != 0) {
                // Matching disasm: (diff << 8) / 0x68, then << 8
                // Combined: (diff << 16) / 0x68
                increment16 = ((long) diff << 16) / 0x68;
            }

            // Start in 16.16 fixed point
            long currentVal = (long) startVal << 16;

            int limit = Math.min(VISIBLE_LINES, lineIndex + section3Count);
            for (; lineIndex < limit; lineIndex++) {
                int bgPixel = (int) (currentVal >> 16);
                short bgScroll = negWord(bgPixel);

                horizScrollBuf[lineIndex] = packScrollWords(fgScroll, bgScroll);

                int offset = (bgScroll & 0xFFFF) - (fgScroll & 0xFFFF);
                if ((short) offset < minScrollOffset) minScrollOffset = (short) offset;
                if ((short) offset > maxScrollOffset) maxScrollOffset = (short) offset;

                currentVal += increment16;
            }
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
