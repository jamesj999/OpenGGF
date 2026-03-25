package com.openggf.game.sonic1.objects.bosses;

import org.junit.Test;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.level.objects.ObjectSpawn;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class TestSonic1FZBossDamageFlash {

    @Test
    public void inTubeDamageAnimationAlternatesSurpriseAndIntubeFrames() throws Exception {
        Sonic1FZBossInstance boss = new Sonic1FZBossInstance(
                new ObjectSpawn(0, 0, Sonic1ObjectIds.FZ_BOSS, 0, 0, false, 0));

        setIntField(boss, "seggAnim", Sonic1BossAnimations.ANIM_SEGG_INTUBE);
        setIntField(boss, "seggAnimPrev", -1);

        invokePrivateMethod(boss, "updateSeggAnimation");
        assertEquals("Ani_SEgg.intube should start on frame 5 (surprise)", 5, readIntField(boss, "seggFrame"));

        invokePrivateMethod(boss, "updateSeggAnimation");
        assertEquals("Ani_SEgg.intube should alternate to frame 9 (intube flash)", 9, readIntField(boss, "seggFrame"));

        invokePrivateMethod(boss, "updateSeggAnimation");
        assertEquals("Ani_SEgg.intube should loop back to frame 5", 5, readIntField(boss, "seggFrame"));
    }

    private static int readIntField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static void invokePrivateMethod(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }
}
