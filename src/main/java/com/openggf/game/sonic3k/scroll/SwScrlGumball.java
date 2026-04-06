package com.openggf.game.sonic3k.scroll;

import com.openggf.game.sonic3k.objects.GumballMachineObjectInstance;
import com.openggf.level.scroll.AbstractZoneScrollHandler;

import static com.openggf.level.scroll.M68KMath.*;

/**
 * Gumball Machine bonus stage scroll handler.
 *
 * <p>Implements per-column FG VSCROLL so the machine body tiles drift with
 * the gumball machine object while the surrounding walls stay locked to
 * the camera.
 *
 * <p>ROM reference: {@code Gumball_SetUpVScroll} / {@code Gumball_VScroll}
 * (s3.asm lines 76130-76147) runs every frame in {@code Gumball_ScreenEvent}:
 * <pre>
 *   d0 = Camera_Y_pos_copy
 *   d1 = machineY_saved - $C8 - cameraY
 *   d1 = -d1                           ; = cameraY + $C8 - machineY_saved
 *   HScroll_table+$0  = d0   (strip 0 FG: camera Y)
 *   HScroll_table+$4  = d1   (strip 1 FG: machine-tracked Y)
 *   HScroll_table+$8  = d0   (strip 2 FG: camera Y)
 *   HScroll_table+$C  = d0   (strip 3 FG: camera Y)
 *   HScroll_table+$10 = d0   (strip 4 FG: camera Y)
 * </pre>
 *
 * <p>The scatter array {@code Gumball_VScrollArray = {$C0, $80, $7FFF}} (192px,
 * 128px, terminator) is combined with the 5 VScroll values via
 * {@code DrawTilesVDeform} so that the machine-tracked strip coincides with the
 * body of the machine at world X around $100.
 *
 * <p>This implementation approximates the ROM behavior column-by-column: each
 * 16-pixel column whose world X falls inside the machine body range uses the
 * machine-tracked VScroll; every other column uses {@code cameraY}. This
 * mirrors the visual effect of the ROM's strip scatter without having to
 * replicate the VDP VSRAM scatter layout.
 */
public class SwScrlGumball extends AbstractZoneScrollHandler {

    /** ROM H40 display: 40 cells wide, column pairs of 16px each (20 entries). */
    private static final int COLUMN_COUNT = 20;

    /** ROM Gumball_SetUpVScroll: d1 = cameraY + $C8 - machineY_saved. */
    private static final int Y_BASE_OFFSET = 0xC8;

    /**
     * ROM Gumball_VScroll writes machineTrackedY only to HScroll_table offset $4
     * (= VSRAM entry for Plane A column-pair 1 = screen pixels 32-63).
     * DrawTilesVDeform with scatter array {$C0, $80, $7FFF} then distributes
     * VSRAM across 6+4 column-pairs, but only column-pair 1 has the machine
     * value — all others get cameraY.
     *
     * In the engine's 20-column system (16px each), column-pair 1 = columns 2-3.
     * Rather than hardcoding column indices, we use the world X range that
     * maps to those columns. With camera at ~0x60, column 2 starts at
     * 0x60 + 32 = 0x80, column 3 ends at 0x60 + 63 = 0x9F.
     * But the machine body chunks span 2 chunks (256px) centered at 0x100.
     * The actual visible machine tiles = chunks at X=[0x80, 0x180).
     *
     * The ROM uses a SINGLE 32px column-pair for the per-strip VSCROLL.
     * This works because DrawTilesVDeform redraws tiles at shifted positions
     * within each strip. Our shader-based approach applies VSCROLL to the
     * entire visible column, so we need to apply it to ALL columns that
     * overlap the machine body — otherwise only 32px of the body moves.
     */
    // Machine body tiles: chunks 1-2 span world X [0x80, 0x180).
    // Add 8px (half-column) inset on each side to prevent wall tiles from
    // being caught when a 16px column straddles the chunk boundary.
    private static final int MACHINE_BODY_MIN_X = 0x88;
    private static final int MACHINE_BODY_MAX_X = 0x178;

    private final short[] fgColumns = new short[COLUMN_COUNT];
    private boolean fgColumnsActive;
    private short vscrollFactorFG;

    @Override
    public void update(int[] horizScrollBuf,
                       int cameraX,
                       int cameraY,
                       int frameCounter,
                       int actId) {
        resetScrollTracking();

        // Standard flat deformation: FG tied to camera, BG at 1/2 vertical speed
        // (ROM Gumball_Deform at s3.asm:76172 sets Camera_Y_pos_BG_copy = cameraY/2).
        short fgScroll = negWord(cameraX);
        short bgScroll = negWord(cameraX);
        vscrollFactorBG = asrWord(cameraY, 1);
        vscrollFactorFG = (short) cameraY;

        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);
        for (int line = 0; line < VISIBLE_LINES; line++) {
            horizScrollBuf[line] = packed;
        }

        // ROM Gumball_SetUpVScroll: compute machine-tracked strip Y value.
        // d1 = cameraY + $C8 - machineY_saved.  (Use the drifted currentY so
        // the body tiles follow the machine as it sinks when gumballs drop.)
        GumballMachineObjectInstance machine = GumballMachineObjectInstance.current();
        if (machine == null) {
            // Fall back to flat FG VScroll when no machine is present.
            fgColumnsActive = false;
            return;
        }

        // The shader adds columnVScroll as a DELTA on top of WorldOffsetY (which
        // is already cameraY). So non-machine columns must be 0 (no extra offset).
        // Machine-tracked columns use a DELTA: the difference between machine-tracked
        // scroll and normal camera scroll.
        //
        // ROM: d1 = cameraY + $C8 - machineY (absolute VSCROLL for the strip).
        // As a delta from cameraY: delta = d1 - cameraY = $C8 - machineY.
        // When machine is at savedY (no drift): delta = $C8 - savedY.
        // As machine drifts DOWN (machineY increases): delta decreases (tiles scroll up
        // relative to camera) — which is correct: the tiles "stay with" the machine
        // while the camera pans normally.
        int machineY = machine.getCurrentY();
        short machineDelta = (short) (Y_BASE_OFFSET - machineY);

        // Log column classification on first frame for debugging
        if (!fgColumnsActive) {
            java.util.logging.Logger.getLogger("SwScrlGumball").info(
                    "VSCROLL columns: cameraX=" + cameraX
                            + " bodyRange=[" + MACHINE_BODY_MIN_X + "," + MACHINE_BODY_MAX_X + ")"
                            + " machineDelta=" + machineDelta);
        }

        int machineColCount = 0;
        for (int col = 0; col < COLUMN_COUNT; col++) {
            int worldX = cameraX + col * 16;
            if (worldX >= MACHINE_BODY_MIN_X && worldX < MACHINE_BODY_MAX_X) {
                fgColumns[col] = machineDelta;
                machineColCount++;
            } else {
                fgColumns[col] = 0;  // no extra offset — normal camera scroll
            }
        }

        if (!fgColumnsActive) {
            java.util.logging.Logger.getLogger("SwScrlGumball").info(
                    "VSCROLL: " + machineColCount + " machine cols, "
                            + (COLUMN_COUNT - machineColCount) + " wall cols");
        }
        fgColumnsActive = true;
    }

    @Override
    public short[] getPerColumnVScrollFG() {
        return fgColumnsActive ? fgColumns : null;
    }

    @Override
    public short getVscrollFactorFG() {
        return vscrollFactorFG;
    }
}
