package uk.co.jamesj999.sonic.game.sonic1.audio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single source of truth for all Sonic 1 SFX IDs and display names.
 * <p>Names from s1disasm sound driver labels. Includes the special SFX (0xD0).
 */
public enum Sonic1Sfx {
    JUMP(0xA0, "Jump"),
    LAMPPOST(0xA1, "Lamppost"),
    A2_UNUSED(0xA2, "A2 (Unused)"),
    DEATH(0xA3, "Death"),
    SKID(0xA4, "Skid"),
    A5_UNUSED(0xA5, "A5 (Unused)"),
    SPIKE_HIT(0xA6, "Spike Hit"),
    PUSH(0xA7, "Push"),
    SS_GOAL(0xA8, "SS Goal"),
    SS_ITEM(0xA9, "SS Item"),
    SPLASH(0xAA, "Splash"),
    AB_UNUSED(0xAB, "AB (Unused)"),
    HIT_BOSS(0xAC, "Hit Boss"),
    BUBBLE(0xAD, "Bubble"),
    AE_UNUSED(0xAE, "AE (Unused)"),
    SHIELD(0xAF, "Shield"),
    SAW(0xB0, "Saw"),
    ELECTRIC(0xB1, "Electric"),
    DROWN(0xB2, "Drown"),
    FLAMETHROWER(0xB3, "Flamethrower"),
    BUMPER(0xB4, "Bumper"),
    RING(0xB5, "Ring"),
    SPIKES_MOVE(0xB6, "Spikes Move"),
    RUMBLING(0xB7, "Rumbling"),
    B8_UNUSED(0xB8, "B8 (Unused)"),
    COLLAPSE(0xB9, "Collapse"),
    SS_GLASS(0xBA, "SS Glass"),
    DOOR(0xBB, "Door"),
    TELEPORT(0xBC, "Teleport"),
    CHAIN_STOMP(0xBD, "Chain Stomp"),
    ROLL(0xBE, "Roll"),
    GOT_CONTINUE(0xBF, "Got Continue"),
    BASARAN_FLAP(0xC0, "Basaran Flap"),
    BREAK_ITEM(0xC1, "Break Item"),
    WARNING(0xC2, "Warning"),
    ENTER_GR(0xC3, "Enter GR"),
    BOSS_EXPLOSION(0xC4, "Boss Explosion"),
    TALLY(0xC5, "Tally"),
    RING_LOSS(0xC6, "Ring Loss"),
    CHAIN_RISE(0xC7, "Chain Rise"),
    FLAPPING(0xC8, "Flapping"),
    ENTER_SS(0xC9, "Enter SS"),
    WALL_SMASH(0xCA, "Wall Smash"),
    SPRING(0xCB, "Spring"),
    SWITCH(0xCC, "Switch"),
    RING_LEFT(0xCD, "Ring (Left)"),
    SIGNPOST(0xCE, "Signpost"),
    WATERFALL(0xCF, "Waterfall"),
    // Special SFX (separate pointer table at SPECIAL_SFX_PTR_TABLE_ADDR)
    WATERFALL_SPECIAL(0xD0, "Waterfall (Special)");

    /** Native SFX ID. */
    public final int id;

    /** Human-readable display name. */
    public final String displayName;

    /** Lowest SFX ID. */
    public static final int ID_BASE = 0xA0;

    /** Highest SFX ID (including special SFX). */
    public static final int ID_MAX = 0xD0;

    private static final Map<Integer, String> NAME_MAP;
    private static final Sonic1Sfx[] BY_ID;

    static {
        Map<Integer, String> m = new LinkedHashMap<>();
        Sonic1Sfx[] lookup = new Sonic1Sfx[ID_MAX - ID_BASE + 1];
        for (Sonic1Sfx sfx : values()) {
            m.put(sfx.id, sfx.displayName);
            lookup[sfx.id - ID_BASE] = sfx;
        }
        NAME_MAP = Collections.unmodifiableMap(m);
        BY_ID = lookup;
    }

    Sonic1Sfx(int id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /** ID-to-name map for the SoundTestCatalog. */
    public static Map<Integer, String> nameMap() {
        return NAME_MAP;
    }

    /** Look up enum constant by native ID, or null if out of range. */
    public static Sonic1Sfx fromId(int id) {
        int index = id - ID_BASE;
        if (index >= 0 && index < BY_ID.length) {
            return BY_ID[index];
        }
        return null;
    }
}
