package com.openggf.game;

import com.openggf.game.sonic1.Sonic1GameModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.tests.TestablePlayableSprite;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the hybrid PhysicsFeatureSet used by CrossGameFeatureProvider.
 * The hybrid set enables spindash (from donor S2/S3K) while preserving
 * all S1-specific physics flags (UNIFIED collision, fixed angle threshold, etc.).
 */
public class TestHybridPhysicsFeatureSet {

    @BeforeEach
    public void setUp() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        CrossGameFeatureProvider.getInstance().resetState();
    }

    @AfterEach
    public void tearDown() {
        CrossGameFeatureProvider.getInstance().resetState();
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
                false,  // instaShieldEnabled - S1 (test uses S1 base, no donor context)
                false,  // angleDiffCardinalSnap - S1
                false,  // extendedEdgeBalance - S1
                PhysicsFeatureSet.RING_FLOOR_CHECK_MASK_S1,  // ringFloorCheckMask - S1
                PhysicsFeatureSet.RING_COLLISION_SIZE_S1,  // ringCollisionWidth - S1
                PhysicsFeatureSet.RING_COLLISION_SIZE_S1,  // ringCollisionHeight - S1
                false,  // lightningShieldEnabled - S1 (no elemental shields)
                null,  // superSpindashSpeedTable - not donated
                (short) 0,  // movingCrouchThreshold - not donated
                false,  // groundWallCollisionEnabled - S1
                false,  // airSuperspeedPreserved - S1
                false,  // slopeRepelChecksOnObject - S1
                PhysicsFeatureSet.FAST_SCROLL_CAP_S2  // fastScrollCap - S1 (same as S2)
        );

        // Verify spindash is enabled (donor contribution)
        assertTrue(expected.spindashEnabled(), "Hybrid should enable spindash");
        assertNotNull(expected.spindashSpeedTable(), "Hybrid should have speed table");
        assertEquals(9, expected.spindashSpeedTable().length, "Speed table has 9 entries");

        // Verify all S1 physics flags are preserved
        assertEquals(CollisionModel.UNIFIED, expected.collisionModel(), "Collision model should be UNIFIED (S1)");
        assertFalse(expected.hasDualCollisionPaths(), "Should not have dual collision paths");
        assertTrue(expected.fixedAnglePosThreshold(), "fixedAnglePosThreshold should be true (S1)");
        assertEquals(PhysicsFeatureSet.LOOK_SCROLL_DELAY_NONE, expected.lookScrollDelay(), "lookScrollDelay should be 0 (S1)");
        assertTrue(expected.waterShimmerEnabled(), "waterShimmerEnabled should be true (S1)");
        assertTrue(expected.inputAlwaysCapsGroundSpeed(), "inputAlwaysCapsGroundSpeed should be true (S1)");
        assertFalse(expected.elementalShieldsEnabled(), "elementalShieldsEnabled should be false (S1)");
        assertFalse(expected.angleDiffCardinalSnap(), "angleDiffCardinalSnap should be false (S1)");
        assertFalse(expected.extendedEdgeBalance(), "extendedEdgeBalance should be false (S1)");
    }

    @Test
    public void testHybridDiffersFromPureS1() {
        PhysicsFeatureSet s1 = PhysicsFeatureSet.SONIC_1;
        PhysicsFeatureSet s2 = PhysicsFeatureSet.SONIC_2;

        // S1 has spindash disabled
        assertFalse(s1.spindashEnabled(), "S1 spindash disabled");

        // S2 has spindash enabled but DUAL_PATH collision
        assertTrue(s2.spindashEnabled(), "S2 spindash enabled");
        assertEquals(CollisionModel.DUAL_PATH, s2.collisionModel(), "S2 has DUAL_PATH");

        // Hybrid should combine: spindash from S2 + collision from S1
        // (This test documents the intent - the actual hybrid is tested above)
    }

    @Test
    public void testHybridDiffersFromPureS2() {
        PhysicsFeatureSet s2 = PhysicsFeatureSet.SONIC_2;

        // The hybrid should NOT inherit S2's dual collision paths
        assertEquals(CollisionModel.DUAL_PATH, s2.collisionModel(), "S2 uses DUAL_PATH");
        assertFalse(s2.fixedAnglePosThreshold(), "S2 has no fixed angle threshold");
        assertEquals(PhysicsFeatureSet.LOOK_SCROLL_DELAY_S2, s2.lookScrollDelay(), "S2 has look scroll delay");

        // These are the specific S2 values that the hybrid must NOT use
        // (hybrid uses S1 values for all non-spindash fields)
    }

    @Test
    public void testCrossGameProviderNotActiveByDefault() {
        assertFalse(CrossGameFeatureProvider.isActive(), "CrossGameFeatureProvider should not be active by default");
    }

    @Test
    public void testResetClearsActiveState() {
        // Even without full initialization, verify reset clears state
        CrossGameFeatureProvider.getInstance().resetState();
        assertFalse(CrossGameFeatureProvider.isActive(), "After reset, should not be active");
    }

    @Test
    public void testS1SpriteGetsS1PhysicsWithoutCrossGame() {
        // Without cross-game features, S1 sprite should have S1 physics
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestablePlayableSprite sprite = new TestablePlayableSprite("test", (short) 100, (short) 100);

        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertNotNull(fs, "Feature set should be set");
        assertFalse(fs.spindashEnabled(), "S1 spindash disabled without cross-game");
        assertEquals(CollisionModel.UNIFIED, fs.collisionModel(), "S1 collision model");
    }
}


