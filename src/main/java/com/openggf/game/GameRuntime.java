package com.openggf.game;

import com.openggf.camera.Camera;
import com.openggf.graphics.FadeManager;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.rings.RingManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.TerrainCollisionManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.timer.TimerManager;

/**
 * Explicit container for all mutable gameplay state.
 * <p>
 * Owns the 10 core manager classes that were previously singletons.
 * Constructed and managed by {@link RuntimeManager}. Not a singleton itself —
 * the engine creates one runtime per gameplay session, and a future level editor
 * may swap runtimes for hard resets.
 * <p>
 * <b>Not owned by GameRuntime</b> (engine globals that stay as singletons):
 * GraphicsManager, AudioManager, RomManager, SonicConfigurationService,
 * PerformanceProfiler, DebugOverlayManager, DebugRenderer, GameModuleRegistry.
 *
 * @see RuntimeManager
 * @see GameServices
 */
public final class GameRuntime {

    private final Camera camera;
    private final TimerManager timers;
    private final GameStateManager gameState;
    private final FadeManager fadeManager;
    private final WaterSystem waterSystem;
    private final ParallaxManager parallaxManager;
    private final TerrainCollisionManager terrainCollisionManager;
    private final CollisionSystem collisionSystem;
    private final SpriteManager spriteManager;
    private final LevelManager levelManager;

    private volatile BonusStageProvider activeBonusStageProvider = NoOpBonusStageProvider.INSTANCE;

    /**
     * Package-private constructor — only {@link RuntimeManager} creates these.
     * Parameters are in construction-order (dependency order):
     * independent managers first, then dependents.
     */
    GameRuntime(Camera camera, TimerManager timers, GameStateManager gameState,
                FadeManager fadeManager, WaterSystem waterSystem,
                ParallaxManager parallaxManager,
                TerrainCollisionManager terrainCollisionManager,
                CollisionSystem collisionSystem, SpriteManager spriteManager,
                LevelManager levelManager) {
        this.camera = camera;
        this.timers = timers;
        this.gameState = gameState;
        this.fadeManager = fadeManager;
        this.waterSystem = waterSystem;
        this.parallaxManager = parallaxManager;
        this.terrainCollisionManager = terrainCollisionManager;
        this.collisionSystem = collisionSystem;
        this.spriteManager = spriteManager;
        this.levelManager = levelManager;
    }

    // ── Getters ──────────────────────────────────────────────────────────

    public Camera getCamera() { return camera; }
    public TimerManager getTimers() { return timers; }
    public GameStateManager getGameState() { return gameState; }
    public FadeManager getFadeManager() { return fadeManager; }
    public WaterSystem getWaterSystem() { return waterSystem; }
    public ParallaxManager getParallaxManager() { return parallaxManager; }
    public TerrainCollisionManager getTerrainCollisionManager() { return terrainCollisionManager; }
    public CollisionSystem getCollisionSystem() { return collisionSystem; }
    public SpriteManager getSpriteManager() { return spriteManager; }
    public LevelManager getLevelManager() { return levelManager; }

    public BonusStageProvider getActiveBonusStageProvider() { return activeBonusStageProvider; }

    public void setActiveBonusStageProvider(BonusStageProvider provider) {
        this.activeBonusStageProvider = provider != null ? provider : NoOpBonusStageProvider.INSTANCE;
    }

    // ── Convenience: LevelManager-owned sub-managers ─────────────────────

    /** Returns the ObjectManager from LevelManager (created during level load). */
    public ObjectManager getObjectManager() { return levelManager.getObjectManager(); }

    /** Returns the RingManager from LevelManager (created during level load). */
    public RingManager getRingManager() { return levelManager.getRingManager(); }

    // ── Lifecycle ────────────────────────────────────────────────────────

    /**
     * Tears down all managers in reverse construction order.
     * Called by {@link RuntimeManager#destroyCurrent()}.
     */
    public void destroy() {
        levelManager.resetState();
        spriteManager.resetState();
        collisionSystem.resetState();
        terrainCollisionManager.resetState();
        parallaxManager.resetState();
        waterSystem.reset();
        fadeManager.cancel();
        gameState.resetState();
        timers.resetState();
        camera.resetState();
    }
}
