package com.openggf.game.sonic3k.specialstage;

import com.openggf.audio.AudioManager;
import com.openggf.game.GameServices;
import com.openggf.game.GameStateManager;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Pattern;

import java.io.IOException;
import java.util.logging.Logger;

import static com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageConstants.*;

/**
 * Core coordinator for the Sonic 3&K Blue Ball special stage.
 * <p>
 * Manages the complete lifecycle: grid loading, player movement, collision
 * detection, sphere-to-ring conversion, perspective rendering, HUD, banner,
 * and stage completion.
 * <p>
 * Reference: docs/skdisasm/sonic3k.asm SpecialStage (line 10585)
 */
public class Sonic3kSpecialStageManager {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kSpecialStageManager.class.getName());

    private static Sonic3kSpecialStageManager instance;

    // ==================== Core State ====================

    private int currentStage;
    private boolean initialized;
    private boolean finished;
    private boolean emeraldCollected;
    private int ringsCollected;
    private int spheresLeft;
    private int frameCounter;

    // ==================== Subsystems ====================

    private final Sonic3kSpecialStageGrid grid = new Sonic3kSpecialStageGrid();
    private final Sonic3kSpecialStagePlayer player = new Sonic3kSpecialStagePlayer();
    private final Sonic3kSpecialStageCollision collision = new Sonic3kSpecialStageCollision();
    private final Sonic3kSpecialStageRingConverter ringConverter = new Sonic3kSpecialStageRingConverter();
    private final Sonic3kSpecialStageCollisionQueue collisionQueue = new Sonic3kSpecialStageCollisionQueue();
    private final Sonic3kSpecialStagePerspective perspective = new Sonic3kSpecialStagePerspective();
    private final Sonic3kSpecialStageBackground background = new Sonic3kSpecialStageBackground();
    private final Sonic3kSpecialStageHud hud = new Sonic3kSpecialStageHud();
    private final Sonic3kSpecialStageBanner banner = new Sonic3kSpecialStageBanner();

    private Sonic3kSpecialStageRenderer renderer;
    private Sonic3kSpecialStagePalette palette;
    private Sonic3kSpecialStageDataLoader dataLoader;

    // ==================== Input State ====================

    private int heldButtons;
    private int pressedButtons;

    // ==================== Banner State ====================

    /** Banner phase: 0=sliding in, 1=displaying, 2=sliding out, 3=done, 4=re-entering */
    private int bannerPhase;
    private int bannerTimer;
    private int bannerOffset;

    // ==================== Clear Sequence State ====================

    /**
     * Clear routine state machine.
     * 0 = normal play, 1 = fly-away timer, 2 = emerald loading,
     * 3 = emerald approach, 4 = complete
     */
    private int clearRoutine;
    private int clearTimer;
    private int emeraldTimer;
    private int emeraldInteractIndex;

    // ==================== Remaining rings from sphere conversion ====================
    private int ringsLeft;
    /** Whether the exit spin animation has been started. */
    private boolean exitSpinStarted;

    // ==================== Ring Animation ====================
    /** Ring animation timer (counts down from 7, resets). */
    private int ringAnimTimer;
    /** Ring animation frame: 0, 1, or 2 (cycles every 8 game frames). */
    private int ringAnimFrame;

    // Debug state
    private boolean spriteDebugMode;

    private Sonic3kSpecialStageManager() {}

    public static synchronized Sonic3kSpecialStageManager getInstance() {
        if (instance == null) {
            instance = new Sonic3kSpecialStageManager();
        }
        return instance;
    }

    /**
     * Initialize the special stage with the given stage index.
     * ROM: SpecialStage (sonic3k.asm:10585) + sub_85B0 (line 10809)
     *
     * @param stageIndex stage number (0-7)
     * @throws IOException if ROM data loading fails
     */
    public void initialize(int stageIndex) throws IOException {
        LOGGER.info("Initializing S3K special stage " + stageIndex);
        this.currentStage = stageIndex & 7;
        this.initialized = true;
        this.finished = false;
        this.emeraldCollected = false;
        this.ringsCollected = 0;
        this.frameCounter = 0;
        this.clearRoutine = 0;
        this.clearTimer = 0;
        this.emeraldTimer = 0;
        this.emeraldInteractIndex = -1;
        this.ringsLeft = 0;
        this.exitSpinStarted = false;
        this.bannerPhase = 0;
        this.bannerTimer = 0;
        this.bannerOffset = 0;

        collisionQueue.clear();

        // Initialize renderer
        if (renderer == null) {
            renderer = new Sonic3kSpecialStageRenderer(GraphicsManager.getInstance());
        }
        palette = new Sonic3kSpecialStagePalette();

        // Load ROM data and cache art patterns
        if (Sonic3kSpecialStageRomOffsets.areOffsetsVerified()) {
            loadRomData();
        } else {
            LOGGER.warning("S3K SS ROM offsets not verified — using placeholder rendering");
            player.initialize(ANGLE_NORTH, 0x1000, 0x1000, false);
        }

        spheresLeft = grid.countBlueSpheres();

        // Initialize subsystems
        background.initialize(player.getXPos(), player.getYPos());
        hud.initialize();
        banner.initialize();
    }

    /** Pattern ID base for special stage art (avoids conflicts with level patterns). */
    private static final int SS_PATTERN_BASE = 0x3000;

    /**
     * Load all ROM data: layouts, art, palettes, perspective maps.
     * Caches decompressed patterns into the graphics atlas.
     */
    private void loadRomData() throws IOException {
        dataLoader = Sonic3kSpecialStageDataLoader.create();
        GraphicsManager gm = GraphicsManager.getInstance();

        // Load layout directly from ROM (S3 Lockon data, each 0x408 bytes)
        com.openggf.data.Rom rom = GameServices.rom().getRom();
        long layoutAddr = Sonic3kSpecialStageRomOffsets.LAYOUT_S3_STAGE_1
                + (long) currentStage * Sonic3kSpecialStageRomOffsets.LAYOUT_STAGE_SIZE;
        byte[] stageData = rom.readBytes(layoutAddr,
                Sonic3kSpecialStageRomOffsets.LAYOUT_STAGE_SIZE);
        int[] params = grid.loadFromLayoutData(stageData);
        player.initialize(params[0], params[1], params[2], false);
        spheresLeft = grid.countBlueSpheres();
        ringsLeft = params[3]; // ROM: Special_stage_rings_left from layout trailer
        LOGGER.info("Loaded S3K SS layout for stage " + currentStage +
                ": angle=0x" + Integer.toHexString(params[0]) +
                " pos=(" + Integer.toHexString(params[1]) + "," +
                Integer.toHexString(params[2]) + ")" +
                " spheres=" + spheresLeft);

        // Cache art patterns into the graphics atlas
        int nextBase = SS_PATTERN_BASE;

        // Floor layout art (checkerboard tiles)
        Pattern[] floorPatterns = dataLoader.getLayoutArt();
        renderer.setFloorPatternBase(nextBase);
        for (int i = 0; i < floorPatterns.length; i++) {
            gm.cachePatternTexture(floorPatterns[i], nextBase + i);
        }
        nextBase += floorPatterns.length;
        LOGGER.fine("Cached " + floorPatterns.length + " floor patterns");

        // Sphere art
        Pattern[] spherePatterns = dataLoader.getSphereArt();
        renderer.setSpherePatternBase(nextBase);
        for (int i = 0; i < spherePatterns.length; i++) {
            gm.cachePatternTexture(spherePatterns[i], nextBase + i);
        }
        nextBase += spherePatterns.length;

        // Ring art
        Pattern[] ringPatterns = dataLoader.getRingArt();
        renderer.setRingPatternBase(nextBase);
        for (int i = 0; i < ringPatterns.length; i++) {
            gm.cachePatternTexture(ringPatterns[i], nextBase + i);
        }
        nextBase += ringPatterns.length;

        // Background art
        Pattern[] bgPatterns = dataLoader.getBgArt();
        renderer.setBgPatternBase(nextBase);
        for (int i = 0; i < bgPatterns.length; i++) {
            gm.cachePatternTexture(bgPatterns[i], nextBase + i);
        }
        nextBase += bgPatterns.length;

        // Shadow art
        Pattern[] shadowPatterns = dataLoader.getShadowArt();
        renderer.setShadowPatternBase(nextBase);
        for (int i = 0; i < shadowPatterns.length; i++) {
            gm.cachePatternTexture(shadowPatterns[i], nextBase + i);
        }
        nextBase += shadowPatterns.length;

        // "Get Blue Spheres" text art + arrow art
        // ROM loads GBS art at ART_TILE_GET_BLUE_SPHERES (0x055F) and
        // arrow art at offset 0x199 from that base.
        Pattern[] gbsPatterns = dataLoader.getGetBlueSphereArt();
        Pattern[] gbsArrowPatterns = dataLoader.getGbsArrowArt();
        renderer.setGetBlueSpherePatternBase(nextBase);
        for (int i = 0; i < gbsPatterns.length; i++) {
            gm.cachePatternTexture(gbsPatterns[i], nextBase + i);
        }
        // Load arrow art at offset 0x199 from the GBS base
        for (int i = 0; i < gbsArrowPatterns.length; i++) {
            gm.cachePatternTexture(gbsArrowPatterns[i], nextBase + 0x199 + i);
        }
        nextBase += 0x199 + gbsArrowPatterns.length;

        // Digits art (HUD numbers)
        Pattern[] digitsPatterns = dataLoader.getDigitsArt();
        renderer.setDigitsPatternBase(nextBase);
        for (int i = 0; i < digitsPatterns.length; i++) {
            gm.cachePatternTexture(digitsPatterns[i], nextBase + i);
        }
        nextBase += digitsPatterns.length;

        // Icons art (HUD icons)
        Pattern[] iconsPatterns = dataLoader.getIconsArt();
        renderer.setIconsPatternBase(nextBase);
        for (int i = 0; i < iconsPatterns.length; i++) {
            gm.cachePatternTexture(iconsPatterns[i], nextBase + i);
        }
        nextBase += iconsPatterns.length;

        // Player art (Sonic by default)
        Pattern[] playerPatterns = loadPlayerArt();
        renderer.setPlayerPatternBase(nextBase);
        for (int i = 0; i < playerPatterns.length; i++) {
            gm.cachePatternTexture(playerPatterns[i], nextBase + i);
        }
        nextBase += playerPatterns.length;

        // Load perspective maps and pass to renderer
        byte[] perspData = dataLoader.getPerspectiveMaps();
        perspective.loadMaps(perspData);
        renderer.setPerspectiveMaps(perspData);

        // Load floor map (Enigma-decompressed layout map - 9 frames of 40x28 tiles)
        byte[] floorMap = dataLoader.getLayoutEnigmaMap();
        renderer.setFloorMapData(floorMap);

        // Load BG map (Enigma-decompressed starfield - 64x32 tiles)
        byte[] bgMap = dataLoader.getBgEnigmaMap();
        renderer.setBgMapData(bgMap);

        // Load Sonic mapping + DPLC data for sprite rendering
        // Map_SStageSonic and PLC_SStageSonic are in the same include file,
        // immediately after ArtUnc_SStageSonic in ROM.
        // The file contains: 12 mapping frame offsets (24 bytes),
        // then 12 DPLC frame offsets (24 bytes), then the frame data.
        long sonicMapAddr = Sonic3kSpecialStageRomOffsets.ART_UNC_SONIC
                + Sonic3kSpecialStageRomOffsets.ART_UNC_SONIC_SIZE;
        byte[] sonicMapData = rom.readBytes(sonicMapAddr, 400);
        renderer.setSonicMappingData(sonicMapData, sonicMapData);

        // Load banner mapping data (Map_GetBlueSpheres at ROM 0x8F5E, ~76 bytes)
        byte[] bannerMapData = rom.readBytes(0x8F5E, 76);
        renderer.setBannerMappingData(bannerMapData);

        // Load HUD number map and template
        byte[] hudNumMap = dataLoader.getHudNumberMap();
        renderer.setHudNumberMap(hudNumMap);
        byte[] hudTemplate = dataLoader.getHudDisplayMap();
        renderer.setHudTemplate(hudTemplate);

        // Load emerald art (KosinskiM compressed)
        Pattern[] emeraldPatterns = dataLoader.getChaosEmeraldArt();
        renderer.setEmeraldPatternBase(nextBase);
        for (int i = 0; i < emeraldPatterns.length; i++) {
            gm.cachePatternTexture(emeraldPatterns[i], nextBase + i);
        }
        nextBase += emeraldPatterns.length;

        // Load scalar table
        byte[] scalars = dataLoader.getScalarTable();
        // Scalars are used by the 3D projection system

        // Load and apply palettes to the graphics manager
        // In the combined S3K ROM, SK_alone_flag=0 and SK_special_stage_flag=0
        // for the first playthrough (chaos emeralds), so use S3 palettes (skMode=false).
        // skMode=true would be for S&K standalone or super emerald stages.
        palette.initialize(dataLoader, currentStage, false, false);
        com.openggf.level.Palette[] palLines = palette.getPalettes();
        for (int i = 0; i < palLines.length; i++) {
            if (palLines[i] != null) {
                gm.cachePaletteTexture(palLines[i], i);
            }
        }
        LOGGER.fine("Cached " + palLines.length + " SS palette lines");

        // Mark art as loaded
        renderer.setArtLoaded(true);

        LOGGER.info("S3K SS art loaded: " + (nextBase - SS_PATTERN_BASE) + " total patterns");
    }

    /**
     * Load player art based on current character.
     */
    private Pattern[] loadPlayerArt() throws IOException {
        byte[] artData = dataLoader.getSonicArt();
        // Convert raw uncompressed art to Pattern array
        int patternCount = artData.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[patternCount];
        for (int i = 0; i < patternCount; i++) {
            patterns[i] = new Pattern();
            byte[] subArray = new byte[Pattern.PATTERN_SIZE_IN_ROM];
            System.arraycopy(artData, i * Pattern.PATTERN_SIZE_IN_ROM,
                    subArray, 0, Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(subArray);
        }
        return patterns;
    }

    /**
     * Update the special stage by one frame.
     * ROM: loc_84C2 (sonic3k.asm:10737) - main loop
     */
    public void update() {
        if (!initialized || finished) {
            return;
        }

        frameCounter++;

        // Banner state machine
        boolean bannerTriggeredAdvance = banner.update();
        if (bannerTriggeredAdvance) {
            if (player.getVelocity() == 0) {
                player.setAdvancing(true);
                player.setStarted(true);
            }
        }

        // Player movement (includes speed timer, input, velocity, position)
        player.update(heldButtons, pressedButtons);
        player.updateJump(pressedButtons);

        // Collision detection (only when not jumping, not in clear sequence, not exiting)
        if ((player.getJumping() & 0x80) == 0 && clearRoutine == 0 && !exitSpinStarted) {
            processCollision();
        }

        // Collision response queue (ring/sphere animations)
        collisionQueue.update(grid, this::onBlueSphereAnimComplete,
                player.getXPos(), player.getYPos());

        // Ring rotation animation (ROM: Animate_SSRings, sonic3k.asm:12723)
        // Cycles through 3 frames (0, 1, 2) every 8 game frames
        ringAnimTimer--;
        if (ringAnimTimer < 0) {
            ringAnimTimer = 7;
            ringAnimFrame++;
            if (ringAnimFrame >= 3) {
                ringAnimFrame = 0;
            }
        }

        // Clear sequence
        if (clearRoutine > 0) {
            updateClearSequence();
        }

        // During exit spin, fade the palette to white.
        // ROM: Pal_ToWhite called every 3 frames during the 60-frame exit loop.
        if (exitSpinStarted && player.getFadeTimer() > 0) {
            // Fade palette toward white by incrementing color components
            if (player.getFadeTimer() % 3 == 0 && palette != null) {
                for (int line = 0; line < 4; line++) {
                    com.openggf.level.Palette pal = palette.getPalette(line);
                    for (int c = 0; c < 16; c++) {
                        int r = pal.colors[c].r & 0xFF;
                        int g = pal.colors[c].g & 0xFF;
                        int b = pal.colors[c].b & 0xFF;
                        pal.colors[c].r = (byte) Math.min(255, r + 28);
                        pal.colors[c].g = (byte) Math.min(255, g + 28);
                        pal.colors[c].b = (byte) Math.min(255, b + 28);
                    }
                    GraphicsManager.getInstance().cachePaletteTexture(pal, line);
                }
            }
        }

        // Finish stage after the exit spin animation completes.
        // fadeTimer goes 1→0x61 (spinning), then resets to 0 when aligned.
        if (exitSpinStarted && player.getFadeTimer() == 0) {
            finished = true;
        }

        // Update perspective animation frame
        perspective.updateAnimFrame(player);

        // Palette rotation — cycles palette line 3 colors to animate the floor
        // Skip during exit spin so the fade-to-white isn't overwritten
        if (palette != null && !exitSpinStarted) {
            palette.updateRotation(
                    perspective.getAnimFrame(),
                    perspective.getPaletteFrame(),
                    player.getTurning() < 0);
            // Push updated palette line 3 to graphics manager each frame
            GraphicsManager.getInstance().cachePaletteTexture(palette.getPalette(3), 3);
        }

        // Background scroll
        background.update(player);

        // HUD update
        hud.update(spheresLeft, ringsCollected);
    }

    /**
     * Render the special stage.
     */
    public void draw() {
        if (!initialized) {
            return;
        }

        if (renderer != null) {
            renderer.render(this);
        }
    }

    /**
     * Handle player 1 input.
     */
    public void handleInput(int heldButtons, int pressedButtons) {
        if (!initialized || finished) {
            return;
        }
        this.heldButtons = heldButtons;
        this.pressedButtons = pressedButtons;
    }

    /**
     * Handle player 2 input (Tails).
     */
    public void handlePlayer2Input(int heldButtons, int logicalButtons) {
        // TODO: Tails AI
    }

    // ==================== Collision Processing ====================

    /**
     * Process collision at the player's current position.
     * ROM: sub_972E (sonic3k.asm:12088)
     */
    private void processCollision() {
        var result = collision.checkCollision(grid, player);

        switch (result.result) {
            case NONE:
                break;

            case BLUE_SPHERE:
                if (collisionQueue.addBlueSphere(result.gridIndex)) {
                    AudioManager.getInstance().playSfx(Sonic3kSfx.BLUE_SPHERE.id);
                }
                break;

            case RED_SPHERE:
                if (!exitSpinStarted) {
                    player.setFailed(true);
                    player.setFadeTimer(1);
                    exitSpinStarted = true;
                    emeraldCollected = false;
                    AudioManager.getInstance().playSfx(Sonic3kSfx.GOAL.id);
                }
                break;

            case BUMPER:
                player.activateBumperLock(result.gridIndex);
                AudioManager.getInstance().playSfx(Sonic3kSfx.BUMPER.id);
                break;

            case RING:
                if (collisionQueue.addRing(result.gridIndex)) {
                    collectRing(result.gridIndex);
                }
                break;

            case SPRING:
                player.springJump();
                AudioManager.getInstance().playSfx(Sonic3kSfx.SPRING.id);
                break;

            case EMERALD:
                collectEmerald();
                break;
        }
    }

    /**
     * Collect a ring at the given grid index.
     * ROM: loc_9822 (sonic3k.asm:12173)
     */
    private void collectRing(int gridIndex) {
        // Ring queue entry already added by caller

        // Track remaining rings from sphere conversion
        if (ringsLeft > 0) {
            ringsLeft--;
            if (ringsLeft == 0) {
                // All rings collected — play PERFECT SFX and show PERFECT banner
                AudioManager.getInstance().playSfx(Sonic3kSfx.PERFECT.id);
                banner.triggerReEntry(); // Shows "PERFECT" text
            }
        }

        ringsCollected++;

        // Extra life thresholds
        if (ringsCollected == EXTRA_LIFE_THRESHOLD_CONTINUE) {
            AudioManager.getInstance().playSfx(Sonic3kSfx.CONTINUE.id);
        } else if (ringsCollected == EXTRA_LIFE_THRESHOLD_1
                || ringsCollected == EXTRA_LIFE_THRESHOLD_2) {
            AudioManager.getInstance().playSfx(Sonic3kSfx.RING_LOSS.id);
        } else {
            AudioManager.getInstance().playSfx(Sonic3kSfx.RING_RIGHT.id);
        }
    }

    /**
     * Collect the emerald (player walked into emerald cell).
     * ROM: loc_9CE6 (sonic3k.asm:12664)
     */
    private void collectEmerald() {
        // Mark emerald collected in game state
        GameStateManager gameState = GameServices.gameState();
        if (currentStage < EMERALD_COUNT) {
            emeraldCollected = true;
            gameState.markEmeraldCollected(currentStage);
        }

        clearRoutine = 4; // Skip to completion
        player.setFadeTimer(1);
        exitSpinStarted = true;
        // Don't set finished=true here — let the spin animation play first.
        // finished will be set when fadeTimer completes its cycle.
        AudioManager.getInstance().playSfx(Sonic3kSfx.GOAL.id);
    }

    /**
     * Called when a blue sphere's collection animation completes.
     * Triggers the sphere-to-ring conversion algorithm.
     */
    private void onBlueSphereAnimComplete(int gridIndex) {
        // Decrement sphere count
        spheresLeft--;

        // Check for stage clear
        if (spheresLeft <= 0) {
            spheresLeft = 0;
            clearRoutine = 1;
            player.setClearRoutineActive(true);
        }

        // Attempt sphere-to-ring conversion
        var convResult = ringConverter.convert(grid, gridIndex);
        if (convResult.converted) {
            spheresLeft -= convResult.blueSpheresConverted;
            if (spheresLeft < 0) spheresLeft = 0;

            // Check for stage clear again after conversion
            if (spheresLeft == 0 && clearRoutine == 0) {
                clearRoutine = 1;
                player.setClearRoutineActive(true);
            }

            AudioManager.getInstance().playSfx(Sonic3kSfx.RING_LOSS.id);
        }
    }

    // Banner is now handled by Sonic3kSpecialStageBanner class

    // ==================== Clear Sequence ====================

    /**
     * Update the stage clear sequence.
     * ROM: sub_9B62 (sonic3k.asm:12530)
     */
    private void updateClearSequence() {
        switch (clearRoutine) {
            case 1: // Fly-away timer
                updateClearFlyaway();
                break;
            case 2: // Wait for emerald art loading
                updateClearEmeraldLoad();
                break;
            case 3: // Player approaches emerald
                updateClearEmeraldApproach();
                break;
        }
    }

    /**
     * Clear routine state 1: fly-away animation.
     * ROM: loc_9B7C (sonic3k.asm:12530)
     */
    private void updateClearFlyaway() {
        if (clearTimer >= CLEAR_TIMER_COMPLETE) {
            // Advance to next state
            clearRoutine = 2;
            // Clear the grid and place emerald
            placeEmerald();
            return;
        }

        clearTimer += 2;
        if (clearTimer == 2) {
            AudioManager.getInstance().playMusic(Sonic3kSfx.ALL_SPHERES.id);
        }

        // Accelerate timer after thresholds
        if (clearTimer >= CLEAR_TIMER_ACCEL_1) {
            clearTimer++;
        }
        if (clearTimer >= CLEAR_TIMER_ACCEL_2) {
            clearTimer++;
        }
    }

    /**
     * Place the chaos emerald on the grid ahead of the player.
     * ROM: loc_9BA6 (sonic3k.asm:12553)
     */
    private void placeEmerald() {
        grid.clearAll();

        // Calculate emerald position: ahead of player
        int sin = Sonic3kSpecialStagePlayer.getSine(player.getAngle());
        int cos = Sonic3kSpecialStagePlayer.getCosine(player.getAngle());
        int emeraldX = player.getXPos() - (sin * 8);
        int emeraldY = player.getYPos() - (cos * 8);

        int emeraldIndex = Sonic3kSpecialStageGrid.positionToIndex(emeraldX, emeraldY);
        // Place chaos emerald (or super emerald)
        grid.setCellByIndex(emeraldIndex, CELL_CHAOS_EMERALD);
        emeraldInteractIndex = emeraldIndex;

        player.setVelocity(CLEAR_VELOCITY);
        emeraldTimer = EMERALD_TIMER_INIT;

        // TODO: Queue emerald art loading (ArtKosM_SStageChaosEmerald)
        // TODO: Load emerald palette from Pal_SStage_Emeralds
    }

    /**
     * Clear routine state 2: wait for emerald art to load.
     * ROM: loc_9C5C (sonic3k.asm:12613)
     */
    private void updateClearEmeraldLoad() {
        // In ROM, waits for Kos_modules_left to be 0
        // We skip this since we load synchronously
        clearTimer = 0;
        emeraldTimer--;
        if (emeraldTimer <= 0) {
            clearRoutine = 3;
            AudioManager.getInstance().playMusic(Sonic3kMusic.EMERALD.id);
        }
    }

    /**
     * Clear routine state 3: player approaches and collects emerald.
     * Handled by normal collision detection (EMERALD case).
     * ROM: loc_9C80 (sonic3k.asm:12629)
     */
    private void updateClearEmeraldApproach() {
        // Collision detection handles emerald collection
        // Check if player has reached the emerald
        int playerIndex = Sonic3kSpecialStageGrid.positionToIndex(
                player.getXPos(), player.getYPos());
        if (playerIndex == emeraldInteractIndex) {
            int combined = player.getXPos() | player.getYPos();
            if ((combined & CELL_ALIGN_MASK) == 0) {
                collectEmerald();
            }
        }
    }

    // ==================== Lifecycle ====================

    public boolean isFinished() {
        return finished;
    }

    public void reset() {
        initialized = false;
        finished = false;
        emeraldCollected = false;
        ringsCollected = 0;
        spheresLeft = 0;
        currentStage = 0;
        frameCounter = 0;
        clearRoutine = 0;
        clearTimer = 0;
        emeraldTimer = 0;
        ringsLeft = 0;
        bannerPhase = 0;
        bannerTimer = 0;
        bannerOffset = 0;
        heldButtons = 0;
        pressedButtons = 0;
        collisionQueue.clear();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public int getCurrentStage() {
        return currentStage;
    }

    public boolean hasEmeraldCollected() {
        return emeraldCollected;
    }

    public void setEmeraldCollected(boolean collected) {
        this.emeraldCollected = collected;
    }

    public int getRingsCollected() {
        return ringsCollected;
    }

    public int getSpheresLeft() {
        return spheresLeft;
    }

    public int getRingsLeft() {
        return ringsLeft;
    }

    // ==================== Accessors for Renderer ====================

    public Sonic3kSpecialStageGrid getGrid() {
        return grid;
    }

    public Sonic3kSpecialStageBackground getBackground() {
        return background;
    }

    public Sonic3kSpecialStageBanner getBanner() {
        return banner;
    }

    public Sonic3kSpecialStagePlayer getPlayer() {
        return player;
    }

    public int getBannerOffset() {
        return bannerOffset;
    }

    public int getBannerPhase() {
        return bannerPhase;
    }

    public int getClearTimer() {
        return clearTimer;
    }

    public int getClearRoutine() {
        return clearRoutine;
    }

    public int getFrameCounter() {
        return frameCounter;
    }

    public int getRingAnimFrame() {
        return ringAnimFrame;
    }

    // ==================== Debug Methods ====================

    public boolean isSpriteDebugMode() {
        return spriteDebugMode;
    }

    public void toggleSpriteDebugMode() {
        spriteDebugMode = !spriteDebugMode;
    }

    public void cyclePlaneDebugMode() {
        // TODO Phase 4
    }

    public com.openggf.game.SpecialStageDebugProvider getDebugProvider() {
        return null;
    }

    public boolean isAlignmentTestMode() {
        return false;
    }

    public void toggleAlignmentTestMode() {}

    public void adjustAlignmentOffset(int delta) {}

    public void adjustAlignmentSpeed(double delta) {}

    public void toggleAlignmentStepMode() {}

    public void renderAlignmentOverlay(int viewportWidth, int viewportHeight) {}

    public void renderLagCompensationOverlay(int viewportWidth, int viewportHeight) {}

    public double getLagCompensation() {
        return 0.0;
    }

    public void setLagCompensation(double factor) {}
}
