package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Scans the S3K ROM to locate key data table addresses.
 *
 * <p>Unlike Sonic 2 which has relatively stable ROM offsets across revisions,
 * S3K combined ROMs can vary. This scanner finds tables by searching for
 * known data patterns (e.g., AIZ1 level boundaries).
 *
 * <p>The scanner populates the static fields in {@link Sonic3kConstants}.
 * If scanning fails for a particular table, the verified default address
 * from Sonic3kConstants is left in place.
 */
public class Sonic3kRomScanner {
    private static final Logger LOG = Logger.getLogger(Sonic3kRomScanner.class.getName());

    private final Rom rom;
    private final int romSize;

    public Sonic3kRomScanner(Rom rom) throws IOException {
        this.rom = rom;
        this.romSize = (int) rom.getSize();
    }

    /**
     * Scans the ROM and populates Sonic3kConstants addresses.
     * Call this once during initialization.
     *
     * @throws IOException if ROM reading fails
     * @throws IllegalStateException if critical tables cannot be found
     */
    public void scan() throws IOException {
        if (Sonic3kConstants.isScanned()) {
            return;
        }

        LOG.info("Scanning S3K ROM for table addresses...");

        // Find LevelSizes by searching for AIZ1's known boundaries: $1308, $6000, $0000, $0390
        int levelSizesAddr = findPattern(Sonic3kConstants.LEVEL_SIZES_AIZ1_PATTERN, 0x1000, romSize);
        if (levelSizesAddr < 0) {
            throw new IllegalStateException("Could not find LevelSizes table in S3K ROM");
        }
        Sonic3kConstants.LEVEL_SIZES_ADDR = levelSizesAddr;
        LOG.info(String.format("  LevelSizes: 0x%06X", levelSizesAddr));

        // From LevelSizes, we can find nearby tables.
        // In the disassembly, the ordering near LevelSizes is:
        //   LevelSizes -> ... code ... -> AngleArray -> HeightMaps -> HeightMapsRot
        //   -> SolidIndexes -> LevelPtrs -> LevelLoadBlock -> Sonic_Start_Locations

        // Find AngleArray by searching for it after LevelSizes.
        int searchStart = levelSizesAddr + 0x200; // Skip past LevelSizes entries

        int angleArrayAddr = findAngleArray(searchStart);
        if (angleArrayAddr >= 0) {
            Sonic3kConstants.SOLID_TILE_ANGLE_ADDR = angleArrayAddr;
            Sonic3kConstants.SOLID_TILE_VERTICAL_MAP_ADDR = angleArrayAddr + Sonic3kConstants.SOLID_TILE_ANGLE_SIZE;
            Sonic3kConstants.SOLID_TILE_HORIZONTAL_MAP_ADDR =
                    Sonic3kConstants.SOLID_TILE_VERTICAL_MAP_ADDR + Sonic3kConstants.SOLID_TILE_MAP_SIZE;
            LOG.info(String.format("  AngleArray: 0x%06X", angleArrayAddr));
            LOG.info(String.format("  HeightMaps: 0x%06X", Sonic3kConstants.SOLID_TILE_VERTICAL_MAP_ADDR));
            LOG.info(String.format("  HeightMapsRot: 0x%06X", Sonic3kConstants.SOLID_TILE_HORIZONTAL_MAP_ADDR));
        } else {
            LOG.warning("Could not find AngleArray - collision data will not work");
        }

        // Find SolidIndexes: table of 32-bit pointers with interleaved/noninterleaved flags.
        int solidSearchStart = angleArrayAddr >= 0
                ? Sonic3kConstants.SOLID_TILE_HORIZONTAL_MAP_ADDR + Sonic3kConstants.SOLID_TILE_MAP_SIZE
                : searchStart + 0x8000;
        int solidIndexesAddr = findSolidIndexes(solidSearchStart);
        if (solidIndexesAddr >= 0) {
            Sonic3kConstants.SOLID_INDEXES_ADDR = solidIndexesAddr;
            LOG.info(String.format("  SolidIndexes: 0x%06X", solidIndexesAddr));
        } else {
            LOG.warning("Could not find SolidIndexes table");
        }

        // Find LevelPtrs: table of 32-bit ROM pointers to layout data.
        int levelPtrsAddr = findLevelPtrs(solidSearchStart);
        if (levelPtrsAddr >= 0) {
            Sonic3kConstants.LEVEL_PTRS_ADDR = levelPtrsAddr;
            LOG.info(String.format("  LevelPtrs: 0x%06X", levelPtrsAddr));
        } else {
            LOG.warning("Could not find LevelPtrs table");
        }

        // Find LevelLoadBlock: 24-byte entries where first longword has high byte = PLC index
        int llbAddr = findLevelLoadBlock();
        if (llbAddr >= 0) {
            Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR = llbAddr;
            LOG.info(String.format("  LevelLoadBlock: 0x%06X", llbAddr));
        } else {
            LOG.warning("Could not find LevelLoadBlock table");
        }

        // Find Start Locations
        int startLocAddr = findStartLocations();
        if (startLocAddr >= 0) {
            Sonic3kConstants.SONIC_START_LOCATIONS_ADDR = startLocAddr;
            LOG.info(String.format("  Sonic_Start_Locations: 0x%06X", startLocAddr));
        }

        // SpriteLocPtrs and RingLocPtrs follow Knux_Start_Locations.
        if (Sonic3kConstants.KNUX_START_LOCATIONS_ADDR > 0) {
            int spritePtrs = Sonic3kConstants.KNUX_START_LOCATIONS_ADDR
                    + Sonic3kConstants.START_LOCATION_ENTRY_COUNT * Sonic3kConstants.START_LOCATION_ENTRY_SIZE;
            Sonic3kConstants.SPRITE_LOC_PTRS_ADDR = spritePtrs;
            LOG.info(String.format("  SpriteLocPtrs: 0x%06X", spritePtrs));

            // RingLocPtrs follows SpriteLocPtrs (48 entries of 4 bytes)
            int ringPtrs = spritePtrs + Sonic3kConstants.START_LOCATION_ENTRY_COUNT * 4;
            Sonic3kConstants.RING_LOC_PTRS_ADDR = ringPtrs;
            LOG.info(String.format("  RingLocPtrs: 0x%06X", ringPtrs));
        }

        Sonic3kConstants.setScanned(true);
        LOG.info("S3K ROM scan complete.");
    }

    /**
     * Searches for a byte pattern in the ROM.
     *
     * @return the ROM offset where the pattern starts, or -1 if not found
     */
    private int findPattern(byte[] pattern, int start, int end) throws IOException {
        int searchEnd = Math.min(end, romSize) - pattern.length;
        for (int i = start; i <= searchEnd; i++) {
            boolean match = true;
            for (int j = 0; j < pattern.length; j++) {
                if (rom.readByte(i + j) != pattern[j]) {
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
     * Finds the AngleArray by looking for the characteristic 256-byte angle table
     * followed by HeightMaps (first entry is all zeros).
     */
    private int findAngleArray(int searchStart) throws IOException {
        int searchEnd = Math.min(searchStart + 0x100000, romSize);

        for (int i = searchStart; i < searchEnd - 0x2100; i++) {
            // After 256 bytes of angle data, next 16 bytes (HeightMaps entry 0) should be all zero
            boolean heightMapZero = true;
            int heightMapStart = i + 0x100;
            for (int j = 0; j < 16; j++) {
                if (rom.readByte(heightMapStart + j) != 0) {
                    heightMapZero = false;
                    break;
                }
            }
            if (!heightMapZero) continue;

            // HeightMaps entry 1 (offset 0x110) should have non-zero data
            boolean entry1NonZero = false;
            for (int j = 0; j < 16; j++) {
                if (rom.readByte(heightMapStart + 16 + j) != 0) {
                    entry1NonZero = true;
                    break;
                }
            }
            if (!entry1NonZero) continue;

            // Angle entry 0 should be 0xFF (flat ground)
            if ((rom.readByte(i) & 0xFF) == 0xFF) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the SolidIndexes table by looking for consecutive 32-bit entries
     * that point to collision data with interleaved/noninterleaved flags.
     */
    private int findSolidIndexes(int searchStart) throws IOException {
        int searchEnd = Math.min(searchStart + 0x80000, romSize);
        for (int i = searchStart; i < searchEnd - 16; i += 2) {
            // SolidIndexes entries: pointer with bit 0 set = interleaved
            int val0 = rom.read32BitAddr(i);
            int addr0 = val0 & 0x7FFFFFFE; // clear bit 31 and bit 0
            if (addr0 < 0x10000 || addr0 >= romSize) continue;

            // Check second entry
            int val1 = rom.read32BitAddr(i + 4);
            int addr1 = val1 & 0x7FFFFFFE;
            if (addr1 < 0x10000 || addr1 >= romSize) continue;

            // Entries should point to nearby collision data blocks
            if (Math.abs(addr1 - addr0) <= 0x2000) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the LevelPtrs table: consecutive 32-bit ROM pointers to layout data.
     */
    private int findLevelPtrs(int searchStart) throws IOException {
        int searchEnd = Math.min(searchStart + 0x100000, romSize);
        for (int i = searchStart; i < searchEnd - 48; i += 2) {
            boolean valid = true;
            for (int j = 0; j < 8; j++) {
                int ptr = rom.read32BitAddr(i + j * 4);
                if (ptr < 0x50000 || ptr >= romSize) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            int layoutPtr = rom.read32BitAddr(i);
            if (layoutPtr + Sonic3kConstants.LEVEL_LAYOUT_TOTAL_SIZE <= romSize) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Finds the LevelLoadBlock table by looking for the characteristic
     * 24-byte entry pattern with PLC indices in high bytes.
     */
    private int findLevelLoadBlock() throws IOException {
        int searchStart = romSize / 4;
        int searchEnd = romSize - Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;

        for (int i = searchStart; i < searchEnd; i += 2) {
            int word0 = rom.read32BitAddr(i);
            int plc1 = (word0 >> 24) & 0xFF;
            int art1 = word0 & 0x00FFFFFF;

            // AIZ1 entry: plc1 = 0x0B
            if (plc1 != 0x0B) continue;
            if (art1 < 0x10000 || art1 >= romSize) continue;

            // AIZ1 second longword: plc2 should also be 0x0B
            int word1 = rom.read32BitAddr(i + 4);
            int plc2 = (word1 >> 24) & 0xFF;
            if (plc2 != 0x0B) continue;

            // AIZ2 entry (24 bytes later): plc1 = 0x0C
            int word2 = rom.read32BitAddr(i + 24);
            int plc2_1 = (word2 >> 24) & 0xFF;
            if (plc2_1 != 0x0C) continue;

            // AIZ1 chunks addresses (offsets 16 and 20) should be equal
            int chunks1 = rom.read32BitAddr(i + 16);
            int chunks2 = rom.read32BitAddr(i + 20);
            if (chunks1 != chunks2) continue;
            if (chunks1 < 0x10000 || chunks1 >= romSize) continue;

            return i;
        }
        return -1;
    }

    /**
     * Finds Sonic_Start_Locations table.
     * AIZ1 Sonic start: X=$13A0, Y=$041A.
     */
    private int findStartLocations() throws IOException {
        int found = findPattern(Sonic3kConstants.START_LOC_AIZ1_PATTERN, romSize / 4, romSize);
        if (found >= 0) {
            // Verify next entry (AIZ2) has reasonable coordinates
            int x2 = rom.read16BitAddr(found + 4);
            int y2 = rom.read16BitAddr(found + 6);
            if (x2 < 0x8000 && y2 < 0x8000) {
                // Knux starts 48 entries after Sonic
                Sonic3kConstants.KNUX_START_LOCATIONS_ADDR =
                        found + Sonic3kConstants.START_LOCATION_ENTRY_COUNT * Sonic3kConstants.START_LOCATION_ENTRY_SIZE;
                LOG.info(String.format("  Knux_Start_Locations: 0x%06X",
                        Sonic3kConstants.KNUX_START_LOCATIONS_ADDR));
                return found;
            }
        }
        return -1;
    }
}
