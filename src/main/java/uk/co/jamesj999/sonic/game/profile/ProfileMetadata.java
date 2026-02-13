package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Header metadata for a ROM profile.
 *
 * @param name        human-readable profile name (e.g. "Sonic 2 REV01")
 * @param game        game identifier (e.g. "sonic1", "sonic2", "sonic3k")
 * @param checksum    ROM checksum for matching profiles to ROMs
 * @param generatedBy tool or process that created this profile
 * @param complete    whether all addresses have been verified
 */
public record ProfileMetadata(
        @JsonProperty("name") String name,
        @JsonProperty("game") String game,
        @JsonProperty("checksum") String checksum,
        @JsonProperty("generatedBy") String generatedBy,
        @JsonProperty("complete") boolean complete
) {
}
