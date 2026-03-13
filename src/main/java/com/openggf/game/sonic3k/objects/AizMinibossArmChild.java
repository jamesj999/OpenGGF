package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss arm child.
 *
 * ROM: loc_6870A / loc_68720
 * - Offset from ChildObjDat_6905C: (-0x24, +8)
 * - mapping_frame=6
 * - purely visual (no collision)
 */
public class AizMinibossArmChild extends AbstractBossChild {
    private static final int X_OFFSET = -0x24;
    private static final int Y_OFFSET = 8;

    public AizMinibossArmChild(AbstractBossInstance parent) {
        super(parent, "AIZMinibossArm", 2, 0x90);
    }

    @Override
    public void syncPositionWithParent() {
        if (parent != null && !parent.isDestroyed()) {
            int signedOffset = ((parent.getState().renderFlags & 1) != 0) ? -X_OFFSET : X_OFFSET;
            this.currentX = parent.getX() + signedOffset;
            this.currentY = parent.getY() + Y_OFFSET;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!shouldUpdate(frameCounter)) {
            return;
        }
        syncPositionWithParent();
        updateDynamicSpawn();
    }

    @Override
    public boolean isHighPriority() {
        // ROM: make_art_tile(ArtTile_AIZMiniboss,0,1) — priority bit = 1
        return true;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager rm = LevelManager.getInstance().getObjectRenderManager();
        if (rm == null) {
            return;
        }
        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ_MINIBOSS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }
        boolean hFlip = (parent != null) && ((parent.getState().renderFlags & 1) != 0);
        renderer.drawFrameIndex(6, currentX, currentY, hFlip, false);
    }
}
