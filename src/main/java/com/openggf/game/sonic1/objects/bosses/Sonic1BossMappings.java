package com.openggf.game.sonic1.objects.bosses;

import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprite mapping data for all Sonic 1 bosses (Eggman, weapons, ball).
 * Parsed from docs/s1disasm/_maps/Eggman.asm, _maps/GHZ Ball.asm, _maps/Boss Items.asm.
 *
 * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
 */
public final class Sonic1BossMappings {

    private Sonic1BossMappings() {
    }

    /**
     * Eggman mappings (Map_Eggman_internal) — 13 frames.
     * Used by the ship, face overlay, and flame overlay.
     */
    public static List<SpriteMappingFrame> createEggmanMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .ship (6 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x1C, -0x14, 1, 2, 0x0A, false, false, 0, false),
                new SpriteMappingPiece( 0x0C, -0x14, 2, 2, 0x0C, false, false, 0, false),
                new SpriteMappingPiece(-0x1C,  -0x4, 4, 3, 0x10, false, false, 1, false),
                new SpriteMappingPiece( 0x04,  -0x4, 4, 3, 0x1C, false, false, 1, false),
                new SpriteMappingPiece(-0x14,  0x14, 4, 1, 0x28, false, false, 1, false),
                new SpriteMappingPiece( 0x0C,  0x14, 1, 1, 0x2C, false, false, 1, false)
        )));

        // Frame 1: .facenormal1 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 2, 1, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 4, 2, 0x02, false, false, 0, false)
        )));

        // Frame 2: .facenormal2 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 2, 1, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 4, 2, 0x35, false, false, 0, false)
        )));

        // Frame 3: .facelaugh1 (3 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 3, 1, 0x3D, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 3, 2, 0x40, false, false, 0, false),
                new SpriteMappingPiece( 0x04, -0x14, 2, 2, 0x46, false, false, 0, false)
        )));

        // Frame 4: .facelaugh2 (3 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 3, 1, 0x4A, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 3, 2, 0x4D, false, false, 0, false),
                new SpriteMappingPiece( 0x04, -0x14, 2, 2, 0x53, false, false, 0, false)
        )));

        // Frame 5: .facehit (3 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 3, 1, 0x57, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 3, 2, 0x5A, false, false, 0, false),
                new SpriteMappingPiece( 0x04, -0x14, 2, 2, 0x60, false, false, 0, false)
        )));

        // Frame 6: .facepanic (3 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece( 0x04, -0x1C, 2, 1, 0x64, false, false, 0, false),
                new SpriteMappingPiece(-0x0C, -0x1C, 2, 1, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 4, 2, 0x35, false, false, 0, false)
        )));

        // Frame 7: .facedefeat (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 3, 2, 0x66, false, false, 0, false),
                new SpriteMappingPiece(-0x0C, -0x1C, 3, 1, 0x57, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 3, 2, 0x5A, false, false, 0, false),
                new SpriteMappingPiece( 0x04, -0x14, 2, 2, 0x60, false, false, 0, false)
        )));

        // Frame 8: .flame1 (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x22, 0x04, 2, 2, 0x2D, false, false, 0, false)
        )));

        // Frame 9: .flame2 (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x22, 0x04, 2, 2, 0x31, false, false, 0, false)
        )));

        // Frame 10: .blank (0 pieces)
        frames.add(new SpriteMappingFrame(List.of()));

        // Frame 11: .escapeflame1 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x22,  0x00, 3, 1, 0x12A, false, false, 0, false),
                new SpriteMappingPiece(0x22,  0x08, 3, 1, 0x12A, false, true,  0, false)
        )));

        // Frame 12: .escapeflame2 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(0x22, -0x08, 3, 4, 0x12D, false, false, 0, false),
                new SpriteMappingPiece(0x3A,  0x00, 1, 2, 0x139, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * GHZ Ball mappings (Map_GBall_internal) — 4 frames.
     * Used by the wrecking ball at the end of the chain.
     */
    public static List<SpriteMappingFrame> createGHZBallMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .shiny (6 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 2, 1, 0x24, false, false, 0, false),
                new SpriteMappingPiece(-0x10,  -0x8, 2, 1, 0x24, false, true,  0, false),
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x00, false, false, 0, false),
                new SpriteMappingPiece( 0x00, -0x18, 3, 3, 0x00, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,  0x00, 3, 3, 0x00, false, true,  0, false),
                new SpriteMappingPiece( 0x00,  0x00, 3, 3, 0x00, true,  true,  0, false)
        )));

        // Frame 1: .check1 (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x09, false, false, 0, false),
                new SpriteMappingPiece( 0x00, -0x18, 3, 3, 0x09, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,  0x00, 3, 3, 0x09, false, true,  0, false),
                new SpriteMappingPiece( 0x00,  0x00, 3, 3, 0x09, true,  true,  0, false)
        )));

        // Frame 2: .check2 (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x12, false, false, 0, false),
                new SpriteMappingPiece( 0x00, -0x18, 3, 3, 0x1B, false, false, 0, false),
                new SpriteMappingPiece(-0x18,  0x00, 3, 3, 0x1B, true,  true,  0, false),
                new SpriteMappingPiece( 0x00,  0x00, 3, 3, 0x12, true,  true,  0, false)
        )));

        // Frame 3: .check3 (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18, -0x18, 3, 3, 0x1B, true,  false, 0, false),
                new SpriteMappingPiece( 0x00, -0x18, 3, 3, 0x12, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,  0x00, 3, 3, 0x12, false, true,  0, false),
                new SpriteMappingPiece( 0x00,  0x00, 3, 3, 0x1B, false, true,  0, false)
        )));

        return frames;
    }

    /**
     * Boss Items mappings (Map_BossItems_internal) — chain anchor + tube frames.
     * Frames 0-1: GHZ boss chain anchors.
     * Frames 2-3: Placeholder (unused intermediate frames).
     * Frame 4: MZ boss exhaust tube pipe piece.
     */
    public static List<SpriteMappingFrame> createBossItemsMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .chainanchor1 (1 piece — GHZ boss anchor)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x00, false, false, 0, false)
        )));

        // Frame 1: .chainanchor2 (2 pieces — GHZ boss anchor variant)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -4, 2, 1, 0x04, false, false, 0, false),
                new SpriteMappingPiece(-8, -8, 2, 2, 0x00, false, false, 0, false)
        )));

        // Frame 2: .cross (1 piece — small cross marker)
        // ROM: spritePiece -4, -4, 1, 1, 6, 0, 0, 0, 0
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-4, -4, 1, 1, 0x06, false, false, 0, false)
        )));

        // Frame 3: .widepipe (1 piece — SLZ boss exhaust tube)
        // ROM: spritePiece -$C, $14, 3, 2, 7, 0, 0, 0, 0
        // Uses ArtTile_Eggman_Weapons with palette line 1
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, 0x14, 3, 2, 0x07, false, false, 0, false)
        )));

        // Frame 4: .pipe (1 piece — MZ boss exhaust tube)
        // ROM: spritePiece -8, $14, 2, 2, $D, 0, 0, 0, 0
        // Uses ArtTile_Eggman_Weapons with palette line 1
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, 0x14, 2, 2, 0x0D, false, false, 0, false)
        )));

        // Frame 5: .spike (4 pieces — SYZ boss spike)
        // ROM: _maps/Boss Items.asm
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -0x10, 2, 1, 0x11, false, false, 0, false),
                new SpriteMappingPiece(-8,    -8, 1, 2, 0x13, false, false, 0, false),
                new SpriteMappingPiece( 0,    -8, 1, 2, 0x13, true,  false, 0, false),
                new SpriteMappingPiece(-8,     8, 2, 1, 0x15, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * SBZ2/FZ Eggman mappings (Map_SEgg) — 11 frames.
     * Used by the Eggman sprite in Scrap Brain Zone Act 2 and Final Zone.
     */
    public static List<SpriteMappingFrame> createSEggMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .stand (3 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x18,    -4, 1, 1, 0x8F, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x18, 4, 3, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 4, 4, 0x6F, false, false, 0, false)
        )));

        // Frame 1: .laugh1 (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x18, 4, 2, 0x0E, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x18, 4, 3, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 4, 4, 0x6F, false, false, 0, false),
                new SpriteMappingPiece(-0x18,    -4, 1, 1, 0x8F, false, false, 0, false)
        )));

        // Frame 2: .laugh2 (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x17, 4, 2, 0x0E, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x17, 4, 3, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     1, 4, 4, 0x7F, false, false, 0, false),
                new SpriteMappingPiece(-0x18,    -3, 1, 1, 0x8F, false, false, 0, false)
        )));

        // Frame 3: .jump1 (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x0C, 4, 4, 0x20, true,  false, 0, false),
                new SpriteMappingPiece( 0x10, -0x0B, 2, 1, 0x30, true,  false, 0, false),
                new SpriteMappingPiece(-0x10,     8, 3, 2, 0x4E, true,  false, 0, false),
                new SpriteMappingPiece(-0x10, -0x14, 4, 3, 0x00, false, false, 0, false)
        )));

        // Frame 4: .jump2 (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x20, true,  false, 0, false),
                new SpriteMappingPiece( 0x10, -0x0F, 2, 1, 0x30, true,  false, 0, false),
                new SpriteMappingPiece(   -8,     8, 2, 3, 0x3E, true,  false, 0, false),
                new SpriteMappingPiece(-0x10, -0x18, 4, 3, 0x00, false, false, 0, false)
        )));

        // Frame 5: .surprise (4 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x18, 4, 2, 0x16, false, false, 0, false),
                new SpriteMappingPiece( 0x0C, -0x18, 1, 2, 0x1E, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x18, 4, 3, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 4, 4, 0x6F, false, false, 0, false)
        )));

        // Frame 6: .starjump (7 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x18, 4, 2, 0x16, false, false, 0, false),
                new SpriteMappingPiece( 0x0C, -0x18, 1, 2, 0x1E, false, false, 0, false),
                new SpriteMappingPiece(    0,     4, 3, 2, 0x34, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,     4, 2, 2, 0x3A, true,  false, 0, false),
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x20, true,  false, 0, false),
                new SpriteMappingPiece( 0x10, -0x0F, 2, 1, 0x54, true,  false, 0, false),
                new SpriteMappingPiece(-0x20, -0x0F, 2, 1, 0x54, false, false, 0, false)
        )));

        // Frame 7: .running1 (5 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 4, 0x20, true,  false, 0, false),
                new SpriteMappingPiece( 0x10, -0x0F, 2, 1, 0x30, true,  false, 0, false),
                new SpriteMappingPiece(    0,     4, 3, 2, 0x34, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,     4, 2, 2, 0x3A, true,  false, 0, false),
                new SpriteMappingPiece(-0x10, -0x18, 4, 3, 0x00, false, false, 0, false)
        )));

        // Frame 8: .running2 (6 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x12, 4, 4, 0x20, true,  false, 0, false),
                new SpriteMappingPiece( 0x10, -0x11, 2, 1, 0x30, true,  false, 0, false),
                new SpriteMappingPiece(    0,     9, 2, 2, 0x44, true,  false, 0, false),
                new SpriteMappingPiece(   -8,     3, 1, 2, 0x48, true,  false, 0, false),
                new SpriteMappingPiece(-0x18,  0x0B, 2, 2, 0x4A, true,  false, 0, false),
                new SpriteMappingPiece(-0x10, -0x1A, 4, 3, 0x00, false, false, 0, false)
        )));

        // Frame 9: .intube (8 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, -0x18, 4, 2, 0x16, false, false, 0, false),
                new SpriteMappingPiece( 0x0C, -0x18, 1, 2, 0x1E, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x18, 4, 3, 0x00, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 4, 4, 0x6F, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -0x20, 4, 2, 0x6F0, true,  true,  1, false),
                new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x6F0, true,  true,  1, false),
                new SpriteMappingPiece(-0x10,     0, 4, 2, 0x6F0, true,  true,  1, false),
                new SpriteMappingPiece(-0x10,  0x10, 4, 2, 0x6F0, true,  true,  1, false)
        )));

        // Frame 10: .cockpit (3 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x1C, -0x14, 4, 2, 0x56, false, false, 0, false),
                new SpriteMappingPiece(    4, -0x0C, 3, 1, 0x5E, false, false, 0, false),
                new SpriteMappingPiece(   -4, -0x14, 4, 2, 0x61, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * FZ Cylinder mappings (Map_EggCyl) — 12 frames.
     * Used by the extending cylinders in Final Zone.
     */
    public static List<SpriteMappingFrame> createEggCylMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .flat (6 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x60, 4, 2, 0x00, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x60, 4, 2, 0x00, true,  false, 2, false),
                new SpriteMappingPiece(-0x20, -0x50, 4, 1, 0x08, false, false, 1, false),
                new SpriteMappingPiece(    0, -0x50, 4, 1, 0x0C, false, false, 1, false),
                new SpriteMappingPiece(-0x20, -0x48, 4, 4, 0x10, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x48, 4, 4, 0x10, true,  false, 2, false)
        )));

        // Frame 1: .extending1 (8 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x60, 4, 2, 0x00, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x60, 4, 2, 0x00, true,  false, 2, false),
                new SpriteMappingPiece(-0x20, -0x50, 4, 1, 0x08, false, false, 1, false),
                new SpriteMappingPiece(    0, -0x50, 4, 1, 0x0C, false, false, 1, false),
                new SpriteMappingPiece(-0x20, -0x48, 4, 4, 0x10, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x48, 4, 4, 0x10, true,  false, 2, false),
                new SpriteMappingPiece(-0x20, -0x28, 4, 4, 0x20, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x28, 4, 4, 0x20, true,  false, 2, false)
        )));

        // Frame 2: .extending2 (10 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x60, 4, 2, 0x00, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x60, 4, 2, 0x00, true,  false, 2, false),
                new SpriteMappingPiece(-0x20, -0x50, 4, 1, 0x08, false, false, 1, false),
                new SpriteMappingPiece(    0, -0x50, 4, 1, 0x0C, false, false, 1, false),
                new SpriteMappingPiece(-0x20, -0x48, 4, 4, 0x10, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x48, 4, 4, 0x10, true,  false, 2, false),
                new SpriteMappingPiece(-0x20, -0x28, 4, 4, 0x20, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x28, 4, 4, 0x20, true,  false, 2, false),
                new SpriteMappingPiece(-0x20,    -8, 4, 4, 0x30, false, false, 2, false),
                new SpriteMappingPiece(    0,    -8, 4, 4, 0x30, true,  false, 2, false)
        )));

        // Frame 3: .extending3 (12 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x60, 4, 2, 0x00, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x60, 4, 2, 0x00, true,  false, 2, false),
                new SpriteMappingPiece(-0x20, -0x50, 4, 1, 0x08, false, false, 1, false),
                new SpriteMappingPiece(    0, -0x50, 4, 1, 0x0C, false, false, 1, false),
                new SpriteMappingPiece(-0x20, -0x48, 4, 4, 0x10, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x48, 4, 4, 0x10, true,  false, 2, false),
                new SpriteMappingPiece(-0x20, -0x28, 4, 4, 0x20, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x28, 4, 4, 0x20, true,  false, 2, false),
                new SpriteMappingPiece(-0x20,    -8, 4, 4, 0x30, false, false, 2, false),
                new SpriteMappingPiece(    0,    -8, 4, 4, 0x30, true,  false, 2, false),
                new SpriteMappingPiece(-0x20,  0x18, 4, 4, 0x40, false, false, 2, false),
                new SpriteMappingPiece(    0,  0x18, 4, 4, 0x40, true,  false, 2, false)
        )));

        // Frame 4: .extending4 (13 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x20, -0x60, 4, 2, 0x00, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x60, 4, 2, 0x00, true,  false, 2, false),
                new SpriteMappingPiece(-0x20, -0x50, 4, 1, 0x08, false, false, 1, false),
                new SpriteMappingPiece(    0, -0x50, 4, 1, 0x0C, false, false, 1, false),
                new SpriteMappingPiece(-0x20, -0x48, 4, 4, 0x10, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x48, 4, 4, 0x10, true,  false, 2, false),
                new SpriteMappingPiece(-0x20, -0x28, 4, 4, 0x20, false, false, 2, false),
                new SpriteMappingPiece(    0, -0x28, 4, 4, 0x20, true,  false, 2, false),
                new SpriteMappingPiece(-0x20,    -8, 4, 4, 0x30, false, false, 2, false),
                new SpriteMappingPiece(    0,    -8, 4, 4, 0x30, true,  false, 2, false),
                new SpriteMappingPiece(-0x20,  0x18, 4, 4, 0x40, false, false, 2, false),
                new SpriteMappingPiece(    0,  0x18, 4, 4, 0x40, true,  false, 2, false),
                new SpriteMappingPiece(-0x10,  0x38, 4, 4, 0x50, false, false, 2, false)
        )));

        // Frames 5-10: .extendedfully (14 pieces each)
        for (int i = 0; i < 6; i++) {
            frames.add(new SpriteMappingFrame(List.of(
                    new SpriteMappingPiece(-0x20, -0x60, 4, 2, 0x00, false, false, 2, false),
                    new SpriteMappingPiece(    0, -0x60, 4, 2, 0x00, true,  false, 2, false),
                    new SpriteMappingPiece(-0x20, -0x50, 4, 1, 0x08, false, false, 1, false),
                    new SpriteMappingPiece(    0, -0x50, 4, 1, 0x0C, false, false, 1, false),
                    new SpriteMappingPiece(-0x20, -0x48, 4, 4, 0x10, false, false, 2, false),
                    new SpriteMappingPiece(    0, -0x48, 4, 4, 0x10, true,  false, 2, false),
                    new SpriteMappingPiece(-0x20, -0x28, 4, 4, 0x20, false, false, 2, false),
                    new SpriteMappingPiece(    0, -0x28, 4, 4, 0x20, true,  false, 2, false),
                    new SpriteMappingPiece(-0x20,    -8, 4, 4, 0x30, false, false, 2, false),
                    new SpriteMappingPiece(    0,    -8, 4, 4, 0x30, true,  false, 2, false),
                    new SpriteMappingPiece(-0x20,  0x18, 4, 4, 0x40, false, false, 2, false),
                    new SpriteMappingPiece(    0,  0x18, 4, 4, 0x40, true,  false, 2, false),
                    new SpriteMappingPiece(-0x10,  0x38, 4, 4, 0x50, false, false, 2, false),
                    new SpriteMappingPiece(-0x10,  0x58, 4, 4, 0x50, false, false, 2, false)
            )));
        }

        // Frame 11: .controlpanel (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -8, 2, 1, 0x68, false, false, 0, false),
                new SpriteMappingPiece(-0x10,  0, 4, 1, 0x6A, false, false, 0, false)
        )));

        return frames;
    }

    /**
     * Plasma launcher mappings (Map_PLaunch) — 4 frames.
     * Used by the FZ plasma launcher turrets.
     */
    public static List<SpriteMappingFrame> createPLaunchMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .red (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x6E, false, false, 0, false)
        )));

        // Frame 1: .white (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x76, false, false, 0, false)
        )));

        // Frame 2: .sparking1 (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x72, false, false, 0, false)
        )));

        // Frame 3: .sparking2 (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x72, false, true,  0, false)
        )));

        return frames;
    }

    /**
     * Plasma energy ball mappings (Map_Plasma) — 11 frames.
     * Used by the FZ plasma projectiles.
     */
    public static List<SpriteMappingFrame> createPlasmaMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .fuzzy1 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x7A, false, false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 4, 2, 0x7A, true,  true,  0, false)
        )));

        // Frame 1: .fuzzy2 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x0C, 2, 3, 0x82, false, false, 0, false),
                new SpriteMappingPiece(    4, -0x0C, 1, 3, 0x82, true,  true,  0, false)
        )));

        // Frame 2: .white1 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 1, 0x88, false, false, 0, false),
                new SpriteMappingPiece(-8,  0, 2, 1, 0x88, false, true,  0, false)
        )));

        // Frame 3: .white2 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 1, 0x8A, false, false, 0, false),
                new SpriteMappingPiece(-8,  0, 2, 1, 0x8A, false, true,  0, false)
        )));

        // Frame 4: .white3 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 1, 0x8C, false, false, 0, false),
                new SpriteMappingPiece(-8,  0, 2, 1, 0x8C, false, true,  0, false)
        )));

        // Frame 5: .white4 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x0C, 2, 3, 0x8E, false, false, 0, false),
                new SpriteMappingPiece(    4, -0x0C, 1, 3, 0x8E, true,  true,  0, false)
        )));

        // Frame 6: .fuzzy3 (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x94, false, false, 0, false)
        )));

        // Frame 7: .fuzzy4 (1 piece)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0x98, false, false, 0, false)
        )));

        // Frame 8: .fuzzy5 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x10, -0x10, 4, 2, 0x7A, true,  false, 0, false),
                new SpriteMappingPiece(-0x10,     0, 4, 2, 0x7A, false, true,  0, false)
        )));

        // Frame 9: .fuzzy6 (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x0C, 2, 3, 0x82, false, true,  0, false),
                new SpriteMappingPiece(    4, -0x0C, 1, 3, 0x82, true,  false, 0, false)
        )));

        // Frame 10: .blank (0 pieces)
        frames.add(new SpriteMappingFrame(List.of()));

        return frames;
    }

    /**
     * FZ escape ship legs mappings (Map_FZLegs) — 3 frames.
     * Used by the landing/takeoff legs of the Final Zone escape ship.
     */
    public static List<SpriteMappingFrame> createFZLegsMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .extended (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C,  0x14, 4, 3, 0x00, true, false, 1, false),
                new SpriteMappingPiece(-0x14,  0x24, 1, 1, 0x0C, true, false, 1, false)
        )));

        // Frame 1: .halfway (3 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece( 0x0C,  0x0C, 2, 2, 0x0D, true, false, 1, false),
                new SpriteMappingPiece( 0x0C,  0x1C, 1, 1, 0x11, true, false, 1, false),
                new SpriteMappingPiece(-0x14,  0x14, 4, 2, 0x12, true, false, 1, false)
        )));

        // Frame 2: .retracted (2 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece( 0x0C,  0x0C, 1, 2, 0x1A, true, false, 1, false),
                new SpriteMappingPiece(-0x14,  0x14, 4, 1, 0x1C, true, false, 1, false)
        )));

        return frames;
    }

    /**
     * FZ damaged overlay mappings (Map_FZDamaged) — 2 frames.
     * Used for damage overlay on the Final Zone boss.
     */
    public static List<SpriteMappingFrame> createFZDamagedMappings() {
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: .damage1 (6 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 3, 1, 0x20, false, false, 0, false),
                new SpriteMappingPiece(-0x1C, -0x14, 4, 2, 0x23, false, false, 0, false),
                new SpriteMappingPiece(    4, -0x14, 3, 2, 0x2B, false, false, 0, false),
                new SpriteMappingPiece(-0x1C,    -4, 2, 2, 0x3A, false, false, 1, false),
                new SpriteMappingPiece(    4,    -4, 4, 3, 0x3E, false, false, 1, false),
                new SpriteMappingPiece(    4,  0x14, 2, 1, 0x4A, false, false, 1, false)
        )));

        // Frame 1: .damage2 (6 pieces)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 3, 3, 0x31, false, false, 0, false),
                new SpriteMappingPiece(-0x1C, -0x14, 2, 2, 0x23, false, false, 0, false),
                new SpriteMappingPiece(    4, -0x14, 3, 2, 0x2B, false, false, 0, false),
                new SpriteMappingPiece(-0x1C,    -4, 2, 2, 0x3A, false, false, 1, false),
                new SpriteMappingPiece(    4,    -4, 4, 3, 0x3E, false, false, 1, false),
                new SpriteMappingPiece(    4,  0x14, 2, 1, 0x4A, false, false, 1, false)
        )));

        return frames;
    }
}
