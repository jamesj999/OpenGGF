package uk.co.jamesj999.sonic.game.profile.scanner;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Searches a ROM byte array for registered byte patterns and reads address
 * values at match locations. This generalizes the pattern-scanning approach
 * used by {@link uk.co.jamesj999.sonic.game.sonic3k.Sonic3kRomScanner} into
 * a reusable framework.
 *
 * <p>Patterns are registered via {@link #registerPattern(ScanPattern)}, then
 * {@link #scan(byte[], Set)} searches for all patterns that are not already
 * resolved, returning a map of found addresses.</p>
 *
 * <p>The scanner uses simple byte-array comparison for pattern matching and
 * supports big-endian 8/16/32-bit value reads.</p>
 */
public class RomPatternScanner {

    private static final Logger LOG = Logger.getLogger(RomPatternScanner.class.getName());

    private final List<ScanPattern> patterns = new ArrayList<>();

    /**
     * Registers a pattern to search for during the next scan.
     *
     * @param pattern the scan pattern to register
     */
    public void registerPattern(ScanPattern pattern) {
        if (pattern != null) {
            patterns.add(pattern);
        }
    }

    /**
     * Scans the ROM for all registered patterns, skipping any whose key is
     * in {@code alreadyResolved}. Returns a map of "category.name" keys to
     * {@link ScanResult} values for patterns that were found and validated.
     *
     * @param rom             the full ROM byte array
     * @param alreadyResolved set of "category.name" keys to skip (may be null)
     * @return map of found addresses, keyed by "category.name"
     */
    public Map<String, ScanResult> scan(byte[] rom, Set<String> alreadyResolved) {
        Map<String, ScanResult> results = new LinkedHashMap<>();
        if (rom == null || rom.length == 0) {
            return results;
        }
        Set<String> skip = alreadyResolved != null ? alreadyResolved : Set.of();

        for (ScanPattern pattern : patterns) {
            if (skip.contains(pattern.key())) {
                LOG.fine(() -> "Skipping already-resolved pattern: " + pattern.key());
                continue;
            }

            int matchOffset = findPattern(rom, pattern.signature(), 0, rom.length);
            if (matchOffset < 0) {
                LOG.fine(() -> "Pattern not found: " + pattern.key());
                continue;
            }

            int readOffset = matchOffset + pattern.pointerOffset();
            int value = readValue(rom, readOffset, pattern.readMode());

            // Apply validator if present
            if (pattern.validator() != null && !pattern.validator().test(value)) {
                LOG.fine(() -> String.format("Pattern %s found at 0x%06X but value 0x%X failed validation",
                        pattern.key(), matchOffset, value));
                continue;
            }

            results.put(pattern.key(), new ScanResult(value, matchOffset));
            LOG.info(() -> String.format("Scanned %s: value=0x%X at ROM offset 0x%06X",
                    pattern.key(), value, matchOffset));
        }

        return results;
    }

    /**
     * Searches for a byte pattern within a region of the ROM.
     *
     * @param rom     the ROM byte array
     * @param pattern the byte pattern to find
     * @param start   the start offset (inclusive)
     * @param end     the end offset (exclusive, clamped to ROM size)
     * @return the offset of the first match, or -1 if not found
     */
    int findPattern(byte[] rom, byte[] pattern, int start, int end) {
        if (pattern == null || pattern.length == 0) {
            return -1;
        }
        int searchEnd = Math.min(end, rom.length) - pattern.length;
        for (int i = Math.max(start, 0); i <= searchEnd; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (rom[i + j] != pattern[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Reads a value from the ROM byte array at the given offset using the
     * specified read mode.
     *
     * @param rom    the ROM byte array
     * @param offset the byte offset to read from
     * @param mode   the read mode (BYTE, BIG_ENDIAN_16, BIG_ENDIAN_32)
     * @return the read value (unsigned for BYTE/16, signed for 32)
     */
    int readValue(byte[] rom, int offset, ScanPattern.ReadMode mode) {
        return switch (mode) {
            case BYTE -> rom[offset] & 0xFF;
            case BIG_ENDIAN_16 -> ((rom[offset] & 0xFF) << 8) | (rom[offset + 1] & 0xFF);
            case BIG_ENDIAN_32 -> ((rom[offset] & 0xFF) << 24)
                    | ((rom[offset + 1] & 0xFF) << 16)
                    | ((rom[offset + 2] & 0xFF) << 8)
                    | (rom[offset + 3] & 0xFF);
        };
    }
}
