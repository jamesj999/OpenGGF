package uk.co.jamesj999.sonic.game.sonic1.objects;

import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1BurrobotBadnikInstance;
import uk.co.jamesj999.sonic.game.sonic1.objects.badniks.Sonic1OrbinautBadnikInstance;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestSonic1LabyrinthObjectsBasic {

    @Test
    public void registryNamesIncludeNewObjects() {
        Sonic1ObjectRegistry registry = new Sonic1ObjectRegistry();
        assertEquals("FlappingDoor", registry.getPrimaryName(Sonic1ObjectIds.FLAPPING_DOOR));
        assertEquals("Burrobot", registry.getPrimaryName(Sonic1ObjectIds.BURROBOT));
        assertEquals("Orbinaut", registry.getPrimaryName(Sonic1ObjectIds.ORBINAUT));
        assertEquals("Waterfall", registry.getPrimaryName(Sonic1ObjectIds.WATERFALL));
    }

    @Test
    public void flappingDoorSolidityDependsOnPlayerSideWhenClosed() throws Exception {
        Sonic1FlappingDoorObjectInstance door = new Sonic1FlappingDoorObjectInstance(
                new ObjectSpawn(200, 128, Sonic1ObjectIds.FLAPPING_DOOR, 1, 0, false, 0));
        TestPlayableSprite player = new TestPlayableSprite();

        setPrivateInt(door, "flapWait", 999);
        setPrivateInt(door, "mappingFrame", 0);

        player.setCentreX((short) 150);
        player.setCentreY((short) 128);
        door.update(1, player);
        assertTrue(door.isSolidFor(player));

        player.setCentreX((short) 220);
        door.update(2, player);
        assertFalse(door.isSolidFor(player));
    }

    @Test
    public void flappingDoorClosingAnimationHoldsFullyClosedFrame() throws Exception {
        Sonic1FlappingDoorObjectInstance door = new Sonic1FlappingDoorObjectInstance(
                new ObjectSpawn(200, 128, Sonic1ObjectIds.FLAPPING_DOOR, 1, 0, false, 0));

        // Keep flap toggle inactive so we only validate animation script looping.
        setPrivateInt(door, "flapWait", 999);
        setPrivateInt(door, "animationId", 1); // Closing script: 2,1,0,afBack,1
        setPrivateInt(door, "animationFrameIndex", 0);
        setPrivateInt(door, "animationTimer", 0);

        // Run long enough to reach and hold fully-closed frame.
        for (int i = 0; i < 16; i++) {
            door.update(i + 1, null);
        }
        assertEquals("Closing script should hold on frame 0 after completion",
                0, getPrivateInt(door, "mappingFrame"));

        // Continue running; frame should remain 0 (no 1<->0 flicker).
        for (int i = 0; i < 16; i++) {
            door.update(100 + i, null);
        }
        assertEquals("Closing script should continue holding frame 0",
                0, getPrivateInt(door, "mappingFrame"));
    }

    @Test
    public void waterfallSubtypeNineUsesSplashPriorityAndSurvivesUpdates() {
        Sonic1WaterfallObjectInstance waterfall = new Sonic1WaterfallObjectInstance(
                new ObjectSpawn(100, 100, Sonic1ObjectIds.WATERFALL, 0x89, 0, false, 0));

        assertTrue(waterfall.isHighPriority());
        assertEquals(RenderPriority.MIN, waterfall.getPriorityBucket());

        waterfall.update(1, null);
        waterfall.update(2, null);
        assertFalse(waterfall.isDestroyed());
    }

    @Test
    public void burrobotAndOrbinautCanUpdateWithoutPlayerOrLevelManager() {
        Sonic1BurrobotBadnikInstance burrobot = new Sonic1BurrobotBadnikInstance(
                new ObjectSpawn(256, 256, Sonic1ObjectIds.BURROBOT, 0, 0, false, 0), null);
        Sonic1OrbinautBadnikInstance orbinaut = new Sonic1OrbinautBadnikInstance(
                new ObjectSpawn(512, 192, Sonic1ObjectIds.ORBINAUT, 0, 0, false, 0), null);

        burrobot.update(1, null);
        burrobot.update(2, null);
        orbinaut.update(1, null);
        orbinaut.update(2, null);

        assertEquals(0x05, burrobot.getCollisionFlags());
        assertEquals(0x0B, orbinaut.getCollisionFlags());
    }

    private static void setPrivateInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int getPrivateInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static final class TestPlayableSprite extends AbstractPlayableSprite {
        private TestPlayableSprite() {
            super("TEST", (short) 0, (short) 0);
            setWidth(20);
            setHeight(38);
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
            standXRadius = 9;
            standYRadius = 19;
            rollXRadius = 7;
            rollYRadius = 14;
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
