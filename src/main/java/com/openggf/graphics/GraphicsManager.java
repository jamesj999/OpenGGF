package com.openggf.graphics;

import com.openggf.Engine;
import com.openggf.camera.Camera;
import com.openggf.graphics.pipeline.UiRenderPipeline;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.level.render.BackgroundRenderer;

import static com.openggf.level.LevelConstants.*;

import com.openggf.level.slotmachine.CNZSlotMachineRenderer;

import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GraphicsManager {
	private static final Logger LOGGER = Logger.getLogger(GraphicsManager.class.getName());

	private static GraphicsManager graphicsManager;
	List<GLCommandable> commands = new ArrayList<>();

	private final Map<String, Integer> paletteTextureMap = new HashMap<>(); // Map for palette textures
	private Integer combinedPaletteTextureId;
	private int currentPaletteTextureHeight = 0;
	private PatternAtlas patternAtlas;
	// Lazily allocated to avoid LWJGL native library loading in headless tests
	private ByteBuffer paletteUploadBuffer;
	private ByteBuffer underwaterPaletteUploadBuffer;

	private static final int ATLAS_WIDTH = 1024;
	private static final int ATLAS_HEIGHT = 1024;

	// Lazily fetched to avoid initialization chain issues in headless tests
	private Camera camera;
	private boolean glInitialized = false;
	private ShaderProgram shaderProgram;
	private ShaderProgram defaultShaderProgram;
	private WaterShaderProgram waterShaderProgram;
	private ShaderProgram currentShaderProgram;

	private ShaderProgram debugShaderProgram;
	private ShaderProgram fadeShaderProgram;
	private ShaderProgram shadowShaderProgram;
	private static final String DEBUG_SHADER_PATH = "shaders/shader_debug_color.glsl";
	private static final String PARALLAX_SHADER_PATH = "shaders/shader_parallax_bg.glsl";
	private static final String FADE_SHADER_PATH = "shaders/shader_fade.glsl";
	private static final String SHADOW_SHADER_PATH = "shaders/shader_shadow.glsl";
	private static final String WATER_SHADER_PATH = "shaders/shader_water.glsl";
	private static final String TILEMAP_SHADER_PATH = "shaders/shader_tilemap.glsl";
	private static final String BASIC_VERTEX_SHADER_PATH = "shaders/shader_basic.vert";
	private static final String DEBUG_VERTEX_SHADER_PATH = "shaders/shader_debug_color.vert";
	private static final String INSTANCED_VERTEX_SHADER_PATH = "shaders/shader_instanced.vert";
	private static final String CNZ_SLOTS_SHADER_PATH = "shaders/shader_cnz_slots.glsl";
	private static final String SPRITE_PRIORITY_SHADER_PATH = "shaders/shader_sprite_priority.glsl";

	// Sprite priority rendering for ROM-accurate sprite-to-tile layering
	private SpritePriorityShaderProgram spritePriorityShaderProgram;
	private TilePriorityFBO tilePriorityFBO;

	// Sprite priority shader mode flags
	private boolean useSpritePriorityShader = false;
	private boolean currentSpriteHighPriority = false;

	// Background renderer for per-scanline parallax scrolling
	private BackgroundRenderer backgroundRenderer;
	private TilemapGpuRenderer tilemapGpuRenderer;
	private InstancedPatternRenderer instancedPatternRenderer;

	// Fade manager for screen transitions
	private FadeManager fadeManager;

	// CNZ slot machine renderer
	private ShaderProgram cnzSlotsShaderProgram;
	private CNZSlotMachineRenderer cnzSlotMachineRenderer;

	// Unified UI render pipeline for overlay + fade ordering
	private UiRenderPipeline uiRenderPipeline;

	// Batched rendering support
	private boolean batchingEnabled = true;
	private BatchedPatternRenderer batchedRenderer;
	private boolean instancedBatchingEnabled = true;
	private boolean instancedBatchActive = false;

	// Vertical wrap Y adjustment for object rendering.
	// When enabled, adjusts world Y coordinates passed to renderPattern/renderPatternWithId
	// to account for vertical level wrapping (LZ3/SBZ2). Emulates VDP modular sprite Y
	// which naturally wraps coordinates, preventing objects from vanishing at wrap boundaries.
	private boolean verticalWrapAdjustEnabled = false;
	private int verticalWrapRange = 0;
	private int verticalWrapCameraY = 0;

	/**
	 * Reference to the Engine for accessing projection matrix.
	 */
	private Engine engine;

	/**
	 * Projection matrix buffer for shader-based rendering.
	 * Can be set directly by tests or other code that doesn't have an Engine instance.
	 */
	private float[] projectionMatrixBuffer;

	/**
	 * Headless mode flag. When true, GL operations are skipped.
	 * This enables testing game logic without requiring an OpenGL context.
	 */
	private boolean headlessMode = false;

	/**
	 * When true, the batch renderer will use the underwater palette texture
	 * instead of the normal palette texture. Used for background rendering
	 * when Sonic is underwater (original game behavior).
	 */
	private boolean useUnderwaterPaletteForBackground = false;

	// Cached viewport dimensions to avoid glGetIntegerv(GL_VIEWPORT) every batch.
	// Updated when Engine.reshape() is called.
	private int viewportX = 0;
	private int viewportY = 0;
	private int viewportWidth = 320;
	private int viewportHeight = 224;

	// Water-related state for sprite priority shader underwater palette support.
	// These values are set by LevelManager.updateWaterShaderState() each frame.
	private float waterlineScreenY = 0.0f;
	private float windowHeight = 224.0f;
	private float screenHeight = 224.0f;
	private boolean waterEnabled = false;

	public void registerCommand(GLCommandable command) {
		commands.add(command);
	}

	/**
	 * Initialize the GraphicsManager with shader loading.
	 */
	public void init(String pixelShaderPath) throws IOException {
		if (headlessMode) {
			return;
		}
		this.glInitialized = true;
		this.patternAtlas = new PatternAtlas(ATLAS_WIDTH, ATLAS_HEIGHT);
		this.patternAtlas.init();
		this.defaultShaderProgram = new ShaderProgram(BASIC_VERTEX_SHADER_PATH, pixelShaderPath); // Load default shader
		this.defaultShaderProgram.cacheUniformLocations();

		this.waterShaderProgram = new WaterShaderProgram(BASIC_VERTEX_SHADER_PATH, WATER_SHADER_PATH); // Load water shader
		this.waterShaderProgram.cacheUniformLocations();

		this.currentShaderProgram = this.defaultShaderProgram; // Start with default
		this.shaderProgram = this.currentShaderProgram; // Compatibility
		this.debugShaderProgram = new ShaderProgram(DEBUG_VERTEX_SHADER_PATH, DEBUG_SHADER_PATH);
		this.fadeShaderProgram = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER, FADE_SHADER_PATH);
		this.shadowShaderProgram = new ShaderProgram(BASIC_VERTEX_SHADER_PATH, SHADOW_SHADER_PATH);
		this.shadowShaderProgram.cacheUniformLocations();
		this.tilemapGpuRenderer = new TilemapGpuRenderer();
		this.tilemapGpuRenderer.init(TILEMAP_SHADER_PATH);
		this.instancedPatternRenderer = new InstancedPatternRenderer();
		this.instancedPatternRenderer.init(INSTANCED_VERTEX_SHADER_PATH, pixelShaderPath, WATER_SHADER_PATH);

		// Initialize fade manager with shader
		this.fadeManager = FadeManager.getInstance();
		this.fadeManager.setFadeShader(this.fadeShaderProgram);

		// CNZ slot machine renderer - shader is loaded lazily when needed
		this.cnzSlotMachineRenderer = new CNZSlotMachineRenderer();

		// Initialize unified UI render pipeline
		this.uiRenderPipeline = new UiRenderPipeline(this);
		this.uiRenderPipeline.setFadeManager(this.fadeManager);

		// Initialize sprite priority rendering system
		this.spritePriorityShaderProgram = new SpritePriorityShaderProgram(SPRITE_PRIORITY_SHADER_PATH);
		this.spritePriorityShaderProgram.cacheUniformLocations();
		this.tilePriorityFBO = new TilePriorityFBO();
		// FBO will be initialized when first needed with actual screen dimensions
	}

	/**
	 * Initialize the GraphicsManager in headless mode (no GL context).
	 * Use this for testing game logic without rendering.
	 */
	public void initHeadless() {
		this.headlessMode = true;
		this.glInitialized = false;
		if (this.patternAtlas == null) {
			this.patternAtlas = new PatternAtlas(ATLAS_WIDTH, ATLAS_HEIGHT);
		}
		this.tilemapGpuRenderer = null;
		this.instancedPatternRenderer = null;
	}

	/**
	 * Check if running in headless mode.
	 */
	public boolean isHeadlessMode() {
		return headlessMode;
	}

	/**
	 * Set headless mode. Should be called before init().
	 */
	public void setHeadlessMode(boolean headless) {
		this.headlessMode = headless;
	}

	/**
	 * Mark the GL context as initialized.
	 */
	public void setGlInitialized(boolean initialized) {
		this.glInitialized = initialized;
	}

	/**
	 * Lazily get the Camera instance.
	 * This avoids triggering Camera singleton initialization during GraphicsManager construction.
	 */
	private Camera getCamera() {
		if (camera == null) {
			camera = Camera.getInstance();
		}
		return camera;
	}

	/**
	 * Lazily allocate the palette upload buffer.
	 * This avoids triggering LWJGL native library loading during GraphicsManager construction.
	 */
	private ByteBuffer ensurePaletteUploadBuffer() {
		if (paletteUploadBuffer == null) {
			paletteUploadBuffer = MemoryUtil.memAlloc(COLORS_PER_PALETTE * 4);
		}
		return paletteUploadBuffer;
	}

	/**
	 * Lazily allocate the underwater palette upload buffer.
	 * This avoids triggering LWJGL native library loading during GraphicsManager construction.
	 */
	private ByteBuffer ensureUnderwaterPaletteUploadBuffer() {
		if (underwaterPaletteUploadBuffer == null) {
			underwaterPaletteUploadBuffer = MemoryUtil.memAlloc(64 * 4);
		}
		return underwaterPaletteUploadBuffer;
	}

	/**
	 * Flush all registered commands.
	 * Uses shake-adjusted camera positions so sprites shake in sync with FG tiles.
	 */
	public void flush() {
		Camera cam = getCamera();
		flushWithCamera(cam.getXWithShake(), cam.getYWithShake(), cam.getWidth(), cam.getHeight());
	}

	/**
	 * Flush all registered commands with a specific camera position.
	 * Use this for screen-space rendering by passing (0, 0) for camera position.
	 */
	public void flushWithCamera(short cameraX, short cameraY, short cameraWidth, short cameraHeight) {
		if (headlessMode || commands.isEmpty() || !glInitialized) {
			commands.clear();
			return;
		}

		// Reset pattern render state for new batch of commands
		PatternRenderCommand.resetFrameState();

		for (GLCommandable command : commands) {
			command.execute(cameraX, cameraY, cameraWidth, cameraHeight);
		}

		// Cleanup pattern render state after all commands
		PatternRenderCommand.cleanupFrameState();

		commands.clear();
	}

	/**
	 * Flush all registered commands in screen-space (camera at 0,0).
	 * Used for overlays like title cards and results screens.
	 */
	public void flushScreenSpace() {
		Camera cam = getCamera();
		flushWithCamera((short) 0, (short) 0, cam.getWidth(), cam.getHeight());
	}

	/**
	 * Reset OpenGL state for shader-based rendering.
	 * Call this between different rendering phases to ensure clean state.
	 * Note: Fixed-function calls (glDisable(GL_TEXTURE_2D), glMatrixMode, etc.)
	 * have been removed for OpenGL 4.1 core profile compatibility.
	 */
	public void resetForFixedFunction() {
		if (headlessMode || !glInitialized) {
			return;
		}
		// Ensure no shader is active
		glUseProgram(0);
		// Reset texture state
		glActiveTexture(GL_TEXTURE0);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE1);
		glBindTexture(GL_TEXTURE_2D, 0);
		glActiveTexture(GL_TEXTURE0);
	}

	/**
	 * Cache a pattern texture (contains color indices) in the GPU.
	 */
	public void cachePatternTexture(Pattern pattern, int patternId) {
		ensurePatternAtlas();
		if (headlessMode || !glInitialized) {
			patternAtlas.cachePatternHeadless(pattern, patternId);
			return;
		}
		patternAtlas.cachePattern(pattern, patternId);
	}

	public void updatePatternTexture(Pattern pattern, int patternId) {
		ensurePatternAtlas();
		if (headlessMode || !glInitialized) {
			patternAtlas.updatePatternHeadless(pattern, patternId);
			return;
		}
		patternAtlas.updatePattern(pattern, patternId);
	}

	/**
	 * Begin batching pattern atlas uploads. While active, individual
	 * {@code cachePatternTexture} calls write to a CPU-side buffer only.
	 * Call {@link #endPatternAtlasBatch()} to upload everything in one GL call.
	 */
	public void beginPatternAtlasBatch() {
		if (headlessMode || !glInitialized || patternAtlas == null) {
			return;
		}
		patternAtlas.beginBatch();
	}

	/**
	 * Flush the batched pattern atlas uploads to the GPU.
	 */
	public void endPatternAtlasBatch() {
		if (headlessMode || !glInitialized || patternAtlas == null) {
			return;
		}
		patternAtlas.endBatch();
	}

	/**
	 * Remove a pattern from the atlas cache.
	 * This causes the renderer to skip this pattern (getEntry returns null).
	 * Used by CNZ slot machine to clear the tilemap at VRAM 0x0550-0x057F
	 * so the shader overlay can render slot faces there.
	 *
	 * @param patternId The pattern ID to uncache
	 * @return true if the pattern was uncached, false if it wasn't in the cache
	 */
	public boolean uncachePattern(int patternId) {
		if (patternAtlas == null) {
			return false;
		}
		return patternAtlas.removeEntry(patternId);
	}

	/**
	 * Create an alias so that one pattern ID renders the same as another.
	 * This is useful for making multiple pattern IDs render as transparent
	 * by aliasing them to pattern 0 (which is typically all-transparent).
	 * No additional atlas slots are allocated.
	 *
	 * @param aliasId The pattern ID to create as an alias
	 * @param targetId The existing pattern ID to alias to
	 * @return true if the alias was created, false if target doesn't exist
	 */
	public boolean aliasPattern(int aliasId, int targetId) {
		if (patternAtlas == null) {
			return false;
		}
		return patternAtlas.aliasEntry(aliasId, targetId);
	}

	public void cachePaletteTexture(Palette palette, int paletteId) {
		if (headlessMode) {
			// In headless mode, just record that the palette was cached
			paletteTextureMap.put("palette_" + paletteId, -1);
			return;
		}
		int requiredHeight = RenderContext.getTotalPaletteLines();
		if (combinedPaletteTextureId == null) {
			combinedPaletteTextureId = glGenTextures();
			currentPaletteTextureHeight = requiredHeight;
			ByteBuffer emptyBuffer = MemoryUtil.memAlloc(COLORS_PER_PALETTE * 4 * requiredHeight);
			try {
				glBindTexture(GL_TEXTURE_2D, combinedPaletteTextureId);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 16, requiredHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, emptyBuffer);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
				glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
			} finally {
				MemoryUtil.memFree(emptyBuffer);
			}
		} else if (requiredHeight > currentPaletteTextureHeight) {
			// Texture needs to grow to accommodate new donor contexts
			currentPaletteTextureHeight = requiredHeight;
			ByteBuffer emptyBuffer = MemoryUtil.memAlloc(COLORS_PER_PALETTE * 4 * requiredHeight);
			try {
				glBindTexture(GL_TEXTURE_2D, combinedPaletteTextureId);
				glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 16, requiredHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, emptyBuffer);
			} finally {
				MemoryUtil.memFree(emptyBuffer);
			}
		}

		ByteBuffer paletteBuffer = ensurePaletteUploadBuffer();
		paletteBuffer.clear();
		for (int i = 0; i < COLORS_PER_PALETTE; i++) {
			Palette.Color color = palette.getColor(i);
			paletteBuffer.put((byte) Byte.toUnsignedInt(color.r));
			paletteBuffer.put((byte) Byte.toUnsignedInt(color.g));
			paletteBuffer.put((byte) Byte.toUnsignedInt(color.b));
			if (i == 0) {
				paletteBuffer.put((byte) 0);
			} else {
				paletteBuffer.put((byte) 255);
			}
		}
		paletteBuffer.flip();

		glBindTexture(GL_TEXTURE_2D, combinedPaletteTextureId);
		glTexSubImage2D(GL_TEXTURE_2D, 0, 0, paletteId, 16, 1, GL_RGBA, GL_UNSIGNED_BYTE, paletteBuffer);

		paletteTextureMap.put("palette_" + paletteId, combinedPaletteTextureId);
	}

	/**
	 * Render a pre-cached pattern at the given coordinates using the specified
	 * palette.
	 */
	public void renderPattern(PatternDesc desc, int x, int y) {
		renderPatternWithId(desc.getPatternIndex(), desc, x, y);
	}

	/**
	 * Render a pattern using an explicit pattern ID for texture lookup.
	 * This allows using pattern IDs beyond the 11-bit limit of PatternDesc.
	 */
	public void renderPatternWithId(int patternId, PatternDesc desc, int x, int y) {
		if (headlessMode) {
			return;
		}

		// Vertical wrap Y adjustment (emulates VDP modular sprite Y coordinates).
		// When enabled, wraps the world Y to the nearest equivalent position within
		// VERTICAL_WRAP_RANGE of the camera, so objects on the "wrong side" of a
		// wrap boundary render at the correct screen position.
		if (verticalWrapAdjustEnabled && verticalWrapRange > 0) {
			int diff = y - verticalWrapCameraY;
			diff = ((diff % verticalWrapRange) + verticalWrapRange) % verticalWrapRange;
			if (diff > verticalWrapRange / 2) {
				diff -= verticalWrapRange;
			}
			y = verticalWrapCameraY + diff;
		}

		ensurePatternAtlas();
		PatternAtlas.Entry entry = patternAtlas != null ? patternAtlas.getEntry(patternId) : null;

		Integer paletteTextureId;
		if (useUnderwaterPaletteForBackground && underwaterPaletteTextureId != null) {
			paletteTextureId = underwaterPaletteTextureId;
		} else {
			paletteTextureId = combinedPaletteTextureId;
		}

		if (entry == null) {
			return;
		}
		if (paletteTextureId == null) {
			return;
		}

		// Try batched rendering for better performance
		// Only use batching if enabled, batch is active, and pattern was successfully added
		boolean usedBatch = false;
		if (entry.atlasIndex() == 0) {
			if (batchingEnabled && instancedBatchActive && instancedPatternRenderer != null) {
				usedBatch = instancedPatternRenderer.addPattern(entry, desc.getPaletteIndex(), desc, x, y);
			} else if (batchingEnabled && batchedRenderer != null && batchedRenderer.isBatchActive()) {
				usedBatch = batchedRenderer.addPattern(entry, desc.getPaletteIndex(), desc, x, y);
			}
		}

		if (!usedBatch) {
			// Fallback to individual commands (use pooled allocation)
			PatternRenderCommand command = PatternRenderCommand.obtain(entry, paletteTextureId, desc, x, y);
			registerCommand(command);
		}
	}

	/**
	 * Render a pattern as a 2-scanline strip for special stage track rendering.
	 *
	 * The Sonic 2 special stage uses per-scanline horizontal scroll to create
	 * a pseudo-3D halfpipe effect where each 8x8 tile appears as 4 strips of
	 * 2 scanlines each. This method renders a single strip (8 wide × 2 high).
	 *
	 * @param patternId  The pattern texture ID
	 * @param desc       The pattern descriptor (handles H/V flip and palette)
	 * @param x          Screen X position
	 * @param y          Screen Y position of this strip
	 * @param stripIndex Which strip to render (0-3, where 0 is top of original
	 *                   tile)
	 */
	public void renderStripPatternWithId(int patternId, PatternDesc desc, int x, int y, int stripIndex) {
		if (headlessMode) {
			return;
		}
		ensurePatternAtlas();
		PatternAtlas.Entry entry = patternAtlas != null ? patternAtlas.getEntry(patternId) : null;
		if (entry == null || combinedPaletteTextureId == null) {
			return;
		}

		// Only use batched rendering for strip patterns
		if (entry.atlasIndex() == 0) {
			if (batchingEnabled && instancedBatchActive && instancedPatternRenderer != null) {
				instancedPatternRenderer.addStripPattern(entry, desc.getPaletteIndex(), desc, x, y, stripIndex);
			} else if (batchingEnabled && batchedRenderer != null && batchedRenderer.isBatchActive()) {
				batchedRenderer.addStripPattern(entry, desc.getPaletteIndex(), desc, x, y, stripIndex);
			}
		}
	}

	/**
	 * Begin a new pattern batch. Call before rendering patterns for a frame/layer.
	 */
	public void beginPatternBatch() {
		if (headlessMode) {
			return;
		}
		if (!batchingEnabled) {
			return;
		}
		if (instancedBatchingEnabled && instancedPatternRenderer != null && instancedPatternRenderer.isSupported()) {
			instancedPatternRenderer.beginBatch();
			instancedBatchActive = true;
			return;
		}
		if (batchedRenderer == null) {
			batchedRenderer = BatchedPatternRenderer.getInstance();
		}
		batchedRenderer.beginBatch();
	}

	/**
	 * Flush the current pattern batch. Call after all patterns for a layer are
	 * submitted. This queues the batch command for execution in the proper order.
	 */
	public void flushPatternBatch() {
		if (headlessMode) {
			return;
		}
		if (instancedBatchActive && instancedPatternRenderer != null) {
			GLCommandable batchCommand = instancedPatternRenderer.endBatch();
			if (batchCommand != null) {
				registerCommand(batchCommand);
			}
			instancedBatchActive = false;
			return;
		}
		if (batchedRenderer != null) {
			// Always call endBatch to reset batchActive state, even if batch is empty
			GLCommandable batchCommand = batchedRenderer.endBatch();
			if (batchCommand != null) {
				registerCommand(batchCommand);
			}
		}
	}

	/**
	 * Begin a new shadow batch. Shadow batches use VDP shadow/highlight mode
	 * where palette index 14 darkens the background.
	 */
	public void beginShadowBatch() {
		if (headlessMode) {
			return;
		}
		if (batchedRenderer == null) {
			batchedRenderer = BatchedPatternRenderer.getInstance();
		}
		batchedRenderer.beginShadowBatch();
	}

	/**
	 * Add a shadow pattern to the current shadow batch.
	 */
	public void addShadowPattern(int patternIndex, PatternDesc desc, int x, int y) {
		if (headlessMode) {
			return;
		}
		ensurePatternAtlas();
		PatternAtlas.Entry entry = patternAtlas != null ? patternAtlas.getEntry(patternIndex) : null;
		if (entry == null) {
			return;
		}
		if (batchedRenderer != null && batchedRenderer.isShadowBatchActive()) {
			batchedRenderer.addShadowPattern(entry, desc, x, y);
		}
	}

	/**
	 * Flush the current shadow batch. This queues the shadow command for
	 * execution with multiplicative blending.
	 */
	public void flushShadowBatch() {
		if (headlessMode) {
			return;
		}
		if (batchedRenderer != null) {
			GLCommandable batchCommand = batchedRenderer.endShadowBatch();
			if (batchCommand != null) {
				registerCommand(batchCommand);
			}
		}
	}

	/**
	 * Enable or disable pattern batching.
	 */
	public void setBatchingEnabled(boolean enabled) {
		this.batchingEnabled = enabled;
	}

	public boolean isBatchingEnabled() {
		return batchingEnabled;
	}

	public void setInstancedBatchingEnabled(boolean enabled) {
		this.instancedBatchingEnabled = enabled;
	}

	public boolean isInstancedBatchingEnabled() {
		return instancedBatchingEnabled;
	}

	/**
	 * Enables vertical wrap Y adjustment for object rendering.
	 * While enabled, Y coordinates passed to renderPattern/renderPatternWithId
	 * are adjusted to the nearest equivalent position modulo the wrap range,
	 * emulating the Mega Drive VDP's modular sprite coordinate system.
	 * <p>
	 * Call this BEFORE rendering objects in vertically-wrapping zones (LZ3, SBZ2),
	 * and call {@link #disableVerticalWrapAdjust()} afterwards to avoid affecting
	 * HUD or other non-wrapping renders.
	 *
	 * @param range   The vertical wrap range in pixels (e.g. 2048)
	 * @param cameraY The current camera Y position
	 */
	public void enableVerticalWrapAdjust(int range, int cameraY) {
		this.verticalWrapAdjustEnabled = true;
		this.verticalWrapRange = range;
		this.verticalWrapCameraY = cameraY;
	}

	/**
	 * Disables vertical wrap Y adjustment.
	 */
	public void disableVerticalWrapAdjust() {
		this.verticalWrapAdjustEnabled = false;
	}

	/**
	 * Get the combined palette texture ID.
	 */
	public Integer getCombinedPaletteTextureId() {
		return combinedPaletteTextureId;
	}

	/**
	 * Get the texture ID for a cached pattern.
	 * @param patternIndex the pattern ID to look up
	 * @return the texture ID, -1 in headless mode when entry exists, or null if not cached
	 */
	public Integer getPatternTextureId(int patternIndex) {
		if (patternAtlas == null) {
			return null;
		}
		PatternAtlas.Entry entry = patternAtlas.getEntry(patternIndex);
		if (entry == null) {
			return null;  // Pattern not cached
		}
		int textureId = patternAtlas.getTextureId(entry.atlasIndex());
		// In headless mode, textureId is 0, return -1 as sentinel
		return textureId == 0 && headlessMode ? -1 : textureId;
	}

	public Integer getPatternAtlasTextureId() {
		return patternAtlas != null ? patternAtlas.getTextureId() : null;
	}

	public Integer getPatternAtlasTextureId(int atlasIndex) {
		return patternAtlas != null ? patternAtlas.getTextureId(atlasIndex) : null;
	}

	public int getPatternAtlasWidth() {
		return patternAtlas != null ? patternAtlas.getAtlasWidth() : 0;
	}

	public int getPatternAtlasHeight() {
		return patternAtlas != null ? patternAtlas.getAtlasHeight() : 0;
	}

	public PatternAtlas.Entry getPatternAtlasEntry(int patternId) {
		ensurePatternAtlas();
		return patternAtlas != null ? patternAtlas.getEntry(patternId) : null;
	}

	public PatternAtlas getPatternAtlas() {
		return patternAtlas;
	}

	/**
	 * Update cached viewport dimensions. Call this from Engine.reshape().
	 * Avoids expensive glGetIntegerv(GL_VIEWPORT) calls every batch.
	 */
	public void setViewport(int x, int y, int width, int height) {
		this.viewportX = x;
		this.viewportY = y;
		this.viewportWidth = width;
		this.viewportHeight = height;
	}

	public int getViewportX() {
		return viewportX;
	}

	public int getViewportY() {
		return viewportY;
	}

	public int getViewportWidth() {
		return viewportWidth;
	}

	public int getViewportHeight() {
		return viewportHeight;
	}

	/**
	 * Get the current waterline Y position in screen coordinates (pixels from top).
	 * Returns a negative value if there's no water in the current zone.
	 */
	public float getWaterlineScreenY() {
		return waterlineScreenY;
	}

	/**
	 * Set the waterline Y position for sprite priority shader underwater palette.
	 * Called by LevelManager.updateWaterShaderState() each frame.
	 *
	 * @param y Screen Y position where water starts (negative to disable)
	 */
	public void setWaterlineScreenY(float y) {
		this.waterlineScreenY = y;
	}

	/**
	 * Get the physical window height in pixels.
	 */
	public float getWindowHeight() {
		return windowHeight;
	}

	/**
	 * Set the physical window height for sprite priority shader.
	 * Called by LevelManager.updateWaterShaderState() each frame.
	 */
	public void setWindowHeight(float height) {
		this.windowHeight = height;
	}

	/**
	 * Get the logical screen height (e.g., 224 for Genesis).
	 */
	public float getScreenHeight() {
		return screenHeight;
	}

	/**
	 * Set the logical screen height for sprite priority shader.
	 * Called by LevelManager.updateWaterShaderState() each frame.
	 */
	public void setScreenHeight(float height) {
		this.screenHeight = height;
	}

	/**
	 * Check if water is enabled for the current zone.
	 */
	public boolean isWaterEnabled() {
		return waterEnabled;
	}

	/**
	 * Set whether water is enabled for sprite priority shader.
	 * Called by LevelManager.updateWaterShaderState() each frame.
	 */
	public void setWaterEnabled(boolean enabled) {
		this.waterEnabled = enabled;
	}

	private Integer underwaterPaletteTextureId;

	public Integer getUnderwaterPaletteTextureId() {
		return underwaterPaletteTextureId;
	}

	public void cacheUnderwaterPaletteTexture(Palette[] palettes, Palette normalLine0) {
		if (headlessMode)
			return;

		int totalLines = RenderContext.getTotalPaletteLines();

		if (underwaterPaletteTextureId == null) {
			underwaterPaletteTextureId = glGenTextures();
			glBindTexture(GL_TEXTURE_2D, underwaterPaletteTextureId);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
			glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
		}

		// Pre-compute underwater base line 0 for donor palette derivation
		Palette underwaterLine0 = (palettes != null && palettes.length > 0) ? palettes[0] : null;

		// Upload 16 * totalLines colors
		int bufferSize = 16 * totalLines * 4;
		ByteBuffer paletteBuffer = MemoryUtil.memAlloc(bufferSize);
		try {
			paletteBuffer.clear();

			for (int pIndex = 0; pIndex < totalLines; pIndex++) {
				Palette p = (palettes != null && pIndex < palettes.length) ? palettes[pIndex] : null;

				// For donor palette rows, derive underwater palette from base game's color shift
				if (p == null && normalLine0 != null && underwaterLine0 != null) {
					for (RenderContext ctx : RenderContext.getDonorContexts()) {
						int base = ctx.getPaletteLineBase();
						if (pIndex >= base && pIndex < base + RenderContext.LINES_PER_CONTEXT) {
							Palette donorNormal = ctx.getPalette(pIndex - base);
							if (donorNormal != null) {
								p = RenderContext.deriveUnderwaterPalette(donorNormal, normalLine0, underwaterLine0);
							}
							break;
						}
					}
				}

				for (int i = 0; i < 16; i++) {
					try {
						if (p != null) {
							Palette.Color color = p.getColor(i);
							paletteBuffer.put((byte) Byte.toUnsignedInt(color.r));
							paletteBuffer.put((byte) Byte.toUnsignedInt(color.g));
							paletteBuffer.put((byte) Byte.toUnsignedInt(color.b));
							if (i == 0) {
								paletteBuffer.put((byte) 0);
							} else {
								paletteBuffer.put((byte) 255);
							}
						} else {
							// Empty/Black for missing palette lines
							paletteBuffer.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
						}
					} catch (Exception e) {
						// Fallback
						paletteBuffer.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
					}
				}
			}
			paletteBuffer.flip();

			glBindTexture(GL_TEXTURE_2D, underwaterPaletteTextureId);
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 16, totalLines, 0, GL_RGBA, GL_UNSIGNED_BYTE, paletteBuffer);
		} finally {
			MemoryUtil.memFree(paletteBuffer);
		}
	}

	/**
	 * Release GL resources owned by the level renderers, palette textures, and
	 * native upload buffers. Safe to call in any mode (headless guards internally).
	 */
	private void releasePerLevelResources() {
		paletteTextureMap.clear();
		if (!headlessMode && glInitialized) {
			if (backgroundRenderer != null) {
				backgroundRenderer.cleanup();
			}
			if (tilemapGpuRenderer != null) {
				tilemapGpuRenderer.cleanup();
			}
			if (instancedPatternRenderer != null) {
				instancedPatternRenderer.cleanup();
			}
			if (combinedPaletteTextureId != null) {
				glDeleteTextures(combinedPaletteTextureId);
			}
			if (underwaterPaletteTextureId != null) {
				glDeleteTextures(underwaterPaletteTextureId);
			}
		}
		backgroundRenderer = null;
		tilemapGpuRenderer = null;
		instancedPatternRenderer = null;
		combinedPaletteTextureId = null;
		currentPaletteTextureHeight = 0;
		underwaterPaletteTextureId = null;
		if (paletteUploadBuffer != null) {
			MemoryUtil.memFree(paletteUploadBuffer);
			paletteUploadBuffer = null;
		}
		if (underwaterPaletteUploadBuffer != null) {
			MemoryUtil.memFree(underwaterPaletteUploadBuffer);
			underwaterPaletteUploadBuffer = null;
		}
	}

	/**
	 * Cleanup method to delete textures and release resources.
	 */
	public void cleanup() {
		if (headlessMode || !glInitialized) {
			// In headless mode, just clear the tracking maps
			if (patternAtlas != null) {
				patternAtlas.cleanupHeadless();
			}
			BatchedPatternRenderer existingBatch = BatchedPatternRenderer.getInstanceIfInitialized();
			if (existingBatch != null) {
				existingBatch.cleanupHeadless();
			}
			if (instancedPatternRenderer != null) {
				instancedPatternRenderer.cleanupHeadless();
			}
			paletteTextureMap.clear();
			combinedPaletteTextureId = null;
			currentPaletteTextureHeight = 0;
			return;
		}
		// Delete pattern atlas texture
		if (patternAtlas != null) {
			patternAtlas.cleanup();
		}
		// Cleanup shader programs
		if (defaultShaderProgram != null) {
			defaultShaderProgram.cleanup();
		}
		if (waterShaderProgram != null) {
			waterShaderProgram.cleanup();
		}
		if (debugShaderProgram != null) {
			debugShaderProgram.cleanup();
		}
		if (fadeShaderProgram != null) {
			fadeShaderProgram.cleanup();
		}
		if (shadowShaderProgram != null) {
			shadowShaderProgram.cleanup();
		}
		if (cnzSlotsShaderProgram != null) {
			cnzSlotsShaderProgram.cleanup();
		}
		if (cnzSlotMachineRenderer != null) {
			cnzSlotMachineRenderer.cleanup();
		}
		BatchedPatternRenderer existingBatch = BatchedPatternRenderer.getInstanceIfInitialized();
		if (existingBatch != null) {
			existingBatch.cleanup();
		}
		// Sprite priority rendering cleanup
		if (spritePriorityShaderProgram != null) {
			spritePriorityShaderProgram.cleanup();
		}
		if (tilePriorityFBO != null) {
			tilePriorityFBO.cleanup();
		}
		// Reset fade manager
		if (fadeManager != null) {
			fadeManager.cleanup();
			fadeManager.cancel();
			fadeManager = null;
		}
		// Release renderers, palette textures, native buffers
		releasePerLevelResources();
		glInitialized = false;
	}

	private void ensurePatternAtlas() {
		if (patternAtlas == null) {
			patternAtlas = new PatternAtlas(ATLAS_WIDTH, ATLAS_HEIGHT);
		}
		if (!patternAtlas.isInitialized() && glInitialized) {
			patternAtlas.init();
		}
	}

	/**
	 * Singleton access to the GraphicsManager instance.
	 */
	public static synchronized final GraphicsManager getInstance() {
		if (graphicsManager == null) {
			graphicsManager = new GraphicsManager();
		}
		return graphicsManager;
	}

	/**
	 * Resets mutable rendering state without destroying the singleton instance.
	 * Preserves headlessMode and glInitialized configuration.
	 * Clears render command queues and palette caches.
	 */
	public void resetState() {
		commands.clear();
		releasePerLevelResources();
		camera = null;
		useUnderwaterPaletteForBackground = false;
		useSpritePriorityShader = false;
		currentSpriteHighPriority = false;
		waterlineScreenY = 0;
		windowHeight = 224;
		screenHeight = 224;
		waterEnabled = false;
		if (patternAtlas != null) {
			if (headlessMode) {
				patternAtlas.cleanupHeadless();
			} else {
				patternAtlas.cleanup();
			}
			patternAtlas = null;
		}
	}

	/**
	 * Reset the singleton instance. Used for testing.
	 */
	public static synchronized void resetInstance() {
		if (graphicsManager != null) {
			graphicsManager.cleanup();
			graphicsManager = null;
		}
	}

	public ShaderProgram getShaderProgram() {
		if (useSpritePriorityShader && spritePriorityShaderProgram != null) {
			return spritePriorityShaderProgram;
		}
		return currentShaderProgram;
	}

	/**
	 * Set the Engine reference for accessing projection matrix.
	 */
	public void setEngine(Engine engine) {
		this.engine = engine;
	}

	/**
	 * Get the Engine reference.
	 */
	public Engine getEngine() {
		return engine;
	}

	/**
	 * Set the projection matrix buffer directly.
	 * Use this when testing or when no Engine instance is available.
	 * The buffer should be a 16-element float array in column-major order.
	 */
	public void setProjectionMatrixBuffer(float[] buffer) {
		this.projectionMatrixBuffer = buffer;
	}

	/**
	 * Get the projection matrix buffer for shader-based rendering.
	 * First checks if a local buffer has been set, then falls back to Engine.
	 * @return the projection matrix as a 16-element float array, or null if not available
	 */
	public float[] getProjectionMatrixBuffer() {
		// First try local buffer (set directly by tests or other code)
		if (projectionMatrixBuffer != null) {
			return projectionMatrixBuffer;
		}
		// Fall back to engine reference
		if (engine != null) {
			return engine.getProjectionMatrixBuffer();
		}
		// Finally try Engine singleton
		Engine engineInstance = Engine.getInstance();
		if (engineInstance != null) {
			return engineInstance.getProjectionMatrixBuffer();
		}
		return null;
	}

	/**
	 * Enable or disable sprite priority shader mode.
	 * When enabled, getShaderProgram() returns the sprite priority shader.
	 */
	public void setUseSpritePriorityShader(boolean use) {
		this.useSpritePriorityShader = use;
	}

	/**
	 * Check if sprite priority shader mode is enabled.
	 */
	public boolean isUseSpritePriorityShader() {
		return useSpritePriorityShader;
	}

	/**
	 * Set whether the current sprite being rendered has high priority.
	 * This is used by the sprite priority shader to determine if the sprite
	 * should appear above or behind high-priority tiles.
	 */
	public void setCurrentSpriteHighPriority(boolean highPriority) {
		this.currentSpriteHighPriority = highPriority;
	}

	/**
	 * Get whether the current sprite being rendered has high priority.
	 */
	public boolean getCurrentSpriteHighPriority() {
		return currentSpriteHighPriority;
	}

	public WaterShaderProgram getWaterShaderProgram() {
		return waterShaderProgram;
	}

	public ShaderProgram getInstancedShaderProgram() {
		return instancedPatternRenderer != null ? instancedPatternRenderer.getInstancedShaderProgram() : null;
	}

	public WaterShaderProgram getInstancedWaterShaderProgram() {
		return instancedPatternRenderer != null ? instancedPatternRenderer.getInstancedWaterShaderProgram() : null;
	}

	public void setUseWaterShader(boolean use) {
		if (use) {
			currentShaderProgram = waterShaderProgram;
		} else {
			currentShaderProgram = defaultShaderProgram;
		}
		this.shaderProgram = currentShaderProgram;
	}

	/**
	 * Sets whether to use the underwater palette for background rendering.
	 * When true, all patterns rendered will use the underwater palette instead of
	 * the normal palette.
	 * This mirrors the original game's behavior where the entire background changes
	 * palette
	 * when Sonic is underwater.
	 */
	public void setUseUnderwaterPaletteForBackground(boolean use) {
		this.useUnderwaterPaletteForBackground = use;
	}

	/**
	 * Returns whether the underwater palette should be used for background
	 * rendering.
	 */
	public boolean isUseUnderwaterPaletteForBackground() {
		return useUnderwaterPaletteForBackground;
	}

	public ShaderProgram getDebugShaderProgram() {
		return debugShaderProgram;
	}

	public ShaderProgram getFadeShaderProgram() {
		return fadeShaderProgram;
	}

	public TilemapGpuRenderer getTilemapGpuRenderer() {
		return tilemapGpuRenderer;
	}

	public ShaderProgram getShadowShaderProgram() {
		return shadowShaderProgram;
	}

	/**
	 * Get the fade manager for screen transitions.
	 */
	public FadeManager getFadeManager() {
		if (fadeManager == null) {
			fadeManager = FadeManager.getInstance();
			if (fadeShaderProgram != null) {
				fadeManager.setFadeShader(fadeShaderProgram);
			}
		}
		return fadeManager;
	}

	/**
	 * Check if GL is initialized.
	 */
	public boolean isGlInitialized() {
		return glInitialized;
	}

	/**
	 * Get the CNZ slot machine renderer for visual display.
	 * Lazily initializes the shader on first access.
	 */
	public CNZSlotMachineRenderer getCnzSlotMachineRenderer() {
		if (cnzSlotMachineRenderer != null && cnzSlotsShaderProgram == null && glInitialized) {
			try {
				cnzSlotsShaderProgram = new ShaderProgram(ShaderProgram.FULLSCREEN_VERTEX_SHADER, CNZ_SLOTS_SHADER_PATH);
				cnzSlotMachineRenderer.setShader(cnzSlotsShaderProgram);
			} catch (Exception e) {
				System.err.println("Failed to load CNZ slot shader: " + e.getMessage());
			}
		}
		return cnzSlotMachineRenderer;
	}

	/**
	 * Get the background renderer for shader-based parallax scrolling.
	 * Initializes it lazily on first access.
	 */
	public BackgroundRenderer getBackgroundRenderer() {
		if (headlessMode) {
			return null;
		}
		if (backgroundRenderer == null && glInitialized) {
			try {
				backgroundRenderer = new BackgroundRenderer();
				backgroundRenderer.init(PARALLAX_SHADER_PATH);
				LOGGER.info("BackgroundRenderer initialized for shader-based parallax.");
			} catch (IOException e) {
				LOGGER.log(Level.SEVERE, "Failed to initialize BackgroundRenderer", e);
			}
		}
		return backgroundRenderer;
	}

	/**
	 * Enqueue OpenGL state for debug line rendering.
	 * Note: Deprecated fixed-function calls (GL_TEXTURE_2D, GL_LIGHTING, GL_COLOR_MATERIAL)
	 * have been removed for OpenGL 4.1 core profile compatibility.
	 */
	public void enqueueDebugLineState() {
		ShaderProgram debugShader = getDebugShaderProgram();
		int programId = debugShader != null ? debugShader.getProgramId() : 0;
		registerCommand(new GLCommand(GLCommand.CommandType.USE_PROGRAM, programId));
		registerCommand(new GLCommand(GLCommand.CommandType.DISABLE, GL_DEPTH_TEST));
	}

	/**
	 * Enqueue OpenGL state for default shader rendering.
	 * Note: glEnable(GL_TEXTURE_2D) has been removed for OpenGL 4.1 core profile compatibility.
	 * Texturing is now controlled entirely through shaders.
	 */
	public void enqueueDefaultShaderState() {
		ShaderProgram shader = getShaderProgram();
		if (shader != null) {
			int programId = shader.getProgramId();
			if (programId != 0) {
				registerCommand(new GLCommand(GLCommand.CommandType.USE_PROGRAM, programId));
			}
		}
	}

	/**
	 * Enables scissor test with the specified rectangle.
	 * Coordinates are in OpenGL screen space (Y=0 at bottom).
	 *
	 * @param x      Left edge of scissor rectangle
	 * @param y      Bottom edge of scissor rectangle
	 * @param width  Width of scissor rectangle
	 * @param height Height of scissor rectangle
	 */
	public void enableScissor(int x, int y, int width, int height) {
		if (headlessMode || !glInitialized)
			return;
		glScissor(x, y, width, height);
		glEnable(GL_SCISSOR_TEST);
	}

	/**
	 * Disables scissor test.
	 */
	public void disableScissor() {
		if (headlessMode || !glInitialized)
			return;
		glDisable(GL_SCISSOR_TEST);
	}

	/**
	 * Get the unified UI render pipeline for overlay + fade ordering.
	 */
	public UiRenderPipeline getUiRenderPipeline() {
		return uiRenderPipeline;
	}

	// ==================== Sprite Priority Rendering ====================

	/**
	 * Get the sprite priority shader program for ROM-accurate sprite-to-tile layering.
	 * This shader composites sprites with awareness of high-priority foreground tiles.
	 */
	public SpritePriorityShaderProgram getSpritePriorityShaderProgram() {
		return spritePriorityShaderProgram;
	}

	/**
	 * Get the tile priority FBO for rendering high-priority tile information.
	 * Lazily initializes the FBO with specified dimensions if not already done.
	 *
	 * @param width  Screen width in pixels
	 * @param height Screen height in pixels
	 */
	public TilePriorityFBO getTilePriorityFBO(int width, int height) {
		if (headlessMode || !glInitialized) {
			return null;
		}
		if (tilePriorityFBO != null && !tilePriorityFBO.isInitialized()) {
			tilePriorityFBO.init(width, height);
		} else if (tilePriorityFBO != null) {
			tilePriorityFBO.resize(width, height);
		}
		return tilePriorityFBO;
	}

	/**
	 * Get the tile priority FBO without initializing or resizing.
	 * Returns null if not yet initialized.
	 */
	public TilePriorityFBO getTilePriorityFBO() {
		return tilePriorityFBO;
	}

	/**
	 * Begin rendering high-priority tiles to the tile priority FBO.
	 * Call this before rendering the high-priority foreground tile pass.
	 *
	 * @param width  Screen width in pixels
	 * @param height Screen height in pixels
	 */
	public void beginTilePriorityPass(int width, int height) {
		if (headlessMode || !glInitialized) {
			return;
		}
		TilePriorityFBO fbo = getTilePriorityFBO(width, height);
		if (fbo != null) {
			fbo.begin();
		}
	}

	/**
	 * End rendering high-priority tiles to the tile priority FBO.
	 * Call this after rendering the high-priority foreground tile pass.
	 */
	public void endTilePriorityPass() {
		if (headlessMode || !glInitialized || tilePriorityFBO == null) {
			return;
		}
		tilePriorityFBO.end();
	}
}

