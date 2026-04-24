package com.openggf.game.sonic3k.objects;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectSnapshot;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.TrigLookupTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestCnzBalloonInstance {

    @Test
    void hydrateFromRomSnapshotRestoresRandomBobPhase() {
        CnzBalloonInstance balloon =
                new CnzBalloonInstance(new ObjectSpawn(0x180, 0x680, 0x41, 0, 0, false, 0));
        balloon.setServices(new TestObjectServices());

        balloon.hydrateFromRomSnapshot(new RomObjectSnapshot(
                Map.of(
                        0x26, 0xC0,
                        0x28, 0xD7
                ),
                Map.of(
                        0x08, 0x180,
                        0x0C, 0x678,
                        0x32, 0x680
                )));

        assertEquals(0x678, balloon.getY());
        balloon.update(0, null);
        assertEquals(0x680 + (TrigLookupTable.sinHex(0xC0) >> 5), balloon.getY());
    }

    @Test
    void updateDoesNotLaunchOutsideRomTouchRegion() {
        CnzBalloonInstance balloon =
                new CnzBalloonInstance(new ObjectSpawn(0, 0, 0x41, 0, 0, false, 0));
        balloon.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        player.applyRollingRadii(false);
        player.setCentreX((short) 12);
        player.setCentreY((short) 25);
        player.setYSpeed((short) 0x370);

        balloon.update(0, player);

        assertFalse(balloon.isPoppedForTest());
        assertEquals((short) 0x370, player.getYSpeed());
    }

    @Test
    void touchResponseLaunchesAndPopsBalloon() {
        CnzBalloonInstance balloon =
                new CnzBalloonInstance(new ObjectSpawn(0, 0, 0x41, 0, 0, false, 0));
        balloon.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        player.setYSpeed((short) 0x370);

        balloon.onTouchResponse(player, new TouchResponseResult(0x17, 8, 8, TouchCategory.SPECIAL), 0);

        assertTrue(balloon.isPoppedForTest());
        assertEquals((short) -0x700, player.getYSpeed());
        assertTrue(player.getAir());
    }

    @Test
    void poppedBalloonAnimationMovesOffscreenForNormalDeletePath() {
        CnzBalloonInstance balloon =
                new CnzBalloonInstance(new ObjectSpawn(0x17C0, 0x860, 0x41, 0, 0, false, 0));
        balloon.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(com.openggf.game.sonic3k.constants.Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();

        balloon.onTouchResponse(player, new TouchResponseResult(0x17, 8, 8, TouchCategory.SPECIAL), 0);
        for (int i = 0; i < 7; i++) {
            balloon.update(i, player);
        }

        assertTrue(balloon.hasMovedOffscreenForTest());
        assertFalse(balloon.isDestroyed());
        assertEquals(0x7F00, balloon.getX());
        assertEquals(0, balloon.getCollisionFlags());
    }
}
