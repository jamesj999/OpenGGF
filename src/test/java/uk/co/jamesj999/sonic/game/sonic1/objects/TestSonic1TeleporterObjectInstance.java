package uk.co.jamesj999.sonic.game.sonic1.objects;

import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1AnimationIds;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1TeleporterObjectInstance {

    @Test
    public void holdsRollAnimationWhileTeleportingAndClearsOnRelease() {
        Sonic1TeleporterObjectInstance teleporter = createTeleporter(0x00, 0, 0x300, 0x300);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);

        // Capture on first update.
        teleporter.update(1, player);
        assertTrue(player.isObjectControlled());
        assertTrue(player.isControlLocked());
        assertEquals(Sonic1AnimationIds.ROLL, player.getForcedAnimationId());

        // Continue until release; roll animation must remain forced during transport.
        int frame = 2;
        while (player.isObjectControlled() && frame < 800) {
            teleporter.update(frame, player);
            if (player.isObjectControlled()) {
                assertEquals(Sonic1AnimationIds.ROLL, player.getForcedAnimationId());
            }
            frame++;
        }

        assertFalse("teleporter should release within expected frame budget", player.isObjectControlled());
        assertFalse(player.isControlLocked());
        assertEquals(-1, player.getForcedAnimationId());
    }

    @Test
    public void unloadWhileActiveReleasesControlAndForcedAnimation() {
        Sonic1TeleporterObjectInstance teleporter = createTeleporter(0x00, 0, 0x300, 0x300);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);

        teleporter.update(1, player);
        assertTrue(player.isObjectControlled());
        assertEquals(Sonic1AnimationIds.ROLL, player.getForcedAnimationId());

        teleporter.onUnload();

        assertFalse(player.isObjectControlled());
        assertFalse(player.isControlLocked());
        assertEquals(-1, player.getForcedAnimationId());
    }

    private static Sonic1TeleporterObjectInstance createTeleporter(int subtype, int renderFlags, int x, int y) {
        return new Sonic1TeleporterObjectInstance(
                new ObjectSpawn(x, y, 0x72, subtype, renderFlags, false, 0));
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite() {
            super("TEST", (short) 0, (short) 0);
            setWidth(20);
            setHeight(38);
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
            standXRadius = 9;
            standYRadius = 19;
            rollXRadius = 7;
            rollYRadius = 14;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override
        public void draw() {
            // No-op
        }
    }
}
