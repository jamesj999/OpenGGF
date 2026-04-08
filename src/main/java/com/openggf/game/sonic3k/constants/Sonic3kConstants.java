package com.openggf.game.sonic3k.constants;

import com.openggf.level.Pattern;

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
    // Safety caps for map allocation. Actual dimensions are derived per-level from
    // the layout header (fgColsPerRow × fgRows) in Sonic3kLevel.loadMap().
    // S3K levels vary widely: ICZ1 is 216×16, DEZ is 122×32, LBZ is 160×24.
    public static final int MAP_LAYERS = 2;
    public static final int MAP_WIDTH = 256;
    public static final int MAP_HEIGHT = 32;

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
    // S2-compatible Knuckles palette for "Knuckles in Sonic 2" lock-on.
    // Indices 2-5 have Knuckles' reds; indices 0-1 and 6-15 are identical
    // to S2's Pal_SonicTails, so title cards, badniks, etc. are unaffected.
    public static final int KNUCKLES_S2_PALETTE_ADDR = 0x060BEA;

    // Pal_WaterKnux - Knuckles water palette patch (42 bytes = 7 zones x 6 bytes)
    // 6 bytes per zone (3 Mega Drive colors), written to Water_palette+$04 (colors 2-4 of line 0)
    // Covers S3 zones only (0=AIZ through 6=LBZ). Verified via RomOffsetFinder find.
    public static final int PAL_WATER_KNUX_ADDR = 0x7A4A;
    public static final int PAL_WATER_KNUX_ENTRY_SIZE = 6; // 3 colors x 2 bytes each
    public static final int PAL_WATER_KNUX_ZONE_COUNT = 7; // Zones 0-6 (AIZ-LBZ)

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
    public static final int ART_NEM_AIZ_SWING_VINE_ADDR = 0x38D8BC;
    public static final int ART_NEM_AIZ_SLIDE_ROPE_ADDR = 0x38DA22;
    public static final int ART_NEM_AIZ_MISC1_ADDR = 0x38DC90;
    public static final int ART_NEM_AIZ_FALLING_LOG_ADDR = 0x38E4D8;
    public static final int ART_NEM_AIZ_CORK_FLOOR_ADDR = 0x38D586;
    public static final int ART_NEM_AIZ_CORK_FLOOR_2_ADDR = 0x38D72A;

    // ===== Falling Log mappings (Obj_AIZFallingLog, ID 0x2D) =====
    // 4 mapping tables: act-specific log body + splash. ROM addresses from LockOn Data.asm.
    public static final int MAP_AIZ_FALLING_LOG_ADDR = 0x22AE30;         // Map_AIZFallingLog (Act 1 log, 1 frame)
    public static final int MAP_AIZ_FALLING_LOG_2_ADDR = 0x22AE20;       // Map_AIZFallingLog2 (Act 2 log, 1 frame)
    public static final int MAP_AIZ_FALLING_LOG_SPLASH_ADDR = 0x22AEB0;  // Map_AIZFallingLogSplash (Act 1 splash, 4 frames)
    public static final int MAP_AIZ_FALLING_LOG_SPLASH_2_ADDR = 0x22AE40; // Map_AIZFallingLogSplash2 (Act 2 splash, 4 frames)

    // ===== Spiked Log mappings (Obj_AIZSpikedLog, ID 0x2E) =====
    // Map_AIZSpikedLog: 16 frames (rotating spiked log platform).
    // art_tile = make_art_tile(ArtTile_AIZMisc2, 2, 0) → base tile 0x2E9, palette 2.
    public static final int MAP_AIZ_SPIKED_LOG_ADDR = 0x22B0F0;

    // ===== Cork Floor mappings (Obj_CorkFloor, ID 0x2A) =====
    // Each zone has its own mapping table: frame 0 = intact, frame 1 = broken fragments.
    // Addresses derived from frame labels in LockOn Data.asm sequential includes.
    public static final int MAP_AIZ_CORK_FLOOR_ADDR = 0x229B60;   // Map_AIZCorkFloor (2 frames, 6/12 pieces)
    public static final int MAP_AIZ_CORK_FLOOR_2_ADDR = 0x229BD4; // Map_AIZCorkFloor2 (2 frames, 6/12 pieces)
    public static final int MAP_CNZ_CORK_FLOOR_ADDR = 0x229C48;   // Map_CNZCorkFloor (2 frames, 8/16 pieces)
    public static final int MAP_ICZ_CORK_FLOOR_ADDR = 0x229CE0;   // Map_ICZCorkFloor (12 frames: 6 intact pairs + break frames)
    public static final int MAP_LBZ_CORK_FLOOR_ADDR = 0x229EE8;   // Map_LBZCorkFloor (2 frames, 8/16 pieces)
    public static final int MAP_FBZ_CORK_FLOOR_ADDR = 0x2A920;    // Map_FBZCorkFloor (2 frames, 2/4 pieces, in sonic3k.asm)

    // ===== Breakable Wall mappings (Obj_BreakableWall, ID 0x0D) =====
    // Each zone has its own mapping table: even frames = intact, odd frames = broken fragments.
    // S3 half (>= 0x200000) addresses for S3-era zones, S&K half for S&K-era zones.
    public static final int MAP_AIZ_BREAKABLE_WALL_ADDR = 0x21FD52;     // Map_AIZBreakableWall (6 frames)
    public static final int MAP_HCZ_BREAKABLE_WALL_ADDR = 0x21FFD8;     // Map_HCZBreakableWall (4 frames)
    public static final int MAP_MGZ_BREAKABLE_WALL_ADDR = 0x21FF18;     // Map_MGZBreakableWall (4 frames)
    public static final int MAP_CNZ_SOZ_BREAKABLE_WALL_ADDR = 0x021ABA; // Map_CNZSOZBreakableWall (6 frames)
    public static final int MAP_LBZ_BREAKABLE_WALL_ADDR = 0x22005E;     // Map_LBZBreakableWall (2 frames)
    public static final int MAP_MHZ_BREAKABLE_WALL_ADDR = 0x021BCE;     // Map_MHZBreakableWall (2 frames)
    public static final int MAP_LRZ_BREAKABLE_WALL_ADDR = 0x021C1E;     // Map_LRZBreakableWall (2 frames)

    // ArtTile constants for breakable walls (from sonic3k.constants.asm)
    public static final int ARTTILE_HCZ2_KNUX_WALL = 0x0350;   // ArtTile_HCZ2KnuxWall
    public static final int ARTTILE_CNZ_MISC = 0x0351;          // ArtTile_CNZMisc
    public static final int ARTTILE_LBZ2_MISC = 0x02EA;         // ArtTile_LBZ2Misc
    public static final int ARTTILE_MHZ_MISC = 0x0347;          // ArtTile_MHZMisc
    public static final int ARTTILE_SOZ_MISC = 0x03C9;          // ArtTile_SOZMisc

    // ===== HCZ Breakable Bar mappings (Obj_HCZBreakableBar, ID 0x36) =====
    // LockOn data (assembled into S3 half of combined ROM — no S&K-side copy exists)
    public static final int MAP_HCZ_BREAKABLE_BAR_ADDR = 0x21CDCA; // Map_HCZBreakableBar (8 frames)

    // ===== HCZ Water Rush (Obj_HCZWaterRush, ID 0x37) =====
    // ArtNem_HCZWaterRush: Nemesis compressed, 2560 bytes decompressed (80 tiles)
    // Loaded via PLC_0E. LockOn data (S3 half only — no S&K-side copy).
    public static final int ART_NEM_HCZ_WATER_RUSH_ADDR = 0x390348;
    // ArtTile_HCZWaterRush = $037A (from sonic3k.constants.asm)
    public static final int ARTTILE_HCZ_WATER_RUSH = 0x037A;
    // WaterRushBlock uses ArtTile_HCZMisc + $A = 0x03D4 (level-loaded art)
    public static final int ARTTILE_HCZ_WATER_RUSH_BLOCK = 0x03CA + 0xA; // 0x03D4

    // ===== HCZ/CGZ Fan (Obj_HCZCGZFan, ID 0x38) =====
    // Map_HCZFan: 5 frames, 3 pieces each. LockOn data (S3 half).
    public static final int MAP_HCZ_FAN_ADDR = 0x22F3AA;
    // Fan uses ArtTile_HCZMisc + $41 = $040B, palette 1
    public static final int ARTTILE_HCZ_FAN = 0x03CA + 0x41; // ArtTile_HCZMisc + $41 = 0x040B

    // ===== Floating Platform mappings (Obj_FloatingPlatform, ID 0x51) =====
    public static final int MAP_AIZ_FLOATING_PLATFORM_ADDR = 0x256A2; // Map_AIZFloatingPlatform (1 frame, 4 pieces)
    public static final int MAP_HCZ_FLOATING_PLATFORM_ADDR = 0x25688; // Map_HCZFloatingPlatform (2 frames, 2/1 pieces)
    public static final int MAP_HCZ_WAVE_SPLASH_ADDR = 0x01F2CE;     // Map_HCZWaveSplash (7 frames, S&K side)
    public static final int ART_NEM_HCZ_WAVE_SPLASH_ADDR = 0x38FBB4; // ArtNem_HCZWaveSplash (Nemesis, 16 tiles)
    public static final int MAP_MGZ_FLOATING_PLATFORM_ADDR = 0x25654; // Map_MGZFloatingPlatform (1 frame, 8 pieces)

    // ===== ArtTile constants from sonic3k.constants.asm =====
    public static final int ARTTILE_AIZ_FLOATING_PLATFORM = 0x03F7;
    public static final int ARTTILE_AIZ2_FLOATING_PLATFORM = 0x0440;
    public static final int ARTTILE_HCZ_MISC = 0x03CA;
    public static final int ARTTILE_CNZ_PLATFORM = 0x0430;
    public static final int ARTTILE_ICZ_MISC1 = 0x03B6;
    public static final int ARTTILE_LBZ_MISC = 0x03C3;
    public static final int ARTTILE_FBZ_MISC = 0x0379;

    // ===== HUD Art =====
    // ArtUnc_HUDDigits - Score/Time/Ring digits (0-9, colon, E)
    // 768 bytes = 24 tiles (12 characters x 2 tiles each, column-major 8x16)
    // Verified via RomOffsetFinder binary match
    public static final int ART_UNC_HUD_DIGITS_ADDR = 0xE18A;
    public static final int ART_UNC_HUD_DIGITS_SIZE = 768;

    // ArtUnc_LivesDigits - Small 8x8 digits (0-9) for lives counter
    // 320 bytes = 10 tiles
    // Verified via RomOffsetFinder binary match
    public static final int ART_UNC_LIVES_DIGITS_ADDR = 0xE48A;
    public static final int ART_UNC_LIVES_DIGITS_SIZE = 320;

    // Touch_Sizes table: 58 entries of 2 bytes (width, height radius)
    // sonic3k.asm line 20713, verified via ROM binary search
    public static final int TOUCH_SIZES_ADDR = 0x00FF62;
    public static final int TOUCH_SIZES_COUNT = 58;

    // ===== Pattern Load Cue (PLC) tables =====
    // Offs_PLC: 2-byte offset table, 124 entries (IDs 0x00-0x7B)
    // Each word is an offset from Offs_PLC to the PLC data block.
    // PLC data block: dc.w count-1, then count × 6-byte entries:
    //   dc.l nemesis_rom_addr, dc.w vram_dest_bytes (tile_index * 32)
    // Verified by ROM binary search for PLC_01 fingerprint: 0x09249E - 0x112 = 0x09238C
    public static final int OFFS_PLC_ADDR = 0x09238C;
    public static final int OFFS_PLC_ENTRY_COUNT = 124;
    public static final int PLC_ENTRY_SIZE = 6; // 4-byte ROM addr + 2-byte VRAM dest

    public static final int ART_NEM_SONIC_LIFE_ICON_ADDR = 0x190D34;
    public static final int ART_NEM_KNUCKLES_LIFE_ICON_ADDR = 0x190E4C; // ArtNem_KnucklesLifeIcon
    public static final int ART_NEM_TAILS_LIFE_ICON_ADDR = 0x15CFFE;   // ArtNem_TailsLifeIcon (S3 portion)
    public static final int ART_NEM_MONITORS_ADDR = 0x190F4A;
    public static final int ART_NEM_EXPLOSION_ADDR = 0x19200A;
    public static final int ART_NEM_BUBBLES_ADDR = 0x191B46;
    public static final int ART_NEM_RING_HUD_TEXT_ADDR = 0x192AEE;
    public static final int ART_NEM_ENEMY_PTS_STARPOST_ADDR = 0x192D2A;
    public static final int ART_NEM_STARPOST_ADDR = 0x35D8A2; // Dedicated StarPost art (20 tiles)
    // ArtKosM_StarPost_Stars1/Stars2/Stars3 - bonus star variants (KosinskiM, 3 tiles each)
    // Loaded dynamically at checkpoint activation based on ring count.
    // Stars1=Blue (Glowing Spheres), Stars2=Red (Slot Machine), Stars3=Yellow (Gumball)
    public static final int ART_KOSM_STARPOST_STARS1_ADDR = 0x187B8A; // Blue stars
    public static final int ART_KOSM_STARPOST_STARS2_ADDR = 0x187BEC; // Red stars
    public static final int ART_KOSM_STARPOST_STARS3_ADDR = 0x187C4E; // Yellow stars
    public static final int ART_NEM_SPIKES_SPRINGS_ADDR = 0x1927FE;

    // Animal art (Nemesis compressed, per-type)
    public static final int ART_NEM_SEAL_ADDR = 0x192F6E;
    public static final int ART_NEM_PIG_ADDR = 0x19308A;
    public static final int ART_NEM_BLUE_FLICKY_ADDR = 0x1931D6;
    public static final int ART_NEM_CHICKEN_ADDR = 0x193308;
    public static final int ART_NEM_PENGUIN_ADDR = 0x193456;
    public static final int ART_NEM_SQUIRREL_ADDR = 0x1935A8;
    public static final int ART_NEM_RABBIT_ADDR = 0x193706;

    // VRAM tile index for SpikesSprings shared art (spikes start at +8)
    public static final int ARTTILE_SPIKES_SPRINGS = 0x0494;
    // VRAM tile index for diagonal spring art (separate from SpikesSprings)
    public static final int ARTTILE_DIAGONAL_SPRING = 0x043A;

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
    public static final int ARTTILE_AIZ_SLIDE_ROPE = 0x0324;
    public static final int ARTTILE_AIZ_MISC1 = 0x0333;
    public static final int ARTTILE_AIZ_MISC2 = 0x02E9;
    public static final int ARTTILE_LRZ2_MISC = 0x040D;
    public static final int ARTTILE_AIZ_FALLING_LOG = 0x03CF;
    public static final int ARTTILE_AIZ_SWING_VINE = 0x041B;
    public static final int ARTTILE_BUBBLES = 0x045C;
    public static final int ARTTILE_MONITORS = 0x04C4;
    public static final int ARTTILE_STARPOST = 0x05E4;
    public static final int ARTTILE_RING = 0x06BC;
    public static final int ARTTILE_PLAYER_LIFE_ICON = 0x07D4;

    // Map_StarPost - StarPost sprite mappings (5 frames)
    // Frame 0: pole + red ball (idle), 1: pole only, 2: star ball, 3: head, 4: pole + blue ball
    public static final int MAP_STARPOST_ADDR = 0x2D348;

    // Map_StarpostStars - StarPost bonus star mappings (3 frames)
    public static final int MAP_STARPOST_STARS_ADDR = 0x2D3AA;

    // Map_Animals1-5 - Animal sprite mappings (3 frames each, 6-byte pieces)
    // Each set covers a different body shape: 1=A(Chicken/Eagle/Flicky), 2=B(Squirrel/Mouse/Monkey/Turtle/Bear),
    // 3=C(Pig), 4=D(Seal), 5=E(Rabbit/Penguin)
    public static final int MAP_ANIMALS1_ADDR = 0x02CEBA;
    public static final int MAP_ANIMALS2_ADDR = 0x02CED8;
    public static final int MAP_ANIMALS3_ADDR = 0x02CEF6;
    public static final int MAP_ANIMALS4_ADDR = 0x02CF14;
    public static final int MAP_ANIMALS5_ADDR = 0x02CF32;

    // Map_EnemyScore - Enemy points popup mappings (7 frames: 10,20,50,100,1,200,500)
    public static final int MAP_ENEMY_SCORE_ADDR = 0x02CF50;

    // VRAM tile offsets for animal art
    // ArtTile_Animals1 = $0580, ArtTile_Animals2 = $0592, difference = 18 tiles
    public static final int S3K_ANIMAL_TILE_OFFSET = 0x12; // 18 tiles between animal banks

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

    // ===== Player Sprite Art =====
    // ArtUnc_Sonic - Main Sonic sprite art (uncompressed)
    // 131,296 bytes = 4103 tiles x 32 bytes per tile
    public static final int ART_UNC_SONIC_ADDR = 0x100000;
    public static final int ART_UNC_SONIC_SIZE = 131296;
    public static final int ART_UNC_SONIC_TILE_COUNT = 4103;

    // ArtUnc_Sonic_Extra - Super Sonic / extra art frames (uncompressed)
    // 15,520 bytes = 485 tiles x 32 bytes per tile
    // Used for mapping frames >= SONIC_EXTRA_ART_FRAME_THRESHOLD
    public static final int ART_UNC_SONIC_EXTRA_ADDR = 0x140060;
    public static final int ART_UNC_SONIC_EXTRA_SIZE = 15520;
    public static final int ART_UNC_SONIC_EXTRA_TILE_COUNT = 485;

    // Map_Sonic - Sonic sprite mappings (6-byte pieces, no 2P tile word)
    // Combined 1P+2P offset table: 502 entries (first 251 = 1P, second 251 = 2P).
    // Machine code at ROM 0x010B16: move.l #$146620, mappings(a0)
    public static final int MAP_SONIC_ADDR = 0x146620;

    // PLC_Sonic - Sonic dynamic pattern load cues
    // Combined 1P+2P offset table: 502 entries (first 251 = 1P, second 251 = 2P).
    // Same DPLC format as S2 (offset table + per-frame tile load request lists)
    public static final int DPLC_SONIC_ADDR = 0x148182;

    // ArtTile_Player_1 - VRAM base tile index for Sonic
    public static final int ART_TILE_SONIC = 0x0680;

    // AniSonic_ - Animation script table (36 entries: 0x00-0x23)
    public static final int SONIC_ANIM_DATA_ADDR = 0x012AA6;
    public static final int SONIC_ANIM_SCRIPT_COUNT = 36;

    // AniSuperSonic - Super Sonic animation table (32 entries: 0x00-0x1F)
    // Entries with offsets >= 0x8000 are back-references to regular AniSonic scripts
    public static final int SUPER_SONIC_ANIM_DATA_ADDR = 0x012C3A;
    public static final int SUPER_SONIC_ANIM_SCRIPT_COUNT = 32;

    // Extra art frame threshold - mapping frames >= this use Extra art tiles
    // ROM: LoadSonicDynPLC checks frame >= $DA for Extra art
    public static final int SONIC_EXTRA_ART_FRAME_THRESHOLD = 0xDA;

    // Map_SuperSonic - Super Sonic sprite mappings (standalone 251-entry table)
    // Immediately follows Map_Sonic_'s 251 1P offset entries in ROM.
    // First word = 0x01F6 (251 entries), standard parser works without trimming.
    public static final int MAP_SUPER_SONIC_ADDR = 0x146816; // MAP_SONIC_ADDR + 251*2

    // PLC_SuperSonic - Super Sonic dynamic pattern load cues (standalone 251-entry table)
    // Immediately follows PLC_Sonic_'s 251 1P offset entries in ROM.
    // First word is a frame data offset (NOT entry count), so explicit count is required.
    public static final int DPLC_SUPER_SONIC_ADDR = 0x148378; // DPLC_SONIC_ADDR + 251*2

    // Super Sonic table entry count (same frame indexing as normal Sonic)
    public static final int SUPER_SONIC_FRAME_COUNT = 251;

    // Super Sonic constants
    public static final int SUPER_SONIC_RING_DRAIN_INTERVAL = 60;
    public static final int SUPER_SONIC_MIN_RINGS = 50;

    // ===== Tails Player Sprite Art =====
    // ArtUnc_Tails - Main Tails body art (uncompressed, S3 ROM portion)
    public static final int ART_UNC_TAILS_ADDR = 0x3200E0;
    public static final int ART_UNC_TAILS_SIZE = 0x16540;    // 91,456 bytes = 2858 tiles

    // ArtUnc_Tails_Extra - Super Tails / extra art frames (uncompressed, S&K portion)
    public static final int ART_UNC_TAILS_EXTRA_ADDR = 0x143D00;
    public static final int ART_UNC_TAILS_EXTRA_SIZE = 0x2920;  // 10,528 bytes = 329 tiles

    // Map_Tails - Tails body mappings (6-byte pieces)
    // Combined 1P+2P offset table: 502 entries (first 251 = 1P, second 251 = 2P).
    public static final int MAP_TAILS_ADDR = 0x148EB8;

    // PLC_Tails - Tails body dynamic pattern load cues
    // Combined 1P+2P offset table: 502 entries (first 251 = 1P, second 251 = 2P).
    public static final int DPLC_TAILS_ADDR = 0x14A08A;

    // ArtTile_Player_2 - VRAM base tile for Tails (from sonic3k.constants.asm)
    public static final int ART_TILE_TAILS = 0x06A0;

    // AniTails - Animation script table (42 entries: 0x00-0x29)
    // Verified by ROM pattern search + lea ($15AB0).l,a1 instruction at 0x015864
    public static final int TAILS_ANIM_DATA_ADDR = 0x015AB0;
    public static final int TAILS_ANIM_SCRIPT_COUNT = 42;

    // Extra art frame threshold - frames >= this use Extra art tiles
    // ROM: Tails_Load_PLC checks frame index >= $D1
    public static final int TAILS_EXTRA_ART_FRAME_THRESHOLD = 0xD1;

    // ArtTile_Player_2_Tail - VRAM base tile for Tails tail appendage (Obj05)
    // From sonic3k.constants.asm line 1409
    public static final int ART_TILE_TAILS_TAIL = 0x06B0;

    // ===== Tails Tail Appendage (separate sprite object) =====
    public static final int ART_UNC_TAILS_TAIL_ADDR = 0x336620;
    public static final int ART_UNC_TAILS_TAIL_SIZE = 0x1160;   // 4,448 bytes = 139 tiles
    public static final int MAP_TAILS_TAIL_ADDR = 0x344BB8;
    public static final int DPLC_TAILS_TAIL_ADDR = 0x344D74;

    // ===== Knuckles Player Sprite Art =====
    // ArtUnc_Knux - Main Knuckles body art (uncompressed, S&K ROM portion)
    // Split/sk.txt: 0x1200E0 to 0x140060
    public static final int ART_UNC_KNUCKLES_ADDR = 0x1200E0;
    public static final int ART_UNC_KNUCKLES_SIZE = 0x1FF80;    // 130,944 bytes = 4092 tiles

    // Map_Knuckles - Knuckles body mappings (6-byte pieces, standalone 251-entry table)
    // Unlike Sonic/Tails, Knuckles maps are NOT combined 1P+2P.
    public static final int MAP_KNUCKLES_ADDR = 0x14A8D6;

    // PLC_Knuckles - Knuckles body dynamic pattern load cues (standalone 251-entry table)
    public static final int DPLC_KNUCKLES_ADDR = 0x14BD0A;

    // ArtTile_Player_1 - VRAM base tile for Knuckles (same as Sonic — single-player uses one slot)
    public static final int ART_TILE_KNUCKLES = ART_TILE_SONIC; // 0x0680

    // AniKnuckles_ - Animation script table (37 entries: 0x00-0x24)
    // Separate from AniSonic_ — Knuckles has his own animation frame data
    public static final int KNUCKLES_ANIM_DATA_ADDR = 0x017EF4;
    public static final int KNUCKLES_ANIM_SCRIPT_COUNT = 37;

    // ===== Animated palette cycling data (AnPal tables) =====
    // AIZ1 waterfall (palette 2, colors 11-14): 4 frames x 8 bytes = 32 bytes
    public static final int ANPAL_AIZ1_1_ADDR = 0x002AF6;
    public static final int ANPAL_AIZ1_1_SIZE = 32;
    // AIZ1 secondary water (palette 3, colors 12-14): 8 frames x 6 bytes = 48 bytes
    public static final int ANPAL_AIZ1_2_ADDR = 0x002BF6;
    public static final int ANPAL_AIZ1_2_SIZE = 48;
    // AIZ1 fire mode (palette 3, colors 2-5): 10 frames x 8 bytes = 80 bytes
    public static final int ANPAL_AIZ1_3_ADDR = 0x002B16;
    public static final int ANPAL_AIZ1_3_SIZE = 80;
    // AIZ1 fire mode (palette 3, colors 13-15): 10 frames x 6 bytes = 60 bytes
    public static final int ANPAL_AIZ1_4_ADDR = 0x002B96;
    public static final int ANPAL_AIZ1_4_SIZE = 60;
    // AIZ2 water (palette 3, colors 12-15): 4 frames x 8 bytes = 32 bytes
    public static final int ANPAL_AIZ2_1_ADDR = 0x002C26;
    public static final int ANPAL_AIZ2_1_SIZE = 32;
    // AIZ2 water trickle pre-fire (pal 2: colors 4,8; pal 3: color 11): 8 frames x 6 bytes = 48 bytes
    public static final int ANPAL_AIZ2_2_ADDR = 0x002C46;
    public static final int ANPAL_AIZ2_2_SIZE = 48;
    // AIZ2 water trickle post-fire (same targets): 8 frames x 6 bytes = 48 bytes
    public static final int ANPAL_AIZ2_3_ADDR = 0x002C76;
    public static final int ANPAL_AIZ2_3_SIZE = 48;
    // AIZ2 torch glow pre-fire (palette 3, color 1): 26 frames x 2 bytes = 52 bytes
    public static final int ANPAL_AIZ2_4_ADDR = 0x002CA6;
    public static final int ANPAL_AIZ2_4_SIZE = 52;
    // AIZ2 torch glow post-fire (palette 3, color 1): 26 frames x 2 bytes = 52 bytes
    public static final int ANPAL_AIZ2_5_ADDR = 0x002CD8;
    public static final int ANPAL_AIZ2_5_SIZE = 52;
    // HCZ1 water animation (palette 2, colors 3-6): 4 frames x 8 bytes = 32 bytes
    // ROM: AnPal_PalHCZ1 — verified by ROM binary search for 0EC8 0EC0 0EA0 0E80 full sequence
    public static final int ANPAL_HCZ1_ADDR = 0x002D0E;
    public static final int ANPAL_HCZ1_SIZE = 32;
    // CNZ bumpers/teacups (palette 3, colors 9-11): 16 frames x 6 bytes = 96 bytes
    // Verified by ROM binary search (pattern: 00 00 00 66 00 EE)
    public static final int ANPAL_CNZ_1_ADDR = 0x002D2E;
    public static final int ANPAL_CNZ_1_SIZE = 96;
    // CNZ bumpers water table (palette 3, colors 9-11, unused): 16 frames x 6 bytes = 96 bytes
    public static final int ANPAL_CNZ_2_ADDR = 0x002E82;
    public static final int ANPAL_CNZ_2_SIZE = 96;
    // CNZ background (palette 2, colors 9-11): 30 frames x 6 bytes = 180 bytes
    // Verified by ROM binary search (pattern: 0E 20 00 8A 0C 0E)
    public static final int ANPAL_CNZ_3_ADDR = 0x002D8E;
    public static final int ANPAL_CNZ_3_SIZE = 180;
    // CNZ background water table (palette 2, colors 9-11, unused): 30 frames x 6 bytes = 180 bytes
    public static final int ANPAL_CNZ_4_ADDR = 0x002EE2;
    public static final int ANPAL_CNZ_4_SIZE = 180;
    // CNZ tertiary (palette 2, colors 7-8): 16 frames x 4 bytes = 64 bytes
    // Verified by ROM binary search (pattern: 02 E0 0E CE 04 E2 0E AC)
    public static final int ANPAL_CNZ_5_ADDR = 0x002E42;
    public static final int ANPAL_CNZ_5_SIZE = 64;
    // ICZ geyser/ice (palette 2, colors 14-15): 16 frames x 4 bytes = 64 bytes
    // ROM: AnPal_PalICZ_1 — verified 0x002FD6 by binary search (0E 62 0E 20 ...)
    public static final int ANPAL_ICZ_1_ADDR = 0x002FD6;
    public static final int ANPAL_ICZ_1_SIZE = 64;
    // ICZ conditional ch2 (palette 3, colors 14-15): 18 frames x 4 bytes = 72 bytes
    // ROM: AnPal_PalICZ_2 — verified 0x003016 by binary search (0E 06 0E 08 ...)
    public static final int ANPAL_ICZ_2_ADDR = 0x003016;
    public static final int ANPAL_ICZ_2_SIZE = 72;
    // ICZ conditional ch3 (palette 3, colors 12-13): 6 frames x 4 bytes = 24 bytes
    // ROM: AnPal_PalICZ_3 — verified 0x00305E by binary search (08 40 0E EA ...)
    public static final int ANPAL_ICZ_3_ADDR = 0x00305E;
    public static final int ANPAL_ICZ_3_SIZE = 24;
    // ICZ always-on ch4 (palette 2, colors 12-13): 16 frames x 4 bytes = 64 bytes
    // ROM: AnPal_PalICZ_4 — verified 0x003076 by binary search (00 E8 0C EC ...)
    public static final int ANPAL_ICZ_4_ADDR = 0x003076;
    public static final int ANPAL_ICZ_4_SIZE = 64;
    // LBZ Act 1 (palette 2, colors 8-10): 3 frames x 6 bytes = 18 bytes
    // AnPal_PalLBZ1 — verified by ROM binary search (0x0030B6)
    public static final int ANPAL_LBZ1_ADDR = 0x0030B6;
    public static final int ANPAL_LBZ1_SIZE = 18;
    // LBZ Act 2 (palette 2, colors 8-10): 3 frames x 6 bytes = 18 bytes
    // AnPal_PalLBZ2 — immediately follows LBZ1 at 0x0030C8
    public static final int ANPAL_LBZ2_ADDR = 0x0030C8;
    public static final int ANPAL_LBZ2_SIZE = 18;
    // LRZ shared (both acts) channel A: palette 2 colors 1-4 (2 longwords), 16 frames x 8 bytes = 128 bytes
    // counter step +8, wraps at 0x80. Verified by ROM binary search (sonic3k.asm AnPal_PalLRZ12_1).
    public static final int ANPAL_LRZ12_1_ADDR = 0x00327E;
    public static final int ANPAL_LRZ12_1_SIZE = 128;
    // LRZ shared (both acts) channel B: palette 3 colors 1-2 (1 longword), 7 frames x 4 bytes = 28 bytes
    // counter step +4, wraps at 0x1C. Verified by ROM binary search (sonic3k.asm AnPal_PalLRZ12_2).
    public static final int ANPAL_LRZ12_2_ADDR = 0x0032FE;
    public static final int ANPAL_LRZ12_2_SIZE = 28;
    // LRZ Act 1 channel C: palette 2 color 11 (1 word), 17 frames x 2 bytes = 34 bytes
    // counter step +2, wraps at 0x22. Verified by ROM binary search (sonic3k.asm AnPal_PalLRZ1_3).
    public static final int ANPAL_LRZ1_3_ADDR = 0x003322;
    public static final int ANPAL_LRZ1_3_SIZE = 34;
    // LRZ Act 2 channel D: palette 3 colors 11-14 (2 longwords), 32 frames x 8 bytes = 256 bytes
    // counter step +8, wraps at 0x100. Verified by ROM binary search (sonic3k.asm AnPal_PalLRZ2_3).
    // ROM bug: writes same 2 colors twice (uses (a0,d0.w) twice instead of 4(a0,d0.w)) — replicated faithfully.
    public static final int ANPAL_LRZ2_3_ADDR = 0x003344;
    public static final int ANPAL_LRZ2_3_SIZE = 256;
    // BPZ balloons (palette 2, colors 13-15): 3 frames x 6 bytes = 18 bytes
    // Verified by ROM binary search for pattern 00EE 00AE 006C 00AE 006E 00EE 006E 00EE 00AE
    public static final int ANPAL_BPZ_1_ADDR = 0x0034CC;
    public static final int ANPAL_BPZ_1_SIZE = 18;
    // BPZ background (palette 3, colors 2-4): 21 frames x 6 bytes = 126 bytes
    // Immediately follows ANPAL_BPZ_1 in ROM
    public static final int ANPAL_BPZ_2_ADDR = 0x0034DE;
    public static final int ANPAL_BPZ_2_SIZE = 126;
    // CGZ light animation (palette 2, colors 2-5): 10 frames x 8 bytes = 80 bytes
    // AnPal_PalCGZ — verified by ROM binary search at 0x00355C
    public static final int ANPAL_CGZ_ADDR = 0x00355C;
    public static final int ANPAL_CGZ_SIZE = 80;
    // EMZ emerald glow (palette 2, color 14): 30 frames x 2 bytes = 60 bytes
    // ROM reads as 4(a0,d0.w); counter steps +2, wraps at 0x3C. Slice 64 bytes to cover full range.
    public static final int ANPAL_EMZ1_ADDR = 0x0035AC;
    public static final int ANPAL_EMZ1_SIZE = 64;
    // EMZ background (palette 3, colors 9-10): 13 frames x 4 bytes = 52 bytes
    public static final int ANPAL_EMZ2_ADDR = 0x0035E8;
    public static final int ANPAL_EMZ2_SIZE = 52;

    // ===== Animated pattern scripts (AniPLC tables) =====
    // AniPLC_AIZ1: 3 scripts (waterfall cascade, waterfall offset, wave ripple)
    // Verified by ROM binary search for frame data pattern (5CC0 090C 3C4F 3005)
    public static final int ANIPLC_AIZ1_ADDR = 0x028750;

    // AniPLC_AIZ2: 5 scripts (fire/explosion, waterfall cascade, waterfall offset, fire small, fire large)
    // Verified by ROM binary search for frame data pattern (1660 0417 0017 2E45)
    public static final int ANIPLC_AIZ2_ADDR = 0x02879C;

    // AniPLC_HCZ1: 2 scripts (background bubble column, water shimmer)
    // Verified against S&K ROM bytes immediately following AniPLC_AIZ2 at 0x0287F4.
    public static final int ANIPLC_HCZ1_ADDR = 0x0287F4;

    // AniPLC_HCZ2: 2 scripts (waterfall stream, water shimmer)
    // Verified against S&K ROM bytes immediately following AniPLC_HCZ1 at 0x02882C.
    public static final int ANIPLC_HCZ2_ADDR = 0x02882C;

    // ArtUnc_AniAIZ2_FirstTree: Static tree art for AIZ2 near-spawn area (camera X < 0x1C0)
    // 0x460 bytes = 35 tiles, loaded to VRAM tile $0CA
    // Verified by move.l #addr,d1 instruction at ROM 0x02786A
    public static final int ART_UNC_AIZ2_FIRST_TREE_ADDR = 0x2A5880;
    public static final int ART_UNC_AIZ2_FIRST_TREE_SIZE = 0x460;
    public static final int ART_UNC_AIZ2_FIRST_TREE_DEST_TILE = 0x0CA;

    // HCZ1 startup background repair strips.
    // ROM startup with Events_bg+$10 == 0 calls AniHCZ_FixLowerBG, DMAing these
    // two 12-tile rows into VRAM $2F4 and $300 before HCZ background rendering.
    public static final int HCZ_WATERLINE_SCROLL_DATA_ADDR = 0x26D000;
    public static final int HCZ_WATERLINE_SCROLL_DATA_SIZE = 0x2460;
    public static final int ART_UNC_HCZ1_WATERLINE_BELOW1_ADDR = 0x2A6A60;
    public static final int ART_UNC_FIX_HCZ1_UPPER_BG1_ADDR = 0x2A6BE0;
    public static final int ART_UNC_HCZ1_WATERLINE_ABOVE1_ADDR = 0x2A6D60;
    public static final int ART_UNC_FIX_HCZ1_LOWER_BG1_ADDR = 0x2A6EE0;
    public static final int ART_UNC_HCZ1_WATERLINE_BELOW2_ADDR = 0x2A7060;
    public static final int ART_UNC_FIX_HCZ1_UPPER_BG2_ADDR = 0x2A71E0;
    public static final int ART_UNC_HCZ1_WATERLINE_ABOVE2_ADDR = 0x2A7360;
    public static final int ART_UNC_FIX_HCZ1_LOWER_BG2_ADDR = 0x2A74E0;
    public static final int ART_UNC_FIX_HCZ1_BG_STRIP_SIZE = 0x180;

    // HCZ2 direct background DMA sources, driven by HCZ2_Deform deltas.
    public static final int ART_UNC_HCZ2_SMALL_BG_LINE_ADDR = 0x2A87A0;
    public static final int ART_UNC_HCZ2_SMALL_BG_LINE_SIZE = 0x400;
    public static final int ART_UNC_HCZ2_2_ADDR = 0x2A8BA0;
    public static final int ART_UNC_HCZ2_2_SIZE = 0x800;
    public static final int ART_UNC_HCZ2_3_ADDR = 0x2A93A0;
    public static final int ART_UNC_HCZ2_3_SIZE = 0x1000;
    public static final int ART_UNC_HCZ2_4_ADDR = 0x2AA3A0;
    public static final int ART_UNC_HCZ2_4_SIZE = 0x3000;

    // ===== Title Screen Art (Kosinski compressed, S3 lock-on data) =====
    // Sonic animation frames — frames 1-7 share Sonic1 art with different palettes/mappings
    public static final int ART_KOS_TITLE_SONIC1_ADDR = 0x350D26;   // ArtKos_S3TitleSonic1 (frames 1-7)
    public static final int ART_KOS_TITLE_SONIC8_ADDR = 0x351C86;   // ArtKos_S3TitleSonic8
    public static final int ART_KOS_TITLE_SONIC9_ADDR = 0x3542E6;   // ArtKos_S3TitleSonic9
    public static final int ART_KOS_TITLE_SONIC_A_ADDR = 0x3565E6;  // ArtKos_S3TitleSonicA
    public static final int ART_KOS_TITLE_SONIC_B_ADDR = 0x357AC6;  // ArtKos_S3TitleSonicB
    public static final int ART_KOS_TITLE_SONIC_C_ADDR = 0x358DE6;  // ArtKos_S3TitleSonicC
    public static final int ART_KOS_TITLE_SONIC_D_ADDR = 0x359FC6;  // ArtKos_S3TitleSonicD (final frame)

    // Title screen sprite art (Nemesis compressed)
    public static final int ART_NEM_TITLE_BANNER_ADDR = 0x35026C;       // ArtNem_Title_S3Banner (VRAM $A000, tile $500)
    public static final int ART_NEM_TITLE_SCREEN_TEXT_ADDR = 0x4D2A;     // ArtNem_TitleScreenText (VRAM $D000, tile $680)
    public static final int ART_NEM_TITLE_SONIC_SPRITES_ADDR = 0x2C49CC; // ArtNem_Title_SonicSprites (VRAM $8000, tile $400)
    public static final int ART_NEM_TITLE_AND_KNUCKLES_ADDR = 0xD6498;   // ArtNem_Title_ANDKnuckles (VRAM $9800, tile $4C0)

    // Title screen Enigma plane mappings (S3 lock-on data)
    public static final int MAP_ENI_TITLE_SONIC1_ADDR = 0x34F6A0;  // MapEni_S3TitleSonic1
    public static final int MAP_ENI_TITLE_SONIC2_ADDR = 0x34F75C;  // MapEni_S3TitleSonic2
    public static final int MAP_ENI_TITLE_SONIC3_ADDR = 0x34F81E;  // MapEni_S3TitleSonic3
    public static final int MAP_ENI_TITLE_SONIC4_ADDR = 0x34F8E2;  // MapEni_S3TitleSonic4
    public static final int MAP_ENI_TITLE_SONIC5_ADDR = 0x34F9A6;  // MapEni_S3TitleSonic5
    public static final int MAP_ENI_TITLE_SONIC6_ADDR = 0x34FA6A;  // MapEni_S3TitleSonic6
    public static final int MAP_ENI_TITLE_SONIC7_ADDR = 0x34FB30;  // MapEni_S3TitleSonic7
    public static final int MAP_ENI_TITLE_SONIC8_ADDR = 0x34FC2E;  // MapEni_S3TitleSonic8
    public static final int MAP_ENI_TITLE_SONIC9_ADDR = 0x34FD18;  // MapEni_S3TitleSonic9
    public static final int MAP_ENI_TITLE_SONIC_A_ADDR = 0x34FDE6; // MapEni_S3TitleSonicA
    public static final int MAP_ENI_TITLE_SONIC_B_ADDR = 0x34FEAA; // MapEni_S3TitleSonicB
    public static final int MAP_ENI_TITLE_SONIC_C_ADDR = 0x34FF48; // MapEni_S3TitleSonicC
    public static final int MAP_ENI_TITLE_SONIC_D_ADDR = 0x350018; // MapEni_S3TitleSonicD (final frame)
    public static final int MAP_ENI_TITLE_BG_ADDR = 0x350112;      // MapEni_S3TitleBg (Plane B background)

    // Title screen palettes (uncompressed, in S&K code section)
    public static final int PAL_TITLE_TRANSITION_ADDR = 0x459C;     // Pal_Title (112 bytes, 7 colors x 8 steps)
    public static final int PAL_TITLE_TRANSITION_SIZE = 112;
    public static final int PAL_TITLE_SONIC1_ADDR = 0x460C;         // Pal_TitleSonic1 (read 64 bytes for 2 palette lines)
    // Palettes 2-B are contiguous at 32-byte intervals: 0x462C, 0x464C, ...
    public static final int PAL_TITLE_SONIC_D_ADDR = 0x47AC;        // Pal_TitleSonicD (128 bytes = 4 palette lines)
    public static final int PAL_TITLE_SONIC_D_SIZE = 128;
    public static final int PAL_TITLE_WATER_ROT_ADDR = 0x4904;      // Pal_TitleWaterRot (32 bytes, banner palette cycling)
    public static final int PAL_TITLE_WATER_ROT_SIZE = 32;

    // Title screen Enigma mapping sizes (for read buffer allocation)
    public static final int MAP_ENI_TITLE_READ_SIZE = 1024;

    // Title screen VRAM tile constants (from sonic3k.constants.asm)
    public static final int VRAM_TITLE_BUFFER = 0x0300;          // ArtTile_Title_Buffer (double-buffer)
    public static final int VRAM_TITLE_MISC = 0x0400;            // ArtTile_Title_Misc (Sonic sprites)
    public static final int VRAM_TITLE_AND_KNUCKLES = 0x04C0;    // ArtTile_Title_ANDKnuckles
    public static final int VRAM_TITLE_BANNER = 0x0500;          // ArtTile_Title_Banner
    public static final int VRAM_TITLE_MENU = 0x0680;            // ArtTile_Title_Menu

    // ===== Title Card Art (KosinskiM compressed) =====
    // Shared art loaded to VRAM $500+
    public static final int ART_KOSM_TITLE_CARD_RED_ACT_ADDR = 0x0D6F28;   // Red banner + ACT text
    public static final int ART_KOSM_TITLE_CARD_S3K_ZONE_ADDR = 0x15C3A2;  // "ZONE" shared letters
    public static final int ART_KOSM_TITLE_CARD_NUM1_ADDR = 0x0D6D84;      // Act 1 number art
    public static final int ART_KOSM_TITLE_CARD_NUM2_ADDR = 0x0D6E46;      // Act 2 number art

    // Zone-specific title card letter art, indexed by zone (0=AIZ, 1=HCZ, ...)
    public static final int[] TITLE_CARD_ZONE_ART_ADDRS = {
            0x39BDC8,  // 0  AIZ - ArtKosM_AIZTitleCard (960 bytes)
            0x39BEDA,  // 1  HCZ - ArtKosM_HCZTitleCard (1248 bytes)
            0x39C02C,  // 2  MGZ - ArtKosM_MGZTitleCard (1344 bytes)
            0x39C1EE,  // 3  CNZ - ArtKosM_CNZTitleCard (1536 bytes)
            0x0D710A,  // 4  FBZ - ArtKosM_FBZTitleCard (1536 bytes)
            0x39C4E2,  // 5  ICZ - ArtKosM_ICZTitleCard (672 bytes)
            0x39C5B4,  // 6  LBZ - ArtKosM_LBZTitleCard (1248 bytes)
            0x15C454,  // 7  MHZ - ArtKosM_MHZTitleCard (1248 bytes)
            0x15C5D6,  // 8  SOZ - ArtKosM_SOZTitleCard (960 bytes)
            0x15C6E8,  // 9  LRZ - ArtKosM_LRZTitleCard (864 bytes)
            0x15C7FA,  // 10 SSZ - ArtKosM_SSZTitleCard (1536 bytes)
            0x15C9BC,  // 11 DEZ - ArtKosM_DEZTitleCard (960 bytes)
            0x15CA9E,  // 12 DDZ - ArtKosM_DDZTitleCard (1440 bytes)
            0x15CC30,  // 13 HPZ - ArtKosM_HPZTitleCard (1152 bytes)
    };

    // VRAM tile destinations for title card art blocks
    public static final int VRAM_TITLE_CARD_BASE = 0x500;       // RedAct base
    public static final int VRAM_TITLE_CARD_ZONE_TEXT = 0x510;   // S3KZone text overwrites
    public static final int VRAM_TITLE_CARD_ACT_NUM = 0x53D;     // Act number art
    public static final int VRAM_TITLE_CARD_ZONE_ART = 0x54D;    // Zone-specific letters

    // ===== Results Screen Art (KosinskiM) =====
    public static final int ART_KOSM_RESULTS_GENERAL_ADDR = 0x0D6A62;    // "GOT THROUGH", bonus labels
    public static final int ART_KOSM_RESULTS_SONIC_ADDR = 0x15B95C;      // "SONIC" name art (S&K version; S3 version at 0x39A786 shows "SUPER")
    public static final int ART_KOSM_RESULTS_MILES_ADDR = 0x39AA18;      // "MILES" name art
    public static final int ART_KOSM_RESULTS_TAILS_ADDR = 0x39AB6A;      // "TAILS" name art
    public static final int ART_KOSM_RESULTS_KNUCKLES_ADDR = 0x0D67F0;   // "KNUCKLES" name art

    // ===== Results Screen Palette & Mappings =====
    public static final int PAL_RESULTS_ADDR = 0x22D39E;                 // 128 bytes, full palette
    public static final int MAP_RESULTS_ADDR = 0x0002F26A;               // Mapping frames (59 entries)

    // ===== Results Screen VRAM Layout =====
    public static final int VRAM_RESULTS_BASE = 0x520;                   // General art destination
    public static final int VRAM_RESULTS_NUMBERS = 0x568;                // Digit tile destination
    public static final int VRAM_RESULTS_CHAR_NAME_ACT1 = 0x578;         // Character name (act 1)
    public static final int VRAM_RESULTS_CHAR_NAME_ACT2 = 0x5A0;         // Character name (act 2)
    public static final int VRAM_RESULTS_ARRAY_SIZE = 0x200;             // Total tile range $520-$71F (includes HUD text tiles at $6xx)

    // ===== Special Stage Results Art (KosinskiM) =====
    // ROM: SpecialStage_Results (sonic3k.asm lines 63054-63094)
    public static final int ART_KOSM_SS_RESULTS_ADDR = 0x15BABE;          // SS results text art (149 tiles, 4768 bytes decompressed)
    public static final int ART_KOSM_SS_RESULTS_SUPER_ADDR = 0x15B374;    // Super form art (Sonic)
    public static final int ART_KOSM_SS_RESULTS_SUPER_K_ADDR = 0x15B4F6;  // Super form art (Knuckles)

    // ===== Special Stage Results VRAM Layout =====
    // Different from level results: character name at $4F1, Super art at $50F, text at $523, general at $5B8
    public static final int VRAM_SS_RESULTS_CHAR_NAME = 0x4F1;   // Character name art dest
    public static final int VRAM_SS_RESULTS_SUPER = 0x50F;        // Super form art dest
    public static final int VRAM_SS_RESULTS_TEXT = 0x523;          // SS results specific text dest
    public static final int VRAM_SS_RESULTS_GENERAL = 0x5B8;      // General results art dest (same art, different VRAM)
    public static final int VRAM_SS_RESULTS_HUD_TEXT = 0x6BC;       // Ring/HUD text art dest (Nemesis)
    public static final int VRAM_SS_RESULTS_HUD_INITIAL = 0x6E2;  // HUD_DrawInitial overlay dest
    public static final int VRAM_SS_RESULTS_BASE = 0x4F1;         // Lowest VRAM address used
    public static final int VRAM_SS_RESULTS_ARRAY_SIZE = 0x300;   // Tile range $4F1-$7F0

    // HUD text art (Nemesis) — loaded by HUD_DrawInitial to VRAM $6E2 in SS results
    public static final int ART_NEM_HUD_TEXT_ADDR = 0x35CDBA;   // 24 tiles (768 bytes)

    // ===== Shield Art (uncompressed binclude in S3 data region) =====
    // Verified by binary pattern match against skdisasm .bin files, 2026-02-17

    // ArtUnc_FireShield - Fire Shield.bin (269 tiles)
    public static final int ART_UNC_FIRE_SHIELD_ADDR = 0x18C704;
    public static final int ART_UNC_FIRE_SHIELD_SIZE = 8608;

    // ArtUnc_LightningShield - Lightning Shield.bin (130 tiles)
    public static final int ART_UNC_LIGHTNING_SHIELD_ADDR = 0x18E8A4;
    public static final int ART_UNC_LIGHTNING_SHIELD_SIZE = 4160;

    // ArtUnc_LightningShield_Sparks - Sparks.bin (5 tiles)
    public static final int ART_UNC_LIGHTNING_SHIELD_SPARKS_ADDR = 0x18F8E4;
    public static final int ART_UNC_LIGHTNING_SHIELD_SPARKS_SIZE = 160;

    // ArtUnc_BubbleShield - Bubble Shield.bin (138 tiles)
    public static final int ART_UNC_BUBBLE_SHIELD_ADDR = 0x18F984;
    public static final int ART_UNC_BUBBLE_SHIELD_SIZE = 4416;

    // ===== Shield Mappings, DPLCs, Animations =====
    // Verified by ROM binary search for offset table patterns, 2026-02-17

    // Fire Shield: 25 mapping frames, 25 DPLC frames, 2 animations
    public static final int ANI_FIRE_SHIELD_ADDR = 0x019A02;
    public static final int ANI_FIRE_SHIELD_COUNT = 2;
    public static final int MAP_FIRE_SHIELD_ADDR = 0x019AC6;
    public static final int DPLC_FIRE_SHIELD_ADDR = 0x019CE6;

    // Lightning Shield: 24 mapping frames, 23 DPLC frames, 3 animations
    public static final int ANI_LIGHTNING_SHIELD_ADDR = 0x019A2A;
    public static final int ANI_LIGHTNING_SHIELD_COUNT = 3;
    public static final int MAP_LIGHTNING_SHIELD_ADDR = 0x019DC8;
    public static final int DPLC_LIGHTNING_SHIELD_ADDR = 0x019EFA;

    // Bubble Shield: 13 mapping frames, 13 DPLC frames, 3 animations
    public static final int ANI_BUBBLE_SHIELD_ADDR = 0x019A7A;
    public static final int ANI_BUBBLE_SHIELD_COUNT = 3;
    public static final int MAP_BUBBLE_SHIELD_ADDR = 0x019F82;
    public static final int DPLC_BUBBLE_SHIELD_ADDR = 0x01A076;

    // Dash Dust / Splash / Drown: shared art for all characters
    // Verified by ROM binary search (offset table fingerprint + art data match), 2026-04-03
    public static final int ART_UNC_DASH_DUST_ADDR = 0x18A604;
    public static final int ART_UNC_DASH_DUST_SIZE = 5952;  // 186 tiles x 32 bytes
    public static final int MAP_DASH_DUST_ADDR = 0x018DF4;
    public static final int DPLC_DASH_DUST_ADDR = 0x018EE2;

    // Insta-Shield: 8 mapping frames, 8 DPLC frames, 2 animations
    // Verified by ROM binary search, 2026-03-18
    public static final int ART_UNC_INSTA_SHIELD_ADDR = 0x18C084;
    public static final int ART_UNC_INSTA_SHIELD_SIZE = 1664;  // 52 tiles x 32 bytes
    public static final int ANI_INSTA_SHIELD_ADDR = 0x0199EA;
    public static final int ANI_INSTA_SHIELD_COUNT = 2;
    public static final int MAP_INSTA_SHIELD_ADDR = 0x01A0D0;
    public static final int DPLC_INSTA_SHIELD_ADDR = 0x01A154;

    // ArtUnc_Invincibility - Invincibility Stars art (32 tiles, uncompressed)
    // ROM: move.w #$200,d3 — DMA size in words ($200 words = 0x400 bytes = 32 tiles)
    // Verified by RomOffsetFinder, 2026-04-03
    public static final int ART_UNC_INVINCIBILITY_ADDR = 0x18A204;
    public static final int ART_UNC_INVINCIBILITY_SIZE = 0x400;     // 32 tiles × 32 bytes

    // Map_Invincibility - 9 mapping frames for invincibility star sprites
    // Verified by RomOffsetFinder, 2026-04-03
    public static final int MAP_INVINCIBILITY_ADDR = 0x018AEA;

    // ===== Collapsing Platform Mappings (Object 0x04) =====
    // Verified by ROM binary pattern search for offset table fingerprints, 2026-02-17

    // Map_AIZCollapsingPlatform - AIZ Act 1 collapsing platform mappings (4 frames)
    // Frames 0,1 = intact variants, frames 2,3 = fragment variants
    public static final int MAP_AIZ_COLLAPSING_PLATFORM_ADDR = 0x21E6C8;

    // Map_AIZCollapsingPlatform2 - AIZ Act 2 collapsing platform mappings (4 frames)
    public static final int MAP_AIZ_COLLAPSING_PLATFORM2_ADDR = 0x21E7AC;

    // Map_ICZCollapsingBridge - ICZ collapsing platform mappings (6 frames)
    public static final int MAP_ICZ_COLLAPSING_BRIDGE_ADDR = 0x21F2F2;

    // ===== Collapsing Bridge Mappings (Object 0x0F) =====
    // Multi-zone bridge that collapses when the player stands on it.
    // S3 LockOn region addresses (>= 0x200000) for S3KL zones.

    // Map_LBZCollapsingBridge - LBZ bridge variant (3 frames: intact + 2 fragment directions)
    public static final int MAP_LBZ_COLLAPSING_BRIDGE_ADDR = 0x21E896;

    // Map_LBZCollapsingLedge - LBZ ledge variant (3 frames)
    public static final int MAP_LBZ_COLLAPSING_LEDGE_ADDR = 0x21E992;

    // Map_HCZCollapsingBridge - HCZ bridge (12 frames: 4 subtypes × 3 frames each)
    public static final int MAP_HCZ_COLLAPSING_BRIDGE_ADDR = 0x21EA1A;

    // Map_MGZCollapsingBridge - MGZ bridge (9 frames: 3 subtypes × 3 frames each)
    public static final int MAP_MGZ_COLLAPSING_BRIDGE_ADDR = 0x21EE68;

    // Map_ICZCollapsingBridge is shared with Object 0x04 (above): 0x21F2F2
    // Object 0x0F uses frames 3-5, Object 0x04 uses frames 0-3.

    // S&K side addresses (< 0x200000) for SKL zones and HPZ.

    // Map_HPZCollapsingBridge - HPZ bridge (3 frames)
    public static final int MAP_HPZ_COLLAPSING_BRIDGE_ADDR = 0x020FCE;

    // Map_LRZCollapsingPlatform - LRZ bridge via Object 0x0F (3 frames)
    public static final int MAP_LRZ_COLLAPSING_BRIDGE_0F_ADDR = 0x020F0E;

    // Map_FBZCollapsingBridge - FBZ bridge (3 frames)
    public static final int MAP_FBZ_COLLAPSING_BRIDGE_ADDR = 0x02108E;

    // Map_SOZCollapsingBridge - SOZ bridge (3 frames)
    public static final int MAP_SOZ_COLLAPSING_BRIDGE_ADDR = 0x02127A;

    // ===== AIZ Disappearing Floor Mappings (Object 0x29) =====
    // Map_AIZDisappearingFloor - 6 frames: parent visual overlay (frame 0 = invisible, 1-5 = crumbling)
    // In LockOn data region (S3 half). Interleaved with Map_AIZDisappearingFloor2 offset table.
    public static final int MAP_AIZ_DISAPPEARING_FLOOR_ADDR = 0x2294B4;

    // Map_AIZDisappearingFloor2 - 4 frames: water border effect rendered around the platform
    public static final int MAP_AIZ_DISAPPEARING_FLOOR_BORDER_ADDR = 0x2294C0;

    // ===== AIZ Flipping Bridge Mappings (Object 0x2B) =====
    // Map_AIZFlippingBridge - 32 frames: frames 0-4 = flipping animation, frames 5-31 = flat walkable segment.
    // In LockOn data region (S3 half). Verified by ROM binary pattern search, 2026-03-30.
    // Note: first pointer entry != table size, so auto-detect frame count fails; use explicit count 32.
    public static final int MAP_AIZ_FLIPPING_BRIDGE_ADDR = 0x22A310;

    // ===== AIZ Collapsing Log Bridge Mappings (Object 0x2C) =====
    // Verified by ROM binary pattern search, 2026-03-30

    // Map_AIZCollapsingLogBridge - 3 frames: frame 0/1 = log segment, frame 2 = end segment with debris
    public static final int MAP_AIZ_COLLAPSING_LOG_BRIDGE_ADDR = 0x02B070;

    // Map_AIZDrawBridge - 2 frames: frame 0 = empty, frame 1 = single 2x2 bridge segment
    // ROM: Obj_AIZDrawBridge uses make_art_tile(ArtTile_AIZMisc2, 2, 1)
    public static final int MAP_AIZ_DRAW_BRIDGE_ADDR = 0x02B558;

    // Map_AIZDrawBridgeFire - 8 frames: frames 0-2 = bridge pieces, frames 3-7 = fire animation
    public static final int MAP_AIZ_DRAW_BRIDGE_FIRE_ADDR = 0x02B092;
    public static final int ART_NEM_EGG_CAPSULE_ADDR = 0x0DD990;
    public static final int MAP_EGG_CAPSULE_ADDR = 0x086BFC;
    public static final int ARTTILE_EGG_CAPSULE = 0x0494;

    // ===== Level Object Mappings (parsed at runtime by S3kSpriteDataLoader) =====
    // Verified by ROM binary pattern search for offset table fingerprints, 2026-02-17

    // Map_AIZRock - AIZ Act 1 rock mappings (7 frames: 3 intact + 4 debris)
    // Referenced at s3.asm:36232: move.l #Map_AIZRock,mappings(a0)
    public static final int MAP_AIZ_ROCK_ADDR = 0x21DCDC;

    // Map_AIZRock2 - AIZ Act 2 rock mappings (7 frames: 3 intact + 4 debris)
    // Referenced at s3.asm:36240: move.l #Map_AIZRock2,mappings(a0)
    public static final int MAP_AIZ_ROCK2_ADDR = 0x21DD64;

    // Map_AIZMHZRideVine - AIZ/MHZ ride-vine mappings (36 frames).
    // Referenced at sonic3k.asm:46152 and 46802.
    // Base address derived from map include: first frame at 0x22BE6 with 0x48-byte offset table.
    public static final int MAP_AIZ_MHZ_RIDE_VINE_ADDR = 0x022B9E;

    // Map_LRZBreakableRock - LRZ Act 1 breakable rock mappings (11 frames)
    // Referenced at sonic3k.asm:43871: move.l #Map_LRZBreakableRock,mappings(a0)
    public static final int MAP_LRZ_BREAKABLE_ROCK_ADDR = 0x0203D8;

    // Map_LRZBreakableRock2 - LRZ Act 2 breakable rock mappings (12 frames)
    // Referenced at sonic3k.asm:43876: move.l #Map_LRZBreakableRock2,mappings(a0)
    public static final int MAP_LRZ_BREAKABLE_ROCK2_ADDR = 0x02047A;

    // ===== AIZ Badnik mappings (SK side, verified from LockOn Pointers) =====
    // Map_Rhinobot - 8 mapping frames (DPLC-driven)
    public static final int MAP_RHINOBOT_ADDR = 0x3615A8;
    // DPLC_Rhinobot - object DPLC table (startTile in upper 12 bits)
    public static final int DPLC_RHINOBOT_ADDR = 0x36156E;
    // Map_Bloominator - 5 mapping frames (frame 4 is projectile seed)
    public static final int MAP_BLOOMINATOR_ADDR = 0x3616C0;
    // Map_MonkeyDude - 7 mapping frames (frame 6 is coconut projectile)
    public static final int MAP_MONKEY_DUDE_ADDR = 0x361776;

    // ===== AIZ Badnik dedicated art (SK side, verified from LockOn Pointers) =====
    // ArtUnc_AIZRhinobot - uncompressed source art used with DPLC_Rhinobot.
    public static final int ART_UNC_AIZ_RHINOBOT_ADDR = 0x36732A;
    public static final int ART_UNC_AIZ_RHINOBOT_SIZE = 0x0AA0;
    // ArtKosM_AIZ_Bloominator - Kosinski Moduled compressed art.
    public static final int ART_KOSM_AIZ_BLOOMINATOR_ADDR = 0x367DCA;
    // ArtKosM_AIZ_MonkeyDude - Kosinski Moduled compressed art.
    public static final int ART_KOSM_AIZ_MONKEY_DUDE_ADDR = 0x36800C;
    // Map_CaterKillerJr - 6 mapping frames (head, tall body, thin body, coconut large/med/small)
    public static final int MAP_CATERKILLER_JR_ADDR = 0x361A18;
    // ArtKosM_AIZ_CaterkillerJr - Kosinski Moduled compressed art ($202 bytes).
    public static final int ART_KOSM_AIZ_CATERKILLER_JR_ADDR = 0x3681FE;
    // AIZ1_8x8_Flames_KosM - fire overlay tiles loaded at x >= $2E00 (Act 1).
    public static final int ART_KOSM_AIZ1_FIRE_OVERLAY_ADDR = 0x3AF5D0;

    // ===== HCZ Badnik mappings (SK side, verified from LockOn Pointers) =====
    public static final int MAP_BUGGERNAUT_ADDR = 0x360EB4;           // Map_Buggernaut (6 frames)
    public static final int ART_NEM_BUGGERNAUT_ADDR = 0x36A3E0;      // ArtNem_HCZDragonfly (Nemesis, 16 tiles)
    public static final int MAP_BLASTOID_ADDR = 0x360DD0;
    public static final int MAP_TURBO_SPIKER_ADDR = 0x361212;
    public static final int MAP_MEGA_CHOPPER_ADDR = 0x360F26;
    public static final int MAP_POINTDEXTER_ADDR = 0x360E72;
    public static final int MAP_JAWZ_ADDR = 0x361364;
    public static final int ART_KOSM_HCZ_BLASTOID_ADDR = 0x36A7C6;
    public static final int ART_KOSM_HCZ_TURBO_SPIKER_ADDR = 0x36A968;
    public static final int ART_KOSM_HCZ_MEGA_CHOPPER_ADDR = 0x36A6C4;
    public static final int ART_KOSM_HCZ_POINTDEXTER_ADDR = 0x36AD8A;
    public static final int ART_KOSM_HCZ_JAWZ_ADDR = 0x36A552;

    // ===== HCZ Water Wall / Geyser (Object 0x3B) =====
    // LockOn data (assembled into S3 half of combined ROM — no S&K-side copy exists).
    // Verified by ROM hex search: these byte patterns are absent from 0x000000-0x200000.
    public static final int ART_KOSM_HCZ_GEYSER_HORZ_ADDR = 0x390C02; // ArtKosM_HCZGeyserHorz
    public static final int ART_KOSM_HCZ_GEYSER_VERT_ADDR = 0x391394; // ArtKosM_HCZGeyserVert
    // ArtTile_HCZGeyser - VRAM tile base for geyser art (both variants)
    public static final int ARTTILE_HCZ_GEYSER = 0x0500;

    // ===== MGZ Badnik Art =====
    public static final int ART_KOSM_SPIKER_ADDR = 0x36E0C4;
    public static final int MAP_SPIKER_ADDR = 0x361CB8;
    public static final int ART_KOSM_MANTIS_ADDR = 0x36E2D6;
    public static final int MAP_MANTIS_ADDR = 0x361D26;
    public static final int ART_UNC_BUBBLES_BADNIK_ADDR = 0x36D6A4;
    public static final int ART_UNC_BUBBLES_BADNIK_SIZE = 0x0A20;
    public static final int MAP_BUBBLES_BADNIK_ADDR = 0x361C68;
    public static final int DPLC_BUBBLES_BADNIK_ADDR = 0x361C40;
    public static final int ART_KOSM_MGZ_MINIBOSS_ADDR = 0x36B02C;
    public static final int MAP_MGZ_MINIBOSS_ADDR = 0x361972;
    public static final int ART_KOSM_MGZ_ENDBOSS_DEBRIS_ADDR = 0x36D572;

    // ===== CNZ Badnik Art =====
    public static final int ART_KOSM_SPARKLE_ADDR = 0x3700CA;
    public static final int MAP_SPARKLE_ADDR = 0x361B34;
    public static final int ART_KOSM_BATBOT_ADDR = 0x3703EC;
    public static final int MAP_BATBOT_ADDR = 0x361BD0;
    public static final int ART_UNC_CLAMER_ADDR = 0x36EF18;
    public static final int ART_UNC_CLAMER_SIZE = 0x1140;
    public static final int DPLC_CLAMER_ADDR = 0x361A78;
    public static final int MAP_CLAMER_ADDR = 0x361ABC;
    public static final int ART_KOSM_CLAMER_SHOT_ADDR = 0x370058;
    public static final int ART_KOSM_CNZ_BALLOON_ADDR = 0x37060E;
    public static final int MAP_CNZ_BALLOON_ADDR = 0x230502;

    // ===== FBZ Badnik Art =====
    public static final int ART_KOSM_FBZ_BLASTER_ADDR = 0x0DC6C2;
    public static final int MAP_BLASTER_ADDR = 0x08977C;
    public static final int ART_KOSM_FBZ_TECHNOSQUEEK_ADDR = 0x0DC9C4;
    public static final int MAP_TECHNOSQUEEK_ADDR = 0x089B78;
    public static final int ART_KOSM_FBZ_BUTTON_ADDR = 0x165E80;
    public static final int MAP_BUTTON_ADDR = 0x02C71E;
    public static final int MAP_LRZ_BUTTON_ADDR = 0x02C748;
    public static final int MAP_HCZ_BUTTON_ADDR = 0x22BD1A;
    public static final int MAP_CNZ_BUTTON_ADDR = 0x22BD4A;
    public static final int ARTTILE_GRAY_BUTTON = 0x0456;
    public static final int ARTTILE_HCZ_BUTTON = 0x0426;
    public static final int ARTTILE_CNZ_BUTTON = 0x041A; // ArtTile_CNZMisc + $C9
    public static final int ARTTILE_LRZ_MISC = 0x03A1;
    public static final int ARTTILE_LRZ2_BUTTON = 0x0429; // ArtTile_LRZ2Misc + $1C
    public static final int ARTTILE_FBZ_SPIKES = 0x0200;
    public static final int ARTTILE_FBZ_BUTTON = 0x0500;
    public static final int ARTTILE_BLASTER = 0x0506;
    public static final int ARTTILE_TECHNOSQUEEK = 0x052E;

    // ===== ICZ Badnik Art =====
    public static final int ART_KOSM_ICZ_SNOWDUST_ADDR = 0x375134;
    public static final int MAP_ICZ_SNOWDUST_ADDR = 0x361F0E;
    public static final int ART_KOSM_ICZ_STAR_POINTER_ADDR = 0x3751C6;
    public static final int MAP_STAR_POINTER_ADDR = 0x361FAE;
    public static final int ART_UNC_ICZ_PENGUINATOR_ADDR = 0x374154;
    public static final int ART_UNC_ICZ_PENGUINATOR_SIZE = 4064;
    public static final int MAP_PENGUINATOR_ADDR = 0x361E90;
    public static final int DPLC_PENGUINATOR_ADDR = 0x361E4E;

    // ===== LBZ Badnik Art =====
    public static final int ART_KOSM_SNALE_BLASTER_ADDR = 0x377996;
    public static final int MAP_SNALE_BLASTER_ADDR = 0x360400;
    public static final int ART_KOSM_ORBINAUT_ADDR = 0x377D1A;
    public static final int MAP_ORBINAUT_ADDR = 0x3604A4;
    public static final int ART_KOSM_RIBOT_ADDR = 0x377BE8;
    public static final int MAP_RIBOT_ADDR = 0x3604B8;
    public static final int ART_KOSM_CORKEY_ADDR = 0x377DFC;
    public static final int MAP_CORKEY_ADDR = 0x3605C2;

    // ===== MHZ Badnik Art =====
    public static final int ART_KOSM_MADMOLE_ADDR = 0x165F02;
    public static final int MAP_MADMOLE_ADDR = 0x08D9F2;
    public static final int ART_KOSM_MUSHMEANIE_ADDR = 0x166234;
    public static final int MAP_MUSHMEANIE_ADDR = 0x08DCF8;
    public static final int ART_KOSM_DRAGONFLY_ADDR = 0x166386;
    public static final int MAP_DRAGONFLY_ADDR = 0x08DFDA;
    public static final int ART_KOSM_CLUCKOID_ARROW_ADDR = 0x1664C8;
    public static final int ART_UNC_CLUCKOID_ADDR = 0x166A8A;
    public static final int ART_UNC_CLUCKOID_SIZE = 5696;
    public static final int MAP_CLUCKOID_ARROW_ADDR = 0x08E536;
    public static final int MAP_CLUCKOID_ADDR = 0x08E546;
    public static final int DPLC_CLUCKOID_ADDR = 0x08E4B8;
    public static final int ARTTILE_MGZ_MHZ_DIAGONAL_SPRING = 0x0478;

    // ===== SOZ Badnik Art =====
    public static final int ART_KOSM_SKORP_ADDR = 0x16ADC6;
    public static final int MAP_SKORP_ADDR = 0x186C84;
    public static final int ART_KOSM_SANDWORM_ADDR = 0x16B038;
    public static final int MAP_SANDWORM_ADDR = 0x186D10;
    public static final int ART_KOSM_ROCKN_ADDR = 0x16B2BA;
    public static final int MAP_ROCKN_ADDR = 0x08F086;

    // ===== LRZ Badnik Art =====
    public static final int ART_UNC_FIREWORM_ADDR = 0x16EFB2;
    public static final int ART_UNC_FIREWORM_SIZE = 0x380;
    public static final int MAP_FIREWORM_ADDR = 0x8FABE;
    public static final int DPLC_FIREWORM_ADDR = 0x8FAA6;
    public static final int ART_KOSM_FIREWORM_SEGMENTS_ADDR = 0x16F332;
    public static final int MAP_FIREWORM_SEGMENTS_ADDR = 0x8FA5C;
    public static final int ART_KOSM_IWAMODOKI_ADDR = 0x16F4E4;
    public static final int MAP_IWAMODOKI_ADDR = 0x8FC90;
    public static final int ART_KOSM_TOXOMISTER_ADDR = 0x16F7E6;
    public static final int MAP_TOXOMISTER_ADDR = 0x9008E;

    // ===== SSZ/DDZ Badnik Art =====
    public static final int ART_KOSM_EGG_ROBO_BADNIK_ADDR = 0x17B17E;
    public static final int MAP_EGG_ROBO_ADDR = 0x184F34;

    // ===== DEZ Badnik Art =====
    public static final int ART_KOSM_SPIKEBONKER_ADDR = 0x18008C;
    public static final int MAP_SPIKEBONKER_ADDR = 0x184E5C;
    public static final int ART_KOSM_CHAINSPIKE_ADDR = 0x1803EE;
    public static final int MAP_CHAINSPIKE_ADDR = 0x184E8A;

    // ===== StillSprite / AnimatedStillSprite =====
    // Mapping tables (ROM addresses verified via binary search)
    public static final int MAP_STILL_SPRITES_ADDR = 0x02BA9A;
    public static final int MAP_ANIMATED_STILL_SPRITES_ADDR = 0x02BFDA;

    // VRAM tile destinations for shields
    public static final int ART_TILE_SHIELD = 0x079C;
    public static final int ART_TILE_SHIELD_SPARKS = 0x07BB;

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
    // ===== AIZ Miniboss (Object 0x90/0x91) =====
    // PLC 0x5A loads: ArtNem_AIZMiniboss, ArtNem_AIZMinibossSmall,
    //                 ArtNem_AIZBossFire, ArtNem_BossExplosion
    // Art addresses and VRAM tile indices are derived from PLC entries at runtime.
    public static final int PLC_AIZ_MINIBOSS = 0x5A;
    // Pal_AIZMiniboss - Boss palette (32 bytes = 16 colors)
    public static final int PAL_AIZ_MINIBOSS_ADDR = 0x6917C;
    // Map_AIZMiniboss - Boss sprite mappings (18 frames, 0x11A bytes)
    public static final int MAP_AIZ_MINIBOSS_ADDR = 0x3624D0;
    // Map_AIZMinibossFlame - Flame sprite mappings (5 frames, 0x64 bytes)
    public static final int MAP_AIZ_MINIBOSS_FLAME_ADDR = 0x36165C;
    // Map_AIZMinibossSmall - Small debris mappings (3 frames, 0x1E bytes)
    public static final int MAP_AIZ_MINIBOSS_SMALL_ADDR = 0x3625EA;
    // Map_BossExplosion - Boss explosion mappings (6 frames, shared with S2)
    public static final int MAP_BOSS_EXPLOSION_ADDR = 0x083FFC;

    // ===== AIZ End Boss (Object 0x92) =====
    // ArtKosM_AIZEndBoss - Main boss art (Kosinski Moduled, 15712 bytes)
    public static final int ART_KOSM_AIZ_END_BOSS_ADDR = 0x365260;
    // ArtTile_AIZEndBoss - VRAM destination tile index
    public static final int ART_TILE_AIZ_END_BOSS = 0x0180;
    // PLC 0x6B loads: ArtNem_RobotnikShip + ArtNem_BossExplosion (shared Eggman ship/explosion art)
    public static final int PLC_AIZ_END_BOSS = 0x6B;
    // Pal_AIZEndBoss - End boss palette (32 bytes = 16 colors, palette line 2)
    public static final int PAL_AIZ_END_BOSS_ADDR = 0x69E80;
    // Map_AIZEndBoss - Boss sprite mappings (56 frames)
    public static final int MAP_AIZ_END_BOSS_ADDR = 0x361FD6;
    // Map_RobotnikShip - Robotnik ship sprite mappings (13 frames, shared)
    public static final int MAP_ROBOTNIK_SHIP_ADDR = 0x06820C;
    // ArtTile_RobotnikShip - VRAM tile for shared Robotnik ship
    public static final int ART_TILE_ROBOTNIK_SHIP = 0x052E;
    // ArtTile_BossExplosion2 - VRAM tile for boss explosion (PLC_6B)
    public static final int ART_TILE_BOSS_EXPLOSION_2 = 0x04D2;

    // ===== AIZ2 Battleship / Bombing Sequence =====
    // AIZ2_16x16_BomberShip_Kos - Kosinski-compressed 16x16 ship blocks (S3 half 0x1B1372 + 0x200000)
    public static final int AIZ2_16X16_BOMBERSHIP_ADDR = 0x3B1372;
    // AIZ2_16x16_BomberShip_Kos is queued to Block_table+$AB8 in the ROM.
    // In engine terminology this patches Chunk data (16x16 tiles).
    public static final int AIZ2_16X16_BOMBERSHIP_DEST_OFFSET = 0x0AB8;
    // AIZ2_8x8_BomberShip_KosM - Kosinski Moduled 8x8 ship tiles (S3 half 0x1B48C6 + 0x200000)
    public static final int AIZ2_8X8_BOMBERSHIP_ADDR = 0x3B48C6;
    // Queue_Kos_Module(AIZ2_8x8_BomberShip_KosM, tile $1FC)
    public static final int AIZ2_8X8_BOMBERSHIP_DEST_TILE = 0x01FC;
    public static final int AIZ2_8X8_BOMBERSHIP_DEST_BYTES =
            AIZ2_8X8_BOMBERSHIP_DEST_TILE * Pattern.PATTERN_SIZE_IN_ROM;
    // ArtKosM_AIZ2Bombership2_8x8 - KosinskiModuled art (176 tiles, covers all mapping indices)
    public static final int ART_KOSM_AIZ2_BOMBERSHIP_ADDR = 0x399CC4;
    // ArtTile_AIZ2Bombership - VRAM tile for bombership/bomb art
    public static final int ART_TILE_AIZ2_BOMBERSHIP = 0x0500;
    // Map_AIZ2BombExplode - Bomb explosion mappings (12 frames)
    public static final int MAP_AIZ2_BOMB_EXPLODE_ADDR = 0x23C1B2;
    // Map_AIZShipPropeller - Ship propeller mappings (4 frames)
    public static final int MAP_AIZ_SHIP_PROPELLER_ADDR = 0x23C182;
    // Map_AIZ2BossSmall - Small Eggman craft mappings (1 frame)
    public static final int MAP_AIZ2_BOSS_SMALL_ADDR = 0x23C264;
    // ArtNem_AIZBackgroundTree - Nemesis art for parallax trees (15 tiles)
    public static final int ART_NEM_AIZ_BG_TREE_ADDR = 0x38DB46;
    // ArtTile_AIZBackgroundTree - VRAM tile base
    public static final int ART_TILE_AIZ_BG_TREE = 0x0438;
    // Map_AIZ2BGTree - Tree sprite mapping (1 frame, 4 pieces)
    public static final int MAP_AIZ2_BG_TREE_ADDR = 0x23C248;
    // Pal_AIZBattleship - Battleship palette (32 bytes, palette line 2)
    public static final int PAL_AIZ_BATTLESHIP_ADDR = 0x23C05A;
    // Pal_AIZBossSmall - Small boss/bombing palette (28 bytes, palette line 2)
    public static final int PAL_AIZ_BOSS_SMALL_ADDR = 0x23C07A;
    // ArtNem_RobotnikShip - Shared Robotnik ship art (Nemesis)
    public static final int ART_NEM_ROBOTNIK_SHIP_ADDR = 0x0D771E;
    // ArtNem_BossExplosion - Shared boss explosion art (Nemesis)
    public static final int ART_NEM_BOSS_EXPLOSION_ADDR = 0x0D73CE;

    // ===== Signpost (Obj_EndSign) - End of act signpost =====
    // ArtUnc_EndSigns - Uncompressed end-of-act signpost art (3328 bytes)
    public static final int ART_UNC_END_SIGNS_ADDR = 0x0DCC76;
    public static final int ART_UNC_END_SIGNS_SIZE = 3328;
    // ArtNem_SignpostStub - Nemesis-compressed signpost pole/stub art
    public static final int ART_NEM_SIGNPOST_STUB_ADDR = 0x0DD976;
    // Map_EndSigns - Signpost face sprite mappings
    public static final int MAP_END_SIGNS_ADDR = 0x083B9E;
    // DPLC_EndSigns - Signpost DPLC table
    public static final int DPLC_END_SIGNS_ADDR = 0x083B6C;
    // Map_SignpostStub - Signpost pole/stub mappings
    public static final int MAP_SIGNPOST_STUB_ADDR = 0x083BFC;
    // VRAM tile indices for signpost art
    public static final int ART_TILE_END_SIGNS = 0x04AC;
    public static final int ART_TILE_SIGNPOST_STUB = 0x069E;

    // ===== SS Entry Ring (Obj_SSEntryRing) - Special Stage big ring =====
    // ArtUnc_SSEntryRing - Uncompressed art (9984 bytes = 312 tiles)
    public static final int ART_UNC_SS_ENTRY_RING_ADDR = 0x0D8766;
    public static final int ART_UNC_SS_ENTRY_RING_SIZE = 9984;
    // Map_SSEntryRing - 12 mapping frames
    public static final int MAP_SS_ENTRY_RING_ADDR = 0x0619E0;
    // DPLC_SSEntryRing - 12 DPLC frames
    public static final int DPLC_SS_ENTRY_RING_ADDR = 0x061ABE;

    // ===== SS Entry Flash (Obj_SSEntryFlash) - Big ring collection flash effect =====
    // ArtUnc_SSEntryFlash - Uncompressed art (1440 bytes = 45 tiles)
    public static final int ART_UNC_SS_ENTRY_FLASH_ADDR = 0x0DAE66;
    public static final int ART_UNC_SS_ENTRY_FLASH_SIZE = 1440;
    // Map_SSEntryFlash - 4 mapping frames (+ 1 extra embedded frame not in offset table)
    public static final int MAP_SS_ENTRY_FLASH_ADDR = 0x061B28;
    // DPLC_SSEntryFlash - 4 DPLC frames
    public static final int DPLC_SS_ENTRY_FLASH_ADDR = 0x061BFA;

    // ===== Pal_AIZ - Main AIZ palette (for AfterBoss_Cleanup) =====
    public static final int PAL_AIZ_ADDR = 0x0A8B7C;
    public static final int PAL_AIZ_SIZE = 96;

    // Pal_AIZFire - AIZ fire/post-transition palette (96 bytes = 3 lines)
    // AfterBoss_AIZ2 loads first 32 bytes into palette line 1 via PalLoad_Line1
    public static final int PAL_AIZ_FIRE_ADDR = 0x0A8BDC;

    // ===== Level Select Screen =====
    // Art (Nemesis compressed, reuses S2 menu infrastructure at S3K ROM offsets)
    public static final int ART_NEM_S22P_OPTIONS_ADDR = 0xCA5E0;   // Font art (Nemesis)
    public static final int ART_NEM_S2_MENU_BOX_ADDR = 0x2C3AF2;   // Menu box borders (Nemesis)
    public static final int ART_NEM_S2_LEVEL_SELECT_PICS_ADDR = 0x2C3B72; // Zone preview icons (Nemesis)
    public static final int ART_UNC_SONICMILES_ADDR = 0xAA57C;     // SONICMILES background anim (Uncompressed)
    public static final int ART_UNC_SONICMILES_SIZE = 1280;         // 40 tiles x 32 bytes

    // Mappings (Enigma compressed)
    public static final int MAP_ENI_S2_LEV_SEL_ADDR = 0x20731A;    // Main screen layout
    public static final int MAP_ENI_S22P_OPTIONS_ADDR = 0xCAB54;    // Background layout (Plane B, 2P Options)
    public static final int MAP_ENI_S2_LEV_SEL_ICON_ADDR = 0x20746E; // Icon box mappings

    // Palettes (uncompressed)
    public static final int PAL_S2_MENU_ADDR = 0xA8A7C;            // Menu palette (4 lines, 128B)
    public static final int PAL_S2_MENU_SIZE = 128;
    public static final int PAL_S2_LEVEL_ICONS_ADDR = 0x2070BC;    // 15 icon palettes (480B)
    public static final int PAL_S2_LEVEL_ICONS_SIZE = 480;

    private static boolean scanned = false;

    public static boolean isScanned() {
        return scanned;
    }

    public static void setScanned(boolean value) {
        scanned = value;
    }
}
