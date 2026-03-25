package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.level.objects.DefaultObjectServices;
import org.junit.Test;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TestTurtloidBadnikInstance {

    @Test
    public void riderAttackKeepsTurtloidBaseAsPlatform() throws Exception {
        LevelManager levelManager = mock(LevelManager.class);
        ObjectManager objectManager = mock(ObjectManager.class);
        ObjectRenderManager objectRenderManager = mock(ObjectRenderManager.class);
        PatternSpriteRenderer pointsRenderer = mock(PatternSpriteRenderer.class);
        when(levelManager.getObjectManager()).thenReturn(objectManager);
        when(levelManager.getObjectRenderManager()).thenReturn(objectRenderManager);
        when(objectRenderManager.getPointsRenderer()).thenReturn(pointsRenderer);

        // Set up LevelManager singleton so constructor-time spawnChildren() works
        Field lmField = LevelManager.class.getDeclaredField("levelManager");
        lmField.setAccessible(true);
        Object originalLm = lmField.get(null);
        lmField.set(null, levelManager);

        try {
            com.openggf.level.objects.ObjectServices services = mock(com.openggf.level.objects.ObjectServices.class);
            when(services.objectManager()).thenReturn(objectManager);
            when(services.renderManager()).thenReturn(objectRenderManager);

            setConstructionContext(services);
            TurtloidBadnikInstance base;
            try {
                base = new TurtloidBadnikInstance(
                        new ObjectSpawn(0x200, 0x100, Sonic2ObjectIds.TURTLOID, 0x18, 0, false, 0));
            } finally {
                clearConstructionContext();
            }
            base.setServices(services);

        TurtloidRiderInstance rider = (TurtloidRiderInstance) getField(base, "rider");
        assertNotNull("Turtloid should spawn a rider child", rider);

        clearInvocations(objectManager);
        setEnumField(base, "state", "PAUSE_BEFORE");
        setIntField(base, "xVelocity", 0);

        rider.onPlayerAttack(null, null);

        assertTrue("Rider should be destroyed on attack", rider.isDestroyed());
        assertFalse("Turtloid base should remain alive as platform", base.isParentDestroyed());
        assertNull("Parent rider reference should be cleared", getField(base, "rider"));
        assertEquals("Base should resume default movement speed", -0x80, getIntField(base, "xVelocity"));
        assertEquals("Base should transition to DONE movement state", "DONE", getField(base, "state").toString());
        verify(objectManager, times(3)).addDynamicObject(any());
        } finally {
            lmField.set(null, originalLm);
        }
    }

    private static Object getField(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int getIntField(Object target, String fieldName) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumField(Object target, String fieldName, String enumConstant) throws Exception {
        Field field = findField(target.getClass(), fieldName);
        field.setAccessible(true);
        Class<? extends Enum> enumType = (Class<? extends Enum>) field.getType().asSubclass(Enum.class);
        field.set(target, Enum.valueOf(enumType, enumConstant));
    }

    private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    @SuppressWarnings("unchecked")
    private static void setConstructionContext(com.openggf.level.objects.ObjectServices services) {
        try {
            Field field = com.openggf.level.objects.AbstractObjectInstance.class
                    .getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).set(services);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearConstructionContext() {
        try {
            Field field = com.openggf.level.objects.AbstractObjectInstance.class
                    .getDeclaredField("CONSTRUCTION_CONTEXT");
            field.setAccessible(true);
            ((ThreadLocal<Object>) field.get(null)).remove();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
