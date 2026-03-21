package com.openggf.game.sonic1.objects.badniks;

import com.openggf.debug.DebugRenderContext;
import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseListener;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import com.openggf.debug.DebugColor;
import java.util.List;

/**
 * Yadrin (0x50) - Spiky hedgehog Badnik from Spring Yard Zone.
 * Walks along terrain, pauses for 1 second, toggles direction, and resumes.
 * Uses wall detection to reverse when hitting a wall.
 * <p>
 * Collision type $CC in ROM. In S1, the $C0 upper bits route to React_Special
 * which implements a spiky-top check: if Sonic hits the spikes from above he
 * gets hurt even while rolling, otherwise standard React_Enemy applies.
 * <p>
 * Based on docs/s1disasm/_incObj/50 Yadrin.asm.
 * <p>
 * Routine index:
 * <ul>
 *   <li>0 (Yad_Main): Initialization - ObjectFall + ObjFloorDist until floor found</li>
 *   <li>2 (Yad_Action): Main behavior with secondary routines:
 *     <ul>
 *       <li>ob2ndRout=0 (Yad_Move): Pause timer, then start walking</li>
 *       <li>ob2ndRout=2 (Yad_FixToFloor): Walk with terrain following + wall checks</li>
 *     </ul>
 *   </li>
 * </ul>
 * <p>
 * Animation scripts (Ani_Yad):
 * <ul>
 *   <li>Anim 0 (stand): dc.b 7, 0, afEnd - frame 0 at speed 7</li>
 *   <li>Anim 1 (walk): dc.b 7, 0, 3, 1, 4, 0, 3, 2, 5, afEnd - 8 frames cycling</li>
 * </ul>
 */
public class Sonic1YadrinBadnikInstance extends AbstractBadnikInstance implements TouchResponseListener {

    // From disassembly: obColType = $CC
    // Upper 2 bits ($C0) = collision category, lower 6 bits ($0C) = size index
    private static final int COLLISION_SIZE_INDEX = 0x0C;

    /** Vertical overlap threshold for spiky-top hurt (s1disasm: cmpi.w #8,d5) */
    private static final int SPIKY_TOP_THRESHOLD = 8;

    // From disassembly: obHeight = $11, obWidth = 8
    private static final int Y_RADIUS = 0x11;

    // From disassembly: obActWid = $14 (active/display width, also wall sensor offset)
    private static final int ACTIVE_WIDTH = 0x14;

    // Walking velocity: move.w #-$100,obVelX(a0)
    private static final int WALK_VELOCITY = 0x100;

    // Pause duration: move.w #59,yad_timedelay(a0)
    private static final int PAUSE_DURATION = 59;

    // Floor detection thresholds from Yad_FixToFloor:
    // cmpi.w #-8,d1 / blt.s Yad_Pause / cmpi.w #$C,d1 / bge.s Yad_Pause
    private static final int FLOOR_MIN_DIST = -8;
    private static final int FLOOR_MAX_DIST = 0x0C;

    // ObjectFall gravity: addi.w #$38,obVelY(a0)
    private static final int GRAVITY = 0x38;

    // Wall check frequency: andi.w #3,d0 - check walls every 4th frame
    private static final int WALL_CHECK_MASK = 3;

    // Secondary routine states
    private static final int STATE_MOVE = 0;       // ob2ndRout=0: paused/waiting
    private static final int STATE_FIX_TO_FLOOR = 1; // ob2ndRout=2: walking

    // Walk animation frame sequence: dc.b 7, 0, 3, 1, 4, 0, 3, 2, 5, afEnd
    private static final int[] WALK_ANIM_FRAMES = {0, 3, 1, 4, 0, 3, 2, 5};
    private static final int ANIM_SPEED = 7; // Duration = speed + 1 = 8 ticks per frame

    private int secondaryState;
    private int pauseTimer;        // yad_timedelay (objoff_30)
    private int xSubpixel;         // Fractional X position for SpeedToPos
    private int ySubpixel;         // Fractional Y position for ObjectFall
    private int fallVelocity;      // obVelY during initialization
    private boolean initialized;
    private int walkAnimIndex;     // Current index into WALK_ANIM_FRAMES

    public Sonic1YadrinBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Yadrin");
        this.currentX = spawn.x();
        this.currentY = spawn.y();
        // S1: obStatus bit 0 set = facing right (xFlip)
        boolean xFlip = (spawn.renderFlags() & 0x01) != 0;
        this.facingLeft = !xFlip;
        this.secondaryState = STATE_MOVE;
        this.pauseTimer = 0;
        this.xSubpixel = 0;
        this.ySubpixel = 0;
        this.fallVelocity = 0;
        this.initialized = false;
        this.walkAnimIndex = 0;
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        if (!initialized) {
            initialize();
            return;
        }

        switch (secondaryState) {
            case STATE_MOVE -> updateMove();
            case STATE_FIX_TO_FLOOR -> updateFixToFloor(frameCounter);
        }
    }

    /**
     * Routine 0: Yad_Main - ObjectFall + ObjFloorDist until floor is found.
     * Falls under gravity until feet intersect terrain (distance < 0).
     * ROM: bsr.w ObjectFall / bsr.w ObjFloorDist / tst.w d1 / bpl.s locret_F89E
     */
    private void initialize() {
        // ObjectFall: apply velocity to position, then add gravity
        int yPos24 = (currentY << 8) | (ySubpixel & 0xFF);
        yPos24 += fallVelocity;
        currentY = yPos24 >> 8;
        ySubpixel = yPos24 & 0xFF;
        fallVelocity += GRAVITY;

        // ObjFloorDist: check floor from feet
        TerrainCheckResult floorResult = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);

        // ROM: tst.w d1 / bpl.s locret_F89E
        if (floorResult.foundSurface() && floorResult.distance() < 0) {
            currentY += floorResult.distance();  // add.w d1,obY(a0)
            fallVelocity = 0;                     // move.w #0,obVelY(a0)
            initialized = true;                   // addq.b #2,obRoutine(a0)
            // bchg #0,obStatus(a0) - toggle facing direction on init
            facingLeft = !facingLeft;
        }
    }

    /**
     * ob2ndRout=0 (Yad_Move): Decrement pause timer. When expired, start walking.
     * ROM: subq.w #1,yad_timedelay(a0) / bpl.s locret_F8E2
     * Then sets velocity, toggles direction, advances to Yad_FixToFloor.
     */
    private void updateMove() {
        pauseTimer--;
        if (pauseTimer >= 0) {
            return; // bpl.s locret_F8E2
        }

        // Timer expired: start walking
        // addq.b #2,ob2ndRout(a0)
        secondaryState = STATE_FIX_TO_FLOOR;

        // move.w #-$100,obVelX(a0) - default: move left
        xVelocity = -WALK_VELOCITY;

        // move.b #1,obAnim(a0)
        animFrame = 1;
        walkAnimIndex = 0;
        animTimer = 0;

        // bchg #0,obStatus(a0) / bne.s locret_F8E2 / neg.w obVelX(a0)
        // bchg tests OLD bit, toggles it. bne branches if OLD bit was SET (now clear).
        // OLD bit SET -> now CLEAR -> bne taken -> keep negative velocity (walk left)
        // OLD bit CLEAR -> now SET -> bne not taken -> negate velocity (walk right)
        facingLeft = !facingLeft;
        if (!facingLeft) {
            xVelocity = -xVelocity; // neg.w obVelX(a0)
        }
    }

    /**
     * ob2ndRout=2 (Yad_FixToFloor): Walk with terrain following and wall checks.
     * ROM: bsr.w SpeedToPos / bsr.w ObjFloorDist / cmpi.w range checks
     * Also calls Yad_ChkWall which uses (v_framecount + d7) & 3 to throttle checks.
     */
    private void updateFixToFloor(int frameCounter) {
        // SpeedToPos: apply X velocity with subpixel precision
        int xPos24 = (currentX << 8) | (xSubpixel & 0xFF);
        xPos24 += xVelocity;
        currentX = xPos24 >> 8;
        xSubpixel = xPos24 & 0xFF;

        // ObjFloorDist
        TerrainCheckResult floorResult = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);
        int floorDist = floorResult.foundSurface() ? floorResult.distance() : 100;

        // cmpi.w #-8,d1 / blt.s Yad_Pause / cmpi.w #$C,d1 / bge.s Yad_Pause
        if (floorDist < FLOOR_MIN_DIST || floorDist >= FLOOR_MAX_DIST) {
            returnToPause();
            return;
        }

        // add.w d1,obY(a0) - snap to floor
        currentY += floorDist;

        // Yad_ChkWall: check walls every 4th frame
        // ROM: move.w (v_framecount).w,d0 / add.w d7,d0 / andi.w #3,d0 / bne.s .skip
        // d7 is 0 for Yadrin (no per-object offset in this object code)
        if ((frameCounter & WALL_CHECK_MASK) == 0) {
            if (checkWall()) {
                returnToPause();
            }
        }
    }

    /**
     * Yad_ChkWall: Check for wall collision in the direction of travel.
     * ROM: Uses obActWid as sensor distance, ObjHitWallRight or ObjHitWallLeft.
     * Returns true if a wall was hit (d1 < 0 for right, d1 < 0 for left after not.w d3).
     */
    private boolean checkWall() {
        if (xVelocity > 0) {
            // Moving right: check right wall
            // ROM: moveq #0,d3 / move.b obActWid(a0),d3 / bsr.w ObjHitWallRight
            TerrainCheckResult result = ObjectTerrainUtils.checkRightWallDist(
                    currentX + ACTIVE_WIDTH, currentY);
            // tst.w d1 / bpl.s .nowall - wall found if distance < 0
            return result.foundSurface() && result.distance() < 0;
        } else if (xVelocity < 0) {
            // Moving left: check left wall
            // ROM: not.w d3 / bsr.w ObjHitWallLeft
            TerrainCheckResult result = ObjectTerrainUtils.checkLeftWallDist(
                    currentX - ACTIVE_WIDTH, currentY);
            // tst.w d1 / bmi.s .hitwall - wall found if distance < 0
            return result.foundSurface() && result.distance() < 0;
        }
        return false;
    }

    /**
     * Yad_Pause: Return to pause state.
     * ROM: subq.b #2,ob2ndRout(a0) / move.w #59,yad_timedelay(a0) /
     *      move.w #0,obVelX(a0) / move.b #0,obAnim(a0)
     */
    private void returnToPause() {
        secondaryState = STATE_MOVE;
        pauseTimer = PAUSE_DURATION;
        xVelocity = 0;
        animFrame = 0;
        animTimer = 0;
        walkAnimIndex = 0;
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        if (secondaryState == STATE_FIX_TO_FLOOR) {
            // Walk animation: dc.b 7, 0, 3, 1, 4, 0, 3, 2, 5, afEnd
            // Speed 7 = 8 ticks per frame
            animTimer++;
            if (animTimer > ANIM_SPEED) {
                animTimer = 0;
                walkAnimIndex++;
                if (walkAnimIndex >= WALK_ANIM_FRAMES.length) {
                    walkAnimIndex = 0;
                }
            }
        }
    }

    /**
     * Returns the mapping frame index for the current animation state.
     * Stand animation (anim 0): frame 0 at speed 7.
     * Walk animation (anim 1): frames {0, 3, 1, 4, 0, 3, 2, 5} at speed 7.
     */
    private int getMappingFrame() {
        if (secondaryState == STATE_FIX_TO_FLOOR) {
            return WALK_ANIM_FRAMES[walkAnimIndex];
        }
        // Stand: frame 0
        return 0;
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public int getCollisionFlags() {
        // obColType = $CC: SPECIAL category ($40) + size index $0C.
        // React_Special decides between spiky-top hurt and normal enemy defeat.
        return 0x40 | (getCollisionSizeIndex() & 0x3F);
    }

    @Override
    public void onTouchResponse(AbstractPlayableSprite player, TouchResponseResult result, int frameCounter) {
        // React_Special Yadrin check (s1disasm: sub ReactToItem.asm:393-420):
        // Compute vertical overlap between player center and Yadrin center.
        // If overlap < 8 pixels, Sonic is on the spiky top -> hurt.
        // Otherwise, standard enemy destruction.
        int playerCentreY = player.getCentreY();
        int yadrinCentreY = this.currentY;
        int verticalOverlap = yadrinCentreY - playerCentreY;

        if (verticalOverlap >= 0 && verticalOverlap < SPIKY_TOP_THRESHOLD) {
            // Sonic is above Yadrin within the spiky-top zone: hurt Sonic even while rolling.
            applyTouchHurt(player, frameCounter);
            return;
        }

        if (isPlayerAttacking(player)) {
            applyEnemyBounce(player);
            destroyBadnik(player);
        } else {
            applyTouchHurt(player, frameCounter);
        }
    }

    private boolean isPlayerAttacking(AbstractPlayableSprite player) {
        return player.isSuperSonic()
                || player.getInvincibleFrames() > 0
                || player.getRolling()
                || player.getSpindash();
    }

    private void applyEnemyBounce(AbstractPlayableSprite player) {
        short ySpeed = player.getYSpeed();
        if (ySpeed < 0) {
            player.setYSpeed((short) (ySpeed + 0x100));
            return;
        }

        int playerY = player.getCentreY();
        if (playerY < currentY) {
            player.setYSpeed((short) -ySpeed);
        } else {
            player.setYSpeed((short) (ySpeed - 0x100));
        }
    }

    private void applyTouchHurt(AbstractPlayableSprite player, int frameCounter) {
        if (player.getInvulnerable()) {
            return;
        }

        boolean hadRings = player.getRingCount() > 0;
        if (hadRings && !player.hasShield() && services() != null) {
            services().spawnLostRings(player, frameCounter);
        }
        player.applyHurtOrDeath(currentX, false, hadRings);
    }

    @Override
    protected DestructionConfig getDestructionConfig() {
        return Sonic1DestructionConfig.S1_DESTRUCTION_CONFIG;
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed() && isOnScreenX(160);
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(4); // obPriority = 4
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.YADRIN);
        if (renderer == null) return;

        int frame = getMappingFrame();
        // S1 convention: default sprite art faces left, hFlip = true when facing right
        renderer.drawFrameIndex(frame, currentX, currentY, !facingLeft, false);
    }

    @Override
    public void appendDebugRenderCommands(DebugRenderContext ctx) {
        // Yellow hitbox rectangle
        ctx.drawRect(currentX, currentY, 16, 16, 1f, 1f, 0f);

        // Cyan velocity arrow if moving
        if (xVelocity != 0) {
            int endX = currentX + (xVelocity >> 5);
            ctx.drawArrow(currentX, currentY, endX, currentY, 0f, 1f, 1f);
        }

        // State label
        String stateStr = switch (secondaryState) {
            case STATE_MOVE -> "PAUSE(" + pauseTimer + ")";
            case STATE_FIX_TO_FLOOR -> "WALK";
            default -> "?";
        };
        String dir = facingLeft ? "L" : "R";
        String label = name + " " + stateStr + " f" + getMappingFrame() + " " + dir;
        ctx.drawWorldLabel(currentX, currentY, -2, label, DebugColor.YELLOW);
    }
}
