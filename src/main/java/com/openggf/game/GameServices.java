package com.openggf.game;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.timer.TimerManager;

public final class GameServices {

    private GameServices() {
    }

    /**
     * Global game state accessor for non-object code.
     * Object instances should use {@code services().gameState()} instead.
     */
    public static GameStateManager gameState() {
        return GameStateManager.getInstance();
    }

    public static TimerManager timers() {
        return TimerManager.getInstance();
    }

    public static RomManager rom() {
        return RomManager.getInstance();
    }

    public static DebugOverlayManager debugOverlay() {
        return DebugOverlayManager.getInstance();
    }

    public static AudioManager audio() {
        return AudioManager.getInstance();
    }

    /**
     * Global camera accessor for non-object code (HUD, level loading, rendering).
     * Object instances should use {@code services().camera()} instead.
     */
    public static Camera camera() {
        return Camera.getInstance();
    }

    public static LevelManager level() {
        return LevelManager.getInstance();
    }

    public static FadeManager fade() {
        return FadeManager.getInstance();
    }
}

