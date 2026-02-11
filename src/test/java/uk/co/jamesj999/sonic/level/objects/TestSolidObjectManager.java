package uk.co.jamesj999.sonic.level.objects;

import org.junit.Test;
import uk.co.jamesj999.sonic.game.GameModule;
import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1GameModule;
import uk.co.jamesj999.sonic.game.sonic1.objects.Sonic1CollapsingLedgeObjectInstance;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestSolidObjectManager {
    @Test
    public void testStandingContactOnFlatObject() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setCentreX((short) 100);
        int centreY = 100 - params.groundHalfHeight() - player.getYRadius();
        player.setCentreY((short) centreY);
        player.setYSpeed((short) 0);

        assertTrue(manager.hasStandingContact(player));
    }

    @Test
    public void testHeadroomDistanceUpward() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 70, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setCentreX((short) 100);
        player.setCentreY((short) 100);

        int distance = manager.getHeadroomDistance(player, 0x00);

        assertEquals(3, distance);
    }

    @Test
    public void testCollapsingLedgeUsesSlopedSurfaceProfile() {
        ObjectSpawn spawn = new ObjectSpawn(100, 100, 0x1A, 0, 0, false, 0);
        Sonic1CollapsingLedgeObjectInstance ledge = new Sonic1CollapsingLedgeObjectInstance(spawn, null);
        ObjectManager manager = buildManager(ledge);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(true);
        player.setYSpeed((short) 0);

        // Left-side sample (heightmap value 0x20): expected center Y after snap = 59.
        // Use an interior X so top resolution wins over side resolution.
        player.setCentreX((short) 64);
        player.setCentreY((short) 59);
        manager.updateSolidContacts(player);
        int leftCenterY = player.getCentreY();
        assertEquals(59, leftCenterY);

        // Right-side sample (heightmap value 0x30): expected center Y after snap = 43.
        player.setAir(true);
        player.setYSpeed((short) 0);
        player.setCentreX((short) 136);
        player.setCentreY((short) 43);
        manager.updateSolidContacts(player);
        int rightCenterY = player.getCentreY();
        assertEquals(43, rightCenterY);

        // Shape must not be flat: right edge is 16px higher than left edge.
        assertEquals(16, leftCenterY - rightCenterY);
    }

    @Test
    public void testCollapsingLedgeFragmentWalkOffWindowRemainsSolid() throws Exception {
        ObjectSpawn spawn = new ObjectSpawn(100, 100, 0x1A, 0, 0, false, 0);
        Sonic1CollapsingLedgeObjectInstance ledge = new Sonic1CollapsingLedgeObjectInstance(spawn, null);

        setPrivateInt(ledge, "routine", 6);
        setPrivateBoolean(ledge, "collapseFlag", true);
        assertTrue(ledge.isSolidFor(null));

        // Disassembly parity: once collapse flag is cleared in routine 6, the ledge no longer
        // runs walk-off collision and should not remain solid.
        setPrivateBoolean(ledge, "collapseFlag", false);
        assertFalse(ledge.isSolidFor(null));
    }

    @Test
    public void testNearTopSideContactDoesNotSetPushingFlag() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(false);
        player.setXSpeed((short) 0x100);
        // Left edge of object, near top edge: side graze while walking across tops.
        player.setCentreX((short) 85);
        player.setCentreY((short) 71);

        manager.updateSolidContacts(player);

        assertFalse(player.getPushing());
    }

    @Test
    public void testMidSideContactStillSetsPushingFlag() {
        SolidObjectParams params = new SolidObjectParams(16, 8, 8);
        TestSolidObject object = new TestSolidObject(100, 100, params);
        ObjectManager manager = buildManager(object);

        TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
        player.setWidth(20);
        player.setHeight(20);
        player.setAir(false);
        player.setXSpeed((short) 0x100);
        // Left edge of object, deeper than top-edge buffer: should count as push.
        player.setCentreX((short) 85);
        player.setCentreY((short) 81);

        manager.updateSolidContacts(player);

        assertTrue(player.getPushing());
    }

    @Test
    public void testSonic1TopSolidLandsNearEdgeWithoutSidePriorityMiss() {
        GameModule previous = GameModuleRegistry.getCurrent();
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        try {
            SolidObjectParams params = new SolidObjectParams(32, 8, 8);
            TestSolidObject object = new TestSolidObject(100, 100, params, true);
            ObjectManager manager = buildManager(object);

            TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
            player.setWidth(20);
            player.setHeight(20);
            player.setAir(true);
            player.setYSpeed((short) 0x100);

            // Near left edge (small absDistX), but within S1 top landing window.
            player.setCentreX((short) (100 - params.halfWidth() + 5));
            int maxTop = params.groundHalfHeight() + player.getYRadius();
            int targetDistY = 10;
            player.setCentreY((short) (100 - 4 - maxTop + targetDistY));

            manager.updateSolidContacts(player);

            assertTrue(player.isOnObject());
            assertFalse(player.getAir());
        } finally {
            GameModuleRegistry.setCurrent(previous);
        }
    }

    @Test
    public void testLandingFromAirRollOnObjectAdjustsYWhenUnrolling() {
        GameModule previous = GameModuleRegistry.getCurrent();
        GameModuleRegistry.setCurrent(new Sonic1GameModule());
        try {
            SolidObjectParams params = new SolidObjectParams(16, 8, 8);
            TestSolidObject object = new TestSolidObject(100, 100, params);
            ObjectManager manager = buildManager(object);

            TestPlayableSprite player = new TestPlayableSprite((short) 0, (short) 0);
            player.setWidth(20);
            player.setHeight(38);
            player.setAir(true);
            player.setYSpeed((short) 0x100);
            player.setRolling(true);

            // Within top landing window while rolling in air.
            player.setCentreX((short) 100);
            int rollingRadius = player.getYRadius(); // 14
            int distY = 8;
            int centerY = 100 - 4 - (params.groundHalfHeight() + rollingRadius) + distY;
            player.setCentreY((short) centerY);

            manager.updateSolidContacts(player);

            assertTrue(player.isOnObject());
            assertFalse(player.getAir());
            assertFalse(player.getRolling());

            int expectedStandingCenterY = 100 - params.groundHalfHeight() - 19 - 1;
            assertEquals(expectedStandingCenterY, player.getCentreY());
        } finally {
            GameModuleRegistry.setCurrent(previous);
        }
    }

    private static void setPrivateInt(Object instance, String fieldName, int value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(instance, value);
    }

    private static void setPrivateBoolean(Object instance, String fieldName, boolean value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(instance, value);
    }

    private ObjectManager buildManager(ObjectInstance instance) {
        ObjectRegistry registry = new ObjectRegistry() {
            @Override
            public ObjectInstance create(ObjectSpawn spawn) {
                return instance;
            }

            @Override
            public void reportCoverage(List<ObjectSpawn> spawns) {
                // No-op for tests.
            }

            @Override
            public String getPrimaryName(int objectId) {
                return "TEST";
            }
        };

        ObjectManager objectManager = new ObjectManager(List.of(), registry, 0, null, null);
        objectManager.reset(0);
        objectManager.addDynamicObject(instance);
        return objectManager;
    }

    private static final class TestSolidObject implements ObjectInstance, SolidObjectProvider {
        private final ObjectSpawn spawn;
        private final SolidObjectParams params;
        private final boolean topSolidOnly;

        private TestSolidObject(int x, int y, SolidObjectParams params) {
            this(x, y, params, false);
        }

        private TestSolidObject(int x, int y, SolidObjectParams params, boolean topSolidOnly) {
            this.spawn = new ObjectSpawn(x, y, 0, 0, 0, false, 0);
            this.params = params;
            this.topSolidOnly = topSolidOnly;
        }

        @Override
        public ObjectSpawn getSpawn() {
            return spawn;
        }

        @Override
        public void update(int frameCounter, AbstractPlayableSprite player) {
            // No-op for tests.
        }

        @Override
        public void appendRenderCommands(List<GLCommand> commands) {
            // No-op for tests.
        }

        @Override
        public boolean isHighPriority() {
            return false;
        }

        @Override
        public boolean isDestroyed() {
            return false;
        }

        @Override
        public SolidObjectParams getSolidParams() {
            return params;
        }

        @Override
        public boolean isTopSolidOnly() {
            return topSolidOnly;
        }
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite(short x, short y) {
            super("TEST", x, y);
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
            // No-op for tests.
        }
    }
}
