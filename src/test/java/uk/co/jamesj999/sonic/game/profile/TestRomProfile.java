package uk.co.jamesj999.sonic.game.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for ROM profile data model: round-trip JSON serialization,
 * convenience accessors, and missing-key null behavior.
 */
public class TestRomProfile {

    private final ObjectMapper mapper = new ObjectMapper();

    // ========================================
    // Round-trip serialization
    // ========================================

    @Test
    public void testRoundTrip_SerializeAndDeserialize() throws Exception {
        RomProfile original = new RomProfile();
        original.setMetadata(new ProfileMetadata(
                "Sonic 2 REV01", "sonic2", "ABCD1234", "RomOffsetFinder", true
        ));
        original.putAddress("level", "LEVEL_HEADERS_ADDR",
                new AddressEntry(0x042594, "verified"));
        original.putAddress("level", "LEVEL_SIZE_ARRAY_ADDR",
                new AddressEntry(0x042970, "inferred"));
        original.putAddress("audio", "MUSIC_PTR_TABLE_ADDR",
                new AddressEntry(0x0E42F0, "verified"));
        original.putZoneMapping(0, new ZoneMapping("EHZ", "ehz"));
        original.putZoneMapping(1, new ZoneMapping("CPZ", "cpz"));

        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(original);
        RomProfile restored = mapper.readValue(json, RomProfile.class);

        // Metadata
        assertNotNull("metadata present", restored.getMetadata());
        assertEquals("name", "Sonic 2 REV01", restored.getMetadata().name());
        assertEquals("game", "sonic2", restored.getMetadata().game());
        assertEquals("checksum", "ABCD1234", restored.getMetadata().checksum());
        assertEquals("generatedBy", "RomOffsetFinder", restored.getMetadata().generatedBy());
        assertTrue("complete", restored.getMetadata().complete());

        // Addresses
        AddressEntry levelHeaders = restored.getAddress("level", "LEVEL_HEADERS_ADDR");
        assertNotNull("level headers present", levelHeaders);
        assertEquals("level headers value", 0x042594, levelHeaders.value());
        assertEquals("level headers confidence", "verified", levelHeaders.confidence());

        AddressEntry levelSizes = restored.getAddress("level", "LEVEL_SIZE_ARRAY_ADDR");
        assertNotNull("level sizes present", levelSizes);
        assertEquals("level sizes value", 0x042970, levelSizes.value());
        assertEquals("level sizes confidence", "inferred", levelSizes.confidence());

        AddressEntry musicPtr = restored.getAddress("audio", "MUSIC_PTR_TABLE_ADDR");
        assertNotNull("music ptr present", musicPtr);
        assertEquals("music ptr value", 0x0E42F0, musicPtr.value());

        // Zones
        ZoneMapping zone0 = restored.getZoneMapping(0);
        assertNotNull("zone 0 present", zone0);
        assertEquals("zone 0 name", "EHZ", zone0.name());
        assertEquals("zone 0 behavior", "ehz", zone0.behaviorMapping());

        ZoneMapping zone1 = restored.getZoneMapping(1);
        assertNotNull("zone 1 present", zone1);
        assertEquals("zone 1 name", "CPZ", zone1.name());

        // Address count
        assertEquals("total address count", 3, restored.addressCount());
    }

    // ========================================
    // Missing-key null behavior
    // ========================================

    @Test
    public void testGetAddress_MissingCategory_ReturnsNull() {
        RomProfile profile = new RomProfile();
        assertNull("missing category returns null",
                profile.getAddress("nonexistent", "ANY_KEY"));
    }

    @Test
    public void testGetAddress_MissingName_ReturnsNull() {
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "EXISTING", new AddressEntry(0x100, "verified"));
        assertNull("missing name returns null",
                profile.getAddress("level", "NONEXISTENT"));
    }

    @Test
    public void testGetZoneMapping_MissingId_ReturnsNull() {
        RomProfile profile = new RomProfile();
        assertNull("missing zone returns null", profile.getZoneMapping(99));
    }

    // ========================================
    // Address count edge cases
    // ========================================

    @Test
    public void testAddressCount_Empty() {
        RomProfile profile = new RomProfile();
        assertEquals("empty profile has zero addresses", 0, profile.addressCount());
    }

    @Test
    public void testAddressCount_MultipleCategories() {
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "A", new AddressEntry(1, "verified"));
        profile.putAddress("level", "B", new AddressEntry(2, "verified"));
        profile.putAddress("audio", "C", new AddressEntry(3, "inferred"));
        assertEquals("3 addresses across 2 categories", 3, profile.addressCount());
    }

    // ========================================
    // putAddress overwrites existing entry
    // ========================================

    @Test
    public void testPutAddress_OverwritesExisting() {
        RomProfile profile = new RomProfile();
        profile.putAddress("level", "ADDR", new AddressEntry(0x100, "inferred"));
        profile.putAddress("level", "ADDR", new AddressEntry(0x200, "verified"));

        AddressEntry entry = profile.getAddress("level", "ADDR");
        assertNotNull(entry);
        assertEquals("overwritten value", 0x200, entry.value());
        assertEquals("overwritten confidence", "verified", entry.confidence());
        assertEquals("count unchanged after overwrite", 1, profile.addressCount());
    }

    // ========================================
    // JSON field name mapping
    // ========================================

    @Test
    public void testJsonFieldNames() throws Exception {
        RomProfile profile = new RomProfile();
        profile.setMetadata(new ProfileMetadata("Test", "sonic2", "FFFF", "test", false));
        profile.putAddress("level", "X", new AddressEntry(1, "verified"));
        profile.putZoneMapping(0, new ZoneMapping("GHZ", "ghz"));

        String json = mapper.writeValueAsString(profile);
        assertTrue("JSON contains 'profile' key", json.contains("\"profile\""));
        assertTrue("JSON contains 'addresses' key", json.contains("\"addresses\""));
        assertTrue("JSON contains 'zones' key", json.contains("\"zones\""));
        assertTrue("JSON contains 'value' key", json.contains("\"value\""));
        assertTrue("JSON contains 'confidence' key", json.contains("\"confidence\""));
        assertTrue("JSON contains 'behaviorMapping' key", json.contains("\"behaviorMapping\""));
    }

    // ========================================
    // Default/null metadata
    // ========================================

    @Test
    public void testDefaultMetadata_IsNull() {
        RomProfile profile = new RomProfile();
        assertNull("default metadata is null", profile.getMetadata());
    }

    // ========================================
    // Zone map key serialization (integer keys)
    // ========================================

    @Test
    public void testZoneMapKeys_SerializeAsIntegerStrings() throws Exception {
        RomProfile profile = new RomProfile();
        profile.putZoneMapping(7, new ZoneMapping("MCZ", "mcz"));

        String json = mapper.writeValueAsString(profile);
        // Jackson serializes Map<Integer,...> keys as strings in JSON
        assertTrue("zone key 7 present in JSON", json.contains("\"7\""));

        RomProfile restored = mapper.readValue(json, RomProfile.class);
        ZoneMapping mcz = restored.getZoneMapping(7);
        assertNotNull("zone 7 survives round-trip", mcz);
        assertEquals("MCZ", mcz.name());
    }
}
