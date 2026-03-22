package com.openggf.game.sonic1.objects.badniks;

import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Buzz Bomber Missile (0x23) - Projectile fired by Buzz Bomber.
 * <p>
 * Based on docs/s1disasm/_incObj/23 Buzz Bomber Missile.asm.
 * <p>
 * Phases:
 * <ul>
 *   <li>FLARE (routine 0-2): Countdown timer, then play flare animation.
 *       Animation ends with afRoutine which advances to ACTIVE phase.</li>
 *   <li>ACTIVE (routine 4): Moves with velocity, hurts Sonic (obColType=$87).</li>
 * </ul>
 * <p>
 * The missile tracks its parent Buzz Bomber. If the parent is destroyed,
 * the missile is deleted immediately (Msl_ChkCancel).
 */
public class Sonic1BuzzBomberMissileInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // Collision: obColType = $87 -> category HURT ($80), size index 7
    // Size 7: width=$06 (6px), height=$06 (6px)
    private static final int COLLISION_SIZE_INDEX = 0x07;

    // Flare countdown: objoff_32 = $E (14 frames before flare animation starts)
    private static final int FLARE_COUNTDOWN = 0x0E;

    // Flare animation: speed=7 means each frame shows for 8 game frames, 2 frames total = 16 frames
    private static final int FLARE_ANIM_SPEED = 8;
    private static final int FLARE_FRAME_COUNT = 2;

    // Missile active animation: speed=1 means each frame shows for 2 game frames
    private static final int ACTIVE_ANIM_SPEED = 2;

    // Level bottom margin for deletion: $E0 (224 pixels)
    private static final int BOTTOM_MARGIN = 0xE0;

    private enum Phase {
        FLARE_COUNTDOWN,  // Initial countdown before flare
        FLARE_ANIM,       // Flare animation playing
        ACTIVE            // Moving missile, hurts Sonic
    }

    private int currentX;
    private int currentY;
    private final int xVelocity;
    private final int yVelocity;
    private final SubpixelMotion.State motionState;
    private final boolean facingLeft;
    private final Sonic1BuzzBomberBadnikInstance parent;

    private Phase phase;
    private int flareTimer;
    private int animTimer;
    private int animFrame;
    private int renderedFrame;
    private boolean collisionEnabled;

    /**
     * Creates a missile spawned by a Buzz Bomber.
     *
     * @param x         Starting X position
     * @param y         Starting Y position
     * @param xVel      X velocity in subpixels ($200 or -$200)
     * @param yVel      Y velocity in subpixels ($200, always downward)
     * @param facingLeft Direction the parent was facing
     * @param parent    Reference to parent Buzz Bomber (for cancellation tracking)
     * @param levelManager Level manager reference
     */
    public Sonic1BuzzBomberMissileInstance(int x, int y, int xVel, int yVel,
            boolean facingLeft, Sonic1BuzzBomberBadnikInstance parent) {
        super(new ObjectSpawn(x, y, 0x23, 0, 0, false, 0), "BuzzBomberMissile");
        this.currentX = x;
        this.currentY = y;
        this.xVelocity = xVel;
        this.yVelocity = yVel;
        this.motionState = new SubpixelMotion.State(x, y, 0, 0, xVel, yVel);
        this.facingLeft = facingLeft;
        this.parent = parent;
        
        this.phase = Phase.FLARE_COUNTDOWN;
        this.flareTimer = FLARE_COUNTDOWN;
        this.animTimer = 0;
        this.animFrame = 0;
        this.renderedFrame = 0;
        this.collisionEnabled = false;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        // Msl_ChkCancel: if parent Buzz Bomber was destroyed, delete missile
        if (parent != null && parent.isDestroyed()) {
            setDestroyed(true);
            return;
        }

        switch (phase) {
            case FLARE_COUNTDOWN -> updateFlareCountdown();
            case FLARE_ANIM -> updateFlareAnimation();
            case ACTIVE -> updateActive();
        }
    }

    /**
     * Phase 1: Countdown before flare animation starts.
     * Decrements flareTimer each frame. When expired, transitions to flare animation.
     */
    private void updateFlareCountdown() {
        flareTimer--;
        if (flareTimer < 0) {
            phase = Phase.FLARE_ANIM;
            animTimer = 0;
            animFrame = 0;
            renderedFrame = 0; // Flare frame 0
        }
    }

    /**
     * Phase 2: Flare animation plays (2 frames at speed 7 = 16 game frames total).
     * When complete, afRoutine advances routine -> transitions to ACTIVE phase.
     */
    private void updateFlareAnimation() {
        animTimer++;
        if (animTimer >= FLARE_ANIM_SPEED) {
            animTimer = 0;
            animFrame++;
            if (animFrame >= FLARE_FRAME_COUNT) {
                // afRoutine: advance to active phase
                phase = Phase.ACTIVE;
                collisionEnabled = true;
                animFrame = 0;
                renderedFrame = 2; // Active missile frame 0 (Ball1)
                return;
            }
        }
        renderedFrame = animFrame; // Flare frames 0-1
    }

    /**
     * Phase 3: Active flight. Moves with velocity, has collision (obColType=$87).
     * Deletes when below level bottom boundary + $E0.
     */
    private void updateActive() {
        // Apply velocity (SpeedToPos: 16.8 fixed-point)
        motionState.x = currentX;
        motionState.y = currentY;
        motionState.xVel = xVelocity;
        motionState.yVel = yVelocity;
        SubpixelMotion.moveSprite2(motionState);
        currentX = motionState.x;
        currentY = motionState.y;

        // Check if below level bottom boundary + $E0
        if (!isOnScreen(BOTTOM_MARGIN)) {
            setDestroyed(true);
            return;
        }

        // Active missile animation: alternate frames 2-3 at speed 1
        animTimer++;
        if (animTimer >= ACTIVE_ANIM_SPEED) {
            animTimer = 0;
            animFrame = (animFrame == 0) ? 1 : 0;
        }
        renderedFrame = 2 + animFrame; // Ball1 (frame 2) or Ball2 (frame 3)
    }

    @Override
    public int getCollisionFlags() {
        if (!collisionEnabled) {
            return 0; // No collision during flare
        }
        // HURT category ($80) + size index 7
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, 0x23, 0, 0, false, 0);
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
        return RenderPriority.clamp(3); // obPriority = 3
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.BUZZ_BOMBER_MISSILE);
        if (renderer == null) return;

        // Missile uses palette 1 (set in the sprite sheet); art faces left by default
        renderer.drawFrameIndex(renderedFrame, currentX, currentY, !facingLeft, false);
    }
}
