package uk.co.jamesj999.sonic.game.profile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kGameModule;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Integration tests verifying that S3K hardcoded constants correctly
 * populate the {@link RomAddressResolver} via the game module's
 * {@link Sonic3kGameModule#getDefaultOffsets()} method, and that
 * {@link Sonic3kConstants#initFromResolver(RomAddressResolver)} can
 * apply resolver overrides back to the constants.
 */
public class TestS3kResolverIntegration {

    // Save original values to restore after each test
    private int originalLevelSizesAddr;
    private int originalLevelLoadBlockAddr;
    private int originalSolidIndexesAddr;
    private int originalSolidTileAngleAddr;
    private int originalPalPointersAddr;

    @Before
    public void setUp() {
        RomAddressResolver.resetInstance();
        originalLevelSizesAddr = Sonic3kConstants.LEVEL_SIZES_ADDR;
        originalLevelLoadBlockAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR;
        originalSolidIndexesAddr = Sonic3kConstants.SOLID_INDEXES_ADDR;
        originalSolidTileAngleAddr = Sonic3kConstants.SOLID_TILE_ANGLE_ADDR;
        originalPalPointersAddr = Sonic3kConstants.PAL_POINTERS_ADDR;
    }

    @After
    public void tearDown() {
        Sonic3kConstants.LEVEL_SIZES_ADDR = originalLevelSizesAddr;
        Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR = originalLevelLoadBlockAddr;
        Sonic3kConstants.SOLID_INDEXES_ADDR = originalSolidIndexesAddr;
        Sonic3kConstants.SOLID_TILE_ANGLE_ADDR = originalSolidTileAngleAddr;
        Sonic3kConstants.PAL_POINTERS_ADDR = originalPalPointersAddr;
        RomAddressResolver.resetInstance();
    }

    @Test
    public void testS3kDefaultsPopulateResolver() {
        Sonic3kGameModule module = new Sonic3kGameModule();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(module.getDefaultOffsets());
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        // LEVEL_SIZES_ADDR categorizes as "level" (starts with LEVEL_)
        int levelSizesAddr = resolver.getLevelAddress("LEVEL_SIZES_ADDR");
        assertEquals("LEVEL_SIZES_ADDR should resolve from defaults",
                Sonic3kConstants.LEVEL_SIZES_ADDR, levelSizesAddr);
    }

    @Test
    public void testS3kDefaultsContainMultipleCategories() {
        Sonic3kGameModule module = new Sonic3kGameModule();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(module.getDefaultOffsets());
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        // Verify level addresses
        assertEquals("LEVEL_LOAD_BLOCK_ADDR resolves",
                Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR,
                resolver.getLevelAddress("LEVEL_LOAD_BLOCK_ADDR"));
        assertEquals("LEVEL_PTRS_ADDR resolves",
                Sonic3kConstants.LEVEL_PTRS_ADDR,
                resolver.getLevelAddress("LEVEL_PTRS_ADDR"));

        // Verify collision addresses (SOLID_ prefix -> "collision" category)
        assertEquals("SOLID_INDEXES_ADDR resolves",
                Sonic3kConstants.SOLID_INDEXES_ADDR,
                resolver.getCollisionAddress("SOLID_INDEXES_ADDR"));
        assertEquals("SOLID_TILE_ANGLE_ADDR resolves",
                Sonic3kConstants.SOLID_TILE_ANGLE_ADDR,
                resolver.getCollisionAddress("SOLID_TILE_ANGLE_ADDR"));

        // Verify palette addresses (PAL_ prefix -> "palette" category)
        assertEquals("PAL_POINTERS_ADDR resolves",
                Sonic3kConstants.PAL_POINTERS_ADDR,
                resolver.getPaletteAddress("PAL_POINTERS_ADDR"));
    }

    @Test
    public void testS3kDefaultsReportShowsAllFromDefaults() {
        Sonic3kGameModule module = new Sonic3kGameModule();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(module.getDefaultOffsets());
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        ResolutionReport report = resolver.getReport();
        assertEquals("No addresses from profile", 0, report.fromProfile());
        assertEquals("All addresses from defaults", defaults.size(), report.fromDefaults());
        assertEquals("No missing addresses", 0, report.missing());
        // S3K has 14 mutable address fields
        assertEquals("Should have 14 S3K offset entries", 14, report.totalResolved());
    }

    @Test
    public void testGetAllOffsetsContainsExpectedKeys() {
        Map<String, Integer> offsets = Sonic3kConstants.getAllOffsets();
        assertFalse("getAllOffsets() should not be empty", offsets.isEmpty());
        assertEquals("getAllOffsets() should have 14 entries", 14, offsets.size());

        // Verify all expected keys are present
        assertTrue("LEVEL_LOAD_BLOCK_ADDR", offsets.containsKey("LEVEL_LOAD_BLOCK_ADDR"));
        assertTrue("LEVEL_SIZES_ADDR", offsets.containsKey("LEVEL_SIZES_ADDR"));
        assertTrue("SONIC_START_LOCATIONS_ADDR", offsets.containsKey("SONIC_START_LOCATIONS_ADDR"));
        assertTrue("KNUX_START_LOCATIONS_ADDR", offsets.containsKey("KNUX_START_LOCATIONS_ADDR"));
        assertTrue("SPRITE_LOC_PTRS_ADDR", offsets.containsKey("SPRITE_LOC_PTRS_ADDR"));
        assertTrue("RING_LOC_PTRS_ADDR", offsets.containsKey("RING_LOC_PTRS_ADDR"));
        assertTrue("LEVEL_PTRS_ADDR", offsets.containsKey("LEVEL_PTRS_ADDR"));
        assertTrue("SOLID_INDEXES_ADDR", offsets.containsKey("SOLID_INDEXES_ADDR"));
        assertTrue("SOLID_TILE_ANGLE_ADDR", offsets.containsKey("SOLID_TILE_ANGLE_ADDR"));
        assertTrue("SOLID_TILE_VERTICAL_MAP_ADDR", offsets.containsKey("SOLID_TILE_VERTICAL_MAP_ADDR"));
        assertTrue("SOLID_TILE_HORIZONTAL_MAP_ADDR", offsets.containsKey("SOLID_TILE_HORIZONTAL_MAP_ADDR"));
        assertTrue("PAL_POINTERS_ADDR", offsets.containsKey("PAL_POINTERS_ADDR"));
        assertTrue("SONIC_PALETTE_ADDR", offsets.containsKey("SONIC_PALETTE_ADDR"));
        assertTrue("KNUCKLES_PALETTE_ADDR", offsets.containsKey("KNUCKLES_PALETTE_ADDR"));
    }

    @Test
    public void testGetAllOffsetsReturnsCurrentValues() {
        // Verify values match the current static field values
        Map<String, Integer> offsets = Sonic3kConstants.getAllOffsets();
        assertEquals(Sonic3kConstants.LEVEL_SIZES_ADDR, (int) offsets.get("LEVEL_SIZES_ADDR"));
        assertEquals(Sonic3kConstants.SOLID_INDEXES_ADDR, (int) offsets.get("SOLID_INDEXES_ADDR"));
        assertEquals(Sonic3kConstants.PAL_POINTERS_ADDR, (int) offsets.get("PAL_POINTERS_ADDR"));
    }

    @Test
    public void testInitFromResolverOverridesConstant() {
        // Create a profile that overrides LEVEL_SIZES_ADDR
        int overrideValue = 0xDEAD;
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic3k", "TEST", "test", false));
        String category = ProfileGenerator.categorize("LEVEL_SIZES_ADDR");
        profile.putAddress(category, "LEVEL_SIZES_ADDR", new AddressEntry(overrideValue, "verified"));

        // Initialize resolver with profile and defaults
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic3kConstants.getAllOffsets());
        resolver.initialize(profile, defaults);

        // Apply resolver to constants
        Sonic3kConstants.initFromResolver(resolver);

        // The overridden constant should have the new value
        assertEquals("LEVEL_SIZES_ADDR should be overridden by profile",
                overrideValue, Sonic3kConstants.LEVEL_SIZES_ADDR);
    }

    @Test
    public void testInitFromResolverKeepsDefaultsForNonOverridden() {
        // Create a profile that only overrides one field
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic3k", "TEST", "test", false));
        profile.putAddress(ProfileGenerator.categorize("LEVEL_SIZES_ADDR"),
                "LEVEL_SIZES_ADDR", new AddressEntry(0xBEEF, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic3kConstants.getAllOffsets());
        resolver.initialize(profile, defaults);

        Sonic3kConstants.initFromResolver(resolver);

        // Non-overridden constants should retain their original defaults
        assertEquals("SOLID_INDEXES_ADDR should keep its default",
                originalSolidIndexesAddr, Sonic3kConstants.SOLID_INDEXES_ADDR);
        assertEquals("SOLID_TILE_ANGLE_ADDR should keep its default",
                originalSolidTileAngleAddr, Sonic3kConstants.SOLID_TILE_ANGLE_ADDR);
        assertEquals("PAL_POINTERS_ADDR should keep its default",
                originalPalPointersAddr, Sonic3kConstants.PAL_POINTERS_ADDR);
    }

    @Test
    public void testInitFromResolverWithNullProfile() {
        // Initialize resolver with no profile - all constants should keep defaults
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic3kConstants.getAllOffsets());
        resolver.initialize(null, defaults);

        Sonic3kConstants.initFromResolver(resolver);

        assertEquals("LEVEL_SIZES_ADDR should keep its default",
                originalLevelSizesAddr, Sonic3kConstants.LEVEL_SIZES_ADDR);
        assertEquals("LEVEL_LOAD_BLOCK_ADDR should keep its default",
                originalLevelLoadBlockAddr, Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR);
    }

    @Test
    public void testProfileOverridesS3kDefaults() {
        Sonic3kGameModule module = new Sonic3kGameModule();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(module.getDefaultOffsets());
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test Override", "sonic3k", "test", "test", false));
        // Override one address
        profile.putAddress("level", "LEVEL_SIZES_ADDR",
                new AddressEntry(0xCAFE, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, defaults);

        // Profile value should win
        assertEquals("Profile overrides default",
                0xCAFE, resolver.getLevelAddress("LEVEL_SIZES_ADDR"));

        // Other defaults should still be present
        assertEquals("Non-overridden default preserved",
                Sonic3kConstants.LEVEL_PTRS_ADDR,
                resolver.getLevelAddress("LEVEL_PTRS_ADDR"));
    }

    @Test
    public void testRegisterScanPatternsIsNoOp() {
        // Verify that registerScanPatterns doesn't throw and doesn't register patterns
        // (S3K uses Sonic3kRomScanner for cascading pattern discovery instead)
        Sonic3kGameModule module = new Sonic3kGameModule();
        uk.co.jamesj999.sonic.game.profile.scanner.RomPatternScanner scanner =
                new uk.co.jamesj999.sonic.game.profile.scanner.RomPatternScanner();
        module.registerScanPatterns(scanner);

        // Scanning an empty ROM should produce no results (no patterns registered)
        Map<String, ?> results = scanner.scan(new byte[0], null);
        assertTrue("No patterns should be registered for S3K", results.isEmpty());
    }
}
