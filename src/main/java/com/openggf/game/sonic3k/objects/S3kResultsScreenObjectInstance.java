package com.openggf.game.sonic3k.objects;

import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameServices;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic2.objects.AbstractResultsScreen;
import com.openggf.game.sonic3k.Sonic3kObjectArt;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * S3K results screen -- displays "{CHARACTER} GOT THROUGH ACT {N}" with
 * time bonus and ring bonus tally after the signpost lands.
 *
 * <p>ROM: Obj_LevelResults (sonic3k.asm lines 62499-63003).
 *
 * <p>Key differences from S2:
 * <ul>
 *   <li>No fade-to-black -- signals flags and lets level events handle transitions</li>
 *   <li>360-frame pre-tally delay (S2 uses 180)</li>
 *   <li>90-frame post-tally wait (S2 uses 180)</li>
 *   <li>No perfect bonus</li>
 *   <li>Act 1 exit shows act 2 title card; act 2 exit sets End_of_level_flag</li>
 * </ul>
 */
public class S3kResultsScreenObjectInstance extends AbstractResultsScreen {
    private static final Logger LOG = Logger.getLogger(S3kResultsScreenObjectInstance.class.getName());

    // ROM-accurate timing
    private static final int S3K_PRE_TALLY_DELAY = 360;  // 6*60 frames (ROM line 62580)
    private static final int S3K_WAIT_DURATION = 90;      // ROM line 62676
    private static final int MUSIC_TRIGGER_FRAME = 71;    // 360 - 289 = 71 (ROM line 62626)

    // Time bonus table (ROM lines 62910-62918)
    private static final int[] TIME_BONUSES = {5000, 5000, 1000, 500, 400, 300, 100, 10};
    private static final int SPECIAL_TIME_BONUS = 10000;  // 9:59 override (ROM line 62559)
    private static final int MAX_TIMER_SECONDS = 599;     // 9:59 = 9*60 + 59

    // Slide speeds (ROM lines 62847, 62836)
    private static final int SLIDE_IN_SPEED = 16;   // moveq #$10,d1
    private static final int SLIDE_OUT_SPEED = 32;  // move.w #-$20,d0

    // Pattern caching
    private static final int PATTERN_BASE = 0x60000;  // High ID to avoid conflicts

    // Digit rendering constants
    private static final int DIGIT_OFFSET_X = -0x38;  // Digits start 0x38 pixels left of element X
    private static final int DIGIT_SPACING = 8;        // 8px per digit
    private static final int DIGIT_COUNT = 7;           // 7-digit display
    private static final int[] DIVISORS = {1000000, 100000, 10000, 1000, 100, 10, 1};

    // State
    private final PlayerCharacter character;
    private final int act;  // 0-indexed: 0=Act 1, 1=Act 2

    // Tally values
    private int timeBonus;
    private int ringBonus;
    private int totalBonusCountUp;

    // Art
    private Pattern[] combinedPatterns;
    private List<SpriteMappingFrame> mappingFrames;
    private boolean artLoaded;
    private boolean artCached;

    // Rendering
    private ObjectSpriteSheet spriteSheet;
    private PatternSpriteRenderer renderer;

    // Music flag
    private boolean musicPlayed;

    // Elements
    private final ResultsElement[] elements = new ResultsElement[12];
    private int exitQueueCounter;
    private int childrenRemaining;

    public S3kResultsScreenObjectInstance(PlayerCharacter character, int act) {
        super("S3kResults");
        this.character = character;
        this.act = act;

        // Calculate bonuses from current game state (ROM lines 62550-62578)
        calculateBonuses();

        // Fade out current music immediately (ROM line 62513)
        fadeOutMusic();

        // Load art
        loadArt();

        // Create elements
        createElements();

        LOG.fine(() -> String.format("S3K results init: character=%s act=%d timeBonus=%d ringBonus=%d",
                character, act, timeBonus, ringBonus));
    }

    // ---- Element data structure ----

    /**
     * A single visual element on the results screen that slides in and out.
     * ROM: ObjArray_LevResults (sonic3k.asm lines 62919-63003).
     */
    private static class ResultsElement {
        enum Type { CHAR_NAME, GENERAL, TIME_BONUS, RING_BONUS, TOTAL }
        enum SlideDirection { FROM_LEFT, FROM_RIGHT }

        final Type type;
        final int targetX;
        final int startX;
        final int y;
        final int mappingFrame;
        final int widthPixels;
        final int exitQueuePriority;
        final SlideDirection slideDirection;
        int currentX;
        boolean exitStarted;
        boolean offScreen;

        ResultsElement(Type type, int targetX, int startX, int y,
                       int mappingFrame, int widthPixels, int exitQueuePriority) {
            this.type = type;
            this.targetX = targetX;
            this.startX = startX;
            this.y = y;
            this.mappingFrame = mappingFrame;
            this.widthPixels = widthPixels;
            this.exitQueuePriority = exitQueuePriority;
            this.slideDirection = startX < 0 ? SlideDirection.FROM_LEFT : SlideDirection.FROM_RIGHT;
            this.currentX = startX;
        }

        /** Move toward targetX at 16px/frame. Returns true when reached. */
        boolean slideIn() {
            if (currentX == targetX) return true;
            if (currentX < targetX) {
                currentX = Math.min(currentX + SLIDE_IN_SPEED, targetX);
            } else {
                currentX = Math.max(currentX - SLIDE_IN_SPEED, targetX);
            }
            return currentX == targetX;
        }

        /** Move at 32px/frame back toward the direction it came from. */
        void slideOut() {
            if (slideDirection == SlideDirection.FROM_LEFT) {
                currentX -= SLIDE_OUT_SPEED;
            } else {
                currentX += SLIDE_OUT_SPEED;
            }
            offScreen = (currentX < -256 || currentX > 576);
        }
    }

    // ---- Element creation ----

    /**
     * Populates the elements array from ROM data (ObjArray_LevResults).
     * ROM: sonic3k.asm lines 62919-63003.
     */
    private void createElements() {
        // ROM ObjArray_LevResults uses VDP coordinates (+128 hardware offset on X and Y).
        // Our engine uses direct screen coordinates, so subtract 128 from all positions.
        // CLAUDE.md: "VDP hardware adds 128 to X/Y. Convert: screen_position = vdp_value - 128"
        final int V = 128; // VDP offset

        int charNameFrame = getCharNameFrame();
        int charNameTargetX = 0xE0 - V;
        int charNameStartX = -0x220 - V;
        int charNameWidth = 0x48;

        if (character == PlayerCharacter.KNUCKLES) {
            charNameTargetX -= 0x30;
            charNameStartX -= 0x30;
            charNameWidth += 0x30;
        }
        if (character == PlayerCharacter.TAILS_ALONE) {
            charNameTargetX += 8;
            charNameStartX += 8;
            charNameWidth -= 8;
        }

        elements[0]  = new ResultsElement(ResultsElement.Type.CHAR_NAME,  charNameTargetX, charNameStartX, 0xB8 - V, charNameFrame, charNameWidth, 1);
        elements[1]  = new ResultsElement(ResultsElement.Type.GENERAL,    0x130 - V, -0x1D0 - V, 0xB8 - V, 0x11, 0x30, 1);
        elements[2]  = new ResultsElement(ResultsElement.Type.GENERAL,    0xE8 - V,   0x468 - V, 0xCC - V, 0x10, 0x70, 3);
        elements[3]  = new ResultsElement(ResultsElement.Type.GENERAL,    0x160 - V,  0x4E0 - V, 0xBC - V, 0x0F, 0x38, 3);
        elements[4]  = new ResultsElement(ResultsElement.Type.GENERAL,    0xC0 - V,   0x4C0 - V, 0xF0 - V, 0x0E, 0x20, 5);
        elements[5]  = new ResultsElement(ResultsElement.Type.GENERAL,    0xE8 - V,   0x4E8 - V, 0xF0 - V, 0x0C, 0x30, 5);
        elements[6]  = new ResultsElement(ResultsElement.Type.TIME_BONUS, 0x178 - V,  0x578 - V, 0xF0 - V, 1,    0x40, 5);
        elements[7]  = new ResultsElement(ResultsElement.Type.GENERAL,    0xC0 - V,   0x500 - V, 0x100 - V, 0x0D, 0x20, 7);
        elements[8]  = new ResultsElement(ResultsElement.Type.GENERAL,    0xE8 - V,   0x528 - V, 0x100 - V, 0x0C, 0x30, 7);
        elements[9]  = new ResultsElement(ResultsElement.Type.RING_BONUS, 0x178 - V,  0x5B8 - V, 0x100 - V, 1,    0x40, 7);
        elements[10] = new ResultsElement(ResultsElement.Type.GENERAL,    0xD4 - V,   0x554 - V, 0x11C - V, 0x0B, 0x30, 9);
        elements[11] = new ResultsElement(ResultsElement.Type.TOTAL,      0x178 - V,  0x5F8 - V, 0x11C - V, 1,    0x40, 9);
        childrenRemaining = 12;
    }

    /**
     * Returns the mapping frame index for the character name.
     * ROM: character-specific name art frames in Map_LevelResults.
     */
    private int getCharNameFrame() {
        return switch (character) {
            case TAILS_ALONE -> 0x13 + 2;
            case KNUCKLES -> 0x13 + 3;
            default -> 0x13;
        };
    }

    // ---- Bonus calculation ----

    private void calculateBonuses() {
        var levelGamestate = LevelManager.getInstance().getLevelGamestate();
        int elapsedSeconds = (levelGamestate != null) ? levelGamestate.getElapsedSeconds() : 0;

        // Pause the timer (ROM line 62550)
        if (levelGamestate != null) {
            levelGamestate.pauseTimer();
        }

        // Special case: 9:59 -> 10000 (ROM lines 62557-62559)
        if (elapsedSeconds >= MAX_TIMER_SECONDS) {
            timeBonus = SPECIAL_TIME_BONUS;
        } else {
            int index = Math.min(elapsedSeconds / 30, TIME_BONUSES.length - 1);
            timeBonus = TIME_BONUSES[index];
        }

        // Ring bonus: rings x 10 (ROM lines 62576-62578)
        int ringCount = (levelGamestate != null) ? levelGamestate.getRings() : 0;
        ringBonus = ringCount * 10;

        totalBonusCountUp = 0;
    }

    // ---- Timing overrides ----

    @Override
    protected int getSlideDuration() {
        return 0;  // Children handle their own sliding; skip SLIDE_IN entirely
    }

    @Override
    protected int getPreTallyDelay() {
        return S3K_PRE_TALLY_DELAY;
    }

    @Override
    protected int getWaitDuration() {
        return S3K_WAIT_DURATION;
    }

    // ---- Update with element sliding ----

    @Override
    public void update(int frameCounter, AbstractPlayableSprite player) {
        super.update(frameCounter, player);

        // Slide elements during pre-tally delay and tally
        if (state <= STATE_TALLY) {
            for (ResultsElement elem : elements) {
                if (elem != null && !elem.offScreen) {
                    elem.slideIn();
                }
            }
        }

        // Exit queue after STATE_WAIT ends
        if (state == STATE_EXIT) {
            updateExitQueue();
        }
    }

    private void updateExitQueue() {
        if (childrenRemaining <= 0) {
            complete = true;
            onExitReady();
            return;
        }
        exitQueueCounter++;
        for (ResultsElement elem : elements) {
            if (elem == null || elem.offScreen) continue;
            if (exitQueueCounter >= elem.exitQueuePriority) {
                elem.exitStarted = true;
            }
            if (elem.exitStarted) {
                elem.slideOut();
                if (elem.offScreen) {
                    childrenRemaining--;
                }
            }
        }
    }

    // ---- Pre-tally delay with music trigger ----

    @Override
    protected void updatePreTallyDelay() {
        // Trigger music at frame 71 of the 360-frame countdown
        // ROM: checks counter == 289 (360 - 71) at line 62626
        if (!musicPlayed && stateTimer == MUSIC_TRIGGER_FRAME) {
            musicPlayed = true;
            try {
                AudioManager.getInstance().playMusic(Sonic3kMusic.ACT_CLEAR.id);
            } catch (Exception e) {
                // Ignore audio errors
            }
        }

        super.updatePreTallyDelay();
    }

    // ---- Tally logic ----

    @Override
    protected TallyResult performTallyStep() {
        int totalIncrement = 0;

        // Decrement time bonus by 10 (ROM line 62639)
        int[] timeResult = decrementBonus(timeBonus);
        timeBonus = timeResult[0];
        totalIncrement += timeResult[1];

        // Decrement ring bonus by 10 (ROM line 62645)
        int[] ringResult = decrementBonus(ringBonus);
        ringBonus = ringResult[0];
        totalIncrement += ringResult[1];

        // Track running total (ROM line 62648)
        totalBonusCountUp += totalIncrement;

        boolean anyRemaining = (timeBonus > 0 || ringBonus > 0);
        return tallyResult(anyRemaining, totalIncrement);
    }

    @Override
    protected void updateTally() {
        TallyResult result = performTallyStep();

        if (result.totalIncrement() > 0) {
            GameServices.gameState().addScore(result.totalIncrement());
        }

        // ROM uses global frame counter for tick timing (line 62652-62654)
        // Level_frame_counter & 3 == 0
        // We use the frameCounter field (set by update()) as the global frame source
        if (result.anyRemaining()) {
            if ((this.frameCounter & 3) == 0) {
                playTickSound();
            }
        }

        if (!result.anyRemaining()) {
            playTallyEndSound();
            state = STATE_WAIT;
            stateTimer = 0;
        }
    }

    // ---- Audio overrides ----

    @Override
    protected void playTickSound() {
        try {
            AudioManager.getInstance().playSfx(Sonic3kSfx.SWITCH.id);
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    @Override
    protected void playTallyEndSound() {
        try {
            AudioManager.getInstance().playSfx(Sonic3kSfx.REGISTER.id);
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    private void fadeOutMusic() {
        try {
            AudioManager.getInstance().fadeOutMusic();
        } catch (Exception e) {
            // Ignore audio errors
        }
    }

    // ---- Exit behavior ----

    @Override
    protected void onExitReady() {
        int zone = LevelManager.getInstance().getRomZoneId();

        // Act 2, Sky Sanctuary ($A), or LRZ boss ($16): set End_of_level_flag
        // ROM lines 62694-62705
        boolean isAct2OrSpecial = (act != 0) || (zone == 0x0A) || (zone == 0x16);

        GameServices.gameState().setEndOfLevelActive(false);

        if (isAct2OrSpecial) {
            GameServices.gameState().setEndOfLevelFlag(true);
        } else {
            // Act 1: show act 2 title card (ROM lines 62708-62720)
            // ROM sets Apparent_act = 1 -- in our engine the title card handles this visually.
            // The level data continues seamlessly (S3K acts share the same level).

            // Show act 2 title card (except SOZ zone $8 and DEZ zone $B)
            // ROM lines 62713-62720
            boolean skipTitleCard = (zone == 0x08) || (zone == 0x0B);
            if (!skipTitleCard) {
                Sonic3kTitleCardManager.getInstance().initializeInLevel(zone, 1);
            }
        }

        setDestroyed(true);
        LOG.fine(() -> String.format("S3K results exit: zone=%X act=%d isAct2OrSpecial=%b",
                zone, act, isAct2OrSpecial));
    }

    // ---- Persistence ----

    @Override
    public boolean isPersistent() {
        return true;  // Survives screen boundary checks
    }

    // ---- Art loading ----

    private void loadArt() {
        try {
            var rom = GameServices.rom().getRom();
            RomByteReader reader = RomByteReader.fromRom(rom);
            Sonic3kObjectArt objectArt = new Sonic3kObjectArt(null, reader);

            combinedPatterns = objectArt.loadResultsArt(character, act);
            List<SpriteMappingFrame> rawMappings = objectArt.loadResultsMappings();

            if (combinedPatterns != null && !rawMappings.isEmpty()) {
                // Adjust tile indices: ROM mappings use absolute VRAM tile indices (e.g. 0x520),
                // but our patterns array is 0-based (patterns[0] = VRAM $520).
                // Subtract VRAM_RESULTS_BASE so piece.tileIndex() maps to array indices.
                mappingFrames = adjustTileIndices(rawMappings, -Sonic3kConstants.VRAM_RESULTS_BASE);

                // Create sprite sheet and renderer
                spriteSheet = new ObjectSpriteSheet(combinedPatterns, mappingFrames, 0, 1);
                renderer = new PatternSpriteRenderer(spriteSheet);
                artLoaded = true;
            } else {
                artLoaded = false;
                LOG.warning("Failed to load results screen art");
            }

            // Note: The level results screen uses the existing level palette.
            // Pal_Results in the ROM is for the special stage results screen, not here.
            // (S2 results screen also does not load a palette.)
        } catch (Exception e) {
            artLoaded = false;
            LOG.warning("Failed to load results screen art: " + e.getMessage());
        }
    }

    /**
     * Adjusts tile indices in mapping frames by a fixed offset.
     * Mirrors {@code Sonic3kObjectArt.adjustTileIndices()} (which is private).
     */
    private static List<SpriteMappingFrame> adjustTileIndices(
            List<SpriteMappingFrame> frames, int adjustment) {
        if (adjustment == 0) return frames;
        List<SpriteMappingFrame> adjusted = new ArrayList<>(frames.size());
        for (SpriteMappingFrame frame : frames) {
            List<SpriteMappingPiece> adjustedPieces = new ArrayList<>(frame.pieces().size());
            for (SpriteMappingPiece piece : frame.pieces()) {
                adjustedPieces.add(new SpriteMappingPiece(
                        piece.xOffset(), piece.yOffset(),
                        piece.widthTiles(), piece.heightTiles(),
                        piece.tileIndex() + adjustment,
                        piece.hFlip(), piece.vFlip(),
                        piece.paletteIndex(), piece.priority()
                ));
            }
            adjusted.add(new SpriteMappingFrame(adjustedPieces));
        }
        return adjusted;
    }

    // ---- Pattern caching ----

    private void ensureArtCached() {
        if (artCached || !artLoaded || renderer == null) return;
        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null) return;
        renderer.ensurePatternsCached(gm, PATTERN_BASE);
        artCached = true;
    }

    // ---- Rendering ----

    @Override
    public void appendRenderCommands(List<GLCommand> commands) {
        if (!artLoaded || renderer == null) return;
        ensureArtCached();
        if (!renderer.isReady()) return;

        Camera camera = Camera.getInstance();
        if (camera == null) return;

        int baseX = camera.getX();
        int baseY = camera.getY();

        for (ResultsElement elem : elements) {
            if (elem == null || elem.offScreen) continue;
            int worldX = baseX + elem.currentX;
            int worldY = baseY + elem.y;

            switch (elem.type) {
                case TIME_BONUS -> renderBonusDigits(worldX, worldY, timeBonus);
                case RING_BONUS -> renderBonusDigits(worldX, worldY, ringBonus);
                case TOTAL -> renderBonusDigits(worldX, worldY, totalBonusCountUp);
                default -> renderMappingFrame(elem.mappingFrame, worldX, worldY);
            }
        }
    }

    /**
     * Renders a mapping frame at the given world position using the PatternSpriteRenderer.
     */
    private void renderMappingFrame(int frameIndex, int worldX, int worldY) {
        if (frameIndex < 0 || frameIndex >= spriteSheet.getFrameCount()) return;
        renderer.drawFrameIndex(frameIndex, worldX, worldY, false, false);
    }

    /**
     * Renders a 7-digit BCD value with leading zero suppression.
     * Digits are positioned starting at (worldX + DIGIT_OFFSET_X) rightward at 8px intervals.
     * Uses individual pattern tiles from the number art region.
     */
    private void renderBonusDigits(int worldX, int worldY, int value) {
        int x = worldX + DIGIT_OFFSET_X;
        boolean hasNonZero = false;
        int remaining = value;
        // The digit pattern index within the patterns array:
        // VRAM_RESULTS_NUMBERS - VRAM_RESULTS_BASE = offset to first digit tile (digit '0')
        int digitBase = Sonic3kConstants.VRAM_RESULTS_NUMBERS - Sonic3kConstants.VRAM_RESULTS_BASE;

        for (int i = 0; i < DIGIT_COUNT; i++) {
            int digit = remaining / DIVISORS[i];
            remaining %= DIVISORS[i];
            if (digit != 0) hasNonZero = true;
            if (hasNonZero || i == DIGIT_COUNT - 1) {
                renderer.drawPatternIndex(digitBase + digit, x + i * DIGIT_SPACING, worldY, 0);
            }
        }
    }
}
