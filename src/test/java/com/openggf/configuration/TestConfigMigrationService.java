package com.openggf.configuration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_APOSTROPHE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F8;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_WORLD_1;

class TestConfigMigrationService {

    @Test
    void migrateConfig_convertsLegacyAwtArrowAndActionKeys() {
        Map<String, Object> config = new HashMap<>();
        config.put(SonicConfiguration.UP.name(), 38);
        config.put(SonicConfiguration.DOWN.name(), 40);
        config.put(SonicConfiguration.LEFT.name(), 37);
        config.put(SonicConfiguration.RIGHT.name(), 39);
        config.put(SonicConfiguration.JUMP.name(), 32);

        ConfigMigrationService service = new ConfigMigrationService();

        assertTrue(service.detectAwtKeyCodes(config));
        service.migrateConfig(config);

        assertEquals(265, config.get(SonicConfiguration.UP.name()));
        assertEquals(264, config.get(SonicConfiguration.DOWN.name()));
        assertEquals(263, config.get(SonicConfiguration.LEFT.name()));
        assertEquals(262, config.get(SonicConfiguration.RIGHT.name()));
        assertEquals(32, config.get(SonicConfiguration.JUMP.name()));
    }

    @Test
    void migrateDeprecatedS1PreviewCoordLogKey_rewritesOldDefaultBinding() {
        Map<String, Object> config = new HashMap<>();
        config.put(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY.name(), GLFW_KEY_WORLD_1);

        ConfigMigrationService service = new ConfigMigrationService();

        assertTrue(service.migrateDeprecatedS1PreviewCoordLogKey(config));
        assertEquals(GLFW_KEY_APOSTROPHE,
                config.get(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY.name()));

        config.put(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY.name(), GLFW_KEY_F8);
        assertTrue(service.migrateDeprecatedS1PreviewCoordLogKey(config));
        assertEquals(GLFW_KEY_APOSTROPHE,
                config.get(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY.name()));
    }

    @Test
    void migrateDeprecatedS1PreviewCoordLogKey_preservesCustomBinding() {
        Map<String, Object> config = new HashMap<>();
        config.put(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY.name(), 83);

        ConfigMigrationService service = new ConfigMigrationService();

        assertFalse(service.migrateDeprecatedS1PreviewCoordLogKey(config));
        assertEquals(83, config.get(SonicConfiguration.CROSS_GAME_S1_DATA_SELECT_IMAGE_COORD_LOG_KEY.name()));
    }
}
