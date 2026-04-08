package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotBonusPlayer {

    private static final class RecordingController extends S3kSlotStageController {
        private boolean invoked;

        @Override
        public void tickPlayer(S3kSlotBonusPlayer player, boolean left, boolean right, boolean jump, int frameCounter) {
            invoked = true;
            assertEquals("tails", player.getCode());
        }
    }

    @Test
    void tailsSlotPlayerDelegatesToControllerSeam() {
        RecordingController controller = new RecordingController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("tails", (short) 0x460, (short) 0x430, controller);

        assertInstanceOf(Tails.class, player);
        assertEquals("tails", player.getCode());
        assertTrue(player instanceof CustomPlayablePhysics);
        assertTrue(player instanceof S3kSlotBonusPlayer);

        ((CustomPlayablePhysics) player).tickCustomPhysics(
                false, false, true, false, true, false, false, false, (LevelManager) null, 42);
        assertTrue(controller.invoked);
    }
}
