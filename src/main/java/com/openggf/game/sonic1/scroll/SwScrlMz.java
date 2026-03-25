package com.openggf.game.sonic1.scroll;

import com.openggf.level.scroll.AbstractZoneScrollHandler;
import static com.openggf.level.scroll.M68KMath.*;

/**
 * ROM-accurate implementation of REV01 Deform_MZ (Marble Zone scroll routine).
 * Reference: s1disasm/_inc/DeformLayers (JP1).asm - Deform_MZ
 *
 * REV01 MZ uses four horizontal parallax bands selected by BG Y:
 * <ul>
 *   <li>Cloud blend strip (5 entries): interpolates from 1/2x toward 1/4x</li>
 *   <li>Mountains (2 entries): BG3 at 1/4x (scrshiftx * 64)</li>
 *   <li>Bushes/buildings (9 entries): BG2 at 1/2x (scrshiftx * 128)</li>
 *   <li>Dungeon interior (16 entries): BG1 at 3/4x (scrshiftx * 192)</li>
 * </ul>
 *
 * Vertical behavior:
 * <ul>
 *   <li>BG Y baseline is 0x200</li>
 *   <li>If cameraY &gt;= 0x1C8, BG rises by (cameraY - 0x1C8) * 3/4</li>
 *   <li>The 32-entry scroll buffer is sampled by BG Y in 16-line blocks</li>
 * </ul>
 */
public class SwScrlMz extends AbstractZoneScrollHandler {

    private static final int LINES_PER_GROUP = 16;
    private static final int CLOUD_ENTRY_COUNT = 5;
    private static final int MOUNTAIN_ENTRY_COUNT = 2;
    private static final int BUSH_ENTRY_COUNT = 9;
    private static final int INTERIOR_ENTRY_COUNT = 16;
    private static final int SCROLL_BUFFER_SIZE =
            CLOUD_ENTRY_COUNT + MOUNTAIN_ENTRY_COUNT + BUSH_ENTRY_COUNT + INTERIOR_ENTRY_COUNT;

    // REV01 has three independent BG X cameras:
    // BG1 (interior) at 3/4x, BG2 (bushes) at 1/2x, BG3 (mountains) at 1/4x.
    private long bg1XPos;
    private long bg2XPos;
    private long bg3XPos;

    private int lastCameraX;
    private boolean initialized = false;

    // Deform_MZ fills a 32-word buffer, then Bg_Scroll_X expands to per-line hscroll.
    private final short[] scrollBuffer = new short[SCROLL_BUFFER_SIZE];

    public void init(int cameraX) {
        // BgScrollSpeed seeds all MZ X positions from screenposx.
        bg1XPos = (long) cameraX << 16;
        bg2XPos = (long) cameraX << 16;
        bg3XPos = (long) cameraX << 16;
        lastCameraX = cameraX;
        initialized = true;
    }

    @Override
    public void update(int[] horizScrollBuf,
            int cameraX,
            int cameraY,
            int frameCounter,
            int actId) {
        if (!initialized) {
            init(cameraX);
        }

        resetScrollTracking();

        // Compute delta
        int deltaX = cameraX - lastCameraX;
        lastCameraX = cameraX;

        // Deform_MZ (REV01):
        // BG1 (interior): scrshiftx * 192 (3/4x)
        // BG3 (mountains): scrshiftx * 64  (1/4x)
        // BG2 (bushes):    scrshiftx * 128 (1/2x)
        bg1XPos += (long) deltaX * 49152;
        bg3XPos += (long) deltaX * 16384;
        bg2XPos += (long) deltaX * 32768;

        int bg1X = (int) (bg1XPos >> 16);
        int bg2X = (int) (bg2XPos >> 16);
        int bg3X = (int) (bg3XPos >> 16);

        // BG Y (base 0x200, then + 3/4 above threshold 0x1C8).
        int bgY = 0x200;
        int yOffset = cameraY - 0x1C8;
        if (yOffset >= 0) {
            bgY += (yOffset * 3) >> 2;
        }
        vscrollFactorBG = (short) bgY;

        buildScrollBuffer(cameraX, bg3X, bg2X, bg1X);
        writeHScrollBuffer(horizScrollBuf, cameraX, bgY - 0x200);
    }

    private void buildScrollBuffer(int cameraX, int bg3X, int bg2X, int bg1X) {
        int d2 = negWord(cameraX);
        int idx = 0;

        // Cloud blend (5 entries): starts at 1/2x and steps toward 1/4x.
        int stepSource = asrWord(d2, 2) - d2;
        short step = (short) (stepSource / 5);
        long increment16 = (long) step << 12;
        long current = (long) asrWord(d2, 1) << 16;
        for (int i = 0; i < CLOUD_ENTRY_COUNT; i++) {
            scrollBuffer[idx++] = (short) (current >> 16);
            current += increment16;
        }

        // Mountains (2 entries): BG3
        short mountainScroll = negWord(bg3X);
        for (int i = 0; i < MOUNTAIN_ENTRY_COUNT; i++) {
            scrollBuffer[idx++] = mountainScroll;
        }

        // Bushes/buildings (9 entries): BG2
        short bushScroll = negWord(bg2X);
        for (int i = 0; i < BUSH_ENTRY_COUNT; i++) {
            scrollBuffer[idx++] = bushScroll;
        }

        // Dungeon interior (16 entries): BG1
        short interiorScroll = negWord(bg1X);
        for (int i = 0; i < INTERIOR_ENTRY_COUNT; i++) {
            scrollBuffer[idx++] = interiorScroll;
        }
    }

    private void writeHScrollBuffer(int[] horizScrollBuf, int cameraX, int bgYRelativeToBase) {
        short fgScroll = negWord(cameraX);

        // Deform_MZ: clamp lookup start to 0x100 before selecting 16-line band.
        int startWordOffset = bgYRelativeToBase;
        if (startWordOffset >= 0x100) {
            startWordOffset = 0x100;
        }
        startWordOffset &= 0x1F0;
        int scrollBufferIndex = startWordOffset >> 4;

        int subAlign = bgYRelativeToBase & 0xF;
        int lineIndex = 0;

        // First partial 16-line group (when BG Y is not 16-aligned).
        if (subAlign != 0 && scrollBufferIndex < SCROLL_BUFFER_SIZE) {
            lineIndex = fillLines(horizScrollBuf, lineIndex, LINES_PER_GROUP - subAlign, fgScroll,
                    scrollBuffer[scrollBufferIndex++]);
        }

        // Full 16-line groups.
        while (lineIndex < VISIBLE_LINES && scrollBufferIndex < SCROLL_BUFFER_SIZE) {
            lineIndex = fillLines(horizScrollBuf, lineIndex, LINES_PER_GROUP, fgScroll,
                    scrollBuffer[scrollBufferIndex++]);
        }

        // Safety fill for any remaining lines (should normally not be needed).
        short fallback = scrollBuffer[SCROLL_BUFFER_SIZE - 1];
        while (lineIndex < VISIBLE_LINES) {
            lineIndex = fillLines(horizScrollBuf, lineIndex, LINES_PER_GROUP, fgScroll, fallback);
        }
    }

    private int fillLines(int[] horizScrollBuf, int lineStart, int lineCount, short fgScroll, short bgScroll) {
        int packed = packScrollWords(fgScroll, bgScroll);
        trackOffset(fgScroll, bgScroll);

        int line = lineStart;
        int limit = Math.min(VISIBLE_LINES, lineStart + lineCount);
        for (; line < limit; line++) {
            horizScrollBuf[line] = packed;
        }
        return line;
    }

}
