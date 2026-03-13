package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ Miniboss (0x90) - Debris fragment.
 * Six instances spawned with specific offsets from camera position.
 * Uses AIZ_MINIBOSS_SMALL art key with 3 frames.
 *
 * ROM: loc_68DFA / loc_68E8C
 * - Spawns with fixed Y rows and per-piece X velocity (word_68E60)
 * - Immediately transitions to MoveSprite2 movement (no drift/wait phase)
 */
public class AizMinibossDebrisChild extends AbstractObjectInstance {
    private int worldX;
    private int worldY;
    private int xFixed;
    private int yFixed;
    private final int xVel;
    private final int mappingFrame;
    public AizMinibossDebrisChild(int x, int y, int xVel, int mappingFrame) {
        super(new ObjectSpawn(x, y, 0x90, 0, 0, false, 0), "AIZMinibossDebris");
        this.worldX = x;
        this.worldY = y;
        this.xFixed = x << 16;
        this.yFixed = y << 16;
        this.xVel = xVel;
        this.mappingFrame = mappingFrame;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        xFixed += (xVel << 8);
        worldX = xFixed >> 16;
        worldY = yFixed >> 16;

        // ROM removes these once they scroll far from the camera window.
        if (!isOnScreen(256)) {
            setDestroyed(true);
        }
    }

    @Override
    public int getPriorityBucket() {
        // ROM: ObjDat3_46F60 priority $380 / $80 = 7
        return 7;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS_SMALL);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        renderer.drawFrameIndex(mappingFrame, worldX, worldY, false, false);
    }

    @Override
    public int getX() {
        return worldX;
    }

    @Override
    public int getY() {
        return worldY;
    }
}
