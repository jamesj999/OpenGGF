package com.openggf.game.sonic2.objects;

import com.openggf.camera.Camera;
import com.openggf.graphics.GLCommand;
import com.openggf.level.LevelManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.objects.AbstractObjectInstance;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.List;

/**
 * Small breathing bubble that rises from the player's mouth while underwater.
 * <p>
 * The bubble moves upward with a sine wave horizontal oscillation. The sine movement
 * is adjusted based on the direction the player was facing to ensure the bubble begins
 * by moving away from the player's mouth.
 * <p>
 * For countdown bubbles (showing numbers 5-0), the bubble locks its position on screen
 * when the number forms, staying visible until the animation ends.
 * <p>
 * Art key and countdown frame mapping are data-driven, allowing this class to work
 * with both Sonic 1 (LZ_BUBBLES) and Sonic 2 (BUBBLES) bubble art sheets.
 */
public class BreathingBubbleInstance extends AbstractObjectInstance {
    /** Upward velocity in pixels per frame */
    private static final int RISE_SPEED = 1;

    /** Amplitude of horizontal sine wave oscillation in pixels */
    private static final int SINE_AMPLITUDE = 2;

    /** Period of sine wave in frames */
    private static final int SINE_PERIOD = 32;

    /** Frame delay per animation sub-frame for countdown bubbles */
    private static final int COUNTDOWN_FRAME_DELAY = 5;

    /** Number of times the formed number appears before animation ends */
    private static final int COUNTDOWN_NUMBER_REPEATS = 4;

    /** Total frames for countdown bubble animation before number forms.
     *  Original S1 uses drown_time=$1C (28 frames) with rise speed -$88 subpix/frame (~15px rise).
     *  At 1px/frame rise speed, 3 frames * 5 delay = 15 frames ≈ 15px rise, matching the original. */
    private static final int COUNTDOWN_BUBBLE_FRAMES = 3;

    /** Current X position (float for smooth movement) */
    private float currentX;

    /** Current Y position */
    private int currentY;

    /** Base X position for sine calculation */
    private int baseX;

    /** Frame counter for sine oscillation */
    private int sineFrame;

    /** Whether sine wave starts moving left (away from player facing right) */
    private boolean startMovingLeft;

    /** Countdown number to display (-1 for regular bubble) */
    private int countdownNumber;

    /** Frame counter for countdown animation */
    private int countdownFrame;

    /** Whether the countdown number has formed (position locks) */
    private boolean numberFormed;

    /** Screen-space offset from camera when number forms (for position locking) */
    private int lockedScreenX;
    private int lockedScreenY;

    /** Total lifetime in frames */
    private int lifetime;

    /** Art key for the bubble renderer (game-specific) */
    private final String artKey;

    /**
     * Countdown frame mapping: index = countdown number (0-5), value = art frame index.
     * Null for regular (non-countdown) bubbles.
     */
    private final int[] countdownFrameMap;

    /** Maximum frame index for regular bubble growth animation */
    private final int maxBubbleFrame;

    /**
     * Creates a breathing bubble with game-specific art configuration.
     *
     * @param x                 World X coordinate (player's mouth position)
     * @param y                 World Y coordinate (player's Y position)
     * @param startMovingLeft   True if sine wave should start moving left
     * @param countdownNumber   Countdown number to display (-1 for regular bubble)
     * @param artKey            Art renderer key for this game's bubble sprites
     * @param countdownFrameMap Maps countdown number (0-5) to art frame index, or null
     * @param maxBubbleFrame    Maximum frame index for regular bubble growth
     */
    public BreathingBubbleInstance(int x, int y, boolean startMovingLeft, int countdownNumber,
                                   String artKey, int[] countdownFrameMap, int maxBubbleFrame) {
        super(new ObjectSpawn(x, y, 0x0A, 0, 0, false, 0), "BreathingBubble");
        this.currentX = x;
        this.currentY = y;
        this.baseX = x;
        this.sineFrame = 0;
        this.startMovingLeft = startMovingLeft;
        this.countdownNumber = countdownNumber;
        this.countdownFrame = 0;
        this.numberFormed = false;
        this.lifetime = 0;
        this.artKey = artKey;
        this.countdownFrameMap = countdownFrameMap;
        this.maxBubbleFrame = maxBubbleFrame;
    }

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        lifetime++;

        // Check if we've exited water (bubble pops)
        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager != null && levelManager.getCurrentLevel() != null) {
            WaterSystem waterSystem = WaterSystem.getInstance();
            int zoneId = levelManager.getCurrentLevel().getZoneIndex();
            int actId = levelManager.getCurrentAct();

            if (waterSystem.hasWater(zoneId, actId)) {
                int waterY = waterSystem.getVisualWaterLevelY(zoneId, actId);
                if (currentY <= waterY) {
                    // Bubble has reached the water surface - destroy it
                    setDestroyed(true);
                    return;
                }
            }
        }

        // Handle countdown bubble animation
        if (countdownNumber >= 0) {
            countdownFrame++;

            // Check if number is about to form (one frame before)
            int formFrame = COUNTDOWN_BUBBLE_FRAMES * COUNTDOWN_FRAME_DELAY;
            if (countdownFrame >= formFrame - 1 && !numberFormed) {
                // Lock position relative to camera
                Camera camera = Camera.getInstance();
                lockedScreenX = (int) currentX - camera.getX();
                lockedScreenY = currentY - camera.getY();
                numberFormed = true;
            }

            // Check if animation is complete
            int totalAnimFrames = formFrame + (COUNTDOWN_NUMBER_REPEATS * COUNTDOWN_FRAME_DELAY);
            if (countdownFrame >= totalAnimFrames) {
                setDestroyed(true);
                return;
            }
        }

        // Movement logic
        if (numberFormed) {
            // Position locked relative to camera
            Camera camera = Camera.getInstance();
            currentX = camera.getX() + lockedScreenX;
            currentY = camera.getY() + lockedScreenY;
        } else {
            // Normal bubble movement
            // Move upward
            currentY -= RISE_SPEED;

            // Apply sine wave horizontal oscillation
            sineFrame++;
            double angle = (2.0 * Math.PI * sineFrame) / SINE_PERIOD;

            // Adjust starting direction based on player facing
            if (startMovingLeft) {
                // Start moving left (negative direction)
                currentX = baseX - (float) (SINE_AMPLITUDE * Math.sin(angle));
            } else {
                // Start moving right (positive direction)
                currentX = baseX + (float) (SINE_AMPLITUDE * Math.sin(angle));
            }
        }

        // Destroy if bubble has been alive too long (failsafe)
        if (lifetime > 600) { // 10 seconds max
            setDestroyed(true);
        }
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (isDestroyed()) {
            return;
        }

        Camera camera = Camera.getInstance();
        int screenX = (int) currentX - camera.getX();
        int screenY = currentY - camera.getY();

        // Only render if on screen
        if (screenX < -16 || screenX > camera.getWidth() + 16 ||
            screenY < -16 || screenY > camera.getHeight() + 16) {
            return;
        }

        // Get the bubble renderer using game-specific art key
        ObjectRenderManager renderManager = LevelManager.getInstance().getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getRenderer(artKey);
        if (renderer == null || !renderer.isReady()) {
            return;
        }

        // Determine which frame to render
        int frameIndex;
        if (countdownNumber >= 0 && countdownFrameMap != null) {
            // Countdown bubble - show animation frames then number
            int formFrame = COUNTDOWN_BUBBLE_FRAMES * COUNTDOWN_FRAME_DELAY;
            if (countdownFrame >= formFrame) {
                // Show the countdown number using game-specific frame mapping
                frameIndex = countdownFrameMap[countdownNumber];
            } else {
                // Bubble growing animation - cycle through bubble sizes
                int animFrame = countdownFrame / COUNTDOWN_FRAME_DELAY;
                frameIndex = Math.min(animFrame / 3, maxBubbleFrame);
            }
        } else {
            // Regular small bubble - grow through frames as bubble rises
            int growthStage = lifetime / 30;  // Change frame every 30 ticks (~0.5 sec)
            frameIndex = Math.min(growthStage, maxBubbleFrame);
        }

        // Render the sprite
        renderer.drawFrameIndex(frameIndex, (int) currentX, currentY, false, false);
    }

    /**
     * Gets the current X position (integer for ObjectInstance interface).
     */
    @Override
    public int getX() {
        return (int) currentX;
    }

    /**
     * Gets the current Y position.
     */
    public int getY() {
        return currentY;
    }

    /**
     * Returns whether this is a countdown bubble.
     */
    public boolean isCountdownBubble() {
        return countdownNumber >= 0;
    }

    /**
     * Returns the countdown number (-1 if regular bubble).
     */
    public int getCountdownNumber() {
        return countdownNumber;
    }
}
