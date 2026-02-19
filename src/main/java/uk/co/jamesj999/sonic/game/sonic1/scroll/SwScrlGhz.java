package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import static uk.co.jamesj999.sonic.level.scroll.M68KMath.*;

/**
 * ROM-accurate implementation of REV01 Deform_GHZ (Green Hill Zone scroll routine).
 * Reference: s1disasm/_inc/DeformLayers (JP1).asm - Deform_GHZ
 *
 * GHZ line layout is fixed and always totals 224 lines:
 * <ul>
 *   <li>Upper cloud band: 32-d4 lines (variable with camera Y)</li>
 *   <li>Middle cloud band: 16 lines</li>
 *   <li>Lower cloud band: 16 lines</li>
 *   <li>Distant mountains: 48 lines</li>
 *   <li>Hills/waterfalls: 40 lines</li>
 *   <li>Water deformation: 72+d4 lines (variable with camera Y)</li>
 * </ul>
 *
 * REV01 GHZ uses bg3screenposx (96/256 speed) and bg2screenposx (128/256 speed),
 * plus three auto-scroll cloud counters.
 */
public class SwScrlGhz implements ZoneScrollHandler {

    private int minScrollOffset;
    private int maxScrollOffset;
    private short vscrollFactorBG;

    // Persistent BG camera positions (16.16 fixed point, matching original RAM)
    // v_bg2screenposx: accumulated at scrshiftx * 128
    private long bg2XPos;
    // v_bg3screenposx: accumulated at scrshiftx * 96
    private long bg3XPos;

    // GHZ cloud auto-scroll accumulators (16.16 fixed point).
    // High word is added to BG3 X to create layered cloud drift at idle.
    private int cloudLayer1Counter;
    private int cloudLayer2Counter;
    private int cloudLayer3Counter;

    private int lastCameraX;
    private boolean initialized = false;

    /**
     * Initialize BG camera positions.
     * BgScroll_GHZ (JP1) clears bgscreenposx, bgscreenposy, and cloud counters,
     * but bg2screenposx and bg3screenposx retain their values from BgScrollSpeed
     * (= cameraX).
     */
    public void init(int cameraX) {
        // BgScrollSpeed sets bg2screenposx = bg3screenposx = cameraX.
        // BgScroll_GHZ does NOT clear these, only bgscreenposx.
        bg2XPos = (long) cameraX << 16;
        bg3XPos = (long) cameraX << 16;
        cloudLayer1Counter = 0;
        cloudLayer2Counter = 0;
        cloudLayer3Counter = 0;
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

        // BGScroll_Block3: bg3screenposx += scrshiftx * 96
        // scrshiftx = deltaX << 8 (8.8 fixed point)
        // d4 = scrshiftx * 96 = deltaX * 96 * 256
        bg3XPos += (long) deltaX * 24576;

        // BGScroll_Block2: bg2screenposx += scrshiftx * 128
        // d0 = scrshiftx << 7 = deltaX * 128 * 256 = deltaX * 32768
        bg2XPos += (long) deltaX * 32768;

        // GHZ auto-scroll clouds (JP1 Deform_GHZ behavior):
        // +0x10000/frame, +0xC000/frame, +0x8000/frame.
        cloudLayer1Counter += 0x00010000;
        cloudLayer2Counter += 0x0000C000;
        cloudLayer3Counter += 0x00008000;

        // Extract integer pixel positions (high word of 16.16)
        int bg3X = (int) (bg3XPos >> 16);
        int bg2X = (int) (bg2XPos >> 16);

        // d4 = max(0, 0x20 - ((screenposy & 0x7FF) >> 5))
        int d4 = 0x20 - ((cameraY & 0x7FF) >> 5);
        if (d4 < 0) {
            d4 = 0;
        }
        vscrollFactorBG = (short) d4;

        // FG scroll = -screenposx (constant for all lines)
        short fgScroll = negWord(cameraX);

        int lineIndex = 0;

        // ==================== Cloud + mountain bands ====================
        short cloud1Offset = (short) (cloudLayer1Counter >> 16);
        short cloud2Offset = (short) (cloudLayer2Counter >> 16);
        short cloud3Offset = (short) (cloudLayer3Counter >> 16);

        // Upper clouds: 32-d4 lines
        lineIndex = fillBand(horizScrollBuf, lineIndex, Math.max(0, 0x20 - d4), fgScroll,
                negWord(bg3X + cloud1Offset));
        // Middle clouds: 16 lines
        lineIndex = fillBand(horizScrollBuf, lineIndex, 16, fgScroll, negWord(bg3X + cloud2Offset));
        // Lower clouds: 16 lines
        lineIndex = fillBand(horizScrollBuf, lineIndex, 16, fgScroll, negWord(bg3X + cloud3Offset));
        // Mountains: 48 lines
        lineIndex = fillBand(horizScrollBuf, lineIndex, 48, fgScroll, negWord(bg3X));

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
        // (0x48 + d4) lines, interpolating from bg2X to cameraX
        // over 0x68 (104) conceptual divisions.
        //
        // From disassembly (REV01):
        //   d2 = screenposx - bg2screenposx (distance to interpolate)
        //   increment = (d2 << 8) / 0x68 << 8 (16.16 fixed point per line)
        //   start = bg2screenposx (16.16)
        int section3Count = 0x48 + d4;
        if (section3Count > 0 && lineIndex < VISIBLE_LINES) {
            // Start value: bg2screenposx (negated for scroll direction)
            int startVal = bg2X;
            // End value: screenposx (REV01)
            int endVal = cameraX;

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

    private int fillBand(int[] horizScrollBuf, int lineIndex, int lineCount, short fgScroll, short bgScroll) {
        if (lineCount <= 0 || lineIndex >= VISIBLE_LINES) {
            return lineIndex;
        }
        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);
        int limit = Math.min(VISIBLE_LINES, lineIndex + lineCount);
        for (; lineIndex < limit; lineIndex++) {
            horizScrollBuf[lineIndex] = packed;
        }
        return lineIndex;
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
