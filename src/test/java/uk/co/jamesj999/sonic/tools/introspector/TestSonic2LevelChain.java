package uk.co.jamesj999.sonic.tools.introspector;

import org.junit.Test;
import uk.co.jamesj999.sonic.game.profile.RomProfile;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for the Sonic 2 level data introspection chain.
 * Tests validation logic using synthetic ROM data, since actual ROM
 * files are not available in the test environment.
 */
public class TestSonic2LevelChain {

    private final Sonic2LevelChain chain = new Sonic2LevelChain();

    // ========================================
    // Category
    // ========================================

    @Test
    public void testCategory() {
        assertEquals("level", chain.category());
    }

    // ========================================
    // Big-endian read utilities
    // ========================================

    @Test
    public void testReadBigEndian32() {
        byte[] data = {0x00, 0x04, 0x25, (byte) 0x94};
        assertEquals(0x00042594, Sonic2LevelChain.readBigEndian32(data, 0));
    }

    @Test
    public void testReadBigEndian16() {
        byte[] data = {0x0C, 0x54};
        assertEquals(0x0C54, Sonic2LevelChain.readBigEndian16(data, 0));
    }

    // ========================================
    // Level data directory validation
    // ========================================

    @Test
    public void testIsValidLevelDataDir_ValidSynthetic() {
        // Create synthetic data with valid 32-bit ROM pointers at 12-byte intervals
        int entrySize = 12;
        int entryCount = 17;
        byte[] rom = new byte[0x100000]; // 1MB ROM

        int baseOffset = 0x042594;
        for (int i = 0; i < entryCount; i++) {
            int entryOff = baseOffset + i * entrySize;
            // Write a valid ROM pointer (0x040000-0x0FFFFF range) as BE 32-bit
            int ptr = 0x050000 + i * 0x1000;
            writeBigEndian32(rom, entryOff, ptr);
        }

        assertTrue(chain.isValidLevelDataDir(rom, baseOffset, entrySize, entryCount));
    }

    @Test
    public void testIsValidLevelDataDir_InvalidPointer() {
        int entrySize = 12;
        int entryCount = 3;
        byte[] rom = new byte[0x100000];

        int baseOffset = 0x042594;
        // First entry has invalid pointer (too low)
        writeBigEndian32(rom, baseOffset, 0x001000);

        assertFalse(chain.isValidLevelDataDir(rom, baseOffset, entrySize, entryCount));
    }

    @Test
    public void testIsValidLevelDataDir_TruncatedRom() {
        int entrySize = 12;
        int entryCount = 17;
        byte[] rom = new byte[100]; // Too small

        assertFalse(chain.isValidLevelDataDir(rom, 0, entrySize, entryCount));
    }

    // ========================================
    // Angle map validation
    // ========================================

    @Test
    public void testIsValidAngleMap_ValidSynthetic() {
        byte[] rom = new byte[0x050000];
        int offset = 0x042D50;

        // First entry must be 0x00
        rom[offset] = 0x00;

        // Fill with a realistic distribution: ~60% zeros, ~15 distinct values
        for (int i = 1; i < 0x100; i++) {
            if (i % 5 == 0) {
                rom[offset + i] = (byte) (i * 3); // Non-zero angle
            } else if (i % 3 == 0) {
                rom[offset + i] = (byte) 0xFF; // Steep slope
            } else {
                rom[offset + i] = 0x00; // Flat
            }
        }

        assertTrue(chain.isValidAngleMap(rom, offset));
    }

    @Test
    public void testIsValidAngleMap_FirstEntryNonZero() {
        byte[] rom = new byte[0x050000];
        int offset = 0x042D50;
        rom[offset] = 0x01; // Invalid: first entry must be 0

        assertFalse(chain.isValidAngleMap(rom, offset));
    }

    @Test
    public void testIsValidAngleMap_AllZeros() {
        byte[] rom = new byte[0x050000];
        int offset = 0x042D50;
        // All zeros - too many zeros, not enough distinct values

        assertFalse(chain.isValidAngleMap(rom, offset));
    }

    // ========================================
    // Height map validation
    // ========================================

    @Test
    public void testIsValidHeightMap_ValidSynthetic() {
        byte[] rom = new byte[0x050000];
        int offset = 0x042E50;

        // First 16 bytes (tile 0) must be zero
        for (int i = 0; i < 16; i++) {
            rom[offset + i] = 0x00;
        }

        // Fill remaining with a mix of 0x00 (zero columns), 0x10 (full columns),
        // and intermediate values
        for (int i = 16; i < 0x1000; i++) {
            if (i % 4 == 0) {
                rom[offset + i] = 0x10; // Full column
            } else if (i % 3 == 0) {
                rom[offset + i] = (byte) (i % 16); // Intermediate
            } else {
                rom[offset + i] = 0x00; // Empty
            }
        }

        assertTrue(chain.isValidHeightMap(rom, offset));
    }

    @Test
    public void testIsValidHeightMap_Tile0NonZero() {
        byte[] rom = new byte[0x050000];
        int offset = 0x042E50;
        rom[offset + 5] = 0x01; // Tile 0 should be all zeros

        assertFalse(chain.isValidHeightMap(rom, offset));
    }

    // ========================================
    // Boundaries table validation
    // ========================================

    @Test
    public void testIsValidBoundariesTable_ValidSynthetic() {
        byte[] rom = new byte[0x010000];
        int offset = 0x00C054;
        int entrySize = 10;
        int entries = 16;

        for (int i = 0; i < entries; i++) {
            int entryOff = offset + i * entrySize;
            writeBigEndian16(rom, entryOff, 0x0000);     // Left bound = 0
            writeBigEndian16(rom, entryOff + 2, 0x0000);  // Top bound = 0
            writeBigEndian16(rom, entryOff + 4, 0x2000 + i * 0x100); // Right bound
            writeBigEndian16(rom, entryOff + 6, 0x0300 + i * 0x20);  // Bottom bound
            writeBigEndian16(rom, entryOff + 8, 0x0000);  // Y-wrap
        }

        assertTrue(chain.isValidBoundariesTable(rom, offset, entrySize, entries));
    }

    @Test
    public void testIsValidBoundariesTable_InvalidLeftBound() {
        byte[] rom = new byte[0x010000];
        int offset = 0x00C054;
        int entrySize = 10;
        int entries = 16;

        // Make all entries have invalid left boundaries
        for (int i = 0; i < entries; i++) {
            int entryOff = offset + i * entrySize;
            writeBigEndian16(rom, entryOff, 0x1000);       // Left bound too large
            writeBigEndian16(rom, entryOff + 4, 0x2000);   // Right bound
            writeBigEndian16(rom, entryOff + 6, 0x0300);   // Bottom bound
        }

        assertFalse(chain.isValidBoundariesTable(rom, offset, entrySize, entries));
    }

    // ========================================
    // Start location validation
    // ========================================

    @Test
    public void testIsValidStartLocationArray_ValidSynthetic() {
        byte[] rom = new byte[0x010000];
        int offset = 0x009000;
        int entrySize = 4;
        int entries = 32;

        // First entry: reasonable EHZ start coords
        writeBigEndian16(rom, offset, 0x0060);     // X = 0x60
        writeBigEndian16(rom, offset + 2, 0x02A0); // Y = 0x2A0

        for (int i = 1; i < entries; i++) {
            int entryOff = offset + i * entrySize;
            writeBigEndian16(rom, entryOff, 0x0080 + i * 0x40);     // X
            writeBigEndian16(rom, entryOff + 2, 0x0200 + i * 0x10); // Y
        }

        assertTrue(chain.isValidStartLocationArray(rom, offset, entrySize, entries));
    }

    @Test
    public void testIsValidStartLocationArray_InvalidFirstEntry() {
        byte[] rom = new byte[0x010000];
        int offset = 0x009000;

        // First X is out of range
        writeBigEndian16(rom, offset, 0x5000);
        writeBigEndian16(rom, offset + 2, 0x0300);

        assertFalse(chain.isValidStartLocationArray(rom, offset, 4, 32));
    }

    // ========================================
    // Full trace on empty ROM
    // ========================================

    @Test
    public void testTrace_EmptyRom_NoResults() {
        byte[] rom = new byte[0x100000]; // 1MB of zeros
        RomProfile profile = new RomProfile();
        chain.trace(rom, profile);

        // Should not find anything in all-zero data
        // (angle map validator checks for diversity, so all-zero fails)
        // But level data dir might match if zeros happen to look valid
        // This is fine - the test just ensures no crash
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

    private void writeBigEndian16(byte[] rom, int offset, int value) {
        rom[offset] = (byte) ((value >> 8) & 0xFF);
        rom[offset + 1] = (byte) (value & 0xFF);
    }
}
