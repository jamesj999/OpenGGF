package com.openggf.sprites.playable;

import com.openggf.camera.Camera;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.ObjectInstance;
import com.openggf.physics.Direction;
import com.openggf.sprites.managers.SpriteManager;

/**
 * CPU-controlled sidekick follower with daisy-chain support.
 */
public class SidekickCpuController {

    private static final int FOLLOW_DELAY_FRAMES = 17;
    private static final int HORIZONTAL_SNAP_THRESHOLD = 16;
    private static final int JUMP_DISTANCE_TRIGGER = 64;
    private static final int JUMP_HEIGHT_THRESHOLD = 32;
    private static final int DESPAWN_TIMEOUT = 300;
    private static final int RESPAWN_Y_OFFSET = 192;
    private static final int MAX_FLY_ACCEL = 12;
    private static final int MANUAL_CONTROL_FRAMES = 600;
    private static final int FLY_ANIM_ID = Sonic2AnimationIds.FLY.id();
    private static final int INPUT_START = 0x20;
    private static final int MANUAL_HELD_MASK = AbstractPlayableSprite.INPUT_UP
            | AbstractPlayableSprite.INPUT_DOWN
            | AbstractPlayableSprite.INPUT_LEFT
            | AbstractPlayableSprite.INPUT_RIGHT
            | AbstractPlayableSprite.INPUT_JUMP;
    private static final int RESPAWN_BYPASS_MASK = AbstractPlayableSprite.INPUT_JUMP | INPUT_START;
    private static final int FLY_LAND_BLOCKERS = 0xD2;

    public enum State {
        INIT,
        SPAWNING,
        APPROACHING,
        NORMAL,
        PANIC
    }

    private final AbstractPlayableSprite tails;
    private AbstractPlayableSprite leader;

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

    public SidekickCpuController(AbstractPlayableSprite tails) {
        this.tails = tails;
    }

    public void update(int frameCount) {
        this.frameCounter = frameCount;

        if (leader == null) {
            leader = findLeader();
            if (leader == null) {
                clearInputs();
                return;
            }
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
        jumpingFlag = false;
        lastRidingObject = null;
        tails.setForcedAnimationId(-1);
        tails.setControlLocked(false);
        tails.setObjectControlled(false);
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) 0);
        tails.setGSpeed((short) 0);
    }

    private void updateSpawning() {
        if (leader.getDead()) {
            return;
        }
        if ((controller2Logical & RESPAWN_BYPASS_MASK) != 0) {
            respawnToApproaching();
            return;
        }
        if ((frameCounter & 0x3F) != 0) {
            return;
        }
        if (leader.isObjectControlled()) {
            return;
        }
        if (leader.getAir() || leader.getRollingJump() || leader.isInWater() || leader.isPreventTailsRespawn()) {
            return;
        }
        respawnToApproaching();
    }

    private void respawnToApproaching() {
        state = State.APPROACHING;
        controlCounter = 0;
        despawnCounter = 0;
        jumpingFlag = false;
        tails.setCentreX(leader.getCentreX());
        tails.setCentreY((short) (leader.getCentreY() - RESPAWN_Y_OFFSET));
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) 0);
        tails.setGSpeed((short) 0);
        tails.setAir(true);
        tails.setDead(false);
        tails.setHurt(false);
        tails.setSpindash(false);
        tails.setSpindashCounter((short) 0);
        tails.setForcedAnimationId(FLY_ANIM_ID);
        tails.setControlLocked(true);
        tails.setObjectControlled(true);
    }

    private void updateApproaching() {
        tails.setForcedAnimationId(FLY_ANIM_ID);
        tails.setControlLocked(true);
        tails.setObjectControlled(true);

        if (checkDespawn()) {
            return;
        }

        int targetX = getDelayedLeaderX();
        int targetY = clampTargetYToWater(getDelayedLeaderY());
        int tailsX = tails.getCentreX();
        int tailsY = tails.getCentreY();

        int dx = targetX - tailsX;
        if (dx != 0) {
            int move = Math.abs(dx) / 16;
            move = Math.min(move, MAX_FLY_ACCEL);
            move += Math.abs(leader.getXSpeed()) / 256;
            move += 1;
            move = Math.min(move, Math.abs(dx));
            if (dx > 0) {
                tails.setDirection(Direction.RIGHT);
                tails.setX((short) (tails.getX() + move));
                tails.setXSpeed((short) (move * 256));
            } else {
                tails.setDirection(Direction.LEFT);
                tails.setX((short) (tails.getX() - move));
                tails.setXSpeed((short) (-move * 256));
            }
        } else {
            tails.setXSpeed((short) 0);
        }

        int dy = targetY - tailsY;
        if (dy > 0) {
            tails.setY((short) (tails.getY() + 1));
            tails.setYSpeed((short) 0x100);
        } else if (dy < 0) {
            tails.setY((short) (tails.getY() - 1));
            tails.setYSpeed((short) -0x100);
        } else {
            tails.setYSpeed((short) 0);
        }

        int remainingDx = targetX - tails.getCentreX();
        int remainingDy = targetY - tails.getCentreY();
        byte recordedStatus = leader.getStatusHistory(FOLLOW_DELAY_FRAMES);
        if ((recordedStatus & FLY_LAND_BLOCKERS) == 0 && remainingDx == 0 && remainingDy == 0) {
            tails.setForcedAnimationId(-1);
            tails.setControlLocked(false);
            tails.setObjectControlled(false);
            tails.setXSpeed((short) 0);
            tails.setYSpeed((short) 0);
            tails.setGSpeed((short) 0);
            tails.setHurt(false);
            tails.setAir(true);
            state = State.NORMAL;
            despawnCounter = 0;
        }
    }

    private void updateNormal() {
        if (leader.getDead()) {
            enterApproachingState();
            return;
        }
        if (tails.getDead()) {
            return;
        }
        if (checkDespawn()) {
            return;
        }
        if (controlCounter != 0) {
            applyManualControl();
            return;
        }
        if (tails.isObjectControlled()) {
            return;
        }

        if (tails.getMoveLockTimer() > 0 && tails.getGSpeed() == 0) {
            state = State.PANIC;
        }

        short recordedInput = leader.getInputHistory(FOLLOW_DELAY_FRAMES);
        byte recordedStatus = leader.getStatusHistory(FOLLOW_DELAY_FRAMES);
        int targetX = getDelayedLeaderX();
        int targetY = getDelayedLeaderY();
        int dx = targetX - tails.getCentreX();
        int dy = targetY - tails.getCentreY();

        inputLeft = (recordedInput & AbstractPlayableSprite.INPUT_LEFT) != 0;
        inputRight = (recordedInput & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        inputUp = (recordedInput & AbstractPlayableSprite.INPUT_UP) != 0;
        inputDown = (recordedInput & AbstractPlayableSprite.INPUT_DOWN) != 0;
        inputJump = (recordedInput & AbstractPlayableSprite.INPUT_JUMP) != 0;

        if ((recordedStatus & AbstractPlayableSprite.STATUS_FACING_LEFT) != 0) {
            tails.setDirection(Direction.LEFT);
        } else {
            tails.setDirection(Direction.RIGHT);
        }

        boolean skipFollowSteering = tails.getPushing()
                && (recordedStatus & AbstractPlayableSprite.STATUS_PUSHING) == 0;
        if (!skipFollowSteering) {
            if (dx <= -HORIZONTAL_SNAP_THRESHOLD) {
                inputLeft = true;
                inputRight = false;
                if (tails.getGSpeed() != 0 && tails.getDirection() == Direction.LEFT) {
                    tails.setX((short) (tails.getX() - 1));
                }
            } else if (dx >= HORIZONTAL_SNAP_THRESHOLD) {
                inputRight = true;
                inputLeft = false;
                if (tails.getGSpeed() != 0 && tails.getDirection() == Direction.RIGHT) {
                    tails.setX((short) (tails.getX() + 1));
                }
            }
        }

        if (jumpingFlag) {
            inputJump = true;
            if (!tails.getAir()) {
                jumpingFlag = false;
            }
        }

        if (!jumpingFlag && !tails.getAir()) {
            boolean passesDistanceGate = (frameCounter & 0xFF) == 0 || Math.abs(dx) < JUMP_DISTANCE_TRIGGER;
            if (passesDistanceGate
                    && dy <= -JUMP_HEIGHT_THRESHOLD
                    && (frameCounter & 0x3F) == 0
                    && tails.getAnimationId() != Sonic2AnimationIds.DUCK.id()) {
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
        if (tails.getMoveLockTimer() > 0) {
            return;
        }

        tails.setDirection(leader.getCentreX() < tails.getCentreX() ? Direction.LEFT : Direction.RIGHT);
        inputDown = true;

        int phase = frameCounter & 0x7F;
        if (!tails.getSpindash()) {
            if (tails.getGSpeed() != 0) {
                return;
            }
            if (phase == 0) {
                clearInputs();
                state = State.NORMAL;
                return;
            }
            if (tails.getAnimationId() == Sonic2AnimationIds.DUCK.id()) {
                inputJump = true;
            }
            return;
        }

        if (phase == 0) {
            clearInputs();
            state = State.NORMAL;
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
        state = State.APPROACHING;
        despawnCounter = 0;
        controlCounter = 0;
        tails.setSpindash(false);
        tails.setSpindashCounter((short) 0);
        tails.setForcedAnimationId(FLY_ANIM_ID);
        tails.setAir(true);
        tails.setControlLocked(true);
        tails.setObjectControlled(true);
    }

    private int getDelayedLeaderX() {
        return leader.getCentreX(FOLLOW_DELAY_FRAMES);
    }

    private int getDelayedLeaderY() {
        return leader.getCentreY(FOLLOW_DELAY_FRAMES);
    }

    private int clampTargetYToWater(int targetY) {
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
            ridingObject = levelManager.getObjectManager().getRidingObject(tails);
        }

        boolean onScreen = camera != null && camera.isOnScreen(tails);
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
        tails.setDead(false);
        tails.setDeathCountdown(0);
        triggerDespawn();
    }

    private void triggerDespawn() {
        state = State.SPAWNING;
        despawnCounter = 0;
        controlCounter = 0;
        jumpingFlag = false;
        tails.setX((short) 0x4000);
        tails.setY((short) 0);
        tails.setXSpeed((short) 0);
        tails.setYSpeed((short) 0);
        tails.setGSpeed((short) 0);
        tails.setHurt(false);
        tails.setAir(true);
        tails.setDead(false);
        tails.setDeathCountdown(0);
        tails.setSpindash(false);
        tails.setSpindashCounter((short) 0);
        tails.setForcedAnimationId(FLY_ANIM_ID);
        tails.setControlLocked(true);
        tails.setObjectControlled(true);
        lastRidingObject = null;
    }

    private void clearInputs() {
        inputUp = false;
        inputDown = false;
        inputLeft = false;
        inputRight = false;
        inputJump = false;
    }

    private AbstractPlayableSprite findLeader() {
        for (var sprite : SpriteManager.getInstance().getAllSprites()) {
            if (sprite instanceof AbstractPlayableSprite playable && sprite != tails && !playable.isCpuControlled()) {
                return playable;
            }
        }
        return null;
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
        jumpingFlag = false;
        leader = null;
        lastRidingObject = null;
        minXBound = Integer.MIN_VALUE;
        maxXBound = Integer.MIN_VALUE;
        maxYBound = Integer.MIN_VALUE;
        clearInputs();
        tails.setForcedAnimationId(-1);
        tails.setControlLocked(false);
        tails.setObjectControlled(false);
    }
}
