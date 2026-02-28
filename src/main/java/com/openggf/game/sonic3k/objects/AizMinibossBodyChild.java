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

    // ROM byte_69126:
    // dc.b 1,1,2,$FC / dc.b 7,3,4,5,$F4
    private static final int[] INTRO_FRAMES = {1, 2};
    private static final int INTRO_DELAY = 1;
    private static final int[] LOOP_FRAMES = {3, 4, 5};
    private static final int LOOP_DELAY = 7;

    private int mappingFrame = INTRO_FRAMES[0];
    private int animTimer = INTRO_DELAY;
    private boolean inLoopSection;
    private int loopIndex;

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

        animTimer--;
        if (animTimer > 0) {
            return;
        }

        if (!inLoopSection) {
            if (mappingFrame == INTRO_FRAMES[0]) {
                mappingFrame = INTRO_FRAMES[1];
                animTimer = INTRO_DELAY;
                return;
            }
            inLoopSection = true;
            loopIndex = 0;
            mappingFrame = LOOP_FRAMES[loopIndex];
            animTimer = LOOP_DELAY;
            return;
        }

        loopIndex = (loopIndex + 1) % LOOP_FRAMES.length;
        mappingFrame = LOOP_FRAMES[loopIndex];
        animTimer = LOOP_DELAY;
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
