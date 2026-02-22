package com.openggf.game.sonic3k.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.util.List;

/**
 * Lightning shield spark particle (sonic3k.asm:34811-34858).
 * Created by {@link LightningShieldObjectInstance#triggerSparks()} — 4 sparks fly
 * diagonally with gravity, playing animation script 2 (frames [0,1,2] delay=3 LOOP).
 * Auto-deletes after {@link #MAX_LIFE} frames.
 */
public class LightningSparkObjectInstance extends AbstractObjectInstance {

    /** Gravity per frame in subpixels (sonic3k.asm:34849: addi.w #$18,y_vel(a0)) */
    private static final int GRAVITY = 0x18;

    /** Lifetime in frames before auto-delete */
    private static final int MAX_LIFE = 21;

    /** Animation script index for sparks (Ani_LightningShield script 2) */
    private static final int SPARK_ANIM_SCRIPT = 2;

    // Relies on parent (LightningShieldObjectInstance) passing valid DPLC renderer.
    // currentMappingFrame indices must match the DPLC-remapped frame table.
    private final PlayerSpriteRenderer dplcRenderer;
    private final SpriteAnimationSet animSet;

    private int currentX;
    private int currentY;
    private int xSub;
    private int ySub;
    private int xVel;
    private int yVel;

    private int life;
    private int frameIndex;
    private int delayCounter;
    private int currentMappingFrame;

    private final SubpixelMotion.State motionState = new SubpixelMotion.State(0, 0, 0, 0, 0, 0);

    public LightningSparkObjectInstance(int x, int y, int xVel, int yVel,
            PlayerSpriteRenderer dplcRenderer, SpriteAnimationSet animSet) {
        super(null, "LightningSpark");
        this.currentX = x;
        this.currentY = y;
        this.xSub = 0;
        this.ySub = 0;
        this.xVel = xVel;
        this.yVel = yVel;
        this.dplcRenderer = dplcRenderer;
        this.animSet = animSet;
        this.life = 0;
        this.frameIndex = 0;
        this.currentMappingFrame = 0;
        initAnimation();
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
        return true; // Don't cull by spawn position — has no spawn
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        life++;
        if (life >= MAX_LIFE) {
            setDestroyed(true);
            return;
        }

        // Apply subpixel movement with gravity
        motionState.x = currentX; motionState.y = currentY;
        motionState.xSub = xSub;  motionState.ySub = ySub;
        motionState.xVel = xVel;  motionState.yVel = yVel;
        SubpixelMotion.moveSprite(motionState, GRAVITY);
        currentX = motionState.x; currentY = motionState.y;
        xSub = motionState.xSub;  ySub = motionState.ySub;
        yVel = motionState.yVel;

        stepAnimation();
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed() || dplcRenderer == null) return;
        dplcRenderer.drawFrame(currentMappingFrame, currentX, currentY, false, false);
    }

    private void initAnimation() {
        frameIndex = 0;
        if (animSet != null) {
            SpriteAnimationScript script = animSet.getScript(SPARK_ANIM_SCRIPT);
            if (script != null) {
                delayCounter = script.delay();
                if (!script.frames().isEmpty()) {
                    currentMappingFrame = script.frames().get(0);
                }
            }
        }
    }

    private void stepAnimation() {
        if (animSet == null) return;
        SpriteAnimationScript script = animSet.getScript(SPARK_ANIM_SCRIPT);
        if (script == null || script.frames().isEmpty()) return;

        if (delayCounter > 0) {
            delayCounter--;
            return;
        }
        delayCounter = script.delay();

        frameIndex++;
        if (frameIndex >= script.frames().size()) {
            frameIndex = 0; // Always loop for sparks
        }
        currentMappingFrame = script.frames().get(frameIndex);
    }
}
