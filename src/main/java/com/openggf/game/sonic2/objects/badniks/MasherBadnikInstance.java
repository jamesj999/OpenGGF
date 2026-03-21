package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.AbstractBadnikInstance;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Masher (0x5C) - Leaping piranha fish Badnik from EHZ.
 * Jumps up and down from a waterfall spawn point.
 */
public class MasherBadnikInstance extends AbstractBadnikInstance {
    private static final int COLLISION_SIZE_INDEX = 0x09; // collision_flags from disassembly
    private static final int INITIAL_Y_VEL = -0x400; // move.w #-$400,y_vel(a0)
    private static final int JUMP_Y_VEL = -0x500; // move.w #-$500,y_vel(a0)
    private static final int GRAVITY = 0x18; // addi.w #$18,y_vel(a0)
    private static final int JUMP_HEIGHT = 0xC0; // subi.w #$C0,d0 (192 pixels above base)

    private final int baseY; // Initial y position (lowest point)
    private int localYVel; // Current y velocity in subpixels

    public MasherBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Masher", Sonic2BadnikConfig.DESTRUCTION);
        this.baseY = spawn.y();
        this.currentY = baseY;
        this.localYVel = INITIAL_Y_VEL; // Start moving upward
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        // Apply gravity
        localYVel += GRAVITY;

        // Update Y position (velocity is in subpixels, need to convert)
        currentY += (localYVel >> 8);

        // Check if we've reached the bottom (initial y position)
        if (currentY >= baseY) {
            currentY = baseY;
            localYVel = JUMP_Y_VEL; // Jump back up
        }

        // Keep X position fixed at spawn
        currentX = spawn.x();
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        // From disassembly:
        // - Below threshold (close to peak): open mouth fast animation (anim 1)
        // - Above threshold: closed mouth slow animation (anim 0)
        // - Falling back down: closed mouth static (anim 2 = frame 0)
        int threshold = baseY - JUMP_HEIGHT;

        if (currentY < threshold) {
            // Above threshold (near peak) - closed mouth
            animFrame = 0;
        } else if (localYVel >= 0) {
            // Falling - closed mouth
            animFrame = 0;
        } else {
            // Rising and below threshold - open mouth animation
            animFrame = ((frameCounter >> 2) & 1); // Fast toggle between 0 and 1
        }
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
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

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.MASHER);
        if (renderer == null) return;

        // Render current animation frame
        boolean hFlip = false;
        boolean vFlip = false;
        renderer.drawFrameIndex(animFrame, currentX, currentY, hFlip, vFlip);
    }
}
