package com.openggf;

import com.openggf.game.*;
import com.openggf.graphics.*;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import com.openggf.Control.InputHandler;
import com.openggf.audio.AudioManager;
import com.openggf.audio.LWJGLAudioBackend;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugOption;
import com.openggf.debug.DebugRenderer;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.debug.DebugState;
import com.openggf.level.LevelManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.KnucklesRespawnStrategy;
import com.openggf.sprites.playable.Sonic;
import com.openggf.sprites.playable.SonicRespawnStrategy;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.playable.SidekickCpuController;
import com.openggf.debug.playback.PlaybackDebugManager;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static org.lwjgl.glfw.Callbacks.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.system.MemoryStack.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Controls the game.
 *
 * @author james
 */
public class Engine {
	private static final Logger LOGGER = Logger.getLogger(Engine.class.getName());
	public static final String RESOURCES_SHADERS_PIXEL_SHADER_GLSL = "shaders/shader_the_hedgehog.glsl";
	private final SonicConfigurationService configService = SonicConfigurationService.getInstance();
	private final SpriteManager spriteManager = SpriteManager.getInstance();
	private final GraphicsManager graphicsManager = GraphicsManager.getInstance();

	private final Camera camera = Camera.getInstance();
	// Lazy-initialized: DebugRenderer.<clinit> references java.awt.Color which
	// is unavailable in GraalVM native-image builds.
	private DebugRenderer debugRenderer;
	private final PerformanceProfiler profiler = PerformanceProfiler.getInstance();

	private final GameLoop gameLoop = new GameLoop();

	public static DebugState debugState = DebugState.NONE;
	public static DebugOption debugOption = DebugOption.A;

	private double realWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
	private double realHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);

	// Current projection width - can be changed for H32/H40 mode switching
	// H40 mode (normal levels): 320 pixels wide
	// H32 mode (special stages): 256 pixels wide
	private double projectionWidth = realWidth;

	private boolean debugViewEnabled = configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);

	private final LevelManager levelManager = LevelManager.getInstance();

	// Pre-allocated list for results screen rendering
	private final java.util.List<GLCommand> resultsCommands = new java.util.ArrayList<>(64);

	// GLFW window handle
	private long window;

	// Window dimensions
	private int windowWidth;
	private int windowHeight;

	private boolean overlayStateReady = false;

	// Input handler for keyboard input
	private InputHandler inputHandler;

	// Master title screen (game selection before ROM loading)
	private MasterTitleScreen masterTitleScreen;

	// Viewport parameters for aspect-ratio-correct rendering
	private int viewportX = 0;
	private int viewportY = 0;
	private int viewportWidth = 0;
	private int viewportHeight = 0;

	// Window snap-to-integer-scale after resize
	private long lastResizeTimeNanos = 0;
	private boolean resizePendingSnap = false;
	private boolean isSnappingWindowSize = false;
	private int lastSnappedScale = 0;

	// JOML matrices for projection - accessible for shader uniforms
	private final org.joml.Matrix4f projectionMatrix = new org.joml.Matrix4f();
	private final float[] matrixBuffer = new float[16];

	// Static instance for singleton access
	private static Engine instance;

	// Frame timing
	private int targetFps;
	private long lastFrameTime;
	private boolean paused = false;

	private DebugRenderer getDebugRenderer() {
		if (debugRenderer == null) {
			debugRenderer = DebugRenderer.getInstance();
		}
		return debugRenderer;
	}

	public Engine() {
		this.windowWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH);
		this.windowHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT);
		this.targetFps = configService.getInt(SonicConfiguration.FPS);

		// Debug overlay uses Java2D (GlyphAtlas) which is unavailable in native images
		if (isNativeImage()) {
			debugViewEnabled = false;
		}

		// Set up game mode change listener to update projection width
		gameLoop.setGameModeChangeListener((oldMode, newMode) -> {
			// Keep projection at 320 for both modes
			projectionWidth = realWidth;
		});

		instance = this;
	}

	public void setInputHandler(InputHandler inputHandler) {
		this.inputHandler = inputHandler;
		gameLoop.setInputHandler(inputHandler);
	}

	public void run() {
		init();
		try { loop(); } finally { cleanup(); }
	}

	private static boolean isNativeImage() {
		return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
	}

	private void init() {
		// === PHASE 1: Window, GL context, input (always runs) ===

		// CRITICAL: Initialize AWT BEFORE GLFW on macOS.
		// Java2D uses Core Graphics which conflicts with GLFW's Cocoa event handling
		// if AWT initializes after GLFW. Pre-loading AWT fixes the freeze.
		// Skip in native-image builds where AWT is not available.
		if (debugViewEnabled && !isNativeImage()) {
			java.awt.Toolkit.getDefaultToolkit();
			// Create and dispose a small image to fully initialize Java2D
			var img = new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
			img.createGraphics().dispose();
		}

		// Setup an error callback
		GLFWErrorCallback.createPrint(System.err).set();

		// Initialize GLFW
		if (!glfwInit()) {
			throw new IllegalStateException("Unable to initialize GLFW");
		}

		// Configure GLFW
		glfwDefaultWindowHints();
		glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
		glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
		glfwWindowHint(GLFW_SCALE_TO_MONITOR, GLFW_TRUE); // DPI-aware window scaling

		// Request OpenGL 4.1 core profile for macOS compatibility
		glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4);
		glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);
		glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
		glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE); // Required for macOS

		// Create the window
		String version = SonicConfigurationService.ENGINE_VERSION;
		window = glfwCreateWindow(windowWidth, windowHeight,
				"OpenGGF " + version, NULL, NULL);
		if (window == NULL) {
			throw new RuntimeException("Failed to create the GLFW window");
		}

		// Setup key callback
		glfwSetKeyCallback(window, (windowHandle, key, scancode, action, mods) -> {
			if (inputHandler != null) {
				inputHandler.handleKeyEvent(key, action);
			}
		});

		// Setup window resize callback
		glfwSetFramebufferSizeCallback(window, (windowHandle, width, height) -> {
			this.windowWidth = width;
			this.windowHeight = height;
			// Only reshape if GL context is initialized (avoids crash during window setup)
			if (graphicsManager.isGlInitialized()) {
				reshape(width, height);
			}
			// Schedule a window snap once the user finishes resizing
			if (!isSnappingWindowSize) {
				resizePendingSnap = true;
				lastResizeTimeNanos = System.nanoTime();
			}
		});

		// Setup window focus callback
		glfwSetWindowFocusCallback(window, (windowHandle, focused) -> {
			if (focused) {
				gameLoop.resume();
			} else {
				gameLoop.pause();
			}
		});

		// Setup window iconify callback
		glfwSetWindowIconifyCallback(window, (windowHandle, iconified) -> {
			if (iconified) {
				paused = true;
				gameLoop.pause();
			} else {
				paused = false;
				gameLoop.resume();
			}
		});

		// Get the thread stack and push a new frame
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);

			// Get the window size passed to glfwCreateWindow
			glfwGetWindowSize(window, pWidth, pHeight);

			// Get the resolution of the primary monitor
			GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());

			// Center the window
			glfwSetWindowPos(
					window,
					(vidmode.width() - pWidth.get(0)) / 2,
					(vidmode.height() - pHeight.get(0)) / 2
			);
		}

		// Make the OpenGL context current
		glfwMakeContextCurrent(window);

		// Enable v-sync
		glfwSwapInterval(1);

		// Make the window visible
		glfwShowWindow(window);

		// This line is critical for LWJGL's interoperation with GLFW's
		// OpenGL context, or any context that is managed externally.
		GL.createCapabilities();

		try {
			graphicsManager.init(RESOURCES_SHADERS_PIXEL_SHADER_GLSL);
			graphicsManager.setEngine(this);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// Create input handler and set it
		inputHandler = new InputHandler();
		setInputHandler(inputHandler);

		// Set window handle for clipboard operations (GLFW-based, no AWT dependency)
		GameServices.debugOverlay().setWindowHandle(window);

		// Initial reshape and snap to integer scale (handles DPI-scaled framebuffer)
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);
			glfwGetFramebufferSize(window, pWidth, pHeight);
			this.windowWidth = pWidth.get(0);
			this.windowHeight = pHeight.get(0);
			reshape(windowWidth, windowHeight);
		}
		snapWindowToIntegerScale();

		// === Check master title screen before Phase 2 ===
		boolean masterTitleOnStartup = configService.getBoolean(SonicConfiguration.MASTER_TITLE_SCREEN_ON_STARTUP);
		if (masterTitleOnStartup) {
			masterTitleScreen = new MasterTitleScreen();
			masterTitleScreen.initialize();
			gameLoop.setGameMode(GameMode.MASTER_TITLE_SCREEN);
			// Skip Phase 2 entirely - will be called on game selection
		} else {
			// === PHASE 2: ROM loading, sprites, audio, level ===
			initializeGame();
		}

		// Eagerly initialize debug renderer BEFORE the main loop starts.
		// This is critical on macOS: the GlyphAtlas uses Java2D which conflicts
		// with GLFW's event loop if initialized lazily during glfwPollEvents().
		// Skip in native-image builds where AWT/Java2D is not available.
		if (debugViewEnabled && !isNativeImage()) {
			getDebugRenderer().updateViewport(viewportWidth, viewportHeight);
			getDebugRenderer().eagerInit();
			// Force GL sync and unbind all state
			glFinish();
			glBindTexture(GL_TEXTURE_2D, 0);
			glBindVertexArray(0);
			glBindBuffer(GL_ARRAY_BUFFER, 0);
			glUseProgram(0);
		}

		lastFrameTime = System.nanoTime();
	}

	/**
	 * Phase 2 initialization: loads ROM, creates sprites, initializes audio, loads level.
	 * Called either directly from init() (no master title screen) or from
	 * exitMasterTitleScreen() after game selection.
	 */
	public void initializeGame() {
		if (configService.getBoolean(SonicConfiguration.AUDIO_ENABLED)) {
			AudioManager.getInstance().setBackend(new LWJGLAudioBackend());
		}

		// Initialize cross-game feature donation if enabled
		if (configService.getBoolean(SonicConfiguration.CROSS_GAME_FEATURES_ENABLED)) {
			try {
				String donorGame = configService.getString(SonicConfiguration.CROSS_GAME_SOURCE);
				CrossGameFeatureProvider.getInstance().initialize(donorGame);
			} catch (IOException e) {
				LOGGER.severe("Cross-game features enabled but initialization failed. "
					+ "Check that the " + configService.getString(SonicConfiguration.CROSS_GAME_SOURCE)
					+ " ROM is configured and accessible. Error: " + e.getMessage());
			}
		}

		String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
		AbstractPlayableSprite mainSprite;
		if ("tails".equalsIgnoreCase(mainCode)) {
			mainSprite = new Tails(mainCode, (short) 100, (short) 624);
		} else {
			mainSprite = new Sonic(mainCode, (short) 100, (short) 624);
		}
		spriteManager.addSprite(mainSprite);

		// Create CPU-controlled sidekicks if configured (empty string = no sidekick).
		// Supports comma-separated list for multiple sidekicks chained leader-to-follower.
		List<String> sidekickNames = parseSidekickConfig(
			configService.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE));
		boolean sidekickAllowed = GameModuleRegistry.getCurrent().supportsSidekick()
				|| CrossGameFeatureProvider.isActive();
		if (sidekickAllowed) {
			AbstractPlayableSprite previousLeader = mainSprite;
			int cameraLeftBound = 0; // camera not yet positioned, use 0 as minimum
			for (int i = 0; i < sidekickNames.size(); i++) {
				String charName = sidekickNames.get(i);
				String code = charName + "_p" + (i + 2);
				int spawnX = Math.max(cameraLeftBound, mainSprite.getX() - 0x20 * (i + 1));
				boolean offScreen = (mainSprite.getX() - 0x20 * (i + 1)) < cameraLeftBound;

				AbstractPlayableSprite sidekick;
				if ("tails".equalsIgnoreCase(charName)) {
					sidekick = new Tails(code, (short) spawnX, (short) (mainSprite.getY() + 4));
				} else {
					sidekick = new Sonic(code, (short) spawnX, (short) (mainSprite.getY() + 4));
				}
				sidekick.setCpuControlled(true);
				SidekickCpuController controller = new SidekickCpuController(sidekick, previousLeader);
				controller.setSidekickCount(sidekickNames.size());
				if (offScreen) {
					controller.setInitialState(SidekickCpuController.State.SPAWNING);
				}
				sidekick.setCpuController(controller);

				// Select respawn strategy based on character type
				if ("knuckles".equalsIgnoreCase(charName)) {
					controller.setRespawnStrategy(new KnucklesRespawnStrategy(controller));
				} else if (!"tails".equalsIgnoreCase(charName)) {
					controller.setRespawnStrategy(new SonicRespawnStrategy(controller));
				}
				// Tails is the default — already set in constructor

				spriteManager.addSprite(sidekick, charName);
				previousLeader = sidekick;
			}
		}

		camera.setFocusedSprite(mainSprite);
		camera.updatePosition(true);

		// Check startup mode: title screen takes priority when enabled
		boolean titleScreenOnStartup = configService.getBoolean(SonicConfiguration.TITLE_SCREEN_ON_STARTUP);
		boolean levelSelectOnStartup = configService.getBoolean(SonicConfiguration.LEVEL_SELECT_ON_STARTUP);
		if (titleScreenOnStartup) {
			// Start in title screen mode
			gameLoop.initializeTitleScreenMode();
		} else if (levelSelectOnStartup) {
			// Start in level select mode - no level loaded initially
			gameLoop.initializeLevelSelectMode();
		} else {
			// Load zone 0 act 0 as the default starting level
			try {
				levelManager.loadZoneAndAct(0, 0);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	/**
	 * Gets the master title screen instance (for GameLoop to call update/draw).
	 */
	public MasterTitleScreen getMasterTitleScreen() {
		return masterTitleScreen;
	}

	/**
	 * Called by GameLoop when the user selects a game from the master title screen.
	 * Performs the Phase 2 init for the selected game.
	 */
	public void exitMasterTitleScreen(String gameId) {
		// Set the DEFAULT_ROM config to the selected game
		configService.setConfigValue(SonicConfiguration.DEFAULT_ROM, gameId);

		// Clean up master title screen GL resources
		if (masterTitleScreen != null) {
			masterTitleScreen.cleanup();
			masterTitleScreen = null;
		}

		// Phase 2: load ROM, sprites, audio, level
		initializeGame();
	}

	private void reshape(int width, int height) {
		int nativeW = (int) realWidth;   // 320
		int nativeH = (int) realHeight;  // 224

		// Find the largest integer scale that fits both dimensions
		int scale = Math.max(1, Math.min(width / nativeW, height / nativeH));
		viewportWidth = scale * nativeW;
		viewportHeight = scale * nativeH;

		// Center the integer-scaled viewport within the framebuffer
		viewportX = (width - viewportWidth) / 2;
		viewportY = (height - viewportHeight) / 2;

		// Set the viewport to the integer-scaled area
		glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Cache viewport dimensions in GraphicsManager
		graphicsManager.setViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Setup orthographic projection using JOML - stored for shader access
		projectionMatrix.identity().ortho2D(0, (float) projectionWidth, 0, (float) realHeight);
		projectionMatrix.get(matrixBuffer);
	}

	/**
	 * Snaps the window size to the nearest integer multiple of the native resolution
	 * so every game pixel maps to exactly NxN screen pixels with no fractional scaling.
	 * Rounds UP when the window grew (e.g. DPI increase) and DOWN when it shrank,
	 * preventing progressive shrinking across monitor moves.
	 */
	private void snapWindowToIntegerScale() {
		int nativeW = (int) realWidth;
		int nativeH = (int) realHeight;

		// Get the current monitor's usable resolution as an upper bound
		long monitor = glfwGetWindowMonitor(window);
		if (monitor == NULL) {
			monitor = glfwGetPrimaryMonitor();
		}
		GLFWVidMode vidmode = glfwGetVideoMode(monitor);
		int maxScale = Math.min(vidmode.width() / nativeW, vidmode.height() / nativeH);

		double currentScale = Math.min((double) windowWidth / nativeW, (double) windowHeight / nativeH);

		int scale;
		if (lastSnappedScale > 0 && currentScale > lastSnappedScale) {
			// Window grew (DPI increase or manual resize up) - round up
			scale = (int) Math.ceil(currentScale);
		} else {
			// Window shrank, unchanged, or initial load - round down
			scale = (int) currentScale;
		}
		scale = Math.max(1, Math.min(scale, maxScale));

		lastSnappedScale = scale;
		int targetW = scale * nativeW;
		int targetH = scale * nativeH;
		if (targetW != windowWidth || targetH != windowHeight) {
			isSnappingWindowSize = true;
			glfwSetWindowSize(window, targetW, targetH);
			isSnappingWindowSize = false;
		}
	}

	private void loop() {
		long frameTimeNanos = 1_000_000_000L / targetFps;
		long accumulator = 0;
		long previousTime = System.nanoTime();

		while (!glfwWindowShouldClose(window)) {
			long currentTime = System.nanoTime();
			long deltaTime = currentTime - previousTime;
			previousTime = currentTime;

			if (!paused) {
				accumulator += deltaTime;

				// Process exactly one frame per target interval
				if (accumulator >= frameTimeNanos) {
					display();
					glfwSwapBuffers(window);
					// Preserve remainder to prevent timing drift
					accumulator -= frameTimeNanos;

					// Clamp accumulator to prevent spiral of death if frames take too long
					if (accumulator > frameTimeNanos) {
						accumulator = frameTimeNanos;
					}
				}
			}

			glfwPollEvents();

			// Snap window to nearest integer scale once resize drag ends (~200ms debounce)
			if (resizePendingSnap && System.nanoTime() - lastResizeTimeNanos > 200_000_000L) {
				resizePendingSnap = false;
				snapWindowToIntegerScale();
			}

			// Note: inputHandler.update() is called at the end of GameLoop.step()
			// to properly handle isKeyPressed() edge detection. Do NOT call update()
			// here or isKeyPressed() will always return false.

			// Hybrid sleep: sleep most of the wait time, then spin-wait for precision
			long remainingTime = frameTimeNanos - accumulator;
			if (remainingTime > 2_000_000) {
				// Sleep for most of the remaining time, leaving ~1ms for spin-wait
				try {
					Thread.sleep((remainingTime - 1_000_000) / 1_000_000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

			// Spin-wait the final portion for sub-millisecond precision
			// Calculate target time for next frame check
			long targetTime = previousTime + (frameTimeNanos - accumulator);
			while (System.nanoTime() < targetTime) {
				Thread.onSpinWait();
			}
		}
	}

	/**
	 * Called each frame to render the game.
	 */
	private void display() {
		profiler.beginFrame();

		// Clear the entire window to black first (for letterbox/pillarbox bars)
		glViewport(0, 0, windowWidth, windowHeight);
		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		// Set the viewport to the aspect-ratio-correct area for game rendering
		glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Update projection matrix for current mode - stored for shader access
		projectionMatrix.identity().ortho2D(0, (float) projectionWidth, 0, (float) realHeight);
		projectionMatrix.get(matrixBuffer);

		// Set clear color based on game mode and clear the game viewport
		if (getCurrentGameMode() == GameMode.SPECIAL_STAGE) {
			SpecialStageProvider ssProviderForClear = gameLoop.getActiveSpecialStageProvider();
			if (ssProviderForClear != null) {
				ssProviderForClear.setClearColor();
			} else {
				glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
			}
		} else if (getCurrentGameMode() == GameMode.SPECIAL_STAGE_RESULTS) {
			// White background — Pal_Results sets backdrop color to white ($0EEE)
			glClearColor(1.0f, 1.0f, 1.0f, 1.0f);
		} else if (getCurrentGameMode() == GameMode.TITLE_SCREEN) {
			// Title screen backdrop is palette 2, color 0 (per VDP register $8720)
			TitleScreenProvider titleScreen = gameLoop.getTitleScreenProvider();
			if (titleScreen != null) {
				titleScreen.setClearColor();
			}
		} else if (getCurrentGameMode() == GameMode.LEVEL_SELECT) {
			// Level select backdrop is palette 0, color 0 (per VDP register $8700)
			LevelSelectProvider levelSelect = gameLoop.getLevelSelectProvider();
			if (levelSelect != null) {
				levelSelect.setClearColor();
			}
		} else if (getCurrentGameMode() == GameMode.CREDITS_TEXT
				|| getCurrentGameMode() == GameMode.ENDING_CUTSCENE) {
			// Ending: delegate to EndingProvider for phase-dependent background color
			// (sky blue during cutscene sky phases, black during photos/credits/logo)
			EndingProvider ending = gameLoop.getEndingProvider();
			if (ending != null) {
				ending.setClearColor();
			} else {
				glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
			}
		} else if (getCurrentGameMode() == GameMode.TRY_AGAIN_END) {
			// TRY AGAIN / END screen: black background
			glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		} else if (getCurrentGameMode() == GameMode.MASTER_TITLE_SCREEN) {
			glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		} else if (getCurrentGameMode() == GameMode.TITLE_CARD) {
			levelManager.setClearColor();
		} else {
			levelManager.setClearColor();
		}
		glScissor(viewportX, viewportY, viewportWidth, viewportHeight);
		glEnable(GL_SCISSOR_TEST);
		glClear(GL_COLOR_BUFFER_BIT);
		glDisable(GL_SCISSOR_TEST);

		glColorMask(true, true, true, true);

		// Update fade via unified UI render pipeline — process fade state first so
		// that callbacks (e.g. credits transition flags) are available to step()
		// in the same frame, preventing 1-frame gaps where the overlay would drop.
		var uiPipeline = graphicsManager.getUiRenderPipeline();
		if (uiPipeline != null) {
			uiPipeline.updateFade();
		}

		profiler.beginSection("update");
		update();
		profiler.endSection("update");

		profiler.beginSection("render");
		draw();
		graphicsManager.flush();
		profiler.endSection("render");

		// Render screen fade overlay via unified UI render pipeline
		if (uiPipeline != null) {
			uiPipeline.renderFadePass();
		}
		if (getCurrentGameMode() == GameMode.CREDITS_DEMO) {
			EndingProvider provider = gameLoop.getEndingProvider();
			if (provider != null && provider.shouldRenderDemoSpritesOverFade()) {
				levelManager.renderSpriteObjectPass(spriteManager, true);
				graphicsManager.flush();
			}
		}

		boolean playbackHud = PlaybackDebugManager.getInstance().isHudVisible();
		boolean needsOverlay = (getCurrentGameMode() == GameMode.SPECIAL_STAGE) ||
				((debugViewEnabled || playbackHud) && getCurrentGameMode() != GameMode.SPECIAL_STAGE && !isNativeImage());

		if (needsOverlay) {
			prepareOverlayState();
		}

		profiler.beginSection("debug");
		if (getCurrentGameMode() == GameMode.SPECIAL_STAGE) {
			SpecialStageProvider ssProvider = gameLoop.getActiveSpecialStageProvider();
			if (ssProvider.isAlignmentTestMode()) {
				ssProvider.renderAlignmentOverlay(windowWidth, windowHeight);
			} else {
				ssProvider.renderLagCompensationOverlay(windowWidth, windowHeight);
			}
		} else if ((debugViewEnabled || playbackHud) && !isNativeImage()) {
			getDebugRenderer().updateViewport(viewportWidth, viewportHeight);
			getDebugRenderer().renderDebugInfo();

			// Clean up GL state after debug rendering to prevent macOS event loop issues
			glBindVertexArray(0);
			glBindBuffer(GL_ARRAY_BUFFER, 0);
			glUseProgram(0);
		}
		profiler.endSection("debug");

		// F12 screenshot capture (after all rendering is complete)
		if (inputHandler != null && inputHandler.isKeyPressed(GLFW_KEY_F12)) {
			try {
				String timestamp = java.time.LocalDateTime.now()
						.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
				java.nio.file.Path path = java.nio.file.Path.of("screenshot_" + timestamp + ".png");
				ScreenshotCapture.captureAndSavePNG(viewportWidth, viewportHeight, path);
				LOGGER.info("Screenshot saved: " + path);
			} catch (Exception e) {
				LOGGER.warning("Screenshot failed: " + e.getMessage());
			}
		}

		profiler.endFrame();
		overlayStateReady = false;
	}

	/**
	 * Updates the game state by one frame.
	 */
	public void update() {
		gameLoop.step();
	}

	/**
	 * Gets the current game mode from the game loop.
	 */
	public GameMode getCurrentGameMode() {
		return gameLoop.getCurrentGameMode();
	}

	/**
	 * Gets the game loop instance for testing purposes.
	 */
	public GameLoop getGameLoop() {
		return gameLoop;
	}

	public void draw() {
		if (getCurrentGameMode() == GameMode.MASTER_TITLE_SCREEN) {
			camera.setX((short) 0);
			camera.setY((short) 0);
			if (masterTitleScreen != null) {
				masterTitleScreen.draw();
			}
			return;
		}
		if (getCurrentGameMode() == GameMode.SPECIAL_STAGE) {
			SpecialStageProvider ssProvider = gameLoop.getActiveSpecialStageProvider();
			if (ssProvider.isSpriteDebugMode()) {
				SpecialStageDebugProvider debugProvider = ssProvider.getDebugProvider();
				if (debugProvider != null) {
					debugProvider.draw();
				} else {
					ssProvider.draw();
				}
			} else {
				ssProvider.draw();
			}
		} else if (getCurrentGameMode() == GameMode.SPECIAL_STAGE_RESULTS) {
			var resultsScreen = gameLoop.getResultsScreen();
			if (resultsScreen != null) {
				camera.setX((short) 0);
				camera.setY((short) 0);

				graphicsManager.beginPatternBatch();

				resultsCommands.clear();
				resultsScreen.appendRenderCommands(resultsCommands);

				// Flush pattern batch (PatternSpriteRenderer renders through the
				// instanced batch system, not through the GLCommand list)
				graphicsManager.flushPatternBatch();

				// Also register any GLCommand-based rendering (S2 results screen)
				if (!resultsCommands.isEmpty()) {
					graphicsManager.registerCommand(new GLCommandGroup(
							GL_LINES, resultsCommands));
				}

				// Execute all queued commands in screen space (camera at 0,0)
				graphicsManager.flushScreenSpace();
			}
		} else if (getCurrentGameMode() == GameMode.TITLE_SCREEN) {
			// Render title screen
			camera.setX((short) 0);
			camera.setY((short) 0);
			TitleScreenProvider titleScreen = gameLoop.getTitleScreenProvider();
			if (titleScreen != null) {
				titleScreen.draw();
			}
		} else if (getCurrentGameMode() == GameMode.LEVEL_SELECT) {
			// Render level select screen
			camera.setX((short) 0);
			camera.setY((short) 0);
			LevelSelectProvider levelSelect = gameLoop.getLevelSelectProvider();
			if (levelSelect != null) {
				levelSelect.draw();
			}
		} else if (getCurrentGameMode() == GameMode.ENDING_CUTSCENE) {
			// Ending cutscene: render DEZ background during sky phases, then cutscene sprites
			camera.setX((short) 0);
			camera.setY((short) 0);
			EndingProvider provider = gameLoop.getEndingProvider();
			if (provider != null) {
				if (provider.needsLevelBackground()) {
					levelManager.renderEndingBackground(
							provider.getBackgroundVscroll(),
							provider.getBackdropColorOverride());
					// Flush deferred BG shader commands BEFORE cutscene sprites.
					// Without this, the parallax compositing pass executes during
					// the top-level flush() AFTER provider.draw(), rendering the
					// DEZ star field ON TOP of the palette-faded cutscene.
					graphicsManager.flush();
				}
				provider.draw();
			}
		} else if (getCurrentGameMode() == GameMode.CREDITS_TEXT) {
			// Credits text: screen-space rendering (no background)
			camera.setX((short) 0);
			camera.setY((short) 0);
			EndingProvider provider = gameLoop.getEndingProvider();
			if (provider != null) {
				provider.draw();
			}
		} else if (getCurrentGameMode() == GameMode.CREDITS_DEMO) {
			// Sonic 1 credits demo fade-in keeps sprites/objects visible while the
			// tile planes are still under the black fade. Render only the tile side
			// here and replay the sprite/object pass after the fade overlay.
			EndingProvider provider = gameLoop.getEndingProvider();
			boolean includeSprites = provider == null || !provider.shouldRenderDemoSpritesOverFade();
			levelManager.drawWithSpritePriority(spriteManager, includeSprites);
		} else if (getCurrentGameMode() == GameMode.TRY_AGAIN_END) {
			// TRY AGAIN / END / post-credits screen: screen-space rendering
			camera.setX((short) 0);
			camera.setY((short) 0);
			EndingProvider provider = gameLoop.getEndingProvider();
			if (provider != null) {
				provider.draw();
			}
		} else if (getCurrentGameMode() == GameMode.TITLE_CARD) {
			levelManager.drawWithSpritePriority(spriteManager);

			graphicsManager.flush();
			graphicsManager.resetForFixedFunction();

			TitleCardProvider titleCardProvider = gameLoop.getTitleCardProvider();
			if (titleCardProvider != null) {
				titleCardProvider.draw();
				graphicsManager.flushScreenSpace();
			}
		} else if (!debugViewEnabled) {
			levelManager.drawWithSpritePriority(spriteManager);

			TitleCardProvider levelTitleCardProvider = gameLoop.getTitleCardProvider();
			if (levelTitleCardProvider != null && levelTitleCardProvider.isOverlayActive()) {
				graphicsManager.flush();
				graphicsManager.resetForFixedFunction();
				levelTitleCardProvider.draw();
				graphicsManager.flushScreenSpace();
			}
		} else {
			switch (debugState) {
				case PATTERNS_VIEW -> levelManager.drawAllPatterns();
				case BLOCKS_VIEW -> levelManager.draw();
				case null, default -> levelManager.drawWithSpritePriority(spriteManager);
			}

			TitleCardProvider debugTitleCardProvider = gameLoop.getTitleCardProvider();
			if (debugTitleCardProvider != null && debugTitleCardProvider.isOverlayActive()) {
				graphicsManager.flush();
				graphicsManager.resetForFixedFunction();
				debugTitleCardProvider.draw();
				graphicsManager.flushScreenSpace();
			}
		}
	}

	private void prepareOverlayState() {
		if (overlayStateReady) {
			return;
		}
		glActiveTexture(GL_TEXTURE0);
		glUseProgram(0);
		glDisable(GL_DEPTH_TEST);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE0);

		glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Update projection matrix for overlay - stored for shader access
		projectionMatrix.identity().ortho2D(0, (float) projectionWidth, 0, (float) realHeight);
		projectionMatrix.get(matrixBuffer);

		glEnable(GL_BLEND);
		glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

		overlayStateReady = true;
	}

	private void cleanup() {
		AudioManager.getInstance().clearDonorAudio();
		CrossGameFeatureProvider.resetInstance();
		RenderContext.reset();
		if (masterTitleScreen != null) {
			masterTitleScreen.cleanup();
			masterTitleScreen = null;
		}
		graphicsManager.cleanup();
		AudioManager.getInstance().destroy();

		// Free the window callbacks and destroy the window
		glfwFreeCallbacks(window);
		glfwDestroyWindow(window);

		// Terminate GLFW and free the error callback
		glfwTerminate();
		GLFWErrorCallback callback = glfwSetErrorCallback(null);
		if (callback != null) {
			callback.free();
		}
	}

	public static void nextDebugState() {
		debugState = debugState.next();
		debugOption = DebugOption.A;
	}

	public static void nextDebugOption() {
		debugOption = debugOption.next();
	}

	public static void main(String[] args) {
		if (isNativeImage() && System.getProperty("org.lwjgl.librarypath") == null) {
			// In a native image, LWJGL can't extract native libs from JARs.
			// Find the bundled .dylib/.so/.dll files next to the executable.
			String libPath = findNativeLibsDir();
			if (libPath != null) {
				System.setProperty("org.lwjgl.librarypath", libPath);
			}
		}
		new Engine().run();
	}

	private static String findNativeLibsDir() {
		// Strategy 1: SONIC_NATIVE_LIBS_DIR env var set by .app launcher script.
		// This is NOT stripped by macOS SIP (unlike DYLD_LIBRARY_PATH).
		String envDir = System.getenv("SONIC_NATIVE_LIBS_DIR");
		if (envDir != null && !envDir.isEmpty()) {
			java.io.File dir = new java.io.File(envDir);
			if (hasNativeLibs(dir)) {
				return dir.getAbsolutePath();
			}
		}

		// Strategy 2: directory containing the executable (works for bare binary
		// and .app bundles where dylibs are co-located with the binary)
		try {
			String cmd = ProcessHandle.current().info().command().orElse("");
			if (!cmd.isEmpty()) {
				java.io.File execDir = new java.io.File(cmd).getParentFile();
				if (execDir != null && hasNativeLibs(execDir)) {
					return execDir.getAbsolutePath();
				}
			}
		} catch (Exception ignored) {
		}

		// Strategy 3: working directory (if user runs from target/)
		java.io.File cwd = new java.io.File(System.getProperty("user.dir"));
		if (hasNativeLibs(cwd)) {
			return cwd.getAbsolutePath();
		}

		// Strategy 4: target/native-libs/ relative to working directory (dev builds)
		java.io.File targetNativeLibs = new java.io.File(cwd, "target/native-libs");
		if (hasNativeLibs(targetNativeLibs)) {
			return targetNativeLibs.getAbsolutePath();
		}

		return null;
	}

	private static boolean hasNativeLibs(java.io.File dir) {
		if (!dir.isDirectory()) return false;
		String[] files = dir.list();
		if (files == null) return false;
		for (String f : files) {
			if (f.startsWith("liblwjgl.")) return true;   // .dylib or .so
			if (f.equals("lwjgl.dll")) return true;
		}
		return false;
	}

	// For testing - get window handle
	long getWindowHandle() {
		return window;
	}

	/**
	 * Gets the singleton instance of the Engine.
	 * @return the Engine instance, or null if not yet created
	 */
	public static synchronized Engine getInstance() {
		return instance;
	}

	/**
	 * Gets the current projection matrix for use in shaders.
	 * @return the projection matrix
	 */
	public org.joml.Matrix4f getProjectionMatrix() {
		return projectionMatrix;
	}

	/**
	 * Gets the projection matrix data as a float array for shader uniforms.
	 * @return the projection matrix as a 16-element float array
	 */
	public float[] getProjectionMatrixBuffer() {
		return fboProjectionActive ? fboMatrixBuffer : matrixBuffer;
	}

	// FBO projection support - used when rendering to off-screen framebuffers
	private boolean fboProjectionActive = false;
	private final org.joml.Matrix4f fboProjectionMatrix = new org.joml.Matrix4f();
	private final float[] fboMatrixBuffer = new float[16];
	private int fboWidth = 256;
	private int fboHeight = 256;

	/**
	 * Sets up FBO projection mode for rendering to an off-screen framebuffer.
	 * While active, getProjectionMatrixBuffer() returns the FBO projection.
	 *
	 * @param width  The FBO width in pixels
	 * @param height The FBO height in pixels
	 */
	public void beginFBOProjection(int width, int height) {
		this.fboWidth = width;
		this.fboHeight = height;
		fboProjectionMatrix.identity().ortho2D(0, width, 0, height);
		fboProjectionMatrix.get(fboMatrixBuffer);
		fboProjectionActive = true;
	}

	/**
	 * Restores normal screen projection after FBO rendering.
	 */
	public void endFBOProjection() {
		fboProjectionActive = false;
	}

	/**
	 * Returns the current display height for coordinate calculations.
	 * When FBO projection is active, returns the FBO height.
	 * Otherwise returns the normal screen height.
	 */
	public int getCurrentDisplayHeight() {
		return fboProjectionActive ? fboHeight : (int) realHeight;
	}

	/**
	 * Returns whether FBO projection mode is currently active.
	 */
	public boolean isFBOProjectionActive() {
		return fboProjectionActive;
	}

	/**
	 * Parses a comma-separated sidekick configuration string into a list of
	 * character names. Returns an empty list for null, empty, or blank input.
	 */
	public static List<String> parseSidekickConfig(String value) {
		if (value == null || value.isBlank()) {
			return List.of();
		}
		return Arrays.stream(value.split(","))
			.map(String::trim)
			.filter(s -> !s.isEmpty())
			.toList();
	}
}
