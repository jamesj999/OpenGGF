package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
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
class TestClamerObjectInstance {

    @Test
    void springChildUsesRomOffsetAndLaunchesPlayer() {
        ClamerObjectInstance clamer =
                new ClamerObjectInstance(new ObjectSpawn(0x0578, 0x0690, 0xA3, 0, 0, false, 0));
        clamer.setServices(new TestObjectServices());
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();
        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x0573);
        player.setCentreY((short) 0x0678);
        player.setAir(false);
        player.setJumping(true);

        assertEquals(0x0578, clamer.getX());
        assertEquals(0x0690, clamer.getY());
        assertEquals(0x0578, clamer.getMultiTouchRegions()[1].x());
        assertEquals(0x0688, clamer.getMultiTouchRegions()[1].y());

        clamer.onTouchResponse(player, new TouchResponseResult(0x17, 8, 8, TouchCategory.SPECIAL), 0x026C);

        assertEquals((short) 0x0800, player.getXSpeed());
        assertEquals((short) 0x0800, player.getGSpeed());
        assertEquals((short) -0x0800, player.getYSpeed());
        assertEquals(0x067E, player.getCentreY());
        assertEquals(Direction.RIGHT, player.getDirection());
        assertEquals(Sonic3kAnimationIds.SPRING.id(), player.getAnimationId());
        assertTrue(player.getAir());
        assertFalse(player.isJumping());
        assertEquals(0, clamer.getCollisionFlags());

        clamer.update(0x026C, player);
        fixture.stepFrame(false, false, true, false, false);

        assertEquals((short) 0x07E8, player.getXSpeed());
        assertEquals((short) -0x07C8, player.getYSpeed());

        clamer.update(0x026D, player);
        fixture.stepFrame(false, false, false, false, false);
        clamer.onTouchResponse(player, new TouchResponseResult(0x17, 8, 8, TouchCategory.SPECIAL), 0x026E);

        assertEquals(0x0674, player.getCentreY());
        assertEquals((short) 0x0800, player.getXSpeed());
        assertEquals((short) -0x0800, player.getYSpeed());
    }

    @Test
    void flippedSpringLaunchesLeft() {
        ClamerObjectInstance clamer =
                new ClamerObjectInstance(new ObjectSpawn(0x0578, 0x0690, 0xA3, 0, 1, false, 0));
        clamer.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();

        clamer.onTouchResponse(player, new TouchResponseResult(0x17, 8, 8, TouchCategory.SPECIAL), 0);

        assertEquals((short) -0x0800, player.getXSpeed());
        assertEquals((short) -0x0800, player.getGSpeed());
        assertEquals(Direction.LEFT, player.getDirection());
    }
}
