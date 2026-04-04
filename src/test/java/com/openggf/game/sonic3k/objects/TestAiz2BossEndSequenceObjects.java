package com.openggf.game.sonic3k.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SolidContact;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.tests.TestablePlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestAiz2BossEndSequenceObjects {

    @AfterEach
    void tearDown() {
        Aiz2BossEndSequenceState.reset();
        Camera.getInstance().resetState();
    }

    @Test
    void cutsceneButtonPressesWhenKnucklesReachesIt() throws Exception {
        S3kCutsceneButtonObjectInstance button =
                new S3kCutsceneButtonObjectInstance(new ObjectSpawn(0x4B18, 0x0189, 0x83, 0, 0, false, 0));
        button.setServices(new TestObjectServices());

        CutsceneKnucklesAiz2Instance knuckles = CutsceneKnucklesAiz2Instance.createDefault();
        setField(knuckles, "currentX", 0x4B10);
        setField(knuckles, "currentY", 0x0188);
        Aiz2BossEndSequenceState.setActiveKnuckles(knuckles);

        button.update(0, null);

        assertTrue(Aiz2BossEndSequenceState.isButtonPressed());
    }

    @Test
    void drawBridgeDropsPlayersIntoHurtFallAfterButtonPress() {
        AizDrawBridgeObjectInstance bridge =
                new AizDrawBridgeObjectInstance(new ObjectSpawn(0x4B48, 0x0218, 0x32, 0, 2, false, 0));
        bridge.setServices(new TestObjectServices().withGameState(new GameStateManager()));

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x4B48);
        player.setCentreY((short) 0x0210);
        player.setOnObject(true);
        bridge.onSolidContact(player, new SolidContact(true, false, false, true, false), 0);

        Aiz2BossEndSequenceState.triggerBridgeDrop();
        for (int i = 0; i < 40; i++) {
            bridge.update(i, player);
        }
        assertTrue(bridge.isSolidFor(player));

        Aiz2BossEndSequenceState.pressButton();
        for (int i = 0; i < 20; i++) {
            bridge.update(40 + i, player);
        }

        assertTrue(player.getAir());
        assertEquals(Sonic3kAnimationIds.HURT_FALL.id(), player.getAnimationId());
    }

    @Test
    void eggCapsuleReleasesControllerAfterResultsFinish() throws Exception {
        Camera camera = Camera.getInstance();
        camera.resetState();
        camera.setX((short) 0x4880);
        camera.setY((short) 0x0100);

        GameStateManager gameState = new GameStateManager();
        TestObjectServices services = new TestObjectServices()
                .withCamera(camera)
                .withGameState(gameState);

        Aiz2EndEggCapsuleInstance capsule = Aiz2EndEggCapsuleInstance.createForCamera(
                camera.getX() & 0xFFFF, camera.getY() & 0xFFFF);
        capsule.setServices(services);

        assertFalse(Aiz2BossEndSequenceState.isEggCapsuleReleased());
        setField(capsule, "currentY", 0x0140);
        setField(capsule, "resultsStarted", 1);
        gameState.setEndOfLevelFlag(true);
        capsule.update(0, null);

        assertTrue(Aiz2BossEndSequenceState.isEggCapsuleReleased());
    }

    @Test
    void controllerWaitsForEggCapsuleBeforeStartingWalkAndHydrocityTransition() {
        Camera camera = Camera.getInstance();
        camera.resetState();
        camera.setMaxX((short) 0x4880);
        camera.setMaxY((short) 0x0200);
        camera.setX((short) 0x4880);
        camera.setY((short) 0x0100);

        TestablePlayableSprite player = new TestablePlayableSprite("sonic", (short) 0, (short) 0);
        player.setCentreX((short) 0x4A60);
        player.setCentreY((short) 0x0210);
        player.setTestY((short) 0x0170);

        RecordingServices services = new RecordingServices();
        services.withCamera(camera);
        services.withGameState(new GameStateManager());

        Aiz2BossEndSequenceController controller = new Aiz2BossEndSequenceController(0x4880, 0x0000);
        controller.setServices(services);

        controller.update(0, player);
        assertEquals(0x4880, camera.getMaxXTarget() & 0xFFFF);
        assertFalse(player.isControlLocked());
        assertFalse(player.isForceInputRight());

        Aiz2BossEndSequenceState.releaseEggCapsule();
        controller.update(1, player);
        assertEquals(0x49D8, camera.getMaxXTarget() & 0xFFFF);
        assertTrue(player.isControlLocked());
        assertTrue(player.isForceInputRight());

        for (int i = 0; i < 16; i++) {
            camera.updateBoundaryEasing();
            controller.update(i + 2, player);
        }
        assertTrue((camera.getMaxX() & 0xFFFF) > 0x4880);

        player.setCentreX((short) 0x4A80);
        Aiz2BossEndSequenceState.pressButton();
        player.setTestY((short) 0x01F0);
        controller.update(100, player);

        assertEquals(Sonic3kZoneIds.ZONE_HCZ, services.requestedZone);
        assertEquals(0, services.requestedAct);
    }

    private static void setField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        if (field.getType() == boolean.class) {
            field.setBoolean(target, value != 0);
        } else {
            field.setInt(target, value);
        }
    }

    private static final class RecordingServices extends TestObjectServices {
        int requestedZone = -1;
        int requestedAct = -1;

        @Override
        public void requestZoneAndAct(int zone, int act) {
            requestedZone = zone;
            requestedAct = act;
        }
    }
}
