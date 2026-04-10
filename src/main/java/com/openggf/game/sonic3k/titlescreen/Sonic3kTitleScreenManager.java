package com.openggf.game.sonic3k.titlescreen;

import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.control.InputHandler;
import com.openggf.game.GameServices;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.audio.Sonic3kSfx;
import com.openggf.game.sonic3k.audio.Sonic3kSmpsConstants;
import com.openggf.audio.AudioManager;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Palette;
import com.openggf.level.PatternDesc;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.level.render.PatternSpriteRenderer;
import com.openggf.level.render.SpriteMappingFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.lwjgl.opengl.GL11.glClearColor;

/**
 * Main state machine for the Sonic 3&amp;K title screen.
 *
 * <p>Implements {@link TitleScreenProvider} with a multi-phase animation flow:
 * <ol>
 *   <li><b>SEGA phase</b> (~3s): Shows first animation frame, fades from black, plays SEGA sound</li>
 *   <li><b>Palette transition</b> (~8 frames): Pal_Title gradually transitions colors to black</li>
 *   <li><b>Sonic animation</b> (12 frames over ~3.5s): Full-screen plane animation with
 *       per-frame art/palette/mapping changes</li>
 *   <li><b>White flash</b> (~8 frames): Flash screen white, load final scene</li>
 *   <li><b>Interactive menu</b>: Banner bounces in, sprites animate, menu selection</li>
 * </ol>
 *
 * <p>During the animation phases the entire screen is a single 40x28 nametable that changes
 * completely each animation frame. After the animation completes, the screen switches to a
 * two-plane setup (Plane A foreground + Plane B background) with overlay sprites for the
 * banner, "&amp; KNUCKLES", menu text, copyright, Sonic finger/wink, and Tails plane.
 *
 * <p>Singleton pattern, following the same approach as S2's {@code TitleScreenManager}.
 */
public class Sonic3kTitleScreenManager implements TitleScreenProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kTitleScreenManager.class.getName());

    private static Sonic3kTitleScreenManager instance;
    private Runnable exitToLevelHandler = () -> {};

    private final SonicConfigurationService configService = com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().configuration();
    private final Sonic3kTitleScreenDataLoader dataLoader = new Sonic3kTitleScreenDataLoader();
    private final PatternDesc reusableDesc = new PatternDesc();

    private State state = State.INACTIVE;

    // Screen dimensions
    private static final int SCREEN_WIDTH = 320;
    private static final int SCREEN_HEIGHT = 224;

    // Nametable dimensions
    private static final int MAP_WIDTH = 40;
    private static final int MAP_HEIGHT = 28;

    // -----------------------------------------------------------------------
    // Internal phase state machine
    // -----------------------------------------------------------------------

    /**
     * Internal phases for the S3K title screen animation sequence.
     * These map to the coarser {@link TitleScreenProvider.State} values.
     */
    private enum Phase {
        /** Fade from black, show first animation frame. */
        SEGA_FADE_IN,
        /** Hold for ~3 seconds, play SEGA sound. */
        SEGA_HOLD,
        /** Pal_Title transition to black. */
        PAL_TRANSITION,
        /** 12-frame Sonic running animation. */
        SONIC_ANIMATION,
        /** Flash screen white, load final scene. */
        WHITE_FLASH,
        /** Banner bounce + menu selection. */
        INTERACTIVE,
        /** Fade to black before exiting (handles fade ourselves since
         *  FadeManager instance may differ between GameLoop and UiRenderPipeline
         *  after the RuntimeManager singleton migration). */
        FADE_OUT,
        /** Fade complete, ready to exit. */
        EXITING
    }

    private Phase phase = Phase.SEGA_FADE_IN;

    // -----------------------------------------------------------------------
    // Timing constants
    // -----------------------------------------------------------------------

    /** Duration of the SEGA fade-in from black (frames). */
    private static final int SEGA_FADE_DURATION = 16;

    /** Duration of the SEGA hold (frames, ~3 seconds at 60fps). */
    private static final int SEGA_HOLD_DURATION = 180;

    /**
     * Number of palette transition steps from Pal_Title.
     * Each step writes 14 bytes (7 colors) to palette line 0 colors 0-6.
     * The data is 112 bytes = 8 blocks of 14 bytes.
     */
    private static final int PAL_TRANSITION_STEPS = 8;

    /** Bytes per palette transition step (7 colors × 2 bytes each). */
    private static final int PAL_TRANSITION_BYTES_PER_STEP = 14;

    /** Current step in the palette transition. */
    private int palTransitionStep = 0;

    /**
     * Per-frame durations for each animation step (indices 0-10 in SONIC_FRAME_INDEX_TABLE).
     * Measured from the original hardware: frame 1=16, 2=4, 3=4, 4=4, 5=4, 6=6,
     * 7=16, 8=12, 9=12, 10=10, 11=3 then white flash.
     */
    private static final int[] ANIM_FRAME_DURATIONS = {
            16, 4, 4, 4, 4, 6, 16, 12, 12, 10, 3
    };

    /** Duration of the white flash (frames). */
    private static final int WHITE_FLASH_DURATION = 8;

    /** Duration of the exit fade-to-black (frames, ~21 = standard Mega Drive fade). */
    private static final int EXIT_FADE_DURATION = 21;

    /**
     * SonicFrameIndex table: animation frame indices to advance through.
     * Values 1-0xB, terminated by 0xFF meaning animation complete.
     */
    private static final int[] SONIC_FRAME_INDEX_TABLE = {
            1, 2, 3, 4, 5, 6, 7, 8, 9, 0xA, 0xB, 0xFF
    };

    /** Frame index representing the final static scene (frame D = 0xD = 13). */
    private static final int FINAL_FRAME_INDEX = 0xD;

    // -----------------------------------------------------------------------
    // Phase timers and animation state
    // -----------------------------------------------------------------------

    private int phaseTimer = 0;
    private int frameCounter = 0;

    /** Current index into SONIC_FRAME_INDEX_TABLE. */
    private int animTableIndex = 0;

    /** Current animation frame index (1-based, 1 through 0xD). */
    private int currentAnimFrame = 1;

    /** Frames remaining before advancing to the next animation frame. */
    private int animFrameTimer = 0;

    /** Whether SEGA sound has been played. */
    private boolean segaSoundPlayed = false;

    /** Whether title music has been started. */
    private boolean musicPlaying = false;

    // -----------------------------------------------------------------------
    // Banner bounce physics (from disassembly Obj_TitleBanner)
    // -----------------------------------------------------------------------

    /**
     * Banner position in 16.16 fixed point (32-bit signed).
     * Initial value 0xFFA00000 = -96.0 in 16.16.
     */
    private int bannerPos32 = 0xFFA00000;

    /** Banner velocity (signed 16-bit, treated as 8.8 fixed point via <<8 shift). */
    private short bannerVel = 0x0400;

    /** Whether the banner has settled to its rest position. */
    private boolean bannerSettled = false;

    // -----------------------------------------------------------------------
    // V_scroll for interactive phase
    // -----------------------------------------------------------------------

    /** Vertical scroll value (increments to 16 after banner settles). */
    private int vScroll = 0;

    /** Target vScroll value. */
    private static final int VSCROLL_TARGET = 16;

    // -----------------------------------------------------------------------
    // Menu selection
    // -----------------------------------------------------------------------

    /** Menu item: 0 = "1 PLAYER", 1 = "COMPETITION". */
    private int menuSelection = 0;

    // -----------------------------------------------------------------------
    // Sprite state (lightweight, same pattern as S2 AnimatedSprite)
    // -----------------------------------------------------------------------

    private final AnimatedSprite bannerSprite = new AnimatedSprite();
    private final AnimatedSprite andKnucklesSprite = new AnimatedSprite();
    private final AnimatedSprite selectionSprite = new AnimatedSprite();
    private final AnimatedSprite copyrightSprite = new AnimatedSprite();
    private final AnimatedSprite tmSprite = new AnimatedSprite();
    private final AnimatedSprite sonicFingerSprite = new AnimatedSprite();
    private final AnimatedSprite sonicWinkSprite = new AnimatedSprite();
    private final AnimatedSprite tailsPlaneSprite = new AnimatedSprite();

    // ----- Sonic Finger animation (Animate_Sprite format) -----
    // From Ani_TitleSonicFinger: duration=5 (advance every 6 frames), then frame indices.
    // Frame 4 = empty/invisible. Sequence: wait, wag 3 times (0-1-0-1-0-1), wait, loop.
    private static final int FINGER_ANIM_DELAY = 5; // advance every (5+1)=6 frames
    private static final int[] FINGER_ANIM_FRAMES = {
            4, 4, 4, 4, 4, 4, 0, 4, 1, 4, 0, 4, 1, 4, 0, 4, 1, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4
    };
    private int fingerAnimIndex = 0;  // index into FINGER_ANIM_FRAMES
    private int fingerAnimTimer = 0;  // counts up to FINGER_ANIM_DELAY

    // ----- Sonic Wink animation (Animate_SpriteIrregularDelay format) -----
    // From Ani_TitleSonicWink: pairs of (frame, delay), $FF = loop.
    // Runs continuously and independently of the finger animation.
    private static final int[] WINK_ANIM_DATA = {
            2, 1,     // frame 2 for (1+1)=2 ticks
            3, 7,     // frame 3 for (7+1)=8 ticks
            2, 5,     // frame 2 for (5+1)=6 ticks
            4, 0x67,  // frame 4 (empty) for ($67+1)=104 ticks
            4, 0x2F   // frame 4 (empty) for ($2F+1)=48 ticks
    };
    private int winkDataIndex = 0;  // index into WINK_ANIM_DATA (by pairs)
    private int winkAnimTimer = 0;  // counts up to current delay

    // Tails plane animation: cycles frames 0-5
    private static final int TAILS_PLANE_ANIM_DURATION = 2;
    private int tailsPlaneAnimTimer = 0;
    private int tailsPlaneAnimFrame = 0;

    // Tails plane movement: VDP x from 0 to $240, then flip and return
    // VDP y=$C0 going right, $D0 going left (screen = VDP - 128)
    private int tailsPlaneVdpX = 0;
    private boolean tailsPlaneGoingRight = true;
    private boolean tailsPlaneHFlip = false;

    // Banner palette cycling (Pal_TitleWaterRot)
    // From disassembly: timer resets to 9 (10 frames per cycle), advances by 4 bytes,
    // wraps with AND $1C = 8 unique positions (0, 4, 8, $C, $10, $14, $18, $1C)
    private int waterRotIndex = 0;
    private int waterRotTimer = 9;
    private static final int WATER_ROT_CYCLE_FRAMES = 10;

    // -----------------------------------------------------------------------
    // Sprite rendering
    // -----------------------------------------------------------------------

    // Mapping frame lists for each sprite type
    private List<SpriteMappingFrame> bannerFrames;
    private List<SpriteMappingFrame> andKnucklesFrames;
    private List<SpriteMappingFrame> selectionFrames;
    private List<SpriteMappingFrame> copyrightFrames;
    private List<SpriteMappingFrame> sonicAnimFrames;
    private List<SpriteMappingFrame> tailsPlaneFrames;

    // Sprite renderers (one per sprite type since they share the same pattern array
    // but have different mapping frame lists)
    private PatternSpriteRenderer bannerRenderer;
    private PatternSpriteRenderer andKnucklesRenderer;
    private PatternSpriteRenderer selectionRenderer;
    private PatternSpriteRenderer copyrightRenderer;
    private PatternSpriteRenderer sonicAnimRenderer;
    private PatternSpriteRenderer tailsPlaneRenderer;

    private boolean spritesInitialized = false;

    // -----------------------------------------------------------------------
    // Constructor and singleton
    // -----------------------------------------------------------------------

    private Sonic3kTitleScreenManager() {
    }

    public static synchronized Sonic3kTitleScreenManager getInstance() {
        if (instance == null) {
            instance = new Sonic3kTitleScreenManager();
        }
        return instance;
    }

    // -----------------------------------------------------------------------
    // TitleScreenProvider implementation
    // -----------------------------------------------------------------------

    @Override
    public void initialize() {
        LOGGER.info("Initializing S3K title screen");

        // Load data if not already loaded
        if (!dataLoader.isDataLoaded()) {
            dataLoader.loadData();
        }

        // Force palette re-upload on next draw
        dataLoader.resetCache();

        // Reset state
        state = State.INTRO_TEXT_FADE_IN;
        phase = Phase.SEGA_FADE_IN;
        phaseTimer = 0;
        frameCounter = 0;
        animTableIndex = 0;
        currentAnimFrame = 1;
        animFrameTimer = 0;
        segaSoundPlayed = false;
        musicPlaying = false;
        palTransitionStep = 0;

        // Reset banner physics
        bannerPos32 = 0xFFA00000; // -96.0 in 16.16
        bannerVel = 0x0400;
        bannerSettled = false;

        // Reset vScroll
        vScroll = 0;

        // Reset menu
        menuSelection = 0;

        // Reset sprites
        bannerSprite.reset();
        andKnucklesSprite.reset();
        selectionSprite.reset();
        copyrightSprite.reset();
        tmSprite.reset();
        sonicFingerSprite.reset();
        sonicWinkSprite.reset();
        tailsPlaneSprite.reset();

        // Reset finger/wink animation
        fingerAnimIndex = 0;
        fingerAnimTimer = 0;
        winkDataIndex = 0;
        winkAnimTimer = 0;

        // Reset Tails plane
        tailsPlaneAnimTimer = 0;
        tailsPlaneAnimFrame = 0;
        tailsPlaneVdpX = 0;
        tailsPlaneGoingRight = true;
        tailsPlaneHFlip = false;

        // Reset palette cycling
        waterRotIndex = 0;
        waterRotTimer = 9; // Counts down from 9 (10-frame period)

        // Reset sprite renderers
        spritesInitialized = false;

        // Cache the first animation frame
        if (dataLoader.isDataLoaded()) {
            dataLoader.cacheAnimationFrame(1);
        }

        LOGGER.info("S3K title screen initialized, entering SEGA_FADE_IN phase");
    }

    @Override
    public void update(InputHandler input) {
        switch (phase) {
            case SEGA_FADE_IN -> updateSegaFadeIn(input);
            case SEGA_HOLD -> updateSegaHold(input);
            case PAL_TRANSITION -> updatePalTransition(input);
            case SONIC_ANIMATION -> updateSonicAnimation(input);
            case WHITE_FLASH -> updateWhiteFlash(input);
            case INTERACTIVE -> updateInteractive(input);
            case FADE_OUT -> updateFadeOut();
            case EXITING -> { }
        }
        frameCounter++;
    }

    @Override
    public void draw() {
        if (!dataLoader.isDataLoaded()) {
            dataLoader.loadData();
        }

        GraphicsManager gm = com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().graphics();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }

        // Ensure sprite art is cached
        dataLoader.cacheToGpu();

        // Initialize sprite renderers if needed
        if (!spritesInitialized && dataLoader.getSpritePatterns() != null) {
            initSpriteRenderers(gm);
        }

        switch (phase) {
            case SEGA_FADE_IN, SEGA_HOLD, PAL_TRANSITION, SONIC_ANIMATION ->
                    drawAnimationPhase(gm);
            case WHITE_FLASH -> drawWhiteFlash(gm);
            case INTERACTIVE -> drawInteractivePhase(gm);
            case FADE_OUT -> drawFadeOut(gm);
            case EXITING -> { /* Screen is black, GameLoop handles transition */ }
        }
    }

    @Override
    public void setClearColor() {
        // On the Mega Drive, palette line 0 color 0 is the background color —
        // it fills the screen behind everything. Transparent pixels (color 0)
        // in tile patterns show this background color.
        byte[] palData = dataLoader.getAnimPaletteData(currentAnimFrame);
        if (palData != null && palData.length >= 2) {
            // Read color 0 from palette line 0 (first 2 bytes, big-endian)
            // Mega Drive format: 0x0BGR where B,G,R are nibbles (0-E, 8 levels)
            int color0 = ((palData[0] & 0xFF) << 8) | (palData[1] & 0xFF);
            float r = ((color0 >> 1) & 0x7) / 7.0f;
            float g = ((color0 >> 5) & 0x7) / 7.0f;
            float b = ((color0 >> 9) & 0x7) / 7.0f;
            glClearColor(r, g, b, 1.0f);
        } else {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        }
    }

    @Override
    public void reset() {
        state = State.INACTIVE;
        phase = Phase.SEGA_FADE_IN;
        phaseTimer = 0;
        frameCounter = 0;
        musicPlaying = false;
        segaSoundPlayed = false;
        spritesInitialized = false;

        // Cancel any stale FadeManager overlay. The GameLoop's exitTitleScreen()
        // uses fadeManager.startFadeToBlack() with a callback to doExitTitleScreen(),
        // which calls this reset(). After the callback, FadeManager.completeFade()
        // would persist the black overlay indefinitely (holdDuration = MAX_VALUE).
        // Cancelling here clears the overlay so the level can render.
        GameServices.fade().cancel();

        LOGGER.info("S3K title screen reset to inactive");
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
    public void setExitToLevelHandler(Runnable handler) {
        this.exitToLevelHandler = handler != null ? handler : () -> {};
    }

    // -----------------------------------------------------------------------
    // Phase update methods
    // -----------------------------------------------------------------------

    private void updateSegaFadeIn(InputHandler input) {
        phaseTimer++;

        if (checkSkipToInteractive(input)) {
            return;
        }

        if (phaseTimer >= SEGA_FADE_DURATION) {
            phase = Phase.SEGA_HOLD;
            phaseTimer = 0;
            state = State.INTRO_TEXT_HOLD;
            LOGGER.fine("S3K title screen entered SEGA_HOLD phase");
        }
    }

    private void updateSegaHold(InputHandler input) {
        phaseTimer++;

        if (checkSkipToInteractive(input)) {
            return;
        }

        // Play SEGA sound immediately (disassembly plays it right after Pal_FadeFromBlack)
        if (!segaSoundPlayed) {
            segaSoundPlayed = true;
            GameServices.audio().playMusic(Sonic3kSmpsConstants.CMD_SEGA);
        }

        if (phaseTimer >= SEGA_HOLD_DURATION) {
            phase = Phase.PAL_TRANSITION;
            phaseTimer = 0;
            state = State.INTRO_TEXT_FADE_OUT;
            LOGGER.fine("S3K title screen entered PAL_TRANSITION phase");
        }
    }

    /**
     * Updates the palette transition phase.
     *
     * <p>From the disassembly (sonic3k.asm lines 5501-5512): each VSync, reads
     * 14 bytes from {@code Pal_Title} and writes them to palette line 0 colors 0-6.
     * This gradually changes the SEGA screen background to black while leaving
     * the SEGA text (colors 7+) unchanged. Completes when color 0 becomes $0000.
     */
    private void updatePalTransition(InputHandler input) {
        if (checkSkipToInteractive(input)) {
            return;
        }

        // Apply one palette transition step per frame (matching the disassembly's
        // VSync loop that writes 14 bytes per iteration)
        byte[] transitionData = dataLoader.getPalTransitionData();
        if (transitionData != null && palTransitionStep < PAL_TRANSITION_STEPS) {
            applyPalTransitionStep(transitionData, palTransitionStep);
            palTransitionStep++;
        }

        if (palTransitionStep >= PAL_TRANSITION_STEPS) {
            // Transition complete — background is now black
            phase = Phase.SONIC_ANIMATION;
            phaseTimer = 0;
            animTableIndex = 0;
            currentAnimFrame = SONIC_FRAME_INDEX_TABLE[0];
            animFrameTimer = 0;
            state = State.FADE_IN;

            // Cache the first animation frame art/palette
            dataLoader.cacheAnimationFrame(currentAnimFrame);

            // Play title music
            if (!musicPlaying) {
                musicPlaying = true;
                GameServices.audio().playMusic(Sonic3kMusic.TITLE.id);
            }

            LOGGER.fine("S3K title screen entered SONIC_ANIMATION phase");
        }
    }

    /**
     * Applies one step of the Pal_Title transition to palette line 0.
     * Each step overwrites colors 0-6 (14 bytes) with data from the transition table.
     */
    private void applyPalTransitionStep(byte[] transitionData, int step) {
        GraphicsManager gm = com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().graphics();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }

        int offset = step * PAL_TRANSITION_BYTES_PER_STEP;
        if (offset + PAL_TRANSITION_BYTES_PER_STEP > transitionData.length) {
            return;
        }

        // Get the current palette line 0 from the frame 1 palette
        byte[] frame1Pal = dataLoader.getAnimPaletteData(1);
        if (frame1Pal == null || frame1Pal.length < Palette.PALETTE_SIZE_IN_ROM) {
            return;
        }

        // Copy full palette line 0 (32 bytes) then overwrite colors 0-6
        byte[] line0Data = new byte[Palette.PALETTE_SIZE_IN_ROM];
        System.arraycopy(frame1Pal, 0, line0Data, 0, Palette.PALETTE_SIZE_IN_ROM);

        // Overwrite colors 0-6 (14 bytes) with transition data
        System.arraycopy(transitionData, offset, line0Data, 0, PAL_TRANSITION_BYTES_PER_STEP);

        Palette palette = new Palette();
        palette.fromSegaFormat(line0Data);
        gm.cachePaletteTexture(palette, 0);
    }

    private void updateSonicAnimation(InputHandler input) {
        if (checkSkipToInteractive(input)) {
            return;
        }

        animFrameTimer++;

        // Look up duration for current animation step from the measured table
        int frameDuration = (animTableIndex < ANIM_FRAME_DURATIONS.length)
                ? ANIM_FRAME_DURATIONS[animTableIndex]
                : 4; // fallback

        if (animFrameTimer >= frameDuration) {
            animFrameTimer = 0;
            animTableIndex++;

            if (animTableIndex >= SONIC_FRAME_INDEX_TABLE.length) {
                // Animation complete - shouldn't happen (0xFF triggers below)
                transitionToWhiteFlash();
                return;
            }

            int nextFrame = SONIC_FRAME_INDEX_TABLE[animTableIndex];
            if (nextFrame == 0xFF) {
                // Animation complete
                transitionToWhiteFlash();
                return;
            }

            currentAnimFrame = nextFrame;
            // Cache new frame art and palette
            dataLoader.cacheAnimationFrame(currentAnimFrame);
        }
    }

    private void updateWhiteFlash(InputHandler input) {
        phaseTimer++;

        if (phaseTimer >= WHITE_FLASH_DURATION) {
            transitionToInteractive();
        }
    }

    /**
     * Updates the exit fade-to-black phase.
     *
     * <p>We handle the full exit transition ourselves rather than relying on
     * the GameLoop's {@code exitTitleScreen()} → FadeManager → callback chain,
     * because the upstream RuntimeManager singleton migration can cause the
     * FadeManager instance in GameLoop to differ from the one that the
     * UiRenderPipeline updates, preventing the fade callback from ever firing.
     *
     * <p>When our visual fade completes, we directly reset, set the game mode
     * to LEVEL, and load the first zone.
     */
    private void updateFadeOut() {
        phaseTimer++;
        if (phaseTimer >= EXIT_FADE_DURATION) {
            LOGGER.info("S3K title screen exit fade complete, loading level");

            // Reset title screen state
            state = State.INACTIVE;
            phase = Phase.EXITING;
            spritesInitialized = false;

            // Cancel any stale FadeManager overlays
            GameServices.fade().cancel();

            // Transition directly to LEVEL mode and load the first zone
            try {
                exitToLevelHandler.run();
            } catch (Exception e) {
                LOGGER.severe("Failed to load level after title screen: " + e.getMessage());
            }
        }
    }

    private void updateInteractive(InputHandler input) {
        int jumpKey = configService.getInt(SonicConfiguration.JUMP);
        int upKey = configService.getInt(SonicConfiguration.UP);
        int downKey = configService.getInt(SonicConfiguration.DOWN);

        // Menu navigation
        if (input.isKeyPressed(upKey) && menuSelection > 0) {
            menuSelection--;
            selectionSprite.mappingFrame = menuSelection;
            GameServices.audio().playSfx(Sonic3kSfx.SWITCH.id);
        }
        if (input.isKeyPressed(downKey) && menuSelection < 1) {
            menuSelection++;
            selectionSprite.mappingFrame = menuSelection;
            GameServices.audio().playSfx(Sonic3kSfx.SWITCH.id);
        }

        // Start pressed - begin exit fade
        if (input.isKeyPressed(jumpKey)) {
            phase = Phase.FADE_OUT;
            phaseTimer = 0;
            // State stays ACTIVE during our fade — we only set EXITING once
            // the visual fade is complete, so the GameLoop's exitTitleScreen()
            // finds the screen already black and can transition immediately.
            com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().audio().fadeOutMusic();
            LOGGER.info("S3K title screen starting exit fade (menu selection: " + menuSelection + ")");
            return;
        }

        // Update banner bounce physics
        if (!bannerSettled) {
            updateBannerBounce();
        }

        // Update vScroll — starts immediately during bounce (matching disassembly:
        // V_scroll_value increments every frame of Obj_TitleBanner_Main/Display)
        if (vScroll < VSCROLL_TARGET) {
            vScroll++;
        }

        // Update Tails plane movement and animation
        updateTailsPlane();

        // Update Sonic finger and wink animations (both run simultaneously)
        updateSonicFinger();
        updateSonicWink();

        // Update banner palette cycling
        updateWaterRotCycling();
    }

    // -----------------------------------------------------------------------
    // Animation helpers
    // -----------------------------------------------------------------------

    /**
     * Checks if Start is pressed and skips directly to the interactive phase.
     *
     * @return true if skip occurred
     */
    private boolean checkSkipToInteractive(InputHandler input) {
        int jumpKey = configService.getInt(SonicConfiguration.JUMP);
        if (input.isKeyPressed(jumpKey)) {
            transitionToWhiteFlash();
            return true;
        }
        return false;
    }

    private void transitionToWhiteFlash() {
        phase = Phase.WHITE_FLASH;
        phaseTimer = 0;
        state = State.FADE_IN;

        // Load final frame
        currentAnimFrame = FINAL_FRAME_INDEX;
        dataLoader.cacheFinalScene();

        // Play title music if not already playing
        if (!musicPlaying) {
            musicPlaying = true;
            GameServices.audio().playMusic(Sonic3kMusic.TITLE.id);
        }

        LOGGER.fine("S3K title screen entered WHITE_FLASH phase");
    }

    private void transitionToInteractive() {
        phase = Phase.INTERACTIVE;
        state = State.ACTIVE;

        // Load the final scene art (frame D with full 4-line palette)
        dataLoader.cacheFinalScene();

        // Initialize interactive sprites
        initInteractiveSprites();

        LOGGER.fine("S3K title screen entered INTERACTIVE phase");
    }

    private void initInteractiveSprites() {
        // Banner: starts off-screen below, bounces into position
        bannerSprite.active = true;
        bannerSprite.mappingFrame = 0; // Main banner frame

        // &Knuckles: follows banner Y
        andKnucklesSprite.active = true;
        andKnucklesSprite.mappingFrame = 0;

        // TM symbol: positioned relative to banner
        tmSprite.active = true;
        tmSprite.mappingFrame = 1; // TM frame in banner mappings

        // Selection menu
        selectionSprite.active = true;
        selectionSprite.mappingFrame = menuSelection;

        // Copyright text
        copyrightSprite.active = true;
        copyrightSprite.mappingFrame = 0;

        // Sonic finger and wink: both run simultaneously from the start
        // They are separate sprites at different positions on Sonic's body
        sonicFingerSprite.active = true;
        sonicFingerSprite.mappingFrame = FINGER_ANIM_FRAMES[0];
        fingerAnimIndex = 0;
        fingerAnimTimer = 0;

        sonicWinkSprite.active = true;
        sonicWinkSprite.mappingFrame = WINK_ANIM_DATA[0]; // first frame
        winkDataIndex = 0;
        winkAnimTimer = 0;

        // Tails plane: flies across screen
        tailsPlaneSprite.active = true;
        tailsPlaneSprite.mappingFrame = 0;
        tailsPlaneVdpX = 0;
        tailsPlaneGoingRight = true;
        tailsPlaneHFlip = false;

        // Reset banner physics
        bannerPos32 = 0xFFA00000; // -96.0 in 16.16
        bannerVel = 0x0400;
        bannerSettled = false;
    }

    // -----------------------------------------------------------------------
    // Banner bounce physics (from disassembly Obj_TitleBanner)
    // -----------------------------------------------------------------------

    /**
     * Bounce flag from previous frame. Mirrors the disassembly's $34(a0) byte:
     * 0 when position &lt; 0, -1 when position &gt;= 0. Velocity is halved when
     * this flag changes between frames (i.e., position crosses zero).
     */
    private byte bannerBounceFlag = 0;

    /**
     * Updates banner bounce physics per frame.
     *
     * <p>Faithful port of {@code Obj_TitleBanner_Main} from the disassembly
     * (sonic3k.asm lines 6007-6056). Position is 16.16 fixed point stored as a
     * 32-bit signed int. Velocity is signed 16-bit. Each frame:
     * <ol>
     *   <li>Save previous bounce flag</li>
     *   <li>pos += vel &lt;&lt; 8</li>
     *   <li>If posInt &lt; 0: gravity = +$40. If posInt &gt;= 0: gravity = -$40, set bounce flag</li>
     *   <li>vel += gravity</li>
     *   <li>If bounce flag changed: vel &gt;&gt;= 1 (halve on zero-crossing)</li>
     *   <li>Settled when posInt == 0 and vel == -$5B</li>
     * </ol>
     */
    private void updateBannerBounce() {
        // d2 = previous frame's bounce flag
        byte prevFlag = bannerBounceFlag;

        // pos += vel << 8 (extend 16-bit vel to 32-bit, shift left 8)
        bannerPos32 += ((int) bannerVel) << 8;

        // d0 = high word of position (integer part, signed)
        short posInt = (short) (bannerPos32 >> 16);

        // Clear flag for this frame
        bannerBounceFlag = 0;

        // d1 = gravity direction
        short gravity = 0x40;

        if (posInt < 0) {
            // Position below center: accelerate upward, flag stays 0
            // (falls through to apply gravity)
        } else if (posInt == 0 && bannerVel == (short) -0x5B) {
            // Settled: pos=0, vel=-0x5B
            bannerSettled = true;
            bannerPos32 = 0;
            bannerVel = 0;
            LOGGER.fine("Banner settled at frame " + frameCounter);
            return;
        } else {
            // Position at or above center (and not settled): set flag, reverse gravity
            bannerBounceFlag = -1;
            gravity = -0x40;
        }

        // Apply gravity
        bannerVel += gravity;

        // If bounce flag changed from previous frame (position crossed zero): halve velocity
        if (bannerBounceFlag != prevFlag) {
            bannerVel >>= 1; // Arithmetic shift right (same as 68000 ASR)
        }
    }

    /**
     * Calculates the banner screen Y from its physics position.
     *
     * <p>VDP y_pos = 0xD4 - posInteger. Screen Y = VDP - 128.
     *
     * @return screen Y position
     */
    private int getBannerScreenY() {
        short posInt = (short) (bannerPos32 >> 16);
        int vdpY = 0xD4 - posInt;
        return vdpY - 128;
    }

    /**
     * Calculates the "&amp; KNUCKLES" screen Y.
     *
     * <p>VDP y = banner_vdp_y + 0x5C. Screen Y = VDP - 128.
     */
    private int getAndKnucklesScreenY() {
        short posInt = (short) (bannerPos32 >> 16);
        int bannerVdpY = 0xD4 - posInt;
        int andKnucklesVdpY = bannerVdpY + 0x5C;
        return andKnucklesVdpY - 128;
    }

    // -----------------------------------------------------------------------
    // Sprite animation updates
    // -----------------------------------------------------------------------

    private void updateTailsPlane() {
        // Move across screen (from disassembly Obj_TitleTailsPlane)
        // Going right: VDP x increments from 0 to $240, y=$C0
        // Going left: VDP x decrements from $240 to 0, y=$D0, H-flip set
        if (tailsPlaneGoingRight) {
            tailsPlaneVdpX++;
            if (tailsPlaneVdpX >= 0x240) {
                tailsPlaneGoingRight = false;
                tailsPlaneHFlip = true;
            }
        } else {
            tailsPlaneVdpX--;
            if (tailsPlaneVdpX <= 0) {
                tailsPlaneGoingRight = true;
                tailsPlaneHFlip = false;
            }
        }

        // Animate propeller
        tailsPlaneAnimTimer++;
        if (tailsPlaneAnimTimer >= TAILS_PLANE_ANIM_DURATION) {
            tailsPlaneAnimTimer = 0;
            tailsPlaneAnimFrame++;
            if (tailsPlaneAnimFrame >= 6) {
                tailsPlaneAnimFrame = 0;
            }
            tailsPlaneSprite.mappingFrame = tailsPlaneAnimFrame;
        }
    }

    /**
     * Updates the Sonic finger animation using the standard Animate_Sprite format.
     *
     * <p>Duration byte = 5 means advance every (5+1) = 6 frames. The frame index
     * sequence plays continuously and loops at $FF. Frame 4 = empty/invisible.
     */
    private void updateSonicFinger() {
        fingerAnimTimer++;
        if (fingerAnimTimer > FINGER_ANIM_DELAY) {
            fingerAnimTimer = 0;
            fingerAnimIndex++;
            if (fingerAnimIndex >= FINGER_ANIM_FRAMES.length) {
                fingerAnimIndex = 0; // $FF = loop
            }
            sonicFingerSprite.mappingFrame = FINGER_ANIM_FRAMES[fingerAnimIndex];
        }
    }

    /**
     * Updates the Sonic wink animation using the Animate_SpriteIrregularDelay format.
     *
     * <p>Each entry is a (frame, delay) pair. The delay value means the frame is shown
     * for (delay+1) ticks. Loops at $FF. Frame 4 = empty/invisible.
     */
    private void updateSonicWink() {
        winkAnimTimer++;
        // Current delay is WINK_ANIM_DATA[winkDataIndex + 1]
        int currentDelay = WINK_ANIM_DATA[winkDataIndex + 1];
        if (winkAnimTimer > currentDelay) {
            winkAnimTimer = 0;
            winkDataIndex += 2; // advance to next (frame, delay) pair
            if (winkDataIndex >= WINK_ANIM_DATA.length) {
                winkDataIndex = 0; // $FF = loop
            }
            sonicWinkSprite.mappingFrame = WINK_ANIM_DATA[winkDataIndex];
        }
    }

    /**
     * Updates the palette cycling for the banner water shimmer effect.
     *
     * <p>From the disassembly (Obj_TitleBanner_Display): every 10 frames, reads
     * 4 bytes (2 Mega Drive colors) from {@code Pal_TitleWaterRot} and writes
     * them to palette line 3 at byte offset $1A (color indices 13-14).
     * The offset advances by 4 and wraps with AND $1C (8 unique positions).
     */
    private void updateWaterRotCycling() {
        byte[] waterRotData = dataLoader.getWaterRotData();
        if (waterRotData == null || waterRotData.length < 4) {
            return;
        }

        waterRotTimer--;
        if (waterRotTimer < 0) {
            waterRotTimer = WATER_ROT_CYCLE_FRAMES - 1; // Reset to 9 (10-frame period)
            waterRotIndex = (waterRotIndex + 4) & 0x1C; // Advance by 4, wrap at $1C

            // Apply the 2 cycling colors to palette line 3
            applyWaterRotPalette(waterRotData);
        }
    }

    /**
     * Applies 2 colors from water rotation data to palette line 2 (0-indexed), colors 13-14.
     *
     * <p>The disassembly writes to {@code Target_palette_line_3+$1A}. In the S3K constants,
     * {@code Target_palette_line_3 = Target_palette+$40}, which is the THIRD palette line
     * (1-based numbering), i.e. palette index 2 (0-based). This is the background palette,
     * which contains the water gradient colors.
     */
    private void applyWaterRotPalette(byte[] waterRotData) {
        GraphicsManager gm = com.openggf.game.EngineServices.fromLegacySingletonsForBootstrap().graphics();
        if (gm == null || gm.isHeadlessMode()) {
            return;
        }

        // Get the current palette line 2 (0-indexed) data from the frame D palette
        byte[] palDData = dataLoader.getFrameDPaletteData();
        if (palDData == null || palDData.length < 96) {
            return;
        }

        // Palette line 2 (0-indexed) starts at byte 64 (2 * 32) in the 128-byte palette data
        byte[] lineData = new byte[Palette.PALETTE_SIZE_IN_ROM];
        System.arraycopy(palDData, 2 * Palette.PALETTE_SIZE_IN_ROM, lineData, 0,
                Palette.PALETTE_SIZE_IN_ROM);

        // Overwrite colors 13-14 (byte offset $1A = 26) with cycling data
        if (waterRotIndex + 3 < waterRotData.length) {
            lineData[26] = waterRotData[waterRotIndex];
            lineData[27] = waterRotData[waterRotIndex + 1];
            lineData[28] = waterRotData[waterRotIndex + 2];
            lineData[29] = waterRotData[waterRotIndex + 3];
        }

        // Re-upload palette line 2 (0-indexed)
        Palette palette = new Palette();
        palette.fromSegaFormat(lineData);
        gm.cachePaletteTexture(palette, 2);
    }

    // -----------------------------------------------------------------------
    // Drawing methods
    // -----------------------------------------------------------------------

    /**
     * Draws during the SEGA/animation phases: renders the current animation
     * frame as a full-screen 40x28 nametable.
     */
    private void drawAnimationPhase(GraphicsManager gm) {
        int[] nametable = dataLoader.getAnimationMapping(currentAnimFrame);
        if (nametable == null || nametable.length == 0) {
            return;
        }

        int animPatternBase = dataLoader.getAnimPatternBase();

        gm.beginPatternBatch();
        int mapSize = MAP_WIDTH * MAP_HEIGHT;
        for (int row = 0; row < MAP_HEIGHT; row++) {
            for (int col = 0; col < MAP_WIDTH; col++) {
                int idx = row * MAP_WIDTH + col;
                if (idx >= nametable.length || idx >= mapSize) {
                    continue;
                }
                int word = nametable[idx];
                if (word == 0) {
                    continue;
                }
                // Extract pattern fields from nametable word
                int tileIndex = word & 0x7FF;
                reusableDesc.set(word);
                gm.renderPatternWithId(animPatternBase + tileIndex, reusableDesc, col * 8, row * 8);
            }
        }
        gm.flushPatternBatch();

        // Apply fade overlay for SEGA fade-in only.
        // PAL_TRANSITION uses actual per-color palette modification (not an overlay)
        // so the SEGA text stays white while the background goes dark.
        if (phase == Phase.SEGA_FADE_IN) {
            float fadeAmount = 1.0f - (float) phaseTimer / SEGA_FADE_DURATION;
            if (fadeAmount > 0.0f) {
                gm.registerCommand(new GLCommand(
                        GLCommand.CommandType.RECTI, -1,
                        GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                        0.0f, 0.0f, 0.0f, fadeAmount,
                        0, 0, SCREEN_WIDTH, SCREEN_HEIGHT
                ));
            }
        }
    }

    /**
     * Draws the white flash transition and the final frame underneath.
     */
    private void drawWhiteFlash(GraphicsManager gm) {
        // Draw the final animation frame underneath
        int[] nametable = dataLoader.getAnimationMapping(FINAL_FRAME_INDEX);
        if (nametable != null && nametable.length > 0) {
            int animPatternBase = dataLoader.getAnimPatternBase();
            gm.beginPatternBatch();
            for (int row = 0; row < MAP_HEIGHT; row++) {
                for (int col = 0; col < MAP_WIDTH; col++) {
                    int idx = row * MAP_WIDTH + col;
                    if (idx >= nametable.length) {
                        continue;
                    }
                    int word = nametable[idx];
                    if (word == 0) {
                        continue;
                    }
                    int tileIndex = word & 0x7FF;
                    reusableDesc.set(word);
                    gm.renderPatternWithId(animPatternBase + tileIndex, reusableDesc, col * 8, row * 8);
                }
            }
            gm.flushPatternBatch();
        }

        // White flash overlay, fading out
        float flashAlpha = 1.0f - (float) phaseTimer / WHITE_FLASH_DURATION;
        if (flashAlpha > 0.0f) {
            gm.registerCommand(new GLCommand(
                    GLCommand.CommandType.RECTI, -1,
                    GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                    1.0f, 1.0f, 1.0f, flashAlpha,
                    0, 0, SCREEN_WIDTH, SCREEN_HEIGHT
            ));
        }
    }

    /**
     * Draws the exit fade-to-black: renders the interactive scene with a
     * progressively opaque black overlay.
     */
    private void drawFadeOut(GraphicsManager gm) {
        // Draw the interactive scene underneath
        drawInteractivePhase(gm);

        // Black overlay, fading in
        float fadeAlpha = (float) phaseTimer / EXIT_FADE_DURATION;
        if (fadeAlpha > 0.0f) {
            gm.registerCommand(new GLCommand(
                    GLCommand.CommandType.RECTI, -1,
                    GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                    0.0f, 0.0f, 0.0f, Math.min(1.0f, fadeAlpha),
                    0, 0, SCREEN_WIDTH, SCREEN_HEIGHT
            ));
        }
    }

    /**
     * Draws the interactive phase: background plane, foreground plane, and all sprites.
     */
    private void drawInteractivePhase(GraphicsManager gm) {
        // 1. Render Plane B (background)
        renderPlaneB(gm);

        // 2. Render Tails plane sprite (no priority, renders behind Plane A)
        if (tailsPlaneSprite.active && tailsPlaneRenderer != null && tailsPlaneRenderer.isReady()) {
            int tailsScreenX = tailsPlaneVdpX - 128;
            int tailsVdpY = tailsPlaneGoingRight ? 0xC0 : 0xD0;
            int tailsScreenY = tailsVdpY - 128;
            gm.beginPatternBatch();
            tailsPlaneRenderer.drawFrameIndex(tailsPlaneSprite.mappingFrame,
                    tailsScreenX, tailsScreenY, tailsPlaneHFlip, false);
            gm.flushPatternBatch();
        }

        // 3. Render Plane A (final Sonic frame, shifted by vScroll)
        renderPlaneA(gm);

        // 4. Render sprites in VDP priority order (back to front in painter's algorithm).
        // On VDP, lower priority values = drawn in front. From the disassembly:
        //   Tails plane: priority $380 (furthest back, already drawn above)
        //   Finger/Wink: priority $180 (behind banner)
        //   Banner/TM/&Knuckles: priority $80 (in front of finger)
        //   Selection/Copyright: priority $80 (frontmost)
        gm.beginPatternBatch();

        // Sonic finger wag — priority $180, drawn BEHIND the banner
        // VDP x=$148, y=($DC - vScroll)
        if (sonicFingerSprite.active && sonicAnimRenderer != null && sonicAnimRenderer.isReady()) {
            int fingerScreenX = 0x148 - 128; // 200
            int fingerScreenY = 0xDC - vScroll - 128;
            sonicAnimRenderer.drawFrameIndex(sonicFingerSprite.mappingFrame,
                    fingerScreenX, fingerScreenY);
        }

        // Sonic wink — priority $180, drawn BEHIND the banner
        // VDP x=$F8, y=($C8 - vScroll)
        if (sonicWinkSprite.active && sonicAnimRenderer != null && sonicAnimRenderer.isReady()) {
            int winkScreenX = 0xF8 - 128; // 120
            int winkScreenY = 0xC8 - vScroll - 128;
            sonicAnimRenderer.drawFrameIndex(sonicWinkSprite.mappingFrame,
                    winkScreenX, winkScreenY);
        }

        // Banner — priority $80, drawn IN FRONT of finger/wink
        if (bannerSprite.active && bannerRenderer != null && bannerRenderer.isReady()) {
            int bannerScreenX = 0x120 - 128; // VDP $120 -> screen 160
            int bannerScreenY = getBannerScreenY();
            bannerRenderer.drawFrameIndex(0, bannerScreenX, bannerScreenY);

            // TM symbol — VDP x=$188, y=$EC (fixed position)
            if (tmSprite.active && bannerSettled) {
                int tmScreenX = 0x188 - 128; // 264
                int tmScreenY = 0xEC - 128;  // 108
                bannerRenderer.drawFrameIndex(1, tmScreenX, tmScreenY);
            }
        }

        // & KNUCKLES — priority $80
        if (andKnucklesSprite.active && andKnucklesRenderer != null && andKnucklesRenderer.isReady()) {
            int andKnucklesScreenX = 0x120 - 128;
            int andKnucklesScreenY = getAndKnucklesScreenY();
            andKnucklesRenderer.drawFrameIndex(0, andKnucklesScreenX, andKnucklesScreenY);
        }

        // Menu selection — VDP x=$F0, y=$140
        if (selectionSprite.active && selectionRenderer != null && selectionRenderer.isReady()) {
            int selScreenX = 0xF0 - 128; // 112
            int selScreenY = 0x140 - 128; // 192
            selectionRenderer.drawFrameIndex(selectionSprite.mappingFrame,
                    selScreenX, selScreenY);
        }

        // Copyright text — VDP x=$158, y=$14C
        if (copyrightSprite.active && copyrightRenderer != null && copyrightRenderer.isReady()) {
            int copyScreenX = 0x158 - 128; // 216
            int copyScreenY = 0x14C - 128; // 204
            copyrightRenderer.drawFrameIndex(0, copyScreenX, copyScreenY);
        }

        gm.flushPatternBatch();
    }

    /**
     * Renders Plane B (background) for the interactive phase.
     * Uses the Enigma-decoded MapEni_S3TitleBg nametable.
     */
    /**
     * Renders Plane B (background) for the interactive phase.
     * NOT affected by vScroll — on VDP, V_scroll_value is written to VSRAM
     * word 0 (Plane A only). Plane B has its own V_scroll (word 1) which stays at 0.
     */
    private void renderPlaneB(GraphicsManager gm) {
        int[] bgMap = dataLoader.getBackgroundMapping();
        if (bgMap == null || bgMap.length == 0) {
            return;
        }

        int animPatternBase = dataLoader.getAnimPatternBase();

        gm.beginPatternBatch();
        for (int row = 0; row < MAP_HEIGHT; row++) {
            for (int col = 0; col < MAP_WIDTH; col++) {
                int idx = row * MAP_WIDTH + col;
                if (idx >= bgMap.length) {
                    continue;
                }
                int word = bgMap[idx];
                if (word == 0) {
                    continue;
                }
                int tileIndex = word & 0x7FF;
                reusableDesc.set(word);
                gm.renderPatternWithId(animPatternBase + tileIndex, reusableDesc, col * 8, row * 8);
            }
        }
        gm.flushPatternBatch();
    }

    /**
     * Renders Plane A (final Sonic foreground) for the interactive phase.
     * Shifted up by vScroll to match the VDP V_scroll_value behaviour.
     */
    private void renderPlaneA(GraphicsManager gm) {
        int[] fgMap = dataLoader.getAnimationMapping(FINAL_FRAME_INDEX);
        if (fgMap == null || fgMap.length == 0) {
            return;
        }

        int animPatternBase = dataLoader.getAnimPatternBase();

        gm.beginPatternBatch();
        for (int row = 0; row < MAP_HEIGHT; row++) {
            int drawY = row * 8 - vScroll;
            for (int col = 0; col < MAP_WIDTH; col++) {
                int idx = row * MAP_WIDTH + col;
                if (idx >= fgMap.length) {
                    continue;
                }
                int word = fgMap[idx];
                if (word == 0) {
                    continue;
                }
                int tileIndex = word & 0x7FF;
                reusableDesc.set(word);
                gm.renderPatternWithId(animPatternBase + tileIndex, reusableDesc, col * 8, drawY);
            }
        }
        gm.flushPatternBatch();
    }

    // -----------------------------------------------------------------------
    // Sprite renderer initialization
    // -----------------------------------------------------------------------

    /**
     * Initializes sprite renderers for each sprite type.
     * Each type gets its own renderer backed by the shared sprite pattern array.
     */
    private void initSpriteRenderers(GraphicsManager gm) {
        bannerFrames = Sonic3kTitleScreenMappings.createBannerFrames();
        andKnucklesFrames = Sonic3kTitleScreenMappings.createAndKnucklesFrames();
        selectionFrames = Sonic3kTitleScreenMappings.createSelectionFrames();
        copyrightFrames = Sonic3kTitleScreenMappings.createCopyrightFrame();
        sonicAnimFrames = Sonic3kTitleScreenMappings.createSonicAnimFrames();
        tailsPlaneFrames = Sonic3kTitleScreenMappings.createTailsPlaneFrames();

        int spriteBase = dataLoader.getSpritePatternBase();

        bannerRenderer = createSpriteRenderer(bannerFrames, spriteBase, gm);
        andKnucklesRenderer = createSpriteRenderer(andKnucklesFrames, spriteBase, gm);
        selectionRenderer = createSpriteRenderer(selectionFrames, spriteBase, gm);
        copyrightRenderer = createSpriteRenderer(copyrightFrames, spriteBase, gm);
        sonicAnimRenderer = createSpriteRenderer(sonicAnimFrames, spriteBase, gm);
        tailsPlaneRenderer = createSpriteRenderer(tailsPlaneFrames, spriteBase, gm);

        spritesInitialized = true;
        LOGGER.info("S3K title screen sprite renderers initialized");
    }

    private PatternSpriteRenderer createSpriteRenderer(
            List<SpriteMappingFrame> frames, int patternBase, GraphicsManager gm) {
        ObjectSpriteSheet sheet = new ObjectSpriteSheet(
                dataLoader.getSpritePatterns(),
                frames,
                -1,  // paletteIndex = -1 for absolute mode (each piece has its own palette)
                1    // frameDelay
        );
        PatternSpriteRenderer renderer = new PatternSpriteRenderer(sheet);
        renderer.ensurePatternsCached(gm, patternBase);
        return renderer;
    }

    // -----------------------------------------------------------------------
    // Inner state class
    // -----------------------------------------------------------------------

    /**
     * Lightweight animated sprite state for the S3K title screen.
     * Same pattern as S2's AnimatedSprite inner class.
     */
    private static class AnimatedSprite {
        int x;
        int y;
        int mappingFrame;
        boolean active;

        void reset() {
            x = 0;
            y = 0;
            mappingFrame = 0;
            active = false;
        }
    }
}
