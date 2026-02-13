package uk.co.jamesj999.sonic.tools.introspector;

import uk.co.jamesj999.sonic.game.profile.AddressEntry;
import uk.co.jamesj999.sonic.game.profile.RomProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Introspection chain that discovers Sonic 2 level data structures by
 * scanning the ROM for known byte patterns and pointer structures.
 *
 * <p>Discovers:
 * <ul>
 *   <li>Level data directory (12-byte entries with compression type markers)</li>
 *   <li>Collision arrays (angle map, height maps)</li>
 *   <li>Level layout index (Kosinski-compressed layout pointers)</li>
 *   <li>Level boundaries table</li>
 *   <li>Start location array</li>
 * </ul>
 */
public class Sonic2LevelChain implements IntrospectionChain {

    private static final Logger LOG = Logger.getLogger(Sonic2LevelChain.class.getName());

    @Override
    public String category() {
        return "level";
    }

    @Override
    public void trace(byte[] rom, RomProfile profile) {
        List<IntrospectionResult> results = new ArrayList<>();

        traceLevelDataDir(rom, results);
        traceCollisionArrays(rom, results);
        traceLevelBoundaries(rom, results);
        traceStartLocations(rom, results);

        // Add all results to the profile
        for (IntrospectionResult result : results) {
            profile.putAddress(category(), result.key(), new AddressEntry(result.value(), result.confidence()));
            LOG.info(String.format("  %s = 0x%06X (%s) - %s",
                    result.key(), result.value(), result.confidence(), result.traceLog()));
        }
    }

    /**
     * Scans for the level data directory. Each entry is 12 bytes with a structure:
     * <pre>
     *   4 bytes: art pointer (Nemesis/Kosinski compressed)
     *   2 bytes: 16x16 chunk data (high byte typically 0x00-0x04)
     *   2 bytes: 128x128 block data
     *   2 bytes: palette pointer
     *   2 bytes: second palette entry / padding
     * </pre>
     *
     * <p>The directory is identified by finding a region where consecutive 12-byte
     * entries have valid pointer patterns: the first longword is a ROM address in
     * a reasonable range (0x040000-0x0FFFFF), and the structure repeats for the
     * expected number of zones (17 entries for Sonic 2).</p>
     */
    void traceLevelDataDir(byte[] rom, List<IntrospectionResult> results) {
        // Sonic 2 level data directory has 17 entries of 12 bytes each = 204 bytes
        int entrySize = 12;
        int entryCount = 17;
        int tableSize = entrySize * entryCount;

        // Scan through reasonable ROM range for the level data directory
        // The directory is in the 0x040000-0x050000 range for standard Sonic 2
        int searchStart = 0x040000;
        int searchEnd = RomReadUtil.safeSearchEnd(0x060000, rom.length, tableSize);
        if (searchEnd < searchStart) return;

        for (int offset = searchStart; offset < searchEnd; offset += 2) {
            if (isValidLevelDataDir(rom, offset, entrySize, entryCount)) {
                results.add(new IntrospectionResult(
                        "LEVEL_DATA_DIR", offset, "traced",
                        String.format("Found %d-entry level data directory at 0x%06X", entryCount, offset)));
                return;
            }
        }

        LOG.fine("Level data directory not found");
    }

    /**
     * Validates whether the given offset looks like a valid level data directory.
     * Checks that each 12-byte entry has:
     * <ul>
     *   <li>First 4 bytes form a ROM pointer in the 0x030000-0x100000 range</li>
     *   <li>The entries are self-consistent (pointers are all in similar ranges)</li>
     * </ul>
     */
    boolean isValidLevelDataDir(byte[] rom, int offset, int entrySize, int entryCount) {
        if (offset + entrySize * entryCount > rom.length) {
            return false;
        }

        int validEntries = 0;
        for (int i = 0; i < entryCount; i++) {
            int entryOffset = offset + i * entrySize;
            int ptr = readBigEndian32(rom, entryOffset);

            // Art pointer should be a valid ROM address
            if (ptr < 0x030000 || ptr > 0x100000) {
                return false;
            }

            // Verify the pointer points to data within the ROM
            if (ptr >= rom.length) {
                return false;
            }

            validEntries++;
        }

        return validEntries == entryCount;
    }

    /**
     * Scans for collision data arrays. The angle map is a distinctive 256-byte
     * table where most entries are either 0x00 (flat) or specific angle values
     * (0xFF for steep slopes, etc.).
     *
     * <p>Also looks for the 0x1000-byte height maps that follow the angle map.</p>
     */
    void traceCollisionArrays(byte[] rom, List<IntrospectionResult> results) {
        // The angle map is 256 bytes, followed by two 0x1000-byte height maps
        // The angle table has a distinctive pattern: entry[0]=0x00 (flat ground),
        // and many entries are 0x00 or 0xFF

        int searchStart = 0x040000;
        int searchEnd = RomReadUtil.safeSearchEnd(0x050000, rom.length, 0x2100);
        if (searchEnd < searchStart) return;

        for (int offset = searchStart; offset < searchEnd; offset += 2) {
            if (isValidAngleMap(rom, offset)) {
                results.add(new IntrospectionResult(
                        "SOLID_TILE_ANGLE_ADDR", offset, "traced",
                        String.format("Found 256-byte angle map at 0x%06X", offset)));

                // The vertical height map should follow at offset + 0x100
                int vertMapAddr = offset + 0x100;
                if (isValidHeightMap(rom, vertMapAddr)) {
                    results.add(new IntrospectionResult(
                            "SOLID_TILE_VERTICAL_MAP_ADDR", vertMapAddr, "traced",
                            String.format("Found vertical height map at 0x%06X (angle + 0x100)", vertMapAddr)));
                }

                // The horizontal height map follows at offset + 0x1100
                int horizMapAddr = offset + 0x1100;
                if (horizMapAddr + 0x1000 <= rom.length && isValidHeightMap(rom, horizMapAddr)) {
                    results.add(new IntrospectionResult(
                            "SOLID_TILE_HORIZONTAL_MAP_ADDR", horizMapAddr, "traced",
                            String.format("Found horizontal height map at 0x%06X (angle + 0x1100)", horizMapAddr)));
                }

                return;
            }
        }

        LOG.fine("Collision arrays not found");
    }

    /**
     * Validates a 256-byte angle map. Characteristics:
     * <ul>
     *   <li>Entry 0 is 0x00 (flat ground)</li>
     *   <li>At least 40% of entries are 0x00 (many flat tiles)</li>
     *   <li>Values cover a reasonable range (not all identical)</li>
     * </ul>
     */
    boolean isValidAngleMap(byte[] rom, int offset) {
        if (offset + 0x100 > rom.length) {
            return false;
        }

        // Entry 0 must be 0x00 (flat ground / tile ID 0)
        if (rom[offset] != 0x00) {
            return false;
        }

        int zeroCount = 0;
        int distinctValues = 0;
        boolean[] seen = new boolean[256];

        for (int i = 0; i < 0x100; i++) {
            int val = rom[offset + i] & 0xFF;
            if (val == 0x00) {
                zeroCount++;
            }
            if (!seen[val]) {
                seen[val] = true;
                distinctValues++;
            }
        }

        // At least 40% zeros (many flat tiles), but not ALL zeros
        // Also need reasonable diversity (at least 10 distinct angle values)
        return zeroCount >= 100 && zeroCount < 250 && distinctValues >= 10;
    }

    /**
     * Validates a 0x1000-byte height map. Each entry is a signed height value
     * for one column of a 16x16 tile. Valid maps have a mix of 0x00, 0x10 (full),
     * and intermediate values.
     */
    boolean isValidHeightMap(byte[] rom, int offset) {
        if (offset + 0x1000 > rom.length) {
            return false;
        }

        // First 16 bytes (tile 0) should be all zeros (empty tile)
        for (int i = 0; i < 16; i++) {
            if (rom[offset + i] != 0x00) {
                return false;
            }
        }

        // Check for reasonable distribution of values in the rest
        int zeroCount = 0;
        int fullCount = 0; // 0x10 = full column
        for (int i = 16; i < 0x1000; i++) {
            int val = rom[offset + i] & 0xFF;
            if (val == 0x00) zeroCount++;
            if (val == 0x10) fullCount++;
        }

        // Should have a good mix of zero and full columns, with some in between
        return zeroCount > 200 && fullCount > 100;
    }

    /**
     * Scans for the level boundaries table. In Sonic 2, this is a table of
     * 10-byte entries (5 words): left boundary, top boundary, right boundary,
     * bottom boundary, and a Y-wrap value.
     *
     * <p>Identified by finding entries where:</p>
     * <ul>
     *   <li>Left boundary is typically small (0x0000-0x0010)</li>
     *   <li>Right boundary is typically large (0x1000-0x6000)</li>
     *   <li>Bottom boundary is in a reasonable range (0x0300-0x0800)</li>
     * </ul>
     */
    void traceLevelBoundaries(byte[] rom, List<IntrospectionResult> results) {
        int entrySize = 10;
        int minEntries = 16; // At least 16 zone/act entries

        // Search in the typical code region for the boundaries table
        int searchStart = 0x00C000;
        int searchEnd = RomReadUtil.safeSearchEnd(0x010000, rom.length, entrySize * minEntries);
        if (searchEnd < searchStart) return;

        for (int offset = searchStart; offset < searchEnd; offset += 2) {
            if (isValidBoundariesTable(rom, offset, entrySize, minEntries)) {
                results.add(new IntrospectionResult(
                        "LEVEL_BOUNDARIES_ADDR", offset, "traced",
                        String.format("Found level boundaries table at 0x%06X", offset)));
                return;
            }
        }

        LOG.fine("Level boundaries table not found");
    }

    /**
     * Validates a potential level boundaries table.
     */
    boolean isValidBoundariesTable(byte[] rom, int offset, int entrySize, int minEntries) {
        if (offset + entrySize * minEntries > rom.length) {
            return false;
        }

        int validEntries = 0;
        for (int i = 0; i < minEntries; i++) {
            int entryOff = offset + i * entrySize;
            int leftBound = readBigEndian16(rom, entryOff);
            int topBound = readBigEndian16(rom, entryOff + 2);
            int rightBound = readBigEndian16(rom, entryOff + 4);
            int bottomBound = readBigEndian16(rom, entryOff + 6);

            // Left boundary is typically small
            if (leftBound > 0x0100) continue;
            // Right boundary should be larger than left
            if (rightBound <= leftBound) continue;
            // Right boundary in reasonable range for a level
            if (rightBound < 0x0800 || rightBound > 0x7000) continue;
            // Bottom boundary in reasonable range
            if (bottomBound < 0x0200 || bottomBound > 0x0900) continue;

            validEntries++;
        }

        // At least 75% of entries should look valid
        return validEntries >= minEntries * 3 / 4;
    }

    /**
     * Scans for the start location array. In Sonic 2, this is a table of
     * 4-byte entries (2 words: X, Y position).
     *
     * <p>The first entry (EHZ Act 1) typically has X around 0x0050-0x0080
     * and Y around 0x02A0-0x03C0.</p>
     */
    void traceStartLocations(byte[] rom, List<IntrospectionResult> results) {
        int entrySize = 4;
        int entryCount = 32; // Enough zone/act slots

        // Search in the typical code region
        int searchStart = 0x009000;
        int searchEnd = RomReadUtil.safeSearchEnd(0x010000, rom.length, entrySize * entryCount);
        if (searchEnd < searchStart) return;

        for (int offset = searchStart; offset < searchEnd; offset += 2) {
            if (isValidStartLocationArray(rom, offset, entrySize, entryCount)) {
                results.add(new IntrospectionResult(
                        "START_LOC_ARRAY_ADDR", offset, "traced",
                        String.format("Found start location array at 0x%06X", offset)));
                return;
            }
        }

        LOG.fine("Start location array not found");
    }

    /**
     * Validates a potential start location array.
     * Start positions should have reasonable X (0x0020-0x0200) and Y (0x0100-0x0600) values.
     */
    boolean isValidStartLocationArray(byte[] rom, int offset, int entrySize, int entryCount) {
        if (offset + entrySize * entryCount > rom.length) {
            return false;
        }

        // Check first entry specifically (EHZ Act 1 has known reasonable coords)
        int firstX = readBigEndian16(rom, offset);
        int firstY = readBigEndian16(rom, offset + 2);

        // First zone start X is typically in 0x0030-0x0100 range
        if (firstX < 0x0020 || firstX > 0x0200) return false;
        // First zone start Y is typically in 0x0200-0x0500 range
        if (firstY < 0x0100 || firstY > 0x0600) return false;

        int validEntries = 0;
        for (int i = 0; i < entryCount; i++) {
            int entryOff = offset + i * entrySize;
            int x = readBigEndian16(rom, entryOff);
            int y = readBigEndian16(rom, entryOff + 2);

            // Reasonable spawn coordinates
            if (x >= 0x0010 && x <= 0x3000 && y >= 0x0080 && y <= 0x0800) {
                validEntries++;
            }
        }

        // At least 60% should be valid (some entries may be unused/zeroed)
        return validEntries >= entryCount * 3 / 5;
    }

    // ---- Utility methods (delegate to shared RomReadUtil) ----

    static int readBigEndian32(byte[] rom, int offset) {
        return RomReadUtil.readBigEndian32(rom, offset);
    }

    static int readBigEndian16(byte[] rom, int offset) {
        return RomReadUtil.readBigEndian16(rom, offset);
    }
}
