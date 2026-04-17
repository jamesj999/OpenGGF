package com.openggf.tests;

import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.game.sonic3k.objects.CnzBalloonInstance;
import com.openggf.game.sonic3k.objects.CnzRisingPlatformInstance;
import com.openggf.level.objects.DefaultObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
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
    void balloonSubtypeZeroLaunchesWithoutSnappingPlayerX() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzBalloonInstance balloon = spawnBalloon(0x19C0, 0x05B0, 0x00);
        fixture.sprite().setCentreX((short) 0x19B8);
        fixture.sprite().setCentreY((short) 0x05A8);

        balloon.update(0, fixture.sprite());

        assertTrue(fixture.sprite().getAir(), "CNZ balloon contact should launch the player into the air");
        assertEquals((short) -0x700, fixture.sprite().getYSpeed(),
                "Subtype 0x00 should use the standard launch speed");
        assertEquals(0x19B8, fixture.sprite().getCentreX(),
                "Subtype 0x00 must not snap the player to the balloon centre");
        assertTrue(invokeBooleanHook(balloon, "isPoppedForTest"),
                "Balloon should switch into its popped render state after launch");
        assertEquals(3, invokeIntHook(balloon, "getRenderFrameForTest"),
                "Subtype 0x00 should render the popped frame after launch");
    }

    @Test
    void balloonSubtype80DoesNotSnapPlayerWhenDryAndUsesLowerLaunchSpeed() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzBalloonInstance balloon = spawnBalloon(0x19C0, 0x05B0, 0x80);
        fixture.sprite().setCentreX((short) 0x19B8);
        fixture.sprite().setCentreY((short) 0x05A8);

        assertFalse(fixture.sprite().isInWater(),
                "The regression should be exercising the dry seam, not the underwater case");

        balloon.update(0, fixture.sprite());

        assertEquals(0x19B8, fixture.sprite().getCentreX(),
                "Dry negative subtype balloons should not snap the player horizontally");
        assertEquals(0x05A8, fixture.sprite().getCentreY(),
                "Dry negative subtype balloons should not snap the player vertically");
        assertEquals((short) -0x380, fixture.sprite().getYSpeed(),
                "Negative subtype balloons use the lower upward launch speed");
        assertTrue(invokeBooleanHook(balloon, "isPoppedForTest"));
    }

    @Test
    void poppedBalloonDoesNotLaunchAgainOnLaterFrame() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzBalloonInstance balloon = spawnBalloon(0x19C0, 0x05B0, 0x00);
        fixture.sprite().setCentreX((short) 0x19B8);
        fixture.sprite().setCentreY((short) 0x05A8);

        balloon.update(0, fixture.sprite());
        assertTrue(invokeBooleanHook(balloon, "isPoppedForTest"));

        fixture.sprite().setCentreX((short) 0x19B8);
        fixture.sprite().setCentreY((short) 0x05A8);
        fixture.sprite().setYSpeed((short) 0x120);
        balloon.update(1, fixture.sprite());

        assertEquals((short) 0x120, fixture.sprite().getYSpeed(),
                "Popped balloons should stop responding to later touches");
        assertEquals(3, invokeIntHook(balloon, "getRenderFrameForTest"),
                "Popped balloons should keep the popped visual frame");
    }

    @Test
    void risingPlatformCompressesWhileStandingThenSpringsBackToFloor() {
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withZoneAndAct(Sonic3kZoneIds.ZONE_CNZ, 0)
                .build();

        CnzRisingPlatformInstance platform = spawnRisingPlatform(0x1D80, 0x06A0, 0x20);
        fixture.sprite().setCentreX((short) 0x1D80);
        fixture.sprite().setCentreY((short) 0x068C);
        fixture.sprite().setAir(false);
        fixture.sprite().setRolling(false);

        for (int frame = 0; frame < 60; frame++) {
            platform.onSolidContact(fixture.sprite(), new SolidContact(true, false, false, true, false), frame);
            platform.update(frame, fixture.sprite());
        }

        assertTrue(invokeBooleanHook(platform, "isArmedForTest"),
                "Standing on the platform should arm the rising state machine");
        int settledY = platform.getSpawn().y();
        assertTrue(invokeIntHook(platform, "getYSpeedForTest") > 0,
                "Floor contact should preserve the carried compression while still standing");
        assertEquals(2, invokeIntHook(platform, "getRenderFrameForTest"),
                "Floor contact while carried should switch the platform to the settled frame");

        for (int frame = 60; frame < 70; frame++) {
            platform.onSolidContact(fixture.sprite(), new SolidContact(true, false, false, true, false), frame);
            platform.update(frame, fixture.sprite());
            assertEquals(settledY, platform.getSpawn().y(),
                    "Continued standing should not let the platform tunnel through the floor");
            assertTrue(invokeIntHook(platform, "getYSpeedForTest") > 0,
                    "Settled platform should preserve its carried compression while standing");
            assertEquals(2, invokeIntHook(platform, "getRenderFrameForTest"),
                    "Settled platform should keep the resting frame while carried");
        }

        int storedCompression = invokeIntHook(platform, "getYSpeedForTest");
        platform.update(60, fixture.sprite());
        int releaseY = platform.getSpawn().y();
        assertFalse(invokeBooleanHook(platform, "isArmedForTest"),
                "Stepping off should clear the armed flag");
        assertEquals(-storedCompression - 0x80, invokeIntHook(platform, "getYSpeedForTest"),
                "Stepping off should bounce from the stored compression, not a clamped floor velocity");
        assertEquals(2, invokeIntHook(platform, "getRenderFrameForTest"),
                "Release should switch the platform to the settling frame");

        for (int frame = 61; frame < 120; frame++) {
            platform.update(frame, fixture.sprite());
            if (platform.getSpawn().y() == settledY && invokeIntHook(platform, "getYSpeedForTest") == 0) {
                break;
            }
        }

        assertTrue(platform.getSpawn().y() < releaseY,
                "The platform should settle upward onto its floor position");
        assertEquals(0, invokeIntHook(platform, "getYSpeedForTest"),
                "Settled platforms should come to rest");
        assertEquals(2, invokeIntHook(platform, "getRenderFrameForTest"),
                "Settled platforms should keep the resting frame");
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
