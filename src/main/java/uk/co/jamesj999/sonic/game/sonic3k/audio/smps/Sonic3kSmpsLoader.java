package uk.co.jamesj999.sonic.game.sonic3k.audio.smps;

import static uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kSmpsConstants.*;

import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;
import uk.co.jamesj999.sonic.audio.smps.DacData;
import uk.co.jamesj999.sonic.audio.smps.SmpsLoader;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.tools.KosinskiReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * SMPS data loader for Sonic 3 &amp; Knuckles.
 *
 * <p>S3K uses a Z80 sound driver that is Kosinski-compressed in ROM. The driver
 * code lives at {@link Sonic3kSmpsConstants#Z80_DRIVER_ADDR}, and additional data
 * (pointer tables, PSG envelopes, instrument table) at
 * {@link Sonic3kSmpsConstants#Z80_ADDITIONAL_DATA_ADDR} (loaded to Z80 RAM 0x1300).
 *
 * <p>Key differences from Sonic 1/2 loaders:
 * <ul>
 *   <li>Music data is bank-switched raw data (not Saxman compressed like S2).</li>
 *   <li>A music bank list maps each music ID to a ROM bank address.</li>
 *   <li>A music pointer list gives Z80 offsets within each bank.</li>
 *   <li>SFX are in a single bank (like S2), pointed to by the SFX pointer list.</li>
 *   <li>A global instrument table is shared across songs.</li>
 *   <li>DAC samples use DPCM compression with bank-switching.</li>
 * </ul>
 */
public class Sonic3kSmpsLoader implements SmpsLoader {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSmpsLoader.class.getName());

    /** Maximum reasonable size for a single SMPS song/SFX blob. */
    private static final int MAX_BLOB_SIZE = 0x8000; // 32 KB (one Z80 bank)

    /** Voice table padding for SFX (25 bytes per voice, generous margin). */
    private static final int SFX_VOICE_TABLE_PADDING = 0x100;

    private final Rom rom;
    private final Map<Integer, AbstractSmpsData> musicCache = new HashMap<>();
    private final Map<Integer, AbstractSmpsData> sfxCache = new HashMap<>();

    // Decompressed Z80 driver data
    private byte[] z80Driver;
    private byte[] z80AdditionalData;

    // Parsed from Z80 data
    private byte[] globalVoiceData;
    private Map<Integer, byte[]> psgEnvelopes;
    private int[] musicBankAddresses;
    private int[] musicPointers;
    private int[] sfxPointers;

    public Sonic3kSmpsLoader(Rom rom) {
        this.rom = rom;
        decompressZ80Data();
        parseZ80Tables();
        loadPsgEnvelopes();
        loadGlobalInstrumentTable();
    }

    @Override
    public AbstractSmpsData loadMusic(int musicId) {
        if (musicId <= 0 || musicBankAddresses == null || musicPointers == null) {
            LOGGER.fine("Cannot load S3K music ID 0x" + Integer.toHexString(musicId)
                    + ": driver data not loaded.");
            return null;
        }

        AbstractSmpsData cached = musicCache.get(musicId);
        if (cached != null) {
            return cached;
        }

        try {
            // Music bank list has one entry per music ID (2 bytes each, LE).
            // The entry is a ROM bank address (upper 17 bits of a 24-bit address,
            // shifted right by 15, stored as a word that the Z80 uses for bank switching).
            if (musicId >= musicBankAddresses.length) {
                LOGGER.warning("S3K music ID 0x" + Integer.toHexString(musicId)
                        + " exceeds bank list size (" + musicBankAddresses.length + ").");
                return null;
            }

            int bankAddr = musicBankAddresses[musicId];
            if (bankAddr == 0) {
                LOGGER.fine("S3K music ID 0x" + Integer.toHexString(musicId) + " has null bank.");
                return null;
            }

            // Music pointer list has one entry per music ID (2 bytes each, LE).
            // This is a Z80 address (0x8000-based) within the bank.
            if (musicId >= musicPointers.length) {
                LOGGER.warning("S3K music ID 0x" + Integer.toHexString(musicId)
                        + " exceeds pointer list size.");
                return null;
            }

            int z80Ptr = musicPointers[musicId];
            if (z80Ptr == 0) {
                LOGGER.fine("S3K music ID 0x" + Integer.toHexString(musicId)
                        + " has null pointer.");
                return null;
            }

            // Convert Z80 bank-relative pointer to ROM address
            int bankOffset = z80Ptr & Z80_BANK_MASK;
            int romAddr = bankAddr + bankOffset;

            if (romAddr <= 0 || romAddr >= rom.getSize()) {
                LOGGER.warning("Invalid S3K music ROM address for ID 0x"
                        + Integer.toHexString(musicId) + ": 0x" + Integer.toHexString(romAddr));
                return null;
            }

            // Calculate data size (bounded by bank end or next music entry)
            int dataSize = calculateMusicDataSize(musicId, romAddr, bankAddr);
            byte[] raw = rom.readBytes(romAddr, dataSize);

            LOGGER.info("Loaded S3K music ID 0x" + Integer.toHexString(musicId)
                    + " at ROM 0x" + Integer.toHexString(romAddr)
                    + " (bank 0x" + Integer.toHexString(bankAddr)
                    + ", z80Ptr=0x" + Integer.toHexString(z80Ptr)
                    + ", " + raw.length + " bytes)");
            LOGGER.info("  First 8 bytes: " + hexDump(raw, 0, 8));

            Sonic3kSmpsData data = new Sonic3kSmpsData(raw, z80Ptr);
            data.setPsgEnvelopes(psgEnvelopes);
            data.setGlobalVoiceData(globalVoiceData);
            data.setId(musicId);
            musicCache.put(musicId, data);
            return data;
        } catch (IOException e) {
            LOGGER.severe("Failed to load S3K music ID 0x" + Integer.toHexString(musicId)
                    + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public AbstractSmpsData loadSfx(int sfxId) {
        if (sfxId < SFX_ID_BASE || sfxId > SFX_ID_MAX) {
            LOGGER.fine("S3K SFX ID 0x" + Integer.toHexString(sfxId) + " out of range.");
            return null;
        }

        AbstractSmpsData cached = sfxCache.get(sfxId);
        if (cached != null) {
            return cached;
        }

        try {
            int index = sfxId - SFX_ID_BASE;
            if (sfxPointers == null || index >= sfxPointers.length) {
                LOGGER.warning("S3K SFX ID 0x" + Integer.toHexString(sfxId)
                        + " exceeds pointer list size.");
                return null;
            }

            int z80Ptr = sfxPointers[index];
            if (z80Ptr == 0) {
                return null;
            }

            // SFX are in the bank at SFX_BANK_BASE
            int bankOffset = z80Ptr & Z80_BANK_MASK;
            int romOffset = SFX_BANK_BASE + bankOffset;
            int headerOffset = bankOffset;

            // Calculate SFX length
            int sfxLength = computeSfxLength(index, romOffset);

            // Extend buffer for voice table
            int voicePtr = readLE16FromRom(romOffset);
            int voiceOffset = (voicePtr == 0) ? -1 : (voicePtr & Z80_BANK_MASK);
            int voiceReach = (voiceOffset < 0) ? 0 : voiceOffset + SFX_VOICE_TABLE_PADDING;
            int readLength = Math.max(headerOffset + sfxLength, voiceReach);
            int bankLimit = Z80_BANK_BASE;
            if (readLength > bankLimit) {
                readLength = bankLimit;
            }

            byte[] raw = rom.readBytes(SFX_BANK_BASE, readLength);

            Sonic3kSfxData sfx = new Sonic3kSfxData(raw, Z80_BANK_BASE, 0, headerOffset);
            sfx.setPsgEnvelopes(psgEnvelopes);
            sfx.setId(sfxId);

            if (sfx.getTrackEntries().isEmpty()) {
                LOGGER.fine("S3K SFX ID 0x" + Integer.toHexString(sfxId) + " has no tracks.");
                return null;
            }

            sfxCache.put(sfxId, sfx);
            return sfx;
        } catch (Exception e) {
            LOGGER.severe("Failed to load S3K SFX ID 0x" + Integer.toHexString(sfxId)
                    + ": " + e.getMessage());
            return null;
        }
    }

    @Override
    public AbstractSmpsData loadSfx(String sfxName) {
        if (sfxName != null) {
            try {
                int id = Integer.parseInt(sfxName, 16);
                if (id >= SFX_ID_BASE && id <= SFX_ID_MAX) {
                    return loadSfx(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @Override
    public DacData loadDacData() {
        Map<Integer, byte[]> samples = new HashMap<>();
        Map<Integer, DacData.DacEntry> mapping = new HashMap<>();

        if (z80Driver == null) {
            LOGGER.warning("Z80 driver not loaded, cannot load DAC data.");
            return new DacData(samples, mapping);
        }

        try {
            Sonic3kDpcmDecoder decoder = new Sonic3kDpcmDecoder();

            // DAC bank list at Z80_DAC_BANK_LIST (starts with entry for note 0x80).
            // Each entry is 1 BYTE (bank number); ROM bank = bankByte << 15.
            // DAC drum pointer list at Z80_DAC_DRUM_PTR_LIST (starts with entry for note 0x81).
            // Each entry is 4 bytes: 2-byte pointer (LE) + 2-byte length (LE).

            int dacBankListOffset = Z80_DAC_BANK_LIST;
            int dacDrumPtrOffset = Z80_DAC_DRUM_PTR_LIST;

            // Vanilla S&K has DAC notes 0x81-0x9B plus 0xB2/0xB3 present
            for (int noteId = DAC_NOTE_BASE; noteId <= DAC_NOTE_MAX; noteId++) {
                // Bank list entry for this note (note 0x80 is index 0)
                int bankIndex = noteId - (DAC_NOTE_BASE - 1); // note 0x81 = index 1
                int bankEntryOffset = dacBankListOffset + bankIndex;

                if (bankEntryOffset >= z80Driver.length) {
                    break;
                }

                int bankByte = z80Driver[bankEntryOffset] & 0xFF;
                if (bankByte == 0) {
                    continue;
                }

                // Convert bank byte to ROM address: bankByte << 15
                int romBank = bankByte << 15;

                // Drum pointer list entry (note 0x81 is index 0)
                int drumIndex = noteId - DAC_NOTE_BASE;
                int drumEntryOffset = dacDrumPtrOffset + (drumIndex * 4);

                // Drum pointers are in the bank-switched area (0x8000+)
                // We need to read from the ROM bank
                if (drumEntryOffset + 3 >= Z80_BANK_BASE) {
                    // Drum pointer list is at the start of the switched bank.
                    // Read from the ROM bank directly.
                    int romDrumAddr = romBank + (drumEntryOffset & Z80_BANK_MASK);
                    if (romDrumAddr + 3 >= rom.getSize()) {
                        continue;
                    }

                    int samplePtr = readLE16FromRom(romDrumAddr);
                    int sampleLen = readLE16FromRom(romDrumAddr + 2);

                    if (samplePtr == 0 || sampleLen == 0) {
                        continue;
                    }

                    int sampleRomAddr = romBank + (samplePtr & Z80_BANK_MASK);
                    if (sampleRomAddr + sampleLen > rom.getSize()) {
                        sampleLen = (int) (rom.getSize() - sampleRomAddr);
                        if (sampleLen <= 0) continue;
                    }

                    byte[] compressed = rom.readBytes(sampleRomAddr, sampleLen);
                    byte[] pcm = decoder.decode(compressed);

                    samples.put(noteId, pcm);
                    mapping.put(noteId, new DacData.DacEntry(noteId, 0));

                    LOGGER.fine("Loaded S3K DAC note 0x" + Integer.toHexString(noteId)
                            + ": " + sampleLen + " DPCM bytes -> " + pcm.length + " PCM bytes"
                            + " (bank 0x" + Integer.toHexString(romBank) + ")");
                }
            }

            LOGGER.info("Loaded " + samples.size() + " S3K DAC samples.");
            return new DacData(samples, mapping);
        } catch (Exception e) {
            LOGGER.severe("Failed to load S3K DAC data: " + e.getMessage());
            return new DacData(samples, mapping);
        }
    }

    /**
     * Returns the loaded PSG envelope data.
     */
    public Map<Integer, byte[]> getPsgEnvelopes() {
        return psgEnvelopes;
    }

    /**
     * Returns the global instrument (voice) table data.
     */
    public byte[] getGlobalVoiceData() {
        return globalVoiceData;
    }

    // -----------------------------------------------------------------------
    // Z80 driver decompression and table parsing
    // -----------------------------------------------------------------------

    @Override
    public int findMusicOffset(int musicId) {
        if (musicId <= 0 || musicBankAddresses == null || musicPointers == null
                || musicId >= musicBankAddresses.length || musicId >= musicPointers.length) {
            return -1;
        }
        int bankAddr = musicBankAddresses[musicId];
        int z80Ptr = musicPointers[musicId];
        if (bankAddr == 0 || z80Ptr == 0) return -1;
        return bankAddr + (z80Ptr & Z80_BANK_MASK);
    }

    private void decompressZ80Data() {
        try {
            // Decompress the main Z80 driver
            byte[] compressed = rom.readBytes(Z80_DRIVER_ADDR, MAX_BLOB_SIZE);
            z80Driver = KosinskiReader.decompress(
                    Channels.newChannel(new ByteArrayInputStream(compressed)), false);
            LOGGER.info("Decompressed S3K Z80 driver: " + z80Driver.length
                    + " bytes from ROM 0x" + Integer.toHexString(Z80_DRIVER_ADDR));
            LOGGER.info("  First 16 bytes: " + hexDump(z80Driver, 0, 16));

            // Decompress additional Z80 data (goes to Z80 RAM 0x1300)
            compressed = rom.readBytes(Z80_ADDITIONAL_DATA_ADDR, MAX_BLOB_SIZE);
            z80AdditionalData = KosinskiReader.decompress(
                    Channels.newChannel(new ByteArrayInputStream(compressed)), false);
            LOGGER.info("Decompressed S3K Z80 additional data: " + z80AdditionalData.length
                    + " bytes from ROM 0x" + Integer.toHexString(Z80_ADDITIONAL_DATA_ADDR));
            LOGGER.info("  First 16 bytes: " + hexDump(z80AdditionalData, 0, 16));
        } catch (Exception e) {
            LOGGER.severe("Failed to decompress S3K Z80 data: " + e.getMessage());
        }
    }

    private void parseZ80Tables() {
        if (z80Driver == null || z80AdditionalData == null) {
            LOGGER.warning("Z80 data not available, cannot parse tables.");
            return;
        }

        // The additional data is loaded at Z80 RAM 0x1300.
        // Music bank list is at Z80 RAM offset Z80_MUSIC_BANK_LIST (0x0B65) - in the main driver.
        // Music pointer list is at Z80 RAM offset Z80_MUSIC_PTR_LIST (0x1618) - in the additional data.
        // SFX pointer list is at Z80 RAM offset Z80_SFX_PTR_LIST (0x167E) - in the additional data.

        // Parse music bank list from main driver data
        parseMusicBankList();

        // Parse music pointer list from additional data
        parseMusicPointerList();

        // Parse SFX pointer list from additional data
        parseSfxPointerList();
    }

    private void parseMusicBankList() {
        int offset = Z80_MUSIC_BANK_LIST;
        if (offset + 1 > z80Driver.length) {
            LOGGER.warning("Music bank list offset 0x" + Integer.toHexString(offset)
                    + " exceeds Z80 driver size.");
            return;
        }

        // Estimate count: read entries until we hit the next known table or end of reasonable range
        int maxEntries = 0x60; // generous upper bound
        musicBankAddresses = new int[maxEntries];

        for (int i = 0; i < maxEntries; i++) {
            // Bank list entries are 1 BYTE each (not 2-byte words).
            // Each byte is a Z80 bank number; the ROM address is bankByte << 15.
            int entryOffset = offset + i;
            if (entryOffset >= z80Driver.length) {
                break;
            }
            int bankByte = z80Driver[entryOffset] & 0xFF;
            musicBankAddresses[i] = bankByte << 15;
        }

        // Log first 5 entries for diagnostics
        StringBuilder sb = new StringBuilder("S3K music bank list (first 5): ");
        for (int i = 0; i < Math.min(5, maxEntries); i++) {
            if (i > 0) sb.append(", ");
            sb.append("[").append(i).append("]=0x").append(Integer.toHexString(musicBankAddresses[i]));
        }
        LOGGER.info(sb.toString());
        LOGGER.info("Parsed S3K music bank list: " + maxEntries + " entries from offset 0x"
                + Integer.toHexString(offset));
    }

    private void parseMusicPointerList() {
        // Additional data is loaded at Z80 RAM 0x1300
        // Music pointer list is at Z80 RAM 0x1618
        int additionalBase = Z80_GENERAL_PTR_LIST; // 0x1300
        int listZ80Addr = Z80_MUSIC_PTR_LIST;     // 0x1618
        int relOffset = listZ80Addr - additionalBase;

        if (relOffset < 0 || relOffset + 2 > z80AdditionalData.length) {
            LOGGER.warning("Music pointer list offset 0x" + Integer.toHexString(relOffset)
                    + " exceeds additional data size.");
            return;
        }

        int maxEntries = 0x60;
        musicPointers = new int[maxEntries];

        for (int i = 0; i < maxEntries; i++) {
            int entryOffset = relOffset + (i * 2);
            if (entryOffset + 1 >= z80AdditionalData.length) {
                break;
            }
            musicPointers[i] = readLE16(z80AdditionalData, entryOffset);
        }

        // Log first 5 entries for diagnostics
        StringBuilder sb = new StringBuilder("S3K music pointer list (first 5): ");
        for (int i = 0; i < Math.min(5, maxEntries); i++) {
            if (i > 0) sb.append(", ");
            sb.append("[").append(i).append("]=0x").append(Integer.toHexString(musicPointers[i]));
        }
        LOGGER.info(sb.toString());
        LOGGER.info("Parsed S3K music pointer list: " + maxEntries + " entries.");
    }

    private void parseSfxPointerList() {
        int additionalBase = Z80_GENERAL_PTR_LIST;
        int listZ80Addr = Z80_SFX_PTR_LIST;
        int relOffset = listZ80Addr - additionalBase;

        if (relOffset < 0 || relOffset + 2 > z80AdditionalData.length) {
            LOGGER.warning("SFX pointer list offset 0x" + Integer.toHexString(relOffset)
                    + " exceeds additional data size.");
            return;
        }

        int maxEntries = SFX_ID_MAX - SFX_ID_BASE + 1; // 0x60 entries
        sfxPointers = new int[maxEntries];

        for (int i = 0; i < maxEntries; i++) {
            int entryOffset = relOffset + (i * 2);
            if (entryOffset + 1 >= z80AdditionalData.length) {
                break;
            }
            sfxPointers[i] = readLE16(z80AdditionalData, entryOffset);
        }

        LOGGER.fine("Parsed S3K SFX pointer list: " + maxEntries + " entries.");
    }

    private void loadPsgEnvelopes() {
        psgEnvelopes = new HashMap<>();

        if (z80AdditionalData == null) {
            return;
        }

        int additionalBase = Z80_GENERAL_PTR_LIST;
        int listZ80Addr = Z80_PSG_PTR_LIST;        // 0x1387
        int relOffset = listZ80Addr - additionalBase;

        if (relOffset < 0 || relOffset + 2 > z80AdditionalData.length) {
            LOGGER.warning("PSG pointer list not available.");
            return;
        }

        // Read PSG envelope pointers (2 bytes each, LE)
        int maxEnvelopes = 40; // generous upper bound
        for (int i = 1; i <= maxEnvelopes; i++) {
            int entryOffset = relOffset + ((i - 1) * 2);
            if (entryOffset + 1 >= z80AdditionalData.length) {
                break;
            }

            int ptr = readLE16(z80AdditionalData, entryOffset);
            if (ptr == 0) {
                continue;
            }

            // Resolve the pointer within the additional data
            int envRelOffset = ptr - additionalBase;
            if (envRelOffset < 0 || envRelOffset >= z80AdditionalData.length) {
                // Try as main driver offset
                if (ptr < z80Driver.length) {
                    envRelOffset = ptr;
                    loadEnvelopeFromData(i, z80Driver, envRelOffset);
                    continue;
                }
                continue;
            }

            loadEnvelopeFromData(i, z80AdditionalData, envRelOffset);
        }

        LOGGER.info("Loaded " + psgEnvelopes.size() + " S3K PSG envelopes.");
    }

    private void loadEnvelopeFromData(int id, byte[] sourceData, int offset) {
        // Read envelope bytes until a terminator command (0x80-0x84)
        byte[] buffer = new byte[256];
        int len = 0;

        for (int j = 0; j < 256 && (offset + j) < sourceData.length; j++) {
            int b = sourceData[offset + j] & 0xFF;
            buffer[len++] = (byte) b;

            if (b == 0x80 || b == 0x81 || b == 0x83) {
                break; // RESET, HOLD, VOLSTOP_MODHOLD
            } else if (b == 0x82 || b == 0x84) {
                // LOOP (82) or CHG_MULT (84) - takes a parameter byte
                j++;
                if (offset + j < sourceData.length) {
                    buffer[len++] = sourceData[offset + j];
                }
                if (b == 0x82) break; // LOOP terminates
            }
        }

        if (len > 0) {
            byte[] env = new byte[len];
            System.arraycopy(buffer, 0, env, 0, len);
            psgEnvelopes.put(id, env);
        }
    }

    private void loadGlobalInstrumentTable() {
        if (z80AdditionalData == null) {
            return;
        }

        int additionalBase = Z80_GENERAL_PTR_LIST;
        int tableZ80Addr = Z80_GLOBAL_INSTRUMENT_TABLE; // 0x17D8
        int relOffset = tableZ80Addr - additionalBase;

        if (relOffset < 0 || relOffset >= z80AdditionalData.length) {
            LOGGER.warning("Global instrument table not available at offset 0x"
                    + Integer.toHexString(relOffset));
            return;
        }

        // Read the remaining data from this point as the instrument table.
        // Voices are 25 bytes each; read as many complete voices as available.
        int available = z80AdditionalData.length - relOffset;
        int voiceCount = available / 25;
        int tableSize = voiceCount * 25;

        if (tableSize > 0) {
            globalVoiceData = new byte[tableSize];
            System.arraycopy(z80AdditionalData, relOffset, globalVoiceData, 0, tableSize);
            LOGGER.info("Loaded S3K global instrument table: " + voiceCount
                    + " voices (" + tableSize + " bytes).");
        }
    }

    // -----------------------------------------------------------------------
    // Size calculation helpers
    // -----------------------------------------------------------------------

    private int calculateMusicDataSize(int musicId, int romAddr, int bankAddr) throws IOException {
        int bankEnd = bankAddr + Z80_BANK_BASE; // End of 32KB bank

        // Try to find the next music entry in the same bank
        if (musicPointers != null && musicBankAddresses != null) {
            int bestNextAddr = bankEnd;
            for (int i = 0; i < musicPointers.length; i++) {
                if (i == musicId) continue;
                if (i < musicBankAddresses.length && musicBankAddresses[i] == bankAddr) {
                    int candidatePtr = musicPointers[i];
                    if (candidatePtr != 0) {
                        int candidateRom = bankAddr + (candidatePtr & Z80_BANK_MASK);
                        if (candidateRom > romAddr && candidateRom < bestNextAddr) {
                            bestNextAddr = candidateRom;
                        }
                    }
                }
            }

            int size = bestNextAddr - romAddr;
            if (size > 0 && size <= MAX_BLOB_SIZE) {
                return Math.min(size, (int) (rom.getSize() - romAddr));
            }
        }

        // Fallback: read to end of bank
        int size = bankEnd - romAddr;
        if (size <= 0 || size > MAX_BLOB_SIZE) {
            size = MAX_BLOB_SIZE;
        }
        return Math.min(size, (int) (rom.getSize() - romAddr));
    }

    private int computeSfxLength(int tableIndex, int romOffset) {
        int bankEnd = SFX_BANK_BASE + Z80_BANK_BASE;

        // Find next SFX pointer
        if (sfxPointers != null) {
            for (int i = tableIndex + 1; i < sfxPointers.length; i++) {
                int ptr = sfxPointers[i];
                if (ptr != 0) {
                    int candidate = SFX_BANK_BASE + (ptr & Z80_BANK_MASK);
                    if (candidate > romOffset) {
                        int length = candidate - romOffset;
                        return Math.max(length, 16);
                    }
                }
            }
        }

        int length = bankEnd - romOffset;
        if (length <= 0 || length > (bankEnd - SFX_BANK_BASE)) {
            length = bankEnd - SFX_BANK_BASE;
        }
        return Math.max(length, 16);
    }

    // -----------------------------------------------------------------------
    // Byte reading helpers
    // -----------------------------------------------------------------------

    private static int readLE16(byte[] data, int offset) {
        if (offset + 1 >= data.length) return 0;
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private int readLE16FromRom(int romAddr) throws IOException {
        int lo = rom.readByte(romAddr) & 0xFF;
        int hi = rom.readByte(romAddr + 1) & 0xFF;
        return lo | (hi << 8);
    }

    private static String hexDump(byte[] data, int offset, int length) {
        if (data == null) return "(null)";
        StringBuilder sb = new StringBuilder();
        int end = Math.min(offset + length, data.length);
        for (int i = offset; i < end; i++) {
            if (i > offset) sb.append(' ');
            sb.append(String.format("%02X", data[i] & 0xFF));
        }
        return sb.toString();
    }
}
