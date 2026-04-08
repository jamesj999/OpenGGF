package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.sprites.playable.CustomPlayablePhysics;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Knuckles;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.Tails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotBonusPlayer {

    private static final class RecordingController extends S3kSlotStageController {
        private boolean invoked;
        private S3kSlotBonusPlayer recordedPlayer;
        private boolean recordedLeft;
        private boolean recordedRight;
        private boolean recordedJump;
        private int recordedFrameCounter;

        @Override
        public void tickPlayer(S3kSlotBonusPlayer player, boolean left, boolean right, boolean jump, int frameCounter) {
            invoked = true;
            recordedPlayer = player;
            recordedLeft = left;
            recordedRight = right;
            recordedJump = jump;
            recordedFrameCounter = frameCounter;
        }
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
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

    @Test
    void spriteManagerTickPlayablePhysicsUsesCustomPhysicsForSonicSlotPlayer() {
        RuntimeManager.createGameplay();
        RecordingController controller = new RecordingController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("sonic", (short) 0x460, (short) 0x430, controller);

        assertInstanceOf(Sonic.class, player);
        assertFalse(player instanceof Tails);
        assertEquals("sonic", player.getCode());
        assertTrue(player instanceof CustomPlayablePhysics);
        assertTrue(player instanceof S3kSlotBonusPlayer);

        SpriteManager.tickPlayablePhysics(player,
                false, false, true, false, true, false, false, false,
                GameServices.level(), 42);

        assertTrue(controller.invoked);
        assertSame(player, controller.recordedPlayer);
        assertTrue(controller.recordedLeft);
        assertFalse(controller.recordedRight);
        assertTrue(controller.recordedJump);
        assertEquals(42, controller.recordedFrameCounter);
    }

    @Test
    void createReturnsKnucklesSlotPlayerForKnucklesMainCode() {
        RecordingController controller = new RecordingController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("knuckles", (short) 0x460, (short) 0x430, controller);

        assertInstanceOf(Knuckles.class, player);
        assertFalse(player instanceof Tails);
        assertFalse(player instanceof Sonic);
        assertEquals("knuckles", player.getCode());
        assertTrue(player instanceof CustomPlayablePhysics);
        assertTrue(player instanceof S3kSlotBonusPlayer);
    }

    @Test
    void customPhysicsConsumesLaunchedVelocityIntoPosition() {
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("sonic", (short) 0x460, (short) 0x430, controller);

        short startX = player.getX();
        short startY = player.getY();
        player.setJumpInputPressed(true);

        ((CustomPlayablePhysics) player).tickCustomPhysics(
                false, false, false, true, true, false, false, false, (LevelManager) null, 42);

        assertTrue(player.getAir());
        assertTrue(player.getX() != startX || player.getY() != startY);
    }
}
