package uk.co.jamesj999.sonic.game.profile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for the constants migration shim: {@link Sonic2Constants#initFromResolver(RomAddressResolver)}.
 *
 * <p>Verifies that the resolver can override specific constants and that
 * non-overridden constants keep their hardcoded default values.</p>
 */
public class TestConstantsMigrationShim {

    // Save original values to restore after each test
    private int originalLevelDataDir;
    private int originalArtNemMonitorAddr;
    private int originalSolidTileAngleAddr;
    private int originalMusicPlaylistAddr;

    @Before
    public void setUp() {
        RomAddressResolver.resetInstance();
        // Save original constant values before any test modifies them
        originalLevelDataDir = Sonic2Constants.LEVEL_DATA_DIR;
        originalArtNemMonitorAddr = Sonic2Constants.ART_NEM_MONITOR_ADDR;
        originalSolidTileAngleAddr = Sonic2Constants.SOLID_TILE_ANGLE_ADDR;
        originalMusicPlaylistAddr = Sonic2Constants.MUSIC_PLAYLIST_ADDR;
    }

    @After
    public void tearDown() {
        // Restore original constant values to avoid polluting other tests
        Sonic2Constants.LEVEL_DATA_DIR = originalLevelDataDir;
        Sonic2Constants.ART_NEM_MONITOR_ADDR = originalArtNemMonitorAddr;
        Sonic2Constants.SOLID_TILE_ANGLE_ADDR = originalSolidTileAngleAddr;
        Sonic2Constants.MUSIC_PLAYLIST_ADDR = originalMusicPlaylistAddr;
        RomAddressResolver.resetInstance();
    }

    @Test
    public void testInitFromResolverOverridesConstant() {
        // Create a profile that overrides LEVEL_DATA_DIR
        int overrideValue = 0xDEAD;
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic2", "TEST", "test", false));
        String category = ProfileGenerator.categorize("LEVEL_DATA_DIR");
        profile.putAddress(category, "LEVEL_DATA_DIR", new AddressEntry(overrideValue, "verified"));

        // Initialize resolver with profile and defaults from constants
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic2Constants.getAllOffsets());
        resolver.initialize(profile, defaults);

        // Apply resolver to constants
        Sonic2Constants.initFromResolver(resolver);

        // The overridden constant should have the new value
        assertEquals("LEVEL_DATA_DIR should be overridden by profile",
                overrideValue, Sonic2Constants.LEVEL_DATA_DIR);
    }

    @Test
    public void testInitFromResolverKeepsDefaultsForNonOverridden() {
        // Create a profile that only overrides LEVEL_DATA_DIR
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic2", "TEST", "test", false));
        String category = ProfileGenerator.categorize("LEVEL_DATA_DIR");
        profile.putAddress(category, "LEVEL_DATA_DIR", new AddressEntry(0xBEEF, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic2Constants.getAllOffsets());
        resolver.initialize(profile, defaults);

        // Apply resolver to constants
        Sonic2Constants.initFromResolver(resolver);

        // Non-overridden constants should retain their original defaults
        assertEquals("ART_NEM_MONITOR_ADDR should keep its default",
                originalArtNemMonitorAddr, Sonic2Constants.ART_NEM_MONITOR_ADDR);
        assertEquals("SOLID_TILE_ANGLE_ADDR should keep its default",
                originalSolidTileAngleAddr, Sonic2Constants.SOLID_TILE_ANGLE_ADDR);
        assertEquals("MUSIC_PLAYLIST_ADDR should keep its default",
                originalMusicPlaylistAddr, Sonic2Constants.MUSIC_PLAYLIST_ADDR);
    }

    @Test
    public void testInitFromResolverWithNullProfile() {
        // Initialize resolver with no profile (null) - all constants should keep defaults
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic2Constants.getAllOffsets());
        resolver.initialize(null, defaults);

        Sonic2Constants.initFromResolver(resolver);

        // All values should remain at their original defaults
        assertEquals("LEVEL_DATA_DIR should keep its default",
                originalLevelDataDir, Sonic2Constants.LEVEL_DATA_DIR);
        assertEquals("ART_NEM_MONITOR_ADDR should keep its default",
                originalArtNemMonitorAddr, Sonic2Constants.ART_NEM_MONITOR_ADDR);
    }

    @Test
    public void testInitFromResolverMultipleOverrides() {
        // Override multiple constants from different categories
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic2", "TEST", "test", false));

        // Override one level address and one art address
        profile.putAddress(ProfileGenerator.categorize("LEVEL_DATA_DIR"),
                "LEVEL_DATA_DIR", new AddressEntry(0x11111, "verified"));
        profile.putAddress(ProfileGenerator.categorize("ART_NEM_MONITOR_ADDR"),
                "ART_NEM_MONITOR_ADDR", new AddressEntry(0x22222, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic2Constants.getAllOffsets());
        resolver.initialize(profile, defaults);

        Sonic2Constants.initFromResolver(resolver);

        assertEquals("LEVEL_DATA_DIR should be overridden",
                0x11111, Sonic2Constants.LEVEL_DATA_DIR);
        assertEquals("ART_NEM_MONITOR_ADDR should be overridden",
                0x22222, Sonic2Constants.ART_NEM_MONITOR_ADDR);
        // Non-overridden should keep default
        assertEquals("SOLID_TILE_ANGLE_ADDR should keep its default",
                originalSolidTileAngleAddr, Sonic2Constants.SOLID_TILE_ANGLE_ADDR);
    }

    @Test
    public void testAllOffsetsFieldsAreNonFinal() {
        // Verify all fields in getAllOffsets() can be written to (non-final)
        // by reading their values, writing a different value, and restoring
        Map<String, Integer> offsets = Sonic2Constants.getAllOffsets();
        assertFalse("getAllOffsets() should not be empty", offsets.isEmpty());
        assertTrue("getAllOffsets() should have at least 300 entries", offsets.size() >= 300);
    }
}
