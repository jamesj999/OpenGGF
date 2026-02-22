package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.sonic2.objects.ObjectAnimationState;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.animation.SpriteAnimationEndAction;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Aquis (0x50) - seahorse badnik from Oil Ocean Zone.
 * Waits until on-screen, chases the player with acceleration, fires downward
 * projectiles, and escapes after 3 shooting cycles. Has a wing child object
 * that follows the body.
 * Based on disassembly Obj50 (lines 60044-60310).
 */
public class AquisBadnikInstance extends AbstractBadnikInstance {

    private enum State {
        WAIT_FOR_SCREEN,  // routine_secondary 0: idle until on-screen
        CHASE,            // routine_secondary 2: follow player with accel
        SHOOTING,         // routine_secondary 4: wait then fire projectile
        ESCAPE            // routine_secondary 6: fly away
    }

    private static final int COLLISION_SIZE_INDEX = 0x0A; // collision_flags $0A
    private static final int WIDTH_PIXELS = 0x10;
    private static final int CHASE_ACCEL = 0x10;           // +-0x10 per frame
    private static final int MAX_CHASE_SPEED = 0x100;      // cap speed 0x100
    private static final int CHASE_TIMER = 0x80;           // 128 frames
    private static final int SHOOT_DELAY = 0x20;           // 32 frames
    private static final int INITIAL_SHOTS = 3;
    private static final int BULLET_X_VEL = 0x300;
    private static final int BULLET_Y_VEL = 0x200;
    private static final int BULLET_X_OFFSET = 0x10;
    private static final int BULLET_Y_OFFSET = 0x0A;
    private static final int ESCAPE_X_VEL = -0x200;
    private static final int POST_SHOT_Y_VEL = -0x100;
    private static final int WING_X_OFFSET = 0x0A;
    private static final int WING_Y_OFFSET = -6;

    private static final SpriteAnimationSet ANIMATIONS = createAnimations();
    private static final SpriteAnimationSet WING_ANIMATIONS = createWingAnimations();

    private State state;
    private int timer;
    private int shotsRemaining;
    private boolean shootingFlag; // prevents double-fire per shooting phase
    private int xSubpixel;
    private int ySubpixel;
    private final ObjectAnimationState animationState;

    // Wing child state
    private final ObjectAnimationState wingAnimationState;
    private boolean wingDestroyed;

    public AquisBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "Aquis");
        this.state = State.WAIT_FOR_SCREEN;
        this.timer = 0;
        this.shotsRemaining = INITIAL_SHOTS;
        this.shootingFlag = false;
        this.xSubpixel = 0;
        this.ySubpixel = 0;
        this.facingLeft = (spawn.renderFlags() & 0x01) != 0;
        this.animationState = new ObjectAnimationState(ANIMATIONS, 0, 0);
        this.wingAnimationState = new ObjectAnimationState(WING_ANIMATIONS, 0, 1);
        this.wingDestroyed = false;
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case WAIT_FOR_SCREEN -> updateWaitForScreen();
            case CHASE -> updateChase(player);
            case SHOOTING -> updateShooting(player);
            case ESCAPE -> updateEscape();
        }

        // Update wing animation
        wingAnimationState.update();
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        animationState.update();
        animFrame = animationState.getMappingFrame();
    }

    private void updateWaitForScreen() {
        if (isOnScreen(32)) {
            state = State.CHASE;
            timer = CHASE_TIMER;
            animationState.setAnimId(1); // Flapping body animation
        }
    }

    private void updateChase(AbstractPlayableSprite player) {
        if (player != null && !player.isDebugMode()) {
            // Determine direction to player and accelerate
            if (player.getCentreX() < currentX) {
                xVelocity -= CHASE_ACCEL;
                facingLeft = true;
            } else {
                xVelocity += CHASE_ACCEL;
                facingLeft = false;
            }

            if (player.getCentreY() < currentY) {
                yVelocity -= CHASE_ACCEL;
            } else {
                yVelocity += CHASE_ACCEL;
            }
        }

        // Cap speed (Obj_CapSpeed equivalent)
        xVelocity = clampSpeed(xVelocity, MAX_CHASE_SPEED);
        yVelocity = clampSpeed(yVelocity, MAX_CHASE_SPEED);

        applyMovement();

        timer--;
        if (timer <= 0) {
            // Stop movement, transition to shooting
            xVelocity = 0;
            yVelocity = 0;
            state = State.SHOOTING;
            timer = SHOOT_DELAY;
            shootingFlag = false;
            animationState.setAnimId(0); // Static body
        }
    }

    private void updateShooting(AbstractPlayableSprite player) {
        // Fire projectile if player is below and we haven't fired yet this phase
        if (!shootingFlag && player != null && !player.isDebugMode()) {
            if (player.getCentreY() > currentY) {
                fireProjectile();
                shootingFlag = true;
            }
        }

        timer--;
        if (timer <= 0) {
            shotsRemaining--;
            if (shotsRemaining > 0) {
                // Return to chase
                state = State.CHASE;
                timer = CHASE_TIMER;
                yVelocity = POST_SHOT_Y_VEL; // Thrust upward after shot
                shootingFlag = false;
                animationState.setAnimId(1); // Flapping body
            } else {
                // All shots used, escape
                state = State.ESCAPE;
                xVelocity = ESCAPE_X_VEL;
                yVelocity = 0;
            }
        }
    }

    private void updateEscape() {
        applyMovement();

        if (!isOnScreen(64)) {
            destroyed = true;
            setDestroyed(true);
            wingDestroyed = true;
        }
    }

    private void fireProjectile() {
        ObjectManager objectManager = levelManager.getObjectManager();
        if (objectManager == null) {
            return;
        }

        int bulletX = facingLeft ? currentX - BULLET_X_OFFSET : currentX + BULLET_X_OFFSET;
        int bulletY = currentY + BULLET_Y_OFFSET;
        int bulletXVel = facingLeft ? -BULLET_X_VEL : BULLET_X_VEL;

        BadnikProjectileInstance bullet = new BadnikProjectileInstance(
                spawn,
                BadnikProjectileInstance.ProjectileType.AQUIS_BULLET,
                bulletX,
                bulletY,
                bulletXVel,
                BULLET_Y_VEL,
                false,      // No gravity
                !facingLeft);
        objectManager.addDynamicObject(bullet);
    }

    private void applyMovement() {
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos24 += xVelocity;
        yPos24 += yVelocity;
        currentX = xPos24 >> 8;
        currentY = yPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
        ySubpixel = yPos24 & 0xFF;
    }

    private static int clampSpeed(int velocity, int max) {
        if (velocity > max) return max;
        if (velocity < -max) return -max;
        return velocity;
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

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.AQUIS);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Draw main body (priority 4)
        renderer.drawFrameIndex(animFrame, currentX, currentY, !facingLeft, false);

        // Draw wing child (priority 3, slightly in front)
        if (!wingDestroyed) {
            int wingX = facingLeft ? currentX + WING_X_OFFSET : currentX - WING_X_OFFSET;
            int wingY = currentY + WING_Y_OFFSET;
            int wingFrame = wingAnimationState.getMappingFrame();
            renderer.drawFrameIndex(wingFrame, wingX, wingY, !facingLeft, false);
        }
    }

    @Override
    protected void destroyBadnik(AbstractPlayableSprite player) {
        wingDestroyed = true;
        super.destroyBadnik(player);
    }

    /**
     * Animation scripts from Ani_obj50 (disassembly).
     */
    private static SpriteAnimationSet createAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Normal body (static) - dc.b $E, 0, $FF
        set.addScript(0, new SpriteAnimationScript(
                0x0E,
                List.of(0),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 1: Body with flapping - dc.b 5, 3, 4, 3, 4, 3, 4, $FF
        set.addScript(1, new SpriteAnimationScript(
                5,
                List.of(3, 4, 3, 4, 3, 4),
                SpriteAnimationEndAction.LOOP,
                0));

        // Anim 2: Bullet spinning - dc.b 3, 5, 6, 7, 6, $FF
        set.addScript(2, new SpriteAnimationScript(
                3,
                List.of(5, 6, 7, 6),
                SpriteAnimationEndAction.LOOP,
                0));

        return set;
    }

    /**
     * Wing animation scripts.
     */
    private static SpriteAnimationSet createWingAnimations() {
        SpriteAnimationSet set = new SpriteAnimationSet();

        // Anim 0: Wing flapping - dc.b 3, 1, 2, $FF
        set.addScript(0, new SpriteAnimationScript(
                3,
                List.of(1, 2),
                SpriteAnimationEndAction.LOOP,
                0));

        return set;
    }
}
