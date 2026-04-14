package com.openggf.level.scroll.compose;

/**
 * Lightweight running-offset helper for frame-local scroll table generation.
 */
public final class PersistentAccumulator {

    private int value;

    public PersistentAccumulator(int initialValue) {
        this.value = initialValue;
    }

    public int get() {
        return value;
    }

    public int add(int delta) {
        value += delta;
        return value;
    }

    public void set(int value) {
        this.value = value;
    }
}
