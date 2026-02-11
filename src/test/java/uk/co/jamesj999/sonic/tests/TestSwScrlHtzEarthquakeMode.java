package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.LevelEventManager;
import uk.co.jamesj999.sonic.level.scroll.BackgroundCamera;
import uk.co.jamesj999.sonic.level.scroll.SwScrlHtz;

import java.lang.reflect.Field;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Regression tests for HTZ earthquake scroll path parity.
 */
public class TestSwScrlHtzEarthquakeMode {

    private LevelEventManager levelEvents;

    @Before
    public void setUp() throws Exception {
        TestEnvironment.resetAll();
        levelEvents = LevelEventManager.getInstance();
        levelEvents.initLevel(LevelEventManager.ZONE_HTZ, 1);
    }

    @Test
    public void usesEarthquakeModeWhenHtzFlagIsActiveEvenWithoutGeneralShake() throws Exception {
        GameServices.gameState().setHtzScreenShakeActive(true);
        GameServices.gameState().setScreenShakeActive(false);

        setPrivateInt(levelEvents, "cameraBgYOffset", 0x140);
        setPrivateInt(levelEvents, "htzCurrentBgXOffset", 0);

        SwScrlHtz handler = new SwScrlHtz(null, new BackgroundCamera());
        int[] hScroll = new int[224];
        handler.update(hScroll, 0x1800, 0x450, 10, 0);

        short expectedFg = (short) -0x1800;
        short expectedBg = (short) -0x1800;
        int expectedPacked = ((expectedFg & 0xFFFF) << 16) | (expectedBg & 0xFFFF);
        int[] expected = new int[224];
        java.util.Arrays.fill(expected, expectedPacked);

        assertArrayEquals(expected, hScroll);
        assertEquals(0x450, handler.getVscrollFactorFG() & 0xFFFF);
        assertEquals(0x450 - 0x140, handler.getVscrollFactorBG() & 0xFFFF);
        assertEquals(0, handler.getShakeOffsetX());
        assertEquals(0, handler.getShakeOffsetY());
    }

    @Test
    public void bottomRouteBgXOffsetAffectsBgHorizontalScroll() throws Exception {
        GameServices.gameState().setHtzScreenShakeActive(true);
        GameServices.gameState().setScreenShakeActive(false);

        setPrivateInt(levelEvents, "cameraBgYOffset", 0x300);
        setPrivateInt(levelEvents, "htzCurrentBgXOffset", -0x680);

        SwScrlHtz handler = new SwScrlHtz(null, new BackgroundCamera());
        int[] hScroll = new int[224];
        handler.update(hScroll, 0x1600, 0x400, 12, 1);

        int bgXPos = 0x1600 - (-0x680); // Camera_BG_X_pos target in bottom route
        short expectedFg = (short) -0x1600;
        short expectedBg = (short) -bgXPos;
        int expectedPacked = ((expectedFg & 0xFFFF) << 16) | (expectedBg & 0xFFFF);
        assertEquals(expectedPacked, hScroll[0]);
        assertEquals(expectedPacked, hScroll[223]);
    }

    private static void setPrivateInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

}
