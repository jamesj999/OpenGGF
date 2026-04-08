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
        assertFalse(player.getCeilingSensors()[0].isActive());
        assertFalse(player.getCeilingSensors()[1].isActive());
        player.setJumpInputPressed(true);

        ((CustomPlayablePhysics) player).tickCustomPhysics(
                false, false, false, true, true, false, false, false, (LevelManager) null, 42);

        assertTrue(player.getAir());
        assertTrue(player.getX() != startX || player.getY() != startY);
        assertTrue(player.getCeilingSensors()[0].isActive());
        assertTrue(player.getCeilingSensors()[1].isActive());
    }

    @Test
    void groundMotionProjectsVelocityThroughRotationAngle() {
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        // Set angle to 0 first, then change via setScalarIndex + tick
        controller.setScalarIndex(0x40);
        controller.tick(); // angle = 0x40

        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("sonic",
                (short) 0x460, (short) 0x430, controller);
        player.setGSpeed((short) 0x200);
        player.setAir(false);

        short startX = player.getX();
        short startY = player.getY();

        // tickAndMove projects gSpeed through angle 0x40
        S3kSlotBonusPlayer.tickAndMove(player, controller, false, false, false, 0);

        // At angle 0x40, the projected direction should differ from pure horizontal
        assertTrue(player.getX() != startX || player.getY() != startY,
                "Player should have moved via rotation projection");
    }

    @Test
    void airGravityIsAngleDependentUsingFactor0x2A() {
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        // angle stays at 0 (no tick called)

        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("sonic",
                (short) 0x460, (short) 0x430, controller);
        player.setAir(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);

        S3kSlotBonusPlayer.tickAndMove(player, controller, false, false, false, 0);

        // At angle 0, sin(0)=0 and cos(0)=256
        // X gravity component: sin(0) * 0x2A = 0
        // Y gravity component: cos(0) * 0x2A = 256 * 42 = 10752 → >> 8 = 42 = 0x2A
        assertEquals((short) 0, player.getXSpeed());
        assertEquals((short) 0x2A, player.getYSpeed());
    }

    @Test
    void airGravityAtAngle0x40ProducesXComponent() {
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        controller.tick(); // angle = 0x40

        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("sonic",
                (short) 0x460, (short) 0x430, controller);
        player.setAir(true);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);

        S3kSlotBonusPlayer.tickAndMove(player, controller, false, false, false, 0);

        // At angle 0x40 (90°), sin(0x40)=256, cos(0x40)≈0
        // X gravity: sin(0x40) * 0x2A = 256*42 >> 8 = 42 = 0x2A
        // Y gravity: cos(0x40) * 0x2A ≈ 0
        assertEquals((short) 0x2A, player.getXSpeed());
        assertTrue(Math.abs(player.getYSpeed()) <= 1, "Y should be near zero at angle 0x40");
    }

    @Test
    void movementConstantsMatchDisassembly() {
        assertEquals(0x0C, S3kSlotBonusPlayer.GROUND_ACCEL);
        assertEquals(0x0C, S3kSlotBonusPlayer.GROUND_DECEL);
        assertEquals(0x40, S3kSlotBonusPlayer.GROUND_REVERSAL_DECEL);
        assertEquals(0x800, S3kSlotBonusPlayer.GROUND_MAX_SPEED);
    }

    @Test
    void startPositionMatchesCageCapture() {
        assertEquals((short) 0x0460, S3kSlotRomData.SLOT_BONUS_START_X);
        assertEquals((short) 0x0430, S3kSlotRomData.SLOT_BONUS_START_Y);
    }

    @Test
    void spriteManagerTickPlayablePhysicsRespondsToGroundedRightInputForSlotPlayer() {
        RuntimeManager.createGameplay();
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("sonic", (short) 0x460, (short) 0x430, controller);

        short startX = player.getX();

        for (int frame = 0; frame < 8; frame++) {
            SpriteManager.tickPlayablePhysics(player,
                    false, false, false, true, false, false, false, false,
                    GameServices.level(), 42 + frame);
        }

        assertTrue(player.getGSpeed() > 0 || player.getXSpeed() > 0,
                "gSpeed=" + player.getGSpeed() + " xSpeed=" + player.getXSpeed() + " x=" + player.getX());
        assertTrue(player.getX() > startX,
                "gSpeed=" + player.getGSpeed() + " xSpeed=" + player.getXSpeed() + " x=" + player.getX() + " startX=" + startX);
    }
}
