package com.openggf.graphics;

import com.openggf.level.render.SpritePieceRenderer;

/**
 * Transient SAT-like sprite entry kept at mapping-piece granularity so ROM-style
 * sprite-mask processing can happen before pieces are expanded into 8x8 tiles.
 */
public record SpriteSatEntry(
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
    public SpriteSatEntry {
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

    public static SpriteSatEntry of(
            int x,
            int y,
            int widthTiles,
            int heightTiles,
            int firstPatternIndex,
            int paletteIndex,
            boolean hFlip,
            boolean vFlip,
            boolean piecePriority,
            boolean globalHighPriority
    ) {
        return of(x, y, widthTiles, heightTiles, firstPatternIndex, firstPatternIndex & 0x7FF,
                paletteIndex, hFlip, vFlip, piecePriority, globalHighPriority);
    }

    public static SpriteSatEntry of(
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
            boolean globalHighPriority
    ) {
        return new SpriteSatEntry(
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
                SpriteMaskReplayRole.NORMAL,
                0,
                widthTiles,
                0,
                heightTiles);
    }

    public static SpriteSatEntry fromPreparedPiece(SpritePieceRenderer.PreparedPiece piece) {
        return new SpriteSatEntry(
                piece.x(),
                piece.y(),
                piece.widthTiles(),
                piece.heightTiles(),
                piece.firstPatternIndex(),
                piece.rawTileWordLow11(),
                piece.paletteIndex(),
                piece.hFlip(),
                piece.vFlip(),
                piece.piecePriority(),
                piece.globalHighPriority(),
                piece.maskReplayRole(),
                piece.startColTile(),
                piece.colCountTiles(),
                piece.startRowTile(),
                piece.rowCountTiles());
    }

    public SpritePieceRenderer.PreparedPiece toPreparedPiece() {
        return new SpritePieceRenderer.PreparedPiece(
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
                startColTile,
                colCountTiles,
                startRowTile,
                rowCountTiles);
    }

    public int endXExclusive() {
        return x + (widthTiles * 8);
    }

    public int endYExclusive() {
        return y + (heightTiles * 8);
    }

    public int tileWordLow11() {
        return rawTileWordLow11;
    }

    public boolean effectiveHighPriority() {
        return piecePriority || globalHighPriority;
    }

    public SpriteSatEntry clipRect(
            int clippedStartColTile,
            int clippedColCountTiles,
            int clippedStartRowTile,
            int clippedRowCountTiles
    ) {
        return new SpriteSatEntry(
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

    public SpriteSatEntry clipRows(int clippedStartRowTile, int clippedRowCountTiles) {
        return clipRect(startColTile, colCountTiles, clippedStartRowTile, clippedRowCountTiles);
    }

    public SpriteSatEntry withMaskReplayRole(SpriteMaskReplayRole overriddenMaskReplayRole) {
        return new SpriteSatEntry(
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
}
