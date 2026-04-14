package com.openggf.game.render;

import com.openggf.camera.Camera;
import com.openggf.level.LevelManager;

import java.util.Objects;

/**
 * Frame-local context passed to advanced render modes during resolution.
 */
public record AdvancedRenderModeContext(Camera camera,
                                        int frameCounter,
                                        LevelManager levelManager,
                                        int zoneIndex,
                                        int actIndex,
                                        int cameraX) {
    public AdvancedRenderModeContext {
        Objects.requireNonNull(camera, "camera");
        Objects.requireNonNull(levelManager, "levelManager");
    }
}
