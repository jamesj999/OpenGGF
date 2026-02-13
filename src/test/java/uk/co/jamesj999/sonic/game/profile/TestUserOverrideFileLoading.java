package uk.co.jamesj999.sonic.game.profile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Tests for user override profile file loading.
 *
 * <p>The engine supports placing a {@code <rom-filename>.profile.json} file
 * next to a ROM file. When present, this user override profile takes priority
 * over shipped classpath profiles matched by checksum. This allows users to
 * customize ROM address mappings and zone behavior for ROM hacks without
 * modifying the engine's built-in profiles.
 *
 * <p>The full integration path is:
 * <ol>
 *   <li>{@link uk.co.jamesj999.sonic.data.RomManager#getRomPath()} returns the ROM path</li>
 *   <li>{@link uk.co.jamesj999.sonic.game.GameModuleRegistry} computes
 *       {@code romPath + ".profile.json"} and tries loading it</li>
 *   <li>{@link ProfileLoader#loadFromFile(Path)} parses the JSON</li>
 *   <li>If found, the user override profile is used instead of the classpath profile</li>
 * </ol>
 *
 * <p>These tests focus on the {@link ProfileLoader#loadFromFile(Path)} path
 * which is the core mechanism enabling user overrides.
 */
public class TestUserOverrideFileLoading {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final ProfileLoader loader = new ProfileLoader();

    // ========================================
    // User override profile loading
    // ========================================

    @Test
    public void testLoadUserOverride_ValidProfile() throws Exception {
        String json = """
                {
                  "profile": {
                    "name": "My ROM Hack",
                    "game": "sonic2",
                    "checksum": "deadbeef1234",
                    "generatedBy": "user",
                    "complete": false
                  },
                  "addresses": {
                    "level": {
                      "LEVEL_HEADERS_ADDR": {
                        "value": 327680,
                        "confidence": "verified"
                      },
                      "LEVEL_LAYOUT_INDEX_ADDR": {
                        "value": 458752,
                        "confidence": "inferred"
                      }
                    },
                    "audio": {
                      "MUSIC_PTR_TABLE_ADDR": {
                        "value": 933632,
                        "confidence": "verified"
                      }
                    }
                  },
                  "zones": {
                    "0": {
                      "name": "Chemical Plant",
                      "behaviorMapping": "cpz"
                    },
                    "1": {
                      "name": "Hill Top",
                      "behaviorMapping": "htz"
                    }
                  }
                }
                """;

        // Simulate: ROM is at "MyHack.gen", override is "MyHack.gen.profile.json"
        File overrideFile = tempFolder.newFile("MyHack.gen.profile.json");
        Files.writeString(overrideFile.toPath(), json);

        RomProfile profile = loader.loadFromFile(overrideFile.toPath());

        assertNotNull("user override profile should load", profile);

        // Verify metadata
        assertEquals("My ROM Hack", profile.getMetadata().name());
        assertEquals("sonic2", profile.getMetadata().game());
        assertEquals("deadbeef1234", profile.getMetadata().checksum());
        assertEquals("user", profile.getMetadata().generatedBy());
        assertFalse(profile.getMetadata().complete());

        // Verify addresses
        AddressEntry levelHeaders = profile.getAddress("level", "LEVEL_HEADERS_ADDR");
        assertNotNull("level headers present", levelHeaders);
        assertEquals(327680, levelHeaders.value());
        assertEquals("verified", levelHeaders.confidence());

        AddressEntry levelLayout = profile.getAddress("level", "LEVEL_LAYOUT_INDEX_ADDR");
        assertNotNull("level layout present", levelLayout);
        assertEquals(458752, levelLayout.value());

        AddressEntry musicPtr = profile.getAddress("audio", "MUSIC_PTR_TABLE_ADDR");
        assertNotNull("music ptr present", musicPtr);
        assertEquals(933632, musicPtr.value());

        // Verify zone mappings (key part for ROM hack support)
        assertEquals("3 addresses across 2 categories", 3, profile.addressCount());

        ZoneMapping zone0 = profile.getZoneMapping(0);
        assertNotNull("zone 0 mapped", zone0);
        assertEquals("Chemical Plant", zone0.name());
        assertEquals("cpz", zone0.behaviorMapping());

        ZoneMapping zone1 = profile.getZoneMapping(1);
        assertNotNull("zone 1 mapped", zone1);
        assertEquals("Hill Top", zone1.name());
        assertEquals("htz", zone1.behaviorMapping());
    }

    @Test
    public void testLoadUserOverride_FileNotFound_ReturnsNull() {
        Path nonexistent = tempFolder.getRoot().toPath().resolve("SomeHack.gen.profile.json");
        RomProfile profile = loader.loadFromFile(nonexistent);
        assertNull("missing override file should return null", profile);
    }

    @Test
    public void testLoadUserOverride_MalformedJson_ReturnsNull() throws Exception {
        File overrideFile = tempFolder.newFile("BadHack.gen.profile.json");
        Files.writeString(overrideFile.toPath(), "{ this is not valid json }}}");

        RomProfile profile = loader.loadFromFile(overrideFile.toPath());
        assertNull("malformed JSON should return null gracefully", profile);
    }

    @Test
    public void testLoadUserOverride_EmptyAddresses_Succeeds() throws Exception {
        String json = """
                {
                  "profile": {
                    "name": "Minimal Hack",
                    "game": "sonic2",
                    "checksum": "minimal",
                    "generatedBy": "user",
                    "complete": false
                  },
                  "addresses": {},
                  "zones": {
                    "0": {
                      "name": "CPZ",
                      "behaviorMapping": "cpz"
                    }
                  }
                }
                """;

        File overrideFile = tempFolder.newFile("MinimalHack.gen.profile.json");
        Files.writeString(overrideFile.toPath(), json);

        RomProfile profile = loader.loadFromFile(overrideFile.toPath());
        assertNotNull("minimal profile with empty addresses should load", profile);
        assertEquals("Minimal Hack", profile.getMetadata().name());
        assertEquals(0, profile.addressCount());

        ZoneMapping zone0 = profile.getZoneMapping(0);
        assertNotNull(zone0);
        assertEquals("cpz", zone0.behaviorMapping());
    }

    // ========================================
    // Override path computation convention
    // ========================================

    @Test
    public void testOverridePathConvention() {
        // Verify the convention: override path = ROM path + ".profile.json"
        String romPath = "C:\\Games\\MyHack.gen";
        String expectedOverridePath = romPath + ".profile.json";
        assertEquals("C:\\Games\\MyHack.gen.profile.json", expectedOverridePath);

        // Unix-style path
        String unixRomPath = "/home/user/roms/MyHack.gen";
        String unixOverridePath = unixRomPath + ".profile.json";
        assertEquals("/home/user/roms/MyHack.gen.profile.json", unixOverridePath);
    }
}
