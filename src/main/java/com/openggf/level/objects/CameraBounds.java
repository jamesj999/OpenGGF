package com.openggf.level.objects;

/**
 * Pre-computed camera visibility bounds, updated once per frame.
 * ROM equivalent: Objects check against Camera_X_pos/Camera_Y_pos in MarkObjGone.
 * By caching these values, we avoid repeated Camera.getInstance() calls
 * and field reads when checking visibility for many objects.
 *
 * Mutable to avoid per-frame allocation - use update() to change values.
 */
public final class CameraBounds {
    private int left;
    private int top;
    private int right;
    private int bottom;

    // Vertical wrap range for modular Y checks (0 = no wrapping).
    // When > 0, Y visibility uses modular arithmetic to emulate VDP coordinate wrapping.
    private int verticalWrapRange = 0;

    public CameraBounds(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /**
     * Updates all bounds in place, avoiding allocation.
     */
    public void update(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    /**
     * Sets the vertical wrap range for modular Y checks.
     * @param range Wrap range in pixels (e.g. 2048 for LZ3/SBZ2), or 0 to disable.
     */
    public void setVerticalWrapRange(int range) {
        this.verticalWrapRange = range;
    }

    public int left() { return left; }
    public int top() { return top; }
    public int right() { return right; }
    public int bottom() { return bottom; }

    /**
     * Checks if a point is within these bounds.
     * When vertical wrapping is active, Y is checked using modular arithmetic.
     */
    public boolean contains(int x, int y) {
        if (x < left || x > right) return false;
        return containsY(y, 0);
    }

    /**
     * Checks if an X coordinate is within horizontal bounds.
     * Matches ROM's MarkObjGone which only checks X distance for the on_screen flag.
     */
    public boolean containsX(int x) {
        return x >= left && x <= right;
    }

    /**
     * Checks if an X coordinate is within horizontal bounds, expanded by a margin.
     */
    public boolean containsX(int x, int margin) {
        return x >= left - margin && x <= right + margin;
    }

    /**
     * Checks if a point is within these bounds with a margin.
     * When vertical wrapping is active, Y is checked using modular arithmetic.
     */
    public boolean contains(int x, int y, int margin) {
        if (x < left - margin || x > right + margin) return false;
        return containsY(y, margin);
    }

    /**
     * Checks if a Y coordinate is within the vertical bounds, optionally with margin.
     * When vertical wrapping is active, uses modular arithmetic: computes the shortest
     * distance in the wrapped space and checks if it falls within the screen height.
     */
    private boolean containsY(int y, int margin) {
        if (verticalWrapRange > 0) {
            int adjustedTop = top - margin;
            int height = (bottom - top) + 2 * margin;
            int diff = y - adjustedTop;
            diff = ((diff % verticalWrapRange) + verticalWrapRange) % verticalWrapRange;
            return diff <= height;
        }
        return y >= top - margin && y <= bottom + margin;
    }
}
