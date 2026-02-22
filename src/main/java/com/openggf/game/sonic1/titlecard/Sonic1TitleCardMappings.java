package com.openggf.game.sonic1.titlecard;

import com.openggf.game.sonic2.titlecard.TitleCardMappings;

/**
 * Sprite mapping data for Sonic 1 title cards.
 * Extracted from s1disasm sonic.asm:4964-5102 (Map_Card table).
 *
 * <p>Sonic 1 uses a single Nemesis-compressed art file with tile indices starting at 0.
 * Unlike Sonic 2 which has separate art files and VRAM-offset tile indices ($580+),
 * Sonic 1 tile indices are direct offsets into the art pattern array.
 *
 * <p>The spritePiece macro format from disassembly:
 * spritePiece xOff, yOff, width, height, tileIndex, hFlip, vFlip, palette, priority
 */
public final class Sonic1TitleCardMappings {

    private Sonic1TitleCardMappings() {}

    // Frame indices
    public static final int FRAME_GHZ = 0;   // GREEN HILL
    public static final int FRAME_LZ = 1;    // LABYRINTH
    public static final int FRAME_MZ = 2;    // MARBLE
    public static final int FRAME_SLZ = 3;   // STAR LIGHT
    public static final int FRAME_SYZ = 4;   // SPRING YARD
    public static final int FRAME_SBZ = 5;   // SCRAP BRAIN
    public static final int FRAME_ZONE = 6;  // "ZONE"
    public static final int FRAME_ACT_1 = 7; // ACT 1
    public static final int FRAME_ACT_2 = 8; // ACT 2
    public static final int FRAME_ACT_3 = 9; // ACT 3
    public static final int FRAME_OVAL = 10;  // Oval decoration
    public static final int FRAME_FZ = 11;   // FINAL

    // ========== Zone Name Mappings ==========

    // GREEN HILL (M_Card_GHZ) - 9 pieces
    private static final TitleCardMappings.SpritePiece[] MC_GHZ = {
            new TitleCardMappings.SpritePiece(-0x4C, -8, 2, 2, 0x18, false, false, 0, false), // G
            new TitleCardMappings.SpritePiece(-0x3C, -8, 2, 2, 0x3A, false, false, 0, false), // R
            new TitleCardMappings.SpritePiece(-0x2C, -8, 2, 2, 0x10, false, false, 0, false), // E
            new TitleCardMappings.SpritePiece(-0x1C, -8, 2, 2, 0x10, false, false, 0, false), // E
            new TitleCardMappings.SpritePiece(-0x0C, -8, 2, 2, 0x2E, false, false, 0, false), // N
            new TitleCardMappings.SpritePiece( 0x14, -8, 2, 2, 0x1C, false, false, 0, false), // H
            new TitleCardMappings.SpritePiece( 0x24, -8, 1, 2, 0x20, false, false, 0, false), // I
            new TitleCardMappings.SpritePiece( 0x2C, -8, 2, 2, 0x26, false, false, 0, false), // L
            new TitleCardMappings.SpritePiece( 0x3C, -8, 2, 2, 0x26, false, false, 0, false), // L
    };

    // LABYRINTH (M_Card_LZ) - 9 pieces
    private static final TitleCardMappings.SpritePiece[] MC_LZ = {
            new TitleCardMappings.SpritePiece(-0x44, -8, 2, 2, 0x26, false, false, 0, false), // L
            new TitleCardMappings.SpritePiece(-0x34, -8, 2, 2, 0x00, false, false, 0, false), // A
            new TitleCardMappings.SpritePiece(-0x24, -8, 2, 2, 0x04, false, false, 0, false), // B
            new TitleCardMappings.SpritePiece(-0x14, -8, 2, 2, 0x4A, false, false, 0, false), // Y
            new TitleCardMappings.SpritePiece(-0x04, -8, 2, 2, 0x3A, false, false, 0, false), // R
            new TitleCardMappings.SpritePiece( 0x0C, -8, 1, 2, 0x20, false, false, 0, false), // I
            new TitleCardMappings.SpritePiece( 0x14, -8, 2, 2, 0x2E, false, false, 0, false), // N
            new TitleCardMappings.SpritePiece( 0x24, -8, 2, 2, 0x42, false, false, 0, false), // T
            new TitleCardMappings.SpritePiece( 0x34, -8, 2, 2, 0x1C, false, false, 0, false), // H
    };

    // MARBLE (M_Card_MZ) - 6 pieces
    private static final TitleCardMappings.SpritePiece[] MC_MZ = {
            new TitleCardMappings.SpritePiece(-0x31, -8, 2, 2, 0x2A, false, false, 0, false), // M
            new TitleCardMappings.SpritePiece(-0x20, -8, 2, 2, 0x00, false, false, 0, false), // A
            new TitleCardMappings.SpritePiece(-0x10, -8, 2, 2, 0x3A, false, false, 0, false), // R
            new TitleCardMappings.SpritePiece( 0x00, -8, 2, 2, 0x04, false, false, 0, false), // B
            new TitleCardMappings.SpritePiece( 0x10, -8, 2, 2, 0x26, false, false, 0, false), // L
            new TitleCardMappings.SpritePiece( 0x20, -8, 2, 2, 0x10, false, false, 0, false), // E
    };

    // STAR LIGHT (M_Card_SLZ) - 9 pieces
    private static final TitleCardMappings.SpritePiece[] MC_SLZ = {
            new TitleCardMappings.SpritePiece(-0x4C, -8, 2, 2, 0x3E, false, false, 0, false), // S
            new TitleCardMappings.SpritePiece(-0x3C, -8, 2, 2, 0x42, false, false, 0, false), // T
            new TitleCardMappings.SpritePiece(-0x2C, -8, 2, 2, 0x00, false, false, 0, false), // A
            new TitleCardMappings.SpritePiece(-0x1C, -8, 2, 2, 0x3A, false, false, 0, false), // R
            new TitleCardMappings.SpritePiece( 0x04, -8, 2, 2, 0x26, false, false, 0, false), // L
            new TitleCardMappings.SpritePiece( 0x14, -8, 1, 2, 0x20, false, false, 0, false), // I
            new TitleCardMappings.SpritePiece( 0x1C, -8, 2, 2, 0x18, false, false, 0, false), // G
            new TitleCardMappings.SpritePiece( 0x2C, -8, 2, 2, 0x1C, false, false, 0, false), // H
            new TitleCardMappings.SpritePiece( 0x3C, -8, 2, 2, 0x42, false, false, 0, false), // T
    };

    // SPRING YARD (M_Card_SYZ) - 10 pieces
    private static final TitleCardMappings.SpritePiece[] MC_SYZ = {
            new TitleCardMappings.SpritePiece(-0x54, -8, 2, 2, 0x3E, false, false, 0, false), // S
            new TitleCardMappings.SpritePiece(-0x44, -8, 2, 2, 0x36, false, false, 0, false), // P
            new TitleCardMappings.SpritePiece(-0x34, -8, 2, 2, 0x3A, false, false, 0, false), // R
            new TitleCardMappings.SpritePiece(-0x24, -8, 1, 2, 0x20, false, false, 0, false), // I
            new TitleCardMappings.SpritePiece(-0x1C, -8, 2, 2, 0x2E, false, false, 0, false), // N
            new TitleCardMappings.SpritePiece(-0x0C, -8, 2, 2, 0x18, false, false, 0, false), // G
            new TitleCardMappings.SpritePiece( 0x14, -8, 2, 2, 0x4A, false, false, 0, false), // Y
            new TitleCardMappings.SpritePiece( 0x24, -8, 2, 2, 0x00, false, false, 0, false), // A
            new TitleCardMappings.SpritePiece( 0x34, -8, 2, 2, 0x3A, false, false, 0, false), // R
            new TitleCardMappings.SpritePiece( 0x44, -8, 2, 2, 0x0C, false, false, 0, false), // D
    };

    // SCRAP BRAIN (M_Card_SBZ) - 10 pieces
    private static final TitleCardMappings.SpritePiece[] MC_SBZ = {
            new TitleCardMappings.SpritePiece(-0x54, -8, 2, 2, 0x3E, false, false, 0, false), // S
            new TitleCardMappings.SpritePiece(-0x44, -8, 2, 2, 0x08, false, false, 0, false), // C
            new TitleCardMappings.SpritePiece(-0x34, -8, 2, 2, 0x3A, false, false, 0, false), // R
            new TitleCardMappings.SpritePiece(-0x24, -8, 2, 2, 0x00, false, false, 0, false), // A
            new TitleCardMappings.SpritePiece(-0x14, -8, 2, 2, 0x36, false, false, 0, false), // P
            new TitleCardMappings.SpritePiece( 0x0C, -8, 2, 2, 0x04, false, false, 0, false), // B
            new TitleCardMappings.SpritePiece( 0x1C, -8, 2, 2, 0x3A, false, false, 0, false), // R
            new TitleCardMappings.SpritePiece( 0x2C, -8, 2, 2, 0x00, false, false, 0, false), // A
            new TitleCardMappings.SpritePiece( 0x3C, -8, 1, 2, 0x20, false, false, 0, false), // I
            new TitleCardMappings.SpritePiece( 0x44, -8, 2, 2, 0x2E, false, false, 0, false), // N
    };

    // "ZONE" (M_Card_Zone) - 4 pieces
    private static final TitleCardMappings.SpritePiece[] MC_ZONE = {
            new TitleCardMappings.SpritePiece(-0x20, -8, 2, 2, 0x4E, false, false, 0, false), // Z
            new TitleCardMappings.SpritePiece(-0x10, -8, 2, 2, 0x32, false, false, 0, false), // O
            new TitleCardMappings.SpritePiece( 0x00, -8, 2, 2, 0x2E, false, false, 0, false), // N
            new TitleCardMappings.SpritePiece( 0x10, -8, 2, 2, 0x10, false, false, 0, false), // E
    };

    // ACT 1 (M_Card_Act1) - 2 pieces
    private static final TitleCardMappings.SpritePiece[] MC_ACT1 = {
            new TitleCardMappings.SpritePiece(-0x14, 4, 4, 1, 0x53, false, false, 0, false),
            new TitleCardMappings.SpritePiece( 0x0C, -0x0C, 1, 3, 0x57, false, false, 0, false),
    };

    // ACT 2 (M_Card_Act2) - 2 pieces
    private static final TitleCardMappings.SpritePiece[] MC_ACT2 = {
            new TitleCardMappings.SpritePiece(-0x14, 4, 4, 1, 0x53, false, false, 0, false),
            new TitleCardMappings.SpritePiece( 0x08, -0x0C, 2, 3, 0x5A, false, false, 0, false),
    };

    // ACT 3 (M_Card_Act3) - 2 pieces
    private static final TitleCardMappings.SpritePiece[] MC_ACT3 = {
            new TitleCardMappings.SpritePiece(-0x14, 4, 4, 1, 0x53, false, false, 0, false),
            new TitleCardMappings.SpritePiece( 0x08, -0x0C, 2, 3, 0x60, false, false, 0, false),
    };

    // OVAL (M_Card_Oval) - 13 pieces (note hFlip/vFlip on mirrored pieces)
    private static final TitleCardMappings.SpritePiece[] MC_OVAL = {
            new TitleCardMappings.SpritePiece(-0x0C, -0x1C, 4, 1, 0x70, false, false, 0, false),
            new TitleCardMappings.SpritePiece( 0x14, -0x1C, 1, 3, 0x74, false, false, 0, false),
            new TitleCardMappings.SpritePiece(-0x14, -0x14, 2, 1, 0x77, false, false, 0, false),
            new TitleCardMappings.SpritePiece(-0x1C, -0x0C, 2, 2, 0x79, false, false, 0, false),
            new TitleCardMappings.SpritePiece(-0x14,  0x14, 4, 1, 0x70, true,  true,  0, false),
            new TitleCardMappings.SpritePiece(-0x1C,  0x04, 1, 3, 0x74, true,  true,  0, false),
            new TitleCardMappings.SpritePiece( 0x04,  0x0C, 2, 1, 0x77, true,  true,  0, false),
            new TitleCardMappings.SpritePiece( 0x0C, -0x04, 2, 2, 0x79, true,  true,  0, false),
            new TitleCardMappings.SpritePiece(-0x04, -0x14, 3, 1, 0x7D, false, false, 0, false),
            new TitleCardMappings.SpritePiece(-0x0C, -0x0C, 4, 1, 0x7C, false, false, 0, false),
            new TitleCardMappings.SpritePiece(-0x0C, -0x04, 3, 1, 0x7C, false, false, 0, false),
            new TitleCardMappings.SpritePiece(-0x14,  0x04, 4, 1, 0x7C, false, false, 0, false),
            new TitleCardMappings.SpritePiece(-0x14,  0x0C, 3, 1, 0x7C, false, false, 0, false),
    };

    // FINAL (M_Card_FZ) - 5 pieces
    private static final TitleCardMappings.SpritePiece[] MC_FZ = {
            new TitleCardMappings.SpritePiece(-0x24, -8, 2, 2, 0x14, false, false, 0, false), // F
            new TitleCardMappings.SpritePiece(-0x14, -8, 1, 2, 0x20, false, false, 0, false), // I
            new TitleCardMappings.SpritePiece(-0x0C, -8, 2, 2, 0x2E, false, false, 0, false), // N
            new TitleCardMappings.SpritePiece( 0x04, -8, 2, 2, 0x00, false, false, 0, false), // A
            new TitleCardMappings.SpritePiece( 0x14, -8, 2, 2, 0x26, false, false, 0, false), // L
    };

    /**
     * Gets the sprite pieces for a mapping frame.
     * @param frameIndex Frame index (0-11)
     * @return Array of sprite pieces
     */
    public static TitleCardMappings.SpritePiece[] getFrame(int frameIndex) {
        return switch (frameIndex) {
            case FRAME_GHZ -> MC_GHZ;
            case FRAME_LZ -> MC_LZ;
            case FRAME_MZ -> MC_MZ;
            case FRAME_SLZ -> MC_SLZ;
            case FRAME_SYZ -> MC_SYZ;
            case FRAME_SBZ -> MC_SBZ;
            case FRAME_ZONE -> MC_ZONE;
            case FRAME_ACT_1 -> MC_ACT1;
            case FRAME_ACT_2 -> MC_ACT2;
            case FRAME_ACT_3 -> MC_ACT3;
            case FRAME_OVAL -> MC_OVAL;
            case FRAME_FZ -> MC_FZ;
            default -> new TitleCardMappings.SpritePiece[0];
        };
    }

    /**
     * Mapping from gameplay progression index to title card frame index.
     * Gameplay order: GHZ=0, MZ=1, SYZ=2, LZ=3, SLZ=4, SBZ=5, FZ=6
     */
    private static final int[] GAMEPLAY_TO_FRAME = {
            FRAME_GHZ,  // gameplay 0 = GHZ
            FRAME_MZ,   // gameplay 1 = MZ
            FRAME_SYZ,  // gameplay 2 = SYZ
            FRAME_LZ,   // gameplay 3 = LZ
            FRAME_SLZ,  // gameplay 4 = SLZ
            FRAME_SBZ,  // gameplay 5 = SBZ
            FRAME_FZ,   // gameplay 6 = FZ
    };

    /**
     * Gets the zone name frame index for a given zone.
     * Handles special cases: Final Zone uses FINAL.
     *
     * @param zoneIndex S1 gameplay zone index (0-6, matching zone registry order)
     * @param actIndex Act index (0-2)
     * @return Frame index for the zone name
     */
    public static int getZoneNameFrame(int zoneIndex, int actIndex) {
        // Final Zone (gameplay index 6)
        if (zoneIndex == 6) {
            return FRAME_FZ;
        }
        if (zoneIndex >= 0 && zoneIndex < GAMEPLAY_TO_FRAME.length) {
            return GAMEPLAY_TO_FRAME[zoneIndex];
        }
        return FRAME_GHZ; // Default
    }

    /**
     * Gets the act number frame.
     * @param actIndex Act index (0-2)
     * @return Frame index for act number (7, 8, or 9)
     */
    public static int getActFrame(int actIndex) {
        // Act 0 -> ACT 1 (frame 7), Act 1 -> ACT 2 (frame 8), Act 2 -> ACT 3 (frame 9)
        // Cap at frame 9 (act 3 uses same as act 2+1, but clamp to available frames)
        return FRAME_ACT_1 + Math.min(actIndex, 2);
    }

    /**
     * Returns true if this zone/act should skip the act number.
     * Final Zone has no act number displayed.
     *
     * @param zoneIndex Gameplay zone index (0-6, matching zone registry order)
     * @param actIndex Act index
     * @return true if act number should be hidden
     */
    public static boolean shouldHideActNumber(int zoneIndex, int actIndex) {
        // Final Zone has no act number.
        return zoneIndex == 6;
    }

    // ========== Per-zone element configuration ==========
    // Converted from Card_ConData (s1disasm line 152-158)
    // VDP coordinates converted to screen coordinates (subtract 128 from X/Y)
    //
    // Format: { zoneName_startX, zoneName_targetX, zone_startX, zone_targetX,
    //           act_startX, act_targetX, oval_startX, oval_targetX }

    // Gameplay progression order: GHZ, MZ, SYZ, LZ, SLZ, SBZ, FZ
    private static final int[][] CON_DATA = {
            // GHZ: dc.w 0, $120, $FEFC, $13C, $414, $154, $214, $154
            { -128, 160, -388, 188, 916, 212, 404, 212 },
            // MZ: dc.w 0, $120, $FEE0, $120, $3F8, $138, $1F8, $138
            { -128, 160, -416, 160, 888, 184, 376, 184 },
            // SYZ: dc.w 0, $120, $FF04, $144, $41C, $15C, $21C, $15C
            { -128, 160, -380, 196, 924, 220, 412, 220 },
            // LZ: dc.w 0, $120, $FEF4, $134, $40C, $14C, $20C, $14C
            { -128, 160, -396, 180, 908, 204, 396, 204 },
            // SLZ: dc.w 0, $120, $FEFC, $13C, $414, $154, $214, $154
            { -128, 160, -388, 188, 916, 212, 404, 212 },
            // SBZ: dc.w 0, $120, $FF04, $144, $41C, $15C, $21C, $15C
            { -128, 160, -380, 196, 924, 220, 412, 220 },
            // FZ: dc.w 0, $120, $FEE4, $124, $3EC, $3EC, $1EC, $12C
            { -128, 160, -412, 164, 876, 876, 364, 172 },
    };

    // Y positions (screen coords) from Card_ItemData
    public static final int Y_ZONE_NAME = 80;  // $D0 - 128
    public static final int Y_ZONE_TEXT = 100;  // $E4 - 128
    public static final int Y_ACT = 106;        // $EA - 128
    public static final int Y_OVAL = 96;         // $E0 - 128

    /**
     * Gets the configuration data for a zone.
     * @param configIndex Zone config index (0-6, from Card_CheckSBZ3 logic)
     * @return Array of 8 ints: {zoneName_startX, zoneName_targetX, zone_startX, zone_targetX,
     *         act_startX, act_targetX, oval_startX, oval_targetX}
     */
    public static int[] getConData(int configIndex) {
        if (configIndex >= 0 && configIndex < CON_DATA.length) {
            return CON_DATA[configIndex];
        }
        return CON_DATA[0]; // Default to GHZ
    }

    /**
     * Gets the config index for a zone/act combination.
     * Matches Card_CheckSBZ3 / Card_CheckFZ logic from disassembly.
     * CON_DATA is now in gameplay order, so config index = gameplay zone index.
     *
     * @param zoneIndex Gameplay zone index (0-6, matching zone registry order)
     * @param actIndex Act index (0-2)
     * @return Config index for CON_DATA (0-6)
     */
    public static int getConfigIndex(int zoneIndex, int actIndex) {
        // Zone 6 (FZ) uses FZ config
        if (zoneIndex == 6) {
            return 6;
        }
        return Math.min(zoneIndex, 5);
    }
}
