package uk.co.jamesj999.sonic.game.sonic1.objects;

import org.junit.Test;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1RunningDiscObjectInstance {

    @Test
    public void attachesAndEnablesTunnelMode() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x200);
        player.setAir(false);
        player.setGSpeed((short) 0);

        disc.update(1, player);

        assertTrue(player.isStickToConvex());
        assertTrue(player.isTunnelMode());
        assertEquals(0x400, player.getGSpeed());
    }

    @Test
    public void leavingDiscClearsAttachmentFlags() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x200);
        player.setAir(false);

        disc.update(1, player);
        assertTrue(player.isStickToConvex());
        assertTrue(player.isTunnelMode());

        // Outside detection square -> detach.
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);
        disc.update(2, player);

        assertFalse(player.isStickToConvex());
        assertFalse(player.isTunnelMode());
    }

    @Test
    public void airborneInsideRangeKeepsTunnelSuppressionUntilRangeExit() {
        Sonic1RunningDiscObjectInstance disc = createDisc(0x10, 0x200, 0x200);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 0x200);
        player.setCentreY((short) 0x200);
        player.setAir(false);

        disc.update(1, player);
        assertTrue(player.isTunnelMode());

        // In-range but airborne without jump intent: keep attachment.
        player.setAir(true);
        disc.update(2, player);

        assertTrue(player.isTunnelMode());
        assertTrue(player.isStickToConvex());

        // Once actually out of range, attachment flags are cleared.
        player.setCentreX((short) 0x300);
        player.setCentreY((short) 0x300);
        disc.update(3, player);
        assertFalse(player.isTunnelMode());
    }

    private static Sonic1RunningDiscObjectInstance createDisc(int subtype, int x, int y) {
        return new Sonic1RunningDiscObjectInstance(
                new ObjectSpawn(x, y, 0x67, subtype, 0, false, 0));
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
