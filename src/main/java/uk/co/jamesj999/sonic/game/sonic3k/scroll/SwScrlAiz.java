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

    private short vscrollFactorBG;
    private int minScrollOffset;
    private int maxScrollOffset;
    private final short[] introBandValues = new short[INTRO_DEFORM_BANDS];

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
            // Non-intro mode follows AIZ1_Deform's half-speed vertical camera.
            short bgScroll = asrWord(fgScroll, 1);
            vscrollFactorBG = asrWord(cameraY, 1);
            int packed = packScrollWords(fgScroll, bgScroll);
            trackOffset(fgScroll, bgScroll);

            for (int line = 0; line < VISIBLE_LINES; line++) {
                horizScrollBuf[line] = packed;
            }
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
