package uk.co.jamesj999.sonic.tools.introspector;

import uk.co.jamesj999.sonic.game.profile.AddressEntry;
import uk.co.jamesj999.sonic.game.profile.RomProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Introspection chain that discovers Sonic 2 audio data structures by
 * scanning the ROM for known byte patterns.
 *
 * <p>Discovers:
 * <ul>
 *   <li>Music pointer table (via flags byte pattern)</li>
 *   <li>SFX pointer table</li>
 *   <li>PCM sample pointer table</li>
 *   <li>PSG envelope table</li>
 *   <li>Music flags table</li>
 * </ul>
 *
 * <p>Sonic 2 uses a Z80-based SMPS sound driver. The music data is stored
 * in two banks (0x0F0000 and 0x0F8000). The pointer table and metadata
 * structures are in the 0x0EC000-0x0ED000 range.</p>
 */
public class Sonic2AudioChain implements IntrospectionChain {

    private static final Logger LOG = Logger.getLogger(Sonic2AudioChain.class.getName());

    @Override
    public String category() {
        return "audio";
    }

    @Override
    public void trace(byte[] rom, RomProfile profile) {
        List<IntrospectionResult> results = new ArrayList<>();

        traceMusicPtrTable(rom, results);
        traceMusicFlags(rom, results);
        traceSfxPtrTable(rom, results);
        tracePcmSampleTable(rom, results);
        tracePsgEnvelopeTable(rom, results);

        // Add all results to the profile
        for (IntrospectionResult result : results) {
            profile.putAddress(category(), result.key(), new AddressEntry(result.value(), result.confidence()));
            LOG.info(String.format("  %s = 0x%06X (%s) - %s",
                    result.key(), result.value(), result.confidence(), result.traceLog()));
        }
    }

    /**
     * Finds the music pointer table. In Sonic 2, the Z80 driver stores
     * music pointers as a table of 4-byte entries (2-byte Z80 pointer + 2-byte flags).
     *
     * <p>The table is identifiable by the pattern of its entries: each pointer
     * is a Z80 address (0x8000-0xFFFF range) followed by bank flags where the
     * high bit selects between bank0 (0x0F0000) and bank1 (0x0F8000).</p>
     *
     * <p>An alternative approach: look for the music flags table which has a
     * known structure (sequence of bytes where most are 0x00, 0x80, or 0x02)
     * near the pointer table.</p>
     */
    void traceMusicPtrTable(byte[] rom, List<IntrospectionResult> results) {
        // The Sonic 2 music pointer table is at 0x0EC810 in REV01.
        // Each entry is 4 bytes: 2 bytes Z80 pointer (LE) + 1 byte bank flag + 1 byte pad/flags
        // The bank flag is typically 0x00 (bank0) or 0x80 (bank1).
        //
        // Strategy: scan in the 0x0E0000-0x0F0000 range for a sequence of 4-byte entries
        // where bytes at offset+2 are consistently 0x00 or 0x80, and the Z80 pointers
        // are in the 0x8000-0xFFFF range.
        int entrySize = 4;
        int minEntries = 10; // Sonic 2 has many music tracks
        int searchStart = 0x0E0000;
        int searchEnd = RomReadUtil.safeSearchEnd(0x0F0000, rom.length, entrySize * minEntries);
        if (searchEnd < searchStart) return;

        for (int offset = searchStart; offset < searchEnd; offset += 2) {
            if (isValidMusicPtrTable(rom, offset, entrySize, minEntries)) {
                results.add(new IntrospectionResult(
                        "MUSIC_PTR_TABLE_ADDR", offset, "traced",
                        String.format("Found music pointer table at 0x%06X", offset)));
                return;
            }
        }

        LOG.fine("Music pointer table not found");
    }

    /**
     * Validates a potential music pointer table.
     * Each 4-byte entry: LE 16-bit Z80 pointer + bank flag byte + padding byte.
     * Z80 pointers should be in 0x8000-0xFFFF range.
     * Bank flag should be 0x00 or 0x80.
     */
    boolean isValidMusicPtrTable(byte[] rom, int offset, int entrySize, int minEntries) {
        if (offset + entrySize * minEntries > rom.length) {
            return false;
        }

        int validEntries = 0;
        for (int i = 0; i < minEntries; i++) {
            int entryOff = offset + i * entrySize;

            // Read little-endian Z80 pointer
            int z80Ptr = (rom[entryOff] & 0xFF) | ((rom[entryOff + 1] & 0xFF) << 8);

            // Z80 pointer must be in banked range
            if (z80Ptr < 0x8000 || z80Ptr > 0xFFFF) {
                return false;
            }

            // Bank flag byte (offset+2) should be 0x00 or 0x80
            int bankFlag = rom[entryOff + 2] & 0xFF;
            if (bankFlag != 0x00 && bankFlag != 0x80) {
                return false;
            }

            validEntries++;
        }

        return validEntries == minEntries;
    }

    /**
     * Finds the music flags table. This table has one byte per music ID,
     * containing flags like 0x00 (normal), 0x80 (bank1), 0x02 (alternate).
     *
     * <p>In Sonic 2, the flags table is near the pointer table, typically
     * at MUSIC_FLAGS_ADDR (0x0ECF36 in REV01).</p>
     */
    void traceMusicFlags(byte[] rom, List<IntrospectionResult> results) {
        // The music flags table is typically 30-50 bytes of flag values
        // Search near the end of the 0x0EC000 range
        int searchStart = 0x0EC000;
        int searchEnd = RomReadUtil.safeSearchEnd(0x0ED000, rom.length, 64);
        if (searchEnd < searchStart) return;

        for (int offset = searchStart; offset < searchEnd; offset++) {
            if (isValidMusicFlagsTable(rom, offset, 20)) {
                results.add(new IntrospectionResult(
                        "MUSIC_FLAGS_ADDR", offset, "traced",
                        String.format("Found music flags table at 0x%06X", offset)));
                return;
            }
        }

        LOG.fine("Music flags table not found");
    }

    /**
     * Validates a potential music flags table.
     * Entries should be 0x00, 0x02, or 0x80 (valid flag combinations).
     */
    boolean isValidMusicFlagsTable(byte[] rom, int offset, int minEntries) {
        if (offset + minEntries > rom.length) {
            return false;
        }

        int validCount = 0;
        int bank1Count = 0;
        for (int i = 0; i < minEntries; i++) {
            int val = rom[offset + i] & 0xFF;
            if (val == 0x00 || val == 0x02 || val == 0x80 || val == 0x82) {
                validCount++;
            }
            if (val == 0x80 || val == 0x82) {
                bank1Count++;
            }
        }

        // All entries should be valid flag values, and there should be a mix
        // of bank0 and bank1 songs
        return validCount == minEntries && bank1Count >= 3 && bank1Count < minEntries;
    }

    /**
     * Finds the SFX pointer table. Similar to music but in a different ROM bank.
     * Sonic 2 SFX are in the 0x0F8000+ range and the pointer table is near 0x0FEE91.
     *
     * <p>The SFX pointer table entries are 4-byte structures similar to music pointers.</p>
     */
    void traceSfxPtrTable(byte[] rom, List<IntrospectionResult> results) {
        int entrySize = 4;
        int minEntries = 20; // Sonic 2 has many SFX

        // SFX pointer table is typically in 0x0FE000-0x0FF000
        int searchStart = 0x0FE000;
        int searchEnd = RomReadUtil.safeSearchEnd(0x0FF000, rom.length, entrySize * minEntries);
        if (searchEnd < searchStart) return;

        for (int offset = searchStart; offset < searchEnd; offset += 2) {
            if (isValidSfxPtrTable(rom, offset, entrySize, minEntries)) {
                results.add(new IntrospectionResult(
                        "SFX_POINTER_TABLE_ADDR", offset, "traced",
                        String.format("Found SFX pointer table at 0x%06X", offset)));
                return;
            }
        }

        LOG.fine("SFX pointer table not found");
    }

    /**
     * Validates a potential SFX pointer table.
     * SFX pointers are Z80 addresses with bank info, all in the SFX bank.
     */
    boolean isValidSfxPtrTable(byte[] rom, int offset, int entrySize, int minEntries) {
        if (offset + entrySize * minEntries > rom.length) {
            return false;
        }

        int validEntries = 0;
        for (int i = 0; i < minEntries; i++) {
            int entryOff = offset + i * entrySize;

            // Read little-endian Z80 pointer
            int z80Ptr = (rom[entryOff] & 0xFF) | ((rom[entryOff + 1] & 0xFF) << 8);

            // SFX Z80 pointers should be in valid range
            if (z80Ptr < 0x8000 || z80Ptr > 0xFFFF) {
                return false;
            }

            validEntries++;
        }

        return validEntries == minEntries;
    }

    /**
     * Finds the PCM sample pointer table. In Sonic 2, this contains 7 entries
     * (kick, snare, timpani variants, etc.) with ROM pointers to PCM data.
     *
     * <p>Located near the music flags in the 0x0ECF00 range.</p>
     */
    void tracePcmSampleTable(byte[] rom, List<IntrospectionResult> results) {
        // PCM sample pointer table: 4-byte ROM pointers to PCM data
        // PCM data is in the 0x0E0000 range
        int searchStart = 0x0EC000;
        int searchEnd = RomReadUtil.safeSearchEnd(0x0ED000, rom.length, 32);
        if (searchEnd < searchStart) return;

        for (int offset = searchStart; offset < searchEnd; offset += 2) {
            if (isValidPcmSampleTable(rom, offset, 7)) {
                results.add(new IntrospectionResult(
                        "PCM_SAMPLE_PTR_TABLE_ADDR", offset, "traced",
                        String.format("Found PCM sample pointer table at 0x%06X", offset)));
                return;
            }
        }

        LOG.fine("PCM sample pointer table not found");
    }

    /**
     * Validates a potential PCM sample pointer table.
     * Entries should be 32-bit ROM pointers into the PCM data bank (0x0E0000).
     */
    boolean isValidPcmSampleTable(byte[] rom, int offset, int sampleCount) {
        if (offset + sampleCount * 4 > rom.length) {
            return false;
        }

        for (int i = 0; i < sampleCount; i++) {
            int ptr = readBigEndian32(rom, offset + i * 4);

            // PCM sample pointers should point into 0x0E0000-0x0F0000 range
            if (ptr < 0x0E0000 || ptr > 0x0F0000) {
                return false;
            }

            // Pointer must be within ROM
            if (ptr >= rom.length) {
                return false;
            }
        }

        return true;
    }

    /**
     * Finds the PSG envelope pointer table. This table contains 32-bit ROM
     * pointers to PSG volume envelope data.
     *
     * <p>In Sonic 2, the PSG envelope table is at 0x0F2E5C. The pointers
     * reference data in the 0x0F0000 range.</p>
     */
    void tracePsgEnvelopeTable(byte[] rom, List<IntrospectionResult> results) {
        int minEntries = 5;
        int searchStart = 0x0F2000;
        int searchEnd = RomReadUtil.safeSearchEnd(0x0F4000, rom.length, minEntries * 4);
        if (searchEnd < searchStart) return;

        for (int offset = searchStart; offset < searchEnd; offset += 2) {
            if (isValidPsgEnvelopeTable(rom, offset, minEntries)) {
                results.add(new IntrospectionResult(
                        "PSG_ENVELOPE_TABLE_ADDR", offset, "traced",
                        String.format("Found PSG envelope table at 0x%06X", offset)));
                return;
            }
        }

        LOG.fine("PSG envelope table not found");
    }

    /**
     * Validates a potential PSG envelope pointer table.
     * Entries should be 32-bit pointers into the PSG data area.
     */
    boolean isValidPsgEnvelopeTable(byte[] rom, int offset, int minEntries) {
        if (offset + minEntries * 4 > rom.length) {
            return false;
        }

        for (int i = 0; i < minEntries; i++) {
            int ptr = readBigEndian32(rom, offset + i * 4);

            // PSG envelope pointers should be in the 0x0F0000-0x100000 range
            if (ptr < 0x0F0000 || ptr > 0x100000) {
                return false;
            }

            if (ptr >= rom.length) {
                return false;
            }
        }

        return true;
    }

    // ---- Utility methods (delegate to shared RomReadUtil) ----

    static int readBigEndian32(byte[] rom, int offset) {
        return RomReadUtil.readBigEndian32(rom, offset);
    }

    static int readBigEndian16(byte[] rom, int offset) {
        return RomReadUtil.readBigEndian16(rom, offset);
    }
}
