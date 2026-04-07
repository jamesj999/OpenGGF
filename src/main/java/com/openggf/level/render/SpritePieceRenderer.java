package com.openggf.level.render;

import com.openggf.graphics.SpriteMaskReplayRole;
import com.openggf.level.Pattern;

import java.util.List;

/**
 * Shared sprite-piece renderer for pattern-based sprites.
 */
public final class SpritePieceRenderer {
    private SpritePieceRenderer() {
    }

    public record PreparedPiece(
            int x,
            int y,
            int widthTiles,
            int heightTiles,
            int firstPatternIndex,
            int rawTileWordLow11,
            int paletteIndex,
            boolean hFlip,
            boolean vFlip,
            boolean piecePriority,
            boolean globalHighPriority,
            SpriteMaskReplayRole maskReplayRole,
            int startColTile,
            int colCountTiles,
            int startRowTile,
            int rowCountTiles
    ) {
        public PreparedPiece {
            if (widthTiles < 0 || heightTiles < 0) {
                throw new IllegalArgumentException("Tile dimensions must be non-negative");
            }
            if (startColTile < 0 || colCountTiles < 0 || startColTile + colCountTiles > widthTiles) {
                throw new IllegalArgumentException("Clipped columns must stay within the piece width");
            }
            if (startRowTile < 0 || rowCountTiles < 0 || startRowTile + rowCountTiles > heightTiles) {
                throw new IllegalArgumentException("Clipped rows must stay within the piece height");
            }
        }

        public PreparedPiece(
                int x,
                int y,
                int widthTiles,
                int heightTiles,
                int firstPatternIndex,
                int rawTileWordLow11,
                int paletteIndex,
                boolean hFlip,
                boolean vFlip,
                boolean piecePriority,
                boolean globalHighPriority,
                int startRowTile,
                int rowCountTiles
        ) {
            this(x, y, widthTiles, heightTiles, firstPatternIndex, rawTileWordLow11, paletteIndex,
                    hFlip, vFlip, piecePriority, globalHighPriority,
                    SpriteMaskReplayRole.NORMAL, 0, widthTiles, startRowTile, rowCountTiles);
        }

        public int endXExclusive() {
            return x + (widthTiles * Pattern.PATTERN_WIDTH);
        }

        public int endYExclusive() {
            return y + (heightTiles * Pattern.PATTERN_HEIGHT);
        }

        public PreparedPiece clipRect(
                int clippedStartColTile,
                int clippedColCountTiles,
                int clippedStartRowTile,
                int clippedRowCountTiles
        ) {
            return new PreparedPiece(
                    x,
                    y,
                    widthTiles,
                    heightTiles,
                    firstPatternIndex,
                    rawTileWordLow11,
                    paletteIndex,
                    hFlip,
                    vFlip,
                    piecePriority,
                    globalHighPriority,
                    maskReplayRole,
                    clippedStartColTile,
                    clippedColCountTiles,
                    clippedStartRowTile,
                    clippedRowCountTiles);
        }

        public PreparedPiece clipRows(int clippedStartRowTile, int clippedRowCountTiles) {
            return clipRect(startColTile, colCountTiles, clippedStartRowTile, clippedRowCountTiles);
        }

        public PreparedPiece withMaskReplayRole(SpriteMaskReplayRole overriddenMaskReplayRole) {
            return new PreparedPiece(
                    x,
                    y,
                    widthTiles,
                    heightTiles,
                    firstPatternIndex,
                    rawTileWordLow11,
                    paletteIndex,
                    hFlip,
                    vFlip,
                    piecePriority,
                    globalHighPriority,
                    overriddenMaskReplayRole,
                    startColTile,
                    colCountTiles,
                    startRowTile,
                    rowCountTiles);
        }

        public PreparedPiece withPriorityFlags(boolean overriddenPiecePriority, boolean overriddenGlobalHighPriority) {
            return new PreparedPiece(
                    x,
                    y,
                    widthTiles,
                    heightTiles,
                    firstPatternIndex,
                    rawTileWordLow11,
                    paletteIndex,
                    hFlip,
                    vFlip,
                    overriddenPiecePriority,
                    overriddenGlobalHighPriority,
                    maskReplayRole,
                    startColTile,
                    colCountTiles,
                    startRowTile,
                    rowCountTiles);
        }
    }

    @FunctionalInterface
    public interface TileConsumer {
        void render(int patternIndex, boolean hFlip, boolean vFlip, int paletteIndex, int drawX, int drawY);
    }

    @FunctionalInterface
    public interface PieceConsumer {
        void render(PreparedPiece piece);
    }

    /**
     * Renders a single sprite piece without wrapping in a list.
     */
    public static void renderPiece(
            SpriteFramePiece piece,
            int originX,
            int originY,
            int basePatternIndex,
            int defaultPaletteIndex,
            boolean frameHFlip,
            boolean frameVFlip,
            TileConsumer consumer
    ) {
        if (piece == null || consumer == null) {
            return;
        }
        preparePiece(piece, originX, originY, basePatternIndex, defaultPaletteIndex,
                frameHFlip, frameVFlip, false, preparedPiece -> renderPreparedPiece(preparedPiece, consumer));
    }

    public static void renderPieces(
            List<? extends SpriteFramePiece> pieces,
            int originX,
            int originY,
            int basePatternIndex,
            int defaultPaletteIndex,
            boolean frameHFlip,
            boolean frameVFlip,
            TileConsumer consumer
    ) {
        if (pieces == null || consumer == null) {
            return;
        }
        for (SpriteFramePiece piece : pieces) {
            preparePiece(piece, originX, originY, basePatternIndex, defaultPaletteIndex,
                    frameHFlip, frameVFlip, false, preparedPiece -> renderPreparedPiece(preparedPiece, consumer));
        }
    }

    public static void preparePiece(
            SpriteFramePiece piece,
            int originX,
            int originY,
            int basePatternIndex,
            int defaultPaletteIndex,
            boolean frameHFlip,
            boolean frameVFlip,
            boolean globalHighPriority,
            PieceConsumer consumer
    ) {
        if (piece == null || consumer == null) {
            return;
        }
        int widthTiles = piece.widthTiles();
        int heightTiles = piece.heightTiles();
        int widthPixels = widthTiles * Pattern.PATTERN_WIDTH;
        int heightPixels = heightTiles * Pattern.PATTERN_HEIGHT;

        int pieceXOffset = piece.xOffset();
        int pieceYOffset = piece.yOffset();
        boolean pieceHFlip = piece.hFlip();
        boolean pieceVFlip = piece.vFlip();

        if (frameHFlip) {
            pieceXOffset = -pieceXOffset - widthPixels;
            pieceHFlip = !pieceHFlip;
        }
        if (frameVFlip) {
            pieceYOffset = -pieceYOffset - heightPixels;
            pieceVFlip = !pieceVFlip;
        }

        int pieceX = originX + pieceXOffset;
        int pieceY = originY + pieceYOffset;
        // If defaultPaletteIndex is negative, use piece's palette directly (absolute).
        // Otherwise, ADD piece's palette to the default (matching Sonic 2 art_tile behavior
        // where the pattern name word is added to art_tile, including palette bits).
        int paletteIndex;
        if (defaultPaletteIndex < 0) {
            paletteIndex = piece.paletteIndex();
        } else {
            int paletteBankBits = defaultPaletteIndex & ~0x3;
            int paletteLineLowBits = (piece.paletteIndex() + defaultPaletteIndex) & 0x3;
            paletteIndex = paletteBankBits | paletteLineLowBits;
        }

        consumer.render(new PreparedPiece(
                pieceX,
                pieceY,
                widthTiles,
                heightTiles,
                basePatternIndex + piece.tileIndex(),
                piece.tileIndex() & 0x7FF,
                paletteIndex,
                pieceHFlip,
                pieceVFlip,
                piece.priority(),
                globalHighPriority,
                SpriteMaskReplayRole.NORMAL,
                0,
                widthTiles,
                0,
                heightTiles));
    }

    public static void renderPreparedPiece(PreparedPiece piece, TileConsumer consumer) {
        if (piece == null || consumer == null) {
            return;
        }
        for (int ty = 0; ty < piece.rowCountTiles(); ty++) {
            int originalRow = piece.startRowTile() + ty;
            for (int tx = 0; tx < piece.colCountTiles(); tx++) {
                int originalCol = piece.startColTile() + tx;
                int srcX = piece.hFlip() ? (piece.widthTiles() - 1 - originalCol) : originalCol;
                int srcY = piece.vFlip() ? (piece.heightTiles() - 1 - originalRow) : originalRow;
                int tileOffset = (originalCol * piece.heightTiles()) + originalRow;
                int patternIndex = piece.firstPatternIndex() + tileOffset;

                int drawX = piece.x() + (srcX * Pattern.PATTERN_WIDTH);
                int drawY = piece.y() + (srcY * Pattern.PATTERN_HEIGHT);

                consumer.render(patternIndex, piece.hFlip(), piece.vFlip(), piece.paletteIndex(), drawX, drawY);
            }
        }
    }

    public static FrameBounds computeFrameBounds(
            List<? extends SpriteFramePiece> pieces,
            boolean frameHFlip,
            boolean frameVFlip
    ) {
        if (pieces == null || pieces.isEmpty()) {
            return new FrameBounds(0, 0, -1, -1);
        }
        boolean first = true;
        int minX = 0;
        int minY = 0;
        int maxX = 0;
        int maxY = 0;
        for (SpriteFramePiece piece : pieces) {
            int widthPixels = piece.widthTiles() * Pattern.PATTERN_WIDTH;
            int heightPixels = piece.heightTiles() * Pattern.PATTERN_HEIGHT;
            int pieceXOffset = piece.xOffset();
            int pieceYOffset = piece.yOffset();

            if (frameHFlip) {
                pieceXOffset = -pieceXOffset - widthPixels;
            }
            if (frameVFlip) {
                pieceYOffset = -pieceYOffset - heightPixels;
            }

            int left = pieceXOffset;
            int top = pieceYOffset;
            int right = pieceXOffset + widthPixels - 1;
            int bottom = pieceYOffset + heightPixels - 1;

            if (first) {
                minX = left;
                minY = top;
                maxX = right;
                maxY = bottom;
                first = false;
            } else {
                minX = Math.min(minX, left);
                minY = Math.min(minY, top);
                maxX = Math.max(maxX, right);
                maxY = Math.max(maxY, bottom);
            }
        }
        if (first) {
            return new FrameBounds(0, 0, -1, -1);
        }
        return new FrameBounds(minX, minY, maxX, maxY);
    }

    public record FrameBounds(int minX, int minY, int maxX, int maxY) {
        public int width() {
            return maxX >= minX ? maxX - minX + 1 : 0;
        }

        public int height() {
            return maxY >= minY ? maxY - minY + 1 : 0;
        }
    }
}
