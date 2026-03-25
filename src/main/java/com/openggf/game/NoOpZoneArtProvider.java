package com.openggf.game;

import java.util.Collections;
import java.util.List;

/**
 * No-op implementation of {@link ZoneArtProvider} for games without zone-specific art.
 * Used as the default implementation to avoid null checks.
 * All lookups return null/empty collections.
 */
public final class NoOpZoneArtProvider implements ZoneArtProvider {
    public static final NoOpZoneArtProvider INSTANCE = new NoOpZoneArtProvider();

    private NoOpZoneArtProvider() {}

    @Override
    public ObjectArtConfig getObjectArt(int objectId, int zoneId) {
        return null;
    }

    @Override
    public List<ArtLoadRequest> getZoneArt(int zoneId, int actId) {
        return Collections.emptyList();
    }
}
