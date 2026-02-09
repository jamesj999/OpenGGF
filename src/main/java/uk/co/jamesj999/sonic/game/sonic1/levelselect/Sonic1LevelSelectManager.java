package uk.co.jamesj999.sonic.game.sonic1.levelselect;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.Control.InputHandler;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.LevelSelectProvider;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Music;
import uk.co.jamesj999.sonic.game.sonic1.titlescreen.Sonic1TitleScreenManager;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.level.PatternDesc;

import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.glClearColor;

import static uk.co.jamesj999.sonic.game.sonic1.levelselect.Sonic1LevelSelectConstants.*;

/**
 * Manages the Sonic 1 Level Select screen.
 *
 * <p>The Sonic 1 level select is a simple vertical list of 21 items:
 * 6 zones with 3 acts each, Final Zone, Special Stage, and Sound Select.
 *
 * <p>Unlike Sonic 2, there are no zone preview icons and no two-column layout.
 * Navigation wraps vertically. Left/right only affects the sound test value.
 *
 * <p>State machine:
 * <pre>
 * INACTIVE -> FADE_IN -> ACTIVE -> EXITING
 * </pre>
 */
public class Sonic1LevelSelectManager implements LevelSelectProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic1LevelSelectManager.class.getName());

    private static Sonic1LevelSelectManager instance;

    private final SonicConfigurationService configService = SonicConfigurationService.getInstance();
    private final Sonic1LevelSelectDataLoader dataLoader = new Sonic1LevelSelectDataLoader();
    private final PatternDesc reusableDesc = new PatternDesc();

    private State state = State.INACTIVE;
    private int selectedIndex = 0;      // Menu selection (0-20)
    private int soundTestValue = 0;     // Sound test internal value (0-31)

    // Input handling
    private int upHoldTimer = 0;
    private int downHoldTimer = 0;
    private int leftHoldTimer = 0;
    private int rightHoldTimer = 0;

    // Fade timing
    private int fadeTimer = 0;
    private static final int FADE_DURATION = 16;

    private Sonic1LevelSelectManager() {
    }

    public static synchronized Sonic1LevelSelectManager getInstance() {
        if (instance == null) {
            instance = new Sonic1LevelSelectManager();
        }
        return instance;
    }

    @Override
    public void initialize() {
        LOGGER.info("Initializing Sonic 1 level select screen");

        if (!dataLoader.isDataLoaded()) {
            dataLoader.loadData();
        }

        // Force palette re-upload on next draw
        dataLoader.resetCache();

        // Reset state
        state = State.FADE_IN;
        fadeTimer = 0;
        selectedIndex = 0;
        soundTestValue = 0;
        resetHoldTimers();

        // Play title screen music (Sonic 1 uses title music for level select)
        AudioManager.getInstance().playMusic(Sonic1Music.TITLE.id);

        LOGGER.info("Sonic 1 level select initialized, entering FADE_IN state");
    }

    /**
     * Initializes the level select when transitioning from the title screen.
     * Unlike {@link #initialize()}, this does not restart the title music
     * (it continues playing) and skips the fade-in (immediate display).
     * This matches the original Sonic 1 behaviour where pressing Start+A on
     * the title screen immediately shows the level select with no fade and
     * no music interruption.
     */
    @Override
    public void initializeFromTitleScreen() {
        LOGGER.info("Initializing Sonic 1 level select from title screen (no music restart)");

        if (!dataLoader.isDataLoaded()) {
            dataLoader.loadData();
        }

        // Force palette re-upload on next draw
        dataLoader.resetCache();

        // Go straight to ACTIVE - no fade-in (matches original: immediate display)
        state = State.ACTIVE;
        fadeTimer = 0;
        selectedIndex = 0;
        soundTestValue = 0;
        resetHoldTimers();

        // Do NOT play music - title music continues from the title screen
        LOGGER.info("Sonic 1 level select initialized from title screen, entering ACTIVE state");
    }

    @Override
    public void update(InputHandler input) {
        switch (state) {
            case FADE_IN -> updateFadeIn();
            case ACTIVE -> updateActive(input);
            case INACTIVE, EXITING -> { }
        }
    }

    private void updateFadeIn() {
        fadeTimer++;
        if (fadeTimer >= FADE_DURATION) {
            state = State.ACTIVE;
            LOGGER.fine("Sonic 1 level select entered ACTIVE state");
        }
    }

    private void updateActive(InputHandler input) {
        int upKey = configService.getInt(SonicConfiguration.UP);
        int downKey = configService.getInt(SonicConfiguration.DOWN);
        int leftKey = configService.getInt(SonicConfiguration.LEFT);
        int rightKey = configService.getInt(SonicConfiguration.RIGHT);
        int jumpKey = configService.getInt(SonicConfiguration.JUMP);

        // Handle up/down navigation
        if (input.isKeyDown(upKey)) {
            if (upHoldTimer == 0 || (upHoldTimer >= HOLD_REPEAT_DELAY && upHoldTimer % HOLD_REPEAT_RATE == 0)) {
                moveUp();
            }
            upHoldTimer++;
        } else {
            upHoldTimer = 0;
        }

        if (input.isKeyDown(downKey)) {
            if (downHoldTimer == 0 || (downHoldTimer >= HOLD_REPEAT_DELAY && downHoldTimer % HOLD_REPEAT_RATE == 0)) {
                moveDown();
            }
            downHoldTimer++;
        } else {
            downHoldTimer = 0;
        }

        // Handle left/right (sound test value adjustment only)
        if (input.isKeyDown(leftKey)) {
            if (leftHoldTimer == 0 || (leftHoldTimer >= HOLD_REPEAT_DELAY && leftHoldTimer % HOLD_REPEAT_RATE == 0)) {
                if (selectedIndex == MENU_ENTRY_COUNT - 1) {
                    soundTestValue--;
                    if (soundTestValue < SOUND_TEST_MIN) {
                        soundTestValue = SOUND_TEST_MAX;
                    }
                }
            }
            leftHoldTimer++;
        } else {
            leftHoldTimer = 0;
        }

        if (input.isKeyDown(rightKey)) {
            if (rightHoldTimer == 0 || (rightHoldTimer >= HOLD_REPEAT_DELAY && rightHoldTimer % HOLD_REPEAT_RATE == 0)) {
                if (selectedIndex == MENU_ENTRY_COUNT - 1) {
                    soundTestValue++;
                    if (soundTestValue > SOUND_TEST_MAX) {
                        soundTestValue = SOUND_TEST_MIN;
                    }
                }
            }
            rightHoldTimer++;
        } else {
            rightHoldTimer = 0;
        }

        // Handle start/jump to select
        if (input.isKeyPressed(jumpKey)) {
            handleSelect();
        }
    }

    private void moveUp() {
        selectedIndex--;
        if (selectedIndex < 0) {
            selectedIndex = MENU_ENTRY_COUNT - 1;
        }
    }

    private void moveDown() {
        selectedIndex++;
        if (selectedIndex >= MENU_ENTRY_COUNT) {
            selectedIndex = 0;
        }
    }

    private void handleSelect() {
        int zoneAct = LEVEL_ORDER[selectedIndex];

        if (zoneAct == SOUND_TEST_VALUE) {
            // Sound test - play the selected sound
            int soundId = soundTestValue + SOUND_TEST_OFFSET;
            LOGGER.info("Playing sound test: 0x" + Integer.toHexString(soundId));
            // Sonic 1 sound test plays values 0x80-0x9F which covers both music and SFX
            if (soundId >= 0x80 && soundId <= 0x93) {
                // Music range
                AudioManager.getInstance().playMusic(soundId);
            } else {
                // SFX range
                AudioManager.getInstance().playSfx(soundId);
            }
        } else {
            // Signal exit - GameLoop will handle the fade
            state = State.EXITING;
            LOGGER.info("Sonic 1 level select exiting for selection: " + selectedIndex);
        }
    }

    private void resetHoldTimers() {
        upHoldTimer = 0;
        downHoldTimer = 0;
        leftHoldTimer = 0;
        rightHoldTimer = 0;
    }

    @Override
    public void draw() {
        if (!dataLoader.isDataLoaded()) {
            dataLoader.loadData();
        }
        // Upload level select palettes FIRST (before rendering frozen title art)
        // This ensures the title screen art appears with the brown/sepia tint
        // from Pal_LevelSel, matching the original hardware behaviour where
        // changing CRAM immediately affects all displayed tiles.
        dataLoader.cacheToGpu();

        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }

        // Render frozen title screen art behind the level select text.
        // The level select palette is already active on the GPU, so the
        // foreground logo and Sonic sprite appear brown/sepia.
        Sonic1TitleScreenManager titleScreen = Sonic1TitleScreenManager.getInstance();
        if (titleScreen.supportsLevelSelectOverlay()) {
            titleScreen.drawFrozenForLevelSelect();
        }

        gm.beginPatternBatch();

        // Draw all menu text lines
        drawMenuText(gm);

        // Draw sound test value
        drawSoundTestValue(gm);

        gm.flushPatternBatch();

        // Apply fade effect
        float fadeAmount = 0.0f;
        if (state == State.FADE_IN) {
            fadeAmount = 1.0f - (float) fadeTimer / FADE_DURATION;
        }

        if (fadeAmount > 0.0f) {
            gm.registerCommand(new GLCommand(
                    GLCommand.CommandType.RECTI,
                    -1,
                    GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                    0.0f, 0.0f, 0.0f, fadeAmount,
                    0, 0, SCREEN_WIDTH, SCREEN_HEIGHT
            ));
        }
    }

    /**
     * Draws all 21 menu text lines.
     * Selected line uses highlight palette, others use normal palette.
     */
    private void drawMenuText(GraphicsManager gm) {
        for (int line = 0; line < MENU_ENTRY_COUNT; line++) {
            int paletteIndex = (line == selectedIndex) ? HIGHLIGHT_PALETTE_INDEX : NORMAL_PALETTE_INDEX;
            int y = TEXT_START_Y + line * LINE_SPACING;
            drawTextLine(gm, MENU_TEXT[line], TEXT_START_X, y, paletteIndex);
        }
    }

    /**
     * Draws a single line of text using the loaded font patterns.
     *
     * @param gm           Graphics manager
     * @param text         Text to draw (24 characters)
     * @param x            X position
     * @param y            Y position
     * @param paletteIndex Palette line (2=highlight, 3=normal)
     */
    private void drawTextLine(GraphicsManager gm, String text, int x, int y, int paletteIndex) {
        int charX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            int charIndex = Sonic1LevelSelectDataLoader.getCharacterTileIndex(c);
            if (charIndex >= 0) {
                int patternId = PATTERN_BASE + charIndex;
                reusableDesc.set(charIndex | (paletteIndex << 13));
                gm.renderPatternWithId(patternId, reusableDesc, charX, y);
            }
            charX += 8;
        }
    }

    /**
     * Draws the sound test value as a 2-digit hex number.
     * Display format: 0x80-0x9F (internal value + 0x80 offset).
     */
    private void drawSoundTestValue(GraphicsManager gm) {
        int displayValue = soundTestValue + SOUND_TEST_OFFSET;
        int highNibble = (displayValue >> 4) & 0xF;
        int lowNibble = displayValue & 0xF;

        int paletteIndex = (selectedIndex == MENU_ENTRY_COUNT - 1)
                ? HIGHLIGHT_PALETTE_INDEX : NORMAL_PALETTE_INDEX;

        drawHexDigit(gm, highNibble, SOUND_TEST_X, SOUND_TEST_Y, paletteIndex);
        drawHexDigit(gm, lowNibble, SOUND_TEST_X + 8, SOUND_TEST_Y, paletteIndex);
    }

    /**
     * Draws a single hex digit (0-F) using the font.
     */
    private void drawHexDigit(GraphicsManager gm, int digit, int x, int y, int paletteIndex) {
        int charIndex = Sonic1LevelSelectDataLoader.getHexDigitTileIndex(digit);
        int patternId = PATTERN_BASE + charIndex;
        reusableDesc.set(charIndex | (paletteIndex << 13));
        gm.renderPatternWithId(patternId, reusableDesc, x, y);
    }

    @Override
    public void setClearColor() {
        if (!dataLoader.isDataLoaded()) {
            dataLoader.loadData();
        }
        Palette pal0 = dataLoader.getPalette(0);
        if (pal0 != null) {
            Palette.Color backdrop = pal0.getColor(0);
            glClearColor(backdrop.rFloat(), backdrop.gFloat(), backdrop.bFloat(), 1.0f);
        } else {
            // Fallback: black
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        }
    }

    @Override
    public State getState() {
        return state;
    }

    @Override
    public boolean isExiting() {
        return state == State.EXITING;
    }

    @Override
    public boolean isActive() {
        return state != State.INACTIVE;
    }

    @Override
    public boolean isSpecialStageSelected() {
        return LEVEL_ORDER[selectedIndex] == SPECIAL_STAGE_VALUE;
    }

    @Override
    public boolean isSoundTestSelected() {
        return LEVEL_ORDER[selectedIndex] == SOUND_TEST_VALUE;
    }

    @Override
    public int getSelectedZone() {
        int zoneAct = LEVEL_ORDER[selectedIndex];
        if (zoneAct == SPECIAL_STAGE_VALUE || zoneAct == SOUND_TEST_VALUE) {
            return -1;
        }
        return (zoneAct >> 8) & 0xFF;
    }

    @Override
    public int getSelectedAct() {
        int zoneAct = LEVEL_ORDER[selectedIndex];
        if (zoneAct == SPECIAL_STAGE_VALUE || zoneAct == SOUND_TEST_VALUE) {
            return -1;
        }
        return zoneAct & 0xFF;
    }

    @Override
    public int getSelectedZoneAct() {
        return LEVEL_ORDER[selectedIndex];
    }

    @Override
    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public int getSoundTestValue() {
        return soundTestValue;
    }

    @Override
    public void reset() {
        state = State.INACTIVE;
        selectedIndex = 0;
        soundTestValue = 0;
        fadeTimer = 0;
        resetHoldTimers();
        LOGGER.info("Sonic 1 level select reset to inactive");
    }
}
