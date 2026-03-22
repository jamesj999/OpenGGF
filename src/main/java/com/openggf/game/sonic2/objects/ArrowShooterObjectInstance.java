package com.openggf.game.sonic2.objects;

import com.openggf.game.GameServices;
import com.openggf.game.PlayableEntity;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.debug.DebugRenderContext;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Object 22 - Arrow Shooter from Aquatic Ruin Zone.
 * <p>
 * A stationary hazard that detects the player horizontally and fires arrow projectiles.
 * <p>
 * Behavior from disassembly (s2.asm lines 51010-51147):
 * <ul>
 *   <li>Detects player within 64 ($40) pixels horizontally</li>
 *   <li>When detected, plays "detecting" animation (frames 1-2)</li>
 *   <li>When player moves away, plays "Pre-Arrow Firing" sound (0xDB) and fires arrow</li>
 *   <li>Arrow travels at 4 pixels/frame ($400 fixed-point)</li>
 * </ul>
 * <p>
 * Animation scripts (Ani_obj22):
 * <ul>
 *   <li>Anim 0 (Idle): delay $1F, frame 1, loop</li>
 *   <li>Anim 1 (Detecting): delay $03, frames 1-2, loop</li>
 *   <li>Anim 2 (Firing): delay $07, frames 3,4,$FC,4,3,1,$FD,0 (fires arrow on $FD)</li>
 * </ul>
 */
public class ArrowShooterObjectInstance extends AbstractObjectInstance {
    private static final Logger LOGGER = Logger.getLogger(ArrowShooterObjectInstance.class.getName());

    private static final int DETECTION_DISTANCE = 0x40; // 64 pixels
    private static final int PRIORITY = 3;

    // Animation IDs
    private static final int ANIM_IDLE = 0;
    private static final int ANIM_DETECTING = 1;
    private static final int ANIM_FIRING = 2;

    // Animation delays (from Ani_obj22)
    private static final int DELAY_IDLE = 0x1F;
    private static final int DELAY_DETECTING = 0x03;
    private static final int DELAY_FIRING = 0x07;

    // Firing animation sequence: frames 3,4, then reset, then 4,3,1, then callback
    private static final int[] FIRING_SEQUENCE = {3, 4, 4, 3, 1};
    private static final int FIRING_CALLBACK_INDEX = 5; // After frame index 4 (value 1), trigger callback

    private int currentX;
    private int currentY;
    private int currentAnim;
    private int animFrame;
    private int animTimer;
    private int firingIndex;
    private boolean arrowFired;
    private boolean hFlip;

    public ArrowShooterObjectInstance(ObjectSpawn spawn, String name) {
        super(spawn, name);
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        this.currentAnim = ANIM_IDLE;
        this.animFrame = 1; // Start at mapping frame 1 (shooter idle)
        this.animTimer = DELAY_IDLE;
        this.firingIndex = 0;
        this.arrowFired = false;
        this.hFlip = (spawn.renderFlags() & 0x01) != 0;
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (currentAnim != ANIM_FIRING) {
            updateDetection(player);
        }
        updateAnimation();
    }

    private void updateDetection(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }

        // Check if player is within detection distance
        // Use getCentreX to match ROM x_pos (center coordinate)
        int dx = currentX - player.getCentreX();
        if (dx < 0) {
            dx = -dx;
        }

        boolean playerDetected = dx < DETECTION_DISTANCE;

        if (playerDetected) {
            // Player within range - switch to detecting animation
            if (currentAnim != ANIM_DETECTING) {
                currentAnim = ANIM_DETECTING;
                animTimer = DELAY_DETECTING;
            }
        } else {
            // Player out of range
            if (currentAnim == ANIM_DETECTING) {
                // Was detecting, now fire arrow
                currentAnim = ANIM_FIRING;
                animTimer = DELAY_FIRING;
                firingIndex = 0;
                arrowFired = false;
            } else if (currentAnim == ANIM_IDLE) {
                // Stay idle
            }
        }
    }

    private void updateAnimation() {
        animTimer--;
        if (animTimer < 0) {
            switch (currentAnim) {
                case ANIM_IDLE:
                    animFrame = 1;
                    animTimer = DELAY_IDLE;
                    break;

                case ANIM_DETECTING:
                    // Toggle between frames 1 and 2
                    animFrame = (animFrame == 1) ? 2 : 1;
                    animTimer = DELAY_DETECTING;
                    break;

                case ANIM_FIRING:
                    if (firingIndex < FIRING_SEQUENCE.length) {
                        animFrame = FIRING_SEQUENCE[firingIndex];
                        firingIndex++;
                        animTimer = DELAY_FIRING;

                        // Fire arrow after showing frame 1 at the end
                        if (firingIndex == FIRING_CALLBACK_INDEX && !arrowFired) {
                            fireArrow();
                            arrowFired = true;
                        }
                    } else {
                        // Firing animation complete, return to idle
                        currentAnim = ANIM_IDLE;
                        animFrame = 1;
                        animTimer = DELAY_IDLE;
                    }
                    break;
            }
        }
    }

    private void fireArrow() {
        // Play pre-arrow sound
        services().playSfx(Sonic2Sfx.PRE_ARROW_FIRING.id);

        // Spawn arrow projectile
        if (GameServices.level() == null) {
            return;
        }
        ObjectManager objectManager = services().objectManager();
        if (objectManager == null) {
            return;
        }

        // Create arrow at shooter's position
        ArrowProjectileInstance arrow = new ArrowProjectileInstance(
                spawn, currentX, currentY, hFlip);
        objectManager.addDynamicObject(arrow);
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
        return RenderPriority.clamp(PRIORITY);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.ARROW_SHOOTER);
        if (renderer == null) return;

        // Clamp frame to valid range (0-4)
        int frame = Math.max(0, Math.min(animFrame, 4));
        renderer.drawFrameIndex(frame, currentX, currentY, hFlip, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        int halfWidth = 0x10;
        int halfHeight = 0x10;
        int left = currentX - halfWidth;
        int right = currentX + halfWidth;
        int top = currentY - halfHeight;
        int bottom = currentY + halfHeight;

        ctx.drawLine(left, top, right, top, 0.4f, 0.6f, 0.2f);
        ctx.drawLine(right, top, right, bottom, 0.4f, 0.6f, 0.2f);
        ctx.drawLine(right, bottom, left, bottom, 0.4f, 0.6f, 0.2f);
        ctx.drawLine(left, bottom, left, top, 0.4f, 0.6f, 0.2f);
    }

}
