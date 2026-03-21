package com.openggf.level.objects;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Base class for collapsing platform fragments that share "delay timer, gravity fall,
 * off-screen destroy" behavior.
 *
 * <p>ROM pattern: each fragment waits for an individual delay countdown, then falls with
 * gravity via ObjectFall / MoveSprite until it leaves the screen. Subclasses provide only
 * rendering via {@link #appendRenderCommands(List)}.
 *
 * <p>Position arithmetic uses 16.16 fixed-point storage with the ROM-standard ObjectFall
 * velocity integration:
 * <pre>
 *     move.w  obVelY(a0),d0           ; read OLD velocity
 *     addi.w  #$38,obVelY(a0)         ; apply gravity for NEXT frame
 *     ext.l   d0 / asl.l #8,d0        ; shift velocity into 16.16
 *     add.l   d0,obY(a0)              ; update position
 * </pre>
 *
 * <p>This differs from {@link GravityDebrisChild} which uses {@link SubpixelMotion} (16:8
 * format) and has initial X/Y velocity. Collapsing platform fragments have zero initial
 * velocity and use the delay-before-falling pattern instead.
 *
 * @see GravityDebrisChild
 */
public abstract class AbstractFallingFragment extends AbstractObjectInstance {

    /** Standard gravity constant from ObjectFall: addi.w #$38,obVelY(a0). */
    protected static final int GRAVITY = 0x38;

    /** Off-screen margin for destroy check (pixels beyond camera viewport). */
    private static final int OFF_SCREEN_MARGIN = 128;

    private int x;
    private int y;
    private int yFrac;
    private int velY;
    private int delayTimer;
    private final int priority;

    /**
     * @param spawn      spawn point (typically parent position)
     * @param name       debug name for this fragment type
     * @param delay      frame count to wait before falling begins
     * @param priority   render priority bucket
     */
    protected AbstractFallingFragment(ObjectSpawn spawn, String name,
                                      int delay, int priority) {
        super(spawn, name);
        this.x = spawn.x();
        this.y = spawn.y();
        this.delayTimer = delay;
        this.priority = priority;
    }

    @Override
    public final int getX() {
        return x;
    }

    @Override
    public final int getY() {
        return y;
    }

    @Override
    public final void update(int frameCounter, AbstractPlayableSprite player) {
        if (isDestroyed()) {
            return;
        }

        if (delayTimer > 0) {
            delayTimer--;
            return;
        }

        // ObjectFall: read old velocity, apply gravity, then update position
        int oldVelY = (int) (short) velY;
        velY += GRAVITY;
        int y32 = (y << 16) | (yFrac & 0xFFFF);
        y32 += oldVelY << 8;
        y = y32 >> 16;
        yFrac = y32 & 0xFFFF;

        if (!isOnScreen(OFF_SCREEN_MARGIN)) {
            setDestroyed(true);
        }
    }

    @Override
    public final int getPriorityBucket() {
        return RenderPriority.clamp(priority);
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed();
    }
}
