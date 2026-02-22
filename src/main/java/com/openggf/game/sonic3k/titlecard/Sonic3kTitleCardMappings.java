package com.openggf.game.sonic3k.titlecard;

import com.openggf.game.sonic2.titlecard.TitleCardMappings;

/**
 * S3K title card sprite mapping data, transcribed from Map - Title Card.asm (S&K version).
 *
 * <p>Mapping byte format (6 bytes per piece):
 * <pre>
 * byte 0:    Y offset (signed byte, relative to element center)
 * byte 1:    VDP size byte - width = ((b>>2)&3)+1, height = (b&3)+1
 * bytes 2-3: Attribute word - priority(1)|palette(2)|vflip(1)|hflip(1)|tileIndex(11)
 * bytes 4-5: X offset (signed 16-bit, relative to element center)
 * </pre>
 *
 * <p>All pieces in this data have attribute high byte $85:
 * priority=true, palette=0, no flips. Tile indices reference
 * VRAM positions starting at $500.
 */
public final class Sonic3kTitleCardMappings {

    private Sonic3kTitleCardMappings() {}

    // Frame indices in the mapping pointer table
    public static final int FRAME_BLANK = 0;
    public static final int FRAME_BANNER = 1;
    public static final int FRAME_ACT = 2;
    public static final int FRAME_ZONE = 3;
    public static final int FRAME_AIZ = 4;
    public static final int FRAME_HCZ = 5;
    public static final int FRAME_MGZ = 6;
    public static final int FRAME_CNZ = 7;
    public static final int FRAME_FBZ = 8;
    public static final int FRAME_ICZ = 9;
    public static final int FRAME_LBZ = 10;
    public static final int FRAME_MHZ = 11;
    public static final int FRAME_SOZ = 12;
    public static final int FRAME_LRZ = 13;
    public static final int FRAME_SSZ = 14;
    public static final int FRAME_DEZ = 15;
    public static final int FRAME_DDZ = 16;
    public static final int FRAME_HPZ = 17;

    // Helper to create a piece with standard flags (priority=true, palette=0, no flip)
    private static TitleCardMappings.SpritePiece p(int x, int y, int w, int h, int tile) {
        return new TitleCardMappings.SpritePiece(x, y, w, h, tile, false, false, 0, true);
    }

    // Frame 1: Red Banner (16 pieces)
    // Vertical stack of red blocks with ACT text at top
    private static final TitleCardMappings.SpritePiece[] TC_BANNER = {
            p(-24,  88, 3, 2, 0x510),
            p(  0,  88, 3, 2, 0x516),
            p(-32,-112, 4, 4, 0x500),
            p(  0,-112, 4, 4, 0x500),
            p(-32, -80, 4, 4, 0x500),
            p(  0, -80, 4, 4, 0x500),
            p(-32, -48, 4, 4, 0x500),
            p(  0, -48, 4, 4, 0x500),
            p(-32, -16, 4, 4, 0x500),
            p(  0, -16, 4, 4, 0x500),
            p(-32,  16, 4, 4, 0x500),
            p(  0,  16, 4, 4, 0x500),
            p(-32,  48, 4, 4, 0x500),
            p(  0,  48, 4, 4, 0x500),
            p(-32,  80, 4, 4, 0x500),
            p(  0,  80, 4, 4, 0x500),
    };

    // Frame 2: ACT text (2 pieces) - "ACT" label + act number
    private static final TitleCardMappings.SpritePiece[] TC_ACT = {
            p(-28, 16, 3, 2, 0x537),
            p(-11,  0, 4, 4, 0x53D),
    };

    // Frame 3: "ZONE" text (4 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_ZONE = {
            p(-36, 0, 2, 3, 0x531),  // Z
            p(-20, 0, 3, 3, 0x528),  // O
            p(  4, 0, 2, 3, 0x522),  // N
            p( 20, 0, 2, 3, 0x51C),  // E
    };

    // Frame 4: AIZ - "ANGEL ISLAND" (11 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_AIZ = {
            p(-32, 0, 2, 3, 0x54D),
            p(-16, 0, 2, 3, 0x522),
            p(  0, 0, 2, 3, 0x559),
            p( 16, 0, 2, 3, 0x51C),
            p( 32, 0, 1, 3, 0x562),
            p( 48, 0, 1, 3, 0x55F),
            p( 56, 0, 2, 3, 0x565),
            p( 72, 0, 1, 3, 0x562),
            p( 80, 0, 2, 3, 0x54D),
            p( 96, 0, 2, 3, 0x522),
            p(112, 0, 2, 3, 0x553),
    };

    // Frame 5: HCZ - "HYDROCITY" (8 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_HCZ = {
            p(-16, 0, 2, 3, 0x559),
            p(  0, 0, 2, 3, 0x56E),
            p( 16, 0, 2, 3, 0x553),
            p( 32, 0, 2, 3, 0x562),
            p( 48, 0, 3, 3, 0x528),
            p( 72, 0, 2, 3, 0x54D),
            p( 88, 0, 1, 3, 0x55F),
            p( 96, 0, 4, 3, 0x568),
    };

    // Frame 6: MGZ - "MARBLE GARDEN" (12 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_MGZ = {
            p(-72, 0, 3, 3, 0x568),
            p(-48, 0, 2, 3, 0x54D),
            p(-32, 0, 2, 3, 0x571),
            p(-16, 0, 2, 3, 0x553),
            p(  0, 0, 1, 3, 0x565),
            p(  8, 0, 2, 3, 0x51C),
            p( 32, 0, 2, 3, 0x55F),
            p( 48, 0, 2, 3, 0x54D),
            p( 64, 0, 2, 3, 0x571),
            p( 80, 0, 2, 3, 0x559),
            p( 96, 0, 2, 3, 0x51C),
            p(112, 0, 2, 3, 0x522),
    };

    // Frame 7: CNZ - "CARNIVAL NIGHT" (12 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_CNZ = {
            p(-64, 0, 2, 3, 0x553),
            p(-48, 0, 2, 3, 0x54D),
            p(-32, 0, 2, 3, 0x56B),
            p(-16, 0, 2, 3, 0x522),
            p(  0, 0, 1, 3, 0x565),
            p(  8, 0, 2, 3, 0x577),
            p( 24, 0, 2, 3, 0x54D),
            p( 40, 0, 1, 3, 0x568),
            p( 56, 0, 2, 3, 0x522),
            p( 72, 0, 1, 3, 0x565),
            p( 80, 0, 4, 3, 0x559),
            p(112, 0, 2, 3, 0x571),
    };

    // Frame 8: FBZ - "FLYING BATTERY" (13 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_FBZ = {
            p(-72, 0, 2, 3, 0x559),
            p(-56, 0, 1, 3, 0x568),
            p(-48, 0, 2, 3, 0x577),
            p(-32, 0, 1, 3, 0x565),
            p(-24, 0, 2, 3, 0x522),
            p( -8, 0, 2, 3, 0x55F),
            p( 16, 0, 2, 3, 0x553),
            p( 32, 0, 2, 3, 0x54D),
            p( 48, 0, 2, 3, 0x571),
            p( 64, 0, 2, 3, 0x571),
            p( 80, 0, 2, 3, 0x51C),
            p( 96, 0, 2, 3, 0x56B),
            p(112, 0, 2, 3, 0x577),
    };

    // Frame 9: ICZ - "ICECAP" (6 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_ICZ = {
            p( 40, 0, 1, 3, 0x559),
            p( 48, 0, 2, 3, 0x553),
            p( 64, 0, 2, 3, 0x51C),
            p( 80, 0, 2, 3, 0x553),
            p( 96, 0, 2, 3, 0x54D),
            p(112, 0, 2, 3, 0x55C),
    };

    // Frame 10: LBZ - "LAUNCH BASE" (9 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_LBZ = {
            p(-32, 0, 1, 3, 0x565),
            p(-24, 0, 2, 3, 0x54D),
            p( -8, 0, 2, 3, 0x56E),
            p(  8, 0, 2, 3, 0x522),
            p( 24, 0, 4, 3, 0x559),
            p( 64, 0, 2, 3, 0x553),
            p( 80, 0, 2, 3, 0x54D),
            p( 96, 0, 2, 3, 0x568),
            p(112, 0, 2, 3, 0x51C),
    };

    // Frame 11: MHZ - "MUSHROOM HILL" (10 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_MHZ = {
            p(-80, 0, 3, 3, 0x559),
            p(-56, 0, 2, 3, 0x56E),
            p(-40, 0, 2, 3, 0x568),
            p(-24, 0, 2, 3, 0x54D),
            p( -8, 0, 2, 3, 0x562),
            p(  8, 0, 3, 3, 0x528),
            p( 32, 0, 3, 3, 0x528),
            p( 56, 0, 3, 3, 0x559),
            p( 88, 0, 4, 3, 0x54D),
            p(120, 0, 1, 3, 0x556),
    };

    // Frame 12: SOZ - "SANDOPOLIS" (10 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_SOZ = {
            p(-32, 0, 2, 3, 0x565),
            p(-16, 0, 2, 3, 0x54D),
            p(  0, 0, 2, 3, 0x522),
            p( 16, 0, 2, 3, 0x553),
            p( 32, 0, 3, 3, 0x528),
            p( 56, 0, 2, 3, 0x55F),
            p( 72, 0, 3, 3, 0x528),
            p( 96, 0, 1, 3, 0x55C),
            p(104, 0, 1, 3, 0x559),
            p(112, 0, 2, 3, 0x565),
    };

    // Frame 13: LRZ - "LAVA REEF" (8 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_LRZ = {
            p(  0, 0, 1, 3, 0x559),
            p(  8, 0, 2, 3, 0x54D),
            p( 24, 0, 2, 3, 0x562),
            p( 40, 0, 2, 3, 0x54D),
            p( 64, 0, 2, 3, 0x55C),
            p( 80, 0, 2, 3, 0x51C),
            p( 96, 0, 2, 3, 0x51C),
            p(112, 0, 2, 3, 0x553),
    };

    // Frame 14: SSZ - "SKY SANCTUARY" (11 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_SSZ = {
            p(-72, 0, 2, 3, 0x565),
            p(-56, 0, 2, 3, 0x559),
            p(-40, 0, 2, 3, 0x577),
            p(-16, 0, 2, 3, 0x565),
            p(  0, 0, 2, 3, 0x54D),
            p( 16, 0, 2, 3, 0x522),
            p( 32, 0, 2, 3, 0x553),
            p( 48, 0, 4, 3, 0x56B),
            p( 80, 0, 2, 3, 0x54D),
            p( 96, 0, 2, 3, 0x55F),
            p(112, 0, 2, 3, 0x577),
    };

    // Frame 15: DEZ - "DEATH EGG" (8 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_DEZ = {
            p( -8, 0, 2, 3, 0x553),
            p(  8, 0, 2, 3, 0x51C),
            p( 24, 0, 2, 3, 0x54D),
            p( 40, 0, 2, 3, 0x565),
            p( 56, 0, 2, 3, 0x55F),
            p( 80, 0, 2, 3, 0x51C),
            p( 96, 0, 2, 3, 0x559),
            p(112, 0, 2, 3, 0x559),
    };

    // Frame 16: DDZ - "THE DOOMSDAY" (11 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_DDZ = {
            p(-80, 0, 2, 3, 0x56E),
            p(-64, 0, 2, 3, 0x559),
            p(-48, 0, 2, 3, 0x51C),
            p(-24, 0, 2, 3, 0x553),
            p( -8, 0, 3, 3, 0x528),
            p( 16, 0, 3, 3, 0x528),
            p( 40, 0, 3, 3, 0x55F),
            p( 64, 0, 2, 3, 0x568),
            p( 80, 0, 2, 3, 0x553),
            p( 96, 0, 2, 3, 0x54D),
            p(112, 0, 2, 3, 0x574),
    };

    // Frame 17: HPZ - "HIDDEN PALACE" (11 pieces)
    private static final TitleCardMappings.SpritePiece[] TC_HPZ = {
            p(-56, 0, 3, 3, 0x55F),
            p(-32, 0, 2, 3, 0x559),
            p(-16, 0, 2, 3, 0x559),
            p(  0, 0, 2, 3, 0x51C),
            p( 16, 0, 2, 3, 0x522),
            p( 40, 0, 2, 3, 0x56B),
            p( 56, 0, 2, 3, 0x54D),
            p( 72, 0, 1, 3, 0x568),
            p( 80, 0, 2, 3, 0x54D),
            p( 96, 0, 2, 3, 0x553),
            p(112, 0, 2, 3, 0x51C),
    };

    // Empty frame
    private static final TitleCardMappings.SpritePiece[] TC_BLANK = {};

    /**
     * Returns the SpritePiece array for the given frame index.
     */
    public static TitleCardMappings.SpritePiece[] getFrame(int frameIndex) {
        return switch (frameIndex) {
            case FRAME_BLANK -> TC_BLANK;
            case FRAME_BANNER -> TC_BANNER;
            case FRAME_ACT -> TC_ACT;
            case FRAME_ZONE -> TC_ZONE;
            case FRAME_AIZ -> TC_AIZ;
            case FRAME_HCZ -> TC_HCZ;
            case FRAME_MGZ -> TC_MGZ;
            case FRAME_CNZ -> TC_CNZ;
            case FRAME_FBZ -> TC_FBZ;
            case FRAME_ICZ -> TC_ICZ;
            case FRAME_LBZ -> TC_LBZ;
            case FRAME_MHZ -> TC_MHZ;
            case FRAME_SOZ -> TC_SOZ;
            case FRAME_LRZ -> TC_LRZ;
            case FRAME_SSZ -> TC_SSZ;
            case FRAME_DEZ -> TC_DEZ;
            case FRAME_DDZ -> TC_DDZ;
            case FRAME_HPZ -> TC_HPZ;
            default -> TC_BLANK;
        };
    }

    /**
     * Maps zone index to mapping frame index.
     * Zones 0-12 map directly (zone + 4). HPZ is a special case.
     */
    public static int getZoneFrame(int zoneIndex) {
        if (zoneIndex >= 0 && zoneIndex <= 12) {
            return zoneIndex + FRAME_AIZ;  // zone 0 → frame 4, zone 12 → frame 16
        }
        // Special zone mappings (HPZ = zone 22 in S3K addressing)
        if (zoneIndex == 22) {
            return FRAME_HPZ;
        }
        return FRAME_AIZ;  // Fallback to AIZ
    }

    /**
     * Returns whether the zone shows only 1 act (no act number displayed).
     * SSZ (zone 10), DDZ (zone 12), HPZ (zone 22) are single-act.
     */
    public static boolean isSingleActZone(int zoneIndex) {
        return zoneIndex == 10 || zoneIndex == 12 || zoneIndex == 22;
    }
}
