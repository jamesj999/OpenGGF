package com.openggf.game.sonic3k;

import com.openggf.game.DynamicWaterHandler;
import com.openggf.level.WaterSystem;

/**
 * Dynamic water handler for Angel Island Zone Act 2.
 * Implements the ROM logic from DynamicWaterHeight_AIZ2 (sonic3k.asm:8648-8695).
 * <p>
 * The water starts at an initial level (0x0618), drops to a lower level (0x0528)
 * early in the act, then rises back to the initial level when the camera passes
 * the trigger X threshold (0x2850).
 */
public class Aiz2DynamicWaterHandler implements DynamicWaterHandler {

    /** Starting water level for AIZ2 before the drop. */
    static final int INITIAL_LEVEL = 0x0618;

    /** Water level after the early drop. */
    static final int DROP_LEVEL = 0x0528;

    /** Camera X below which the initial drop occurs. */
    static final int FIRST_THRESHOLD_X = 0x2440;

    /** Camera X at which the water rise-back is triggered. */
    static final int TRIGGER_X = 0x2850;

    private boolean triggered;

    public Aiz2DynamicWaterHandler() {
        this.triggered = false;
    }

    @Override
    public void update(WaterSystem.DynamicWaterState state, int cameraX, int cameraY) {
        // Phase 1: Before first threshold, if at initial level -> drop water
        if (cameraX < FIRST_THRESHOLD_X) {
            if (state.getTargetLevel() == INITIAL_LEVEL) {
                state.setTarget(DROP_LEVEL);
                state.setSpeed(2);
            }
            return;
        }

        // Phase 2: Check for trigger zone entry
        if (!triggered && cameraX >= TRIGGER_X) {
            triggered = true;
        }

        // Phase 3: When triggered, raise water back
        if (triggered && state.getTargetLevel() != INITIAL_LEVEL) {
            state.setTarget(INITIAL_LEVEL);
            state.setSpeed(1);
        }
    }

    /**
     * Reset handler state for level reload.
     */
    public void reset() {
        this.triggered = false;
    }
}
