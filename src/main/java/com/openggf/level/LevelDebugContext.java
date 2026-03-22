package com.openggf.level;

import com.openggf.debug.DebugOverlayManager;
import com.openggf.graphics.GraphicsManager;

/**
 * Read-only context passed to LevelDebugRenderer for rendering
 * debug overlays without coupling to LevelManager fields.
 */
public record LevelDebugContext(
    Level level,
    int blockPixelSize,
    DebugOverlayManager overlayManager,
    GraphicsManager graphicsManager,
    int screenWidth,
    int screenHeight
) {}
