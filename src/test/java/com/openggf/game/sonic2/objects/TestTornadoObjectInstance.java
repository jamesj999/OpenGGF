package com.openggf.game.sonic2.objects;

import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic3k.objects.AizPlaneIntroInstance;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.TestObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.physics.Sensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestTornadoObjectInstance {

    private GameModule previousModule;

    @BeforeEach
    public void setUp() {
        RuntimeManager.createGameplay();
        previousModule = GameModuleRegistry.getCurrent();
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
        GameServices.camera().resetState();
        GameServices.sprites().resetState();
        GameServices.level().resetState();
        AizPlaneIntroInstance.resetIntroPhaseState();
    }

    @AfterEach
    public void tearDown() {
        GameServices.sprites().resetState();
        GameServices.level().resetState();
        GameModuleRegistry.setCurrent(previousModule);
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void moveObeyPlayerClampsPlayerToTornado() throws Exception {
        TornadoObjectInstance tornado = createTornado(100, 0x100, 0x50);
        TestPlayableSprite main = new TestPlayableSprite("main", (short) 200, (short) 100);

        invokePrivate(tornado, "moveObeyPlayer",
                new Class<?>[]{AbstractPlayableSprite.class, boolean.class}, main, true);

        // ROM: move.w x_pos(a1),d1 / add.w d3,d1 / move.w d1,x_pos(a0)
        // Moves TORNADO to follow PLAYER. Player at 200, tornado follows to 200-16=184.
        assertEquals(184, tornado.getX(), "ObjB2_Move_obbey_player should move Tornado to player - 16");
        assertEquals(200, main.getCentreX(), "Player should not be moved");
    }

    @Test
    public void moveWithPlayerTransitionUsesClosestPlayerAsAnchor() throws Exception {
        TornadoObjectInstance tornado = createTornado(150, 0x100, 0x50);
        TestPlayableSprite main = new TestPlayableSprite("main", (short) 300, (short) 100);
        TestPlayableSprite sidekick = new TestPlayableSprite("sidekick", (short) 140, (short) 100);
        sidekick.setCpuControlled(true);

        GameServices.sprites().addSprite(main);
        GameServices.sprites().addSprite(sidekick);
        setField(tornado, "standingTransition", true);

        invokePrivate(tornado, "moveWithPlayer",
                new Class<?>[]{AbstractPlayableSprite.class, boolean.class}, main, false);

        assertEquals(149, tornado.getX(), "ObjB2_Move_below_player should anchor to closest player on transition");
    }

    @Test
    public void moveWithPlayerTransitionUsesClosestPlayerInScz() throws Exception {
        // ROM: Obj_GetOrientationToPlayer always considers both players, even in SCZ.
        // SpriteManager suppresses CPU sidekick for SCZ, so this tests that the
        // Tornado correctly uses the main player when sidekick is not available.
        TornadoObjectInstance tornado = createTornado(150, 0x100, 0x50);
        TestPlayableSprite main = new TestPlayableSprite("main", (short) 300, (short) 100);
        TestPlayableSprite sidekick = new TestPlayableSprite("sidekick", (short) 140, (short) 100);
        sidekick.setCpuControlled(true);

        GameServices.sprites().addSprite(main);
        GameServices.sprites().addSprite(sidekick);
        setField(GameServices.level(), "currentZone", 8);
        setField(tornado, "standingTransition", true);

        invokePrivate(tornado, "moveWithPlayer",
                new Class<?>[]{AbstractPlayableSprite.class, boolean.class}, main, false);

        // SCZ suppresses CPU sidekick (isCpuSidekickSuppressed()), so the Tornado
        // anchors to main player. Sidekick at 140 would give smoothOffsetX=-10 (closer),
        // but it's suppressed, so main at 300 gives smoothOffsetX=150.
        assertEquals(151, tornado.getX(), "SCZ Tornado anchors to main when sidekick is suppressed");
    }

    @Test
    public void unusedMoverUsesCloudVisualsAndMarkObjGoneRange() throws Exception {
        GameServices.camera().setX((short) 0);

        TornadoObjectInstance tornado = createTornado(0x700, 0x100, 0x5A);
        String artKey = (String) invokePrivate(tornado, "resolveRenderArtKey", new Class<?>[0]);

        assertEquals(Sonic2ObjectArtKeys.CLOUDS, artKey);
        assertEquals(6, tornado.getPriorityBucket());

        tornado.update(1, null);
        assertTrue(tornado.isDestroyed(), "Routine C should cull via MarkObjGone");
    }

    @Test
    public void wfzStartUsesDeleteOffScreenCulling() {
        GameServices.camera().setX((short) 0);

        TornadoObjectInstance tornado = createTornado(0x700, 0x100, 0x52);
        tornado.update(1, null);

        assertTrue(tornado.isDestroyed(), "WFZ start routine should delete when outside Obj_DeleteOffScreen range");
    }

    @Test
    public void requestZoneAndActDeactivateLevelFlagIsSet() {
        LevelManager levelManager = GameServices.level();
        levelManager.requestZoneAndAct(9, 0, true);
        assertTrue(levelManager.isLevelInactiveForTransition());

        levelManager.requestZoneAndAct(10, 0);
        assertFalse(levelManager.isLevelInactiveForTransition());
    }

    private static TornadoObjectInstance createTornado(int x, int y, int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(x, y, Sonic2ObjectIds.TORNADO, subtype, 0, false, 0);
        TornadoObjectInstance t = new TornadoObjectInstance(spawn);
        t.setServices(new TestObjectServices()
                .withCamera(GameServices.camera())
                .withParallaxManager(GameServices.parallax())
                .withSpriteManager(GameServices.sprites()));
        return t;
    }

    private static Object invokePrivate(Object target, String methodName, Class<?>[] argTypes, Object... args)
            throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, argTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite(String code, short x, short y) {
            super(code, x, y);
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
            rollHeight = 28;
            runHeight = 38;
        }

        @Override
        protected void createSensorLines() {
            groundSensors = new Sensor[0];
            ceilingSensors = new Sensor[0];
            pushSensors = new Sensor[0];
        }

        @Override
        public void draw() {
            // No-op for unit tests.
        }
    }
}


