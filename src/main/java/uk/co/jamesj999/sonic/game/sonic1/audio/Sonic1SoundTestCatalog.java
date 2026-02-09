package uk.co.jamesj999.sonic.game.sonic1.audio;

import uk.co.jamesj999.sonic.audio.debug.SoundTestCatalog;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Sound test catalog for Sonic 1. Music titles from s1disasm Constants.asm,
 * SFX names from s1disasm sound driver labels.
 */
public final class Sonic1SoundTestCatalog implements SoundTestCatalog {
    private static final Sonic1SoundTestCatalog INSTANCE = new Sonic1SoundTestCatalog();
    private static final Map<Integer, String> TITLE_MAP = Sonic1Music.titleMap();
    private static final NavigableSet<Integer> VALID_SONGS = new TreeSet<>(TITLE_MAP.keySet());
    private static final Map<Integer, String> SFX_NAMES = Sonic1Sfx.nameMap();

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
        return SFX_NAMES;
    }

    @Override
    public int getDefaultSongId() {
        return Sonic1Music.GHZ.id;
    }

    @Override
    public int getSfxIdBase() {
        return Sonic1Sfx.ID_BASE;
    }

    @Override
    public int getSfxIdMax() {
        return Sonic1Sfx.ID_MAX;
    }

    @Override
    public String getGameName() {
        return "Sonic 1";
    }

}
