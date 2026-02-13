package uk.co.jamesj999.sonic.game.profile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Integration test verifying that Sonic 2 hardcoded constants correctly
 * populate the {@link RomAddressResolver} via {@link ProfileGenerator#toResolverDefaults(Map)}.
 */
public class TestResolverIntegration {

    @Before
    public void setUp() {
        RomAddressResolver.resetInstance();
    }

    @After
    public void tearDown() {
        RomAddressResolver.resetInstance();
    }

    @Test
    public void testSonic2DefaultsPopulateResolver() {
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic2Constants.getAllOffsets());
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        int levelAddr = resolver.getLevelAddress("LEVEL_LAYOUT_DIR_ADDR_LOC");
        assertEquals(Sonic2Constants.LEVEL_LAYOUT_DIR_ADDR_LOC, levelAddr);
    }

    @Test
    public void testSonic2DefaultsContainMultipleCategories() {
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic2Constants.getAllOffsets());
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        // Verify level addresses
        assertEquals("Level data dir address",
                Sonic2Constants.LEVEL_DATA_DIR,
                resolver.getLevelAddress("LEVEL_DATA_DIR"));

        // Verify audio addresses (MUSIC_PLAYLIST_ADDR categorizes as "misc" since prefix is not MUSIC_PTR etc.)
        assertEquals("Music playlist address",
                Sonic2Constants.MUSIC_PLAYLIST_ADDR,
                resolver.getAudioAddress("MUSIC_PLAYLIST_ADDR"));

        // Verify collision addresses
        assertEquals("Collision layout dir address",
                Sonic2Constants.COLLISION_LAYOUT_DIR_ADDR,
                resolver.getCollisionAddress("COLLISION_LAYOUT_DIR_ADDR"));
    }

    @Test
    public void testSonic2DefaultsReportShowsAllFromDefaults() {
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic2Constants.getAllOffsets());
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, defaults);

        ResolutionReport report = resolver.getReport();
        assertEquals("No addresses from profile", 0, report.fromProfile());
        assertEquals("All addresses from defaults",
                defaults.size(), report.fromDefaults());
        assertEquals("No missing addresses", 0, report.missing());
        assertTrue("Should have many addresses", report.totalResolved() > 100);
    }

    @Test
    public void testProfileOverridesSonic2Defaults() {
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Sonic2Constants.getAllOffsets());
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test Override", "sonic2", "test", "test", false));
        // Override one address with a different value
        profile.putAddress("level", "LEVEL_LAYOUT_DIR_ADDR_LOC",
                new AddressEntry(0xDEAD, "verified"));

        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(profile, defaults);

        // Profile value should win
        assertEquals("Profile overrides default",
                0xDEAD, resolver.getLevelAddress("LEVEL_LAYOUT_DIR_ADDR_LOC"));

        // Other defaults should still be present
        assertEquals("Non-overridden default preserved",
                Sonic2Constants.LEVEL_DATA_DIR,
                resolver.getLevelAddress("LEVEL_DATA_DIR"));
    }
}
