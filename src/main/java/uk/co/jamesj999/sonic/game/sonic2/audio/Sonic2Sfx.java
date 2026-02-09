package uk.co.jamesj999.sonic.game.sonic2.audio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for all Sonic 2 SFX IDs and display names.
 * <p>Names verified against s2disasm SoundIndex labels.
 */
public enum Sonic2Sfx {
    JUMP(0xA0, "Jump"),
    CHECKPOINT(0xA1, "Checkpoint"),
    SPIKE_SWITCH(0xA2, "Spike Switch"),
    HURT(0xA3, "Hurt"),
    SKIDDING(0xA4, "Skidding"),
    MISSILE_DISSOLVE(0xA5, "Missile Dissolve (Unused)"),
    HURT_BY_SPIKES(0xA6, "Hurt By Spikes"),
    SPARKLE(0xA7, "Sparkle"),
    BEEP(0xA8, "Beep"),
    BWOOP(0xA9, "Bwoop (Unused)"),
    SPLASH(0xAA, "Splash"),
    SWISH(0xAB, "Swish"),
    BOSS_HIT(0xAC, "Boss Hit"),
    INHALING_BUBBLE(0xAD, "Inhaling Bubble"),
    ARROW_FIRING(0xAE, "Arrow Firing"),
    SHIELD(0xAF, "Shield"),
    LASER_BEAM(0xB0, "Laser Beam"),
    ZAP(0xB1, "Zap (Unused)"),
    DROWN(0xB2, "Drown"),
    FIRE_BURN(0xB3, "Fire Burn"),
    BUMPER(0xB4, "Bumper"),
    RING_RIGHT(0xB5, "Ring (Right)"),
    SPIKES_MOVE(0xB6, "Spikes Move"),
    RUMBLING(0xB7, "Rumbling"),
    UNUSED_B8(0xB8, "Unused"),
    SMASH(0xB9, "Smash"),
    DING(0xBA, "Ding (Unused)"),
    DOOR_SLAM(0xBB, "Door Slam"),
    SPINDASH_RELEASE(0xBC, "Spindash Release"),
    HAMMER(0xBD, "Hammer"),
    ROLL(0xBE, "Roll"),
    CONTINUE_JINGLE(0xBF, "Continue Jingle"),
    CASINO_BONUS(0xC0, "Casino Bonus"),
    EXPLOSION(0xC1, "Explosion"),
    WATER_WARNING(0xC2, "Water Warning"),
    ENTER_GIANT_RING(0xC3, "Enter Giant Ring"),
    BOSS_EXPLOSION(0xC4, "Boss Explosion"),
    TALLY_END(0xC5, "Tally End"),
    RING_SPILL(0xC6, "Ring Spill"),
    CHAIN_PULL(0xC7, "Chain Pull (Unused)"),
    FLAMETHROWER(0xC8, "Flamethrower"),
    BONUS(0xC9, "Bonus"),
    SPECIAL_STAGE_ENTRY(0xCA, "Special Stage Entry"),
    SLOW_SMASH(0xCB, "Slow Smash"),
    SPRING(0xCC, "Spring"),
    BLIP(0xCD, "Blip"),
    RING_LEFT(0xCE, "Ring (Left)"),
    SIGNPOST(0xCF, "Signpost"),
    CNZ_BOSS_ZAP(0xD0, "CNZ Boss Zap"),
    UNUSED_D1(0xD1, "Unused"),
    UNUSED_D2(0xD2, "Unused"),
    SIGNPOST_2P(0xD3, "Signpost 2P"),
    OOZ_LID_POP(0xD4, "OOZ Lid Pop"),
    SLIDING_SPIKE(0xD5, "Sliding Spike"),
    CNZ_ELEVATOR(0xD6, "CNZ Elevator"),
    PLATFORM_KNOCK(0xD7, "Platform Knock"),
    BONUS_BUMPER(0xD8, "Bonus Bumper"),
    LARGE_BUMPER(0xD9, "Large Bumper"),
    GLOOP(0xDA, "Gloop"),
    PRE_ARROW_FIRING(0xDB, "Pre-Arrow Firing"),
    FIRE(0xDC, "Fire"),
    ARROW_STICK(0xDD, "Arrow Stick"),
    WING_FORTRESS(0xDE, "Wing Fortress"),
    SUPER_TRANSFORM(0xDF, "Super Transform"),
    SPINDASH_CHARGE(0xE0, "Spindash Charge"),
    RUMBLING_2(0xE1, "Rumbling 2"),
    CNZ_LAUNCH(0xE2, "CNZ Launch"),
    FLIPPER(0xE3, "Flipper"),
    HTZ_LIFT_CLICK(0xE4, "HTZ Lift Click"),
    LEAVES(0xE5, "Leaves"),
    MEGA_MACK_DROP(0xE6, "Mega Mack Drop"),
    DRAWBRIDGE_MOVE(0xE7, "Drawbridge Move"),
    QUICK_DOOR_SLAM(0xE8, "Quick Door Slam"),
    DRAWBRIDGE_DOWN(0xE9, "Drawbridge Down"),
    LASER_BURST(0xEA, "Laser Burst"),
    LASER_FLOOR(0xEB, "Laser Floor"),
    TELEPORT(0xEC, "Teleport"),
    ERROR(0xED, "Error"),
    MECHA_SONIC_BUZZ(0xEE, "Mecha Sonic Buzz"),
    LARGE_LASER(0xEF, "Large Laser");

    /** Native SFX ID. */
    public final int id;

    /** Human-readable display name. */
    public final String displayName;

    /** Lowest SFX ID. */
    public static final int ID_BASE = 0xA0;

    /** Highest SFX ID. */
    public static final int ID_MAX = 0xEF;

    private static final Map<Integer, String> NAME_MAP;
    private static final Sonic2Sfx[] BY_ID;

    static {
        Map<Integer, String> m = new LinkedHashMap<>();
        Sonic2Sfx[] lookup = new Sonic2Sfx[ID_MAX - ID_BASE + 1];
        for (Sonic2Sfx sfx : values()) {
            m.put(sfx.id, sfx.displayName);
            lookup[sfx.id - ID_BASE] = sfx;
        }
        NAME_MAP = Collections.unmodifiableMap(m);
        BY_ID = lookup;
    }

    Sonic2Sfx(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** ID-to-name map for the SoundTestCatalog. */
    public static Map<Integer, String> nameMap() {
        return NAME_MAP;
    }

    /** Look up enum constant by native ID, or null if out of range. */
    public static Sonic2Sfx fromId(int id) {
        int index = id - ID_BASE;
        if (index >= 0 && index < BY_ID.length) {
            return BY_ID[index];
        }
        return null;
    }
}
