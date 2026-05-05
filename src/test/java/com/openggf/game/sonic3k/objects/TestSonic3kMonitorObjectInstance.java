package com.openggf.game.sonic3k.objects;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestSonic3kMonitorObjectInstance {

    private static final TouchResponseResult TOUCH_RESULT =
            new TouchResponseResult(0, 0x0E, 0x0E, TouchCategory.SPECIAL);

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        SessionManager.clear();
        RuntimeManager.createGameplay();
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
        SessionManager.clear();
        GameModuleRegistry.reset();
    }

    @Test
    void touchFromAboveRequiresRollAnimationNotJustRollingStatus() {
        Sonic3kMonitorObjectInstance monitor = monitor();
        DummyPlayer player = new DummyPlayer();
        player.setRolling(true);
        player.setAnimationId(Sonic3kAnimationIds.SPRING);
        player.setYSpeed((short) 0x05A0);

        monitor.onTouchResponse(player, TOUCH_RESULT, 1);

        assertEquals(0x46, monitor.getCollisionFlags(),
                "S3K Touch_Monitor checks anim == AniIDSonAni_Roll, not just the rolling status bit");
        assertEquals(0x05A0, player.getYSpeed() & 0xFFFF,
                "Blocked monitor hits must leave the player's Y speed unchanged");
        assertTrue(monitor.isSolidFor(player),
                "SolidObject_Monitor_SonicKnux uses the same animation-id gate");
    }

    @Test
    void touchFromAboveBreaksMonitorWhenRollAnimationIsActive() {
        Sonic3kMonitorObjectInstance monitor = monitor();
        DummyPlayer player = new DummyPlayer();
        player.setRolling(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL);
        player.setYSpeed((short) 0x05A0);

        monitor.onTouchResponse(player, TOUCH_RESULT, 1);

        assertEquals(0, monitor.getCollisionFlags());
        assertEquals(0xFA60, player.getYSpeed() & 0xFFFF,
                "Breaking the monitor should negate the player's downward Y speed");
        assertFalse(monitor.isSolidFor(player));
    }

    @Test
    void breakWithPriorPushingContactReleasesPlayerIntoAir() {
        Sonic3kMonitorObjectInstance monitor = monitor();
        DummyPlayer player = new DummyPlayer();
        player.setRolling(true);
        player.setAnimationId(Sonic3kAnimationIds.ROLL);
        player.setYSpeed((short) 0);
        player.setAir(false);
        player.setPushing(true);

        monitor.setPlayerPushing(player, true);
        monitor.onTouchResponse(player, TOUCH_RESULT, 1);

        assertTrue(player.getAir(),
                "Obj_MonitorBreak sets Status_InAir when the monitor still has p1_pushing set");
        assertFalse(player.getPushing(),
                "Obj_MonitorBreak clears the player's pushing status via andi.b #$D7");
    }

    @Test
    void solidContactLandsAtZeroDistanceUsingS3kSolidObjectVerticalOffset() {
        TestObjectServices services = new TestObjectServices();
        ObjectManager objectManager = new ObjectManager(
                List.of(), null, 0, null, null, null, null, services);
        Sonic3kMonitorObjectInstance monitor = new Sonic3kMonitorObjectInstance(
                new ObjectSpawn(0x0840, 0x06E9, 0x01, 0x03, 0, false, 0));
        objectManager.addDynamicObject(monitor);
        monitor.snapshotPreUpdatePosition();

        DummyPlayer player = new DummyPlayer();
        player.setWidth(18);
        player.setHeight(38);
        player.setCentreX((short) 0x0842);
        player.setCentreY((short) 0x06C2);
        player.setXSpeed((short) 0x06B6);
        player.setGSpeed((short) 0x06B6);
        player.setYSpeed((short) 0x01C0);
        player.setAir(true);

        objectManager.updateSolidContacts(player);

        assertEquals(0x06C5, player.getCentreY() & 0xFFFF);
        assertEquals(0, player.getYSpeed());
        assertFalse(player.getAir());
        assertTrue(player.isOnObject());
    }

    private static Sonic3kMonitorObjectInstance monitor() {
        Sonic3kMonitorObjectInstance monitor = new Sonic3kMonitorObjectInstance(
                new ObjectSpawn(0x1E30, 0x0530, 0x01, 0x03, 0, false, 0));
        monitor.setServices(new TestObjectServices());
        return monitor;
    }

    private static final class DummyPlayer extends AbstractPlayableSprite {
        private DummyPlayer() {
            super("sonic", (short) 0x1E30, (short) 0x0500);
        }

        @Override
        protected void defineSpeeds() {
            runAccel = 0;
            runDecel = 0;
            friction = 0;
            max = 0;
            jump = 0;
            angle = 0;
            slopeRunning = 0;
            slopeRollingDown = 0;
            slopeRollingUp = 0;
            rollDecel = 0;
            minStartRollSpeed = 0;
            minRollSpeed = 0;
            maxRoll = 0;
            rollHeight = 0;
            runHeight = 0;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override
        public void draw() {
        }
    }
}
