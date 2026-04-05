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
     * World X range of the machine body. The gumball machine spawns around
     * world X $100, with platform offsets covering roughly ±$40. Widening the
     * range to {@code [0x80, 0x180)} captures the machine frame plus the side
     * caps, matching the body sprite footprint. Columns outside this range
     * belong to the stage walls and scroll with the camera.
     */
    private static final int MACHINE_BODY_MIN_X = 0x80;
    private static final int MACHINE_BODY_MAX_X = 0x180;

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

        int machineY = machine.getCurrentY();
        short machineTrackedY = (short) (cameraY + Y_BASE_OFFSET - machineY);
        short cameraTrackedY = (short) cameraY;

        // Per-column scatter: columns overlapping the machine body use the
        // machine-tracked Y, columns outside use the camera Y.
        for (int col = 0; col < COLUMN_COUNT; col++) {
            int worldX = cameraX + col * 16;
            if (worldX >= MACHINE_BODY_MIN_X && worldX < MACHINE_BODY_MAX_X) {
                fgColumns[col] = machineTrackedY;
            } else {
                fgColumns[col] = cameraTrackedY;
            }
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
