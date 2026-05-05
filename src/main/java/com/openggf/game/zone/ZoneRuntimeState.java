package com.openggf.game.zone;

public interface ZoneRuntimeState {
    String gameId();
    int zoneIndex();
    int actIndex();

    /**
     * Whether the current zone runtime forces a full-width BG tilemap rather
     * than the normal 512px VDP-plane wrap. Used by HTZ earthquake mode where
     * BG high-priority tiles (cave ceiling) are rendered as a direct overlay
     * spanning the full BG map.
     *
     * <p>Default {@code false}: zones honour {@code ZoneFeatureProvider.bgWrapsHorizontally()}.
     */
    default boolean requiresFullWidthBgTilemap() {
        return false;
    }
}
