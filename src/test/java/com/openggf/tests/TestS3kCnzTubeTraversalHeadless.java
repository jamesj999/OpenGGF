package com.openggf.tests;

import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CnzVacuumTubeInstance;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzTubeTraversalHeadless {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void tubeSourceDescriptionsReflectTheVerifiedVacuumAndSpiralPreconditions() {
        assertEquals(Sonic3kObjectIds.CNZ_VACUUM_TUBE, invokeStaticInt("vacuumObjectId"));
        assertEquals(Sonic3kObjectIds.CNZ_SPIRAL_TUBE, invokeStaticInt("spiralObjectId"));
        assertEquals(4, invokeStaticInt("spiralPathCount"));

        String vacuumSource = invokeStaticString("describeVacuumSource");
        String spiralSource = invokeStaticString("describeSpiralSource");

        assertTrue(vacuumSource.contains("inline"),
                "Vacuum Tube should document its inline controller flow rather than a fake external route table");
        assertTrue(vacuumSource.contains("S&K"),
                "Vacuum Tube should explicitly record that the verified controller logic comes from the S&K half");
        assertTrue(spiralSource.contains("off_33320"),
                "Spiral Tube should document the verified off_33320 table family");
        assertTrue(spiralSource.contains("S&K"),
                "Spiral Tube should explicitly record that its route tables come from the S&K half");
    }

    @Test
    void vacuumTubeSubtypeZeroUsesFacingBitForHorizontalBoostWithoutObjectControl() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzVacuumTubeInstance tube = spawnVacuumTube(0x3EC0, 0x07F0, 0x00, 0x01);
        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x3EA0);
        player.setCentreY((short) 0x07D0);
        player.setAir(false);
        player.setJumping(false);
        player.setControlLocked(false);
        player.setObjectControlled(false);
        player.setXSpeed((short) 0);
        player.setGSpeed((short) 0);

        tube.update(0, player);

        assertFalse(player.isControlLocked(),
                "Subtype 0x00 should keep player input free because sub_31F62 never sets object_control");
        assertFalse(player.isObjectControlled(),
                "Subtype 0x00 should behave like the inline booster field, not a captured path object");
        assertEquals(0x1000, player.getXSpeed(),
                "Facing/status bit 0 should decide the horizontal boost direction for subtype 0x00");
        assertEquals(0x1000, player.getGSpeed(),
                "Subtype 0x00 should mirror the boost into ground_vel as well as x_vel");
    }

    @Test
    void vacuumTubeLiftSubtypesUseSubtypeScaledTimersThenReleaseUpwardForStockPlacements() {
        verifyLiftSubtype(0x10);
        verifyLiftSubtype(0x20);
        verifyLiftSubtype(0x30);
    }

    private void verifyLiftSubtype(int subtype) {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzVacuumTubeInstance tube = spawnVacuumTube(0x4740, 0x0828, subtype, 0);
        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x4748);
        player.setCentreY((short) 0x0800);
        player.setAir(false);
        player.setJumping(true);
        player.setControlLocked(false);
        player.setObjectControlled(false);
        player.setXSpeed((short) 0x120);
        player.setYSpeed((short) 0x0100);
        player.setGSpeed((short) 0x0200);

        int initialY = player.getCentreY();
        int configuredLiftFrames = subtype * 2;

        tube.update(0, player);

        assertFalse(player.isControlLocked(),
                "Lift subtypes should keep player control free because sub_32010 never sets object_control");
        assertFalse(player.isObjectControlled(),
                "Lift subtypes should use the inline carry timer instead of object ownership");
        assertTrue(player.getAir(),
                "Lift capture should force the player airborne on the first capture frame");
        assertTrue(player.getCentreY() < initialY,
                "Lift capture should immediately raise the player toward the tube centre");
        assertEquals(0x0F, player.getAnimationId(),
                "Lift capture should force animation $0F from sub_32010");

        for (int frame = 1; frame < configuredLiftFrames; frame++) {
            short previousY = player.getCentreY();
            tube.update(frame, player);
            assertEquals(previousY - 8, player.getCentreY(),
                    "Active lift frames should rise by 8 pixels each frame for subtype 0x"
                            + Integer.toHexString(subtype));
            assertEquals(0, player.getXSpeed(),
                    "Active lift frames should zero x_vel while the player is being carried");
            assertEquals(0, player.getGSpeed(),
                    "Active lift frames should zero ground_vel while the player is being carried");
            assertEquals(0, player.getYSpeed(),
                    "Active lift frames should keep y_vel cleared until the release frame");
        }

        short releaseY = player.getCentreY();
        tube.update(configuredLiftFrames, player);

        assertEquals(releaseY, player.getCentreY(),
                "The release frame should stop the forced 8px lift and hand off to the launch velocity");
        assertEquals(-0x800, player.getYSpeed(),
                "When the subtype-scaled timer expires, the tube should launch upward with y_vel=-$800");
        assertFalse(player.isObjectControlled(),
                "Release should leave object_control clear because the vacuum tube never claimed it");
    }

    private static CnzVacuumTubeInstance spawnVacuumTube(int x, int y, int subtype, int renderFlags) {
        CnzVacuumTubeInstance object = new CnzVacuumTubeInstance(
                new ObjectSpawn(x, y, Sonic3kObjectIds.CNZ_VACUUM_TUBE, subtype, renderFlags, false, 0));
        object.setServices(new DefaultObjectServices(RuntimeManager.getCurrent()));
        return object;
    }

    private static int invokeStaticInt(String methodName) {
        try {
            return (int) tubePathTablesMethod(methodName).invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Failed to invoke CnzTubePathTables." + methodName, e);
        }
    }

    private static String invokeStaticString(String methodName) {
        try {
            return (String) tubePathTablesMethod(methodName).invoke(null);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AssertionError("Failed to invoke CnzTubePathTables." + methodName, e);
        }
    }

    private static Method tubePathTablesMethod(String methodName) {
        try {
            Class<?> type = Class.forName("com.openggf.game.sonic3k.objects.CnzTubePathTables");
            return type.getDeclaredMethod(methodName);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("CnzTubePathTables should expose " + methodName, e);
        }
    }
}
