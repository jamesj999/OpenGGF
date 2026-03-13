package com.openggf.game.sonic1.objects.badniks;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1YadrinBadnikInstance {

    @Test
    public void collisionFlagsUseSpecialCategory() {
        Sonic1YadrinBadnikInstance yadrin = new Sonic1YadrinBadnikInstance(
                new ObjectSpawn(160, 100, 0x50, 0, 0, false, 0), null);

        assertEquals(0x40, yadrin.getCollisionFlags() & 0xC0);
    }

    @Test
    public void rollingTopHitHurtsWithoutDestroyingBadnik() {
        Sonic1YadrinBadnikInstance yadrin = new Sonic1YadrinBadnikInstance(
                new ObjectSpawn(160, 100, 0x50, 0, 0, false, 0), null);
        TestPlayableSprite player = new TestPlayableSprite();
        player.setRolling(true);
        player.setCentreX((short) 160);
        player.setCentreY((short) 96);

        yadrin.onTouchResponse(player, new TouchResponseResult(0x0C, 16, 16, TouchCategory.SPECIAL), 12);

        assertTrue(player.hurtOrDeathCalled);
        assertFalse(yadrin.isDestroyed());
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private boolean hurtOrDeathCalled;

        private TestPlayableSprite() {
            super("TEST", (short) 0, (short) 0);
            setWidth(20);
            setHeight(38);
        }

        @Override
        public boolean applyHurtOrDeath(int sourceX, boolean spikeHit, boolean hadRings) {
            hurtOrDeathCalled = true;
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
        }

        @Override
        public void draw() {
        }
    }
}
