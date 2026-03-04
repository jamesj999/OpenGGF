package com.openggf.game;

import java.io.IOException;

/**
 * Provides runtime start positions sourced from game-specific data (usually ROM tables).
 *
 * <p>This allows games like Sonic 3&K to use authoritative per-act start locations
 * instead of static enum defaults.
 */
public interface DynamicStartPositionProvider {
    /**
     * Returns the level start position for the given zone/act.
     *
     * @param zoneIndex zone index (0-based)
     * @param actIndex  act index (0-based)
     * @return [x, y] center coordinates, or null if unavailable
     * @throws IOException if ROM-backed lookup fails
     */
    int[] getStartPosition(int zoneIndex, int actIndex) throws IOException;
}
