package com.openggf.game.sonic1;

import com.openggf.data.Rom;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.level.Pattern;
import com.openggf.level.rings.RingFrame;
import com.openggf.level.rings.RingFramePiece;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.util.PatternDecompressor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads ring art and creates mapping frames for Sonic 1.
 *
 * <p>Sonic 1 ring mappings from {@code _maps/Rings.asm}:
 * <ul>
 *   <li>Frames 0-3: spin animation (front, angled, edge, angled-mirrored)</li>
 *   <li>Frames 4-7: sparkle animation (collection effect)</li>
 * </ul>
 *
 * <p>Art is Nemesis-compressed at {@code Nem_Ring} (0x39A0E).
 * Palette index 1 (same as Sonic 2).
 */
public class Sonic1RingArt {

    private static final int RING_PALETTE_INDEX = 1;
    private static final int RING_FRAME_DELAY = 8;
    private static final int SPIN_FRAME_COUNT = 4;
    private static final int SPARKLE_FRAME_COUNT = 4;

    private final Rom rom;
    private RingSpriteSheet cached;

    public Sonic1RingArt(Rom rom) {
        this.rom = rom;
    }

    public RingSpriteSheet load() throws IOException {
        if (cached != null) {
            return cached;
        }

        Pattern[] patterns = loadRingPatterns();
        List<RingFrame> frames = buildRingFrames();

        cached = new RingSpriteSheet(patterns, frames, RING_PALETTE_INDEX, RING_FRAME_DELAY,
                SPIN_FRAME_COUNT, SPARKLE_FRAME_COUNT);
        return cached;
    }

    private Pattern[] loadRingPatterns() throws IOException {
        return PatternDecompressor.nemesis(rom, Sonic1Constants.ART_NEM_RING_ADDR);
    }

    /**
     * Builds the 8 ring mapping frames from hardcoded data matching
     * {@code _maps/Rings.asm} in the Sonic 1 disassembly.
     *
     * <p>Mapping format (Sonic 1 5-byte piece format):
     * <pre>
     *   Frame 0 (front):   1 piece, 2x2 tiles at (-8,-8), tile 0
     *   Frame 1 (angle1):  1 piece, 2x2 tiles at (-8,-8), tile 4
     *   Frame 2 (edge):    1 piece, 1x2 tiles at (-4,-8), tile 8
     *   Frame 3 (angle2):  1 piece, 2x2 tiles at (-8,-8), tile 4, hFlip
     *   Frame 4 (sparkle1): 1 piece, 2x2 tiles at (-8,-8), tile 0xA
     *   Frame 5 (sparkle2): 1 piece, 2x2 tiles at (-8,-8), tile 0xA, hFlip
     *   Frame 6 (sparkle3): 1 piece, 2x2 tiles at (-8,-8), tile 0xA, vFlip
     *   Frame 7 (sparkle4): 1 piece, 2x2 tiles at (-8,-8), tile 0xA, hFlip+vFlip
     * </pre>
     */
    private List<RingFrame> buildRingFrames() {
        List<RingFrame> frames = new ArrayList<>(8);

        // Frame 0: front face (2x2 at tile 0)
        frames.add(singlePieceFrame(-8, -8, 2, 2, 0, false, false));

        // Frame 1: angled (2x2 at tile 4)
        frames.add(singlePieceFrame(-8, -8, 2, 2, 4, false, false));

        // Frame 2: edge-on (1x2 at tile 8)
        frames.add(singlePieceFrame(-4, -8, 1, 2, 8, false, false));

        // Frame 3: angled mirrored (2x2 at tile 4, hFlip)
        frames.add(singlePieceFrame(-8, -8, 2, 2, 4, true, false));

        // Frame 4: sparkle 1 (2x2 at tile 0xA)
        frames.add(singlePieceFrame(-8, -8, 2, 2, 0xA, false, false));

        // Frame 5: sparkle 2 (2x2 at tile 0xA, hFlip)
        frames.add(singlePieceFrame(-8, -8, 2, 2, 0xA, true, false));

        // Frame 6: sparkle 3 (2x2 at tile 0xA, vFlip)
        frames.add(singlePieceFrame(-8, -8, 2, 2, 0xA, false, true));

        // Frame 7: sparkle 4 (2x2 at tile 0xA, hFlip + vFlip)
        frames.add(singlePieceFrame(-8, -8, 2, 2, 0xA, true, true));

        return frames;
    }

    private RingFrame singlePieceFrame(int xOff, int yOff, int wTiles, int hTiles,
                                        int tileIndex, boolean hFlip, boolean vFlip) {
        // Piece palette must be 0 because SpritePieceRenderer ADDS the piece palette
        // to the sheet's default palette (RING_PALETTE_INDEX=1). Using 0 here gives
        // (0 + 1) & 3 = 1, matching the original game's palette line 1 for rings.
        return new RingFrame(List.of(
                new RingFramePiece(xOff, yOff, wTiles, hTiles, tileIndex, hFlip, vFlip, 0)
        ));
    }
}
