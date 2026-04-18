package com.openggf.configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonicConfigurationFileBootstrap {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    @TempDir
    Path tempDir;

    @Test
    void ensureConfigFileExists_createsDefaultConfigWhenMissing() throws IOException {
        String originalUserDir = System.getProperty("user.dir");
        Path configPath = tempDir.resolve("config.json");

        try {
            System.setProperty("user.dir", tempDir.toString());
            SonicConfigurationService.resetStaticInstance();

            SonicConfigurationService service = SonicConfigurationService.getInstance();

            assertFalse(Files.exists(configPath));

            service.ensureConfigFileExists();

            assertTrue(Files.exists(configPath), "First startup should materialize config.json");

            Map<String, Object> savedConfig = OBJECT_MAPPER.readValue(configPath.toFile(), MAP_TYPE);
            assertEquals(640, ((Number) savedConfig.get(SonicConfiguration.SCREEN_WIDTH.name())).intValue());
            assertEquals(320, ((Number) savedConfig.get(SonicConfiguration.SCREEN_WIDTH_PIXELS.name())).intValue());
            assertEquals(service.getString(SonicConfiguration.DEFAULT_ROM),
                    savedConfig.get(SonicConfiguration.DEFAULT_ROM.name()));
            assertEquals("Q", savedConfig.get(SonicConfiguration.FRAME_STEP_KEY.name()));
            assertEquals("", savedConfig.get(SonicConfiguration.PLAYBACK_MOVIE_PATH.name()));
            assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.TITLE_SCREEN_ON_STARTUP.name()));
            assertEquals(Boolean.FALSE, savedConfig.get(SonicConfiguration.LEVEL_SELECT_ON_STARTUP.name()));
            assertEquals(Boolean.TRUE, savedConfig.get(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP.name()));
            assertTrue(savedConfig.containsKey(SonicConfiguration.DEBUG_VIEW_ENABLED.name()));
        } finally {
            if (originalUserDir != null) {
                System.setProperty("user.dir", originalUserDir);
            }
            SonicConfigurationService.resetStaticInstance();
        }
    }
}
