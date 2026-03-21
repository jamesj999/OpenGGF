package com.openggf.level.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.LevelState;
import com.openggf.game.RespawnState;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Production implementation of {@link ObjectServices} backed by existing singletons.
 * A single instance is held by {@link ObjectManager} and shared across all objects.
 */
public class DefaultObjectServices implements ObjectServices {

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
    public void playSfx(int soundId) {
        AudioManager.getInstance().playSfx(soundId);
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
    public void spawnLostRings(AbstractPlayableSprite player, int frameCounter) {
        LevelManager.getInstance().spawnLostRings(player, frameCounter);
    }
}
