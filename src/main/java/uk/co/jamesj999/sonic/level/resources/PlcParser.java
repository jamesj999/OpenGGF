package uk.co.jamesj999.sonic.level.resources;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.level.Pattern;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Game-agnostic parser for Pattern Load Cue (PLC) tables.
 *
 * <p>PLCs are Nemesis-compressed art entries stored in an offset table
 * followed by per-PLC data blocks. Each block has a count-1 header word
 * followed by 6-byte entries: 4-byte Nemesis ROM address + 2-byte VRAM
 * destination (tile index x 32).
 *
 * <p>On real hardware, PLC entries decompress into shared VRAM, which means
 * art from different PLCs can overwrite each other (e.g. boss fire art at
 * 0x0482 overlapping spike/spring art at 0x0494). Use {@link #decompressEntry}
 * to decompress into standalone {@link Pattern} arrays that avoid this
 * conflict, or {@link #decompressEntryRaw} when raw bytes are needed for
 * level buffer application.
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
     * Parses a PLC definition using a {@link RomByteReader}.
     * This overload is for tools that hold a RomByteReader rather than a {@link Rom}.
     *
     * @param rom       the ROM byte reader
     * @param tableAddr the base address of the PLC offset table
     * @param plcId     the PLC ID (index into the offset table)
     * @return the parsed PLC definition, or a definition with empty entries on error
     */
    public static PlcDefinition parse(RomByteReader rom, int tableAddr, int plcId) {
        int offsetWord = rom.readU16BE(tableAddr + plcId * 2);
        int plcDataAddr = tableAddr + offsetWord;

        if (plcDataAddr < tableAddr || plcDataAddr >= rom.size()) {
            LOG.warning(String.format("PLC 0x%02X data address 0x%06X out of ROM bounds", plcId, plcDataAddr));
            return new PlcDefinition(plcId, List.of());
        }

        int countMinusOne = rom.readU16BE(plcDataAddr);
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
            int nemAddr = rom.readU32BE(addr) & 0x00FFFFFF;
            int vramDest = rom.readU16BE(addr + 4) & 0xFFFF;
            int tileIndex = vramDest / 32;
            entries.add(new PlcEntry(nemAddr, tileIndex));
        }

        LOG.fine(String.format("Parsed PLC 0x%02X: %d entries", plcId, entries.size()));
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

    // --- Standalone decompression (no level buffer involvement) ---

    /**
     * Decompresses a single PLC entry's Nemesis art into a standalone {@link Pattern} array.
     * This does NOT write into any level pattern buffer, avoiding VRAM overlap conflicts.
     *
     * @param rom   the ROM to read from
     * @param entry the PLC entry to decompress
     * @return the decompressed patterns, or an empty array on failure
     */
    public static Pattern[] decompressEntry(Rom rom, PlcEntry entry) throws IOException {
        byte[] data = decompressEntryRaw(rom, entry);
        return bytesToPatterns(data);
    }

    /**
     * Decompresses all entries in a PLC definition into standalone {@link Pattern} arrays.
     *
     * @param rom        the ROM to read from
     * @param definition the parsed PLC
     * @return list of Pattern arrays, one per PLC entry (same order as entries)
     */
    public static List<Pattern[]> decompressAll(Rom rom, PlcDefinition definition) throws IOException {
        List<Pattern[]> result = new ArrayList<>(definition.entries().size());
        for (PlcEntry entry : definition.entries()) {
            result.add(decompressEntry(rom, entry));
        }
        return result;
    }

    /**
     * Decompresses a single PLC entry's Nemesis art into raw bytes.
     * Useful when the caller needs the raw tile data (e.g. for level buffer application).
     *
     * @param rom   the ROM to read from
     * @param entry the PLC entry to decompress
     * @return the decompressed byte data
     */
    public static byte[] decompressEntryRaw(Rom rom, PlcEntry entry) throws IOException {
        ResourceLoader loader = new ResourceLoader(rom);
        return loader.loadSingle(LoadOp.overlay(entry.romAddr(), CompressionType.NEMESIS, 0));
    }

    private static Pattern[] bytesToPatterns(byte[] data) {
        if (data == null || data.length == 0) {
            return new Pattern[0];
        }
        int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
            byte[] tile = Arrays.copyOfRange(data,
                    i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(tile);
        }
        return patterns;
    }
}
