package com.openggf.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.glfw.GLFW.*;

class TestConfigKeyNameResolution {

    private SonicConfigurationService configService;

    @BeforeEach
    void setUp() {
        // Reset singleton to get a fresh instance with defaults applied
        SonicConfigurationService.resetStaticInstance();
        configService = SonicConfigurationService.getInstance();
    }

    @Test
    void getInt_numericInteger_returnsSameValue() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, 81);
        assertEquals(GLFW_KEY_Q, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    }

    @Test
    void getInt_numericString_parsesAsInt() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "81");
        assertEquals(GLFW_KEY_Q, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    }

    @Test
    void getInt_keyNameString_resolvesViaGlfwKeyNameResolver() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "Q");
        assertEquals(GLFW_KEY_Q, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    }

    @Test
    void getInt_keyNameCaseInsensitive() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "space");
        assertEquals(GLFW_KEY_SPACE, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    }

    @Test
    void getInt_keyNameWithGlfwPrefix() {
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "GLFW_KEY_D");
        assertEquals(GLFW_KEY_D, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    }

    @Test
    void getInt_invalidString_fallsBackToDefault() {
        // FRAME_STEP_KEY default is GLFW_KEY_Q (81)
        configService.setConfigValue(SonicConfiguration.FRAME_STEP_KEY, "banana");
        assertEquals(GLFW_KEY_Q, configService.getInt(SonicConfiguration.FRAME_STEP_KEY));
    }

    @Test
    void getInt_invalidString_nonKeyConfig_returnsDefault() {
        // DEBUG_MODE_KEY default is GLFW_KEY_D (68)
        configService.setConfigValue(SonicConfiguration.DEBUG_MODE_KEY, "banana");
        assertEquals(GLFW_KEY_D, configService.getInt(SonicConfiguration.DEBUG_MODE_KEY));
    }
}
