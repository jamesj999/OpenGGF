package uk.co.jamesj999.sonic.sprites.managers;

import org.junit.Test;
import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1GameModule;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2GameModule;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSpriteManagerCollisionOrder {

    @Test
    public void testSonic1UsesPostMovementSolidPass() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestPlayableSprite sprite = new TestPlayableSprite();

        assertTrue(SpriteManager.requiresPostMovementSolidPass(sprite));
    }

    @Test
    public void testSonic2DoesNotUsePostMovementSolidPass() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestPlayableSprite sprite = new TestPlayableSprite();

        assertFalse(SpriteManager.requiresPostMovementSolidPass(sprite));
    }

    @Test
    public void testNullSpriteDoesNotUsePostMovementSolidPass() {
        assertFalse(SpriteManager.requiresPostMovementSolidPass(null));
        assertFalse(SpriteManager.shouldRunPostMovementSolidPass(null));
    }

    @Test
    public void testSonic1PostMovementPassRunsOnlyWhenAirborne() {
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        TestPlayableSprite sprite = new TestPlayableSprite();

        sprite.setAir(false);
        assertFalse(SpriteManager.shouldRunPostMovementSolidPass(sprite));

        sprite.setAir(true);
        assertTrue(SpriteManager.shouldRunPostMovementSolidPass(sprite));
    }

    @Test
    public void testSonic2PostMovementPassNeverRuns() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        TestPlayableSprite sprite = new TestPlayableSprite();
        sprite.setAir(true);

        assertFalse(SpriteManager.shouldRunPostMovementSolidPass(sprite));
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite() {
            super("TEST", (short) 0, (short) 0);
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 0;
            runDecel = 0;
            friction = 0;
            max = 0;
            jump = 0;
            angle = 0;
            slopeRunning = 0;
            slopeRollingDown = 0;
            slopeRollingUp = 0;
            rollDecel = 0;
            minStartRollSpeed = 0;
            minRollSpeed = 0;
            maxRoll = 0;
            rollHeight = 0;
            runHeight = 0;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override
        public void draw() {
            // No-op for tests.
        }
    }
}
