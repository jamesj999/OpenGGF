package uk.co.jamesj999.sonic.game.sonic1.audio;

import uk.co.jamesj999.sonic.audio.debug.SoundTestCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Sound test catalog for Sonic 1. Music titles from s1disasm Constants.asm,
 * SFX names from s1disasm sound driver labels.
 */
public final class Sonic1SoundTestCatalog implements SoundTestCatalog {
    private static final Sonic1SoundTestCatalog INSTANCE = new Sonic1SoundTestCatalog();
    private static final Map<Integer, String> TITLE_MAP = buildTitleMap();
    private static final NavigableSet<Integer> VALID_SONGS = new TreeSet<>(TITLE_MAP.keySet());
    private static final Map<Integer, String> SFX_NAMES = buildSfxNames();

    private Sonic1SoundTestCatalog() {
    }

    public static Sonic1SoundTestCatalog getInstance() {
        return INSTANCE;
    }

    @Override
    public String lookupTitle(int songId) {
        return TITLE_MAP.get(songId);
    }

    @Override
    public NavigableSet<Integer> getValidSongs() {
        return Collections.unmodifiableNavigableSet(VALID_SONGS);
    }

    @Override
    public Map<Integer, String> getSfxNames() {
        return Collections.unmodifiableMap(SFX_NAMES);
    }

    @Override
    public int getDefaultSongId() {
        return 0x81; // Green Hill Zone
    }

    @Override
    public int getSfxIdBase() {
        return Sonic1SmpsConstants.SFX_ID_BASE;
    }

    @Override
    public int getSfxIdMax() {
        return Sonic1SmpsConstants.SPECIAL_SFX_ID_BASE + Sonic1SmpsConstants.SPECIAL_SFX_COUNT - 1;
    }

    @Override
    public String getGameName() {
        return "Sonic 1";
    }

    private static Map<Integer, String> buildTitleMap() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(0x81, "Green Hill Zone");
        m.put(0x82, "Labyrinth Zone");
        m.put(0x83, "Marble Zone");
        m.put(0x84, "Star Light Zone");
        m.put(0x85, "Spring Yard Zone");
        m.put(0x86, "Scrap Brain Zone");
        m.put(0x87, "Invincibility");
        m.put(0x88, "Extra Life");
        m.put(0x89, "Special Stage");
        m.put(0x8A, "Title Screen");
        m.put(0x8B, "Ending");
        m.put(0x8C, "Boss");
        m.put(0x8D, "Final Zone");
        m.put(0x8E, "Act Clear");
        m.put(0x8F, "Game Over");
        m.put(0x90, "Continue");
        m.put(0x91, "Credits");
        m.put(0x92, "Drowning");
        m.put(0x93, "Chaos Emerald");
        return m;
    }

    private static Map<Integer, String> buildSfxNames() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(0xA0, "Jump");
        m.put(0xA1, "Lamppost");
        m.put(0xA2, "A2 (Unused)");
        m.put(0xA3, "Death");
        m.put(0xA4, "Skid");
        m.put(0xA5, "A5 (Unused)");
        m.put(0xA6, "Spike Hit");
        m.put(0xA7, "Push");
        m.put(0xA8, "SS Goal");
        m.put(0xA9, "SS Item");
        m.put(0xAA, "Splash");
        m.put(0xAB, "AB (Unused)");
        m.put(0xAC, "Hit Boss");
        m.put(0xAD, "Bubble");
        m.put(0xAE, "AE (Unused)");
        m.put(0xAF, "Shield");
        m.put(0xB0, "Saw");
        m.put(0xB1, "Electric");
        m.put(0xB2, "Drown");
        m.put(0xB3, "Flamethrower");
        m.put(0xB4, "Bumper");
        m.put(0xB5, "Ring");
        m.put(0xB6, "Spikes Move");
        m.put(0xB7, "Rumbling");
        m.put(0xB8, "B8 (Unused)");
        m.put(0xB9, "Collapse");
        m.put(0xBA, "SS Glass");
        m.put(0xBB, "Door");
        m.put(0xBC, "Teleport");
        m.put(0xBD, "Chain Stomp");
        m.put(0xBE, "Roll");
        m.put(0xBF, "Got Continue");
        m.put(0xC0, "Basaran Flap");
        m.put(0xC1, "Break Item");
        m.put(0xC2, "Warning");
        m.put(0xC3, "Enter GR");
        m.put(0xC4, "Boss Explosion");
        m.put(0xC5, "Tally");
        m.put(0xC6, "Ring Loss");
        m.put(0xC7, "Chain Rise");
        m.put(0xC8, "Flapping");
        m.put(0xC9, "Enter SS");
        m.put(0xCA, "Wall Smash");
        m.put(0xCB, "Spring");
        m.put(0xCC, "Switch");
        m.put(0xCD, "Ring (Left)");
        m.put(0xCE, "Signpost");
        m.put(0xCF, "Waterfall");
        m.put(0xD0, "Waterfall (Special)");
        return m;
    }
}
