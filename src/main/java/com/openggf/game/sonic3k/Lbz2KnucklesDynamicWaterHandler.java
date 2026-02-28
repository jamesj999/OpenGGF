package com.openggf.game.sonic3k;

import com.openggf.game.DynamicWaterHandler;
import com.openggf.game.ThresholdTableWaterHandler;
import com.openggf.level.WaterSystem;

/**
 * Dynamic water handler for Launch Base Zone Act 2 (Knuckles).
 * Implements DynamicWaterHeight_LBZ2_Knuckles (sonic3k.asm: loc_6F00-6F1A).
 * <p>
 * Has two modes:
 * <ul>
 *   <li><b>Normal:</b> delegates to a threshold table (word_6F12).</li>
 *   <li><b>Pipe plug destroyed</b> (ROM _unkF7C2 set): if {@code meanLevel < cameraY},
 *       snaps mean water level to 0x0660. Otherwise no-op.</li>
 * </ul>
 * The pipe plug flag is set externally when the cork/plug object is destroyed.
 */
public class Lbz2KnucklesDynamicWaterHandler implements DynamicWaterHandler {

    /** Water level to snap to when pipe plug is destroyed (ROM: move.w #$660). */
    static final int PIPE_PLUG_SNAP_LEVEL = 0x0660;

    private final ThresholdTableWaterHandler thresholdHandler;
    private boolean pipePlugDestroyed;

    public Lbz2KnucklesDynamicWaterHandler(ThresholdTableWaterHandler thresholdHandler) {
        this.thresholdHandler = thresholdHandler;
        this.pipePlugDestroyed = false;
    }

    @Override
    public void update(WaterSystem.DynamicWaterState state, int cameraX, int cameraY) {
        if (pipePlugDestroyed) {
            // ROM loc_6F00: cmp.w (Camera_Y_pos).w,(Mean_water_level).w; bcc.s locret
            // bcc = branch if mean >= cameraY (unsigned). Snap only when mean < cameraY.
            if (state.getMeanLevel() < cameraY) {
                state.setMeanDirect(PIPE_PLUG_SNAP_LEVEL);
            }
            return;
        }
        thresholdHandler.update(state, cameraX, cameraY);
    }

    /** Set when the LBZ2 pipe plug object is destroyed (ROM _unkF7C2). */
    public void setPipePlugDestroyed(boolean destroyed) {
        this.pipePlugDestroyed = destroyed;
    }

    public boolean isPipePlugDestroyed() {
        return pipePlugDestroyed;
    }
}
