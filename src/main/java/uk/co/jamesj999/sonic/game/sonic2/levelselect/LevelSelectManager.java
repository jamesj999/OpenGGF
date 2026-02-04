package uk.co.jamesj999.sonic.game.sonic2.levelselect;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.Control.InputHandler;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2AudioConstants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.level.PatternDesc;

import java.util.logging.Logger;

import static uk.co.jamesj999.sonic.game.sonic2.levelselect.LevelSelectConstants.*;

/**
 * Manages the Sonic 2 Level Select screen.
 *
 * <p>The level select screen allows selecting any zone/act, special stage,
 * or sound test mode. It loads graphics directly from the ROM and replicates
 * the original menu layout.
 *
 * <p>State machine:
 * <pre>
 * INACTIVE → FADE_IN → ACTIVE → FADE_OUT → EXITING
 * </pre>
 */
public class LevelSelectManager {
    private static final Logger LOGGER = Logger.getLogger(LevelSelectManager.class.getName());

    private static LevelSelectManager instance;

    /**
     * State machine for level select screen.
     */
    public enum State {
        /** Screen is not active */
        INACTIVE,
        /** Fading in from black */
        FADE_IN,
        /** Main interactive state */
        ACTIVE,
        /** Fading out to load level */
        FADE_OUT,
        /** Ready to exit and load selected level */
        EXITING
    }

    private final SonicConfigurationService configService = SonicConfigurationService.getInstance();
    private final LevelSelectDataLoader dataLoader = new LevelSelectDataLoader();

    private State state = State.INACTIVE;
    private int selectedIndex = 0;      // Menu selection (0-21)
    private int soundTestValue = 0;     // Sound test value (0x00-0x7F)

    // Input handling
    private int upHoldTimer = 0;
    private int downHoldTimer = 0;
    private int leftHoldTimer = 0;
    private int rightHoldTimer = 0;

    // Fade timing
    private int fadeTimer = 0;
    private static final int FADE_DURATION = 16;

    // Background color (blue like original)
    private static final float BG_R = 0.0f;
    private static final float BG_G = 0.0f;
    private static final float BG_B = 0.5f;

    private LevelSelectManager() {
    }

    public static synchronized LevelSelectManager getInstance() {
        if (instance == null) {
            instance = new LevelSelectManager();
        }
        return instance;
    }

    /**
     * Initializes the level select screen.
     * Loads data from ROM and sets state to FADE_IN.
     */
    public void initialize() {
        LOGGER.info("Initializing level select screen");

        // Load data if not already loaded
        if (!dataLoader.isDataLoaded()) {
            dataLoader.loadData();
        }

        // Reset state
        state = State.FADE_IN;
        fadeTimer = 0;
        selectedIndex = 0;
        soundTestValue = 0;
        resetHoldTimers();

        // Play level select music
        AudioManager.getInstance().playMusic(Sonic2AudioConstants.MUS_OPTIONS);

        LOGGER.info("Level select initialized, entering FADE_IN state");
    }

    /**
     * Updates the level select state machine.
     *
     * @param input Input handler for keyboard input
     */
    public void update(InputHandler input) {
        switch (state) {
            case FADE_IN -> updateFadeIn();
            case ACTIVE -> updateActive(input);
            case FADE_OUT -> updateFadeOut();
            case INACTIVE, EXITING -> { }
        }
    }

    private void updateFadeIn() {
        fadeTimer++;
        if (fadeTimer >= FADE_DURATION) {
            state = State.ACTIVE;
            LOGGER.fine("Level select entered ACTIVE state");
        }
    }

    private void updateActive(InputHandler input) {
        // Navigation keys
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

        // Handle left/right (column switch or sound test adjustment)
        if (input.isKeyDown(leftKey)) {
            if (leftHoldTimer == 0 || (leftHoldTimer >= HOLD_REPEAT_DELAY && leftHoldTimer % HOLD_REPEAT_RATE == 0)) {
                moveLeft();
            }
            leftHoldTimer++;
        } else {
            leftHoldTimer = 0;
        }

        if (input.isKeyDown(rightKey)) {
            if (rightHoldTimer == 0 || (rightHoldTimer >= HOLD_REPEAT_DELAY && rightHoldTimer % HOLD_REPEAT_RATE == 0)) {
                moveRight();
            }
            rightHoldTimer++;
        } else {
            rightHoldTimer = 0;
        }

        // Handle start/jump to select
        if (input.isKeyPressed(jumpKey)) {
            handleSelect();
        }

        // A button adds 16 to sound test value (like original game)
        // We'll use the 'A' key for this
        if (input.isKeyPressed(configService.getInt(SonicConfiguration.TEST))) {
            if (selectedIndex == MENU_ENTRY_COUNT - 1) { // Sound test
                soundTestValue = (soundTestValue + 16) & 0x7F;
                AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_RING_LEFT);
            }
        }
    }

    private void updateFadeOut() {
        fadeTimer++;
        if (fadeTimer >= FADE_DURATION) {
            state = State.EXITING;
            LOGGER.info("Level select fade out complete, ready to exit");
        }
    }

    private void moveUp() {
        selectedIndex--;
        if (selectedIndex < 0) {
            selectedIndex = MENU_ENTRY_COUNT - 1;
        }
        AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_RING_LEFT);
        LOGGER.fine("Level select moved up to index " + selectedIndex);
    }

    private void moveDown() {
        selectedIndex++;
        if (selectedIndex >= MENU_ENTRY_COUNT) {
            selectedIndex = 0;
        }
        AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_RING_LEFT);
        LOGGER.fine("Level select moved down to index " + selectedIndex);
    }

    private void moveLeft() {
        if (selectedIndex == MENU_ENTRY_COUNT - 1) {
            // Sound test mode - decrease value
            soundTestValue--;
            if (soundTestValue < 0) {
                soundTestValue = 0x7F;
            }
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_RING_LEFT);
        } else {
            // Switch columns
            selectedIndex = SWITCH_TABLE[selectedIndex];
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_RING_LEFT);
        }
        LOGGER.fine("Level select moved left to index " + selectedIndex);
    }

    private void moveRight() {
        if (selectedIndex == MENU_ENTRY_COUNT - 1) {
            // Sound test mode - increase value
            soundTestValue++;
            if (soundTestValue > 0x7F) {
                soundTestValue = 0;
            }
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_RING_LEFT);
        } else {
            // Switch columns
            selectedIndex = SWITCH_TABLE[selectedIndex];
            AudioManager.getInstance().playSfx(Sonic2AudioConstants.SFX_RING_LEFT);
        }
        LOGGER.fine("Level select moved right to index " + selectedIndex);
    }

    private void handleSelect() {
        int zoneAct = LEVEL_ORDER[selectedIndex];

        if (zoneAct == SOUND_TEST_VALUE) {
            // Sound test - play the selected sound
            LOGGER.info("Playing sound test: 0x" + Integer.toHexString(soundTestValue));
            if (soundTestValue < 0x20) {
                // Music
                AudioManager.getInstance().playMusic(soundTestValue);
            } else {
                // SFX
                AudioManager.getInstance().playSfx(soundTestValue);
            }
        } else {
            // Start fade out for level/special stage
            state = State.FADE_OUT;
            fadeTimer = 0;
            AudioManager.getInstance().fadeOutMusic();
            LOGGER.info("Level select starting fade out for selection: " + selectedIndex);
        }
    }

    private void resetHoldTimers() {
        upHoldTimer = 0;
        downHoldTimer = 0;
        leftHoldTimer = 0;
        rightHoldTimer = 0;
    }

    /**
     * Renders the level select screen.
     */
    public void draw() {
        // Ensure data is loaded and cached
        if (!dataLoader.isDataLoaded()) {
            dataLoader.loadData();
        }
        dataLoader.cacheToGpu();

        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }

        // Draw background color
        gm.registerCommand(new GLCommand(
                GLCommand.CommandType.RECTI,
                -1,
                BG_R, BG_G, BG_B,
                0, 0, SCREEN_WIDTH, SCREEN_HEIGHT
        ));

        // Draw the zone icon preview first (uses its own palette on line 3)
        gm.beginPatternBatch();
        drawZoneIcon(gm);
        gm.flushPatternBatch();

        // Restore menu palettes for text rendering (skip line 1 which has icon palette)
        int[] textPaletteLines = {0, 2, 3};
        for (int i : textPaletteLines) {
            Palette menuPal = dataLoader.getMenuPalette(i);
            if (menuPal != null) {
                gm.cachePaletteTexture(menuPal, i);
            }
        }

        // Draw the menu entries (uses menu palettes 0 and 3)
        gm.beginPatternBatch();
        drawMenuEntries(gm);

        // Draw the selection highlight
        drawSelectionHighlight(gm);

        // Draw sound test value if on sound test
        if (selectedIndex == MENU_ENTRY_COUNT - 1) {
            drawSoundTestValue(gm);
        }

        gm.flushPatternBatch();

        // Apply fade effect
        float fadeAmount = 0.0f;
        if (state == State.FADE_IN) {
            fadeAmount = 1.0f - (float) fadeTimer / FADE_DURATION;
        } else if (state == State.FADE_OUT) {
            fadeAmount = (float) fadeTimer / FADE_DURATION;
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
     * Draws the menu entries as text.
     *
     * <p>Layout matches the original game:
     * <ul>
     *   <li>Left column: Zone names with act numbers (1, 2) stacked vertically</li>
     *   <li>Right column: Metropolis (3 acts), single-act zones, Special Stage, Sound Test</li>
     * </ul>
     *
     * <p>Positions derived from MARK_TABLE (col values are col*2 byte offsets):
     * <ul>
     *   <li>col=6 → X=24 (left zone names)</li>
     *   <li>col=0x24 → X=144 (left act numbers)</li>
     *   <li>col=0x2C → X=176 (right zone names)</li>
     *   <li>col=0x48 → X=288 (right act numbers)</li>
     * </ul>
     */
    private void drawMenuEntries(GraphicsManager gm) {
        // Left column zone names (drawn once per zone)
        // From MARK_TABLE: lines are 3, 6, 9, 12, 15, 18, 21 (multiply by 8 for pixels)
        String[] leftZoneNames = {"EMERALD HILL", "CHEMICAL PLANT", "AQUATIC RUIN",
                "CASINO NIGHT", "HILL TOP", "MYSTIC CAVE", "OIL OCEAN"};
        int[] leftZoneLines = {3, 6, 9, 12, 15, 18, 21};
        int leftZoneX = 24;   // col=6 → tile 3 → X=24
        int leftActX = 144;   // col=0x24 → tile 18 → X=144

        for (int zone = 0; zone < 7; zone++) {
            int zoneY = leftZoneLines[zone] * 8;
            int entryIndex = zone * 2;  // First act of this zone

            // Draw zone name (highlighted if either act is selected)
            boolean zoneHighlighted = (selectedIndex == entryIndex || selectedIndex == entryIndex + 1);
            drawText(gm, leftZoneNames[zone], leftZoneX, zoneY, zoneHighlighted ? 3 : 0);

            // Draw act numbers stacked vertically
            // Act 1
            drawText(gm, "1", leftActX, zoneY, selectedIndex == entryIndex ? 3 : 0);
            // Act 2
            drawText(gm, "2", leftActX, zoneY + 8, selectedIndex == entryIndex + 1 ? 3 : 0);
        }

        // Right column
        int rightZoneX = 176;  // col=0x2C → tile 22 → X=176
        int rightActX = 288;   // col=0x48 → tile 36 → X=288

        // Metropolis (3 acts) - line 3
        int mtzY = 3 * 8;
        boolean mtzHighlighted = (selectedIndex >= 14 && selectedIndex <= 16);
        drawText(gm, "METROPOLIS", rightZoneX, mtzY, mtzHighlighted ? 3 : 0);
        drawText(gm, "1", rightActX, mtzY, selectedIndex == 14 ? 3 : 0);
        drawText(gm, "2", rightActX, mtzY + 8, selectedIndex == 15 ? 3 : 0);
        drawText(gm, "3", rightActX, mtzY + 16, selectedIndex == 16 ? 3 : 0);

        // Sky Chase (single act) - line 6
        int sczY = 6 * 8;
        drawText(gm, "SKY CHASE", rightZoneX, sczY, selectedIndex == 17 ? 3 : 0);

        // Wing Fortress (single act) - line 9
        int wfzY = 9 * 8;
        drawText(gm, "WING FORTRESS", rightZoneX, wfzY, selectedIndex == 18 ? 3 : 0);

        // Death Egg (single act) - line 12
        int dezY = 12 * 8;
        drawText(gm, "DEATH EGG", rightZoneX, dezY, selectedIndex == 19 ? 3 : 0);

        // Special Stage - line 15
        int ssY = 15 * 8;
        drawText(gm, "SPECIAL STAGE", rightZoneX, ssY, selectedIndex == 20 ? 3 : 0);

        // Sound Test - line 18
        int stY = 18 * 8;
        drawText(gm, "SOUND TEST", rightZoneX, stY, selectedIndex == 21 ? 3 : 0);
    }

    /**
     * Draws text using the loaded font patterns.
     *
     * @param gm           Graphics manager
     * @param text         Text to draw
     * @param x            X position
     * @param y            Y position
     * @param paletteIndex Palette line (0=normal, 3=highlight)
     */
    private void drawText(GraphicsManager gm, String text, int x, int y, int paletteIndex) {
        int charX = x;
        for (char c : text.toUpperCase().toCharArray()) {
            int charIndex = getCharacterTileIndex(c);
            if (charIndex >= 0) {
                // Font patterns are at FONT_OFFSET
                int patternId = PATTERN_BASE + dataLoader.getFontOffset() + charIndex;
                PatternDesc desc = new PatternDesc(charIndex | (paletteIndex << 13));
                gm.renderPatternWithId(patternId, desc, charX, y);
            }
            charX += 8;
        }
    }

    /**
     * Gets the tile index for a character in the font.
     *
     * <p>From PlaneEd project:
     * <ul>
     *   <li>Number Offset: 0x00 - digits 0-9 at indices 0-9</li>
     *   <li>Letter Offset: 0x0E - letters A-Z starting at index 14</li>
     * </ul>
     */
    private int getCharacterTileIndex(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';  // 0-9 at indices 0-9
        } else if (c >= 'A' && c <= 'Z') {
            return 0x0E + (c - 'A');  // A-Z starting at index 14 (0x0E)
        } else if (c == ' ') {
            return -1; // Space - skip
        }
        return -1; // Unknown character
    }

    /**
     * Draws the zone preview icon.
     */
    private void drawZoneIcon(GraphicsManager gm) {
        if (selectedIndex >= ICON_TABLE.length) {
            return;
        }

        int iconIndex = ICON_TABLE[selectedIndex];
        if (iconIndex < 0 || iconIndex >= 15) {
            return;
        }

        // Update icon palette (palette line 1 - separate from text palettes)
        Palette iconPalette = dataLoader.getIconPalette(iconIndex);
        if (iconPalette != null) {
            gm.cachePaletteTexture(iconPalette, 1);
        }

        // Icon position (bottom right area of screen)
        int iconX = 216;
        int iconY = 176;

        // Each icon is 4x3 tiles (32x24 pixels)
        // Icons are stored sequentially in ArtNem_LevelSelectPics
        int tilesPerIcon = 12; // 4 * 3
        int baseTile = dataLoader.getLevelSelectPicsOffset() + (iconIndex * tilesPerIcon);

        // Draw the 4x3 icon (row-major order - tiles stored left-to-right, top-to-bottom)
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 4; col++) {
                int tileOffset = row * 4 + col;  // row-major: row * width + col
                int patternId = PATTERN_BASE + baseTile + tileOffset;
                int tileX = iconX + col * 8;
                int tileY = iconY + row * 8;

                // Use palette line 1 for icons (separate from text highlight on line 3)
                PatternDesc desc = new PatternDesc(tileOffset | (1 << 13));
                gm.renderPatternWithId(patternId, desc, tileX, tileY);
            }
        }
    }

    /**
     * Draws the selection highlight.
     * Note: The original game highlights selected text by changing palette only,
     * which is already handled in drawMenuEntries(). No additional visual indicator needed.
     */
    private void drawSelectionHighlight(GraphicsManager gm) {
        // The original Sonic 2 level select only highlights by changing text color
        // to yellow (palette line 3). No arrow or background rectangle is drawn.
        // The palette-based highlighting is already done in drawMenuEntries().
    }

    /**
     * Draws the sound test value as hex digits.
     */
    private void drawSoundTestValue(GraphicsManager gm) {
        // Draw the sound test value at the right side of the sound test entry
        // Sound Test is at line 18, X position after "SOUND TEST" text
        int x = 288;      // col=0x48 → tile 36 → X=288 (same as right column act numbers)
        int y = 18 * 8;   // line 18 → Y=144

        // Convert to 2-digit hex
        int highNibble = (soundTestValue >> 4) & 0xF;
        int lowNibble = soundTestValue & 0xF;

        // Draw high nibble
        drawHexDigit(gm, highNibble, x, y);
        // Draw low nibble
        drawHexDigit(gm, lowNibble, x + 8, y);
    }

    /**
     * Draws a single hex digit (0-F).
     */
    private void drawHexDigit(GraphicsManager gm, int digit, int x, int y) {
        int charIndex;
        if (digit < 10) {
            charIndex = digit; // 0-9 at indices 0-9
        } else {
            charIndex = 0x0E + (digit - 10); // A-F are letters starting at index 14
        }

        int patternId = PATTERN_BASE + dataLoader.getFontOffset() + charIndex;
        PatternDesc desc = new PatternDesc(charIndex | (3 << 13)); // Yellow palette (line 3)
        gm.renderPatternWithId(patternId, desc, x, y);
    }

    /**
     * Returns the current state.
     */
    public State getState() {
        return state;
    }

    /**
     * Returns true if the level select is exiting (level should be loaded).
     */
    public boolean isExiting() {
        return state == State.EXITING;
    }

    /**
     * Returns true if the level select is active (not inactive).
     */
    public boolean isActive() {
        return state != State.INACTIVE;
    }

    /**
     * Gets the selected zone/act word value.
     * High byte = zone ID, Low byte = act number.
     * Special values: 0x4000 = Special Stage, 0xFFFF = Sound Test.
     *
     * @return zone/act word or special value
     */
    public int getSelectedZoneAct() {
        return LEVEL_ORDER[selectedIndex];
    }

    /**
     * Gets the selected zone index (0-10) or -1 for special stage/sound test.
     */
    public int getSelectedZone() {
        int zoneAct = LEVEL_ORDER[selectedIndex];
        if (zoneAct == SPECIAL_STAGE_VALUE || zoneAct == SOUND_TEST_VALUE) {
            return -1;
        }
        return (zoneAct >> 8) & 0xFF;
    }

    /**
     * Gets the selected act index (0-2) or -1 for special stage/sound test.
     */
    public int getSelectedAct() {
        int zoneAct = LEVEL_ORDER[selectedIndex];
        if (zoneAct == SPECIAL_STAGE_VALUE || zoneAct == SOUND_TEST_VALUE) {
            return -1;
        }
        return zoneAct & 0xFF;
    }

    /**
     * Returns true if Special Stage is selected.
     */
    public boolean isSpecialStageSelected() {
        return LEVEL_ORDER[selectedIndex] == SPECIAL_STAGE_VALUE;
    }

    /**
     * Returns true if Sound Test is selected.
     */
    public boolean isSoundTestSelected() {
        return LEVEL_ORDER[selectedIndex] == SOUND_TEST_VALUE;
    }

    /**
     * Resets the manager to inactive state.
     */
    public void reset() {
        state = State.INACTIVE;
        selectedIndex = 0;
        soundTestValue = 0;
        fadeTimer = 0;
        resetHoldTimers();
        LOGGER.info("Level select reset to inactive");
    }

    /**
     * Gets the current menu selection index.
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Gets the current sound test value.
     */
    public int getSoundTestValue() {
        return soundTestValue;
    }
}
