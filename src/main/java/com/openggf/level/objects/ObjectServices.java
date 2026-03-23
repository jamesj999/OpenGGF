package com.openggf.level.objects;

import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.RespawnState;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.level.Level;
import java.util.List;

/**
 * Injectable service handle for game objects. Provides access to level sub-managers
 * and audio without requiring singleton lookups.
 * <p>
 * Injected by {@link ObjectManager} after object construction via
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
}
