package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestAizHollowTreeObjectInstance {

    @Test
    void captureSetsObjectControlBitsSixAndOneWithoutSuppressingMovement() {
        AizHollowTreeObjectInstance tree = new AizHollowTreeObjectInstance(new ObjectSpawn(
                0x2D00, 0x03CC, Sonic3kObjectIds.AIZ_HOLLOW_TREE, 0, 0, false, 0));
        tree.setServices(new TestObjectServices().withCamera(new Camera()));
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_AIZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x2CF1);
        player.setCentreY((short) 0x0456);
        player.setXSpeed((short) 0x0AAC);
        player.setGSpeed((short) 0x0AE3);
        player.setAir(false);

        tree.update(0, player);

        assertTrue(player.isObjectControlled(),
                "Obj_AIZHollowTree sets object_control bits 6+1 while riding");
        assertTrue(player.isObjectControlAllowsCpu(),
                "Bits 6+1 are not ROM bit 7, so CPU/touch dispatch must not be suppressed");
        assertFalse(player.isObjectControlSuppressesMovement(),
                "Obj_AIZHollowTree does not set object_control bit 0");
        assertTrue(player.isSuppressGroundWallCollision(),
                "object_control bit 6 makes Sonic_WalkSpeed skip CalcRoomInFront");
    }
}
