package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void leftAndRightInputsDoNotSkipJumpWhenBothPressed() {
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("tails", (short) 0, (short) 0, controller);

        controller.tickPlayer((S3kSlotBonusPlayer) player, false, true, false, 0);
        assertEquals(4, controller.angle());
        assertEquals((byte) 4, player.getAngle());

        player.setJumpInputPressed(true);
        controller.tickPlayer((S3kSlotBonusPlayer) player, true, true, true, 1);
        int angle = controller.angle();
        int launchAngle = (-((angle & 0xFC)) - 0x40) & 0xFF;

        assertEquals(4, angle);
        assertEquals((byte) 4, player.getAngle());
        assertTrue(player.getAir());
        assertEquals((short) ((TrigLookupTable.cosHex(launchAngle) * 0x680) >> 8), player.getXSpeed());
        assertEquals((short) ((TrigLookupTable.sinHex(launchAngle) * 0x680) >> 8), player.getYSpeed());
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

    @Test
    void jumpLaunchesPlayerUsingCurrentAngleAndSetsAirborneState() {
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("tails", (short) 0, (short) 0, controller);

        controller.tickPlayer((S3kSlotBonusPlayer) player, false, true, false, 0);
        int angle = controller.angle();
        player.setJumpInputPressed(true);
        controller.tickPlayer((S3kSlotBonusPlayer) player, false, false, true, 1);
        int launchAngle = (-((angle & 0xFC)) - 0x40) & 0xFF;

        assertTrue(player.getAir());
        assertEquals((short) ((TrigLookupTable.cosHex(launchAngle) * 0x680) >> 8), player.getXSpeed());
        assertEquals((short) ((TrigLookupTable.sinHex(launchAngle) * 0x680) >> 8), player.getYSpeed());
        assertEquals((byte) angle, player.getAngle());
    }

    @Test
    void heldJumpDoesNotRelaunchAfterTheFirstPress() {
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("tails", (short) 0, (short) 0, controller);

        controller.tickPlayer((S3kSlotBonusPlayer) player, false, true, false, 0);
        player.setJumpInputPressed(true);
        controller.tickPlayer((S3kSlotBonusPlayer) player, false, false, true, 1);

        player.setXSpeed((short) 0x0123);
        player.setYSpeed((short) 0x0456);

        player.setJumpInputPressed(true);
        controller.tickPlayer((S3kSlotBonusPlayer) player, false, false, true, 2);

        assertEquals((short) 0x0123, player.getXSpeed());
        assertEquals((short) 0x0456, player.getYSpeed());
    }
}
