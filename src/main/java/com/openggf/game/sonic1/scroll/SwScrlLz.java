package com.openggf.game.sonic1.scroll;

import com.openggf.camera.Camera;
import com.openggf.level.scroll.AbstractZoneScrollHandler;
import static com.openggf.level.scroll.M68KMath.*;

/**
 * ROM-accurate implementation of Deform_LZ (Labyrinth Zone scroll routine).
 * Reference: s1disasm/_inc/DeformLayers.asm - Deform_LZ
 *
 * LZ has the simplest scroll routine:
 * <ul>
 *   <li>BG X: scrolls at 50% of camera speed (scrshiftx &lt;&lt; 7 = 128/256)</li>
 *   <li>BG Y: scrolls at 50% of camera speed (scrshifty &lt;&lt; 7 = 128/256)</li>
 *   <li>H-scroll: uniform (all 224 lines have the same FG/BG values)</li>
 * </ul>
 *
 * Initial BG position: BgScroll_LZ sets bgscreenposy = screenposy &gt;&gt; 1.
 * Uses ScrollBlock1 for per-frame accumulation.
 */
public class SwScrlLz extends AbstractZoneScrollHandler {

    // Persistent BG camera positions (16.16 fixed point)
    private long bgXPos;
    private long bgYPos;

    private int lastCameraX;
    private int lastCameraY;
    private boolean initialized = false;

    /**
     * Initialize BG camera positions.
     * BgScrollSpeed sets bgscreenposx = screenposx, then BgScroll_LZ
     * overrides bgscreenposy = screenposy / 2.
     */
    public void init(int cameraX, int cameraY) {
        bgXPos = (long) cameraX << 16;
        // BgScroll_LZ: asr.l #1,d0 → bgscreenposy = cameraY / 2
        bgYPos = (long) (cameraY >> 1) << 16;
        lastCameraX = cameraX;
        lastCameraY = cameraY;
        initialized = true;
    }

    @Override
    public void update(int[] horizScrollBuf,
            int cameraX,
            int cameraY,
            int frameCounter,
            int actId) {
        if (!initialized) {
            init(cameraX, cameraY);
        }

        resetScrollTracking();

        // Compute deltas
        int deltaX = cameraX - lastCameraX;
        int deltaY = cameraY - lastCameraY;

        // ROM: When vertical wrapping is active (LZ3/SBZ2), a wrap can cause a
        // huge delta (e.g. camera jumps from ~2047 to ~0). Detect and correct
        // by checking if |deltaY| exceeds half the wrap range.
        Camera camera = Camera.getInstance();
        if (camera.isVerticalWrapEnabled()) {
            int halfRange = Camera.getVerticalWrapRange() / 2; // 0x400
            if (deltaY > halfRange) {
                deltaY -= Camera.getVerticalWrapRange();
            } else if (deltaY < -halfRange) {
                deltaY += Camera.getVerticalWrapRange();
            }
        }

        lastCameraX = cameraX;
        lastCameraY = cameraY;

        // ScrollBlock1: d4 = scrshiftx << 7 = deltaX * 128 * 256
        bgXPos += (long) deltaX * 128 * 256;
        // ScrollBlock1: d5 = scrshifty << 7 = deltaY * 128 * 256
        bgYPos += (long) deltaY * 128 * 256;

        int bgX = (int) (bgXPos >> 16);
        int bgY = (int) (bgYPos >> 16);

        // ROM: When vertical wrapping is active, apply BG Y mask (AND 0x3FF)
        if (camera.isVerticalWrapEnabled()) {
            bgY &= Camera.getVerticalWrapBgMask();
            // Also constrain the accumulator to prevent drift
            bgYPos = (long) bgY << 16 | (bgYPos & 0xFFFF);
        }

        vscrollFactorBG = (short) bgY;

        // FG and BG scroll values (constant for all lines)
        short fgScroll = negWord(cameraX);
        short bgScroll = negWord(bgX);

        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);

        // Uniform: all 224 lines have the same value
        for (int i = 0; i < VISIBLE_LINES; i++) {
            horizScrollBuf[i] = packed;
        }
    }

}
