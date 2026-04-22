package com.openggf.sprites.playable;

import java.util.Objects;

import com.openggf.camera.Camera;
import com.openggf.game.AbstractLevelEventManager;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.GameModule;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.PlayerCharacter;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;

/**
 * CPU-controlled sidekick follower with daisy-chain support.
 */
public class SidekickCpuController {

    // ROM subtracts $44 bytes from Sonic_Pos_Record_Index in TailsCPU_Normal/Flying.
    // That index points at the next free 4-byte slot, while engine historyPos points
    // at the latest written slot, so the equivalent engine lookback is 16 frames.
    static final int ROM_FOLLOW_DELAY_FRAMES = 16;
    private static final int HORIZONTAL_SNAP_THRESHOLD = 16;
    private static final int JUMP_DISTANCE_TRIGGER = 64;
    private static final int JUMP_HEIGHT_THRESHOLD = 32;
    private static final int DESPAWN_TIMEOUT = 300;
    private static final int MANUAL_CONTROL_FRAMES = 600;
    private final int flyAnimId;
    private final int duckAnimId;
    private static final int INPUT_START = 0x20;
    private static final int MANUAL_HELD_MASK = AbstractPlayableSprite.INPUT_UP
            | AbstractPlayableSprite.INPUT_DOWN
            | AbstractPlayableSprite.INPUT_LEFT
            | AbstractPlayableSprite.INPUT_RIGHT
            | AbstractPlayableSprite.INPUT_JUMP;
    private static final int RESPAWN_BYPASS_MASK = AbstractPlayableSprite.INPUT_JUMP | INPUT_START;

    public enum State {
        INIT,
        SPAWNING,
        APPROACHING,
        NORMAL,
        PANIC,
        CARRY_INIT,   // ROM routine 0x0C - first tick after trigger (teleport + pickup)
        CARRYING      // ROM routine 0x0E / 0x20 - per-frame carry body
    }

    private static final int SETTLED_FRAME_THRESHOLD = 15;

    private final AbstractPlayableSprite sidekick;
    private AbstractPlayableSprite leader;
    private SidekickRespawnStrategy respawnStrategy;

    private State state = State.INIT;
    private int despawnCounter;
    private int frameCounter;
    private int controlCounter;
    private int controller2Held;
    private int controller2Logical;
    private boolean inputUp;
    private boolean inputDown;
    private boolean inputLeft;
    private boolean inputRight;
    private boolean inputJump;
    private boolean inputJumpPress;
    private boolean jumpingFlag;
    private int minXBound = Integer.MIN_VALUE;
    private int maxXBound = Integer.MIN_VALUE;
    private int maxYBound = Integer.MIN_VALUE;
    private int lastInteractObjectId;
    private int normalFrameCount;
    private int sidekickCount = 1;

    // =====================================================================
    // Tails-carry-Sonic support (S3K-only; null trigger = feature disabled)
    // =====================================================================
    private SidekickCarryTrigger carryTrigger;
    private short carryLatchX;
    private short carryLatchY;
    private boolean flyingCarryingFlag;
    private int releaseCooldown;

    public SidekickCpuController(AbstractPlayableSprite sidekick) {
        this(sidekick, null);
    }

    public SidekickCpuController(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        this.sidekick = sidekick;
        this.leader = leader;
        this.respawnStrategy = new TailsRespawnStrategy(this);
        this.flyAnimId = sidekick.resolveAnimationId(CanonicalAnimation.FLY);
        this.duckAnimId = sidekick.resolveAnimationId(CanonicalAnimation.DUCK);
    }

    public void update(int frameCount) {
        this.frameCounter = frameCount;

        // Decrement release cooldown every frame regardless of state (applies after carry).
        if (releaseCooldown > 0) {
            releaseCooldown--;
        }

        if (leader == null) {
            clearInputs();
            return;
        }

        clearInputs();
        if ((controller2Held & MANUAL_HELD_MASK) != 0) {
            controlCounter = MANUAL_CONTROL_FRAMES;
        }

        switch (state) {
            case INIT        -> updateInit();
            case SPAWNING    -> updateSpawning();
            case APPROACHING -> updateApproaching();
            case NORMAL      -> updateNormal();
            case PANIC       -> updatePanic();
            case CARRY_INIT  -> updateCarryInit();
            case CARRYING    -> updateCarrying();
        }
    }

    public void setController2Input(int held, int logical) {
        controller2Held = held;
        controller2Logical = logical;
    }

    private void updateInit() {
        // S3K Tails-carry hook (null trigger = no-op, keeps S1/S2 behaviour).
        if (carryTrigger != null && leader != null) {
            LevelManager lm = sidekick.currentLevelManager();
            if (lm != null) {
                int zone = lm.getCurrentZone();
                int act = lm.getCurrentAct();
                PlayerCharacter pc = resolvePlayerCharacter();
                if (carryTrigger.shouldEnterCarry(zone, act, pc)) {
                    // ROM loc_13A10 (sonic3k.asm:26414) for CNZ act 0:
                    //   move.w #$C,(Tails_CPU_routine).w
                    //   rts
                    // The INIT handler sets routine=0x0C and RETURNS. It does
                    // NOT fall through into loc_13FC2 (the 0x0C body). That
                    // body (which writes x_vel=$100) only runs on the NEXT
                    // tick. The same-frame fall-through that DOES exist is
                    // 0x0C -> 0x0E (loc_13FC2 -> loc_13FFA, no rts at the
                    // end of loc_13FC2); see updateCarryInit() which mirrors
                    // that fall-through by calling updateCarrying() directly.
                    carryTrigger.applyInitialPlacement(sidekick, leader);
                    state = State.CARRY_INIT;
                    return;
                }
            }
        }

        // ---- existing INIT body (preserved verbatim from the pre-carry implementation) ----
        state = State.NORMAL;
        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        lastInteractObjectId = 0;
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        sidekick.setObjectControlled(false);
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
    }

    private static PlayerCharacter resolvePlayerCharacter() {
        GameModule gameModule = GameModuleRegistry.getCurrent();
        if (gameModule != null) {
            LevelEventProvider lep = gameModule.getLevelEventProvider();
            if (lep instanceof AbstractLevelEventManager alem) {
                return alem.getPlayerCharacter();
            }
        }
        return PlayerCharacter.SONIC_AND_TAILS;
    }

    private void updateSpawning() {
        // Use effective leader for respawn checks — if our direct leader is also
        // despawned, chain-heal to whoever IS available (allows parallel respawn
        // instead of sequential cascade).
        AbstractPlayableSprite target = getEffectiveLeader();
        if (target == null || target.getDead()) {
            return;
        }
        if ((controller2Logical & RESPAWN_BYPASS_MASK) != 0) {
            respawnToApproaching(target);
            return;
        }
        if ((frameCounter & 0x3F) != 0) {
            return;
        }
        if (target.isObjectControlled()) {
            return;
        }
        if (target.getAir() || target.getRollingJump() || target.isInWater() || target.isPreventTailsRespawn()) {
            return;
        }
        respawnToApproaching(target);
    }

    private void respawnToApproaching(AbstractPlayableSprite target) {
        boolean started = respawnStrategy.beginApproach(sidekick, target);
        if (!started) {
            return; // Strategy can't start — stay in SPAWNING
        }
        state = State.APPROACHING;
        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
    }

    private void updateApproaching() {
        if (checkDespawn()) {
            return;
        }

        AbstractPlayableSprite effectiveLeader = getEffectiveLeader();
        if (effectiveLeader == null) {
            return;
        }
        if (respawnStrategy.updateApproaching(sidekick, effectiveLeader, frameCounter)) {
            respawnStrategy.onApproachComplete(sidekick, effectiveLeader);
            sidekick.setForcedAnimationId(-1);
            sidekick.setControlLocked(false);
            sidekick.setObjectControlled(false);
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setHurt(false);
            sidekick.setAir(true);
            state = State.NORMAL;
            normalFrameCount = 0;
            despawnCounter = 0;
        }
    }

    private void updateNormal() {
        normalFrameCount++;

        if (leader.getDead()) {
            enterApproachingState();
            return;
        }
        if (sidekick.getDead()) {
            return;
        }
        if (checkDespawn()) {
            return;
        }
        if (controlCounter != 0) {
            applyManualControl();
            return;
        }
        if (sidekick.isObjectControlled()) {
            return;
        }

        if (sidekick.getMoveLockTimer() > 0 && sidekick.getGSpeed() == 0) {
            state = State.PANIC;
            normalFrameCount = 0;
        }

        AbstractPlayableSprite effectiveLeader = getEffectiveLeader();
        if (effectiveLeader == null) {
            return;
        }
        short recordedInput = effectiveLeader.getInputHistory(ROM_FOLLOW_DELAY_FRAMES);
        byte recordedStatus = effectiveLeader.getStatusHistory(ROM_FOLLOW_DELAY_FRAMES);
        int targetX = effectiveLeader.getCentreX(ROM_FOLLOW_DELAY_FRAMES);
        int targetY = effectiveLeader.getCentreY(ROM_FOLLOW_DELAY_FRAMES);
        int dx = targetX - sidekick.getCentreX();
        int dy = targetY - sidekick.getCentreY();

        inputLeft = (recordedInput & AbstractPlayableSprite.INPUT_LEFT) != 0;
        inputRight = (recordedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        inputUp = (recordedInput & AbstractPlayableSprite.INPUT_UP) != 0;
        inputDown = (recordedInput & AbstractPlayableSprite.INPUT_DOWN) != 0;
        inputJump = (recordedInput & AbstractPlayableSprite.INPUT_JUMP) != 0;

        boolean skipFollowSteering = sidekick.getPushing()
                && (recordedStatus & AbstractPlayableSprite.STATUS_PUSHING) == 0;
        if (!skipFollowSteering) {
            // ROM enters FollowLeft/FollowRight for any nonzero dx; the 16px gate only
            // decides whether Tails overrides left/right input, not whether the +/-1 x_pos
            // nudge runs.
            if (dx < 0) {
                int absDx = -dx;
                if (absDx >= HORIZONTAL_SNAP_THRESHOLD) {
                    inputLeft = true;
                    inputRight = false;
                }
                if (sidekick.getGSpeed() != 0 && sidekick.getDirection() == Direction.LEFT) {
                    sidekick.shiftX(-1);
                }
            } else if (dx > 0) {
                if (dx >= HORIZONTAL_SNAP_THRESHOLD) {
                    inputRight = true;
                    inputLeft = false;
                }
                if (sidekick.getGSpeed() != 0 && sidekick.getDirection() == Direction.RIGHT) {
                    sidekick.shiftX(1);
                }
            } else if ((recordedStatus & AbstractPlayableSprite.STATUS_FACING_LEFT) != 0) {
                sidekick.setDirection(Direction.LEFT);
            } else {
                sidekick.setDirection(Direction.RIGHT);
            }
        }

        if (jumpingFlag) {
            inputJump = true;
            if (!sidekick.getAir()) {
                jumpingFlag = false;
            }
        }

        if (!jumpingFlag && !sidekick.getAir()) {
            boolean passesDistanceGate = (frameCounter & 0xFF) == 0 || Math.abs(dx) < JUMP_DISTANCE_TRIGGER;
            if (passesDistanceGate
                    && dy <= -JUMP_HEIGHT_THRESHOLD
                    && (frameCounter & 0x3F) == 0
                    && sidekick.getAnimationId() != duckAnimId) {
                inputJump = true;
                inputJumpPress = true;
                jumpingFlag = true;
            }
        }
    }

    private void updatePanic() {
        if (checkDespawn()) {
            return;
        }
        if (controlCounter != 0) {
            applyManualControl();
            return;
        }
        if (sidekick.getMoveLockTimer() > 0) {
            return;
        }

        if (!sidekick.getSpindash()) {
            if (sidekick.getGSpeed() != 0) {
                return;
            }
            sidekick.setDirection(leader.getCentreX() < sidekick.getCentreX() ? Direction.LEFT : Direction.RIGHT);
            inputDown = true;
            int phase = frameCounter & 0x7F;
            if (phase == 0) {
                clearInputs();
                state = State.NORMAL;
                normalFrameCount = 0;
                return;
            }
            if (sidekick.getAnimationId() == duckAnimId) {
                inputJump = true;
                inputJumpPress = true;
            }
            return;
        }

        inputDown = true;
        int phase = frameCounter & 0x7F;
        if (phase == 0) {
            clearInputs();
            state = State.NORMAL;
            normalFrameCount = 0;
            return;
        }
        if ((phase & 0x1F) == 0) {
            inputJump = true;
            inputJumpPress = true;
        }
    }

    /** ROM routine 0x0C. Mirrors sub_1459E (pickup) then falls through to 0x20. */
    private void updateCarryInit() {
        // sub_1459E semantics on Sonic (leader = cargo)
        leader.setObjectControlled(true);
        leader.setAir(true);
        leader.setRolling(false);
        leader.setRollingJump(false);
        leader.setGSpeed((short) 0);
        leader.setXSpeed(carryTrigger.carryInitXVel());
        leader.setYSpeed((short) 0);
        // Forced animation id: high-byte 0x22 per ROM sub_1459E; the sprite's
        // animation table maps the "carried" id. If the mapping is not yet wired
        // (see risk 9.2), this is a no-op for physics parity.
        leader.setForcedAnimationId(leader.resolveAnimationId(CanonicalAnimation.TAILS_CARRIED));

        // Tails's per-carry state
        sidekick.setAir(true);
        sidekick.setXSpeed(carryTrigger.carryInitXVel());
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setControlLocked(true);
        sidekick.setForcedAnimationId(flyAnimId);

        // Initialize the latch
        carryLatchX = carryTrigger.carryInitXVel();
        carryLatchY = 0;
        flyingCarryingFlag = true;
        releaseCooldown = 0;

        state = State.CARRYING;
        // ROM 0x0C -> 0x20 fall-through: one tick of the body this same frame.
        updateCarrying();
    }

    /** ROM routines 0x0E / 0x20 body. Runs each carry frame. */
    private void updateCarrying() {
        // ROM order inside Tails_Carry_Sonic:

        // 1. Hurt/dead (Sonic routine >= 4)
        if (leader.isHurt() || leader.getDead()) {
            releaseCarry(carryTrigger.carryLatchReleaseCooldownFrames());
            return;
        }

        // 2. External velocity change (release path C: latch mismatch)
        if (leader.getXSpeed() != carryLatchX || leader.getYSpeed() != carryLatchY) {
            releaseCarry(carryTrigger.carryLatchReleaseCooldownFrames());
            return;
        }

        // 3. A/B/C just-pressed (release path B)
        if (leader.isJumpJustPressed()) {
            performJumpRelease();
            return;
        }

        // 4. Ground release (release path A): Sonic in-air bit clear
        if (!leader.getAir()) {
            // ROM loc_1445A (sonic3k.asm:27268): move.w #-$100,y_vel(a1)
            // Small upward impulse on the ground-release path before clearing
            // object_control, matching ROM fall-through into loc_14460/loc_14466.
            leader.setYSpeed((short) -0x100);
            releaseCarry(0);
            return;
        }

        // --- No release this frame; re-apply parentage ---

        // Synthetic right-press injection every 32 frames (ROM: Level_frame_counter cadence)
        if ((frameCounter & carryTrigger.carryInputInjectMask()) == 0) {
            inputRight = true;
        }

        // Clamp Tails's x_vel to the carry velocity
        sidekick.setXSpeed(carryTrigger.carryInitXVel());

        // Sonic parentage (Tails_Carry_Sonic steps 5 + 8):
        //   x_pos = Tails.x_pos
        //   y_pos = Tails.y_pos + carryDescendOffsetY()
        //   x_vel = Tails.x_vel
        //   y_vel = Tails.y_vel
        leader.setCentreXPreserveSubpixel(sidekick.getCentreX());
        leader.setCentreYPreserveSubpixel(
                (short) (sidekick.getCentreY() + carryTrigger.carryDescendOffsetY()));
        leader.setXSpeed(sidekick.getXSpeed());
        leader.setYSpeed(sidekick.getYSpeed());

        // ROM Tails_Carry_Sonic loc_144F8 (sonic3k.asm:27328-27331):
        //   movem.l d0-a6,-(sp)
        //   lea     (Player_1).w,a0
        //   bsr.w   SonicKnux_DoLevelCollision
        //   movem.l (sp)+,d0-a6
        //
        // After parentage writes the ROM explicitly runs the full airborne
        // collision on Sonic (Player_1).  That probe's Player_TouchFloor tail
        // (sonic3k.asm:24366) clears Status_InAir when Tails's descended
        // position puts Sonic on ground.  Next frame's in-air check at
        // sonic3k.asm:27227 then branches to loc_1445A (release path A).
        //
        // Without this probe, the engine leaves Sonic's air flag set forever
        // while object_control holds him to Tails, so the ground-release path
        // is unreachable once Tails lands.
        //
        // NOTE: the normal landing handler (PlayableSpriteMovement.calculateLanding)
        // calls resetOnFloor(), which early-returns when isObjectControlled() is
        // true — meaning it would NOT clear the air bit in this carry scenario.
        // We supply a minimal inline landing handler that mirrors the subset of
        // Player_TouchFloor (sonic3k.asm:24366-24369) needed for the
        // next-frame release check to trip.
        CollisionSystem collision = Objects.requireNonNull(
                GameServices.collision(),
                "CollisionSystem must be available during CARRYING state "
                        + "(Tails-carry post-parentage probe, sonic3k.asm:27330)");
        collision.resolveAirCollision(leader, sprite -> {
            // ROM Player_TouchFloor (sonic3k.asm:24366-24369):
            //   bclr #Status_InAir,status(a0)
            //   bclr #Status_Push,status(a0)
            //   bclr #Status_RollJump,status(a0)
            //   move.b #0,jumping(a0)
            sprite.setAir(false);
            sprite.setPushing(false);
            sprite.setRollingJump(false);
            sprite.setJumping(false);
        });

        // Refresh the latch AFTER our writes so the next frame's compare is
        // against what we just wrote, not stale values.  The probe above may
        // have cleared the leader's y_vel via the collision adjustment, so
        // the latch captures the post-probe values to avoid a false latch
        // mismatch on the next frame.
        carryLatchX = leader.getXSpeed();
        carryLatchY = leader.getYSpeed();
    }

    private void performJumpRelease() {
        short xMag = carryTrigger.carryReleaseJumpXVel();
        short xVel = leader.getDirection() == Direction.LEFT
                ? (short) -xMag
                : xMag;
        leader.setXSpeed(xVel);
        leader.setYSpeed(carryTrigger.carryReleaseJumpYVel());
        leader.setRollingJump(true);
        leader.setForcedAnimationId(leader.resolveAnimationId(CanonicalAnimation.ROLL));
        releaseCarry(carryTrigger.carryJumpReleaseCooldownFrames());
    }

    private void releaseCarry(int cooldownFrames) {
        leader.setObjectControlled(false);
        leader.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        sidekick.setForcedAnimationId(-1);
        flyingCarryingFlag = false;
        releaseCooldown = cooldownFrames;
        state = State.NORMAL;
        normalFrameCount = 0;
    }

    private void applyManualControl() {
        inputUp = (controller2Held & AbstractPlayableSprite.INPUT_UP) != 0;
        inputDown = (controller2Held & AbstractPlayableSprite.INPUT_DOWN) != 0;
        inputLeft = (controller2Held & AbstractPlayableSprite.INPUT_LEFT) != 0;
        inputRight = (controller2Held & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        inputJump = (controller2Held & AbstractPlayableSprite.INPUT_JUMP) != 0;
        inputJumpPress = (controller2Logical & AbstractPlayableSprite.INPUT_JUMP) != 0;
        controlCounter--;
    }

    private void enterApproachingState() {
        AbstractPlayableSprite target = getEffectiveLeader();
        if (target == null) {
            triggerDespawn();
            return;
        }
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        boolean started = respawnStrategy.beginApproach(sidekick, target);
        if (!started) {
            triggerDespawn();
            return;
        }
        state = State.APPROACHING;
        despawnCounter = 0;
        controlCounter = 0;
        normalFrameCount = 0;
    }

    int clampTargetYToWater(int targetY) {
        LevelManager levelManager = sidekick.currentLevelManager();
        if (levelManager == null) {
            return targetY;
        }
        WaterSystem waterSystem = sidekick.currentWaterSystem();
        int waterY = waterSystem.getWaterLevelY(levelManager.getCurrentZone(), levelManager.getCurrentAct());
        if (waterY == 0) {
            return targetY;
        }
        return Math.min(targetY, waterY - 0x10);
    }

    private boolean checkDespawn() {
        boolean onScreen = sidekick.hasRenderFlagOnScreenState()
                ? sidekick.isRenderFlagOnScreen()
                : isCurrentlyVisible();
        int currentInteractObjectId = sidekick.getLatchedSolidObjectId() & 0xFF;
        if (onScreen) {
            lastInteractObjectId = currentInteractObjectId;
            despawnCounter = 0;
            return false;
        }

        if (sidekick.isOnObject()
                && currentInteractObjectId != 0
                && lastInteractObjectId != 0
                && currentInteractObjectId != lastInteractObjectId) {
            lastInteractObjectId = currentInteractObjectId;
            triggerDespawn();
            return true;
        }

        despawnCounter++;
        lastInteractObjectId = currentInteractObjectId;
        if (despawnCounter >= DESPAWN_TIMEOUT) {
            triggerDespawn();
            return true;
        }
        return false;
    }

    private boolean isCurrentlyVisible() {
        Camera camera = sidekick.currentCamera();
        return camera != null && camera.isOnScreen(sidekick);
    }

    public void despawn() {
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        triggerDespawn();
    }

    private void triggerDespawn() {
        state = State.SPAWNING;
        despawnCounter = 0;
        controlCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        sidekick.setHurt(false);
        sidekick.setRolling(false);
        sidekick.setRollingJump(false);
        sidekick.setOnObject(false);
        sidekick.setPushing(false);
        sidekick.setLatchedSolidObjectId(0);
        sidekick.setDirection(Direction.RIGHT);
        sidekick.setAir(true);
        sidekick.setCentreXPreserveSubpixel((short) 0x4000);
        sidekick.setCentreYPreserveSubpixel((short) 0);
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setForcedAnimationId(flyAnimId);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);
        lastInteractObjectId = 0;
    }

    private void clearInputs() {
        inputUp = false;
        inputDown = false;
        inputLeft = false;
        inputRight = false;
        inputJump = false;
        inputJumpPress = false;
    }

    public void setRespawnStrategy(SidekickRespawnStrategy strategy) {
        this.respawnStrategy = strategy;
    }

    public SidekickRespawnStrategy getRespawnStrategy() {
        return respawnStrategy;
    }

    public void setLeader(AbstractPlayableSprite leader) {
        this.leader = leader;
    }

    public AbstractPlayableSprite getLeader() {
        return leader;
    }

    public void setSidekickCount(int sidekickCount) {
        this.sidekickCount = sidekickCount;
    }

    /**
     * Returns true when this sidekick has been in NORMAL state for at least
     * {@link #SETTLED_FRAME_THRESHOLD} consecutive frames, meaning it has
     * "caught up" to its position in the chain.
     */
    public boolean isSettled() {
        return state == State.NORMAL && normalFrameCount >= SETTLED_FRAME_THRESHOLD;
    }

    /**
     * Walks up the leader chain to find the nearest settled leader (or the main
     * player). If the direct leader is not CPU-controlled or is settled, it is
     * returned immediately. Otherwise the chain is walked until a settled
     * sidekick or the main player is found.
     */
    public AbstractPlayableSprite getEffectiveLeader() {
        AbstractPlayableSprite current = leader;
        int maxSteps = sidekickCount;
        while (current != null && current.isCpuControlled() && maxSteps-- > 0) {
            SidekickCpuController ctrl = current.getCpuController();
            if (ctrl == null) {
                return current;
            }
            if (ctrl.isSettled()) {
                return current;
            }
            current = ctrl.getLeader();
        }
        return current;
    }

    /**
     * Sets the initial state for production use (e.g. pre-setting SPAWNING
     * after a level transition).
     */
    public void setInitialState(State state) {
        this.state = state;
        normalFrameCount = state == State.NORMAL ? SETTLED_FRAME_THRESHOLD : 0;
    }

    /**
     * Restores the ROM Tails CPU globals captured by the trace recorder.
     * Used by trace replay bootstrap so sidekick AI decisions continue from the
     * exact pre-trace state instead of restarting from a generic NORMAL state.
     */
    public void hydrateFromRomCpuState(int cpuRoutine, int controlCounter,
                                       int respawnCounter, int interactId,
                                       boolean jumping) {
        state = mapRomCpuRoutine(cpuRoutine);
        this.controlCounter = Math.max(0, controlCounter);
        this.despawnCounter = Math.max(0, respawnCounter);
        this.lastInteractObjectId = interactId & 0xFF;
        sidekick.setLatchedSolidObjectId(interactId);
        this.jumpingFlag = jumping;
        this.normalFrameCount = state == State.NORMAL ? SETTLED_FRAME_THRESHOLD : 0;
        clearInputs();
    }

    /**
     * Package-private test helper: sets both state and normalFrameCount directly.
     */
    void forceStateForTest(State state, int normalFrames) {
        this.state = state;
        this.normalFrameCount = normalFrames;
    }

    private static State mapRomCpuRoutine(int cpuRoutine) {
        return switch (cpuRoutine) {
            case 0x00 -> State.INIT;
            case 0x02 -> State.SPAWNING;
            case 0x04 -> State.APPROACHING;
            case 0x06 -> State.NORMAL;
            case 0x08 -> State.PANIC;
            case 0x0C -> State.CARRY_INIT;
            case 0x0E, 0x20 -> State.CARRYING;
            default -> throw new IllegalArgumentException(
                    "Unsupported ROM Tails CPU routine: 0x"
                            + Integer.toHexString(cpuRoutine));
        };
    }

    public boolean getInputUp() { return inputUp; }
    public boolean getInputDown() { return inputDown; }
    public boolean getInputLeft() { return inputLeft; }
    public boolean getInputRight() { return inputRight; }
    public boolean getInputJumpPress() { return inputJumpPress; }

    /** Package-private: allows respawn strategies to set directional input toward the leader. */
    void setApproachInput(boolean left, boolean right) {
        this.inputLeft = left;
        this.inputRight = right;
    }
    public boolean getInputJump() { return inputJump; }
    public State getState() { return state; }
    public boolean isApproaching() { return state == State.APPROACHING; }

    public int getMinXBound(int fallback) {
        return minXBound == Integer.MIN_VALUE ? fallback : minXBound;
    }

    public int getMaxXBound(int fallback) {
        return maxXBound == Integer.MIN_VALUE ? fallback : maxXBound;
    }

    public int getMaxYBound(int fallback) {
        return maxYBound == Integer.MIN_VALUE ? fallback : maxYBound;
    }

    public void setLevelBounds(Integer minX, Integer maxX, Integer maxY) {
        if (minX != null) {
            minXBound = minX;
        }
        if (maxX != null) {
            maxXBound = maxX;
        }
        if (maxY != null) {
            maxYBound = maxY;
        }
    }

    /**
     * Installs the game-specific carry trigger. Null (default) disables the
     * carry state machine; S1/S2 game modules pass null and the driver behaves
     * as before.
     */
    public void setCarryTrigger(SidekickCarryTrigger trigger) {
        this.carryTrigger = trigger;
    }

    /** Test/debug accessor for the release-cooldown byte (ROM Flying_carrying_Sonic_flag+1). */
    int getReleaseCooldownForTest() { return releaseCooldown; }

    int resolveAnimationId(CanonicalAnimation animation) {
        return sidekick.resolveAnimationId(animation);
    }

    public void reset() {
        state = State.INIT;
        despawnCounter = 0;
        controlCounter = 0;
        controller2Held = 0;
        controller2Logical = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        // Note: leader is NOT cleared — it's a structural chain relationship set at
        // construction time, not per-level state. Clearing it would break the sidekick
        // permanently since findLeader() scanning was removed in favor of explicit assignment.
        lastInteractObjectId = 0;
        minXBound = Integer.MIN_VALUE;
        maxXBound = Integer.MIN_VALUE;
        maxYBound = Integer.MIN_VALUE;
        clearInputs();
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        sidekick.setObjectControlled(false);
        // Carry state (carryTrigger is intentionally NOT cleared — level-load-scoped)
        carryLatchX = 0;
        carryLatchY = 0;
        flyingCarryingFlag = false;
        releaseCooldown = 0;
    }
}
