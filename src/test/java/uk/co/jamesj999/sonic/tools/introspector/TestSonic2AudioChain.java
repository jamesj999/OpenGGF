package uk.co.jamesj999.sonic.tools.introspector;

import org.junit.Test;
import uk.co.jamesj999.sonic.game.profile.RomProfile;

import static org.junit.Assert.*;

/**
 * Tests for the Sonic 2 audio introspection chain.
 * Tests validation logic using synthetic ROM data.
 */
public class TestSonic2AudioChain {

    private final Sonic2AudioChain chain = new Sonic2AudioChain();

    // ========================================
    // Category
    // ========================================

    @Test
    public void testCategory() {
        assertEquals("audio", chain.category());
    }

    // ========================================
    // Big-endian read utilities
    // ========================================

    @Test
    public void testReadBigEndian32() {
        byte[] data = {0x00, (byte) 0x0E, (byte) 0xC8, 0x10};
        assertEquals(0x000EC810, Sonic2AudioChain.readBigEndian32(data, 0));
    }

    @Test
    public void testReadBigEndian16() {
        byte[] data = {(byte) 0x80, 0x00};
        assertEquals(0x8000, Sonic2AudioChain.readBigEndian16(data, 0));
    }

    // ========================================
    // Music pointer table validation
    // ========================================

    @Test
    public void testIsValidMusicPtrTable_ValidSynthetic() {
        // Create a table with 10 entries of 4 bytes each
        // Each entry: LE Z80 pointer (0x8000-0xFFFF) + bank flag (0x00 or 0x80) + padding
        byte[] rom = new byte[0x0F0000];
        int offset = 0x0EC810;

        for (int i = 0; i < 10; i++) {
            int entryOff = offset + i * 4;
            int z80Ptr = 0x8100 + i * 0x200;

            // Little-endian Z80 pointer
            rom[entryOff] = (byte) (z80Ptr & 0xFF);
            rom[entryOff + 1] = (byte) ((z80Ptr >> 8) & 0xFF);

            // Bank flag: alternate between bank0 and bank1
            rom[entryOff + 2] = (byte) ((i % 3 == 0) ? 0x80 : 0x00);
            rom[entryOff + 3] = 0x00;
        }

        assertTrue(chain.isValidMusicPtrTable(rom, offset, 4, 10));
    }

    @Test
    public void testIsValidMusicPtrTable_InvalidZ80Pointer() {
        byte[] rom = new byte[0x0F0000];
        int offset = 0x0EC810;

        // First entry has Z80 pointer below 0x8000
        rom[offset] = 0x00;
        rom[offset + 1] = 0x10; // 0x1000 < 0x8000

        assertFalse(chain.isValidMusicPtrTable(rom, offset, 4, 10));
    }

    @Test
    public void testIsValidMusicPtrTable_InvalidBankFlag() {
        byte[] rom = new byte[0x0F0000];
        int offset = 0x0EC810;

        for (int i = 0; i < 10; i++) {
            int entryOff = offset + i * 4;
            rom[entryOff] = (byte) 0x00;
            rom[entryOff + 1] = (byte) 0x80;
            rom[entryOff + 2] = (byte) 0x40; // Invalid bank flag
        }

        assertFalse(chain.isValidMusicPtrTable(rom, offset, 4, 10));
    }

    // ========================================
    // Music flags table validation
    // ========================================

    @Test
    public void testIsValidMusicFlagsTable_ValidSynthetic() {
        byte[] rom = new byte[0x0ED000];
        int offset = 0x0ECF36;

        // Create a valid flags table: mix of 0x00 and 0x80
        byte[] flags = {
                0x00, 0x00, 0x00, 0x00, 0x00, (byte) 0x80, (byte) 0x80, 0x00,
                0x00, (byte) 0x80, 0x00, 0x00, 0x00, 0x00, (byte) 0x80, (byte) 0x80,
                0x00, 0x00, (byte) 0x80, 0x00
        };
        System.arraycopy(flags, 0, rom, offset, flags.length);

        assertTrue(chain.isValidMusicFlagsTable(rom, offset, 20));
    }

    @Test
    public void testIsValidMusicFlagsTable_InvalidByte() {
        byte[] rom = new byte[0x0ED000];
        int offset = 0x0ECF36;

        // All entries are 0x00 except one that's invalid
        for (int i = 0; i < 20; i++) {
            rom[offset + i] = 0x00;
        }
        // 0x41 is not a valid flag value
        rom[offset + 5] = 0x41;

        assertFalse(chain.isValidMusicFlagsTable(rom, offset, 20));
    }

    @Test
    public void testIsValidMusicFlagsTable_AllBank0() {
        byte[] rom = new byte[0x0ED000];
        int offset = 0x0ECF36;

        // All 0x00 - needs at least 3 bank1 entries
        for (int i = 0; i < 20; i++) {
            rom[offset + i] = 0x00;
        }

        assertFalse(chain.isValidMusicFlagsTable(rom, offset, 20));
    }

    // ========================================
    // SFX pointer table validation
    // ========================================

    @Test
    public void testIsValidSfxPtrTable_ValidSynthetic() {
        byte[] rom = new byte[0x100000];
        int offset = 0x0FEE91;

        for (int i = 0; i < 20; i++) {
            int entryOff = offset + i * 4;
            int z80Ptr = 0x8200 + i * 0x100;

            rom[entryOff] = (byte) (z80Ptr & 0xFF);
            rom[entryOff + 1] = (byte) ((z80Ptr >> 8) & 0xFF);
            rom[entryOff + 2] = 0x00;
            rom[entryOff + 3] = 0x00;
        }

        assertTrue(chain.isValidSfxPtrTable(rom, offset, 4, 20));
    }

    @Test
    public void testIsValidSfxPtrTable_InvalidPointer() {
        byte[] rom = new byte[0x100000];
        int offset = 0x0FEE91;

        // First entry has invalid Z80 pointer
        rom[offset] = 0x00;
        rom[offset + 1] = 0x10; // 0x1000 < 0x8000

        assertFalse(chain.isValidSfxPtrTable(rom, offset, 4, 20));
    }

    // ========================================
    // PCM sample table validation
    // ========================================

    @Test
    public void testIsValidPcmSampleTable_ValidSynthetic() {
        byte[] rom = new byte[0x100000];
        int offset = 0x0ECF7C;

        for (int i = 0; i < 7; i++) {
            // Write BE 32-bit pointers into 0x0E0000 range
            int ptr = 0x0E1000 + i * 0x2000;
            writeBigEndian32(rom, offset + i * 4, ptr);
        }

        assertTrue(chain.isValidPcmSampleTable(rom, offset, 7));
    }

    @Test
    public void testIsValidPcmSampleTable_OutOfRange() {
        byte[] rom = new byte[0x100000];
        int offset = 0x0ECF7C;

        // Pointer outside PCM bank range
        writeBigEndian32(rom, offset, 0x0F1000);

        assertFalse(chain.isValidPcmSampleTable(rom, offset, 7));
    }

    // ========================================
    // PSG envelope table validation
    // ========================================

    @Test
    public void testIsValidPsgEnvelopeTable_ValidSynthetic() {
        byte[] rom = new byte[0x100000];
        int offset = 0x0F2E5C;

        for (int i = 0; i < 5; i++) {
            int ptr = 0x0F3000 + i * 0x100;
            writeBigEndian32(rom, offset + i * 4, ptr);
        }

        assertTrue(chain.isValidPsgEnvelopeTable(rom, offset, 5));
    }

    @Test
    public void testIsValidPsgEnvelopeTable_OutOfRange() {
        byte[] rom = new byte[0x100000];
        int offset = 0x0F2E5C;

        // Pointer below PSG range
        writeBigEndian32(rom, offset, 0x0E0000);

        assertFalse(chain.isValidPsgEnvelopeTable(rom, offset, 5));
    }

    // ========================================
    // Full trace on empty ROM
    // ========================================

    @Test
    public void testTrace_EmptyRom_NoResults() {
        byte[] rom = new byte[0x100000];
        RomProfile profile = new RomProfile();
        chain.trace(rom, profile);

        // Empty ROM should not produce false positives for audio structures
        // (all-zero data has Z80 pointer 0x0000, which is < 0x8000, failing validation)
        assertEquals(0, profile.addressCount());
    }

    // ========================================
    // Helpers
    // ========================================

    private void writeBigEndian32(byte[] rom, int offset, int value) {
        rom[offset] = (byte) ((value >> 24) & 0xFF);
        rom[offset + 1] = (byte) ((value >> 16) & 0xFF);
        rom[offset + 2] = (byte) ((value >> 8) & 0xFF);
        rom[offset + 3] = (byte) (value & 0xFF);
    }
}
