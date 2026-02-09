package uk.co.jamesj999.sonic.game.sonic3k.audio;

import uk.co.jamesj999.sonic.audio.debug.SoundTestCatalog;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Sound test catalog for Sonic 3 &amp; Knuckles. Music titles from S3K disassembly,
 * SFX names from known S3K SFX labels.
 */
public final class Sonic3kSoundTestCatalog implements SoundTestCatalog {
    private static final Sonic3kSoundTestCatalog INSTANCE = new Sonic3kSoundTestCatalog();
    private static final Map<Integer, String> TITLE_MAP = Sonic3kMusic.titleMap();
    private static final NavigableSet<Integer> VALID_SONGS = new TreeSet<>(TITLE_MAP.keySet());
    private static final Map<Integer, String> SFX_NAMES = Sonic3kSfx.nameMap();

    private Sonic3kSoundTestCatalog() {
    }

    public static Sonic3kSoundTestCatalog getInstance() {
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
        return Sonic3kMusic.AIZ1.id;
    }

    @Override
    public int getSfxIdBase() {
        return Sonic3kSfx.ID_BASE;
    }

    @Override
    public int getSfxIdMax() {
        return Sonic3kSfx.ID_MAX;
    }

    @Override
    public String getGameName() {
        return "Sonic 3&K";
    }

}
