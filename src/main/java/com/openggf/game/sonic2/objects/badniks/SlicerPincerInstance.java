package com.openggf.game.sonic2.objects.badniks;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseProvider;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Slicer's thrown pincer projectile (ObjA2, s2.asm:75428).
 *
 * Two-phase behavior:
 *   Phase 1 (Homing): Accelerates toward player for HOMING_TIMER frames,
 *     capped at MAX_SPEED. Uses ObjectMove (no gravity).
 *   Phase 2 (Falling): ObjectMoveAndFall (gravity $38) for FALL_TIMER frames,
 *     then deletes.
 *
 * Collision: $9A = 0x80 (HURT category) | 0x1A (size index).
 * Animation: Ani_objA2 = {3, 5, 6, 7, 8, $FF} → frames 5-8 at speed 4.
 */
public class SlicerPincerInstance extends AbstractObjectInstance
        implements TouchResponseProvider {

    // From ObjA2_SubObjData: collision_flags = $9A
    private static final int COLLISION_SIZE_INDEX = 0x1A;

    // From ObjA2_acceleration: dc.w -$10, $10
    private static final int ACCELERATION = 0x10;

    // From disassembly: move.w #$200,d0 / move.w d0,d1 / bra.w Obj_CapSpeed
    private static final int MAX_SPEED = 0x200;

    // From disassembly: move.w #$60,objoff_2A(a0) (fall timer)
    private static final int FALL_TIMER = 0x60;

    // From ObjectMoveAndFall: addi.w #$38,y_vel(a0)
    private static final int GRAVITY = 0x38;

    // Animation: Ani_objA2 = {3, 5, 6, 7, 8, $FF} → speed 3+1=4, frames 5,6,7,8
    private static final int ANIM_SPEED = 4;
    private static final int[] ANIM_FRAMES = { 5, 6, 7, 8 };

    private enum Phase {
        HOMING,  // Routine 2: accelerate toward player
        FALLING  // Routine 4: fall with gravity until timer expires
    }

    private Phase phase;
    private int currentX;
    private int currentY;
    private int xVelocity; // 8.8 fixed point
    private int yVelocity; // 8.8 fixed point
    private int xSubpixel;
    private int ySubpixel;
    private int timer;
    private boolean hFlip;
    private int animIndex;
    private int animTimer;
    private final SlicerBadnikInstance parent; // ROM: objoff_2C - parent reference for alive check

    /**
     * @param spawn       Parent spawn data
     * @param parent      Parent Slicer instance (for alive check)
     * @param x           Starting X position
     * @param y           Starting Y position
     * @param xVel        Initial X velocity (signed, in subpixels)
     * @param hFlip       Horizontal flip for rendering
     * @param homingTimer Frames for homing phase ($78 from disassembly)
     */
    public SlicerPincerInstance(ObjectSpawn spawn, SlicerBadnikInstance parent,
                                int x, int y, int xVel,
                                boolean hFlip, int homingTimer) {
        super(spawn, "SlicerPincer");
        this.parent = parent;
        this.currentX = x;
        this.currentY = y;
        this.xVelocity = xVel;
        this.yVelocity = 0;
        this.hFlip = hFlip;
        this.timer = homingTimer;
        this.phase = Phase.HOMING;
        this.animIndex = 0;
        this.animTimer = ANIM_SPEED;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        switch (phase) {
            case HOMING -> updateHoming(player);
            case FALLING -> updateFalling();
        }

        // Animate: cycle through frames 5,6,7,8
        animTimer--;
        if (animTimer <= 0) {
            animTimer = ANIM_SPEED;
            animIndex = (animIndex + 1) % ANIM_FRAMES.length;
        }
    }

    /**
     * Routine 2 (ObjA2_Main): Homing phase.
     * Accelerates toward player, capped at MAX_SPEED.
     * When timer expires, transitions to falling phase.
     */
    private void updateHoming(AbstractPlayableSprite player) {
        // ROM: _btst #render_flags.on_screen / _beq.w JmpTo65_DeleteObject
        if (!isOnScreenX(32)) {
            setDestroyed(true);
            return;
        }

        timer--;
        if (timer < 0 || (parent != null && parent.isDestroyed())) {
            // ROM: subq.w #1,objoff_2A(a0) / bmi.s loc_3851A
            // ROM: cmpi.b #ObjID_Slicer,id(a1) / bne.s loc_3851A
            startFalling();
            return;
        }

        if (player != null) {
            int dx = currentX - player.getCentreX();
            int dy = currentY - player.getCentreY();

            // Obj_GetOrientationToPlayer: d0=0 if dx>=0 (player LEFT), d0=2 if dx<0 (player RIGHT)
            // ObjA2_acceleration: dc.w -$10, $10 → accelerate TOWARD player
            int xAccel = dx >= 0 ? -ACCELERATION : ACCELERATION;
            int yAccel = dy >= 0 ? -ACCELERATION : ACCELERATION;

            xVelocity += xAccel;
            yVelocity += yAccel;

            // Cap speed (Obj_CapSpeed with d0=$200, d1=$200)
            capSpeed();
        }

        // ObjectMove
        objectMove();
    }

    /**
     * Routine 4 (ObjA2_Main2): Falling phase.
     * ObjectMoveAndFall until timer expires, then delete.
     */
    private void updateFalling() {
        timer--;
        if (timer < 0) {
            setDestroyed(true);
            return;
        }

        // ObjectMoveAndFall: apply gravity then move
        yVelocity += GRAVITY;
        objectMove();
    }

    private void startFalling() {
        phase = Phase.FALLING;
        timer = FALL_TIMER;
    }

    /**
     * ObjectMove: apply velocity to position using 16.8 fixed-point
     * (matches s2.asm:29990 ObjectMove).
     */
    private void objectMove() {
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        xPos24 += xVelocity;
        yPos24 += yVelocity;
        currentX = xPos24 >> 8;
        currentY = yPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;
        ySubpixel = yPos24 & 0xFF;
    }

    /**
     * Obj_CapSpeed: clamp x_vel and y_vel to [-MAX_SPEED, MAX_SPEED].
     */
    private void capSpeed() {
        if (xVelocity > MAX_SPEED) xVelocity = MAX_SPEED;
        if (xVelocity < -MAX_SPEED) xVelocity = -MAX_SPEED;
        if (yVelocity > MAX_SPEED) yVelocity = MAX_SPEED;
        if (yVelocity < -MAX_SPEED) yVelocity = -MAX_SPEED;
    }

    @Override
    public int getCollisionFlags() {
        // HURT category (0x80) + size index
        return 0x80 | (COLLISION_SIZE_INDEX & 0x3F);
    }

    @Override
    public int getCollisionProperty() {
        return 0;
    }

    @Override
    public ObjectSpawn getSpawn() {
        return new ObjectSpawn(currentX, currentY, spawn.objectId(),
                spawn.subtype(), spawn.renderFlags(), spawn.respawnTracked(), spawn.rawYWord());
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
        // From ObjA2_SubObjData: priority = 4
        return RenderPriority.clamp(4);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) return;

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.SLICER);
        if (renderer == null || !renderer.isReady()) return;

        int frame = ANIM_FRAMES[animIndex];
        renderer.drawFrameIndex(frame, currentX, currentY, hFlip, false);
    }
}
