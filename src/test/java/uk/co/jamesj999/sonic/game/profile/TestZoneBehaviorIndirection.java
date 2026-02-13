package uk.co.jamesj999.sonic.game.profile;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for zone behavior indirection through the {@link RomAddressResolver}.
 *
 * <p>When a ROM hack places Chemical Plant data in zone slot 0 (where EHZ
 * normally lives), the profile's zone mapping tells the engine to use CPZ's
 * scroll handler, event manager, and objects instead of EHZ's.
 *
 * <p>The {@code getZoneBehavior()} method returns the raw profile mapping,
 * while {@code resolveZoneBehavior()} adds a fallback to the zone registry's
 * default name when no profile mapping exists.
 *
 * <h3>How call sites would use this</h3>
 *
 * <p>Where the engine currently does:
 * <pre>
 *   ZoneScrollHandler handler = scrollProvider.getHandler(zoneId);
 * </pre>
 * It could instead use the resolver to determine which zone's behavior to apply:
 * <pre>
 *   String behavior = RomAddressResolver.getInstance().resolveZoneBehavior(zoneId);
 *   // behavior might be "cpz" even though zoneId is 0 (normally EHZ)
 *   // Use behavior key to select scroll handler, event manager, etc.
 * </pre>
 *
 * <p>This allows ROM hacks that rearrange zone data across slots to still get
 * the correct gameplay behavior for each zone slot.
 */
public class TestZoneBehaviorIndirection {

    @Before
    public void setUp() {
        RomAddressResolver.resetInstance();
    }

    @After
    public void tearDown() {
        RomAddressResolver.resetInstance();
    }

    // ========================================
    // Profile-based zone behavior mapping
    // ========================================

    @Test
    public void testZoneBehavior_ProfileMapsZone0ToCPZ() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("ROM Hack", "sonic2", "HACK1234", "test", false));
        profile.putZoneMapping(0, new ZoneMapping("Chemical Plant in slot 0", "CPZ"));

        resolver.initialize(profile, Map.of());

        assertEquals("zone 0 should use CPZ behavior from profile",
                "CPZ", resolver.getZoneBehavior(0));
    }

    @Test
    public void testZoneBehavior_UnmappedZoneReturnsNull() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("ROM Hack", "sonic2", "HACK1234", "test", false));
        profile.putZoneMapping(0, new ZoneMapping("CPZ", "CPZ"));

        resolver.initialize(profile, Map.of());

        assertNull("unmapped zone 99 should return null",
                resolver.getZoneBehavior(99));
    }

    @Test
    public void testZoneBehavior_MultipleRemappedZones() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Rearranged Hack", "sonic2", "HACK5678", "test", false));
        // Hack rearranges: slot 0 = CPZ, slot 1 = HTZ, slot 2 = MCZ
        profile.putZoneMapping(0, new ZoneMapping("CPZ", "cpz"));
        profile.putZoneMapping(1, new ZoneMapping("HTZ", "htz"));
        profile.putZoneMapping(2, new ZoneMapping("MCZ", "mcz"));

        resolver.initialize(profile, Map.of());

        assertEquals("zone 0 behavior", "cpz", resolver.getZoneBehavior(0));
        assertEquals("zone 1 behavior", "htz", resolver.getZoneBehavior(1));
        assertEquals("zone 2 behavior", "mcz", resolver.getZoneBehavior(2));
        assertNull("zone 3 unmapped", resolver.getZoneBehavior(3));
    }

    @Test
    public void testZoneBehavior_NullBehaviorMappingReturnsNull() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.putZoneMapping(0, new ZoneMapping("Unknown Zone", null));

        resolver.initialize(profile, Map.of());

        assertNull("null behavior mapping returns null",
                resolver.getZoneBehavior(0));
    }

    // ========================================
    // resolveZoneBehavior with fallback
    // ========================================

    @Test
    public void testResolveZoneBehavior_ProfileMappingTakesPriority() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        RomProfile profile = new RomProfile();
        profile.putZoneMapping(0, new ZoneMapping("CPZ in slot 0", "cpz"));

        resolver.initialize(profile, Map.of());

        // Profile mapping should win, even though zone registry would say "emerald_hill"
        assertEquals("profile behavior wins over registry fallback",
                "cpz", resolver.resolveZoneBehavior(0));
    }

    @Test
    public void testResolveZoneBehavior_UnmappedZoneOutOfRange_ReturnsNull() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        // Zone 99 is well beyond any registry range
        assertNull("out-of-range zone with no profile returns null",
                resolver.resolveZoneBehavior(99));
    }

    @Test
    public void testResolveZoneBehavior_NegativeZoneId_ReturnsNull() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        assertNull("negative zone ID returns null",
                resolver.resolveZoneBehavior(-1));
    }

    @Test
    public void testResolveZoneBehavior_FallsBackToRegistry() {
        // When no profile mapping exists, resolveZoneBehavior falls back to the
        // zone registry from the current game module. Since GameModuleRegistry
        // defaults to Sonic2GameModule, zone 0 is "Emerald Hill" -> "emerald_hill"
        RomAddressResolver resolver = RomAddressResolver.getInstance();
        resolver.initialize(null, Map.of());

        String behavior = resolver.resolveZoneBehavior(0);
        // The Sonic2 zone registry returns "Emerald Hill" for zone 0,
        // which gets lowercased and space-to-underscore converted
        assertNotNull("zone 0 should resolve from registry fallback", behavior);
        assertEquals("registry fallback for zone 0", "emerald_hill", behavior);
    }

    // ========================================
    // Reinitialize clears zone mappings
    // ========================================

    @Test
    public void testZoneBehavior_ReinitializeClearsMappings() {
        RomAddressResolver resolver = RomAddressResolver.getInstance();

        // First init with profile mapping
        RomProfile profile = new RomProfile();
        profile.putZoneMapping(0, new ZoneMapping("CPZ", "cpz"));
        resolver.initialize(profile, Map.of());
        assertEquals("cpz", resolver.getZoneBehavior(0));

        // Reinitialize without profile
        resolver.initialize(null, Map.of());
        assertNull("zone mapping cleared after reinitialize",
                resolver.getZoneBehavior(0));
    }
}
