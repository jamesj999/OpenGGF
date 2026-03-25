package com.openggf.tests;

import org.junit.Before;
import org.junit.Test;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.game.sonic2.objects.RisingLavaObjectInstance;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TestObjectServices;

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
        GameServices.camera().resetState();
        GameServices.gameState().resetSession();

        camera = GameServices.camera();
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
        ObjectServices services = new TestObjectServices().withCamera(camera);

        camera.setY((short) 0x200); // top route
        setConstructionContext(services);
        RisingLavaObjectInstance subtype6Top;
        RisingLavaObjectInstance subtype8Top;
        try {
            subtype6Top = new RisingLavaObjectInstance(spawnWithSubtype(6), "Obj30_6_top");
            subtype8Top = new RisingLavaObjectInstance(spawnWithSubtype(8), "Obj30_8_top");
        } finally {
            clearConstructionContext();
        }
        subtype6Top.setServices(services);
        subtype8Top.setServices(services);
        assertFalse(subtype6Top.isSolidFor(null));
        assertTrue(subtype8Top.isSolidFor(null));

        camera.setY((short) 0x400); // bottom route
        setConstructionContext(services);
        RisingLavaObjectInstance subtype6Bottom;
        RisingLavaObjectInstance subtype8Bottom;
        try {
            subtype6Bottom = new RisingLavaObjectInstance(spawnWithSubtype(6), "Obj30_6_bottom");
            subtype8Bottom = new RisingLavaObjectInstance(spawnWithSubtype(8), "Obj30_8_bottom");
        } finally {
            clearConstructionContext();
        }
        subtype6Bottom.setServices(services);
        subtype8Bottom.setServices(services);
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

    @SuppressWarnings("unchecked")
    private static void setConstructionContext(ObjectServices svc) {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).set(svc);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearConstructionContext() {
        try {
            Field field = AbstractObjectInstance.class.getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
