package com.openggf.tests;

import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CnzCannonInstance;
import com.openggf.game.sonic3k.objects.CnzCylinderInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kCnzDirectedTraversalHeadless {

    private static final int CANNON_X = 0x1600;
    private static final int CANNON_Y = 0x0680;

    @Test
    void cnzCannonCapturesForcesRollingAndLaunchesThePlayer() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) CANNON_X);
        player.setCentreY((short) (CANNON_Y - 0x20));
        player.setAir(false);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setRolling(false);
        player.setControlLocked(false);
        player.setObjectControlled(false);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCannonInstance cannon = new CnzCannonInstance(new ObjectSpawn(
                CANNON_X, CANNON_Y, Sonic3kObjectIds.CNZ_CANNON, 0, 0, false, 0));
        objectManager.addDynamicObject(cannon);

        fixture.camera().updatePosition(true);

        boolean captured = false;
        for (int i = 0; i < 12; i++) {
            fixture.stepFrame(false, false, false, false, false);
            if (player.isObjectControlled()) {
                captured = true;
                break;
            }
        }

        assertTrue(captured, "CNZ cannon should capture the player before launching");
        assertTrue(player.isControlLocked(), "CNZ cannon should lock player control while captured");
        assertTrue(player.getRolling(), "CNZ cannon should force rolling state while captured");
        assertEquals(7, player.getXRadius(), "CNZ cannon should use the ROM rolling x-radius");
        assertEquals(14, player.getYRadius(), "CNZ cannon should use the ROM rolling y-radius");

        invokeLaunchDelayHook(cannon, 0);

        player.setJumpInputPressed(true);
        fixture.stepFrame(false, false, false, false, true);

        assertFalse(player.isObjectControlled(), "CNZ cannon should release object control after launch");
        assertFalse(player.isControlLocked(), "CNZ cannon should release control lock after launch");
        assertTrue(player.getXSpeed() != 0 || player.getYSpeed() != 0,
                "CNZ cannon should impart a launch vector");
    }

    @Test
    void cnzCylinderCapturesPlayerAppliesRollingRadiiAndReleasesAtSubtypeExit() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        AbstractPlayableSprite player = fixture.sprite();
        player.setCentreX((short) 0x38C0);
        player.setCentreY((short) 0x07F0);
        player.setAir(false);
        player.setXSpeed((short) 0);
        player.setYSpeed((short) 0);
        player.setGSpeed((short) 0);
        player.setRolling(false);
        player.setControlLocked(false);
        player.setObjectControlled(false);

        ObjectManager objectManager = GameServices.level().getObjectManager();
        CnzCylinderInstance cylinder = new CnzCylinderInstance(new ObjectSpawn(
                0x38C0, 0x0800, Sonic3kObjectIds.CNZ_CYLINDER, 0x01, 0, false, 0));
        objectManager.addDynamicObject(cylinder);

        fixture.camera().updatePosition(true);

        boolean captured = false;
        for (int i = 0; i < 12; i++) {
            fixture.stepFrame(false, false, false, false, false);
            if (player.isObjectControlled()) {
                captured = true;
                break;
            }
        }

        assertTrue(captured, "CNZ cylinder should capture the player before traversal");
        assertTrue(player.isControlLocked(), "CNZ cylinder should lock player control while captured");
        assertTrue(player.getRolling(), "CNZ cylinder should force rolling state while captured");
        assertEquals(7, player.getXRadius(), "CNZ cylinder should use the ROM rolling x-radius");
        assertEquals(14, player.getYRadius(), "CNZ cylinder should use the ROM rolling y-radius");

        int routeFrames = invokeCylinderHook(cylinder, "getRouteFrameCountForTest");
        int expectedExitX = invokeCylinderHook(cylinder, "getExpectedExitXForTest");
        int expectedExitY = invokeCylinderHook(cylinder, "getExpectedExitYForTest");

        for (int frame = 1; frame <= routeFrames; frame++) {
            fixture.stepFrame(false, false, false, false, false);
        }

        assertFalse(player.isControlLocked(), "CNZ cylinder should release control lock at route exit");
        assertFalse(player.isObjectControlled(), "CNZ cylinder should release object control at route exit");
        assertEquals(expectedExitX, player.getCentreX(), "CNZ cylinder should release at the documented exit X");
        assertEquals(expectedExitY, player.getCentreY(), "CNZ cylinder should release at the documented exit Y");
    }

    private static void invokeLaunchDelayHook(CnzCannonInstance cannon, int frames) {
        try {
            Method method = CnzCannonInstance.class.getDeclaredMethod("setLaunchDelayFramesForTest", int.class);
            method.setAccessible(true);
            method.invoke(cannon, frames);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to set CNZ cannon launch delay for test", e);
        }
    }

    private static int invokeCylinderHook(CnzCylinderInstance cylinder, String methodName) {
        try {
            Method method = CnzCylinderInstance.class.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (int) method.invoke(cylinder);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke CNZ cylinder hook " + methodName, e);
        }
    }
}
