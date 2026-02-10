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

    // ---- Player sprite data (REV01) ----
    // Sonic's uncompressed art tiles (8x8 patterns)
    public static final int ART_UNC_SONIC_ADDR = 0x22610;
    public static final int ART_UNC_SONIC_SIZE = 0xA120; // 41248 bytes = 1289 patterns

    // Sonic's sprite mappings (S1 format: 5 bytes per piece, byte header)
    public static final int MAP_SONIC_ADDR = 0x21CF4;

    // Sonic's Dynamic Pattern Load Cues (S1 format: byte header)
    public static final int DPLC_SONIC_ADDR = 0x22310;

    // Sonic's animation scripts (31 animations)
    public static final int SONIC_ANIM_DATA_ADDR = 0x1421C;
    public static final int SONIC_ANIM_SCRIPT_COUNT = 31;

    // Sonic's VRAM tile index
    public static final int ART_TILE_SONIC = 0x0780;
    public static final int ARTTILE_RING = 0x0780;

    // S1 mapping/DPLC frame count (Map_Sonic and SonicDynPLC both have this many entries)
    public static final int SONIC_MAPPING_FRAME_COUNT = 88;

    // ---- Animated level art (uncompressed) ----
    // Addresses calculated backward from LEVEL_INDEX_ADDR using artunc file sizes.
    // GHZ animated art
    public static final int ART_UNC_GHZ_WATER_ADDR   = 0x66A96; // 512 bytes, 16 tiles (2 frames × 8 tiles)
    public static final int ART_UNC_GHZ_FLOWER1_ADDR  = 0x66C96; // 1024 bytes, 32 tiles (2 frames × 16 tiles)
    public static final int ART_UNC_GHZ_FLOWER2_ADDR  = 0x67096; // 1152 bytes, 36 tiles (3 frames × 12 tiles)

    // MZ animated art
    public static final int ART_UNC_MZ_LAVA1_ADDR    = 0x67516; // 768 bytes, 24 tiles (3 frames × 8 tiles)
    public static final int ART_UNC_MZ_LAVA2_ADDR    = 0x67816; // 1536 bytes, 48 tiles (magma, 3 frames × 16 tiles)
    public static final int ART_UNC_MZ_TORCH_ADDR    = 0x67E16; // 768 bytes, 24 tiles (4 frames × 6 tiles)

    // SBZ animated art
    public static final int ART_UNC_SBZ_SMOKE_ADDR   = 0x68116; // 2688 bytes, 84 tiles (7 frames × 12 tiles)

    // Giant ring art (after level layout data)
    public static final int ART_UNC_GIANT_RING_ADDR  = 0x6A2E4; // Art_BigRing (verified by RomOffsetFinder)
    public static final int ART_UNC_GIANT_RING_SIZE  = 3136;     // $C40 bytes = 98 tiles

    // Giant ring flash art (Nemesis compressed)
    public static final int ART_NEM_GIANT_RING_FLASH_ADDR = 0x3AF24; // Nem_BigFlash (verified by RomOffsetFinder)

    // ---- Title card art ----
    // Nem_TitleCard: Nemesis-compressed title card sprite art (1550 bytes)
    // Contains zone name letters, "ZONE", act numbers, oval decoration
    // Verified by binary search matching docs/s1disasm/artnem/Title Cards.nem
    public static final int ART_NEM_TITLE_CARD_ADDR = 0x39204;

    // ---- Animated tile VRAM destinations (ArtTile_Level = 0x000) ----
    // GHZ
    public static final int ARTTILE_GHZ_WATERFALL     = 0x378;
    public static final int ARTTILE_GHZ_BIG_FLOWER_1  = 0x35C;
    public static final int ARTTILE_GHZ_SMALL_FLOWER  = 0x36C;
    // MZ
    public static final int ARTTILE_MZ_ANIMATED_LAVA  = 0x2E2;
    public static final int ARTTILE_MZ_ANIMATED_MAGMA = 0x2D2;
    public static final int ARTTILE_MZ_TORCH          = 0x2F2;
    // SBZ
    public static final int ARTTILE_SBZ_SMOKE_PUFF_1  = 0x448;
    public static final int ARTTILE_SBZ_SMOKE_PUFF_2  = 0x454;

    // ---- Title screen art ----
    public static final int ART_NEM_TITLE_FG_ADDR    = 0x1ED80;  // Nem_TitleFg (title foreground)
    public static final int ART_NEM_TITLE_SONIC_ADDR  = 0x1FD8C;  // Nem_TitleSonic (Sonic sprite)
    public static final int ART_NEM_TITLE_TM_ADDR     = 0x2175A;  // Nem_TitleTM (trademark symbol)
    public static final int ART_NEM_CREDIT_TEXT_ADDR   = 0x6203A;  // Nem_CreditText (credit text font)
    public static final int MAP_ENI_TITLE_ADDR         = 0x1EC6C;  // Eni_Title (title foreground tilemap)
    public static final int ART_NEM_GHZ_1ST_ADDR       = 0x3CB3C;  // Nem_GHZ_1st (GHZ background patterns)
    public static final int BLK16_GHZ_ADDR             = 0x3C19C;  // Blk16_GHZ (16x16 chunk mappings, Enigma)
    public static final int BLK256_GHZ_ADDR            = 0x3F544;  // Blk256_GHZ (256x256 block mappings, Kosinski)
    public static final int LEVEL_GHZ_BG_ADDR          = 0x68F22;  // Level_GHZbg (background layout, uncompressed)

    // ---- Title screen palettes ----
    public static final int PAL_TITLE_ADDR       = 0x2280;  // Pal_Title (128 bytes, 4 palette lines)
    public static final int PAL_TITLE_CYCLE_ADDR = 0x1B5E;  // Pal_TitleCyc (32 bytes, water cycle palette)

    // ---- Title screen VRAM tile indices (from Constants.asm) ----
    public static final int ARTTILE_TITLE_FOREGROUND   = 0x200;
    public static final int ARTTILE_TITLE_SONIC        = 0x300;
    public static final int ARTTILE_TITLE_TRADEMARK    = 0x510;
    public static final int ARTTILE_SONIC_TEAM_FONT    = 0x0A6;

    // ---- HUD art ----
    // Art_Hud: Uncompressed HUD digit tiles (0-9 + colon), each digit = 2 tiles (top/bottom)
    public static final int ART_UNC_HUD_NUMBERS_ADDR = 0x1D2A6;
    public static final int ART_UNC_HUD_NUMBERS_SIZE = 0x300; // 768 bytes = 24 tiles

    // Art_LivesNums: Uncompressed 8x8 lives counter digit tiles (0-9)
    public static final int ART_UNC_LIVES_NUMBERS_ADDR = 0x1D5A6;
    public static final int ART_UNC_LIVES_NUMBERS_SIZE = 320; // 10 tiles

    // Nem_Hud: Nemesis-compressed HUD text labels (SCORE/TIME/RINGS)
    public static final int ART_NEM_HUD_ADDR = 0x39812;

    // Nem_Lives: Nemesis-compressed life counter icon (Sonic face + name letters)
    public static final int ART_NEM_LIFE_ICON_ADDR = 0x39908;

    // Nem_Ring: Nemesis-compressed ring art (8 frames: 4 spin + 4 sparkle)
    public static final int ART_NEM_RING_ADDR = 0x39A0E;

    // Nem_Monitors: Nemesis-compressed monitor art (1120 bytes, all types + broken shell)
    public static final int ART_NEM_MONITOR_ADDR = 0x39B02;

    // Nem_Explode: Nemesis-compressed explosion art (ArtTile_Explosion = $5A0)
    public static final int ART_NEM_EXPLOSION_ADDR = 0x39F62;

    // Nem_Points: Nemesis-compressed points popups (100/200/500/1000)
    public static final int ART_NEM_POINTS_ADDR = 0x3A5C8;

    // Nem_Bonus: Nemesis-compressed hidden bonus point popup art (769 bytes)
    // ArtTile_Hidden_Points = $4B6
    // Verified by RomOffsetFinder --game s1 search Bonus
    public static final int ART_NEM_HIDDEN_BONUS_ADDR = 0x3B098;

    // Zone animal art (used by badnik destruction object 0x28)
    public static final int ART_NEM_ANIMAL_RABBIT_ADDR = 0x3B884;
    public static final int ART_NEM_ANIMAL_CHICKEN_ADDR = 0x3B9DC;
    public static final int ART_NEM_ANIMAL_PENGUIN_ADDR = 0x3BB38;
    public static final int ART_NEM_ANIMAL_SEAL_ADDR = 0x3BCB4;
    public static final int ART_NEM_ANIMAL_PIG_ADDR = 0x3BDD0;
    public static final int ART_NEM_ANIMAL_FLICKY_ADDR = 0x3BF06;
    public static final int ART_NEM_ANIMAL_SQUIRREL_ADDR = 0x3C040;

    // Nem_Shield: Nemesis-compressed shield art (ArtTile_Shield = $541)
    public static final int ART_NEM_SHIELD_ADDR = 0x2C730;

    // ---- Touch collision sizes (ReactToItem .sizes table) ----
    // S1 uses `lea .sizes-2(pc,d0.w)` so effective base is .sizes-2 = 0x1B5E4-2.
    // 36 entries indexed 0x01-0x24; need 37 array slots (0-36) for the engine's 0-based lookup.
    public static final int TOUCH_SIZES_ADDR = 0x1B5E2;
    public static final int TOUCH_ENTRY_COUNT = 37;

    // Nem_PplRock: Nemesis-compressed purple rock art (GHZ, 302 bytes)
    public static final int ART_NEM_PURPLE_ROCK_ADDR = 0x300BA;

    // Nem_Bridge: Nemesis-compressed bridge art (GHZ, ~10 tiles: log, stump, rope)
    // Loaded via PLC_GHZ2: plcm Nem_Bridge, ArtTile_GHZ_Bridge
    public static final int ART_NEM_BRIDGE_ADDR = 0x2FA2C;

    // Nem_SlzCannon: Nemesis-compressed SLZ fireball launcher / lava thrower art
    // Loaded via PLC_SLZ: plcm Nem_SlzCannon, ArtTile_SLZ_Fireball_Launcher
    // Verified by RomOffsetFinder --game s1 search Cannon
    public static final int ART_NEM_SLZ_CANNON_ADDR = 0x34254;

    // Nem_Lamp: Nemesis-compressed lamppost art (10 tiles: pole, blue ball, red ball)
    public static final int ART_NEM_LAMPPOST_ADDR = 0x3AE64;

    // Nem_Swing: Nemesis-compressed GHZ/MZ swinging platform art (281 bytes, ArtTile $380)
    // Verified by RomOffsetFinder --game s1 search Swing
    public static final int ART_NEM_SWING_ADDR = 0x2F912;

    // Nem_Ball: Nemesis-compressed GHZ giant ball art (413 bytes, ArtTile $3AA)
    // Verified by RomOffsetFinder --game s1 search Ball
    public static final int ART_NEM_GIANT_BALL_ADDR = 0x2FB60;

    // Nem_SpikePole: Nemesis-compressed spiked pole helix art (GHZ, 300 bytes, ArtTile $398)
    // Verified by RomOffsetFinder --game s1 search SpikePole
    public static final int ART_NEM_SPIKE_POLE_ADDR = 0x2FF8E;

    // Nem_Spikes: Nemesis-compressed spike art (upward + sideways, 8 tiles)
    public static final int ART_NEM_SPIKES_ADDR = 0x2FCFE;

    // Nem_Buzz: Nemesis-compressed Buzz Bomber art (GHZ/MZ/SYZ, ArtTile $444)
    public static final int ART_NEM_BUZZ_BOMBER_ADDR = 0x3639E;

    // Nem_Crabmeat: Nemesis-compressed Crabmeat art (GHZ/SYZ, ArtTile $400)
    // Verified by decompression at ROM offset via RomOffsetFinder
    public static final int ART_NEM_CRABMEAT_ADDR = 0x35EB0;

    // Nem_Chopper: Nemesis-compressed Chopper art (GHZ, ArtTile_Chopper = $47B)
    // Verified by RomOffsetFinder --game s1 search Chopper (616 bytes, 1024 decompressed)
    public static final int ART_NEM_CHOPPER_ADDR = 0x37016;

    // Nem_Motobug: Nemesis-compressed Motobug art (GHZ, ArtTile $4F0)
    // Verified by RomOffsetFinder --game s1 search Motobug
    public static final int ART_NEM_MOTOBUG_ADDR = 0x37A2C;

    // Nem_Newtron: Nemesis-compressed Newtron art (GHZ, ArtTile_Newtron = $49B)
    // Verified by RomOffsetFinder --game s1 find Nem_Newtron (2720 bytes decompressed = 85 tiles)
    public static final int ART_NEM_NEWTRON_ADDR = 0x37CB6;

    // Nem_HSpring: Nemesis-compressed horizontal spring art (up/down springs, 16 tiles)
    // ArtTile_Spring_Horizontal = $523
    public static final int ART_NEM_HSPRING_ADDR = 0x3A80A;

    // Nem_VSpring: Nemesis-compressed vertical spring art (left/right springs, ~14 tiles)
    // ArtTile_Spring_Vertical = $533
    public static final int ART_NEM_VSPRING_ADDR = 0x3A90C;

    // Nem_Sign: Nemesis-compressed signpost art (end-of-act sign, 58 tiles)
    public static final int ART_NEM_SIGNPOST_ADDR = 0x3A9E8;

    // Nem_GhzWall1: Nemesis-compressed GHZ breakable wall art (ArtTile_GHZ_SLZ_Smashable_Wall = $50F)
    // Verified by RomOffsetFinder --game s1 search GhzWall1 (157 bytes)
    public static final int ART_NEM_GHZ_BREAKABLE_WALL_ADDR = 0x301E8;

    // Nem_GhzWall2: Nemesis-compressed GHZ edge wall art (ArtTile_GHZ_Edge_Wall = $34C)
    // Verified by RomOffsetFinder --game s1 search GhzWall (96 bytes)
    public static final int ART_NEM_GHZ_EDGE_WALL_ADDR = 0x30286;

    // Nem_SlzWall: Nemesis-compressed SLZ breakable wall art (ArtTile_GHZ_SLZ_Smashable_Wall+4)
    // Verified by RomOffsetFinder --game s1 search SlzWall (97 bytes)
    public static final int ART_NEM_SLZ_BREAKABLE_WALL_ADDR = 0x33E22;

    // Nem_SlzSwing: Nemesis-compressed SLZ swinging platform art (482 bytes, ArtTile $3DC)
    // Verified by RomOffsetFinder --game s1 search Swing
    public static final int ART_NEM_SLZ_SWING_ADDR = 0x33F66;

    // Nem_SyzSpike1: Nemesis-compressed SYZ large spikeball / SBZ spiked ball art (654 bytes, ArtTile $391)
    // Used by swinging platform object in SBZ (spiked ball on a chain)
    // Verified by RomOffsetFinder --game s1 search Ball
    public static final int ART_NEM_SBZ_SPIKED_BALL_ADDR = 0x345A6;

    // ---- Boss art (Nemesis compressed) ----
    // Nem_Eggman: Main Eggman ship + face + flame art (verified by RomOffsetFinder)
    public static final int ART_NEM_EGGMAN_ADDR = 0x5D0FC;
    // Nem_Weapons: Boss weapons art — chain anchor, pipes, spikes (verified)
    public static final int ART_NEM_BOSS_WEAPONS_ADDR = 0x5D960;
    // Nem_Exhaust: Boss exhaust/escape flame art (verified)
    public static final int ART_NEM_BOSS_EXHAUST_ADDR = 0x5F9E2;
    // Nem_Prison: Prison capsule art (verified)
    public static final int ART_NEM_PRISON_ADDR = 0x5DC4A;

    // ---- Loop / Plane Switching ----
    // LoopTileNums table from Sonic_Loops (_incObj/01 Sonic.asm lines 1536-1611).
    // Per-zone: { loop1, loop2, roll1, roll2 } - raw layout values including bit 7.
    // 0x7F means "disabled" (no loop/roll in that zone).
    public static final int LOOP_DISABLED = 0x7F;
    // GHZ loop tile IDs
    public static final int GHZ_LOOP1 = 0xB5;  // 0x35 | 0x80
    public static final int GHZ_LOOP2 = 0x7F;  // disabled
    public static final int GHZ_ROLL1 = 0x1F;
    public static final int GHZ_ROLL2 = 0x20;
    // SLZ loop tile IDs
    public static final int SLZ_LOOP1 = 0xAA;  // 0x2A | 0x80
    public static final int SLZ_LOOP2 = 0xB4;  // 0x34 | 0x80
    public static final int SLZ_ROLL1 = 0x7F;  // disabled
    public static final int SLZ_ROLL2 = 0x7F;  // disabled
    // Position thresholds within 256x256 block for plane switching
    public static final int LOOP_HIGH_PLANE_X_MAX = 0x2C; // X < 0x2C → high plane
    public static final int LOOP_LOW_PLANE_X_MIN = 0xE0;  // X >= 0xE0 → low plane
    // Special-case block remap: block 0x29 remaps to 0x51 in FindNearestTile
    public static final int LOOP_BLOCK_REMAP_FROM = 0x29;
    public static final int LOOP_BLOCK_REMAP_TO = 0x51;

    // ---- Results screen VRAM layout ----
    // ArtTile_Title_Card = $580: title card letters, zone names, oval, act numbers
    // ArtTile_HUD = $6CA = $580 + $14A: HUD text labels (SCORE/TIME/RINGS)
    // Bonus digits occupy $570-$57F (16 tiles below ArtTile_Title_Card)
    public static final int VRAM_RESULTS_BASE = 0x570;
    public static final int VRAM_RESULTS_TITLE_CARD = 0x580;
    public static final int VRAM_RESULTS_HUD_TEXT = 0x6CA;
    // Tile adjust: mapping tile IDs are relative to $580, array starts at $570
    public static final int RESULTS_TILE_ADJUST = VRAM_RESULTS_TITLE_CARD - VRAM_RESULTS_BASE; // 0x10
    // Bonus digit tiles: 2 groups (time bonus, ring bonus) x 4 digits x 2 tiles = 16
    public static final int S1_RESULTS_BONUS_DIGIT_TILES = 16;
    public static final int S1_RESULTS_BONUS_DIGIT_GROUP_TILES = 8;

    // ---- Special Stage data ----

    // SS_LayoutIndex: 6 longword pointers to Enigma-compressed stage layouts
    public static final int SS_LAYOUT_INDEX_ADDR = 0x1BE02;
    // Individual layout ROM offsets (verified via RomOffsetFinder)
    public static final int SS_LAYOUT_1_ADDR = 0x65432;
    public static final int SS_LAYOUT_2_ADDR = 0x656AC;
    public static final int SS_LAYOUT_3_ADDR = 0x65ABE;
    public static final int SS_LAYOUT_4_ADDR = 0x65E1A;
    public static final int SS_LAYOUT_5_ADDR = 0x662F4;
    public static final int SS_LAYOUT_6_ADDR = 0x667A4;

    // SS_StartLoc: 6 entries x 4 bytes (word X, word Y) immediately after SS_LayoutIndex
    public static final int SS_START_LOC_ADDR = 0x1BE1A;

    // Layout format constants
    public static final int SS_STAGE_COUNT = 6;
    public static final int SS_LAYOUT_STRIDE = 0x80;   // stride per row in block buffer
    public static final int SS_LAYOUT_COLS = 0x40;      // data columns per row
    public static final int SS_BLOCK_SIZE_PX = 0x18;    // 24px per block
    public static final int SS_LAYOUT_RAM_SIZE = 0x4000; // v_ssbuffer1..v_ssbuffer2 clear range
    public static final int SS_BLOCKBUFFER_OFFSET = 0x1020; // v_ssblockbuffer = v_ssbuffer1 + $1020
    public static final int SS_BLOCKBUFFER_ROWS = 0x40; // (v_ssblockbuffer_end-v_ssblockbuffer)/$80

    // ---- Special Stage palette ----
    // palid_Special = index 10 in PalIndex table
    public static final int PAL_SS_ADDR = 0x2460;       // Pal_Special (128 bytes, 4 palette lines)
    public static final int PAL_SS_SIZE = 128;
    public static final int PAL_SS_CYC1_ADDR = 0x4AD6;  // Pal_SSCyc1 (72 bytes)
    public static final int PAL_SS_CYC1_SIZE = 72;
    public static final int PAL_SS_CYC2_ADDR = 0x4B1E;  // Pal_SSCyc2 (210 bytes)
    public static final int PAL_SS_CYC2_SIZE = 210;

    // ---- Special Stage art (Nemesis compressed) ----
    // All verified via RomOffsetFinder --game s1
    public static final int ART_NEM_SS_WALLS_ADDR     = 0x2CA8E; // 2360 bytes
    public static final int ART_NEM_SS_WALLS_SIZE      = 2360;
    public static final int ART_NEM_SS_BUMPER_ADDR     = 0x342F8; // Nem_Bumper (SYZ bumper reused), 362 bytes
    public static final int ART_NEM_SS_BUMPER_SIZE     = 362;
    public static final int ART_NEM_SS_GOAL_ADDR       = 0x2E97E; // 237 bytes
    public static final int ART_NEM_SS_GOAL_SIZE       = 237;
    public static final int ART_NEM_SS_UPDOWN_ADDR     = 0x2F1E0; // 500 bytes
    public static final int ART_NEM_SS_UPDOWN_SIZE     = 500;
    public static final int ART_NEM_SS_RBLOCK_ADDR     = 0x2EA6C; // 207 bytes
    public static final int ART_NEM_SS_RBLOCK_SIZE     = 207;
    public static final int ART_NEM_SS_1UP_ADDR        = 0x2EB3C; // 245 bytes
    public static final int ART_NEM_SS_1UP_SIZE        = 245;
    public static final int ART_NEM_SS_EM_STARS_ADDR   = 0x2EC32; // 93 bytes
    public static final int ART_NEM_SS_EM_STARS_SIZE   = 93;
    public static final int ART_NEM_SS_RED_WHITE_ADDR  = 0x2EC90; // 145 bytes
    public static final int ART_NEM_SS_RED_WHITE_SIZE  = 145;
    public static final int ART_NEM_SS_GHOST_ADDR      = 0x2F53C; // 176 bytes
    public static final int ART_NEM_SS_GHOST_SIZE      = 176;
    public static final int ART_NEM_SS_WBLOCK_ADDR     = 0x2F5EC; // 218 bytes
    public static final int ART_NEM_SS_WBLOCK_SIZE     = 218;
    public static final int ART_NEM_SS_GLASS_ADDR      = 0x2F6C6; // 131 bytes
    public static final int ART_NEM_SS_GLASS_SIZE      = 131;
    public static final int ART_NEM_SS_EMERALD_ADDR    = 0x2F3D4; // 359 bytes
    public static final int ART_NEM_SS_EMERALD_SIZE    = 359;
    public static final int ART_NEM_SS_ZONE1_ADDR      = 0x2ED22; // 193 bytes
    public static final int ART_NEM_SS_ZONE1_SIZE      = 193;
    public static final int ART_NEM_SS_ZONE2_ADDR      = 0x2EDE4; // 206 bytes
    public static final int ART_NEM_SS_ZONE2_SIZE      = 206;
    public static final int ART_NEM_SS_ZONE3_ADDR      = 0x2EEB2; // 203 bytes
    public static final int ART_NEM_SS_ZONE3_SIZE      = 203;
    public static final int ART_NEM_SS_ZONE4_ADDR      = 0x2EF7E; // 199 bytes
    public static final int ART_NEM_SS_ZONE4_SIZE      = 199;
    public static final int ART_NEM_SS_ZONE5_ADDR      = 0x2F046; // 201 bytes
    public static final int ART_NEM_SS_ZONE5_SIZE      = 201;
    public static final int ART_NEM_SS_ZONE6_ADDR      = 0x2F110; // 207 bytes
    public static final int ART_NEM_SS_ZONE6_SIZE      = 207;
    public static final int ART_NEM_SS_RESULT_EM_ADDR  = 0x2F74A; // 382 bytes
    public static final int ART_NEM_SS_RESULT_EM_SIZE  = 382;

    // Background art (Nemesis compressed)
    public static final int ART_NEM_SS_BG_CLOUD_ADDR   = 0x2E48A; // 1268 bytes
    public static final int ART_NEM_SS_BG_CLOUD_SIZE   = 1268;
    public static final int ART_NEM_SS_BG_FISH_ADDR    = 0x2D4FA; // 3215 bytes
    public static final int ART_NEM_SS_BG_FISH_SIZE    = 3215;

    // Background tilemaps (Enigma compressed)
    public static final int ENI_SS_BG1_ADDR            = 0x2D3C6; // 308 bytes
    public static final int ENI_SS_BG1_SIZE            = 308;
    public static final int ENI_SS_BG2_ADDR            = 0x2E18A; // 768 bytes
    public static final int ENI_SS_BG2_SIZE            = 768;

    // ---- Special Stage ArtTile bases (from Constants.asm) ----
    public static final int ARTTILE_SS_BG_CLOUDS       = 0x000;
    public static final int ARTTILE_SS_BG_FISH         = 0x051;
    public static final int ARTTILE_SS_WALL            = 0x142;
    public static final int ARTTILE_SS_BUMPER          = 0x23B;
    public static final int ARTTILE_SS_GOAL            = 0x251;
    public static final int ARTTILE_SS_UP_DOWN         = 0x263;
    public static final int ARTTILE_SS_R_BLOCK         = 0x2F0;
    public static final int ARTTILE_SS_EXTRA_LIFE      = 0x370;
    public static final int ARTTILE_SS_EMERALD_SPARKLE = 0x3F0;
    public static final int ARTTILE_SS_RED_WHITE       = 0x470;
    public static final int ARTTILE_SS_GHOST           = 0x4F0;
    public static final int ARTTILE_SS_W_BLOCK         = 0x570;
    public static final int ARTTILE_SS_GLASS           = 0x5F0;
    public static final int ARTTILE_SS_EMERALD         = 0x770;
    public static final int ARTTILE_SS_ZONE_1          = 0x797;
    public static final int ARTTILE_SS_ZONE_2          = 0x7A0;
    public static final int ARTTILE_SS_ZONE_3          = 0x7A9;
    public static final int ARTTILE_SS_ZONE_4          = 0x7B2;
    public static final int ARTTILE_SS_ZONE_5          = 0x7BB;
    public static final int ARTTILE_SS_ZONE_6          = 0x7C4;

    // ---- Special Stage BG animation data (from SS_BGAnimate, sonic.asm) ----

    /**
     * byte_4CCC: 10 sine oscillator amplitude/speed pairs.
     * Each oscillator has: amplitude (unsigned byte), speed (signed byte).
     * ROM data: 8,2, 4,$FF, 2,3, 8,$FF, 4,2, 2,3, 8,$FD, 4,2, 2,3, 2,$FF
     */
    public static final int[] SS_BG_SINE_AMPLITUDES = {8, 4, 2, 8, 4, 2, 8, 4, 2, 2};
    public static final int[] SS_BG_SINE_SPEEDS = {2, -1, 3, -1, 2, 3, -3, 2, 3, -1};

    /**
     * byte_4CB8: Sine band widths. First byte = band count - 1, rest = scanline heights.
     * Widths: 40,24,16,40,24,16,48,24,8,16 = 256 total.
     */
    public static final int[] SS_SINE_BAND_WIDTHS = {9, 0x28, 0x18, 0x10, 0x28, 0x18, 0x10, 0x30, 0x18, 8, 0x10};

    /**
     * byte_4CC4: Scroll band widths for band-scroll transitions.
     * First byte = band count - 1, rest = scanline heights.
     * Widths: 48,48,48,40,24,24,24 = 256 total.
     */
    public static final int[] SS_SCROLL_BAND_WIDTHS = {6, 0x30, 0x30, 0x30, 0x28, 0x18, 0x18, 0x18};

    // ---- Special Stage physics constants (from Obj09) ----
    public static final int SS_ACCEL           = 0x0C;   // movement acceleration
    public static final int SS_BRAKE           = 0x40;   // braking deceleration
    public static final int SS_MAX_SPEED       = 0x800;  // max inertia
    public static final int SS_JUMP_FORCE      = 0x680;  // jump force multiplier
    public static final int SS_GRAVITY         = 0x2A;   // gravity multiplier
    public static final int SS_BUMPER_FORCE    = 0x700;  // bumper bounce force
    public static final int SS_INIT_ROTATION   = 0x40;   // initial rotation speed
    public static final int SS_UP_DOWN_COOLDOWN = 0x1E;  // UP/DOWN/R block cooldown frames
}
