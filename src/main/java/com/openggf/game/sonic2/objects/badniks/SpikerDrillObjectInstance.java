package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Spiker Drill (0x93) - drill projectile thrown by Spiker in HTZ.
 * Moves vertically at a constant speed and flips horizontally each frame.
 */
public class SpikerDrillObjectInstance extends AbstractObjectInstance implements TouchResponseProvider {
    private static final int COLLISION_SIZE_INDEX = 0x12; // From Obj92_SubObjData
    private static final int Y_VELOCITY = 0x200; // 2 pixels/frame in 8.8 fixed

    private int currentX;
    private int currentY;
    private int yVelocity;
    private final SubpixelMotion.State motionState;
    private boolean hFlip;
    private final boolean vFlip;

    public SpikerDrillObjectInstance(ObjectSpawn spawn, int x, int y, boolean xFlip, boolean yFlip) {
        super(spawn, "SpikerDrill");
        this.currentX = x;
        this.currentY = y;
        this.hFlip = xFlip;
        this.vFlip = yFlip;
        // ROM: if y_flip set, velocity stays +2 (down). Otherwise it's negated.
        this.yVelocity = yFlip ? Y_VELOCITY : -Y_VELOCITY;
        this.motionState = new SubpixelMotion.State(x, y, 0, 0, 0, this.yVelocity);
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (!isOnScreenX(128)) {
            setDestroyed(true);
            return;
        }

        hFlip = !hFlip; // bchg #render_flags.x_flip, render_flags(a0)

        motionState.y = currentY;
        motionState.yVel = yVelocity;
        SubpixelMotion.moveSprite2(motionState);
        currentY = motionState.y;
    }

    @Override
    public int getCollisionFlags() {
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return buildSpawnAt(currentX, currentY);
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
    public int getPriorityBucket() {
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.SPIKER);
        if (renderer == null) return;

        renderer.drawFrameIndex(4, currentX, currentY, hFlip, vFlip);
    }
}
