package com.openggf.game.sonic2.credits;

import com.openggf.audio.AudioManager;
import com.openggf.data.Rom;
import com.openggf.data.RomManager;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.constants.Sonic2AudioConstants;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;
import com.openggf.level.Pattern;
import com.openggf.tools.EnigmaReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.logging.Logger;

/**
 * State machine for the Sonic 2 ending cutscene sequence.
 * <p>
 * Modeled on the ROM's ObjCA (ending controller) routines from s2.asm lines 13109-13623.
 * The cutscene plays after defeating the Death Egg Robot and shows:
 * <ol>
 *   <li>PHOTO_SEQUENCE: 4 ending photos displayed sequentially</li>
 *   <li>SKY_FALL: Character falling through sky with scrolling clouds</li>
 *   <li>PLANE_APPROACH: Tornado plane enters from the left</li>
 *   <li>PLANE_RESCUE: Character boards the plane, birds spawn</li>
 *   <li>FLY_AWAY: Plane exits screen to the right</li>
 *   <li>TRIGGER_CREDITS: Terminal state, signals provider to begin credits</li>
 * </ol>
 * <p>
 * This is a self-contained renderer; it does not use the game's level/object system.
 * All rendering happens in {@link #draw()} using PatternDesc-based tile rendering.
 */
public class Sonic2EndingCutsceneManager {

    private static final Logger LOGGER = Logger.getLogger(Sonic2EndingCutsceneManager.class.getName());

    // ========================================================================
    // Cutscene states
    // ========================================================================

    /**
     * States of the ending cutscene, corresponding to ObjCA routine progression.
     */
    public enum CutsceneState {
        /** Loading art and initializing. */
        INIT,
        /** Displaying 4 ending photos sequentially. */
        PHOTO_SEQUENCE,
        /** Character falling through sky with clouds. */
        SKY_FALL,
        /** Tornado plane approaching from the left. */
        PLANE_APPROACH,
        /** Character boarding the plane, birds flying. */
        PLANE_RESCUE,
        /** Plane exiting screen to the right. */
        FLY_AWAY,
        /** Terminal state: cutscene is complete. */
        TRIGGER_CREDITS
    }

    // ========================================================================
    // Screen dimensions
    // ========================================================================

    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    // ========================================================================
    // Photo sequence constants
    // ========================================================================

    /**
     * Photo Enigma map addresses in ROM order.
     * ROM reference: ObjCA routines 0-6, MapEng_Ending1 through MapEng_Ending4.
     */
    private static final int[] PHOTO_MAP_ADDRS = {
            Sonic2Constants.MAP_ENG_ENDING1_ADDR,
            Sonic2Constants.MAP_ENG_ENDING2_ADDR,
            Sonic2Constants.MAP_ENG_ENDING3_ADDR,
            Sonic2Constants.MAP_ENG_ENDING4_ADDR
    };

    /** Number of photos in the sequence. */
    private static final int PHOTO_COUNT = 4;

    /**
     * Photo display area dimensions (in tiles).
     * ROM reference: PlaneMapToVRAM_H40 with dimensions 12x9 at planeLoc(64,14,8).
     */
    private static final int PHOTO_WIDTH_TILES = 12;
    private static final int PHOTO_HEIGHT_TILES = 9;

    /**
     * Photo display position on screen (in pixels).
     * ROM: planeLoc(planeWidth=64, startCol=14, startRow=8) -> pixel (14*8, 8*8) = (112, 64).
     */
    private static final int PHOTO_X = 112;
    private static final int PHOTO_Y = 64;

    // ========================================================================
    // Sky fall constants
    // ========================================================================

    /** Character sprite position during sky fall. */
    private static final int SKY_FALL_CHAR_X = SCREEN_WIDTH / 2;
    /** ROM: move.w #$50,y_pos(a1) (s2.asm:13467) */
    private static final int SKY_FALL_CHAR_Y = 0x50;

    /** Cloud Y positions and speeds for parallax effect. */
    private static final int[] CLOUD_INIT_Y = {40, 80, 140};

    // ========================================================================
    // Plane approach constants
    // ========================================================================

    /** Plane starts off-screen left. */
    private static final int PLANE_START_X_SUB = -0x10 << 8;
    /** Plane Y start position (subpixels). ROM: move.w #$C0,y_pos(a0) (s2.asm:13549) */
    private static final int PLANE_START_Y_SUB = 0xC0 << 8;

    // ========================================================================
    // State
    // ========================================================================

    private CutsceneState state = CutsceneState.INIT;
    private int frameCounter;
    private int stateFrameCounter;

    // Art loader
    private Sonic2EndingArt endingArt;
    private Sonic2EndingArt.EndingRoutine routine;

    // Photo sequence state
    private int currentPhotoIndex;
    private int[][] photoMaps;

    // Sky fall state
    private int[] cloudYSub;  // Cloud Y positions in subpixels (256 = 1px)

    // Plane approach/rescue/fly-away state
    private int planeXSub;    // Plane X in subpixels
    private int planeYSub;    // Plane Y in subpixels
    private int characterXSub; // Character X in subpixels (during rescue)
    private int characterYSub; // Character Y in subpixels

    // Bird state (simple: track 3 birds)
    private int[] birdXSub;
    private int[] birdYSub;
    private int birdAnimFrame;
    private int birdAnimTimer;

    // Reusable PatternDesc for tile rendering
    private final PatternDesc reusableDesc = new PatternDesc();

    // ========================================================================
    // Lifecycle
    // ========================================================================

    /**
     * Initializes the ending cutscene: loads art, palettes, plays music, and
     * transitions to the photo sequence.
     *
     * @param rom the ROM to load ending art from
     */
    public void initialize(Rom rom) {
        routine = Sonic2EndingArt.determineEndingRoutine();
        endingArt = new Sonic2EndingArt();
        endingArt.loadArt(rom, routine);
        endingArt.loadPalettes(rom, routine);
        endingArt.cacheToGpu();

        // Decode all 4 photo Enigma maps
        photoMaps = new int[PHOTO_COUNT][];
        for (int i = 0; i < PHOTO_COUNT; i++) {
            photoMaps[i] = loadEnigmaMap(rom, PHOTO_MAP_ADDRS[i],
                    Sonic2Constants.ART_TILE_ENDING_PICS, "EndingPhoto" + (i + 1));
        }

        // Play ending music
        AudioManager.getInstance().playMusic(Sonic2AudioConstants.MUS_ENDING);

        // Transition to photo sequence
        currentPhotoIndex = 0;
        frameCounter = 0;
        stateFrameCounter = 0;
        state = CutsceneState.PHOTO_SEQUENCE;

        LOGGER.info("Ending cutscene initialized with routine=" + routine);
    }

    /**
     * Per-frame update. Advances the cutscene state machine.
     */
    public void update() {
        frameCounter++;
        stateFrameCounter++;

        switch (state) {
            case PHOTO_SEQUENCE -> updatePhotoSequence();
            case SKY_FALL -> updateSkyFall();
            case PLANE_APPROACH -> updatePlaneApproach();
            case PLANE_RESCUE -> updatePlaneRescue();
            case FLY_AWAY -> updateFlyAway();
            case TRIGGER_CREDITS -> { /* no-op, picked up by provider */ }
            default -> { }
        }
    }

    /**
     * Renders the current cutscene state.
     */
    public void draw() {
        if (!endingArt.isInitialized()) {
            return;
        }

        GraphicsManager gm = GraphicsManager.getInstance();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }

        switch (state) {
            case PHOTO_SEQUENCE -> drawPhotoSequence(gm);
            case SKY_FALL -> drawSkyFall(gm);
            case PLANE_APPROACH -> drawPlaneApproach(gm);
            case PLANE_RESCUE -> drawPlaneRescue(gm);
            case FLY_AWAY -> drawFlyAway(gm);
            default -> { }
        }
    }

    /**
     * Returns whether the cutscene is complete and credits should begin.
     */
    public boolean isDone() {
        return state == CutsceneState.TRIGGER_CREDITS;
    }

    /**
     * Returns the current cutscene state (for debugging/testing).
     */
    public CutsceneState getState() {
        return state;
    }

    // ========================================================================
    // PHOTO_SEQUENCE
    // ========================================================================

    /**
     * Updates the photo sequence state.
     * Each photo is held for PHOTO_HOLD_FRAMES, then advances to the next.
     * After all 4 photos, transitions to SKY_FALL.
     */
    private void updatePhotoSequence() {
        if (stateFrameCounter >= Sonic2CreditsData.PHOTO_HOLD_FRAMES) {
            currentPhotoIndex++;
            stateFrameCounter = 0;

            if (currentPhotoIndex >= PHOTO_COUNT) {
                transitionToSkyFall();
            }
        }
    }

    /**
     * Draws the current photo using Enigma-decoded tile data.
     * <p>
     * ROM reference: Photos are Enigma-mapped tilemaps decoded with
     * startingArtTile = ArtTile_ArtNem_EndingPics. The decoded words are VDP
     * nametable entries containing tile index, palette, and flip flags.
     * Each photo is a 12x9 tile grid rendered at screen position (112, 64).
     */
    private void drawPhotoSequence(GraphicsManager gm) {
        if (currentPhotoIndex < 0 || currentPhotoIndex >= PHOTO_COUNT) {
            return;
        }
        int[] map = photoMaps[currentPhotoIndex];
        if (map == null || map.length == 0) {
            return;
        }

        gm.beginPatternBatch();

        for (int ty = 0; ty < PHOTO_HEIGHT_TILES; ty++) {
            for (int tx = 0; tx < PHOTO_WIDTH_TILES; tx++) {
                int idx = ty * PHOTO_WIDTH_TILES + tx;
                if (idx >= map.length) {
                    continue;
                }
                int word = map[idx];
                if (word == 0) {
                    continue;
                }
                reusableDesc.set(word);
                int patternId = Sonic2EndingArt.PATTERN_BASE_PICS + reusableDesc.getPatternIndex();
                gm.renderPatternWithId(patternId, reusableDesc,
                        PHOTO_X + tx * 8, PHOTO_Y + ty * 8);
            }
        }

        gm.flushPatternBatch();
    }

    // ========================================================================
    // SKY_FALL
    // ========================================================================

    /**
     * Transitions to the sky fall phase.
     * Initializes cloud positions for parallax scrolling.
     */
    private void transitionToSkyFall() {
        state = CutsceneState.SKY_FALL;
        stateFrameCounter = 0;

        // Initialize 3 cloud layers at different Y positions (subpixels)
        cloudYSub = new int[3];
        for (int i = 0; i < CLOUD_INIT_Y.length; i++) {
            cloudYSub[i] = CLOUD_INIT_Y[i] << 8;
        }

        LOGGER.fine("Cutscene: entering SKY_FALL");
    }

    /**
     * Updates the sky fall state.
     * Clouds scroll upward at varying speeds. Duration: SKY_FALL_FRAMES.
     */
    private void updateSkyFall() {
        if (cloudYSub != null) {
            // Three cloud layers at different speeds
            cloudYSub[0] += Sonic2CreditsData.CLOUD_SPEED_FAST;
            cloudYSub[1] += Sonic2CreditsData.CLOUD_SPEED_MED;
            cloudYSub[2] += Sonic2CreditsData.CLOUD_SPEED_SLOW;

            // Wrap clouds that scroll off the top of the screen
            for (int i = 0; i < cloudYSub.length; i++) {
                if ((cloudYSub[i] >> 8) < -32) {
                    cloudYSub[i] = (SCREEN_HEIGHT + 32) << 8;
                }
            }
        }

        if (stateFrameCounter >= Sonic2CreditsData.SKY_FALL_FRAMES) {
            transitionToPlaneApproach();
        }
    }

    /**
     * Draws the sky fall scene: clouds and falling character.
     */
    private void drawSkyFall(GraphicsManager gm) {
        gm.beginPatternBatch();

        // Draw clouds at their current Y positions
        // Each cloud is rendered as a simple sprite from the cloud pattern set
        if (cloudYSub != null) {
            drawCloudLayer(gm, 0, 40, cloudYSub[0] >> 8);
            drawCloudLayer(gm, 1, 160, cloudYSub[1] >> 8);
            drawCloudLayer(gm, 2, 240, cloudYSub[2] >> 8);
        }

        // Draw character sprite at center
        // Use first few patterns from character art as a simple sprite representation
        drawCharacterSprite(gm, SKY_FALL_CHAR_X, SKY_FALL_CHAR_Y);

        gm.flushPatternBatch();
    }

    // ========================================================================
    // PLANE_APPROACH
    // ========================================================================

    /**
     * Transitions to the plane approach phase.
     */
    private void transitionToPlaneApproach() {
        state = CutsceneState.PLANE_APPROACH;
        stateFrameCounter = 0;

        planeXSub = PLANE_START_X_SUB;
        planeYSub = PLANE_START_Y_SUB;

        LOGGER.fine("Cutscene: entering PLANE_APPROACH");
    }

    /**
     * Updates the plane approach state.
     * Tornado moves from left to target X position.
     * ROM reference: ObjCC (tornado) movement, ObjCA routines 10-12.
     */
    private void updatePlaneApproach() {
        // Move plane rightward and slightly upward
        planeXSub += Sonic2CreditsData.PLANE_X_SPEED;
        planeYSub += Sonic2CreditsData.PLANE_Y_SPEED;

        // Clouds continue scrolling during plane approach (slower, horizontal feel)
        if (cloudYSub != null) {
            cloudYSub[0] += Sonic2CreditsData.CLOUD_SPEED_SLOW;
            cloudYSub[1] += Sonic2CreditsData.CLOUD_SPEED_SLOW / 2;
            cloudYSub[2] += Sonic2CreditsData.CLOUD_SPEED_SLOW / 4;

            for (int i = 0; i < cloudYSub.length; i++) {
                if ((cloudYSub[i] >> 8) < -32) {
                    cloudYSub[i] = (SCREEN_HEIGHT + 32) << 8;
                }
            }
        }

        // Check if plane has reached target position
        int planeX = planeXSub >> 8;
        if (planeX >= Sonic2CreditsData.PLANE_TARGET_X) {
            transitionToPlaneRescue();
        }
    }

    /**
     * Draws the plane approach scene: clouds, character falling, and approaching tornado.
     */
    private void drawPlaneApproach(GraphicsManager gm) {
        gm.beginPatternBatch();

        // Draw clouds
        if (cloudYSub != null) {
            drawCloudLayer(gm, 0, 40, cloudYSub[0] >> 8);
            drawCloudLayer(gm, 1, 160, cloudYSub[1] >> 8);
            drawCloudLayer(gm, 2, 240, cloudYSub[2] >> 8);
        }

        // Draw character at center (still falling/waiting)
        drawCharacterSprite(gm, SKY_FALL_CHAR_X, SKY_FALL_CHAR_Y);

        // Draw mini tornado approaching from left
        drawMiniTornado(gm, planeXSub >> 8, planeYSub >> 8);

        gm.flushPatternBatch();
    }

    // ========================================================================
    // PLANE_RESCUE
    // ========================================================================

    /**
     * Transitions to the plane rescue phase.
     * Character boards the plane and birds spawn.
     */
    private void transitionToPlaneRescue() {
        state = CutsceneState.PLANE_RESCUE;
        stateFrameCounter = 0;

        // Position character on the plane
        characterXSub = planeXSub;
        characterYSub = planeYSub - (16 << 8); // Character sits on top of plane

        // Initialize birds (3 birds at varying positions)
        birdXSub = new int[3];
        birdYSub = new int[3];
        birdXSub[0] = (planeXSub >> 8) + 40 << 8;
        birdYSub[0] = (planeYSub >> 8) - 40 << 8;
        birdXSub[1] = (planeXSub >> 8) + 60 << 8;
        birdYSub[1] = (planeYSub >> 8) - 20 << 8;
        birdXSub[2] = (planeXSub >> 8) + 50 << 8;
        birdYSub[2] = (planeYSub >> 8) - 60 << 8;
        birdAnimFrame = 0;
        birdAnimTimer = 0;

        LOGGER.fine("Cutscene: entering PLANE_RESCUE");
    }

    /**
     * Updates the plane rescue state.
     * Short boarding animation, then transition to fly away.
     * ROM reference: ObjCC states 2-4 (s2.asm ~lines 13579-13623).
     */
    private void updatePlaneRescue() {
        // Animate birds
        birdAnimTimer++;
        if (birdAnimTimer >= 8) {
            birdAnimTimer = 0;
            birdAnimFrame = (birdAnimFrame + 1) % 2;
        }

        // Birds drift slightly
        if (birdXSub != null) {
            for (int i = 0; i < birdXSub.length; i++) {
                birdXSub[i] += 0x40;  // Drift right slowly
                birdYSub[i] += (i % 2 == 0) ? -0x20 : 0x20; // Oscillate
            }
        }

        // Character settles onto plane
        if (characterYSub < planeYSub - (8 << 8)) {
            characterYSub += 0x80;
        }

        // After boarding animation, transition to fly away
        // ROM uses a short hold (~64 frames for boarding)
        if (stateFrameCounter >= 64) {
            transitionToFlyAway();
        }
    }

    /**
     * Draws the plane rescue scene.
     */
    private void drawPlaneRescue(GraphicsManager gm) {
        gm.beginPatternBatch();

        // Draw clouds
        if (cloudYSub != null) {
            drawCloudLayer(gm, 0, 40, cloudYSub[0] >> 8);
            drawCloudLayer(gm, 1, 160, cloudYSub[1] >> 8);
            drawCloudLayer(gm, 2, 240, cloudYSub[2] >> 8);
        }

        // Draw birds
        drawBirds(gm);

        // Draw mini tornado with character on top
        drawMiniTornado(gm, planeXSub >> 8, planeYSub >> 8);
        drawCharacterSprite(gm, characterXSub >> 8, characterYSub >> 8);

        gm.flushPatternBatch();
    }

    // ========================================================================
    // FLY_AWAY
    // ========================================================================

    /**
     * Transitions to the fly away phase.
     * Plane exits screen to the right.
     */
    private void transitionToFlyAway() {
        state = CutsceneState.FLY_AWAY;
        stateFrameCounter = 0;

        LOGGER.fine("Cutscene: entering FLY_AWAY");
    }

    /**
     * Updates the fly away state.
     * Plane moves rightward. When off-screen, transition to TRIGGER_CREDITS.
     */
    private void updateFlyAway() {
        // Move plane and character rightward
        planeXSub += Sonic2CreditsData.PLANE_X_SPEED * 2; // Accelerate exit
        planeYSub += Sonic2CreditsData.PLANE_Y_SPEED / 2; // Slight upward drift
        characterXSub = planeXSub;
        characterYSub = planeYSub - (8 << 8);

        // Birds follow the plane
        if (birdXSub != null) {
            for (int i = 0; i < birdXSub.length; i++) {
                birdXSub[i] += Sonic2CreditsData.PLANE_X_SPEED * 2;
                birdYSub[i] += (i % 2 == 0) ? -0x20 : 0x20;
            }
        }

        // Animate birds
        birdAnimTimer++;
        if (birdAnimTimer >= 8) {
            birdAnimTimer = 0;
            birdAnimFrame = (birdAnimFrame + 1) % 2;
        }

        // When plane exits screen (X > 352), cutscene is done
        if ((planeXSub >> 8) > SCREEN_WIDTH + 32) {
            state = CutsceneState.TRIGGER_CREDITS;
            LOGGER.info("Ending cutscene complete, triggering credits");
        }
    }

    /**
     * Draws the fly away scene.
     */
    private void drawFlyAway(GraphicsManager gm) {
        gm.beginPatternBatch();

        // Draw clouds
        if (cloudYSub != null) {
            drawCloudLayer(gm, 0, 40, cloudYSub[0] >> 8);
            drawCloudLayer(gm, 1, 160, cloudYSub[1] >> 8);
            drawCloudLayer(gm, 2, 240, cloudYSub[2] >> 8);
        }

        // Draw birds
        drawBirds(gm);

        // Draw plane with character
        int px = planeXSub >> 8;
        int py = planeYSub >> 8;
        if (px < SCREEN_WIDTH + 64 && px > -64) {
            drawMiniTornado(gm, px, py);
            drawCharacterSprite(gm, characterXSub >> 8, characterYSub >> 8);
        }

        gm.flushPatternBatch();
    }

    // ========================================================================
    // Sprite rendering helpers
    // ========================================================================

    /**
     * Draws a cloud sprite layer.
     * Uses cloud patterns as a simple 4x2 tile block at the given position.
     *
     * @param layerIndex cloud layer (0-2) for pattern offset variety
     * @param x          screen X position
     * @param y          screen Y position
     */
    private void drawCloudLayer(GraphicsManager gm, int layerIndex, int x, int y) {
        Pattern[] clouds = endingArt.getCloudPatterns();
        if (clouds == null || clouds.length == 0) {
            return;
        }

        // Render a 4x2 tile cloud block using sequential patterns
        int startTile = (layerIndex * 8) % clouds.length;
        int tilesW = 4;
        int tilesH = 2;
        for (int ty = 0; ty < tilesH; ty++) {
            for (int tx = 0; tx < tilesW; tx++) {
                int tileIdx = startTile + ty * tilesW + tx;
                if (tileIdx >= clouds.length) {
                    break;
                }
                // Build a VDP word: palette 2 (background), no flip, no priority
                int word = tileIdx | (2 << 13);
                reusableDesc.set(word);
                int patternId = Sonic2EndingArt.PATTERN_BASE_CLOUDS + tileIdx;
                gm.renderPatternWithId(patternId, reusableDesc,
                        x + tx * 8, y + ty * 8);
            }
        }
    }

    /**
     * Draws the character sprite (Sonic/Super Sonic/Tails) as a simple 3x4 tile block.
     * <p>
     * This is a simplified representation. The ROM uses full sprite mappings
     * (MAP_UNC_OBJCF_ADDR) for the mini ending sprites.
     * TODO: Parse ObjCF mappings for accurate frame rendering.
     */
    private void drawCharacterSprite(GraphicsManager gm, int x, int y) {
        Pattern[] character = endingArt.getCharacterPatterns();
        if (character == null || character.length == 0) {
            return;
        }

        // Render a 3x4 tile character block from the character art
        int tilesW = 3;
        int tilesH = 4;
        int originX = x - (tilesW * 4); // Center horizontally
        int originY = y - (tilesH * 4); // Center vertically
        for (int ty = 0; ty < tilesH; ty++) {
            for (int tx = 0; tx < tilesW; tx++) {
                int tileIdx = tx * tilesH + ty; // Column-major VDP ordering
                if (tileIdx >= character.length) {
                    break;
                }
                // Palette 0 (character palette), no flip
                int word = tileIdx;
                reusableDesc.set(word);
                int patternId = Sonic2EndingArt.PATTERN_BASE_CHARACTER + tileIdx;
                gm.renderPatternWithId(patternId, reusableDesc,
                        originX + tx * 8, originY + ty * 8);
            }
        }
    }

    /**
     * Draws the mini tornado sprite as a 4x3 tile block.
     * TODO: Parse MAP_UNC_OBJCF_ADDR for accurate tornado frame rendering.
     */
    private void drawMiniTornado(GraphicsManager gm, int x, int y) {
        Pattern[] tornado = endingArt.getMiniTornadoPatterns();
        if (tornado == null || tornado.length == 0) {
            return;
        }

        int tilesW = 4;
        int tilesH = 3;
        for (int ty = 0; ty < tilesH; ty++) {
            for (int tx = 0; tx < tilesW; tx++) {
                int tileIdx = tx * tilesH + ty; // Column-major
                if (tileIdx >= tornado.length) {
                    break;
                }
                // Palette 0, no flip
                int word = tileIdx;
                reusableDesc.set(word);
                int patternId = Sonic2EndingArt.PATTERN_BASE_MINI_TORNADO + tileIdx;
                gm.renderPatternWithId(patternId, reusableDesc,
                        x + tx * 8, y + ty * 8);
            }
        }
    }

    /**
     * Draws bird sprites (ObjCD animals).
     * Uses animal patterns with simple 2-frame wing flap animation.
     * TODO: Parse MAP_UNC_OBJ28_A_ADDR for accurate animal frame rendering.
     */
    private void drawBirds(GraphicsManager gm) {
        if (birdXSub == null || endingArt.getAnimalPatterns() == null) {
            return;
        }
        Pattern[] animal = endingArt.getAnimalPatterns();
        if (animal.length == 0) {
            return;
        }

        for (int b = 0; b < birdXSub.length; b++) {
            int bx = birdXSub[b] >> 8;
            int by = birdYSub[b] >> 8;

            // Skip if off-screen
            if (bx < -32 || bx > SCREEN_WIDTH + 32 || by < -32 || by > SCREEN_HEIGHT + 32) {
                continue;
            }

            // Draw a 2x2 bird sprite, using animation frame offset
            int frameOffset = birdAnimFrame * 4;
            for (int ty = 0; ty < 2; ty++) {
                for (int tx = 0; tx < 2; tx++) {
                    int tileIdx = frameOffset + tx * 2 + ty; // Column-major
                    if (tileIdx >= animal.length) {
                        break;
                    }
                    int word = tileIdx;
                    reusableDesc.set(word);
                    int patternId = Sonic2EndingArt.PATTERN_BASE_ANIMAL + tileIdx;
                    gm.renderPatternWithId(patternId, reusableDesc,
                            bx + tx * 8, by + ty * 8);
                }
            }
        }
    }

    // ========================================================================
    // Enigma map loading
    // ========================================================================

    /**
     * Loads an Enigma-compressed tilemap from ROM.
     *
     * @param rom            ROM to read from
     * @param address        ROM address of the Enigma data
     * @param startingArtTile starting art tile index to add to decoded values
     * @param name           descriptive name for logging
     * @return array of VDP nametable words, or empty array on failure
     */
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
}
