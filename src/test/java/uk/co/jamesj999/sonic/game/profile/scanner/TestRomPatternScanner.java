package uk.co.jamesj999.sonic.game.profile.scanner;

import org.junit.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Tests for {@link RomPatternScanner} - ROM byte pattern scanning framework.
 */
public class TestRomPatternScanner {

    // ========================================
    // Helper methods
    // ========================================

    /**
     * Builds a small synthetic ROM with a known pattern at a specific offset.
     */
    private byte[] buildRom(int size, int patternOffset, byte[] pattern) {
        byte[] rom = new byte[size];
        // Fill with non-zero garbage to avoid accidental matches
        for (int i = 0; i < size; i++) {
            rom[i] = (byte) ((i * 7 + 3) & 0xFF);
        }
        // Insert the pattern at the specified offset
        System.arraycopy(pattern, 0, rom, patternOffset, pattern.length);
        return rom;
    }

    // ========================================
    // 1. Scan finds pattern and reads correct value
    // ========================================

    @Test
    public void testScanFindsPattern_ReadByte() {
        // Pattern: { 0xAA, 0xBB } at offset 0x100, read BYTE at pointerOffset=0 (reads 0xAA)
        byte[] signature = { (byte) 0xAA, (byte) 0xBB };
        byte[] rom = buildRom(512, 0x100, signature);

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "test", "BYTE_ADDR", signature, 0,
                ScanPattern.ReadMode.BYTE, null));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());

        assertTrue("should find pattern", results.containsKey("test.BYTE_ADDR"));
        ScanResult result = results.get("test.BYTE_ADDR");
        assertEquals("byte value at match start", 0xAA, result.value());
        assertEquals("found at offset 0x100", 0x100, result.foundAtOffset());
    }

    @Test
    public void testScanFindsPattern_ReadBigEndian16() {
        // Insert signature at offset 0x80, then read 16-bit value at pointerOffset=2
        byte[] signature = { (byte) 0xDE, (byte) 0xAD };
        byte[] rom = buildRom(512, 0x80, new byte[] {
                (byte) 0xDE, (byte) 0xAD,   // signature at 0x80
                (byte) 0x12, (byte) 0x34     // 16-bit value at 0x82
        });

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "TABLE_ADDR", signature, 2,
                ScanPattern.ReadMode.BIG_ENDIAN_16, null));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());

        assertTrue("should find pattern", results.containsKey("level.TABLE_ADDR"));
        assertEquals("16-bit big-endian value", 0x1234, results.get("level.TABLE_ADDR").value());
        assertEquals("found at offset 0x80", 0x80, results.get("level.TABLE_ADDR").foundAtOffset());
    }

    @Test
    public void testScanFindsPattern_ReadBigEndian32() {
        // Insert 4-byte signature at offset 0x200, read 32-bit value at pointerOffset=4
        byte[] signature = { (byte) 0x01, (byte) 0x02, (byte) 0x03, (byte) 0x04 };
        byte[] rom = buildRom(1024, 0x200, new byte[] {
                0x01, 0x02, 0x03, 0x04,         // signature at 0x200
                0x00, 0x0A, (byte) 0xBC, (byte) 0xDE  // 32-bit value at 0x204 = 0x000ABCDE
        });

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "BIG_TABLE_ADDR", signature, 4,
                ScanPattern.ReadMode.BIG_ENDIAN_32, null));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());

        assertTrue("should find pattern", results.containsKey("level.BIG_TABLE_ADDR"));
        assertEquals("32-bit big-endian value", 0x000ABCDE, results.get("level.BIG_TABLE_ADDR").value());
    }

    // ========================================
    // 2. Scan skips already-resolved keys
    // ========================================

    @Test
    public void testScanSkipsAlreadyResolvedKeys() {
        byte[] signature = { (byte) 0xFF, (byte) 0xEE };
        byte[] rom = buildRom(256, 0x10, signature);

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "ALREADY_KNOWN", signature, 0,
                ScanPattern.ReadMode.BYTE, null));

        // Mark the key as already resolved
        Map<String, ScanResult> results = scanner.scan(rom, Set.of("level.ALREADY_KNOWN"));

        assertFalse("should skip already-resolved key", results.containsKey("level.ALREADY_KNOWN"));
    }

    @Test
    public void testScanSkipsAlreadyResolved_OtherPatternsStillFound() {
        byte[] sig1 = { (byte) 0xAA };
        byte[] sig2 = { (byte) 0xBB };
        byte[] rom = new byte[256];
        rom[0x10] = (byte) 0xAA;
        rom[0x20] = (byte) 0xBB;

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "SKIP_ME", sig1, 0,
                ScanPattern.ReadMode.BYTE, null));
        scanner.registerPattern(new ScanPattern(
                "level", "FIND_ME", sig2, 0,
                ScanPattern.ReadMode.BYTE, null));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of("level.SKIP_ME"));

        assertFalse("skipped key not in results", results.containsKey("level.SKIP_ME"));
        assertTrue("non-skipped key found", results.containsKey("level.FIND_ME"));
    }

    // ========================================
    // 3. Pattern not found returns empty
    // ========================================

    @Test
    public void testPatternNotFound_ReturnsEmpty() {
        byte[] signature = { (byte) 0xCC, (byte) 0xDD, (byte) 0xEE };
        byte[] rom = new byte[256]; // all zeros, no match

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "audio", "MISSING", signature, 0,
                ScanPattern.ReadMode.BYTE, null));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());

        assertFalse("pattern not found, not in results", results.containsKey("audio.MISSING"));
        assertTrue("results map is empty", results.isEmpty());
    }

    @Test
    public void testEmptyRom_ReturnsEmpty() {
        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "ADDR", new byte[] { 0x01 }, 0,
                ScanPattern.ReadMode.BYTE, null));

        Map<String, ScanResult> results = scanner.scan(new byte[0], Set.of());
        assertTrue("empty ROM returns empty results", results.isEmpty());
    }

    @Test
    public void testNullRom_ReturnsEmpty() {
        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "ADDR", new byte[] { 0x01 }, 0,
                ScanPattern.ReadMode.BYTE, null));

        Map<String, ScanResult> results = scanner.scan(null, Set.of());
        assertTrue("null ROM returns empty results", results.isEmpty());
    }

    @Test
    public void testNoRegisteredPatterns_ReturnsEmpty() {
        RomPatternScanner scanner = new RomPatternScanner();
        byte[] rom = new byte[256];

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());
        assertTrue("no patterns returns empty results", results.isEmpty());
    }

    // ========================================
    // 4. Validator rejection filters false positives
    // ========================================

    @Test
    public void testValidatorRejectsValue() {
        // Pattern found but value fails validation
        byte[] signature = { (byte) 0x42 };
        byte[] rom = new byte[256];
        rom[0x10] = 0x42;  // signature
        rom[0x11] = 0x05;  // value to read

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "VALIDATED_ADDR", signature, 1,
                ScanPattern.ReadMode.BYTE,
                value -> value > 0x10  // validator: value must be > 0x10, but actual is 0x05
        ));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());

        assertFalse("validator rejected value, not in results",
                results.containsKey("level.VALIDATED_ADDR"));
    }

    @Test
    public void testValidatorAcceptsValue() {
        byte[] signature = { (byte) 0x42 };
        byte[] rom = new byte[256];
        rom[0x10] = 0x42;  // signature
        rom[0x11] = 0x20;  // value to read

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "VALIDATED_ADDR", signature, 1,
                ScanPattern.ReadMode.BYTE,
                value -> value > 0x10  // validator: value must be > 0x10, actual is 0x20
        ));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());

        assertTrue("validator accepted value", results.containsKey("level.VALIDATED_ADDR"));
        assertEquals("read value matches", 0x20, results.get("level.VALIDATED_ADDR").value());
    }

    @Test
    public void testNullValidator_AlwaysAccepts() {
        byte[] signature = { (byte) 0x55 };
        byte[] rom = new byte[256];
        rom[0x30] = 0x55;

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "NO_VALIDATOR", signature, 0,
                ScanPattern.ReadMode.BYTE, null));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());

        assertTrue("null validator accepts any value",
                results.containsKey("level.NO_VALIDATOR"));
    }

    // ========================================
    // 5. ScanPattern key() method
    // ========================================

    @Test
    public void testScanPatternKey() {
        ScanPattern pattern = new ScanPattern(
                "audio", "MUSIC_TABLE", new byte[] {}, 0,
                ScanPattern.ReadMode.BYTE, null);
        assertEquals("audio.MUSIC_TABLE", pattern.key());
    }

    // ========================================
    // 6. Internal findPattern method
    // ========================================

    @Test
    public void testFindPattern_FindsAtStart() {
        RomPatternScanner scanner = new RomPatternScanner();
        byte[] rom = { 0x01, 0x02, 0x03, 0x04, 0x05 };
        byte[] pattern = { 0x01, 0x02 };

        assertEquals("pattern at start", 0, scanner.findPattern(rom, pattern, 0, rom.length));
    }

    @Test
    public void testFindPattern_FindsAtEnd() {
        RomPatternScanner scanner = new RomPatternScanner();
        byte[] rom = { 0x01, 0x02, 0x03, 0x04, 0x05 };
        byte[] pattern = { 0x04, 0x05 };

        assertEquals("pattern at end", 3, scanner.findPattern(rom, pattern, 0, rom.length));
    }

    @Test
    public void testFindPattern_NotFound() {
        RomPatternScanner scanner = new RomPatternScanner();
        byte[] rom = { 0x01, 0x02, 0x03 };
        byte[] pattern = { (byte) 0xFF, (byte) 0xFE };

        assertEquals("not found", -1, scanner.findPattern(rom, pattern, 0, rom.length));
    }

    @Test
    public void testFindPattern_EmptyPattern() {
        RomPatternScanner scanner = new RomPatternScanner();
        byte[] rom = { 0x01, 0x02, 0x03 };

        assertEquals("empty pattern returns -1", -1, scanner.findPattern(rom, new byte[0], 0, rom.length));
    }

    @Test
    public void testFindPattern_NullPattern() {
        RomPatternScanner scanner = new RomPatternScanner();
        byte[] rom = { 0x01, 0x02, 0x03 };

        assertEquals("null pattern returns -1", -1, scanner.findPattern(rom, null, 0, rom.length));
    }

    // ========================================
    // 7. Internal readValue method
    // ========================================

    @Test
    public void testReadValue_Byte() {
        RomPatternScanner scanner = new RomPatternScanner();
        byte[] rom = { (byte) 0xFF, (byte) 0x42 };

        assertEquals("unsigned byte 0xFF", 0xFF, scanner.readValue(rom, 0, ScanPattern.ReadMode.BYTE));
        assertEquals("unsigned byte 0x42", 0x42, scanner.readValue(rom, 1, ScanPattern.ReadMode.BYTE));
    }

    @Test
    public void testReadValue_BigEndian16() {
        RomPatternScanner scanner = new RomPatternScanner();
        byte[] rom = { (byte) 0xAB, (byte) 0xCD };

        assertEquals("big-endian 16-bit", 0xABCD, scanner.readValue(rom, 0, ScanPattern.ReadMode.BIG_ENDIAN_16));
    }

    @Test
    public void testReadValue_BigEndian32() {
        RomPatternScanner scanner = new RomPatternScanner();
        byte[] rom = { (byte) 0x00, (byte) 0x04, (byte) 0x25, (byte) 0x94 };

        assertEquals("big-endian 32-bit", 0x00042594,
                scanner.readValue(rom, 0, ScanPattern.ReadMode.BIG_ENDIAN_32));
    }

    // ========================================
    // 8. Multiple patterns in one scan
    // ========================================

    @Test
    public void testMultiplePatterns_AllFound() {
        byte[] rom = new byte[512];
        // Pattern 1 at offset 0x10
        rom[0x10] = (byte) 0xAA;
        rom[0x11] = (byte) 0xBB;
        // Pattern 2 at offset 0x80
        rom[0x80] = (byte) 0xCC;
        rom[0x81] = (byte) 0xDD;

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "FIRST", new byte[] { (byte) 0xAA, (byte) 0xBB }, 0,
                ScanPattern.ReadMode.BIG_ENDIAN_16, null));
        scanner.registerPattern(new ScanPattern(
                "audio", "SECOND", new byte[] { (byte) 0xCC, (byte) 0xDD }, 0,
                ScanPattern.ReadMode.BIG_ENDIAN_16, null));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());

        assertEquals("two results", 2, results.size());
        assertTrue(results.containsKey("level.FIRST"));
        assertTrue(results.containsKey("audio.SECOND"));
        assertEquals(0xAABB, results.get("level.FIRST").value());
        assertEquals(0xCCDD, results.get("audio.SECOND").value());
    }

    // ========================================
    // 9. Pointer offset reads from correct location
    // ========================================

    @Test
    public void testPointerOffset_ReadsFromCorrectLocation() {
        // Signature at 0x50, pointer at signature+8
        byte[] rom = new byte[256];
        rom[0x50] = (byte) 0xDE;
        rom[0x51] = (byte) 0xAD;
        // Value at offset 0x58 (pointerOffset=8)
        rom[0x58] = (byte) 0x00;
        rom[0x59] = (byte) 0x07;
        rom[0x5A] = (byte) 0x19;
        rom[0x5B] = (byte) 0xA8;

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "audio", "PTR_TABLE", new byte[] { (byte) 0xDE, (byte) 0xAD }, 8,
                ScanPattern.ReadMode.BIG_ENDIAN_32, null));

        Map<String, ScanResult> results = scanner.scan(rom, Set.of());

        assertTrue(results.containsKey("audio.PTR_TABLE"));
        assertEquals("value at signature+8", 0x000719A8, results.get("audio.PTR_TABLE").value());
        assertEquals("match at 0x50", 0x50, results.get("audio.PTR_TABLE").foundAtOffset());
    }

    // ========================================
    // 10. Null alreadyResolved treated as empty
    // ========================================

    @Test
    public void testNullAlreadyResolved_TreatedAsEmpty() {
        byte[] signature = { (byte) 0x77 };
        byte[] rom = new byte[64];
        rom[0x10] = (byte) 0x77;

        RomPatternScanner scanner = new RomPatternScanner();
        scanner.registerPattern(new ScanPattern(
                "level", "ADDR", signature, 0,
                ScanPattern.ReadMode.BYTE, null));

        // Pass null for alreadyResolved
        Map<String, ScanResult> results = scanner.scan(rom, null);

        assertTrue("null alreadyResolved still finds patterns",
                results.containsKey("level.ADDR"));
    }
}
