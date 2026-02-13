package uk.co.jamesj999.sonic.game.profile;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Tests for {@link ProfileGenerator} - utility that creates {@link RomProfile}
 * instances from flat offset maps and converts to resolver-keyed format.
 */
public class TestProfileGenerator {

    // ========================================
    // 1. categorize() - prefix-based routing
    // ========================================

    @Test
    public void testCategorize_ArtPrefixes() {
        assertEquals("ART_ prefix", "art", ProfileGenerator.categorize("ART_NEM_MONITOR_ADDR"));
        assertEquals("MAP_ prefix", "art", ProfileGenerator.categorize("MAP_SONIC_ADDR"));
        assertEquals("DPLC_ prefix", "art", ProfileGenerator.categorize("DPLC_SONIC_ADDR"));
    }

    @Test
    public void testCategorize_AnimationPrefixes() {
        assertEquals("ANIM_PAT_ prefix", "animation", ProfileGenerator.categorize("ANIM_PAT_EHZ_ADDR"));
        assertEquals("ANI_ prefix", "animation", ProfileGenerator.categorize("ANI_SONIC_WALK"));
    }

    @Test
    public void testCategorize_CharacterAnimArt() {
        assertEquals("SONIC_ANIM_ prefix", "art", ProfileGenerator.categorize("SONIC_ANIM_SCRIPT_ADDR"));
        assertEquals("TAILS_ANIM_ prefix", "art", ProfileGenerator.categorize("TAILS_ANIM_SCRIPT_ADDR"));
    }

    @Test
    public void testCategorize_LevelPrefixes() {
        assertEquals("LEVEL_ prefix", "level", ProfileGenerator.categorize("LEVEL_LAYOUT_DIR_ADDR_LOC"));
        assertEquals("DEFAULT_LEVEL_ prefix", "level", ProfileGenerator.categorize("DEFAULT_LEVEL_SIZE"));
        assertEquals("BG_SCROLL_ prefix", "level", ProfileGenerator.categorize("BG_SCROLL_FLAGS_ADDR"));
        assertEquals("RING_ prefix", "level", ProfileGenerator.categorize("RING_LAYOUT_INDEX_ADDR"));
        assertEquals("START_LOC_ prefix", "level", ProfileGenerator.categorize("START_LOC_ARRAY_ADDR"));
    }

    @Test
    public void testCategorize_CollisionPrefixes() {
        assertEquals("COLLISION_ prefix", "collision", ProfileGenerator.categorize("COLLISION_LAYOUT_DIR_ADDR"));
        assertEquals("SOLID_ prefix", "collision", ProfileGenerator.categorize("SOLID_INDEXES_ADDR"));
        assertEquals("ALT_COLLISION_ prefix", "collision", ProfileGenerator.categorize("ALT_COLLISION_INDEX_ADDR"));
    }

    @Test
    public void testCategorize_AudioPrefixes() {
        assertEquals("MUSIC_ prefix", "audio", ProfileGenerator.categorize("MUSIC_PLAYLIST_ADDR"));
        assertEquals("SFX_ prefix", "audio", ProfileGenerator.categorize("SFX_PTR_TABLE_ADDR"));
        assertEquals("SOUND_ prefix", "audio", ProfileGenerator.categorize("SOUND_PRIORITIES_ADDR"));
        assertEquals("DAC_ prefix", "audio", ProfileGenerator.categorize("DAC_DRIVER_ADDR"));
        assertEquals("Z80_ prefix", "audio", ProfileGenerator.categorize("Z80_DRIVER_ADDR"));
        assertEquals("PSG_ prefix", "audio", ProfileGenerator.categorize("PSG_ENV_PTR_TABLE_ADDR"));
        assertEquals("SPEED_UP_ prefix", "audio", ProfileGenerator.categorize("SPEED_UP_INDEX_ADDR"));
        assertEquals("SEGA_SOUND_ prefix", "audio", ProfileGenerator.categorize("SEGA_SOUND_ADDR"));
    }

    @Test
    public void testCategorize_PalettePrefixes() {
        assertEquals("PAL_ prefix", "palette", ProfileGenerator.categorize("PAL_EHZ_ADDR"));
        assertEquals("CYCLING_PAL_ prefix", "palette", ProfileGenerator.categorize("CYCLING_PAL_EHZ_ARZ_WATER_ADDR"));
    }

    @Test
    public void testCategorize_PaletteContains() {
        assertEquals("contains PALETTE", "palette", ProfileGenerator.categorize("SONIC_PALETTE_ADDR"));
        assertEquals("contains PALETTE mid-name", "palette", ProfileGenerator.categorize("UNDERWATER_PALETTE_TABLE_ADDR"));
    }

    @Test
    public void testCategorize_ObjectPrefixes() {
        assertEquals("OBJECT_ prefix", "objects", ProfileGenerator.categorize("OBJECT_LAYOUT_INDEX_ADDR"));
        assertEquals("OBJ_POS_ prefix", "objects", ProfileGenerator.categorize("OBJ_POS_INDEX_ADDR"));
        assertEquals("TOUCH_ prefix", "objects", ProfileGenerator.categorize("TOUCH_RESPONSE_TABLE_ADDR"));
    }

    @Test
    public void testCategorize_Misc() {
        assertEquals("unknown prefix", "misc", ProfileGenerator.categorize("SOMETHING_ELSE_ADDR"));
        assertEquals("empty string", "misc", ProfileGenerator.categorize(""));
    }

    // ========================================
    // 2. generateFromOffsets() - profile creation
    // ========================================

    @Test
    public void testGenerateFromOffsetMap() {
        Map<String, Integer> offsets = new LinkedHashMap<>();
        offsets.put("ART_NEM_MONITOR_ADDR", 0x40D90);
        offsets.put("LEVEL_LAYOUT_DIR_ADDR_LOC", 0x42594);
        offsets.put("COLLISION_LAYOUT_DIR_ADDR", 0x44E8C);
        offsets.put("MUSIC_PLAYLIST_ADDR", 0x6E70);
        offsets.put("CYCLING_PAL_EHZ_ARZ_WATER_ADDR", 0x2710);

        ProfileGenerator generator = new ProfileGenerator();
        RomProfile profile = generator.generateFromOffsets("Sonic 2 REV01", "sonic2", "fake", offsets);

        // Metadata
        assertNotNull("metadata present", profile.getMetadata());
        assertEquals("name", "Sonic 2 REV01", profile.getMetadata().name());
        assertEquals("game", "sonic2", profile.getMetadata().game());
        assertEquals("checksum", "fake", profile.getMetadata().checksum());
        assertEquals("generatedBy", "shipped", profile.getMetadata().generatedBy());
        assertTrue("complete", profile.getMetadata().complete());

        // Art categorization
        AddressEntry art = profile.getAddress("art", "ART_NEM_MONITOR_ADDR");
        assertNotNull("art entry present", art);
        assertEquals("art value", 0x40D90, art.value());
        assertEquals("art confidence", "verified", art.confidence());

        // Level categorization
        AddressEntry level = profile.getAddress("level", "LEVEL_LAYOUT_DIR_ADDR_LOC");
        assertNotNull("level entry present", level);
        assertEquals("level value", 0x42594, level.value());
        assertEquals("level confidence", "verified", level.confidence());

        // Collision categorization
        AddressEntry collision = profile.getAddress("collision", "COLLISION_LAYOUT_DIR_ADDR");
        assertNotNull("collision entry present", collision);
        assertEquals("collision value", 0x44E8C, collision.value());

        // Audio categorization
        AddressEntry audio = profile.getAddress("audio", "MUSIC_PLAYLIST_ADDR");
        assertNotNull("audio entry present", audio);
        assertEquals("audio value", 0x6E70, audio.value());

        // Palette categorization
        AddressEntry palette = profile.getAddress("palette", "CYCLING_PAL_EHZ_ARZ_WATER_ADDR");
        assertNotNull("palette entry present", palette);
        assertEquals("palette value", 0x2710, palette.value());

        // Total count
        assertEquals("total address count", 5, profile.addressCount());
    }

    @Test
    public void testGenerateFromOffsets_EmptyMap() {
        ProfileGenerator generator = new ProfileGenerator();
        RomProfile profile = generator.generateFromOffsets("Empty", "sonic2", "none", Map.of());

        assertNotNull("metadata present", profile.getMetadata());
        assertEquals("empty profile has zero addresses", 0, profile.addressCount());
        assertTrue("complete even when empty", profile.getMetadata().complete());
    }

    @Test
    public void testGenerateFromOffsets_AllSameCategory() {
        Map<String, Integer> offsets = Map.of(
                "MUSIC_PTR_TABLE_ADDR", 0x0E42F0,
                "MUSIC_PLAYLIST_ADDR", 0x6E70,
                "MUSIC_FM_VOICE_PTR_ADDR", 0x0E3A00
        );

        ProfileGenerator generator = new ProfileGenerator();
        RomProfile profile = generator.generateFromOffsets("Test", "sonic2", "abc", offsets);

        // All three should be under "audio"
        assertNotNull(profile.getAddress("audio", "MUSIC_PTR_TABLE_ADDR"));
        assertNotNull(profile.getAddress("audio", "MUSIC_PLAYLIST_ADDR"));
        assertNotNull(profile.getAddress("audio", "MUSIC_FM_VOICE_PTR_ADDR"));
        assertEquals("3 addresses in one category", 3, profile.addressCount());
    }

    @Test
    public void testGenerateFromOffsets_MiscCategory() {
        Map<String, Integer> offsets = Map.of("UNKNOWN_THING", 0x1234);

        ProfileGenerator generator = new ProfileGenerator();
        RomProfile profile = generator.generateFromOffsets("Test", "sonic2", "abc", offsets);

        AddressEntry entry = profile.getAddress("misc", "UNKNOWN_THING");
        assertNotNull("misc entry present", entry);
        assertEquals("misc value", 0x1234, entry.value());
    }

    // ========================================
    // 3. toResolverDefaults() - flat to keyed conversion
    // ========================================

    @Test
    public void testToResolverDefaults() {
        Map<String, Integer> offsets = Map.of(
                "ART_SONIC_ADDR", 0x1234,
                "LEVEL_HEADERS_ADDR", 0x5678
        );

        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(offsets);

        assertEquals("art key", Integer.valueOf(0x1234), defaults.get("art.ART_SONIC_ADDR"));
        assertEquals("level key", Integer.valueOf(0x5678), defaults.get("level.LEVEL_HEADERS_ADDR"));
    }

    @Test
    public void testToResolverDefaults_EmptyMap() {
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(Map.of());
        assertTrue("empty input produces empty output", defaults.isEmpty());
    }

    @Test
    public void testToResolverDefaults_AllCategories() {
        Map<String, Integer> offsets = new LinkedHashMap<>();
        offsets.put("ART_SONIC_ADDR", 0x01);
        offsets.put("ANIM_PAT_EHZ_ADDR", 0x02);
        offsets.put("LEVEL_HEADERS_ADDR", 0x03);
        offsets.put("COLLISION_INDEX_ADDR", 0x04);
        offsets.put("MUSIC_PTR_TABLE_ADDR", 0x05);
        offsets.put("PAL_EHZ_ADDR", 0x06);
        offsets.put("OBJECT_LAYOUT_INDEX_ADDR", 0x07);
        offsets.put("RANDOM_ADDR", 0x08);

        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(offsets);

        assertEquals("art", Integer.valueOf(0x01), defaults.get("art.ART_SONIC_ADDR"));
        assertEquals("animation", Integer.valueOf(0x02), defaults.get("animation.ANIM_PAT_EHZ_ADDR"));
        assertEquals("level", Integer.valueOf(0x03), defaults.get("level.LEVEL_HEADERS_ADDR"));
        assertEquals("collision", Integer.valueOf(0x04), defaults.get("collision.COLLISION_INDEX_ADDR"));
        assertEquals("audio", Integer.valueOf(0x05), defaults.get("audio.MUSIC_PTR_TABLE_ADDR"));
        assertEquals("palette", Integer.valueOf(0x06), defaults.get("palette.PAL_EHZ_ADDR"));
        assertEquals("objects", Integer.valueOf(0x07), defaults.get("objects.OBJECT_LAYOUT_INDEX_ADDR"));
        assertEquals("misc", Integer.valueOf(0x08), defaults.get("misc.RANDOM_ADDR"));
        assertEquals("8 entries", 8, defaults.size());
    }

    @Test
    public void testToResolverDefaults_PaletteContains() {
        Map<String, Integer> offsets = Map.of("SONIC_PALETTE_ADDR", 0xABCD);
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(offsets);
        assertEquals("palette by contains", Integer.valueOf(0xABCD), defaults.get("palette.SONIC_PALETTE_ADDR"));
    }

    // ========================================
    // 4. Categorization priority
    // ========================================

    @Test
    public void testCategorize_PrefixPriorityOverContains() {
        // PAL_ prefix should match "palette" before any contains check
        assertEquals("PAL_ wins", "palette", ProfileGenerator.categorize("PAL_PALETTE_THING"));
        // SONIC_ANIM_ should be "art" even though SONIC_PALETTE would be "palette"
        assertEquals("SONIC_ANIM_ is art", "art", ProfileGenerator.categorize("SONIC_ANIM_SCRIPT_ADDR"));
    }

    @Test
    public void testCategorize_CharacterAnimDoesNotMatchPlain() {
        // "SONIC_SOMETHING" without ANIM should not match SONIC_ANIM_ prefix
        // It would contain neither "PALETTE" nor any other prefix, so misc
        assertEquals("SONIC_ without ANIM", "misc", ProfileGenerator.categorize("SONIC_SPEED_ADDR"));
    }

    // ========================================
    // 5. Integration: generateFromOffsets + resolver
    // ========================================

    @Test
    public void testGeneratedProfile_WorksWithResolver() {
        Map<String, Integer> offsets = Map.of(
                "LEVEL_HEADERS_ADDR", 0x42594,
                "MUSIC_PTR_TABLE_ADDR", 0x0E42F0
        );

        ProfileGenerator generator = new ProfileGenerator();
        RomProfile profile = generator.generateFromOffsets("Test", "sonic2", "checksum", offsets);

        // Feed profile into resolver
        RomAddressResolver.resetInstance();
        try {
            RomAddressResolver resolver = RomAddressResolver.getInstance();
            resolver.initialize(profile, Map.of());

            assertEquals("level address from generated profile",
                    0x42594, resolver.getLevelAddress("LEVEL_HEADERS_ADDR"));
            assertEquals("audio address from generated profile",
                    0x0E42F0, resolver.getAudioAddress("MUSIC_PTR_TABLE_ADDR"));

            ResolutionReport report = resolver.getReport();
            assertEquals("2 from profile", 2, report.fromProfile());
        } finally {
            RomAddressResolver.resetInstance();
        }
    }

    @Test
    public void testToResolverDefaults_WorksAsResolverDefaults() {
        Map<String, Integer> offsets = Map.of(
                "LEVEL_HEADERS_ADDR", 0x42594,
                "ART_SONIC_ADDR", 0x1234
        );
        Map<String, Integer> defaults = ProfileGenerator.toResolverDefaults(offsets);

        RomAddressResolver.resetInstance();
        try {
            RomAddressResolver resolver = RomAddressResolver.getInstance();
            resolver.initialize(null, defaults);

            assertEquals("level default", 0x42594, resolver.getLevelAddress("LEVEL_HEADERS_ADDR"));
            assertEquals("art default", 0x1234, resolver.getArtAddress("ART_SONIC_ADDR"));

            ResolutionReport report = resolver.getReport();
            assertEquals("2 from defaults", 2, report.fromDefaults());
        } finally {
            RomAddressResolver.resetInstance();
        }
    }
}
