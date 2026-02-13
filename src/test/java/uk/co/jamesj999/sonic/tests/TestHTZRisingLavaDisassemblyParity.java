package uk.co.jamesj999.sonic.tests;

import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2LevelEventManager;
import uk.co.jamesj999.sonic.game.sonic2.objects.RisingLavaObjectInstance;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression checks for HTZ rising lava behavior against s2.asm.
 */
public class TestHTZRisingLavaDisassemblyParity {

    private Camera camera;
    private Sonic2LevelEventManager levelEvents;

    @Before
    public void setUp() throws Exception {
        resetSonic2LevelEventManagerSingleton();
        Camera.resetInstance();
        GameServices.gameState().resetSession();

        camera = Camera.getInstance();
        levelEvents = Sonic2LevelEventManager.getInstance();
    }

    @Test
    public void act1OscillationStartsAt1978NotBefore() throws Exception {
        levelEvents.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 0);
        levelEvents.setEventRoutine(2);
        GameServices.gameState().setHtzScreenShakeActive(true);

        camera.setX((short) 0x1900); // < $1978, should not oscillate
        camera.setY((short) 0x410);
        Object htzHandler = getHtzHandler(levelEvents);
        setPrivateInt(htzHandler, "cameraBgYOffset", 300);
        setPrivateBoolean(htzHandler, "htzTerrainSinking", false);
        setPrivateInt(htzHandler, "htzTerrainDelay", 0);

        for (int i = 0; i < 16; i++) {
            levelEvents.update();
        }
        assertEquals(300, levelEvents.getCameraBgYOffset());

        camera.setX((short) 0x1980); // >= $1978, oscillation now allowed
        for (int i = 0; i < 8; i++) {
            levelEvents.update();
        }
        assertTrue(levelEvents.getCameraBgYOffset() > 300);
    }

    @Test
    public void act2BottomRouteInitialYOffsetIs300() throws Exception {
        levelEvents.initLevel(Sonic2LevelEventManager.ZONE_HTZ, 1);
        camera.setX((short) 0x14C0);
        camera.setY((short) 0x380);

        levelEvents.update();

        assertEquals(8, levelEvents.getEventRoutine());
        assertEquals(0x300, levelEvents.getCameraBgYOffset());
        assertEquals(-0x680, levelEvents.getHtzBgXOffset());
        assertTrue(GameServices.gameState().isHtzScreenShakeActive());
        assertEquals(0, levelEvents.getHtzBgVerticalShift());

        Object htzHandler = getHtzHandler(levelEvents);
        setPrivateInt(htzHandler, "cameraBgYOffset", 0x2F0);
        assertEquals(0x10, levelEvents.getHtzBgVerticalShift());
    }

    @Test
    public void obj30Subtype6And8FollowRouteSplitAt380() {
        GameServices.gameState().setHtzScreenShakeActive(true);

        camera.setY((short) 0x200); // top route
        RisingLavaObjectInstance subtype6Top = new RisingLavaObjectInstance(spawnWithSubtype(6), "Obj30_6_top");
        RisingLavaObjectInstance subtype8Top = new RisingLavaObjectInstance(spawnWithSubtype(8), "Obj30_8_top");
        assertFalse(subtype6Top.isSolidFor(null));
        assertTrue(subtype8Top.isSolidFor(null));

        camera.setY((short) 0x400); // bottom route
        RisingLavaObjectInstance subtype6Bottom = new RisingLavaObjectInstance(spawnWithSubtype(6), "Obj30_6_bottom");
        RisingLavaObjectInstance subtype8Bottom = new RisingLavaObjectInstance(spawnWithSubtype(8), "Obj30_8_bottom");
        assertTrue(subtype6Bottom.isSolidFor(null));
        assertFalse(subtype8Bottom.isSolidFor(null));
    }

    private static ObjectSpawn spawnWithSubtype(int subtype) {
        return new ObjectSpawn(0x1800, 0x420, 0x30, subtype, 0, false, 0);
    }

    private static Object getHtzHandler(Object manager) throws Exception {
        Field f = manager.getClass().getDeclaredField("htzEvents");
        f.setAccessible(true);
        return f.get(manager);
    }

    private static void setPrivateInt(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void setPrivateBoolean(Object target, String fieldName, boolean value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setBoolean(target, value);
    }

    private static void resetSonic2LevelEventManagerSingleton() throws Exception {
        Field instanceField = Sonic2LevelEventManager.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }
}
