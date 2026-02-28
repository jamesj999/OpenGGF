package com.openggf.game;

import com.openggf.level.WaterSystem;

import java.util.List;

/**
 * Scans a threshold table left-to-right, setting the water target when
 * cameraX is at or below a threshold. Used by S3K HCZ and LBZ zones.
 * <p>
 * If a target value has bit 15 set, the mean water level is set directly
 * (instant teleport) rather than gradually moving toward the target.
 * <p>
 * Mirrors the ROM pattern at {@code DynamicWaterHeight_HCZ1} (sonic3k.asm:8710).
 */
public class ThresholdTableWaterHandler implements DynamicWaterHandler {

    /** Camera X threshold and corresponding target water level pair. */
    public record WaterThreshold(int cameraXThreshold, int targetWaterLevel) {}

    private final List<WaterThreshold> thresholds;

    public ThresholdTableWaterHandler(List<WaterThreshold> thresholds) {
        this.thresholds = List.copyOf(thresholds);
    }

    @Override
    public void update(WaterSystem.DynamicWaterState state, int cameraX, int cameraY) {
        for (var t : thresholds) {
            if (cameraX <= t.cameraXThreshold()) {
                int target = t.targetWaterLevel();
                if ((target & 0x8000) != 0) {
                    // Bit 15 set: instant-set mean level
                    state.setMeanDirect(target & 0x7FFF);
                } else {
                    state.setTarget(target);
                }
                return;
            }
        }
        // Past all thresholds: no change
    }
}
