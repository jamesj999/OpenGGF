package com.openggf.game.sonic1.objects.badniks;

import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Bomb Fuse child object (subtype 4) - The sparking fuse that rises from the bomb's body.
 * <p>
 * Based on docs/s1disasm/_incObj/5F Bomb Enemy.asm, Bom_Display routine (routine 4).
 * <p>
 * The fuse moves with obVelY = $10 (rising up, or -$10 if upside-down via btst #1,obStatus).
 * After bom_time expires, it creates 4 shrapnel pieces and resets itself (which in ROM
 * means the object is recycled; in our engine we just destroy it).
 * <p>
 * Animations: Uses fuse animation (Ani_Bomb index 3): frames 8, 9 at speed 3.
 */
public class Sonic1BombFuseInstance extends AbstractObjectInstance {

    // Animation speed 3 + 1 = 4 ticks per frame
    private static final int ANIM_SPEED = 3 + 1;

    // Fuse mapping frames (from Ani_Bomb .fuse: 8, 9)
    private static final int[] FUSE_FRAMES = {8, 9};

    // From disassembly: move.b #3,obPriority(a0)
    private static final int RENDER_PRIORITY = 3;

    private final LevelManager levelManager;
    private final Sonic1BombBadnikInstance parent;
    private int currentX;
    private int currentY;
    private final int origY;        // bom_origY (objoff_34): original Y position
    private int yVelocity;
    private final SubpixelMotion.State motionState;
    private int timer;              // bom_time
    private int animTickCounter;
    private boolean facingLeft;
    private boolean ceilingBomb;    // obStatus bit 1: parent is upside-down
    private boolean destroyed;

    /**
     * Creates a fuse child at the bomb's position.
     *
     * @param x             Bomb X position
     * @param y             Bomb Y position
     * @param facingLeft    Bomb's facing direction (status bit 0)
     * @param ceilingBomb   Whether parent is upside-down (status bit 1)
     * @param fuseTime      Countdown timer (143 frames)
     * @param fuseYSpeed    Vertical speed ($10 or -$10 for upside-down)
     * @param parent        Parent bomb instance for shrapnel spawning
     * @param levelManager  Level manager reference
     */
    public Sonic1BombFuseInstance(int x, int y, boolean facingLeft, boolean ceilingBomb,
                                  int fuseTime, int fuseYSpeed,
                                  Sonic1BombBadnikInstance parent,
                                  LevelManager levelManager) {
        super(new ObjectSpawn(x, y, 0x5F, 4, 0, false, 0), "BombFuse");
        this.levelManager = levelManager;
        this.parent = parent;
        this.currentX = x;
        this.currentY = y;
        this.origY = y;
        this.yVelocity = fuseYSpeed;
        this.motionState = new SubpixelMotion.State(x, y, 0, 0, 0, fuseYSpeed);
        this.timer = fuseTime;
        this.facingLeft = facingLeft;
        this.ceilingBomb = ceilingBomb;
        this.animTickCounter = 0;
        this.destroyed = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }

        // loc_11B70: countdown and move
        timer--;
        if (timer < 0) {
            // loc_11B7C: Timer expired - spawn shrapnel and destroy
            // In ROM: clears bom_time, resets obRoutine to 0, restores origY,
            // then creates 4 shrapnel at current position.
            currentY = origY;
            parent.spawnShrapnel(currentX, currentY);

            // Destroy the fuse
            destroyed = true;
            setDestroyed(true);
            return;
        }

        // SpeedToPos: apply Y velocity
        motionState.y = currentY;
        motionState.yVel = yVelocity;
        SubpixelMotion.moveSprite2(motionState);
        currentY = motionState.y;

        // Animate
        animTickCounter++;
    }

    /**
     * Returns the mapping frame index for fuse animation.
     * From Ani_Bomb .fuse: dc.b 3, 8, 9, afEnd
     */
    private int getMappingFrame() {
        int step = (animTickCounter / ANIM_SPEED) % FUSE_FRAMES.length;
        return FUSE_FRAMES[step];
    }

    @Override
    public boolean isPersistent() {
        // RememberState: persists while on screen
        return !destroyed && isOnScreenX(160);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(RENDER_PRIORITY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (destroyed) {
            return;
        }

        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(ObjectArtKeys.BOMB);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        int frame = getMappingFrame();
        // obStatus bit 1 inherited from parent: V-flip for ceiling bombs
        renderer.drawFrameIndex(frame, currentX, currentY, !facingLeft, ceilingBomb);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        ctx.drawRect(currentX, currentY, 4, 8, 1f, 0.5f, 0f);
        String label = "Fuse t" + timer;
        ctx.drawWorldLabel(currentX, currentY, -2, label, DebugColor.ORANGE);
    }

    @Override
    public int getX() {
        return currentX;
    }

    @Override
    public int getY() {
        return currentY;
    }
}
