package com.openggf.game.sonic3k;

import com.openggf.game.DynamicWaterHandler;
import com.openggf.level.WaterSystem;

/**
 * Dynamic water handler for Angel Island Zone Act 2.
 * Implements the ROM logic from DynamicWaterHeight_AIZ2 (sonic3k.asm:8648-8695).
 * <p>
 * The water starts at 0x0528 (from StartingWaterHeights.bin), drops with speed=2
 * early in the act, then rises back to 0x0618 when the camera passes the trigger
 * X threshold (0x2850). The rise inherits the current speed (2 after the drop).
 */
public class Aiz2DynamicWaterHandler implements DynamicWaterHandler {

    /** Water level the ROM raises TO after the trigger (NOT the starting height).
     *  Starting height is 0x0528 from StartingWaterHeights.bin. */
    static final int INITIAL_LEVEL = 0x0618;

    /** Water level after the early drop. */
    static final int DROP_LEVEL = 0x0528;

    /** Camera X below which the initial drop occurs. */
    static final int FIRST_THRESHOLD_X = 0x2440;

    /** Camera X at which the water rise-back is triggered. */
    static final int TRIGGER_X = 0x2850;

    /** Frames of screen shake when the rise is triggered (ROM: Obj_6E6E timer). */
    static final int SHAKE_DURATION = 180;

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
        // ROM does NOT change Water_speed here — it inherits the last-set speed
        // (initially 1, or 2 after a drop cycle)
        if (triggered && state.getTargetLevel() != INITIAL_LEVEL) {
            state.setTarget(INITIAL_LEVEL);
            // ROM sets Screen_shake_flag=-1 and creates 180-frame timer (Obj_6E6E)
            state.setShakeTimer(SHAKE_DURATION);
        }
    }

    /**
     * Reset handler state for level reload.
     */
    public void reset() {
        this.triggered = false;
    }
}
