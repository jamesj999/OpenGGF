package com.openggf.game;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestCrossGameFeatureProviderRefactor {

    @AfterEach
    void cleanup() {
        CrossGameFeatureProvider.getInstance().resetState();
        GameModuleRegistry.reset();
    }

    @Test
    void sameGameDonationIsDisabled() {
        GameModuleRegistry.setCurrent(new com.openggf.game.sonic2.Sonic2GameModule());
        try {
            CrossGameFeatureProvider.getInstance().initialize("s2");
        } catch (Exception e) {
            // ROM not available, but guard should fire before ROM access
        }
        assertFalse(CrossGameFeatureProvider.isActive(),
                "Same-game donation should be disabled");
    }

    @Test
    void hybridFeatureSetReflectsDonorCapabilities() {
        DonorCapabilities s1Caps = new com.openggf.game.sonic1.Sonic1GameModule()
                .getDonorCapabilities();
        assertFalse(s1Caps.hasSpindash());
        assertFalse(s1Caps.hasSuperTransform());
        assertFalse(s1Caps.hasInstaShield());
    }
}
