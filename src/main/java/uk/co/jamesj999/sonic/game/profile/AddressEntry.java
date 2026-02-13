package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single ROM address entry with its value and confidence level.
 *
 * @param value      the ROM address (integer offset)
 * @param confidence how the address was determined (e.g. "verified", "inferred", "scanned")
 */
public record AddressEntry(
        @JsonProperty("value") int value,
        @JsonProperty("confidence") String confidence
) {
}
