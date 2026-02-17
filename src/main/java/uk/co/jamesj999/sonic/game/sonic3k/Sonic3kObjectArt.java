package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Builds object sprite sheets for S3K objects.
 * Many S3K objects use level patterns rather than dedicated compressed art.
 */
public class Sonic3kObjectArt {
    private static final Logger LOG = Logger.getLogger(Sonic3kObjectArt.class.getName());

    private final Level level;

    public Sonic3kObjectArt(Level level) {
        this.level = level;
    }

    /**
     * Builds a sprite sheet from level patterns for an object that uses level art.
     *
     * @param artTileBase     the art_tile base index (added to piece tile indices)
     * @param sheetPalette    the sheet palette line (0-3)
     * @param frames          the mapping frames
     * @param minTile         the minimum tile index used across all pieces
     * @param maxTileExclusive one past the maximum tile index used
     * @return the sprite sheet, or null if level lacks needed patterns
     */
    public ObjectSpriteSheet buildLevelArtSheet(int artTileBase, int sheetPalette,
            List<SpriteMappingFrame> frames, int minTile, int maxTileExclusive) {
        if (level == null) {
            return null;
        }

        int patternCount = maxTileExclusive - minTile;
        Pattern[] patterns = new Pattern[patternCount];
        int levelPatternCount = level.getPatternCount();

        for (int i = 0; i < patternCount; i++) {
            int levelIndex = artTileBase + minTile + i;
            if (levelIndex < levelPatternCount) {
                patterns[i] = level.getPattern(levelIndex);
            } else {
                patterns[i] = new Pattern();
                LOG.fine("Level pattern " + levelIndex + " out of range (count=" + levelPatternCount + ")");
            }
        }

        // Adjust tile indices in frames by -minTile so they're 0-based
        List<SpriteMappingFrame> adjusted = adjustTileIndices(frames, -minTile);
        return new ObjectSpriteSheet(patterns, adjusted, sheetPalette, 1);
    }

    /**
     * Builds the AIZ1Tree sprite sheet.
     * <p>
     * From disassembly (Map - Act 1 Tree.asm):
     * art_tile = make_art_tile($001, 2, 0) → base tile 1, palette 2
     * Mapping: 1 frame, 3 pieces (2x2 each), tile index 0x38
     * Y offsets: -24, -8, +8; X offset: -8
     */
    public ObjectSpriteSheet buildAiz1TreeSheet() {
        // Mapping pieces from Map - Act 1 Tree.asm
        // 3 pieces, each 2x2 tiles (16x16px), all tile index 0x38, palette 0
        List<SpriteMappingPiece> pieces = List.of(
                new SpriteMappingPiece(-8, -24, 2, 2, 0x38, false, false, 0),
                new SpriteMappingPiece(-8, -8, 2, 2, 0x38, false, false, 0),
                new SpriteMappingPiece(-8, 8, 2, 2, 0x38, false, false, 0)
        );
        SpriteMappingFrame frame = new SpriteMappingFrame(pieces);
        List<SpriteMappingFrame> frames = List.of(frame);

        // art_tile base = 1, palette = 2
        // Tile range: 0x38 to 0x38+3 = 0x3B (each 2x2 piece uses 4 tiles)
        // minTile = 0x38, maxTileExclusive = 0x3C
        return buildLevelArtSheet(1, 2, frames, 0x38, 0x3C);
    }

    /**
     * Builds the AIZ1ZiplinePeg sprite sheet.
     * <p>
     * From disassembly (Map - Act 1 Zipline Peg.asm):
     * art_tile = make_art_tile(ArtTile_AIZSlideRope, 2, 0) → base tile 0x324, palette 2
     * Mapping: 1 frame, 3 pieces
     * Piece 0: 4x1 (32x8px), tile 0, Y=-12, X=-32
     * Piece 1: 2x1 (16x8px), tile 4, Y=-4, X=-8
     * Piece 2: 3x3 (24x24px), tile 6, Y=-12, X=+8
     */
    public ObjectSpriteSheet buildAiz1ZiplinePegSheet() {
        // Mapping pieces from Map - Act 1 Zipline Peg.asm
        List<SpriteMappingPiece> pieces = List.of(
                new SpriteMappingPiece(-32, -12, 4, 1, 0, false, false, 0),
                new SpriteMappingPiece(-8, -4, 2, 1, 4, false, false, 0),
                new SpriteMappingPiece(8, -12, 3, 3, 6, false, false, 0)
        );
        SpriteMappingFrame frame = new SpriteMappingFrame(pieces);
        List<SpriteMappingFrame> frames = List.of(frame);

        // art_tile base = 0x324, palette = 2
        // Tile range: 0 to 6 + (3*3) - 1 = 14 → maxTileExclusive = 15
        return buildLevelArtSheet(0x324, 2, frames, 0, 15);
    }

    private List<SpriteMappingFrame> adjustTileIndices(List<SpriteMappingFrame> frames, int adjustment) {
        if (adjustment == 0) {
            return frames;
        }
        List<SpriteMappingFrame> adjusted = new ArrayList<>(frames.size());
        for (SpriteMappingFrame frame : frames) {
            List<SpriteMappingPiece> adjustedPieces = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece piece : frame.pieces()) {
                adjustedPieces.add(new SpriteMappingPiece(
                        piece.xOffset(), piece.yOffset(),
                        piece.widthTiles(), piece.heightTiles(),
                        piece.tileIndex() + adjustment,
                        piece.hFlip(), piece.vFlip(),
                        piece.paletteIndex(), piece.priority()
                ));
            }
            adjusted.add(new SpriteMappingFrame(adjustedPieces));
        }
        return adjusted;
    }
}
