package com.openggf.audio.debug;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Base implementation of {@link SoundTestCatalog} that eliminates structural
 * duplication across game-specific catalogs. Each subclass passes its static
 * maps and constants to the constructor.
 */
public abstract class AbstractSoundTestCatalog implements SoundTestCatalog {

    private final Map<Integer, String> titleMap;
    private final NavigableSet<Integer> validSongs;
    private final Map<Integer, String> sfxNames;
    private final int defaultSongId;
    private final int sfxIdBase;
    private final int sfxIdMax;
    private final String gameName;

    protected AbstractSoundTestCatalog(Map<Integer, String> titleMap,
                                       Map<Integer, String> sfxNames,
                                       int defaultSongId,
                                       int sfxIdBase,
                                       int sfxIdMax,
                                       String gameName) {
        this.titleMap = titleMap;
        this.validSongs = new TreeSet<>(titleMap.keySet());
        this.sfxNames = sfxNames;
        this.defaultSongId = defaultSongId;
        this.sfxIdBase = sfxIdBase;
        this.sfxIdMax = sfxIdMax;
        this.gameName = gameName;
    }

    @Override
    public String lookupTitle(int songId) {
        return titleMap.get(songId);
    }

    @Override
    public NavigableSet<Integer> getValidSongs() {
        return Collections.unmodifiableNavigableSet(validSongs);
    }

    @Override
    public Map<Integer, String> getSfxNames() {
        return sfxNames;
    }

    @Override
    public int getDefaultSongId() {
        return defaultSongId;
    }

    @Override
    public int getSfxIdBase() {
        return sfxIdBase;
    }

    @Override
    public int getSfxIdMax() {
        return sfxIdMax;
    }

    @Override
    public String getGameName() {
        return gameName;
    }

    /**
     * Returns the underlying title map (unmodifiable). Useful for subclasses
     * that expose static query helpers (e.g. {@code isMusicId()}).
     */
    protected Map<Integer, String> getTitleMapView() {
        return Collections.unmodifiableMap(titleMap);
    }
}
