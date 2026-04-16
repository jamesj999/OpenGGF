package com.openggf.level.scroll.compose;

/**
 * Lightweight running-offset helper for frame-local scroll table generation.
 *
 * <p>Scroll handlers use this to mirror ROM routines that keep a small amount of persistent
 * accumulator state between frames, such as drifting clouds or sine-phase offsets.
 */
public final class PersistentAccumulator {

    private int value;

    public PersistentAccumulator(int initialValue) {
        this.value = initialValue;
    }

    /** Returns the current accumulated value. */
    public int get() {
        return value;
    }

    /** Adds {@code delta} and returns the updated accumulator value. */
    public int add(int delta) {
        value += delta;
        return value;
    }

    /** Overwrites the accumulator with an externally computed value. */
    public void set(int value) {
        this.value = value;
    }
}
