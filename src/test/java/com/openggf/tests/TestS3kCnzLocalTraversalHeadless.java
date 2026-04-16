package com.openggf.tests;

import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CnzBalloonInstance;
import com.openggf.game.sonic3k.objects.CnzRisingPlatformInstance;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
class TestS3kCnzLocalTraversalHeadless {

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        com.openggf.game.session.SessionManager.clear();
    }

    @Test
    void balloonLaunchesPlayerUsingCentreCoordinatesAndRomBounceSpeed() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzBalloonInstance balloon = spawnBalloon(0x19C0, 0x05B0, 0x00);
        fixture.sprite().setCentreX((short) 0x19C0);
        fixture.sprite().setCentreY((short) 0x05A8);
        fixture.sprite().setYSpeed((short) 0x0200);

        balloon.update(0, fixture.sprite());

        assertTrue(fixture.sprite().getAir(), "CNZ balloon contact should launch the player into the air");
        assertTrue(fixture.sprite().getYSpeed() < 0, "Balloon should reverse Y motion into an upward launch");
        assertEquals(0x19C0, fixture.sprite().getCentreX(),
                "ROM x_pos writes must use centre coordinates");
    }

    @Test
    void risingPlatformStartsOnContactMovesToSubtypeLimitAndStops() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzRisingPlatformInstance platform = spawnRisingPlatform(0x1D80, 0x06A0, 0x20);
        fixture.sprite().setCentreX((short) 0x1D80);
        fixture.sprite().setCentreY((short) 0x0690);
        fixture.sprite().setAir(false);
        fixture.sprite().setRolling(false);

        for (int frame = 0; frame < 96; frame++) {
            platform.update(frame, fixture.sprite());
        }

        int travelPixels = invokeIntHook(platform, "getSubtypeTravelForTest");
        assertTrue(invokeBooleanHook(platform, "wasTriggeredForTest"),
                "Standing on the platform should arm the rising state machine");
        assertEquals(0x06A0 - travelPixels, platform.getSpawn().y(),
                "The platform should stop at the subtype-defined travel limit");
        assertEquals(0, invokeIntHook(platform, "getYSpeedForTest"),
                "Once the platform reaches its limit, its motion should stop");
        assertFalse(fixture.sprite().getAir(),
                "The test player should remain grounded while the platform is carrying them");
    }

    private static CnzBalloonInstance spawnBalloon(int x, int y, int subtype) {
        CnzBalloonInstance object = new CnzBalloonInstance(
                new ObjectSpawn(x, y, Sonic3kObjectIds.CNZ_BALLOON, subtype, 0, false, 0));
        object.setServices(new DefaultObjectServices(RuntimeManager.getCurrent()));
        return object;
    }

    private static CnzRisingPlatformInstance spawnRisingPlatform(int x, int y, int subtype) {
        CnzRisingPlatformInstance object = new CnzRisingPlatformInstance(
                new ObjectSpawn(x, y, Sonic3kObjectIds.CNZ_RISING_PLATFORM, subtype, 0, false, 0));
        object.setServices(new DefaultObjectServices(RuntimeManager.getCurrent()));
        return object;
    }

    private static boolean invokeBooleanHook(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (boolean) method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read hook " + methodName, e);
        }
    }

    private static int invokeIntHook(Object target, String methodName) {
        try {
            Method method = target.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return (int) method.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read hook " + methodName, e);
        }
    }
}
