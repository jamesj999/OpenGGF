package com.openggf.game.sonic3k.scroll;

import com.openggf.level.scroll.ZoneScrollHandler;

import java.util.Arrays;

import static com.openggf.level.scroll.M68KMath.negWord;
import static com.openggf.level.scroll.M68KMath.packScrollWords;

/**
 * Marble Garden Zone (MGZ) scroll handler for Sonic 3K.
 *
 * Ports MGZ1_Deform / MGZ2_BGDeform (normal path) from the S3K disassembly to
 * produce real per-line parallax rather than a flat fallback ratio.
 */
public class SwScrlMgz implements ZoneScrollHandler {
    private static final int VISIBLE_LINES = 224;

    // MGZ1_BGDeformArray
    private static final int[] MGZ1_BG_DEFORM = {
            0x10, 0x04, 0x04, 0x08, 0x08, 0x08, 0x0D, 0x13, 0x08, 0x08, 0x08, 0x08, 0x18, 0x7FFF
    };

    // MGZ2_BGDeformArray
    private static final int[] MGZ2_BG_DEFORM = {
            0x10, 0x10, 0x10, 0x10, 0x10, 0x18, 0x08, 0x10, 0x08, 0x08, 0x10, 0x08,
            0x08, 0x08, 0x05, 0x2B, 0x0C, 0x06, 0x06, 0x08, 0x08, 0x18, 0xD8, 0x7FFF
    };

    // MGZ2_BGDeformIndex
    private static final int[] MGZ2_BG_DEFORM_INDEX = {
            0x1C, 0x18, 0x1A, 0x0C, 0x06, 0x14, 0x02, 0x10, 0x16, 0x12, 0x0A, 0x00, 0x08, 0x04, 0x0E
    };

    // MGZ2_BGDeformOffset
    private static final int[] MGZ2_BG_DEFORM_OFFSET = {
            -5, -8, 9, 10, 2, -12, 3, 16, -1, 13, -15, 6, -11, -4, 14,
            -8, 16, 8, 0, -8, 16, 8, 0
    };

    private static final int HSCROLL_WORD_COUNT = 32;

    private final short[] hScrollTable = new short[HSCROLL_WORD_COUNT];

    // ROM accumulators that live in HScroll_table longwords and persist frame-to-frame.
    private int mgz1CloudAccumulator;
    private int mgz2CloudAccumulator;

    private int lastActId = -1;

    private short vscrollFactorBG;
    private int minScrollOffset;
    private int maxScrollOffset;

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        if (frameCounter == 0 || actId != lastActId) {
            resetActState(actId);
        }

        minScrollOffset = Integer.MAX_VALUE;
        maxScrollOffset = Integer.MIN_VALUE;

        Arrays.fill(hScrollTable, (short) 0);
        short fgScroll = negWord(cameraX);

        if (actId == 0) {
            buildMgz1HScroll(cameraX);
            vscrollFactorBG = 0;
            applyBgDeformation(horizScrollBuf, fgScroll, 0, MGZ1_BG_DEFORM, 0);
        } else {
            int bgY = computeMgz2BgY(cameraY);
            vscrollFactorBG = (short) bgY;
            buildMgz2HScroll(cameraX, true);
            applyBgDeformation(horizScrollBuf, fgScroll, bgY, MGZ2_BG_DEFORM, 4);
        }

        if (minScrollOffset == Integer.MAX_VALUE) {
            minScrollOffset = 0;
            maxScrollOffset = 0;
        }
    }

    private void resetActState(int actId) {
        if (actId == 0) {
            mgz1CloudAccumulator = 0;
        } else {
            mgz2CloudAccumulator = 0;
        }
        lastActId = actId;
    }

    /**
     * Port of MGZ1_Deform's HScroll_table generation.
     */
    private void buildMgz1HScroll(int cameraX) {
        int d0 = ((short) cameraX) << 16;
        d0 >>= 2;

        int d1 = d0 >> 4;

        int a1 = 14; // HScroll_table+$01C word index
        for (int i = 0; i < 9; i++) {
            hScrollTable[--a1] = (short) (d0 >> 16);
            d0 -= d1;
        }

        int d2 = mgz1CloudAccumulator;
        mgz1CloudAccumulator += 0x500;

        d0 >>= 1;
        for (int i = 0; i < 5; i++) {
            d0 += d2;
            d2 += 0x500;
            hScrollTable[a1++] = (short) (d0 >> 16);
            d0 += d1;
        }

        // move.w -2(a1),d0 / move.w -4(a1),-2(a1) / move.w d0,-4(a1)
        short swap = hScrollTable[9];
        hScrollTable[9] = hScrollTable[8];
        hScrollTable[8] = swap;
    }

    /**
     * Port of MGZ2_BGDeform normal-path HScroll_table generation.
     */
    private void buildMgz2HScroll(int cameraX, boolean autoMoveClouds) {
        int d0 = ((short) cameraX) << 16;
        d0 >>= 1;

        int d1 = d0 >> 3;
        int d2 = d1 >> 2;

        int a1 = 27; // HScroll_table+$036 word index
        for (int i = 0; i < 8; i++) {
            hScrollTable[--a1] = (short) (d0 >> 16);
            d0 -= d1;
        }

        if (autoMoveClouds) {
            mgz2CloudAccumulator += 0x800;
        }

        int cloudAcc = mgz2CloudAccumulator;
        int d0Cloud = d2;
        int d2Step = d2 >> 1;
        for (int i = 0; i < MGZ2_BG_DEFORM_INDEX.length; i++) {
            d0Cloud += cloudAcc;
            int idx = 4 + (MGZ2_BG_DEFORM_INDEX[i] >> 1); // base a1 = HScroll_table+$008
            if (idx >= 0 && idx < hScrollTable.length) {
                hScrollTable[idx] = (short) (d0Cloud >> 16);
            }
            d0Cloud += d2Step;
        }

        for (int i = 0; i < MGZ2_BG_DEFORM_OFFSET.length; i++) {
            int idx = 4 + i;
            if (idx >= 0 && idx < hScrollTable.length) {
                hScrollTable[idx] = (short) (hScrollTable[idx] + MGZ2_BG_DEFORM_OFFSET[i]);
            }
        }
    }

    /**
     * Apply the deform table to 224 lines (equivalent to ApplyDeformation for
     * non-negative segment entries).
     */
    private void applyBgDeformation(int[] horizScrollBuf,
                                    short fgScroll,
                                    int cameraYBg,
                                    int[] deformHeights,
                                    int tableStartIndex) {
        int segmentIndex = 0;
        int tableIndex = tableStartIndex;
        int y = (short) cameraYBg;

        int height = nextHeight(deformHeights, segmentIndex++);
        while ((y - height) >= 0) {
            y -= height;
            tableIndex++;
            height = nextHeight(deformHeights, segmentIndex++);
        }
        y -= height;

        int line = 0;
        int firstCount = -y;
        line = writeLines(horizScrollBuf, line, firstCount, fgScroll, tableIndex);

        while (line < VISIBLE_LINES) {
            int count = nextHeight(deformHeights, segmentIndex++);
            line = writeLines(horizScrollBuf, line, count, fgScroll, ++tableIndex);
        }
    }

    private int nextHeight(int[] deformHeights, int index) {
        if (index >= deformHeights.length) {
            return 0x7FFF;
        }
        int value = deformHeights[index] & 0x7FFF;
        return value == 0 ? 1 : value;
    }

    private int writeLines(int[] horizScrollBuf,
                           int startLine,
                           int lineCount,
                           short fgScroll,
                           int tableIndex) {
        if (lineCount <= 0 || startLine >= VISIBLE_LINES) {
            return startLine;
        }

        int clampedTableIndex = Math.max(0, Math.min(tableIndex, hScrollTable.length - 1));
        short bgScroll = (short) -hScrollTable[clampedTableIndex];
        int packed = packScrollWords(fgScroll, bgScroll);
        int endLine = Math.min(VISIBLE_LINES, startLine + lineCount);

        int offset = bgScroll - fgScroll;
        if (offset < minScrollOffset) {
            minScrollOffset = offset;
        }
        if (offset > maxScrollOffset) {
            maxScrollOffset = offset;
        }

        for (int line = startLine; line < endLine; line++) {
            horizScrollBuf[line] = packed;
        }
        return endLine;
    }

    private int computeMgz2BgY(int cameraY) {
        int d0 = ((short) cameraY) << 16;
        d0 >>= 4;
        int d1 = d0;
        d0 += d0;
        d0 += d1;
        return (short) (d0 >> 16);
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
