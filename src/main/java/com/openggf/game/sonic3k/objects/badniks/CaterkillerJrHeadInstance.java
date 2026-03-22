package com.openggf.game.sonic3k.objects.badniks;

import com.openggf.game.sonic3k.Sonic3kObjectArtKeys;
import com.openggf.game.PlayableEntity;

import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseResult;
import com.openggf.physics.SwingMotion;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

/**
 * S3K Obj $8F - CaterKiller Jr head (AIZ).
 * Multi-segment caterpillar-like badnik. The head is attackable; body segments
 * always hurt the player.
 * <p>
 * Based on Obj_CaterKillerJr (sonic3k.asm lines 183317-183515).
 *
 * <h3>Movement cycle:</h3>
 * <ol>
 *   <li>Routine 4: Swing up/down for 3 peaks (max_vel=0x80, accel=8)</li>
 *   <li>Routine 6: Faster swing (max_vel=0x100), at peak: reverse x_vel, flip sprite</li>
 *   <li>Routine 8: Continue swing, at next peak: return to step 1</li>
 * </ol>
 */
public final class CaterkillerJrHeadInstance extends AbstractS3kBadnikInstance {

    private static final int COLLISION_SIZE_INDEX = 0x17;
    private static final int PRIORITY_BUCKET = 5;
    private static final int INITIAL_X_VEL = -0x100;
    private static final int SLOW_MAX_VEL = 0x80;
    private static final int FAST_MAX_VEL = 0x100;
    private static final int SWING_ACCEL = 8;
    private static final int SLOW_PEAK_COUNT = 3;
    private static final int BODY_SEGMENT_COUNT = 6;
    private static final int[] SEGMENT_WAIT_DELAYS = {0x0B, 0x17, 0x23, 0x2F, 0x37, 0x3F};

    private enum Phase { SWING_COUNTED, SWING_FAST, SWING_FINISH }

    private Phase phase;
    private int peakCounter;
    private int swingMaxVel;
    private boolean swingDown;

    private final List<CaterkillerJrBodyInstance> bodySegments = new ArrayList<>();

    public CaterkillerJrHeadInstance(ObjectSpawn spawn) {
        super(spawn, "CaterKillerJr",
                Sonic3kObjectArtKeys.CATERKILLER_JR, COLLISION_SIZE_INDEX, PRIORITY_BUCKET);
        this.mappingFrame = 0;
        this.xVelocity = INITIAL_X_VEL;
        initSwingPhase1();
        spawnBodySegments();
    }

    private void spawnBodySegments() {
        ObjectManager objectManager = services() != null ? services().objectManager() : null;
        if (objectManager == null) return;

        for (int i = 0; i < BODY_SEGMENT_COUNT; i++) {
            CaterkillerJrBodyInstance segment = new CaterkillerJrBodyInstance(
                    spawn, i, SEGMENT_WAIT_DELAYS[i]);
            bodySegments.add(segment);
            objectManager.addDynamicObject(segment);
        }
    }

    @Override
    public void update(int frameCounter, PlayableEntity playerEntity) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        if (destroyed) return;

        boolean shouldMove = switch (phase) {
            case SWING_COUNTED -> updateSwingCounted();
            case SWING_FAST -> updateSwingFast();
            case SWING_FINISH -> updateSwingFinish();
        };
        if (shouldMove) {
            moveWithVelocity();
        }
    }

    /** Routine 4: swing with counter. Skip movement on transition to phase 2. */
    private boolean updateSwingCounted() {
        if (applySwing()) {
            peakCounter--;
            if (peakCounter < 0) {
                initSwingPhase2();
                return false;
            }
        }
        return true;
    }

    /** Routine 6: faster swing. On peak, reverse direction. Always moves. */
    private boolean updateSwingFast() {
        if (applySwing()) {
            phase = Phase.SWING_FINISH;
            xVelocity = -xVelocity;
            facingLeft = !facingLeft;
        }
        return true;
    }

    /** Routine 8: on peak, reset to phase 1. Skip movement on transition. */
    private boolean updateSwingFinish() {
        if (applySwing()) {
            initSwingPhase1();
            return false;
        }
        return true;
    }

    /** Applies one frame of Swing_UpAndDown. Returns true if peak was reached. */
    private boolean applySwing() {
        SwingMotion.Result r = SwingMotion.update(SWING_ACCEL, yVelocity, swingMaxVel, swingDown);
        yVelocity = r.velocity();
        swingDown = r.directionDown();
        return r.directionChanged();
    }

    private void initSwingPhase1() {
        phase = Phase.SWING_COUNTED;
        peakCounter = SLOW_PEAK_COUNT;
        swingMaxVel = SLOW_MAX_VEL;
        yVelocity = SLOW_MAX_VEL;
        swingDown = false;
    }

    private void initSwingPhase2() {
        phase = Phase.SWING_FAST;
        swingMaxVel = FAST_MAX_VEL;
        yVelocity = FAST_MAX_VEL;
        swingDown = false;
    }

    @Override
    public void onPlayerAttack(PlayableEntity playerEntity, TouchResponseResult result) {
        AbstractPlayableSprite player = (AbstractPlayableSprite) playerEntity;
        super.onPlayerAttack(player, result);
        for (CaterkillerJrBodyInstance segment : bodySegments) {
            segment.onHeadDestroyed();
        }
    }
}
