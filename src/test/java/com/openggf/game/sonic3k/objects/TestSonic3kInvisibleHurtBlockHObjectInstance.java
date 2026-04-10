package com.openggf.game.sonic3k.objects;

import com.openggf.game.DamageCause;
import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.SolidObjectParams;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic3kInvisibleHurtBlockHObjectInstance {

    @Test
    public void subtypeDecodesSolidDimensionsLikeInvisibleBlock() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0xF1, 0, false, 0));

        SolidObjectParams params = block.getSolidParams();

        assertEquals(0x80 + 0x0B, params.halfWidth());
        assertEquals(0x10, params.airHalfHeight());
        assertEquals(0x11, params.groundHalfHeight());
    }

    @Test
    public void defaultPlacementHurtsOnStandingContact() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0, false, 0));
        RecordingPlayer player = new RecordingPlayer();

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 7);

        assertTrue(player.hurtOrDeathCalled);
        assertEquals(0x100, player.lastSourceX);
        assertEquals(DamageCause.NORMAL, player.lastCause);
        assertFalse(player.lastHadRings);
    }

    @Test
    public void xFlipHurtsOnSideContactOnly() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0x01, false, 0));
        RecordingPlayer player = new RecordingPlayer();

        block.onSolidContact(player, new SolidContact(false, true, false, false, false, 6), 7);

        assertTrue(player.hurtOrDeathCalled);
    }

    @Test
    public void yFlipHurtsOnBottomContactOnly() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0x02, false, 0));
        RecordingPlayer player = new RecordingPlayer();

        block.onSolidContact(player, new SolidContact(false, false, true, false, false), 7);

        assertTrue(player.hurtOrDeathCalled);
    }

    @Test
    public void inactiveFaceDoesNotHurt() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0x01, false, 0));
        RecordingPlayer player = new RecordingPlayer();

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 7);

        assertFalse(player.hurtOrDeathCalled);
        assertFalse(player.hurtCalled);
    }

    @Test
    public void invulnerablePlayerIsIgnored() {
        Sonic3kInvisibleHurtBlockHObjectInstance block = new Sonic3kInvisibleHurtBlockHObjectInstance(
                new ObjectSpawn(0x100, 0x180, 0x6A, 0x11, 0, false, 0));
        RecordingPlayer player = new RecordingPlayer();
        player.setInvulnerableFrames(60);

        block.onSolidContact(player, new SolidContact(true, false, false, true, false), 7);

        assertFalse(player.hurtOrDeathCalled);
        assertFalse(player.hurtCalled);
    }

    private static final class RecordingPlayer extends TestPlayableSprite {
        private boolean hurtOrDeathCalled;
        private boolean hurtCalled;
        private int lastSourceX;
        private DamageCause lastCause;
        private boolean lastHadRings;

        @Override
        public boolean applyHurt(int sourceX) {
            hurtCalled = true;
            lastSourceX = sourceX;
            return true;
        }

        @Override
        public boolean applyHurtOrDeath(int sourceX, DamageCause cause, boolean hadRings) {
            hurtOrDeathCalled = true;
            lastSourceX = sourceX;
            lastCause = cause;
            lastHadRings = hadRings;
            return true;
        }
    }
}
