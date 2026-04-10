package com.openggf.game;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.session.WorldSession;
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

    private static GameRuntime requireRuntime(String accessor) {
        GameRuntime rt = RuntimeManager.getCurrent();
        if (rt == null) {
            throw new IllegalStateException(
                    "GameServices." + accessor + "() requires an active GameRuntime. "
                    + "Create one via RuntimeManager.createGameplay() before accessing runtime-owned managers.");
        }
        return rt;
    }

    public static boolean hasRuntime() {
        return RuntimeManager.getCurrent() != null;
    }

    public static GameRuntime runtimeOrNull() {
        return RuntimeManager.getCurrent();
    }

    public static Camera cameraOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getCamera() : null;
    }

    public static Camera cameraOrBootstrap() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getCamera() : Camera.getInstance();
    }

    public static LevelManager levelOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getLevelManager() : null;
    }

    public static GameStateManager gameStateOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getGameState() : null;
    }

    public static TimerManager timersOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getTimers() : null;
    }

    public static GameRng rngOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getRng() : null;
    }

    public static ParallaxManager parallaxOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getParallaxManager() : null;
    }

    public static FadeManager fadeOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getFadeManager() : null;
    }

    public static FadeManager fadeOrBootstrap() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getFadeManager() : FadeManager.getInstance();
    }

    public static SpriteManager spritesOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getSpriteManager() : null;
    }

    public static CollisionSystem collisionOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getCollisionSystem() : null;
    }

    public static TerrainCollisionManager terrainCollisionOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getTerrainCollisionManager() : null;
    }

    public static WaterSystem waterOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getWaterSystem() : null;
    }

    public static BonusStageProvider bonusStageOrNull() {
        GameRuntime rt = runtimeOrNull();
        return rt != null ? rt.getActiveBonusStageProvider() : null;
    }

    // â”€â”€ Runtime-owned managers (delegate to RuntimeManager) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Global camera accessor for non-object code (HUD, level loading, rendering).
     * Object instances should use {@code services().camera()} instead.
     */
    public static Camera camera() {
        return requireRuntime("camera").getCamera();
    }

    public static LevelManager level() {
        return requireRuntime("level").getLevelManager();
    }

    /**
     * Global game state accessor for non-object code.
     * Object instances should use {@code services().gameState()} instead.
     */
    public static GameStateManager gameState() {
        return requireRuntime("gameState").getGameState();
    }

    public static TimerManager timers() {
        return requireRuntime("timers").getTimers();
    }

    public static GameRng rng() {
        return requireRuntime("rng").getRng();
    }

    public static FadeManager fade() {
        return requireRuntime("fade").getFadeManager();
    }

    public static SpriteManager sprites() {
        return requireRuntime("sprites").getSpriteManager();
    }

    public static CollisionSystem collision() {
        return requireRuntime("collision").getCollisionSystem();
    }

    public static TerrainCollisionManager terrainCollision() {
        return requireRuntime("terrainCollision").getTerrainCollisionManager();
    }

    public static ParallaxManager parallax() {
        return requireRuntime("parallax").getParallaxManager();
    }

    public static WaterSystem water() {
        return requireRuntime("water").getWaterSystem();
    }

    public static WorldSession worldSession() {
        return requireRuntime("worldSession").getWorldSession();
    }

    public static GameModule module() {
        return worldSession().getGameModule();
    }

    /**
     * Returns the active bonus stage provider. Returns {@link NoOpBonusStageProvider}
     * when not in a bonus stage. Objects call this to signal stage completion
     * via {@link BonusStageProvider#requestExit()}.
     */
    public static BonusStageProvider bonusStage() {
        return requireRuntime("bonusStage").getActiveBonusStageProvider();
    }

    // â”€â”€ Engine globals (stay as direct singleton calls) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    public static RomManager rom() {
        return com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().roms();
    }

    public static DebugOverlayManager debugOverlay() {
        return com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().debugOverlay();
    }

    public static AudioManager audio() {
        return com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().audio();
    }
}
