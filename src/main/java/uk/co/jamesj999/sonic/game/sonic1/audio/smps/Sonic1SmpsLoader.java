package uk.co.jamesj999.sonic.game.sonic1.audio.smps;

import static uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1SmpsConstants.*;

import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Music;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;

import uk.co.jamesj999.sonic.audio.smps.AbstractSmpsData;
import uk.co.jamesj999.sonic.audio.smps.DacData;
import uk.co.jamesj999.sonic.audio.smps.SmpsLoader;
import uk.co.jamesj999.sonic.audio.smps.Sonic1SmpsData;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.tools.DcmDecoder;
import uk.co.jamesj999.sonic.tools.KosinskiReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * SMPS data loader for Sonic 1.
 *
 * <p>Key differences from Sonic 2:
 * <ul>
 *   <li>Pointer tables use 32-bit big-endian absolute ROM addresses (4 bytes per entry).
 *       Sonic 2 uses 16-bit little-endian Z80-relative pointers.</li>
 *   <li>Music and SFX data is stored uncompressed in ROM. Sonic 2 uses Saxman compression
 *       for music.</li>
 *   <li>Within a song, voice and channel pointers are 16-bit big-endian offsets relative
 *       to the song start address. Sonic 2 uses absolute Z80 addresses.</li>
 *   <li>PSG envelope data is loaded from a 9-entry pointer table in ROM.</li>
 * </ul>
 */
public class Sonic1SmpsLoader implements SmpsLoader {
    private static final Logger LOGGER = Logger.getLogger(Sonic1SmpsLoader.class.getName());

    /** Maximum reasonable size for a single SMPS song/SFX blob. */
    private static final int MAX_BLOB_SIZE = 0x4000; // 16 KB safety limit

    private final Rom rom;
    private final Map<Integer, AbstractSmpsData> musicCache = new HashMap<>();
    private final Map<Integer, AbstractSmpsData> sfxCache = new HashMap<>();
    private byte[][] psgEnvelopes;

    public Sonic1SmpsLoader(Rom rom) {
        this.rom = rom;
        loadPsgEnvelopes();
    }

    @Override
    public AbstractSmpsData loadMusic(int musicId) {
        if (musicId < Sonic1Music.ID_BASE || musicId > Sonic1Music.ID_MAX) {
            LOGGER.fine("Music ID 0x" + Integer.toHexString(musicId) + " out of range.");
            return null;
        }

        AbstractSmpsData cached = musicCache.get(musicId);
        if (cached != null) {
            return cached;
        }

        try {
            int index = musicId - Sonic1Music.ID_BASE;
            int romAddr = rom.read32BitAddr(MUSIC_PTR_TABLE_ADDR + index * 4);
            if (romAddr <= 0 || romAddr >= rom.getSize()) {
                LOGGER.warning("Invalid music pointer for ID 0x" + Integer.toHexString(musicId)
                        + ": 0x" + Integer.toHexString(romAddr));
                return null;
            }

            int dataSize = calculateMusicDataSize(index, romAddr);
            byte[] raw = rom.readBytes(romAddr, dataSize);

            LOGGER.info("Loaded S1 music ID 0x" + Integer.toHexString(musicId)
                    + " at ROM 0x" + Integer.toHexString(romAddr)
                    + " (" + raw.length + " bytes)");

            Sonic1SmpsData data = new Sonic1SmpsData(raw, 0);
            data.setPsgEnvelopes(psgEnvelopes);
            data.setId(musicId);
            musicCache.put(musicId, data);
            return data;
        } catch (IOException e) {
            LOGGER.severe("Failed to load S1 music ID 0x" + Integer.toHexString(musicId));
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public AbstractSmpsData loadSfx(int sfxId) {
        if (sfxId < Sonic1Sfx.ID_BASE || sfxId > Sonic1Sfx.ID_MAX) {
            // Check special SFX range
            if (sfxId >= SPECIAL_SFX_ID_BASE
                    && sfxId < SPECIAL_SFX_ID_BASE + SPECIAL_SFX_COUNT) {
                return loadSpecialSfx(sfxId);
            }
            LOGGER.fine("SFX ID 0x" + Integer.toHexString(sfxId) + " out of range.");
            return null;
        }

        AbstractSmpsData cached = sfxCache.get(sfxId);
        if (cached != null) {
            return cached;
        }

        try {
            int index = sfxId - Sonic1Sfx.ID_BASE;
            int romAddr = rom.read32BitAddr(SFX_PTR_TABLE_ADDR + index * 4);
            if (romAddr <= 0 || romAddr >= rom.getSize()) {
                LOGGER.warning("Invalid SFX pointer for ID 0x" + Integer.toHexString(sfxId)
                        + ": 0x" + Integer.toHexString(romAddr));
                return null;
            }

            int dataSize = calculateSfxDataSize(index, romAddr);
            byte[] raw = rom.readBytes(romAddr, dataSize);

            LOGGER.info("Loaded S1 SFX ID 0x" + Integer.toHexString(sfxId)
                    + " at ROM 0x" + Integer.toHexString(romAddr)
                    + " (" + raw.length + " bytes)");

            Sonic1SfxData data = new Sonic1SfxData(raw, 0);
            data.setPsgEnvelopes(psgEnvelopes);
            data.setId(sfxId);
            sfxCache.put(sfxId, data);
            return data;
        } catch (IOException e) {
            LOGGER.severe("Failed to load S1 SFX ID 0x" + Integer.toHexString(sfxId));
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public AbstractSmpsData loadSfx(String sfxName) {
        // Try parsing as hex ID
        if (sfxName != null) {
            try {
                int id = Integer.parseInt(sfxName, 16);
                if (id >= Sonic1Sfx.ID_BASE && id <= Sonic1Sfx.ID_MAX) {
                    return loadSfx(id);
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    @Override
    public DacData loadDacData() {
        try {
            // 1. Decompress the Kosinski-compressed Z80 DAC driver from ROM
            byte[] compressed = rom.readBytes(DAC_DRIVER_ADDR, MAX_BLOB_SIZE);
            byte[] z80Binary = KosinskiReader.decompress(
                    Channels.newChannel(new ByteArrayInputStream(compressed)), false);

            LOGGER.info("Decompressed S1 Z80 DAC driver: " + z80Binary.length
                    + " bytes from ROM 0x" + Integer.toHexString(DAC_DRIVER_ADDR));

            // 2. Parse zPCM_Table at Z80 offset 0x00D6: 3 entries x 8 bytes each
            //    Format per entry: startAddr(LE word), length(LE word), pitch(LE word), pad(2)
            Map<Integer, byte[]> samples = new HashMap<>();
            Map<Integer, DacData.DacEntry> mapping = new HashMap<>();
            DcmDecoder decoder = new DcmDecoder();

            int tableOffset = DAC_PTR_TABLE_Z80_OFFSET;
            int[] sampleRates = new int[DAC_SAMPLE_COUNT];

            for (int i = 0; i < DAC_SAMPLE_COUNT; i++) {
                int entryBase = tableOffset + i * 8;
                if (entryBase + 6 > z80Binary.length) {
                    LOGGER.warning("Z80 binary too short for DAC entry " + i);
                    break;
                }

                // Little-endian 16-bit reads from decompressed Z80 data
                int startAddr = (z80Binary[entryBase] & 0xFF)
                        | ((z80Binary[entryBase + 1] & 0xFF) << 8);
                int sampleLen = (z80Binary[entryBase + 2] & 0xFF)
                        | ((z80Binary[entryBase + 3] & 0xFF) << 8);
                int pitch = (z80Binary[entryBase + 4] & 0xFF)
                        | ((z80Binary[entryBase + 5] & 0xFF) << 8);

                sampleRates[i] = pitch;

                if (startAddr == 0 || sampleLen == 0) {
                    LOGGER.fine("DAC sample " + i + " has zero addr/len, skipping");
                    continue;
                }
                if (startAddr + sampleLen > z80Binary.length) {
                    LOGGER.warning("DAC sample " + i + " extends beyond Z80 binary"
                            + " (addr=0x" + Integer.toHexString(startAddr)
                            + ", len=" + sampleLen
                            + ", z80size=" + z80Binary.length + ")");
                    // Clamp to available data
                    sampleLen = z80Binary.length - startAddr;
                    if (sampleLen <= 0) continue;
                }

                // Extract DPCM bytes and decode
                byte[] dpcmData = new byte[sampleLen];
                System.arraycopy(z80Binary, startAddr, dpcmData, 0, sampleLen);
                byte[] pcm = decoder.decode(dpcmData);

                int sampleId = DAC_SAMPLE_ID_BASE + i;
                samples.put(sampleId, pcm);

                LOGGER.info("Loaded S1 DAC sample 0x" + Integer.toHexString(sampleId)
                        + ": " + sampleLen + " DPCM bytes -> " + pcm.length
                        + " PCM bytes, pitch=" + pitch);
            }

            // 3. Build note-to-sample mapping
            // 0x81 = Kick, 0x82 = Snare, 0x83 = Timpani (direct samples)
            for (int i = 0; i < DAC_SAMPLE_COUNT; i++) {
                int noteId = DAC_SAMPLE_ID_BASE + i;
                if (samples.containsKey(noteId)) {
                    mapping.put(noteId, new DacData.DacEntry(noteId, sampleRates[i]));
                }
            }

            // 4. Timpani pitch variants 0x88-0x8B
            //    Read 4 pitch modifier bytes from ROM at DAC_SAMPLE_RATE_TABLE_ADDR
            int timpaniSampleId = DAC_SAMPLE_ID_BASE + 2; // 0x83
            for (int i = 0; i < 4; i++) {
                int pitchByte = rom.readByte(DAC_SAMPLE_RATE_TABLE_ADDR + i) & 0xFF;
                if (pitchByte == 0xFF) continue; // invalid entry
                int noteId = 0x88 + i;
                mapping.put(noteId, new DacData.DacEntry(timpaniSampleId, pitchByte));

                LOGGER.fine("Timpani variant 0x" + Integer.toHexString(noteId)
                        + " -> sample 0x" + Integer.toHexString(timpaniSampleId)
                        + ", rate=" + pitchByte);
            }

            return new DacData(samples, mapping, 301); // S1 baseCycles = 301
        } catch (Exception e) {
            LOGGER.severe("Failed to load S1 DAC data: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Returns the loaded PSG envelope data for external use.
     */
    public byte[][] getPsgEnvelopes() {
        return psgEnvelopes;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private AbstractSmpsData loadSpecialSfx(int sfxId) {
        AbstractSmpsData cached = sfxCache.get(sfxId);
        if (cached != null) {
            return cached;
        }

        try {
            int index = sfxId - SPECIAL_SFX_ID_BASE;
            int romAddr = rom.read32BitAddr(SPECIAL_SFX_PTR_TABLE_ADDR + index * 4);
            if (romAddr <= 0 || romAddr >= rom.getSize()) {
                LOGGER.warning("Invalid special SFX pointer for ID 0x"
                        + Integer.toHexString(sfxId));
                return null;
            }

            // Special SFX: use a generous read since there's only one entry
            int available = (int) Math.min(MAX_BLOB_SIZE, rom.getSize() - romAddr);
            byte[] raw = rom.readBytes(romAddr, available);

            Sonic1SfxData data = new Sonic1SfxData(raw, 0);
            data.setPsgEnvelopes(psgEnvelopes);
            data.setId(sfxId);
            sfxCache.put(sfxId, data);
            return data;
        } catch (IOException e) {
            LOGGER.severe("Failed to load special SFX ID 0x" + Integer.toHexString(sfxId));
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Calculate music data size by reading the next entry's pointer.
     * For the last entry, uses the known SFX pointer table start as boundary.
     */
    private int calculateMusicDataSize(int index, int romAddr) throws IOException {
        int nextAddr;
        if (index < MUSIC_COUNT - 1) {
            // Read the next music entry's pointer
            nextAddr = rom.read32BitAddr(MUSIC_PTR_TABLE_ADDR + (index + 1) * 4);
            if (nextAddr > romAddr && nextAddr < romAddr + MAX_BLOB_SIZE) {
                return nextAddr - romAddr;
            }
        }

        // For the last entry or if next pointer is not usable, scan forward
        // through subsequent pointers to find a reasonable boundary.
        // As a fallback, use a generous read size.
        int bestBound = romAddr + MAX_BLOB_SIZE;
        for (int i = index + 1; i < MUSIC_COUNT; i++) {
            int candidate = rom.read32BitAddr(MUSIC_PTR_TABLE_ADDR + i * 4);
            if (candidate > romAddr && candidate < bestBound) {
                bestBound = candidate;
                break;
            }
        }

        int size = bestBound - romAddr;
        int available = (int) Math.min(size, rom.getSize() - romAddr);
        return Math.min(available, MAX_BLOB_SIZE);
    }

    /**
     * Calculate SFX data size by reading the next entry's pointer.
     * For the last entry, uses a generous read.
     */
    private int calculateSfxDataSize(int index, int romAddr) throws IOException {
        // Try next SFX pointer
        for (int i = index + 1; i < SFX_COUNT; i++) {
            int candidate = rom.read32BitAddr(SFX_PTR_TABLE_ADDR + i * 4);
            if (candidate > romAddr && candidate < romAddr + MAX_BLOB_SIZE) {
                return candidate - romAddr;
            }
        }

        // If no next SFX pointer found, try special SFX table
        if (SPECIAL_SFX_COUNT > 0) {
            int candidate = rom.read32BitAddr(SPECIAL_SFX_PTR_TABLE_ADDR);
            if (candidate > romAddr && candidate < romAddr + MAX_BLOB_SIZE) {
                return candidate - romAddr;
            }
        }

        // Fallback: read a reasonable chunk
        int available = (int) Math.min(MAX_BLOB_SIZE, rom.getSize() - romAddr);
        return Math.min(available, 0x800); // 2 KB for SFX
    }

    /**
     * Load PSG envelope data from ROM.
     *
     * <p>The PSG envelope pointer table has 9 entries of 4 bytes each (32-bit BE pointers).
     * Each envelope is a sequence of attenuation bytes terminated by 0x80 (HOLD).
     */
    private void loadPsgEnvelopes() {
        psgEnvelopes = new byte[9][];
        for (int i = 0; i < 9; i++) {
            try {
                int ptrAddr = PSG_ENV_PTR_TABLE_ADDR + i * 4;
                int envAddr = rom.read32BitAddr(ptrAddr);
                if (envAddr <= 0 || envAddr >= rom.getSize()) {
                    LOGGER.fine("Invalid PSG envelope pointer " + i
                            + " at 0x" + Integer.toHexString(ptrAddr));
                    psgEnvelopes[i] = new byte[] { (byte) 0x80 }; // empty hold
                    continue;
                }

                List<Byte> bytes = new ArrayList<>();
                int offset = 0;
                while (offset < 256) { // safety limit
                    int b = rom.readByte(envAddr + offset) & 0xFF;
                    bytes.add((byte) b);
                    if (b == 0x80) {
                        break; // HOLD terminator
                    }
                    offset++;
                }

                psgEnvelopes[i] = new byte[bytes.size()];
                for (int j = 0; j < bytes.size(); j++) {
                    psgEnvelopes[i][j] = bytes.get(j);
                }

                LOGGER.fine("Loaded PSG envelope " + i + " from 0x"
                        + Integer.toHexString(envAddr) + " (" + bytes.size() + " bytes)");
            } catch (IOException e) {
                LOGGER.warning("Failed to load PSG envelope " + i);
                psgEnvelopes[i] = new byte[] { (byte) 0x80 }; // fallback
            }
        }
    }
}
