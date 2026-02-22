package uk.co.jamesj999.sonic.tests.physics;

import org.junit.Test;
import uk.co.jamesj999.sonic.physics.Direction;
import uk.co.jamesj999.sonic.physics.GroundSensor;
import uk.co.jamesj999.sonic.tests.TestablePlayableSprite;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class TestGroundSensorNegFloorDistance {

    @Test
    public void testNegFloorDistanceTracksTileBaseDelta() throws Exception {
        GroundSensor sensor = new GroundSensor(
                new TestablePlayableSprite("test", (short) 0, (short) 0),
                Direction.DOWN,
                (byte) 0,
                (byte) 0,
                true);

        short origY = (short) 0x1234; // yInTile = 4
        short prevTileY = (short) (origY - 16);
        short nextTileY = (short) (origY + 16);

        byte sameTile = invokeNegFloorDistance(sensor, origY, origY);
        byte prevTile = invokeNegFloorDistance(sensor, origY, prevTileY);
        byte nextTile = invokeNegFloorDistance(sensor, origY, nextTileY);

        assertEquals("Base FindFloor2 .negfloor distance should be ~yInTile", (byte) ~0x04, sameTile);
        assertEquals("Previous-tile call should apply -16 offset", (byte) (sameTile - 16), prevTile);
        assertEquals("Next-tile call should apply +16 offset", (byte) (sameTile + 16), nextTile);
    }

    private static byte invokeNegFloorDistance(GroundSensor sensor, short origY, short checkY) throws Exception {
        Method method = GroundSensor.class.getDeclaredMethod("calculateNegativeExtensionDistance", short.class,
                short.class);
        method.setAccessible(true);
        return (byte) method.invoke(sensor, origY, checkY);
    }
}
