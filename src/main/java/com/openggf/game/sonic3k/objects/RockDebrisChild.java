package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Lightweight gravity-affected rock debris fragment.
 * Spawned by {@link AizLrzRockObjectInstance} when a rock breaks.
 * <p>
 * Each fragment receives a scattered velocity and renders a specific mapping
 * frame from the parent rock's sprite sheet. Falls with gravity until offscreen,
 * then deletes itself.
 * <p>
 * ROM: BreakObjectToPieces (sonic3k.asm:45772) creates fragments with
 * velocities from word_2A8B0. Gravity = 0x18 subpixels/frame (same as
 * cork floor fragments).
 */
public class RockDebrisChild extends AbstractObjectInstance {

    private static final int GRAVITY = 0x18;

    private int currentX;
    private int currentY;
    private final int mappingFrame;
    private final String artKey;

    private final SubpixelMotion.State motionState;

    public RockDebrisChild(ObjectSpawn spawn, int xVel, int yVel,
                           int mappingFrame, String artKey) {
        super(spawn, "RockDebris");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.mappingFrame = mappingFrame;
        this.artKey = artKey;
        this.motionState = new SubpixelMotion.State(currentX, currentY, 0, 0, xVel, yVel);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }

    @Override
    public boolean isPersistent() {
        return false;
    }

    @Override
    public boolean isHighPriority() {
        return true; // ROM: ori.w #high_priority,art_tile(a1)
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        motionState.x = currentX;
        motionState.y = currentY;
        SubpixelMotion.moveSprite(motionState, GRAVITY);
        currentX = motionState.x;
        currentY = motionState.y;

        if (!isOnScreen()) {
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }
        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer != null && renderer.isReady()) {
            renderer.drawFrameIndex(mappingFrame, currentX, currentY, false, false);
        }
    }
}
