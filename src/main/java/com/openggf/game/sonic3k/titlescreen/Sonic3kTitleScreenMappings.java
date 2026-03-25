package com.openggf.game.sonic3k.titlescreen;

import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprite mapping definitions for the Sonic 3&amp;K title screen.
 *
 * <p>Converted from S3K disassembly mapping files:
 * <ul>
 *   <li>{@code Map - S3 Banner.asm} (banner and TM symbol)</li>
 *   <li>{@code Map - S3 ANDKnuckles.asm} ("&amp; KNUCKLES" text)</li>
 *   <li>{@code Map - S3 Screen Text.asm} (menu selection and copyright)</li>
 *   <li>{@code Map - S3 Sonic Anim.asm} (Sonic finger wag and wink)</li>
 *   <li>{@code Map - S3 Tails Plane.asm} (Tails flying the Tornado)</li>
 * </ul>
 *
 * <h3>VRAM tile bases (combined sprite pattern array starts at $400)</h3>
 * <ul>
 *   <li>Sonic sprites: VRAM $400, array offset $000</li>
 *   <li>&amp;Knuckles: VRAM $4C0, array offset $0C0</li>
 *   <li>Banner: VRAM $500, array offset $100</li>
 *   <li>Menu text: VRAM $680, array offset $280</li>
 * </ul>
 *
 * <p>Each piece's {@code startTile} is pre-computed as
 * {@code (objectVramBase - $400) + mappingPieceTileIndex}, and the final
 * palette/priority combine the object's {@code art_tile} with the mapping
 * piece attributes (VDP addition semantics).
 */
public final class Sonic3kTitleScreenMappings {

    /** Tile offset for Sonic anim / Tails plane sprites (VRAM $400 - $400 = $000). */
    private static final int BASE_SONIC = 0x000;

    /** Tile offset for &amp;Knuckles sprites (VRAM $4C0 - $400 = $0C0). */
    private static final int BASE_AND_KNUCKLES = 0x0C0;

    /** Tile offset for banner sprites (VRAM $500 - $400 = $100). */
    private static final int BASE_BANNER = 0x100;

    /** Tile offset for screen text sprites (VRAM $680 - $400 = $280). */
    private static final int BASE_TEXT = 0x280;

    private Sonic3kTitleScreenMappings() {}

    // -----------------------------------------------------------------------
    // Banner (Obj_TitleBanner) — palette 3, priority
    // Source: Map - S3 Banner.asm
    // -----------------------------------------------------------------------

    /**
     * Creates banner mapping frames.
     *
     * <p>Frame 0: main "SONIC THE HEDGEHOG 3" banner (21 pieces).<br>
     * Frame 1: TM symbol (1 piece).
     *
     * @return list of 2 frames
     */
    public static List<SpriteMappingFrame> createBannerFrames() {
        List<SpriteMappingFrame> frames = new ArrayList<>(2);
        int b = BASE_BANNER;

        // Frame 0 — full banner (21 pieces)
        // Top row (y=$10 = 16): six 4x4 blocks plus one 2x4 hflip
        // Middle row (y=$30 = 48): large banner body
        // Bottom row (y=$50 = 80): small edge pieces
        frames.add(frame(
                // Top row
                piece(-104, 16, 4, 4, b + 0x00, false, false, 3, true),
                piece(-72,  16, 4, 4, b + 0x10, false, false, 3, true),
                piece(-40,  16, 4, 4, b + 0x20, false, false, 3, true),
                piece(-8,   16, 4, 4, b + 0x30, false, false, 3, true),
                piece(24,   16, 4, 4, b + 0x40, false, false, 3, true),
                piece(56,   16, 4, 4, b + 0x50, false, false, 3, true),
                piece(88,   16, 2, 4, b + 0x00, true,  false, 3, true),  // hflip mirror of tile 0

                // Middle row
                piece(-128, 48, 4, 4, b + 0x60, false, false, 3, true),
                piece(-96,  48, 2, 4, b + 0x70, false, false, 3, true),
                piece(-80,  48, 3, 2, b + 0x78, false, false, 3, true),
                piece(-56,  48, 4, 4, b + 0x7E, false, false, 3, true),
                piece(-24,  48, 4, 4, b + 0x8E, false, false, 3, true),
                piece(8,    48, 4, 4, b + 0x9E, false, false, 3, true),
                piece(40,   48, 2, 4, b + 0xAE, false, false, 3, true),
                piece(56,   48, 3, 2, b + 0xB6, false, false, 3, true),
                piece(80,   48, 4, 4, b + 0xBC, false, false, 3, true),
                piece(112,  48, 2, 4, b + 0x60, true,  false, 3, true),  // hflip mirror of tile $60

                // Bottom row
                piece(-128, 80, 2, 2, b + 0xCC, false, false, 3, true),
                piece(-112, 80, 3, 1, b + 0xD0, false, false, 3, true),
                piece(88,   80, 3, 1, b + 0xD0, true,  false, 3, true),  // hflip mirror of $D0
                piece(112,  80, 2, 2, b + 0xCC, true,  false, 3, true)   // hflip mirror of $CC
        ));

        // Frame 1 — TM symbol (1 piece)
        frames.add(frame(
                piece(-12, -4, 3, 1, b + 0xD3, false, false, 3, true)
        ));

        return frames;
    }

    // -----------------------------------------------------------------------
    // AND Knuckles (Obj_TitleANDKnuckles) — palette 3, priority
    // Source: Map - S3 ANDKnuckles.asm
    // -----------------------------------------------------------------------

    /**
     * Creates "&amp; KNUCKLES" mapping frames.
     *
     * <p>Frame 0: full text (6 pieces).
     *
     * @return list of 1 frame
     */
    public static List<SpriteMappingFrame> createAndKnucklesFrames() {
        List<SpriteMappingFrame> frames = new ArrayList<>(1);
        int b = BASE_AND_KNUCKLES;

        // Frame 0 — "& KNUCKLES" (6 pieces, each 4x3 except last 1x3)
        frames.add(frame(
                piece(-84, -12, 4, 3, b + 0x00, false, false, 3, true),
                piece(-52, -12, 4, 3, b + 0x0C, false, false, 3, true),
                piece(-20, -12, 4, 3, b + 0x18, false, false, 3, true),
                piece(12,  -12, 4, 3, b + 0x24, false, false, 3, true),
                piece(44,  -12, 4, 3, b + 0x30, false, false, 3, true),
                piece(76,  -12, 1, 3, b + 0x3C, false, false, 3, true)
        ));

        return frames;
    }

    // -----------------------------------------------------------------------
    // Selection (Obj_TitleSelection) — object palette 2, priority
    // Highlighted rows use mapping palette 1 -> final palette 3
    // Non-highlighted rows use mapping palette 0 -> final palette 2
    // Source: Map - S3 Screen Text.asm
    // -----------------------------------------------------------------------

    /**
     * Creates menu selection mapping frames.
     *
     * <p>Each frame represents a different menu highlight state:
     * <ul>
     *   <li>Frame 0: "1 PLAYER" highlighted (top row pal 3, bottom row pal 2)</li>
     *   <li>Frame 1: "COMPETITION" highlighted (top row pal 2, bottom row pal 3)</li>
     * </ul>
     *
     * <p>The "1" prefix piece on the left uses the same palette as the
     * highlighted row.
     *
     * @return list of frames
     */
    public static List<SpriteMappingFrame> createSelectionFrames() {
        List<SpriteMappingFrame> frames = new ArrayList<>(2);
        int b = BASE_TEXT;

        // Frame 0 — "1 PLAYER" highlighted
        // Top row (y=0): highlighted (palette 3)
        // Bottom row (y=$10): non-highlighted (palette 2)
        // "1" prefix piece (y=0, x=-20): highlighted (palette 3)
        frames.add(frame(
                piece(0,   0,  4, 1, b + 0x00, false, false, 3, true),
                piece(32,  0,  4, 1, b + 0x04, false, false, 3, true),
                piece(64,  0,  4, 1, b + 0x08, false, false, 3, true),
                piece(0,   16, 4, 1, b + 0x0C, false, false, 2, true),
                piece(32,  16, 4, 1, b + 0x10, false, false, 2, true),
                piece(64,  16, 4, 1, b + 0x14, false, false, 2, true),
                piece(-20, 0,  2, 1, b + 0x24, false, false, 3, true)
        ));

        // Frame 1 — "COMPETITION" highlighted
        // Top row (y=0): non-highlighted (palette 2)
        // Bottom row (y=$10): highlighted (palette 3)
        // Indicator piece moves to y=$10 with highlighted palette (palette 3)
        // (from disasm: dc.b $10, 4, $20, $24, $FF, $EC — y=$10, pal 1 → combined pal 3)
        frames.add(frame(
                piece(0,   0,  4, 1, b + 0x00, false, false, 2, true),
                piece(32,  0,  4, 1, b + 0x04, false, false, 2, true),
                piece(64,  0,  4, 1, b + 0x08, false, false, 2, true),
                piece(0,   16, 4, 1, b + 0x0C, false, false, 3, true),
                piece(32,  16, 4, 1, b + 0x10, false, false, 3, true),
                piece(64,  16, 4, 1, b + 0x14, false, false, 3, true),
                piece(-20, 16, 2, 1, b + 0x24, false, false, 3, true)
        ));

        return frames;
    }

    // -----------------------------------------------------------------------
    // Copyright (Obj_TitleCopyright) — object palette 3, priority
    // All mapping pieces have palette 0 -> final palette 3
    // Source: Map - S3 Screen Text.asm (frame 3)
    // -----------------------------------------------------------------------

    /**
     * Creates the copyright text mapping frame.
     *
     * <p>Corresponds to frame 3 of {@code Map_TitleScreenText_} in the
     * disassembly. Object uses {@code make_art_tile(ArtTile_Title_Menu, 3, 1)}.
     *
     * @return list of 1 frame
     */
    public static List<SpriteMappingFrame> createCopyrightFrame() {
        List<SpriteMappingFrame> frames = new ArrayList<>(1);
        int b = BASE_TEXT;

        // Frame 0 (disasm frame 3) — copyright text (3 pieces)
        frames.add(frame(
                piece(0,  0,  1, 1, b + 0x26, false, false, 3, true),
                piece(16, 0,  4, 1, b + 0x27, false, false, 3, true),
                piece(56, 0,  4, 1, b + 0x2B, false, false, 3, true)
        ));

        return frames;
    }

    // -----------------------------------------------------------------------
    // Sonic Anim (Obj_TitleSonicFinger / Obj_TitleSonicWink) — palette 1, priority
    // Source: Map - S3 Sonic Anim.asm
    // -----------------------------------------------------------------------

    /**
     * Creates Sonic animation mapping frames (finger wag / wink).
     *
     * <p>Frame index assignments:
     * <ul>
     *   <li>0-1: Sonic finger wag animation</li>
     *   <li>2-3: Sonic wink animation</li>
     *   <li>4: Empty frame (animation end marker)</li>
     * </ul>
     *
     * @return list of 5 frames
     */
    public static List<SpriteMappingFrame> createSonicAnimFrames() {
        List<SpriteMappingFrame> frames = new ArrayList<>(5);
        int b = BASE_SONIC;

        // Frame 0 — Sonic finger wag 1 (4 pieces: two 3x4 top, two 3x3 bottom)
        frames.add(frame(
                piece(-24, -28, 3, 4, b + 0x00, false, false, 1, true),
                piece(0,   -28, 3, 4, b + 0x0C, false, false, 1, true),
                piece(-24, 4,   3, 3, b + 0x18, false, false, 1, true),
                piece(0,   4,   3, 3, b + 0x21, false, false, 1, true)
        ));

        // Frame 1 — Sonic finger wag 2 (4 pieces: two 3x4 top, two 3x3 bottom)
        frames.add(frame(
                piece(-24, -28, 3, 4, b + 0x2A, false, false, 1, true),
                piece(0,   -28, 3, 4, b + 0x36, false, false, 1, true),
                piece(-24, 4,   3, 3, b + 0x42, false, false, 1, true),
                piece(0,   4,   3, 3, b + 0x4B, false, false, 1, true)
        ));

        // Frame 2 — Sonic wink 1 (2 pieces: one 4x4, one 4x2)
        frames.add(frame(
                piece(-16, -24, 4, 4, b + 0x54, false, false, 1, true),
                piece(-16, 8,   4, 2, b + 0x64, false, false, 1, true)
        ));

        // Frame 3 — Sonic wink 2 (2 pieces: one 4x4, one 4x2)
        frames.add(frame(
                piece(-16, -24, 4, 4, b + 0x6C, false, false, 1, true),
                piece(-16, 8,   4, 2, b + 0x7C, false, false, 1, true)
        ));

        // Frame 4 — Empty (animation end marker)
        frames.add(frame());

        return frames;
    }

    // -----------------------------------------------------------------------
    // Tails Plane (Obj_TitleTailsPlane) — palette 3, NO priority
    // Source: Map - S3 Tails Plane.asm
    // -----------------------------------------------------------------------

    /**
     * Creates Tails plane (Tornado) mapping frames.
     *
     * <p>6 frames of propeller animation, each with two pieces
     * (4x3 fuselage + 2x3 propeller). The fuselage alternates between
     * two tile sets and the propeller cycles through three positions.
     *
     * @return list of 6 frames
     */
    public static List<SpriteMappingFrame> createTailsPlaneFrames() {
        List<SpriteMappingFrame> frames = new ArrayList<>(6);
        int b = BASE_SONIC;

        // Frame 0 (fuselage A, propeller 1)
        frames.add(frame(
                piece(-24, -12, 4, 3, b + 0x84, false, false, 3, false),
                piece(8,   -12, 2, 3, b + 0x9C, false, false, 3, false)
        ));

        // Frame 1 (fuselage B, propeller 2)
        frames.add(frame(
                piece(-24, -12, 4, 3, b + 0x90, false, false, 3, false),
                piece(8,   -12, 2, 3, b + 0xA2, false, false, 3, false)
        ));

        // Frame 2 (fuselage A, propeller 3)
        frames.add(frame(
                piece(-24, -12, 4, 3, b + 0x84, false, false, 3, false),
                piece(8,   -12, 2, 3, b + 0xA8, false, false, 3, false)
        ));

        // Frame 3 (fuselage B, propeller 1)
        frames.add(frame(
                piece(-24, -12, 4, 3, b + 0x90, false, false, 3, false),
                piece(8,   -12, 2, 3, b + 0x9C, false, false, 3, false)
        ));

        // Frame 4 (fuselage A, propeller 2)
        frames.add(frame(
                piece(-24, -12, 4, 3, b + 0x84, false, false, 3, false),
                piece(8,   -12, 2, 3, b + 0xA2, false, false, 3, false)
        ));

        // Frame 5 (fuselage B, propeller 3)
        frames.add(frame(
                piece(-24, -12, 4, 3, b + 0x90, false, false, 3, false),
                piece(8,   -12, 2, 3, b + 0xA8, false, false, 3, false)
        ));

        return frames;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static SpriteMappingFrame frame(SpriteMappingPiece... pieces) {
        return new SpriteMappingFrame(List.of(pieces));
    }

    private static SpriteMappingPiece piece(int xOff, int yOff, int w, int h,
                                            int tile, boolean hFlip, boolean vFlip,
                                            int palette, boolean priority) {
        return new SpriteMappingPiece(xOff, yOff, w, h, tile, hFlip, vFlip, palette, priority);
    }
}
