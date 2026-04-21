package com.openggf.game.sonic2.objects.badniks;

import com.openggf.level.objects.AbstractBadnikInstance;

import com.openggf.game.sonic2.Sonic2ObjectArtKeys;
import com.openggf.game.PlayableEntity;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.RenderPriority;

import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.RomObjectSnapshot;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Coconuts (0x9D) - Monkey Badnik from EHZ.
 * Climbs up and down a tree and throws coconut projectiles at the player.
 */
public class CoconutsBadnikInstance extends AbstractBadnikInstance {
    private static final int COLLISION_SIZE_INDEX = 0x09;
    private static final int IDLE_TIMER_INIT = 0x10;
    private static final int ATTACK_TIMER_RESET = 0x20;
    private static final int THROW_TIMER_INIT = 0x08;
    private static final int THROW_RANGE = 0x60;
    private static final int THROW_X_OFFSET = 0x0B;
    private static final int THROW_Y_OFFSET = -0x0D;
    private static final int THROW_X_VEL = 0x100;
    private static final int THROW_Y_VEL = -0x100;
    private static final int CLIMB_ANIM_SPEED = 5;
    private static final int[][] CLIMB_DATA = {
            { -1, 0x20 },
            { 1, 0x18 },
            { -1, 0x10 },
            { 1, 0x28 },
            { -1, 0x20 },
            { 1, 0x10 }
    };

    private enum State {
        IDLE,
        CLIMBING,
        THROWING
    }

    private enum ThrowState {
        HAND_RAISED,
        HAND_LOWERED
    }

    private int timer;
    private int climbTableIndex;
    private int attackTimer;
    private int yVelocity;
    private State state;
    private ThrowState throwState;

    public CoconutsBadnikInstance(ObjectSpawn spawn) {
        super(spawn, "Coconuts", Sonic2BadnikConfig.DESTRUCTION);
        this.currentY = spawn.y();
        this.currentX = spawn.x();
        this.timer = IDLE_TIMER_INIT;
        this.climbTableIndex = 0;
        this.attackTimer = 0;
        this.yVelocity = 0;
        this.state = State.IDLE;
        this.throwState = ThrowState.HAND_RAISED;
    }

    /**
     * Decodes the ROM's Obj9D state machine fields onto the engine's enum/int view.
     *
     * <p>Field mapping:
     * <ul>
     *   <li>{@code routine ($24)} → {@link State}
     *       (0/2 → IDLE, 4 → CLIMBING, 6 → THROWING).</li>
     *   <li>{@code routine_secondary ($25)} → {@link ThrowState}
     *       (0 → HAND_RAISED, non-zero → HAND_LOWERED).</li>
     *   <li>{@code Obj9D_timer ($2A, byte)} → {@link #timer}.</li>
     *   <li>{@code Obj9D_climb_table_index ($2C, word)} → {@link #climbTableIndex}.
     *       ROM stores byte-pair offset (0, 2, 4, …, $C wrapping); engine stores
     *       the entry index (0..5), so divide by 2 and wrap.</li>
     *   <li>{@code Obj9D_attack_timer ($2E, byte)} → {@link #attackTimer}.</li>
     * </ul>
     *
     * <p>{@code yVelocity} is re-assigned here because {@code CoconutsBadnikInstance}
     * shadows the inherited field; the parent class wrote to its own field, not this one.
     * Reference: {@code s2.asm} Obj9D_Idle / Obj9D_Climbing / Obj9D_Throwing
     * (ClimbData writes y_vel high byte via {@code move.b}, producing word 0xFF00 = -256).
     */
    @Override
    public void hydrateFromRomSnapshot(RomObjectSnapshot snapshot) {
        super.hydrateFromRomSnapshot(snapshot);

        int routine = snapshot.routine() & 0xFF;
        this.state = switch (routine) {
            case 0x04 -> State.CLIMBING;
            case 0x06 -> State.THROWING;
            default -> State.IDLE;   // 0x00 (Init) and 0x02 (Idle) both land here
        };

        this.throwState = (snapshot.routineSecondary() & 0xFF) == 0
                ? ThrowState.HAND_RAISED
                : ThrowState.HAND_LOWERED;

        this.timer = snapshot.byteAt(0x2A) & 0xFF;
        int romClimbIdx = snapshot.wordAt(0x2C) & 0xFFFF;
        this.climbTableIndex = (romClimbIdx / 2) % CLIMB_DATA.length;
        this.attackTimer = snapshot.byteAt(0x2E) & 0xFF;

        // Shadows AbstractBadnikInstance.yVelocity - must be set explicitly.
        this.yVelocity = snapshot.yVel();
    }

    @Override
    protected void updateMovement(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        currentX = spawn.x();
        switch (state) {
            case IDLE -> updateIdle(player);
            case CLIMBING -> updateClimbing();
            case THROWING -> updateThrowing();
        }
    }

    private void updateIdle(AbstractPlayableSprite player) {
        if (player != null) {
            facingLeft = player.getCentreX() < currentX;
            int distance = Math.abs(player.getCentreX() - currentX);
            if (distance < THROW_RANGE) {
                if (attackTimer == 0) {
                    startThrowing();
                    return;
                }
                attackTimer--;
            }
        }

        timer--;
        if (timer < 0) {
            state = State.CLIMBING;
            setClimbingDirection();
        }
    }

    private void updateClimbing() {
        timer--;
        if (timer <= 0) {
            state = State.IDLE;
            timer = IDLE_TIMER_INIT;
            return;
        }

        currentY += (yVelocity >> 8);
    }

    private void updateThrowing() {
        timer--;
        if (timer >= 0) {
            return;
        }

        if (throwState == ThrowState.HAND_RAISED) {
            throwState = ThrowState.HAND_LOWERED;
            timer = THROW_TIMER_INIT;
            animFrame = 2;
            throwCoconut();
            return;
        }

        throwState = ThrowState.HAND_RAISED;
        state = State.CLIMBING;
        setClimbingDirection();
    }

    private void startThrowing() {
        state = State.THROWING;
        throwState = ThrowState.HAND_RAISED;
        animFrame = 1;
        timer = THROW_TIMER_INIT;
        attackTimer = ATTACK_TIMER_RESET;
    }

    private void setClimbingDirection() {
        if (climbTableIndex >= CLIMB_DATA.length) {
            climbTableIndex = 0;
        }
        int[] entry = CLIMB_DATA[climbTableIndex++];
        yVelocity = entry[0] << 8;
        timer = entry[1];
    }

    private void throwCoconut() {
        int xOffset;
        int xVel;
        if (facingLeft) {
            xOffset = THROW_X_OFFSET;
            xVel = -THROW_X_VEL;
        } else {
            xOffset = -THROW_X_OFFSET;
            xVel = THROW_X_VEL;
        }

        BadnikProjectileInstance projectile = new BadnikProjectileInstance(
                spawn,
                BadnikProjectileInstance.ProjectileType.COCONUT,
                currentX + xOffset,
                currentY + THROW_Y_OFFSET,
                xVel,
                THROW_Y_VEL,
                true,
                !facingLeft);

        services().objectManager().addDynamicObject(projectile);
    }

    @Override
    protected void updateAnimation(int frameCounter) {
        if (state == State.THROWING) {
            animFrame = (throwState == ThrowState.HAND_RAISED) ? 1 : 2;
            return;
        }
        if (state == State.CLIMBING) {
            animFrame = ((frameCounter / CLIMB_ANIM_SPEED) & 1);
        }
    }

    @Override
    protected int getCollisionSizeIndex() {
        return COLLISION_SIZE_INDEX;
    }

    @Override
    public int getPriorityBucket() {
        return RenderPriority.clamp(5);
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        PatternSpriteRenderer renderer = getRenderer(Sonic2ObjectArtKeys.COCONUTS);
        if (renderer == null) return;

        boolean hFlip = !facingLeft;
        boolean vFlip = false;
        renderer.drawFrameIndex(animFrame, currentX, currentY, hFlip, vFlip);
    }
}
