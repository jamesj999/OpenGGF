package com.openggf.game.sonic2;

import com.openggf.data.Rom;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.level.resources.PlcParser;
import com.openggf.level.resources.PlcParser.PlcDefinition;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

/**
 * Parses and provides Pattern Load Cues (PLCs) from the Sonic 2 ROM.
 *
 * <p>PLCs define which Nemesis-compressed art to load and where in VRAM to place it.
 * The S2 ROM stores them in the ArtLoadCues table at {@link Sonic2Constants#ART_LOAD_CUES_ADDR}.
 * PLC IDs for each zone are embedded in the LevelArtPointers table at bytes 0 and 4
 * of each 12-byte entry (currently masked out by the level loader).
 *
 * <p>This class delegates format parsing to the game-agnostic {@link PlcParser}.
 */
public final class Sonic2PlcLoader {
    private static final Logger LOG = Logger.getLogger(Sonic2PlcLoader.class.getName());

    private Sonic2PlcLoader() {}

    /**
     * Parses a PLC definition from the Sonic 2 ROM.
     *
     * @param rom   the ROM to read from
     * @param plcId the PLC ID (0–66)
     * @return the parsed PLC definition, or a definition with empty entries if invalid
     */
    public static PlcDefinition parsePlc(Rom rom, int plcId) throws IOException {
        if (plcId < 0 || plcId >= Sonic2Constants.ART_LOAD_CUES_ENTRY_COUNT) {
            LOG.warning(String.format("PLC ID %d out of range (max %d)",
                    plcId, Sonic2Constants.ART_LOAD_CUES_ENTRY_COUNT - 1));
            return new PlcDefinition(plcId, List.of());
        }
        return PlcParser.parse(rom, Sonic2Constants.ART_LOAD_CUES_ADDR, plcId);
    }

    /**
     * Extracts PLC IDs from the LEVEL_DATA_DIR table for a given zone.
     *
     * <p>Each 12-byte LEVEL_DATA_DIR entry has the structure:
     * <pre>
     *   Byte 0: PLC1 ID (primary zone art)
     *   Bytes 1-3: 24-bit ROM address for art data 1
     *   Byte 4: PLC2 ID (secondary zone art)
     *   Bytes 5-7: 24-bit ROM address for art data 2
     *   Bytes 8-11: Additional level data
     * </pre>
     *
     * @param rom       the ROM to read from
     * @param zoneIndex the zone index (0-based, 0-16 for the 17-entry LEVEL_DATA_DIR table)
     * @return array of [plc1Id, plc2Id]
     */
    public static int[] getZonePlcIds(Rom rom, int zoneIndex) throws IOException {
        if (zoneIndex < 0 || zoneIndex >= 17) {
            LOG.warning("Zone index " + zoneIndex + " out of range for LEVEL_DATA_DIR (max 16)");
            return new int[]{0, 0};
        }
        int base = Sonic2Constants.LEVEL_DATA_DIR + zoneIndex * Sonic2Constants.LEVEL_DATA_DIR_ENTRY_SIZE;
        // The first byte of each 4-byte longword holds the PLC ID
        // (the remaining 3 bytes are the ROM art address, already handled by level loader)
        int plc1Id = rom.readByte(base) & 0xFF;
        int plc2Id = rom.readByte(base + 4) & 0xFF;
        return new int[] { plc1Id, plc2Id };
    }
}
