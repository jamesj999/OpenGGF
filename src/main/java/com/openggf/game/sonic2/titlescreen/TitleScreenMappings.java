package com.openggf.game.sonic2.titlescreen;

import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprite mapping definitions for the Sonic 2 title screen (Obj0E).
 *
 * <p>Converted from {@code docs/s2disasm/mappings/sprite/obj0E.asm}.
 * The {@code spritePiece} macro format is:
 * <pre>
 * spritePiece xOff, yOff, width, height, startTile, xFlip, yFlip, palette, priority
 * </pre>
 *
 * <p>Frame index assignments (from the disassembly):
 * <ul>
 *   <li>0-4: Tails animation frames</li>
 *   <li>5-8: Sonic emerging animation</li>
 *   <li>9: Sonic's hand</li>
 *   <li>10 ($A): Logo top with TM (non-Japanese)</li>
 *   <li>11 ($B): Logo top with TM (Japanese)</li>
 *   <li>12 ($C): Small star</li>
 *   <li>13 ($D): Medium star</li>
 *   <li>14 ($E): Large star</li>
 *   <li>15 ($F): Falling star variant</li>
 *   <li>16 ($10): Small TM piece</li>
 *   <li>17 ($11): Masking sprite (uses background art, not sprite art)</li>
 *   <li>18 ($12): Sonic final static pose</li>
 *   <li>19 ($13): Tails' hand</li>
 * </ul>
 */
public final class TitleScreenMappings {

    private TitleScreenMappings() {}

    /**
     * Creates all 20 sprite mapping frames for the title screen.
     *
     * @return list of frames indexed 0-19
     */
    public static List<SpriteMappingFrame> createFrames() {
        List<SpriteMappingFrame> frames = new ArrayList<>(20);

        // Frame 0 (Map_obj0E_0028) - Tails frame 1
        frames.add(frame(
                piece(0x30, 0x10, 4, 1, 0x00, false, false, 1, true),
                piece(0x18, 0x18, 4, 4, 0x04, false, false, 1, true),
                piece(0x38, 0x18, 3, 4, 0x14, false, false, 1, true),
                piece(0x20, 0x38, 2, 1, 0x20, false, false, 1, true),
                piece(0x28, 0x40, 1, 1, 0x22, false, false, 1, true),
                piece(0x30, 0x38, 4, 3, 0x23, false, false, 1, true)
        ));

        // Frame 1 (Map_obj0E_005A) - Tails frame 2
        frames.add(frame(
                piece(0x48, 0x10, 2, 1, 0x2F, false, false, 1, true),
                piece(0x20, 0x18, 4, 4, 0x31, false, false, 1, true),
                piece(0x40, 0x18, 3, 4, 0x41, false, false, 1, true),
                piece(0x58, 0x18, 1, 2, 0x4D, false, false, 1, true),
                piece(0x00, 0x38, 1, 2, 0x4F, false, false, 1, true),
                piece(0x08, 0x38, 4, 3, 0x51, false, false, 1, true),
                piece(0x28, 0x38, 3, 3, 0x5D, false, false, 1, true),
                piece(0x40, 0x38, 2, 2, 0x66, false, false, 1, true),
                piece(0x50, 0x38, 1, 1, 0x6A, false, false, 1, true)
        ));

        // Frame 2 (Map_obj0E_00A4) - Tails frame 3
        frames.add(frame(
                piece(0x28, 0x10, 3, 3, 0x6B, false, false, 1, true),
                piece(0x40, 0x18, 4, 2, 0x74, false, false, 1, true),
                piece(0x08, 0x28, 4, 4, 0x7C, false, false, 1, true),
                piece(0x28, 0x28, 4, 4, 0x8C, false, false, 1, true),
                piece(0x48, 0x28, 2, 4, 0x9C, false, false, 1, true),
                piece(0x58, 0x28, 1, 2, 0xA4, false, false, 1, true),
                piece(0x10, 0x48, 4, 1, 0xA6, false, false, 1, true),
                piece(0x30, 0x48, 2, 1, 0xAA, false, false, 1, true)
        ));

        // Frame 3 (Map_obj0E_00E6) - Tails frame 4
        frames.add(frame(
                piece(0x20, 0x10, 1, 1, 0xAC, false, false, 1, true),
                piece(0x28, 0x10, 4, 3, 0xAD, false, false, 1, true),
                piece(0x48, 0x10, 1, 1, 0xB9, false, false, 1, true),
                piece(0x48, 0x18, 3, 2, 0xBA, false, false, 1, true),
                piece(0x10, 0x20, 2, 1, 0xC0, false, false, 1, true),
                piece(0x00, 0x38, 1, 1, 0xC2, false, false, 1, true),
                piece(0x08, 0x28, 4, 3, 0xC3, false, false, 1, true),
                piece(0x28, 0x28, 4, 3, 0xCF, false, false, 1, true),
                piece(0x48, 0x28, 2, 3, 0xDB, false, false, 1, true),
                piece(0x58, 0x28, 1, 1, 0xE1, false, false, 1, true),
                piece(0x10, 0x40, 4, 2, 0xE2, false, false, 1, true),
                piece(0x30, 0x40, 2, 2, 0xEA, false, false, 1, true),
                piece(0x40, 0x40, 3, 1, 0xEE, false, false, 1, true)
        ));

        // Frame 4 (Map_obj0E_0150) - Tails frame 5
        frames.add(frame(
                piece(0x40, 0x08, 2, 1, 0xF1, false, false, 1, true),
                piece(0x18, 0x10, 1, 1, 0xAC, false, true, 1, true),
                piece(0x20, 0x10, 4, 3, 0xF3, false, false, 1, true),
                piece(0x40, 0x10, 4, 2, 0xFF, false, false, 1, true),
                piece(0x58, 0x20, 1, 1, 0x107, false, false, 1, true),
                piece(0x40, 0x20, 2, 1, 0x108, false, false, 1, true),
                piece(0x08, 0x28, 1, 1, 0x10A, false, false, 1, true),
                piece(0x10, 0x28, 4, 4, 0x10B, false, false, 1, true),
                piece(0x00, 0x38, 2, 2, 0x11B, false, false, 1, true),
                piece(0x30, 0x28, 4, 4, 0x11F, false, false, 1, true),
                piece(0x50, 0x28, 1, 2, 0x12F, false, false, 1, true),
                piece(0x10, 0x48, 4, 1, 0x131, false, false, 1, true),
                piece(0x30, 0x48, 2, 1, 0x135, false, false, 1, true)
        ));

        // Frame 5 (Map_obj0E_01BA) - Sonic initial pose (mostly hidden behind emblem)
        frames.add(frame(
                piece(0x20, 0x08, 4, 1, 0x137, false, false, 0, true),
                piece(0x40, 0x10, 1, 1, 0x13B, false, false, 0, true),
                piece(0x08, 0x10, 4, 4, 0x13C, false, false, 0, true),
                piece(0x00, 0x30, 4, 2, 0x14C, false, false, 0, true),
                piece(0x00, 0x18, 1, 3, 0x154, false, false, 0, true),
                piece(0x28, 0x10, 3, 4, 0x157, false, false, 0, true),
                piece(0x40, 0x28, 1, 1, 0x163, false, false, 0, true),
                piece(0x48, 0x30, 1, 1, 0x164, false, false, 0, true),
                piece(0x20, 0x30, 4, 2, 0x165, false, false, 0, true),
                piece(0x08, 0x40, 1, 1, 0x16D, false, false, 0, true),
                piece(0x10, 0x40, 4, 2, 0x16E, false, false, 0, true),
                piece(0x30, 0x40, 2, 2, 0x176, false, false, 0, true),
                piece(0x40, 0x30, 1, 3, 0x17A, false, false, 0, true)
        ));

        // Frame 6 (Map_obj0E_0224) - Sonic emerging animation 1
        frames.add(frame(
                piece(0x18, 0x08, 4, 1, 0x17D, false, false, 0, true),
                piece(0x38, 0x08, 2, 4, 0x181, false, false, 0, true),
                piece(0x48, 0x10, 1, 1, 0x189, false, false, 0, true),
                piece(0x00, 0x20, 4, 4, 0x18A, false, false, 0, true),
                piece(0x08, 0x18, 1, 1, 0x19A, false, false, 0, true),
                piece(0x10, 0x10, 2, 2, 0x19B, false, false, 0, true),
                piece(0x20, 0x10, 3, 3, 0x19F, false, false, 0, true),
                piece(0x20, 0x28, 4, 4, 0x1A8, false, false, 0, true),
                piece(0x40, 0x28, 2, 4, 0x1B8, false, false, 0, true),
                piece(0x08, 0x40, 3, 1, 0x1C0, false, false, 0, true),
                piece(0x08, 0x50, 1, 2, 0x1C3, false, false, 0, true),
                piece(0x10, 0x48, 3, 3, 0x1C5, false, false, 0, true),
                piece(0x28, 0x48, 4, 2, 0x1CE, false, false, 0, true)
        ));

        // Frame 7 (Map_obj0E_028E) - Sonic emerging animation 2
        frames.add(frame(
                piece(0x28, 0x08, 4, 1, 0x1D6, false, false, 0, true),
                piece(0x48, 0x08, 1, 1, 0x1DA, false, false, 0, true),
                piece(0x10, 0x18, 1, 4, 0x1DB, false, false, 0, true),
                piece(0x18, 0x10, 4, 4, 0x1DF, false, false, 0, true),
                piece(0x38, 0x10, 4, 4, 0x1EF, false, false, 0, true),
                piece(0x58, 0x10, 1, 1, 0x1FF, false, false, 0, true),
                piece(0x58, 0x28, 1, 1, 0x200, false, false, 0, true),
                piece(0x10, 0x38, 1, 1, 0x201, false, false, 0, true),
                piece(0x18, 0x30, 4, 2, 0x202, false, false, 0, true),
                piece(0x38, 0x30, 4, 2, 0x20A, false, false, 0, true),
                piece(0x20, 0x40, 1, 1, 0x212, false, false, 0, true),
                piece(0x28, 0x40, 1, 2, 0x213, false, false, 0, true),
                piece(0x30, 0x40, 4, 3, 0x215, false, false, 0, true),
                piece(0x50, 0x40, 1, 3, 0x221, false, false, 0, true),
                piece(0x58, 0x38, 1, 2, 0x224, false, false, 0, true)
        ));

        // Frame 8 (Map_obj0E_0308) - Sonic prototype frame (same as $12 but missing right arm)
        frames.add(frame(
                piece(0x28, 0x08, 4, 4, 0x226, false, false, 0, true),
                piece(0x20, 0x10, 1, 1, 0x236, false, false, 0, true),
                piece(0x18, 0x18, 2, 4, 0x237, false, false, 0, true),
                piece(0x48, 0x08, 2, 4, 0x23F, false, false, 0, true),
                piece(0x58, 0x10, 1, 1, 0x247, false, false, 0, true),
                piece(0x18, 0x38, 2, 1, 0x248, false, false, 0, true),
                piece(0x28, 0x28, 4, 4, 0x24A, false, false, 0, true),
                piece(0x48, 0x28, 3, 2, 0x25A, false, false, 0, true),
                piece(0x48, 0x38, 2, 2, 0x260, false, false, 0, true),
                piece(0x58, 0x40, 1, 2, 0x264, false, false, 0, true),
                piece(0x28, 0x48, 2, 1, 0x266, false, false, 0, true),
                piece(0x30, 0x50, 1, 1, 0x268, false, false, 0, true),
                piece(0x38, 0x48, 4, 2, 0x269, false, false, 0, true)
        ));

        // Frame 9 (Map_obj0E_0372) - Sonic's hand
        frames.add(frame(
                piece(0x08, 0x00, 4, 2, 0x271, false, false, 0, true),
                piece(0x08, 0x10, 3, 2, 0x279, false, false, 0, true),
                piece(0x10, 0x20, 2, 2, 0x27F, false, false, 0, true)
        ));

        // Frame 10 ($A) (Map_obj0E_038C) - Logo top with TM (non-Japanese)
        frames.add(frame(
                piece(-0x50, 0x00, 4, 1, 0x283, false, false, 3, true),
                piece(-0x30, 0x00, 3, 1, 0x287, false, false, 3, true),
                piece(0x18, 0x00, 3, 1, 0x28A, false, false, 3, true),
                piece(0x30, 0x00, 4, 1, 0x28D, false, false, 3, true),
                piece(-0x18, 0x00, 4, 1, 0x10, false, false, 0, false),
                piece(0x08, 0x00, 2, 1, 0x10, false, false, 0, false)
        ));

        // Frame 11 ($B) (Map_obj0E_03BE) - Logo top with TM (Japanese)
        frames.add(frame(
                piece(-0x50, 0x00, 4, 1, 0x283, false, false, 3, true),
                piece(-0x30, 0x00, 3, 1, 0x287, false, false, 3, true),
                piece(0x18, 0x00, 3, 1, 0x28A, false, false, 3, true),
                piece(0x30, 0x00, 4, 1, 0x28D, false, false, 3, true),
                piece(-0x18, 0x00, 4, 1, 0x10, false, false, 0, false),
                piece(0x08, 0x00, 2, 1, 0x10, false, false, 0, false),
                piece(0x58, 0x08, 2, 1, 0x2A0, false, false, 3, true)
        ));

        // Frame 12 ($C) (Map_obj0E_03F8) - Small star
        frames.add(frame(
                piece(-4, -4, 1, 1, 0x291, false, false, 1, false)
        ));

        // Frame 13 ($D) (Map_obj0E_0402) - Medium star
        frames.add(frame(
                piece(-8, -8, 2, 2, 0x292, false, false, 1, false)
        ));

        // Frame 14 ($E) (Map_obj0E_040C) - Large star
        frames.add(frame(
                piece(-0x0C, -0x0C, 3, 3, 0x296, false, false, 1, false)
        ));

        // Frame 15 ($F) (Map_obj0E_0416) - Falling star variant
        frames.add(frame(
                piece(-4, -4, 1, 1, 0x29F, false, false, 1, false)
        ));

        // Frame 16 ($10) (Map_obj0E_0420) - Small TM piece
        frames.add(frame(
                piece(-8, -4, 2, 1, 0x2A0, false, false, 0, true)
        ));

        // Frame 17 ($11) (Map_obj0E_042A) - Masking sprite (VDP-specific, blank tiles)
        frames.add(frame(
                piece(0x08, 0x00, 1, 4, 0, false, false, 0, false),
                piece(0x00, 0x00, 1, 4, 0, false, false, 0, false),
                piece(0x08, 0x20, 1, 4, 0, false, false, 0, false),
                piece(0x00, 0x20, 1, 4, 0, false, false, 0, false)
        ));

        // Frame 18 ($12) (Map_obj0E_044C) - Sonic final static pose (with right arm)
        frames.add(frame(
                piece(0x28, 0x08, 4, 4, 0x226, false, false, 0, true),
                piece(0x20, 0x10, 1, 1, 0x236, false, false, 0, true),
                piece(0x18, 0x18, 2, 4, 0x237, false, false, 0, true),
                piece(0x48, 0x08, 2, 4, 0x23F, false, false, 0, true),
                piece(0x58, 0x10, 1, 1, 0x247, false, false, 0, true),
                piece(0x18, 0x38, 2, 1, 0x248, false, false, 0, true),
                piece(0x28, 0x28, 4, 4, 0x24A, false, false, 0, true),
                piece(0x48, 0x28, 3, 2, 0x25A, false, false, 0, true),
                piece(0x48, 0x38, 2, 2, 0x260, false, false, 0, true),
                piece(0x58, 0x40, 1, 2, 0x264, false, false, 0, true),
                piece(0x28, 0x48, 2, 1, 0x266, false, false, 0, true),
                piece(0x30, 0x50, 1, 1, 0x268, false, false, 0, true),
                piece(0x38, 0x48, 4, 2, 0x269, false, false, 0, true),
                piece(0x20, 0x48, 2, 1, 0x2A2, false, false, 0, true)
        ));

        // Frame 19 ($13) (Map_obj0E_04BE) - Tails' hand
        frames.add(frame(
                piece(0x08, 0x00, 2, 3, 0x2A4, false, false, 1, true)
        ));

        return frames;
    }

    private static SpriteMappingFrame frame(SpriteMappingPiece... pieces) {
        return new SpriteMappingFrame(List.of(pieces));
    }

    private static SpriteMappingPiece piece(int xOff, int yOff, int w, int h,
                                            int tile, boolean hFlip, boolean vFlip,
                                            int palette, boolean priority) {
        return new SpriteMappingPiece(xOff, yOff, w, h, tile, hFlip, vFlip, palette, priority);
    }
}
