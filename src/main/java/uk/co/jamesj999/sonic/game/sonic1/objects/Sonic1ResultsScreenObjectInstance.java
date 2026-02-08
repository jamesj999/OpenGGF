package uk.co.jamesj999.sonic.game.sonic1.objects;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1AudioProfile;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic2.objects.AbstractResultsScreen;
import uk.co.jamesj999.sonic.graphics.FadeManager;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;

import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 1 end-of-act results screen ("SONIC HAS PASSED").
 * <p>
 * Based on the Sonic 1 disassembly: {@code _incObj/3A Got Through Card.asm}
 * <p>
 * States (from Got_Index):
 * <ol>
 *   <li>SLIDE_IN: 7 elements slide toward target positions at 16px/frame</li>
 *   <li>PRE_TALLY_DELAY: Wait 180 frames (3 seconds)</li>
 *   <li>TALLY: Subtract 10 from time/ring bonus each frame, add to score.
 *        Play tick sound every 4 frames. Play cash SFX when done.</li>
 *   <li>POST_TALLY_DELAY: Wait 180 frames</li>
 *   <li>EXIT: Fade to black, advance to next level</li>
 * </ol>
 *
 * @see AbstractResultsScreen
 */
public class Sonic1ResultsScreenObjectInstance extends AbstractResultsScreen {
    private static final Logger LOGGER = Logger.getLogger(Sonic1ResultsScreenObjectInstance.class.getName());

    // -----------------------------------------------------------------------
    // Time bonus table (from s1disasm 0D Signpost.asm:TimeBonuses)
    // Identical to Sonic 2. Index = (totalSeconds / 15), capped at 20.
    // -----------------------------------------------------------------------
    private static final int[] TIME_BONUSES = {
            5000, 5000, 1000, 500, 400, 400, 300, 300,
            200, 200, 200, 200, 100, 100, 100, 100,
            50, 50, 50, 50, 0
    };

    // -----------------------------------------------------------------------
    // Slide configuration (from Got_Config in 3A Got Through Card.asm)
    //
    // Each element has: startX, targetX, screenY, mappingFrame
    //
    // VDP coordinates use +128 offset. Screen Y = VDP Y - 128.
    // X values are VDP pixel coords. Elements slide from startX toward
    // targetX at 16 pixels/frame (Got_Move: moveq #$10,d1).
    // -----------------------------------------------------------------------

    // Element indices into the slide config arrays
    private static final int ELEM_SONIC_HAS = 0;
    private static final int ELEM_PASSED = 1;
    private static final int ELEM_ACT = 2;
    private static final int ELEM_SCORE = 3;
    private static final int ELEM_TIME_BONUS = 4;
    private static final int ELEM_RING_BONUS = 5;
    private static final int ELEM_OVAL = 6;
    private static final int ELEMENT_COUNT = 7;

    // Start X positions (VDP coordinates from Got_Config)
    private static final int[] ELEM_START_X = {
            4,       // "SONIC HAS"
            -0x120,  // "PASSED"
            0x40C,   // "ACT N"
            0x520,   // Score
            0x540,   // Time Bonus
            0x560,   // Ring Bonus
            0x20C    // Oval
    };

    // Target X positions (VDP coordinates from Got_Config got_mainX)
    private static final int[] ELEM_TARGET_X = {
            0x124,   // "SONIC HAS"
            0x120,   // "PASSED"
            0x14C,   // "ACT N"
            0x120,   // Score
            0x120,   // Time Bonus
            0x120,   // Ring Bonus
            0x14C    // Oval
    };

    // Screen Y positions (VDP Y - 128, from Got_Config obScreenY)
    private static final int ELEM_Y_SONIC_HAS = 0xBC - 128;    // 60
    private static final int ELEM_Y_PASSED = 0xD0 - 128;       // 80
    private static final int ELEM_Y_ACT = 0xD6 - 128;          // 86
    private static final int ELEM_Y_SCORE = 0xEC - 128;        // 108
    private static final int ELEM_Y_TIME_BONUS = 0xFC - 128;   // 124
    private static final int ELEM_Y_RING_BONUS = 0x10C - 128;  // 140
    private static final int ELEM_Y_OVAL = 0xCC - 128;         // 76

    private static final int[] ELEM_SCREEN_Y = {
            ELEM_Y_SONIC_HAS, ELEM_Y_PASSED, ELEM_Y_ACT, ELEM_Y_SCORE,
            ELEM_Y_TIME_BONUS, ELEM_Y_RING_BONUS, ELEM_Y_OVAL
    };

    // Mapping frame indices (from Got_Config)
    // Frame 6 = "ACT" with act number added at runtime
    private static final int FRAME_SONIC_HAS = 0;
    private static final int FRAME_PASSED = 1;
    private static final int FRAME_SCORE = 2;
    private static final int FRAME_TIME_BONUS = 3;
    private static final int FRAME_RING_BONUS = 4;
    private static final int FRAME_OVAL = 5;
    private static final int FRAME_ACT_BASE = 6; // add v_act (0-indexed) for ACT 1/2/3
    private static final int FRAME_SCORE_DOTS = 9; // separator dots split from SCORE frame

    // Slide-in duration: determined by the slowest element (ring bonus)
    // Distance = |0x560 - 0x120| = 0x440 = 1088 pixels, at 16px/frame = 68 frames
    private static final int SLIDE_IN_FRAMES = 68;

    // -----------------------------------------------------------------------
    // Bonus state
    // -----------------------------------------------------------------------
    private int timeBonus;
    private int ringBonus;

    // Input data
    private final int elapsedTimeSeconds;
    private final int ringCount;
    private final int actNumber; // 1-indexed for display

    // Per-element current X positions (VDP coordinates, updated each frame during slide)
    private final int[] elemCurrentX = new int[ELEMENT_COUNT];

    // Track whether elements have reached their targets
    private final boolean[] elemArrived = new boolean[ELEMENT_COUNT];

    public Sonic1ResultsScreenObjectInstance(int elapsedTimeSeconds, int ringCount, int actNumber) {
        super("s1_results_screen");
        this.elapsedTimeSeconds = elapsedTimeSeconds;
        this.ringCount = ringCount;
        this.actNumber = actNumber;

        calculateBonuses();

        // Initialize element positions to their start values
        System.arraycopy(ELEM_START_X, 0, elemCurrentX, 0, ELEMENT_COUNT);

        LOGGER.info("S1 Results screen created: act=" + actNumber
                + ", timeBonus=" + timeBonus + ", ringBonus=" + ringBonus);
    }

    private void calculateBonuses() {
        // Time bonus: index by (total seconds / 15), capped at index 20
        // From s1disasm 0D Signpost.asm: divu.w #15,d0 / cmp.w d1,d0
        int index = elapsedTimeSeconds / 15;
        if (index < 0) {
            index = 0;
        } else if (index >= TIME_BONUSES.length) {
            index = TIME_BONUSES.length - 1;
        }
        timeBonus = TIME_BONUSES[index];

        // Ring bonus: rings * 10
        // From s1disasm: mulu.w #10,d0
        ringBonus = ringCount * 10;
    }

    // -----------------------------------------------------------------------
    // State machine overrides
    // -----------------------------------------------------------------------

    @Override
    protected int getSlideDuration() {
        return SLIDE_IN_FRAMES;
    }

    /**
     * Override slide-in to use per-element independent movement.
     * Each element moves 16 pixels/frame toward its target.
     * From Got_Move (routine 2): moveq #$10,d1
     */
    @Override
    protected void updateSlideIn() {
        boolean allArrived = true;

        for (int i = 0; i < ELEMENT_COUNT; i++) {
            if (elemArrived[i]) {
                continue;
            }

            int target = ELEM_TARGET_X[i];
            int current = elemCurrentX[i];

            if (current == target) {
                elemArrived[i] = true;
                continue;
            }

            // Move 16 pixels toward target (Got_Move: moveq #$10,d1)
            int direction = (target > current) ? SLIDE_SPEED_PIXELS_PER_FRAME : -SLIDE_SPEED_PIXELS_PER_FRAME;
            int next = current + direction;

            // Check if we've reached or passed the target
            if ((direction > 0 && next >= target) || (direction < 0 && next <= target)) {
                next = target;
                elemArrived[i] = true;
            }

            elemCurrentX[i] = next;

            if (!elemArrived[i]) {
                allArrived = false;
            }
        }

        // In the original, the state transition triggers when the element with
        // frame==4 (ring bonus) reaches its target position.
        // Got_Move -> loc_C61A: cmpi.b #4,obFrame(a0) / bne.s loc_C5FE
        if (elemArrived[ELEM_RING_BONUS]) {
            state = STATE_PRE_TALLY_DELAY;
            stateTimer = 0;
            onSlideInComplete();
        }

        // Keep incrementing totalFrames for rendering (base class update handles this,
        // but we override updateSlideIn so stateTimer is handled by the base update())
    }

    @Override
    protected TallyResult performTallyStep() {
        boolean anyRemaining = false;
        int totalIncrement = 0;

        // Decrement time bonus by 10 (Got_TimeBonus: subi.w #10,(v_timebonus).w)
        int[] timeResult = decrementBonus(timeBonus);
        timeBonus = timeResult[0];
        totalIncrement += timeResult[1];
        if (timeResult[1] > 0) {
            anyRemaining = true;
        }

        // Decrement ring bonus by 10 (Got_RingBonus: subi.w #10,(v_ringbonus).w)
        int[] ringResult = decrementBonus(ringBonus);
        ringBonus = ringResult[0];
        totalIncrement += ringResult[1];
        if (ringResult[1] > 0) {
            anyRemaining = true;
        }

        return tallyResult(anyRemaining, totalIncrement);
    }

    /**
     * Override tick sound to use Sonic 1 sfx_Switch (0xCD).
     * From Got_AddBonus: andi.b #3,d0 / bne.s locret / move.w #sfx_Switch,d0
     */
    @Override
    protected void playTickSound() {
        try {
            AudioManager.getInstance().playSfx(Sonic1AudioProfile.SFX_SWITCH);
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    /**
     * Override tally end sound to use Sonic 1 sfx_Cash (0xC5).
     * From Got_ChkBonus: move.w #sfx_Cash,d0 / jsr (QueueSound2).l
     */
    @Override
    protected void playTallyEndSound() {
        try {
            AudioManager.getInstance().playSfx(Sonic1AudioProfile.SFX_CASH);
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    @Override
    protected void onExitReady() {
        triggerFadeToBlack();
    }

    private void triggerFadeToBlack() {
        LOGGER.info("S1 Results screen complete, starting fade to black");

        // TODO: SBZ Act 2 special case - transition to Final Zone with camera scroll
        // instead of normal fade. From Got_NextLevel:
        //   cmpi.w #(id_SBZ<<8)+1,(v_zone).w => skip post-tally wait, play bgm_FZ,
        //   scroll camera right until (v_limitright2) == $2100

        FadeManager fadeManager = FadeManager.getInstance();
        fadeManager.startFadeToBlack(() -> {
            setDestroyed(true);
            LevelManager levelManager = LevelManager.getInstance();
            if (levelManager != null) {
                try {
                    levelManager.advanceToNextLevel();
                } catch (java.io.IOException e) {
                    LOGGER.severe("Failed to load next level: " + e.getMessage());
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        Camera camera = Camera.getInstance();
        if (camera == null) {
            return;
        }

        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager == null) {
            return;
        }

        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager != null) {
            PatternSpriteRenderer renderer = renderManager.getResultsRenderer();
            if (renderer != null) {
                appendArtRenderCommands(commands, camera, renderer, renderManager);
                return;
            }
        }

        // Fallback to placeholder rendering if ROM art is not available
        appendPlaceholderRenderCommands(commands, camera);
    }

    /**
     * Render using ROM art patterns via the PatternSpriteRenderer.
     */
    private void appendArtRenderCommands(List<GLCommand> commands, Camera camera,
            PatternSpriteRenderer renderer, ObjectRenderManager renderManager) {
        int worldBaseY = camera.getY();

        // Convert VDP X coordinates to world coordinates.
        // VDP X has +128 offset; screen X = VDP X - 128; world X = camera.getX() + screenX.
        // Since our elemCurrentX stores VDP coords, world X = camera.getX() + (vdpX - 128).
        int worldXBase = camera.getX() - 128;

        // Draw order: oval first (behind), then text elements on top.
        // On the VDP, earlier sprites in the link table render in front of later ones.
        // The oval is last in Got_Config (object index 6), so it renders behind everything.
        // Our renderer draws later calls on top, so we draw the oval first.

        // Oval (frame 5) - drawn first so it appears behind other elements
        renderer.drawFrameIndex(FRAME_OVAL,
                worldXBase + elemCurrentX[ELEM_OVAL],
                worldBaseY + ELEM_SCREEN_Y[ELEM_OVAL], false, false);

        // "SONIC HAS" (frame 0)
        renderer.drawFrameIndex(FRAME_SONIC_HAS,
                worldXBase + elemCurrentX[ELEM_SONIC_HAS],
                worldBaseY + ELEM_SCREEN_Y[ELEM_SONIC_HAS], false, false);

        // "PASSED" (frame 1)
        renderer.drawFrameIndex(FRAME_PASSED,
                worldXBase + elemCurrentX[ELEM_PASSED],
                worldBaseY + ELEM_SCREEN_Y[ELEM_PASSED], false, false);

        // "ACT N" (frame 6 + act offset; v_act is 0-indexed, actNumber is 1-indexed)
        int actFrame = FRAME_ACT_BASE + (actNumber - 1);
        renderer.drawFrameIndex(actFrame,
                worldXBase + elemCurrentX[ELEM_ACT],
                worldBaseY + ELEM_SCREEN_Y[ELEM_ACT], false, false);

        // Score separator dots (frame 9) - drawn before SCORE text so dots appear behind
        renderer.drawFrameIndex(FRAME_SCORE_DOTS,
                worldXBase + elemCurrentX[ELEM_SCORE],
                worldBaseY + ELEM_SCREEN_Y[ELEM_SCORE], false, false);

        // Score text + digits (frame 2)
        renderer.drawFrameIndex(FRAME_SCORE,
                worldXBase + elemCurrentX[ELEM_SCORE],
                worldBaseY + ELEM_SCREEN_Y[ELEM_SCORE], false, false);

        // Time Bonus (frame 3)
        renderer.drawFrameIndex(FRAME_TIME_BONUS,
                worldXBase + elemCurrentX[ELEM_TIME_BONUS],
                worldBaseY + ELEM_SCREEN_Y[ELEM_TIME_BONUS], false, false);

        // Ring Bonus (frame 4)
        renderer.drawFrameIndex(FRAME_RING_BONUS,
                worldXBase + elemCurrentX[ELEM_RING_BONUS],
                worldBaseY + ELEM_SCREEN_Y[ELEM_RING_BONUS], false, false);

        // Update bonus digit patterns
        updateBonusPatterns(renderManager);
    }

    private int lastTimeBonus = Integer.MIN_VALUE;
    private int lastRingBonus = Integer.MIN_VALUE;
    private final Pattern blankDigit = new Pattern();

    /**
     * Writes bonus digit patterns into the results screen composite pattern array.
     * <p>
     * In the original ROM, bonus digits are DMA'd into VRAM at $570 (time bonus)
     * and $578 (ring bonus). Each 4-digit value uses 8 tiles (4 digits x 2 tiles each,
     * column-major: top tile + bottom tile per digit).
     * <p>
     * Our composite array starts at VRAM $570, so:
     * - Time bonus digits: array indices 0-7
     * - Ring bonus digits: array indices 8-15
     */
    private void updateBonusPatterns(ObjectRenderManager renderManager) {
        PatternSpriteRenderer renderer = renderManager.getResultsRenderer();
        if (renderer == null) {
            return;
        }

        // HUD digit patterns: 0-9, each digit = 2 tiles (top/bottom)
        Pattern[] digitPatterns = renderManager.getResultsHudDigitPatterns();
        if (digitPatterns == null || digitPatterns.length < 20) {
            return;
        }

        // Skip update if values haven't changed
        if (timeBonus == lastTimeBonus && ringBonus == lastRingBonus) {
            return;
        }

        ObjectSpriteSheet resultsSheet = renderManager.getResultsSheet();
        if (resultsSheet == null) {
            return;
        }
        Pattern[] patterns = resultsSheet.getPatterns();
        if (patterns == null || patterns.length < Sonic1Constants.S1_RESULTS_BONUS_DIGIT_TILES) {
            return;
        }

        // Ensure writable patterns in digit slots
        for (int i = 0; i < Sonic1Constants.S1_RESULTS_BONUS_DIGIT_TILES; i++) {
            if (patterns[i] == null) {
                patterns[i] = new Pattern();
            }
        }

        // Write time bonus digits at index 0, ring bonus digits at index 8
        writeBonusValue(patterns, 0, timeBonus, digitPatterns);
        writeBonusValue(patterns, Sonic1Constants.S1_RESULTS_BONUS_DIGIT_GROUP_TILES, ringBonus, digitPatterns);

        // Push updated digit patterns to GPU
        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        renderer.updatePatternRange(graphicsManager, 0, Sonic1Constants.S1_RESULTS_BONUS_DIGIT_TILES);

        lastTimeBonus = timeBonus;
        lastRingBonus = ringBonus;
    }

    /**
     * Writes a 4-digit bonus value into the pattern array.
     * Each digit occupies 2 tiles (column-major: top + bottom).
     */
    private void writeBonusValue(Pattern[] dest, int startIndex, int value, Pattern[] digits) {
        int[] divisors = {1000, 100, 10, 1};
        boolean hasDigit = false;
        for (int i = 0; i < divisors.length; i++) {
            int divisor = divisors[i];
            int digit = value / divisor;
            value %= divisor;
            int tileIndex = startIndex + (i * 2);
            boolean isLastDigit = (i == divisors.length - 1);
            if (digit != 0 || hasDigit || isLastDigit) {
                hasDigit = true;
                copyDigit(dest, tileIndex, digit, digits);
            } else {
                dest[tileIndex].copyFrom(blankDigit);
                dest[tileIndex + 1].copyFrom(blankDigit);
            }
        }
    }

    private void copyDigit(Pattern[] dest, int destIndex, int digit, Pattern[] digits) {
        int srcIndex = digit * 2;
        if (srcIndex + 1 >= digits.length || destIndex + 1 >= dest.length) {
            return;
        }
        dest[destIndex].copyFrom(digits[srcIndex]);
        dest[destIndex + 1].copyFrom(digits[srcIndex + 1]);
    }

    /**
     * Fallback placeholder rendering when ROM art is not available.
     * Draws colored boxes at the correct slide positions.
     */
    private void appendPlaceholderRenderCommands(List<GLCommand> commands, Camera camera) {
        int worldBaseY = camera.getY();
        int worldXBase = camera.getX() - 128;

        // Convert VDP X to screen-relative for placeholder boxes
        // "SONIC HAS" - blue
        int sonicHasScreenX = elemCurrentX[ELEM_SONIC_HAS] - 128;
        if (sonicHasScreenX >= 0 && sonicHasScreenX < SCREEN_WIDTH + 100) {
            renderPlaceholderBox(commands,
                    camera.getX() + sonicHasScreenX,
                    worldBaseY + ELEM_SCREEN_Y[ELEM_SONIC_HAS],
                    80, 16, 0.2f, 0.6f, 1.0f);
        }

        // "PASSED" - blue
        int passedScreenX = elemCurrentX[ELEM_PASSED] - 128;
        if (passedScreenX >= -100 && passedScreenX < SCREEN_WIDTH + 100) {
            renderPlaceholderBox(commands,
                    camera.getX() + passedScreenX,
                    worldBaseY + ELEM_SCREEN_Y[ELEM_PASSED],
                    60, 16, 0.2f, 0.6f, 1.0f);
        }

        // "ACT N" - green
        int actScreenX = elemCurrentX[ELEM_ACT] - 128;
        if (actScreenX >= 0 && actScreenX < SCREEN_WIDTH + 100) {
            renderPlaceholderBox(commands,
                    camera.getX() + actScreenX,
                    worldBaseY + ELEM_SCREEN_Y[ELEM_ACT],
                    48, 16, 0.2f, 0.8f, 0.4f);
        }

        // Score - yellow
        int scoreScreenX = elemCurrentX[ELEM_SCORE] - 128;
        if (scoreScreenX >= 0 && scoreScreenX < SCREEN_WIDTH + 100) {
            renderPlaceholderBox(commands,
                    camera.getX() + scoreScreenX,
                    worldBaseY + ELEM_SCREEN_Y[ELEM_SCORE],
                    80, 12, 1.0f, 1.0f, 0.4f);
        }

        // Time Bonus - yellow
        int timeBonusScreenX = elemCurrentX[ELEM_TIME_BONUS] - 128;
        if (timeBonusScreenX >= 0 && timeBonusScreenX < SCREEN_WIDTH + 100) {
            renderPlaceholderBox(commands,
                    camera.getX() + timeBonusScreenX,
                    worldBaseY + ELEM_SCREEN_Y[ELEM_TIME_BONUS],
                    80, 12, 1.0f, 1.0f, 0.4f);
        }

        // Ring Bonus - orange
        int ringBonusScreenX = elemCurrentX[ELEM_RING_BONUS] - 128;
        if (ringBonusScreenX >= 0 && ringBonusScreenX < SCREEN_WIDTH + 100) {
            renderPlaceholderBox(commands,
                    camera.getX() + ringBonusScreenX,
                    worldBaseY + ELEM_SCREEN_Y[ELEM_RING_BONUS],
                    80, 12, 1.0f, 0.8f, 0.2f);
        }

        // Oval - grey
        int ovalScreenX = elemCurrentX[ELEM_OVAL] - 128;
        if (ovalScreenX >= 0 && ovalScreenX < SCREEN_WIDTH + 100) {
            renderPlaceholderBox(commands,
                    camera.getX() + ovalScreenX,
                    worldBaseY + ELEM_SCREEN_Y[ELEM_OVAL],
                    32, 24, 0.6f, 0.6f, 0.6f);
        }
    }

    // -----------------------------------------------------------------------
    // Accessors (for testing)
    // -----------------------------------------------------------------------

    public int getTimeBonus() {
        return timeBonus;
    }

    public int getRingBonus() {
        return ringBonus;
    }

    public int getActNumber() {
        return actNumber;
    }
}
