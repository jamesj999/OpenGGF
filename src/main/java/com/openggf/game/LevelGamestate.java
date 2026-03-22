package com.openggf.game;

import com.openggf.audio.GameAudioProfile;

/**
 * Manages transient state for a single level execution, such as Rings and Time.
 * Typically reset when a level is loaded or restarted (except checkpoints?).
 * Rings are always reset on level load/respawn (unless specialized checkout
 * logic exists, but normally 0).
 */
public class LevelGamestate implements LevelState {
    private final LevelTimer timer;
    private int rings;

    public LevelGamestate() {
        this.timer = new LevelTimer();
        this.rings = 0;
    }

    public void update() {
        timer.update();
    }

    public LevelTimer getTimer() {
        return timer;
    }

    public int getRings() {
        return rings;
    }

    public void setRings(int rings) {
        this.rings = Math.max(0, rings);
    }

    public void addRings(int amount) {
        if (amount != 0) {
            int previousRings = rings;
            int next = rings + amount;
            this.rings = Math.max(0, next);

            // Ring Bonus Logic: 100 and 200 rings grant an extra life
            if (amount > 0) {
                if ((previousRings < 100 && rings >= 100)
                        || (previousRings < 200 && rings >= 200)) {
                    GameServices.gameState().addLife();
                    GameAudioProfile profile = GameServices.audio().getAudioProfile();
                    if (profile != null) {
                        GameServices.audio().playMusic(profile.getExtraLifeMusicId());
                    }
                }
            }
        }
    }

    @Override
    public boolean isTimeOver() {
        return timer.isTimeOver();
    }

    @Override
    public String getDisplayTime() {
        return timer.getDisplayTime();
    }

    @Override
    public boolean shouldFlashTimer() {
        return timer.shouldFlash();
    }

    @Override
    public boolean getFlashCycle() {
        return timer.getFlashCycle();
    }

    @Override
    public void pauseTimer() {
        timer.pause();
    }

    @Override
    public boolean isTimerPaused() {
        return timer.isPaused();
    }

    @Override
    public int getElapsedSeconds() {
        return timer.getElapsedSeconds();
    }
}
