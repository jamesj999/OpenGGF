package com.openggf.game.sonic2.credits;

import com.openggf.audio.AudioManager;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.S2SpriteDataLoader;
import com.openggf.game.sonic2.constants.Sonic2AudioConstants;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpritePieceRenderer;
import com.openggf.tools.EnigmaReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

/**
 * ROM-accurate state machine for the Sonic 2 ending cutscene sequence.
 * <p>
 * Modeled 1:1 on the ROM's ObjCA (ending controller) 8 routines from s2.asm,
 * with ObjCC (tornado) as a 6-sub-state inner machine, plus ObjCB (clouds),
 * ObjCD (birds), ObjCE (jumping character), and ObjCF (plane helixes).
 * <p>
 * The cutscene plays after defeating the Death Egg Robot:
 * <ol>
 *   <li>Photo loop: 4 photos, each with white-to-target palette fade</li>
 *   <li>CHARACTER_APPEAR: character floats on cleared screen</li>
 *   <li>CAMERA_SCROLL: reveals sky background</li>
 *   <li>MAIN_ENDING: tornado approach, birds, rotation, departure, camera pan</li>
 *   <li>TRIGGER_CREDITS: terminal state</li>
 * </ol>
 * <p>
 * This is a self-contained renderer; it does not use the game's level/object system.
 * All rendering happens in {@link #draw()} using PatternDesc-based tile rendering.
 */
public class Sonic2EndingCutsceneManager {

    private static final Logger LOGGER = Logger.getLogger(Sonic2EndingCutsceneManager.class.getName());

    // ========================================================================
    // Cutscene states — maps 1:1 to ObjCA routines 0/$2/$4/$6/$8/$A/$C/$E
    // ========================================================================

    /**
     * States of the ending cutscene, corresponding to ObjCA routine progression.
     * The photo loop (INIT through PHOTO_LOAD) cycles 4 times before advancing.
     */
    public enum CutsceneState {
        /** ObjCA routine 0: spawn palette changer, set timer. */
        INIT,
        /** ObjCA routine 2: wait for palette fade timer 1. */
        PALETTE_WAIT_1,
        /** ObjCA routine 4: spawn second palette changer. */
        PALETTE_SETUP_2,
        /** ObjCA routine 6: wait for palette fade timer 2. */
        PALETTE_WAIT_2,
        /** ObjCA routine 8: load next photo to VRAM; if photos remain loop, else advance. */
        PHOTO_LOAD,
        /** ObjCA routine $A: character floats/walks on cleared screen. */
        CHARACTER_APPEAR,
        /** ObjCA routine $C: DEZ scroll reveals sky background. */
        CAMERA_SCROLL,
        /** ObjCA routine $E: ObjCC manages tornado sequence (6 sub-states). */
        MAIN_ENDING,
        /** Credits_Trigger set — terminal state. */
        TRIGGER_CREDITS
    }

    /** ObjCC tornado sub-states during MAIN_ENDING. */
    private enum TornadoSubState {
        /** Sub-state 0: Tornado approaches from left using ObjB2 body only. */
        APPROACH,
        /** Sub-state 2: Birds spawn, character on tornado, clouds horizontal. */
        BIRDS_AND_HOLD,
        /** Sub-state 4: 28-step rotation with position/frame tables. */
        ROTATION,
        /** Sub-state 6: Character departure, spawn ObjCE/ObjCF. */
        DEPARTURE,
        /** Sub-state 8: Camera pan with 7 delta pairs. */
        CAMERA_PAN,
        /** Sub-state $A: Super Sonic final position sequence. */
        SUPER_FINAL
    }

    /** ObjCD bird behavior states. */
    private enum BirdState {
        FLY_RIGHT,
        HOMING,
        EXIT_LEFT
    }

    // ========================================================================
    // Screen dimensions
    // ========================================================================

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    /** ROM: Camera_BG_Y_pos initial value ($C8 = 200). */
    private static final int INITIAL_BG_Y_POS = 0xC8;

    // ========================================================================
    // Photo sequence constants
    // ========================================================================

    private static final int[] PHOTO_MAP_ADDRS = {
            Sonic2Constants.MAP_ENG_ENDING1_ADDR,
            Sonic2Constants.MAP_ENG_ENDING2_ADDR,
            Sonic2Constants.MAP_ENG_ENDING3_ADDR,
            Sonic2Constants.MAP_ENG_ENDING4_ADDR
    };
    private static final int PHOTO_COUNT = 4;
    private static final int PHOTO_WIDTH_TILES = 12;
    private static final int PHOTO_HEIGHT_TILES = 9;
    private static final int PHOTO_X = 112;
    private static final int PHOTO_Y = 64;

    // ========================================================================
    // State fields
    // ========================================================================

    private CutsceneState state = CutsceneState.INIT;
    private int frameCounter;
    private int stateFrameCounter;

    // Art loader
    private Sonic2EndingArt endingArt;
    private Sonic2EndingArt.EndingRoutine routine;

    // Photo sequence state
    private int photoIndex;
    private int[][] photoMaps;

    // Palette fade state — ROM starts Normal_palette as all-white ($EEE) and runs
    // PaletteFadeFrom every frame to fade toward Target_palette.
    private Palette[] displayPalettes;
    private boolean paletteFadeActive;

    // CHARACTER_APPEAR state
    private int charAppearFrame;
    private int charAppearAnimTimer;

    // CAMERA_SCROLL state
    private float scrollProgress;

    // Background vertical scroll tracking (ROM: Camera_BG_Y_pos)
    // Starts at $C8 (200) during CHARACTER_APPEAR, increments during CAMERA_SCROLL
    private int bgYPos;

    // MAIN_ENDING: ObjCC tornado state
    private TornadoSubState tornadoSubState;
    private int tornadoTimer;
    private int tornadoXSub;
    private int tornadoYSub;
    private int tornadoXVel;
    private int tornadoYVel;
    private boolean cutSceneFlag;

    // Tornado rotation state (Sub-state 4)
    private int rotationStep;
    private int rotationFrameTimer;

    // Departure state (Sub-state 6)
    private int departureTimer;
    private int superDepartureStep;

    // Camera pan state (Sub-state 8)
    private int cameraPanStep;
    private int cameraPanFrameTimer;
    private int horizScrollOffset;
    private int vscrollOffset;
    private boolean standingFlag;

    // Super final state (Sub-state $A)
    private int superFinalStep;

    // Character on tornado position
    private int charOnTornadoX;
    private int charOnTornadoY;

    // ObjCE jumping character
    private boolean objCeActive;
    private int objCeX;
    private int objCeY;
    private int objCeSavedX;
    private int objCeSavedY;
    private int objCeFrame;
    private int objCeTimer;
    private int objCePhase;

    // ObjCF plane helixes
    private boolean objCfHelixActive;
    private int objCfHelixX;
    private int objCfHelixY;
    private int objCfHelixSavedX;
    private int objCfHelixSavedY;
    private int objCfHelixFrame;
    private int objCfHelixAnimTimer;

    // ObjCD birds
    private final List<Bird> birds = new ArrayList<>();
    private int birdSpawnCounter;
    private int birdSpawnDelay;
    private boolean birdsSpawning;

    // ObjCB clouds
    private final List<Cloud> clouds = new ArrayList<>();
    private int cloudSpawnTimer;

    // ROM-parsed sprite mappings
    private List<SpriteMappingFrame> objCfFrames;
    private List<SpriteMappingFrame> animalFrames;
    private List<SpriteMappingFrame> tornadoFrames;
    private List<SpriteMappingFrame> cloudFrames;

    private final PatternDesc reusableDesc = new PatternDesc();
    private final Random random = new Random();

    /**
     * Mega Drive color levels (0-7). Each MD color component uses values 0,$2,$4,$6,$8,$A,$C,$E
     * which map to these 0-255 scaled values via: {@code (level * 255 + 3) / 7}.
     */
    private static final int[] MD_COLOR_LEVELS = {0, 36, 73, 109, 146, 182, 219, 255};

    // ========================================================================
    // Inner data classes for entity tracking
    // ========================================================================

    private static class Bird {
        int xSub, ySub;
        int xVel, yVel;
        int initialYVel;
        int targetY;
        BirdState birdState;
        int stateTimer;
        int animFrame;
        int animTimer;

        Bird(int xSub, int ySub, int xVel, int yVel, int targetY) {
            this.xSub = xSub;
            this.ySub = ySub;
            this.xVel = xVel;
            this.yVel = yVel;
            this.initialYVel = yVel;
            this.targetY = targetY;
            this.birdState = BirdState.FLY_RIGHT;
        }
    }

    private static class Cloud {
        int xSub, ySub;
        int xVel, yVel;
        int frame;
        boolean horizontal;

        Cloud(int xSub, int ySub, int xVel, int yVel, int frame, boolean horizontal) {
            this.xSub = xSub;
            this.ySub = ySub;
            this.xVel = xVel;
            this.yVel = yVel;
            this.frame = frame;
            this.horizontal = horizontal;
        }
    }

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Initializes the ending cutscene: loads art, palettes, plays music, and
     * starts the photo loop.
     *
     * @param rom the ROM to load ending art from
     */
    public void initialize(Rom rom) {
        routine = Sonic2EndingArt.determineEndingRoutine();
        endingArt = new Sonic2EndingArt();
        endingArt.loadArt(rom, routine);
        endingArt.loadPalettes(rom, routine);
        endingArt.cacheToGpu();

        // ROM: Normal_palette filled with $0EEE0EEE (all white) at EndingSequence init
        initDisplayPalettes();

        // Decode all 4 photo Enigma maps
        photoMaps = new int[PHOTO_COUNT][];
        for (int i = 0; i < PHOTO_COUNT; i++) {
            photoMaps[i] = loadEnigmaMap(rom, PHOTO_MAP_ADDRS[i],
                    Sonic2Constants.ART_TILE_ENDING_PICS, "EndingPhoto" + (i + 1));
        }

        // Parse ROM sprite mappings for accurate cutscene rendering
        try {
            RomByteReader reader = RomByteReader.fromRom(rom);
            objCfFrames = S2SpriteDataLoader.loadMappingFrames(reader, Sonic2Constants.MAP_UNC_OBJCF_ADDR);
            animalFrames = S2SpriteDataLoader.loadMappingFrames(reader, Sonic2Constants.MAP_UNC_OBJ28_A_ADDR);
            tornadoFrames = S2SpriteDataLoader.loadMappingFrames(reader, Sonic2Constants.MAP_UNC_OBJB2_A_ADDR);
            cloudFrames = S2SpriteDataLoader.loadMappingFrames(reader, Sonic2Constants.MAP_UNC_CLOUD_ADDR);
            LOGGER.fine("Parsed sprite mappings: ObjCF=" + objCfFrames.size()
                    + " Obj28_a=" + animalFrames.size()
                    + " ObjB2=" + tornadoFrames.size()
                    + " ObjB3=" + cloudFrames.size() + " frames");
        } catch (IOException e) {
            LOGGER.warning("Failed to parse sprite mappings: " + e.getMessage());
        }

        // Play ending music
        AudioManager.getInstance().playMusic(Sonic2AudioConstants.MUS_ENDING);

        // Start photo loop at photo 0
        photoIndex = 0;
        frameCounter = 0;
        stateFrameCounter = 0;
        enterInit();

        LOGGER.info("Ending cutscene initialized with routine=" + routine);
    }

    // ========================================================================
    // Update
    // ========================================================================

    /**
     * Per-frame update. Advances the cutscene state machine.
     */
    public void update() {
        frameCounter++;
        stateFrameCounter++;

        // ROM: PaletteFadeFrom runs every frame in the ending main loop
        if (paletteFadeActive) {
            runPaletteFadeStep();
        }

        switch (state) {
            case INIT -> { /* immediate transition done in enterInit() */ }
            case PALETTE_WAIT_1 -> updatePaletteWait1();
            case PALETTE_SETUP_2 -> updatePaletteSetup2();
            case PALETTE_WAIT_2 -> updatePaletteWait2();
            case PHOTO_LOAD -> updatePhotoLoad();
            case CHARACTER_APPEAR -> updateCharacterAppear();
            case CAMERA_SCROLL -> updateCameraScroll();
            case MAIN_ENDING -> updateMainEnding();
            case TRIGGER_CREDITS -> { /* terminal */ }
        }
    }

    // ========================================================================
    // Draw
    // ========================================================================

    /**
     * Renders the current cutscene state.
     */
    public void draw() {
        if (endingArt == null || !endingArt.isInitialized()) {
            return;
        }

        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }

        switch (state) {
            case INIT, PALETTE_WAIT_1, PALETTE_SETUP_2, PALETTE_WAIT_2, PHOTO_LOAD ->
                    drawPhotoSequence(gm);
            case CHARACTER_APPEAR -> drawCharacterAppear(gm);
            case CAMERA_SCROLL -> drawCameraScroll(gm);
            case MAIN_ENDING -> drawMainEnding(gm);
            default -> { }
        }
    }

    // ========================================================================
    // Public accessors
    // ========================================================================

    /** Returns whether the cutscene is complete and credits should begin. */
    public boolean isDone() {
        return state == CutsceneState.TRIGGER_CREDITS;
    }

    /** Returns the current cutscene state (for debugging/testing). */
    public CutsceneState getState() {
        return state;
    }

    /**
     * Returns whether the cutscene is in a sky phase where the background should
     * show sky colors. ROM: VDP register $8720 = palette 2, color 0.
     */
    public boolean isSkyPhase() {
        return state == CutsceneState.CAMERA_SCROLL
                || state == CutsceneState.MAIN_ENDING;
    }

    /**
     * Returns whether the level background (DEZ star field) should be rendered
     * behind the cutscene sprites. Active during CAMERA_SCROLL and MAIN_ENDING.
     */
    public boolean needsLevelBackground() {
        return state == CutsceneState.CAMERA_SCROLL
                || state == CutsceneState.MAIN_ENDING;
    }

    /**
     * Returns the current background vertical scroll value (ROM: Vscroll_Factor_BG).
     * Starts at $C8 during CHARACTER_APPEAR, increments during CAMERA_SCROLL.
     */
    public int getBackgroundVscroll() {
        return bgYPos;
    }

    /**
     * Sets the OpenGL clear color for the ending cutscene.
     * Uses palette line 2 color 0 from display palettes (starts white, fades to target).
     * ROM reference: VDP register $8720 = palette 2, color 0.
     */
    public void setClearColor() {
        // Prefer display palettes (starts white, fades toward target)
        if (displayPalettes != null && displayPalettes.length > 2 && displayPalettes[2] != null) {
            Palette.Color bgColor = displayPalettes[2].getColor(0);
            org.lwjgl.opengl.GL11.glClearColor(bgColor.rFloat(), bgColor.gFloat(), bgColor.bFloat(), 1.0f);
            return;
        }
        // Fallback to target palettes
        if (endingArt != null && endingArt.getEndingPalettes() != null) {
            Palette[] palettes = endingArt.getEndingPalettes();
            if (palettes.length > 2 && palettes[2] != null) {
                Palette.Color bgColor = palettes[2].getColor(0);
                org.lwjgl.opengl.GL11.glClearColor(bgColor.rFloat(), bgColor.gFloat(), bgColor.bFloat(), 1.0f);
                return;
            }
        }
        org.lwjgl.opengl.GL11.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
    }

    // ========================================================================
    // Photo loop: INIT -> PALETTE_WAIT_1 -> PALETTE_SETUP_2 -> PALETTE_WAIT_2
    //             -> PHOTO_LOAD (cycles 4 times, then CHARACTER_APPEAR)
    // ========================================================================

    /**
     * Enters the INIT state for the current photo. Resets display palettes to
     * all-white and immediately transitions to PALETTE_WAIT_1.
     */
    private void enterInit() {
        state = CutsceneState.INIT;
        stateFrameCounter = 0;

        // Reset display palettes to all-white for this photo's fade-from-white
        resetDisplayPalettesToWhite();
        paletteFadeActive = true;

        // Immediately transition to PALETTE_WAIT_1 (ROM: routine 0 → routine 2)
        state = CutsceneState.PALETTE_WAIT_1;
        stateFrameCounter = 0;
    }

    private void updatePaletteWait1() {
        if (stateFrameCounter >= Sonic2CreditsData.PALETTE_WAIT_1_60FPS) {
            state = CutsceneState.PALETTE_SETUP_2;
            stateFrameCounter = 0;
        }
    }

    private void updatePaletteSetup2() {
        // ROM spawns second ObjC9 palette changer; we continue the same fade
        state = CutsceneState.PALETTE_WAIT_2;
        stateFrameCounter = 0;
    }

    private void updatePaletteWait2() {
        if (stateFrameCounter >= Sonic2CreditsData.PALETTE_WAIT_2) {
            state = CutsceneState.PHOTO_LOAD;
            stateFrameCounter = 0;
        }
    }

    private void updatePhotoLoad() {
        // Current photo displayed; advance to next
        photoIndex++;

        if (photoIndex < PHOTO_COUNT) {
            // Loop back for next photo with fresh white fade
            enterInit();
        } else {
            // All 4 photos done -> CHARACTER_APPEAR
            enterCharacterAppear();
        }
    }

    /**
     * Draws the current photo using Enigma-decoded tile data.
     * Photos are 12x9 tile grids rendered at screen position (112, 64).
     */
    private void drawPhotoSequence(GraphicsManager gm) {
        if (photoIndex < 0 || photoIndex >= PHOTO_COUNT) {
            return;
        }
        int[] map = photoMaps[photoIndex];
        if (map == null || map.length == 0) {
            return;
        }

        gm.beginPatternBatch();

        for (int ty = 0; ty < PHOTO_HEIGHT_TILES; ty++) {
            for (int tx = 0; tx < PHOTO_WIDTH_TILES; tx++) {
                int idx = ty * PHOTO_WIDTH_TILES + tx;
                if (idx >= map.length) continue;
                int word = map[idx];
                if (word == 0) continue;
                reusableDesc.set(word);
                int patternId = Sonic2EndingArt.PATTERN_BASE_PICS
                        + reusableDesc.getPatternIndex() - Sonic2Constants.ART_TILE_ENDING_PICS;
                gm.renderPatternWithId(patternId, reusableDesc,
                        PHOTO_X + tx * 8, PHOTO_Y + ty * 8);
            }
        }

        gm.flushPatternBatch();
    }

    // ========================================================================
    // CHARACTER_APPEAR (ObjCA routine $A)
    // ========================================================================

    private void enterCharacterAppear() {
        state = CutsceneState.CHARACTER_APPEAR;
        stateFrameCounter = 0;

        // Character at X=$A0 (160), Y=$50 (80)
        charAppearFrame = 5; // ObjCF anim 2 initial frame (floating)
        charAppearAnimTimer = 0;

        // ROM: EndingSequence sets Camera_BG_Y_pos = $C8
        bgYPos = INITIAL_BG_Y_POS;

        LOGGER.fine("Cutscene: entering CHARACTER_APPEAR");
    }

    private void updateCharacterAppear() {
        // Animate floating character: Ani_objCF anim 2: speed 1, frames 5->6 loop
        charAppearAnimTimer++;
        if (charAppearAnimTimer >= 2) { // speed 1 = tick every 2 frames
            charAppearAnimTimer = 0;
            charAppearFrame = (charAppearFrame == 5) ? 6 : 5;
        }

        if (stateFrameCounter >= Sonic2CreditsData.CHARACTER_APPEAR_HOLD) {
            enterCameraScroll();
        }
    }

    private void drawCharacterAppear(GraphicsManager gm) {
        gm.beginPatternBatch();
        drawObjCfFrame(gm, charAppearFrame, 0xA0, 0x50, -1);
        gm.flushPatternBatch();
    }

    // ========================================================================
    // CAMERA_SCROLL (ObjCA routine $C)
    // ========================================================================

    private void enterCameraScroll() {
        state = CutsceneState.CAMERA_SCROLL;
        stateFrameCounter = 0;
        scrollProgress = 0.0f;

        LOGGER.fine("Cutscene: entering CAMERA_SCROLL");
    }

    private void updateCameraScroll() {
        // ROM: SetHorizVertiScrollFlagsBG adds Camera_Y_pos_diff << 8 to Camera_BG_Y_pos
        // With Camera_Y_pos_diff=$100, this increments ~1px/frame (256 subpixels in 8.8 fixed-point)
        bgYPos++;

        int duration = Sonic2CreditsData.CAMERA_SCROLL_SONIC_60FPS;
        if (routine == Sonic2EndingArt.EndingRoutine.TAILS) {
            duration = Sonic2CreditsData.CAMERA_SCROLL_TAILS_60FPS;
        }

        scrollProgress = Math.min(1.0f, (float) stateFrameCounter / duration);

        if (stateFrameCounter >= duration) {
            enterMainEnding();
        }
    }

    private void drawCameraScroll(GraphicsManager gm) {
        // Background transitions from white to sky via palette fade (setClearColor)
        // Character still visible floating at center
        gm.beginPatternBatch();
        drawObjCfFrame(gm, charAppearFrame, 0xA0, 0x50, -1);
        gm.flushPatternBatch();
    }

    // ========================================================================
    // MAIN_ENDING (ObjCA routine $E) — ObjCC tornado sub-state machine
    // ========================================================================

    private void enterMainEnding() {
        state = CutsceneState.MAIN_ENDING;
        stateFrameCounter = 0;

        // ObjCC init: tornado starts off-screen left
        tornadoSubState = TornadoSubState.APPROACH;
        tornadoXSub = -0x10 << 8;
        tornadoYSub = 0xC0 << 8;
        tornadoXVel = Sonic2CreditsData.PLANE_X_SPEED;
        tornadoYVel = Sonic2CreditsData.PLANE_Y_SPEED;
        tornadoTimer = 0;
        cutSceneFlag = false;

        // Reset scroll offsets
        horizScrollOffset = 0;
        vscrollOffset = 0;
        standingFlag = false;

        // Reset entity lists
        birds.clear();
        clouds.clear();
        birdSpawnCounter = 0;
        birdSpawnDelay = 0;
        birdsSpawning = false;
        cloudSpawnTimer = 0;

        // Reset ObjCE/ObjCF
        objCeActive = false;
        objCfHelixActive = false;

        LOGGER.fine("Cutscene: entering MAIN_ENDING");
    }

    private void updateMainEnding() {
        // Update tornado sub-state machine
        switch (tornadoSubState) {
            case APPROACH -> updateTornadoApproach();
            case BIRDS_AND_HOLD -> updateBirdsAndHold();
            case ROTATION -> updateRotation();
            case DEPARTURE -> updateDeparture();
            case CAMERA_PAN -> updateCameraPan();
            case SUPER_FINAL -> updateSuperFinal();
        }

        // Update all active entities
        updateBirds();
        updateClouds();
        updateObjCe();
        updateObjCfHelix();

        // Check global credits trigger
        if (frameCounter >= Sonic2CreditsData.CREDITS_TRIGGER_60FPS) {
            state = CutsceneState.TRIGGER_CREDITS;
            LOGGER.info("Ending cutscene complete, triggering credits");
        }
    }

    // --- ObjCC Sub-state 0: Tornado Approach ---

    private void updateTornadoApproach() {
        tornadoXSub += tornadoXVel;
        tornadoYSub += tornadoYVel;

        if ((tornadoXSub >> 8) >= Sonic2CreditsData.PLANE_TARGET_X) {
            // Snap to target, clear velocities
            tornadoXSub = Sonic2CreditsData.PLANE_TARGET_X << 8;
            tornadoXVel = 0;
            tornadoYVel = 0;

            // Set hold timer and CutScene flag
            tornadoTimer = Sonic2CreditsData.PLANE_HOLD_FRAMES_60FPS;
            cutSceneFlag = true;

            // Position character on tornado
            int tornadoY = tornadoYSub >> 8;
            charOnTornadoX = tornadoXSub >> 8;
            charOnTornadoY = tornadoY - (routine == Sonic2EndingArt.EndingRoutine.TAILS ? 0x18 : 0x1C);

            // Begin bird spawning
            birdsSpawning = true;
            birdSpawnCounter = Sonic2CreditsData.BIRD_SPAWN_COUNT;
            birdSpawnDelay = 0;

            tornadoSubState = TornadoSubState.BIRDS_AND_HOLD;
            LOGGER.fine("Tornado arrived, entering BIRDS_AND_HOLD");
        }
    }

    // --- ObjCC Sub-state 2: Birds + Character on Tornado ---

    private void updateBirdsAndHold() {
        // Spawn birds at random intervals
        if (birdsSpawning && birdSpawnCounter > 0) {
            birdSpawnDelay--;
            if (birdSpawnDelay <= 0) {
                spawnBird();
                birdSpawnCounter--;
                birdSpawnDelay = random.nextInt(16); // 0-15 frame delay
            }
        }

        // Spawn clouds continuously while CutScene flag set (horizontal mode)
        if (cutSceneFlag) {
            spawnCloudIfNeeded();
        }

        // Super Sonic drifts: addi.l #$8000,x_pos; addq.w #1,y_pos
        if (routine == Sonic2EndingArt.EndingRoutine.SUPER_SONIC) {
            if (charOnTornadoX < 0xC0) {
                charOnTornadoX++;
            }
            if (charOnTornadoY < 0x90) {
                charOnTornadoY++;
            }
        }

        tornadoTimer--;
        if (tornadoTimer <= 0) {
            // Switch ObjCC to ObjCF mappings; adjust position
            // ROM: subi.w #$14,x_pos(a0); addi.w #$14,y_pos(a0)
            tornadoXSub -= 0x14 << 8;
            tornadoYSub += 0x14 << 8;

            rotationStep = 0;
            rotationFrameTimer = Sonic2CreditsData.ROTATION_FRAME_DELAY;
            tornadoSubState = TornadoSubState.ROTATION;
            LOGGER.fine("Entering ROTATION");
        }
    }

    // --- ObjCC Sub-state 4: Tornado Rotation ---

    private void updateRotation() {
        rotationFrameTimer--;
        if (rotationFrameTimer <= 0) {
            rotationFrameTimer = Sonic2CreditsData.ROTATION_FRAME_DELAY;
            rotationStep++;

            if (rotationStep >= Sonic2CreditsData.ROTATION_STEPS) {
                // Non-Super: move character off-screen
                if (routine != Sonic2EndingArt.EndingRoutine.SUPER_SONIC) {
                    charOnTornadoX = 0x200;
                    charOnTornadoY = 0;
                }

                departureTimer = Sonic2CreditsData.DEPARTURE_TIMER;
                superDepartureStep = 0;
                tornadoSubState = TornadoSubState.DEPARTURE;
                LOGGER.fine("Entering DEPARTURE");
                return;
            }
        }

        // Update tornado position from path table
        if (rotationStep < Sonic2CreditsData.TORNADO_PATH.length) {
            int[] pos = Sonic2CreditsData.TORNADO_PATH[rotationStep];
            tornadoXSub = pos[0] << 8;
            tornadoYSub = pos[1] << 8;
        }
    }

    // --- ObjCC Sub-state 6: Character Departure ---

    private void updateDeparture() {
        // Super Sonic follows separate position table
        if (routine == Sonic2EndingArt.EndingRoutine.SUPER_SONIC
                && superDepartureStep < Sonic2CreditsData.SUPER_SONIC_PATH.length) {
            int[] pos = Sonic2CreditsData.SUPER_SONIC_PATH[superDepartureStep];
            charOnTornadoX = pos[0];
            charOnTornadoY = pos[1];
            superDepartureStep++;
        }

        departureTimer--;
        if (departureTimer <= 0) {
            // Spawn ObjCF plane helixes at X=$10F, Y=$15E
            objCfHelixActive = true;
            objCfHelixX = 0x10F;
            objCfHelixY = 0x15E;
            objCfHelixSavedX = objCfHelixX;
            objCfHelixSavedY = objCfHelixY;
            objCfHelixFrame = 5; // anim 2 initial
            objCfHelixAnimTimer = 0;

            // Spawn ObjCE jumping character at X=$E8, Y=$118
            objCeActive = true;
            objCeX = 0xE8;
            objCeY = 0x118;
            objCeSavedX = objCeX;
            objCeSavedY = objCeY;
            objCeFrame = (routine == Sonic2EndingArt.EndingRoutine.TAILS) ? 0xF : 0xC;
            objCeTimer = 0;
            objCePhase = 0; // follow

            cameraPanStep = 0;
            cameraPanFrameTimer = Sonic2CreditsData.CAMERA_PAN_FRAME_DELAY;
            tornadoSubState = TornadoSubState.CAMERA_PAN;
            LOGGER.fine("Entering CAMERA_PAN");
        }
    }

    // --- ObjCC Sub-state 8: Camera Pan ---

    private void updateCameraPan() {
        cameraPanFrameTimer--;
        if (cameraPanFrameTimer <= 0) {
            cameraPanFrameTimer = Sonic2CreditsData.CAMERA_PAN_FRAME_DELAY;

            if (cameraPanStep < Sonic2CreditsData.CAMERA_PAN_STEPS) {
                int[] delta = Sonic2CreditsData.CAMERA_PAN_DELTAS[cameraPanStep];
                horizScrollOffset += delta[0];
                vscrollOffset += delta[1];
                cameraPanStep++;
            }

            if (cameraPanStep >= Sonic2CreditsData.CAMERA_PAN_STEPS) {
                standingFlag = true;

                if (routine == Sonic2EndingArt.EndingRoutine.SUPER_SONIC) {
                    superFinalStep = 0;
                    tornadoSubState = TornadoSubState.SUPER_FINAL;
                    LOGGER.fine("Entering SUPER_FINAL");
                }
                // Non-super: stay here, credits trigger via global timer
            }
        }
    }

    // --- ObjCC Sub-state $A: Super Sonic Final ---

    private void updateSuperFinal() {
        if (superFinalStep < Sonic2CreditsData.SUPER_FINAL_PATH.length) {
            int[] pos = Sonic2CreditsData.SUPER_FINAL_PATH[superFinalStep];
            charOnTornadoX = pos[0];
            charOnTornadoY = pos[1];
            superFinalStep++;
        }
        // Credits trigger via global timer
    }

    // ========================================================================
    // Entity management
    // ========================================================================

    private void spawnBird() {
        // X = -$A0 + (random & $7F) -> off-screen left
        int bx = (-0xA0 + (random.nextInt(0x80))) << 8;
        // Y = 8 + (random & $FF)
        int by = (8 + random.nextInt(0x100)) << 8;
        int yVel = (random.nextBoolean()) ? Sonic2CreditsData.BIRD_Y_VEL : -Sonic2CreditsData.BIRD_Y_VEL;
        int targetY = by >> 8;
        birds.add(new Bird(bx, by, Sonic2CreditsData.BIRD_X_VEL, yVel, targetY));
    }

    private void updateBirds() {
        var it = birds.iterator();
        while (it.hasNext()) {
            Bird bird = it.next();
            bird.stateTimer++;

            // Animate: Ani_objCD speed 5, frames 0->1 loop
            bird.animTimer++;
            if (bird.animTimer >= Sonic2CreditsData.BIRD_ANIM_SPEED) {
                bird.animTimer = 0;
                bird.animFrame = (bird.animFrame + 1) % 2;
            }

            switch (bird.birdState) {
                case FLY_RIGHT -> {
                    bird.xSub += bird.xVel;
                    bird.ySub += bird.yVel;
                    if (bird.stateTimer >= Sonic2CreditsData.BIRD_STATE0_FRAMES) {
                        bird.birdState = BirdState.HOMING;
                        bird.stateTimer = 0;
                        bird.xVel = 0;
                    }
                }
                case HOMING -> {
                    int currentY = bird.ySub >> 8;
                    if (currentY < bird.targetY) {
                        bird.yVel += 4;
                    } else if (currentY > bird.targetY) {
                        bird.yVel -= 4;
                    }
                    bird.ySub += bird.yVel;
                    if (bird.stateTimer >= Sonic2CreditsData.BIRD_STATE1_FRAMES) {
                        bird.birdState = BirdState.EXIT_LEFT;
                        bird.stateTimer = 0;
                        bird.xVel = -Sonic2CreditsData.BIRD_X_VEL;
                        bird.yVel = bird.initialYVel;
                    }
                }
                case EXIT_LEFT -> {
                    bird.xSub += bird.xVel;
                    bird.ySub += bird.yVel;
                    if (bird.stateTimer >= Sonic2CreditsData.BIRD_STATE2_FRAMES) {
                        it.remove();
                    }
                }
            }
        }
    }

    private void spawnCloudIfNeeded() {
        cloudSpawnTimer++;
        if (cloudSpawnTimer < 8) return;
        cloudSpawnTimer = 0;

        if (clouds.size() >= 12) return;

        // Horizontal mode: spawn from right side
        int cx = 0x150 << 8;
        int cy = random.nextInt(0x100) << 8;
        int cloudIdx = random.nextInt(Sonic2CreditsData.CLOUD_Y_VELS.length);
        // Cloud y_vel becomes x_vel in horizontal mode (drift left)
        int xVel = Sonic2CreditsData.CLOUD_Y_VELS[cloudIdx];
        int frame = Sonic2CreditsData.CLOUD_FRAMES[cloudIdx];
        clouds.add(new Cloud(cx, cy, xVel, 0, frame, true));
    }

    private void updateClouds() {
        var it = clouds.iterator();
        while (it.hasNext()) {
            Cloud cloud = it.next();
            cloud.xSub += cloud.xVel;
            cloud.ySub += cloud.yVel;

            if (cloud.horizontal) {
                if ((cloud.xSub >> 8) < -0x20) it.remove();
            } else {
                if ((cloud.ySub >> 8) < 0) it.remove();
            }
        }
    }

    private void updateObjCe() {
        if (!objCeActive) return;

        // Follow scroll offsets
        objCeX = objCeSavedX + horizScrollOffset;
        objCeY = objCeSavedY - vscrollOffset;

        if (standingFlag && objCePhase == 0) {
            objCePhase = 1; // start jump
            objCeTimer = 0;
        }

        if (objCePhase == 1) {
            objCeTimer++;
            // 4-frame tick, apply delta pairs
            if (objCeTimer % 4 == 0) {
                int deltaIdx = (objCeTimer / 4) - 1;
                int[][] deltas = (routine == Sonic2EndingArt.EndingRoutine.TAILS)
                        ? Sonic2CreditsData.CHAR_JUMP_DELTAS_TAILS
                        : Sonic2CreditsData.CHAR_JUMP_DELTAS_SONIC;
                if (deltaIdx >= 0 && deltaIdx < deltas.length) {
                    objCeSavedX += deltas[deltaIdx][0];
                    objCeSavedY += deltas[deltaIdx][1];
                    objCeFrame++;
                }
            }
        }
    }

    private void updateObjCfHelix() {
        if (!objCfHelixActive) return;

        // Follow scroll offsets
        objCfHelixX = objCfHelixSavedX + horizScrollOffset;
        objCfHelixY = objCfHelixSavedY - vscrollOffset;

        // Ani_objCF anim 2: speed 1, frames 5->6, $FF (stop after 6)
        objCfHelixAnimTimer++;
        if (objCfHelixAnimTimer >= 2 && objCfHelixFrame == 5) {
            objCfHelixFrame = 6;
        }
    }

    // ========================================================================
    // MAIN_ENDING rendering
    // ========================================================================

    private void drawMainEnding(GraphicsManager gm) {
        gm.beginPatternBatch();

        // Clouds (behind everything)
        drawClouds(gm);

        // Tornado
        drawTornado(gm);

        // Character on tornado (visible during BIRDS_AND_HOLD, ROTATION, DEPARTURE, SUPER_FINAL)
        if (tornadoSubState == TornadoSubState.BIRDS_AND_HOLD
                || tornadoSubState == TornadoSubState.ROTATION
                || tornadoSubState == TornadoSubState.DEPARTURE
                || tornadoSubState == TornadoSubState.SUPER_FINAL) {
            if (charOnTornadoX > -64 && charOnTornadoX < SCREEN_WIDTH
                    && charOnTornadoY > -64 && charOnTornadoY < SCREEN_HEIGHT) {
                int frame = getCharacterFrameForCurrentState();
                if (frame >= 0) {
                    drawObjCfFrame(gm, frame, charOnTornadoX, charOnTornadoY, -1);
                }
            }
        }

        // Birds
        drawBirds(gm);

        // ObjCF plane helixes
        if (objCfHelixActive) {
            drawObjCfFrame(gm, objCfHelixFrame, objCfHelixX, objCfHelixY, -1);
        }

        // ObjCE jumping character
        if (objCeActive && objCeFrame >= 0 && objCfFrames != null && objCeFrame < objCfFrames.size()) {
            drawObjCfFrame(gm, objCeFrame, objCeX, objCeY, -1);
        }

        gm.flushPatternBatch();
    }

    private int getCharacterFrameForCurrentState() {
        if (tornadoSubState == TornadoSubState.ROTATION
                && rotationStep >= 0 && rotationStep < Sonic2CreditsData.ROTATION_STEPS) {
            return switch (routine) {
                case SONIC -> Sonic2CreditsData.TORNADO_FRAMES_SONIC[rotationStep];
                case SUPER_SONIC -> Sonic2CreditsData.TORNADO_FRAMES_SUPER[rotationStep];
                case TAILS -> Sonic2CreditsData.TORNADO_FRAMES_TAILS[rotationStep];
            };
        }
        if (tornadoSubState == TornadoSubState.SUPER_FINAL) {
            return 0x17;
        }
        // BIRDS_AND_HOLD / DEPARTURE: floating frame
        return 5;
    }

    private void drawTornado(GraphicsManager gm) {
        int tx = tornadoXSub >> 8;
        int ty = tornadoYSub >> 8;

        if (tx < -64 || tx > SCREEN_WIDTH + 64 || ty < -64 || ty > SCREEN_HEIGHT + 64) {
            return;
        }

        switch (tornadoSubState) {
            case APPROACH, BIRDS_AND_HOLD -> {
                // ObjB2 body ONLY — NO ObjCF overlay during approach/hold
                // ROM: make_art_tile(ArtTile_ArtNem_Tornado, 0, 1) — palette 0, priority 1
                if (tornadoFrames != null && !tornadoFrames.isEmpty()) {
                    int animFrame = (frameCounter % 4);
                    if (animFrame >= tornadoFrames.size()) animFrame = 0;
                    int basePattern = Sonic2EndingArt.PATTERN_BASE_VRAM
                            + Sonic2Constants.ART_TILE_ENDING_TORNADO;
                    drawMappingFrame(gm, tornadoFrames, animFrame, tx, ty, basePattern, 0);
                }
            }
            case ROTATION, DEPARTURE, CAMERA_PAN, SUPER_FINAL -> {
                // ObjCF mappings after rotation switch
                int frame = getObjCfTornadoFrame();
                if (frame >= 0) {
                    drawObjCfFrame(gm, frame, tx, ty, -1);
                }
            }
        }
    }

    private int getObjCfTornadoFrame() {
        if (tornadoSubState == TornadoSubState.ROTATION
                && rotationStep >= 0 && rotationStep < Sonic2CreditsData.ROTATION_STEPS) {
            return switch (routine) {
                case SONIC -> Sonic2CreditsData.TORNADO_FRAMES_SONIC[rotationStep];
                case SUPER_SONIC -> Sonic2CreditsData.TORNADO_FRAMES_SUPER[rotationStep];
                case TAILS -> Sonic2CreditsData.TORNADO_FRAMES_TAILS[rotationStep];
            };
        }
        // Post-rotation default
        return 0xB;
    }

    private void drawClouds(GraphicsManager gm) {
        if (cloudFrames == null || cloudFrames.isEmpty()) return;

        for (Cloud cloud : clouds) {
            int cx = cloud.xSub >> 8;
            int cy = cloud.ySub >> 8;
            if (cx < -64 || cx > SCREEN_WIDTH + 64 || cy < -64 || cy > SCREEN_HEIGHT + 64) {
                continue;
            }
            int frameIdx = cloud.frame % cloudFrames.size();
            int basePattern = Sonic2EndingArt.PATTERN_BASE_VRAM + Sonic2Constants.ART_TILE_ENDING_CLOUDS;
            drawMappingFrame(gm, cloudFrames, frameIdx, cx, cy, basePattern, 2);
        }
    }

    private void drawBirds(GraphicsManager gm) {
        if (animalFrames == null || animalFrames.isEmpty()) return;

        for (Bird bird : birds) {
            int bx = bird.xSub >> 8;
            int by = bird.ySub >> 8;
            if (bx < -32 || bx > SCREEN_WIDTH + 32 || by < -32 || by > SCREEN_HEIGHT + 32) {
                continue;
            }
            int frameIdx = bird.animFrame % animalFrames.size();
            int basePattern = Sonic2EndingArt.PATTERN_BASE_VRAM + Sonic2Constants.ART_TILE_ENDING_ANIMAL_2;
            drawMappingFrame(gm, animalFrames, frameIdx, bx, by, basePattern, -1);
        }
    }

    // ========================================================================
    // ROM mapping frame rendering
    // ========================================================================

    /**
     * Draws an ObjCF mapping frame at the given screen position.
     * Uses PATTERN_BASE_VRAM so absolute VRAM tile indices in mapping pieces
     * resolve directly to GPU-cached patterns.
     */
    private void drawObjCfFrame(GraphicsManager gm, int frameIndex, int originX, int originY,
                                 int paletteOverride) {
        if (objCfFrames == null || frameIndex < 0 || frameIndex >= objCfFrames.size()) {
            return;
        }
        SpriteMappingFrame frame = objCfFrames.get(frameIndex);
        SpritePieceRenderer.renderPieces(
                frame.pieces(), originX, originY,
                Sonic2EndingArt.PATTERN_BASE_VRAM, paletteOverride,
                false, false,
                (patternIdx, pieceHFlip, pieceVFlip, palIdx, drawX, drawY) -> {
                    int descIndex = patternIdx & 0x7FF;
                    if (pieceHFlip) descIndex |= 0x800;
                    if (pieceVFlip) descIndex |= 0x1000;
                    descIndex |= (palIdx & 0x3) << 13;
                    reusableDesc.set(descIndex);
                    gm.renderPatternWithId(patternIdx, reusableDesc, drawX, drawY);
                });
    }

    /**
     * Generic mapping frame renderer for ObjB2 (tornado), ObjB3 (clouds), Obj28 (animals).
     */
    private void drawMappingFrame(GraphicsManager gm, List<SpriteMappingFrame> frames,
                                   int frameIndex, int originX, int originY,
                                   int basePatternIdx, int paletteOverride) {
        if (frames == null || frameIndex < 0 || frameIndex >= frames.size()) {
            return;
        }
        SpriteMappingFrame frame = frames.get(frameIndex);
        SpritePieceRenderer.renderPieces(
                frame.pieces(), originX, originY,
                basePatternIdx, paletteOverride,
                false, false,
                (patternIdx, pieceHFlip, pieceVFlip, palIdx, drawX, drawY) -> {
                    int descIndex = patternIdx & 0x7FF;
                    if (pieceHFlip) descIndex |= 0x800;
                    if (pieceVFlip) descIndex |= 0x1000;
                    descIndex |= (palIdx & 0x3) << 13;
                    reusableDesc.set(descIndex);
                    gm.renderPatternWithId(patternIdx, reusableDesc, drawX, drawY);
                });
    }

    // ========================================================================
    // Enigma map loading
    // ========================================================================

    private int[] loadEnigmaMap(Rom rom, int address, int startingArtTile, String name) {
        try {
            byte[] compressed = rom.readBytes(address, 1024);
            try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
                 ReadableByteChannel channel = Channels.newChannel(bais)) {
                byte[] decompressed = EnigmaReader.decompress(channel, startingArtTile);

                int wordCount = decompressed.length / 2;
                int[] map = new int[wordCount];
                ByteBuffer buf = ByteBuffer.wrap(decompressed);
                for (int i = 0; i < wordCount; i++) {
                    map[i] = buf.getShort() & 0xFFFF;
                }

                LOGGER.fine("Loaded " + name + " Enigma map: " + wordCount + " tiles");
                return map;
            }
        } catch (IOException e) {
            LOGGER.warning("Failed to load " + name + " Enigma map: " + e.getMessage());
            return new int[0];
        }
    }

    // ========================================================================
    // Palette fade system
    // ========================================================================

    /**
     * Initializes display palettes as all-white, matching ROM's EndingSequence init
     * where Normal_palette is filled with $0EEE (white) for every color entry.
     */
    private void initDisplayPalettes() {
        Palette[] targetPalettes = endingArt.getEndingPalettes();
        if (targetPalettes == null) return;

        displayPalettes = new Palette[targetPalettes.length];
        for (int i = 0; i < displayPalettes.length; i++) {
            displayPalettes[i] = new Palette();
            for (int c = 0; c < Palette.PALETTE_SIZE; c++) {
                displayPalettes[i].setColor(c,
                        new Palette.Color((byte) 0xFF, (byte) 0xFF, (byte) 0xFF));
            }
        }

        cacheDisplayPalettes();
        paletteFadeActive = true;
        LOGGER.fine("Display palettes initialized as all-white for fade-from-white effect");
    }

    /**
     * Resets display palettes back to all-white for the next photo's fade cycle.
     */
    private void resetDisplayPalettesToWhite() {
        if (displayPalettes == null) return;
        for (Palette palette : displayPalettes) {
            if (palette == null) continue;
            for (int c = 0; c < Palette.PALETTE_SIZE; c++) {
                palette.setColor(c,
                        new Palette.Color((byte) 0xFF, (byte) 0xFF, (byte) 0xFF));
            }
        }
        cacheDisplayPalettes();
        paletteFadeActive = true;
    }

    /**
     * Runs one step of PaletteFadeFrom: changes each color component by one MD level
     * toward the target palette. Deactivates when all colors match.
     */
    private void runPaletteFadeStep() {
        Palette[] targetPalettes = endingArt.getEndingPalettes();
        if (displayPalettes == null || targetPalettes == null) {
            paletteFadeActive = false;
            return;
        }

        boolean anyChanged = false;

        for (int line = 0; line < displayPalettes.length && line < targetPalettes.length; line++) {
            if (displayPalettes[line] == null || targetPalettes[line] == null) continue;
            for (int c = 0; c < Palette.PALETTE_SIZE; c++) {
                Palette.Color display = displayPalettes[line].getColor(c);
                Palette.Color target = targetPalettes[line].getColor(c);

                int newR = fadeColorStep(display.r & 0xFF, target.r & 0xFF);
                int newG = fadeColorStep(display.g & 0xFF, target.g & 0xFF);
                int newB = fadeColorStep(display.b & 0xFF, target.b & 0xFF);

                if (newR != (display.r & 0xFF) || newG != (display.g & 0xFF)
                        || newB != (display.b & 0xFF)) {
                    display.r = (byte) newR;
                    display.g = (byte) newG;
                    display.b = (byte) newB;
                    anyChanged = true;
                }
            }
        }

        if (anyChanged) {
            cacheDisplayPalettes();
        } else {
            paletteFadeActive = false;
            LOGGER.fine("Palette fade from white complete");
        }
    }

    /**
     * Fades a single color component by one MD level toward the target.
     */
    private static int fadeColorStep(int current, int target) {
        if (current == target) return current;
        if (current > target) {
            for (int i = MD_COLOR_LEVELS.length - 1; i >= 0; i--) {
                if (MD_COLOR_LEVELS[i] < current) {
                    return Math.max(MD_COLOR_LEVELS[i], target);
                }
            }
            return target;
        } else {
            for (int i = 0; i < MD_COLOR_LEVELS.length; i++) {
                if (MD_COLOR_LEVELS[i] > current) {
                    return Math.min(MD_COLOR_LEVELS[i], target);
                }
            }
            return target;
        }
    }

    private void cacheDisplayPalettes() {
        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null || gm.isHeadlessMode() || displayPalettes == null) return;
        for (int i = 0; i < displayPalettes.length; i++) {
            if (displayPalettes[i] != null) {
                gm.cachePaletteTexture(displayPalettes[i], i);
            }
        }
    }
}
