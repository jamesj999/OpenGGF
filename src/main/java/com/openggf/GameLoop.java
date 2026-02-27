package com.openggf;

import com.openggf.debug.DebugOverlayToggle;
import com.openggf.game.*;

import com.openggf.Control.InputHandler;
import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugObjectArtViewer;
import com.openggf.game.GameMode;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelSelectProvider;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.RespawnState;
import com.openggf.game.ResultsScreen;
import com.openggf.game.NoOpSpecialStageProvider;
import com.openggf.game.SpecialStageProvider;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectSpawn;
import static org.lwjgl.glfw.GLFW.*;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.timer.TimerManager;
import com.openggf.graphics.FadeManager;

import com.openggf.game.sonic1.Sonic1ZoneFeatureProvider;
import com.openggf.game.sonic1.credits.Sonic1CreditsDemoData;
import com.openggf.game.sonic1.credits.TryAgainEndManager;
import com.openggf.level.WaterSystem;
import com.openggf.debug.playback.PlaybackDebugManager;

import java.io.IOException;
import java.util.Comparator;
import java.util.logging.Logger;

/**
 * Standalone game loop that can run independently of the rendering system.
 * This enables headless testing of game logic without requiring OpenGL context.
 *
 * <p>
 * The GameLoop manages:
 * <ul>
 * <li>Audio updates</li>
 * <li>Timer updates</li>
 * <li>Input processing</li>
 * <li>Game mode transitions (level ↔ special stage)</li>
 * <li>Sprite collision and movement</li>
 * <li>Camera updates</li>
 * <li>Level updates</li>
 * </ul>
 *
 * <p>
 * For headless testing, create a GameLoop with a mock InputHandler
 * and call {@link #step()} to advance one frame.
 */
public class GameLoop {
    private static final Logger LOGGER = Logger.getLogger(GameLoop.class.getName());

    private final SonicConfigurationService configService = SonicConfigurationService.getInstance();
    private final SpriteManager spriteManager = SpriteManager.getInstance();
    private final Camera camera = Camera.getInstance();
    private final TimerManager timerManager = GameServices.timers();
    private final LevelManager levelManager = LevelManager.getInstance();
    private final PerformanceProfiler profiler = PerformanceProfiler.getInstance();
    private final PlaybackDebugManager playbackDebugManager = PlaybackDebugManager.getInstance();
    private SpecialStageProvider activeSpecialStageProvider = NoOpSpecialStageProvider.INSTANCE;

    // Title card provider - lazily initialized when GameModule is available
    private TitleCardProvider titleCardProvider;

    private InputHandler inputHandler;
    private GameMode currentGameMode = GameMode.LEVEL;

    // Special stage results screen
    private ResultsScreen resultsScreen;
    private int ssRingsCollected;
    private boolean ssEmeraldCollected;
    private int ssStageIndex;
    private int resultsFrameCounter = 0;

    // Flag to track when returning from special stage (for title card exit
    // handling)
    private boolean returningFromSpecialStage = false;

    // Flag to freeze level updates during special stage entry transition
    private boolean specialStageTransitionPending = false;

    // Game-agnostic ending/credits provider (wraps S1 CreditsManager, S2 credits, etc.)
    private EndingProvider endingProvider;

    // Listener for game mode changes (used by Engine to update projection)
    private GameModeChangeListener gameModeChangeListener;

    /**
     * Callback interface for game mode changes.
     */
    public interface GameModeChangeListener {
        void onGameModeChanged(GameMode oldMode, GameMode newMode);
    }

    private volatile boolean paused = false;      // Window focus pause
    private volatile boolean userPaused = false;  // Keyboard toggle pause
    private boolean playbackInputSuppressed = false;
    private boolean playbackForcedMaskApplied = false;
    private boolean playbackFrameConsumed = false;

    public GameLoop() {
    }

    public GameLoop(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    public void setInputHandler(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    /**
     * Gets the title card provider, lazily initializing it from the current
     * GameModule.
     * 
     * @return the title card provider
     */
    private TitleCardProvider getTitleCardProviderLazy() {
        if (titleCardProvider == null) {
            titleCardProvider = GameModuleRegistry.getCurrent().getTitleCardProvider();
        }
        return titleCardProvider;
    }

    public InputHandler getInputHandler() {
        return inputHandler;
    }

    public void setGameModeChangeListener(GameModeChangeListener listener) {
        this.gameModeChangeListener = listener;
    }

    public GameMode getCurrentGameMode() {
        return currentGameMode;
    }

    /**
     * Sets the game mode directly. Used for master title screen initialization.
     */
    public void setGameMode(GameMode mode) {
        GameMode oldMode = currentGameMode;
        currentGameMode = mode;
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, mode);
        }
    }

    /**
     * Pauses the game loop due to window losing focus.
     * Audio is also paused to prevent music continuing while game is frozen.
     */
    public synchronized void pause() {
        if (!paused) {
            paused = true;
            updateAudioPauseState();
        }
    }

    /**
     * Resumes the game loop after window regains focus.
     * Audio playback is restored only if user hasn't also paused via keyboard.
     */
    public synchronized void resume() {
        if (paused) {
            paused = false;
            updateAudioPauseState();
        }
    }

    /**
     * Toggles the user-initiated pause state (via keyboard).
     * This is separate from window focus pause so both can work independently.
     */
    public synchronized void toggleUserPause() {
        userPaused = !userPaused;
        updateAudioPauseState();
    }

    /**
     * @return true if the user has paused the game via keyboard
     */
    public boolean isUserPaused() {
        return userPaused;
    }

    /**
     * @return true if the game loop is currently paused (either by window or user)
     */
    public synchronized boolean isPaused() {
        return paused || userPaused;
    }

    /**
     * Updates audio pause state based on combined pause flags.
     * Audio should be paused if either window or user pause is active.
     */
    private synchronized void updateAudioPauseState() {
        if (paused || userPaused) {
            AudioManager.getInstance().pause();
        } else {
            AudioManager.getInstance().resume();
        }
    }

    /**
     * Advances the game by one frame. This is the main update loop.
     * Call this method at your target FPS (typically 60fps).
     */
    public void step() {
        if (inputHandler == null) {
            throw new IllegalStateException("InputHandler must be set before calling step()");
        }
        playbackDebugManager.handleInput(inputHandler);
        syncPlaybackInputBridge();
        playbackDebugManager.setObservedMode(currentGameMode);

        // Master title screen mode - runs before any ROM/game systems are loaded.
        // Must be checked before pause handling since Enter is both confirm and pause.
        if (currentGameMode == GameMode.MASTER_TITLE_SCREEN) {
            MasterTitleScreen masterScreen =
                    Engine.getInstance().getMasterTitleScreen();
            if (masterScreen != null) {
                masterScreen.update(inputHandler);
                if (masterScreen.isGameSelected()) {
                    exitMasterTitleScreen(masterScreen);
                }
            }
            inputHandler.update();
            return;
        }

        // Handle pause toggle - must work even when paused
        int pauseKey = configService.getInt(SonicConfiguration.PAUSE_KEY);
        if (inputHandler.isKeyPressed(pauseKey)) {
            toggleUserPause();
        }

        // Handle frame step - only works when paused
        // isKeyPressed() only returns true on the first frame the key is pressed,
        // so the key must be released and pressed again to step another frame
        int frameStepKey = configService.getInt(SonicConfiguration.FRAME_STEP_KEY);
        boolean doFrameStep = isPaused() && inputHandler.isKeyPressed(frameStepKey);

        // When paused (and not frame stepping), still update input handler so we can detect keys
        if (isPaused() && !doFrameStep) {
            inputHandler.update();
            return;
        }

        profiler.beginSection("audio");
        AudioManager.getInstance().update();
        profiler.endSection("audio");

        profiler.beginSection("timers");
        timerManager.update();
        profiler.endSection("timers");

        profiler.beginSection("input");
        GameServices.debugOverlay().updateInput(inputHandler);
        DebugObjectArtViewer.getInstance().updateInput(inputHandler);

        // Check for Special Stage toggle (TAB by default)
        if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_KEY))) {
            handleSpecialStageDebugKey();
        }

        if (currentGameMode == GameMode.SPECIAL_STAGE) {
            SpecialStageProvider ssProvider = getActiveSpecialStageProvider();

            // Debug complete special stage with emerald (for testing results screen)
            if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_COMPLETE_KEY))) {
                debugCompleteSpecialStageWithEmerald();
            }

            // Debug fail special stage (for testing results screen without emerald)
            if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_FAIL_KEY))) {
                debugFailSpecialStage();
            }

            // Toggle sprite frame debug viewer (shows all animation frames)
            if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_SPRITE_DEBUG_KEY))) {
                ssProvider.toggleSpriteDebugMode();
            }

            // Cycle special stage plane visibility (A/B/both/off)
            if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.SPECIAL_STAGE_PLANE_DEBUG_KEY))) {
                ssProvider.cyclePlaneDebugMode();
            }

            // Handle sprite debug viewer navigation (uses configured movement keys)
            if (ssProvider.isSpriteDebugMode()) {
                SpecialStageDebugProvider debugProvider = ssProvider.getDebugProvider();
                if (debugProvider != null) {
                    // Left/Right: Change page within current graphics set
                    if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.RIGHT))) {
                        debugProvider.nextPage();
                    }
                    if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.LEFT))) {
                        debugProvider.previousPage();
                    }
                    // Up/Down: Cycle between graphics sets
                    if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.DOWN))) {
                        debugProvider.nextSet();
                    }
                    if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.UP))) {
                        debugProvider.previousSet();
                    }
                }
            }

            updateSpecialStageInput();
            ssProvider.update();

            // Check for special stage completion or failure
            if (ssProvider.isFinished()) {
                boolean gotEmerald = ssProvider.isEmeraldCollected();
                enterResultsScreen(gotEmerald);
            }
        } else if (currentGameMode == GameMode.SPECIAL_STAGE_RESULTS) {
            // Update results screen
            resultsFrameCounter++;
            if (resultsScreen != null) {
                resultsScreen.update(resultsFrameCounter, null);
                if (resultsScreen.isComplete()) {
                    exitResultsScreen();
                }
            }
        } else if (currentGameMode == GameMode.TITLE_CARD) {
            // Update title card animation
            TitleCardProvider tcpCard = getTitleCardProviderLazy();
            if (tcpCard != null) {
                tcpCard.update();
            }

            // From disassembly lines 5073-5078: control is released at the START of
            // TEXT_WAIT,
            // not when the title card is complete. This allows the player to move while the
            // text is still visible on screen.
            if (tcpCard == null || tcpCard.shouldReleaseControl()) {
                exitTitleCard();
                // Continue to LEVEL mode processing this frame (fall through)
            } else {
                // Still in locked phase.
                // Keep objects updated during title card lock.
                // SCZ depends on ObjB2 (Tornado) solid updates during this phase so
                // the player lands on the plane instead of free-falling.
                levelManager.updateObjectPositions();
                // Run player physics only if the title card provider allows it.
                // S2: runs physics so Sonic settles onto ground / Tornado (SCZ).
                // S1: ROM title card is blocking; player stays at spawn position
                //     until the title card ends (important for SBZ3 airborne spawn).
                if (tcpCard.shouldRunPlayerPhysics()) {
                    spriteManager.updateWithoutInput();
                }
                // Force camera to snap to player position during title card (no smooth
                // scrolling)
                camera.updatePosition(true);
                return; // Don't process LEVEL mode logic yet
            }
        } else if (currentGameMode == GameMode.TITLE_SCREEN) {
            // Update title screen
            TitleScreenProvider titleScreen = getTitleScreenProviderLazy();
            if (titleScreen != null) {
                titleScreen.update(inputHandler);

                if (titleScreen.isExiting()) {
                    exitTitleScreen();
                }
            }
            inputHandler.update();
            return; // Don't process LEVEL mode logic
        } else if (currentGameMode == GameMode.LEVEL_SELECT) {
            // Update level select screen
            LevelSelectProvider levelSelect = getLevelSelectProviderLazy();
            if (levelSelect != null) {
                levelSelect.update(inputHandler);

                // Check if user made a selection
                if (levelSelect.isExiting()) {
                    exitLevelSelect();
                }
            }
            inputHandler.update();
            return; // Don't process LEVEL mode logic
        } else if (currentGameMode == GameMode.CREDITS_TEXT
                || currentGameMode == GameMode.CREDITS_DEMO
                || currentGameMode == GameMode.TRY_AGAIN_END
                || currentGameMode == GameMode.ENDING_CUTSCENE) {
            updateEnding();
            inputHandler.update();
            return;
        }

        profiler.endSection("input");

        // LEVEL mode (or just transitioned from TITLE_CARD)
        if (currentGameMode == GameMode.LEVEL) {
            // Continue updating title card overlay if still active
            // (TEXT_WAIT and TEXT_EXIT phases where player can move but text is still
            // visible)
            TitleCardProvider tcp = getTitleCardProviderLazy();
            if (tcp != null && tcp.isOverlayActive()) {
                tcp.update();
            }
            // Check if a title card was requested (new level loaded)
            if (levelManager.consumeTitleCardRequest()) {
                enterTitleCard(levelManager.getTitleCardZone(), levelManager.getTitleCardAct());
                return; // Skip normal level update this frame
            }

            // Check for transition requests that need fade-to-black
            FadeManager fadeManager = FadeManager.getInstance();
            if (!fadeManager.isActive()) {
                if (levelManager.consumeRespawnRequest()) {
                    startRespawnFade();
                    return;
                }
                if (levelManager.consumeNextActRequest()) {
                    startNextActFade();
                    return;
                }
                if (levelManager.consumeNextZoneRequest()) {
                    startNextZoneFade();
                    return;
                }
                if (levelManager.consumeZoneActRequest()) {
                    startZoneActFade(levelManager.getRequestedZone(), levelManager.getRequestedAct());
                    return;
                }
                if (levelManager.consumeCreditsRequest()) {
                    startEndingFade();
                    return;
                }
            }

            boolean freezeForArtViewer = GameServices.debugOverlay()
                    .isEnabled(DebugOverlayToggle.OBJECT_ART_VIEWER);
            // Freeze level updates during special stage entry transition
            boolean freezeForSpecialStage = specialStageTransitionPending;
            // ObjB2 transition parity: freeze gameplay during pending zone-act fade.
            boolean freezeForZoneActTransition = levelManager.isLevelInactiveForTransition();
            if (!freezeForArtViewer && !freezeForSpecialStage && !freezeForZoneActTransition) {
                // Objects must update BEFORE player physics so SolidContacts sees new positions.
                // This fixes 1-frame lag on fast-moving platforms (SwingingPlatform, CNZ Elevators).
                profiler.beginSection("objects");
                levelManager.updateObjectPositions();
                profiler.endSection("objects");

                // ROM order: LZWaterFeatures runs before ExecuteObjects (sonic.asm:3043-3044).
                // Water slides and wind tunnels must set f_slidemode and obInertia before
                // Sonic_Move executes so the sliding flag is visible to input handling.
                levelManager.updateZoneFeaturesPrePhysics();

                profiler.beginSection("physics");
                spriteManager.update(inputHandler);
                profiler.endSection("physics");
                if (playbackFrameConsumed) {
                    playbackDebugManager.onLevelFrameAdvanced();
                }

                // Dynamic level events update boundary targets (game-specific)
                LevelEventProvider levelEvents = GameModuleRegistry.getCurrent().getLevelEventProvider();
                if (levelEvents != null) {
                    levelEvents.update();
                }

                profiler.beginSection("camera");
                // Ease boundaries toward targets at 2px/frame
                camera.updateBoundaryEasing();
                camera.updatePosition();
                profiler.endSection("camera");

                profiler.beginSection("level");
                levelManager.update();
                profiler.endSection("level");

                // Check if a checkpoint star requested a special stage
                if (levelManager.consumeSpecialStageRequest()) {
                    enterSpecialStage();
                }
            }

            // Debug keys for level transitions (use request system for fade)
            if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.NEXT_ACT))) {
                levelManager.requestNextAct();
            }

            if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.NEXT_ZONE))) {
                levelManager.requestNextZone();
            }

            // Debug: Teleport to last checkpoint (END key, only in LEVEL mode)
            if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.DEBUG_LAST_CHECKPOINT_KEY))) {
                teleportToLastCheckpoint();
            }

            // Level select key (F9 by default)
            if (inputHandler.isKeyPressed(configService.getInt(SonicConfiguration.LEVEL_SELECT_KEY))) {
                enterLevelSelect();
            }
        }

        inputHandler.update();
    }

    private void syncPlaybackInputBridge() {
        playbackFrameConsumed = false;
        boolean shouldDrive = playbackDebugManager.isDriving(currentGameMode);
        if (shouldDrive != playbackInputSuppressed) {
            spriteManager.setPlaybackInputSuppressed(shouldDrive);
            playbackInputSuppressed = shouldDrive;
        }

        AbstractPlayableSprite player = getMainPlayableSprite();
        if (shouldDrive && player != null) {
            player.setForcedInputMask(playbackDebugManager.getCurrentForcedInputMask());
            player.setForcedJumpPress(playbackDebugManager.isCurrentForcedJumpPress());
            playbackForcedMaskApplied = true;
            playbackFrameConsumed = true;
            return;
        }

        playbackDebugManager.clearLastAppliedState();
        if (playbackForcedMaskApplied && player != null) {
            player.clearForcedInputMask();
            playbackForcedMaskApplied = false;
        } else if (player == null) {
            playbackForcedMaskApplied = false;
        }
    }

    private AbstractPlayableSprite getMainPlayableSprite() {
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (mainCode == null || mainCode.isBlank()) {
            mainCode = "sonic";
        }
        var sprite = spriteManager.getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite playable) {
            return playable;
        }
        return null;
    }

    /**
     * Handles the special stage debug key (TAB by default).
     * When in level mode, enters the next special stage.
     * When in special stage mode, exits to results screen (as failure).
     * When in results screen mode, skips back to level.
     */
    private void handleSpecialStageDebugKey() {
        if (currentGameMode == GameMode.LEVEL) {
            enterSpecialStage();
        } else if (currentGameMode == GameMode.SPECIAL_STAGE) {
            enterResultsScreen(false);
        } else if (currentGameMode == GameMode.SPECIAL_STAGE_RESULTS) {
            exitResultsScreen();
        }
    }

    /**
     * Debug function: Teleports the player to the furthest right checkpoint in the level.
     * Only works in LEVEL mode (END key is used for special stage completion in special stage mode).
     */
    private void teleportToLastCheckpoint() {
        Level level = levelManager.getCurrentLevel();
        if (level == null) {
            return;
        }

        // Find the furthest right checkpoint (game-agnostic)
        int checkpointId = GameModuleRegistry.getCurrent().getCheckpointObjectId();
        if (checkpointId == 0) {
            LOGGER.info("DEBUG: Current game has no checkpoint object ID configured");
            return;
        }
        ObjectSpawn lastCheckpoint = level.getObjects().stream()
            .filter(spawn -> spawn.objectId() == checkpointId)
            .max(Comparator.comparingInt(ObjectSpawn::x))
            .orElse(null);

        if (lastCheckpoint != null) {
            int checkpointX = lastCheckpoint.x();
            int checkpointY = lastCheckpoint.y();

            String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
            if (mainCode == null) mainCode = "sonic";
            var sprite = spriteManager.getSprite(mainCode);
            if (sprite instanceof AbstractPlayableSprite player) {
                // Teleport player to checkpoint position
                player.setX((short) checkpointX);
                player.setY((short) checkpointY);
                player.setXSpeed((short) 0);
                player.setYSpeed((short) 0);
                player.setGSpeed((short) 0);
                player.setAir(false);
                player.setRolling(false);

                // Move camera to center on player (prevents pit death from camera mismatch)
                int screenWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
                int screenHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);
                int cameraX = checkpointX - (screenWidth / 2);
                int cameraY = checkpointY - (screenHeight / 2);

                // Clamp camera to reasonable range (floor at 0)
                cameraX = Math.max(0, cameraX);
                cameraY = Math.max(0, cameraY);

                camera.setX((short) cameraX);
                camera.setY((short) cameraY);

                LOGGER.info("DEBUG: Teleported to checkpoint at (" + checkpointX + ", " + checkpointY +
                    "), camera at (" + cameraX + ", " + cameraY + ")");
            }
        } else {
            LOGGER.info("DEBUG: No checkpoints found in this level");
        }
    }

    /**
     * Debug function: Immediately completes the special stage with emerald
     * collected.
     * Simulates successful completion with the ring requirement met.
     * Press END key during special stage to trigger.
     */
    private void debugCompleteSpecialStageWithEmerald() {
        if (currentGameMode != GameMode.SPECIAL_STAGE) {
            return;
        }

        SpecialStageProvider ssProvider = getActiveSpecialStageProvider();

        // Force emerald collection state
        ssProvider.setEmeraldCollected(true);

        // Get the ring count for this stage from the active provider
        int stageIndex = ssProvider.getCurrentStage();
        int ringRequirement = ssProvider.getDebugCompletionRingCount(stageIndex);

        LOGGER.info("DEBUG: Completing Special Stage " + (stageIndex + 1) +
                " with emerald (forcing " + ringRequirement + " rings)");

        // Enter results screen with emerald collected and simulated ring count
        enterResultsScreenWithDebugRings(true, ringRequirement);
    }

    /**
     * Debug method to fail special stage and go directly to results screen.
     * Press DEL key during special stage to trigger.
     */
    private void debugFailSpecialStage() {
        if (currentGameMode != GameMode.SPECIAL_STAGE) {
            return;
        }

        int stageIndex = getActiveSpecialStageProvider().getCurrentStage();
        int smallRingCount = 15; // A small amount of rings to show ring bonus tally

        LOGGER.info("DEBUG: Failing Special Stage " + (stageIndex + 1) +
                " (with " + smallRingCount + " rings)");

        // Enter results screen without emerald and with small ring count
        enterResultsScreenWithDebugRings(false, smallRingCount);
    }

    /**
     * Enters results screen with a specific ring count (for debug).
     * Uses fade-to-white transition like the normal path.
     */
    private void enterResultsScreenWithDebugRings(boolean emeraldCollected, int ringsCollected) {
        if (currentGameMode != GameMode.SPECIAL_STAGE) {
            return;
        }

        // Don't start another fade if one is already in progress
        FadeManager fadeManager = FadeManager.getInstance();
        if (fadeManager.isActive()) {
            return;
        }

        // Store special stage results for the results screen
        ssRingsCollected = ringsCollected;
        ssEmeraldCollected = emeraldCollected;
        ssStageIndex = getActiveSpecialStageProvider().getCurrentStage();

        // Mark emerald as collected now (so it shows in results screen)
        if (emeraldCollected) {
            GameStateManager gsm = GameServices.gameState();
            gsm.markEmeraldCollected(ssStageIndex);
            LOGGER.info("DEBUG: Collected emerald " + (ssStageIndex + 1) + "! Total: " + gsm.getEmeraldCount());
        }

        // Start fade-to-white, then show results when complete
        fadeManager.startFadeToWhite(() -> {
            doEnterResultsScreenDebug();
        });

        LOGGER.info("DEBUG: Starting fade-to-white to exit Special Stage");
    }

    /**
     * Actually enters the results screen after fade-to-white completes (debug
     * version).
     */
    private void doEnterResultsScreenDebug() {
        doEnterResultsScreen();
    }

    /**
     * Enters the special stage from level mode.
     * Uses GameStateManager to track which stage to enter (cycles 0-6).
     * Performs fade-to-white transition before entering.
     */
    public void enterSpecialStage() {
        if (currentGameMode != GameMode.LEVEL) {
            return;
        }

        FadeManager fadeManager = FadeManager.getInstance();
        boolean screenAlreadyFaded = false;
        boolean fadeFromBlack = false;

        if (fadeManager.isActive()) {
            FadeManager.FadeState fadeState = fadeManager.getState();
            if (fadeState == FadeManager.FadeState.HOLD_WHITE) {
                // Screen is held white (S1 big ring -> results -> fade to white path).
                // Take over and enter special stage directly with fade-from-white.
                screenAlreadyFaded = true;
                fadeFromBlack = false;
            } else if (fadeState == FadeManager.FadeState.HOLD_BLACK) {
                // Screen is held black. Enter special stage with fade-from-black.
                screenAlreadyFaded = true;
                fadeFromBlack = true;
            } else {
                // Different fade in progress, can't start
                return;
            }
        }

        SpecialStageProvider ssProvider = getCurrentModuleSpecialStageProvider();
        if (!ssProvider.hasSpecialStages()) {
            LOGGER.fine("Current game module has no special stages; ignoring entry request");
            return;
        }

        // Clear power-ups before entering special stage
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (mainCode == null)
            mainCode = "sonic";
        var sprite = spriteManager.getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite playable) {
            playable.clearPowerUps();
        }

        // Play special stage entry sound
        playSpecialStageTransitionSfx(ssProvider);

        // Fade out the current music gradually (ROM: MusID_FadeOut / zFadeOutMusic)
        // This preserves the SFX we just started, unlike stopMusic() which silences all
        AudioManager.getInstance().fadeOutMusic();

        // Determine which stage to enter
        GameStateManager gsm = GameServices.gameState();
        final int stageIndex = gsm.consumeCurrentSpecialStageIndexAndAdvance();

        if (screenAlreadyFaded) {
            // Screen is already fully faded (from S1 results screen after big ring).
            // Cancel the hold, enter the special stage directly, and fade to reveal.
            fadeManager.cancel();
            doEnterSpecialStage(ssProvider, stageIndex, fadeFromBlack);
            LOGGER.info("Entering Special Stage " + (stageIndex + 1) +
                    " from " + (fadeFromBlack ? "black" : "white") + " screen (S1 big ring path)");
        } else {
            // Normal path (S2 checkpoint star): freeze level, fade to white, then enter
            specialStageTransitionPending = true;
            fadeManager.startFadeToWhite(() -> {
                doEnterSpecialStage(ssProvider, stageIndex, false);
            });
            LOGGER.info("Starting fade-to-white for Special Stage " + (stageIndex + 1));
        }
    }

    /**
     * Actually enters the special stage after the transition fade completes.
     * Called by the fade callback (fade-to-white) or directly (screen-already-black).
     *
     * @param fadeFromBlack true if the screen is already black and should fade from black;
     *                      false for the normal fade-from-white reveal
     */
    private void doEnterSpecialStage(SpecialStageProvider ssProvider, int stageIndex,
                                     boolean fadeFromBlack) {
        // Clear the transition freeze flag (now we're in special stage mode)
        specialStageTransitionPending = false;

        try {
            ssProvider.reset();
            ssProvider.initializeStage(stageIndex);
            activeSpecialStageProvider = ssProvider;

            GameMode oldMode = currentGameMode;
            currentGameMode = GameMode.SPECIAL_STAGE;

            // Set camera to origin for special stage rendering (uses screen coordinates)
            camera.setX((short) 0);
            camera.setY((short) 0);

            playSpecialStageStageMusic(ssProvider);

            // Notify listener of mode change
            if (gameModeChangeListener != null) {
                gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
            }

            // Reveal the special stage
            if (fadeFromBlack) {
                FadeManager.getInstance().startFadeFromBlack(null);
            } else {
                FadeManager.getInstance().startFadeFromWhite(null);
            }

            LOGGER.info("Entered Special Stage " + (stageIndex + 1) + " (H32 mode: 256x224)");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize Special Stage " + (stageIndex + 1), e);
        }
    }

    /**
     * Enters the results screen after special stage completion/failure.
     * Performs fade-to-white transition before showing results.
     * 
     * @param emeraldCollected true if an emerald was collected
     */
    private void enterResultsScreen(boolean emeraldCollected) {
        if (currentGameMode != GameMode.SPECIAL_STAGE) {
            return;
        }

        // Check if the SS manager pre-started a fade (S1: concurrent fade during exit spin)
        FadeManager fadeManager = FadeManager.getInstance();
        boolean fadeAlreadyWhite = (fadeManager.getState() == FadeManager.FadeState.HOLD_WHITE);

        if (!fadeAlreadyWhite && fadeManager.isActive()) {
            return; // Different fade in progress, wait
        }

        SpecialStageProvider ssProvider = getActiveSpecialStageProvider();

        // Store special stage results for the results screen
        ssRingsCollected = ssProvider.getRingsCollected();
        ssEmeraldCollected = emeraldCollected;
        ssStageIndex = ssProvider.getCurrentStage();

        // Mark emerald as collected now (so it shows in results screen)
        if (emeraldCollected) {
            GameStateManager gsm = GameServices.gameState();
            gsm.markEmeraldCollected(ssStageIndex);
            LOGGER.info("Collected emerald " + (ssStageIndex + 1) + "! Total: " + gsm.getEmeraldCount());
        }

        if (fadeAlreadyWhite) {
            // Fade pre-started by SS manager (S1) - screen is already white.
            // Go directly to results; doEnterResultsScreen() calls startFadeFromWhite().
            doEnterResultsScreen();
        } else {
            // Normal path (S2): start fade-to-white, then callback to results
            fadeManager.startFadeToWhite(() -> {
                doEnterResultsScreen();
            });
        }

        LOGGER.info("Starting fade-to-white to exit Special Stage");
    }

    /**
     * Actually enters the results screen after fade-to-white completes.
     */
    private void doEnterResultsScreen() {
        // Reset special stage provider
        SpecialStageProvider ssProvider = getActiveSpecialStageProvider();
        ssProvider.reset();

        // Transition to results mode
        GameMode oldMode = currentGameMode;
        currentGameMode = GameMode.SPECIAL_STAGE_RESULTS;
        resultsFrameCounter = 0;

        // Create results screen with current emerald count via provider
        int totalEmeralds = GameServices.gameState().getEmeraldCount();
        resultsScreen = ssProvider.createResultsScreen(
                ssRingsCollected, ssEmeraldCollected, ssStageIndex, totalEmeralds);

        if (resultsScreen == null) {
            resultsScreen = NoOpResultsScreen.INSTANCE;
        }

        playSpecialStageResultsMusic(ssProvider);

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        // Start fade-from-white to reveal the results screen
        FadeManager.getInstance().startFadeFromWhite(null);

        LOGGER.info("Entered Special Stage Results Screen (rings=" + ssRingsCollected +
                ", emerald=" + ssEmeraldCollected + ")");
    }

    /**
     * Exits the results screen and shows the title card before returning to the
     * level.
     * Performs fade-to-black transition before showing title card.
     */
    private void exitResultsScreen() {
        if (currentGameMode != GameMode.SPECIAL_STAGE_RESULTS) {
            return;
        }

        // Don't start another fade if one is already in progress
        FadeManager fadeManager = FadeManager.getInstance();
        if (fadeManager.isActive()) {
            return;
        }

        // Play the special stage exit sound (same as entry sound)
        playSpecialStageTransitionSfx(getActiveSpecialStageProvider());

        // Start fade-to-white, then show title card when complete
        fadeManager.startFadeToWhite(() -> {
            doExitResultsScreen();
        });

        LOGGER.info("Starting fade-to-white to exit Results Screen");
    }

    /**
     * Actually exits the results screen after fade-to-black completes.
     */
    private void doExitResultsScreen() {
        // Clean up results screen
        resultsScreen = null;
        activeSpecialStageProvider = NoOpSpecialStageProvider.INSTANCE;

        if (levelManager.getCurrentLevel() == null) {
            // No level was loaded (special stage launched from level select).
            // Load the starting level; loadCurrentLevel() will request its own title card.
            GameMode oldMode = currentGameMode;
            currentGameMode = GameMode.LEVEL;
            try {
                levelManager.loadZoneAndAct(0, 0);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load starting level after special stage", e);
            }

            if (gameModeChangeListener != null) {
                gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
            }

            FadeManager.getInstance().startFadeFromWhite(null);

            LOGGER.info("Exited Results Screen, loaded starting level (no previous level)");
            return;
        }

        boolean reloadLevelBeforeReturn = levelManager.consumeSpecialStageReturnLevelReloadRequest();
        if (reloadLevelBeforeReturn) {
            // Giant-ring progression (S1): zone/act was advanced before SS entry.
            // Load the new act now so player spawn/state matches the title card.
            levelManager.loadCurrentLevel();
        } else {
            // Starpost flow (S2): return to the same act/checkpoint.
            levelManager.reloadLevelPalettes();
        }

        // Consume any pending title card request to prevent double title card
        // (we're manually entering the title card below)
        levelManager.consumeTitleCardRequest();

        // Set flag so exitTitleCard knows to restore checkpoint state
        returningFromSpecialStage = true;

        // Enter title card mode for the current zone/act
        int zoneIndex = levelManager.getCurrentZone();
        int actIndex = levelManager.getCurrentAct();
        enterTitleCardFromResults(zoneIndex, actIndex);

        // Reveal the title card by fading from white (the screen is currently white
        // from exitResultsScreen()'s fade-to-white). Without this, the white overlay
        // persists indefinitely because completeFade() sees no new fade was started.
        FadeManager.getInstance().startFadeFromWhite(null);

        LOGGER.info("Exited Results Screen, entering Title Card for zone " + zoneIndex + " act " + actIndex);
    }

    /**
     * Enters the title card from the results screen context.
     * Similar to enterTitleCard but allows entry from SPECIAL_STAGE_RESULTS mode.
     * Restores the player to their checkpoint position before showing title card.
     */
    private void enterTitleCardFromResults(int zoneIndex, int actIndex) {
        GameMode oldMode = currentGameMode;
        currentGameMode = GameMode.TITLE_CARD;

        // Restore player to checkpoint state BEFORE title card starts
        // This prevents the player from falling/dying during the title card animation
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (mainCode == null)
            mainCode = "sonic";
        var sprite = spriteManager.getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite playable) {
            RespawnState checkpointState = levelManager.getCheckpointState();

            if (checkpointState != null && checkpointState.isActive()) {
                // Restore player and camera position from checkpoint (ROM-accurate)
                checkpointState.restoreToPlayer(playable, camera);
            } else {
                // No checkpoint - camera will follow player at level start position
                camera.updatePosition(true);
            }

            // Freeze all movement during title card
            playable.setXSpeed((short) 0);
            playable.setYSpeed((short) 0);
            playable.setGSpeed((short) 0);
            playable.setAir(false);
            // Clear death/hurt state to prevent dying during title card
            playable.setDead(false);
            playable.setHurt(false);
            playable.setDeathCountdown(0);
            playable.setInvulnerableFrames(0);
            playable.setRolling(false);

            // Reset rings to 0 when returning from special stage
            LevelState gamestate = levelManager.getLevelGamestate();
            if (gamestate != null) {
                gamestate.setRings(0);
            }
        }

        // Initialize the title card manager
        if (getTitleCardProviderLazy() != null) {
            getTitleCardProviderLazy().initialize(zoneIndex, actIndex);
        }

        // Start zone music immediately when title card begins (not at the end)
        int zoneMusicId = levelManager.getCurrentLevelMusicId();
        if (zoneMusicId >= 0) {
            AudioManager.getInstance().playMusic(zoneMusicId);
            LOGGER.fine("Started zone music at title card: 0x" + Integer.toHexString(zoneMusicId));
        }

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }
    }

    /**
     * Enters the title card for the current zone/act.
     * Called when a level first loads or after player respawns.
     *
     * @param zoneIndex Zone index (0-10)
     * @param actIndex  Act index (0-2)
     */
    public void enterTitleCard(int zoneIndex, int actIndex) {
        if (currentGameMode != GameMode.LEVEL) {
            return;
        }

        GameMode oldMode = currentGameMode;
        currentGameMode = GameMode.TITLE_CARD;

        // Freeze the player during title card - full state reset
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (mainCode == null)
            mainCode = "sonic";
        var sprite = spriteManager.getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite playable) {
            // Freeze all movement
            playable.setXSpeed((short) 0);
            playable.setYSpeed((short) 0);
            playable.setGSpeed((short) 0);
            playable.setAir(false);
            // Clear death/hurt state to prevent dying during title card
            playable.setDead(false);
            playable.setHurt(false);
            playable.setDeathCountdown(0);
            playable.setInvulnerableFrames(0);
            playable.setRolling(false);
        }

        // Initialize the title card manager
        if (getTitleCardProviderLazy() != null) {
            getTitleCardProviderLazy().initialize(zoneIndex, actIndex);
        }

        // Snap camera to player position immediately so it's correct from the start
        // Normal updates during the title card will keep it settled
        camera.updatePosition(true);

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        LOGGER.info("Entered Title Card for zone " + zoneIndex + " act " + actIndex);
    }

    /**
     * Exits the title card and returns to level mode.
     * Note: We do NOT reset the title card manager here because the overlay
     * (TEXT_WAIT and TEXT_EXIT phases) still needs to run. The title card
     * will reset itself when it reaches COMPLETE state, or when a new
     * title card is initialized.
     */
    private void exitTitleCard() {
        if (currentGameMode != GameMode.TITLE_CARD) {
            return;
        }

        GameMode oldMode = currentGameMode;
        currentGameMode = GameMode.LEVEL;

        // Don't reset title card - overlay phases (TEXT_WAIT, TEXT_EXIT) still need to
        // run
        // getTitleCardProviderLazy().reset();

        if (returningFromSpecialStage) {
            // Returning from special stage - checkpoint was already restored in
            // enterTitleCardFromResults()
            returningFromSpecialStage = false;
            LOGGER.info("Exited Title Card, returned to level from special stage at checkpoint");
        } else {
            // Normal title card exit (level start)
            // Physics has been running during title card, so player is already settled
            LOGGER.info("Exited Title Card, starting level");
        }

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }
    }

    // ==================== Master Title Screen Methods ====================

    /**
     * Exits the master title screen after the user selects a game.
     * Performs a fade-to-black, then initializes the selected game (Phase 2),
     * and transitions to the game-specific title screen.
     */
    private void exitMasterTitleScreen(MasterTitleScreen masterScreen) {
        FadeManager fadeManager = FadeManager.getInstance();
        if (fadeManager.isActive()) {
            return;
        }

        String selectedGameId = masterScreen.getSelectedGameId();

        fadeManager.startFadeToBlack(() -> {
            doExitMasterTitleScreen(selectedGameId);
        });

        LOGGER.info("Starting fade-to-black for master title screen exit (game: " + selectedGameId + ")");
    }

    /**
     * Actually performs the master title screen exit after fade-to-black completes.
     */
    private void doExitMasterTitleScreen(String selectedGameId) {
        Engine engine = Engine.getInstance();
        if (engine != null) {
            engine.exitMasterTitleScreen(selectedGameId);
        }

        // When TITLE_SCREEN_ON_STARTUP or LEVEL_SELECT_ON_STARTUP is true,
        // initializeGame() sets currentGameMode via initializeTitleScreenMode/
        // initializeLevelSelectMode. Otherwise, loadZoneAndAct() does NOT set
        // currentGameMode directly (it fires a title card request for the next
        // frame). Force mode to LEVEL so step() can process the pending request.
        if (currentGameMode == GameMode.MASTER_TITLE_SCREEN) {
            currentGameMode = GameMode.LEVEL;
        }

        FadeManager.getInstance().startFadeFromBlack(null);

        LOGGER.info("Exited master title screen, now in mode: " + currentGameMode);
    }

    // ==================== Title Screen Methods ====================

    /**
     * Initializes the game loop to start in title screen mode.
     * Called from Engine.init() when TITLE_SCREEN_ON_STARTUP is true.
     */
    public void initializeTitleScreenMode() {
        LOGGER.info("Initializing game in Title Screen mode");

        // Ensure the ROM is loaded and audio is initialized
        try {
            var rom = GameServices.rom().getRom();
            var gameModule = GameModuleRegistry.getCurrent();

            AudioManager audioManager = AudioManager.getInstance();
            audioManager.setAudioProfile(gameModule.getAudioProfile());
            audioManager.setRom(rom);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ROM for title screen", e);
        }

        GameMode oldMode = currentGameMode;
        currentGameMode = GameMode.TITLE_SCREEN;

        camera.setX((short) 0);
        camera.setY((short) 0);

        TitleScreenProvider titleScreen = getTitleScreenProviderLazy();
        if (titleScreen != null) {
            titleScreen.initialize();
        }

        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        LOGGER.info("Game initialized in Title Screen mode");
    }

    /**
     * Exits the title screen. Fades to black, then transitions to level select
     * (if LEVEL_SELECT_ON_STARTUP is true) or loads EHZ Act 1.
     *
     * <p>Special case: If the title screen supports a level select overlay
     * (e.g. Sonic 1), the transition is immediate with no fade and no music
     * restart, matching the original hardware behaviour.
     */
    private void exitTitleScreen() {
        TitleScreenProvider titleScreen = getTitleScreenProviderLazy();
        if (titleScreen == null) {
            return;
        }

        boolean levelSelectOnStartup = configService.getBoolean(SonicConfiguration.LEVEL_SELECT_ON_STARTUP);

        // Sonic 1 level select: immediate transition, no fade, music continues.
        // The original game loads Pal_LevelSel and clears the BG plane instantly.
        // Title screen art data is kept loaded so it can be rendered behind the
        // level select text with the brown/sepia palette tint.
        if (levelSelectOnStartup && titleScreen.supportsLevelSelectOverlay()) {
            doEnterLevelSelectFromTitleScreen();
            return;
        }

        // Don't start another fade if one is already in progress
        FadeManager fadeManager = FadeManager.getInstance();
        if (fadeManager.isActive()) {
            return;
        }

        // Fade out title music
        AudioManager.getInstance().fadeOutMusic();

        // Start fade-to-black, then transition
        fadeManager.startFadeToBlack(() -> {
            doExitTitleScreen();
        });

        LOGGER.info("Starting fade-to-black for Title Screen exit");
    }

    /**
     * Enters the level select screen directly from the title screen, with no
     * fade transition and no music restart. Used by Sonic 1 where the title
     * screen art remains visible (with level select palette) behind the menu.
     *
     * <p>From the Sonic 1 disassembly (Tit_ChkLevSel): the transition loads
     * Pal_LevelSel, clears the BG VRAM plane, and draws the menu text. Music
     * continues uninterrupted.
     */
    private void doEnterLevelSelectFromTitleScreen() {
        // Do NOT reset the title screen - its art data is still needed
        // for rendering the frozen background behind level select text.
        // Do NOT fade music - title music continues.

        GameMode oldMode = currentGameMode;
        currentGameMode = GameMode.LEVEL_SELECT;

        camera.setX((short) 0);
        camera.setY((short) 0);

        LevelSelectProvider levelSelect = getLevelSelectProviderLazy();
        if (levelSelect != null) {
            levelSelect.initializeFromTitleScreen();
        }

        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        LOGGER.info("Title screen -> Level Select (immediate, no fade)");
    }

    /**
     * Actually performs the title screen exit after fade-to-black completes.
     */
    private void doExitTitleScreen() {
        TitleScreenProvider titleScreen = getTitleScreenProviderLazy();
        if (titleScreen != null) {
            titleScreen.reset();
        }

        boolean levelSelectOnStartup = configService.getBoolean(SonicConfiguration.LEVEL_SELECT_ON_STARTUP);
        if (levelSelectOnStartup) {
            // Transition to level select
            doEnterLevelSelect();
        } else {
            // Load EHZ Act 1
            GameMode oldMode = currentGameMode;
            currentGameMode = GameMode.LEVEL;

            try {
                levelManager.loadZoneAndAct(0, 0);
            } catch (IOException e) {
                LOGGER.severe("Failed to load EHZ Act 1: " + e.getMessage());
                throw new RuntimeException("Failed to load EHZ Act 1", e);
            }

            if (gameModeChangeListener != null) {
                gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
            }

            FadeManager.getInstance().startFadeFromBlack(null);
            LOGGER.info("Title screen -> EHZ Act 1");
        }
    }

    /**
     * Gets the title screen provider from the current game module.
     */
    public TitleScreenProvider getTitleScreenProvider() {
        return getTitleScreenProviderLazy();
    }

    private TitleScreenProvider getTitleScreenProviderLazy() {
        var gameModule = GameModuleRegistry.getCurrent();
        if (gameModule != null) {
            return gameModule.getTitleScreenProvider();
        }
        return null;
    }

    // ==================== Level Select Methods ====================

    /**
     * Initializes the game loop to start directly in level select mode.
     * Called from Engine.init() when LEVEL_SELECT_ON_STARTUP is true.
     * Unlike enterLevelSelect(), this does not require being in LEVEL mode first
     * and does not perform a fade transition.
     */
    public void initializeLevelSelectMode() {
        LOGGER.info("Initializing game in Level Select mode");

        // Ensure the ROM is loaded and audio is initialized before level select
        try {
            var rom = GameServices.rom().getRom();
            var gameModule = GameModuleRegistry.getCurrent();

            // Initialize audio system with game module's audio profile
            AudioManager audioManager = AudioManager.getInstance();
            audioManager.setAudioProfile(gameModule.getAudioProfile());
            audioManager.setRom(rom);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load ROM for level select", e);
        }

        GameMode oldMode = currentGameMode;
        currentGameMode = GameMode.LEVEL_SELECT;

        // Set camera to origin for level select rendering
        camera.setX((short) 0);
        camera.setY((short) 0);

        // Initialize the level select provider (also loads and caches palettes)
        LevelSelectProvider levelSelect = getLevelSelectProviderLazy();
        if (levelSelect != null) {
            levelSelect.initialize();
        }

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        LOGGER.info("Game initialized in Level Select mode");
    }

    /**
     * Enters the level select screen from level mode.
     * Performs fade-to-black transition before showing level select.
     */
    public void enterLevelSelect() {
        if (currentGameMode != GameMode.LEVEL) {
            return;
        }

        // Don't start another fade if one is already in progress
        FadeManager fadeManager = FadeManager.getInstance();
        if (fadeManager.isActive()) {
            return;
        }

        // Fade out current music
        AudioManager.getInstance().fadeOutMusic();

        // Start fade-to-black, then enter level select when complete
        fadeManager.startFadeToBlack(() -> {
            doEnterLevelSelect();
        });

        LOGGER.info("Starting fade-to-black for Level Select");
    }

    /**
     * Actually enters the level select screen after fade-to-black completes.
     */
    private void doEnterLevelSelect() {
        GameMode oldMode = currentGameMode;
        currentGameMode = GameMode.LEVEL_SELECT;

        // Set camera to origin for level select rendering
        camera.setX((short) 0);
        camera.setY((short) 0);

        // Initialize the level select provider
        LevelSelectProvider levelSelect = getLevelSelectProviderLazy();
        if (levelSelect != null) {
            levelSelect.initialize();
        }

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        // Start fade-from-black to reveal the level select
        FadeManager.getInstance().startFadeFromBlack(null);

        LOGGER.info("Entered Level Select screen");
    }

    /**
     * Exits the level select screen and loads the selected zone/act or special stage.
     */
    private void exitLevelSelect() {
        LevelSelectProvider levelSelect = getLevelSelectProviderLazy();
        if (levelSelect == null) {
            return;
        }

        // Don't start another fade if one is already in progress
        FadeManager fadeManager = FadeManager.getInstance();
        if (fadeManager.isActive()) {
            return;
        }

        if (levelSelect.isSpecialStageSelected()) {
            // Enter special stage
            levelSelect.reset();
            GameMode oldMode = currentGameMode;
            currentGameMode = GameMode.LEVEL;

            // Notify listener of mode change
            if (gameModeChangeListener != null) {
                gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
            }

            // Now enter special stage via the normal path
            enterSpecialStage();
            LOGGER.info("Level select -> Special Stage");

        } else if (levelSelect.isSoundTestSelected()) {
            // Sound test was selected but not a level, just reset
            levelSelect.reset();
            LOGGER.info("Level select sound test (no level transition)");

        } else {
            // Load selected zone/act
            int zone = levelSelect.getSelectedZone();
            int act = levelSelect.getSelectedAct();

            // Reset level select manager
            levelSelect.reset();

            // Fade out level select music
            AudioManager.getInstance().fadeOutMusic();

            // Start fade-to-black, then load level
            FadeManager.getInstance().startFadeToBlack(() -> {
                doExitLevelSelectToZone(zone, act);
            });

            LOGGER.info("Level select -> Zone " + zone + " Act " + act);
        }
    }

    /**
     * Actually loads the selected zone/act after fade-to-black completes.
     */
    private void doExitLevelSelectToZone(int zone, int act) {
        GameMode oldMode = currentGameMode;
        currentGameMode = GameMode.LEVEL;

        // Load the selected zone/act
        try {
            levelManager.loadZoneAndAct(zone, act);
        } catch (IOException e) {
            LOGGER.severe("Failed to load zone " + zone + " act " + act + ": " + e.getMessage());
            throw new RuntimeException("Failed to load zone " + zone + " act " + act, e);
        }

        // Notify listener of mode change
        if (gameModeChangeListener != null) {
            gameModeChangeListener.onGameModeChanged(oldMode, currentGameMode);
        }

        // Start fade-from-black to reveal the title card
        FadeManager.getInstance().startFadeFromBlack(null);

        LOGGER.info("Loaded zone " + zone + " act " + act + " from level select");
    }

    /**
     * Gets the level select provider from the current game module.
     *
     * @return the level select provider, or null if not available
     */
    public LevelSelectProvider getLevelSelectProvider() {
        return getLevelSelectProviderLazy();
    }

    /**
     * Lazily retrieves the level select provider from the current game module.
     */
    private LevelSelectProvider getLevelSelectProviderLazy() {
        var gameModule = GameModuleRegistry.getCurrent();
        if (gameModule != null) {
            return gameModule.getLevelSelectProvider();
        }
        return null;
    }

    // ==================== Level Transition Methods with Fade ====================

    /**
     * Starts the fade-to-black transition for death respawn.
     */
    private void startRespawnFade() {
        LOGGER.info("Starting fade-to-black for respawn");

        // Fade out current music (ROM: s2.asm:4757 - level entry with title card)
        AudioManager.getInstance().fadeOutMusic();

        // Start fade-to-black, then respawn when complete
        FadeManager.getInstance().startFadeToBlack(() -> {
            doRespawn();
        });
    }

    /**
     * Actually performs the respawn after fade-to-black completes.
     */
    private void doRespawn() {
        // Reload the current level (with title card)
        levelManager.loadCurrentLevel();

        // Start fade-from-black to reveal the title card
        FadeManager.getInstance().startFadeFromBlack(null);

        LOGGER.info("Respawned player, entering title card");
    }

    /**
     * Starts the fade-to-black transition for next act.
     */
    private void startNextActFade() {
        LOGGER.info("Starting fade-to-black for next act");

        // Fade out current music (ROM: s2.asm:4757 - level entry with title card)
        AudioManager.getInstance().fadeOutMusic();

        // Start fade-to-black, then load next act when complete
        FadeManager.getInstance().startFadeToBlack(() -> {
            doNextAct();
        });
    }

    /**
     * Actually loads the next act after fade-to-black completes.
     */
    private void doNextAct() {
        try {
            levelManager.nextAct();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load next act", e);
        }

        // Start fade-from-black to reveal the title card
        FadeManager.getInstance().startFadeFromBlack(null);

        LOGGER.info("Loaded next act");
    }

    /**
     * Starts the fade-to-black transition for next zone.
     */
    private void startNextZoneFade() {
        LOGGER.info("Starting fade-to-black for next zone");

        // Fade out current music (ROM: s2.asm:4757 - level entry with title card)
        AudioManager.getInstance().fadeOutMusic();

        // Start fade-to-black, then load next zone when complete
        FadeManager.getInstance().startFadeToBlack(() -> {
            doNextZone();
        });
    }

    /**
     * Actually loads the next zone after fade-to-black completes.
     */
    private void doNextZone() {
        try {
            levelManager.nextZone();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load next zone", e);
        }

        // Start fade-from-black to reveal the title card
        FadeManager.getInstance().startFadeFromBlack(null);

        LOGGER.info("Loaded next zone");
    }

    /**
     * Starts the fade-to-black transition for a specific zone/act.
     */
    private void startZoneActFade(int zone, int act) {
        LOGGER.info("Starting fade-to-black for zone " + zone + " act " + act);

        AudioManager.getInstance().fadeOutMusic();

        FadeManager.getInstance().startFadeToBlack(() -> {
            doZoneAct(zone, act);
        });
    }

    /**
     * Actually loads the specified zone/act after fade-to-black completes.
     */
    private void doZoneAct(int zone, int act) {
        try {
            levelManager.loadZoneAndAct(zone, act);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load zone " + zone + " act " + act, e);
        }

        FadeManager.getInstance().startFadeFromBlack(null);

        LOGGER.info("Loaded zone " + zone + " act " + act);
    }

    /**
     * Gets the title card provider (for rendering).
     * 
     * @return the title card provider
     */
    public TitleCardProvider getTitleCardProvider() {
        return getTitleCardProviderLazy();
    }

    /**
     * Gets the current results screen object (for rendering).
     * 
     * @return the results screen object, or null if not in results mode
     */
    public ResultsScreen getResultsScreen() {
        return resultsScreen;
    }

    public SpecialStageProvider getActiveSpecialStageProvider() {
        if (currentGameMode == GameMode.SPECIAL_STAGE || currentGameMode == GameMode.SPECIAL_STAGE_RESULTS) {
            return activeSpecialStageProvider != null ? activeSpecialStageProvider : NoOpSpecialStageProvider.INSTANCE;
        }
        return getCurrentModuleSpecialStageProvider();
    }

    private SpecialStageProvider getCurrentModuleSpecialStageProvider() {
        var module = GameModuleRegistry.getCurrent();
        if (module == null) {
            return NoOpSpecialStageProvider.INSTANCE;
        }
        return module.getSpecialStageProvider();
    }

    private void playSpecialStageTransitionSfx(SpecialStageProvider ssProvider) {
        int sfxId = ssProvider.getTransitionSfxId();
        if (sfxId >= 0) {
            AudioManager.getInstance().playSfx(sfxId);
        }
    }

    private void playSpecialStageStageMusic(SpecialStageProvider ssProvider) {
        int musicId = ssProvider.getStageMusicId();
        if (musicId >= 0) {
            AudioManager.getInstance().playMusic(musicId);
        }
    }

    private void playSpecialStageResultsMusic(SpecialStageProvider ssProvider) {
        int musicId = ssProvider.getResultsMusicId();
        if (musicId >= 0) {
            AudioManager.getInstance().playMusic(musicId);
        }
    }

    private void updateSpecialStageInput() {
        int leftKey = configService.getInt(SonicConfiguration.LEFT);
        int rightKey = configService.getInt(SonicConfiguration.RIGHT);
        int upKey = configService.getInt(SonicConfiguration.UP);
        int downKey = configService.getInt(SonicConfiguration.DOWN);
        int jumpKey = configService.getInt(SonicConfiguration.JUMP);
        int debugModeKey = configService.getInt(SonicConfiguration.DEBUG_MODE_KEY);

        SpecialStageProvider ssProvider = getActiveSpecialStageProvider();

        if (inputHandler.isKeyPressed(debugModeKey)) {
            ssProvider.toggleGameplayDebugMode();
        }

        if (inputHandler.isKeyPressed(GLFW_KEY_F4)) {
            ssProvider.toggleAlignmentTestMode();
        }

        // Lag compensation adjustment (F6 decrease, F7 increase)
        if (inputHandler.isKeyPressed(GLFW_KEY_F6)) {
            adjustLagCompensation(-0.05);
        }
        if (inputHandler.isKeyPressed(GLFW_KEY_F7)) {
            adjustLagCompensation(0.05);
        }

        if (ssProvider.isAlignmentTestMode()) {
            if (inputHandler.isKeyPressed(leftKey)) {
                ssProvider.adjustAlignmentOffset(-1);
            }
            if (inputHandler.isKeyPressed(rightKey)) {
                ssProvider.adjustAlignmentOffset(1);
            }
            if (inputHandler.isKeyPressed(upKey)) {
                ssProvider.adjustAlignmentSpeed(0.1);
            }
            if (inputHandler.isKeyPressed(downKey)) {
                ssProvider.adjustAlignmentSpeed(-0.1);
            }
            if (inputHandler.isKeyPressed(GLFW_KEY_SPACE)) {
                ssProvider.toggleAlignmentStepMode();
            }
            return;
        }

        int heldButtons = 0;
        int pressedButtons = 0;

        if (inputHandler.isKeyDown(leftKey)) {
            heldButtons |= 0x04;
        }
        if (inputHandler.isKeyDown(rightKey)) {
            heldButtons |= 0x08;
        }

        if (ssProvider.isGameplayDebugMode()) {
            if (inputHandler.isKeyDown(upKey)) {
                heldButtons |= 0x01;
            }
            if (inputHandler.isKeyDown(downKey)) {
                heldButtons |= 0x02;
            }
        } else {
            if (inputHandler.isKeyPressed(jumpKey)) {
                pressedButtons |= 0x70;
            }
            if (inputHandler.isKeyDown(jumpKey)) {
                heldButtons |= 0x70;
            }
        }

        ssProvider.handleInput(heldButtons, pressedButtons);
    }

    /**
     * Adjusts the lag compensation factor for the entire special stage simulation.
     * The lag compensation simulates original Mega Drive hardware lag frames,
     * affecting track animation, player movement, object speed, and all other
     * timing.
     *
     * @param delta Amount to adjust (positive = more lag compensation = slower
     *              simulation)
     */
    private void adjustLagCompensation(double delta) {
        SpecialStageProvider ssProvider = getActiveSpecialStageProvider();
        if (!ssProvider.isInitialized()) {
            return;
        }

        double current = ssProvider.getLagCompensation();
        double newValue = current + delta;
        ssProvider.setLagCompensation(newValue);

        // Calculate effective simulation rate for display
        // Base is 60 fps. With lag compensation, effective = 60 * (1 - lagComp)
        double effectiveUpdates = 60.0 * (1.0 - ssProvider.getLagCompensation());

        LOGGER.info(String.format("Lag compensation: %.0f%% (effective ~%.1f updates/sec)",
                ssProvider.getLagCompensation() * 100, effectiveUpdates));
    }

    // ==================== Ending / Credits Sequence Methods ====================

    /**
     * Starts the fade-to-black transition to enter the ending sequence.
     * Called when the level requests credits (e.g., after final boss defeat).
     */
    private void startEndingFade() {
        LOGGER.info("Starting fade-to-black for ending sequence");
        AudioManager.getInstance().fadeOutMusic();
        FadeManager.getInstance().startFadeToBlack(this::doEnterEnding);
    }

    /**
     * Actually enters the ending sequence after fade-to-black completes.
     * Initializes the EndingProvider from the current GameModule.
     */
    private void doEnterEnding() {
        endingProvider = GameModuleRegistry.getCurrent().getEndingProvider();
        if (endingProvider == null) {
            // No ending provider for this game — return to title screen
            LOGGER.warning("No EndingProvider available, returning to title screen");
            setGameMode(GameMode.TITLE_SCREEN);
            TitleScreenProvider titleScreen = getTitleScreenProviderLazy();
            if (titleScreen != null) {
                titleScreen.initialize();
            }
            return;
        }

        endingProvider.initialize();
        setGameMode(gameModeForPhase(endingProvider.getCurrentPhase()));

        // Reveal the ending scene (screen is currently black from startEndingFade())
        // ROM: PaletteFadeIn after loading ending art
        FadeManager.getInstance().startFadeFromBlack(null);

        LOGGER.info("Entered ending sequence, phase=" + endingProvider.getCurrentPhase());
    }

    /**
     * Unified update method for all ending-related game modes.
     * Dispatches to phase-specific logic based on the current EndingPhase.
     */
    private void updateEnding() {
        if (endingProvider == null) {
            return;
        }

        EndingPhase phase = endingProvider.getCurrentPhase();

        switch (phase) {
            case CREDITS_TEXT -> updateEndingCreditsText();
            case CREDITS_DEMO -> updateEndingCreditsDemo();
            case POST_CREDITS -> updateEndingPostCredits();
            case CUTSCENE -> updateEndingCutscene();
            case FINISHED -> exitEndingToTitleScreen();
        }

        // Sync GameMode to the current phase (in case provider changed phase internally)
        if (endingProvider != null && !endingProvider.isComplete()) {
            EndingPhase newPhase = endingProvider.getCurrentPhase();
            GameMode targetMode = gameModeForPhase(newPhase);
            if (targetMode != currentGameMode) {
                setGameMode(targetMode);
            }
        }
    }

    /**
     * CREDITS_TEXT phase: update provider, check for demo load requests and completion.
     */
    private void updateEndingCreditsText() {
        endingProvider.update();

        // Check if the provider wants to load a demo zone
        if (endingProvider.hasDemoLoadRequest()) {
            endingProvider.consumeDemoLoadRequest();
            loadEndingDemoZone();
        }

        // Check if ending is complete (e.g., after "PRESENTED BY SEGA")
        if (endingProvider.isComplete()) {
            exitEndingToTitleScreen();
        }
    }

    /**
     * CREDITS_DEMO phase: update provider, run level physics with demo input.
     */
    private void updateEndingCreditsDemo() {
        endingProvider.update();

        // Apply demo input to player
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (mainCode == null) mainCode = "sonic";
        var sprite = spriteManager.getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite player) {
            // ROM does NOT set obj_control during demos — it writes demo input
            // directly to jpadhold1/jpadpress1 (MoveSonicInDemo.asm).
            // Do NOT use controlLocked here: PlayableSpriteMovement re-reads it
            // and would zero out left/right/jump, overriding the forced input.
            player.setForcedInputMask(endingProvider.getDemoInputMask());
        }

        // Run level physics (objects + player)
        levelManager.updateObjectPositions();
        levelManager.updateZoneFeaturesPrePhysics();
        spriteManager.update(inputHandler);

        // Level events
        LevelEventProvider levelEvents = GameModuleRegistry.getCurrent().getLevelEventProvider();
        if (levelEvents != null) {
            levelEvents.update();
        }

        // Camera/scroll only if not frozen (during fadeout, scroll freezes)
        if (!endingProvider.isScrollFrozen()) {
            camera.updateBoundaryEasing();
            camera.updatePosition();
            levelManager.update();
        }

        // Check if returning to text phase
        if (endingProvider.hasTextReturnRequest()) {
            endingProvider.consumeTextReturnRequest();
            returnFromEndingDemo();
        }

        // Check if ending is complete
        if (endingProvider.isComplete()) {
            exitEndingToTitleScreen();
        }
    }

    /**
     * POST_CREDITS phase: update the post-credits screen (e.g., TRY AGAIN / END).
     * For Sonic 1, the TryAgainEndManager needs InputHandler access for START press.
     * For Sonic 2, the LogoFlashManager needs InputHandler for button skip detection.
     */
    private void updateEndingPostCredits() {
        if (endingProvider instanceof com.openggf.game.sonic1.credits.Sonic1EndingProvider s1Provider) {
            TryAgainEndManager tryAgainEnd = s1Provider.getTryAgainEndManager();
            if (tryAgainEnd != null) {
                tryAgainEnd.update(inputHandler);
                if (tryAgainEnd.consumeExitRequest()) {
                    exitEndingToTitleScreen();
                    return;
                }
            }
        } else if (endingProvider instanceof com.openggf.game.sonic2.credits.Sonic2EndingProvider s2Provider) {
            // S2 logo flash needs InputHandler for B/C/A/Start skip detection
            var logoFlash = s2Provider.getLogoFlashManager();
            if (logoFlash != null) {
                logoFlash.update(inputHandler);
                if (logoFlash.isDone()) {
                    exitEndingToTitleScreen();
                    return;
                }
            } else {
                // Logo not yet loaded -- let provider advance its internal state
                endingProvider.update();
            }
            if (endingProvider.isComplete()) {
                exitEndingToTitleScreen();
            }
        } else {
            // Generic provider: just call update() and check completion
            endingProvider.update();
            if (endingProvider.isComplete()) {
                exitEndingToTitleScreen();
            }
        }
    }

    /**
     * CUTSCENE phase: update provider (e.g., S2 Tornado flyby).
     */
    private void updateEndingCutscene() {
        endingProvider.update();

        if (endingProvider.isComplete()) {
            exitEndingToTitleScreen();
        }
    }

    /**
     * Loads a demo zone for ending credits and transitions to CREDITS_DEMO mode.
     * Reads zone/act/position from the EndingProvider.
     */
    private void loadEndingDemoZone() {
        int zone = endingProvider.getDemoZone();
        int act = endingProvider.getDemoAct();
        int startX = endingProvider.getDemoStartX();
        int startY = endingProvider.getDemoStartY();

        try {
            // Suppress zone music — credits music should continue playing
            levelManager.setSuppressNextMusicChange(true);
            levelManager.loadZoneAndAct(zone, act);
            // Consume the title card request since we don't want a title card
            levelManager.consumeTitleCardRequest();
        } catch (IOException e) {
            LOGGER.severe("Failed to load ending demo zone " + zone + " act " + act + ": " + e.getMessage());
            return;
        }

        // Suppress HUD during credits demos (ROM: HUD is never drawn during credits)
        levelManager.setForceHudSuppressed(true);

        // Position the player at the demo start position (ROM uses center coordinates)
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (mainCode == null) mainCode = "sonic";
        var sprite = spriteManager.getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite player) {
            // ROM: EndingDemoLoad clears rings, time, score, lamppost (sonic.asm:4148-4152)
            player.setRingCount(0);
            player.setXSpeed((short) 0);
            player.setYSpeed((short) 0);
            player.setGSpeed((short) 0);
            // Don't set controlLocked — ROM doesn't use obj_control during demos.
            // PlayableSpriteMovement re-reads obj_control and would block all input.
            player.setControlLocked(false);
            player.setForcedInputMask(0);

            if (endingProvider.isLzDemo()) {
                // LZ demo (credit 3): use lamppost position, not startpos
                // ROM: EndDemo_LampVar (sonic.asm:4176-4187) — lamppost is pre-loaded
                // so the level code uses lamppost position instead of startpos.
                // ROM checks v_creditsnum==4 (already incremented) = original credit 3.
                player.setCentreX((short) Sonic1CreditsDemoData.LZ_LAMP_X);
                player.setCentreY((short) Sonic1CreditsDemoData.LZ_LAMP_Y);
                player.setRingCount(Sonic1CreditsDemoData.LZ_LAMP_RINGS);
                camera.setX((short) Sonic1CreditsDemoData.LZ_LAMP_CAMERA_X);
                camera.setY((short) Sonic1CreditsDemoData.LZ_LAMP_CAMERA_Y);
                camera.setMaxY((short) Sonic1CreditsDemoData.LZ_LAMP_BOTTOM_BND);

                // Restore lamppost water state (ROM: EndDemo_LampVar dc.w $308 / dc.b 1,1)
                // Level loading sets water to default LZ3 height (0x0900), but the demo
                // starts at a lamppost where water has risen to 0x0308 with routine 1.
                int featureZone = levelManager.getFeatureZoneId();
                int featureAct = levelManager.getFeatureActId();
                WaterSystem waterSystem = WaterSystem.getInstance();
                waterSystem.setWaterLevelDirect(featureZone, featureAct,
                        Sonic1CreditsDemoData.LZ_LAMP_WATER_HEIGHT);
                waterSystem.setWaterLevelTarget(featureZone, featureAct,
                        Sonic1CreditsDemoData.LZ_LAMP_WATER_HEIGHT);
                ZoneFeatureProvider zfp = levelManager.getZoneFeatureProvider();
                if (zfp instanceof Sonic1ZoneFeatureProvider s1zfp && s1zfp.getWaterEvents() != null) {
                    s1zfp.getWaterEvents().setWaterRoutine(Sonic1CreditsDemoData.LZ_LAMP_WATER_ROUTINE);
                }
            } else {
                // All other demos: use startpos (center coordinates)
                player.setCentreX((short) startX);
                player.setCentreY((short) startY);
            }
        }

        // Snap camera to player position (unless LZ which has explicit camera coords)
        if (!endingProvider.isLzDemo()) {
            camera.updatePosition(true);
        }

        // Suppress player keyboard input — demo input comes from forcedInputMask only
        spriteManager.setInputSuppressed(true);

        // Switch to CREDITS_DEMO mode
        setGameMode(GameMode.CREDITS_DEMO);

        // Notify provider that zone is loaded
        endingProvider.onDemoZoneLoaded();

        LOGGER.info("Loaded ending demo zone " + zone + " act " + act +
                " at (" + startX + ", " + startY + ")");
    }

    /**
     * Returns from CREDITS_DEMO to CREDITS_TEXT for the next credit.
     */
    private void returnFromEndingDemo() {
        // Restore player keyboard input and clear HUD suppression
        spriteManager.setInputSuppressed(false);
        levelManager.setForceHudSuppressed(false);
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (mainCode == null) mainCode = "sonic";
        var sprite = spriteManager.getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite player) {
            player.setControlLocked(false);
            player.clearForcedInputMask();
        }

        setGameMode(GameMode.CREDITS_TEXT);
        endingProvider.onReturnToText();

        LOGGER.info("Returned from ending demo to credits text");
    }

    /**
     * Exits the ending sequence and returns to the title screen.
     */
    private void exitEndingToTitleScreen() {
        LOGGER.info("Ending sequence complete, returning to title screen");

        // Clean up any remaining demo state
        spriteManager.setInputSuppressed(false);
        levelManager.setForceHudSuppressed(false);
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        if (mainCode == null) mainCode = "sonic";
        var sprite = spriteManager.getSprite(mainCode);
        if (sprite instanceof AbstractPlayableSprite player) {
            player.setControlLocked(false);
            player.clearForcedInputMask();
        }

        AudioManager.getInstance().fadeOutMusic();

        FadeManager fadeManager = FadeManager.getInstance();
        if (!fadeManager.isActive()) {
            fadeManager.startFadeToBlack(this::doExitEndingToTitleScreen);
        } else {
            doExitEndingToTitleScreen();
        }
    }

    /**
     * Actually transitions to the title screen after fade completes.
     */
    private void doExitEndingToTitleScreen() {
        endingProvider = null;

        camera.setX((short) 0);
        camera.setY((short) 0);

        setGameMode(GameMode.TITLE_SCREEN);

        TitleScreenProvider titleScreen = getTitleScreenProviderLazy();
        if (titleScreen != null) {
            titleScreen.initialize();
        }

        FadeManager.getInstance().startFadeFromBlack(null);
        LOGGER.info("Ending -> Title Screen");
    }

    /**
     * Maps an {@link EndingPhase} to the corresponding {@link GameMode}.
     */
    private GameMode gameModeForPhase(EndingPhase phase) {
        return switch (phase) {
            case CUTSCENE -> GameMode.ENDING_CUTSCENE;
            case CREDITS_TEXT -> GameMode.CREDITS_TEXT;
            case CREDITS_DEMO -> GameMode.CREDITS_DEMO;
            case POST_CREDITS -> GameMode.TRY_AGAIN_END;
            case FINISHED -> GameMode.TITLE_SCREEN;
        };
    }

    /**
     * Gets the ending provider (for rendering in Engine.java).
     */
    public EndingProvider getEndingProvider() {
        return endingProvider;
    }
}
