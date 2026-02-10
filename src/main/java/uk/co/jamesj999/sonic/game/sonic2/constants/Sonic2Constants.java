package uk.co.jamesj999.sonic.game.sonic2.constants;

import java.util.HashMap;
import java.util.Map;

public class Sonic2Constants {
    public static final int DEFAULT_ROM_SIZE = 0x100000; // 1MB
    public static final int DEFAULT_LEVEL_LAYOUT_DIR_ADDR = 0x045A80;
    public static final int LEVEL_LAYOUT_DIR_ADDR_LOC = 0xE46E;
    public static final int LEVEL_LAYOUT_DIR_SIZE = 68;
    public static final int LEVEL_SELECT_ADDR = 0x9454;
    public static final int LEVEL_DATA_DIR = 0x42594;
    public static final int LEVEL_DATA_DIR_ENTRY_SIZE = 12;
    public static final int LEVEL_PALETTE_DIR = 0x2782;
    public static final int SONIC_TAILS_PALETTE_ADDR = 0x29E2;
    public static final int COLLISION_LAYOUT_DIR_ADDR = 0x49E8;
    public static final int ALT_COLLISION_LAYOUT_DIR_ADDR = 0x4A2C;
    public static final int OBJECT_LAYOUT_DIR_ADDR = 0x44D34;
    public static final int SOLID_TILE_VERTICAL_MAP_ADDR = 0x42E50;
    public static final int SOLID_TILE_HORIZONTAL_MAP_ADDR = 0x43E50;
    public static final int SOLID_TILE_MAP_SIZE = 0x1000;
    public static final int SOLID_TILE_ANGLE_ADDR = 0x42D50;
    public static final int SOLID_TILE_ANGLE_SIZE = 0x100;
    public static final int LEVEL_BOUNDARIES_ADDR = 0xC054;
    public static final int MUSIC_PLAYLIST_ADDR = 0x3EA0;
    public static final int ANIM_PAT_MAPS_ADDR = 0x40350;

    // Player sprite art (Rev01, no header)
    public static final int ART_UNC_SONIC_ADDR = 0x50000;
    public static final int ART_UNC_SONIC_SIZE = 0x14320;
    public static final int ART_UNC_TAILS_ADDR = 0x64320;
    public static final int ART_UNC_TAILS_SIZE = 0x0B8C0;
    public static final int MAP_UNC_SONIC_ADDR = 0x6FBE0;
    public static final int MAP_R_UNC_SONIC_ADDR = 0x714E0;
    public static final int MAP_UNC_TAILS_ADDR = 0x739E2;
    public static final int MAP_R_UNC_TAILS_ADDR = 0x7446C;
    public static final int ART_TILE_SONIC = 0x0780;
    public static final int ART_TILE_TAILS = 0x07A0;
    public static final int ART_UNC_SPLASH_DUST_ADDR = 0x71FFC;
    public static final int ART_UNC_SPLASH_DUST_SIZE = 0x1940;
    public static final int MAP_UNC_OBJ08_ADDR = 0x1DF5E;
    public static final int MAP_R_UNC_OBJ08_ADDR = 0x1E074;
    public static final int ART_TILE_SONIC_DUST = 0x049C;
    public static final int ART_TILE_TAILS_DUST = 0x048C;
    public static final int ART_TILE_TAILS_TAILS = 0x07B0;
    // Object art (Nemesis) + mappings
    public static final int ART_NEM_MONITOR_ADDR = 0x79550;
    public static final int ART_NEM_SONIC_LIFE_ADDR = 0x79346;
    public static final int ART_NEM_TAILS_LIFE_ADDR = 0x7C20C;
    public static final int ART_NEM_EXPLOSION_ADDR = 0x7B592;
    public static final int ART_TILE_EXPLOSION = 0x05A4;
    public static final int ART_NEM_SPIKES_ADDR = 0x7995C;
    public static final int ART_NEM_SPIKES_SIDE_ADDR = 0x7AC9A;
    public static final int ART_NEM_SPRING_VERTICAL_ADDR = 0x78E84;
    public static final int ART_NEM_SPRING_HORIZONTAL_ADDR = 0x78FA0;
    public static final int ART_NEM_SPRING_DIAGONAL_ADDR = 0x7906A;
    public static final int MAP_UNC_MONITOR_ADDR = 0x12D36;
    public static final int MAP_UNC_SPIKES_ADDR = 0x15B68;
    public static final int MAP_UNC_SPRING_ADDR = 0x1901C;
    public static final int MAP_UNC_SPRING_RED_ADDR = 0x19032;
    public static final int ANI_OBJ26_ADDR = 0x12CCE;
    public static final int ANI_OBJ26_SCRIPT_COUNT = 0x0B;
    public static final int ANI_OBJ41_ADDR = 0x18FE2;
    public static final int ANI_OBJ41_SCRIPT_COUNT = 0x06;
    public static final int SONIC_ANIM_DATA_ADDR = 0x01B618;
    public static final int SONIC_ANIM_SCRIPT_COUNT = 0x22;
    public static final int TAILS_ANIM_DATA_ADDR = 0x01D038;
    public static final int TAILS_ANIM_SCRIPT_COUNT = 0x21; // 33 scripts (0x00-0x20)

    public static final int ART_NEM_SHIELD_ADDR = 0x71D8E;
    // Bridge art (EHZ wooden bridge - 8 blocks)
    public static final int ART_NEM_BRIDGE_ADDR = 0xF052A;

    // Waterfall art (EHZ waterfall)
    public static final int ART_NEM_EHZ_WATERFALL_ADDR = 0xF02D6;
    public static final int ART_TILE_EHZ_WATERFALL = 0x39E;
    // Palette cycling (EHZ/ARZ water)
    public static final int CYCLING_PAL_EHZ_ARZ_WATER_ADDR = 0x001E7A;
    public static final int CYCLING_PAL_EHZ_ARZ_WATER_LEN = 0x20;

    // Palette cycling (CPZ - Chemical Plant Zone)
    public static final int CYCLING_PAL_CPZ1_ADDR = 0x002022;  // 9 frames × 6 bytes = 54 bytes (3 colors)
    public static final int CYCLING_PAL_CPZ1_LEN = 54;
    public static final int CYCLING_PAL_CPZ2_ADDR = 0x002058;  // 21 frames × 2 bytes = 42 bytes (1 color)
    public static final int CYCLING_PAL_CPZ2_LEN = 42;
    public static final int CYCLING_PAL_CPZ3_ADDR = 0x002082;  // 16 frames × 2 bytes = 32 bytes (1 color)
    public static final int CYCLING_PAL_CPZ3_LEN = 32;

    // Palette cycling (HTZ - Hill Top Zone) - Lava animation
    public static final int CYCLING_PAL_LAVA_ADDR = 0x001E9A;  // 16 frames × 8 bytes = 128 bytes
    public static final int CYCLING_PAL_LAVA_LEN = 128;

    // Palette cycling (MTZ - Metropolis Zone)
    public static final int CYCLING_PAL_MTZ1_ADDR = 0x001F2A;  // 6 frames × 2 bytes = 12 bytes
    public static final int CYCLING_PAL_MTZ1_LEN = 12;
    public static final int CYCLING_PAL_MTZ2_ADDR = 0x001F36;  // 3 frames × 4 bytes (2 colors + padding) = 12 bytes
    public static final int CYCLING_PAL_MTZ2_LEN = 12;
    public static final int CYCLING_PAL_MTZ3_ADDR = 0x001F42;  // 10 frames × 2 bytes = 20 bytes
    public static final int CYCLING_PAL_MTZ3_LEN = 20;

    // Palette cycling (OOZ - Oil Ocean Zone)
    public static final int CYCLING_PAL_OIL_ADDR = 0x001F76;  // 4 frames × 4 bytes = 16 bytes
    public static final int CYCLING_PAL_OIL_LEN = 16;

    // OOZ Oil Surface (Obj07) - ROM: s2.asm:49667-49672
    public static final int OIL_SURFACE_Y = 0x758;       // Default Y position of oil surface
    public static final int OIL_SUBMERSION_MAX = 0x30;    // 48 frames before suffocation
    public static final int OIL_WIDTH = 0x20;             // Platform half-width (tracks player X)

    // Palette cycling (MCZ - Mystic Cave Zone) - Lanterns
    public static final int CYCLING_PAL_LANTERN_ADDR = 0x001F86;  // 4 frames × 2 bytes = 8 bytes
    public static final int CYCLING_PAL_LANTERN_LEN = 8;

    // Palette cycling (CNZ - Casino Night Zone)
    public static final int CYCLING_PAL_CNZ1_ADDR = 0x001F8E;  // 3 frames × 12 bytes = 36 bytes (6 interleaved colors)
    public static final int CYCLING_PAL_CNZ1_LEN = 36;
    public static final int CYCLING_PAL_CNZ3_ADDR = 0x001FB2;  // 3 frames × 6 bytes = 18 bytes (3 interleaved colors)
    public static final int CYCLING_PAL_CNZ3_LEN = 18;
    public static final int CYCLING_PAL_CNZ4_ADDR = 0x001FC4;  // 18 frames × varying = 40 bytes
    public static final int CYCLING_PAL_CNZ4_LEN = 40;

    public static final int ART_NEM_INVINCIBILITY_STARS_ADDR = 0x71F14;
    public static final int MAP_UNC_INVINCIBILITY_STARS_ADDR = 0x1DCBC;
    public static final int ART_TILE_INVINCIBILITY_STARS = 0x05C0;

    public static final int MAP_UNC_OBJ18_A_ADDR = 0x107F6;
    public static final int MAP_UNC_OBJ18_B_ADDR = 0x1084E;

    // Falling Pillar (Object 0x23) - ARZ pillar that drops its lower section
    public static final int MAP_UNC_OBJ23_ADDR = 0x259E6;  // Obj23_MapUnc_259E6

    // Rising Pillar (Object 0x2B) - ARZ pillar that rises and launches player
    public static final int MAP_UNC_OBJ2B_ADDR = 0x25C6E;  // Obj2B_MapUnc_25C6E

    // Swinging Platform (Object 0x82) - ARZ swinging vine platform
    public static final int MAP_UNC_OBJ82_ADDR = 0x2A476;  // Obj82_MapUnc_2A476

    // MCZ Brick / Spike Ball (Object 0x75)
    public static final int MAP_UNC_OBJ75_ADDR = 0x28D8A;  // Obj75_MapUnc_28D8A

    // Rotating Platforms (Object 0x83) - ARZ (shared mappings with Obj15 SwingingPlatform)
    public static final int MAP_UNC_OBJ83_ADDR = 0x1021E;  // Obj15_Obj83_MapUnc_1021E

    // SwingingPlatform (Object 0x15) - chain-suspended platform in OOZ, ARZ, MCZ
    public static final int ART_NEM_OOZ_SWING_PLAT_ADDR = 0x80E26;  // ArtNem_OOZSwingPlat (verified via RomOffsetFinder)
    public static final int ART_TILE_OOZ_SWING_PLAT = 0x03E3;       // VRAM tile offset
    public static final int MAP_UNC_OBJ15_A_ADDR = 0x101E8;         // OOZ mappings (Obj15_MapUnc_101E8)
    public static final int MAP_UNC_OBJ15_MCZ_ADDR = 0x10256;       // MCZ mappings (Obj15_Obj7A_MapUnc_10256)
    public static final int MAP_UNC_OBJ15_TRAP_ADDR = 0x102DE;      // MCZ trap mappings (Obj15_MapUnc_102DE)

    public static final int ZONE_AQUATIC_RUIN = 2;

    // Checkpoint/Starpost (Object $79)
    public static final int ART_NEM_CHECKPOINT_ADDR = 0x79A86; // Star pole.nem
    public static final int MAP_UNC_CHECKPOINT_ADDR = 0x1F424; // obj79_a.asm (pole+dongle)
    public static final int MAP_UNC_CHECKPOINT_STAR_ADDR = 0x1F4A0; // obj79_b.asm (special stage stars)
    public static final int ANI_OBJ79_ADDR = 0x1F0C2; // Ani_obj79
    public static final int ANI_OBJ79_SCRIPT_COUNT = 3;
    public static final int ART_TILE_CHECKPOINT = 0x047C;

    // Signpost/Goal Plate (Object 0D)
    public static final int ART_NEM_SIGNPOST_ADDR = 0x79BDE; // Signpost.nem (78 blocks)

    // Egg Prison / Capsule (Object 0x3E)
    public static final int ART_NEM_EGG_PRISON_ADDR = 0x7BA32; // Egg Prison.nem (verified via RomOffsetFinder)
    public static final int MAP_UNC_EGG_PRISON_ADDR = 0x1F436;  // Obj3E_MapUnc_3F436 (relative offset in s2.asm)
    public static final int ART_TILE_SIGNPOST = 0x0434;

    // Results Screen Art (Obj3A)
    public static final int ART_UNC_HUD_NUMBERS_ADDR = 0x4134C; // Art_Hud (digits source)
    public static final int ART_UNC_HUD_NUMBERS_SIZE = 0x300; // 24 tiles (10 digits x 2 tiles + extras?)
    public static final int ART_UNC_LIVES_NUMBERS_ADDR = 0x4164C; // Small numbers for lives counter
    public static final int ART_UNC_LIVES_NUMBERS_SIZE = 320; // 10 tiles (0-9)
    // Debug font (italic hex digits 0-9, A-F with slashed zeros - leftover from
    // Sonic 1 level select)
    public static final int ART_UNC_DEBUG_FONT_ADDR = 0x45D74; // Art_Text (Debug font)
    public static final int ART_UNC_DEBUG_FONT_SIZE = 512; // 16 tiles (0-9, A-F) * 32 bytes each
    public static final int ART_NEM_HUD_ADDR = 0x7923E; // HUD.nem (SCORE/TIME/RING text)
    public static final int ART_NEM_TITLE_CARD_ADDR = 0x7D22C; // Title card.nem (E, N, O, Z letters)
    public static final int ART_NEM_TITLE_CARD2_ADDR = 0x7D58A; // Font using large broken letters.nem (other letters)

    // Level Select Art (Nemesis compressed)
    public static final int ART_NEM_MENU_BOX_ADDR = 0x7D990;           // Menu borders, boxes, text frames
    public static final int ART_NEM_LEVEL_SELECT_PICS_ADDR = 0x7DA10;  // Zone preview icons (15 icons)
    public static final int ART_NEM_FONT_STUFF_ADDR = 0x7C43A;         // Standard menu font

    // Level Select Mappings (Enigma compressed)
    public static final int MAP_ENI_LEVEL_SELECT_ADDR = 0x9ADE;        // Main screen layout (40x28 tiles)
    public static final int MAP_ENI_LEVEL_SELECT_ICON_ADDR = 0x9C32;   // Icon box layout (preview area)

    // Menu Background (Sonic/Miles) - uncompressed art + Enigma mappings
    public static final int ART_UNC_MENU_BACK_ADDR = 0x7CD2C;          // ArtUnc_MenuBack (40 tiles)
    public static final int ART_UNC_MENU_BACK_SIZE = 1280;             // 40 tiles * 32 bytes
    public static final int MAP_ENI_MENU_BACK_ADDR = 0x7CB80;          // MapEng_MenuBack (40x28 tiles)
    public static final int MAP_ENI_MENU_BACK_SIZE = 428;              // Enigma-compressed map size

    // Level Select Palettes (uncompressed)
    public static final int PAL_MENU_ADDR = 0x30E2;                    // Pal_Menu - 4 palette lines (128 bytes)
    public static final int PAL_MENU_SIZE = 128;                       // 4 lines * 16 colors * 2 bytes
    public static final int PAL_LEVEL_ICONS_ADDR = 0x9880;             // 15 icon palettes (32 bytes each)
    public static final int PAL_LEVEL_ICONS_SIZE = 480;                // 15 * 32 bytes
    // Title Screen Art (Nemesis compressed)
    public static final int ART_NEM_TITLE_ADDR = 0x74F6C;          // Background patterns
    public static final int ART_NEM_TITLE_SPRITES_ADDR = 0x7667A;  // ArtNem_TitleSprites (Sonic/Tails/stars, verified)
    public static final int ART_NEM_MENU_JUNK_ADDR = 0x78CBC;     // ArtNem_MenuJunk (extra sprite tiles at VRAM 0x03F2)

    // Credit Text Art (Nemesis compressed, 64 patterns)
    // Used for "SONIC AND MILES 'TAILS' PROWER IN" intro screen
    public static final int ART_NEM_CREDIT_TEXT_ADDR = 0xBD26;     // ArtNem_CreditText

    // Title Screen Sonic Palette (uncompressed, 32 bytes, loaded to palette line 0)
    public static final int PAL_TITLE_SONIC_ADDR = 0x133EC;        // Pal_133EC (Title Sonic.bin)
    public static final int PAL_TITLE_SONIC_SIZE = 32;

    // Title Screen Mappings (Enigma compressed)
    public static final int MAP_ENI_TITLE_SCREEN_ADDR = 0x74DC6;   // Plane B background (40x28)
    public static final int MAP_ENI_TITLE_BACK_ADDR = 0x74E3A;     // Plane B water/horizon (24x28, col 40+)
    public static final int MAP_ENI_TITLE_LOGO_ADDR = 0x74E86;     // Plane A logo/emblem (40x28)

    // Title Screen Palettes (uncompressed, 32 bytes each)
    // Pal_Title loaded at palette line 1 via PalLoad_ForFade (palptr Pal_Title, 1)
    public static final int PAL_TITLE_ADDR = 0x2942;
    public static final int PAL_TITLE_SIZE = 32;
    // Pal_1340C loaded at palette line 2 (Normal_palette_line3) by Obj0E intro animation
    // Used by Plane B background (make_art_tile palette 2)
    public static final int PAL_TITLE_BACKGROUND_ADDR = 0x1340C;
    public static final int PAL_TITLE_BACKGROUND_SIZE = 32;
    // Pal_1342C loaded at palette line 3 (Normal_palette_line4) by Obj0E intro animation
    // Used by Plane A logo/emblem (make_art_tile palette 3)
    public static final int PAL_TITLE_EMBLEM_ADDR = 0x1342C;
    public static final int PAL_TITLE_EMBLEM_SIZE = 32;

    public static final int ART_NEM_RESULTS_TEXT_ADDR = 0x7E86A; // End of level results text.nem
    public static final int ART_NEM_MINI_SONIC_ADDR = 0x7C0AA; // Sonic continue.nem (mini character)
    public static final int ART_NEM_PERFECT_ADDR = 0x7EEBE; // Perfect text.nem
    public static final int MAPPINGS_EOL_TITLE_CARDS_ADDR = 0x14CBC; // MapUnc_EOLTitleCards
    public static final int VRAM_BASE_NUMBERS = 0x520; // ArtTile_HUD_Bonus_Score
    public static final int VRAM_BASE_PERFECT = 0x540; // ArtTile_ArtNem_Perfect
    public static final int VRAM_BASE_TITLE_CARD = 0x580; // ArtTile_ArtNem_TitleCard
    public static final int VRAM_BASE_RESULTS_TEXT = 0x5B0; // ArtTile_ArtNem_ResultsText
    public static final int VRAM_BASE_MINI_CHARACTER = 0x5F4; // ArtTile_ArtNem_MiniCharacter
    public static final int VRAM_BASE_HUD_TEXT = 0x6CA; // ArtTile_ArtNem_HUD
    public static final int RESULTS_BONUS_DIGIT_TILES = 32; // 4 counters * 4 digits * 2 tiles
    public static final int RESULTS_BONUS_DIGIT_GROUP_TILES = 8; // 4 digits * 2 tiles

    public static final int ART_NEM_NUMBERS_ADDR = 0x799AC; // Numbers.nem (points)

    // EHZ Badnik Art (Nemesis compressed)
    public static final int ART_NEM_BUZZER_ADDR = 0x8316A; // 28 blocks
    public static final int ART_NEM_MASHER_ADDR = 0x839EA; // 22 blocks
    public static final int ART_NEM_COCONUTS_ADDR = 0x8A87A; // 38 blocks
    public static final int ART_NEM_ANIMAL_ADDR = 0x7FDD2; // Rabbit (Pocky) fallback

    // MCZ Badnik Art (Nemesis compressed)
    public static final int ART_NEM_CRAWLTON_ADDR = 0x8AB36; // Crawlton / Snake badnik from MCZ (verified)
    public static final int ART_NEM_FLASHER_ADDR = 0x8AC5E;  // Flasher / Firefly from MCZ (verified)

    // CPZ Badnik Art (Nemesis compressed)
    public static final int ART_NEM_SPINY_ADDR = 0x8B430; // Spiny (CPZ crawling badnik)
    public static final int ART_NEM_GRABBER_ADDR = 0x8B6B4; // Grabber (CPZ spider badnik)

    // ARZ Badnik Art (Nemesis compressed)
    public static final int ART_NEM_CHOPCHOP_ADDR = 0x89B9A; // ChopChop (piranha from ARZ)
    public static final int ART_NEM_WHISP_ADDR = 0x895E4;    // Whisp (blowfly from ARZ)
    public static final int ART_NEM_GROUNDER_ADDR = 0x8970E; // Grounder (drill badnik from ARZ)

    // HTZ Badnik Art (Nemesis compressed)
    public static final int ART_NEM_SPIKER_ADDR = 0x89FAA;   // Spiker (drill badnik from HTZ)
    public static final int ART_NEM_REXON_ADDR = 0x89DEC;    // Rexon (lava snake from HTZ)

    // OOZ Badnik Art (Nemesis compressed)
    public static final int ART_NEM_OCTUS_ADDR = 0x8336A;    // Octus (octopus badnik from OOZ)
    public static final int ART_NEM_AQUIS_ADDR = 0x8368A;    // Aquis (seahorse badnik from OOZ)

    // CNZ Badnik Art (Nemesis compressed)
    public static final int ART_NEM_CRAWL_ADDR = 0x901A4;    // Crawl (bouncer badnik from CNZ, 42 tiles)

    // Arrow Shooter (Object 0x22) - ARZ
    public static final int ART_NEM_ARROW_SHOOTER_ADDR = 0x82F74; // ArtNem_ArrowAndShooter
    public static final int MAP_UNC_ARROW_SHOOTER_ADDR = 0x25804; // Obj22_MapUnc_25804
    public static final int ART_TILE_ARROW_SHOOTER = 0x0417;      // ArtTile_ArtNem_ArrowAndShooter

    // MCZ Boss art
    public static final int ART_NEM_MCZ_BOSS_ADDR = 0x86B6E;         // ArtNem_MCZBoss (verified via RomOffsetFinder)
    public static final int ART_UNC_FALLING_ROCKS_ADDR = 0x894E4;    // ArtUnc_FallingRocks (256 bytes, verified)
    public static final int ART_TILE_MCZ_BOSS = 0x03C0;              // ArtTile_ArtNem_MCZBoss
    public static final int ART_TILE_FALLING_ROCKS = 0x0560;         // ArtTile_ArtUnc_FallingRocks
    public static final int MAP_UNC_MCZ_BOSS_ADDR = 0x316EC;         // Obj57_MapUnc_316EC (21 frames)
    public static final int PAL_MCZ_BOSS_ADDR = 0x3082;              // Pal_MCZ_B (32 bytes, verified)

    // Boss art (Nemesis compressed, verified offsets)
    public static final int ART_NEM_EGGPOD_ADDR = 0x83BF6;     // ArtNem_Eggpod (flying vehicle)
    public static final int ART_NEM_EHZ_BOSS_ADDR = 0x8507C;   // ArtNem_EHZBoss (ground vehicle/wheels/spike)
    public static final int ART_NEM_HTZ_BOSS_ADDR = 0x8595C;   // ArtNem_HTZBoss (flamethrower/lava ball components)
    public static final int ART_NEM_CNZ_BOSS_ADDR = 0x87AAC;   // ArtNem_CNZBoss (verified via RomOffsetFinder)
    public static final int ART_NEM_EGG_CHOPPERS_ADDR = 0x85868; // ArtNem_EggChoppers (propeller blades)
    public static final int ART_NEM_CPZ_BOSS_ADDR = 0x84332;   // ArtNem_CPZBoss (verified via RomOffsetFinder)
    public static final int ART_NEM_EGGPOD_JETS_ADDR = 0x84F18; // ArtNem_EggpodJets (verified via RomOffsetFinder)
    public static final int ART_NEM_BOSS_SMOKE_ADDR = 0x84F96;  // ArtNem_BossSmoke (verified via RomOffsetFinder)
    public static final int ART_NEM_FIERY_EXPLOSION_ADDR = 0x84890; // ArtNem_FieryExplosion (Obj58, verified)
    public static final int ART_NEM_ARZ_BOSS_ADDR = 0x86128;    // ArtNem_ARZBoss (verified via RomOffsetFinder)

    // Boss art tile bases (VRAM pattern IDs)
    public static final int ART_TILE_EGGPOD = 0x03A0;      // ArtTile_ArtNem_Eggpod_1
    public static final int ART_TILE_EHZ_BOSS = 0x0400;    // ArtTile_ArtNem_EHZBoss
    public static final int ART_TILE_EGG_CHOPPERS = 0x056C; // ArtTile_ArtNem_EggChoppers
    public static final int ART_TILE_EGGPOD_3 = 0x0420;    // ArtTile_ArtNem_Eggpod_3 (CPZ boss)
    public static final int ART_TILE_EGGPOD_JETS_1 = 0x0418; // ArtTile_ArtNem_EggpodJets_1 (CPZ boss)
    public static final int ART_TILE_CPZ_BOSS = 0x0500;    // ArtTile_ArtNem_CPZBoss
    public static final int ART_TILE_BOSS_SMOKE_1 = 0x0570; // ArtTile_ArtNem_BossSmoke_1
    public static final int ART_TILE_FIERY_EXPLOSION = 0x0580; // ArtTile_ArtNem_FieryExplosion
    public static final int ART_TILE_ARZ_BOSS = 0x03E0;    // ArtTile_ArtNem_ARZBoss
    public static final int ART_TILE_EGGPOD_4 = 0x0500;   // ArtTile_ArtNem_Eggpod_4 (ARZ/MCZ/CNZ/MTZ boss)
    public static final int ART_TILE_CNZ_BOSS = 0x0407;   // ArtTile_ArtNem_CNZBoss
    public static final int ART_TILE_CNZ_BOSS_FUDGE = 0x03A7; // ArtTile_ArtNem_CNZBoss_Fudge (= 0x0407 - 0x60)
    public static final int ART_TILE_HTZ_BOSS = 0x0421;  // ArtTile_ArtNem_HTZBoss (flamethrower/lava ball)
    public static final int ART_TILE_EGGPOD_2 = 0x03C1;  // ArtTile_ArtNem_Eggpod_2 (HTZ boss uses this)

    // CNZ Boss mappings (uncompressed)
    public static final int MAP_UNC_CNZ_BOSS_ADDR = 0x320EA;  // Obj51_MapUnc_320EA (21 frames)

    // CNZ Boss palette cycling (addresses from disassembly)
    public static final int CYCLING_PAL_CNZ_BOSS1_ADDR = 0x1FEC;  // CyclingPal_CNZ1_B (boss cycle 1)
    public static final int CYCLING_PAL_CNZ_BOSS1_LEN = 18;
    public static final int CYCLING_PAL_CNZ_BOSS2_ADDR = 0x1FFE;  // CyclingPal_CNZ2_B (boss cycle 2)
    public static final int CYCLING_PAL_CNZ_BOSS2_LEN = 20;
    public static final int CYCLING_PAL_CNZ_BOSS3_ADDR = 0x2012;  // CyclingPal_CNZ3_B (boss cycle 3)
    public static final int CYCLING_PAL_CNZ_BOSS3_LEN = 16;

    // CPZ Boss mappings (uncompressed)
    public static final int MAP_UNC_CPZ_BOSS_PARTS_ADDR = 0x2EADC;   // Obj5D_MapUnc_2EADC (CPZ boss parts)
    public static final int MAP_UNC_CPZ_BOSS_EGGPOD_ADDR = 0x2ED8C;  // Obj5D_MapUnc_2ED8C (eggpod body)
    public static final int MAP_UNC_CPZ_BOSS_JETS_ADDR = 0x2EE88;    // Obj5D_MapUnc_2EE88 (eggpod jets)
    public static final int MAP_UNC_CPZ_BOSS_SMOKE_ADDR = 0x2EEA0;   // Obj5D_MapUnc_2EEA0 (boss smoke)
    public static final int MAP_UNC_BOSS_EXPLOSION_ADDR = 0x2D50A;   // Obj58_MapUnc_2D50A (boss explosion)
    public static final int MAP_UNC_ARZ_BOSS_PARTS_ADDR = 0x30D68;   // Obj89_MapUnc_30D68 (ARZ boss parts)
    public static final int MAP_UNC_ARZ_BOSS_MAIN_ADDR = 0x30E04;    // Obj89_MapUnc_30E04 (ARZ boss main)

    // HTZ Boss mappings (uncompressed)
    public static final int MAP_UNC_HTZ_BOSS_SMOKE_ADDR = 0x30258;  // Obj52_MapUnc_30258 (smoke particles)
    public static final int MAP_UNC_HTZ_BOSS_MAIN_ADDR = 0x302BC;   // Obj52_MapUnc_302BC (main boss, flamethrower, lava ball)

    // Animal art (Nemesis compressed, verified offsets)
    public static final int ART_NEM_FLICKY_ADDR = 0x7EF60;
    public static final int ART_NEM_SQUIRREL_ADDR = 0x7F0A2;
    public static final int ART_NEM_MOUSE_ADDR = 0x7F206;
    public static final int ART_NEM_CHICKEN_ADDR = 0x7F340;
    public static final int ART_NEM_MONKEY_ADDR = 0x7F4A2;
    public static final int ART_NEM_EAGLE_ADDR = 0x7F5E2;
    public static final int ART_NEM_PIG_ADDR = 0x7F710;
    public static final int ART_NEM_SEAL_ADDR = 0x7F846;
    public static final int ART_NEM_PENGUIN_ADDR = 0x7F962;
    public static final int ART_NEM_TURTLE_ADDR = 0x7FADE;
    public static final int ART_NEM_BEAR_ADDR = 0x7FC90;
    public static final int ART_NEM_RABBIT_ADDR = 0x7FDD2;

    // Springboard / Lever Spring (Object 0x40) - CPZ, ARZ, MCZ
    public static final int ART_NEM_LEVER_SPRING_ADDR = 0x7AB4A;  // ArtNem_LeverSpring

    // CNZ Bumpers (addresses from Nemesis S2 art listing)
    public static final int ART_NEM_HEX_BUMPER_ADDR = 0x81894; // ArtNem_CNZHexBumper - Hex Bumper (ObjD7) - 6 blocks
    public static final int ART_NEM_BUMPER_ADDR = 0x8191E; // ArtNem_CNZRoundBumper - Round Bumper (Obj44) - 24 blocks
    public static final int ART_NEM_BONUS_BLOCK_ADDR = 0x81DCC; // ArtNem_CNZMiniBumper - Bonus Block (ObjD8) - 28
                                                                // blocks

    // CNZ Map Bumpers (triangular bumpers embedded in level tiles)
    // ROM Reference: SpecialCNZBumpers at s2.asm line 32146
    public static final int CNZ_BUMPERS_ACT1_ADDR = 0x1781A; // SpecialCNZBumpers_Act1
    public static final int CNZ_BUMPERS_ACT2_ADDR = 0x1795E; // SpecialCNZBumpers_Act2
    public static final int ZONE_CNZ = 0x0C; // Casino Night Zone index (s2.constants.asm: casino_night_zone = $0C)

    // CNZ Flipper (Object 0x86)
    public static final int ART_NEM_FLIPPER_ADDR = 0x81EF2; // ArtNem_CNZFlipper

    // CNZ Rect Blocks (Object 0xD2) - "Caterpiller" flashing blocks
    public static final int ART_NEM_CNZ_SNAKE_ADDR = 0x81600; // ArtNem_CNZSnake

    // CNZ Big Block (Object 0xD4) - Large 64x64 oscillating platform
    public static final int ART_NEM_CNZ_BIG_BLOCK_ADDR = 0x816C8; // ArtNem_BigMovingBlock

    // CNZ Elevator (Object 0xD5) - Vertical platform that moves when stood on
    public static final int ART_NEM_CNZ_ELEVATOR_ADDR = 0x817B4; // ArtNem_CNZElevator

    // CNZ Point Pokey Cage (Object 0xD6) - Casino cage that awards points
    public static final int ART_NEM_CNZ_CAGE_ADDR = 0x81826; // ArtNem_CNZCage (verified)

    // CNZ Bonus Spike (Object 0xD3) - Spiky ball prize from slot machine
    public static final int ART_NEM_CNZ_BONUS_SPIKE_ADDR = 0x81668; // ArtNem_CNZBonusSpike (verified)

    // CNZ Slot Machine Pictures (uncompressed) - 6 faces × 512 bytes = 3072 bytes
    // 4×4 tiles (32×32 pixels) per face, 4bpp indexed color
    public static final int ART_UNC_CNZ_SLOT_PICS_ADDR = 0x4EEFE; // ArtUnc_CNZSlotPics (verified)
    public static final int ART_UNC_CNZ_SLOT_PICS_SIZE = 3072;    // 6 faces × 16 tiles × 32 bytes

    // CNZ LauncherSpring (Object 0x85) - pressure springs
    public static final int ART_NEM_CNZ_VERT_PLUNGER_ADDR = 0x81C96;  // Vertical spring art
    public static final int ART_NEM_CNZ_DIAG_PLUNGER_ADDR = 0x81AB0;  // Diagonal spring art
    public static final int ART_TILE_CNZ_VERT_PLUNGER = 0x0422;       // VRAM tile offset
    public static final int ART_TILE_CNZ_DIAG_PLUNGER = 0x0402;       // VRAM tile offset

    // Water Surface Art (Object $04 / SurfaceWater)
    // CPZ uses the same water surface art as HPZ (pink/purple chemical water)
    public static final int ART_NEM_WATER_SURFACE_CPZ_ADDR = 0x82364; // Top of water in HPZ and CPZ (24 blocks)
    // ARZ has distinct water surface art (natural blue water)
    public static final int ART_NEM_WATER_SURFACE_ARZ_ADDR = 0x82E02; // Top of water in ARZ (16 blocks)

    // Bubbles Art (Object $0A Small Bubbles, Object $24 Bubble Generator)
    // Small bubbles (Obj0A): 0x7AEE2 (10 tiles) - breathing bubbles from player's mouth
    // Bubble generator (Obj24): 0x7AD16 (37 tiles) - air bubble generator object
    // Countdown numbers: 0x7AF82 (uncompressed)
    public static final int ART_NEM_BUBBLES_ADDR = 0x7AEE2;  // ArtNem_Bubbles - small breathing bubbles
    public static final int ART_NEM_BUBBLE_GENERATOR_ADDR = 0x7AD16;  // ArtNem_BubbleGenerator - large bubbles (37 tiles)
    public static final int ART_UNC_COUNTDOWN_ADDR = 0x7AF82;  // Countdown numbers for drowning (uncompressed)
    public static final int MAP_UNC_SMALL_BUBBLES_ADDR = 0x21FD6;  // Obj0A_MapUnc - small breathing bubbles
    public static final int MAP_UNC_BUBBLES_ADDR = 0x1FCA2;  // Obj24_MapUnc - bubble generator / countdown bubbles
    public static final int ART_TILE_BUBBLES = 0x055B;  // ArtTile_ArtNem_BigBubbles - VRAM tile base (original)

    // Leaves Art (Object $2C LeavesGenerator - ARZ falling leaves)
    public static final int ART_NEM_LEAVES_ADDR = 0x82EE8;  // ArtNem_Leaves - falling leaves (7 tiles)

    // CPZ Speed Booster (Object 0x1B)
    public static final int ART_NEM_SPEED_BOOSTER_ADDR = 0x824D4;  // ArtNem_CPZBooster (verified)
    public static final int MAP_UNC_SPEED_BOOSTER_ADDR = 0x223E2;  // Obj1B_MapUnc

    // CPZ Pipe Exit Spring (Object 0x7B) - warp tube exit spring
    public static final int ART_NEM_PIPE_EXIT_SPRING_ADDR = 0x82C06;  // ArtNem_CPZTubeSpring (verified)

    // CPZ Tipping Floor (Object 0x0B) - Small yellow platform that tips
    public static final int ART_NEM_CPZ_ANIMATED_BITS_ADDR = 0x82864;  // ArtNem_CPZAnimatedBits (verified)

    // Barrier (Object 0x2D) - One-way rising barrier
    public static final int ART_NEM_CONSTRUCTION_STRIPES_ADDR = 0x827F8;  // ArtNem_ConstructionStripes (CPZ/DEZ)
    public static final int ART_NEM_ARZ_BARRIER_ADDR = 0x830D2;           // ArtNem_ARZBarrierThing
    public static final int ART_NEM_HTZ_VALVE_BARRIER_ADDR = 0xF08F6;    // ArtNem_HtzValveBarrier (HTZ subtype 0)
    public static final int MAP_UNC_BARRIER_ADDR = 0x11822;               // Obj2D_MapUnc_11822 (Enigma)

    // CPZ BlueBalls (Object 0x1D) - Bouncing water droplet hazard
    public static final int ART_NEM_CPZ_DROPLET_ADDR = 0x8253C;  // ArtNem_CPZDroplet (verified)

    // Breakable Block (Object 0x32) - CPZ metal blocks / HTZ rocks
    public static final int ART_NEM_CPZ_METAL_BLOCK_ADDR = 0x827B8;  // ArtNem_CPZMetalBlock (verified)
    public static final int ART_NEM_HTZ_ROCK_ADDR = 0x0F0C14;        // ArtNem_HtzRock (verified)
    public static final int MAP_UNC_OBJ32_HTZ_ADDR = 0x23852;        // Obj32_MapUnc_23852 (HTZ rock)
    public static final int MAP_UNC_OBJ32_CPZ_ADDR = 0x23886;        // Obj32_MapUnc_23886 (CPZ metal block)

    // HTZ Dynamic Background Art (loaded at runtime based on camera position)
    public static final int ART_NEM_HTZ_CLIFFS_ADDR = 0x49A14;       // ArtNem_HTZCliffs (Nemesis, ~6KB decompressed)
    public static final int ART_UNC_HTZ_CLOUDS_ADDR = 0x4A33E;       // ArtUnc_HTZClouds (Uncompressed, 1024 bytes)
    public static final int ART_UNC_HTZ_CLOUDS_SIZE = 1024;          // 32 tiles × 32 bytes

    // CPZ/OOZ/WFZ Moving Platform (Object 0x19)
    public static final int MAP_UNC_OBJ19_ADDR = 0x2222A;  // Obj19_MapUnc_2222A
    public static final int ART_NEM_CPZ_ELEVATOR_ADDR = 0x82216;    // ArtNem_CPZElevator (verified)
    public static final int ART_NEM_OOZ_ELEVATOR_ADDR = 0x810B8;    // ArtNem_OOZElevator (verified)

    // CPZ Staircase (Object 0x78) - shares appearance with CPZ platform
    public static final int ART_NEM_CPZ_STAIRBLOCK_ADDR = 0x82A46;  // ArtNem_CPZStairBlock (Moving block from CPZ)

    // CPZ Pylon (Object 0x7C) - decorative background pylon
    public static final int ART_NEM_CPZ_METAL_THINGS_ADDR = 0x825AE;  // ArtNem_CPZMetalThings (verified)
    public static final int ART_NEM_WFZ_PLATFORM_ADDR = 0x8D96E;    // ArtNem_WfzFloatingPlatform (verified)
    public static final int ART_TILE_CPZ_ELEVATOR = 0x03A0;  // palette 3
    public static final int ART_TILE_OOZ_ELEVATOR = 0x02F4;  // palette 3
    public static final int ART_TILE_WFZ_PLATFORM = 0x046D;  // palette 1, priority

    // Zone indices (from s2.constants.asm zoneID macro)
    public static final int ZONE_CHEMICAL_PLANT = 0x0D;  // chemical_plant_zone
    public static final int ZONE_OIL_OCEAN = 0x0A;       // oil_ocean_zone
    public static final int ZONE_WING_FORTRESS = 0x06;   // wing_fortress_zone
    public static final int ZONE_MYSTIC_CAVE = 0x0B;     // mystic_cave_zone
    public static final int ZONE_ARZ = 0x0F;             // aquatic_ruin_zone

    // MovingVine (Object 0x80) - MCZ vine / WFZ hook
    // ROM Reference: s2.asm lines 56145-56412 (Obj80 code)
    public static final int ART_NEM_VINE_PULLEY_ADDR = 0xF1D5C;    // ArtNem_VinePulley (MCZ vine art)
    public static final int ART_NEM_WFZ_HOOK_ADDR = 0x8D388;        // ArtNem_WfzHook (WFZ hook art)
    public static final int MAP_UNC_OBJ80_MCZ_ADDR = 0x29C64;       // Obj80_MapUnc_29C64 (MCZ mappings, 7 frames)
    public static final int MAP_UNC_OBJ80_WFZ_ADDR = 0x29DD0;       // Obj80_MapUnc_29DD0 (WFZ mappings, 13 frames)
    public static final int ART_TILE_VINE_PULLEY = 0x041E;          // ArtTile_ArtNem_VinePulley (MCZ palette 3)
    public static final int ART_TILE_WFZ_HOOK = 0x03FA;             // ArtTile_ArtNem_WfzHook (WFZ palette 1)
    public static final int ART_TILE_WFZ_HOOK_FUDGE = 0x03FE;       // ArtTile_ArtNem_WfzHook_Fudge (bad mappings offset)

    // VineSwitch (Object 0x7F) - MCZ pull switch
    // ROM Reference: s2.asm lines 56022-56132 (Obj7F code)
    public static final int ART_NEM_VINE_SWITCH_ADDR = 0xF1C64;     // ArtNem_VineSwitch (Pull switch from MCZ.nem)
    public static final int MAP_UNC_OBJ7F_ADDR = 0x29938;           // Obj7F_MapUnc_29938 (2 frames)
    public static final int ART_TILE_VINE_SWITCH = 0x040E;          // ArtTile_ArtNem_VineSwitch (MCZ palette 3)

    // MCZRotPforms (Object 0x6A) - MCZ wooden crate / MTZ moving platform
    // ROM Reference: s2.asm lines 53645-53850 (Obj6A code)
    public static final int ART_NEM_CRATE_ADDR = 0xF187C;           // ArtNem_Crate (Large wooden box from MCZ.nem)
    public static final int ART_TILE_CRATE = 0x03C8;                // ArtTile_ArtNem_Crate (MCZ palette 3)

    // MCZDrawbridge (Object 0x81) - MCZ rotatable drawbridge
    // ROM Reference: s2.asm lines 56420-56617 (Obj81 code)
    public static final int ART_NEM_MCZ_GATE_LOG_ADDR = 0xF1E06;    // ArtNem_MCZGateLog (Drawbridge logs from MCZ.nem)
    public static final int MAP_UNC_OBJ81_ADDR = 0x2A24E;           // Obj81_MapUnc_2A24E (2 frames)
    public static final int ART_TILE_MCZ_GATE_LOG = 0x043C;         // ArtTile_ArtNem_MCZGateLog (MCZ palette 3)

    // OOZPoppingPform (Object 0x33) - green burner platform
    public static final int ART_NEM_BURNER_LID_ADDR = 0x80274;   // ArtNem_BurnerLid (verified via RomOffsetFinder)
    public static final int ART_NEM_OOZ_BURN_ADDR = 0x81514;     // ArtNem_OOZBurn (verified via RomOffsetFinder)

    // OOZ Launcher (Object 0x3D) - striped blocks that launch rolling player
    public static final int ART_NEM_STRIPED_BLOCKS_VERT_ADDR = 0x8030A;  // ArtNem_StripedBlocksVert (CPZ)
    public static final int ART_NEM_STRIPED_BLOCKS_HORIZ_ADDR = 0x81048; // ArtNem_StripedBlocksHoriz (OOZ)

    // Fan (Object 0x3F) - OOZ wind fan (horizontal and vertical variants share art)
    public static final int ART_NEM_OOZ_FAN_ADDR = 0x81254;  // ArtNem_OOZFanHoriz (verified via RomOffsetFinder)

    // LauncherBall (Object 0x48) - OOZ transporter ball
    public static final int ART_NEM_LAUNCH_BALL_ADDR = 0x806E0;  // ArtNem_LaunchBall (verified via RomOffsetFinder)

    // Collapsing Platform art (Object 0x1F)
    public static final int ART_NEM_OOZ_COLLAPSING_PLATFORM_ADDR = 0x809D0;  // ArtNem_OOZPlatform
    public static final int ART_NEM_MCZ_COLLAPSING_PLATFORM_ADDR = 0xF1ABA;  // ArtNem_MCZCollapsePlat

    // HTZ/EHZ Level Resource Composition (from SonLVL.ini)
    // HTZ uses EHZ_HTZ base data with HTZ-specific overlays.
    // Reference: docs/s2disasm/SonLVL.ini [Hill Top Zone Act 1/2]
    //
    // TERMINOLOGY NOTE: SonLVL uses inverted terminology from this engine!
    //   - SonLVL "blocks" (16x16) = Engine "chunks"
    //   - SonLVL "chunks" (128x128) = Engine "blocks"
    // The constants below use ENGINE terminology (chunks=16x16, blocks=128x128).

    // Foreground 8x8 patterns (Kosinski-compressed):
    // SonLVL: tiles=../art/kosinski/EHZ_HTZ.bin|../art/kosinski/HTZ_Supp.bin:0x3F80
    public static final int HTZ_PATTERNS_BASE_ADDR = 0x095C24;      // Base patterns (shared EHZ/HTZ)
    public static final int HTZ_PATTERNS_OVERLAY_ADDR = 0x098AB4;   // HTZ supplemental patterns
    public static final int HTZ_PATTERNS_OVERLAY_OFFSET = 0x3F80;   // Byte offset for HTZ pattern overlay

    // 16x16 chunk mappings (engine terminology - SonLVL calls these "blocks"):
    // SonLVL: blocks=../mappings/16x16/EHZ.bin|../mappings/16x16/HTZ.bin:0x980
    public static final int HTZ_CHUNKS_BASE_ADDR = 0x094E74;        // Base 16x16 chunks (EHZ)
    public static final int HTZ_CHUNKS_OVERLAY_ADDR = 0x0985A4;     // HTZ supplemental 16x16 chunks
    public static final int HTZ_CHUNKS_OVERLAY_OFFSET = 0x0980;     // Byte offset for HTZ chunk overlay

    // 128x128 block mappings (engine terminology - SonLVL calls these "chunks"):
    // SonLVL: chunks=../mappings/128x128/EHZ_HTZ.bin
    public static final int HTZ_BLOCKS_ADDR = 0x099D34;             // Shared 128x128 blocks (no overlay)

    // Collision index arrays (shared between EHZ and HTZ):
    // colind1=../collision/EHZ and HTZ primary 16x16 collision index.bin
    // colind2=../collision/EHZ and HTZ secondary 16x16 collision index.bin
    public static final int HTZ_COLLISION_PRIMARY_ADDR = 0x044E50;    // Primary collision index
    public static final int HTZ_COLLISION_SECONDARY_ADDR = 0x044F40;  // Secondary collision index

    // HTZ ROM zone ID (from s2.constants.asm)
    public static final int ZONE_HTZ = 0x07;  // hill_top_zone

    // HTZ Seesaw (Object 0x14)
    public static final int ART_NEM_HTZ_SEESAW_ADDR = 0xF096E;  // Nemesis compressed
    public static final int ART_NEM_SOL_ADDR = 0xF0D4A;         // Ball uses Sol badnik art
    public static final int ART_NEM_HTZ_FIREBALL1_ADDR = 0xF0160; // ArtNem_HtzFireball1 (Sol fireball)

    // HTZ Zipline Lift (Object 0x16)
    public static final int ART_NEM_HTZ_ZIPLINE_ADDR = 0xF0602;  // Nemesis compressed

    // HTZ Dynamic Art Tile Indices (from s2.constants.asm)
    // These tiles are normally populated by Dynamic_HTZ at runtime
    // ArtTile_ArtUnc_HTZMountains = $0500 (24 tiles for mountain/cliff art)
    // ArtTile_ArtUnc_HTZClouds = $0518 (8 tiles for cloud art)
    public static final int HTZ_MOUNTAINS_TILE_INDEX = 0x0500;  // Tile index 1280
    public static final int HTZ_MOUNTAINS_TILE_COUNT = 0x18;    // 24 tiles
    public static final int HTZ_CLOUDS_TILE_INDEX = 0x0518;     // Tile index 1304
    public static final int HTZ_CLOUDS_TILE_COUNT = 8;          // 8 tiles
    public static final int HTZ_DYNAMIC_TILES_END = 0x0520;     // First tile after dynamic art (1312)

    public static final int[][] START_POSITIONS = {
            { 0x0060, 0x028F }, // 0 Emerald Hill 1 (EHZ_1.bin)
            { 0x0060, 0x02AF }, // 1 Emerald Hill 2 (EHZ_2.bin)
            { 0x0000, 0x0000 }, // 2 Unused (e.g. HPZ / WZ / etc. – not wired in final game)
            { 0x0000, 0x0000 }, // 3 Unused
            { 0x0060, 0x01EC }, // 4 Chemical Plant 1 (CPZ_1.bin)
            { 0x0000, 0x0000 }, // 5 Chemical Plant 2 (CPZ_2.bin – not fetched)
            { 0x0000, 0x0000 }, // 6 Aquatic Ruin 1 (ARZ_1.bin – not fetched)
            { 0x0000, 0x0000 }, // 7 Aquatic Ruin 2 (ARZ_2.bin – not fetched)
            { 0x0000, 0x0000 }, // 8 Casino Night 1 (CNZ_1.bin – not fetched)
            { 0x0000, 0x0000 }, // 9 Casino Night 2 (CNZ_2.bin – not fetched)
            { 0x0060, 0x03EF }, // 10 Hill Top 1 (HTZ_1.bin)
            { 0x0000, 0x0000 }, // 11 Hill Top 2 (HTZ_2.bin – not fetched)
            { 0x0060, 0x06AC }, // 12 Mystic Cave 1 (MCZ_1.bin)
            { 0x0000, 0x0000 }, // 13 Mystic Cave 2 (MCZ_2.bin – not fetched)
            { 0x0060, 0x06AC }, // 14 Oil Ocean 1 (OOZ_1.bin)
            { 0x0000, 0x0000 }, // 15 Oil Ocean 2 (OOZ_2.bin – not fetched)
            { 0x0060, 0x028C }, // 16 Metropolis 1 (MTZ_1.bin)
            { 0x0000, 0x0000 }, // 17 Metropolis 2 (MTZ_2.bin – not fetched)
            { 0x0000, 0x0000 }, // 18 Metropolis 3 (MTZ_3.bin – not fetched)
            { 0x0000, 0x0000 }, // 19 Unused
            { 0x0120, 0x0070 }, // 20 Sky Chase 1 (SCZ.bin)
            { 0x0000, 0x0000 }, // 21 Unused
            { 0x0060, 0x04CC }, // 22 Wing Fortress 1 (WFZ_1.bin)
            { 0x0000, 0x0000 }, // 23 Unused
            { 0x0060, 0x012D }, // 24 Death Egg 1 (DEZ_1.bin)
            { 0x0000, 0x0000 }, // 25 Unused
            { 0x0000, 0x0000 }, // 26 Special Stage
    };

    /**
     * Returns all integer constants as a name-to-value map.
     * Used by Sonic2RomOffsetProvider to avoid reflection.
     */
    public static Map<String, Integer> getAllOffsets() {
        Map<String, Integer> offsets = new HashMap<>();
        offsets.put("DEFAULT_ROM_SIZE", DEFAULT_ROM_SIZE);
        offsets.put("DEFAULT_LEVEL_LAYOUT_DIR_ADDR", DEFAULT_LEVEL_LAYOUT_DIR_ADDR);
        offsets.put("LEVEL_LAYOUT_DIR_ADDR_LOC", LEVEL_LAYOUT_DIR_ADDR_LOC);
        offsets.put("LEVEL_LAYOUT_DIR_SIZE", LEVEL_LAYOUT_DIR_SIZE);
        offsets.put("LEVEL_SELECT_ADDR", LEVEL_SELECT_ADDR);
        offsets.put("LEVEL_DATA_DIR", LEVEL_DATA_DIR);
        offsets.put("LEVEL_DATA_DIR_ENTRY_SIZE", LEVEL_DATA_DIR_ENTRY_SIZE);
        offsets.put("LEVEL_PALETTE_DIR", LEVEL_PALETTE_DIR);
        offsets.put("SONIC_TAILS_PALETTE_ADDR", SONIC_TAILS_PALETTE_ADDR);
        offsets.put("COLLISION_LAYOUT_DIR_ADDR", COLLISION_LAYOUT_DIR_ADDR);
        offsets.put("ALT_COLLISION_LAYOUT_DIR_ADDR", ALT_COLLISION_LAYOUT_DIR_ADDR);
        offsets.put("OBJECT_LAYOUT_DIR_ADDR", OBJECT_LAYOUT_DIR_ADDR);
        offsets.put("SOLID_TILE_VERTICAL_MAP_ADDR", SOLID_TILE_VERTICAL_MAP_ADDR);
        offsets.put("SOLID_TILE_HORIZONTAL_MAP_ADDR", SOLID_TILE_HORIZONTAL_MAP_ADDR);
        offsets.put("SOLID_TILE_MAP_SIZE", SOLID_TILE_MAP_SIZE);
        offsets.put("SOLID_TILE_ANGLE_ADDR", SOLID_TILE_ANGLE_ADDR);
        offsets.put("SOLID_TILE_ANGLE_SIZE", SOLID_TILE_ANGLE_SIZE);
        offsets.put("LEVEL_BOUNDARIES_ADDR", LEVEL_BOUNDARIES_ADDR);
        offsets.put("MUSIC_PLAYLIST_ADDR", MUSIC_PLAYLIST_ADDR);
        offsets.put("ANIM_PAT_MAPS_ADDR", ANIM_PAT_MAPS_ADDR);
        offsets.put("ART_UNC_SONIC_ADDR", ART_UNC_SONIC_ADDR);
        offsets.put("ART_UNC_SONIC_SIZE", ART_UNC_SONIC_SIZE);
        offsets.put("ART_UNC_TAILS_ADDR", ART_UNC_TAILS_ADDR);
        offsets.put("ART_UNC_TAILS_SIZE", ART_UNC_TAILS_SIZE);
        offsets.put("MAP_UNC_SONIC_ADDR", MAP_UNC_SONIC_ADDR);
        offsets.put("MAP_R_UNC_SONIC_ADDR", MAP_R_UNC_SONIC_ADDR);
        offsets.put("MAP_UNC_TAILS_ADDR", MAP_UNC_TAILS_ADDR);
        offsets.put("MAP_R_UNC_TAILS_ADDR", MAP_R_UNC_TAILS_ADDR);
        offsets.put("ART_TILE_SONIC", ART_TILE_SONIC);
        offsets.put("ART_TILE_TAILS", ART_TILE_TAILS);
        offsets.put("ART_UNC_SPLASH_DUST_ADDR", ART_UNC_SPLASH_DUST_ADDR);
        offsets.put("ART_UNC_SPLASH_DUST_SIZE", ART_UNC_SPLASH_DUST_SIZE);
        offsets.put("MAP_UNC_OBJ08_ADDR", MAP_UNC_OBJ08_ADDR);
        offsets.put("MAP_R_UNC_OBJ08_ADDR", MAP_R_UNC_OBJ08_ADDR);
        offsets.put("ART_TILE_SONIC_DUST", ART_TILE_SONIC_DUST);
        offsets.put("ART_TILE_TAILS_DUST", ART_TILE_TAILS_DUST);
        offsets.put("ART_TILE_TAILS_TAILS", ART_TILE_TAILS_TAILS);
        offsets.put("ART_NEM_MONITOR_ADDR", ART_NEM_MONITOR_ADDR);
        offsets.put("ART_NEM_SONIC_LIFE_ADDR", ART_NEM_SONIC_LIFE_ADDR);
        offsets.put("ART_NEM_TAILS_LIFE_ADDR", ART_NEM_TAILS_LIFE_ADDR);
        offsets.put("ART_NEM_EXPLOSION_ADDR", ART_NEM_EXPLOSION_ADDR);
        offsets.put("ART_TILE_EXPLOSION", ART_TILE_EXPLOSION);
        offsets.put("ART_NEM_SPIKES_ADDR", ART_NEM_SPIKES_ADDR);
        offsets.put("ART_NEM_SPIKES_SIDE_ADDR", ART_NEM_SPIKES_SIDE_ADDR);
        offsets.put("ART_NEM_SPRING_VERTICAL_ADDR", ART_NEM_SPRING_VERTICAL_ADDR);
        offsets.put("ART_NEM_SPRING_HORIZONTAL_ADDR", ART_NEM_SPRING_HORIZONTAL_ADDR);
        offsets.put("ART_NEM_SPRING_DIAGONAL_ADDR", ART_NEM_SPRING_DIAGONAL_ADDR);
        offsets.put("MAP_UNC_MONITOR_ADDR", MAP_UNC_MONITOR_ADDR);
        offsets.put("MAP_UNC_SPIKES_ADDR", MAP_UNC_SPIKES_ADDR);
        offsets.put("MAP_UNC_SPRING_ADDR", MAP_UNC_SPRING_ADDR);
        offsets.put("MAP_UNC_SPRING_RED_ADDR", MAP_UNC_SPRING_RED_ADDR);
        offsets.put("ANI_OBJ26_ADDR", ANI_OBJ26_ADDR);
        offsets.put("ANI_OBJ26_SCRIPT_COUNT", ANI_OBJ26_SCRIPT_COUNT);
        offsets.put("ANI_OBJ41_ADDR", ANI_OBJ41_ADDR);
        offsets.put("ANI_OBJ41_SCRIPT_COUNT", ANI_OBJ41_SCRIPT_COUNT);
        offsets.put("SONIC_ANIM_DATA_ADDR", SONIC_ANIM_DATA_ADDR);
        offsets.put("SONIC_ANIM_SCRIPT_COUNT", SONIC_ANIM_SCRIPT_COUNT);
        offsets.put("TAILS_ANIM_DATA_ADDR", TAILS_ANIM_DATA_ADDR);
        offsets.put("TAILS_ANIM_SCRIPT_COUNT", TAILS_ANIM_SCRIPT_COUNT);
        offsets.put("ART_NEM_SHIELD_ADDR", ART_NEM_SHIELD_ADDR);
        offsets.put("ART_NEM_BRIDGE_ADDR", ART_NEM_BRIDGE_ADDR);
        offsets.put("ART_NEM_EHZ_WATERFALL_ADDR", ART_NEM_EHZ_WATERFALL_ADDR);
        offsets.put("ART_TILE_EHZ_WATERFALL", ART_TILE_EHZ_WATERFALL);
        offsets.put("CYCLING_PAL_EHZ_ARZ_WATER_ADDR", CYCLING_PAL_EHZ_ARZ_WATER_ADDR);
        offsets.put("CYCLING_PAL_EHZ_ARZ_WATER_LEN", CYCLING_PAL_EHZ_ARZ_WATER_LEN);
        offsets.put("CYCLING_PAL_CPZ1_ADDR", CYCLING_PAL_CPZ1_ADDR);
        offsets.put("CYCLING_PAL_CPZ1_LEN", CYCLING_PAL_CPZ1_LEN);
        offsets.put("CYCLING_PAL_CPZ2_ADDR", CYCLING_PAL_CPZ2_ADDR);
        offsets.put("CYCLING_PAL_CPZ2_LEN", CYCLING_PAL_CPZ2_LEN);
        offsets.put("CYCLING_PAL_CPZ3_ADDR", CYCLING_PAL_CPZ3_ADDR);
        offsets.put("CYCLING_PAL_CPZ3_LEN", CYCLING_PAL_CPZ3_LEN);
        offsets.put("CYCLING_PAL_LAVA_ADDR", CYCLING_PAL_LAVA_ADDR);
        offsets.put("CYCLING_PAL_LAVA_LEN", CYCLING_PAL_LAVA_LEN);
        offsets.put("CYCLING_PAL_MTZ1_ADDR", CYCLING_PAL_MTZ1_ADDR);
        offsets.put("CYCLING_PAL_MTZ1_LEN", CYCLING_PAL_MTZ1_LEN);
        offsets.put("CYCLING_PAL_MTZ2_ADDR", CYCLING_PAL_MTZ2_ADDR);
        offsets.put("CYCLING_PAL_MTZ2_LEN", CYCLING_PAL_MTZ2_LEN);
        offsets.put("CYCLING_PAL_MTZ3_ADDR", CYCLING_PAL_MTZ3_ADDR);
        offsets.put("CYCLING_PAL_MTZ3_LEN", CYCLING_PAL_MTZ3_LEN);
        offsets.put("CYCLING_PAL_OIL_ADDR", CYCLING_PAL_OIL_ADDR);
        offsets.put("CYCLING_PAL_OIL_LEN", CYCLING_PAL_OIL_LEN);
        offsets.put("CYCLING_PAL_LANTERN_ADDR", CYCLING_PAL_LANTERN_ADDR);
        offsets.put("CYCLING_PAL_LANTERN_LEN", CYCLING_PAL_LANTERN_LEN);
        offsets.put("CYCLING_PAL_CNZ1_ADDR", CYCLING_PAL_CNZ1_ADDR);
        offsets.put("CYCLING_PAL_CNZ1_LEN", CYCLING_PAL_CNZ1_LEN);
        offsets.put("CYCLING_PAL_CNZ3_ADDR", CYCLING_PAL_CNZ3_ADDR);
        offsets.put("CYCLING_PAL_CNZ3_LEN", CYCLING_PAL_CNZ3_LEN);
        offsets.put("CYCLING_PAL_CNZ4_ADDR", CYCLING_PAL_CNZ4_ADDR);
        offsets.put("CYCLING_PAL_CNZ4_LEN", CYCLING_PAL_CNZ4_LEN);
        offsets.put("ART_NEM_TITLE_ADDR", ART_NEM_TITLE_ADDR);
        offsets.put("MAP_ENI_TITLE_SCREEN_ADDR", MAP_ENI_TITLE_SCREEN_ADDR);
        offsets.put("MAP_ENI_TITLE_BACK_ADDR", MAP_ENI_TITLE_BACK_ADDR);
        offsets.put("MAP_ENI_TITLE_LOGO_ADDR", MAP_ENI_TITLE_LOGO_ADDR);
        offsets.put("PAL_TITLE_ADDR", PAL_TITLE_ADDR);
        offsets.put("ART_NEM_INVINCIBILITY_STARS_ADDR", ART_NEM_INVINCIBILITY_STARS_ADDR);
        offsets.put("MAP_UNC_INVINCIBILITY_STARS_ADDR", MAP_UNC_INVINCIBILITY_STARS_ADDR);
        offsets.put("ART_TILE_INVINCIBILITY_STARS", ART_TILE_INVINCIBILITY_STARS);
        offsets.put("MAP_UNC_OBJ18_A_ADDR", MAP_UNC_OBJ18_A_ADDR);
        offsets.put("MAP_UNC_OBJ18_B_ADDR", MAP_UNC_OBJ18_B_ADDR);
        offsets.put("MAP_UNC_OBJ23_ADDR", MAP_UNC_OBJ23_ADDR);
        offsets.put("MAP_UNC_OBJ2B_ADDR", MAP_UNC_OBJ2B_ADDR);
        offsets.put("MAP_UNC_OBJ82_ADDR", MAP_UNC_OBJ82_ADDR);
        offsets.put("MAP_UNC_OBJ83_ADDR", MAP_UNC_OBJ83_ADDR);
        offsets.put("ART_NEM_OOZ_SWING_PLAT_ADDR", ART_NEM_OOZ_SWING_PLAT_ADDR);
        offsets.put("ART_TILE_OOZ_SWING_PLAT", ART_TILE_OOZ_SWING_PLAT);
        offsets.put("MAP_UNC_OBJ15_A_ADDR", MAP_UNC_OBJ15_A_ADDR);
        offsets.put("MAP_UNC_OBJ15_MCZ_ADDR", MAP_UNC_OBJ15_MCZ_ADDR);
        offsets.put("MAP_UNC_OBJ15_TRAP_ADDR", MAP_UNC_OBJ15_TRAP_ADDR);
        offsets.put("ZONE_AQUATIC_RUIN", ZONE_AQUATIC_RUIN);
        offsets.put("ART_NEM_CHECKPOINT_ADDR", ART_NEM_CHECKPOINT_ADDR);
        offsets.put("MAP_UNC_CHECKPOINT_ADDR", MAP_UNC_CHECKPOINT_ADDR);
        offsets.put("MAP_UNC_CHECKPOINT_STAR_ADDR", MAP_UNC_CHECKPOINT_STAR_ADDR);
        offsets.put("ANI_OBJ79_ADDR", ANI_OBJ79_ADDR);
        offsets.put("ANI_OBJ79_SCRIPT_COUNT", ANI_OBJ79_SCRIPT_COUNT);
        offsets.put("ART_TILE_CHECKPOINT", ART_TILE_CHECKPOINT);
        offsets.put("ART_NEM_SIGNPOST_ADDR", ART_NEM_SIGNPOST_ADDR);
        offsets.put("ART_NEM_EGG_PRISON_ADDR", ART_NEM_EGG_PRISON_ADDR);
        offsets.put("MAP_UNC_EGG_PRISON_ADDR", MAP_UNC_EGG_PRISON_ADDR);
        offsets.put("ART_TILE_SIGNPOST", ART_TILE_SIGNPOST);
        offsets.put("ART_UNC_HUD_NUMBERS_ADDR", ART_UNC_HUD_NUMBERS_ADDR);
        offsets.put("ART_UNC_HUD_NUMBERS_SIZE", ART_UNC_HUD_NUMBERS_SIZE);
        offsets.put("ART_UNC_LIVES_NUMBERS_ADDR", ART_UNC_LIVES_NUMBERS_ADDR);
        offsets.put("ART_UNC_LIVES_NUMBERS_SIZE", ART_UNC_LIVES_NUMBERS_SIZE);
        offsets.put("ART_UNC_DEBUG_FONT_ADDR", ART_UNC_DEBUG_FONT_ADDR);
        offsets.put("ART_UNC_DEBUG_FONT_SIZE", ART_UNC_DEBUG_FONT_SIZE);
        offsets.put("ART_NEM_HUD_ADDR", ART_NEM_HUD_ADDR);
        offsets.put("ART_NEM_TITLE_CARD_ADDR", ART_NEM_TITLE_CARD_ADDR);
        offsets.put("ART_NEM_TITLE_CARD2_ADDR", ART_NEM_TITLE_CARD2_ADDR);
        offsets.put("ART_NEM_MENU_BOX_ADDR", ART_NEM_MENU_BOX_ADDR);
        offsets.put("ART_NEM_LEVEL_SELECT_PICS_ADDR", ART_NEM_LEVEL_SELECT_PICS_ADDR);
        offsets.put("ART_NEM_FONT_STUFF_ADDR", ART_NEM_FONT_STUFF_ADDR);
        offsets.put("MAP_ENI_LEVEL_SELECT_ADDR", MAP_ENI_LEVEL_SELECT_ADDR);
        offsets.put("MAP_ENI_LEVEL_SELECT_ICON_ADDR", MAP_ENI_LEVEL_SELECT_ICON_ADDR);
        offsets.put("ART_UNC_MENU_BACK_ADDR", ART_UNC_MENU_BACK_ADDR);
        offsets.put("ART_UNC_MENU_BACK_SIZE", ART_UNC_MENU_BACK_SIZE);
        offsets.put("MAP_ENI_MENU_BACK_ADDR", MAP_ENI_MENU_BACK_ADDR);
        offsets.put("MAP_ENI_MENU_BACK_SIZE", MAP_ENI_MENU_BACK_SIZE);
        offsets.put("PAL_MENU_ADDR", PAL_MENU_ADDR);
        offsets.put("PAL_MENU_SIZE", PAL_MENU_SIZE);
        offsets.put("PAL_LEVEL_ICONS_ADDR", PAL_LEVEL_ICONS_ADDR);
        offsets.put("PAL_LEVEL_ICONS_SIZE", PAL_LEVEL_ICONS_SIZE);
        offsets.put("ART_NEM_RESULTS_TEXT_ADDR", ART_NEM_RESULTS_TEXT_ADDR);
        offsets.put("ART_NEM_MINI_SONIC_ADDR", ART_NEM_MINI_SONIC_ADDR);
        offsets.put("ART_NEM_PERFECT_ADDR", ART_NEM_PERFECT_ADDR);
        offsets.put("MAPPINGS_EOL_TITLE_CARDS_ADDR", MAPPINGS_EOL_TITLE_CARDS_ADDR);
        offsets.put("VRAM_BASE_NUMBERS", VRAM_BASE_NUMBERS);
        offsets.put("VRAM_BASE_PERFECT", VRAM_BASE_PERFECT);
        offsets.put("VRAM_BASE_TITLE_CARD", VRAM_BASE_TITLE_CARD);
        offsets.put("VRAM_BASE_RESULTS_TEXT", VRAM_BASE_RESULTS_TEXT);
        offsets.put("VRAM_BASE_MINI_CHARACTER", VRAM_BASE_MINI_CHARACTER);
        offsets.put("VRAM_BASE_HUD_TEXT", VRAM_BASE_HUD_TEXT);
        offsets.put("RESULTS_BONUS_DIGIT_TILES", RESULTS_BONUS_DIGIT_TILES);
        offsets.put("RESULTS_BONUS_DIGIT_GROUP_TILES", RESULTS_BONUS_DIGIT_GROUP_TILES);
        offsets.put("ART_NEM_NUMBERS_ADDR", ART_NEM_NUMBERS_ADDR);
        offsets.put("ART_NEM_BUZZER_ADDR", ART_NEM_BUZZER_ADDR);
        offsets.put("ART_NEM_MASHER_ADDR", ART_NEM_MASHER_ADDR);
        offsets.put("ART_NEM_COCONUTS_ADDR", ART_NEM_COCONUTS_ADDR);
        offsets.put("ART_NEM_ANIMAL_ADDR", ART_NEM_ANIMAL_ADDR);
        offsets.put("ART_NEM_SPINY_ADDR", ART_NEM_SPINY_ADDR);
        offsets.put("ART_NEM_GRABBER_ADDR", ART_NEM_GRABBER_ADDR);
        offsets.put("ART_NEM_CHOPCHOP_ADDR", ART_NEM_CHOPCHOP_ADDR);
        offsets.put("ART_NEM_WHISP_ADDR", ART_NEM_WHISP_ADDR);
        offsets.put("ART_NEM_GROUNDER_ADDR", ART_NEM_GROUNDER_ADDR);
        offsets.put("ART_NEM_SPIKER_ADDR", ART_NEM_SPIKER_ADDR);
        offsets.put("ART_NEM_REXON_ADDR", ART_NEM_REXON_ADDR);
        offsets.put("ART_NEM_OCTUS_ADDR", ART_NEM_OCTUS_ADDR);
        offsets.put("ART_NEM_AQUIS_ADDR", ART_NEM_AQUIS_ADDR);
        offsets.put("ART_NEM_CRAWL_ADDR", ART_NEM_CRAWL_ADDR);
        offsets.put("ART_NEM_ARROW_SHOOTER_ADDR", ART_NEM_ARROW_SHOOTER_ADDR);
        offsets.put("MAP_UNC_ARROW_SHOOTER_ADDR", MAP_UNC_ARROW_SHOOTER_ADDR);
        offsets.put("ART_TILE_ARROW_SHOOTER", ART_TILE_ARROW_SHOOTER);
        offsets.put("ART_NEM_EGGPOD_ADDR", ART_NEM_EGGPOD_ADDR);
        offsets.put("ART_NEM_EHZ_BOSS_ADDR", ART_NEM_EHZ_BOSS_ADDR);
        offsets.put("ART_NEM_CNZ_BOSS_ADDR", ART_NEM_CNZ_BOSS_ADDR);
        offsets.put("ART_NEM_EGG_CHOPPERS_ADDR", ART_NEM_EGG_CHOPPERS_ADDR);
        offsets.put("ART_NEM_CPZ_BOSS_ADDR", ART_NEM_CPZ_BOSS_ADDR);
        offsets.put("ART_NEM_EGGPOD_JETS_ADDR", ART_NEM_EGGPOD_JETS_ADDR);
        offsets.put("ART_NEM_BOSS_SMOKE_ADDR", ART_NEM_BOSS_SMOKE_ADDR);
        offsets.put("ART_NEM_FIERY_EXPLOSION_ADDR", ART_NEM_FIERY_EXPLOSION_ADDR);
        offsets.put("ART_NEM_ARZ_BOSS_ADDR", ART_NEM_ARZ_BOSS_ADDR);
        offsets.put("ART_TILE_EGGPOD", ART_TILE_EGGPOD);
        offsets.put("ART_TILE_EHZ_BOSS", ART_TILE_EHZ_BOSS);
        offsets.put("ART_TILE_EGG_CHOPPERS", ART_TILE_EGG_CHOPPERS);
        offsets.put("ART_TILE_EGGPOD_3", ART_TILE_EGGPOD_3);
        offsets.put("ART_TILE_EGGPOD_JETS_1", ART_TILE_EGGPOD_JETS_1);
        offsets.put("ART_TILE_CPZ_BOSS", ART_TILE_CPZ_BOSS);
        offsets.put("ART_TILE_BOSS_SMOKE_1", ART_TILE_BOSS_SMOKE_1);
        offsets.put("ART_TILE_FIERY_EXPLOSION", ART_TILE_FIERY_EXPLOSION);
        offsets.put("ART_TILE_ARZ_BOSS", ART_TILE_ARZ_BOSS);
        offsets.put("ART_TILE_EGGPOD_4", ART_TILE_EGGPOD_4);
        offsets.put("ART_TILE_CNZ_BOSS", ART_TILE_CNZ_BOSS);
        offsets.put("ART_TILE_CNZ_BOSS_FUDGE", ART_TILE_CNZ_BOSS_FUDGE);
        offsets.put("ART_NEM_MCZ_BOSS_ADDR", ART_NEM_MCZ_BOSS_ADDR);
        offsets.put("ART_UNC_FALLING_ROCKS_ADDR", ART_UNC_FALLING_ROCKS_ADDR);
        offsets.put("MAP_UNC_MCZ_BOSS_ADDR", MAP_UNC_MCZ_BOSS_ADDR);
        offsets.put("MAP_UNC_CNZ_BOSS_ADDR", MAP_UNC_CNZ_BOSS_ADDR);
        offsets.put("CYCLING_PAL_CNZ_BOSS1_ADDR", CYCLING_PAL_CNZ_BOSS1_ADDR);
        offsets.put("CYCLING_PAL_CNZ_BOSS1_LEN", CYCLING_PAL_CNZ_BOSS1_LEN);
        offsets.put("CYCLING_PAL_CNZ_BOSS2_ADDR", CYCLING_PAL_CNZ_BOSS2_ADDR);
        offsets.put("CYCLING_PAL_CNZ_BOSS2_LEN", CYCLING_PAL_CNZ_BOSS2_LEN);
        offsets.put("CYCLING_PAL_CNZ_BOSS3_ADDR", CYCLING_PAL_CNZ_BOSS3_ADDR);
        offsets.put("CYCLING_PAL_CNZ_BOSS3_LEN", CYCLING_PAL_CNZ_BOSS3_LEN);
        offsets.put("MAP_UNC_CPZ_BOSS_PARTS_ADDR", MAP_UNC_CPZ_BOSS_PARTS_ADDR);
        offsets.put("MAP_UNC_CPZ_BOSS_EGGPOD_ADDR", MAP_UNC_CPZ_BOSS_EGGPOD_ADDR);
        offsets.put("MAP_UNC_CPZ_BOSS_JETS_ADDR", MAP_UNC_CPZ_BOSS_JETS_ADDR);
        offsets.put("MAP_UNC_CPZ_BOSS_SMOKE_ADDR", MAP_UNC_CPZ_BOSS_SMOKE_ADDR);
        offsets.put("MAP_UNC_BOSS_EXPLOSION_ADDR", MAP_UNC_BOSS_EXPLOSION_ADDR);
        offsets.put("MAP_UNC_ARZ_BOSS_PARTS_ADDR", MAP_UNC_ARZ_BOSS_PARTS_ADDR);
        offsets.put("MAP_UNC_ARZ_BOSS_MAIN_ADDR", MAP_UNC_ARZ_BOSS_MAIN_ADDR);
        offsets.put("ART_NEM_FLICKY_ADDR", ART_NEM_FLICKY_ADDR);
        offsets.put("ART_NEM_SQUIRREL_ADDR", ART_NEM_SQUIRREL_ADDR);
        offsets.put("ART_NEM_MOUSE_ADDR", ART_NEM_MOUSE_ADDR);
        offsets.put("ART_NEM_CHICKEN_ADDR", ART_NEM_CHICKEN_ADDR);
        offsets.put("ART_NEM_MONKEY_ADDR", ART_NEM_MONKEY_ADDR);
        offsets.put("ART_NEM_EAGLE_ADDR", ART_NEM_EAGLE_ADDR);
        offsets.put("ART_NEM_PIG_ADDR", ART_NEM_PIG_ADDR);
        offsets.put("ART_NEM_SEAL_ADDR", ART_NEM_SEAL_ADDR);
        offsets.put("ART_NEM_PENGUIN_ADDR", ART_NEM_PENGUIN_ADDR);
        offsets.put("ART_NEM_TURTLE_ADDR", ART_NEM_TURTLE_ADDR);
        offsets.put("ART_NEM_BEAR_ADDR", ART_NEM_BEAR_ADDR);
        offsets.put("ART_NEM_RABBIT_ADDR", ART_NEM_RABBIT_ADDR);
        offsets.put("ART_NEM_LEVER_SPRING_ADDR", ART_NEM_LEVER_SPRING_ADDR);
        offsets.put("ART_NEM_HEX_BUMPER_ADDR", ART_NEM_HEX_BUMPER_ADDR);
        offsets.put("ART_NEM_BUMPER_ADDR", ART_NEM_BUMPER_ADDR);
        offsets.put("ART_NEM_BONUS_BLOCK_ADDR", ART_NEM_BONUS_BLOCK_ADDR);
        offsets.put("CNZ_BUMPERS_ACT1_ADDR", CNZ_BUMPERS_ACT1_ADDR);
        offsets.put("CNZ_BUMPERS_ACT2_ADDR", CNZ_BUMPERS_ACT2_ADDR);
        offsets.put("ZONE_CNZ", ZONE_CNZ);
        offsets.put("ART_NEM_FLIPPER_ADDR", ART_NEM_FLIPPER_ADDR);
        offsets.put("ART_NEM_CNZ_SNAKE_ADDR", ART_NEM_CNZ_SNAKE_ADDR);
        offsets.put("ART_NEM_CNZ_BIG_BLOCK_ADDR", ART_NEM_CNZ_BIG_BLOCK_ADDR);
        offsets.put("ART_NEM_CNZ_ELEVATOR_ADDR", ART_NEM_CNZ_ELEVATOR_ADDR);
        offsets.put("ART_NEM_CNZ_CAGE_ADDR", ART_NEM_CNZ_CAGE_ADDR);
        offsets.put("ART_NEM_CNZ_BONUS_SPIKE_ADDR", ART_NEM_CNZ_BONUS_SPIKE_ADDR);
        offsets.put("ART_UNC_CNZ_SLOT_PICS_ADDR", ART_UNC_CNZ_SLOT_PICS_ADDR);
        offsets.put("ART_UNC_CNZ_SLOT_PICS_SIZE", ART_UNC_CNZ_SLOT_PICS_SIZE);
        offsets.put("ART_NEM_CNZ_VERT_PLUNGER_ADDR", ART_NEM_CNZ_VERT_PLUNGER_ADDR);
        offsets.put("ART_NEM_CNZ_DIAG_PLUNGER_ADDR", ART_NEM_CNZ_DIAG_PLUNGER_ADDR);
        offsets.put("ART_TILE_CNZ_VERT_PLUNGER", ART_TILE_CNZ_VERT_PLUNGER);
        offsets.put("ART_TILE_CNZ_DIAG_PLUNGER", ART_TILE_CNZ_DIAG_PLUNGER);
        offsets.put("ART_NEM_WATER_SURFACE_CPZ_ADDR", ART_NEM_WATER_SURFACE_CPZ_ADDR);
        offsets.put("ART_NEM_WATER_SURFACE_ARZ_ADDR", ART_NEM_WATER_SURFACE_ARZ_ADDR);
        offsets.put("ART_NEM_BUBBLES_ADDR", ART_NEM_BUBBLES_ADDR);
        offsets.put("ART_NEM_BUBBLE_GENERATOR_ADDR", ART_NEM_BUBBLE_GENERATOR_ADDR);
        offsets.put("ART_UNC_COUNTDOWN_ADDR", ART_UNC_COUNTDOWN_ADDR);
        offsets.put("MAP_UNC_SMALL_BUBBLES_ADDR", MAP_UNC_SMALL_BUBBLES_ADDR);
        offsets.put("MAP_UNC_BUBBLES_ADDR", MAP_UNC_BUBBLES_ADDR);
        offsets.put("ART_TILE_BUBBLES", ART_TILE_BUBBLES);
        offsets.put("ART_NEM_LEAVES_ADDR", ART_NEM_LEAVES_ADDR);
        offsets.put("ART_NEM_SPEED_BOOSTER_ADDR", ART_NEM_SPEED_BOOSTER_ADDR);
        offsets.put("MAP_UNC_SPEED_BOOSTER_ADDR", MAP_UNC_SPEED_BOOSTER_ADDR);
        offsets.put("ART_NEM_PIPE_EXIT_SPRING_ADDR", ART_NEM_PIPE_EXIT_SPRING_ADDR);
        offsets.put("ART_NEM_CPZ_ANIMATED_BITS_ADDR", ART_NEM_CPZ_ANIMATED_BITS_ADDR);
        offsets.put("ART_NEM_CONSTRUCTION_STRIPES_ADDR", ART_NEM_CONSTRUCTION_STRIPES_ADDR);
        offsets.put("ART_NEM_ARZ_BARRIER_ADDR", ART_NEM_ARZ_BARRIER_ADDR);
        offsets.put("ART_NEM_HTZ_VALVE_BARRIER_ADDR", ART_NEM_HTZ_VALVE_BARRIER_ADDR);
        offsets.put("MAP_UNC_BARRIER_ADDR", MAP_UNC_BARRIER_ADDR);
        offsets.put("ART_NEM_CPZ_DROPLET_ADDR", ART_NEM_CPZ_DROPLET_ADDR);
        offsets.put("ART_NEM_CPZ_METAL_BLOCK_ADDR", ART_NEM_CPZ_METAL_BLOCK_ADDR);
        offsets.put("ART_NEM_HTZ_ROCK_ADDR", ART_NEM_HTZ_ROCK_ADDR);
        offsets.put("MAP_UNC_OBJ32_HTZ_ADDR", MAP_UNC_OBJ32_HTZ_ADDR);
        offsets.put("MAP_UNC_OBJ32_CPZ_ADDR", MAP_UNC_OBJ32_CPZ_ADDR);
        offsets.put("ART_NEM_HTZ_CLIFFS_ADDR", ART_NEM_HTZ_CLIFFS_ADDR);
        offsets.put("ART_UNC_HTZ_CLOUDS_ADDR", ART_UNC_HTZ_CLOUDS_ADDR);
        offsets.put("ART_UNC_HTZ_CLOUDS_SIZE", ART_UNC_HTZ_CLOUDS_SIZE);
        offsets.put("MAP_UNC_OBJ19_ADDR", MAP_UNC_OBJ19_ADDR);
        offsets.put("ART_NEM_CPZ_ELEVATOR_ADDR", ART_NEM_CPZ_ELEVATOR_ADDR);
        offsets.put("ART_NEM_OOZ_ELEVATOR_ADDR", ART_NEM_OOZ_ELEVATOR_ADDR);
        offsets.put("ART_NEM_CPZ_STAIRBLOCK_ADDR", ART_NEM_CPZ_STAIRBLOCK_ADDR);
        offsets.put("ART_NEM_CPZ_METAL_THINGS_ADDR", ART_NEM_CPZ_METAL_THINGS_ADDR);
        offsets.put("ART_NEM_WFZ_PLATFORM_ADDR", ART_NEM_WFZ_PLATFORM_ADDR);
        offsets.put("ART_TILE_CPZ_ELEVATOR", ART_TILE_CPZ_ELEVATOR);
        offsets.put("ART_TILE_OOZ_ELEVATOR", ART_TILE_OOZ_ELEVATOR);
        offsets.put("ART_TILE_WFZ_PLATFORM", ART_TILE_WFZ_PLATFORM);
        offsets.put("ZONE_CHEMICAL_PLANT", ZONE_CHEMICAL_PLANT);
        offsets.put("ZONE_OIL_OCEAN", ZONE_OIL_OCEAN);
        offsets.put("ZONE_WING_FORTRESS", ZONE_WING_FORTRESS);
        offsets.put("ZONE_MYSTIC_CAVE", ZONE_MYSTIC_CAVE);
        offsets.put("ZONE_ARZ", ZONE_ARZ);
        offsets.put("ART_NEM_BURNER_LID_ADDR", ART_NEM_BURNER_LID_ADDR);
        offsets.put("ART_NEM_OOZ_BURN_ADDR", ART_NEM_OOZ_BURN_ADDR);
        offsets.put("ART_NEM_STRIPED_BLOCKS_VERT_ADDR", ART_NEM_STRIPED_BLOCKS_VERT_ADDR);
        offsets.put("ART_NEM_STRIPED_BLOCKS_HORIZ_ADDR", ART_NEM_STRIPED_BLOCKS_HORIZ_ADDR);
        offsets.put("ART_NEM_OOZ_FAN_ADDR", ART_NEM_OOZ_FAN_ADDR);
        offsets.put("ART_NEM_LAUNCH_BALL_ADDR", ART_NEM_LAUNCH_BALL_ADDR);
        offsets.put("ART_NEM_OOZ_COLLAPSING_PLATFORM_ADDR", ART_NEM_OOZ_COLLAPSING_PLATFORM_ADDR);
        offsets.put("ART_NEM_MCZ_COLLAPSING_PLATFORM_ADDR", ART_NEM_MCZ_COLLAPSING_PLATFORM_ADDR);
        offsets.put("HTZ_PATTERNS_BASE_ADDR", HTZ_PATTERNS_BASE_ADDR);
        offsets.put("HTZ_PATTERNS_OVERLAY_ADDR", HTZ_PATTERNS_OVERLAY_ADDR);
        offsets.put("HTZ_PATTERNS_OVERLAY_OFFSET", HTZ_PATTERNS_OVERLAY_OFFSET);
        offsets.put("HTZ_CHUNKS_BASE_ADDR", HTZ_CHUNKS_BASE_ADDR);
        offsets.put("HTZ_CHUNKS_OVERLAY_ADDR", HTZ_CHUNKS_OVERLAY_ADDR);
        offsets.put("HTZ_CHUNKS_OVERLAY_OFFSET", HTZ_CHUNKS_OVERLAY_OFFSET);
        offsets.put("HTZ_BLOCKS_ADDR", HTZ_BLOCKS_ADDR);
        offsets.put("HTZ_COLLISION_PRIMARY_ADDR", HTZ_COLLISION_PRIMARY_ADDR);
        offsets.put("HTZ_COLLISION_SECONDARY_ADDR", HTZ_COLLISION_SECONDARY_ADDR);
        offsets.put("ZONE_HTZ", ZONE_HTZ);
        offsets.put("ART_NEM_HTZ_SEESAW_ADDR", ART_NEM_HTZ_SEESAW_ADDR);
        offsets.put("ART_NEM_SOL_ADDR", ART_NEM_SOL_ADDR);
        offsets.put("ART_NEM_HTZ_FIREBALL1_ADDR", ART_NEM_HTZ_FIREBALL1_ADDR);
        offsets.put("ART_NEM_HTZ_ZIPLINE_ADDR", ART_NEM_HTZ_ZIPLINE_ADDR);
        offsets.put("HTZ_MOUNTAINS_TILE_INDEX", HTZ_MOUNTAINS_TILE_INDEX);
        offsets.put("HTZ_MOUNTAINS_TILE_COUNT", HTZ_MOUNTAINS_TILE_COUNT);
        offsets.put("HTZ_CLOUDS_TILE_INDEX", HTZ_CLOUDS_TILE_INDEX);
        offsets.put("HTZ_CLOUDS_TILE_COUNT", HTZ_CLOUDS_TILE_COUNT);
        offsets.put("HTZ_DYNAMIC_TILES_END", HTZ_DYNAMIC_TILES_END);
        return offsets;
    }
}
