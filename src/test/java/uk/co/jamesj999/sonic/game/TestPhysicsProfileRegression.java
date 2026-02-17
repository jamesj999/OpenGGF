package uk.co.jamesj999.sonic.game;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2GameModule;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.*;

/**
 * Regression test: verify that getter values under the new PhysicsModifiers
 * path match the original hardcoded Sonic 2 values with/without water/shoes.
 */
public class TestPhysicsProfileRegression {

    private TestableSprite sprite;

    @Before
    public void setUp() {
        // Ensure Sonic 2 module is active (default)
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        sprite = new TestableSprite("test", (short) 100, (short) 100);
    }

    // ========================================
    // Normal (no water, no speed shoes)
    // ========================================

    @Test
    public void testNormal_RunAccel() {
        assertEquals(12, sprite.getRunAccel());
    }

    @Test
    public void testNormal_RunDecel() {
        assertEquals(128, sprite.getRunDecel());
    }

    @Test
    public void testNormal_Friction() {
        assertEquals(12, sprite.getFriction());
    }

    @Test
    public void testNormal_Max() {
        assertEquals(0x600, sprite.getMax());
    }

    @Test
    public void testNormal_Jump() {
        assertEquals(0x680, sprite.getJump());
    }

    // ========================================
    // Water
    // ========================================

    @Test
    public void testWater_RunAccel() {
        sprite.setInWater(true);
        assertEquals(6, sprite.getRunAccel());
    }

    @Test
    public void testWater_RunDecel() {
        sprite.setInWater(true);
        assertEquals(64, sprite.getRunDecel());
    }

    @Test
    public void testWater_Friction() {
        sprite.setInWater(true);
        assertEquals(6, sprite.getFriction());
    }

    @Test
    public void testWater_Max() {
        sprite.setInWater(true);
        assertEquals(0x300, sprite.getMax());
    }

    @Test
    public void testWater_Jump() {
        sprite.setInWater(true);
        assertEquals(0x380, sprite.getJump());
    }

    // ========================================
    // Speed shoes
    // ========================================

    @Test
    public void testSpeedShoes_RunAccel() {
        sprite.setTestSpeedShoes(true);
        assertEquals(24, sprite.getRunAccel());
    }

    @Test
    public void testSpeedShoes_RunDecel_Unchanged() {
        sprite.setTestSpeedShoes(true);
        // Speed shoes don't affect decel
        assertEquals(128, sprite.getRunDecel());
    }

    @Test
    public void testSpeedShoes_Friction() {
        sprite.setTestSpeedShoes(true);
        assertEquals(24, sprite.getFriction());
    }

    @Test
    public void testSpeedShoes_Max() {
        sprite.setTestSpeedShoes(true);
        assertEquals(0xC00, sprite.getMax());
    }

    // ========================================
    // Water + speed shoes combined
    // ========================================

    @Test
    public void testWaterAndShoes_RunAccel() {
        sprite.setInWater(true);
        sprite.setTestSpeedShoes(true);
        // Water halves (12->6), then shoes double (6->12)
        assertEquals(12, sprite.getRunAccel());
    }

    @Test
    public void testWaterAndShoes_Max() {
        sprite.setInWater(true);
        sprite.setTestSpeedShoes(true);
        // Water halves (0x600->0x300), then shoes double (0x300->0x600)
        assertEquals(0x600, sprite.getMax());
    }

    // ========================================
    // Provider accessors
    // ========================================

    @Test
    public void testPhysicsFeatureSet_IsSet() {
        assertNotNull("Feature set should be populated", sprite.getPhysicsFeatureSet());
        assertTrue("S2 spindash enabled", sprite.getPhysicsFeatureSet().spindashEnabled());
    }

    @Test
    public void testPhysicsModifiers_IsSet() {
        assertNotNull("Modifiers should be populated", sprite.getPhysicsModifiers());
        assertEquals("Water gravity reduction", 0x28, sprite.getPhysicsModifiers().waterGravityReduction());
        assertEquals("Water hurt gravity reduction", 0x20, sprite.getPhysicsModifiers().waterHurtGravityReduction());
    }

    /**
     * Minimal test subclass.
     */
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

        public void setTestSpeedShoes(boolean shoes) {
            this.speedShoes = shoes;
        }
    }
}
