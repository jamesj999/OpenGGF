package com.openggf.audio.smps;

import com.openggf.data.Rom;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Base class for game-specific SMPS loaders. Provides shared fields (ROM reference,
 * music/SFX caches) and a default {@link #loadSfx(String)} implementation that
 * parses the name as a hex SFX ID and delegates to {@link #loadSfx(int)}.
 *
 * <p>Subclasses must implement the abstract loading methods and provide an
 * {@link #isValidSfxId(int)} predicate for hex-string SFX lookup validation.
 */
public abstract class AbstractSmpsLoader implements SmpsLoader {
    private static final Logger LOGGER = Logger.getLogger(AbstractSmpsLoader.class.getName());

    protected final Rom rom;
    protected final Map<Integer, AbstractSmpsData> musicCache = new HashMap<>();
    protected final Map<Integer, AbstractSmpsData> sfxCache = new HashMap<>();

    protected AbstractSmpsLoader(Rom rom) {
        this.rom = rom;
    }

    // -----------------------------------------------------------------------
    // Default loadSfx(String) — parse hex ID, validate, delegate
    // -----------------------------------------------------------------------

    /**
     * Attempts to parse {@code sfxName} as a hexadecimal SFX ID and load the
     * corresponding SFX data. Subclasses with additional name-based lookup
     * (e.g. Sonic 2's {@code sfxMap}) should override this method, perform
     * their custom lookup first, then call {@code super.loadSfx(sfxName)} as
     * a fallback.
     */
    @Override
    public AbstractSmpsData loadSfx(String sfxName) {
        if (sfxName != null) {
            try {
                int id = Integer.parseInt(sfxName, 16);
                if (isValidSfxId(id)) {
                    return loadSfx(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Returns {@code true} if the given integer is a valid SFX ID for this
     * game's loader. Used by the default {@link #loadSfx(String)} to gate
     * hex-parsed values before delegating to {@link #loadSfx(int)}.
     */
    protected abstract boolean isValidSfxId(int id);

    // -----------------------------------------------------------------------
    // Cache helpers
    // -----------------------------------------------------------------------

    protected AbstractSmpsData getCachedMusic(int musicId) {
        return musicCache.get(musicId);
    }

    protected void cacheMusic(int musicId, AbstractSmpsData data) {
        musicCache.put(musicId, data);
    }

    protected AbstractSmpsData getCachedSfx(int sfxId) {
        return sfxCache.get(sfxId);
    }

    protected void cacheSfx(int sfxId, AbstractSmpsData data) {
        sfxCache.put(sfxId, data);
    }
}
