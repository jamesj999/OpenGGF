package uk.co.jamesj999.sonic.game;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1GameModule;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2GameModule;
import uk.co.jamesj999.sonic.game.sonic2.objects.SpringHelper;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.*;

/**
 * Tests collision model differentiation between Sonic 1 (unified) and Sonic 2/3K (dual-path).
 * <p>
 * Sonic 1 locks solid bits to 0x0C/0x0D — setters are no-ops.
 * Sonic 2/3K allows dynamic switching via plane switchers and springs.
 */
public class TestCollisionModel {

    @Before
    public void setUp() {
        // Default to Sonic 2
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
    }

    // ========================================
    // Feature set constants
    // ========================================

    @Test
    public void testSonic1_UnifiedCollision() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_1;
        assertEquals("S1 collision model", CollisionModel.UNIFIED, fs.collisionModel());
        assertFalse("S1 no dual paths", fs.hasDualCollisionPaths());
    }

    @Test
    public void testSonic2_DualPathCollision() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_2;
        assertEquals("S2 collision model", CollisionModel.DUAL_PATH, fs.collisionModel());
        assertTrue("S2 has dual paths", fs.hasDualCollisionPaths());
    }

    @Test
    public void testSonic3K_DualPathCollision() {
        PhysicsFeatureSet fs = PhysicsFeatureSet.SONIC_3K;
        assertEquals("S3K collision model", CollisionModel.DUAL_PATH, fs.collisionModel());
        assertTrue("S3K has dual paths", fs.hasDualCollisionPaths());
    }

    // ========================================
    // Setter guarding (Sonic 1)
    // ========================================

    @Test
    public void testSonic1_SetTopSolidBitIgnored() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        assertEquals("Initial topSolidBit", 0x0C, sprite.getTopSolidBit());
        sprite.setTopSolidBit((byte) 0x0E);
        assertEquals("topSolidBit unchanged in S1", 0x0C, sprite.getTopSolidBit());
    }

    @Test
    public void testSonic1_SetLrbSolidBitIgnored() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        assertEquals("Initial lrbSolidBit", 0x0D, sprite.getLrbSolidBit());
        sprite.setLrbSolidBit((byte) 0x0F);
        assertEquals("lrbSolidBit unchanged in S1", 0x0D, sprite.getLrbSolidBit());
    }

    // ========================================
    // Setter works (Sonic 2)
    // ========================================

    @Test
    public void testSonic2_SetTopSolidBitWorks() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        assertEquals("Initial topSolidBit", 0x0C, sprite.getTopSolidBit());
        sprite.setTopSolidBit((byte) 0x0E);
        assertEquals("topSolidBit changed in S2", 0x0E, sprite.getTopSolidBit());
    }

    @Test
    public void testSonic2_SetLrbSolidBitWorks() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        assertEquals("Initial lrbSolidBit", 0x0D, sprite.getLrbSolidBit());
        sprite.setLrbSolidBit((byte) 0x0F);
        assertEquals("lrbSolidBit changed in S2", 0x0F, sprite.getLrbSolidBit());
    }

    // ========================================
    // Module switch
    // ========================================

    @Test
    public void testModuleSwitch_S2toS1_SettersBecomGuarded() {
        // Start with S2 where setters work
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        sprite.setTopSolidBit((byte) 0x0E);
        assertEquals("S2 allows change", 0x0E, sprite.getTopSolidBit());

        // Switch to S1 and reset state (re-resolves physics)
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        sprite.resetState();

        // Bits should be back to defaults after reset
        assertEquals("After reset, topSolidBit default", 0x0C, sprite.getTopSolidBit());

        // Setter should now be guarded
        sprite.setTopSolidBit((byte) 0x0E);
        assertEquals("S1 guards setter after switch", 0x0C, sprite.getTopSolidBit());
    }

    // ========================================
    // SpringHelper guarding
    // ========================================

    @Test
    public void testSpringHelper_NoOpInSonic1() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        // Subtype 0x08 would normally switch to secondary layer
        SpringHelper.applyCollisionLayerBits(sprite, 0x08);
        assertEquals("topSolidBit unchanged by spring in S1", 0x0C, sprite.getTopSolidBit());
        assertEquals("lrbSolidBit unchanged by spring in S1", 0x0D, sprite.getLrbSolidBit());
    }

    @Test
    public void testSpringHelper_WorksInSonic2() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        // Subtype 0x08 switches to secondary layer
        SpringHelper.applyCollisionLayerBits(sprite, 0x08);
        assertEquals("topSolidBit changed by spring in S2", 0x0E, sprite.getTopSolidBit());
        assertEquals("lrbSolidBit changed by spring in S2", 0x0F, sprite.getLrbSolidBit());
    }

    // ========================================
    // TestableSprite (same pattern as TestSpindashGating)
    // ========================================

    private static class TestableSprite extends AbstractPlayableSprite {
        public TestableSprite(String code, short x, short y) {
            super(code, x, y);
        }

        @Override
        public void draw() {
        }

        @Override
        public void defineSpeeds() {
            runAccel = 12;
            runDecel = 128;
            friction = 12;
            max = 1536;
            jump = 1664;
            slopeRunning = 32;
            slopeRollingDown = 80;
            slopeRollingUp = 20;
            rollDecel = 32;
            minStartRollSpeed = 128;
            minRollSpeed = 128;
            maxRoll = 4096;
            rollHeight = 28;
            runHeight = 38;
            standXRadius = 9;
            standYRadius = 19;
            rollXRadius = 7;
            rollYRadius = 14;
        }

        @Override
        protected void createSensorLines() {
        }
    }
}
