package com.openggf.game;

import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.tests.TestablePlayableSprite;

import static org.junit.Assert.*;

/**
 * Tests spindash feature gating per game module.
 * S1 module: spindash should be disabled and never enter spindash state.
 * S2 module: spindash should be enabled.
 */
public class TestSpindashGating {

    @Before
    public void setUp() {
        // Default to Sonic 2
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
    }

    @After
    public void tearDown() {
        GameModuleRegistry.reset();
    }

    @Test
    public void testSonic1Module_SpindashDisabled() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);

        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertNotNull("Feature set should be set", fs);
        assertFalse("S1 spindash should be disabled", fs.spindashEnabled());
        assertNull("S1 no spindash speed table", fs.spindashSpeedTable());
    }

    @Test
    public void testSonic2Module_SpindashEnabled() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);

        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertNotNull("Feature set should be set", fs);
        assertTrue("S2 spindash should be enabled", fs.spindashEnabled());
        assertNotNull("S2 has spindash speed table", fs.spindashSpeedTable());
        assertEquals("S2 speed table has 9 entries", 9, fs.spindashSpeedTable().length);
    }

    @Test
    public void testSonic1Module_SpindashFlagNeverSet() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);

        // Simulate ducking + jump press scenario:
        // In S1, even if the sprite is crouching, the spindash flag should never be set
        // because the feature gate prevents doCheckSpindash() from proceeding.
        assertFalse("Spindash should not be active", sprite.getSpindash());
    }

    @Test
    public void testModuleSwitch_UpdatesFeatureSet() {
        // Start with S2
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);
        assertTrue("Initially S2 spindash", sprite.getPhysicsFeatureSet().spindashEnabled());

        // Switch to S1 and reset state
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        sprite.resetState();
        assertFalse("After switch to S1, spindash disabled", sprite.getPhysicsFeatureSet().spindashEnabled());
    }

}
