package com.openggf.timer.timers;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.timer.AbstractTimer;

/**
 * Timer to unfreeze camera after spindash.
 *
 * Note: Spindash camera lag is now handled by Camera.setHorizScrollDelay() which
 * auto-decrements each frame (matching ROM behavior). This timer is no longer
 * used for normal spindash lag, but remains for any legacy freeze scenarios.
 */
public class SpindashCameraTimer extends AbstractTimer {

    public SpindashCameraTimer(String code, int ticks) {
        super(code, ticks);
    }

    public boolean perform() {
        Camera camera = GameServices.cameraOrNull();
        if (camera != null) {
            // Clear full freeze (if any)
            camera.setFrozen(false);
            // Also explicitly clear any horizontal delay
            camera.setHorizScrollDelay(0);
            return true;
        } else {
            return false;
        }
    }
}
