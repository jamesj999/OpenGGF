package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps a zone slot to its display name and behavior key.
 *
 * @param name            human-readable zone name (e.g. "EHZ", "CPZ")
 * @param behaviorMapping key used to look up zone-specific behavior (e.g. scroll handlers, event handlers)
 */
public record ZoneMapping(
        @JsonProperty("name") String name,
        @JsonProperty("behaviorMapping") String behaviorMapping
) {
}
