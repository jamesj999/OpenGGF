package com.openggf.game.sonic1.objects.badniks;

import com.openggf.level.objects.AbstractBadnikInstance;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import com.openggf.level.objects.DestructionEffects.DestructionConfig;
import com.openggf.level.objects.ObjectArtKeys;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;
import com.openggf.level.objects.SubpixelMotion;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Sonic 1 Badnik 0x2D - Burrobot (LZ).
 * <p>
 * ROM reference: docs/s1disasm/_incObj/2D Burrobot.asm
 */
public class Sonic1BurrobotBadnikInstance extends AbstractBadnikInstance {

    private static final int COLLISION_SIZE_INDEX = 0x05;
    private static final int Y_RADIUS = 0x13;

    private static final int WALK_SPEED = 0x80;
    private static final int JUMP_VELOCITY = -0x400;
    private static final int GRAVITY = 0x18;

    // Burro .index states (ob2ndRout / 2).
    private static final int STATE_CHANGEDIR = 0;
    private static final int STATE_MOVE = 1;
    private static final int STATE_JUMP = 2;
    private static final int STATE_CHECK_SONIC = 3;

    // Ani_Burro animation IDs.
    private static final int ANIM_WALK1 = 0;
    private static final int ANIM_WALK2 = 1;
    private static final int ANIM_DIGGING = 2;
    private static final int ANIM_FALL = 3;

    private int state;
    private int stateTimer;

    private final SubpixelMotion.State motionState;
    private boolean floorProbeToggle;

    private int animationId;
    private int animationTick;

    public Sonic1BurrobotBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Burrobot");
        this.currentX = spawn.x();
        this.currentY = spawn.y();

        // S1 obStatus bit 0: 0 = facing left, 1 = facing right.
        this.facingLeft = (spawn.renderFlags() & 0x01) == 0;

        this.state = STATE_CHECK_SONIC;
        this.stateTimer = 0;

        this.motionState = new SubpixelMotion.State(spawn.x(), spawn.y(), 0, 0, 0, 0);
        this.floorProbeToggle = false;

        this.animationId = ANIM_DIGGING;
        this.animationTick = 0;
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case STATE_CHANGEDIR -> updateChangeDir();
            case STATE_MOVE -> updateMove(frameCounter);
            case STATE_JUMP -> updateJump(player);
            case STATE_CHECK_SONIC -> updateCheckSonic(player);
            default -> {
            }
        }
    }

    private void updateChangeDir() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = STATE_MOVE;
        stateTimer = 255;
        animationId = ANIM_WALK2;

        facingLeft = !facingLeft;
        xVelocity = facingLeft ? -WALK_SPEED : WALK_SPEED;
    }

    private void updateMove(int frameCounter) {
        stateTimer--;
        if (stateTimer < 0) {
            enterMoveEndBranch(frameCounter);
            return;
        }

        applySpeedToPos();

        floorProbeToggle = !floorProbeToggle;
        int probeX = currentX + (facingLeft ? -0x0C : 0x0C);

        if (!floorProbeToggle) {
            TerrainCheckResult aheadFloor = ObjectTerrainUtils.checkFloorDist(probeX, currentY, Y_RADIUS);
            if (!aheadFloor.foundSurface() || aheadFloor.distance() >= 0x0C) {
                enterMoveEndBranch(frameCounter);
            }
            return;
        }

        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);
        if (floor.foundSurface()) {
            currentY += floor.distance();
        }
    }

    private void enterMoveEndBranch(int frameCounter) {
        if ((frameCounter & 0x04) != 0) {
            state = STATE_CHANGEDIR;
            stateTimer = 59;
            xVelocity = 0;
            animationId = ANIM_WALK1;
            return;
        }

        state = STATE_JUMP;
        yVelocity = JUMP_VELOCITY;
        animationId = ANIM_DIGGING;
    }

    private void updateJump(AbstractPlayableSprite player) {
        applySpeedToPos();
        yVelocity += GRAVITY;

        if (yVelocity < 0) {
            return;
        }

        animationId = ANIM_FALL;

        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(currentX, currentY, Y_RADIUS);
        if (!floor.foundSurface() || floor.distance() >= 0) {
            return;
        }

        currentY += floor.distance();
        yVelocity = 0;
        animationId = ANIM_WALK2;
        stateTimer = 255;
        state = STATE_MOVE;

        // Burro_ChkSonic2 is used here to update facing based on player side.
        resolvePlayerDirectionAndVelocity(player, 0x80);
    }

    private void updateCheckSonic(AbstractPlayableSprite player) {
        int jumpVelocity = resolvePlayerDirectionAndVelocity(player, 0x60);
        if (player == null) {
            return;
        }

        // bcc.s locret_AE20 - exit if player is outside horizontal range ($60)
        int dx = player.getCentreX() - currentX;
        if (dx < 0) dx = -dx;
        if (dx >= 0x60) {
            return;
        }

        int dy = player.getCentreY() - currentY;
        if (dy >= 0 || dy < -0x80) {
            return;
        }

        if (player.isDebugMode()) {
            return;
        }

        state = STATE_JUMP;
        xVelocity = jumpVelocity;
        yVelocity = JUMP_VELOCITY;
        animationId = ANIM_DIGGING;
    }

    /**
     * Equivalent of Burro_ChkSonic2: resolve facing direction from player side and
     * return signed horizontal speed (+$80 right, -$80 left).
     */
    private int resolvePlayerDirectionAndVelocity(AbstractPlayableSprite player, int distanceLimit) {
        int speed = WALK_SPEED;
        if (player == null) {
            return facingLeft ? -speed : speed;
        }

        int dx = player.getCentreX() - currentX;
        if (dx < 0) {
            dx = -dx;
            speed = -speed;
            facingLeft = true;
        } else {
            facingLeft = false;
        }

        if (dx > distanceLimit) {
            return facingLeft ? -WALK_SPEED : WALK_SPEED;
        }

        return speed;
    }

    private void applySpeedToPos() {
        motionState.x = currentX;
        motionState.y = currentY;
        motionState.xVel = xVelocity;
        motionState.yVel = yVelocity;
        SubpixelMotion.moveSprite2(motionState);
        currentX = motionState.x;
        currentY = motionState.y;
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        animationTick++;
    }

    private int getMappingFrame() {
        int step = (animationTick / 4);
        return switch (animationId) {
            case ANIM_WALK1 -> (step & 1) == 0 ? 0 : 6;
            case ANIM_WALK2 -> (step & 1) == 0 ? 0 : 1;
            case ANIM_DIGGING -> (step & 1) == 0 ? 2 : 3;
            case ANIM_FALL -> 4;
            default -> 0;
        };
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    protected DestructionConfig getDestructionConfig() {
        return Sonic1DestructionConfig.S1_DESTRUCTION_CONFIG;
    }

    @Override
    public boolean isPersistent() {
        return !isDestroyed() && isOnScreenX(192);
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

        PatternSpriteRenderer renderer = getRenderer(ObjectArtKeys.BURROBOT);
        if (renderer == null) return;

        int frame = getMappingFrame();
        renderer.drawFrameIndex(frame, currentX, currentY, !facingLeft, false);
    }
}
