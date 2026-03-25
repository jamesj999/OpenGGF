package com.openggf.tests;

import com.openggf.LevelFrameStep;
import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Headless test runner that simulates the full game loop update cycle.
 * Delegates to {@link LevelFrameStep} for frame-level ordering and
 * {@link SpriteManager#tickPlayablePhysics} for per-sprite physics,
 * ensuring the test harness cannot drift from production.
 *
 * <p>Usage example:
 * <pre>
 * HeadlessTestRunner runner = new HeadlessTestRunner(sprite);
 * runner.stepFrame(false, false, true, false, false); // Walk left
 * </pre>
 *
 * <p>Important setup requirements for tests using this class:
 * <ul>
 *   <li>Reset singletons: GraphicsManager.getInstance().resetState(), GameServices.camera().resetState()</li>
 *   <li>Initialize headless graphics: GraphicsManager.getInstance().initHeadless()</li>
 *   <li>Load level: GameServices.level().loadZoneAndAct(zone, act)</li>
 *   <li>Fix GroundSensor: GroundSensor.setLevelManager(GameServices.level())</li>
 *   <li>Update camera position: GameServices.camera().updatePosition(true)</li>
 * </ul>
 */
public class HeadlessTestRunner {
    private final AbstractPlayableSprite sprite;
    private final LevelManager levelManager;
    private int frameCounter = 0;

    /**
     * Creates a new HeadlessTestRunner for the given sprite.
     *
     * @param sprite The playable sprite to run physics updates on
     */
    public HeadlessTestRunner(AbstractPlayableSprite sprite) {
        this.sprite = sprite;
        this.levelManager = GameServices.level();
    }

    /**
     * Steps one frame with the given input state.
     * Frame-level ordering is defined by {@link LevelFrameStep#execute};
     * per-sprite physics ordering is defined by
     * {@link SpriteManager#tickPlayablePhysics}.
     *
     * @param up    Up input pressed
     * @param down  Down input pressed
     * @param left  Left input pressed
     * @param right Right input pressed
     * @param jump  Jump input pressed
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump) {
        frameCounter++;

        // Store RAW input state for objects (like springs) that need to query button state
        // even when control is locked. This matches SpriteManager.update() behavior where
        // input state is set BEFORE object updates. Objects read isJumpPressed() during
        // their update() methods, so this must be set before updateObjectPositions().
        sprite.setJumpInputPressed(jump);
        sprite.setDirectionalInputPressed(up, down, left, right);

        // Canonical frame tick: objects → zone features → sprites → level events → camera → level.
        LevelFrameStep.execute(levelManager, GameServices.camera(), () -> {
            // Compute effective inputs matching SpriteManager.update() filtering.
            boolean controlLocked = sprite.isControlLocked();
            boolean forcedRight = sprite.isForcedInputActive(AbstractPlayableSprite.INPUT_RIGHT)
                    || sprite.isForceInputRight();
            boolean forcedLeft = sprite.isForcedInputActive(AbstractPlayableSprite.INPUT_LEFT);
            boolean forcedUp = sprite.isForcedInputActive(AbstractPlayableSprite.INPUT_UP);
            boolean forcedDown = sprite.isForcedInputActive(AbstractPlayableSprite.INPUT_DOWN);
            boolean forcedJump = sprite.isForcedInputActive(AbstractPlayableSprite.INPUT_JUMP);
            boolean effectiveRight = (!controlLocked && right) || forcedRight;
            boolean effectiveLeft = ((!controlLocked && left) || forcedLeft) && !forcedRight;
            boolean effectiveUp = (!controlLocked && up) || forcedUp;
            boolean effectiveDown = (!controlLocked && down) || forcedDown;
            boolean effectiveJump = (!controlLocked && jump) || forcedJump;

            SpriteManager.tickPlayablePhysics(sprite,
                    effectiveUp, effectiveDown, effectiveLeft, effectiveRight, effectiveJump,
                    false, false, false, levelManager, frameCounter);
        });
    }

    /**
     * Steps multiple frames with no input (idle).
     *
     * @param frames Number of frames to step
     */
    public void stepIdleFrames(int frames) {
        for (int i = 0; i < frames; i++) {
            stepFrame(false, false, false, false, false);
        }
    }

    /**
     * Gets the current frame counter.
     *
     * @return The number of frames stepped since creation
     */
    public int getFrameCounter() {
        return frameCounter;
    }

    /**
     * Gets the sprite being controlled.
     *
     * @return The playable sprite
     */
    public AbstractPlayableSprite getSprite() {
        return sprite;
    }
}
