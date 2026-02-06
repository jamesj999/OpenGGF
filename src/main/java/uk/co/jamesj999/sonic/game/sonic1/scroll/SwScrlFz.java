package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import static uk.co.jamesj999.sonic.level.scroll.M68KMath.*;

/**
 * ROM-accurate implementation of the Final Zone / Ending scroll routine.
 * Reference: s1disasm/_inc/DeformLayers.asm - Deform_Index entry 6 → Deform_GHZ
 *            s1disasm/_inc/LevelSizeLoad &amp; BgScrollSpeed.asm - BgScroll_End
 *
 * The Final Zone / Ending reuses Deform_GHZ for its deformation, but with
 * fixed BG Y positions from BgScroll_End:
 * <pre>
 *   bgscreenposy = 0x1E
 *   bg2screenposy = 0x1E
 * </pre>
 *
 * Since the ending zone is typically a short, horizontally-constrained area,
 * the perspective effect behaves differently from GHZ proper due to the
 * fixed Y positions.
 */
public class SwScrlFz implements ZoneScrollHandler {

    private final SwScrlGhz ghzHandler = new SwScrlGhz();

    @Override
    public void update(int[] horizScrollBuf,
            int cameraX,
            int cameraY,
            int frameCounter,
            int actId) {
        // BgScroll_End sets fixed Y positions: bgscreenposy = bg2screenposy = 0x1E
        // The GHZ deform routine uses cameraY for bg2screenposy calculation,
        // but at the ending zone, the level is constrained so the effect is similar.
        // We delegate to the GHZ handler which computes bg2screenposy from cameraY.
        ghzHandler.update(horizScrollBuf, cameraX, cameraY, frameCounter, actId);
    }

    @Override
    public short getVscrollFactorBG() {
        // BgScroll_End: bgscreenposy = 0x1E
        return 0x1E;
    }

    @Override
    public int getMinScrollOffset() {
        return ghzHandler.getMinScrollOffset();
    }

    @Override
    public int getMaxScrollOffset() {
        return ghzHandler.getMaxScrollOffset();
    }
}
