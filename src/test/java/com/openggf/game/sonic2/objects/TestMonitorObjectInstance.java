package com.openggf.game.sonic2.objects;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.SessionManager;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.StubObjectServices;
import com.openggf.level.objects.TouchCategory;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.Sensor;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TestMonitorObjectInstance {

    private static final TouchResponseResult TOUCH_RESULT =
            new TouchResponseResult(0, 0x0E, 0x0E, TouchCategory.SPECIAL);

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
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
        ObjectManager objectManager = mock(ObjectManager.class);
        MonitorObjectInstance monitor = new MonitorObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, 0x26, 0x00, 0, false, 0),
                "Monitor");
        monitor.setServices(new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }
        });

        DummyPlayer player = new DummyPlayer();
        player.setRolling(true);
        player.setAnimationId(Sonic2AnimationIds.SPRING);
        player.setYSpeed((short) 0x0120);

        monitor.onTouchResponse(player, TOUCH_RESULT, 1);

        assertFalse(isBroken(monitor),
                "S2 Touch_Monitor checks anim == AniIDSonAni_Roll, not just the rolling status bit");
        assertEquals(0x0120, player.getYSpeed() & 0xFFFF,
                "Blocked monitor hits must leave the player's Y speed unchanged");
        verify(objectManager, never()).markRemembered(monitor.getSpawn());
    }

    @Test
    void touchFromAboveBreaksMonitorWhenRollAnimationIsActive() {
        ObjectManager objectManager = mock(ObjectManager.class);
        MonitorObjectInstance monitor = new MonitorObjectInstance(
                new ObjectSpawn(0x0100, 0x0100, 0x26, 0x00, 0, false, 0),
                "Monitor");
        monitor.setServices(new StubObjectServices() {
            @Override
            public ObjectManager objectManager() {
                return objectManager;
            }
        });

        DummyPlayer player = new DummyPlayer();
        player.setRolling(true);
        player.setAnimationId(Sonic2AnimationIds.ROLL);
        player.setYSpeed((short) 0x0120);

        monitor.onTouchResponse(player, TOUCH_RESULT, 1);

        assertTrue(isBroken(monitor));
        assertEquals(0xFEE0, player.getYSpeed() & 0xFFFF,
                "Breaking the monitor should negate the player's downward Y speed");
        verify(objectManager).markRemembered(monitor.getSpawn());
    }

    private static boolean isBroken(MonitorObjectInstance monitor) {
        try {
            Field field = MonitorObjectInstance.class.getDeclaredField("broken");
            field.setAccessible(true);
            return field.getBoolean(monitor);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class DummyPlayer extends AbstractPlayableSprite {
        private DummyPlayer() {
            super("sonic", (short) 0x0100, (short) 0x0100);
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
