package com.openggf.level.render;

/**
 * Common sprite frame piece contract for pattern-based renderers.
 */
public interface SpriteFramePiece {
    int xOffset();
    int yOffset();
    int widthTiles();
    int heightTiles();
    int tileIndex();
    boolean hFlip();
    boolean vFlip();
    int paletteIndex();

    /**
     * VDP priority flag. When true, the sprite is rendered in front of the playfield.
     * Used by objects like the CNZ LauncherSpring to create a visual "flash" effect
     * by toggling between priority=0 and priority=1 frames.
     */
    default boolean priority() {
        return false;
    }
}
