package uk.co.jamesj999.sonic.game.sonic1.objects.bosses;

import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprite mapping data for the GHZ boss.
 * Parsed from docs/s1disasm/_maps/Eggman.asm, _maps/GHZ Ball.asm, _maps/Boss Items.asm.
 *
 * spritePiece format: x, y, width, height, startTile, xflip, yflip, pal, pri
 */
public final class GHZBossMappings {

    private GHZBossMappings() {
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
     * Boss Items mappings (Map_BossItems_internal) — chain anchor frames.
     * Used by the wrecking ball base/chain component.
     * Only frames 0 and 1 are used by GHZ boss.
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

        return frames;
    }
}
