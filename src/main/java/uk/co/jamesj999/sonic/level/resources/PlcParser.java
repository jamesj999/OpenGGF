package uk.co.jamesj999.sonic.level.resources;

import uk.co.jamesj999.sonic.data.Rom;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Game-agnostic parser for Pattern Load Cue (PLC) tables.
 *
 * <p>PLCs are Nemesis-compressed art entries stored in an offset table
 * followed by per-PLC data blocks. Each block has a count-1 header word
 * followed by 6-byte entries: 4-byte Nemesis ROM address + 2-byte VRAM
 * destination (tile index x 32).
 */
public final class PlcParser {
    private static final Logger LOG = Logger.getLogger(PlcParser.class.getName());
    private static final int PLC_ENTRY_SIZE = 6;

    private PlcParser() {}

    /** A single PLC entry: Nemesis art at {@code romAddr} destined for {@code tileIndex}. */
    public record PlcEntry(int romAddr, int tileIndex) {}

    /** A parsed PLC definition with its ID and list of entries. */
    public record PlcDefinition(int plcId, List<PlcEntry> entries) {}

    /**
     * Parses a PLC definition from ROM.
     *
     * @param rom       the ROM to read from
     * @param tableAddr the base address of the PLC offset table
     * @param plcId     the PLC ID (index into the offset table)
     * @return the parsed PLC definition, or a definition with empty entries if the count is negative
     */
    public static PlcDefinition parse(Rom rom, int tableAddr, int plcId) throws IOException {
        int offsetWord = rom.read16BitAddr(tableAddr + plcId * 2);
        int plcDataAddr = tableAddr + offsetWord;

        if (plcDataAddr < tableAddr || plcDataAddr >= (int) rom.getSize()) {
            LOG.warning(String.format("PLC 0x%02X data address 0x%06X out of ROM bounds", plcId, plcDataAddr));
            return new PlcDefinition(plcId, List.of());
        }

        int countMinusOne = rom.read16BitAddr(plcDataAddr);
        if ((countMinusOne & 0x8000) != 0) {
            return new PlcDefinition(plcId, List.of());
        }

        int entryCount = (countMinusOne & 0xFFFF) + 1;
        if (entryCount > 256) {
            LOG.warning(String.format("PLC 0x%02X has implausible entry count %d (raw word=0x%04X) - corrupt data?",
                    plcId, entryCount, countMinusOne));
            return new PlcDefinition(plcId, List.of());
        }
        List<PlcEntry> entries = new ArrayList<>(entryCount);
        int entryBase = plcDataAddr + 2;

        for (int i = 0; i < entryCount; i++) {
            int addr = entryBase + i * PLC_ENTRY_SIZE;
            int nemAddr = rom.read32BitAddr(addr) & 0x00FFFFFF;
            int vramDest = rom.read16BitAddr(addr + 4) & 0xFFFF;
            int tileIndex = vramDest / 32;
            entries.add(new PlcEntry(nemAddr, tileIndex));
        }

        LOG.info(String.format("Parsed PLC 0x%02X: %d entries", plcId, entries.size()));
        return new PlcDefinition(plcId, entries);
    }

    /**
     * Converts a PLC definition into {@link LoadOp} entries.
     * Each entry becomes a Nemesis overlay at the appropriate byte offset.
     */
    public static List<LoadOp> toPatternOps(PlcDefinition definition) {
        List<LoadOp> ops = new ArrayList<>(definition.entries().size());
        for (PlcEntry entry : definition.entries()) {
            int destOffset = entry.tileIndex() * 32;
            ops.add(LoadOp.overlay(entry.romAddr(), CompressionType.NEMESIS, destOffset));
        }
        return ops;
    }
}
