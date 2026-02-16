package uk.co.jamesj999.sonic.level.objects;

/**
 * Standard renderer/sheet keys shared across Sonic games.
 * These keys provide a common vocabulary for accessing object art
 * that exists in multiple games (Sonic 1, 2, 3&K, etc.).
 * <p>
 * Game-specific objects should use keys defined in game-specific classes
 * (e.g., Sonic2ObjectArtKeys for CNZ bumpers, CPZ objects).
 */
public final class ObjectArtKeys {

    private ObjectArtKeys() {
        // Constants only
    }

    // Common objects found in multiple Sonic games
    public static final String MONITOR = "monitor";
    public static final String SPIKE = "spike";
    public static final String SPIKE_SIDE = "spike_side";
    public static final String SPRING_VERTICAL = "spring_vertical";
    public static final String SPRING_HORIZONTAL = "spring_horizontal";
    public static final String SPRING_DIAGONAL = "spring_diagonal";
    public static final String SPRING_VERTICAL_RED = "spring_vertical_red";
    public static final String SPRING_HORIZONTAL_RED = "spring_horizontal_red";
    public static final String SPRING_DIAGONAL_RED = "spring_diagonal_red";
    public static final String EXPLOSION = "explosion";
    public static final String SHIELD = "shield";
    public static final String INVINCIBILITY_STARS = "invincibility_stars";
    public static final String CHECKPOINT = "checkpoint";
    public static final String CHECKPOINT_STAR = "checkpoint_star";
    public static final String SIGNPOST = "signpost";
    public static final String ANIMAL = "animal";
    public static final String POINTS = "points";
    public static final String BRIDGE = "bridge";
    public static final String ROCK = "rock";
    public static final String GHZ_EDGE_WALL = "ghz_edge_wall";
    public static final String BREAKABLE_WALL = "breakable_wall";
    public static final String SCENERY = "scenery";
    public static final String RESULTS = "results";
    public static final String EGG_PRISON = "egg_prison";

    // Hidden bonus points (S1 end-of-act)
    public static final String HIDDEN_BONUS = "hidden_bonus";

    // Special stage results
    public static final String SS_RESULTS_EMERALDS = "ss_results_emeralds";

    // Giant Ring / Special Stage entry
    public static final String GIANT_RING = "giant_ring";
    public static final String GIANT_RING_FLASH = "giant_ring_flash";

    // Spiked pole helix (S1 GHZ)
    public static final String SPIKED_POLE_HELIX = "spiked_pole_helix";

    // Swinging platform variants (S1)
    public static final String SWING_GHZ = "swing_ghz";
    public static final String SWING_SLZ = "swing_slz";
    public static final String SWING_SBZ_BALL = "swing_sbz_ball";
    public static final String SWING_GIANT_BALL = "swing_giant_ball";

    // SYZ big spiked ball (uses same art/mappings as SBZ ball on chain)
    public static final String SYZ_BIG_SPIKED_BALL = "syz_big_spiked_ball";

    // Spiked ball and chain (Object 0x57)
    public static final String SYZ_SPIKEBALL_CHAIN = "syz_spikeball_chain";
    public static final String LZ_SPIKEBALL_CHAIN = "lz_spikeball_chain";

    // Boss art keys (S1 GHZ boss)
    public static final String EGGMAN = "eggman";
    public static final String BOSS_WEAPONS = "boss_weapons";
    public static final String BOSS_EXHAUST = "boss_exhaust";
    public static final String BOSS_BALL = "boss_ball";

    // Zone objects using level tiles
    public static final String PLATFORM = "platform";
    public static final String COLLAPSING_LEDGE = "collapsing_ledge";
    public static final String MZ_BRICK = "mz_brick";
    public static final String MZ_LARGE_GRASSY_PLATFORM = "mz_large_grassy_platform";
    public static final String MZ_FIREBALL = "mz_fireball";
    public static final String MZ_LAVA_GEYSER = "mz_lava_geyser";
    public static final String MZ_LAVA_WALL = "mz_lava_wall";
    public static final String SLZ_FIREBALL = "slz_fireball";
    public static final String MZ_CHAINED_STOMPER = "mz_chained_stomper";
    public static final String MZ_GLASS_BLOCK = "mz_glass_block";
    public static final String MZ_SMASH_BLOCK = "mz_smash_block";
    public static final String MZ_COLLAPSING_FLOOR = "mz_collapsing_floor";
    public static final String SLZ_COLLAPSING_FLOOR = "slz_collapsing_floor";
    public static final String SBZ_COLLAPSING_FLOOR = "sbz_collapsing_floor";
    public static final String MZ_PUSH_BLOCK = "mz_push_block";
    public static final String LZ_PUSH_BLOCK = "lz_push_block";
    public static final String MZ_MOVING_BLOCK = "mz_moving_block";
    public static final String LZ_MOVING_BLOCK = "lz_moving_block";
    public static final String SBZ_MOVING_BLOCK_SHORT = "sbz_moving_block_short";
    public static final String SBZ_MOVING_BLOCK_LONG = "sbz_moving_block_long";
    public static final String BUTTON = "button";
    public static final String SYZ_FLOATING_BLOCK = "syz_floating_block";
    public static final String LZ_FLOATING_BLOCK = "lz_floating_block";
    public static final String SYZ_SPINNING_LIGHT = "syz_spinning_light";
    public static final String SYZ_BOSS_BLOCK = "syz_boss_block";

    // LZ harpoon spike trap (S1)
    public static final String LZ_HARPOON = "lz_harpoon";

    // LZ breakable pole (S1 Object 0x0B)
    public static final String LZ_BREAKABLE_POLE = "lz_breakable_pole";

    // LZ flapping door (S1 Object 0x0C)
    public static final String LZ_FLAPPING_DOOR = "lz_flapping_door";

    // LZ gargoyle head & fireball (S1)
    public static final String LZ_GARGOYLE = "lz_gargoyle";

    // LZ conveyor belt wheel/platform (S1)
    public static final String LZ_CONVEYOR = "lz_conveyor";

    // LZ labyrinth blocks (S1 Object 0x61)
    public static final String LZ_LABYRINTH_BLOCK = "lz_labyrinth_block";

    // LZ bubbles (S1)
    public static final String LZ_BUBBLES = "lz_bubbles";

    // LZ waterfall/splash object art (S1 Object 0x65)
    public static final String LZ_WATERFALL = "lz_waterfall";

    // SLZ circling platform (S1 Object 0x5A)
    public static final String SLZ_CIRCLING_PLATFORM = "slz_circling_platform";

    // SLZ fan (S1 Object 0x5D)
    public static final String SLZ_FAN = "slz_fan";

    // SYZ bumper (S1)
    public static final String BUMPER = "bumper";

    // Badnik art keys
    public static final String BUZZ_BOMBER = "buzz_bomber";
    public static final String BUZZ_BOMBER_MISSILE = "buzz_bomber_missile";
    public static final String BUZZ_BOMBER_MISSILE_DISSOLVE = "buzz_bomber_missile_dissolve";
    public static final String CHOPPER = "chopper";
    public static final String CRABMEAT = "crabmeat";
    public static final String MOTOBUG = "motobug";
    public static final String NEWTRON = "newtron";
    public static final String CATERKILLER = "caterkiller";
    public static final String BATBRAIN = "batbrain";
    public static final String ROLLER = "roller";
    public static final String YADRIN = "yadrin";
    public static final String JAWS = "jaws";
    public static final String BURROBOT = "burrobot";
    public static final String ORBINAUT = "orbinaut";
    public static final String BOMB = "bomb";

    // Animation keys (common across games)
    public static final String ANIM_MONITOR = "monitor";
    public static final String ANIM_SPRING = "spring";
    public static final String ANIM_CHECKPOINT = "checkpoint";
    public static final String ANIM_SIGNPOST = "signpost";

    // Zone data keys
    public static final String ANIMAL_TYPE_A = "animal_type_a";
    public static final String ANIMAL_TYPE_B = "animal_type_b";
}
