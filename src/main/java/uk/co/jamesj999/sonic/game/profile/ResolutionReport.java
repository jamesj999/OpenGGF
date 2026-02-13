package uk.co.jamesj999.sonic.game.profile;

/**
 * Tracks resolution statistics for the {@link RomAddressResolver}.
 * Reports how many addresses were resolved from each source layer
 * and how many remain unresolved.
 *
 * @param fromProfile number of addresses resolved from the ROM profile
 * @param fromScanned number of addresses resolved from runtime scanning
 * @param fromDefaults number of addresses resolved from hardcoded defaults
 * @param missing number of addresses that could not be resolved from any source
 */
public record ResolutionReport(int fromProfile, int fromScanned, int fromDefaults, int missing) {

    /**
     * Total number of addresses successfully resolved from any source.
     */
    public int totalResolved() {
        return fromProfile + fromScanned + fromDefaults;
    }

    /**
     * Total number of addresses expected (resolved + missing).
     */
    public int totalExpected() {
        return totalResolved() + missing;
    }
}
