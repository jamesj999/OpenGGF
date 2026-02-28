package com.openggf.game;

import com.openggf.level.WaterSystem;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TestThresholdTableWaterHandler {

    @Test
    public void testTargetSetAtFirstMatchingThreshold() {
        var handler = new ThresholdTableWaterHandler(List.of(
            new ThresholdTableWaterHandler.WaterThreshold(0x0500, 0x0900),
            new ThresholdTableWaterHandler.WaterThreshold(0x0680, 0x2A00)
        ));
        var state = new WaterSystem.DynamicWaterState(0x0600);

        handler.update(state, 0x0400, 0); // cameraX below first threshold

        assertEquals(0x0900, state.getTargetLevel());
    }

    @Test
    public void testTargetSetAtSecondThreshold() {
        var handler = new ThresholdTableWaterHandler(List.of(
            new ThresholdTableWaterHandler.WaterThreshold(0x0500, 0x0900),
            new ThresholdTableWaterHandler.WaterThreshold(0x0680, 0x2A00)
        ));
        var state = new WaterSystem.DynamicWaterState(0x0600);

        handler.update(state, 0x0600, 0); // between thresholds

        assertEquals(0x2A00, state.getTargetLevel());
    }

    @Test
    public void testNoChangeWhenPastAllThresholds() {
        var handler = new ThresholdTableWaterHandler(List.of(
            new ThresholdTableWaterHandler.WaterThreshold(0x0500, 0x0900)
        ));
        var state = new WaterSystem.DynamicWaterState(0x0600);

        handler.update(state, 0x9000, 0); // past all thresholds

        assertEquals("Target should stay at initial", 0x0600, state.getTargetLevel());
    }

    @Test
    public void testInstantSetWhenBit15Set() {
        // Bit 15 set means instant-set mean level directly
        var handler = new ThresholdTableWaterHandler(List.of(
            new ThresholdTableWaterHandler.WaterThreshold(0x0500, 0x0900 | 0x8000)
        ));
        var state = new WaterSystem.DynamicWaterState(0x0600);

        handler.update(state, 0x0400, 0);

        assertEquals("Mean should be set directly", 0x0900, state.getMeanLevel());
        assertEquals("Current should match mean", 0x0900, state.getCurrentLevel());
    }

    @Test
    public void testExactThresholdMatch() {
        var handler = new ThresholdTableWaterHandler(List.of(
            new ThresholdTableWaterHandler.WaterThreshold(0x0500, 0x0900),
            new ThresholdTableWaterHandler.WaterThreshold(0x0680, 0x2A00)
        ));
        var state = new WaterSystem.DynamicWaterState(0x0600);

        handler.update(state, 0x0500, 0); // exactly at first threshold

        assertEquals("Should match at exact threshold", 0x0900, state.getTargetLevel());
    }
}
