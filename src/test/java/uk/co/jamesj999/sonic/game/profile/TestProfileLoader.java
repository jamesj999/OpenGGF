package uk.co.jamesj999.sonic.game.profile;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * Tests for {@link ProfileLoader}: loading profiles from filesystem and classpath.
 */
public class TestProfileLoader {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private final ProfileLoader loader = new ProfileLoader();

    // ========================================
    // Load from file
    // ========================================

    @Test
    public void testLoadFromFile_ValidJson_Success() throws Exception {
        String json = """
                {
                  "profile": {
                    "name": "Sonic 2 REV01",
                    "game": "sonic2",
                    "checksum": "abc123",
                    "generatedBy": "test",
                    "complete": true
                  },
                  "addresses": {
                    "level": {
                      "LEVEL_HEADERS_ADDR": {
                        "value": 271764,
                        "confidence": "verified"
                      }
                    }
                  },
                  "zones": {}
                }
                """;
        File file = tempFolder.newFile("test.profile.json");
        Files.writeString(file.toPath(), json);

        RomProfile profile = loader.loadFromFile(file.toPath());

        assertNotNull("should load valid JSON", profile);
        assertEquals("Sonic 2 REV01", profile.getMetadata().name());
        assertEquals("sonic2", profile.getMetadata().game());
        assertEquals("abc123", profile.getMetadata().checksum());
        assertTrue(profile.getMetadata().complete());

        AddressEntry entry = profile.getAddress("level", "LEVEL_HEADERS_ADDR");
        assertNotNull("should have address entry", entry);
        assertEquals(271764, entry.value());
        assertEquals("verified", entry.confidence());
    }

    @Test
    public void testLoadFromFile_MissingFile_ReturnsNull() {
        Path missing = tempFolder.getRoot().toPath().resolve("nonexistent.json");
        RomProfile profile = loader.loadFromFile(missing);
        assertNull("missing file should return null", profile);
    }

    @Test
    public void testLoadFromFile_InvalidJson_ReturnsNull() throws Exception {
        File file = tempFolder.newFile("bad.json");
        Files.writeString(file.toPath(), "this is not valid json {{{");

        RomProfile profile = loader.loadFromFile(file.toPath());
        assertNull("invalid JSON should return null", profile);
    }

    // ========================================
    // Load from classpath
    // ========================================

    @Test
    public void testLoadFromClasspath_NoMatchingResource_ReturnsNull() {
        RomProfile profile = loader.loadFromClasspath("0000000000000000000000000000000000000000000000000000000000000000");
        assertNull("non-existent classpath resource should return null", profile);
    }
}
