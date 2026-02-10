package uk.co.jamesj999.sonic.game.sonic1.specialstage;

import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomManager;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.ResultsScreen;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1Sfx;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Palette;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.render.SpriteMappingFrame;
import uk.co.jamesj999.sonic.level.render.SpriteMappingPiece;
import uk.co.jamesj999.sonic.tools.NemesisReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Sonic 1 special-stage results screen (Obj7E behavior).
 *
 * <p>Implements the three message scenarios:
 * 1) Failed to get emerald: "SPECIAL STAGE"
 * 2) Got an emerald: "CHAOS EMERALDS"
 * 3) Got all emeralds: "SONIC GOT THEM ALL"
 *
 * <p>Art loading is self-contained (loaded directly from ROM) so the results
 * screen works regardless of whether a level is loaded. This follows the same
 * pattern as the S2 {@code SpecialStageResultsScreenObjectInstance}.
 */
public final class Sonic1SpecialStageResultsScreen implements ResultsScreen {
    private static final Logger LOGGER = Logger.getLogger(Sonic1SpecialStageResultsScreen.class.getName());

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

    // Map_SSR frame indices (matching createResultsScreenMappings() below).
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

    // GPU cache base IDs (avoids collision with level 0x20000, SS 0x10000, HUD 0x28000).
    private static final int PATTERN_BASE = 0x40000;
    private static final int EMERALD_PATTERN_BASE = 0x41000;

    // Score digit copy constants (matching Sonic1ObjectArtProvider).
    private static final int RESULTS_SCORE_DIGIT_PAIR_COUNT = 8;
    private static final int RESULTS_SCORE_DIGIT_TILE_COUNT = RESULTS_SCORE_DIGIT_PAIR_COUNT * 2;
    private static final int HUD_TEXT_E_PAIR_INDEX = 22;

    // SS results palette table index. Pal_SSResult is entry 17 in Pal_Index (loads to palette line 1).
    private static final int PALETTE_ID_SS_RESULT = 17;

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

    // Self-contained art state.
    private Pattern[] combinedPatterns;
    private Pattern[] sourceDigitPatterns;
    private PatternSpriteRenderer resultsRenderer;
    private PatternSpriteRenderer emeraldRenderer;
    private boolean artLoaded;
    private boolean artCached;

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

        ensureArtCached();

        if (resultsRenderer == null) {
            appendPlaceholder(commands, camera);
            return;
        }

        updateDynamicNumberPatterns();

        // Draw oval first so other text appears on top.
        resultsRenderer.drawFrameIndex(FRAME_OVAL,
                toWorldX(ovalX, camera), toWorldY(OVAL_Y, camera), false, false);

        resultsRenderer.drawFrameIndex(getScenarioFrameIndex(),
                toWorldX(textX, camera), toWorldY(TEXT_Y, camera), false, false);

        resultsRenderer.drawFrameIndex(FRAME_SCORE,
                toWorldX(scoreX, camera), toWorldY(SCORE_Y, camera), false, false);

        resultsRenderer.drawFrameIndex(FRAME_RING_BONUS,
                toWorldX(ringX, camera), toWorldY(RING_Y, camera), false, false);

        appendEmeraldIndicators(commands, camera);

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager != null) {
            graphicsManager.flushPatternBatch();
        }
    }

    // ===== Self-contained art loading =====

    /**
     * Loads all art from ROM and builds renderers. Follows the same pattern as
     * {@code SpecialStageResultsScreenObjectInstance.loadArt()} in S2.
     */
    private void loadArt() {
        try {
            RomManager romManager = GameServices.rom();
            if (!romManager.isRomAvailable()) {
                LOGGER.warning("ROM not available for S1 SS results art loading");
                return;
            }
            Rom rom = romManager.getRom();

            // Load title card patterns (Nem_TitleCard) -> VRAM $580+
            Pattern[] titleCardPatterns = loadNemesisPatterns(rom,
                    Sonic1Constants.ART_NEM_TITLE_CARD_ADDR, "TitleCard");

            // Load HUD text patterns (Nem_Hud: SCORE/TIME/RINGS labels) -> VRAM $6CA+
            Pattern[] hudTextPatterns = loadNemesisPatterns(rom,
                    Sonic1Constants.ART_NEM_HUD_ADDR, "HUDText");

            // Load HUD digit patterns (uncompressed, for number rendering)
            Pattern[] hudDigitPatterns = loadUncompressedPatterns(rom,
                    Sonic1Constants.ART_UNC_HUD_NUMBERS_ADDR,
                    Sonic1Constants.ART_UNC_HUD_NUMBERS_SIZE, "HUDNumbers");

            if (titleCardPatterns.length == 0 || hudTextPatterns.length == 0) {
                LOGGER.warning("Failed to load essential results screen art");
                return;
            }

            // Build composite pattern array matching Sonic1ObjectArtProvider.loadResultsScreenArt()
            int hudTextStartIndex = Sonic1Constants.VRAM_RESULTS_HUD_TEXT - Sonic1Constants.VRAM_RESULTS_BASE;
            int hudScoreDigitsStartIndex =
                    (Sonic1Constants.VRAM_RESULTS_HUD_TEXT + 0x18) - Sonic1Constants.VRAM_RESULTS_BASE;
            int totalSize = Math.max(
                    hudTextStartIndex + hudTextPatterns.length,
                    hudScoreDigitsStartIndex + RESULTS_SCORE_DIGIT_TILE_COUNT);

            combinedPatterns = new Pattern[totalSize];
            for (int i = 0; i < totalSize; i++) {
                combinedPatterns[i] = new Pattern();
            }

            // Copy title card patterns at index RESULTS_TILE_ADJUST (0x10)
            int titleCardStart = Sonic1Constants.RESULTS_TILE_ADJUST;
            for (int i = 0; i < titleCardPatterns.length && (titleCardStart + i) < totalSize; i++) {
                combinedPatterns[titleCardStart + i] = titleCardPatterns[i];
            }

            // Copy HUD text patterns
            for (int i = 0; i < hudTextPatterns.length && (hudTextStartIndex + i) < totalSize; i++) {
                combinedPatterns[hudTextStartIndex + i] = hudTextPatterns[i];
            }

            // Copy score digit tiles (E + seven zeros)
            copyResultsScoreDigitTiles(combinedPatterns, hudScoreDigitsStartIndex,
                    hudTextPatterns, hudDigitPatterns);

            // Preserve source digit patterns for tally updates
            if (hudDigitPatterns.length >= 20) {
                sourceDigitPatterns = new Pattern[hudDigitPatterns.length];
                for (int i = 0; i < hudDigitPatterns.length; i++) {
                    sourceDigitPatterns[i] = new Pattern();
                    sourceDigitPatterns[i].copyFrom(hudDigitPatterns[i]);
                }
            }

            // Create results sprite sheet and renderer
            List<SpriteMappingFrame> mappings = createResultsScreenMappings();
            ObjectSpriteSheet sheet = new ObjectSpriteSheet(combinedPatterns, mappings, 0, 1);
            resultsRenderer = new PatternSpriteRenderer(sheet);

            // Load emerald art and create emerald renderer
            loadEmeraldArt(rom);

            artLoaded = true;
            LOGGER.info("S1 SS results art loaded: " + totalSize + " composite patterns, "
                    + mappings.size() + " frames");

        } catch (IOException e) {
            LOGGER.warning("Failed to load S1 SS results art: " + e.getMessage());
            combinedPatterns = null;
        }
    }

    private void loadEmeraldArt(Rom rom) {
        Pattern[] emeraldPatterns = loadNemesisPatterns(rom,
                Sonic1Constants.ART_NEM_SS_RESULT_EM_ADDR, "SSResultEmerald");
        if (emeraldPatterns.length == 0) {
            LOGGER.warning("Failed to load SS results emerald art");
            return;
        }

        // Map_SSRC: each frame is a single 2x2 spritePiece(-8, -8, 2, 2, tile, pal)
        List<SpriteMappingFrame> frames = new ArrayList<>();
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 4, false, false, 1))));  // 0: Blue
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 0, false, false, 0))));  // 1: Yellow
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 4, false, false, 2))));  // 2: Pink
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 4, false, false, 3))));  // 3: Green
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 8, false, false, 1))));  // 4: Orange
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-8, -8, 2, 2, 12, false, false, 1)))); // 5: Purple
        frames.add(new SpriteMappingFrame(List.of()));                         // 6: Blank (flash)

        ObjectSpriteSheet sheet = new ObjectSpriteSheet(emeraldPatterns, frames, 0, 1);
        emeraldRenderer = new PatternSpriteRenderer(sheet);
    }

    /**
     * Ensures art is loaded from ROM and cached to GPU. Also restores the Sonic
     * palette to GPU line 0 (the SS overwrites it with its own palettes) and
     * loads the SS results palette to lines 1-3 for emerald colors.
     */
    private void ensureArtCached() {
        if (artCached) {
            return;
        }

        if (!artLoaded) {
            loadArt();
        }

        if (combinedPatterns == null || resultsRenderer == null) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager == null) {
            return;
        }

        // Cache results patterns
        resultsRenderer.ensurePatternsCached(graphicsManager, PATTERN_BASE);

        // Cache emerald patterns
        if (emeraldRenderer != null) {
            emeraldRenderer.ensurePatternsCached(graphicsManager, EMERALD_PATTERN_BASE);
        }

        // Restore palettes: Sonic palette to line 0, SS results palette to lines 1-3.
        restorePalettes(graphicsManager);

        artCached = true;
        LOGGER.fine("S1 SS results art cached to GPU");
    }

    /**
     * Restores the Sonic character palette to GPU line 0 and loads the SS results
     * palette (Pal_SSResult) to GPU lines 1-3. Without this, text renders with
     * the special stage's palette colors instead of the proper white/yellow.
     */
    private void restorePalettes(GraphicsManager graphicsManager) {
        try {
            RomManager romManager = GameServices.rom();
            if (!romManager.isRomAvailable()) {
                return;
            }
            Rom rom = romManager.getRom();

            // Palette line 0: Sonic character palette (white/yellow for text)
            byte[] sonicPalData = rom.readBytes(
                    Sonic1Constants.SONIC_PALETTE_ADDR, Palette.PALETTE_SIZE_IN_ROM);
            Palette sonicPal = new Palette();
            sonicPal.fromSegaFormat(sonicPalData);
            graphicsManager.cachePaletteTexture(sonicPal, 0);

            // Palette lines 1-3: SS results palette (Pal_SSResult, for emerald colors).
            // Entry 17 in the palette table: 8 bytes per entry, first longword = ROM address.
            int ssResultPalAddr = rom.read32BitAddr(
                    Sonic1Constants.PALETTE_TABLE_ADDR + PALETTE_ID_SS_RESULT * 8);
            // Pal_SSResult is 3 palette lines (96 bytes) loaded to v_palette_line_1
            byte[] resultsPalData = rom.readBytes(ssResultPalAddr, 3 * Palette.PALETTE_SIZE_IN_ROM);
            for (int i = 0; i < 3; i++) {
                int start = i * Palette.PALETTE_SIZE_IN_ROM;
                int end = start + Palette.PALETTE_SIZE_IN_ROM;
                if (end <= resultsPalData.length) {
                    Palette pal = new Palette();
                    pal.fromSegaFormat(Arrays.copyOfRange(resultsPalData, start, end));
                    graphicsManager.cachePaletteTexture(pal, i + 1);
                }
            }

        } catch (Exception e) {
            LOGGER.warning("Failed to restore palettes: " + e.getMessage());
        }
    }

    // ===== Dynamic number patterns =====

    private void updateDynamicNumberPatterns() {
        if (resultsRenderer == null || combinedPatterns == null || sourceDigitPatterns == null) {
            return;
        }

        int score = Math.max(0, GameServices.gameState().getScore());
        if (ringBonus == lastRingBonus && score == lastScoreValue) {
            return;
        }

        // Ring bonus digits live at tile slots 8-15 in the composite sheet.
        int ringDigitStart = Sonic1Constants.S1_RESULTS_BONUS_DIGIT_GROUP_TILES;
        if (ringDigitStart + Sonic1Constants.S1_RESULTS_BONUS_DIGIT_GROUP_TILES > combinedPatterns.length) {
            return;
        }
        ensurePatternSlots(combinedPatterns, ringDigitStart, Sonic1Constants.S1_RESULTS_BONUS_DIGIT_GROUP_TILES);
        writeBonusValue(combinedPatterns, ringDigitStart, ringBonus, sourceDigitPatterns);

        // Score digits live in the HUD text range.
        if (SCORE_DIGITS_START_INDEX + SCORE_DIGIT_TILES <= combinedPatterns.length) {
            ensurePatternSlots(combinedPatterns, SCORE_DIGITS_START_INDEX, SCORE_DIGIT_TILES);
            writeScoreValue(combinedPatterns, score, sourceDigitPatterns);
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        if (graphicsManager != null) {
            resultsRenderer.updatePatternRange(graphicsManager, ringDigitStart,
                    Sonic1Constants.S1_RESULTS_BONUS_DIGIT_GROUP_TILES);
            if (SCORE_DIGITS_START_INDEX + SCORE_DIGIT_TILES <= combinedPatterns.length) {
                resultsRenderer.updatePatternRange(graphicsManager, SCORE_DIGITS_START_INDEX, SCORE_DIGIT_TILES);
            }
        }

        lastRingBonus = ringBonus;
        lastScoreValue = score;
    }

    // ===== Rendering helpers =====

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
        // Flash effect: on odd frames, show emeralds; on even frames, blank (30fps blink).
        if ((totalFrames & 1) == 0) {
            return;
        }

        int emeraldCount = Math.min(SONIC1_CHAOS_EMERALD_COUNT, GameServices.gameState().getEmeraldCount());
        if (emeraldCount <= 0) {
            return;
        }

        for (int i = 0; i < emeraldCount; i++) {
            int worldX = toWorldX(EMERALD_X_POSITIONS[i], camera);
            int worldY = toWorldY(EMERALD_Y, camera);
            if (emeraldRenderer != null) {
                emeraldRenderer.drawFrameIndex(i, worldX, worldY, false, false);
            } else {
                float[] color = EMERALD_COLORS[i];
                renderPlaceholderBox(commands, worldX - 8, worldY - 8, 16, 16,
                        color[0], color[1], color[2]);
            }
        }
    }

    // ===== Pattern/digit helpers =====

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

    /**
     * Copies the initial score digit tiles into the composite array.
     * Tile pair at startIndex is "E" (from HUD text), followed by seven "0" pairs.
     */
    private static void copyResultsScoreDigitTiles(Pattern[] dest, int startIndex,
            Pattern[] hudTextPatterns, Pattern[] hudDigitPatterns) {
        if (dest == null || hudDigitPatterns == null || hudDigitPatterns.length < 2) {
            return;
        }
        // "E" pair from HUD text
        copyPatternPair(dest, startIndex, hudTextPatterns, HUD_TEXT_E_PAIR_INDEX);
        // Seven "0" pairs from digit patterns
        for (int pair = 1; pair < RESULTS_SCORE_DIGIT_PAIR_COUNT; pair++) {
            copyPatternPair(dest, startIndex + (pair * 2), hudDigitPatterns, 0);
        }
    }

    private static void copyPatternPair(Pattern[] dest, int destIndex, Pattern[] src, int srcIndex) {
        if (src == null || srcIndex < 0 || srcIndex + 1 >= src.length) {
            return;
        }
        if (destIndex < 0 || destIndex + 1 >= dest.length) {
            return;
        }
        if (dest[destIndex] == null) {
            dest[destIndex] = new Pattern();
        }
        if (dest[destIndex + 1] == null) {
            dest[destIndex + 1] = new Pattern();
        }
        dest[destIndex].copyFrom(src[srcIndex]);
        dest[destIndex + 1].copyFrom(src[srcIndex + 1]);
    }

    // ===== ROM art loading helpers =====

    private Pattern[] loadNemesisPatterns(Rom rom, int address, String name) {
        try {
            byte[] compressed = rom.readBytes(address, 8192);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = NemesisReader.decompress(channel);
                int count = decompressed.length / Pattern.PATTERN_SIZE_IN_ROM;
                Pattern[] patterns = new Pattern[count];
                for (int i = 0; i < count; i++) {
                    patterns[i] = new Pattern();
                    byte[] sub = Arrays.copyOfRange(decompressed,
                            i * Pattern.PATTERN_SIZE_IN_ROM,
                            (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                    patterns[i].fromSegaFormat(sub);
                }
                return patterns;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load " + name + " patterns: " + e.getMessage());
            return new Pattern[0];
        }
    }

    private Pattern[] loadUncompressedPatterns(Rom rom, int address, int size, String name) {
        try {
            byte[] data = rom.readBytes(address, size);
            if (data.length % Pattern.PATTERN_SIZE_IN_ROM != 0) {
                LOGGER.warning("Inconsistent uncompressed art size for " + name);
                return new Pattern[0];
            }
            int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
            Pattern[] patterns = new Pattern[count];
            for (int i = 0; i < count; i++) {
                patterns[i] = new Pattern();
                byte[] sub = Arrays.copyOfRange(data,
                        i * Pattern.PATTERN_SIZE_IN_ROM,
                        (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
                patterns[i].fromSegaFormat(sub);
            }
            return patterns;
        } catch (Exception e) {
            LOGGER.warning("Failed to load " + name + " patterns: " + e.getMessage());
            return new Pattern[0];
        }
    }

    // ===== Mapping frames (duplicated from Sonic1ObjectArtProvider) =====

    /**
     * Creates sprite mappings for the results screen from Map_Got in the disassembly.
     * All tile IDs from the disassembly are relative to ArtTile_Title_Card ($580).
     * We add RESULTS_TILE_ADJUST (0x10) to convert to composite array indices.
     */
    private static List<SpriteMappingFrame> createResultsScreenMappings() {
        final int T = Sonic1Constants.RESULTS_TILE_ADJUST; // 0x10
        List<SpriteMappingFrame> frames = new ArrayList<>();

        // Frame 0: "SONIC HAS"
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x48, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x38, -8, 2, 2, 0x32 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x28, -8, 2, 2, 0x2E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x18, -8, 1, 2, 0x20 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -8, 2, 2, 0x08 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x10, -8, 2, 2, 0x1C + T, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x30, -8, 2, 2, 0x3E + T, false, false, 0, false)
        )));

        // Frame 1: "PASSED"
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x30, -8, 2, 2, 0x36 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x20, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece( 0x00, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece( 0x10, -8, 2, 2, 0x10 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -8, 2, 2, 0x0C + T, false, false, 0, false)
        )));

        // Frame 2: "SCORE" text + score digits
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x50, -8, 4, 2, 0x14A + T, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -8, 1, 2, 0x162 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x18, -8, 3, 2, 0x164 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x30, -8, 4, 2, 0x16A + T, false, false, 0, false)
        )));

        // Frame 3: "TIME BONUS" + digit area
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x50, -8, 4, 2, 0x15A + T, false, false, 0, false),
                new SpriteMappingPiece(-0x27, -8, 4, 2, 0x66 + T,  false, false, 0, false),
                new SpriteMappingPiece(   -7, -8, 1, 2, 0x14A + T, false, false, 0, false),
                new SpriteMappingPiece( -0xA, -9, 2, 1, 0x6E + T,  false, false, 0, false),
                new SpriteMappingPiece( -0xA, -1, 2, 1, 0x6E + T,  true,  true,  0, false),
                new SpriteMappingPiece( 0x28, -8, 4, 2, 0,         false, false, 0, false),
                new SpriteMappingPiece( 0x48, -8, 1, 2, 0x170 + T, false, false, 0, false)
        )));

        // Frame 4: "RING BONUS" + digit area
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x50, -8, 4, 2, 0x152 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x27, -8, 4, 2, 0x66 + T,  false, false, 0, false),
                new SpriteMappingPiece(   -7, -8, 1, 2, 0x14A + T, false, false, 0, false),
                new SpriteMappingPiece( -0xA, -9, 2, 1, 0x6E + T,  false, false, 0, false),
                new SpriteMappingPiece( -0xA, -1, 2, 1, 0x6E + T,  true,  true,  0, false),
                new SpriteMappingPiece( 0x28, -8, 4, 2, 8,         false, false, 0, false),
                new SpriteMappingPiece( 0x48, -8, 1, 2, 0x170 + T, false, false, 0, false)
        )));

        // Frame 5: Oval decoration
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x0C, -0x1C, 4, 1, 0x70 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x14, -0x1C, 1, 3, 0x74 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14, -0x14, 2, 1, 0x77 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x1C, -0x0C, 2, 2, 0x79 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x14, 4, 1, 0x70 + T, true,  true,  0, false),
                new SpriteMappingPiece(-0x1C,  0x04, 1, 3, 0x74 + T, true,  true,  0, false),
                new SpriteMappingPiece( 0x04,  0x0C, 2, 1, 0x77 + T, true,  true,  0, false),
                new SpriteMappingPiece( 0x0C, -0x04, 2, 2, 0x79 + T, true,  true,  0, false),
                new SpriteMappingPiece(-0x04, -0x14, 3, 1, 0x7D + T, false, false, 0, false),
                new SpriteMappingPiece(-0x0C, -0x0C, 4, 1, 0x7C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x0C, -0x04, 3, 1, 0x7C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x04, 4, 1, 0x7C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x14,  0x0C, 3, 1, 0x7C + T, false, false, 0, false)
        )));

        // Frame 6: "ACT 1"
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, 0x04, 4, 1, 0x53 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x0C, -0x0C, 1, 3, 0x57 + T, false, false, 0, false)
        )));

        // Frame 7: "ACT 2"
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, 0x04, 4, 1, 0x53 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x08, -0x0C, 2, 3, 0x5A + T, false, false, 0, false)
        )));

        // Frame 8: "ACT 3"
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x14, 0x04, 4, 1, 0x53 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x08, -0x0C, 2, 3, 0x60 + T, false, false, 0, false)
        )));

        // Frame 9: SCORE separator dots
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x33, -9, 2, 1, 0x6E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x33, -1, 2, 1, 0x6E + T, true,  true,  0, false)
        )));

        // Frame 10: "CHAOS EMERALDS" (Map_SSR frame 0)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x70, -8, 2, 2, 0x08 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x60, -8, 2, 2, 0x1C + T, false, false, 0, false),
                new SpriteMappingPiece(-0x50, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x40, -8, 2, 2, 0x32 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x30, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x10, -8, 2, 2, 0x10 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x00, -8, 2, 2, 0x2A + T, false, false, 0, false),
                new SpriteMappingPiece( 0x10, -8, 2, 2, 0x10 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -8, 2, 2, 0x3A + T, false, false, 0, false),
                new SpriteMappingPiece( 0x30, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x40, -8, 2, 2, 0x26 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x50, -8, 2, 2, 0x0C + T, false, false, 0, false),
                new SpriteMappingPiece( 0x60, -8, 2, 2, 0x3E + T, false, false, 0, false)
        )));

        // Frame 11: "SPECIAL STAGE" (Map_SSR frame 7)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x64, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x54, -8, 2, 2, 0x36 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x44, -8, 2, 2, 0x10 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x34, -8, 2, 2, 0x08 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x24, -8, 1, 2, 0x20 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x1C, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x0C, -8, 2, 2, 0x26 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x14, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece( 0x24, -8, 2, 2, 0x42 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x34, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x44, -8, 2, 2, 0x18 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x54, -8, 2, 2, 0x10 + T, false, false, 0, false)
        )));

        // Frame 12: "SONIC GOT THEM ALL" (Map_SSR frame 8)
        frames.add(new SpriteMappingFrame(List.of(
                new SpriteMappingPiece(-0x78, -8, 2, 2, 0x3E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x68, -8, 2, 2, 0x32 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x58, -8, 2, 2, 0x2E + T, false, false, 0, false),
                new SpriteMappingPiece(-0x48, -8, 1, 2, 0x20 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x40, -8, 2, 2, 0x08 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x28, -8, 2, 2, 0x18 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x18, -8, 2, 2, 0x32 + T, false, false, 0, false),
                new SpriteMappingPiece(-0x08, -8, 2, 2, 0x42 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x10, -8, 2, 2, 0x42 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x20, -8, 2, 2, 0x1C + T, false, false, 0, false),
                new SpriteMappingPiece( 0x30, -8, 2, 2, 0x10 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x40, -8, 2, 2, 0x2A + T, false, false, 0, false),
                new SpriteMappingPiece( 0x58, -8, 2, 2, 0x00 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x68, -8, 2, 2, 0x26 + T, false, false, 0, false),
                new SpriteMappingPiece( 0x78, -8, 2, 2, 0x26 + T, false, false, 0, false)
        )));

        return frames;
    }

    // ===== Coordinate / utility helpers =====

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
