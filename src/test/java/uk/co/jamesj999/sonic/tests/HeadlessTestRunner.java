package uk.co.jamesj999.sonic.tests;

import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

/**
 * Headless test runner that simulates the full game loop update cycle.
 * This replicates what GameLoop.step() does for level mode, allowing
 * physics and collision tests to run without an OpenGL context.
 *
 * <p>Usage example:
 * <pre>
 * HeadlessTestRunner runner = new HeadlessTestRunner(sprite);
 * runner.stepFrame(false, false, true, false, false); // Walk left
 * </pre>
 *
 * <p>Important setup requirements for tests using this class:
 * <ul>
 *   <li>Reset singletons: GraphicsManager.resetInstance(), Camera.resetInstance()</li>
 *   <li>Initialize headless graphics: GraphicsManager.getInstance().initHeadless()</li>
 *   <li>Load level: LevelManager.getInstance().loadZoneAndAct(zone, act)</li>
 *   <li>Fix GroundSensor: GroundSensor.setLevelManager(LevelManager.getInstance())</li>
 *   <li>Update camera position: Camera.getInstance().updatePosition(true)</li>
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
        this.levelManager = LevelManager.getInstance();
    }

    /**
     * Steps one frame with the given input state.
     * This replicates the update order from SpriteManager.update():
     * <ol>
     *   <li>Update object positions</li>
     *   <li>Solid object collision</li>
     *   <li>Player movement</li>
     *   <li>Plane switchers</li>
     *   <li>Animation</li>
     *   <li>Status tick</li>
     * </ol>
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
        sprite.setDirectionalInputPressed(left, right);

        // Update object positions (from GameLoop.step())
        levelManager.updateObjectPositions();

        // Solid object collision FIRST (from SpriteManager.update())
        if (levelManager.getObjectManager() != null) {
            levelManager.getObjectManager().updateSolidContacts(sprite);
        }

        // Player movement
        sprite.getMovementManager().handleMovement(up, down, left, right, jump, false, false, false);

        // Plane switchers
        levelManager.applyPlaneSwitchers(sprite);

        // Animation update
        sprite.getAnimationManager().update(frameCounter);

        // Status tick (invulnerability frames, etc.)
        sprite.tickStatus();
        sprite.endOfTick();
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
