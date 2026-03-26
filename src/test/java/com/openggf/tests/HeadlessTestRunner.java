package com.openggf.tests;

import com.openggf.LevelFrameStep;
import com.openggf.camera.Camera;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
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

    // BK2 recording playback fields
    private Bk2Movie bk2Movie;
    private int bk2StartIndex;
    private int currentBk2Index;

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

    // ---- BK2 recording playback ----

    /**
     * Sets the BK2 movie for recording playback.
     *
     * @param movie          The parsed BK2 movie
     * @param bk2FrameOffset The BK2 frame number (1-based line) where playback begins
     */
    public void setBk2Movie(Bk2Movie movie, int bk2FrameOffset) {
        this.bk2Movie = movie;
        this.bk2StartIndex = movie.bk2FrameToIndex(bk2FrameOffset);
        this.currentBk2Index = bk2StartIndex;
    }

    /**
     * Steps one frame using input from the BK2 movie recording.
     *
     * @return The raw input mask used (for trace input validation)
     * @throws IllegalStateException if no BK2 movie is loaded or the movie is exhausted
     */
    public int stepFrameFromRecording() {
        if (bk2Movie == null) {
            throw new IllegalStateException("No BK2 movie loaded. Call setBk2Movie() first.");
        }
        if (currentBk2Index >= bk2Movie.getFrameCount()) {
            throw new IllegalStateException(
                    "BK2 movie exhausted at index " + currentBk2Index
                    + " (movie has " + bk2Movie.getFrameCount() + " frames)");
        }

        Bk2FrameInput frameInput = bk2Movie.getFrame(currentBk2Index);
        int mask = frameInput.p1InputMask();

        // BK2 separates directional inputs (p1InputMask) from action buttons (p1ActionMask).
        // The jump/A/B/C buttons are in the action mask, so OR the jump bit into the
        // returned mask to ensure trace input validation sees the complete input state.
        if (frameInput.p1ActionMask() != 0) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }

        boolean up    = (mask & AbstractPlayableSprite.INPUT_UP) != 0;
        boolean down  = (mask & AbstractPlayableSprite.INPUT_DOWN) != 0;
        boolean left  = (mask & AbstractPlayableSprite.INPUT_LEFT) != 0;
        boolean right = (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        boolean jump  = (mask & AbstractPlayableSprite.INPUT_JUMP) != 0;

        stepFrame(up, down, left, right, jump);
        currentBk2Index++;

        return mask;
    }

    /**
     * Returns the number of BK2 recording frames remaining.
     *
     * @return Remaining frames, or 0 if no movie is loaded
     */
    public int getRecordingFramesRemaining() {
        if (bk2Movie == null) return 0;
        return bk2Movie.getFrameCount() - currentBk2Index;
    }
}
