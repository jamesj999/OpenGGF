package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.GameRng;
import org.junit.jupiter.api.Test;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectConstructionContext;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        com.openggf.level.objects.ObjectServices services = mock(com.openggf.level.objects.ObjectServices.class);
        when(services.objectManager()).thenReturn(objectManager);
        when(services.renderManager()).thenReturn(objectRenderManager);
        when(services.rng()).thenReturn(new GameRng(GameRng.Flavour.S1_S2));

        ObjectConstructionContext.setConstructionContext(services);
        TurtloidBadnikInstance base;
        try {
            base = new TurtloidBadnikInstance(
                    new ObjectSpawn(0x200, 0x100, Sonic2ObjectIds.TURTLOID, 0x18, 0, false, 0));
        } finally {
            ObjectConstructionContext.clearConstructionContext();
        }
        base.setServices(services);

        TurtloidRiderInstance rider = (TurtloidRiderInstance) getField(base, "rider");
        assertNotNull(rider, "Turtloid should spawn a rider child");

        clearInvocations(objectManager);
        setEnumField(base, "state", "PAUSE_BEFORE");
        setIntField(base, "xVelocity", 0);

        rider.onPlayerAttack(null, null);

        assertTrue(rider.isDestroyed(), "Rider should be destroyed on attack");
        assertFalse(base.isParentDestroyed(), "Turtloid base should remain alive as platform");
        assertNull(getField(base, "rider"), "Parent rider reference should be cleared");
        assertEquals(-0x80, getIntField(base, "xVelocity"), "Base should resume default movement speed");
        assertEquals("DONE", getField(base, "state").toString(), "Base should transition to DONE movement state");
        verify(objectManager, times(3)).addDynamicObject(any());
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
}


