package uk.co.jamesj999.sonic.game.sonic3k.audio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for all Sonic 3&amp;K SFX IDs and display names.
 * <p>Native 68K queue IDs 0x33-0xDB (Z80 SFX table indices 0-168).
 * Names from sonic3k.constants.asm sfx labels.
 */
public enum Sonic3kSfx {
    RING_RIGHT(0x33, "Ring (Right)"),
    RING_LEFT(0x34, "Ring (Left)"),
    DEATH(0x35, "Death"),
    SKID(0x36, "Skid"),
    SPIKE_HIT(0x37, "Spike Hit"),
    BUBBLE(0x38, "Bubble"),
    SPLASH(0x39, "Splash"),
    SHIELD(0x3A, "Shield"),
    DROWN(0x3B, "Drown"),
    ROLL(0x3C, "Roll"),
    BREAK(0x3D, "Break"),
    FIRE_SHIELD(0x3E, "Fire Shield"),
    BUBBLE_SHIELD(0x3F, "Bubble Shield"),
    UNKNOWN_SHIELD(0x40, "Unknown Shield"),
    LIGHTNING_SHIELD(0x41, "Lightning Shield"),
    INSTA_SHIELD(0x42, "Insta-Shield"),
    FIRE_ATTACK(0x43, "Fire Attack"),
    BUBBLE_ATTACK(0x44, "Bubble Attack"),
    ELECTRIC_ATTACK(0x45, "Electric Attack"),
    WHISTLE(0x46, "Whistle"),
    SANDWALL_RISE(0x47, "Sandwall Rise"),
    BLAST(0x48, "Blast"),
    THUMP(0x49, "Thump"),
    GRAB(0x4A, "Grab"),
    WATERFALL_SPLASH(0x4B, "Waterfall Splash"),
    GLIDE_LAND(0x4C, "Glide Land"),
    PROJECTILE(0x4D, "Projectile"),
    MISSILE_EXPLODE(0x4E, "Missile Explode"),
    FLAMETHROWER_QUIET(0x4F, "Flamethrower (Quiet)"),
    BOSS_ACTIVATE(0x50, "Boss Activate"),
    MISSILE_THROW(0x51, "Missile Throw"),
    SPIKE_MOVE(0x52, "Spike Move"),
    CHARGING(0x53, "Charging"),
    BOSS_LASER(0x54, "Boss Laser"),
    BLOCK_CONVEYOR(0x55, "Block Conveyor"),
    FLIP_BRIDGE(0x56, "Flip Bridge"),
    GEYSER(0x57, "Geyser"),
    FAN_LATCH(0x58, "Fan Latch"),
    COLLAPSE(0x59, "Collapse"),
    UNKNOWN_CHARGE(0x5A, "Unknown Charge"),
    SWITCH(0x5B, "Switch"),
    MECHA_SPARK(0x5C, "Mecha Spark"),
    FLOOR_THUMP(0x5D, "Floor Thump"),
    LASER(0x5E, "Laser"),
    CRASH(0x5F, "Crash"),
    BOSS_ZOOM(0x60, "Boss Zoom"),
    BOSS_HIT_FLOOR(0x61, "Boss Hit Floor"),
    JUMP(0x62, "Jump"),
    STARPOST(0x63, "Starpost"),
    PULLEY_GRAB(0x64, "Pulley Grab"),
    BLUE_SPHERE(0x65, "Blue Sphere"),
    ALL_SPHERES(0x66, "All Spheres"),
    LEVEL_PROJECTILE(0x67, "Level Projectile"),
    PERFECT(0x68, "Perfect"),
    PUSH_BLOCK(0x69, "Push Block"),
    GOAL(0x6A, "Goal"),
    ACTION_BLOCK(0x6B, "Action Block"),
    SPLASH_2(0x6C, "Splash 2"),
    UNKNOWN_SHIFT(0x6D, "Unknown Shift"),
    BOSS_HIT(0x6E, "Boss Hit"),
    RUMBLE_2(0x6F, "Rumble 2"),
    LAVA_BALL(0x70, "Lava Ball"),
    SHIELD_2(0x71, "Shield 2"),
    HOVERPAD(0x72, "Hoverpad"),
    TRANSPORTER(0x73, "Transporter"),
    TUNNEL_BOOSTER(0x74, "Tunnel Booster"),
    BALLOON_PLATFORM(0x75, "Balloon Platform"),
    TRAP_DOOR(0x76, "Trap Door"),
    BALLOON(0x77, "Balloon"),
    GRAVITY_MACHINE(0x78, "Gravity Machine"),
    LIGHTNING(0x79, "Lightning"),
    BOSS_MAGMA(0x7A, "Boss Magma"),
    SMALL_BUMPERS(0x7B, "Small Bumpers"),
    CHAIN_TENSION(0x7C, "Chain Tension"),
    UNKNOWN_PUMP(0x7D, "Unknown Pump"),
    GROUND_SLIDE(0x7E, "Ground Slide"),
    FROST_PUFF(0x7F, "Frost Puff"),
    ICE_SPIKES(0x80, "Ice Spikes"),
    TUBE_LAUNCHER(0x81, "Tube Launcher"),
    SAND_SPLASH(0x82, "Sand Splash"),
    BRIDGE_COLLAPSE(0x83, "Bridge Collapse"),
    UNKNOWN_POWER_UP(0x84, "Unknown Power Up"),
    UNKNOWN_POWER_DOWN(0x85, "Unknown Power Down"),
    ALARM(0x86, "Alarm"),
    MUSHROOM_BOUNCE(0x87, "Mushroom Bounce"),
    PULLEY_MOVE(0x88, "Pulley Move"),
    WEATHER_MACHINE(0x89, "Weather Machine"),
    BOUNCY(0x8A, "Bouncy"),
    CHOP_TREE(0x8B, "Chop Tree"),
    CHOP_STUCK(0x8C, "Chop Stuck"),
    UNKNOWN_FLUTTER(0x8D, "Unknown Flutter"),
    UNKNOWN_REVVING(0x8E, "Unknown Revving"),
    DOOR_OPEN(0x8F, "Door Open"),
    DOOR_MOVE(0x90, "Door Move"),
    DOOR_CLOSE(0x91, "Door Close"),
    GHOST_APPEAR(0x92, "Ghost Appear"),
    BOSS_RECOVERY(0x93, "Boss Recovery"),
    CHAIN_TICK(0x94, "Chain Tick"),
    BOSS_HAND(0x95, "Boss Hand"),
    MECHA_LAND(0x96, "Mecha Land"),
    ENEMY_BREATH(0x97, "Enemy Breath"),
    BOSS_PROJECTILE(0x98, "Boss Projectile"),
    UNKNOWN_PLINK(0x99, "Unknown Plink"),
    SPRING_LATCH(0x9A, "Spring Latch"),
    THUMP_BOSS(0x9B, "Thump Boss"),
    SUPER_EMERALD(0x9C, "Super Emerald"),
    TARGETING(0x9D, "Targeting"),
    CLANK(0x9E, "Clank"),
    SUPER_TRANSFORM(0x9F, "Super Transform"),
    MISSILE_SHOOT(0xA0, "Missile Shoot"),
    UNKNOWN_OMINOUS(0xA1, "Unknown Ominous"),
    FLOOR_LAUNCHER(0xA2, "Floor Launcher"),
    GRAVITY_LIFT(0xA3, "Gravity Lift"),
    MECHA_TRANSFORM(0xA4, "Mecha Transform"),
    UNKNOWN_RISE(0xA5, "Unknown Rise"),
    LAUNCH_GRAB(0xA6, "Launch Grab"),
    LAUNCH_READY(0xA7, "Launch Ready"),
    ENERGY_ZAP(0xA8, "Energy Zap"),
    AIR_DING(0xA9, "Air Ding"),
    BUMPER(0xAA, "Bumper"),
    SPINDASH(0xAB, "Spindash"),
    CONTINUE(0xAC, "Continue"),
    LAUNCH_GO(0xAD, "Launch Go"),
    FLIPPER(0xAE, "Flipper"),
    ENTER_SS(0xAF, "Enter SS"),
    REGISTER(0xB0, "Register"),
    SPRING(0xB1, "Spring"),
    ERROR(0xB2, "Error"),
    BIG_RING(0xB3, "Big Ring"),
    EXPLODE(0xB4, "Explode"),
    DIAMONDS(0xB5, "Diamonds"),
    DASH(0xB6, "Dash"),
    SLOT_MACHINE(0xB7, "Slot Machine"),
    SIGNPOST(0xB8, "Signpost"),
    RING_LOSS(0xB9, "Ring Loss"),
    FLYING(0xBA, "Flying"),
    FLY_TIRED(0xBB, "Fly Tired"),
    // --- Continuous SFX (cfx_*) ---
    SLIDE_SKID_LOUD(0xBC, "Slide Skid (Loud)"),
    LARGE_SHIP(0xBD, "Large Ship"),
    ROBOTNIK_SIREN(0xBE, "Robotnik Siren"),
    BOSS_ROTATE(0xBF, "Boss Rotate"),
    FAN_BIG(0xC0, "Fan (Big)"),
    FAN_SMALL(0xC1, "Fan (Small)"),
    FLAMETHROWER_LOUD(0xC2, "Flamethrower (Loud)"),
    GRAVITY_TUNNEL(0xC3, "Gravity Tunnel"),
    BOSS_PANIC(0xC4, "Boss Panic"),
    UNKNOWN_SPIN(0xC5, "Unknown Spin"),
    WAVE_HOVER(0xC6, "Wave Hover"),
    CANNON_TURN(0xC7, "Cannon Turn"),
    SLIDE_SKID_QUIET(0xC8, "Slide Skid (Quiet)"),
    SPIKE_BALLS(0xC9, "Spike Balls"),
    LIGHT_TUNNEL(0xCA, "Light Tunnel"),
    RUMBLE(0xCB, "Rumble"),
    BIG_RUMBLE(0xCC, "Big Rumble"),
    DEATH_EGG_RISE_LOUD(0xCD, "Death Egg Rise (Loud)"),
    WIND_QUIET(0xCE, "Wind (Quiet)"),
    WIND_LOUD(0xCF, "Wind (Loud)"),
    RISING(0xD0, "Rising"),
    UNKNOWN_FLUTTER_2(0xD1, "Unknown Flutter 2"),
    GUMBALL_TAB(0xD2, "Gumball Tab"),
    DEATH_EGG_RISE_QUIET(0xD3, "Death Egg Rise (Quiet)"),
    TURBINE_HUM(0xD4, "Turbine Hum"),
    LAVA_FALL(0xD5, "Lava Fall"),
    UNKNOWN_ZAP(0xD6, "Unknown Zap"),
    CONVEYOR_PLATFORM(0xD7, "Conveyor Platform"),
    UNKNOWN_SAW(0xD8, "Unknown Saw"),
    MAGNETIC_SPIKE(0xD9, "Magnetic Spike"),
    LEAF_BLOWER(0xDA, "Leaf Blower"),
    WATER_SKID(0xDB, "Water Skid");

    /** Native SFX ID. */
    public final int id;

    /** Human-readable display name. */
    public final String displayName;

    /** First SFX ID (sfx_RingRight, native 68K queue ID 0x33). */
    public static final int ID_BASE = 0x33;

    /** Last standard SFX ID (native 68K queue ID 0xDB). */
    public static final int ID_MAX = 0xDB;

    private static final Map<Integer, String> NAME_MAP;
    private static final Sonic3kSfx[] BY_ID;

    static {
        Map<Integer, String> m = new LinkedHashMap<>();
        Sonic3kSfx[] lookup = new Sonic3kSfx[ID_MAX - ID_BASE + 1];
        for (Sonic3kSfx sfx : values()) {
            m.put(sfx.id, sfx.displayName);
            lookup[sfx.id - ID_BASE] = sfx;
        }
        NAME_MAP = Collections.unmodifiableMap(m);
        BY_ID = lookup;
    }

    Sonic3kSfx(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** ID-to-name map for the SoundTestCatalog. */
    public static Map<Integer, String> nameMap() {
        return NAME_MAP;
    }

    /** Look up enum constant by native ID, or null if out of range. */
    public static Sonic3kSfx fromId(int id) {
        int index = id - ID_BASE;
        if (index >= 0 && index < BY_ID.length) {
            return BY_ID[index];
        }
        return null;
    }
}
