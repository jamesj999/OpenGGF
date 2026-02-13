package uk.co.jamesj999.sonic.tools.introspector;

import uk.co.jamesj999.sonic.game.profile.RomProfile;

/**
 * Interface for a single category tracer in the ROM profile introspection pipeline.
 *
 * <p>Each chain is responsible for discovering ROM addresses within a specific
 * category (e.g. "level", "audio") by scanning the raw ROM byte array for
 * known byte patterns and pointer structures.</p>
 *
 * <p>Chains add their discovered addresses directly to the provided
 * {@link RomProfile} via {@link RomProfile#putAddress(String, String,
 * uk.co.jamesj999.sonic.game.profile.AddressEntry)}.</p>
 */
public interface IntrospectionChain {

    /**
     * Returns the profile address category this chain populates
     * (e.g. "level", "audio", "collision").
     *
     * @return the category name
     */
    String category();

    /**
     * Traces ROM structures and adds discovered addresses to the profile.
     *
     * <p>Implementations should catch and log individual lookup failures
     * internally so that partial results are still recorded.</p>
     *
     * @param rom     the full ROM byte array
     * @param profile the profile to populate with discovered addresses
     */
    void trace(byte[] rom, RomProfile profile);
}
