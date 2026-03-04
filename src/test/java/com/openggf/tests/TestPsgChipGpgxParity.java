package com.openggf.tests;

import org.junit.Test;
import com.openggf.audio.synth.PsgChipGPGX;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestPsgChipGpgxParity {

    @Test
    public void defaultsToFastModeForCrisperGenesisParity() {
        PsgChipGPGX chip = new PsgChipGPGX(44100.0, PsgChipGPGX.ChipType.INTEGRATED);
        assertFalse("GPGX parity should default to fast PSG mode", chip.isHqMode());
    }

    @Test
    public void renderKeepsClockCarryBoundedToPsgCycleRemainder() throws Exception {
        PsgChipGPGX chip = new PsgChipGPGX(44100.0, PsgChipGPGX.ChipType.INTEGRATED);

        // Channel 0: audible tone so the chip has active transitions across renders.
        chip.write(0x80); // Tone 0 period low nibble = 0
        chip.write(0x20); // Tone 0 period high bits -> non-zero period
        chip.write(0x90); // Tone 0 volume = 0 (loudest)

        int[] left = new int[256];
        int[] right = new int[256];

        for (int i = 0; i < 8; i++) {
            chip.renderStereo(left, right);
            int clocks = readPrivateInt(chip, "clocks");
            assertTrue("Clock carry should stay within one PSG cycle remainder", clocks >= 0 && clocks < (15 * 16));
        }
    }

    @Test
    public void noiseLfsrClocksOnEveryToggle() throws Exception {
        PsgChipGPGX chip = new PsgChipGPGX(44100.0, PsgChipGPGX.ChipType.INTEGRATED);

        int initialShift = readPrivateInt(chip, "noiseShiftValue");
        int shiftWidth = readPrivateInt(chip, "noiseShiftWidth");

        // Force deterministic noise stepping: periodic mode, 10 toggles total.
        setPrivateIntArrayValue(chip, "regs", 6, 0x03);
        setPrivateIntArrayValue(chip, "freqCounter", 3, 0);
        setPrivateIntArrayValue(chip, "freqInc", 3, 10);
        setPrivateIntArrayValue(chip, "polarity", 3, -1);

        invokePrivateUpdate(chip, 100);

        int actualShift = readPrivateInt(chip, "noiseShiftValue");
        int expectedShift = advancePeriodicNoise(initialShift, shiftWidth, 10);
        assertEquals("Noise LFSR should advance on every polarity toggle", expectedShift, actualShift);
    }

    @Test
    public void noiseLfsrCanBeConfiguredToPositiveEdgeOnly() throws Exception {
        PsgChipGPGX chip = new PsgChipGPGX(44100.0, PsgChipGPGX.ChipType.INTEGRATED);
        chip.setNoiseShiftOnEveryToggle(false);

        int initialShift = readPrivateInt(chip, "noiseShiftValue");
        int shiftWidth = readPrivateInt(chip, "noiseShiftWidth");

        setPrivateIntArrayValue(chip, "regs", 6, 0x03);
        setPrivateIntArrayValue(chip, "freqCounter", 3, 0);
        setPrivateIntArrayValue(chip, "freqInc", 3, 10);
        setPrivateIntArrayValue(chip, "polarity", 3, -1);

        invokePrivateUpdate(chip, 100);

        int actualShift = readPrivateInt(chip, "noiseShiftValue");
        int expectedShift = advancePeriodicNoise(initialShift, shiftWidth, 5);
        assertEquals("Positive-edge mode should shift half as often as every-toggle mode", expectedShift, actualShift);
    }

    @Test
    public void blipTimingDoesNotAccumulateLargeBacklogAt48khz() throws Exception {
        PsgChipGPGX chip = new PsgChipGPGX(48000.0, PsgChipGPGX.ChipType.INTEGRATED);

        int[] left = new int[1];
        int[] right = new int[1];
        for (int i = 0; i < 200_000; i++) {
            left[0] = 0;
            right[0] = 0;
            chip.renderStereo(left, right);
        }

        Object blip = readPrivateObject(chip, "blip");
        long offsetFp = readPrivateLong(blip, "offsetFp");
        int factorFpBits = readPrivateStaticInt(blip.getClass(), "FACTOR_FP_BITS");
        long oneSampleFp = 1L << (20 + factorFpBits);

        assertTrue("Blip timebase should stay bounded; large growth indicates sample timing drift",
                offsetFp >= 0 && offsetFp < (oneSampleFp * 4));
    }

    private static int readPrivateInt(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(instance);
    }

    private static long readPrivateLong(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getLong(instance);
    }

    private static Object readPrivateObject(Object instance, String fieldName) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(instance);
    }

    private static int readPrivateStaticInt(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static void setPrivateIntArrayValue(Object instance, String fieldName, int index, int value) throws Exception {
        Field field = instance.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        int[] array = (int[]) field.get(instance);
        array[index] = value;
    }

    private static void invokePrivateUpdate(Object instance, int targetClocks) throws Exception {
        Method method = instance.getClass().getDeclaredMethod("psgUpdate", int.class);
        method.setAccessible(true);
        method.invoke(instance, targetClocks);
    }

    private static int advancePeriodicNoise(int shiftValue, int shiftWidth, int steps) {
        int current = shiftValue;
        for (int i = 0; i < steps; i++) {
            int shiftOutput = current & 0x01;
            current = (current >>> 1) | (shiftOutput << shiftWidth);
        }
        return current;
    }
}
