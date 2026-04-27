package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestCnzWireCageObjectInstance {

    @Test
    void activeCageSetsObjectControlBitSixWallCollisionSuppression() {
        CnzWireCageObjectInstance cage = new CnzWireCageObjectInstance(new ObjectSpawn(
                0x1D80, 0x0540, Sonic3kObjectIds.CNZ_WIRE_CAGE, 0x18, 0, false, 0));
        cage.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x1D80);
        player.setCentreY((short) 0x04F3);
        player.setAir(false);
        player.setGSpeed((short) 0x0C00);

        cage.update(0, player);

        assertTrue(player.isSuppressGroundWallCollision(),
                "Obj_CNZWireCage sets object_control bit 6, which makes Sonic_WalkSpeed skip CalcRoomInFront");
        assertFalse(player.isObjectControlled(),
                "Normal cage riding sets object_control bits 6 and 1, not bit 0; player movement still runs");

        player.setDead(true);
        cage.update(1, player);

        assertFalse(player.isSuppressGroundWallCollision());
    }

    @Test
    void airborneAngleZeroCaptureFallsThroughToTouchFloorBeforeLatch() {
        CnzWireCageObjectInstance cage = new CnzWireCageObjectInstance(new ObjectSpawn(
                0x1D80, 0x0540, Sonic3kObjectIds.CNZ_WIRE_CAGE, 0x18, 1, false, 0xA540));
        cage.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();

        player.setAir(true);
        player.setRolling(true);
        player.applyCustomRadii(7, 14);
        player.setCentreX((short) 0x1DD0);
        player.setCentreY((short) 0x04ED);
        player.setAngle((byte) 0);
        player.setXSpeed((short) -0x573);
        player.setYSpeed((short) -0x3A8);
        player.setGSpeed((short) -0x330);

        cage.update(0, player);

        assertTrue(player.isOnObject());
        assertFalse(player.getAir());
        assertTrue(player.isObjectControlled());
        assertFalse(player.getRolling());
        assertEquals(0x13, player.getYRadius());
        assertEquals(9, player.getXRadius());
        assertEquals((short) 0x04E8, player.getCentreY(),
                "Player_TouchFloor subtracts the rolling-to-standing radius delta before the cage sets angle");
        assertEquals((short) 0, player.getXSpeed());
        assertEquals((short) -0x3A8, player.getYSpeed());
        assertEquals((short) -0x330, player.getGSpeed());
        assertEquals((byte) 0x40, player.getAngle());
    }

    @Test
    void heldJumpDuringLatchedCooldownDoesNotReleaseUntilFreshPress() {
        CnzWireCageObjectInstance cage = new CnzWireCageObjectInstance(new ObjectSpawn(
                0x1D80, 0x0540, Sonic3kObjectIds.CNZ_WIRE_CAGE, 0x18, 1, false, 0xA540));
        cage.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();

        player.setAir(true);
        player.setRolling(true);
        player.applyCustomRadii(7, 14);
        player.setCentreX((short) 0x1DD0);
        player.setCentreY((short) 0x04ED);
        player.setAngle((byte) 0);
        player.setXSpeed((short) -0x573);
        player.setYSpeed((short) -0x3A8);
        player.setGSpeed((short) -0x330);

        player.setJumpInputPressed(true);
        cage.update(0, player);
        player.setJumpInputPressed(true);
        cage.update(1, player);

        assertTrue(player.isOnObject());
        assertFalse(player.getAir());
        assertTrue(player.isObjectControlled());
        assertEquals((byte) 0x40, player.getAngle());
        assertEquals((short) 0, player.getXSpeed());
        assertEquals((short) -0x3A8, player.getYSpeed());

        player.setJumpInputPressed(false);
        player.setJumpInputPressed(true);
        cage.update(2, player);

        assertFalse(player.isOnObject());
        assertTrue(player.getAir());
        assertEquals((short) 0x0800, player.getXSpeed());
        assertEquals((short) JUMP_RELEASE_Y_SPEED_FOR_TEST, player.getYSpeed());
    }

    @Test
    void jumpReleasePreservesRomCentrePositionWhileRestoringStandingRadii() {
        CnzWireCageObjectInstance cage = new CnzWireCageObjectInstance(new ObjectSpawn(
                0x1D80, 0x0540, Sonic3kObjectIds.CNZ_WIRE_CAGE, 0x18, 0, false, 0));
        cage.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x1D80);
        player.setCentreY((short) 0x04F3);
        player.setAir(false);
        player.setGSpeed((short) 0x0C00);
        cage.update(0, player);

        player.setRolling(true);
        player.setCentreX((short) 0x1DB4);
        player.setCentreY((short) 0x0496);
        player.setAir(true);
        player.setJumping(true);
        short releaseX = player.getCentreX();
        short releaseY = player.getCentreY();

        cage.update(1, player);

        assertEquals(releaseX, player.getCentreX(),
                "Obj_CNZWireCage release restores radii without changing ROM x_pos");
        assertEquals(releaseY, player.getCentreY(),
                "Obj_CNZWireCage release restores radii without changing ROM y_pos");
        assertEquals(0x13, player.getYRadius());
        assertEquals(9, player.getXRadius());
        assertEquals((short) JUMP_RELEASE_Y_SPEED_FOR_TEST, player.getYSpeed());
        assertFalse(player.getRolling());
    }

    @Test
    void lowSpeedReleaseSetsObjectControlBitZeroForReleaseRide() {
        CnzWireCageObjectInstance cage = new CnzWireCageObjectInstance(new ObjectSpawn(
                0x1D80, 0x0540, Sonic3kObjectIds.CNZ_WIRE_CAGE, 0x18, 0, false, 0));
        cage.setServices(new TestObjectServices());
        AbstractPlayableSprite player = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();

        player.setCentreX((short) 0x1D80);
        player.setCentreY((short) 0x04F3);
        player.setAir(false);
        player.setGSpeed((short) 0x0300);
        cage.update(0, player);

        player.setGSpeed((short) 0x02F7);
        cage.update(1, player);

        assertTrue(player.isObjectControlled(),
                "loc_339B6 sets object_control bit 0 before entering the release ride path");
        assertTrue(player.isControlLocked(),
                "The one-frame release cooldown mirrors the ROM byte at 1(a2)");
        assertTrue(player.isOnObject());
        assertFalse(player.getAir());
    }

    @Test
    void leaderDplcFrameChangeSkipsSidekickMountedRideForOneFrame() {
        CnzWireCageObjectInstance cage = new CnzWireCageObjectInstance(new ObjectSpawn(
                0x1300, 0x07C0, Sonic3kObjectIds.CNZ_WIRE_CAGE, 0x1E, 0, false, 0));
        AbstractPlayableSprite leader = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build()
                .sprite();
        Tails sidekick = new Tails("tails", (short) 0, (short) 0);
        cage.setServices(new TestObjectServices().withSidekicks(List.of(sidekick)));

        leader.setCentreX((short) 0x1300);
        leader.setCentreY((short) 0x07C0);
        leader.setAir(false);
        leader.setGSpeed((short) 0x0800);
        sidekick.setCentreX((short) 0x1300);
        sidekick.setCentreY((short) 0x07C0);
        sidekick.setAir(false);
        sidekick.setGSpeed((short) 0x0800);

        cage.update(0, leader);
        short sidekickXAfterLatch = sidekick.getCentreX();
        short sidekickYAfterLatch = sidekick.getCentreY();
        int sidekickFrameAfterLatch = sidekick.getMappingFrame();
        sidekick.setAir(true);
        sidekick.setOnObject(false);

        cage.update(1, leader);

        assertNotEquals(0, leader.getMappingFrame(),
                "Leader cage rotation changed mapping_frame, so Sonic_Load_PLC2 would clobber d6");
        assertEquals(sidekickXAfterLatch, sidekick.getCentreX(),
                "FixBugs-off d6 corruption makes the P2 btst miss and ROM skips Tails's mounted cage update");
        assertEquals(sidekickYAfterLatch, sidekick.getCentreY());
        assertEquals(sidekickFrameAfterLatch, sidekick.getMappingFrame());
        assertFalse(sidekick.getAir(),
                "The skipped P2 call still sees ROM's persistent Status_OnObj/grounded latch");
        assertTrue(sidekick.isOnObject());
    }

    private static final int JUMP_RELEASE_Y_SPEED_FOR_TEST = -0x200;
}
