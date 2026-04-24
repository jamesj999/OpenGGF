package com.openggf.tests;

import com.openggf.LevelFrameStep;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.control.InputHandler;
import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.game.GameServices;
import com.openggf.game.TitleCardProvider;
import com.openggf.level.LevelManager;
import com.openggf.level.SeamlessLevelTransitionRequest;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/**
 * Headless test runner that simulates the full game loop update cycle.
 * Delegates to {@link LevelFrameStep} for frame-level ordering and
 * {@code SpriteManager.update(...)} for playable updates, ensuring the
 * test harness cannot drift from production team behavior.
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
    private final InputHandler inputHandler = new InputHandler();
    private final int upKey = GameServices.configuration().getInt(SonicConfiguration.UP);
    private final int downKey = GameServices.configuration().getInt(SonicConfiguration.DOWN);
    private final int leftKey = GameServices.configuration().getInt(SonicConfiguration.LEFT);
    private final int rightKey = GameServices.configuration().getInt(SonicConfiguration.RIGHT);
    private final int jumpKey = GameServices.configuration().getInt(SonicConfiguration.JUMP);
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
     * playable/team ordering is defined by {@code SpriteManager.update(...)}.
     *
     * @param up    Up input pressed
     * @param down  Down input pressed
     * @param left  Left input pressed
     * @param right Right input pressed
     * @param jump  Jump input pressed
     */
    public void stepFrame(boolean up, boolean down, boolean left, boolean right, boolean jump) {
        frameCounter++;
        updateActiveTitleCardOverlay();
        if (applyPendingSeamlessTransition()) {
            return;
        }
        startPendingInLevelTitleCardIfRequested();
        GameServices.timers().update();
        setKeyState(upKey, up);
        setKeyState(downKey, down);
        setKeyState(leftKey, left);
        setKeyState(rightKey, right);
        setKeyState(jumpKey, jump);

        LevelFrameStep.execute(levelManager, GameServices.camera(),
                () -> GameServices.sprites().update(inputHandler));
        inputHandler.update();
    }

    private void setKeyState(int keyCode, boolean pressed) {
        inputHandler.handleKeyEvent(keyCode, pressed ? GLFW_PRESS : GLFW_RELEASE);
    }

    private void updateActiveTitleCardOverlay() {
        TitleCardProvider titleCardProvider = GameServices.module().getTitleCardProvider();
        if (titleCardProvider != null && titleCardProvider.isOverlayActive()) {
            titleCardProvider.update();
        }
    }

    private boolean applyPendingSeamlessTransition() {
        SeamlessLevelTransitionRequest seamlessRequest = levelManager.consumeSeamlessTransitionRequest();
        if (seamlessRequest == null) {
            return false;
        }
        levelManager.applySeamlessTransition(seamlessRequest);
        startPendingInLevelTitleCardIfRequested();
        return true;
    }

    private void startPendingInLevelTitleCardIfRequested() {
        if (!levelManager.consumeInLevelTitleCardRequest()) {
            return;
        }
        TitleCardProvider titleCardProvider = GameServices.module().getTitleCardProvider();
        if (titleCardProvider != null) {
            titleCardProvider.initializeInLevel(
                    levelManager.getInLevelTitleCardZone(),
                    levelManager.getInLevelTitleCardAct());
        }
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
     * @param bk2FrameOffset The 0-based emulation frame index where trace recording began
     *                       (from BizHawk's emu.framecount() in the Lua script).
     *                       This is a direct index into the movie's frame list, NOT a
     *                       1-based BK2 line number.
     */
    public void setBk2Movie(Bk2Movie movie, int bk2FrameOffset) {
        this.bk2Movie = movie;
        this.bk2StartIndex = bk2FrameOffset;
        this.currentBk2Index = bk2StartIndex;

        // ROM parity: v_vbla_byte counts ALL VBlanks since power-on, never
        // resets. Objects with timing gates like (v_vbla_byte + d7) & 7 depend
        // on the absolute mod-8 alignment. The bk2FrameOffset equals
        // emu.framecount() at the trace start. ObjectManager.update()
        // increments frameCounter BEFORE passing it to objects, so we
        // initialise one below the offset so the first increment lands on the
        // correct alignment: v_vbla_byte = bk2FrameOffset at the first game
        // frame, and objects see frameCounter = bk2FrameOffset after the ++.
        // TODO: Disabled until slot allocation matches ROM exactly. With correct
        // vbla alignment, slot-dependent timing gates (e.g. Batbrain dropcheck)
        // fire at the wrong frame because engine slots differ from ROM slots.
        // Without init, accidental mod-8 alignment of frameCounter happens to
        // match the first Batbrain encounter. See cascading slot issue analysis.
        // levelManager.getObjectManager().initVblaCounter(bk2FrameOffset - 1);
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

        boolean up = (mask & AbstractPlayableSprite.INPUT_UP) != 0;
        boolean down = (mask & AbstractPlayableSprite.INPUT_DOWN) != 0;
        boolean left = (mask & AbstractPlayableSprite.INPUT_LEFT) != 0;
        boolean right = (mask & AbstractPlayableSprite.INPUT_RIGHT) != 0;
        boolean jump = (mask & AbstractPlayableSprite.INPUT_JUMP) != 0;

        stepFrame(up, down, left, right, jump);
        currentBk2Index++;

        return mask;
    }

    /**
     * Advances the BK2 movie by one frame without processing physics.
     * Used for lag frames where the ROM didn't execute the main game loop,
     * so the engine should not process physics either.
     *
     * @return The raw input mask for that frame (for trace input validation)
     * @throws IllegalStateException if no BK2 movie is loaded or the movie is exhausted
     */
    public int skipFrameFromRecording() {
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
        if (frameInput.p1ActionMask() != 0) {
            mask |= AbstractPlayableSprite.INPUT_JUMP;
        }

        // Advance BK2 index without calling stepFrame() - no physics processed.
        currentBk2Index++;

        // ROM parity: v_vbla_byte increments in the VBlank handler even on lag
        // frames. Objects that use timing gates like (v_vbla_byte + d7) & 7 are
        // sensitive to this. Advance the ObjectManager's frame counter to keep
        // it aligned with v_vbla_byte.
        levelManager.getObjectManager().advanceVblaCounter();
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

    /**
     * Advances the BK2 cursor without mutating gameplay state.
     * Used when replay logic elastically skips trace windows after the
     * engine reaches the matching checkpoint earlier than the recorded run.
     *
     * @param frameCount number of BK2 frames to skip
     */
    public void advanceRecordingCursor(int frameCount) {
        if (frameCount <= 0) {
            return;
        }
        if (bk2Movie == null) {
            throw new IllegalStateException("No BK2 movie loaded. Call setBk2Movie() first.");
        }
        int targetIndex = currentBk2Index + frameCount;
        if (targetIndex > bk2Movie.getFrameCount()) {
            throw new IllegalStateException(
                    "BK2 movie exhausted while advancing cursor to index " + targetIndex
                            + " (movie has " + bk2Movie.getFrameCount() + " frames)");
        }
        currentBk2Index = targetIndex;
    }
}
