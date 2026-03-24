package com.openggf.game;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

/**
 * Thin service locator for non-object code.
 * <p>
 * Runtime-owned managers delegate to {@link RuntimeManager#getCurrent()}.
 * Engine globals (audio, ROM, debug overlay) stay as direct singleton calls.
 * <p>
 * Object instances should use {@code services()} instead of this class.
 */
public final class GameServices {

    private GameServices() {
    }

    // ── Runtime-owned managers (delegate to RuntimeManager) ──────────────

    /**
     * Global camera accessor for non-object code (HUD, level loading, rendering).
     * Object instances should use {@code services().camera()} instead.
     */
    public static Camera camera() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getCamera() : Camera.getInstance();
    }

    public static LevelManager level() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getLevelManager() : LevelManager.getInstance();
    }

    /**
     * Global game state accessor for non-object code.
     * Object instances should use {@code services().gameState()} instead.
     */
    public static GameStateManager gameState() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getGameState() : GameStateManager.getInstance();
    }

    public static TimerManager timers() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getTimers() : TimerManager.getInstance();
    }

    public static FadeManager fade() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getFadeManager() : FadeManager.getInstance();
    }

    public static SpriteManager sprites() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getSpriteManager() : SpriteManager.getInstance();
    }

    public static CollisionSystem collision() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getCollisionSystem() : CollisionSystem.getInstance();
    }

    public static TerrainCollisionManager terrainCollision() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getTerrainCollisionManager() : TerrainCollisionManager.getInstance();
    }

    public static ParallaxManager parallax() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getParallaxManager() : ParallaxManager.getInstance();
    }

    public static WaterSystem water() {
        GameRuntime rt = RuntimeManager.getCurrent();
        return rt != null ? rt.getWaterSystem() : WaterSystem.getInstance();
    }

    // ── Engine globals (stay as direct singleton calls) ──────────────────

    public static RomManager rom() {
        return RomManager.getInstance();
    }

    public static DebugOverlayManager debugOverlay() {
        return DebugOverlayManager.getInstance();
    }

    public static AudioManager audio() {
        return AudioManager.getInstance();
    }
}
