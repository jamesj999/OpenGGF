package com.openggf.game.sonic3k.objects;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.objects.boss.AbstractBossChild;
import com.openggf.level.objects.boss.AbstractBossInstance;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * AIZ miniboss body child.
 *
 * ROM: loc_686BE / loc_686E8
 * - X/Y offset from ChildObjDat_6905C: (0, +0x20)
 * - collision_flags=$9C (hurt)
 * - animated body frames while active
 */
public class AizMinibossBodyChild extends AbstractBossChild implements TouchResponseProvider {
    private static final int Y_OFFSET = 0x20;
    private static final int COLLISION_FLAGS = 0x9C;
    private static final int SHIELD_REACTION = 1 << 4;

    private static final int[] ACTIVE_FRAMES = {3, 4, 5};
    private static final int[] ACTIVE_DELAYS = {7, 5, 5};

    private int mappingFrame = 2;
    private int animIndex = 0;
    private int animTimer = ACTIVE_DELAYS[0];

    public AizMinibossBodyChild(AbstractBossInstance parent) {
        super(parent, "AIZMinibossBody", 3, 0x90);
    }

    @Override
    public void syncPositionWithParent() {
        if (parent != null && !parent.isDestroyed()) {
            this.currentX = parent.getX();
            this.currentY = parent.getY() + Y_OFFSET;
        }
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!shouldUpdate(frameCounter)) {
            return;
        }
        syncPositionWithParent();
        updateAnimation();
        updateDynamicSpawn();
    }

    private void updateAnimation() {
        if (parent == null || parent.getState().defeated) {
            mappingFrame = 2;
            return;
        }

        // Body animation is only active once the parent has entered active phases.
        if (parent.getState().routine < 8) {
            mappingFrame = 2;
            animIndex = 0;
            animTimer = ACTIVE_DELAYS[0];
            return;
        }

        animTimer--;
        if (animTimer > 0) {
            return;
        }

        animIndex = (animIndex + 1) % ACTIVE_FRAMES.length;
        mappingFrame = ACTIVE_FRAMES[animIndex];
        animTimer = ACTIVE_DELAYS[animIndex];
    }

    @Override
    public int getCollisionFlags() {
        if (parent == null || parent.getState().defeated || isDestroyed()) {
            return 0;
        }
        return COLLISION_FLAGS;
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public int getShieldReactionFlags() {
        return SHIELD_REACTION;
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
        boolean hFlip = parent != null && (parent.getState().renderFlags & 1) != 0;
        renderer.drawFrameIndex(mappingFrame, currentX, currentY, hFlip, false);
    }
}
