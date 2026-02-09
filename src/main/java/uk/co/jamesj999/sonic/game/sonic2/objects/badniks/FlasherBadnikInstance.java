package uk.co.jamesj999.sonic.game.sonic2.objects.badniks;

import uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtKeys;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Flasher (0xA3) - firefly/glowbug badnik from MCZ.
 *
 * ROM reference: ObjA3 in s2.asm.
 */
public class FlasherBadnikInstance extends AbstractBadnikInstance {
    private static final int COLLISION_SIZE_INDEX = 0x06; // subObjData ... ,6

    // Routine timers/velocities from ObjA3.
    private static final int WAIT_TIMER_INIT = 0x40;
    private static final int FLIGHT_TIMER_INIT = 0x80;
    private static final int ELECTRIFIED_TIMER_INIT = 0x80;
    private static final int INITIAL_X_VELOCITY = -0x100;
    private static final int INITIAL_Y_VELOCITY = 0x40;
    private static final int INITIAL_X_ACCELERATION = 0x0002;

    // ObjA3 flight phase table (word_38810 + byte_38820 pairs used after each threshold).
    private static final int[] FLIGHT_PHASE_THRESHOLDS = {
            0x100, 0x1A0, 0x208, 0x285, 0x300, 0x340, 0x390, 0x440
    };
    private static final boolean[] TOGGLE_X_ACCEL = {
            true, false, true, false, false, true, false, false
    };
    private static final boolean[] TOGGLE_Y_VELOCITY = {
            true, true, true, true, true, false, true, true
    };

    // Animation scripts from Ani_objA3_a/b/c (byte[0] is delay, rest are frame indices).
    private static final int[] ANIM_CHARGE = { // Ani_objA3_a: delay=0, frames only
            0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 1,
            0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 1, 0, 2, 3, 4
    };
    private static final int[] ANIM_ELECTRIFIED_LOOP = {2, 0, 3, 0, 4, 0, 3, 0}; // Ani_objA3_b: delay=0
    private static final int[] ANIM_RECOVER = {4, 3, 2, 1, 0};
    private static final int RECOVER_FRAME_DELAY = 3; // Ani_objA3_c delay byte
    private static final int FRAME_ELECTRIFIED_TRANSITION = 3; // loc_3884A

    private enum State {
        WAITING,            // routine 2
        FLYING,             // routine 4
        CHARGING,           // routine 6
        ELECTRIFIED_HOLD,   // routine 8
        RECOVERING,         // routine A
        RESETTING           // routine C
    }

    private State state;
    private int stateTimer;

    private int xPosFixed;
    private int yPosFixed;
    private int xAcceleration;
    private int flightCounter;
    private int flightPhaseIndex;
    private boolean electrified;

    private int[] animationScript;
    private boolean animationLoops;
    private int animationDelay;
    private int animationDelayCounter;
    private int animationIndex;

    public FlasherBadnikInstance(ObjectSpawn spawn, LevelManager levelManager) {
        super(spawn, levelManager, "Flasher");
        this.state = State.WAITING;
        this.stateTimer = WAIT_TIMER_INIT;
        this.xPosFixed = currentX << 8;
        this.yPosFixed = currentY << 8;
        this.facingLeft = (spawn.renderFlags() & 0x01) == 0;
        this.xAcceleration = 0;
        this.flightCounter = 0;
        this.flightPhaseIndex = 0;
        this.electrified = false;
        this.animFrame = 0;
    }

    @Override
    protected void updateMovement(int frameCounter, AbstractPlayableSprite player) {
        switch (state) {
            case WAITING -> updateWaiting();
            case FLYING -> updateFlying();
            case CHARGING -> updateCharging();
            case ELECTRIFIED_HOLD -> updateElectrifiedHold();
            case RECOVERING -> updateRecovering();
            case RESETTING -> updateResetting();
        }

        currentX = xPosFixed >> 8;
        currentY = yPosFixed >> 8;
    }

    private void updateWaiting() {
        stateTimer--;
        if (stateTimer >= 0) {
            return;
        }

        state = State.FLYING;
        xVelocity = INITIAL_X_VELOCITY;
        yVelocity = INITIAL_Y_VELOCITY;
        xAcceleration = INITIAL_X_ACCELERATION;
        flightCounter = 0;
        flightPhaseIndex = 0;
        stateTimer = FLIGHT_TIMER_INIT;
        electrified = false;
        clearAnimationState();
        animFrame = 0;
    }

    private void updateFlying() {
        stateTimer--;
        if (stateTimer < 0) {
            state = State.CHARGING;
            electrified = true; // ObjA3: ori.b #$80,collision_flags(a0)
            clearAnimationState();
            startAnimation(ANIM_CHARGE, 0, false);
            return;
        }

        if (flightCounter < 0) {
            destroyed = true;
            setDestroyed(true); // ObjA3: delete if objoff_2A wrapped negative
            return;
        }

        // ObjA3 clears x_flip then sets it when x_vel >= 0.
        facingLeft = xVelocity < 0;

        flightCounter++;
        applyFlightPhaseTransitions();

        xVelocity += xAcceleration;
        xPosFixed += xVelocity;
        yPosFixed += yVelocity;
    }

    private void applyFlightPhaseTransitions() {
        while (flightPhaseIndex < FLIGHT_PHASE_THRESHOLDS.length
                && flightCounter >= FLIGHT_PHASE_THRESHOLDS[flightPhaseIndex]) {
            if (TOGGLE_X_ACCEL[flightPhaseIndex]) {
                xAcceleration = -xAcceleration;
            }
            if (TOGGLE_Y_VELOCITY[flightPhaseIndex]) {
                yVelocity = -yVelocity;
            }
            flightPhaseIndex++;
        }
    }

    private void updateCharging() {
        if (advanceAnimation()) {
            // ObjA3 loc_3884A: clear anim state and set mapping_frame to 3 for one frame.
            clearAnimationState();
            animFrame = FRAME_ELECTRIFIED_TRANSITION;
            state = State.ELECTRIFIED_HOLD;
            stateTimer = ELECTRIFIED_TIMER_INIT;
        }
    }

    private void updateElectrifiedHold() {
        stateTimer--;
        if (stateTimer < 0) {
            // ObjA3 loc_38870: transition to routine A and clear animation fields.
            state = State.RECOVERING;
            clearAnimationState();
            return;
        }

        if (animationScript == null) {
            startAnimation(ANIM_ELECTRIFIED_LOOP, 0, true);
            return;
        }
        advanceAnimation();
    }

    private void updateRecovering() {
        if (animationScript == null) {
            startAnimation(ANIM_RECOVER, RECOVER_FRAME_DELAY, false);
            return;
        }

        if (advanceAnimation()) {
            state = State.RESETTING;
        }
    }

    private void updateResetting() {
        // ObjA3 loc_3888E: routine=4, timer=0x80, clear electrified bit + anim state.
        state = State.FLYING;
        stateTimer = FLIGHT_TIMER_INIT;
        electrified = false;
        clearAnimationState();
        animFrame = 0;
    }

    private void startAnimation(int[] script, int delay, boolean loop) {
        animationScript = script;
        animationDelay = delay;
        animationLoops = loop;
        animationIndex = 0;
        animationDelayCounter = delay;
        animFrame = script[0];
    }

    private boolean advanceAnimation() {
        if (animationScript == null || animationScript.length == 0) {
            return false;
        }

        if (animationDelayCounter > 0) {
            animationDelayCounter--;
            return false;
        }

        animationDelayCounter = animationDelay;
        animationIndex++;
        if (animationIndex >= animationScript.length) {
            if (animationLoops) {
                animationIndex = 0;
            } else {
                animationIndex = animationScript.length - 1;
                return true;
            }
        }
        animFrame = animationScript[animationIndex];
        return false;
    }

    private void clearAnimationState() {
        animationScript = null;
        animationLoops = false;
        animationDelay = 0;
        animationDelayCounter = 0;
        animationIndex = 0;
    }

    @Override
    public int getCollisionFlags() {
        if (destroyed) {
            return 0;
        }
        int category = electrified ? 0x80 : 0x00;
        return category | (getCollisionSizeIndex() & 0x3F);
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

        PatternSpriteRenderer renderer = renderManager.getRenderer(Sonic2ObjectArtKeys.FLASHER);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // ObjA3 uses x_flip = 0 while moving left, x_flip = 1 while moving right.
        renderer.drawFrameIndex(animFrame, currentX, currentY, !facingLeft, false);
    }
}
