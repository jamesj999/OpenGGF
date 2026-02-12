package uk.co.jamesj999.sonic.game.sonic2.objects;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2ObjectIds;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestTornadoObjectInstance {

    @Before
    public void setUp() {
        Camera.resetInstance();
        SpriteManager.getInstance().resetState();
        LevelManager.getInstance().resetState();
    }

    @After
    public void tearDown() {
        SpriteManager.getInstance().resetState();
        LevelManager.getInstance().resetState();
    }

    @Test
    public void moveObeyPlayerAdjustsTornadoXNotPlayerX() throws Exception {
        TornadoObjectInstance tornado = createTornado(100, 0x100, 0x50);
        TestPlayableSprite main = new TestPlayableSprite("main", (short) 200, (short) 100);

        invokePrivate(tornado, "moveObeyPlayer",
                new Class<?>[]{AbstractPlayableSprite.class, boolean.class}, main, true);

        assertEquals("ObjB2_Move_obbey_player writes x_pos(a0), not x_pos(a1)", 184, tornado.getX());
        assertEquals("Player position must remain unchanged by move_obbey", 200, main.getCentreX());
    }

    @Test
    public void moveWithPlayerTransitionUsesClosestPlayerAsAnchor() throws Exception {
        TornadoObjectInstance tornado = createTornado(150, 0x100, 0x50);
        TestPlayableSprite main = new TestPlayableSprite("main", (short) 300, (short) 100);
        TestPlayableSprite sidekick = new TestPlayableSprite("sidekick", (short) 140, (short) 100);
        sidekick.setCpuControlled(true);

        SpriteManager.getInstance().addSprite(main);
        SpriteManager.getInstance().addSprite(sidekick);
        setField(tornado, "standingTransition", true);

        invokePrivate(tornado, "moveWithPlayer",
                new Class<?>[]{AbstractPlayableSprite.class, boolean.class}, main, false);

        assertEquals("ObjB2_Move_below_player should anchor to closest player on transition",
                149, tornado.getX());
    }

    @Test
    public void unusedMoverUsesCloudVisualsAndMarkObjGoneRange() throws Exception {
        Camera.getInstance().setX((short) 0);

        TornadoObjectInstance tornado = createTornado(0x700, 0x100, 0x5A);
        String artKey = (String) invokePrivate(tornado, "resolveRenderArtKey", new Class<?>[0]);

        assertEquals(Sonic2ObjectArtKeys.CLOUDS, artKey);
        assertEquals(6, tornado.getPriorityBucket());

        tornado.update(1, null);
        assertTrue("Routine C should cull via MarkObjGone", tornado.isDestroyed());
    }

    @Test
    public void wfzStartUsesDeleteOffScreenCulling() {
        Camera.getInstance().setX((short) 0);

        TornadoObjectInstance tornado = createTornado(0x700, 0x100, 0x52);
        tornado.update(1, null);

        assertTrue("WFZ start routine should delete when outside Obj_DeleteOffScreen range", tornado.isDestroyed());
    }

    @Test
    public void requestZoneAndActDeactivateLevelFlagIsSet() {
        LevelManager levelManager = LevelManager.getInstance();
        levelManager.requestZoneAndAct(9, 0, true);
        assertTrue(levelManager.isLevelInactiveForTransition());

        levelManager.requestZoneAndAct(10, 0);
        assertFalse(levelManager.isLevelInactiveForTransition());
    }

    private static TornadoObjectInstance createTornado(int x, int y, int subtype) {
        ObjectSpawn spawn = new ObjectSpawn(x, y, Sonic2ObjectIds.TORNADO, subtype, 0, false, 0);
        return new TornadoObjectInstance(spawn);
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
