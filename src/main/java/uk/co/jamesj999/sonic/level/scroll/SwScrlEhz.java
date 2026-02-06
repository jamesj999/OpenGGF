package uk.co.jamesj999.sonic.level.scroll;

import static uk.co.jamesj999.sonic.level.scroll.M68KMath.*;

/**
 * ROM-accurate implementation of SwScrl_EHZ (Emerald Hill Zone scroll routine).
 * Reference: s2.asm SwScrl_EHZ at ROM $C57E
 *
 * EHZ uses banded per-scanline parallax with:
 * - Sky region (static)
 * - Cloud region (slow parallax)
 * - Water surface (now treated as cloud extension, no shimmer)
 * - Hill bands (graduated parallax speeds)
 * - Bottom gradient (interpolated parallax for ground depth)
 *
 * Band structure (line counts):
 * 22 lines: BG = 0 (sky)
 * 58 lines: BG = d2 >> 6 (far clouds)
 * 21 lines: BG = d2 >> 6 (water/clouds extension)
 * 11 lines: BG = 0 (gap)
 * 16 lines: BG = d2 >> 4 (near hills)
 * 16 lines: BG = (d2 >> 4) * 1.5 (nearer hills)
 * 15 lines: gradient 0.25 -> 0.50 speed
 * 18 lines: gradient 0.50 -> 0.75 speed
 * 45 lines: gradient 0.75 -> 1.00 speed
 * Total: 222 lines (last 2 lines intentionally unwritten - original bug)
 */
public class SwScrlEhz implements ZoneScrollHandler {

    private final ParallaxTables tables;

    // Scroll tracking for LevelManager bounds
    private int minScrollOffset;
    private int maxScrollOffset;
    private short vscrollFactorBG;

    // Persistent ripple counter, decrements every 8 frames (matches TempArray_LayerDef)
    private int ripplePhase = 0;

    public SwScrlEhz(ParallaxTables tables) {
        this.tables = tables;
    }

    @Override
    public void update(int[] horizScrollBuf,
            int cameraX,
            int cameraY,
            int frameCounter,
            int actId) {
        minScrollOffset = Integer.MAX_VALUE;
        maxScrollOffset = Integer.MIN_VALUE;

        // d2 = -Camera_X_pos (FG scroll, constant for all lines)
        short d2 = negWord(cameraX);

        // FG scroll word is constant for all lines
        short fgScroll = d2;

        // Vscroll_Factor_BG for EHZ is 0 (BG doesn't scroll vertically independently)
        vscrollFactorBG = 0;

        int lineIndex = 0;

        // ==================== Band 1: Sky (22 lines) ====================
        // BG = 0, FG = d2
        {
            short bgScroll = 0;
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll); // Track once per constant region
            int limit = Math.min(VISIBLE_LINES, lineIndex + 22);
            for (; lineIndex < limit; lineIndex++) {
                horizScrollBuf[lineIndex] = packed;
            }
        }

        // ==================== Band 2: Far Clouds (58 lines) ====================
        // BG = d2 >> 6, FG = d2
        {
            short bgScroll = asrWord(d2, 6);
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll);
            int limit = Math.min(VISIBLE_LINES, lineIndex + 58);
            for (; lineIndex < limit; lineIndex++) {
                horizScrollBuf[lineIndex] = packed;
            }
        }

        // ==================== Band 3: Water Surface (21 lines) ====================
        // Water surface with ripple effect using SwScrl_RippleData
        // Reference: s2.asm lines 15285-15302
        {
            short baseBgScroll = asrWord(d2, 6);
            int limit = Math.min(VISIBLE_LINES, lineIndex + 21);

            // Ripple counter decrements every 8 frames (matches subq.w #1,(TempArray_LayerDef))
            if ((frameCounter & 7) == 0) {
                ripplePhase--;
            }
            int rippleIndex = ripplePhase & 0x1F;

            for (; lineIndex < limit; lineIndex++) {
                int wobble = tables.getRippleSigned(rippleIndex & 0x1F);
                short bgScroll = (short) (baseBgScroll + wobble);

                horizScrollBuf[lineIndex] = packScrollWords(fgScroll, bgScroll);
                trackOffset(fgScroll, bgScroll);

                rippleIndex++;
            }
        }

        // ==================== Band 4: Gap (11 lines) ====================
        // BG = 0, FG = d2
        {
            short bgScroll = 0;
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll);
            int limit = Math.min(VISIBLE_LINES, lineIndex + 11);
            for (; lineIndex < limit; lineIndex++) {
                horizScrollBuf[lineIndex] = packed;
            }
        }

        // ==================== Band 5: Near Hills (16 lines) ====================
        // BG = d2 >> 4, FG = d2
        {
            short bgScroll = asrWord(d2, 4);
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll);
            int limit = Math.min(VISIBLE_LINES, lineIndex + 16);
            for (; lineIndex < limit; lineIndex++) {
                horizScrollBuf[lineIndex] = packed;
            }
        }

        // ==================== Band 6: Nearer Hills (16 lines) ====================
        // BG = (d2 >> 4) * 1.5, FG = d2
        {
            short d0 = asrWord(d2, 4);
            short d3 = asrWord(d0, 1);
            short bgScroll = (short) (d0 + d3);
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll);
            int limit = Math.min(VISIBLE_LINES, lineIndex + 16);
            for (; lineIndex < limit; lineIndex++) {
                horizScrollBuf[lineIndex] = packed;
            }
        }

        // ==================== Bottom Gradient Region ====================
        // ROM-accurate implementation matching s2.asm lines 15330-15380.
        // Uses 68k-style 16.16 fixed-point with swapped word order:
        //   d3 normal: integer in low word, fraction in high word
        //   d3 swapped: fraction in low word, integer in high word (for add.l)
        //
        // Compute per-line increment:
        //   diff = d2/2 - d2/8 = 3*d2/8
        //   increment = ((diff << 8) / 0x30) << 8  (16.16 fixed-point)
        //
        // Starting BG value: d3 = d2 >> 3 (0.125x speed, integer only)
        {
            // Increment calculation (s2.asm lines 15332-15341)
            short halfD2 = asrWord(d2, 1);       // d2/2
            short eighthD2 = asrWord(d2, 3);     // d2/8
            short diff = (short) (halfD2 - eighthD2); // 3*d2/8
            int diffExt = diff;                   // ext.l d0 (sign-extend to 32-bit)
            diffExt <<= 8;                        // asl.l #8,d0
            // divs.w #$30,d0 : 32-bit dividend / 16-bit divisor, quotient in low word
            int quotient;
            if (diffExt == 0) {
                quotient = 0;
            } else {
                quotient = diffExt / 0x30;
            }
            short quotientWord = (short) quotient; // ext.l d0 (take low word, sign-extend)
            int increment = ((int) quotientWord) << 8; // asl.l #8,d0

            // Starting BG value (s2.asm lines 15342-15344)
            // moveq #0,d3; move.w d2,d3; asr.w #3,d3
            // d3 in "normal" form: integer in low word, fraction (0) in high word
            short d3_integer = asrWord(d2, 3);    // d2/8
            // Store in "swapped" form for easy arithmetic:
            //   swapped = integer in high 16, fraction in low 16 (standard 16.16)
            int d3 = (d3_integer & 0xFFFF) << 16; // fraction = 0

            // FG scroll for all gradient lines (d4.w after swap = d2 = FG scroll)
            // Already have fgScroll = d2

            // ===== 15-line band: 1 line per iteration (s2.asm lines 15347-15353) =====
            {
                int limit = Math.min(VISIBLE_LINES, lineIndex + 15);
                for (; lineIndex < limit; lineIndex++) {
                    short bgScroll = (short) (d3 >> 16); // integer part from high word
                    horizScrollBuf[lineIndex] = packScrollWords(fgScroll, bgScroll);
                    trackOffset(fgScroll, bgScroll);
                    // swap d3; add.l d0,d3; swap d3
                    d3 += increment;
                }
            }

            // ===== 18-line band: 2 lines per iteration (s2.asm lines 15356-15365) =====
            // Pairs of lines share the same BG value (stairstep effect)
            {
                int limit = Math.min(VISIBLE_LINES, lineIndex + 18);
                for (int pair = 0; pair < 9 && lineIndex < limit; pair++) {
                    short bgScroll = (short) (d3 >> 16);
                    int packed = packScrollWords(fgScroll, bgScroll);
                    trackOffset(fgScroll, bgScroll);
                    // Write 2 lines with same BG value
                    horizScrollBuf[lineIndex++] = packed;
                    if (lineIndex < limit) {
                        horizScrollBuf[lineIndex++] = packed;
                    }
                    // add.l d0,d3; add.l d0,d3 (advance by 2 increments)
                    d3 += increment;
                    d3 += increment;
                }
            }

            // ===== 45-line band: 3 lines per iteration (s2.asm lines 15368-15380) =====
            // Triplets of lines share the same BG value
            {
                int limit = Math.min(VISIBLE_LINES, lineIndex + 45);
                for (int trip = 0; trip < 15 && lineIndex < limit; trip++) {
                    short bgScroll = (short) (d3 >> 16);
                    int packed = packScrollWords(fgScroll, bgScroll);
                    trackOffset(fgScroll, bgScroll);
                    // Write 3 lines with same BG value
                    horizScrollBuf[lineIndex++] = packed;
                    if (lineIndex < limit) {
                        horizScrollBuf[lineIndex++] = packed;
                    }
                    if (lineIndex < limit) {
                        horizScrollBuf[lineIndex++] = packed;
                    }
                    // add.l d0,d3 x3 (advance by 3 increments)
                    d3 += increment;
                    d3 += increment;
                    d3 += increment;
                }
            }
        }

        // ==================== Bug Reproduction ====================
        // Original EHZ only writes 222 lines, leaving last 2 uninitialized.
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
