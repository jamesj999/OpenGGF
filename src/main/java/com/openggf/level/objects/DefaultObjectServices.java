package com.openggf.level.objects;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.RespawnState;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import java.util.List;

/**
 * Production implementation of {@link ObjectServices} backed by existing singletons.
 * A single instance is held by {@link ObjectManager} and shared across all objects.
 */
public class DefaultObjectServices implements ObjectServices {

    private static final java.util.logging.Logger LOG =
        java.util.logging.Logger.getLogger(DefaultObjectServices.class.getName());

    @Override
    public ObjectManager objectManager() {
        return LevelManager.getInstance().getObjectManager();
    }

    @Override
    public ObjectRenderManager renderManager() {
        return LevelManager.getInstance().getObjectRenderManager();
    }

    @Override
    public LevelState levelGamestate() {
        return LevelManager.getInstance().getLevelGamestate();
    }

    @Override
    public RespawnState checkpointState() {
        return LevelManager.getInstance().getCheckpointState();
    }

    @Override
    public Level currentLevel() {
        return LevelManager.getInstance().getCurrentLevel();
    }

    @Override
    public int romZoneId() {
        return LevelManager.getInstance().getRomZoneId();
    }

    @Override
    public int currentAct() {
        return LevelManager.getInstance().getCurrentAct();
    }

    @Override
    public int featureZoneId() {
        return LevelManager.getInstance().getFeatureZoneId();
    }

    @Override
    public int featureActId() {
        return LevelManager.getInstance().getFeatureActId();
    }

    @Override
    public ZoneFeatureProvider zoneFeatureProvider() {
        return LevelManager.getInstance().getZoneFeatureProvider();
    }

    @Override
    public void playSfx(int soundId) {
        AudioManager.getInstance().playSfx(soundId);
    }

    @Override
    public void playSfx(GameSound sound) {
        AudioManager.getInstance().playSfx(sound);
    }

    @Override
    public void playMusic(int musicId) {
        AudioManager.getInstance().playMusic(musicId);
    }

    @Override
    public void fadeOutMusic() {
        AudioManager.getInstance().fadeOutMusic();
    }

    @Override
    public void spawnLostRings(PlayableEntity player, int frameCounter) {
        if (player instanceof com.openggf.sprites.playable.AbstractPlayableSprite aps) {
            LevelManager.getInstance().spawnLostRings(aps, frameCounter);
        } else {
            LOG.warning("spawnLostRings: player is not AbstractPlayableSprite, rings not spawned");
        }
    }

    @Override
    public Camera camera() {
        return Camera.getInstance();
    }

    @Override
    public GameStateManager gameState() {
        return GameStateManager.getInstance();
    }

    @Override
    public List<PlayableEntity> sidekicks() {
        return List.copyOf(SpriteManager.getInstance().getSidekicks());
    }
}
