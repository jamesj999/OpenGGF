package com.openggf.game.sonic2.slotmachine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestCNZSlotMachineRng {
    @Test
    void setupTargetsUsesVintCounterBytesForSpeedsAndTarget() throws Exception {
        CNZSlotMachineManager manager = new CNZSlotMachineManager();
        manager.activate();

        manager.update(0x1200);

        assertArrayEquals(new int[]{
                (((0x00 & 0x07) - 4) + 0x30),
                (((Integer.rotateLeft(0x00, 4) & 0xFF) & 0x07) - 4) + 0x30,
                (((0x12 & 0x07) - 4) + 0x30)
        }, intArray(manager, "slotSpeeds"));
        assertEquals(3, intField(manager, "slot1Target"));
        assertEquals(0x33, intField(manager, "slot23Target"));
    }

    @Test
    void fineTuneTimerUsesVintCounterLowNibble() throws Exception {
        CNZSlotMachineManager manager = new CNZSlotMachineManager();
        setIntField(manager, "routine", 0x0C);
        setIntField(manager, "slotTimer", 0);

        manager.update(0xABCD);

        assertEquals((0xCD & 0x0F) + 0x0C, intField(manager, "slotTimer"));
    }

    @Test
    void managerDoesNotUseJvmRandomSources() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/com/openggf/game/sonic2/slotmachine/CNZSlotMachineManager.java"));

        assertFalse(source.contains("java.util.Random"));
        assertFalse(source.contains("new Random"));
        assertFalse(source.contains("random.next"));
    }

    private static int intField(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static void setIntField(Object target, String fieldName, int value) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setInt(target, value);
    }

    private static int[] intArray(Object target, String fieldName) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return (int[]) field.get(target);
    }
}
