package com.openggf.level.scroll;

/**
 * Base class providing shared scroll tracking and getters for
 * {@link ZoneScrollHandler} implementations.
 *
 * <p>Every zone scroll handler needs:
 * <ul>
 *   <li>{@code minScrollOffset} / {@code maxScrollOffset} — BG-FG delta bounds
 *       used by LevelManager for tile loading</li>
 *   <li>{@code vscrollFactorBG} — vertical scroll factor written to VSRAM</li>
 *   <li>{@link #trackOffset(short, short)} — per-line offset tracking</li>
 *   <li>{@link #resetScrollTracking()} — called at the top of each update()</li>
 * </ul>
 *
 * <p>Subclasses that work with pre-packed (FG|BG) longwords can use
 * {@link #trackOffsetFromPacked(int)} instead.
 */
public abstract class AbstractZoneScrollHandler implements ZoneScrollHandler {

    protected int minScrollOffset;
    protected int maxScrollOffset;
    protected short vscrollFactorBG;

    /**
     * Reset min/max scroll tracking at the start of each frame.
     */
    protected void resetScrollTracking() {
        minScrollOffset = Integer.MAX_VALUE;
        maxScrollOffset = Integer.MIN_VALUE;
    }

    /**
     * Track the BG-FG scroll offset for this scanline, updating min/max bounds.
     *
     * @param fgScroll foreground scroll value (signed 16-bit)
     * @param bgScroll background scroll value (signed 16-bit)
     */
    protected void trackOffset(short fgScroll, short bgScroll) {
        int offset = bgScroll - fgScroll;
        if (offset < minScrollOffset) {
            minScrollOffset = offset;
        }
        if (offset > maxScrollOffset) {
            maxScrollOffset = offset;
        }
    }

    /**
     * Track the BG-FG scroll offset from a packed (FG << 16 | BG) longword.
     *
     * @param packed packed scroll word (FG in high 16 bits, BG in low 16 bits)
     */
    protected void trackOffsetFromPacked(int packed) {
        short fg = (short) (packed >> 16);
        short bg = (short) packed;
        int offset = bg - fg;
        if (offset < minScrollOffset) {
            minScrollOffset = offset;
        }
        if (offset > maxScrollOffset) {
            maxScrollOffset = offset;
        }
    }

    @Override
    public int getMinScrollOffset() {
        return minScrollOffset;
    }

    @Override
    public int getMaxScrollOffset() {
        return maxScrollOffset;
    }

    @Override
    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }
}
