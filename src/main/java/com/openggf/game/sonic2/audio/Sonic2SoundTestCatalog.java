package com.openggf.game.sonic2.audio;

import com.openggf.audio.debug.SoundTestCatalog;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Shared sound test catalog for Sonic 2 (music IDs + helpers).
 */
public final class Sonic2SoundTestCatalog implements SoundTestCatalog {
    private static final Sonic2SoundTestCatalog INSTANCE = new Sonic2SoundTestCatalog();
    private static final Map<Integer, String> TITLE_MAP = Sonic2Music.titleMap();
    private static final NavigableSet<Integer> VALID_SONGS = new TreeSet<>(TITLE_MAP.keySet());
    private static final Map<Integer, String> SFX_NAMES = Sonic2Sfx.nameMap();

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
        return SFX_NAMES;
    }

    @Override
    public int getDefaultSongId() {
        return Sonic2Music.CHEMICAL_PLANT.id;
    }

    @Override
    public int getSfxIdBase() {
        return Sonic2Sfx.ID_BASE;
    }

    @Override
    public int getSfxIdMax() {
        return Sonic2Sfx.ID_MAX;
    }

    @Override
    public String getGameName() {
        return "Sonic 2";
    }

    public static boolean isMusicId(int id) {
        return TITLE_MAP.containsKey(id);
    }

    public static boolean isSfxId(int id) {
        return id >= Sonic2Sfx.ID_BASE && id <= Sonic2Sfx.ID_MAX;
    }

    public static int toSoundId(int soundTestValue) {
        return (soundTestValue & 0x7F) + 0x80;
    }

}
