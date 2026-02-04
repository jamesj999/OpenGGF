package uk.co.jamesj999.sonic.game.sonic2.audio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Shared sound test catalog for Sonic 2 (music IDs + helpers).
 */
public final class Sonic2SoundTestCatalog {
    private static final Map<Integer, String> TITLE_MAP = buildTitleMap();
    private static final NavigableSet<Integer> VALID_SONGS = new TreeSet<>(TITLE_MAP.keySet());

    private Sonic2SoundTestCatalog() {
    }

    public static Map<Integer, String> getTitleMap() {
        return Collections.unmodifiableMap(TITLE_MAP);
    }

    public static NavigableSet<Integer> getValidSongs() {
        return Collections.unmodifiableNavigableSet(VALID_SONGS);
    }

    public static String lookupTitle(int songId) {
        return TITLE_MAP.get(songId);
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
