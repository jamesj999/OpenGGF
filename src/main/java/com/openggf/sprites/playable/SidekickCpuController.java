package com.openggf.sprites.playable;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.Direction;

/**
 * CPU-controlled sidekick follower with daisy-chain support.
 */
public class SidekickCpuController {

    private static final int FOLLOW_DELAY_FRAMES = 17;
    private static final int HORIZONTAL_SNAP_THRESHOLD = 16;
    private static final int JUMP_DISTANCE_TRIGGER = 64;
    private static final int JUMP_HEIGHT_THRESHOLD = 32;
    private static final int DESPAWN_TIMEOUT = 300;
    private static final int MANUAL_CONTROL_FRAMES = 600;
    private static final int FLY_ANIM_ID = Sonic2AnimationIds.FLY.id();
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
        PANIC
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
    private boolean jumpingFlag;
    private int minXBound = Integer.MIN_VALUE;
    private int maxXBound = Integer.MIN_VALUE;
    private int maxYBound = Integer.MIN_VALUE;
    private ObjectInstance lastRidingObject;
    private int normalFrameCount;
    private int sidekickCount = 1;

    public SidekickCpuController(AbstractPlayableSprite sidekick) {
        this(sidekick, null);
    }

    public SidekickCpuController(AbstractPlayableSprite sidekick, AbstractPlayableSprite leader) {
        this.sidekick = sidekick;
        this.leader = leader;
        this.respawnStrategy = new TailsRespawnStrategy(this);
    }

    public void update(int frameCount) {
        this.frameCounter = frameCount;

        if (leader == null) {
            clearInputs();
            return;
        }

        clearInputs();
        if ((controller2Held & MANUAL_HELD_MASK) != 0) {
            controlCounter = MANUAL_CONTROL_FRAMES;
        }

        switch (state) {
            case INIT -> updateInit();
            case SPAWNING -> updateSpawning();
            case APPROACHING -> updateApproaching();
            case NORMAL -> updateNormal();
            case PANIC -> updatePanic();
        }
    }

    public void setController2Input(int held, int logical) {
        controller2Held = held;
        controller2Logical = logical;
    }

    private void updateInit() {
        state = State.NORMAL;
        controlCounter = 0;
        despawnCounter = 0;
        normalFrameCount = 0;
        jumpingFlag = false;
        lastRidingObject = null;
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        sidekick.setObjectControlled(false);
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
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
        short recordedInput = effectiveLeader.getInputHistory(FOLLOW_DELAY_FRAMES);
        byte recordedStatus = effectiveLeader.getStatusHistory(FOLLOW_DELAY_FRAMES);
        int targetX = effectiveLeader.getCentreX(FOLLOW_DELAY_FRAMES);
        int targetY = effectiveLeader.getCentreY(FOLLOW_DELAY_FRAMES);
        int dx = targetX - sidekick.getCentreX();
        int dy = targetY - sidekick.getCentreY();

        inputLeft = (recordedInput & AbstractPlayableSprite.INPUT_LEFT) != 0;
        inputRight = (recordedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        inputUp = (recordedInput & AbstractPlayableSprite.INPUT_UP) != 0;
        inputDown = (recordedInput & AbstractPlayableSprite.INPUT_DOWN) != 0;
        inputJump = (recordedInput & AbstractPlayableSprite.INPUT_JUMP) != 0;

        if ((recordedStatus & AbstractPlayableSprite.STATUS_FACING_LEFT) != 0) {
            sidekick.setDirection(Direction.LEFT);
        } else {
            sidekick.setDirection(Direction.RIGHT);
        }

        boolean skipFollowSteering = sidekick.getPushing()
                && (recordedStatus & AbstractPlayableSprite.STATUS_PUSHING) == 0;
        if (!skipFollowSteering) {
            if (dx <= -HORIZONTAL_SNAP_THRESHOLD) {
                inputLeft = true;
                inputRight = false;
                if (sidekick.getGSpeed() != 0 && sidekick.getDirection() == Direction.LEFT) {
                    sidekick.setX((short) (sidekick.getX() - 1));
                }
            } else if (dx >= HORIZONTAL_SNAP_THRESHOLD) {
                inputRight = true;
                inputLeft = false;
                if (sidekick.getGSpeed() != 0 && sidekick.getDirection() == Direction.RIGHT) {
                    sidekick.setX((short) (sidekick.getX() + 1));
                }
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
                    && sidekick.getAnimationId() != Sonic2AnimationIds.DUCK.id()) {
                inputJump = true;
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

        sidekick.setDirection(leader.getCentreX() < sidekick.getCentreX() ? Direction.LEFT : Direction.RIGHT);
        inputDown = true;

        int phase = frameCounter & 0x7F;
        if (!sidekick.getSpindash()) {
            if (sidekick.getGSpeed() != 0) {
                return;
            }
            if (phase == 0) {
                clearInputs();
                state = State.NORMAL;
                normalFrameCount = 0;
                return;
            }
            if (sidekick.getAnimationId() == Sonic2AnimationIds.DUCK.id()) {
                inputJump = true;
            }
            return;
        }

        if (phase == 0) {
            clearInputs();
            state = State.NORMAL;
            normalFrameCount = 0;
            return;
        }
        if ((phase & 0x1F) == 0) {
            inputJump = true;
        }
    }

    private void applyManualControl() {
        inputUp = (controller2Logical & AbstractPlayableSprite.INPUT_UP) != 0;
        inputDown = (controller2Logical & AbstractPlayableSprite.INPUT_DOWN) != 0;
        inputLeft = (controller2Logical & AbstractPlayableSprite.INPUT_LEFT) != 0;
        inputRight = (controller2Logical & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        inputJump = (controller2Logical & AbstractPlayableSprite.INPUT_JUMP) != 0;
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
        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager == null) {
            return targetY;
        }
        int waterY = WaterSystem.getInstance().getWaterLevelY(levelManager.getCurrentZone(), levelManager.getCurrentAct());
        if (waterY == 0) {
            return targetY;
        }
        return Math.min(targetY, waterY - 0x10);
    }

    private boolean checkDespawn() {
        Camera camera = Camera.getInstance();
        ObjectInstance ridingObject = null;
        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager != null && levelManager.getObjectManager() != null) {
            ridingObject = levelManager.getObjectManager().getRidingObject(sidekick);
        }

        boolean onScreen = camera != null && camera.isOnScreen(sidekick);
        boolean keepAliveOnObject = ridingObject != null && ridingObject == lastRidingObject;
        if (!onScreen && lastRidingObject != null && ridingObject != null && ridingObject != lastRidingObject) {
            lastRidingObject = ridingObject;
            triggerDespawn();
            return true;
        }
        lastRidingObject = ridingObject;

        if (onScreen || keepAliveOnObject) {
            despawnCounter = 0;
            return false;
        }

        despawnCounter++;
        if (despawnCounter >= DESPAWN_TIMEOUT) {
            triggerDespawn();
            return true;
        }
        return false;
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
        sidekick.setX((short) 0x4000);
        sidekick.setY((short) 0);
        sidekick.setXSpeed((short) 0);
        sidekick.setYSpeed((short) 0);
        sidekick.setGSpeed((short) 0);
        sidekick.setHurt(false);
        sidekick.setAir(true);
        sidekick.setDead(false);
        sidekick.setDeathCountdown(0);
        sidekick.setSpindash(false);
        sidekick.setSpindashCounter((short) 0);
        sidekick.setForcedAnimationId(FLY_ANIM_ID);
        sidekick.setControlLocked(true);
        sidekick.setObjectControlled(true);
        lastRidingObject = null;
    }

    private void clearInputs() {
        inputUp = false;
        inputDown = false;
        inputLeft = false;
        inputRight = false;
        inputJump = false;
    }

    public void setRespawnStrategy(SidekickRespawnStrategy strategy) {
        this.respawnStrategy = strategy;
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
        if (state != State.NORMAL) {
            normalFrameCount = 0;
        }
    }

    /**
     * Package-private test helper: sets both state and normalFrameCount directly.
     */
    void forceStateForTest(State state, int normalFrames) {
        this.state = state;
        this.normalFrameCount = normalFrames;
    }

    public boolean getInputUp() { return inputUp; }
    public boolean getInputDown() { return inputDown; }
    public boolean getInputLeft() { return inputLeft; }
    public boolean getInputRight() { return inputRight; }
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
        lastRidingObject = null;
        minXBound = Integer.MIN_VALUE;
        maxXBound = Integer.MIN_VALUE;
        maxYBound = Integer.MIN_VALUE;
        clearInputs();
        sidekick.setForcedAnimationId(-1);
        sidekick.setControlLocked(false);
        sidekick.setObjectControlled(false);
    }
}
