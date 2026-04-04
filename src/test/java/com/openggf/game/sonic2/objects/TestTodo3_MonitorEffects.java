package com.openggf.game.sonic2.objects;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for S2 Monitor object (Obj26/Obj2E) effects.
 * ROM reference: s2.asm lines 25549-26030 (Obj2E - Monitor Contents)
 */
public class TestTodo3_MonitorEffects {

    /**
     * Verify MonitorObjectInstance.MonitorType has 11 values (10 content types + broken).
     * Uses reflection because MonitorType is a private inner enum.
     * ROM: Obj2E_Types table (s2.asm:25639-25649) defines 10 content types.
     */
    @Test
    public void testS2MonitorTypeCountMatchesRom() throws Exception {
        Class<?>[] innerClasses = MonitorObjectInstance.class.getDeclaredClasses();
        Class<?> monitorTypeClass = null;
        for (Class<?> c : innerClasses) {
            if (c.getSimpleName().equals("MonitorType") && c.isEnum()) {
                monitorTypeClass = c;
                break;
            }
        }
        assertTrue("MonitorObjectInstance should contain a MonitorType enum",
                monitorTypeClass != null);
        Object[] constants = monitorTypeClass.getEnumConstants();
        assertEquals("S2 MonitorType should have 11 values (10 types + BROKEN)",
                11, constants.length);
    }

    /**
     * Verify S1 MonitorObjectInstance also has its MonitorType enum.
     * S1 has 10 values: STATIC through BROKEN (different order from S2).
     */
    @Test
    public void testS1MonitorTypeCountMatchesRom() throws Exception {
        Class<?> s1MonitorClass = Class.forName(
                "com.openggf.game.sonic1.objects.Sonic1MonitorObjectInstance");
        Class<?>[] innerClasses = s1MonitorClass.getDeclaredClasses();
        Class<?> monitorTypeClass = null;
        for (Class<?> c : innerClasses) {
            if (c.getSimpleName().equals("MonitorType") && c.isEnum()) {
                monitorTypeClass = c;
                break;
            }
        }
        assertTrue("Sonic1MonitorObjectInstance should contain a MonitorType enum",
                monitorTypeClass != null);
        Object[] constants = monitorTypeClass.getEnumConstants();
        assertEquals("S1 MonitorType should have 10 values",
                10, constants.length);
    }
}
