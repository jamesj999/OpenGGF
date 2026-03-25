package com.openggf.configuration;

import com.openggf.control.InputHandler;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Service to detect and migrate AWT key codes to GLFW key codes in config files.
 * This handles backwards compatibility when users upgrade from the JOGL build
 * (which used AWT KeyEvent codes) to the LWJGL build (which uses GLFW codes).
 */
public class ConfigMigrationService {

    private static final Logger LOGGER = Logger.getLogger(ConfigMigrationService.class.getName());

    // AWT arrow key codes (used as detection sentinel)
    private static final int AWT_VK_LEFT = 37;
    private static final int AWT_VK_UP = 38;
    private static final int AWT_VK_RIGHT = 39;
    private static final int AWT_VK_DOWN = 40;

    // Key config properties that should be migrated
    private static final List<SonicConfiguration> KEY_CONFIGS = List.of(
        SonicConfiguration.UP,
        SonicConfiguration.DOWN,
        SonicConfiguration.LEFT,
        SonicConfiguration.RIGHT,
        SonicConfiguration.JUMP,
        SonicConfiguration.TEST,
        SonicConfiguration.NEXT_ACT,
        SonicConfiguration.NEXT_ZONE,
        SonicConfiguration.DEBUG_MODE_KEY,
        SonicConfiguration.SPECIAL_STAGE_KEY,
        SonicConfiguration.SPECIAL_STAGE_COMPLETE_KEY,
        SonicConfiguration.SPECIAL_STAGE_FAIL_KEY,
        SonicConfiguration.SPECIAL_STAGE_SPRITE_DEBUG_KEY,
        SonicConfiguration.SPECIAL_STAGE_PLANE_DEBUG_KEY,
        SonicConfiguration.PAUSE_KEY,
        SonicConfiguration.FRAME_STEP_KEY,
        SonicConfiguration.DEBUG_LAST_CHECKPOINT_KEY
    );

    /**
     * Detect whether the config file contains AWT key codes.
     * Uses the arrow keys as a sentinel: AWT uses 37-40, GLFW uses 262-265.
     *
     * @param config The config map to check
     * @return true if AWT key codes are detected
     */
    public boolean detectAwtKeyCodes(Map<String, Object> config) {
        Integer up = getIntValue(config, SonicConfiguration.UP.name());
        Integer down = getIntValue(config, SonicConfiguration.DOWN.name());
        Integer left = getIntValue(config, SonicConfiguration.LEFT.name());
        Integer right = getIntValue(config, SonicConfiguration.RIGHT.name());

        if (up == null || down == null || left == null || right == null) {
            return false; // Missing keys will use defaults
        }

        // AWT pattern: UP=38, DOWN=40, LEFT=37, RIGHT=39
        return up == AWT_VK_UP && down == AWT_VK_DOWN
            && left == AWT_VK_LEFT && right == AWT_VK_RIGHT;
    }

    /**
     * Migrate all key codes in the config from AWT to GLFW format.
     *
     * @param config The config map to migrate (modified in place)
     */
    public void migrateConfig(Map<String, Object> config) {
        LOGGER.info("[ConfigMigration] Migrating config from AWT to GLFW key codes...");
        int migrated = 0;

        for (SonicConfiguration keyConfig : KEY_CONFIGS) {
            Integer awtCode = getIntValue(config, keyConfig.name());
            if (awtCode != null) {
                int glfwCode = InputHandler.awtToGlfw(awtCode);
                if (glfwCode != awtCode) {
                    config.put(keyConfig.name(), glfwCode);
                    LOGGER.info("[ConfigMigration] Migrated " + keyConfig.name() + ": " + awtCode + " -> " + glfwCode);
                    migrated++;
                }
            }
        }

        LOGGER.info("[ConfigMigration] Migrated " + migrated + " key bindings to GLFW codes");
    }

    private Integer getIntValue(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }
}
