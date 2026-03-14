package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * S3K boss explosion child (ROM: Obj_BossExplosion2).
 * Plays sfx_Explode (0xB4) on init, animates through AniRaw_BossExplosion frames.
 * Uses ArtTile_BossExplosion2 — same ArtNem_BossExplosion as S2.
 */
public class S3kBossExplosionChild extends AbstractObjectInstance {
    private static final int[] ANIM_SEQUENCE = {0, 0, 0, 1, 1, 1, 2, 2, 3, 3, 4, 4, 5, 4};

    private int animTick;
    private boolean sfxPlayed;

    public S3kBossExplosionChild(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "S3kBossExplosion");
        this.animTick = 0;
        this.sfxPlayed = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!sfxPlayed) {
            AudioManager.getInstance().playSfx(Sonic3kSfx.EXPLODE.id);
            sfxPlayed = true;
        }
        // Increment AFTER render pass uses the current tick, so ANIM_SEQUENCE[0] is displayed
        if (animTick + 1 >= ANIM_SEQUENCE.length) {
            setDestroyed(true);
            return;
        }
        animTick++;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || animTick >= ANIM_SEQUENCE.length) return;
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
        if (rm == null) return;
        PatternSpriteRenderer renderer = rm.getBossExplosionRenderer();
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFrameIndex(ANIM_SEQUENCE[animTick], spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return 1;
    }
}
