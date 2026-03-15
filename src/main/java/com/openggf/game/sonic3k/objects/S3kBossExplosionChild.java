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
 * Plays sfx_Explode (0xB4) on init, animates through AniRaw_BossExplosion.
 *
 * ROM animation format: Animate_RawMultiDelay — (delay, frame) pairs.
 * AniRaw_BossExplosion (sonic3k.asm:176871):
 *   dc.b 0,0, 0,1, 1,1, 2,2, 3,3, 4,4, 5,4, $F4
 * $F4 = end (calls Go_Delete_Sprite via $34 callback).
 */
public class S3kBossExplosionChild extends AbstractObjectInstance {
    // ROM: (delay, frame) pairs from AniRaw_BossExplosion
    // delay N = show frame for N+1 ticks
    private static final int[][] ANIM_PAIRS = {
            {0, 0},  // frame 0, 1 tick
            {0, 1},  // frame 1, 1 tick
            {1, 1},  // frame 1, 2 ticks
            {2, 2},  // frame 2, 3 ticks
            {3, 3},  // frame 3, 4 ticks
            {4, 4},  // frame 4, 5 ticks
            {5, 4},  // frame 4, 6 ticks  (total: 22 ticks)
    };

    private int pairIndex;
    private int delayCounter;

    public S3kBossExplosionChild(int x, int y) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "S3kBossExplosion");
        this.pairIndex = 0;
        this.delayCounter = ANIM_PAIRS[0][0];
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // SFX is played by the controller (sub_52850), not each child
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }
        // Advance to next pair
        pairIndex++;
        if (pairIndex >= ANIM_PAIRS.length) {
            setDestroyed(true);
            return;
        }
        delayCounter = ANIM_PAIRS[pairIndex][0];
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || pairIndex >= ANIM_PAIRS.length) return;
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
        if (rm == null) return;
        // ROM: Obj_BossExplosion2 uses ArtTile_BossExplosion2, loaded from PLC $5A entry 3
        PatternSpriteRenderer renderer = rm.getBossExplosionRenderer();
        if (renderer == null || !renderer.isReady()) return;
        renderer.drawFrameIndex(ANIM_PAIRS[pairIndex][1], spawn.x(), spawn.y(), false, false);
    }

    @Override
    public int getPriorityBucket() {
        return 1;
    }
}
