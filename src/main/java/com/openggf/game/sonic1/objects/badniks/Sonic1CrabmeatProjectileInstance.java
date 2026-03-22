package com.openggf.game.sonic1.objects.badniks;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractProjectileInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Crabmeat Projectile (energy ball) - Fired by Crabmeat in opposite directions.
 * Uses shared object ID 0x1F with routine 6/8 in the disassembly.
 * <p>
 * Based on docs/s1disasm/_incObj/1F Crabmeat.asm (Crab_BallMain / Crab_BallMove).
 * <p>
 * Behavior:
 * <ul>
 *   <li>Launched with horizontal velocity (+/-$100) and upward velocity (-$400)</li>
 *   <li>Gravity applied each frame via ObjectFall ($38 subpixels/frame²)</li>
 *   <li>Alternates between ball animation frames 5 and 6 at speed 1</li>
 *   <li>Collision type $87: HURT category + size index 7</li>
 *   <li>Deleted when falling below level bottom boundary + $E0</li>
 * </ul>
 */
public class Sonic1CrabmeatProjectileInstance extends AbstractProjectileInstance {

    // Standard Mega Drive gravity: $38 subpixels/frame² (ObjectFall)
    private static final int GRAVITY = 0x38;

    // Collision: obColType = $87 -> category HURT ($80), size index 7
    private static final int COLLISION_SIZE_INDEX = 0x07;

    // Animation: speed 1 = each frame shows for 2 game frames
    private static final int ANIM_SPEED = 2;

    // Mapping frames for projectile: ball1=5, ball2=6
    private static final int BALL_FRAME_1 = 5;
    private static final int BALL_FRAME_2 = 6;

    // Level bottom margin for deletion: $E0 (224 pixels)
    private static final int BOTTOM_MARGIN = 0xE0;

    private int animTimer;
    private int renderedFrame;

    /**
     * Creates a Crabmeat projectile.
     *
     * @param x    Starting X position
     * @param y    Starting Y position
     * @param xVel X velocity in subpixels (+/-$100)
     * @param yVel Initial Y velocity in subpixels (-$400, upward)
     * @param parent Reference to parent Crabmeat (unused, kept for spawn-site compatibility)
     * @param levelManager Level manager reference (unused, kept for spawn-site compatibility)
     */
    public Sonic1CrabmeatProjectileInstance(int x, int y, int xVel, int yVel,
            Sonic1CrabmeatBadnikInstance parent) {
        super(new ObjectSpawn(x, y, 0x1F, 0, 0, false, 0), "CrabmeatBall",
                xVel, yVel, GRAVITY, COLLISION_SIZE_INDEX, BOTTOM_MARGIN);
        this.animTimer = 0;
        this.renderedFrame = BALL_FRAME_1;
    }

    /**
     * S1 ObjectFall order: gravity is applied BEFORE the move, not after.
     * This differs from the S3K moveSprite convention (move-then-gravity).
     */
    @Override
    protected void updateMotion() {
        // ObjectFall: addi.w #$38,obVelY(a0) then SpeedToPos
        motionState.yVel += gravity;
        SubpixelMotion.moveSprite2(motionState);
    }

    @Override
    protected void updateExtra(int frameCounter, AbstractPlayableSprite player) {
        // Animate: alternate between ball frame 1 and 2 at speed 1
        animTimer++;
        if (animTimer >= ANIM_SPEED) {
            animTimer = 0;
            renderedFrame = (renderedFrame == BALL_FRAME_1) ? BALL_FRAME_2 : BALL_FRAME_1;
        }
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(3); // obPriority = 3
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.CRABMEAT);
        if (renderer == null) return;

        renderer.drawFrameIndex(renderedFrame, currentX, currentY, false, false);
    }
}
