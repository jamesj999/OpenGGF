package uk.co.jamesj999.sonic.game.profile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link RomAddressResolver} - layered ROM address resolution
 * with profile, scanned, and default sources.
 */
public class TestRomAddressResolver {

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

    private RomProfile createProfile(String category, String name, int value) {
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic2", "ABCD", "test", false));
        profile.putAddress(category, name, new AddressEntry(value, "verified"));
        return profile;
    }

    private Map<String, Integer> defaults(String... keysAndValues) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(keysAndValues[i], Integer.parseInt(keysAndValues[i + 1], 16));
        }
        return map;
    }

    // ========================================
    // 1. Resolve from profile
    // ========================================

    @Test
    public void testResolveFromProfile() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = createProfile("level", "LEVEL_HEADERS_ADDR", 0x042594);

        resolver.initialize(profile, Map.of());

        assertEquals("profile address resolved",
                0x042594, resolver.getAddress("level", "LEVEL_HEADERS_ADDR"));
    }

    // ========================================
    // 2. Fallback to defaults when no profile
    // ========================================

    @Test
    public void testFallbackToDefaults_NullProfile() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("level.LEVEL_HEADERS_ADDR", 0x042594);

        resolver.initialize(null, defaults);

        assertEquals("default address resolved",
                0x042594, resolver.getAddress("level", "LEVEL_HEADERS_ADDR"));
    }

    @Test
    public void testFallbackToDefaults_EmptyProfile() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        Map<String, Integer> defaults = Map.of("audio.MUSIC_PTR_TABLE_ADDR", 0x0E42F0);

        resolver.initialize(profile, defaults);

        assertEquals("default address resolved with empty profile",
                0x0E42F0, resolver.getAddress("audio", "MUSIC_PTR_TABLE_ADDR"));
    }

    // ========================================
    // 3. Profile overrides defaults
    // ========================================

    @Test
    public void testProfileOverridesDefaults() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = createProfile("level", "LEVEL_HEADERS_ADDR", 0x050000);
        Map<String, Integer> defaults = Map.of("level.LEVEL_HEADERS_ADDR", 0x042594);

        resolver.initialize(profile, defaults);

        assertEquals("profile value wins over default",
                0x050000, resolver.getAddress("level", "LEVEL_HEADERS_ADDR"));
    }

    // ========================================
    // 4. Missing address returns -1
    // ========================================

    @Test
    public void testMissingAddress_ReturnsNegativeOne() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        assertEquals("missing address returns -1",
                -1, resolver.getAddress("level", "NONEXISTENT"));
    }

    @Test
    public void testMissingAddress_NoCategoryMatch() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("level.LEVEL_HEADERS_ADDR", 0x042594);
        resolver.initialize(null, defaults);

        assertEquals("wrong category returns -1",
                -1, resolver.getAddress("audio", "LEVEL_HEADERS_ADDR"));
    }

    // ========================================
    // 5. Convenience methods
    // ========================================

    @Test
    public void testConvenienceMethod_getLevelAddress() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("level.LEVEL_HEADERS_ADDR", 0x042594);
        resolver.initialize(null, defaults);

        assertEquals("getLevelAddress resolves",
                0x042594, resolver.getLevelAddress("LEVEL_HEADERS_ADDR"));
    }

    @Test
    public void testConvenienceMethod_getAudioAddress() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("audio.MUSIC_PTR_TABLE_ADDR", 0x0E42F0);
        resolver.initialize(null, defaults);

        assertEquals("getAudioAddress resolves",
                0x0E42F0, resolver.getAudioAddress("MUSIC_PTR_TABLE_ADDR"));
    }

    @Test
    public void testConvenienceMethod_getArtAddress() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("art.SPRITE_ART_ADDR", 0x0A0000);
        resolver.initialize(null, defaults);

        assertEquals("getArtAddress resolves",
                0x0A0000, resolver.getArtAddress("SPRITE_ART_ADDR"));
    }

    @Test
    public void testConvenienceMethod_getCollisionAddress() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("collision.COLLISION_INDEX_ADDR", 0x040000);
        resolver.initialize(null, defaults);

        assertEquals("getCollisionAddress resolves",
                0x040000, resolver.getCollisionAddress("COLLISION_INDEX_ADDR"));
    }

    @Test
    public void testConvenienceMethod_getPaletteAddress() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("palette.SONIC_PALETTE_ADDR", 0x002300);
        resolver.initialize(null, defaults);

        assertEquals("getPaletteAddress resolves",
                0x002300, resolver.getPaletteAddress("SONIC_PALETTE_ADDR"));
    }

    @Test
    public void testConvenienceMethod_getMissing() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        assertEquals("missing level address returns -1",
                -1, resolver.getLevelAddress("NONEXISTENT"));
        assertEquals("missing audio address returns -1",
                -1, resolver.getAudioAddress("NONEXISTENT"));
        assertEquals("missing art address returns -1",
                -1, resolver.getArtAddress("NONEXISTENT"));
        assertEquals("missing collision address returns -1",
                -1, resolver.getCollisionAddress("NONEXISTENT"));
        assertEquals("missing palette address returns -1",
                -1, resolver.getPaletteAddress("NONEXISTENT"));
    }

    // ========================================
    // 6. getAddress with default value
    // ========================================

    @Test
    public void testGetAddress_WithDefaultValue_Found() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("level.LEVEL_HEADERS_ADDR", 0x042594);
        resolver.initialize(null, defaults);

        assertEquals("found address ignores caller default",
                0x042594, resolver.getAddress("level", "LEVEL_HEADERS_ADDR", 0xFFFFFF));
    }

    @Test
    public void testGetAddress_WithDefaultValue_Missing() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        assertEquals("missing address returns caller default",
                0xBEEF, resolver.getAddress("level", "MISSING", 0xBEEF));
    }

    @Test
    public void testGetLevelAddress_WithDefaultValue() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        assertEquals("missing level address returns caller default",
                0xCAFE, resolver.getLevelAddress("MISSING", 0xCAFE));
    }

    @Test
    public void testGetLevelAddress_WithDefaultValue_Found() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("level.ADDR", 0x1234);
        resolver.initialize(null, defaults);

        assertEquals("found level address ignores caller default",
                0x1234, resolver.getLevelAddress("ADDR", 0xFFFF));
    }

    // ========================================
    // 7. Zone behavior lookup
    // ========================================

    @Test
    public void testZoneBehavior_Mapped() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.putZoneMapping(0, new ZoneMapping("EHZ", "ehz"));
        profile.putZoneMapping(1, new ZoneMapping("CPZ", "cpz"));
        resolver.initialize(profile, Map.of());

        assertEquals("zone 0 behavior", "ehz", resolver.getZoneBehavior(0));
        assertEquals("zone 1 behavior", "cpz", resolver.getZoneBehavior(1));
    }

    @Test
    public void testZoneBehavior_NullMapping() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.putZoneMapping(5, new ZoneMapping("Unknown", null));
        resolver.initialize(profile, Map.of());

        assertNull("null behavior mapping returns null",
                resolver.getZoneBehavior(5));
    }

    @Test
    public void testZoneBehavior_Unmapped() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        assertNull("unmapped zone returns null",
                resolver.getZoneBehavior(99));
    }

    // ========================================
    // 8. Resolution report counts
    // ========================================

    @Test
    public void testReport_AllFromProfile() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "A", new AddressEntry(1, "verified"));
        profile.putAddress("level", "B", new AddressEntry(2, "verified"));
        resolver.initialize(profile, Map.of());

        ResolutionReport report = resolver.getReport();
        assertEquals("2 from profile", 2, report.fromProfile());
        assertEquals("0 from scanned", 0, report.fromScanned());
        assertEquals("0 from defaults", 0, report.fromDefaults());
        assertEquals("0 missing", 0, report.missing());
        assertEquals("2 total resolved", 2, report.totalResolved());
        assertEquals("2 total expected", 2, report.totalExpected());
    }

    @Test
    public void testReport_AllFromDefaults() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of(
                "level.A", 1,
                "level.B", 2,
                "audio.C", 3
        );
        resolver.initialize(null, defaults);

        ResolutionReport report = resolver.getReport();
        assertEquals("0 from profile", 0, report.fromProfile());
        assertEquals("0 from scanned", 0, report.fromScanned());
        assertEquals("3 from defaults", 3, report.fromDefaults());
        assertEquals("0 missing", 0, report.missing());
    }

    @Test
    public void testReport_MixedSources() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "A", new AddressEntry(0x100, "verified"));
        Map<String, Integer> defaults = Map.of(
                "level.A", 0x200,   // will be overridden by profile
                "level.B", 0x300    // will use default
        );
        resolver.initialize(profile, defaults);

        ResolutionReport report = resolver.getReport();
        assertEquals("1 from profile", 1, report.fromProfile());
        assertEquals("1 from defaults", 1, report.fromDefaults());
        assertEquals("0 missing", 0, report.missing());
        assertEquals("2 total resolved", 2, report.totalResolved());
    }

    @Test
    public void testReport_EmptyResolver() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        ResolutionReport report = resolver.getReport();
        assertEquals("0 from profile", 0, report.fromProfile());
        assertEquals("0 from scanned", 0, report.fromScanned());
        assertEquals("0 from defaults", 0, report.fromDefaults());
        assertEquals("0 missing", 0, report.missing());
        assertEquals("0 total resolved", 0, report.totalResolved());
        assertEquals("0 total expected", 0, report.totalExpected());
    }

    // ========================================
    // 9. Scanned addresses
    // ========================================

    @Test
    public void testScannedAddresses_FillGaps() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        resolver.addScannedAddresses(Map.of("level.DISCOVERED", 0xABCDEF));

        assertEquals("scanned address fills gap",
                0xABCDEF, resolver.getAddress("level", "DISCOVERED"));
    }

    @Test
    public void testScannedAddresses_DoNotOverrideProfile() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = createProfile("level", "ADDR", 0x100);
        resolver.initialize(profile, Map.of());

        resolver.addScannedAddresses(Map.of("level.ADDR", 0x999));

        assertEquals("profile value preserved over scanned",
                0x100, resolver.getAddress("level", "ADDR"));
    }

    @Test
    public void testScannedAddresses_OverrideDefaults() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = Map.of("level.ADDR", 0x100);
        resolver.initialize(null, defaults);

        resolver.addScannedAddresses(Map.of("level.ADDR", 0x200));

        assertEquals("scanned value overrides default",
                0x200, resolver.getAddress("level", "ADDR"));
    }

    @Test
    public void testScannedAddresses_ReportCounts() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "PROFILE_ADDR", new AddressEntry(0x100, "verified"));
        Map<String, Integer> defaults = Map.of(
                "level.PROFILE_ADDR", 0x200,
                "level.DEFAULT_ADDR", 0x300,
                "level.SCANNED_ADDR", 0x400
        );
        resolver.initialize(profile, defaults);

        // Scanned overrides default, does not override profile
        resolver.addScannedAddresses(Map.of(
                "level.PROFILE_ADDR", 0x999,
                "level.SCANNED_ADDR", 0x500
        ));

        assertEquals("profile addr unchanged", 0x100, resolver.getAddress("level", "PROFILE_ADDR"));
        assertEquals("default addr intact", 0x300, resolver.getAddress("level", "DEFAULT_ADDR"));
        assertEquals("scanned addr replaced default", 0x500, resolver.getAddress("level", "SCANNED_ADDR"));

        ResolutionReport report = resolver.getReport();
        assertEquals("1 from profile", 1, report.fromProfile());
        assertEquals("1 from scanned", 1, report.fromScanned());
        assertEquals("1 from defaults", 1, report.fromDefaults());
        assertEquals("0 missing", 0, report.missing());
    }

    @Test
    public void testScannedAddresses_NullIgnored() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        // Should not throw
        resolver.addScannedAddresses(null);

        assertEquals("still returns -1 for missing",
                -1, resolver.getAddress("level", "ANYTHING"));
    }

    // ========================================
    // 10. Singleton behavior
    // ========================================

    @Test
    public void testSingleton_SameInstance() {
        RomAddressResolver a = RomAddressResolver.getInstance();
        RomAddressResolver b = RomAddressResolver.getInstance();
        assertSame("getInstance returns same instance", a, b);
    }

    @Test
    public void testSingleton_ResetCreatesNew() {
        RomAddressResolver first = RomAddressResolver.getInstance();
        RomAddressResolver.resetInstance();
        RomAddressResolver second = RomAddressResolver.getInstance();
        assertNotSame("reset creates a new instance", first, second);
    }

    @Test
    public void testReinitialize_ClearsPreviousState() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();

        // First initialization
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "OLD_ADDR", new AddressEntry(0x100, "verified"));
        profile.putZoneMapping(0, new ZoneMapping("EHZ", "ehz"));
        resolver.initialize(profile, Map.of());
        assertEquals(0x100, resolver.getAddress("level", "OLD_ADDR"));
        assertEquals("ehz", resolver.getZoneBehavior(0));

        // Re-initialize with different data
        resolver.initialize(null, Map.of("audio.NEW_ADDR", 0x200));
        assertEquals("old address gone after reinitialize",
                -1, resolver.getAddress("level", "OLD_ADDR"));
        assertEquals("new address present",
                0x200, resolver.getAddress("audio", "NEW_ADDR"));
        assertNull("old zone mapping gone", resolver.getZoneBehavior(0));
    }

    // ========================================
    // 11. Multiple categories in profile
    // ========================================

    @Test
    public void testMultipleCategoriesFromProfile() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "LEVEL_ADDR", new AddressEntry(0x1000, "verified"));
        profile.putAddress("audio", "MUSIC_ADDR", new AddressEntry(0x2000, "verified"));
        profile.putAddress("art", "ART_ADDR", new AddressEntry(0x3000, "inferred"));
        profile.putAddress("collision", "COL_ADDR", new AddressEntry(0x4000, "verified"));
        profile.putAddress("palette", "PAL_ADDR", new AddressEntry(0x5000, "verified"));

        resolver.initialize(profile, Map.of());

        assertEquals(0x1000, resolver.getLevelAddress("LEVEL_ADDR"));
        assertEquals(0x2000, resolver.getAudioAddress("MUSIC_ADDR"));
        assertEquals(0x3000, resolver.getArtAddress("ART_ADDR"));
        assertEquals(0x4000, resolver.getCollisionAddress("COL_ADDR"));
        assertEquals(0x5000, resolver.getPaletteAddress("PAL_ADDR"));
    }

    // ========================================
    // 12. ResolutionReport record
    // ========================================

    @Test
    public void testResolutionReport_Record() {
        ResolutionReport report = new ResolutionReport(5, 3, 10, 2);
        assertEquals(5, report.fromProfile());
        assertEquals(3, report.fromScanned());
        assertEquals(10, report.fromDefaults());
        assertEquals(2, report.missing());
        assertEquals(18, report.totalResolved());
        assertEquals(20, report.totalExpected());
    }

    @Test
    public void testResolutionReport_AllZeros() {
        ResolutionReport report = new ResolutionReport(0, 0, 0, 0);
        assertEquals(0, report.totalResolved());
        assertEquals(0, report.totalExpected());
    }
}
