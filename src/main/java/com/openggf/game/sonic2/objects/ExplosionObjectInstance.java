package com.openggf.game.sonic2.objects;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;

import com.openggf.graphics.GLCommand;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

public class ExplosionObjectInstance extends AbstractObjectInstance {
    private final ObjectRenderManager renderManager;
    private int animTimer = 0;
    private int animFrame = 0;
    // S1 disassembly: obTimeFrame = 7, counts 7→0 then advances = 8 game frames per sprite frame
    private static final int ANIM_DELAY = 8;
    private static final int MAX_FRAME = 4;

    public ExplosionObjectInstance(int id, int x, int y, ObjectRenderManager renderManager) {
        super(new ObjectSpawn(x, y, id, 0, 0, false, 0), "Explosion");
        this.renderManager = renderManager;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Animation
        animTimer++;
        if (animTimer >= ANIM_DELAY) {
            animTimer = 0;
            animFrame++;
            if (animFrame > MAX_FRAME) {
                setDestroyed(true);
            }
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || animFrame > MAX_FRAME)
            return;
        renderManager.getExplosionRenderer().drawFrameIndex(animFrame, spawn.x(), spawn.y(), false, false);
    }
}
