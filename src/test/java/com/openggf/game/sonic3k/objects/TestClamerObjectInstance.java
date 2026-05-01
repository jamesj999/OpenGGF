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

    /**
     * ROM Clamer_Index auto-close gate (sonic3k.asm:185880-185902).
     * <p>When the closer player is within {@code abs(dx) < 0x60} of the
     * Clamer parent, on the side it faces, the parent transitions
     * routine 0x02 -> 0x06 ({@code loc_89036}). When the player is on
     * the opposite side or out of range, the parent stays in routine 0x02.
     */
    @Test
    void autoCloseFiresWhenPlayerOnFacingSideWithinThreshold() {
        // Clamer facing right (renderFlags bit 0 = 1). Player approaching
        // from the right side at dx=+0x40 (< 0x60 threshold).
        ClamerObjectInstance clamer =
                new ClamerObjectInstance(new ObjectSpawn(0x0C98, 0x0470, 0xA3, 0, 1, false, 0));
        clamer.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        player.setCentreX((short) (0x0C98 + 0x40));
        player.setCentreY((short) 0x0470);

        assertEquals(0x02, clamer.testRoutine());
        clamer.update(0x1000, player);
        // Facing right, player on the right (d0=2, then -=2 = 0): close.
        assertEquals(0x06, clamer.testRoutine());
    }

    @Test
    void autoCloseHoldsWhenPlayerOnOppositeSide() {
        // Clamer facing right; player on the LEFT (d0=0, then -=2 = -2).
        ClamerObjectInstance clamer =
                new ClamerObjectInstance(new ObjectSpawn(0x0C98, 0x0470, 0xA3, 0, 1, false, 0));
        clamer.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        player.setCentreX((short) (0x0C98 - 0x40));
        player.setCentreY((short) 0x0470);

        clamer.update(0x1000, player);
        assertEquals(0x02, clamer.testRoutine());
    }

    @Test
    void autoCloseHoldsWhenPlayerBeyondThreshold() {
        // Clamer facing right; player on the right but at dx=0x60 (== threshold).
        ClamerObjectInstance clamer =
                new ClamerObjectInstance(new ObjectSpawn(0x0C98, 0x0470, 0xA3, 0, 1, false, 0));
        clamer.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        player.setCentreX((short) (0x0C98 + 0x60));
        player.setCentreY((short) 0x0470);

        clamer.update(0x1000, player);
        assertEquals(0x02, clamer.testRoutine());
    }

    @Test
    void autoCloseAnimationCompletesAndReopens() {
        ClamerObjectInstance clamer =
                new ClamerObjectInstance(new ObjectSpawn(0x0C98, 0x0470, 0xA3, 0, 1, false, 0));
        clamer.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        player.setCentreX((short) (0x0C98 + 0x40));
        player.setCentreY((short) 0x0470);

        clamer.update(0x1000, player);
        assertEquals(0x06, clamer.testRoutine());

        // Move the player out of range so the gate would not re-trigger.
        player.setCentreX((short) (0x0C98 + 0x80));
        for (int i = 0; i < 12; i++) {
            clamer.update(0x1001 + i, player);
        }
        // After the close timer expires, loc_89056 resets routine to 0x02.
        assertEquals(0x02, clamer.testRoutine());
    }
}
