package uk.co.jamesj999.sonic.game.sonic2.audio;

import uk.co.jamesj999.sonic.audio.debug.SoundTestCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Shared sound test catalog for Sonic 2 (music IDs + helpers).
 */
public final class Sonic2SoundTestCatalog implements SoundTestCatalog {
    private static final Sonic2SoundTestCatalog INSTANCE = new Sonic2SoundTestCatalog();
    private static final Map<Integer, String> TITLE_MAP = buildTitleMap();
    private static final NavigableSet<Integer> VALID_SONGS = new TreeSet<>(TITLE_MAP.keySet());
    private static final Map<Integer, String> SFX_NAMES = buildSfxNames();

    private Sonic2SoundTestCatalog() {
    }

    public static Sonic2SoundTestCatalog getInstance() {
        return INSTANCE;
    }

    public static Map<Integer, String> getTitleMap() {
        return Collections.unmodifiableMap(TITLE_MAP);
    }

    @Override
    public NavigableSet<Integer> getValidSongs() {
        return Collections.unmodifiableNavigableSet(VALID_SONGS);
    }

    @Override
    public String lookupTitle(int songId) {
        return TITLE_MAP.get(songId);
    }

    @Override
    public Map<Integer, String> getSfxNames() {
        return Collections.unmodifiableMap(SFX_NAMES);
    }

    @Override
    public int getDefaultSongId() {
        return 0x8C; // Chemical Plant Zone
    }

    @Override
    public int getSfxIdBase() {
        return Sonic2SmpsConstants.SFX_ID_BASE;
    }

    @Override
    public int getSfxIdMax() {
        return Sonic2SmpsConstants.SFX_ID_MAX;
    }

    @Override
    public String getGameName() {
        return "Sonic 2";
    }

    public static boolean isMusicId(int id) {
        return TITLE_MAP.containsKey(id);
    }

    public static boolean isSfxId(int id) {
        return id >= Sonic2SmpsConstants.SFX_ID_BASE && id <= Sonic2SmpsConstants.SFX_ID_MAX;
    }

    public static int toSoundId(int soundTestValue) {
        return (soundTestValue & 0x7F) + 0x80;
    }

    private static Map<Integer, String> buildSfxNames() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(0xA0, "Jump");
        m.put(0xA1, "Checkpoint");
        m.put(0xA2, "Sparkle (Unused)");
        m.put(0xA3, "Hurt");
        m.put(0xA4, "Skid");
        m.put(0xA5, "Block Push");
        m.put(0xA6, "Spike Hit");
        m.put(0xA7, "Destroy");
        m.put(0xA8, "Big Ring");
        m.put(0xA9, "Splash");
        m.put(0xAA, "Rumbling");
        m.put(0xAB, "Door");
        m.put(0xAC, "Spin");
        m.put(0xAD, "Pop");
        m.put(0xAE, "Bubble");
        m.put(0xAF, "Smash");
        m.put(0xB0, "Shield");
        m.put(0xB1, "Drown");
        m.put(0xB2, "Stomp");
        m.put(0xB3, "Air Ding");
        m.put(0xB4, "Ring");
        m.put(0xB5, "Bubbling");
        m.put(0xB6, "Bounce");
        m.put(0xB7, "SS Glass");
        m.put(0xB8, "Boss Hit");
        m.put(0xB9, "Poof");
        m.put(0xBA, "SS Bomb");
        m.put(0xBB, "Spindash Charge");
        m.put(0xBC, "Spindash Release");
        m.put(0xBD, "Hammer");
        m.put(0xBE, "Roll");
        m.put(0xBF, "Continue Jingle");
        m.put(0xC0, "Casino Bonus");
        m.put(0xC1, "Explosion");
        m.put(0xC2, "Water Warning");
        m.put(0xC3, "Enter Giant Ring");
        m.put(0xC4, "Boss Explosion");
        m.put(0xC5, "Tally End");
        m.put(0xC6, "Ring Spill");
        m.put(0xC7, "Chain Pull (Unused)");
        m.put(0xC8, "Flamethrower");
        m.put(0xC9, "Bonus");
        m.put(0xCA, "Special Stage Entry");
        m.put(0xCB, "Slow Smash");
        m.put(0xCC, "Spring");
        m.put(0xCD, "Blip");
        m.put(0xCE, "Ring (Left)");
        m.put(0xCF, "Signpost");
        m.put(0xD0, "CNZ Boss Zap");
        m.put(0xD1, "Unused");
        m.put(0xD2, "Unused");
        m.put(0xD3, "Signpost 2P");
        m.put(0xD4, "OOZ Lid Pop");
        m.put(0xD5, "Sliding Spike");
        m.put(0xD6, "CNZ Elevator");
        m.put(0xD7, "Platform Knock");
        m.put(0xD8, "Bonus Bumper");
        m.put(0xD9, "Large Bumper");
        m.put(0xDA, "Gloop");
        m.put(0xDB, "Pre-Arrow Firing");
        m.put(0xDC, "Fire");
        m.put(0xDD, "Arrow Stick");
        m.put(0xDE, "Wing Fortress");
        m.put(0xDF, "Super Transform");
        m.put(0xE0, "Spindash Charge 2");
        m.put(0xE1, "Rumbling 2");
        m.put(0xE2, "CNZ Launch");
        m.put(0xE3, "Flipper");
        m.put(0xE4, "HTZ Lift Click");
        m.put(0xE5, "Leaves");
        m.put(0xE6, "Mega Mack Drop");
        m.put(0xE7, "Drawbridge Move");
        m.put(0xE8, "Quick Door Slam");
        m.put(0xE9, "Drawbridge Down");
        m.put(0xEA, "Laser Burst");
        m.put(0xEB, "Laser Floor");
        m.put(0xEC, "Teleport");
        m.put(0xED, "Error");
        m.put(0xEE, "SS Ring");
        m.put(0xEF, "Fly");
        return m;
    }

    private static Map<Integer, String> buildTitleMap() {
        Map<Integer, String> m = new LinkedHashMap<>();
        m.put(0x00, "Continue");
        m.put(0x80, "Casino Night Zone (2P)");
        m.put(0x81, "Emerald Hill Zone");
        m.put(0x82, "Metropolis Zone");
        m.put(0x83, "Casino Night Zone");
        m.put(0x84, "Mystic Cave Zone");
        m.put(0x85, "Mystic Cave Zone (2P)");
        m.put(0x86, "Aquatic Ruin Zone");
        m.put(0x87, "Death Egg Zone");
        m.put(0x88, "Special Stage");
        m.put(0x89, "Option Screen");
        m.put(0x8A, "Ending");
        m.put(0x8B, "Final Battle");
        m.put(0x8C, "Chemical Plant Zone");
        m.put(0x8D, "Boss");
        m.put(0x8E, "Sky Chase Zone");
        m.put(0x8F, "Oil Ocean Zone");
        m.put(0x90, "Wing Fortress Zone");
        m.put(0x91, "Emerald Hill Zone (2P)");
        m.put(0x92, "2P Results Screen");
        m.put(0x93, "Super Sonic");
        m.put(0x94, "Hill Top Zone");
        m.put(0x96, "Title Screen");
        m.put(0x97, "Stage Clear");
        m.put(0x99, "Invincibility");
        m.put(0x9B, "Hidden Palace Zone");
        m.put(0xB5, "1-Up");
        m.put(0xB8, "Game Over");
        m.put(0xBA, "Got an Emerald");
        m.put(0xBD, "Credits");
        m.put(0xDC, "Underwater Timing");
        return m;
    }
}
