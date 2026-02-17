package uk.co.jamesj999.sonic;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import uk.co.jamesj999.sonic.Control.InputHandler;
import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.audio.LWJGLAudioBackend;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.debug.DebugOption;
import uk.co.jamesj999.sonic.debug.DebugRenderer;
import uk.co.jamesj999.sonic.debug.PerformanceProfiler;
import uk.co.jamesj999.sonic.debug.DebugState;
import uk.co.jamesj999.sonic.game.SpecialStageDebugProvider;
import uk.co.jamesj999.sonic.game.SpecialStageProvider;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.graphics.ScreenshotCapture;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.Sonic;
import uk.co.jamesj999.sonic.sprites.playable.Tails;
import uk.co.jamesj999.sonic.sprites.playable.TailsCpuController;
import uk.co.jamesj999.sonic.game.GameMode;
import uk.co.jamesj999.sonic.game.LevelSelectProvider;
import uk.co.jamesj999.sonic.game.TitleCardProvider;
import uk.co.jamesj999.sonic.game.TitleScreenProvider;

import java.io.IOException;
import java.nio.IntBuffer;
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
	private final java.util.List<uk.co.jamesj999.sonic.graphics.GLCommand> resultsCommands = new java.util.ArrayList<>(64);

	// GLFW window handle
	private long window;

	// Window dimensions
	private int windowWidth;
	private int windowHeight;

	private boolean overlayStateReady = false;

	// Input handler for keyboard input
	private InputHandler inputHandler;

	// Viewport parameters for aspect-ratio-correct rendering
	private int viewportX = 0;
	private int viewportY = 0;
	private int viewportWidth = 0;
	private int viewportHeight = 0;

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
				"Java Sonic Engine by Jamesj999 and Raiscan " + version, NULL, NULL);
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

		if (configService.getBoolean(SonicConfiguration.AUDIO_ENABLED)) {
			AudioManager.getInstance().setBackend(new LWJGLAudioBackend());
		}

		String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
		AbstractPlayableSprite mainSprite;
		if ("tails".equalsIgnoreCase(mainCode)) {
			mainSprite = new Tails(mainCode, (short) 100, (short) 624);
		} else {
			mainSprite = new Sonic(mainCode, (short) 100, (short) 624);
		}
		spriteManager.addSprite(mainSprite);

		// Create CPU-controlled sidekick if configured (empty string = no sidekick).
		// ROM: Both Sonic and Tails share the same start position from the zone start location table.
		// Sidekick must start at the same X as main so the AI doesn't immediately chase (threshold is 16px).
		String sidekickCode = configService.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
		if (!sidekickCode.isEmpty()) {
			AbstractPlayableSprite sidekick;
			if ("tails".equalsIgnoreCase(sidekickCode)) {
				sidekick = new Tails(sidekickCode, mainSprite.getX(), mainSprite.getY());
			} else {
				sidekick = new Sonic(sidekickCode, mainSprite.getX(), mainSprite.getY());
			}
			sidekick.setCpuControlled(true);
			TailsCpuController cpuController = new TailsCpuController(sidekick);
			sidekick.setCpuController(cpuController);
			spriteManager.addSprite(sidekick);
		}

		camera.setFocusedSprite(mainSprite);
		camera.updatePosition(true);

		// Create input handler and set it (needed before level select initialization)
		inputHandler = new InputHandler();
		setInputHandler(inputHandler);

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
			// Load Emerald Hill Zone Act 1 as the default starting level
			try {
				levelManager.loadZoneAndAct(0, 0);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		// Initial reshape
		try (MemoryStack stack = stackPush()) {
			IntBuffer pWidth = stack.mallocInt(1);
			IntBuffer pHeight = stack.mallocInt(1);
			glfwGetFramebufferSize(window, pWidth, pHeight);
			reshape(pWidth.get(0), pHeight.get(0));
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

	private void reshape(int width, int height) {
		// Calculate aspect-ratio-correct viewport
		double targetAspect = realWidth / realHeight;
		double windowAspect = (double) width / height;

		if (windowAspect > targetAspect) {
			// Window is wider than target - pillarbox
			viewportHeight = height;
			viewportWidth = (int) (height * targetAspect);
			viewportX = (width - viewportWidth) / 2;
			viewportY = 0;
		} else {
			// Window is taller than target - letterbox
			viewportWidth = width;
			viewportHeight = (int) (width / targetAspect);
			viewportX = 0;
			viewportY = (height - viewportHeight) / 2;
		}

		// Set the viewport to the aspect-ratio-correct area
		glViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Cache viewport dimensions in GraphicsManager
		graphicsManager.setViewport(viewportX, viewportY, viewportWidth, viewportHeight);

		// Setup orthographic projection using JOML - stored for shader access
		projectionMatrix.identity().ortho2D(0, (float) projectionWidth, 0, (float) realHeight);
		projectionMatrix.get(matrixBuffer);
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
			glClearColor(0.85f, 0.9f, 0.95f, 1.0f);
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

		profiler.beginSection("update");
		update();
		profiler.endSection("update");

		// Update fade via unified UI render pipeline
		var uiPipeline = graphicsManager.getUiRenderPipeline();
		if (uiPipeline != null) {
			uiPipeline.updateFade();
		}

		profiler.beginSection("render");
		draw();
		graphicsManager.flush();
		profiler.endSection("render");

		// Render screen fade overlay via unified UI render pipeline
		if (uiPipeline != null) {
			uiPipeline.renderFadePass();
		}

		boolean needsOverlay = (getCurrentGameMode() == GameMode.SPECIAL_STAGE) ||
				(debugViewEnabled && getCurrentGameMode() != GameMode.SPECIAL_STAGE);

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
		} else if (debugViewEnabled) {
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
				java.awt.image.BufferedImage screenshot = ScreenshotCapture.captureFramebuffer(
						viewportWidth, viewportHeight);
				String timestamp = java.time.LocalDateTime.now()
						.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
				java.nio.file.Path path = java.nio.file.Path.of("screenshot_" + timestamp + ".png");
				ScreenshotCapture.savePNG(screenshot, path);
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

				if (!resultsCommands.isEmpty()) {
					graphicsManager.registerCommand(new uk.co.jamesj999.sonic.graphics.GLCommandGroup(
							GL_LINES, resultsCommands));
				}
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
}
