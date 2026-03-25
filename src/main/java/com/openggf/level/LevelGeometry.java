package com.openggf.level;

/**
 * Immutable snapshot of level geometry dimensions, shared between
 * LevelManager and LevelTilemapManager to avoid back-references.
 */
public record LevelGeometry(
    Level level,
    int fgWidthPx, int fgHeightPx,
    int bgWidthPx, int bgContiguousWidthPx, int bgHeightPx,
    int blockPixelSize, int chunksPerBlockSide
) {}
