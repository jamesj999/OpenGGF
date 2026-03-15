package com.openggf.game.sonic1.objects;

import org.junit.Test;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1SpikeObjectInstance {

    @Test
    public void spikesHurtDuringInvulnerabilityFrames() {
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x00, 0, false, 0));
        SpikeTestPlayableSprite player = new SpikeTestPlayableSprite();
        player.setCentreX((short) 160);
        player.setCentreY((short) 80);
        player.setInvulnerableFrames(120);
        player.setHurt(false);
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
        SpikeTestPlayableSprite player = new SpikeTestPlayableSprite();
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
    public void spikesDoNotHurtWhenAlreadyHurt() {
        // Sideways spike (subtype 0x10) — ROM check: cmpi.b #4,obRoutine(a0) / bhs.s loc_CF20
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x10, 0, false, 0));
        SpikeTestPlayableSprite player = new SpikeTestPlayableSprite();
        player.setCentreX((short) 160);
        player.setCentreY((short) 112);
        player.setHurt(true);

        spikes.onSolidContact(player, new SolidContact(false, true, false, false, false), 1);

        assertFalse(player.hurtOrDeathIgnoringIFramesCalled);
        assertFalse(player.hurtIgnoringIFramesCalled);
    }

    @Test
    public void spikesDoNotHurtWhenDead() {
        // Sideways spike (subtype 0x10) — ROM check: cmpi.b #4,obRoutine(a0) / bhs.s loc_CF20
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x10, 0, false, 0));
        SpikeTestPlayableSprite player = new SpikeTestPlayableSprite();
        player.setCentreX((short) 160);
        player.setCentreY((short) 112);
        player.setDead(true);

        spikes.onSolidContact(player, new SolidContact(false, true, false, false, false), 1);

        assertFalse(player.hurtOrDeathIgnoringIFramesCalled);
        assertFalse(player.hurtIgnoringIFramesCalled);
    }

    @Test
    public void ceilingSpikeSetsRomKnockbackVelocity() {
        // Upright spike (subtype 0x00) hit from below (touchBottom = ceiling contact).
        // ROM: HurtSonic always sets ySpeed = -$400 (upward), regardless of contact direction.
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x00, 0, false, 0));
        SpikeTestPlayableSprite player = new SpikeTestPlayableSprite();
        player.setCentreX((short) 160);
        player.setCentreY((short) 130); // below spike
        player.setYSpeed((short) -0x400); // jumping upward

        // touchBottom = true: Sonic hits the bottom of the spike from below (ceiling contact)
        spikes.onSolidContact(player, new SolidContact(false, false, true, false, false), 1);

        assertTrue("Spike should have hurt player", player.hurtOrDeathIgnoringIFramesCalled);
        assertTrue("ySpeed should be negative (upward) matching ROM HurtSonic, was: " + player.getYSpeed(),
                player.getYSpeed() < 0);
    }

    @Test
    public void floorSpikeSetsRomKnockbackVelocity() {
        // Upright spike (subtype 0x00) hit from above (standing contact).
        // ROM: HurtSonic always sets ySpeed = -$400 (upward).
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x00, 0, false, 0));
        SpikeTestPlayableSprite player = new SpikeTestPlayableSprite();
        player.setCentreX((short) 160);
        player.setCentreY((short) 80); // above spike
        player.setYSpeed((short) 0); // descending onto spike

        // standing = true: Sonic landed on top of the spike
        spikes.onSolidContact(player, new SolidContact(true, false, false, false, false), 1);

        assertTrue("Spike should have hurt player", player.hurtOrDeathIgnoringIFramesCalled);
        assertTrue("ySpeed should be negative (upward) matching ROM HurtSonic, was: " + player.getYSpeed(),
                player.getYSpeed() < 0);
    }

    @Test
    public void uprightSpikesIgnoreStandingContactOutsideRomLandingWindow() {
        Sonic1SpikeObjectInstance spikes = new Sonic1SpikeObjectInstance(
                new ObjectSpawn(160, 112, 0x36, 0x00, 0, false, 0));
        SpikeTestPlayableSprite player = new SpikeTestPlayableSprite();
        player.setCentreX((short) 160);
        // relY = 20 (outside Solid_Landed threshold < 16)
        player.setCentreY((short) 93);

        spikes.onSolidContact(player, new SolidContact(true, false, false, false, false), 1);

        assertFalse(player.hurtOrDeathIgnoringIFramesCalled);
        assertFalse(player.hurtIgnoringIFramesCalled);
    }

    private static final class SpikeTestPlayableSprite extends TestPlayableSprite {
        private boolean hurtOrDeathIgnoringIFramesCalled;
        private boolean hurtIgnoringIFramesCalled;
        private int lastSourceX;
        private boolean lastSpikeHit;
        private boolean lastHadRings;

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
            // Simulate real applyHurt behavior: set hurt flag and knockback velocity
            setHurt(true);
            int dir = (getCentreX() >= sourceX) ? 1 : -1;
            setXSpeed((short) (0x200 * dir));
            setYSpeed((short) -0x400);
            return true;
        }
    }
}
