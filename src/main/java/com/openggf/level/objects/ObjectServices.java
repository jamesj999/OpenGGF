package com.openggf.level.objects;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.RespawnState;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.rings.RingManager;
import com.openggf.sprites.managers.SpriteManager;
import java.io.IOException;
import java.util.List;

/**
 * Injectable service handle for game objects. Provides access to level sub-managers,
 * rendering, audio, and gameplay systems without requiring singleton lookups.
 * <p>
 * Available during construction (via ThreadLocal context), update, and rendering.
 * Injected by {@link ObjectManager} via
 * {@link AbstractObjectInstance#setServices(ObjectServices)}.
 */
public interface ObjectServices {
    // Object management
    ObjectManager objectManager();
    ObjectRenderManager renderManager();

    // Level state
    LevelState levelGamestate();
    RespawnState checkpointState();
    Level currentLevel();
    int romZoneId();
    int currentAct();
    int featureZoneId();
    int featureActId();
    ZoneFeatureProvider zoneFeatureProvider();

    // Audio
    void playSfx(int soundId);
    void playSfx(GameSound sound);
    void playMusic(int musicId);
    void fadeOutMusic();
    AudioManager audioManager();

    // Gameplay
    void spawnLostRings(PlayableEntity player, int frameCounter);

    // Context-specific managers
    /**
     * Returns the camera for position queries and bounds checks.
     * <p>
     * <b>Governance:</b> Object instance code (subclasses of {@link AbstractObjectInstance})
     * should use this method, not {@link com.openggf.game.GameServices#camera()}.
     * {@code GameServices.camera()} is for non-object code (HUD, level loading, etc.).
     */
    Camera camera();

    /**
     * Returns the game state manager for score, lives, and emerald tracking.
     * <p>
     * <b>Governance:</b> Object instance code should use this method, not
     * {@link com.openggf.game.GameServices#gameState()}.
     */
    GameStateManager gameState();

    // Player/sidekick access
    List<PlayableEntity> sidekicks();

    /** Returns the sprite manager for player sprite access. */
    SpriteManager spriteManager();

    // --- Rendering ---

    /** Returns the graphics manager for pattern caching and rendering. */
    GraphicsManager graphicsManager();

    /** Returns the fade manager for screen transitions. */
    FadeManager fadeManager();

    // --- ROM data ---

    /** Returns the current ROM instance. */
    Rom rom() throws IOException;

    /** Returns a ROM byte reader for the current ROM. */
    RomByteReader romReader() throws IOException;

    // --- Level subsystems ---

    /** Returns the water system for water level queries. */
    WaterSystem waterSystem();

    /** Returns the parallax manager for scroll offset queries. */
    ParallaxManager parallaxManager();

    // --- Level actions ---

    /** Requests transition to the next level. Wraps IOException as unchecked. */
    void advanceToNextLevel();

    /** Requests transition to the credits/ending sequence. */
    void requestCreditsTransition();

    /** Requests entry into a special stage. */
    void requestSpecialStageEntry();

    /** Invalidates the cached foreground tilemap (e.g., after block changes). */
    void invalidateForegroundTilemap();

    /** Updates a palette line in the level's palette table. */
    void updatePalette(int paletteIndex, byte[] paletteData);

    /** Returns the ring manager for ring-related operations. */
    RingManager ringManager();

    /** Returns the current zone index (rom-mapped). Alias for {@link #romZoneId()}. */
    default int currentZone() { return romZoneId(); }

    /** Returns true if all rings in the current level have been collected. */
    boolean areAllRingsCollected();

    // --- Level transition actions ---

    /**
     * Advances zone/act counters without loading the new level.
     * Used when entering a special stage after results screen (ROM: Got_NextLevel).
     */
    void advanceZoneActOnly();

    /**
     * Requests entry into a special stage from a checkpoint/big ring.
     * Wraps {@link com.openggf.level.LevelTransitionCoordinator#requestSpecialStageFromCheckpoint()}.
     */
    void requestSpecialStageFromCheckpoint();

    /**
     * Requests transition to a specific zone and act.
     *
     * @param zone the zone index (0-based)
     * @param act  the act index (0-based)
     */
    void requestZoneAndAct(int zone, int act);

    /**
     * Requests transition to a specific zone and act, optionally freezing level updates.
     *
     * @param zone               the zone index (0-based)
     * @param act                the act index (0-based)
     * @param deactivateLevelNow true to freeze level updates until the transition completes
     */
    void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow);

    // --- Level queries ---

    /**
     * Returns the music ID for the current level, or -1 if unknown.
     */
    int getCurrentLevelMusicId();

    /**
     * Searches the level's foreground tilemap for a pattern within a radius.
     *
     * @param refX         reference X position (world coordinates)
     * @param refY         reference Y position (world coordinates)
     * @param minTileIdx   minimum tile index to match
     * @param maxTileIdx   maximum tile index to match
     * @param searchRadius search radius in tiles
     * @return {offsetX, offsetY} from ref to pattern center, or null if not found
     */
    int[] findPatternOffset(int refX, int refY, int minTileIdx, int maxTileIdx, int searchRadius);

    /**
     * Saves the player/camera position for returning from a big ring special stage.
     */
    void saveBigRingReturnPosition(int playerX, int playerY, int cameraX, int cameraY);

    // --- Game-specific providers ---

    /**
     * Returns the level event provider for the current game.
     * Object code may cast to the game-specific type (e.g., Sonic2LevelEventManager)
     * for methods not on the base interface.
     *
     * @return the level event provider, or null if unavailable
     */
    default LevelEventProvider levelEventProvider() { return null; }

    /**
     * Returns the title card provider for the current game.
     *
     * @return the title card provider, or null if unavailable
     */
    default TitleCardProvider titleCardProvider() { return null; }

    /**
     * Returns a game-specific service by type, or null if not available.
     * Used for game-specific singletons (e.g., Sonic1SwitchManager, Sonic2SpecialStageManager)
     * that don't have cross-game abstract interfaces.
     * <p>
     * The service is resolved through the current {@link com.openggf.game.GameModule}.
     *
     * @param type the service class
     * @param <T>  the service type
     * @return the service instance, or null if not registered for the current game
     */
    default <T> T gameService(Class<T> type) { return null; }
}
