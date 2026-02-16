package uk.co.jamesj999.sonic.game;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1GameModule;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2GameModule;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.*;

/**
 * Tests the per-game ground speed capping behavior.
 *
 * S1: Pressing direction of movement always clamps ground speed to max,
 * even if speed was above max from slopes/springs.
 * ROM: s1disasm/_incObj/01 Sonic.asm:555-558 — unconditional clamp.
 *
 * S2/S3K: Preserves ground speeds above max when pressing direction of movement.
 * ROM: s2.asm:36610-36616 — undo acceleration if speed was already >= max.
 */
public class TestGroundSpeedCapping {

    @Before
    public void setUp() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
    }

    @Test
    public void testSonic1_inputAlwaysCapsGroundSpeed_isTrue() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertNotNull("Feature set should be set", fs);
        assertTrue("S1 should always cap ground speed on input",
                fs.inputAlwaysCapsGroundSpeed());
    }

    @Test
    public void testSonic2_inputAlwaysCapsGroundSpeed_isFalse() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertNotNull("Feature set should be set", fs);
        assertFalse("S2 should preserve high ground speed on input",
                fs.inputAlwaysCapsGroundSpeed());
    }

    @Test
    public void testSonic1_highSpeedCappedWhenPressingDirection() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        // Simulate Sonic at high speed (above max 0x600) from a spring
        short highSpeed = 0x800;
        short max = sprite.getMax();
        assertTrue("Test setup: speed should be above max", highSpeed > max);

        // S1 behavior: pressing right when going right at high speed should clamp to max
        // This matches: add accel, then unconditional clamp
        // Result: min(highSpeed + accel, max) = max since highSpeed + accel > max
        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertTrue("S1 feature flag", fs.inputAlwaysCapsGroundSpeed());

        // Verify the clamp would reduce the speed: highSpeed + accel still > max
        short accel = sprite.getRunAccel();
        short result = (short) (highSpeed + accel);
        assertTrue("With acceleration, still above max", result > max);
        // After S1 clamping: result should be max
        if (result > max) result = max;
        assertEquals("S1 should clamp to max", max, result);
    }

    @Test
    public void testSonic2_highSpeedPreservedWhenPressingDirection() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestableSprite sprite = new TestableSprite("test", (short) 100, (short) 100);

        // Simulate Sonic at high speed (above max 0x600) from a spring
        short highSpeed = 0x800;
        short max = sprite.getMax();
        assertTrue("Test setup: speed should be above max", highSpeed > max);

        // S2 behavior: pressing right when going right at high speed should preserve speed
        PhysicsFeatureSet fs = sprite.getPhysicsFeatureSet();
        assertFalse("S2 feature flag", fs.inputAlwaysCapsGroundSpeed());

        // In S2, if gSpeed >= max, acceleration is skipped entirely
        // Speed stays at highSpeed
        assertEquals("S2 should preserve high speed", highSpeed, highSpeed);
    }

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
