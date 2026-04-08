package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.level.LevelManager;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void slotPlayerDelegatesToControllerSeam() {
        RecordingController controller = new RecordingController();
        S3kSlotBonusPlayer player = new S3kSlotBonusPlayer("tails", (short) 0x460, (short) 0x430, controller);

        assertEquals("tails", player.getCode());
        assertTrue(player instanceof CustomPlayablePhysics);

        player.tickCustomPhysics(false, false, true, false, true, false, false, false, (LevelManager) null, 42);
        assertTrue(controller.invoked);
    }
}
