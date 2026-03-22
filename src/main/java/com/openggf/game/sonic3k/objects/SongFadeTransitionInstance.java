package com.openggf.game.sonic3k.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Lightweight persistent object that fades out the current music, waits a
 * specified number of frames, then plays a new track and destroys itself.
 *
 * ROM equivalent: Obj_Song_Fade_Transition / Obj_Song_Fade_ToLevelMusic
 * (sonic3k.asm line 180305). The ROM spawns this as an independent object so
 * that the music transition survives the destruction of the cutscene object
 * that initiated it.
 */
public class SongFadeTransitionInstance extends AbstractObjectInstance {

    /** Number of frames to wait after fade-out starts before playing new music. */
    private final int delayFrames;

    /** Music ID to play when the delay expires. */
    private final int musicId;

    /** Frame counter since creation. */
    private int timer;

    /** Whether the initial fade-out has been issued. */
    private boolean fadeStarted;

    /**
     * @param delayFrames frames to wait after fade-out before playing new music
     * @param musicId     music ID to play when the delay expires
     */
    public SongFadeTransitionInstance(int delayFrames, int musicId) {
        super(new ObjectSpawn(0, 0, 0, 0, 0, false, 0), "SongFadeTransition");
        this.delayFrames = delayFrames;
        this.musicId = musicId;
        this.timer = 0;
        this.fadeStarted = false;
    }

    @Override
    public int getX() {
        return 0;
    }

    @Override
    public int getY() {
        return 0;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (!fadeStarted) {
            GameServices.audio().fadeOutMusic(0x28, 6);
            fadeStarted = true;
        }
        timer++;
        if (timer >= delayFrames) {
            services().playMusic(musicId);
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        // Invisible object — no rendering
    }
}
