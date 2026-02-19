package uk.co.jamesj999.sonic.game.sonic1.credits;

import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprite mapping definitions for Sonic 1 ending credits text.
 * <p>
 * Transcribed from {@code docs/s1disasm/_maps/Credits.asm} (Map_Cred).
 * Each frame renders one credit screen. Tile indices reference the
 * credit text font (Nemesis art at {@code ART_NEM_CREDIT_TEXT_ADDR}).
 * <p>
 * The {@code spritePiece} macro format is:
 * <pre>
 * spritePiece xOff, yOff, width, height, startTile, xFlip, yFlip, palette, priority
 * </pre>
 * <p>
 * Frame assignments:
 * <ul>
 *   <li>0: "SONIC TEAM STAFF"</li>
 *   <li>1: "GAME PLAN / CAROL YAS"</li>
 *   <li>2: "PROGRAM / YU 2"</li>
 *   <li>3: "CHARACTER DESIGN / BIGISLAND"</li>
 *   <li>4: "DESIGN / JINYA PHENIX RIE"</li>
 *   <li>5: "SOUND PRODUCE / MASATO NAKAMURA"</li>
 *   <li>6: "SOUND PROGRAM / JIMITA MACKY"</li>
 *   <li>7: "SPECIAL THANKS / FUJIO MINEGISHI PAPA"</li>
 *   <li>8: "PRESENTED BY SEGA"</li>
 * </ul>
 */
public final class Sonic1CreditsMappings {

    private Sonic1CreditsMappings() {}

    public static final int FRAME_STAFF          = 0;
    public static final int FRAME_GAME_PLAN      = 1;
    public static final int FRAME_PROGRAM        = 2;
    public static final int FRAME_CHARACTER       = 3;
    public static final int FRAME_DESIGN         = 4;
    public static final int FRAME_SOUND_PRODUCE  = 5;
    public static final int FRAME_SOUND_PROGRAM  = 6;
    public static final int FRAME_THANKS         = 7;
    public static final int FRAME_PRESENTED_BY   = 8;

    /**
     * Creates all 9 credit text mapping frames.
     *
     * @return list of frames indexed 0-8
     */
    public static List<SpriteMappingFrame> createFrames() {
        List<SpriteMappingFrame> frames = new ArrayList<>(9);

        // Frame 0: .staff — "SONIC TEAM STAFF"
        frames.add(frame(
                piece(-0x78, -8, 2, 2, 0x2E, false, false, 0, false), // S
                piece(-0x68, -8, 2, 2, 0x26, false, false, 0, false), // O
                piece(-0x58, -8, 2, 2, 0x1A, false, false, 0, false), // N
                piece(-0x48, -8, 1, 2, 0x46, false, false, 0, false), // I
                piece(-0x40, -8, 2, 2, 0x1E, false, false, 0, false), // C
                piece(-0x28, -8, 2, 2, 0x3E, false, false, 0, false), // T
                piece(-0x18, -8, 2, 2, 0x0E, false, false, 0, false), // E
                piece(  -8, -8, 2, 2, 0x04, false, false, 0, false), // A
                piece(   8, -8, 3, 2, 0x08, false, false, 0, false), // M
                piece(0x28, -8, 2, 2, 0x2E, false, false, 0, false), // S
                piece(0x38, -8, 2, 2, 0x3E, false, false, 0, false), // T
                piece(0x48, -8, 2, 2, 0x04, false, false, 0, false), // A
                piece(0x58, -8, 2, 2, 0x5C, false, false, 0, false), // F
                piece(0x68, -8, 2, 2, 0x5C, false, false, 0, false)  // F
        ));

        // Frame 1: .gameplan — "GAME PLAN" / "CAROL YAS"
        frames.add(frame(
                piece(-0x80, -0x28, 2, 2, 0x00, false, false, 0, false), // G
                piece(-0x70, -0x28, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x60, -0x28, 3, 2, 0x08, false, false, 0, false), // M
                piece(-0x4C, -0x28, 2, 2, 0x0E, false, false, 0, false), // E
                piece(-0x30, -0x28, 2, 2, 0x12, false, false, 0, false), // P
                piece(-0x20, -0x28, 2, 2, 0x16, false, false, 0, false), // L
                piece(-0x10, -0x28, 2, 2, 0x04, false, false, 0, false), // A
                piece(    0, -0x28, 2, 2, 0x1A, false, false, 0, false), // N
                piece(-0x38,     8, 2, 2, 0x1E, false, false, 0, false), // C
                piece(-0x28,     8, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x18,     8, 2, 2, 0x22, false, false, 0, false), // R
                piece(   -8,     8, 2, 2, 0x26, false, false, 0, false), // O
                piece(    8,     8, 2, 2, 0x16, false, false, 0, false), // L
                piece( 0x20,     8, 2, 2, 0x2A, false, false, 0, false), // Y
                piece( 0x30,     8, 2, 2, 0x04, false, false, 0, false), // A
                piece( 0x44,     8, 2, 2, 0x2E, false, false, 0, false)  // S
        ));

        // Frame 2: .program — "PROGRAM" / "YU 2"
        frames.add(frame(
                piece(-0x80, -0x28, 2, 2, 0x12, false, false, 0, false), // P
                piece(-0x70, -0x28, 2, 2, 0x22, false, false, 0, false), // R
                piece(-0x60, -0x28, 2, 2, 0x26, false, false, 0, false), // O
                piece(-0x50, -0x28, 2, 2, 0x00, false, false, 0, false), // G
                piece(-0x40, -0x28, 2, 2, 0x22, false, false, 0, false), // R
                piece(-0x30, -0x28, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x20, -0x28, 3, 2, 0x08, false, false, 0, false), // M
                piece(-0x18,     8, 2, 2, 0x2A, false, false, 0, false), // Y
                piece(   -8,     8, 2, 2, 0x32, false, false, 0, false), // U
                piece(    8,     8, 2, 2, 0x36, false, false, 0, false)  // 2
        ));

        // Frame 3: .character — "CHARACTER DESIGN" / "BIGISLAND"
        frames.add(frame(
                piece(-0x78, -0x28, 2, 2, 0x1E, false, false, 0, false), // C
                piece(-0x68, -0x28, 2, 2, 0x3A, false, false, 0, false), // H
                piece(-0x58, -0x28, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x48, -0x28, 2, 2, 0x22, false, false, 0, false), // R
                piece(-0x38, -0x28, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x28, -0x28, 2, 2, 0x1E, false, false, 0, false), // C
                piece(-0x18, -0x28, 2, 2, 0x3E, false, false, 0, false), // T
                piece(   -8, -0x28, 2, 2, 0x0E, false, false, 0, false), // E
                piece(    8, -0x28, 2, 2, 0x22, false, false, 0, false), // R
                piece( 0x20, -0x28, 2, 2, 0x42, false, false, 0, false), // D
                piece( 0x30, -0x28, 2, 2, 0x0E, false, false, 0, false), // E
                piece( 0x40, -0x28, 2, 2, 0x2E, false, false, 0, false), // S
                piece( 0x50, -0x28, 1, 2, 0x46, false, false, 0, false), // I
                piece( 0x58, -0x28, 2, 2, 0x00, false, false, 0, false), // G
                piece( 0x68, -0x28, 2, 2, 0x1A, false, false, 0, false), // N
                piece(-0x40,     8, 2, 2, 0x48, false, false, 0, false), // B
                piece(-0x30,     8, 1, 2, 0x46, false, false, 0, false), // I
                piece(-0x28,     8, 2, 2, 0x00, false, false, 0, false), // G
                piece(-0x18,     8, 1, 2, 0x46, false, false, 0, false), // I
                piece(-0x10,     8, 2, 2, 0x2E, false, false, 0, false), // S
                piece(    0,     8, 2, 2, 0x16, false, false, 0, false), // L
                piece( 0x10,     8, 2, 2, 0x04, false, false, 0, false), // A
                piece( 0x20,     8, 2, 2, 0x1A, false, false, 0, false), // N
                piece( 0x30,     8, 2, 2, 0x42, false, false, 0, false)  // D
        ));

        // Frame 4: .design — "DESIGN" / "JINYA" / "PHENIX RIE"
        frames.add(frame(
                piece(-0x60, -0x30, 2, 2, 0x42, false, false, 0, false), // D
                piece(-0x50, -0x30, 2, 2, 0x0E, false, false, 0, false), // E
                piece(-0x40, -0x30, 2, 2, 0x2E, false, false, 0, false), // S
                piece(-0x30, -0x30, 1, 2, 0x46, false, false, 0, false), // I
                piece(-0x28, -0x30, 2, 2, 0x00, false, false, 0, false), // G
                piece(-0x18, -0x30, 2, 2, 0x1A, false, false, 0, false), // N
                piece(-0x18,     0, 2, 2, 0x4C, false, false, 0, false), // J
                piece(   -8,     0, 1, 2, 0x46, false, false, 0, false), // I
                piece(    4,     0, 2, 2, 0x1A, false, false, 0, false), // N
                piece( 0x14,     0, 2, 2, 0x2A, false, false, 0, false), // Y
                piece( 0x24,     0, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x30,  0x20, 2, 2, 0x12, false, false, 0, false), // P
                piece(-0x20,  0x20, 2, 2, 0x3A, false, false, 0, false), // H
                piece(-0x10,  0x20, 2, 2, 0x0E, false, false, 0, false), // E
                piece(    0,  0x20, 2, 2, 0x1A, false, false, 0, false), // N
                piece( 0x10,  0x20, 1, 2, 0x46, false, false, 0, false), // I
                piece( 0x18,  0x20, 2, 2, 0x50, false, false, 0, false), // X
                piece( 0x30,  0x20, 2, 2, 0x22, false, false, 0, false), // R
                piece( 0x40,  0x20, 1, 2, 0x46, false, false, 0, false), // I
                piece( 0x48,  0x20, 2, 2, 0x0E, false, false, 0, false)  // E
        ));

        // Frame 5: .soundproduce — "SOUND PRODUCE" / "MASATO NAKAMURA"
        frames.add(frame(
                piece(-0x68, -0x28, 2, 2, 0x2E, false, false, 0, false), // S
                piece(-0x58, -0x28, 2, 2, 0x26, false, false, 0, false), // O
                piece(-0x48, -0x28, 2, 2, 0x32, false, false, 0, false), // U
                piece(-0x38, -0x28, 2, 2, 0x1A, false, false, 0, false), // N
                piece(-0x28, -0x28, 2, 2, 0x54, false, false, 0, false), // D
                piece(   -8, -0x28, 2, 2, 0x12, false, false, 0, false), // P
                piece(    8, -0x28, 2, 2, 0x22, false, false, 0, false), // R
                piece( 0x18, -0x28, 2, 2, 0x26, false, false, 0, false), // O
                piece( 0x28, -0x28, 2, 2, 0x42, false, false, 0, false), // D
                piece( 0x38, -0x28, 2, 2, 0x32, false, false, 0, false), // U
                piece( 0x48, -0x28, 2, 2, 0x1E, false, false, 0, false), // C
                piece( 0x58, -0x28, 2, 2, 0x0E, false, false, 0, false), // E
                piece(-0x78,     8, 3, 2, 0x08, false, false, 0, false), // M
                piece(-0x64,     8, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x54,     8, 2, 2, 0x2E, false, false, 0, false), // S
                piece(-0x44,     8, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x34,     8, 2, 2, 0x3E, false, false, 0, false), // T
                piece(-0x24,     8, 2, 2, 0x26, false, false, 0, false), // O
                piece(   -8,     8, 2, 2, 0x1A, false, false, 0, false), // N
                piece(    8,     8, 2, 2, 0x04, false, false, 0, false), // A
                piece( 0x18,     8, 2, 2, 0x58, false, false, 0, false), // K
                piece( 0x28,     8, 2, 2, 0x04, false, false, 0, false), // A
                piece( 0x38,     8, 3, 2, 0x08, false, false, 0, false), // M
                piece( 0x4C,     8, 2, 2, 0x32, false, false, 0, false), // U
                piece( 0x5C,     8, 2, 2, 0x22, false, false, 0, false), // R
                piece( 0x6C,     8, 2, 2, 0x04, false, false, 0, false)  // A
        ));

        // Frame 6: .soundprogram — "SOUND PROGRAM" / "JIMITA" / "MACKY"
        frames.add(frame(
                piece(-0x68, -0x30, 2, 2, 0x2E, false, false, 0, false), // S
                piece(-0x58, -0x30, 2, 2, 0x26, false, false, 0, false), // O
                piece(-0x48, -0x30, 2, 2, 0x32, false, false, 0, false), // U
                piece(-0x38, -0x30, 2, 2, 0x1A, false, false, 0, false), // N
                piece(-0x28, -0x30, 2, 2, 0x54, false, false, 0, false), // D
                piece(   -8, -0x30, 2, 2, 0x12, false, false, 0, false), // P
                piece(    8, -0x30, 2, 2, 0x22, false, false, 0, false), // R
                piece( 0x18, -0x30, 2, 2, 0x26, false, false, 0, false), // O
                piece( 0x28, -0x30, 2, 2, 0x00, false, false, 0, false), // G
                piece( 0x38, -0x30, 2, 2, 0x22, false, false, 0, false), // R
                piece( 0x48, -0x30, 2, 2, 0x04, false, false, 0, false), // A
                piece( 0x58, -0x30, 3, 2, 0x08, false, false, 0, false), // M
                piece(-0x30,     0, 2, 2, 0x4C, false, false, 0, false), // J
                piece(-0x20,     0, 1, 2, 0x46, false, false, 0, false), // I
                piece(-0x18,     0, 3, 2, 0x08, false, false, 0, false), // M
                piece(   -4,     0, 1, 2, 0x46, false, false, 0, false), // I
                piece(    4,     0, 2, 2, 0x3E, false, false, 0, false), // T
                piece( 0x14,     0, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x30,  0x20, 3, 2, 0x08, false, false, 0, false), // M
                piece(-0x1C,  0x20, 2, 2, 0x04, false, false, 0, false), // A
                piece( -0xC,  0x20, 2, 2, 0x1E, false, false, 0, false), // C
                piece(    4,  0x20, 2, 2, 0x58, false, false, 0, false), // K
                piece( 0x14,  0x20, 2, 2, 0x2A, false, false, 0, false)  // Y
        ));

        // Frame 7: .thanks — "SPECIAL THANKS" / "FUJIO MINEGISHI" / "PAPA"
        frames.add(frame(
                piece(-0x80, -0x28, 2, 2, 0x2E, false, false, 0, false), // S
                piece(-0x70, -0x28, 2, 2, 0x12, false, false, 0, false), // P
                piece(-0x60, -0x28, 2, 2, 0x0E, false, false, 0, false), // E
                piece(-0x50, -0x28, 2, 2, 0x1E, false, false, 0, false), // C
                piece(-0x40, -0x28, 1, 2, 0x46, false, false, 0, false), // I
                piece(-0x38, -0x28, 2, 2, 0x04, false, false, 0, false), // A
                piece(-0x28, -0x28, 2, 2, 0x16, false, false, 0, false), // L
                piece(   -8, -0x28, 2, 2, 0x3E, false, false, 0, false), // T
                piece(    8, -0x28, 2, 2, 0x3A, false, false, 0, false), // H
                piece( 0x18, -0x28, 2, 2, 0x04, false, false, 0, false), // A
                piece( 0x28, -0x28, 2, 2, 0x1A, false, false, 0, false), // N
                piece( 0x38, -0x28, 2, 2, 0x58, false, false, 0, false), // K
                piece( 0x48, -0x28, 2, 2, 0x2E, false, false, 0, false), // S
                piece(-0x50,     0, 2, 2, 0x5C, false, false, 0, false), // F
                piece(-0x40,     0, 2, 2, 0x32, false, false, 0, false), // U
                piece(-0x30,     0, 2, 2, 0x4C, false, false, 0, false), // J
                piece(-0x20,     0, 1, 2, 0x46, false, false, 0, false), // I
                piece(-0x18,     0, 2, 2, 0x26, false, false, 0, false), // O
                piece(    0,     0, 3, 2, 0x08, false, false, 0, false), // M
                piece( 0x14,     0, 1, 2, 0x46, false, false, 0, false), // I
                piece( 0x1C,     0, 2, 2, 0x1A, false, false, 0, false), // N
                piece( 0x2C,     0, 2, 2, 0x0E, false, false, 0, false), // E
                piece( 0x3C,     0, 2, 2, 0x00, false, false, 0, false), // G
                piece( 0x4C,     0, 1, 2, 0x46, false, false, 0, false), // I
                piece( 0x54,     0, 2, 2, 0x2E, false, false, 0, false), // S
                piece( 0x64,     0, 2, 2, 0x3A, false, false, 0, false), // H
                piece( 0x74,     0, 1, 2, 0x46, false, false, 0, false), // I
                piece(   -8,  0x20, 2, 2, 0x12, false, false, 0, false), // P
                piece(    8,  0x20, 2, 2, 0x04, false, false, 0, false), // A
                piece( 0x18,  0x20, 2, 2, 0x12, false, false, 0, false), // P
                piece( 0x28,  0x20, 2, 2, 0x04, false, false, 0, false)  // A
        ));

        // Frame 8: .presentedby — "PRESENTED BY SEGA"
        frames.add(frame(
                piece(-0x80, -8, 2, 2, 0x12, false, false, 0, false), // P
                piece(-0x70, -8, 2, 2, 0x22, false, false, 0, false), // R
                piece(-0x60, -8, 2, 2, 0x0E, false, false, 0, false), // E
                piece(-0x50, -8, 2, 2, 0x2E, false, false, 0, false), // S
                piece(-0x40, -8, 2, 2, 0x0E, false, false, 0, false), // E
                piece(-0x30, -8, 2, 2, 0x1A, false, false, 0, false), // N
                piece(-0x20, -8, 2, 2, 0x3E, false, false, 0, false), // T
                piece(-0x10, -8, 2, 2, 0x0E, false, false, 0, false), // E
                piece(    0, -8, 2, 2, 0x42, false, false, 0, false), // D
                piece( 0x18, -8, 2, 2, 0x48, false, false, 0, false), // B
                piece( 0x28, -8, 2, 2, 0x2A, false, false, 0, false), // Y
                piece( 0x40, -8, 2, 2, 0x2E, false, false, 0, false), // S
                piece( 0x50, -8, 2, 2, 0x0E, false, false, 0, false), // E
                piece( 0x60, -8, 2, 2, 0x00, false, false, 0, false), // G
                piece( 0x70, -8, 2, 2, 0x04, false, false, 0, false)  // A
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
