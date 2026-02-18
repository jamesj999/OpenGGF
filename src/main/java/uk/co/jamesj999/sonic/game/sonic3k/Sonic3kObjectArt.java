package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
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
    private final RomByteReader reader;

    public Sonic3kObjectArt(Level level) {
        this(level, null);
    }

    public Sonic3kObjectArt(Level level, RomByteReader reader) {
        this.level = level;
        this.reader = reader;
    }

    // Tracks the level tile range of the most recently built sheet,
    // so callers can record which tiles each sheet depends on.
    private int lastBuildStartTile = -1;
    private int lastBuildTileCount = -1;

    /** Returns the starting level tile index of the last built sheet, or -1. */
    public int getLastBuildStartTile() { return lastBuildStartTile; }

    /** Returns the tile count of the last built sheet, or -1. */
    public int getLastBuildTileCount() { return lastBuildTileCount; }

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
            lastBuildStartTile = -1;
            lastBuildTileCount = -1;
            return null;
        }

        int patternCount = maxTileExclusive - minTile;
        Pattern[] patterns = new Pattern[patternCount];
        int levelPatternCount = level.getPatternCount();

        lastBuildStartTile = artTileBase + minTile;
        lastBuildTileCount = patternCount;

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
     * Builds a sprite sheet by parsing S3K mapping frames from ROM at runtime.
     * Automatically computes the tile range from all pieces in the mapping.
     *
     * @param mappingAddr   ROM address of the S3K mapping table
     * @param artTileBase   the art_tile base index (VRAM tile destination)
     * @param sheetPalette  the sheet palette line (0-3)
     * @return the sprite sheet, or null if reader is unavailable or mapping is empty
     */
    public ObjectSpriteSheet buildLevelArtSheetFromRom(int mappingAddr,
            int artTileBase, int sheetPalette) {
        if (reader == null) return null;
        List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr);
        if (frames.isEmpty()) return null;

        int minTile = Integer.MAX_VALUE;
        int maxTile = Integer.MIN_VALUE;
        for (SpriteMappingFrame frame : frames) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                minTile = Math.min(minTile, piece.tileIndex());
                int pieceTiles = piece.widthTiles() * piece.heightTiles();
                maxTile = Math.max(maxTile, piece.tileIndex() + pieceTiles);
            }
        }
        if (minTile == Integer.MAX_VALUE) return null;

        return buildLevelArtSheet(artTileBase, sheetPalette, frames, minTile, maxTile);
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

    /**
     * Builds the Spikes sprite sheet.
     * <p>
     * From disassembly (Map - Spikes.asm / Obj_Spikes), 8 frames:
     * Frames 0-3: upright spikes (2w×4h pieces), art_tile = ArtTile_SpikesSprings+$8 ($049C)
     * Frames 4-7: sideways spikes (4w×2h pieces with hflip), art_tile = ArtTile_SpikesSprings ($0494)
     * <p>
     * The ROM overrides art_tile for sideways spikes (size index >= 4) to use
     * tiles $0494-$049B instead of $049C-$04A3. Sheet covers both ranges (16 tiles).
     */
    public ObjectSpriteSheet buildSpikesSheet() {
        // Sheet base = $0494, covering tiles 0-15 (sideways=0-7, upright=8-15)
        List<SpriteMappingFrame> frames = new ArrayList<>(8);

        // Frames 0-3: upright spikes (2w×4h = 16×32px pieces)
        // art_tile = $049C = base + 8, so piece tile index = 8
        for (int count = 2; count <= 8; count += 2) {
            List<SpriteMappingPiece> pieces = new ArrayList<>(count);
            int startX = -(count / 2) * 16;
            for (int i = 0; i < count; i++) {
                pieces.add(new SpriteMappingPiece(startX + i * 16, -16, 2, 4, 8, false, false, 0));
            }
            frames.add(new SpriteMappingFrame(pieces));
        }

        // Frames 4-7: sideways spikes (4w×2h = 32×16px pieces, hflip=true)
        // art_tile = $0494 = base, so piece tile index = 0
        for (int count = 2; count <= 8; count += 2) {
            List<SpriteMappingPiece> pieces = new ArrayList<>(count);
            int startY = -(count / 2) * 16;
            for (int i = 0; i < count; i++) {
                pieces.add(new SpriteMappingPiece(-16, startY + i * 16, 4, 2, 0, true, false, 0));
            }
            frames.add(new SpriteMappingFrame(pieces));
        }

        // 16 tiles: sideways art (0-7) + upright art (8-15)
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_SPIKES_SPRINGS, 0, frames, 0, 16);
    }

    // --- Spring art sheets ---
    // Mapping data parsed from Map - Spring.asm (skdisasm/General/Sprites/Level Misc/)
    // Vertical/Down springs: art_tile = ArtTile_SpikesSprings + $10 = $04A4
    // Horizontal springs: art_tile = ArtTile_SpikesSprings + $20 = $04B4
    // Diagonal springs: art_tile = ArtTile_DiagonalSpring = $043A

    /** Red vertical spring: 3 frames (idle, triggered-compress, triggered-extend). */
    public ObjectSpriteSheet buildSpringVerticalSheet() {
        List<SpriteMappingFrame> frames = List.of(
                // Frame 0 (idle): coil plate + base plate
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -8, 4, 1, 0, false, false, 0),
                        new SpriteMappingPiece(-8, 0, 2, 1, 8, false, false, 0))),
                // Frame 1 (triggered-compress): coil plate only, shifted down
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, 0, 4, 1, 0, false, false, 0))),
                // Frame 2 (triggered-extend): coil plate shifted up + extended piece
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -24, 4, 1, 0, false, false, 0),
                        new SpriteMappingPiece(-8, -16, 2, 3, 0xA, false, false, 0))));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_SPIKES_SPRINGS + 0x10, 0, frames, 0, 0x10);
    }

    /** Yellow vertical spring: same layout as red, different coil tiles (4) and palette (1). */
    public ObjectSpriteSheet buildSpringVerticalYellowSheet() {
        List<SpriteMappingFrame> frames = List.of(
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -8, 4, 1, 4, false, false, 1),
                        new SpriteMappingPiece(-8, 0, 2, 1, 8, false, false, 0))),
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, 0, 4, 1, 4, false, false, 1))),
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -24, 4, 1, 4, false, false, 1),
                        new SpriteMappingPiece(-8, -16, 2, 3, 0xA, false, false, 0))));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_SPIKES_SPRINGS + 0x10, 0, frames, 0, 0x10);
    }

    /** Red horizontal spring: 3 frames. */
    public ObjectSpriteSheet buildSpringHorizontalSheet() {
        List<SpriteMappingFrame> frames = List.of(
                // Frame 0 (idle): coil column + base column
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(0, -16, 1, 4, 0, false, false, 0),
                        new SpriteMappingPiece(-8, -8, 1, 2, 8, false, false, 0))),
                // Frame 1 (triggered-compress): coil column only
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -16, 1, 4, 0, false, false, 0))),
                // Frame 2 (triggered-extend): coil column shifted + extended piece
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(16, -16, 1, 4, 0, false, false, 0),
                        new SpriteMappingPiece(-8, -8, 3, 2, 0xA, false, false, 0))));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_SPIKES_SPRINGS + 0x20, 0, frames, 0, 0x10);
    }

    /** Yellow horizontal spring: same layout, different coil tiles. */
    public ObjectSpriteSheet buildSpringHorizontalYellowSheet() {
        List<SpriteMappingFrame> frames = List.of(
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(0, -16, 1, 4, 4, false, false, 1),
                        new SpriteMappingPiece(-8, -8, 1, 2, 8, false, false, 0))),
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -16, 1, 4, 4, false, false, 1))),
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(16, -16, 1, 4, 4, false, false, 1),
                        new SpriteMappingPiece(-8, -8, 3, 2, 0xA, false, false, 0))));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_SPIKES_SPRINGS + 0x20, 0, frames, 0, 0x10);
    }

    /** Red diagonal spring: 3 frames. */
    public ObjectSpriteSheet buildSpringDiagonalSheet() {
        List<SpriteMappingFrame> frames = List.of(
                // Frame 0 (idle): 4 pieces
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-21, -15, 3, 1, 0, false, false, 0),
                        new SpriteMappingPiece(-13, -7, 3, 1, 3, false, false, 0),
                        new SpriteMappingPiece(-5, 1, 2, 2, 6, false, false, 0),
                        new SpriteMappingPiece(-15, -5, 2, 2, 0x14, false, false, 0))),
                // Frame 1 (triggered-compress): 3 pieces
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-26, -9, 3, 1, 0, false, false, 0),
                        new SpriteMappingPiece(-18, -1, 3, 1, 3, false, false, 0),
                        new SpriteMappingPiece(-10, 7, 2, 2, 6, false, false, 0))),
                // Frame 2 (triggered-extend): 5 pieces
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-10, -26, 3, 1, 0, false, false, 0),
                        new SpriteMappingPiece(-2, -18, 3, 1, 3, false, false, 0),
                        new SpriteMappingPiece(6, -10, 2, 2, 6, false, false, 0),
                        new SpriteMappingPiece(-6, -11, 2, 1, 0x18, false, false, 0),
                        new SpriteMappingPiece(-14, -3, 2, 1, 0x1A, false, false, 0))));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_DIAGONAL_SPRING, 0, frames, 0, 0x1C);
    }

    /** Yellow diagonal spring: different coil tiles (0xA/0xD/0x10), palette 1. */
    public ObjectSpriteSheet buildSpringDiagonalYellowSheet() {
        List<SpriteMappingFrame> frames = List.of(
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-21, -15, 3, 1, 0xA, false, false, 1),
                        new SpriteMappingPiece(-13, -7, 3, 1, 0xD, false, false, 1),
                        new SpriteMappingPiece(-5, 1, 2, 2, 0x10, false, false, 1),
                        new SpriteMappingPiece(-15, -5, 2, 2, 0x14, false, false, 0))),
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-26, -9, 3, 1, 0xA, false, false, 1),
                        new SpriteMappingPiece(-18, -1, 3, 1, 0xD, false, false, 1),
                        new SpriteMappingPiece(-10, 7, 2, 2, 0x10, false, false, 1))),
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-10, -26, 3, 1, 0xA, false, false, 1),
                        new SpriteMappingPiece(-2, -18, 3, 1, 0xD, false, false, 1),
                        new SpriteMappingPiece(6, -10, 2, 2, 0x10, false, false, 1),
                        new SpriteMappingPiece(-6, -11, 2, 1, 0x18, false, false, 0),
                        new SpriteMappingPiece(-14, -3, 2, 1, 0x1A, false, false, 0))));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_DIAGONAL_SPRING, 0, frames, 0, 0x1C);
    }

    /**
     * Builds the AIZForegroundPlant sprite sheet.
     * <p>
     * From disassembly (Map - AIZ Foreground Plant.asm):
     * art_tile = make_art_tile(ArtTile_AIZMisc1, 2, 1) → base tile 0x333, palette 2, priority
     * Mapping: 2 frames (0=with flowers, 1=without flowers), 8 pieces each.
     * Tile range: 0x64 to 0x9B (56 patterns from level art).
     */
    public ObjectSpriteSheet buildAizForegroundPlantSheet() {
        // Frame 0: with flowers (8 pieces)
        List<SpriteMappingPiece> frame0Pieces = List.of(
                new SpriteMappingPiece(-32, -48, 4, 3, 0x64, false, false, 0),
                new SpriteMappingPiece(-32, -24, 4, 4, 0x70, false, false, 0),
                new SpriteMappingPiece(-24, 8, 3, 2, 0x80, false, false, 0),
                new SpriteMappingPiece(-8, 24, 1, 3, 0x86, false, false, 0),
                new SpriteMappingPiece(16, -24, 2, 1, 0x89, false, false, 0),
                new SpriteMappingPiece(0, -16, 4, 2, 0x8B, false, false, 0),
                new SpriteMappingPiece(0, 0, 3, 2, 0x93, false, false, 0),
                new SpriteMappingPiece(0, 16, 1, 3, 0x99, false, false, 0));

        // Frame 1: without flowers (8 pieces)
        List<SpriteMappingPiece> frame1Pieces = List.of(
                new SpriteMappingPiece(0, -60, 4, 3, 0x64, true, false, 0),
                new SpriteMappingPiece(0, -36, 4, 4, 0x70, true, false, 0),
                new SpriteMappingPiece(0, -4, 3, 2, 0x80, true, false, 0),
                new SpriteMappingPiece(0, 12, 1, 3, 0x86, true, false, 0),
                new SpriteMappingPiece(-32, -36, 4, 3, 0x64, false, false, 0),
                new SpriteMappingPiece(-32, -12, 4, 4, 0x70, false, false, 0),
                new SpriteMappingPiece(-24, 20, 3, 2, 0x80, false, false, 0),
                new SpriteMappingPiece(-8, 36, 1, 3, 0x86, false, false, 0));

        List<SpriteMappingFrame> frames = List.of(
                new SpriteMappingFrame(frame0Pieces),
                new SpriteMappingFrame(frame1Pieces));

        // art_tile base = 0x333, palette = 2
        // Tile range: 0x64 to 0x9C (exclusive) = 56 patterns
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_AIZ_MISC1, 2, frames, 0x64, 0x9C);
    }

    // ===== Collapsing Platform sprite sheets (parsed from ROM) =====

    /**
     * Builds the AIZ Act 1 Collapsing Platform sprite sheet (Map_AIZCollapsingPlatform, 4 frames).
     * art_tile = make_art_tile($001, 2, 0) → base tile 1, palette 2.
     */
    public ObjectSpriteSheet buildCollapsingPlatformAiz1Sheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_AIZ_COLLAPSING_PLATFORM_ADDR, 1, 2);
    }

    /**
     * Builds the AIZ Act 2 Collapsing Platform sprite sheet (Map_AIZCollapsingPlatform2, 4 frames).
     * art_tile = make_art_tile($001, 2, 0) → base tile 1, palette 2.
     */
    public ObjectSpriteSheet buildCollapsingPlatformAiz2Sheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_AIZ_COLLAPSING_PLATFORM2_ADDR, 1, 2);
    }

    /**
     * Builds the ICZ Collapsing Platform sprite sheet (Map_ICZCollapsingBridge, 6 frames).
     * art_tile = make_art_tile($001, 2, 0) → base tile 1, palette 2.
     */
    public ObjectSpriteSheet buildCollapsingPlatformIczSheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_ICZ_COLLAPSING_BRIDGE_ADDR, 1, 2);
    }

    // ===== AIZ/LRZ Rock sprite sheets (parsed from ROM) =====

    /**
     * Builds the AIZ Act 1 Rock sprite sheet (Map_AIZRock, 7 frames).
     * art_tile = ArtTile_AIZ_Misc1 ($0333), palette 1.
     */
    public ObjectSpriteSheet buildAiz1RockSheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_AIZ_ROCK_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_MISC1, 1);
    }

    /**
     * Builds the AIZ Act 2 Rock sprite sheet (Map_AIZRock2, 7 frames).
     * art_tile = ArtTile_AIZ_Misc2 ($02E9), palette 2.
     */
    public ObjectSpriteSheet buildAiz2RockSheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_AIZ_ROCK2_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_MISC2, 2);
    }

    /**
     * Builds the LRZ Act 1 Breakable Rock sprite sheet (Map_LRZBreakableRock, 11 frames).
     * art_tile = $00D3, palette 2.
     */
    public ObjectSpriteSheet buildLrz1RockSheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_LRZ_BREAKABLE_ROCK_ADDR,
                0x00D3, 2);
    }

    /**
     * Builds the LRZ Act 2 Breakable Rock sprite sheet (Map_LRZBreakableRock2, 12 frames).
     * art_tile = ArtTile_LRZ2_MISC ($040D), palette 3.
     */
    public ObjectSpriteSheet buildLrz2RockSheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_LRZ_BREAKABLE_ROCK2_ADDR,
                Sonic3kConstants.ARTTILE_LRZ2_MISC, 3);
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
