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
 *       noninterleaved entries commonly use pointer bit 0 (+1 marker),
 *       bit 31 reserved for S3Complete builds</li>
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
    // LevelLoadBlock entry index for "SONIC/TAILS INTRO" in sonic3k.asm levartptrs table.
    // Used by AIZ1 intro-skip bootstrap to source gameplay-ready 16x16/8x8 secondary data.
    public static final int LEVEL_LOAD_BLOCK_AIZ1_INTRO_INDEX = 26;

    // ===== Level sizes table =====
    // 8 bytes per act: dc.w xstart, xend, ystart, yend
    // Sequential: AIZ1, AIZ2, HCZ1, HCZ2, ...
    public static int LEVEL_SIZES_ADDR = 0x01BCC6;
    public static final int LEVEL_SIZES_ENTRY_SIZE = 8;
    // LevelSizes entry index for "AIZ Intro" (Current_zone_and_act = $0D00).
    // This has the taller vertical bounds needed for post-intro AIZ1 gameplay.
    public static final int LEVEL_SIZES_AIZ1_INTRO_INDEX = 26;

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
    public static final int LEVEL_LAYOUT_RAM_BASE = 0x8000;
    public static final int LEVEL_LAYOUT_ROW_POINTER_MASK = 0x7FFF;

    // ===== Collision =====
    // SolidIndexes: 4 bytes per act (indexed as zone*2+act), dc.l pointing to collision index data
    // Format detection: addresses >= S3_LEVEL_SOLID_DATA are non-interleaved (S3 zones),
    //                   addresses < S3_LEVEL_SOLID_DATA are interleaved (SK zones)
    // Non-interleaved: primary 0x600 bytes, then secondary 0x600 bytes
    // Interleaved: primary/secondary alternate bytes in 0xC00 block
    public static int SOLID_INDEXES_ADDR = 0x098100;
    public static final int SOLID_INDEXES_ENTRY_SIZE = 4;
    public static final int COLLISION_INDEX_SIZE = 0x600; // per layer (primary or secondary)
    public static final int COLLISION_INDEX_STRIDE_BYTES = 2;

    // Address threshold for collision format detection (from sonic3k.asm LoadSolids routine)
    // S3 zones have collision data at >= this address (non-interleaved)
    // SK zones have collision data below this address (interleaved)
    public static final int S3_LEVEL_SOLID_DATA = 0x260000;

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

    // ===== AIZ Intro Cinematic =====================================================
    // Addresses verified 2026-02-13 by ROM binary pattern search and LockOn Pointer
    // cumulative offset calculation, cross-checked against disassembly frame labels.

    // --- Art addresses ---
    // ArtKosM_AIZIntroPlane - Tornado biplane sprite art (KosinskiM compressed)
    // Decompresses to 4352 bytes (136 tiles, 8x8 @ 4bpp)
    public static final int ART_KOSM_AIZ_INTRO_PLANE_ADDR = 0x382624;

    // ArtKosM_AIZIntroEmeralds - Chaos Emerald sprite art (KosinskiM compressed)
    // Decompresses to 224 bytes (7 tiles)
    public static final int ART_KOSM_AIZ_INTRO_EMERALDS_ADDR = 0x387CA6;

    // ArtNem_AIZIntroSprites - Wave/water spray sprite art (Nemesis compressed)
    // Decompresses to 11008 bytes (344 tiles)
    public static final int ART_NEM_AIZ_INTRO_SPRITES_ADDR = 0x3481A0;

    // ArtUnc_CutsceneKnux - Cutscene Knuckles sprite art (uncompressed, DPLC-driven)
    // 0x4EE0 bytes = 631 tiles
    public static final int ART_UNC_CUTSCENE_KNUX_ADDR = 0x382DC6;
    public static final int ART_UNC_CUTSCENE_KNUX_SIZE = 0x4EE0;

    // --- Mapping addresses ---
    // Map_AIZIntroPlane - Tornado biplane sprite mappings (0xF2 bytes, 11 frames)
    public static final int MAP_AIZ_INTRO_PLANE_ADDR = 0x364470;

    // Map_AIZIntroEmeralds - Emerald sprite mappings (0x46 bytes, 7 frames)
    public static final int MAP_AIZ_INTRO_EMERALDS_ADDR = 0x364562;

    // Map_AIZIntroWaves - Water wave/spray sprite mappings (0x3750 bytes, 6 frames)
    public static final int MAP_AIZ_INTRO_WAVES_ADDR = 0x22119A;

    // Map_CutsceneKnux - Cutscene Knuckles sprite mappings (0x2F8 bytes)
    public static final int MAP_CUTSCENE_KNUX_ADDR = 0x364016;

    // DPLC_CutsceneKnux - Cutscene Knuckles dynamic pattern load cues (0x162 bytes)
    public static final int DPLC_CUTSCENE_KNUX_ADDR = 0x36430E;

    // --- Palette addresses ---
    // Pal_AIZIntro - AIZ intro zone palette (96 bytes, palette index 10)
    // Loaded via PalPoint table at PAL_POINTERS_ADDR + 10*8
    public static final int PAL_AIZ_INTRO_ADDR = 0x0A8B1C;
    public static final int PAL_AIZ_INTRO_INDEX = 10;
    public static final int PAL_AIZ_INTRO_SIZE = 96;

    // Pal_CutsceneKnux - Knuckles cutscene palette (32 bytes = 16 colors)
    public static final int PAL_CUTSCENE_KNUX_ADDR = 0x066912;

    // Pal_AIZIntroEmeralds - Emerald palette (32 bytes = 16 colors)
    public static final int PAL_AIZ_INTRO_EMERALDS_ADDR = 0x067AAA;

    // PalCycle_SuperSonic - Super Sonic palette cycle data
    // 10 entries x 3 words (6 bytes each) = 60 bytes total
    // Entries 0-5: fade-in, entries 6-9: cycling loop (loop starts at offset $24)
    public static final int PAL_CYCLE_SUPER_SONIC_ADDR = 0x00398E;
    public static final int PAL_CYCLE_SUPER_SONIC_ENTRY_COUNT = 10;
    public static final int PAL_CYCLE_SUPER_SONIC_ENTRY_SIZE = 6; // 3 words

    // --- VRAM art tile destinations ---
    // VDP tile indices where art is loaded in VRAM during the intro
    public static final int ARTTILE_AIZ_INTRO_SPRITES = 0x03D1;  // ArtTile_AIZIntroSprites
    public static final int ARTTILE_AIZ_INTRO_PLANE = 0x0529;    // ArtTile_AIZIntroPlane
    public static final int ARTTILE_AIZ_INTRO_EMERALDS = 0x05B1; // ArtTile_AIZIntroEmeralds
    public static final int ARTTILE_CUTSCENE_KNUX = 0x04DA;      // ArtTile_CutsceneKnux

    // --- Animation scripts (inline data in S3 code space) ---
    // Knuckles cutscene animation scripts
    // Format: dc.b duration, frame0, frame1, ..., $FC (loop) or $F4 (end)
    public static final int ANIM_CUTSCENE_KNUX_WALK_ADDR = 0x0666A9;   // byte_666A9
    public static final int ANIM_CUTSCENE_KNUX_REACT_ADDR = 0x0666AF;  // byte_666AF
    public static final int ANIM_CUTSCENE_KNUX_LOOK_ADDR = 0x0666B9;   // byte_666B9

    // Wave animation script (used for water spray child objects)
    public static final int ANIM_AIZ_INTRO_WAVES_ADDR = 0x067A9B;      // byte_67A9B

    // Emerald sparkle/glow animation scripts
    public static final int ANIM_EMERALD_SPARKLE_ADDR = 0x067A84;      // byte_67A84
    public static final int ANIM_EMERALD_GLOW_ADDR = 0x067A8F;         // byte_67A8F

    // --- Object data tables ---
    // ObjDat3_67A4E - Emerald object attributes (Map ptr, art_tile, size, render_flags)
    public static final int OBJDAT_AIZ_INTRO_EMERALDS_ADDR = 0x067A4E;

    // ChildObjDat tables for creating child objects during intro
    public static final int CHILD_OBJDAT_SUPER_GLOW_ADDR = 0x067A5A;   // ChildObjDat_67A5A
    public static final int CHILD_OBJDAT_PLANE_CHILDREN_ADDR = 0x067A62; // ChildObjDat_67A62
    public static final int CHILD_OBJDAT_WAKE_SPLASH_ADDR = 0x067A70;  // ChildObjDat_67A70
    public static final int CHILD_OBJDAT_CUTSCENE_KNUX_ADDR = 0x067A78; // ChildObjDat_67A78
    public static final int CHILD_OBJDAT_EMERALDS_ADDR = 0x067A7E;     // ChildObjDat_67A7E (7 children)

    // --- Velocity data ---
    // Obj_VelocityIndex - Shared velocity table for object scatter effects
    // Each entry is 4 bytes: dc.w x_vel, y_vel
    // Emerald scatter uses entries at offset $40 (subtypes 0-6 with stride 4 bytes each)
    public static final int OBJ_VELOCITY_INDEX_ADDR = 0x0852F4;
    public static final int EMERALD_SCATTER_VELOCITY_OFFSET = 0x40;

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
