package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.physics.Direction;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestCnzTriangleBumperObjectInstance {

    @Test
    void flippedDownwardTriangleAppliesRomTraceBounce() {
        CnzTriangleBumperObjectInstance bumper = new CnzTriangleBumperObjectInstance(
                new ObjectSpawn(0x1280, 0x0478, Sonic3kObjectIds.CNZ_TRIANGLE_BUMPER, 0x60, 3, false, 0));
        bumper.setServices(new TestObjectServices());
        AbstractPlayableSprite player = newPlayer();
        player.setCentreX((short) 0x12D3);
        player.setCentreY((short) 0x0486);
        player.setAir(true);
        player.setRollingJump(true);
        player.setJumping(true);
        player.setPushing(true);
        player.setGSpeed((short) 0);

        bumper.update(0, player);

        assertEquals((short) -0x0800, player.getXSpeed());
        assertEquals((short) 0x0800, player.getYSpeed());
        assertEquals((short) -0x0800, player.getGSpeed());
        assertEquals(15, player.getMoveLockTimer());
        assertEquals(Direction.LEFT, player.getDirection());
        assertTrue(player.getAir());
        assertFalse(player.getRollingJump());
        assertFalse(player.isJumping());
        assertFalse(player.getPushing());
    }

    @Test
    void unflippedTriangleLaunchesRightAndUp() {
        CnzTriangleBumperObjectInstance bumper = new CnzTriangleBumperObjectInstance(
                new ObjectSpawn(0x0200, 0x0300, Sonic3kObjectIds.CNZ_TRIANGLE_BUMPER, 0x40, 0, false, 0));
        bumper.setServices(new TestObjectServices());
        AbstractPlayableSprite player = newPlayer();
        player.setCentreX((short) 0x0200);
        player.setCentreY((short) 0x0300);
        player.setAnimationId(9);

        bumper.update(0, player);

        assertEquals((short) 0x0800, player.getXSpeed());
        assertEquals((short) -0x0800, player.getYSpeed());
        assertEquals((short) 0x0800, player.getGSpeed());
        assertEquals(Direction.RIGHT, player.getDirection());
        assertEquals(0, player.getAnimationId());
        assertEquals(1, player.getFlipAngle());
        assertEquals(3, player.getFlipsRemaining());
        assertEquals(8, player.getFlipSpeed());
    }

    @Test
    void outsideSubtypeWidthDoesNotBounce() {
        CnzTriangleBumperObjectInstance bumper = new CnzTriangleBumperObjectInstance(
                new ObjectSpawn(0x0200, 0x0300, Sonic3kObjectIds.CNZ_TRIANGLE_BUMPER, 0x40, 0, false, 0));
        bumper.setServices(new TestObjectServices());
        AbstractPlayableSprite player = newPlayer();
        player.setCentreX((short) 0x0240);
        player.setCentreY((short) 0x0300);
        player.setXSpeed((short) 0x0123);
        player.setYSpeed((short) -0x0456);

        bumper.update(0, player);

        assertEquals((short) 0x0123, player.getXSpeed());
        assertEquals((short) -0x0456, player.getYSpeed());
        assertEquals(0, player.getMoveLockTimer());
    }

    private static AbstractPlayableSprite newPlayer() {
        return HeadlessTestFixture.builder()
                .withZoneAndAct(com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
    }
}
