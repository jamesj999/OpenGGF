package com.openggf.level.objects;

import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.RespawnState;
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

    // Audio
    void playSfx(int soundId);
    void playMusic(int musicId);
    void fadeOutMusic();

    // Gameplay
    void spawnLostRings(PlayableEntity player, int frameCounter);

    // Context-specific managers
    Camera camera();
    GameStateManager gameState();

    // Player/sidekick access
    List<PlayableEntity> sidekicks();
}
