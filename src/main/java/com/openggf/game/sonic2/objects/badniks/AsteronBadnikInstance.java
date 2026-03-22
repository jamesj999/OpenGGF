package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.AbstractBadnikInstance;

import com.openggf.debug.DebugRenderContext;
import com.openggf.game.sonic2.audio.Sonic2Sfx;
import com.openggf.game.PlayableEntity;
import com.openggf.level.objects.ExplosionObjectInstance;
import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Asteron (0xA4) - Exploding starfish badnik from Metropolis Zone.
 *
 * Behavior from s2.asm ObjA4:
 * - Routine 2: Idle. Waits for player within detection range (0xC0 horizontal, 0x80 vertical).
 * - Routine 4: Armed. Checks player direction and sets velocity toward player.
 *   Horizontal: if abs(dx) in [0x10..0x60), sets x_vel to +/-0x40.
 *   Vertical: if abs(dy) in [0x10..0x60), sets y_vel to +/-0x40.
 * - Routine 6: Moving + animating for 0x40 frames. Then explodes into 5 projectiles.
 *
 * SubObjData: collision_flags=$0B, priority=4, width_pixels=$10.
 */
public class AsteronBadnikInstance extends AbstractBadnikInstance {
    // From ObjA4_SubObjData: collision_flags = $0B
    private static final int COLLISION_SIZE_INDEX = 0x0B;

    // Movement velocity from word_38A1A: -$40 or +$40 (8.8 fixed point = 0.25 px/frame)
    private static final int MOVE_VELOCITY = 0x40;

    // Timer for routine 6 movement phase: objoff_2A = $40 (64 frames)
    private static final int MOVE_TIMER_INIT = 0x40;

    // Detection ranges from routine 2 (loc_389B6)
    // d2 + $60 compared to $C0 → player within -$60..$60 horizontally
    private static final int DETECT_X_OFFSET = 0x60;
    private static final int DETECT_X_RANGE = 0xC0;
    // d3 + $40 compared to $80 → player within -$40..$40 vertically
    private static final int DETECT_Y_OFFSET = 0x40;
    private static final int DETECT_Y_RANGE = 0x80;

    // Firing thresholds from routine 4 (loc_389DA)
    // abs(d2/d3) must be >= $10 and < $60 to fire in that axis
    private static final int FIRE_DISTANCE_MIN = 0x10;
    private static final int FIRE_DISTANCE_MAX = 0x60;

    // Projectile data from word_38A68 (5 projectiles, 6 bytes each)
    // Format: xOff, yOff, xVel(byte→<<8), yVel(byte→<<8), mappingFrame, renderFlags
    private static final int[][] PROJECTILE_DATA = {
            // xOff, yOff, xVel*256, yVel*256, frame, hFlip
            {0, -8, 0, -4 * 256, 2, 0},           // Up
            {8, -4, 3 * 256, -1 * 256, 3, 1},      // Up-right
            {8, 8, 3 * 256, 3 * 256, 4, 1},         // Down-right
            {-8, 8, -3 * 256, 3 * 256, 4, 0},       // Down-left
            {-8, -4, -3 * 256, -1 * 256, 3, 0},     // Up-left
    };

    private enum State {
        IDLE,       // Routine 2: waiting for player in range
        ARMED,      // Routine 4: checking direction to fire
        MOVING      // Routine 6: moving toward player, then exploding
    }

    private State state;
    private int moveTimer;
    private final SubpixelMotion.State motionState;

    public AsteronBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Asteron", Sonic2BadnikConfig.DESTRUCTION);
        this.state = State.IDLE;
        this.moveTimer = 0;
        this.motionState = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        switch (state) {
            case IDLE -> updateIdle(player);
            case ARMED -> updateArmed(player);
            case MOVING -> updateMoving();
        }
    }

    /**
     * Routine 2 (loc_389B6): Check if player is within detection range.
     * ROM: Obj_GetOrientationToPlayer gives d2=xDist, d3=yDist (signed, obj-player).
     * Checks: (d2 + $60) >= $C0 → outside horizontal range → stay idle.
     *         (d3 + $40) >= $80 → outside vertical range → stay idle.
     * Both must pass to advance to routine 4.
     */
    private void updateIdle(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        int dx = currentX - player.getCentreX();
        int dy = currentY - player.getCentreY();

        // Check horizontal range: dx + $60 must be < $C0 (unsigned comparison)
        int adjustedDx = dx + DETECT_X_OFFSET;
        if (adjustedDx < 0 || adjustedDx >= DETECT_X_RANGE) {
            return;
        }
        // Check vertical range: dy + $40 must be < $80 (unsigned comparison)
        int adjustedDy = dy + DETECT_Y_OFFSET;
        if (adjustedDy < 0 || adjustedDy >= DETECT_Y_RANGE) {
            return;
        }

        // Player in range, advance to armed state
        state = State.ARMED;
    }

    /**
     * Routine 4 (loc_389DA): Determine velocity toward player and start moving.
     * ROM: Obj_GetOrientationToPlayer gives d0=0/2 (player left/right of obj),
     *      d1=0/2 (player above/below), d2=xDist, d3=yDist.
     * For X axis: if abs(d2) in [$10..$60), set x_vel = word_38A1A[d0] = -$40 or +$40.
     * For Y axis: if abs(d3) in [$10..$60), set y_vel = word_38A1A[d1] = -$40 or +$40.
     * If either velocity was set, transition to routine 6 with timer $40.
     */
    private void updateArmed(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        int dx = currentX - player.getCentreX();
        int dy = currentY - player.getCentreY();
        boolean velocitySet = false;

        // Check X: abs(dx) in [FIRE_DISTANCE_MIN, FIRE_DISTANCE_MAX)
        int absDx = Math.abs(dx);
        if (absDx >= FIRE_DISTANCE_MIN && absDx < FIRE_DISTANCE_MAX) {
            // word_38A1A[d0]: move toward player on X axis
            xVelocity = (dx > 0) ? -MOVE_VELOCITY : MOVE_VELOCITY;
            velocitySet = true;
        }

        // Check Y: abs(dy) in [FIRE_DISTANCE_MIN, FIRE_DISTANCE_MAX)
        int absDy = Math.abs(dy);
        if (absDy >= FIRE_DISTANCE_MIN && absDy < FIRE_DISTANCE_MAX) {
            // word_38A1A[d1]: move toward player on Y axis
            yVelocity = (dy > 0) ? -MOVE_VELOCITY : MOVE_VELOCITY;
            velocitySet = true;
        }

        if (velocitySet) {
            state = State.MOVING;
            moveTimer = MOVE_TIMER_INIT;
        }
    }

    /**
     * Routine 6 (loc_38A2C): Move with set velocity, animate, then explode.
     * ROM: Decrements objoff_2A each frame. While > 0: ObjectMove + AnimateSprite.
     * When timer reaches 0: convert to explosion + spawn 5 projectiles.
     */
    private void updateMoving() {
        moveTimer--;
        if (moveTimer < 0) {
            explode();
            return;
        }

        // ObjectMove: position += velocity (8.8 fixed point)
        motionState.x = currentX;
        motionState.y = currentY;
        motionState.xVel = xVelocity;
        motionState.yVel = yVelocity;
        SubpixelMotion.moveSprite2(motionState);
        currentX = motionState.x;
        currentY = motionState.y;
    }

    /**
     * ROM: When timer expires, convert to explosion and spawn 5 projectiles.
     * loc_38A44: Sets id to ObjID_Explosion, then calls loc_38A58 which uses
     * Obj_CreateProjectiles with the 5-entry projectile table.
     */
    private void explode() {
        // Destroy self (the Asteron becomes an explosion)
        setDestroyed(true);
        setDestroyed(true);

        var objectManager = services().objectManager();
        if (objectManager != null) {
            objectManager.removeFromActiveSpawns(spawn);
        }

        // Spawn explosion at current position
        var explosion = new ExplosionObjectInstance(
                0x27, currentX, currentY, services().renderManager());
        objectManager.addDynamicObject(explosion);

        // Play explosion SFX
        services().playSfx(
                Sonic2Sfx.EXPLOSION.id);

        // Spawn 5 projectiles (from word_38A68 / Obj_CreateProjectiles)
        for (int[] data : PROJECTILE_DATA) {
            int projX = currentX + data[0];
            int projY = currentY + data[1];
            int projXVel = data[2];
            int projYVel = data[3];
            int mappingFrame = data[4];
            boolean hFlip = data[5] != 0;

            BadnikProjectileInstance projectile = new BadnikProjectileInstance(
                    spawn,
                    BadnikProjectileInstance.ProjectileType.ASTERON_SPIKE,
                    projX, projY,
                    projXVel, projYVel,
                    false,  // No gravity - uses ObjectMove (straight line)
                    hFlip,
                    0,      // No initial delay
                    mappingFrame);

            objectManager.addDynamicObject(projectile);
        }
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        if (state == State.MOVING) {
            // Animation from Ani_objA4: dc.b 1, 0, 1, $FF
            // Alternates frames 0 and 1 with delay of 1 (every 2 frames)
            animFrame = ((frameCounter >> 1) & 1);
        } else {
            animFrame = 0;
        }
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public int getPriorityBucket() {
        // From ObjA4_SubObjData: priority = 4
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.ASTERON);
        if (renderer == null) return;

        // Asteron has no directional flipping - it's a symmetric starfish
        renderer.drawFrameIndex(animFrame, currentX, currentY, false, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        super.appendDebugRenderCommands(ctx);
        String stateLabel = "State: " + state.name();
        if (state == State.MOVING) {
            stateLabel += " t=" + moveTimer;
        }
        ctx.drawWorldLabel(currentX, currentY, -12, stateLabel, DebugColor.ORANGE);
    }
}
