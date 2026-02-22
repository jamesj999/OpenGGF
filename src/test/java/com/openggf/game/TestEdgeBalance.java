package com.openggf.game;

import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.tests.TestablePlayableSprite;

import static org.junit.Assert.*;

/**
 * Tests edge balance feature gating per game module.
 *
 * S1: Simple balance - single animation state, always forces facing toward edge,
 *     no precarious check. ROM: s1disasm/_incObj/01 Sonic.asm:354-375
 *
 * S2/S3K: Extended balance - 4 states (facing toward/away × safe/precarious),
 *         secondary floor probe for precarious detection.
 *         ROM: s2.asm:36246-36373
 */
public class TestEdgeBalance {

    @Before
    public void setUp() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
    }

    @After
    public void tearDown() {
        GameModuleRegistry.reset();
    }

    @Test
    public void testSonic1_ExtendedEdgeBalanceDisabled() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);

        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertNotNull("Feature set should be set", fs);
        assertFalse("S1 should not have extended edge balance", fs.extendedEdgeBalance());
    }

    @Test
    public void testSonic2_ExtendedEdgeBalanceEnabled() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);

        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertNotNull("Feature set should be set", fs);
        assertTrue("S2 should have extended edge balance", fs.extendedEdgeBalance());
    }

    @Test
    public void testSonic3k_ExtendedEdgeBalanceEnabled() {
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);

        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertNotNull("Feature set should be set", fs);
        assertTrue("S3K should have extended edge balance", fs.extendedEdgeBalance());
    }

    @Test
    public void testSonic1_SingleBalanceState() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);

        // S1 only ever uses balance state 1 (the single balance animation).
        // Verify that the feature set configuration means S1 will never produce
        // states 2, 3, or 4 (which require extendedEdgeBalance).
        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertFalse("S1 balance mode should be simple (not extended)", fs.extendedEdgeBalance());

        // Balance state starts at 0
        assertEquals("Initial balance state should be 0", 0, sprite.getBalanceState());
        assertFalse("Should not be balancing initially", sprite.isBalancing());
    }

    @Test
    public void testFeatureSetConstants_CorrectValues() {
        // Verify the static constants have correct extendedEdgeBalance values
        assertFalse("SONIC_1 should not have extended edge balance",
                PhysicsFeatureSet.SONIC_1.extendedEdgeBalance());
        assertTrue("SONIC_2 should have extended edge balance",
                PhysicsFeatureSet.SONIC_2.extendedEdgeBalance());
        assertTrue("SONIC_3K should have extended edge balance",
                PhysicsFeatureSet.SONIC_3K.extendedEdgeBalance());
    }
}
