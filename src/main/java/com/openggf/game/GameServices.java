package com.openggf.game;

import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.timer.TimerManager;

public final class GameServices {

    private GameServices() {
    }

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
}

