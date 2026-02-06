package uk.co.jamesj999.sonic.game.sonic1.constants;

/**
 * ROM addresses and constants for Sonic the Hedgehog 1 (Mega Drive/Genesis).
 * Addresses are for the standard REV00/REV01 ROM.
 */
public final class Sonic1Constants {

    private Sonic1Constants() {
    }

    // ---- Zone IDs (from s1disasm) ----
    public static final int ZONE_GHZ  = 0x00; // Green Hill Zone
    public static final int ZONE_LZ   = 0x01; // Labyrinth Zone
    public static final int ZONE_MZ   = 0x02; // Marble Zone
    public static final int ZONE_SLZ  = 0x03; // Star Light Zone
    public static final int ZONE_SYZ  = 0x04; // Spring Yard Zone
    public static final int ZONE_SBZ  = 0x05; // Scrap Brain Zone
    public static final int ZONE_ENDZ = 0x06; // Ending / Final Zone
    public static final int ZONE_SS   = 0x07; // Special Stage

    // ---- Level data ----
    public static final int LEVEL_HEADERS_ADDR    = 0x1DD16; // 16 bytes per zone header
    public static final int LEVEL_INDEX_ADDR      = 0x68B96; // Layout offset table
    public static final int OBJ_POS_INDEX_ADDR    = 0x6B000; // Object placement index
    public static final int START_LOC_ARRAY_ADDR  = 0x0611E; // Start positions (4 bytes per act)

    // ---- Level size / boundary array ----
    // LevelSizeArray from disassembly - contains camera boundaries per act
    // Each entry: 6 words (12 bytes) = unused, left, right, top, bottom, vshift
    // 4 act slots per zone, 7 zones (GHZ..Ending)
    public static final int LEVEL_SIZE_ARRAY_ADDR = 0x05F2A;

    // ---- Collision data ----
    public static final int COLLISION_ARRAY_NORMAL_ADDR  = 0x62A00; // CollArray1 (0x1000 bytes)
    public static final int COLLISION_ARRAY_ROTATED_ADDR = 0x63A00; // CollArray2 (0x1000 bytes)
    public static final int ANGLE_MAP_ADDR               = 0x62900; // AngleMap (0x100 bytes)

    // Per-zone collision index addresses (raw binary)
    public static final int COL_GHZ_ADDR = 0x64A00;
    public static final int COL_LZ_ADDR  = 0x64B9A;
    public static final int COL_MZ_ADDR  = 0x64C62;
    public static final int COL_SLZ_ADDR = 0x64DF2;
    public static final int COL_SYZ_ADDR = 0x64FE6;
    public static final int COL_SBZ_ADDR = 0x651DA;

    // ---- Palette data ----
    // PalPointers table: 8 bytes per entry (dc.l addr, dc.w ramDest, dc.w count-1)
    public static final int PALETTE_TABLE_ADDR = 0x2168;
    public static final int SONIC_PALETTE_ADDR = 0x23A0; // Pal_Sonic (palette ID 3)
    public static final int GHZ_PALETTE_ADDR   = 0x2400; // palid_GHZ = 4

    // ---- Pattern Load Cues (PLC) ----
    // ArtLoadCues pointer table: word offsets to PLC lists, indexed by PLC ID.
    // Each PLC list starts with a word (entry_count - 1), followed by 6-byte entries:
    //   dc.l rom_address, dc.w vram_byte_offset
    // Divide vram_byte_offset by 0x20 to get tile index.
    public static final int ART_LOAD_CUES_ADDR = 0x01DD86;

    // Per-zone art addresses (patterns, 16x16, 256x256) are read dynamically
    // from LevelHeaders at runtime - no need for per-zone constants here.

    // ---- Block / tile sizes (Sonic 1 uses 256x256 blocks, not 128x128) ----
    public static final int BLOCK_WIDTH_PX  = 256;
    public static final int BLOCK_HEIGHT_PX = 256;
    public static final int CHUNKS_PER_BLOCK_SIDE = 16; // 16x16 chunks in a 256x256 block
    public static final int CHUNK_SIZE_PX   = 16;       // Each chunk is 16x16 pixels
    public static final int PATTERN_SIZE_PX = 8;        // Each pattern is 8x8 pixels

    // Solid tile data sizes
    public static final int SOLID_TILE_MAP_SIZE   = 0x1000;
    public static final int SOLID_TILE_ANGLE_SIZE = 0x100;
}
