package uk.co.jamesj999.sonic.game.sonic3k.scroll;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic3k.objects.AizPlaneIntroInstance;
import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import static uk.co.jamesj999.sonic.level.scroll.M68KMath.*;

/**
 * Angel Island Zone (AIZ) scroll handler.
 *
 * Intro mode implements AIZ1_IntroDeform + ApplyDeformation semantics:
 * - Builds HScroll_table+$28 values from Events_fg_1 / Camera_X_pos_copy
 * - Applies ROM segment heights from AIZ1_IntroDeformArray
 * - Writes negated BG values into the per-scanline hscroll buffer
 */
public class SwScrlAiz implements ZoneScrollHandler {

    private static final int INTRO_DEFORM_BANDS = 0x25;
    private static final int INTRO_DEFORM_CAP = 0x580;
    private static final int INTRO_DEFORM_TERMINATOR = 0x7FFF;
    private static final int[] INTRO_DEFORM_SEGMENTS = buildIntroDeformSegments();

    /** AIZ1_DeformArray heights (bit 15 stripped from $800D entry). */
    private static final int[] DEFORM_HEIGHTS = {
            0xD0, 0x20, 0x30, 0x30, 0x10, 0x10, 0x10,
            0x0D, 0x0F, 0x06, 0x0E, 0x50, 0x20
    };
    private static final int DEFORM_BAND_COUNT = DEFORM_HEIGHTS.length;

    /** Origin X for AIZ1_Deform base calculation (subi.w #$1300,d0). */
    private static final int DEFORM_ORIGIN_X = 0x1300;

    private short vscrollFactorBG;
    private int minScrollOffset;
    private int maxScrollOffset;
    private final short[] introBandValues = new short[INTRO_DEFORM_BANDS];

    /** Persistent wave accumulator (ROM: HScroll_table+$03C, advances $2000/frame). */
    private long waveAccum;

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        minScrollOffset = Integer.MAX_VALUE;
        maxScrollOffset = Integer.MIN_VALUE;

        short fgScroll = negWord(cameraX);

        // ROM mode gate: AIZ1 intro uses IntroDeform only before the $1400 transition.
        boolean introMode = false;
        try {
            introMode = !Camera.getInstance().isLevelStarted()
                    && !AizPlaneIntroInstance.isMainLevelPhaseActive();
        } catch (Exception ignored) {}

        if (introMode) {
            // AIZ1_IntroDeform:
            // d0 = Events_fg_1; if non-negative, use Camera_X_pos_copy.
            int introOffset = AizPlaneIntroInstance.getIntroScrollOffset();
            int source = introOffset < 0 ? introOffset : cameraX;
            buildIntroBandValues(source);
            vscrollFactorBG = wordOf(cameraY);
            writeIntroScroll(horizScrollBuf, fgScroll, cameraY);
        } else {
            // AIZ1_Deform: multi-band BG parallax with per-band speeds.
            // BG vertical scroll = camera Y / 2.
            vscrollFactorBG = asrWord(cameraY, 1);
            computeAiz1Deform(horizScrollBuf, fgScroll, cameraX, cameraY);
        }
    }

    /**
     * AIZ1_Deform: compute multi-band BG scroll values and distribute across scanlines.
     *
     * <p>ROM reference: Screen Events.asm line 576-636 + ApplyDeformation.
     *
     * <p>Computes 13 band BG scroll values at varying speeds relative to
     * {@code cameraX - $1300}. Bands 0-5 (tree canopy) include a per-frame
     * wave animation. Band 6 is a transition band. Bands 7-12 (mountains/sky)
     * increase linearly.
     */
    private void computeAiz1Deform(int[] horizScrollBuf, short fgScroll,
                                    int cameraX, int cameraY) {
        // base = (cameraX - $1300) << 11 in 16.16 fixed-point
        int relative = (short) (cameraX - DEFORM_ORIGIN_X);
        long base = ((long) relative << 16) >> 5; // = relative << 11

        short[] bandValues = new short[DEFORM_BAND_COUNT];

        // --- Tree bands (indices 0-6): second block of ROM code ---
        // Band 6 = base >> 16 (transition band, no wave)
        bandValues[6] = (short) (base >> 16);

        // Wave accumulator persists across frames (ROM: HScroll_table+$03C += $2000)
        long d3 = waveAccum;
        waveAccum += 0x2000;

        // Bands 5→0: increasingly fast tree parallax with wave motion.
        // ROM: d0 starts at base/2, each iteration adds d3 (wave) before write,
        //      then adds base after write.
        long d0 = base >> 1; // base / 2
        for (int i = 5; i >= 0; i--) {
            d0 += d3;
            bandValues[i] = (short) (d0 >> 16);
            d0 += base;
        }

        // --- Mountain/sky bands (indices 7-12): third block of ROM code ---
        // ROM: d0 starts at base, d2 = base/8, each iteration d0 += d2
        long increment = base >> 3; // base / 8
        d0 = base;
        for (int i = 7; i < DEFORM_BAND_COUNT; i++) {
            d0 += increment;
            bandValues[i] = (short) (d0 >> 16);
        }

        // Distribute band values across scanlines using AIZ1_DeformArray heights.
        // ApplyDeformation: skip bands above BG camera Y, then write visible lines.
        writeDeformBands(horizScrollBuf, fgScroll, bandValues, cameraY);
    }

    /**
     * Distribute band BG scroll values across visible scanlines using
     * DEFORM_HEIGHTS (AIZ1_DeformArray).
     *
     * <p>ROM: ApplyDeformation reads Camera_Y_pos_BG_copy to determine which
     * bands are above the visible area, then writes packed (FG,BG) entries
     * for each visible scanline. BG values are negated (ROM: neg.w d3).
     */
    private void writeDeformBands(int[] horizScrollBuf, short fgScroll,
                                   short[] bandValues, int cameraY) {
        // BG camera Y = cameraY / 2 (set as vscrollFactorBG)
        int remainingY = (short) vscrollFactorBG;
        int lineIndex = 0;
        int bandIndex = 0;

        // Skip bands above the visible area
        while (bandIndex < DEFORM_BAND_COUNT) {
            int height = DEFORM_HEIGHTS[bandIndex];
            int next = remainingY - height;
            if (next < 0) {
                // Top of screen is within this band
                int visibleLines = -next;
                short bgScroll = negWord(bandValues[bandIndex]);
                lineIndex = writeSegment(horizScrollBuf, lineIndex, visibleLines, fgScroll, bgScroll);
                bandIndex++;
                break;
            }
            remainingY = next;
            bandIndex++;
        }

        // Write remaining visible bands
        while (lineIndex < VISIBLE_LINES && bandIndex < DEFORM_BAND_COUNT) {
            int height = DEFORM_HEIGHTS[bandIndex];
            int count = Math.min(height, VISIBLE_LINES - lineIndex);
            short bgScroll = negWord(bandValues[bandIndex]);
            lineIndex = writeSegment(horizScrollBuf, lineIndex, count, fgScroll, bgScroll);
            bandIndex++;
        }

        // Pad remaining lines with the last band value
        short lastBg = negWord(bandValues[Math.min(bandIndex, DEFORM_BAND_COUNT) - 1]);
        while (lineIndex < VISIBLE_LINES) {
            int packed = packScrollWords(fgScroll, lastBg);
            trackOffset(fgScroll, lastBg);
            horizScrollBuf[lineIndex++] = packed;
        }
    }

    private void buildIntroBandValues(int source) {
        int d0 = (short) source;
        d0 >>= 1;

        if (d0 >= INTRO_DEFORM_CAP) {
            short value = (short) d0;
            for (int i = 0; i < INTRO_DEFORM_BANDS; i++) {
                introBandValues[i] = value;
            }
            return;
        }

        introBandValues[0] = (short) d0;

        int accum = (d0 - INTRO_DEFORM_CAP) << 16;
        int step = accum >> 5;
        for (int i = 1; i < INTRO_DEFORM_BANDS; i++) {
            accum += step;
            introBandValues[i] = (short) ((accum >> 16) + INTRO_DEFORM_CAP);
        }
    }

    private void writeIntroScroll(int[] horizScrollBuf, short fgScroll, int cameraY) {
        // ApplyDeformation uses Camera_Y_pos_BG_copy as signed word.
        int remainingY = (short) cameraY;
        int lineIndex = 0;
        int segmentIndex = 0;
        int valueIndex = 0;

        // Skip whole deformation segments until the first visible segment is found.
        while (segmentIndex < INTRO_DEFORM_SEGMENTS.length) {
            int segmentHeight = INTRO_DEFORM_SEGMENTS[segmentIndex];
            if (segmentHeight == INTRO_DEFORM_TERMINATOR) {
                break;
            }

            int next = remainingY - segmentHeight;
            if (next >= 0) {
                remainingY = next;
                segmentIndex++;
                if (valueIndex < INTRO_DEFORM_BANDS - 1) {
                    valueIndex++;
                }
                continue;
            }

            // Top of the screen is inside this segment.
            int visibleLinesInCurrentSegment = -next;
            short bgScroll = negWord(introBandValues[valueIndex]);
            lineIndex = writeSegment(horizScrollBuf, lineIndex, visibleLinesInCurrentSegment, fgScroll, bgScroll);

            segmentIndex++;
            if (valueIndex < INTRO_DEFORM_BANDS - 1) {
                valueIndex++;
            }

            // Emit following segments until the screen is filled.
            while (lineIndex < VISIBLE_LINES && segmentIndex < INTRO_DEFORM_SEGMENTS.length) {
                segmentHeight = INTRO_DEFORM_SEGMENTS[segmentIndex];
                if (segmentHeight == INTRO_DEFORM_TERMINATOR) {
                    break;
                }
                int count = Math.min(segmentHeight, VISIBLE_LINES - lineIndex);
                bgScroll = negWord(introBandValues[valueIndex]);
                lineIndex = writeSegment(horizScrollBuf, lineIndex, count, fgScroll, bgScroll);
                segmentIndex++;
                if (valueIndex < INTRO_DEFORM_BANDS - 1) {
                    valueIndex++;
                }
            }
            break;
        }

        // Safety fallback: if decomposition ended early, pad with the last value.
        short fallbackBg = negWord(introBandValues[Math.min(valueIndex, INTRO_DEFORM_BANDS - 1)]);
        while (lineIndex < VISIBLE_LINES) {
            int packed = packScrollWords(fgScroll, fallbackBg);
            trackOffset(fgScroll, fallbackBg);
            horizScrollBuf[lineIndex++] = packed;
        }
    }

    private int writeSegment(int[] horizScrollBuf,
                             int start,
                             int count,
                             short fgScroll,
                             short bgScroll) {
        if (count <= 0 || start >= VISIBLE_LINES) {
            return start;
        }
        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);
        int end = Math.min(VISIBLE_LINES, start + count);
        for (int i = start; i < end; i++) {
            horizScrollBuf[i] = packed;
        }
        return end;
    }

    private static int[] buildIntroDeformSegments() {
        // AIZ1_IntroDeformArray: $3E0, then 36 entries of 4, then $7FFF.
        int[] segments = new int[INTRO_DEFORM_BANDS + 1];
        segments[0] = 0x3E0;
        for (int i = 1; i < INTRO_DEFORM_BANDS; i++) {
            segments[i] = 4;
        }
        segments[INTRO_DEFORM_BANDS] = INTRO_DEFORM_TERMINATOR;
        return segments;
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
