package uk.co.jamesj999.sonic.game.sonic1.objects;

import org.junit.Test;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.SolidContact;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1SpikeObjectInstance {

    @Test
    public void spikesHurtDuringInvulnerabilityFrames() {
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x00, 0, false, 0));
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 160);
        player.setCentreY((short) 80);
        player.setInvulnerableFrames(120);
        player.setHurt(true);
        player.setInvincibleFrames(0);

        spikes.onSolidContact(player, new SolidContact(true, false, false, false, false), 1);

        assertTrue(player.hurtOrDeathIgnoringIFramesCalled);
        assertEquals(160, player.lastSourceX);
        assertTrue(player.lastSpikeHit);
        assertFalse(player.lastHadRings);
    }

    @Test
    public void spikesDoNotHurtWhenInvincibilityIsActive() {
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x00, 0, false, 0));
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 160);
        player.setCentreY((short) 80);
        player.setInvincibleFrames(600);

        spikes.onSolidContact(player, new SolidContact(true, false, false, false, false), 1);

        assertFalse(player.hurtOrDeathIgnoringIFramesCalled);
        assertFalse(player.hurtIgnoringIFramesCalled);
    }

    @Test
    public void spikesDisableStickyContactBuffer() {
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x01, 0, false, 0));
        assertFalse(spikes.usesStickyContactBuffer());
    }

    @Test
    public void uprightSpikesIgnoreStandingContactOutsideRomLandingWindow() {
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x00, 0, false, 0));
        TestPlayableSprite player = new TestPlayableSprite();
        player.setCentreX((short) 160);
        // relY = 20 (outside Solid_Landed threshold < 16)
        player.setCentreY((short) 93);

        spikes.onSolidContact(player, new SolidContact(true, false, false, false, false), 1);

        assertFalse(player.hurtOrDeathIgnoringIFramesCalled);
        assertFalse(player.hurtIgnoringIFramesCalled);
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private boolean hurtOrDeathIgnoringIFramesCalled;
        private boolean hurtIgnoringIFramesCalled;
        private int lastSourceX;
        private boolean lastSpikeHit;
        private boolean lastHadRings;

        private TestPlayableSprite() {
            super("TEST", (short) 0, (short) 0);
            setWidth(20);
            setHeight(38);
        }

        @Override
        public boolean applyHurtIgnoringIFrames(int sourceX, boolean spikeHit) {
            hurtIgnoringIFramesCalled = true;
            lastSourceX = sourceX;
            lastSpikeHit = spikeHit;
            return true;
        }

        @Override
        public boolean applyHurtOrDeathIgnoringIFrames(int sourceX, boolean spikeHit, boolean hadRings) {
            hurtOrDeathIgnoringIFramesCalled = true;
            lastSourceX = sourceX;
            lastSpikeHit = spikeHit;
            lastHadRings = hadRings;
            return true;
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
            // No-op for tests.
        }
    }
}
