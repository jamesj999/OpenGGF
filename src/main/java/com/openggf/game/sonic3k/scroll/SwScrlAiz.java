package com.openggf.game.sonic3k.scroll;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.Sonic3kLevelEventManager;
import com.openggf.game.sonic3k.events.Sonic3kAIZEvents;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.Arrays;

import static com.openggf.level.scroll.M68KMath.*;

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

    /** Bit 15 flag in AIZ1_DeformArray: band uses one scroll value per scanline. */
    private static final int PER_LINE_FLAG = 0x8000;

    /** AIZ1_DeformArray heights. $800D = per-line flag | 13 scanlines. */
    private static final int[] DEFORM_HEIGHTS = {
            0xD0, 0x20, 0x30, 0x30, 0x10, 0x10, 0x10,
            0x800D, 0x0F, 0x06, 0x0E, 0x50, 0x20
    };
    private static final int DEFORM_BAND_COUNT = DEFORM_HEIGHTS.length;

    /** Total words in the flat HScroll_table value array ($008-$038). */
    private static final int FLAT_VALUE_COUNT = 25;

    /** Origin X for AIZ1_Deform base calculation (subi.w #$1300,d0). */
    private static final int DEFORM_ORIGIN_X = 0x1300;
    private static final byte[] AIZ_FLAME_VSCROLL = {
            0, -1, -2, -5, -8, -10, -13, -14,
            -15, -14, -13, -10, -7, -5, -2, -1
    };
    private static final int FIRE_WAVE_COLUMN_COUNT = 0x14; // ROM loop count: moveq #$14-1,d3
    // AIZ2_SOZ1_LRZ3_FGDeformDelta base pattern (32-word cycle, repeated in ROM table).
    private static final short[] AIZ_FINE_HAZE_FG_DEFORM = {
            0, 0, 1, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0
    };

    private short vscrollFactorBG;
    private int minScrollOffset;
    private int maxScrollOffset;
    private final short[] introBandValues = new short[INTRO_DEFORM_BANDS];
    private final short[] perColumnVScrollBG = new short[FIRE_WAVE_COLUMN_COUNT];
    private boolean hasPerColumnVScrollBG;

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
        hasPerColumnVScrollBG = false;
        Arrays.fill(perColumnVScrollBG, (short) 0);

        short fgScroll = negWord(cameraX);
        Sonic3kAIZEvents aizEvents = resolveAizEvents();
        boolean fireTransition = actId == 0
                && aizEvents != null
                && (aizEvents.isFireTransitionActive() || aizEvents.isAct2TransitionRequested());
        int bgSourceX = fireTransition ? aizEvents.getFireTransitionBgX() : cameraX;

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
            vscrollFactorBG = fireTransition
                    ? wordOf(aizEvents.getFireTransitionBgY())
                    : asrWord(cameraY, 1);
            computeAiz1Deform(horizScrollBuf, fgScroll, bgSourceX, cameraY);
        }

        // Fine post-burn haze (AIZ2 style) is a subtle per-line FG deformation.
        // Keep it separate from AIZTrans_WavyFlame, which is a temporary BG transition effect.
        boolean fineHeatHazeActive = !fireTransition
                && (actId > 0
                || (aizEvents != null && aizEvents.isPostFireHazeActive())
                || cameraX >= 0x2E00);
        if (fineHeatHazeActive) {
            applyFineFgHeatHaze(horizScrollBuf, cameraX, cameraY, frameCounter);
        }

        if (fireTransition) {
            buildFireWaveVScroll(frameCounter);
        }
    }

    /**
     * AIZ1_Deform: compute multi-band BG scroll values and distribute across scanlines.
     *
     * <p>ROM reference: s3.asm AIZ1_Deform (line ~70272) + ApplyDeformation.
     *
     * <p>Builds a flat 25-word value array mirroring HScroll_table+$008...$038:
     * <ul>
     *   <li>Bands 0-5 (6 words): tree canopy with wave motion</li>
     *   <li>Band 6 (1 word): transition at base speed</li>
     *   <li>Band 7 (13 words): per-line gradient from base*9/8 to base*21/8</li>
     *   <li>Bands 8-12 (5 words): mountain/sky at base*14, 16, 18, 20, 18</li>
     * </ul>
     */
    private void computeAiz1Deform(int[] horizScrollBuf, short fgScroll,
                                    int cameraX, int cameraY) {
        // base = (cameraX - $1300) << 11 in 16.16 fixed-point
        int relative = (short) (cameraX - DEFORM_ORIGIN_X);
        long base = ((long) relative << 16) >> 5; // = relative << 11

        short[] values = new short[FLAT_VALUE_COUNT];

        // --- Bands 0-5: tree canopy with wave (HScroll_table+$008...$012) ---
        // Wave accumulator persists across frames (ROM: HScroll_table+$03C += $2000)
        long d3 = waveAccum;
        waveAccum += 0x2000;

        // ROM: d0 starts at base/2, each iteration adds d3 (wave) before write,
        //      then adds base (d2) after write.
        long d0 = base >> 1; // base / 2
        for (int i = 5; i >= 0; i--) {
            d0 += d3;
            values[i] = (short) (d0 >> 16);
            d0 += base;
        }

        // --- Band 6: transition (HScroll_table+$014) ---
        values[6] = (short) (base >> 16);

        // --- Band 7: per-line gradient (HScroll_table+$016...$02E, 13 words) ---
        // ROM: d0 = base, d2 = base/8, loop 13x: d0 += d2, store
        long increment = base >> 3; // base / 8
        d0 = base;
        for (int i = 0; i < 13; i++) {
            d0 += increment;
            values[7 + i] = (short) (d0 >> 16);
        }

        // --- Bands 8-12: mountain/sky (HScroll_table+$030...$038, 5 words) ---
        // ROM: d1 = base*2, d0 = d1*8 - d1 = base*14, then d0 += d1 each step
        long d1 = base + base;          // base*2
        d0 = (d1 << 3) - d1;            // base*16 - base*2 = base*14
        values[20] = (short) (d0 >> 16); // band 8: base*14
        d0 += d1;
        values[21] = (short) (d0 >> 16); // band 9: base*16
        d0 += d1;
        values[22] = (short) (d0 >> 16); // band 10: base*18
        d0 += d1;
        values[23] = (short) (d0 >> 16); // band 11: base*20
        values[24] = values[22];          // band 12: base*18 (same as band 10)

        // Distribute values across scanlines using AIZ1_DeformArray heights.
        writeDeformBands(horizScrollBuf, fgScroll, values, cameraY);
    }

    /**
     * Distribute flat BG scroll values across visible scanlines using
     * DEFORM_HEIGHTS (AIZ1_DeformArray).
     *
     * <p>ROM: ApplyDeformation reads Camera_Y_pos_BG_copy to determine which
     * bands are above the visible area, then writes packed (FG,BG) entries
     * for each visible scanline. BG values are negated (ROM: neg.w d3).
     *
     * <p>Bands with the PER_LINE_FLAG consume one value per scanline from
     * the flat array; normal bands consume one value repeated for all lines.
     */
    private void writeDeformBands(int[] horizScrollBuf, short fgScroll,
                                   short[] flatValues, int cameraY) {
        // BG camera Y = cameraY / 2 (set as vscrollFactorBG)
        int remainingY = (short) vscrollFactorBG;
        int lineIndex = 0;
        int bandIndex = 0;
        int valueIndex = 0;

        // Skip bands above the visible area
        while (bandIndex < DEFORM_BAND_COUNT) {
            int raw = DEFORM_HEIGHTS[bandIndex];
            boolean perLine = (raw & PER_LINE_FLAG) != 0;
            int height = raw & 0x7FFF;

            int next = remainingY - height;
            if (next < 0) {
                // Top of screen is within this band
                int invisibleCount = remainingY;
                int visibleLines = -next;

                if (perLine) {
                    valueIndex += invisibleCount;
                    for (int i = 0; i < visibleLines && lineIndex < VISIBLE_LINES; i++) {
                        short bgScroll = negWord(flatValues[valueIndex++]);
                        int packed = packScrollWords(fgScroll, bgScroll);
                        trackOffset(fgScroll, bgScroll);
                        horizScrollBuf[lineIndex++] = packed;
                    }
                } else {
                    short bgScroll = negWord(flatValues[valueIndex++]);
                    lineIndex = writeSegment(horizScrollBuf, lineIndex, visibleLines, fgScroll, bgScroll);
                }
                bandIndex++;
                break;
            }

            // Whole band is above screen — skip its values
            if (perLine) {
                valueIndex += height;
            } else {
                valueIndex++;
            }
            remainingY = next;
            bandIndex++;
        }

        // Write remaining visible bands
        while (lineIndex < VISIBLE_LINES && bandIndex < DEFORM_BAND_COUNT) {
            int raw = DEFORM_HEIGHTS[bandIndex];
            boolean perLine = (raw & PER_LINE_FLAG) != 0;
            int height = raw & 0x7FFF;
            int count = Math.min(height, VISIBLE_LINES - lineIndex);

            if (perLine) {
                for (int i = 0; i < count; i++) {
                    short bgScroll = negWord(flatValues[valueIndex++]);
                    int packed = packScrollWords(fgScroll, bgScroll);
                    trackOffset(fgScroll, bgScroll);
                    horizScrollBuf[lineIndex++] = packed;
                }
            } else {
                short bgScroll = negWord(flatValues[valueIndex++]);
                lineIndex = writeSegment(horizScrollBuf, lineIndex, count, fgScroll, bgScroll);
            }
            bandIndex++;
        }

        // Pad remaining lines with the last band value
        short lastBg = valueIndex > 0 ? negWord(flatValues[valueIndex - 1]) : 0;
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
    public short[] getPerLineVScrollBG() {
        return null;
    }

    @Override
    public short[] getPerColumnVScrollBG() {
        return hasPerColumnVScrollBG ? perColumnVScrollBG : null;
    }

    @Override
    public int getMinScrollOffset() {
        return minScrollOffset;
    }

    @Override
    public int getMaxScrollOffset() {
        return maxScrollOffset;
    }

    private void buildFireWaveVScroll(int frameCounter) {
        // ROM AIZTrans_WavyFlame:
        // d2 = Level_frame_counter >> 2, then each write does d2 += 2 (mod 16),
        // producing 20 VScroll words (column scroll mode).
        int d2 = (frameCounter >> 2) & 0xF;
        for (int i = 0; i < FIRE_WAVE_COLUMN_COUNT; i++) {
            d2 = (d2 + 2) & 0xF;
            perColumnVScrollBG[i] = AIZ_FLAME_VSCROLL[d2];
        }
        hasPerColumnVScrollBG = true;
    }

    private void applyFineFgHeatHaze(int[] horizScrollBuf, int cameraX, int cameraY, int frameCounter) {
        int phase = ((frameCounter + (cameraY << 1)) & 0x3E) >> 1;
        for (int line = 0; line < VISIBLE_LINES; line++) {
            int packed = horizScrollBuf[line];
            short bg = unpackBG(packed);
            short fg = (short) (negWord(cameraX) + AIZ_FINE_HAZE_FG_DEFORM[(phase + line) & 0x1F]);
            horizScrollBuf[line] = packScrollWords(fg, bg);
        }
    }

    private Sonic3kAIZEvents resolveAizEvents() {
        try {
            Sonic3kLevelEventManager lem = Sonic3kLevelEventManager.getInstance();
            return lem != null ? lem.getAizEvents() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
