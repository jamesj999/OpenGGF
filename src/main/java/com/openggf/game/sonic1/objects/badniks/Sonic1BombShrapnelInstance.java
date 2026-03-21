package com.openggf.game.sonic1.objects.badniks;

import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Bomb Shrapnel child object (subtype 6) - Flying debris pieces after the bomb fuse expires.
 * <p>
 * Based on docs/s1disasm/_incObj/5F Bomb Enemy.asm, Bom_End routine (routine 6).
 * <p>
 * Each shrapnel piece flies with predefined X/Y velocity from Bom_ShrSpeed, with
 * gravity applied each frame (addi.w #$18,obVelY). The shrapnel hurts Sonic on
 * contact (obColType = $98: HURT category $80, size index $18 = 4x4 pixels).
 * <p>
 * Shrapnel is deleted when it goes off-screen (tst.b obRender(a0) / bpl.w DeleteObject).
 * <p>
 * Animations: Uses shrapnel animation (Ani_Bomb index 4): frames $A, $B at speed 3.
 */
public class Sonic1BombShrapnelInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // From disassembly: move.b #$98,obColType(a1)
    // $80 = HURT category, $18 = size index (width 4, height 4)
    private static final int COLLISION_SIZE_INDEX = 0x18;

    // From disassembly: addi.w #$18,obVelY(a0) (gravity per frame)
    private static final int GRAVITY = 0x18;

    // Animation speed 3 + 1 = 4 ticks per frame
    private static final int ANIM_SPEED = 3 + 1;

    // Shrapnel mapping frames (from Ani_Bomb .shrapnel: $A, $B)
    private static final int[] SHRAPNEL_FRAMES = {10, 11};

    // From disassembly: move.b #3,obPriority (inherited from parent init)
    // Bom_End does not change priority; shrapnel inherits from the fuse which
    // inherited from the bomb body's priority of 3.
    private static final int RENDER_PRIORITY = 3;

    private final LevelManager levelManager;
    private int currentX;
    private int currentY;
    private int xVelocity;
    private int yVelocity;
    private final SubpixelMotion.State motionState;
    private int animTickCounter;
    private boolean destroyed;

    /**
     * Creates a shrapnel piece at the given position with the given velocity.
     *
     * @param x          Spawn X position (from fuse/bomb position)
     * @param y          Spawn Y position (from original bomb Y)
     * @param xVel       Initial X velocity (from Bom_ShrSpeed)
     * @param yVel       Initial Y velocity (from Bom_ShrSpeed)
     * @param levelManager Level manager reference
     */
    public Sonic1BombShrapnelInstance(int x, int y, int xVel, int yVel,
                                      LevelManager levelManager) {
        super(new ObjectSpawn(x, y, 0x5F, 6, 0, false, 0), "BombShrapnel");
        this.levelManager = levelManager;
        this.currentX = x;
        this.currentY = y;
        this.xVelocity = xVel;
        this.yVelocity = yVel;
        this.motionState = new SubpixelMotion.State(x, y, 0, 0, xVel, yVel);
        this.animTickCounter = 0;
        this.destroyed = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        if (destroyed) {
            return;
        }

        // Bom_End:
        // bsr.w SpeedToPos
        // addi.w #$18,obVelY(a0) - gravity
        // AnimateSprite
        // tst.b obRender(a0) / bpl.w DeleteObject - delete if off-screen
        // bra.w DisplaySprite

        // SpeedToPos + gravity: apply velocity then add gravity
        motionState.x = currentX;
        motionState.y = currentY;
        motionState.xVel = xVelocity;
        motionState.yVel = yVelocity;
        SubpixelMotion.moveSprite2(motionState);
        currentX = motionState.x;
        currentY = motionState.y;

        // Apply gravity
        yVelocity += GRAVITY;

        // Check if off-screen (tst.b obRender(a0) / bpl.w DeleteObject)
        // bset #7,obRender(a1) was set at spawn - bit 7 = on-screen flag
        if (!isOnScreenX(160)) {
            destroyed = true;
            setDestroyed(true);
            return;
        }

        // Animate
        animTickCounter++;
    }

    /**
     * Returns the mapping frame index for shrapnel animation.
     * From Ani_Bomb .shrapnel: dc.b 3, $A, $B, afEnd
     */
    private int getMappingFrame() {
        int step = (animTickCounter / ANIM_SPEED) % SHRAPNEL_FRAMES.length;
        return SHRAPNEL_FRAMES[step];
    }

    // --- TouchResponseProvider ---

    @Override
    public int getCollisionFlags() {
        if (destroyed) {
            return 0;
        }
        // obColType = $98: HURT category ($80) + size index $18
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    // --- Rendering ---

    @Override
    public boolean isPersistent() {
        // DisplaySprite: persists while on screen
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
        // Shrapnel does not flip based on facing direction
        renderer.drawFrameIndex(frame, currentX, currentY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Red hitbox (shrapnel hurts Sonic)
        ctx.drawRect(currentX, currentY, 4, 4, 1f, 0f, 0f);

        // Velocity arrow
        if (xVelocity != 0 || yVelocity != 0) {
            int endX = currentX + (xVelocity >> 5);
            int endY = currentY + (yVelocity >> 5);
            ctx.drawArrow(currentX, currentY, endX, endY, 1f, 0.5f, 0f);
        }

        ctx.drawWorldLabel(currentX, currentY, -2, "Shrapnel", DebugColor.RED);
    }

    // --- Position accessors ---

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, 0x5F, 6, 0, false, 0);
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
