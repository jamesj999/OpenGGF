package com.openggf.graphics;

import com.openggf.game.titlecard.TitleCardMappings;
import com.openggf.level.PatternDesc;

/**
 * Shared sprite-piece renderer for title card managers.
 *
 * <p>All three title card managers (S1, S2, S3K) use structurally identical logic
 * to render mapping frames: iterate tiles in column-major VDP order, apply flip
 * adjustments, build a {@link PatternDesc}, and call
 * {@link GraphicsManager#renderPatternWithId}. This utility extracts that common
 * rendering loop.
 */
public final class TitleCardSpriteRenderer {

    private TitleCardSpriteRenderer() {}

    /**
     * Renders a single sprite piece using column-major VDP tile ordering.
     *
     * @param gm           graphics manager for rendering
     * @param piece        the sprite piece to render (offsets, size, tile, flip, palette)
     * @param originX      screen X origin (element center X)
     * @param originY      screen Y origin (element center Y)
     * @param vramBase     VRAM tile base to subtract from piece tileIndex (0 for S1 where
     *                     tile indices are already 0-based array offsets)
     * @param patternBase  virtual pattern ID base added to the array index
     * @param arraySize    bounds limit for the tile array (tiles beyond this are skipped)
     */
    public static void renderSpritePiece(GraphicsManager gm,
                                         TitleCardMappings.SpritePiece piece,
                                         int originX, int originY,
                                         int vramBase, int patternBase,
                                         int arraySize) {
        int baseTileIndex = piece.tileIndex();
        int arrayIndex = baseTileIndex - vramBase;
        if (arrayIndex < 0) {
            arrayIndex = 0;
        }

        int widthTiles = piece.widthTiles();
        int heightTiles = piece.heightTiles();

        // Render each 8x8 tile in the piece (column-major order like VDP)
        for (int tx = 0; tx < widthTiles; tx++) {
            for (int ty = 0; ty < heightTiles; ty++) {
                int tileOffset = tx * heightTiles + ty;
                int idx = arrayIndex + tileOffset;

                // Bounds check
                if (idx < 0 || idx >= arraySize) {
                    continue;
                }

                int patternId = patternBase + idx;

                // Calculate screen position
                int tileX = originX + piece.xOffset() + (tx * 8);
                int tileY = originY + piece.yOffset() + (ty * 8);

                // Handle flipping - swap column/row order
                if (piece.hFlip()) {
                    tileX = originX + piece.xOffset() + ((widthTiles - 1 - tx) * 8);
                }
                if (piece.vFlip()) {
                    tileY = originY + piece.yOffset() + ((heightTiles - 1 - ty) * 8);
                }

                // Build PatternDesc with flip flags and palette
                int descBits = patternId & 0x7FF;
                if (piece.hFlip()) {
                    descBits |= 0x800;
                }
                if (piece.vFlip()) {
                    descBits |= 0x1000;
                }
                descBits |= (piece.paletteIndex() & 0x3) << 13;
                PatternDesc desc = new PatternDesc(descBits);

                gm.renderPatternWithId(patternId, desc, tileX, tileY);
            }
        }
    }
}
