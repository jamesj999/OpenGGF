package com.openggf.game.sonic3k.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import com.openggf.level.scroll.compose.DeformationPlan;
import com.openggf.level.scroll.compose.PersistentAccumulator;
import com.openggf.level.scroll.compose.ScatterFillPlan;
import com.openggf.level.scroll.compose.ScrollEffectComposer;
import com.openggf.level.scroll.compose.ScrollValueTable;

import static com.openggf.level.scroll.M68KMath.negWord;

/**
 * Marble Garden Zone (MGZ) scroll handler for Sonic 3K.
 *
 * Ports MGZ1_Deform / MGZ2_BGDeform (normal path) from the S3K disassembly to
 * produce real per-line parallax rather than a flat fallback ratio.
 */
public class SwScrlMgz extends AbstractZoneScrollHandler {
    private static final int HSCROLL_WORD_COUNT = 32;

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

    private static final ScatterFillPlan MGZ2_SCATTER_FILL = new ScatterFillPlan(
            18, 16, 17, 10, 7, 14, 5, 12, 15, 13, 9, 4, 8, 6, 11
    );
    private static final DeformationPlan.ScrollValueTransform NEGATE_WORD = value -> negWord(value);

    // ROM accumulators that live in HScroll_table longwords and persist frame-to-frame.
    private final ScrollEffectComposer composer = new ScrollEffectComposer();
    private final ScrollValueTable mgz1HScrollTable = ScrollValueTable.ofLength(HSCROLL_WORD_COUNT);
    private final ScrollValueTable mgz2HScrollTable = ScrollValueTable.ofLength(HSCROLL_WORD_COUNT);
    private final ScrollValueTable mgz2ScatterSource = ScrollValueTable.ofLength(MGZ2_BG_DEFORM_INDEX.length);
    private final PersistentAccumulator mgz1CloudAccumulator = new PersistentAccumulator(0);
    private final PersistentAccumulator mgz2CloudAccumulator = new PersistentAccumulator(0);

    private int lastActId = -1;

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        if (frameCounter == 0 || actId != lastActId) {
            resetActState(actId);
        }

        composer.reset();
        short fgScroll = negWord(cameraX);

        if (actId == 0) {
            composer.setVscrollFactorBG((short) 0);
            buildMgz1HScrollTable(cameraX, mgz1HScrollTable);
            DeformationPlan.applyTableBands(
                    composer,
                    0,
                    fgScroll,
                    mgz1HScrollTable,
                    MGZ1_BG_DEFORM,
                    0,
                    NEGATE_WORD);
        } else {
            int bgY = computeMgz2BgY(cameraY);
            composer.setVscrollFactorBG((short) bgY);
            buildMgz2HScrollTable(cameraX, true, mgz2HScrollTable, mgz2ScatterSource);
            DeformationPlan.applyTableBands(
                    composer,
                    bgY,
                    fgScroll,
                    mgz2HScrollTable,
                    MGZ2_BG_DEFORM,
                    4,
                    NEGATE_WORD);
        }

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
        vscrollFactorBG = composer.getVscrollFactorBG();
    }

    private void resetActState(int actId) {
        if (actId == 0) {
            mgz1CloudAccumulator.set(0);
        } else {
            mgz2CloudAccumulator.set(0);
        }
        lastActId = actId;
    }

    /**
     * Port of MGZ1_Deform's HScroll_table generation.
     */
    private void buildMgz1HScrollTable(int cameraX, ScrollValueTable table) {
        table.clear();
        int d0 = ((short) cameraX) << 16;
        d0 >>= 2;

        int d1 = d0 >> 4;

        int a1 = 14; // HScroll_table+$01C word index
        for (int i = 0; i < 9; i++) {
            table.set(--a1, (short) (d0 >> 16));
            d0 -= d1;
        }

        int d2 = mgz1CloudAccumulator.get();
        mgz1CloudAccumulator.add(0x500);

        d0 >>= 1;
        a1 = 0;
        for (int i = 0; i < 5; i++) {
            d0 += d2;
            d2 += 0x500;
            table.set(a1++, (short) (d0 >> 16));
            d0 += d1;
        }

        // move.w -2(a1),d0 / move.w -4(a1),-2(a1) / move.w d0,-4(a1)
        short swap = table.get(9);
        table.set(9, table.get(8));
        table.set(8, swap);
    }

    /**
     * Port of MGZ2_BGDeform normal-path HScroll_table generation.
     */
    private void buildMgz2HScrollTable(int cameraX,
                                       boolean autoMoveClouds,
                                       ScrollValueTable table,
                                       ScrollValueTable scatterSource) {
        table.clear();
        scatterSource.clear();
        int d0 = ((short) cameraX) << 16;
        d0 >>= 1;

        int d1 = d0 >> 3;
        int d2 = d1 >> 2;

        int a1 = 27; // HScroll_table+$036 word index
        for (int i = 0; i < 8; i++) {
            table.set(--a1, (short) (d0 >> 16));
            d0 -= d1;
        }

        if (autoMoveClouds) {
            mgz2CloudAccumulator.add(0x800);
        }

        int cloudAcc = mgz2CloudAccumulator.get();
        int d0Cloud = d2;
        int d2Step = d2 >> 1;
        for (int i = 0; i < MGZ2_BG_DEFORM_INDEX.length; i++) {
            d0Cloud += cloudAcc;
            scatterSource.set(i, (short) (d0Cloud >> 16));
            d0Cloud += d2Step;
        }
        MGZ2_SCATTER_FILL.apply(scatterSource, table);

        for (int i = 0; i < MGZ2_BG_DEFORM_OFFSET.length; i++) {
            int idx = 4 + i;
            if (idx >= 0 && idx < table.size()) {
                table.set(idx, (short) (table.get(idx) + MGZ2_BG_DEFORM_OFFSET[i]));
            }
        }
    }

    private int computeMgz2BgY(int cameraY) {
        int d0 = ((short) cameraY) << 16;
        d0 >>= 4;
        int d1 = d0;
        d0 += d0;
        d0 += d1;
        return (short) (d0 >> 16);
    }

}
