package uk.co.jamesj999.sonic.game.profile.scanner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.profile.AddressEntry;
import uk.co.jamesj999.sonic.game.profile.ProfileMetadata;
import uk.co.jamesj999.sonic.game.profile.ResolutionReport;
import uk.co.jamesj999.sonic.game.profile.RomAddressResolver;
import uk.co.jamesj999.sonic.game.profile.RomProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Integration tests verifying that the {@link RomPatternScanner} correctly
 * fills gaps in the {@link RomAddressResolver} and does not override
 * profile-sourced values.
 */
public class TestScannerIntegration {

    @Before
    public void setUp() {
        RomAddressResolver.resetInstance();
    }

    @After
    public void tearDown() {
        RomAddressResolver.resetInstance();
    }

    // ========================================
    // Helper methods
    // ========================================

    /**
     * Builds a small synthetic ROM with known patterns embedded at specific offsets.
     */
    private byte[] buildTestRom() {
        byte[] rom = new byte[1024];
        // Pattern "SCAN_SIG_1" at offset 0x100: { 0xDE, 0xAD, 0xBE, 0xEF }
        // followed by a 32-bit address value 0x00042594 at offset 0x104
        rom[0x100] = (byte) 0xDE;
        rom[0x101] = (byte) 0xAD;
        rom[0x102] = (byte) 0xBE;
        rom[0x103] = (byte) 0xEF;
        rom[0x104] = 0x00;
        rom[0x105] = 0x04;
        rom[0x106] = 0x25;
        rom[0x107] = (byte) 0x94;

        // Pattern "SCAN_SIG_2" at offset 0x200: { 0xCA, 0xFE }
        // followed by a 16-bit value 0x1234 at offset 0x202
        rom[0x200] = (byte) 0xCA;
        rom[0x201] = (byte) 0xFE;
        rom[0x202] = 0x12;
        rom[0x203] = 0x34;

        return rom;
    }

    /**
     * Creates a scanner with two patterns matching the test ROM layout.
     */
    private RomPatternScanner createTestScanner() {
        RomPatternScanner scanner = new RomPatternScanner();

        // Pattern 1: finds 32-bit address after DEADBEEF signature
        scanner.registerPattern(new ScanPattern(
                "level", "SCANNED_TABLE_ADDR",
                new byte[] { (byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF },
                4, ScanPattern.ReadMode.BIG_ENDIAN_32, null));

        // Pattern 2: finds 16-bit value after CAFE signature
        scanner.registerPattern(new ScanPattern(
                "audio", "SCANNED_MUSIC_OFFSET",
                new byte[] { (byte) 0xCA, (byte) 0xFE },
                2, ScanPattern.ReadMode.BIG_ENDIAN_16, null));

        return scanner;
    }

    // ========================================
    // 1. Scanned addresses fill gaps in resolver
    // ========================================

    @Test
    public void testScannedAddressesFillGaps() {
        byte[] rom = buildTestRom();
        RomPatternScanner scanner = createTestScanner();

        // Initialize resolver with only defaults (no profile)
        Map<String, Integer> defaults = Map.of(
                "level.EXISTING_ADDR", 0x1000
        );
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        // Run scanner and feed results
        Set<String> alreadyResolved = resolver.getResolvedKeys();
        Map<String, ScanResult> scanResults = scanner.scan(rom, alreadyResolved);

        Map<String, Integer> scannedMap = new LinkedHashMap<>();
        for (Map.Entry<String, ScanResult> entry : scanResults.entrySet()) {
            scannedMap.put(entry.getKey(), entry.getValue().value());
        }
        resolver.addScannedAddresses(scannedMap);

        // Verify scanned values are accessible
        assertEquals("scanned level address filled gap",
                0x00042594, resolver.getLevelAddress("SCANNED_TABLE_ADDR"));
        assertEquals("scanned audio address filled gap",
                0x1234, resolver.getAudioAddress("SCANNED_MUSIC_OFFSET"));

        // Existing default is still there
        assertEquals("existing default preserved",
                0x1000, resolver.getLevelAddress("EXISTING_ADDR"));
    }

    @Test
    public void testScannedAddressesFillGaps_ReportCounts() {
        byte[] rom = buildTestRom();
        RomPatternScanner scanner = createTestScanner();

        Map<String, Integer> defaults = Map.of("level.DEFAULT_ONLY", 0x5000);
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        Map<String, ScanResult> scanResults = scanner.scan(rom, resolver.getResolvedKeys());
        Map<String, Integer> scannedMap = new LinkedHashMap<>();
        for (Map.Entry<String, ScanResult> entry : scanResults.entrySet()) {
            scannedMap.put(entry.getKey(), entry.getValue().value());
        }
        resolver.addScannedAddresses(scannedMap);

        ResolutionReport report = resolver.getReport();
        assertEquals("0 from profile", 0, report.fromProfile());
        assertEquals("2 from scanner", 2, report.fromScanned());
        assertEquals("1 from defaults", 1, report.fromDefaults());
        assertEquals("3 total resolved", 3, report.totalResolved());
    }

    // ========================================
    // 2. Scanned addresses don't override profile values
    // ========================================

    @Test
    public void testScannedAddressesDoNotOverrideProfile() {
        byte[] rom = buildTestRom();

        // Set up profile with a value for the same key the scanner will find
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic2", "ABCD", "test", false));
        profile.putAddress("level", "SCANNED_TABLE_ADDR", new AddressEntry(0xBEEF, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, Map.of());

        // Scanner should skip "level.SCANNED_TABLE_ADDR" since it's already resolved
        RomPatternScanner scanner = createTestScanner();
        Set<String> alreadyResolved = resolver.getResolvedKeys();

        // The skip-set should contain the profile key
        assertTrue("resolved keys includes profile key",
                alreadyResolved.contains("level.SCANNED_TABLE_ADDR"));

        Map<String, ScanResult> scanResults = scanner.scan(rom, alreadyResolved);

        // The scanner should NOT have found "level.SCANNED_TABLE_ADDR" because it was skipped
        assertFalse("scanner skipped already-resolved key",
                scanResults.containsKey("level.SCANNED_TABLE_ADDR"));

        // Even if we feed all scan results, the profile value must be preserved
        Map<String, Integer> scannedMap = new LinkedHashMap<>();
        for (Map.Entry<String, ScanResult> entry : scanResults.entrySet()) {
            scannedMap.put(entry.getKey(), entry.getValue().value());
        }
        resolver.addScannedAddresses(scannedMap);

        // Profile value should NOT be overridden
        assertEquals("profile value preserved",
                0xBEEF, resolver.getLevelAddress("SCANNED_TABLE_ADDR"));
    }

    @Test
    public void testScannedAddressesDoNotOverrideProfile_DoubleProtection() {
        byte[] rom = buildTestRom();

        // Profile defines the same key the scanner would find
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic2", "ABCD", "test", false));
        profile.putAddress("level", "SCANNED_TABLE_ADDR", new AddressEntry(0x9999, "verified"));

        Map<String, Integer> defaults = Map.of("level.SCANNED_TABLE_ADDR", 0x1111);

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, defaults);

        // Force-feed a scanned value for the same key (bypassing the skip-set
        // to test the addScannedAddresses layer of protection)
        resolver.addScannedAddresses(Map.of("level.SCANNED_TABLE_ADDR", 0x42));

        // Profile value still wins even if scanned value is directly added
        assertEquals("profile value preserved after direct scanned add",
                0x9999, resolver.getLevelAddress("SCANNED_TABLE_ADDR"));
    }

    // ========================================
    // 3. getResolvedKeys includes all layers
    // ========================================

    @Test
    public void testGetResolvedKeysIncludesProfileAndDefaults() {
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic2", "ABCD", "test", false));
        profile.putAddress("level", "PROFILE_ADDR", new AddressEntry(0x100, "verified"));

        Map<String, Integer> defaults = Map.of("audio.DEFAULT_ADDR", 0x200);

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, defaults);

        Set<String> keys = resolver.getResolvedKeys();
        assertTrue("contains profile key", keys.contains("level.PROFILE_ADDR"));
        assertTrue("contains default key", keys.contains("audio.DEFAULT_ADDR"));
        assertEquals("exactly 2 keys", 2, keys.size());
    }

    @Test
    public void testGetResolvedKeysIsUnmodifiable() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of("level.A", 1));

        Set<String> keys = resolver.getResolvedKeys();
        try {
            keys.add("should.FAIL");
            fail("should have thrown UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected behavior
        }
    }

    // ========================================
    // 4. Empty scanner produces no changes
    // ========================================

    @Test
    public void testEmptyScanner_NoChangesToResolver() {
        Map<String, Integer> defaults = Map.of("level.ONLY_DEFAULT", 0x4000);
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        // Scanner with no patterns
        RomPatternScanner scanner = new RomPatternScanner();
        Map<String, ScanResult> scanResults = scanner.scan(new byte[256], resolver.getResolvedKeys());

        assertTrue("no results from empty scanner", scanResults.isEmpty());

        // Resolver unchanged
        assertEquals(0x4000, resolver.getLevelAddress("ONLY_DEFAULT"));
        ResolutionReport report = resolver.getReport();
        assertEquals(0, report.fromScanned());
        assertEquals(1, report.fromDefaults());
    }

    // ========================================
    // 5. Scanned overrides defaults
    // ========================================

    @Test
    public void testScannedOverridesDefaults() {
        byte[] rom = buildTestRom();

        // Default has same key that scanner will find
        Map<String, Integer> defaults = Map.of("level.SCANNED_TABLE_ADDR", 0x1111);
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        RomPatternScanner scanner = createTestScanner();

        // Note: the scanner does NOT skip defaults, only profile/resolved keys.
        // But here SCANNED_TABLE_ADDR IS already resolved (as a default).
        // The skip-set from getResolvedKeys() includes defaults too, so
        // the scanner will skip it. We need to feed it separately.

        // Instead, simulate what happens when a default exists and scanner
        // is called before initialization (which is the real flow - scanner
        // results are fed via addScannedAddresses which DOES override defaults)
        Map<String, Integer> scannedAddresses = Map.of("level.SCANNED_TABLE_ADDR", 0x42594);
        resolver.addScannedAddresses(scannedAddresses);

        assertEquals("scanned value overrides default",
                0x42594, resolver.getLevelAddress("SCANNED_TABLE_ADDR"));
    }
}
