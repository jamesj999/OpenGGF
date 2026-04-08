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

        controller.bootstrap();
        controller.tick(); // advance by scalarIndex (0x40)
        assertEquals(0x40, controller.angle());

        controller.bootstrap();

        assertEquals(0, controller.angle());

        controller.tick(); // advance by 0x40 again after re-bootstrap
        assertEquals(0x40, controller.angle());
        controller.tickPlayer((S3kSlotBonusPlayer) player, false, false, false, 1);
        assertEquals((byte) 0x40, player.getAngle());
    }

    @Test
    void leftAndRightInputsDoNotSkipJumpWhenBothPressed() {
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("tails", (short) 0, (short) 0, controller);

        controller.bootstrap();
        controller.tick(); // angle = 0x40
        controller.tickPlayer((S3kSlotBonusPlayer) player, false, false, false, 0);
        assertEquals(0x40, controller.angle());
        assertEquals((byte) 0x40, player.getAngle());

        player.setAir(false);
        player.setJumpInputPressed(true);
        controller.tickPlayer((S3kSlotBonusPlayer) player, true, true, true, 1);
        int angle = controller.angle();
        int launchAngle = (-((angle & 0xFC)) - 0x40) & 0xFF;

        assertEquals(0x40, angle);
        assertEquals((byte) 0x40, player.getAngle());
        assertTrue(player.getAir());
        assertEquals((short) ((TrigLookupTable.cosHex(launchAngle) * 0x680) >> 8), player.getXSpeed());
        assertEquals((short) ((TrigLookupTable.sinHex(launchAngle) * 0x680) >> 8), player.getYSpeed());
    }

    @Test
    void angleWraparoundIsMaskedToEightBits() {
        S3kSlotStageController controller = new S3kSlotStageController();

        controller.bootstrap();
        // Set scalar so that after two ticks we wrap: 0xC0 + 0xC0 = 0x180 & 0xFF = 0x80
        controller.setScalarIndex(0xC0);
        controller.tick(); // angle = 0xC0
        assertEquals(0xC0, controller.angle());
        controller.tick(); // angle = 0xC0 + 0xC0 = 0x180 & 0xFF = 0x80
        assertEquals(0x80, controller.angle());
    }

    @Test
    void jumpLaunchesPlayerUsingCurrentAngleAndSetsAirborneState() {
        S3kSlotStageController controller = new S3kSlotStageController();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("tails", (short) 0, (short) 0, controller);

        controller.bootstrap();
        controller.tick(); // angle = 0x40
        int angle = controller.angle();
        player.setAir(false);
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

        controller.bootstrap();
        controller.tick(); // angle = 0x40
        player.setAir(false);
        player.setJumpInputPressed(true);
        controller.tickPlayer((S3kSlotBonusPlayer) player, false, false, true, 1);

        player.setXSpeed((short) 0x0123);
        player.setYSpeed((short) 0x0456);

        player.setJumpInputPressed(true);
        controller.tickPlayer((S3kSlotBonusPlayer) player, false, false, true, 2);

        assertEquals((short) 0x0123, player.getXSpeed());
        assertEquals((short) 0x0456, player.getYSpeed());
    }

    @Test
    void bootstrapInitializesScalarIndexTo0x40() {
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        assertEquals(0x40, controller.scalarIndex());
    }

    @Test
    void tickAccumulatesStatTableByScalarIndex() {
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        assertEquals(0, controller.angle());

        controller.tick();
        assertEquals(0x40, controller.angle());

        controller.tick();
        assertEquals(0x80, controller.angle());
    }

    @Test
    void negateScalarReversesRotationDirection() {
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        controller.tick(); // angle = 0x40
        assertEquals(0x40, controller.angle());

        controller.negateScalar();
        assertEquals(-0x40, controller.scalarIndex());

        controller.tick(); // angle = 0x40 + (-0x40) = 0x00
        assertEquals(0x00, controller.angle());
    }

    @Test
    void jumpOnlyWorksWhenGrounded() {
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();
        AbstractPlayableSprite player = S3kSlotBonusPlayer.create("sonic", (short) 0, (short) 0, controller);

        // Set player airborne
        player.setAir(true);
        player.setJumpInputPressed(true);

        controller.tickPlayer((S3kSlotBonusPlayer) player, false, false, true, 0);

        // Should NOT have launched (was already airborne)
        assertEquals((short) 0, player.getXSpeed());
        assertEquals((short) 0, player.getYSpeed());
    }
}
