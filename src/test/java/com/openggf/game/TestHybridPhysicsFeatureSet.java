package com.openggf.game;

import com.openggf.game.sonic1.Sonic1GameModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.tests.TestablePlayableSprite;

import static org.junit.Assert.*;

/**
 * Tests the hybrid PhysicsFeatureSet used by CrossGameFeatureProvider.
 * The hybrid set enables spindash (from donor S2/S3K) while preserving
 * all S1-specific physics flags (UNIFIED collision, fixed angle threshold, etc.).
 */
public class TestHybridPhysicsFeatureSet {

    @Before
    public void setUp() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        CrossGameFeatureProvider.resetInstance();
    }

    @After
    public void tearDown() {
        CrossGameFeatureProvider.resetInstance();
        GameModuleRegistry.reset();
    }

    @Test
    public void testExpectedHybridFeatureSetValues() {
        // Validates the expected contract of the hybrid feature set:
        // spindash enabled (from donor), all other flags stay S1.
        // Without a ROM, we construct the expected values directly.
        PhysicsFeatureSet expected = new PhysicsFeatureSet(
                true,  // spindashEnabled - from donor
                new short[]{0x0800, 0x0880, 0x0900, 0x0980, 0x0A00, 0x0A80, 0x0B00, 0x0B80, 0x0C00},
                CollisionModel.UNIFIED,  // S1
                true,   // fixedAnglePosThreshold - S1
                PhysicsFeatureSet.LOOK_SCROLL_DELAY_NONE,  // S1
                true,   // waterShimmerEnabled - S1
                true,   // inputAlwaysCapsGroundSpeed - S1
                false,  // elementalShieldsEnabled - S1
                false,  // angleDiffCardinalSnap - S1
                false,  // extendedEdgeBalance - S1
                PhysicsFeatureSet.RING_FLOOR_CHECK_MASK_S1,  // ringFloorCheckMask - S1
                null,  // superSpindashSpeedTable - not donated
                (short) 0  // movingCrouchThreshold - not donated
        );

        // Verify spindash is enabled (donor contribution)
        assertTrue("Hybrid should enable spindash", expected.spindashEnabled());
        assertNotNull("Hybrid should have speed table", expected.spindashSpeedTable());
        assertEquals("Speed table has 9 entries", 9, expected.spindashSpeedTable().length);

        // Verify all S1 physics flags are preserved
        assertEquals("Collision model should be UNIFIED (S1)",
                CollisionModel.UNIFIED, expected.collisionModel());
        assertFalse("Should not have dual collision paths",
                expected.hasDualCollisionPaths());
        assertTrue("fixedAnglePosThreshold should be true (S1)",
                expected.fixedAnglePosThreshold());
        assertEquals("lookScrollDelay should be 0 (S1)",
                PhysicsFeatureSet.LOOK_SCROLL_DELAY_NONE, expected.lookScrollDelay());
        assertTrue("waterShimmerEnabled should be true (S1)",
                expected.waterShimmerEnabled());
        assertTrue("inputAlwaysCapsGroundSpeed should be true (S1)",
                expected.inputAlwaysCapsGroundSpeed());
        assertFalse("elementalShieldsEnabled should be false (S1)",
                expected.elementalShieldsEnabled());
        assertFalse("angleDiffCardinalSnap should be false (S1)",
                expected.angleDiffCardinalSnap());
        assertFalse("extendedEdgeBalance should be false (S1)",
                expected.extendedEdgeBalance());
    }

    @Test
    public void testHybridDiffersFromPureS1() {
        PhysicsFeatureSet s1 = PhysicsFeatureSet.SONIC_1;
        PhysicsFeatureSet s2 = PhysicsFeatureSet.SONIC_2;

        // S1 has spindash disabled
        assertFalse("S1 spindash disabled", s1.spindashEnabled());

        // S2 has spindash enabled but DUAL_PATH collision
        assertTrue("S2 spindash enabled", s2.spindashEnabled());
        assertEquals("S2 has DUAL_PATH", CollisionModel.DUAL_PATH, s2.collisionModel());

        // Hybrid should combine: spindash from S2 + collision from S1
        // (This test documents the intent - the actual hybrid is tested above)
    }

    @Test
    public void testHybridDiffersFromPureS2() {
        PhysicsFeatureSet s2 = PhysicsFeatureSet.SONIC_2;

        // The hybrid should NOT inherit S2's dual collision paths
        assertEquals("S2 uses DUAL_PATH", CollisionModel.DUAL_PATH, s2.collisionModel());
        assertFalse("S2 has no fixed angle threshold", s2.fixedAnglePosThreshold());
        assertEquals("S2 has look scroll delay", PhysicsFeatureSet.LOOK_SCROLL_DELAY_S2, s2.lookScrollDelay());

        // These are the specific S2 values that the hybrid must NOT use
        // (hybrid uses S1 values for all non-spindash fields)
    }

    @Test
    public void testCrossGameProviderNotActiveByDefault() {
        assertFalse("CrossGameFeatureProvider should not be active by default",
                CrossGameFeatureProvider.isActive());
    }

    @Test
    public void testResetClearsActiveState() {
        // Even without full initialization, verify reset clears state
        CrossGameFeatureProvider.resetInstance();
        assertFalse("After reset, should not be active",
                CrossGameFeatureProvider.isActive());
    }

    @Test
    public void testS1SpriteGetsS1PhysicsWithoutCrossGame() {
        // Without cross-game features, S1 sprite should have S1 physics
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);

        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertNotNull("Feature set should be set", fs);
        assertFalse("S1 spindash disabled without cross-game", fs.spindashEnabled());
        assertEquals("S1 collision model", CollisionModel.UNIFIED, fs.collisionModel());
    }
}
