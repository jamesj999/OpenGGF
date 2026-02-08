package uk.co.jamesj999.sonic.game.sonic3k.constants;

/**
 * ROM offset constants for Sonic 3 &amp; Knuckles (combined S3K ROM).
 *
 * <p>Verified addresses are for the standard "Sonic and Knuckles &amp; Sonic 3 (W) [!].gen"
 * combined ROM (~4MB). Addresses were verified by binary pattern matching against
 * the skdisasm data files on 2026-02-08.
 *
 * <p>Key data format differences from Sonic 2:
 * <ul>
 *   <li>8x8 pattern art: Kosinski Moduled (KosM), not plain Kosinski</li>
 *   <li>LevelLoadBlock: 24 bytes/entry (6 longwords via levartptrs macro)</li>
 *   <li>Level layout: uncompressed, variable size per act</li>
 *   <li>Collision index: noninterleaved format (primary 0x600 + secondary 0x600),
 *       pointer bit 0 set = interleaved, bit 31 reserved for S3Complete flag</li>
 *   <li>Start locations: per-character tables (Sonic vs Knuckles)</li>
 * </ul>
 */
public class Sonic3kConstants {

    private Sonic3kConstants() {}

    // ===== LevelLoadBlock table =====
    // 24 bytes per entry (via levartptrs macro):
    //   dc.l (plc1<<24)|art1        - PLC index + primary 8x8 art (KosM)
    //   dc.l (plc2<<24)|art2        - PLC index + secondary 8x8 art (KosM)
    //   dc.l (palette<<24)|blocks1  - palette index + primary 16x16 blocks (Kos)
    //   dc.l (palette<<24)|blocks2  - palette index + secondary 16x16 blocks (Kos)
    //   dc.l chunks1                - primary 128x128 chunks (Kos)
    //   dc.l chunks2                - secondary 128x128 chunks (Kos)
    public static int LEVEL_LOAD_BLOCK_ADDR = 0x091F0C;
    public static final int LEVEL_LOAD_BLOCK_ENTRY_SIZE = 24;

    // ===== Level sizes table =====
    // 8 bytes per act: dc.w xstart, xend, ystart, yend
    // Sequential: AIZ1, AIZ2, HCZ1, HCZ2, ...
    public static int LEVEL_SIZES_ADDR = 0x01BCC6;
    public static final int LEVEL_SIZES_ENTRY_SIZE = 8;

    // ===== Start location tables =====
    // 4 bytes per act: dc.w x_pos, y_pos
    // Sequential: AIZ1, AIZ2, HCZ1, HCZ2, ... (48 entries each)
    public static int SONIC_START_LOCATIONS_ADDR = 0x1E3C18;
    public static int KNUX_START_LOCATIONS_ADDR = 0x1E3CD8;
    public static final int START_LOCATION_ENTRY_SIZE = 4;
    public static final int START_LOCATION_ENTRY_COUNT = 48;

    // ===== Object and ring position pointer tables =====
    // 4 bytes per act: dc.l data_addr
    // Sequential: AIZ1, AIZ2, HCZ1, HCZ2, ...
    public static int SPRITE_LOC_PTRS_ADDR = 0x1E3D98;
    public static int RING_LOC_PTRS_ADDR = 0x1E3E58;

    // ===== Layout pointer table =====
    // 4 bytes per act: dc.l layout_addr
    // Sequential: AIZ1, AIZ2, HCZ1, HCZ2, ...
    public static int LEVEL_PTRS_ADDR = 0x09D5C0;
    public static final int LEVEL_PTRS_ENTRY_SIZE = 4;

    // ===== Layout data format =====
    // Uncompressed, variable size per act.
    // Layout format: FG layer followed by BG layer, each composed of rows of chunk indices.
    public static final int LEVEL_LAYOUT_TOTAL_SIZE = 0x1000;
    public static final int LEVEL_LAYOUT_HEADER_SIZE = 8;

    // ===== Collision =====
    // SolidIndexes: 4 bytes per act, dc.l pointing to collision index data
    // Pointer flags: bit 0 set = interleaved format (primary/secondary alternating bytes)
    //                bit 0 clear = non-interleaved (primary block then secondary block)
    // Collision index block sizes: 0x600 bytes primary + 0x600 bytes secondary
    public static int SOLID_INDEXES_ADDR = 0x098100;
    public static final int SOLID_INDEXES_ENTRY_SIZE = 4;
    public static final int COLLISION_INDEX_SIZE = 0x600; // per layer (primary or secondary)

    // Height maps and angles
    // AngleArray: 256 bytes of tile slope angles
    // HeightMaps: 256 entries x 16 bytes = 4096 bytes (vertical collision)
    // HeightMapsRot: 256 entries x 16 bytes = 4096 bytes (horizontal collision)
    public static int SOLID_TILE_ANGLE_ADDR = 0x096000;
    public static int SOLID_TILE_VERTICAL_MAP_ADDR = 0x096100;
    public static int SOLID_TILE_HORIZONTAL_MAP_ADDR = 0x097100;
    public static final int SOLID_TILE_MAP_SIZE = 0x1000;  // 256 x 16 bytes
    public static final int SOLID_TILE_ANGLE_SIZE = 0x100;  // 256 bytes

    // ===== Map dimensions =====
    // S3K uses same map structure as S2 for the layout buffer
    public static final int MAP_LAYERS = 2;
    public static final int MAP_WIDTH = 128;
    public static final int MAP_HEIGHT = 16;

    // ===== Acts per zone stride =====
    // The LevelLoadBlock indexes by zone*2+act (each zone has 2 act slots)
    public static final int ACTS_PER_ZONE_STRIDE = 2;

    // ===== Palette =====
    // PalPoint table: 8 bytes per entry (dc.l source_addr, dc.w ram_dest, dc.w longword_count)
    // Palette index from LevelLoadBlock selects entry in this table.
    // Index 3 = Pal_SonicTails, Index 5 = Pal_Knuckles
    public static int PAL_POINTERS_ADDR = 0x0A872C;
    public static final int PAL_POINTER_ENTRY_SIZE = 8;
    public static final int PAL_INDEX_SONIC_TAILS = 3;
    public static final int PAL_INDEX_KNUCKLES = 5;

    // Character palette addresses
    public static int SONIC_PALETTE_ADDR = 0x0A8A3C;  // Pal_SonicTails (64 bytes)
    public static int KNUCKLES_PALETTE_ADDR = 0x0A8AFC; // Pal_Knuckles (32 bytes)

    // ===== Known pattern data for ROM scanning =====
    // AIZ1 LevelSizes first entry: $1308, $6000, $0000, $0390
    public static final byte[] LEVEL_SIZES_AIZ1_PATTERN = {
        0x13, 0x08, 0x60, 0x00, 0x00, 0x00, 0x03, (byte) 0x90
    };

    // AIZ1 Sonic start location: X=$13A0, Y=$041A
    public static final byte[] START_LOC_AIZ1_PATTERN = {
        0x13, (byte) 0xA0, 0x04, 0x1A
    };

    // ===== Scanning state =====
    private static boolean scanned = false;

    public static boolean isScanned() {
        return scanned;
    }

    public static void setScanned(boolean value) {
        scanned = value;
    }
}
