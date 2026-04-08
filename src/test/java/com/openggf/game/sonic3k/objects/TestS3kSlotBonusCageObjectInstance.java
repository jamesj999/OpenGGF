package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.bonusstage.slots.S3kSlotStageController;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotBonusCageObjectInstance {

    @Test
    void captureCentersNearbyPlayableAndLocksControl() {
        ObjectSpawn spawn = new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        S3kSlotBonusCageObjectInstance cage = new S3kSlotBonusCageObjectInstance(spawn, controller);
        cage.setServices(new TestObjectServices());

        Sonic player = new Sonic("sonic", (short) 0x44C, (short) 0x41C);
        player.setXSpeed((short) 0x0123);
        player.setYSpeed((short) -0x0456);
        player.setGSpeed((short) 0x0789);
        player.setAir(false);
        player.setOnObject(true);

        cage.update(0, player);

        assertEquals(spawn.x(), player.getCentreX());
        assertEquals(spawn.y(), player.getCentreY());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, player.getGSpeed());
        assertTrue(player.isObjectControlled());
        assertTrue(player.isControlLocked());
        assertTrue(player.getAir());
        assertFalse(player.isOnObject());
    }

    @Test
    void debugPlayableIsIgnored() {
        ObjectSpawn spawn = new ObjectSpawn(0x460, 0x430, 0x00, 0x00, 0x00, false, 0);
        S3kSlotStageController controller = new S3kSlotStageController();
        controller.bootstrap();

        S3kSlotBonusCageObjectInstance cage = new S3kSlotBonusCageObjectInstance(spawn, controller);
        cage.setServices(new TestObjectServices());

        Sonic player = new Sonic("sonic", (short) 0x44C, (short) 0x41C);
        player.setDebugMode(true);
        short originalX = player.getX();
        short originalY = player.getY();

        cage.update(0, player);

        assertEquals(originalX, player.getX());
        assertEquals(originalY, player.getY());
        assertFalse(player.isObjectControlled());
        assertFalse(player.isControlLocked());
    }
}
