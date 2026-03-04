package com.openggf.level.rings;

import com.openggf.level.render.SpriteFramePiece;

/**
 * One sprite piece in a ring animation frame.
 */
public record RingFramePiece(
        int xOffset,
        int yOffset,
        int widthTiles,
        int heightTiles,
        int tileIndex,
        boolean hFlip,
        boolean vFlip,
        int paletteIndex
) implements SpriteFramePiece {
}
