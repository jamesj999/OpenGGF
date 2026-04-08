package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kSlotStageController {

    @Test
    void bootstrapResetsAngleStateAfterMovement() {
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("tails", (short) 0, (short) 0, controller);

        controller.tickPlayer((S3kSlotBonusPlayer) player, false, true, false, 0);
        assertEquals(4, controller.angle());

        controller.bootstrap();

        assertEquals(0, controller.angle());

        controller.tickPlayer((S3kSlotBonusPlayer) player, false, true, false, 1);
        assertEquals(4, controller.angle());
        assertEquals((byte) 4, player.getAngle());
    }

    @Test
    void leftAndRightInputsAdjustAngleByFourAndNoOpWhenBothPressed() {
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("tails", (short) 0, (short) 0, controller);

        controller.tickPlayer((S3kSlotBonusPlayer) player, false, true, false, 0);
        assertEquals(4, controller.angle());
        assertEquals((byte) 4, player.getAngle());

        controller.tickPlayer((S3kSlotBonusPlayer) player, true, false, false, 1);
        assertEquals(0, controller.angle());
        assertEquals((byte) 0, player.getAngle());

        controller.tickPlayer((S3kSlotBonusPlayer) player, true, true, false, 2);
        assertEquals(0, controller.angle());
        assertEquals((byte) 0, player.getAngle());
    }

    @Test
    void angleWraparoundIsMaskedToTheLowerSixBits() {
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("tails", (short) 0, (short) 0, controller);

        controller.tickPlayer((S3kSlotBonusPlayer) player, true, false, false, 0);
        assertEquals(0xFC, controller.angle());
        assertEquals((byte) 0xFC, player.getAngle());

        controller.tickPlayer((S3kSlotBonusPlayer) player, false, false, false, 1);
        assertEquals(0xFC, controller.angle());
        assertEquals((byte) 0xFC, player.getAngle());
    }
}
