package uk.co.jamesj999.sonic.game.sonic1.specialstage;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.ResultsScreen;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;

import java.util.List;

/**
 * Sonic 1 special-stage results screen (Obj7E behavior).
 *
 * <p>Implements the three message scenarios:
 * 1) Failed to get emerald: "SPECIAL STAGE"
 * 2) Got an emerald: "CHAOS EMERALDS"
 * 3) Got all emeralds: "SONIC GOT THEM ALL"
 */
public final class Sonic1SpecialStageResultsScreen implements ResultsScreen {
    private enum Scenario {
        FAILED_TO_GET_EMERALD,
        GOT_CHAOS_EMERALD,
        GOT_ALL_CHAOS_EMERALDS
    }

    // Sonic 1 has six emeralds.
    private static final int SONIC1_CHAOS_EMERALD_COUNT = 6;

    // Obj7E sequencing/timing.
    private static final int STATE_SLIDE_IN = 0;
    private static final int STATE_PRE_TALLY_DELAY = 1;
    private static final int STATE_RING_TALLY = 2;
    private static final int STATE_POST_TALLY_DELAY = 3;
    private static final int SLIDE_SPEED_PIXELS_PER_FRAME = 16;
    private static final int PRE_TALLY_DELAY_FRAMES = 180;
    private static final int POST_TALLY_DELAY_FRAMES = 180;
    private static final int TALLY_DECREMENT = 10;
    private static final int TALLY_TICK_INTERVAL = 4;

    // Map_SSR frame indices (added to Sonic1ObjectArtProvider results mappings).
    private static final int FRAME_SCORE = 2;
    private static final int FRAME_RING_BONUS = 4;
    private static final int FRAME_OVAL = 5;
    private static final int FRAME_CHAOS_EMERALDS = 10;
    private static final int FRAME_SPECIAL_STAGE = 11;
    private static final int FRAME_GOT_THEM_ALL = 12;

    // Obj7E position config (VDP coordinates).
    private static final int TEXT_START_X = 0x20;
    private static final int TEXT_TARGET_X = 0x120;
    private static final int TEXT_GOT_ALL_START_X = 0x18;
    private static final int TEXT_GOT_ALL_TARGET_X = 0x118;
    private static final int SCORE_START_X = 0x320;
    private static final int SCORE_TARGET_X = 0x120;
    private static final int RING_START_X = 0x360;
    private static final int RING_TARGET_X = 0x120;
    private static final int OVAL_START_X = 0x1EC;
    private static final int OVAL_TARGET_X = 0x11C;
    private static final int TEXT_Y = 0xC4;
    private static final int SCORE_Y = 0x118;
    private static final int RING_Y = 0x128;
    private static final int OVAL_Y = 0xC4;

    // Obj7F emerald positions.
    private static final int[] EMERALD_X_POSITIONS = {0x110, 0x128, 0xF8, 0x140, 0xE0, 0x158};
    private static final int EMERALD_Y = 0xF0;
    private static final float[][] EMERALD_COLORS = {
            {0.25f, 0.55f, 1.0f},
            {1.0f, 0.95f, 0.25f},
            {1.0f, 0.45f, 0.65f},
            {0.30f, 0.95f, 0.35f},
            {1.0f, 0.70f, 0.25f},
            {0.80f, 0.45f, 1.0f}
    };

    // Score digit layout in the composite results sheet.
    private static final int SCORE_DIGITS_COUNT = 7;
    private static final int SCORE_DIGIT_TILES = SCORE_DIGITS_COUNT * 2;
    private static final int SCORE_DIGITS_START_INDEX =
            (Sonic1Constants.VRAM_RESULTS_HUD_TEXT + 0x1A) - Sonic1Constants.VRAM_RESULTS_BASE;

    private final Scenario scenario;
    private final int stageIndex;

    private int state = STATE_SLIDE_IN;
    private int stateTimer;
    private int totalFrames;
    private boolean complete;
    private int frameCounter;

    private int textX;
    private final int textTargetX;
    private int scoreX = SCORE_START_X;
    private int ringX = RING_START_X;
    private int ovalX = OVAL_START_X;

    private int ringBonus;

    private int lastRingBonus = Integer.MIN_VALUE;
    private int lastScoreValue = Integer.MIN_VALUE;
    private final Pattern blankDigit = new Pattern();

    public Sonic1SpecialStageResultsScreen(int ringsCollected, boolean gotEmerald,
            int stageIndex, int totalEmeraldCount) {
        this.stageIndex = stageIndex;
        this.ringBonus = Math.max(0, ringsCollected) * 10;
        if (!gotEmerald) {
            this.scenario = Scenario.FAILED_TO_GET_EMERALD;
            this.textX = TEXT_START_X;
            this.textTargetX = TEXT_TARGET_X;
        } else if (totalEmeraldCount >= SONIC1_CHAOS_EMERALD_COUNT) {
            this.scenario = Scenario.GOT_ALL_CHAOS_EMERALDS;
            this.textX = TEXT_GOT_ALL_START_X;
            this.textTargetX = TEXT_GOT_ALL_TARGET_X;
        } else {
            this.scenario = Scenario.GOT_CHAOS_EMERALD;
            this.textX = TEXT_START_X;
            this.textTargetX = TEXT_TARGET_X;
        }
    }

    @Override
    public void update(int frameCounter, Object context) {
        this.frameCounter = Math.max(this.frameCounter, frameCounter);
        if (complete) {
            return;
        }

        totalFrames++;
        stateTimer++;

        switch (state) {
            case STATE_SLIDE_IN -> updateSlideIn();
            case STATE_PRE_TALLY_DELAY -> updatePreTallyDelay();
            case STATE_RING_TALLY -> updateRingTally();
            case STATE_POST_TALLY_DELAY -> updatePostTallyDelay();
            default -> complete = true;
        }
    }

    private void updateSlideIn() {
        textX = moveTowards(textX, textTargetX, SLIDE_SPEED_PIXELS_PER_FRAME);
        scoreX = moveTowards(scoreX, SCORE_TARGET_X, SLIDE_SPEED_PIXELS_PER_FRAME);
        ringX = moveTowards(ringX, RING_TARGET_X, SLIDE_SPEED_PIXELS_PER_FRAME);
        ovalX = moveTowards(ovalX, OVAL_TARGET_X, SLIDE_SPEED_PIXELS_PER_FRAME);

        // Obj7E advances when the ring bonus row reaches target.
        if (ringX == RING_TARGET_X) {
            state = STATE_PRE_TALLY_DELAY;
            stateTimer = 0;
        }
    }

    private void updatePreTallyDelay() {
        if (stateTimer >= PRE_TALLY_DELAY_FRAMES) {
            state = STATE_RING_TALLY;
            stateTimer = 0;
        }
    }

    private void updateRingTally() {
        if (ringBonus <= 0) {
            playTallyEndSound();
            state = STATE_POST_TALLY_DELAY;
            stateTimer = 0;
            return;
        }

        int decrement = Math.min(TALLY_DECREMENT, ringBonus);
        ringBonus -= decrement;
        GameServices.gameState().addScore(decrement);

        if ((stateTimer % TALLY_TICK_INTERVAL) == 0) {
            playTickSound();
        }
    }

    private void updatePostTallyDelay() {
        if (stateTimer >= POST_TALLY_DELAY_FRAMES) {
            complete = true;
        }
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        Camera camera = Camera.getInstance();
        if (camera == null) {
            return;
        }

        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager == null) {
            appendPlaceholder(commands, camera);
            return;
        }

        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            appendPlaceholder(commands, camera);
            return;
        }

        PatternSpriteRenderer renderer = renderManager.getResultsRenderer();
        if (renderer == null) {
            appendPlaceholder(commands, camera);
            return;
        }

        updateDynamicNumberPatterns(renderManager);

        // Draw oval first so other text appears on top.
        renderer.drawFrameIndex(FRAME_OVAL,
                toWorldX(ovalX, camera), toWorldY(OVAL_Y, camera), false, false);

        renderer.drawFrameIndex(getScenarioFrameIndex(),
                toWorldX(textX, camera), toWorldY(TEXT_Y, camera), false, false);

        renderer.drawFrameIndex(FRAME_SCORE,
                toWorldX(scoreX, camera), toWorldY(SCORE_Y, camera), false, false);

        renderer.drawFrameIndex(FRAME_RING_BONUS,
                toWorldX(ringX, camera), toWorldY(RING_Y, camera), false, false);

        appendEmeraldIndicators(commands, camera);

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager != null) {
            graphicsManager.flushPatternBatch();
        }
    }

    private void appendPlaceholder(List<GLCommand> commands, Camera camera) {
        int messageX = toWorldX(textX, camera);
        int messageY = toWorldY(TEXT_Y, camera);
        int scoreXWorld = toWorldX(scoreX, camera);
        int scoreYWorld = toWorldY(SCORE_Y, camera);
        int ringXWorld = toWorldX(ringX, camera);
        int ringYWorld = toWorldY(RING_Y, camera);

        renderPlaceholderBox(commands, messageX - 70, messageY - 8, 140, 16, 0.2f, 0.75f, 1.0f);
        renderPlaceholderBox(commands, scoreXWorld - 80, scoreYWorld - 8, 160, 16, 1.0f, 0.95f, 0.35f);
        renderPlaceholderBox(commands, ringXWorld - 80, ringYWorld - 8, 160, 16, 1.0f, 0.7f, 0.25f);
        renderPlaceholderBox(commands, toWorldX(ovalX, camera) - 20, toWorldY(OVAL_Y, camera) - 20,
                40, 40, 0.55f, 0.7f, 1.0f);
        appendEmeraldIndicators(commands, camera);
    }

    private void appendEmeraldIndicators(List<GLCommand> commands, Camera camera) {
        if ((totalFrames & 1) == 0) {
            return;
        }

        int emeraldCount = Math.min(SONIC1_CHAOS_EMERALD_COUNT, GameServices.gameState().getEmeraldCount());
        for (int i = 0; i < emeraldCount; i++) {
            int worldX = toWorldX(EMERALD_X_POSITIONS[i], camera);
            int worldY = toWorldY(EMERALD_Y, camera);
            float[] color = EMERALD_COLORS[i];
            renderPlaceholderBox(commands, worldX - 8, worldY - 8, 16, 16, color[0], color[1], color[2]);
        }
    }

    private void updateDynamicNumberPatterns(ObjectRenderManager renderManager) {
        PatternSpriteRenderer renderer = renderManager.getResultsRenderer();
        if (renderer == null) {
            return;
        }

        Pattern[] digitPatterns = renderManager.getResultsHudDigitPatterns();
        if (digitPatterns == null || digitPatterns.length < 20) {
            return;
        }

        ObjectSpriteSheet resultsSheet = renderManager.getResultsSheet();
        if (resultsSheet == null) {
            return;
        }

        Pattern[] patterns = resultsSheet.getPatterns();
        if (patterns == null) {
            return;
        }

        int score = Math.max(0, GameServices.gameState().getScore());
        if (ringBonus == lastRingBonus && score == lastScoreValue) {
            return;
        }

        // Ring bonus digits live at tile slots 8-15 in the composite sheet.
        int ringDigitStart = Sonic1Constants.S1_RESULTS_BONUS_DIGIT_GROUP_TILES;
        if (ringDigitStart + Sonic1Constants.S1_RESULTS_BONUS_DIGIT_GROUP_TILES > patterns.length) {
            return;
        }
        ensurePatternSlots(patterns, ringDigitStart, Sonic1Constants.S1_RESULTS_BONUS_DIGIT_GROUP_TILES);
        writeBonusValue(patterns, ringDigitStart, ringBonus, digitPatterns);

        // Score digits live in the HUD text range.
        if (SCORE_DIGITS_START_INDEX + SCORE_DIGIT_TILES <= patterns.length) {
            ensurePatternSlots(patterns, SCORE_DIGITS_START_INDEX, SCORE_DIGIT_TILES);
            writeScoreValue(patterns, score, digitPatterns);
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager != null) {
            renderer.updatePatternRange(graphicsManager, ringDigitStart,
                    Sonic1Constants.S1_RESULTS_BONUS_DIGIT_GROUP_TILES);
            if (SCORE_DIGITS_START_INDEX + SCORE_DIGIT_TILES <= patterns.length) {
                renderer.updatePatternRange(graphicsManager, SCORE_DIGITS_START_INDEX, SCORE_DIGIT_TILES);
            }
        }

        lastRingBonus = ringBonus;
        lastScoreValue = score;
    }

    private void ensurePatternSlots(Pattern[] patterns, int start, int count) {
        int end = Math.min(patterns.length, start + count);
        for (int i = Math.max(0, start); i < end; i++) {
            if (patterns[i] == null) {
                patterns[i] = new Pattern();
            }
        }
    }

    private void writeBonusValue(Pattern[] destination, int startIndex, int value, Pattern[] digits) {
        int[] divisors = {1000, 100, 10, 1};
        boolean hasDigit = false;

        for (int i = 0; i < divisors.length; i++) {
            int divisor = divisors[i];
            int digit = value / divisor;
            value %= divisor;
            int tileIndex = startIndex + (i * 2);
            boolean isLastDigit = i == divisors.length - 1;
            if (digit != 0 || hasDigit || isLastDigit) {
                hasDigit = true;
                copyDigit(destination, tileIndex, digit, digits);
            } else {
                destination[tileIndex].copyFrom(blankDigit);
                destination[tileIndex + 1].copyFrom(blankDigit);
            }
        }
    }

    private void writeScoreValue(Pattern[] destination, int score, Pattern[] digits) {
        int clampedScore = Math.min(score, 9_999_999);
        int divisor = 1_000_000;
        boolean hasDigit = false;
        for (int i = 0; i < SCORE_DIGITS_COUNT; i++) {
            int digit = (clampedScore / divisor) % 10;
            int tileIndex = SCORE_DIGITS_START_INDEX + (i * 2);
            boolean isLastDigit = i == SCORE_DIGITS_COUNT - 1;
            if (digit != 0 || hasDigit || isLastDigit) {
                hasDigit = true;
                copyDigit(destination, tileIndex, digit, digits);
            } else {
                destination[tileIndex].copyFrom(blankDigit);
                destination[tileIndex + 1].copyFrom(blankDigit);
            }
            divisor /= 10;
        }
    }

    private void copyDigit(Pattern[] destination, int destinationIndex, int digit, Pattern[] digits) {
        int sourceIndex = digit * 2;
        if (sourceIndex + 1 >= digits.length || destinationIndex + 1 >= destination.length) {
            return;
        }
        destination[destinationIndex].copyFrom(digits[sourceIndex]);
        destination[destinationIndex + 1].copyFrom(digits[sourceIndex + 1]);
    }

    private int toWorldX(int vdpX, Camera camera) {
        return camera.getX() + (vdpX - 128);
    }

    private int toWorldY(int vdpY, Camera camera) {
        return camera.getY() + (vdpY - 128);
    }

    private int moveTowards(int current, int target, int speed) {
        if (current == target) {
            return current;
        }
        if (current < target) {
            return Math.min(target, current + speed);
        }
        return Math.max(target, current - speed);
    }

    private void playTickSound() {
        try {
            AudioManager.getInstance().playSfx(Sonic1Sfx.SWITCH.id);
        } catch (Exception ignored) {
            // Audio failure should not affect results flow.
        }
    }

    private void playTallyEndSound() {
        try {
            AudioManager.getInstance().playSfx(Sonic1Sfx.TALLY.id);
        } catch (Exception ignored) {
            // Audio failure should not affect results flow.
        }
    }

    private void renderPlaceholderBox(List<GLCommand> commands, int x, int y, int width, int height,
            float r, float g, float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x, y, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x + width, y, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x + width, y, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x + width, y + height, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x + width, y + height, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x, y + height, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x, y + height, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x, y, 0, 0));
    }

    private int getScenarioFrameIndex() {
        return switch (scenario) {
            case FAILED_TO_GET_EMERALD -> FRAME_SPECIAL_STAGE;
            case GOT_CHAOS_EMERALD -> FRAME_CHAOS_EMERALDS;
            case GOT_ALL_CHAOS_EMERALDS -> FRAME_GOT_THEM_ALL;
        };
    }

    // Package-private accessors for tests.
    int getState() {
        return state;
    }

    int getRingBonus() {
        return ringBonus;
    }

    int getScenarioFrameForTesting() {
        return getScenarioFrameIndex();
    }

    int getStageIndex() {
        return stageIndex;
    }

    int getObservedFrameCounter() {
        return frameCounter;
    }
}
