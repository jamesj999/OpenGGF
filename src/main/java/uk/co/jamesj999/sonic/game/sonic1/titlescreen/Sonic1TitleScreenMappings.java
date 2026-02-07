package uk.co.jamesj999.sonic.game.sonic1.titlescreen;

import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;

import java.util.ArrayList;
import java.util.List;

/**
 * Sprite mapping definitions for the Sonic 1 title screen.
 *
 * <p>Converted from the s1disasm mapping files:
 * <ul>
 *   <li>{@code _maps/Title Screen Sonic.asm} (Map_TSon) - 8 animation frames</li>
 *   <li>{@code _maps/Press Start and TM.asm} (Map_PSB) - PSB text, limiter, TM</li>
 *   <li>{@code _maps/Credits.asm} (Map_Cred .sonicteam) - "SONIC TEAM PRESENTS"</li>
 * </ul>
 *
 * <p>The {@code spritePiece} macro format is:
 * <pre>
 * spritePiece xOff, yOff, width, height, startTile, xFlip, yFlip, palette, priority
 * </pre>
 *
 * <p>Frame index assignments:
 * <ul>
 *   <li>0-7: TitleSonic animation frames (from Map_TSon)</li>
 *   <li>8: "PRESS START BUTTON" text (from Map_PSB frame 0/1)</li>
 *   <li>9: Blank frame (PSB invisible during flash)</li>
 *   <li>10: "TM" symbol (from Map_PSB frame 3)</li>
 *   <li>11: "SONIC TEAM PRESENTS" (from Map_Cred .sonicteam, frame $A)</li>
 * </ul>
 */
public final class Sonic1TitleScreenMappings {

    private Sonic1TitleScreenMappings() {}

    // Frame indices
    public static final int FRAME_TITLE_SONIC_0 = 0;
    public static final int FRAME_TITLE_SONIC_7 = 7;
    public static final int FRAME_PSB_VISIBLE = 8;
    public static final int FRAME_PSB_BLANK = 9;
    public static final int FRAME_TM = 10;
    public static final int FRAME_SONIC_TEAM_PRESENTS = 11;

    /**
     * Creates all sprite mapping frames for the Sonic 1 title screen.
     *
     * @return list of frames indexed 0-11
     */
    public static List<SpriteMappingFrame> createFrames() {
        List<SpriteMappingFrame> frames = new ArrayList<>(12);

        // Frame 0 (byte_A898) - TitleSonic frame 0
        frames.add(frame(
                piece(0x08, 0x08, 3, 1, 0x00, false, false, 0, false),
                piece(0x08, 0x10, 4, 4, 0x03, false, false, 0, false),
                piece(0x28, 0x10, 4, 4, 0x13, false, false, 0, false),
                piece(0x08, 0x30, 4, 3, 0x23, false, false, 0, false),
                piece(0x28, 0x30, 4, 3, 0x2F, false, false, 0, false),
                piece(0x00, 0x48, 4, 2, 0x3B, false, false, 0, false),
                piece(0x20, 0x48, 3, 2, 0x43, false, false, 0, false),
                piece(0x38, 0x48, 1, 1, 0x49, false, false, 0, false),
                piece(0x08, 0x58, 4, 1, 0x4A, false, false, 0, false),
                piece(0x28, 0x58, 1, 1, 0x4E, false, false, 0, false)
        ));

        // Frame 1 (byte_A8CB) - TitleSonic frame 1
        frames.add(frame(
                piece(0x20, 0x48, 4, 3, 0x1BD, false, false, 0, false),
                piece(0x38, 0x38, 2, 2, 0x1C9, false, false, 0, false),
                piece(0x30, 0x40, 1, 1, 0x1CD, false, false, 0, false),
                piece(0x40, 0x48, 1, 1, 0x1CE, false, false, 0, false),
                piece(0x20, 0x60, 1, 1, 0x1CF, false, false, 0, false),
                piece(0x08, 0x10, 4, 3, 0x4F, false, false, 0, false),
                piece(0x28, 0x10, 4, 3, 0x5B, false, false, 0, false),
                piece(0x48, 0x18, 1, 2, 0x67, false, false, 0, false),
                piece(0x00, 0x28, 1, 3, 0x69, false, false, 0, false),
                piece(0x08, 0x28, 4, 4, 0x6C, false, false, 0, false),
                piece(0x28, 0x28, 4, 4, 0x7C, false, false, 0, false),
                piece(0x48, 0x30, 1, 3, 0x8C, false, false, 0, false),
                piece(0x10, 0x48, 4, 3, 0x8F, false, false, 0, false),
                piece(0x30, 0x48, 3, 2, 0x9B, false, false, 0, false),
                piece(0x30, 0x58, 2, 1, 0xA1, false, false, 0, false)
        ));

        // Frame 2 (byte_A917) - TitleSonic frame 2
        frames.add(frame(
                piece(0x28, 0x38, 4, 3, 0x1BD, false, false, 0, false),
                piece(0x40, 0x28, 2, 2, 0x1C9, false, false, 0, false),
                piece(0x38, 0x30, 1, 1, 0x1CD, false, false, 0, false),
                piece(0x48, 0x38, 1, 1, 0x1CE, false, false, 0, false),
                piece(0x28, 0x50, 1, 1, 0x1CF, false, false, 0, false),
                piece(0x08, 0x20, 4, 4, 0x1A9, false, false, 0, false),
                piece(0x28, 0x20, 1, 4, 0x1B9, false, false, 0, false),
                piece(0x08, 0x10, 4, 3, 0x4F, false, false, 0, false),
                piece(0x28, 0x10, 4, 3, 0x5B, false, false, 0, false),
                piece(0x48, 0x18, 1, 2, 0x67, false, false, 0, false),
                piece(0x00, 0x28, 1, 3, 0x69, false, false, 0, false),
                piece(0x08, 0x28, 4, 4, 0x6C, false, false, 0, false),
                piece(0x28, 0x28, 4, 4, 0x7C, false, false, 0, false),
                piece(0x48, 0x30, 1, 3, 0x8C, false, false, 0, false),
                piece(0x10, 0x48, 4, 3, 0x8F, false, false, 0, false),
                piece(0x30, 0x48, 3, 2, 0x9B, false, false, 0, false),
                piece(0x30, 0x58, 2, 1, 0xA1, false, false, 0, false)
        ));

        // Frame 3 (byte_A96D) - TitleSonic frame 3
        frames.add(frame(
                piece(0x08, 0x10, 4, 4, 0xA3, false, false, 0, false),
                piece(0x28, 0x08, 3, 1, 0xB3, false, false, 0, false),
                piece(0x28, 0x10, 4, 4, 0xB6, false, false, 0, false),
                piece(0x48, 0x18, 1, 1, 0xC6, false, false, 0, false),
                piece(0x48, 0x20, 2, 3, 0xC7, false, false, 0, false),
                piece(0x48, 0x38, 1, 1, 0xCD, false, false, 0, false),
                piece(0x08, 0x30, 4, 2, 0xCE, false, false, 0, false),
                piece(0x28, 0x30, 4, 3, 0xD6, false, false, 0, false),
                piece(0x10, 0x40, 3, 4, 0xE2, false, false, 0, false),
                piece(0x28, 0x48, 3, 1, 0xEE, false, false, 0, false),
                piece(0x08, 0x50, 1, 2, 0xF1, false, false, 0, false),
                piece(0x28, 0x50, 2, 1, 0xF3, false, false, 0, false),
                piece(0x28, 0x58, 1, 1, 0xF5, false, false, 0, false)
        ));

        // Frame 4 (byte_A9AF) - TitleSonic frame 4
        frames.add(frame(
                piece(0x10, 0x08, 4, 4, 0xF6, false, false, 0, false),
                piece(0x30, 0x08, 3, 4, 0x106, false, false, 0, false),
                piece(0x48, 0x10, 2, 3, 0x112, false, false, 0, false),
                piece(0x18, 0x28, 4, 3, 0x118, false, false, 0, false),
                piece(0x38, 0x28, 4, 3, 0x124, false, false, 0, false),
                piece(0x10, 0x28, 1, 2, 0x130, false, false, 0, false),
                piece(0x10, 0x40, 4, 3, 0x132, false, false, 0, false),
                piece(0x30, 0x40, 2, 3, 0x13E, false, false, 0, false),
                piece(0x40, 0x40, 2, 1, 0x144, false, false, 0, false),
                piece(0x40, 0x48, 1, 1, 0x146, false, false, 0, false),
                piece(0x18, 0x58, 3, 1, 0x147, false, false, 0, false)
        ));

        // Frame 5 (byte_A9E7) - TitleSonic frame 5
        frames.add(frame(
                piece(0x38, 0x28, 4, 3, 0x1E4, false, false, 0, false),
                piece(0x48, 0x18, 2, 2, 0x1F0, false, false, 0, false),
                piece(0x38, 0x40, 3, 1, 0x1F4, false, false, 0, false),
                piece(0x38, 0x48, 2, 1, 0x1F7, false, false, 0, false),
                piece(0x10, 0x10, 4, 4, 0x14A, false, false, 0, false),
                piece(0x20, 0x08, 2, 1, 0x15A, false, false, 0, false),
                piece(0x30, 0x00, 3, 4, 0x15C, false, false, 0, false),
                piece(0x48, 0x08, 1, 1, 0x168, false, false, 0, false),
                piece(0x48, 0x18, 1, 1, 0x169, false, false, 0, false),
                piece(0x00, 0x18, 2, 2, 0x16A, false, false, 0, false),
                piece(0x08, 0x28, 1, 3, 0x16E, false, false, 0, false),
                piece(0x10, 0x30, 4, 4, 0x171, false, false, 0, false),
                piece(0x30, 0x20, 4, 2, 0x181, false, false, 0, false),
                piece(0x50, 0x20, 1, 2, 0x189, false, false, 0, false),
                piece(0x30, 0x30, 3, 1, 0x18B, false, false, 0, false),
                piece(0x30, 0x38, 4, 3, 0x18E, false, false, 0, false),
                piece(0x08, 0x50, 4, 2, 0x19A, false, false, 0, false),
                piece(0x28, 0x50, 4, 1, 0x1A2, false, false, 0, false),
                piece(0x28, 0x58, 3, 1, 0x1A6, false, false, 0, false)
        ));

        // Frame 6 (byte_AA47) - TitleSonic frame 6
        frames.add(frame(
                piece(0x38, 0x28, 4, 3, 0x1E4, false, false, 0, false),
                piece(0x48, 0x18, 2, 2, 0x1F0, false, false, 0, false),
                piece(0x38, 0x40, 3, 1, 0x1F4, false, false, 0, false),
                piece(0x38, 0x48, 2, 1, 0x1F7, false, false, 0, false),
                piece(0x08, 0x18, 4, 4, 0x1D0, false, false, 0, false),
                piece(0x28, 0x18, 1, 4, 0x1E0, false, false, 0, false),
                piece(0x10, 0x10, 4, 4, 0x14A, false, false, 0, false),
                piece(0x20, 0x08, 2, 1, 0x15A, false, false, 0, false),
                piece(0x30, 0x00, 3, 4, 0x15C, false, false, 0, false),
                piece(0x48, 0x08, 1, 1, 0x168, false, false, 0, false),
                piece(0x48, 0x18, 1, 1, 0x169, false, false, 0, false),
                piece(0x00, 0x18, 2, 2, 0x16A, false, false, 0, false),
                piece(0x08, 0x28, 1, 3, 0x16E, false, false, 0, false),
                piece(0x10, 0x30, 4, 4, 0x171, false, false, 0, false),
                piece(0x30, 0x20, 4, 2, 0x181, false, false, 0, false),
                piece(0x50, 0x20, 1, 2, 0x189, false, false, 0, false),
                piece(0x30, 0x30, 3, 1, 0x18B, false, false, 0, false),
                piece(0x30, 0x38, 4, 3, 0x18E, false, false, 0, false),
                piece(0x08, 0x50, 4, 2, 0x19A, false, false, 0, false),
                piece(0x28, 0x50, 4, 1, 0x1A2, false, false, 0, false),
                piece(0x28, 0x58, 3, 1, 0x1A6, false, false, 0, false)
        ));

        // Frame 7 (byte_AAB1) - TitleSonic frame 7
        frames.add(frame(
                piece(0x38, 0x18, 2, 1, 0x1F9, false, false, 0, false),
                piece(0x38, 0x20, 1, 1, 0x1FB, false, false, 0, false),
                piece(0x30, 0x28, 3, 1, 0x1FC, false, false, 0, false),
                piece(0x30, 0x30, 1, 2, 0x1FF, false, false, 0, false),
                piece(0x38, 0x30, 3, 4, 0x201, false, false, 0, false),
                piece(0x08, 0x18, 4, 4, 0x1D0, false, false, 0, false),
                piece(0x28, 0x18, 1, 4, 0x1E0, false, false, 0, false),
                piece(0x10, 0x10, 4, 4, 0x14A, false, false, 0, false),
                piece(0x20, 0x08, 2, 1, 0x15A, false, false, 0, false),
                piece(0x30, 0x00, 3, 4, 0x15C, false, false, 0, false),
                piece(0x48, 0x08, 1, 1, 0x168, false, false, 0, false),
                piece(0x48, 0x18, 1, 1, 0x169, false, false, 0, false),
                piece(0x00, 0x18, 2, 2, 0x16A, false, false, 0, false),
                piece(0x08, 0x28, 1, 3, 0x16E, false, false, 0, false),
                piece(0x10, 0x30, 4, 4, 0x171, false, false, 0, false),
                piece(0x30, 0x20, 4, 2, 0x181, false, false, 0, false),
                piece(0x50, 0x20, 1, 2, 0x189, false, false, 0, false),
                piece(0x30, 0x30, 3, 1, 0x18B, false, false, 0, false),
                piece(0x30, 0x38, 4, 3, 0x18E, false, false, 0, false),
                piece(0x08, 0x50, 4, 2, 0x19A, false, false, 0, false),
                piece(0x28, 0x50, 4, 1, 0x1A2, false, false, 0, false),
                piece(0x28, 0x58, 3, 1, 0x1A6, false, false, 0, false)
        ));

        // Frame 8 - "PRESS START BUTTON" (Map_PSB frame 0: M_PSB_PSB)
        // Uses ArtTile_Title_Foreground tiles (tile $F0+)
        frames.add(frame(
                piece(0x00, 0x00, 4, 1, 0xF0, false, false, 0, false),
                piece(0x20, 0x00, 1, 1, 0xF3, false, false, 0, false),
                piece(0x30, 0x00, 1, 1, 0xF3, false, false, 0, false),
                piece(0x38, 0x00, 4, 1, 0xF4, false, false, 0, false),
                piece(0x60, 0x00, 3, 1, 0xF8, false, false, 0, false),
                piece(0x78, 0x00, 3, 1, 0xFB, false, false, 0, false)
        ));

        // Frame 9 - Blank frame (PSB invisible during flash cycle)
        frames.add(frame());

        // Frame 10 - "TM" symbol (Map_PSB frame 3: M_PSB_TM)
        // Uses ArtTile_Title_Trademark tiles, palette 1
        frames.add(frame(
                piece(-0x08, -0x04, 2, 1, 0x00, false, false, 0, false)
        ));

        // Frame 11 - "SONIC TEAM PRESENTS" (Map_Cred .sonicteam, frame $A)
        // Uses ArtTile_Sonic_Team_Font tiles, 17 pieces: 2 rows of text
        // Row 1: "SONIC TEAM" (pieces at y=-$18)
        // Row 2: "PRESENTS" (pieces at y=0)
        frames.add(frame(
                piece(-0x4C, -0x18, 2, 2, 0x2E, false, false, 0, false), // S
                piece(-0x3C, -0x18, 2, 2, 0x26, false, false, 0, false), // O
                piece(-0x2C, -0x18, 2, 2, 0x1A, false, false, 0, false), // N
                piece(-0x1C, -0x18, 1, 2, 0x46, false, false, 0, false), // I
                piece(-0x14, -0x18, 2, 2, 0x1E, false, false, 0, false), // C
                piece( 0x04, -0x18, 2, 2, 0x3E, false, false, 0, false), // T
                piece( 0x14, -0x18, 2, 2, 0x0E, false, false, 0, false), // E
                piece( 0x24, -0x18, 2, 2, 0x04, false, false, 0, false), // A
                piece( 0x34, -0x18, 3, 2, 0x08, false, false, 0, false), // M
                piece(-0x40,  0x00, 2, 2, 0x12, false, false, 0, false), // P
                piece(-0x30,  0x00, 2, 2, 0x22, false, false, 0, false), // R
                piece(-0x20,  0x00, 2, 2, 0x0E, false, false, 0, false), // E
                piece(-0x10,  0x00, 2, 2, 0x2E, false, false, 0, false), // S
                piece( 0x00,  0x00, 2, 2, 0x0E, false, false, 0, false), // E
                piece( 0x10,  0x00, 2, 2, 0x1A, false, false, 0, false), // N
                piece( 0x20,  0x00, 2, 2, 0x3E, false, false, 0, false), // T
                piece( 0x30,  0x00, 2, 2, 0x2E, false, false, 0, false)  // S
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
