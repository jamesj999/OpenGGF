package com.openggf.game.sonic3k;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Level;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.level.resources.CompressionType;
import com.openggf.tools.KosinskiReader;
import com.openggf.tools.NemesisReader;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
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
        return buildLevelArtSheetFromRom(mappingAddr, artTileBase, sheetPalette,
                S3kSpriteDataLoader.MappingFormat.STANDARD);
    }

    public ObjectSpriteSheet buildLevelArtSheetFromRom(int mappingAddr,
            int artTileBase, int sheetPalette, S3kSpriteDataLoader.MappingFormat mappingFormat) {
        if (reader == null) return null;
        List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr, mappingFormat);
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
     * Builds the AIZ Ride Vine / Giant Ride Vine sprite sheet.
     * <p>
     * From disassembly:
     * Obj_AIZRideVine / Obj_AIZGiantRideVine:
     * art_tile = make_art_tile(ArtTile_AIZSwingVine, 0, 0)
     * mappings = Map_AIZMHZRideVine
     */
    public ObjectSpriteSheet buildAizRideVineSheet() {
        return buildLevelArtSheetFromRom(
                Sonic3kConstants.MAP_AIZ_MHZ_RIDE_VINE_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_SWING_VINE,
                0);
    }

    /**
     * Builds the Animated Still Sprites sheet used by AIZ (firefly + leaf animations).
     *
     * <p>Disassembly reference:
     * Map_AnimatedStillSprites / Ani_AnimatedStillSprites (sonic3k.asm:60424+).
     * art_tile = make_art_tile(ArtTile_AIZMisc2,3,0). Frames 0-8.
     */
    public ObjectSpriteSheet buildAnimatedStillSpritesSheet() {
        return buildAnimStillSheet(Sonic3kConstants.ARTTILE_AIZ_MISC2, 3, 0, 8);
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

    /**
     * Builds a sprite sheet from ROM mappings, selecting only specific frame indices.
     * Used for StillSprite/AnimatedStillSprite where each zone group uses a subset of
     * the shared mapping table.
     *
     * @param mappingAddr    ROM address of the S3K mapping table
     * @param artTileBase    the art_tile base index (VRAM tile destination)
     * @param sheetPalette   the sheet palette line (0-3)
     * @param frameIndices   which mapping frames to include in the sheet
     * @return the sprite sheet, or null if unavailable
     */
    public ObjectSpriteSheet buildLevelArtSheetFromRomFiltered(int mappingAddr,
            int artTileBase, int sheetPalette, int[] frameIndices) {
        return buildLevelArtSheetFromRomFiltered(mappingAddr, artTileBase, sheetPalette,
                frameIndices, S3kSpriteDataLoader.MappingFormat.STANDARD);
    }

    public ObjectSpriteSheet buildLevelArtSheetFromRomFiltered(int mappingAddr,
            int artTileBase, int sheetPalette, int[] frameIndices,
            S3kSpriteDataLoader.MappingFormat mappingFormat) {
        if (reader == null || frameIndices == null || frameIndices.length == 0) return null;
        List<SpriteMappingFrame> allFrames = S3kSpriteDataLoader.loadMappingFrames(reader, mappingAddr, mappingFormat);
        if (allFrames.isEmpty()) return null;

        List<SpriteMappingFrame> selected = new ArrayList<>(frameIndices.length);
        for (int idx : frameIndices) {
            if (idx >= 0 && idx < allFrames.size()) {
                selected.add(allFrames.get(idx));
            }
        }
        if (selected.isEmpty()) return null;

        int minTile = Integer.MAX_VALUE;
        int maxTile = Integer.MIN_VALUE;
        for (SpriteMappingFrame frame : selected) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                minTile = Math.min(minTile, piece.tileIndex());
                int pieceTiles = piece.widthTiles() * piece.heightTiles();
                maxTile = Math.max(maxTile, piece.tileIndex() + pieceTiles);
            }
        }
        if (minTile == Integer.MAX_VALUE) return null;

        return buildLevelArtSheet(artTileBase, sheetPalette, selected, minTile, maxTile);
    }

    /**
     * Builds AnimatedStillSprite sheet for LRZ subtype 2 (ceiling rock flicker).
     * art_tile = $0D3, palette 2. Animation frames 9-10.
     */
    public ObjectSpriteSheet buildAnimStillLrzD3Sheet() {
        return buildAnimStillSheet(0x00D3, 2, 9, 10);
    }

    /**
     * Builds AnimatedStillSprite sheet for LRZ2 subtype 3 (torch flame).
     * art_tile = LRZ2Misc ($040D), palette 1. Animation frames 11-13.
     */
    public ObjectSpriteSheet buildAnimStillLrz2Sheet() {
        return buildAnimStillSheet(Sonic3kConstants.ARTTILE_LRZ2_MISC, 1, 11, 13);
    }

    /**
     * Builds AnimatedStillSprite sheet for SOZ subtypes 4-7 (torches).
     * art_tile = SOZMisc+$46 ($040F), palette 2. Animation frames 14-29.
     */
    public ObjectSpriteSheet buildAnimStillSozSheet() {
        return buildAnimStillSheet(Sonic3kConstants.ARTTILE_SOZ_MISC + 0x46, 2, 14, 29);
    }

    private ObjectSpriteSheet buildAnimStillSheet(int artTileBase, int palette,
            int firstFrame, int lastFrame) {
        if (reader == null) return null;
        List<SpriteMappingFrame> allFrames = S3kSpriteDataLoader.loadMappingFrames(
                reader, Sonic3kConstants.MAP_ANIMATED_STILL_SPRITES_ADDR);
        if (allFrames.isEmpty()) return null;

        List<SpriteMappingFrame> selected = new ArrayList<>();
        for (int i = firstFrame; i <= lastFrame && i < allFrames.size(); i++) {
            selected.add(allFrames.get(i));
        }
        if (selected.isEmpty()) return null;

        int minTile = Integer.MAX_VALUE;
        int maxTile = Integer.MIN_VALUE;
        for (SpriteMappingFrame frame : selected) {
            for (SpriteMappingPiece piece : frame.pieces()) {
                minTile = Math.min(minTile, piece.tileIndex());
                int pieceTiles = piece.widthTiles() * piece.heightTiles();
                maxTile = Math.max(maxTile, piece.tileIndex() + pieceTiles);
            }
        }
        if (minTile == Integer.MAX_VALUE) return null;

        return buildLevelArtSheet(artTileBase, palette, selected, minTile, maxTile);
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

    // ===== AIZ Flipping Bridge sprite sheet (parsed from ROM) =====

    /**
     * Builds the AIZ Flipping Bridge sprite sheet (Map_AIZFlippingBridge, 32 frames).
     * art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 0) → base tile 0x2E9, palette 2.
     * <p>
     * Note: The mapping's first pointer entry (0x78) doesn't equal the table size (0x40),
     * so the auto-detect frame count method would compute 60 instead of 32. Uses explicit
     * frame count.
     */
    public ObjectSpriteSheet buildFlippingBridgeSheet() {
        if (reader == null) return null;
        List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(
                reader, Sonic3kConstants.MAP_AIZ_FLIPPING_BRIDGE_ADDR, 32);
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

        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_AIZ_MISC2, 2, frames, minTile, maxTile);
    }

    /**
     * Builds the AIZ draw bridge sheet from ROM mappings.
     * ROM: Obj_AIZDrawBridge uses art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 1).
     * Map_AIZDrawBridge has 2 frames: frame 0 = empty, frame 1 = single 2x2 segment.
     */
    public ObjectSpriteSheet buildDrawBridgeSheet() {
        return buildLevelArtSheetFromRom(
                Sonic3kConstants.MAP_AIZ_DRAW_BRIDGE_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_MISC2,
                2);
    }

    // ===== AIZ Disappearing Floor sprite sheets (parsed from ROM) =====

    /**
     * Builds the AIZ Disappearing Floor parent sprite sheet (Map_AIZDisappearingFloor, 6 frames).
     * art_tile = make_art_tile($001, 2, 0) → base tile 1, palette 2.
     * <p>
     * Note: Map_AIZDisappearingFloor and Map_AIZDisappearingFloor2 share a memory region
     * (interleaved offset tables), so auto-detect frame count would fail. Uses explicit count 6.
     */
    public ObjectSpriteSheet buildDisappearingFloorSheet() {
        if (reader == null) return null;
        List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(
                reader, Sonic3kConstants.MAP_AIZ_DISAPPEARING_FLOOR_ADDR, 6);
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

        return buildLevelArtSheet(1, 2, frames, minTile, maxTile);
    }

    /**
     * Builds the AIZ Disappearing Floor water border sprite sheet
     * (Map_AIZDisappearingFloor2, 4 frames).
     * art_tile = make_art_tile(ArtTile_AIZMisc2, 3, 0) → base tile 0x2E9, palette 3.
     * <p>
     * Interleaved with Map_AIZDisappearingFloor; uses explicit count 4.
     */
    public ObjectSpriteSheet buildDisappearingFloorBorderSheet() {
        if (reader == null) return null;
        List<SpriteMappingFrame> frames = S3kSpriteDataLoader.loadMappingFrames(
                reader, Sonic3kConstants.MAP_AIZ_DISAPPEARING_FLOOR_BORDER_ADDR, 4);
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

        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_AIZ_MISC2, 3, frames, minTile, maxTile);
    }

    // ===== AIZ Spiked Log sprite sheet (parsed from ROM) =====

    /**
     * Builds the AIZ Spiked Log sprite sheet (Map_AIZSpikedLog, 16 frames).
     * art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 0) → base tile 0x2E9, palette 2.
     */
    public ObjectSpriteSheet buildSpikedLogSheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_AIZ_SPIKED_LOG_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_MISC2, 2);
    }

    // ===== AIZ Collapsing Log Bridge sprite sheets (parsed from ROM) =====

    /**
     * Builds the AIZ Collapsing Log Bridge sprite sheet (Map_AIZCollapsingLogBridge, 3 frames).
     * art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 0) → base tile 0x2E9, palette 2.
     */
    public ObjectSpriteSheet buildCollapsingLogBridgeSheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_AIZ_COLLAPSING_LOG_BRIDGE_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_MISC2, 2);
    }

    /**
     * Builds the AIZ Draw Bridge Fire sprite sheet (Map_AIZDrawBridgeFire, 8 frames).
     * art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 1) → base tile 0x2E9, palette 2.
     * Note: fire animation frames (3-7) use palette 3 in the ROM via art_tile addition;
     * piece-level palette in the mapping data handles this.
     */
    public ObjectSpriteSheet buildDrawBridgeFireSheet() {
        return buildLevelArtSheetFromRom(Sonic3kConstants.MAP_AIZ_DRAW_BRIDGE_FIRE_ADDR,
                Sonic3kConstants.ARTTILE_AIZ_MISC2, 2);
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

    // ===== AIZ badnik dedicated-art sprite sheets =====

    /**
     * Loads Bloominator (Obj $8C) dedicated art sheet.
     * Art: ArtKosM_AIZ_Bloominator, map: Map_Bloominator.
     */
    public ObjectSpriteSheet loadBloominatorSheet(Rom rom) {
        if (rom == null || reader == null) {
            return null;
        }
        try {
            Pattern[] patterns = loadKosinskiModuledPatterns(rom, Sonic3kConstants.ART_KOSM_AIZ_BLOOMINATOR_ADDR);
            if (patterns.length == 0) {
                return null;
            }
            List<SpriteMappingFrame> mappings =
                    S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_BLOOMINATOR_ADDR);
            return new ObjectSpriteSheet(patterns, mappings, 1, 1);
        } catch (IOException e) {
            LOG.warning("Failed loading Bloominator art: " + e.getMessage());
            return null;
        }
    }

    /**
     * Loads Monkey Dude (Obj $8E) dedicated art sheet.
     * Art: ArtKosM_AIZ_MonkeyDude, map: Map_MonkeyDude.
     */
    public ObjectSpriteSheet loadMonkeyDudeSheet(Rom rom) {
        if (rom == null || reader == null) {
            return null;
        }
        try {
            Pattern[] patterns = loadKosinskiModuledPatterns(rom, Sonic3kConstants.ART_KOSM_AIZ_MONKEY_DUDE_ADDR);
            if (patterns.length == 0) {
                return null;
            }
            List<SpriteMappingFrame> mappings =
                    S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_MONKEY_DUDE_ADDR);
            return new ObjectSpriteSheet(patterns, mappings, 1, 1);
        } catch (IOException e) {
            LOG.warning("Failed loading Monkey Dude art: " + e.getMessage());
            return null;
        }
    }

    /**
     * Loads Rhinobot (Obj $8D) dedicated art sheet.
     * Uses object-format DPLC remap (Perform_DPLC path).
     */
    public ObjectSpriteSheet loadRhinobotSheet(Rom rom) {
        if (rom == null || reader == null) {
            return null;
        }
        try {
            Pattern[] patterns = loadUncompressedPatterns(rom,
                    Sonic3kConstants.ART_UNC_AIZ_RHINOBOT_ADDR,
                    Sonic3kConstants.ART_UNC_AIZ_RHINOBOT_SIZE);
            if (patterns.length == 0) {
                return null;
            }
            List<SpriteMappingFrame> rawMappings =
                    S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_RHINOBOT_ADDR);
            List<SpriteDplcFrame> dplcFrames = loadObjectDplcFrames(reader, Sonic3kConstants.DPLC_RHINOBOT_ADDR);
            List<SpriteMappingFrame> remapped = applyDplcRemap(rawMappings, dplcFrames);
            return new ObjectSpriteSheet(patterns, remapped, 1, 1);
        } catch (IOException e) {
            LOG.warning("Failed loading Rhinobot art: " + e.getMessage());
            return null;
        }
    }

    /**
     * Loads the shared egg capsule / prison art used by S3K boss endings.
     */
    public ObjectSpriteSheet loadEggCapsuleSheet(Rom rom) {
        if (rom == null || reader == null) {
            return null;
        }
        try {
            Pattern[] patterns = PatternDecompressor.nemesis(rom, Sonic3kConstants.ART_NEM_EGG_CAPSULE_ADDR);
            if (patterns == null || patterns.length == 0) {
                return null;
            }
            List<SpriteMappingFrame> mappings =
                    S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_EGG_CAPSULE_ADDR);
            return new ObjectSpriteSheet(patterns, mappings, 0, 1);
        } catch (IOException e) {
            LOG.warning("Failed loading Egg Capsule art: " + e.getMessage());
            return null;
        }
    }

    /**
     * Loads a standalone art sheet from a registry entry.
     * Dispatches based on the entry's compression type and DPLC presence.
     */
    public ObjectSpriteSheet loadStandaloneSheet(Rom rom,
            Sonic3kPlcArtRegistry.StandaloneArtEntry entry) throws IOException {
        if (rom == null || reader == null) return null;

        Pattern[] patterns;
        switch (entry.compression()) {
            case KOSINSKI_MODULED ->
                patterns = loadKosinskiModuledPatterns(rom, entry.artAddr());
            case NEMESIS ->
                patterns = PatternDecompressor.nemesis(rom, entry.artAddr());
            case UNCOMPRESSED ->
                patterns = loadUncompressedPatterns(rom, entry.artAddr(), entry.artSize());
            default -> { return null; }
        }
        if (patterns == null || patterns.length == 0) return null;

        if (entry.mappingAddr() <= 0) {
            List<SpriteMappingFrame> hardcoded = switch (entry.key()) {
                case Sonic3kObjectArtKeys.HCZ_WATER_RUSH -> buildHczWaterRushMappings();
                case Sonic3kObjectArtKeys.HCZ_WATER_SPLASH -> buildHczWaterSplashMappings();
                case Sonic3kObjectArtKeys.HCZ_GEYSER_HORZ -> buildHczGeyserHorzMappings();
                case Sonic3kObjectArtKeys.HCZ_GEYSER_VERT -> buildHczGeyserVertMappings();
                case Sonic3kObjectArtKeys.HCZ_GEYSER_DEBRIS -> buildHczGeyserDebrisMappings();
                case Sonic3kObjectArtKeys.HCZ_GEYSER_SPRAY -> buildHczGeyserSprayMappings();
                case Sonic3kObjectArtKeys.HCZ_BUBBLES -> buildHczGeyserAllFrames();
                case Sonic3kObjectArtKeys.HCZ_FAN_BUBBLE -> buildHczFanBubbleMappings();
                case Sonic3kObjectArtKeys.BUBBLER -> buildBubblerMappings();
                default -> null;
            };
            if (hardcoded == null || hardcoded.isEmpty()) {
                LOG.warning("No hardcoded mappings for standalone '" + entry.key() + "'");
                return null;
            }
            return new ObjectSpriteSheet(patterns, hardcoded, entry.palette(), 1);
        }

        List<SpriteMappingFrame> mappings =
                S3kSpriteDataLoader.loadMappingFrames(reader, entry.mappingAddr(), entry.mappingFormat());

        if (entry.dplcAddr() > 0) {
            List<SpriteDplcFrame> dplcFrames = loadObjectDplcFrames(reader, entry.dplcAddr());
            mappings = applyDplcRemap(mappings, dplcFrames);
        }

        return new ObjectSpriteSheet(patterns, mappings, entry.palette(), 1);
    }

    // ===== Results Screen art loading =====

    /**
     * Loads all results screen art from ROM into a combined pattern array
     * covering the VRAM range $520-$61F (256 tiles).
     *
     * <p>Layout matches the ROM's Load_EndOfAct routine:
     * <ol>
     *   <li>General art ("GOT THROUGH", bonus labels) at VRAM $520 (index 0)</li>
     *   <li>Number art at VRAM $568 (index $48). Uses Num1 for act 1 (non-DDZ), Num2 otherwise</li>
     *   <li>Character name art at VRAM $578 (act 1) or $5A0 (act 2), selected by character</li>
     * </ol>
     *
     * @param character the player character, determines which name art to load
     * @param act       act index (0 = act 1, 1 = act 2)
     * @return pattern array of size {@link Sonic3kConstants#VRAM_RESULTS_ARRAY_SIZE}, or null on failure
     */
    public Pattern[] loadResultsArt(PlayerCharacter character, int act) {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) return null;
            Pattern[] patterns = new Pattern[Sonic3kConstants.VRAM_RESULTS_ARRAY_SIZE];
            Pattern empty = new Pattern();
            Arrays.fill(patterns, empty);

            // 1. General art → VRAM $520 (index 0)
            loadKosmArtInto(rom, Sonic3kConstants.ART_KOSM_RESULTS_GENERAL_ADDR,
                    patterns, 0);

            // 2. Number art → VRAM $568 (index $48)
            // Use Num1 when act == 0 AND zone != 0x16 (DDZ), else Num2
            int zone = GameServices.level().getCurrentZone();
            int numArtAddr = (act == 0 && zone != 0x16)
                    ? Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM1_ADDR
                    : Sonic3kConstants.ART_KOSM_TITLE_CARD_NUM2_ADDR;
            loadKosmArtInto(rom, numArtAddr, patterns,
                    Sonic3kConstants.VRAM_RESULTS_NUMBERS - Sonic3kConstants.VRAM_RESULTS_BASE);

            // 3. Character name art → VRAM $578 (act 1) or $5A0 (act 2)
            int charArtAddr = getCharacterNameArtAddr(character);
            int charDestVram = (act == 0)
                    ? Sonic3kConstants.VRAM_RESULTS_CHAR_NAME_ACT1
                    : Sonic3kConstants.VRAM_RESULTS_CHAR_NAME_ACT2;
            loadKosmArtInto(rom, charArtAddr, patterns,
                    charDestVram - Sonic3kConstants.VRAM_RESULTS_BASE);

            return patterns;
        } catch (Exception e) {
            LOG.warning("Failed to load results screen art: " + e.getMessage());
            return null;
        }
    }

    /**
     * Loads the results screen palette (128 bytes = 4 lines x 16 colors).
     *
     * @return raw palette bytes, or null on failure
     */
    public byte[] loadResultsPalette() {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) return null;
            return rom.readBytes(Sonic3kConstants.PAL_RESULTS_ADDR, 128);
        } catch (Exception e) {
            LOG.warning("Failed to load results palette: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses results screen mapping frames from ROM.
     *
     * @return list of sprite mapping frames (59 entries), or empty list on failure
     */
    public List<SpriteMappingFrame> loadResultsMappings() {
        if (reader == null) return List.of();
        try {
            return S3kSpriteDataLoader.loadMappingFrames(reader, Sonic3kConstants.MAP_RESULTS_ADDR);
        } catch (Exception e) {
            LOG.warning("Failed to load results mappings: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Load special stage results screen art from ROM.
     * <p>
     * ROM loads art at the SS-specific VRAM positions ($4F1, $523, $5B8, $6BC),
     * NOT the level results positions. Per-object art_tile offsets in the results
     * screen code compensate for the difference between these positions and the
     * tile references in Map_Results.
     * <p>
     * Layout (base = $4F1):
     * <ul>
     *   <li>$4F1 (index 0): Character name art</li>
     *   <li>$523 (index $32): SS results text art</li>
     *   <li>$5B8 (index $C7): General results art</li>
     *   <li>$6BC (index $1CB): HUD text art</li>
     * </ul>
     *
     * @param character the player character
     * @return pattern array covering VRAM range $4F1-$7F0, or null on failure
     */
    public Pattern[] loadSSResultsArt(PlayerCharacter character) {
        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) return null;

            int base = Sonic3kConstants.VRAM_SS_RESULTS_BASE; // $4F1
            Pattern[] patterns = new Pattern[Sonic3kConstants.VRAM_SS_RESULTS_ARRAY_SIZE];
            Pattern empty = new Pattern();
            Arrays.fill(patterns, empty);

            // 1. Character name → VRAM $4F1 (index 0)
            loadKosmArtInto(rom, getCharacterNameArtAddr(character), patterns,
                    Sonic3kConstants.VRAM_SS_RESULTS_CHAR_NAME - base);

            // 2. SS results text → VRAM $523 (index $32)
            loadKosmArtInto(rom, Sonic3kConstants.ART_KOSM_SS_RESULTS_ADDR, patterns,
                    Sonic3kConstants.VRAM_SS_RESULTS_TEXT - base);

            // 3. General results art → VRAM $5B8 (index $C7)
            loadKosmArtInto(rom, Sonic3kConstants.ART_KOSM_RESULTS_GENERAL_ADDR, patterns,
                    Sonic3kConstants.VRAM_SS_RESULTS_GENERAL - base);

            // 4. Ring/HUD text → VRAM $6BC (index $1CB, Nemesis compressed)
            loadNemesisArtInto(rom, Sonic3kConstants.ART_NEM_RING_HUD_TEXT_ADDR, patterns,
                    Sonic3kConstants.VRAM_SS_RESULTS_HUD_TEXT - base);

            // 5. Mirror HUD_DrawInitial, which overwrites $6E2+ with large HUD digits.
            // This is what gives frame $31 its correct "E" plus blank/zero suffix tiles.
            overlaySpecialStageHudInitial(rom, patterns, base);

            return patterns;
        } catch (Exception e) {
            LOG.warning("Failed to load SS results screen art: " + e.getMessage());
            return null;
        }
    }

    // ROM: HUD_DrawInitial reads HUD_Initial_Parts then HUD_Zero_Rings,
    // producing 15 8x16 glyph uploads: "E      00:00  0"
    private static final char[] HUD_INITIAL_GLYPHS =
            {'E', ' ', ' ', ' ', ' ', ' ', ' ', '0', '0', ':', '0', '0', ' ', ' ', '0'};

    private void overlaySpecialStageHudInitial(Rom rom, Pattern[] dest, int base)
            throws IOException {
        Pattern[] hudDigits = loadUncompressedPatterns(rom,
                Sonic3kConstants.ART_UNC_HUD_DIGITS_ADDR,
                Sonic3kConstants.ART_UNC_HUD_DIGITS_SIZE);
        int destIndex = Sonic3kConstants.VRAM_SS_RESULTS_HUD_INITIAL - base;

        for (char glyph : HUD_INITIAL_GLYPHS) {
            writeHudInitialGlyph(dest, destIndex, glyph, hudDigits);
            destIndex += 2;
        }
    }

    private void writeHudInitialGlyph(Pattern[] dest, int destIndex, char glyph,
            Pattern[] hudDigits) {
        int srcIndex = switch (glyph) {
            case '0' -> 0;
            case ':' -> 0x14;
            case 'E' -> 0x16;
            default -> -1;
        };
        // Each position needs its own Pattern instance (Pattern is mutable and
        // downstream code may modify individual tiles in-place).
        Pattern top = (srcIndex >= 0) ? hudDigits[srcIndex] : new Pattern();
        Pattern bottom = (srcIndex >= 0 && srcIndex + 1 < hudDigits.length)
                ? hudDigits[srcIndex + 1] : new Pattern();
        if (destIndex >= 0 && destIndex + 1 < dest.length) {
            dest[destIndex] = top;
            dest[destIndex + 1] = bottom;
        }
    }

    /**
     * Decompresses Nemesis art from ROM and places patterns into a destination array.
     */
    private void loadNemesisArtInto(Rom rom, int romAddr, Pattern[] dest, int destIndex)
            throws IOException {
        loadNemesisArtInto(rom, romAddr, dest, destIndex, -1);
    }

    /**
     * Decompresses Nemesis art from ROM and places patterns into a destination array.
     * @param maxTiles maximum tiles to load, or -1 for all
     */
    private void loadNemesisArtInto(Rom rom, int romAddr, Pattern[] dest, int destIndex, int maxTiles)
            throws IOException {
        FileChannel channel = rom.getFileChannel();
        channel.position(romAddr);
        byte[] data = NemesisReader.decompress(channel);
        int tileCount = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        if (maxTiles >= 0) tileCount = Math.min(tileCount, maxTiles);
        for (int i = 0; i < tileCount; i++) {
            int idx = destIndex + i;
            if (idx >= 0 && idx < dest.length) {
                byte[] tileData = Arrays.copyOfRange(data,
                        i * Pattern.PATTERN_SIZE_IN_ROM,
                        (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                Pattern pat = new Pattern();
                pat.fromSegaFormat(tileData);
                dest[idx] = pat;
            }
        }
    }

    private int getCharacterNameArtAddr(PlayerCharacter character) {
        return switch (character) {
            case SONIC_AND_TAILS, SONIC_ALONE -> Sonic3kConstants.ART_KOSM_RESULTS_SONIC_ADDR;
            case TAILS_ALONE -> Sonic3kConstants.ART_KOSM_RESULTS_TAILS_ADDR;
            case KNUCKLES -> Sonic3kConstants.ART_KOSM_RESULTS_KNUCKLES_ADDR;
        };
    }

    /**
     * Decompresses KosinskiM art from ROM and places patterns into a destination array.
     * Follows the same pattern as {@code Sonic3kTitleCardManager.loadKosmArt()}.
     */
    private void loadKosmArtInto(Rom rom, int romAddr, Pattern[] dest, int destIndex)
            throws IOException {
        byte[] header = rom.readBytes(romAddr, 2);
        int fullSize = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        int inputSize = Math.min(Math.max(fullSize + 256, 0x10000), 0x40000);
        long romSize = rom.getSize();
        if (romAddr + inputSize > romSize) {
            inputSize = (int) (romSize - romAddr);
        }

        byte[] romData = rom.readBytes(romAddr, inputSize);
        byte[] decompressed = KosinskiReader.decompressModuled(romData, 0);

        int tileCount = decompressed.length / Pattern.PATTERN_SIZE_IN_ROM;
        for (int i = 0; i < tileCount; i++) {
            int idx = destIndex + i;
            if (idx >= 0 && idx < dest.length) {
                byte[] tileData = Arrays.copyOfRange(decompressed,
                        i * Pattern.PATTERN_SIZE_IN_ROM,
                        (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                Pattern pat = new Pattern();
                pat.fromSegaFormat(tileData);
                dest[idx] = pat;
            }
        }
    }


    private Pattern[] loadKosinskiModuledPatterns(Rom rom, int romAddr) throws IOException {
        byte[] header = rom.readBytes(romAddr, 2);
        if (header.length < 2) {
            return new Pattern[0];
        }
        int fullSize = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);
        int inputSize = Math.min(Math.max(fullSize + 256, 0x10000), 0x40000);
        long romSize = rom.getSize();
        if (romAddr + inputSize > romSize) {
            inputSize = (int) (romSize - romAddr);
        }
        byte[] compressed = rom.readBytes(romAddr, inputSize);
        byte[] data = KosinskiReader.decompressModuled(compressed, 0);
        return bytesToPatterns(data);
    }

    private Pattern[] loadUncompressedPatterns(Rom rom, int romAddr, int size) throws IOException {
        byte[] data = rom.readBytes(romAddr, size);
        return bytesToPatterns(data);
    }

    private Pattern[] bytesToPatterns(byte[] data) {
        return PatternDecompressor.fromBytes(data);
    }

    /**
     * S3K object DPLC parser (Perform_DPLC format):
     * startTile in upper 12 bits, (count-1) in lower 4 bits.
     */
    private static List<SpriteDplcFrame> loadObjectDplcFrames(RomByteReader reader, int dplcAddr) {
        int offsetTableSize = reader.readU16BE(dplcAddr);
        int frameCount = offsetTableSize / 2;

        List<SpriteDplcFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameAddr = dplcAddr + reader.readU16BE(dplcAddr + i * 2);
            int requestCount = reader.readU16BE(frameAddr) + 1;
            frameAddr += 2;

            List<TileLoadRequest> requests = new ArrayList<>(requestCount);
            for (int r = 0; r < requestCount; r++) {
                int entry = reader.readU16BE(frameAddr);
                frameAddr += 2;
                int startTile = (entry >> 4) & 0xFFF;
                int count = (entry & 0xF) + 1;
                requests.add(new TileLoadRequest(startTile, count));
            }
            frames.add(new SpriteDplcFrame(requests));
        }
        return frames;
    }

    /**
     * Remaps mapping tile indices through object DPLC requests.
     */
    private static List<SpriteMappingFrame> applyDplcRemap(
            List<SpriteMappingFrame> mappings, List<SpriteDplcFrame> dplcFrames) {
        if (dplcFrames == null || dplcFrames.isEmpty()) {
            return mappings;
        }

        List<SpriteMappingFrame> remapped = new ArrayList<>(mappings.size());
        for (int i = 0; i < mappings.size(); i++) {
            SpriteMappingFrame frame = mappings.get(i);
            if (i >= dplcFrames.size()) {
                remapped.add(frame);
                continue;
            }

            SpriteDplcFrame dplc = dplcFrames.get(i);
            int totalSlots = 0;
            for (TileLoadRequest req : dplc.requests()) {
                totalSlots += req.count();
            }

            int[] vramToSource = new int[totalSlots];
            int slot = 0;
            for (TileLoadRequest req : dplc.requests()) {
                for (int t = 0; t < req.count(); t++) {
                    vramToSource[slot++] = req.startTile() + t;
                }
            }

            List<SpriteMappingPiece> remappedPieces = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece piece : frame.pieces()) {
                int tileIdx = piece.tileIndex();
                int wTiles = piece.widthTiles();
                int hTiles = piece.heightTiles();
                int tileCount = wTiles * hTiles;

                if (tileIdx < 0 || tileIdx >= vramToSource.length) {
                    remappedPieces.add(piece);
                    continue;
                }

                int remappedBase = vramToSource[tileIdx];
                boolean contiguous = true;
                for (int t = 1; t < tileCount; t++) {
                    int vramSlot = tileIdx + t;
                    if (vramSlot >= vramToSource.length || vramToSource[vramSlot] != remappedBase + t) {
                        contiguous = false;
                        break;
                    }
                }

                if (contiguous) {
                    remappedPieces.add(new SpriteMappingPiece(
                            piece.xOffset(), piece.yOffset(),
                            wTiles, hTiles,
                            remappedBase, piece.hFlip(), piece.vFlip(),
                            piece.paletteIndex(), piece.priority()));
                    continue;
                }

                for (int tx = 0; tx < wTiles; tx++) {
                    for (int ty = 0; ty < hTiles; ty++) {
                        int tileOffset = tx * hTiles + ty;
                        int vramSlot = tileIdx + tileOffset;
                        int remappedTile = vramSlot < vramToSource.length
                                ? vramToSource[vramSlot]
                                : tileIdx + tileOffset;
                        remappedPieces.add(new SpriteMappingPiece(
                                piece.xOffset() + tx * 8,
                                piece.yOffset() + ty * 8,
                                1, 1,
                                remappedTile, piece.hFlip(), piece.vFlip(),
                                piece.paletteIndex(), piece.priority()));
                    }
                }
            }
            remapped.add(new SpriteMappingFrame(remappedPieces));
        }
        return remapped;
    }

    private static SpriteMappingFrame singlePieceFrame(
            int xOffset, int yOffset, int widthTiles, int heightTiles, int tileIndex, boolean hFlip) {
        SpriteMappingPiece piece = new SpriteMappingPiece(
                xOffset, yOffset, widthTiles, heightTiles, tileIndex, hFlip, false, 0);
        return new SpriteMappingFrame(List.of(piece));
    }

    public static List<SpriteMappingFrame> adjustTileIndices(List<SpriteMappingFrame> frames, int adjustment) {
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

    List<SpriteMappingFrame> buildHczWaterRushMappings() {
        SpriteMappingFrame frame0 = new SpriteMappingFrame(List.of(new SpriteMappingPiece(-64, -32, 4, 4, 0x00, false, false, 0), new SpriteMappingPiece(-32, -32, 2, 4, 0x10, false, false, 0), new SpriteMappingPiece(-16, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(16, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(48, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(80, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(112, -32, 2, 4, 0x18, false, false, 0), new SpriteMappingPiece(-48, 0, 4, 4, 0x00, false, false, 0), new SpriteMappingPiece(-16, 0, 2, 4, 0x28, false, false, 0), new SpriteMappingPiece(0, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(32, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(64, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(96, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(-32, 32, 4, 4, 0x00, false, false, 0), new SpriteMappingPiece(0, 32, 2, 4, 0x28, false, false, 0), new SpriteMappingPiece(16, 32, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(48, 32, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(80, 32, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(112, 32, 2, 4, 0x30, false, false, 0)));
        SpriteMappingFrame frame1 = new SpriteMappingFrame(List.of(new SpriteMappingPiece(-48, -32, 4, 4, 0x00, false, false, 0), new SpriteMappingPiece(-16, -32, 2, 4, 0x40, false, false, 0), new SpriteMappingPiece(0, -32, 2, 4, 0x20, false, false, 0), new SpriteMappingPiece(16, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(48, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(80, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(112, -32, 2, 4, 0x18, false, false, 0), new SpriteMappingPiece(-32, 0, 4, 4, 0x00, false, false, 0), new SpriteMappingPiece(0, 0, 2, 4, 0x48, false, false, 0), new SpriteMappingPiece(16, 0, 2, 4, 0x38, false, false, 0), new SpriteMappingPiece(32, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(64, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(96, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(-16, 32, 4, 4, 0x00, false, false, 0), new SpriteMappingPiece(16, 32, 2, 4, 0x48, false, false, 0), new SpriteMappingPiece(32, 32, 2, 4, 0x38, false, false, 0), new SpriteMappingPiece(48, 32, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(80, 32, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(112, 32, 2, 4, 0x30, false, false, 0)));
        SpriteMappingFrame frame2 = new SpriteMappingFrame(List.of(new SpriteMappingPiece(-32, -32, 2, 4, 0x20, false, false, 0), new SpriteMappingPiece(-16, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(16, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(48, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(-32, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(0, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(32, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(64, 0, 2, 4, 0x30, false, false, 0)));
        SpriteMappingFrame frame3 = new SpriteMappingFrame(List.of(new SpriteMappingPiece(-32, -32, 2, 4, 0x20, false, false, 0), new SpriteMappingPiece(-16, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(16, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(48, -32, 4, 4, 0x18, false, false, 0), new SpriteMappingPiece(-64, 0, 4, 4, 0x00, false, false, 0), new SpriteMappingPiece(-32, 0, 2, 4, 0x48, false, false, 0), new SpriteMappingPiece(-16, 0, 2, 4, 0x38, false, false, 0), new SpriteMappingPiece(0, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(32, 0, 4, 4, 0x30, false, false, 0), new SpriteMappingPiece(64, 0, 2, 4, 0x30, false, false, 0)));
        return List.of(frame0, frame1, frame2, frame3);
    }

    /**
     * Builds hardcoded mappings for HCZ Water Splash subtype 0 (Map_HCZWaterSplash).
     * <p>
     * From the disassembly (Map - Water Splash.asm):
     * Frames 0-3 all share Frame_237C6A layout (2 pieces, 24 tiles per frame).
     * DMA in ROM swaps art per frame; we pre-load all 96 tiles and offset per frame.
     * <pre>
     * Frame_237C6A: 2 pieces
     *   piece 0: y=$F0(-16), size=$0B(3w×4h=12 tiles), tile=0, x=$FFE8(-24)
     *   piece 1: y=$F0(-16), size=$0B(3w×4h=12 tiles), tile=$0C, x=$0000(0)
     * Size $0B: bits 2-3=2 → width=3 tiles, bits 0-1=3 → height=4 tiles
     * (matches S3kSpriteDataLoader: width = ((size>>2)&3)+1, height = (size&3)+1)
     * </pre>
     */
    List<SpriteMappingFrame> buildHczWaterSplashMappings() {
        // 4 animation frames, each uses 24 tiles. Frame N starts at tile N*24.
        List<SpriteMappingFrame> frames = new java.util.ArrayList<>(4);
        for (int f = 0; f < 4; f++) {
            int tileBase = f * 24;
            frames.add(new SpriteMappingFrame(List.of(
                    // Piece 0: 3 wide × 4 tall (12 tiles) at (-24, -16)
                    new SpriteMappingPiece(-24, -16, 3, 4, tileBase, false, false, 0, false),
                    // Piece 1: 3 wide × 4 tall (12 tiles) at (0, -16)
                    new SpriteMappingPiece(0, -16, 3, 4, tileBase + 12, false, false, 0, false)
            )));
        }
        return frames;
    }

    /**
     * Builds hardcoded mappings for HCZ horizontal geyser art (Map_HCZWaterWall).
     * All 11 frames: frame 0 is the wide horizontal wall, frame 1 is the vertical
     * column, frames 2-10 are small splash/debris pieces used by child objects.
     */
    List<SpriteMappingFrame> buildHczGeyserHorzMappings() {
        return buildHczGeyserAllFrames();
    }

    /**
     * Builds hardcoded mappings for HCZ vertical geyser art (Map_HCZWaterWall).
     * All 11 frames shared with horizontal art — frame 1 is the vertical column,
     * frame 0 is the horizontal wall, frames 2-10 are splash/debris.
     */
    List<SpriteMappingFrame> buildHczGeyserVertMappings() {
        return buildHczGeyserAllFrames();
    }

    /**
     * Builds hardcoded mappings for HCZ geyser debris art (Map_HCZWaterWallDebris).
     * ROM: 8 single-piece frames. Tile indices are relative to ArtTile_HCZGeyser+$58,
     * so we offset by 0x58 into the geyser pattern array.
     *
     * <p>Size byte decoding: bits 2-3 = (width-1), bits 0-1 = (height-1).
     * $0E = 4w×3h, $0F = 4w×4h, $0B = 3w×4h.
     */
    List<SpriteMappingFrame> buildHczGeyserDebrisMappings() {
        int tileBase = 0x58; // ArtTile_HCZGeyser+$58 offset into decompressed patterns
        return List.of(
                // Frame 0: ($F8,$0E,$00,$00,$FF,$F0) y=-8, 4×3, tile 0x00
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -8, 4, 3, tileBase + 0x00, false, false, 0))),
                // Frame 1: ($F0,$0F,$00,$0C,$FF,$F0) y=-16, 4×4, tile 0x0C
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -16, 4, 4, tileBase + 0x0C, false, false, 0))),
                // Frame 2: ($F0,$0B,$00,$1C,$FF,$F0) y=-16, 3×4, tile 0x1C
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -16, 3, 4, tileBase + 0x1C, false, false, 0))),
                // Frame 3: ($F0,$0F,$08,$0C,$FF,$F0) y=-16, 4×4, hflip, tile 0x0C
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -16, 4, 4, tileBase + 0x0C, true, false, 0))),
                // Frame 4: ($F0,$0E,$10,$00,$FF,$F0) y=-16, 4×3, vflip, tile 0x00
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -16, 4, 3, tileBase + 0x00, false, true, 0))),
                // Frame 5: ($F0,$0F,$18,$0C,$FF,$F0) y=-16, 4×4, hflip+vflip, tile 0x0C
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -16, 4, 4, tileBase + 0x0C, true, true, 0))),
                // Frame 6: ($F0,$0B,$08,$1C,$FF,$F8) y=-16, 3×4, hflip, tile 0x1C, x=-8
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -16, 3, 4, tileBase + 0x1C, true, false, 0))),
                // Frame 7: ($F0,$0F,$10,$0C,$FF,$F0) y=-16, 4×4, vflip, tile 0x0C
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-16, -16, 4, 4, tileBase + 0x0C, false, true, 0)))
        );
    }

    /**
     * All 11 frames from Map_HCZWaterWall in the S3K disassembly.
     * <p>Frame 0: 14-piece horizontal geyser wall (256x64).
     * <p>Frame 1: 12-piece vertical geyser column (64x192).
     * <p>Frames 2-10: small splash/debris pieces (1 piece each).
     */
    private List<SpriteMappingFrame> buildHczGeyserAllFrames() {
        // Frame 0 — horizontal geyser wall (14 pieces)
        SpriteMappingFrame frame0 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-128, -32, 4, 4, 0x00, false, false, 0),
                new SpriteMappingPiece(-96, -32, 4, 4, 0x00, false, false, 0),
                new SpriteMappingPiece(-64, -32, 4, 4, 0x00, false, false, 0),
                new SpriteMappingPiece(-32, -32, 4, 4, 0x00, false, false, 0),
                new SpriteMappingPiece(0, -32, 4, 4, 0x00, false, false, 0),
                new SpriteMappingPiece(32, -32, 4, 4, 0x10, false, false, 0),
                new SpriteMappingPiece(64, -32, 4, 4, 0x20, false, false, 0),
                new SpriteMappingPiece(-128, 0, 4, 4, 0x00, false, true, 0),
                new SpriteMappingPiece(-96, 0, 4, 4, 0x00, false, true, 0),
                new SpriteMappingPiece(-64, 0, 4, 4, 0x00, false, true, 0),
                new SpriteMappingPiece(-32, 0, 4, 4, 0x00, false, true, 0),
                new SpriteMappingPiece(0, 0, 4, 4, 0x00, false, true, 0),
                new SpriteMappingPiece(32, 0, 4, 4, 0x10, false, true, 0),
                new SpriteMappingPiece(64, 0, 4, 4, 0x20, false, true, 0)));

        // Frame 1 — vertical geyser column (12 pieces)
        SpriteMappingFrame frame1 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-32, -96, 4, 4, 0x00, false, false, 0),
                new SpriteMappingPiece(0, -96, 4, 4, 0x00, true, false, 0),
                new SpriteMappingPiece(-32, -64, 4, 4, 0x10, false, false, 0),
                new SpriteMappingPiece(0, -64, 4, 4, 0x10, true, false, 0),
                new SpriteMappingPiece(-32, -32, 4, 4, 0x20, false, false, 0),
                new SpriteMappingPiece(0, -32, 4, 4, 0x20, true, false, 0),
                new SpriteMappingPiece(-32, 0, 4, 4, 0x20, false, false, 0),
                new SpriteMappingPiece(0, 0, 4, 4, 0x20, true, false, 0),
                new SpriteMappingPiece(-32, 32, 4, 4, 0x20, false, false, 0),
                new SpriteMappingPiece(0, 32, 4, 4, 0x20, true, false, 0),
                new SpriteMappingPiece(-32, 64, 4, 4, 0x20, false, false, 0),
                new SpriteMappingPiece(0, 64, 4, 4, 0x20, true, false, 0)));

        // Frames 2-10 — small splash/debris pieces (1 piece each)
        SpriteMappingFrame frame2 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -4, 1, 1, 0x00, false, false, 0)));
        SpriteMappingFrame frame3 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x1A, false, false, 0)));
        SpriteMappingFrame frame4 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x1E, false, false, 0)));
        SpriteMappingFrame frame5 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x22, false, false, 0)));
        SpriteMappingFrame frame6 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -4, 1, 1, 0x26, false, false, 0)));
        SpriteMappingFrame frame7 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -4, 1, 1, 0x27, false, false, 0)));
        SpriteMappingFrame frame8 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -6, 2, 1, 0x00, false, false, 0)));
        SpriteMappingFrame frame9 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-16, -22, 4, 3, 0x02, false, false, 0)));
        SpriteMappingFrame frame10 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-16, -22, 4, 3, 0x0E, false, false, 0)));

        return List.of(frame0, frame1, frame2, frame3, frame4, frame5,
                frame6, frame7, frame8, frame9, frame10);
    }

    /**
     * Builds mappings for HCZ geyser spray/splash art.
     * ROM: spray children use art_tile = ArtTile_HCZGeyser+$30, meaning all mapping
     * tile indices are relative to offset $30 within the decompressed geyser patterns.
     * Since our standalone sheets index from 0, we add $30 to every tile index.
     * Only frames 2-10 are used by spray/splash children.
     */
    /**
     * Builds hardcoded mappings for HCZ fan bubble (Map_Bubbler, frames 0-5).
     * Small bubble sprites spawned by Obj_HCZCGZFan when subtype bit 6 is set.
     * <p>
     * ROM: Map_Bubbler (docs/skdisasm/General/Sprites/Bubbles/Map - Bubbler.asm).
     * Frame 0: 1×1 tile (8×8), tile 0 — tiny bubble dot.
     * Frame 1: 1×1 tile (8×8), tile 1 — slightly different dot.
     * Frame 2: 1×1 tile (8×8), tile 2 — medium dot.
     * Frame 3: 2×2 tile (16×16), tile 3 — small bubble.
     * Frame 4: 2×2 tile (16×16), tile 7 — medium bubble.
     * Frame 5: 3×3 tile (24×24), tile $B — large bubble.
     */
    List<SpriteMappingFrame> buildHczFanBubbleMappings() {
        return List.of(
                // Frame 0: dc.b $FC,$00,$00,$00,$FF,$FC — 1×1 at (-4,-4), tile 0
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-4, -4, 1, 1, 0, false, false, 0))),
                // Frame 1: dc.b $FC,$00,$00,$01,$FF,$FC — 1×1 at (-4,-4), tile 1
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-4, -4, 1, 1, 1, false, false, 0))),
                // Frame 2: dc.b $FC,$00,$00,$02,$FF,$FC — 1×1 at (-4,-4), tile 2
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-4, -4, 1, 1, 2, false, false, 0))),
                // Frame 3: dc.b $F8,$05,$00,$03,$FF,$F8 — 2×2 at (-8,-8), tile 3
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -8, 2, 2, 3, false, false, 0))),
                // Frame 4: dc.b $F8,$05,$00,$07,$FF,$F8 — 2×2 at (-8,-8), tile 7
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-8, -8, 2, 2, 7, false, false, 0))),
                // Frame 5: dc.b $F4,$0A,$00,$0B,$FF,$F4 — 3×3 at (-12,-12), tile $B
                new SpriteMappingFrame(List.of(
                        new SpriteMappingPiece(-12, -12, 3, 3, 0x0B, false, false, 0)))
        );
    }

    /**
     * Builds the subset of Map_Bubbler frames used by Obj_Bubbler.
     * <p>
     * The object only references frames 0-8 (bubble growth/burst) and 19-21
     * (floor vent animation). Frames 9-18 reference high VRAM countdown tiles
     * and are left blank here because the Bubbler object never animates through them.
     * <p>
     * ROM: General/Sprites/Bubbles/Map - Bubbler.asm
     */
    List<SpriteMappingFrame> buildBubblerMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>(22);

        frames.add(single(-4, -4, 1, 1, 0x00));   // 0
        frames.add(single(-4, -4, 1, 1, 0x01));   // 1
        frames.add(single(-4, -4, 1, 1, 0x02));   // 2
        frames.add(single(-8, -8, 2, 2, 0x03));   // 3
        frames.add(single(-8, -8, 2, 2, 0x07));   // 4
        frames.add(single(-12, -12, 3, 3, 0x0B)); // 5
        frames.add(single(-16, -16, 4, 4, 0x14)); // 6
        frames.add(new SpriteMappingFrame(List.of( // 7
                new SpriteMappingPiece(-16, -16, 2, 2, 0x24, false, false, 0),
                new SpriteMappingPiece(0, -16, 2, 2, 0x24, true, false, 0),
                new SpriteMappingPiece(-16, 0, 2, 2, 0x24, false, true, 0),
                new SpriteMappingPiece(0, 0, 2, 2, 0x24, true, true, 0))));
        frames.add(new SpriteMappingFrame(List.of( // 8
                new SpriteMappingPiece(-16, -16, 2, 2, 0x28, false, false, 0),
                new SpriteMappingPiece(0, -16, 2, 2, 0x28, true, false, 0),
                new SpriteMappingPiece(-16, 0, 2, 2, 0x28, false, true, 0),
                new SpriteMappingPiece(0, 0, 2, 2, 0x28, true, true, 0))));

        for (int i = 9; i <= 18; i++) {
            frames.add(new SpriteMappingFrame(List.of()));
        }

        frames.add(single(-8, -8, 2, 2, 0x2C));   // 19
        frames.add(single(-8, -8, 2, 2, 0x30));   // 20
        frames.add(single(-8, -8, 2, 2, 0x34));   // 21

        return frames;
    }

    private SpriteMappingFrame single(int x, int y, int w, int h, int tile) {
        return new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(x, y, w, h, tile, false, false, 0)));
    }

    List<SpriteMappingFrame> buildHczGeyserSprayMappings() {
        int ofs = 0x30; // ArtTile_HCZGeyser+$30 offset
        // Frame 0 and 1 are unused placeholders (spray only uses frames 2-10)
        SpriteMappingFrame placeholder = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0, 0, 1, 1, ofs, false, false, 0)));
        // Frames 2-10: same layout as main geyser but tile indices + $30
        SpriteMappingFrame f2 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -4, 1, 1, ofs + 0x00, false, false, 0)));
        SpriteMappingFrame f3 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, ofs + 0x1A, false, false, 0)));
        SpriteMappingFrame f4 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, ofs + 0x1E, false, false, 0)));
        SpriteMappingFrame f5 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, ofs + 0x22, false, false, 0)));
        SpriteMappingFrame f6 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -4, 1, 1, ofs + 0x26, false, false, 0)));
        SpriteMappingFrame f7 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -4, 1, 1, ofs + 0x27, false, false, 0)));
        SpriteMappingFrame f8 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -6, 2, 1, ofs + 0x00, false, false, 0)));
        SpriteMappingFrame f9 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-16, -22, 4, 3, ofs + 0x02, false, false, 0)));
        SpriteMappingFrame f10 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-16, -22, 4, 3, ofs + 0x0E, false, false, 0)));
        return List.of(placeholder, placeholder, f2, f3, f4, f5, f6, f7, f8, f9, f10);
    }

    /**
     * Builds the HCZ Water Drop sprite sheet (Map_HCZWaterDrop, frames 0-5 only).
     * <p>
     * Frames 0-5 use tiles from ArtTile_HCZ2Slide ($035C), palette 1.
     * Frame 6 (spawner static drip) is not included: the ROM renders it via
     * Sprite_OnScreen_Test, but art_tile addition overflows ($235C + $FCA4 = $2000)
     * producing tile 0 / palette 1, which is effectively invisible in-game.
     */
    public ObjectSpriteSheet buildHczWaterDropSheet() {
        if (level == null) return null;

        int artTileBase = Sonic3kConstants.ARTTILE_HCZ2_SLIDE; // 0x035C
        int levelPatternCount = level.getPatternCount();

        // Tiles at artTileBase + 0..0xB (12 tiles from HCZ2Slide art)
        int tileCount = 12;
        Pattern[] patterns = new Pattern[tileCount];
        for (int i = 0; i < tileCount; i++) {
            int levelIndex = artTileBase + i;
            patterns[i] = (levelIndex < levelPatternCount)
                    ? level.getPattern(levelIndex) : new Pattern();
        }

        // Mapping pieces from Map_HCZWaterDrop (sonic3k.asm, Map - Water Drop.asm)
        // Frame 0: 2x1, tile 0
        SpriteMappingFrame f0 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 1, 0, false, false, 0)));
        // Frame 1: 2x1, tile 2
        SpriteMappingFrame f1 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 1, 2, false, false, 0)));
        // Frame 2: 1x2, tile 4
        SpriteMappingFrame f2 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -8, 1, 2, 4, false, false, 0)));
        // Frame 3: 1x2, tile 6
        SpriteMappingFrame f3 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -8, 1, 2, 6, false, false, 0)));
        // Frame 4: 2x1, tile 8
        SpriteMappingFrame f4 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, 0, 2, 1, 8, false, false, 0)));
        // Frame 5: 2x1, tile 0xA
        SpriteMappingFrame f5 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, 0, 2, 1, 0xA, false, false, 0)));

        return new ObjectSpriteSheet(patterns,
                List.of(f0, f1, f2, f3, f4, f5), 1, 1);
    }

    public ObjectSpriteSheet buildHczWaterRushBlockSheet() {
        SpriteMappingFrame f0 = new SpriteMappingFrame(List.of(new SpriteMappingPiece(-16, -16, 4, 4, 0x00, false, false, 0)));
        SpriteMappingFrame f1 = new SpriteMappingFrame(List.of(new SpriteMappingPiece(-16, -32, 4, 4, 0x00, false, false, 0), new SpriteMappingPiece(-16, 0, 4, 4, 0x00, false, false, 0)));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_HCZ_WATER_RUSH_BLOCK, 2, List.of(f0, f1), 0, 16);
    }

    /**
     * Builds vertical door sheet for HCZ.
     * Uses ArtTile_HCZMisc + $0A = $03D4, palette 2.
     * Subtype 0 / mapping frame 0.
     * From Map - (&CNZ &DEZ) Door.asm (Map_HCZCNZDEZDoor).
     */
    public ObjectSpriteSheet buildDoorVerticalHczSheet() {
        SpriteMappingFrame f0 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-16, -32, 4, 4, 0, false, false, 0, false),
                new SpriteMappingPiece(-16, 0, 4, 4, 0, false, false, 0, false)
        ));
        int artTileBase = Sonic3kConstants.ARTTILE_HCZ_MISC + 0x0A;
        return buildLevelArtSheet(artTileBase, 2, List.of(f0), 0, 16);
    }

    /**
     * Builds vertical door sheet for CNZ.
     * Uses ArtTile_CNZMisc + $C5 = $0416, palette 2.
     * Subtype 1 / mapping frame 1.
     * From Map - (&CNZ &DEZ) Door.asm (Map_HCZCNZDEZDoor).
     */
    public ObjectSpriteSheet buildDoorVerticalCnzSheet() {
        SpriteMappingFrame f1 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -32, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-8, -16, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-8, 0, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-8, 16, 2, 2, 0, false, false, 0, false)
        ));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_CNZ_MISC + 0xC5, 2, List.of(f1), 0, 16);
    }

    /**
     * Builds vertical door sheet for DEZ.
     * Uses ArtTile_DEZMisc + $1E = $036B, palette 1.
     * Subtype 2 / mapping frame 2.
     * From Map - (&CNZ &DEZ) Door.asm (Map_HCZCNZDEZDoor).
     */
    public ObjectSpriteSheet buildDoorVerticalDezSheet() {
        SpriteMappingFrame f2 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-16, -32, 2, 4, 0, false, false, 0, false),
                new SpriteMappingPiece(0, -32, 2, 4, 8, false, false, 0, false),
                new SpriteMappingPiece(-16, 0, 2, 4, 0, false, false, 0, false),
                new SpriteMappingPiece(0, 0, 2, 4, 8, false, false, 0, false)
        ));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_DEZ_MISC + 0x1E, 1, List.of(f2), 0, 16);
    }

    /**
     * Builds horizontal door sheet.
     * Uses ArtTile_CNZMisc + $C5 = $0416, palette 2.
     * One frame: 4 pieces (size=$05, 2x2=16x16 each), tile 0
     * From Map - Door Horizontal.asm (Map_CNZDoorHorizontal).
     */
    public ObjectSpriteSheet buildDoorHorizontalSheet() {
        SpriteMappingFrame f0 = new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-32, -8, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(-16, -8, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(   0, -8, 2, 2, 0, false, false, 0, false),
                new SpriteMappingPiece(  16, -8, 2, 2, 0, false, false, 0, false)
        ));
        return buildLevelArtSheet(Sonic3kConstants.ARTTILE_CNZ_MISC + 0xC5, 2, List.of(f0), 0, 4);
    }
}
