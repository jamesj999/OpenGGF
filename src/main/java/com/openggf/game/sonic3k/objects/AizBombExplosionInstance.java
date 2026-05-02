package com.openggf.game.sonic3k.objects;

import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Bomb explosion fragment from the AIZ2 battleship bombing sequence.
 *
 * <p>ROM: Obj_AIZBombExplosion (sonic3k.asm:105466).
 * 8 fragments spawned per bomb impact, each with staggered delay, position offset,
 * and one of 2 animation variants. Collision flags 0x8B active during early frames.
 *
 * <p>Unlike the falling bomb, the explosion fragments are ordinary world-space
 * sprites after they spawn. ROM parity here is the per-wrap X correction via
 * {@code Level_repeat_offset}.
 */
public class AizBombExplosionInstance extends AbstractObjectInstance implements TouchResponseProvider {

    private static final int COLLISION_FLAGS = 0x8B;
    private static final int[][][] ANIM_SCRIPTS = {
            {{1, 3}, {2, 4}, {3, 5}, {4, 5}, {5, 5}},
            {{6, 2}, {7, 3}, {8, 4}, {9, 5}, {10, 5}, {11, 5}},
    };

    /** World-space X position. */
    private int posX;
    private final int posY;
    private final int animIndex;
    private final int initialDelay;

    private int delayTimer;
    private int scriptStep;
    private int scriptDelay;
    private boolean active;

    /**
     * @param x          world X position
     * @param y          world Y position
     * @param animIndex  animation variant (0 or 1)
     * @param delay      frames to wait before becoming visible/active
     */
    public AizBombExplosionInstance(int x, int y, int animIndex, int delay) {
        super(new ObjectSpawn(x, y, 0, 0, 0, false, 0), "AIZBombExplosion");
        this.posX = x;
        this.posY = y;
        this.animIndex = Math.min(animIndex, ANIM_SCRIPTS.length - 1);
        this.initialDelay = delay;
        this.delayTimer = delay;
        this.scriptStep = 0;
        this.scriptDelay = currentStepDelay();
        this.active = (delay == 0);
    }

    @Override
    public void update(int frameCounter, PlayableEntity player) {
        if (isDestroyed()) return;

        if (!active) {
            delayTimer--;
            if (delayTimer <= 0) {
                active = true;
                scriptDelay = currentStepDelay();
            }
            return;
        }

        scriptDelay--;
        if (scriptDelay <= 0) {
            scriptStep++;
            if (scriptStep >= ANIM_SCRIPTS[animIndex].length) {
                setDestroyed(true);
                return;
            }
            scriptDelay = currentStepDelay();
        }
    }

    @Override
    public int getCollisionFlags() {
        if (!active) {
            return 0;
        }
        // ROM loc_505FC: cmp.b mapping_frame,d0 / bls.s skip collision, so
        // equality with (4 + anim) is already non-collidable.
        return currentMappingFrame() < (4 + animIndex) ? COLLISION_FLAGS : 0;
    }

    @Override
    public int getCollisionProperty() { return 0; }

    @Override
    public int getX() { return posX; }

    @Override
    public int getY() { return posY; }

    /** ROM: subtract Level_repeat_offset on wrap frames. */
    public void applyWrapOffset(int offset) {
        posX -= offset;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || !active) return;

        ObjectRenderManager rm = services().renderManager();
        if (rm == null) return;

        PatternSpriteRenderer renderer = rm.getRenderer(Sonic3kObjectArtKeys.AIZ2_BOMB_EXPLODE);
        if (renderer == null || !renderer.isReady()) return;

        renderer.drawFrameIndex(currentMappingFrame(), getX(), posY, false, false);
    }

    @Override
    public boolean isHighPriority() { return false; }

    @Override
    public int getPriorityBucket() { return 1; }

    private int currentMappingFrame() {
        int[][] script = ANIM_SCRIPTS[animIndex];
        if (scriptStep < 0 || scriptStep >= script.length) {
            return script[script.length - 1][0];
        }
        return script[scriptStep][0];
    }

    private int currentStepDelay() {
        int[][] script = ANIM_SCRIPTS[animIndex];
        if (scriptStep < 0 || scriptStep >= script.length) {
            return 1;
        }
        return script[scriptStep][1];
    }
}
