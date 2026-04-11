package com.openggf.configuration;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
