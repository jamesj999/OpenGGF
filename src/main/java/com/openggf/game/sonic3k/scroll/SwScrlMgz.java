package com.openggf.game.sonic3k.scroll;

import com.openggf.game.GameServices;
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
    // Screen shake support (ROM: Screen_shake_flag, used by Tunnelbot / MGZ Miniboss).
    // Applied to FG and BG VScroll, while getShakeOffsetY() propagates the same
    // vertical delta to sprites. MGZ2_BGDeform compensates BG parallax math so
    // the shake lands 1:1 on the plane instead of being scaled away.
    private int screenShakeOffset;
    private short vscrollFactorFG;

    // ROM: MGZ2_BGDeform (Lockon S3/Screen Events.asm:1090-1145) switches the BG
    // scroll formula per Events_bg+$00 state. State 0 uses 3/16 parallax (cloud
    // layer); state 8 (Sonic rise) locks the BG 1:1 to the FG at a fixed
    // ROM-defined offset so the pre-placed terrain rows in the BG layout line
    // up with the pit and become standable via Background_collision_flag.
    // State C shifts the parallax origin by $500.
    private static final int BG_RISE_NORMAL_STATE = 0;
    private static final int BG_RISE_SONIC_STATE = 8;
    /** ROM: loc_23D1EA — d1 = $8F0, d2 = $3200 for Sonic rise. */
    private static final int MGZ2_SONIC_RISE_Y_BASE = 0x8F0;
    private static final int MGZ2_SONIC_RISE_X_BASE = 0x3200;

    private int bgRiseRoutine;
    private int bgRiseOffset;
    /**
     * Cached BG camera X for {@link #getBgCameraX()}. {@link Integer#MIN_VALUE}
     * means "no override" — the dual-path collision uses the FG cameraX as the
     * BG reference, which is the correct default for normal MGZ play. During
     * state 8 this is set to {@code cameraX - $3200} so ground collision probes
     * land inside the 24-col BG layout.
     */
    private int lastBgCameraX = Integer.MIN_VALUE;

    public void setScreenShakeOffset(int offset) {
        this.screenShakeOffset = offset;
    }

    public void setBgRiseState(int routine, int offset) {
        this.bgRiseRoutine = routine;
        this.bgRiseOffset = offset;
        primeBgCollisionStateFromCurrentCamera();
    }

    @Override
    public int getShakeOffsetY() {
        return screenShakeOffset;
    }

    @Override
    public short getVscrollFactorFG() {
        return vscrollFactorFG;
    }

    @Override
    public int getBgCameraX() {
        return lastBgCameraX;
    }

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
            lastBgCameraX = Integer.MIN_VALUE;
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
        } else if (bgRiseRoutine == BG_RISE_SONIC_STATE) {
            // State 8 (Sonic rise): MGZ2_BGDeform still runs the cloud/scatter
            // parallax builder, but it seeds the final deform slot with the
            // 1:1 terrain lock value (Camera_X_pos_BG_copy = cameraX - $3200).
            // That keeps the clouds drifting while the terrain band scrolls
            // 1:1 with the lift.
            int bgY = ((short) cameraY) - MGZ2_SONIC_RISE_Y_BASE + bgRiseOffset;
            int bgScrollBaseX = ((short) cameraX) - MGZ2_SONIC_RISE_X_BASE;
            // Expose to dual-path collision so probes at world X≈$3800
            // translate into the BG layout's populated 0..23 range.
            lastBgCameraX = bgScrollBaseX;
            composer.setVscrollFactorBG((short) bgY);
            buildMgz2StateEightHScrollTable(cameraX, bgScrollBaseX, mgz2HScrollTable, mgz2ScatterSource);
            DeformationPlan.applyTableBands(
                    composer,
                    bgY,
                    fgScroll,
                    mgz2HScrollTable,
                    MGZ2_BG_DEFORM,
                    4,
                    NEGATE_WORD);
        } else {
            // State 0 (normal MGZ2 play) and state C (after-move) use the
            // unchanged cloud-parallax formula so cloud rendering stays correct.
            int bgY = computeMgz2BgY(cameraY);
            lastBgCameraX = Integer.MIN_VALUE;
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

        // Screen shake: apply the same camera rumble offset to both planes so the
        // BG cloud/floor strip tracks the shaken viewport instead of staying fixed.
        if (screenShakeOffset != 0) {
            composer.setVscrollFactorBG((short) (composer.getVscrollFactorBG() + screenShakeOffset));
            composer.setVscrollFactorFG((short) (cameraY + screenShakeOffset));
        }

        composer.copyPackedScrollWordsTo(horizScrollBuf);
        minScrollOffset = composer.getMinScrollOffset();
        maxScrollOffset = composer.getMaxScrollOffset();
        vscrollFactorBG = composer.getVscrollFactorBG();
        vscrollFactorFG = composer.getVscrollFactorFG();
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

    /**
     * ROM: MGZ2_BGDeform state 8 still executes the cloud/scatter fill from
     * {@code loc_23D24C..loc_23D2B4}, but the state-8 prelude seeds
     * {@code HScroll_table+$036} with {@code Camera_X_pos_BG_copy}. That final
     * slot feeds the locked terrain band while the earlier slots retain the
     * normal cloud parallax.
     */
    private void buildMgz2StateEightHScrollTable(int cameraX,
                                                 int bgScrollBaseX,
                                                 ScrollValueTable table,
                                                 ScrollValueTable scatterSource) {
        buildMgz2HScrollTable(cameraX, true, table, scatterSource);
        table.set(27, (short) bgScrollBaseX);
    }

    private int computeMgz2BgY(int cameraY) {
        int d0 = ((short) cameraY) << 16;
        d0 >>= 4;
        int d1 = d0;
        d0 += d0;
        d0 += d1;
        return (short) (d0 >> 16);
    }

    private void primeBgCollisionStateFromCurrentCamera() {
        var camera = GameServices.cameraOrNull();
        if (camera == null) {
            return;
        }
        if (bgRiseRoutine == BG_RISE_SONIC_STATE) {
            lastBgCameraX = ((short) camera.getX()) - MGZ2_SONIC_RISE_X_BASE;
            vscrollFactorBG = (short) ((((short) camera.getY()) - MGZ2_SONIC_RISE_Y_BASE) + bgRiseOffset);
            return;
        }
        lastBgCameraX = Integer.MIN_VALUE;
        vscrollFactorBG = (short) computeMgz2BgY(camera.getY());
    }
}
