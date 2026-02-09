package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic2.objects.ObjectAnimationState;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationEndAction;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationScript;
import uk.co.jamesj999.sonic.sprites.animation.SpriteAnimationSet;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Octus (0x4A) - octopus badnik from Oil Ocean Zone.
 * Waits submerged at ground level, rises when the player approaches within
 * 128 pixels, fires a horizontal bullet at its peak altitude, hovers briefly,
 * then descends back to its starting position.
 * Based on disassembly Obj4A (lines 59860-60026).
 */
public class OctusBadnikInstance extends AbstractBadnikInstance {

    private enum State {
        WAIT_FOR_PLAYER,    // routine_secondary 0: check player distance
        DELAY_BEFORE_RISE,  // routine_secondary 2: countdown 0x20 frames
        MOVING_UP,          // routine_secondary 4: rise with decel
        HOVERING,           // routine_secondary 6: hover 60 frames, bullet fired
        MOVING_DOWN         // routine_secondary 8: descend back to start
    }

    private static final int COLLISION_SIZE_INDEX = 0x0C; // From disassembly collision_flags $0C
    private static final int DETECT_RANGE = 0x80; // 128 pixels
    private static final int RISE_DELAY = 0x20; // 32 frames
    private static final int INITIAL_Y_VEL = -0x200; // Rise speed
    private static final int Y_ACCEL = 0x10; // Deceleration/acceleration per frame
    private static final int HOVER_DURATION = 60; // 60 frames hovering
    private static final int BULLET_X_VEL = 0x200; // Bullet speed
    private static final int BULLET_DELAY = 0x0F; // 15 frames stationary before moving

    private static final SpriteAnimationSet ANIMATIONS = createAnimations();

    private final int startY;
    private final boolean xFlip;
    private State state;
    private int timer;
    private int ySubpixel;
    private boolean bulletFired;
    private final ObjectAnimationState animationState;

    public OctusBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "Octus");
        this.startY = spawn.y();
        this.xFlip = (spawn.renderFlags() & 0x01) != 0;
        // Octus faces left by default; x_flip in spawn means face right
        this.facingLeft = !xFlip;
        this.state = State.WAIT_FOR_PLAYER;
        this.timer = 0;
        this.ySubpixel = 0;
        this.bulletFired = false;
        this.animationState = new ObjectAnimationState(ANIMATIONS, 0, 1);
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case WAIT_FOR_PLAYER -> updateWaitForPlayer(player);
            case DELAY_BEFORE_RISE -> updateDelayBeforeRise();
            case MOVING_UP -> updateMovingUp();
            case HOVERING -> updateHovering();
            case MOVING_DOWN -> updateMovingDown();
        }
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        animationState.update();
        animFrame = animationState.getMappingFrame();
    }

    private void updateWaitForPlayer(AbstractPlayableSprite player) {
        if (player == null || player.isDebugMode()) {
            return;
        }
        int dx = player.getCentreX() - currentX;
        if (Math.abs(dx) < DETECT_RANGE) {
            // Determine facing based on player position
            facingLeft = isPlayerLeft(player);
            state = State.DELAY_BEFORE_RISE;
            timer = RISE_DELAY;
            animationState.setAnimId(3); // Pre-rise (antenna visible)
        }
    }

    private void updateDelayBeforeRise() {
        timer--;
        if (timer <= 0) {
            state = State.MOVING_UP;
            yVelocity = INITIAL_Y_VEL;
            animationState.setAnimId(4); // Rising animation
        }
    }

    private void updateMovingUp() {
        // Decelerate: y_vel starts at -0x200, add +0x10 per frame
        yVelocity += Y_ACCEL;
        applyYMovement();

        if (yVelocity >= 0) {
            // Reached peak - fire bullet and start hovering
            yVelocity = 0;
            state = State.HOVERING;
            timer = HOVER_DURATION;
            fireBullet();
        }
    }

    private void updateHovering() {
        timer--;
        if (timer <= 0) {
            state = State.MOVING_DOWN;
            yVelocity = 0;
        }
    }

    private void updateMovingDown() {
        // Accelerate downward: +0x10 per frame
        yVelocity += Y_ACCEL;
        applyYMovement();

        if (currentY >= startY) {
            // Returned to start position - reset
            currentY = startY;
            yVelocity = 0;
            ySubpixel = 0;
            state = State.WAIT_FOR_PLAYER;
            bulletFired = false;
            animationState.setAnimId(0); // Back to idle
            animFrame = 1; // mapping_frame = 1
        }
    }

    private void applyYMovement() {
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        yPos24 += yVelocity;
        currentY = yPos24 >> 8;
        ySubpixel = yPos24 & 0xFF;
    }

    private void fireBullet() {
        if (bulletFired) {
            return;
        }
        bulletFired = true;

        ObjectManager objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }

        // Bullet fires in the direction the octus is facing
        int bulletXVel = facingLeft ? -BULLET_X_VEL : BULLET_X_VEL;
        boolean bulletHFlip = !facingLeft;

        BadnikProjectileInstance bullet = new BadnikProjectileInstance(
                spawn,
                BadnikProjectileInstance.ProjectileType.OCTUS_BULLET,
                currentX,
                currentY,
                bulletXVel,
                0,          // No vertical velocity
                false,      // No gravity
                bulletHFlip,
                BULLET_DELAY);
        objectManager.addDynamicObject(bullet);
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
        if (destroyed) {
            return;
        }

        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.OCTUS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Art faces left by default. When xFlip is set in spawn, face right.
        // facingLeft=true means default orientation (no flip needed).
        renderer.drawFrameIndex(animFrame, currentX, currentY, !facingLeft, false);
    }

    /**
     * Animation scripts from Ani_obj4A (disassembly lines 60030-60045).
     */
    private static SpriteAnimationSet createAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Idle/submerged - dc.b $F, 1, 0, $FF
        set.addScript(0, new SpriteAnimationScript(
                0x0F,
                List.of(1, 0),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 1: Alert (unused in final, beta leftover) - dc.b 3, 1, 2, 3, $FF
        set.addScript(1, new SpriteAnimationScript(
                3,
                List.of(1, 2, 3),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 2: Bullet projectile - dc.b 2, 5, 6, $FF
        set.addScript(2, new SpriteAnimationScript(
                2,
                List.of(5, 6),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 3: Pre-rise (antenna visible) - dc.b $F, 4, $FF
        set.addScript(3, new SpriteAnimationScript(
                0x0F,
                List.of(4),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 4: Rising - dc.b 7, 0, 1, $FE, 1
        set.addScript(4, new SpriteAnimationScript(
                7,
                List.of(0, 1),
                SpriteAnimationEndAction.LOOP_BACK,
                1));

        return set;
    }
}
