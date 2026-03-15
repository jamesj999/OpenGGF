package com.openggf.game.sonic1.objects.badniks;

import com.openggf.game.sonic1.objects.TestPlayableSprite;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
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
        YadrinTestPlayableSprite player = new YadrinTestPlayableSprite();
        player.setRolling(true);
        player.setCentreX((short) 160);
        player.setCentreY((short) 96);

        yadrin.onTouchResponse(player, new TouchResponseResult(0x0C, 16, 16, TouchCategory.SPECIAL), 12);

        assertTrue(player.hurtOrDeathCalled);
        assertFalse(yadrin.isDestroyed());
    }

    private static final class YadrinTestPlayableSprite extends TestPlayableSprite {
        private boolean hurtOrDeathCalled;

        @Override
        public boolean applyHurtOrDeath(int sourceX, boolean spikeHit, boolean hadRings) {
            hurtOrDeathCalled = true;
            return true;
        }
    }
}
