package com.openggf.audio.debug;

import java.util.Map;
import java.util.NavigableSet;

/**
 * Provides game-specific metadata for the sound test UI: track titles,
 * valid music/SFX ID sets, and default starting IDs.
 */
public interface SoundTestCatalog {

    /** Look up a human-readable title for a music ID, or null if unknown. */
    String lookupTitle(int songId);

    /** Ordered set of all valid music IDs for this game. */
    NavigableSet<Integer> getValidSongs();

    /** Map of SFX ID to human-readable name. */
    Map<Integer, String> getSfxNames();

    /** Default music ID to select on startup. */
    int getDefaultSongId();

    /** First SFX ID in range (inclusive). */
    int getSfxIdBase();

    /** Last SFX ID in range (inclusive). */
    int getSfxIdMax();

    /** Display name for the game, e.g. "Sonic 1", "Sonic 2", "Sonic 3&K". */
    String getGameName();
}
