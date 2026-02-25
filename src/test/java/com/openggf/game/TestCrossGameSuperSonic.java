package com.openggf.game;

import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.sprites.playable.SuperStateController;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies cross-game Super Sonic delegation behavior.
 */
public class TestCrossGameSuperSonic {

    @After
    public void tearDown() {
        CrossGameFeatureProvider.resetInstance();
    }

    @Test
    public void vanillaS1HasNoSuperSonic() {
        // When cross-game is NOT active, Sonic1GameModule should return null
        Sonic1GameModule s1Module = new Sonic1GameModule();
        assertNull("Vanilla S1 should not support Super Sonic",
                s1Module.createSuperStateController(null));
    }

    @Test
    public void inactiveProviderReturnsNull() {
        // Provider not initialized — should return null
        CrossGameFeatureProvider provider = CrossGameFeatureProvider.getInstance();
        SuperStateController ctrl = provider.createSuperStateController(null);
        assertNull("Inactive provider should return null", ctrl);
    }

    @Test
    public void crossGameInactiveCheckReturnsNullForS1() {
        // CrossGameFeatureProvider.isActive() is false => S1 module returns null
        assertFalse("Provider should not be active without initialization",
                CrossGameFeatureProvider.isActive());
        Sonic1GameModule s1Module = new Sonic1GameModule();
        assertNull(s1Module.createSuperStateController(null));
    }
}
