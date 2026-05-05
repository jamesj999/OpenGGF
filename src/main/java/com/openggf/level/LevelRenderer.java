package com.openggf.level;

import com.openggf.TraceSessionLauncher;
import com.openggf.audio.AudioManager;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.render.AdvancedRenderFrameState;
import com.openggf.game.render.AdvancedRenderModeContext;
import com.openggf.game.render.AdvancedRenderModeController;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.game.sonic3k.constants.Sonic3kZoneIds;
import com.openggf.graphics.GLCommand;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternRenderCommand;
import com.openggf.graphics.RenderPriority;
import com.openggf.graphics.TilePriorityFBO;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.graphics.WaterShaderProgram;
import com.openggf.level.objects.HudRenderManager;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.render.BackgroundRenderer;
import com.openggf.level.rings.RingManager;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glBindTexture;
import static org.lwjgl.opengl.GL11.glBlendFunc;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL13.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13.GL_TEXTURE2;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL14.GL_MAX;
import static org.lwjgl.opengl.GL14.glBlendEquation;
import static org.lwjgl.opengl.GL20.glUniform1i;

/**
 * Owns the per-frame rendering pipeline previously inlined in {@link LevelManager}.
 *
 * <p>This is a pure extraction — the {@code draw()} / {@code drawWithRenderOptions()}
 * sequence, the pre-allocated {@link GLCommand} lambdas they enqueue, and the helper
 * methods that produce them all live here. {@link LevelManager} retains ownership of
 * level state, lifecycle, and frame counters, but delegates the rendering pass to
 * this class.
 *
 * <p>Behavioural parity is required: the order of {@code GLCommand} registrations
 * and the values written into shader uniforms must match the previous in-place
 * implementation pixel-for-pixel.
 */
public final class LevelRenderer {

    /**
     * Reference to the parent {@link LevelManager}. Captured by the
     * {@link GLCommand} field initializers below; non-final because Java's
     * definite-assignment rules flag the lambda captures otherwise.
     */
    private LevelManager lm;

    // Pre-allocated viewport buffer to avoid per-frame int[4] allocations inside GL commands.
    private final int[] viewportBuffer = new int[4];

    // Mutable state for the pre-allocated water shader setup command.
    private float pendingWaterlineScreenY;
    private int pendingWaterShimmerStyle;
    private boolean pendingSuppressUnderwaterPalette;

    // Mutable state for the pre-allocated BG ensureCapacity command.
    private int pendingBgRenderWidth;
    private int pendingBgRenderHeight;

    // Mutable state for the pre-allocated BG renderWithScrollWide command.
    private int[] pendingBgHScrollData;
    private short[] pendingBgVScrollData;
    private short[] pendingBgVScrollColumnData;
    private int pendingBgShaderScrollMidpoint;
    private int pendingBgShaderExtraBuffer;
    private int pendingBgVOffset;
    private boolean pendingBgPerLineScroll;

    // Mutable state for the pre-allocated FG tilemap pass commands (low + high priority).
    private float pendingFgWorldOffsetX_low;
    private float pendingFgWorldOffsetY_low;
    private int pendingFgScreenW_low;
    private int pendingFgScreenH_low;
    private int pendingFgPriorityPass_low;
    private boolean pendingFgUseUnderwater_low;
    private float pendingFgWaterlineScreenY_low;
    private Integer pendingFgAtlasId_low;
    private Integer pendingFgPaletteId_low;
    private Integer pendingFgUnderwaterPaletteId_low;

    private float pendingFgWorldOffsetX_high;
    private float pendingFgWorldOffsetY_high;
    private int pendingFgScreenW_high;
    private int pendingFgScreenH_high;
    private int pendingFgPriorityPass_high;
    private boolean pendingFgUseUnderwater_high;
    private float pendingFgWaterlineScreenY_high;
    private Integer pendingFgAtlasId_high;
    private Integer pendingFgPaletteId_high;
    private Integer pendingFgUnderwaterPaletteId_high;

    // Mutable state for the pre-allocated high-priority FBO command.
    private int pendingFboScreenW;
    private int pendingFboScreenH;
    private float pendingFboFgWorldOffsetX;
    private float pendingFboFgWorldOffsetY;
    private Integer pendingFboAtlasId;
    private Integer pendingFboPaletteId;

    // Mutable state for the pre-allocated BG tile pass command.
    private int pendingBgTilePassRenderWidth;
    private int pendingBgTilePassRenderHeight;
    private boolean pendingBgTilePassHasWater;
    private float pendingBgTilePassFboWaterlineY;
    private int pendingBgTilePassAlignedBgY;
    private float pendingBgTilePassBgTilemapWorldOffsetX;
    private boolean pendingBgTilePassPerLineScroll;
    private short[] pendingBgTilePassPerColumnVScroll;
    private int[] pendingBgTilePassHScrollData;
    private float pendingBgTilePassVdpWrapWidth;
    private float pendingBgTilePassNametableBase;
    private float pendingBgTilePassPerLineScrollSampleYOffsetPx;
    private float pendingBgTilePassUpperBandWrapHeightPx;
    private float pendingBgTilePassUpperBandWrapWidthTiles;

    // Render-frame state (resolved each frame from the AdvancedRenderModeController).
    private AdvancedRenderFrameState currentAdvancedRenderFrameState = AdvancedRenderFrameState.disabled();

    // Shimmer style flag, sampled by background tile pass.
    private int currentShimmerStyle = 0;

    // Pre-allocated GLCommand instances. Safe to reuse — the command list is cleared
    // each frame in flushWithCamera().

    private final GLCommand disableShimmerCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        WaterShaderProgram waterShader = lm.graphicsManager.getWaterShaderProgram();
        if (waterShader != null) {
            waterShader.use();
            waterShader.setShimmerStyle(0);
        }
        WaterShaderProgram instancedWaterShader = lm.graphicsManager.getInstancedWaterShaderProgram();
        if (instancedWaterShader != null) {
            instancedWaterShader.use();
            instancedWaterShader.setShimmerStyle(0);
        }
        if (waterShader != null) {
            waterShader.use();
        }
        PatternRenderCommand.resetFrameState();
    });

    private final GLCommand disableWaterShaderCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        lm.graphicsManager.setUseWaterShader(false);
        PatternRenderCommand.resetFrameState();
    });

    private final GLCommand waterShaderSetupCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        GraphicsManager graphics = lm.graphicsManager;
        SonicConfigurationService configuration = lm.configService;
        graphics.setUseWaterShader(true);

        WaterShaderProgram shader = graphics.getWaterShaderProgram();
        shader.use();

        glGetIntegerv(GL_VIEWPORT, viewportBuffer);
        float windowHeight = (float) viewportBuffer[3];
        float screenHeightPixels = (float) configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);

        shader.setWindowHeight(windowHeight);
        shader.setWaterlineScreenY(pendingWaterlineScreenY);
        shader.setFrameCounter(lm.frameCounter);
        shader.setDistortionAmplitude(0.0f);
        shader.setShimmerStyle(pendingWaterShimmerStyle);
        shader.setIndexedTextureWidth(graphics.getPatternAtlasWidth());
        shader.setScreenDimensions((float) configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS),
                screenHeightPixels);

        graphics.setWaterEnabled(!pendingSuppressUnderwaterPalette);
        graphics.setWaterlineScreenY(pendingWaterlineScreenY);
        graphics.setWindowHeight(windowHeight);
        graphics.setScreenHeight(screenHeightPixels);

        int zoneId = lm.getFeatureZoneId();
        Palette[] underwater = lm.waterSystem.getUnderwaterPalette(zoneId, lm.currentAct);
        if (underwater != null) {
            Palette normalLine0 = (lm.level != null) ? lm.level.getPalette(0) : null;
            graphics.cacheUnderwaterPaletteTexture(underwater, normalLine0);
            Integer texId = graphics.getUnderwaterPaletteTextureId();
            int loc = shader.getUnderwaterPaletteLocation();

            if (texId != null && loc != -1) {
                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, texId);
                glUniform1i(loc, 2);
                glActiveTexture(GL_TEXTURE0);
            }
        }

        TilemapGpuRenderer tilemapRenderer = graphics.getTilemapGpuRenderer();
        if (tilemapRenderer != null) {
            tilemapRenderer.setShimmerState(lm.frameCounter, pendingWaterShimmerStyle);
        }

        WaterShaderProgram instancedShader = graphics.getInstancedWaterShaderProgram();
        if (instancedShader != null) {
            instancedShader.use();
            instancedShader.cacheUniformLocations();
            instancedShader.setWindowHeight(windowHeight);
            instancedShader.setWaterlineScreenY(pendingWaterlineScreenY);
            instancedShader.setFrameCounter(lm.frameCounter);
            instancedShader.setDistortionAmplitude(0.0f);
            instancedShader.setShimmerStyle(pendingWaterShimmerStyle);
            instancedShader.setIndexedTextureWidth(graphics.getPatternAtlasWidth());
            instancedShader.setScreenDimensions((float) configuration.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS),
                    (float) configuration.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS));

            Palette[] underwaterInstanced = lm.waterSystem.getUnderwaterPalette(zoneId, lm.currentAct);
            if (underwaterInstanced != null) {
                Palette normalLine0Instanced = (lm.level != null) ? lm.level.getPalette(0) : null;
                graphics.cacheUnderwaterPaletteTexture(underwaterInstanced, normalLine0Instanced);
                Integer texId = graphics.getUnderwaterPaletteTextureId();
                int loc = instancedShader.getUnderwaterPaletteLocation();
                if (texId != null && loc != -1) {
                    glActiveTexture(GL_TEXTURE2);
                    glBindTexture(GL_TEXTURE_2D, texId);
                    glUniform1i(loc, 2);
                    glActiveTexture(GL_TEXTURE0);
                }
            }
            shader.use();
        }
    });

    private final GLCommand bgEnsureCapacityCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        BackgroundRenderer bgRenderer = lm.graphicsManager.getBackgroundRenderer();
        if (bgRenderer != null) {
            bgRenderer.ensureCapacity(pendingBgRenderWidth, pendingBgRenderHeight);
        }
    });

    private final GLCommand bgRenderWithScrollCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        BackgroundRenderer bgRenderer = lm.graphicsManager.getBackgroundRenderer();
        if (bgRenderer != null) {
            bgRenderer.renderWithScrollWide(pendingBgHScrollData, pendingBgVScrollData, pendingBgVScrollColumnData,
                    pendingBgShaderScrollMidpoint, pendingBgShaderExtraBuffer,
                    pendingBgVOffset, pendingBgPerLineScroll);
        }
    });

    private final GLCommand fgTilemapPassLowCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        TilemapGpuRenderer tilemapRenderer = lm.graphicsManager.getTilemapGpuRenderer();
        if (tilemapRenderer == null) {
            return;
        }
        applyForegroundScrollFeatures(tilemapRenderer);
        short[] fgPerColumnVScrollLow = resolveForegroundPerColumnVScroll();
        if (fgPerColumnVScrollLow != null) {
            tilemapRenderer.enablePerColumnVScroll(fgPerColumnVScrollLow);
        }
        glGetIntegerv(GL_VIEWPORT, viewportBuffer);
        tilemapRenderer.render(
                TilemapGpuRenderer.Layer.FOREGROUND,
                pendingFgScreenW_low,
                pendingFgScreenH_low,
                viewportBuffer[0],
                viewportBuffer[1],
                viewportBuffer[2],
                viewportBuffer[3],
                pendingFgWorldOffsetX_low,
                pendingFgWorldOffsetY_low,
                lm.graphicsManager.getPatternAtlasWidth(),
                lm.graphicsManager.getPatternAtlasHeight(),
                pendingFgAtlasId_low,
                pendingFgPaletteId_low,
                pendingFgUnderwaterPaletteId_low != null ? pendingFgUnderwaterPaletteId_low : 0,
                pendingFgPriorityPass_low,
                lm.verticalWrapEnabled,
                false,
                pendingFgUseUnderwater_low,
                pendingFgWaterlineScreenY_low);
    });

    private final GLCommand fgTilemapPassHighCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        TilemapGpuRenderer tilemapRenderer = lm.graphicsManager.getTilemapGpuRenderer();
        if (tilemapRenderer == null) {
            return;
        }
        applyForegroundScrollFeatures(tilemapRenderer);
        short[] fgPerColumnVScrollHigh = resolveForegroundPerColumnVScroll();
        if (fgPerColumnVScrollHigh != null) {
            tilemapRenderer.enablePerColumnVScroll(fgPerColumnVScrollHigh);
        }
        glGetIntegerv(GL_VIEWPORT, viewportBuffer);
        tilemapRenderer.render(
                TilemapGpuRenderer.Layer.FOREGROUND,
                pendingFgScreenW_high,
                pendingFgScreenH_high,
                viewportBuffer[0],
                viewportBuffer[1],
                viewportBuffer[2],
                viewportBuffer[3],
                pendingFgWorldOffsetX_high,
                pendingFgWorldOffsetY_high,
                lm.graphicsManager.getPatternAtlasWidth(),
                lm.graphicsManager.getPatternAtlasHeight(),
                pendingFgAtlasId_high,
                pendingFgPaletteId_high,
                pendingFgUnderwaterPaletteId_high != null ? pendingFgUnderwaterPaletteId_high : 0,
                pendingFgPriorityPass_high,
                lm.verticalWrapEnabled,
                false,
                pendingFgUseUnderwater_high,
                pendingFgWaterlineScreenY_high);
    });

    private final GLCommand highPriorityFboCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        TilePriorityFBO tileFbo = lm.graphicsManager.getTilePriorityFBO();
        TilemapGpuRenderer tilemapRenderer = lm.graphicsManager.getTilemapGpuRenderer();
        if (tileFbo == null || tilemapRenderer == null) {
            return;
        }

        tileFbo.begin();

        glEnable(GL_BLEND);
        glBlendEquation(GL_MAX);
        glBlendFunc(GL_ONE, GL_ONE);

        applyForegroundScrollFeatures(tilemapRenderer);
        short[] fgPerColumnVScrollFbo = resolveForegroundPerColumnVScroll();
        if (fgPerColumnVScrollFbo != null) {
            tilemapRenderer.enablePerColumnVScroll(fgPerColumnVScrollFbo);
        }
        tilemapRenderer.render(
                TilemapGpuRenderer.Layer.FOREGROUND,
                pendingFboScreenW,
                pendingFboScreenH,
                0, 0, pendingFboScreenW, pendingFboScreenH,
                pendingFboFgWorldOffsetX,
                pendingFboFgWorldOffsetY,
                lm.graphicsManager.getPatternAtlasWidth(),
                lm.graphicsManager.getPatternAtlasHeight(),
                pendingFboAtlasId,
                pendingFboPaletteId,
                0, 1, lm.verticalWrapEnabled, true, false, 0.0f);

        glBlendEquation(GL_FUNC_ADD);
        glDisable(GL_BLEND);

        tileFbo.end();
    });

    private final GLCommand bgTilePassCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        BackgroundRenderer bgRenderer = lm.graphicsManager.getBackgroundRenderer();
        if (bgRenderer == null) {
            return;
        }
        bgRenderer.beginTilePass(pendingBgTilePassRenderWidth, pendingBgTilePassRenderHeight, true);
        TilemapGpuRenderer tilemapRenderer = lm.graphicsManager.getTilemapGpuRenderer();
        if (tilemapRenderer != null) {
            int savedShimmerStyle = tilemapRenderer.getShimmerStyle();
            tilemapRenderer.setShimmerState(lm.frameCounter, 0);

            Integer atlasId = lm.graphicsManager.getPatternAtlasTextureId();
            Integer paletteId = lm.graphicsManager.getCombinedPaletteTextureId();
            Integer underwaterPaletteId = lm.graphicsManager.getUnderwaterPaletteTextureId();
            boolean useUnderwaterPalette = pendingBgTilePassHasWater && underwaterPaletteId != null;
            if (atlasId != null && paletteId != null) {
                if (pendingBgTilePassPerLineScroll) {
                    bgRenderer.uploadHScroll(pendingBgTilePassHScrollData);
                    tilemapRenderer.enablePerLineScroll(
                            bgRenderer.getHScrollTextureId(), 224.0f,
                            pendingBgTilePassVdpWrapWidth, pendingBgTilePassNametableBase,
                            pendingBgTilePassPerLineScrollSampleYOffsetPx);
                }
                tilemapRenderer.setUpperBandWrap(
                        pendingBgTilePassUpperBandWrapHeightPx,
                        pendingBgTilePassUpperBandWrapWidthTiles);
                if (pendingBgTilePassPerColumnVScroll != null && pendingBgTilePassPerColumnVScroll.length > 0) {
                    tilemapRenderer.enablePerColumnVScroll(pendingBgTilePassPerColumnVScroll);
                }
                glGetIntegerv(GL_VIEWPORT, viewportBuffer);
                tilemapRenderer.render(
                        TilemapGpuRenderer.Layer.BACKGROUND,
                        pendingBgTilePassRenderWidth,
                        pendingBgTilePassRenderHeight,
                        viewportBuffer[0],
                        viewportBuffer[1],
                        viewportBuffer[2],
                        viewportBuffer[3],
                        pendingBgTilePassBgTilemapWorldOffsetX,
                        (float) pendingBgTilePassAlignedBgY,
                        lm.graphicsManager.getPatternAtlasWidth(),
                        lm.graphicsManager.getPatternAtlasHeight(),
                        atlasId,
                        paletteId,
                        underwaterPaletteId != null ? underwaterPaletteId : 0,
                        -1,
                        true,
                        false,
                        useUnderwaterPalette,
                        pendingBgTilePassFboWaterlineY);
            }

            tilemapRenderer.setShimmerState(lm.frameCounter, savedShimmerStyle);
        }

        bgRenderer.endTilePass();
        lm.graphicsManager.setUseUnderwaterPaletteForBackground(false);
    });

    LevelRenderer(LevelManager levelManager) {
        this.lm = levelManager;
    }

    /** Returns the AdvancedRenderFrameState resolved for the current frame. */
    public AdvancedRenderFrameState getCurrentAdvancedRenderFrameState() {
        return currentAdvancedRenderFrameState;
    }

    /** Resets per-frame derived state (used when the level is unloaded). */
    void resetState() {
        currentShimmerStyle = 0;
        currentAdvancedRenderFrameState = AdvancedRenderFrameState.disabled();
    }

    /** Drains the GLCommand to disable water shader (used by callers needing post-pass cleanup). */
    GLCommand getDisableWaterShaderCommand() {
        return disableWaterShaderCommand;
    }

    private void applyForegroundScrollFeatures(TilemapGpuRenderer tilemapRenderer) {
        if (currentAdvancedRenderFrameState.enableForegroundHeatHaze()
                || currentAdvancedRenderFrameState.enablePerLineForegroundScroll()) {
            tilemapRenderer.enablePerLineForegroundScroll(lm.parallaxManager.getHScrollForShader());
        }
    }

    private short[] resolveForegroundPerColumnVScroll() {
        short[] override = currentAdvancedRenderFrameState.foregroundPerColumnVScrollOverride();
        return override != null ? override : lm.parallaxManager.getVScrollPerColumnFGForShader();
    }

    private void resolveAdvancedRenderFrameState(int frameCounter) {
        AdvancedRenderModeController controller = GameServices.advancedRenderModeControllerOrNull();
        if (controller == null || controller.isEmpty() || lm.camera == null) {
            currentAdvancedRenderFrameState = AdvancedRenderFrameState.disabled();
            return;
        }
        currentAdvancedRenderFrameState = controller.resolve(new AdvancedRenderModeContext(
                lm.camera,
                frameCounter,
                lm,
                lm.getFeatureZoneId(),
                lm.getFeatureActId(),
                lm.camera.getX()));
    }

    void dispatchSpecialRenderEffects(SpecialRenderEffectStage stage, int frameCounter) {
        SpecialRenderEffectRegistry registry = GameServices.specialRenderEffectRegistryOrNull();
        if (registry == null || registry.isEmpty() || lm.camera == null || lm.graphicsManager == null) {
            return;
        }
        registry.dispatch(stage, new SpecialRenderEffectContext(lm.camera, frameCounter, lm, lm.graphicsManager));
    }

    public void drawWithRenderOptions(SpriteManager spriteManager, LevelManager.LevelRenderOptions renderOptions) {
        if (lm.level == null) {
            LevelManager.LOGGER.warning("No level loaded to draw.");
            return;
        }
        LevelManager.LevelRenderOptions options = renderOptions != null ? renderOptions : LevelManager.LevelRenderOptions.gameplay();

        // frameCounter is now incremented in update() — see comment there.
        if (lm.animatedPatternManager != null) {
            lm.animatedPatternManager.update();
        }
        if (lm.animatedPaletteManager != null && lm.animatedPaletteManager != lm.animatedPatternManager) {
            lm.animatedPaletteManager.update();
        }
        Camera camera = lm.camera;
        int bgScrollY = (int) (camera.getY() * 0.1f);
        if (lm.game != null) {
            int levelIdx = lm.levels.get(lm.currentZone).get(lm.currentAct).getLevelIndex();
            int[] scroll = lm.game.getBackgroundScroll(levelIdx, camera.getX(), camera.getY());
            bgScrollY = scroll[1];
        }

        lm.parallaxManager.update(lm.currentZone, lm.currentAct, camera, lm.frameCounter, bgScrollY, lm.level);
        resolveAdvancedRenderFrameState(lm.frameCounter);

        // Propagate shake offsets from parallax manager to camera.
        // This allows sprite rendering (via GraphicsManager.flush()) to shake
        // in sync with FG tiles.
        camera.setShakeOffsets(
                lm.parallaxManager.getShakeOffsetX(),
                lm.parallaxManager.getShakeOffsetY());

        List<GLCommand> collisionCommands = lm.debugRenderer != null
                ? lm.debugRenderer.getCollisionCommands() : new ArrayList<>();
        collisionCommands.clear();

        // Update water shader state before rendering level
        updateWaterShaderState(camera);

        // Draw Background (Layer 1)
        PerformanceProfiler profiler = lm.profiler;
        profiler.beginSection("render.bg");
        if (lm.useShaderBackground && lm.graphicsManager.getBackgroundRenderer() != null) {
            renderBackgroundShader(collisionCommands, bgScrollY);
        }
        profiler.endSection("render.bg");

        if (lm.zoneFeatureProvider != null) {
            lm.zoneFeatureProvider.renderAfterBackground(camera, lm.frameCounter);
        }
        dispatchSpecialRenderEffects(SpecialRenderEffectStage.AFTER_BACKGROUND, lm.frameCounter);

        // Draw Foreground (Layer 0) low-priority pass
        profiler.beginSection("render.fg");
        lm.ensureForegroundTilemapData();
        enqueueForegroundTilemapPass(camera, 0);

        // Generate collision debug overlay commands (independent of GPU/CPU path)
        DebugOverlayManager overlayManager = lm.overlayManager;
        if (lm.debugRenderer != null && overlayManager.isEnabled(DebugOverlayToggle.COLLISION_VIEW)) {
            lm.debugRenderer.generateCollisionDebugCommands(collisionCommands, camera, lm::getBlockAtPosition);
        }

        // Render collision debug overlay on top of foreground tiles
        if (!collisionCommands.isEmpty() && overlayManager.isEnabled(DebugOverlayToggle.COLLISION_VIEW)) {
            for (GLCommand cmd : collisionCommands) {
                lm.graphicsManager.registerCommand(cmd);
            }
        }

        // Generate tile priority debug overlay commands (shows high-priority tiles in red)
        if (lm.debugRenderer != null && overlayManager.isEnabled(DebugOverlayToggle.TILE_PRIORITY_VIEW)) {
            List<GLCommand> priorityDebugCommands = lm.debugRenderer.getPriorityDebugCommands();
            priorityDebugCommands.clear();
            lm.debugRenderer.generateTilePriorityDebugCommands(priorityDebugCommands, camera, lm::getBlockAtPosition);

            // Render tile priority debug overlay on top of foreground tiles
            if (!priorityDebugCommands.isEmpty()) {
                for (GLCommand cmd : priorityDebugCommands) {
                    lm.graphicsManager.registerCommand(cmd);
                }
            }
        }

        profiler.endSection("render.fg");

        // Render zone features that should appear as part of foreground layer (before sprites)
        // (e.g., CNZ slot machine display that covers corrupted tiles but sprites render on top)
        if (lm.zoneFeatureProvider != null) {
            lm.zoneFeatureProvider.renderAfterForeground(camera);
        }
        dispatchSpecialRenderEffects(SpecialRenderEffectStage.AFTER_FOREGROUND, lm.frameCounter);

        // Draw Foreground (Layer 0) high-priority pass to tile priority FBO
        // This captures high-priority tile pixels for the sprite priority shader
        profiler.beginSection("render.fg.priority");
        renderHighPriorityTilesToFBO(camera);
        profiler.endSection("render.fg.priority");

        // The HTZ earthquake BG high-priority cave-ceiling overlay used to render
        // here. It now runs as a SpecialRenderEffect at AFTER_FOREGROUND stage
        // (registered by Sonic2ZoneFeatureProvider) and dispatches above with the
        // CNZ slot overlay and other AFTER_FOREGROUND effects.

        // Draw Foreground (Layer 0) high-priority pass to screen
        enqueueForegroundTilemapPass(camera, 1);

        if (options.hasGameplayPass()) {
            if (options.includePlayerSprites()
                    && options.includeObjectSprites()
                    && options.includeRings()) {
                renderSpriteObjectPass(spriteManager, options.includeWaterSurface());
            } else {
                renderSpriteObjectPassFiltered(spriteManager, options);
            }
        }
        if (options.includeObjectArtViewer()) {
            overlayManager.getObjectArtViewer().draw(lm.objectRenderManager, camera);
        }

        // The HCZ2 wall-chase BG high-priority overlay used to render here. It now
        // runs as a SpecialRenderEffect at AFTER_SPRITES stage (registered by
        // Sonic3kZoneFeatureProvider) and is dispatched inside renderSpriteObjectPass
        // alongside other AFTER_SPRITES effects.

        if (!options.hasGameplayPass()) {
            // No sprite/object pass this frame; restore the default shader state for
            // any later screen-space rendering after the level tiles.
            lm.graphicsManager.registerCommand(disableWaterShaderCommand);
        }

        profiler.beginSection("render.hud");
        if (options.includeHud() && lm.hudRenderManager != null && !lm.isHudSuppressed()) {
            AbstractPlayableSprite focusedPlayer = camera.getFocusedSprite();
            lm.hudRenderManager.draw(lm.levelGamestate, focusedPlayer);
        }
        profiler.endSection("render.hud");

        boolean debugViewEnabled = lm.configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
        boolean overlayEnabled = options.includeDebugOverlays()
                && debugViewEnabled
                && overlayManager.isEnabled(DebugOverlayToggle.OVERLAY);
        if (options.includeDebugOverlays() && lm.debugRenderer != null) {
            lm.debugRenderer.renderDebugOverlays(overlayEnabled, lm.objectManager, lm.ringManager,
                    spriteManager, lm.gameModule, lm.configService, lm.frameCounter);
        }
        lm.graphicsManager.enqueueDefaultShaderState();
    }

    private void updateWaterShaderState(Camera camera) {
        int zoneId = lm.getFeatureZoneId();
        int actId = lm.getFeatureActId();
        if (lm.waterSystem.hasWater(zoneId, actId)) {
            // Set uniforms via custom command - this also enables the water shader
            // Use visual water level (with oscillation) for rendering effects
            int waterLevel = lm.waterSystem.getVisualWaterLevelY(zoneId, actId);

            // Determine shimmer style from current game module's physics feature set.
            // 0 = S2/S3K smooth sine wave, 1 = S1 integer-snapped shimmer
            int shimmerStyle = 0;
            PhysicsFeatureSet featureSet = null;
            GameModule currentModule = lm.activeGameModule();
            if (currentModule != null && currentModule.getPhysicsProvider() != null) {
                featureSet = currentModule.getPhysicsProvider().getFeatureSet();
                if (featureSet != null && featureSet.waterShimmerEnabled()) {
                    shimmerStyle = 1;
                }
            }

            // S2/S3K split starts 8px above water level so the surface strip is tinted.
            // S1 uses v_waterpos1 directly as the underwater split (ROM-accurate boundary).
            float waterlineOffset = -8.0f;
            if (featureSet != null && featureSet.waterShimmerEnabled()) {
                waterlineOffset = 0.0f;
            }
            // Zone feature provider can override waterline offset (e.g. zones with
            // ROM-driven water surface rendering that conflicts with the -8 split).
            if (lm.zoneFeatureProvider != null) {
                float zoneOffset = lm.zoneFeatureProvider.getWaterlineOffset(zoneId, actId);
                if (zoneOffset != -8.0f) {
                    waterlineOffset = zoneOffset;
                }
            }
            float waterlineScreenY = (float) (waterLevel - camera.getY() + waterlineOffset);
            currentShimmerStyle = shimmerStyle;

            // Set mutable state for pre-allocated water shader setup command
            pendingWaterlineScreenY = waterlineScreenY;
            pendingWaterShimmerStyle = shimmerStyle;
            pendingSuppressUnderwaterPalette = lm.shouldSuppressUnderwaterPalette(zoneId, actId);
            lm.graphicsManager.registerCommand(waterShaderSetupCommand);
        } else {
            // No water in this zone - disable underwater palette for sprite priority shader
            currentShimmerStyle = 0;
            lm.graphicsManager.setWaterEnabled(false);
        }
        // Note: We don't disable water shader here - that's done later before HUD
        // rendering
    }

    void renderBackgroundShader(List<GLCommand> commands, int bgScrollY) {
        if (lm.level == null || lm.level.getMap() == null)
            return;

        BackgroundRenderer bgRenderer = lm.graphicsManager.getBackgroundRenderer();
        if (bgRenderer == null)
            return;

        Palette.Color backdropColor = lm.resolveLevelBackdropColor();
        bgRenderer.setBackdropColor(
                backdropColor.rFloat(),
                backdropColor.gFloat(),
                backdropColor.bFloat());

        int[] hScrollData = lm.parallaxManager.getHScrollForShader();
        short[] vScrollData = lm.parallaxManager.getVScrollPerLineBGForShader();
        short[] vScrollColumnData = lm.parallaxManager.getVScrollPerColumnBGForShader();

        int bgCameraX = lm.parallaxManager.getBgCameraX();
        boolean mgzStateEightPerLineTilemap = lm.applyBackgroundTilemapWindowSelection(bgCameraX);
        // Side-effect-only: tilemap manager has been refreshed.

        lm.ensureBackgroundTilemapData();

        int bgPeriodWidthPixels = lm.tilemapManager.getBackgroundTilemapWidthTiles() * Pattern.PATTERN_WIDTH;
        // Pass bgTilemapBaseX to the shader so it offsets worldX before wrapping.
        // Shader: fboWorldOffsetX = -ScrollMidpoint - ExtraBuffer
        // We want fboWorldOffsetX = bgTilemapBaseX, so ScrollMidpoint = -bgTilemapBaseX.
        int shaderScrollMidpoint = -lm.tilemapManager.getBgTilemapBaseX();
        int shaderExtraBuffer = 0;
        float bgTilemapWorldOffsetX = 0.0f;
        boolean perLineScrollActive = false;
        float vdpWrapWidthTiles = 0.0f;
        float nametableBaseTile = 0.0f;
        float upperBandWrapHeightPx = 0.0f;
        float upperBandWrapWidthTiles = 0.0f;
        Camera camera = lm.camera;
        if (lm.zoneFeatureProvider != null && lm.zoneFeatureProvider.isIntroOceanPhaseActive(lm.currentZone, lm.currentAct)) {
            // Per-scanline HScroll in the tilemap shader, matching VDP behavior.
            // Each pixel computes worldX = pixelX - hScroll[scanline] directly,
            // then looks up the correct tile from the full-width tilemap.
            bgPeriodWidthPixels = lm.cachedScreenWidth;
            bgTilemapWorldOffsetX = 0;
            shaderScrollMidpoint = 0;
            shaderExtraBuffer = 0;
            perLineScrollActive = true;

            // VDP nametable ring buffer: overflow count tracks how many positions
            // have been overwritten with beach tiles as the camera advances.
            // Ocean phase (introScrollOffset < 0): overflow=0 (all ocean).
            // Camera tracking: overflow gradually increases, revealing beach tiles.
            vdpWrapWidthTiles = 64.0f;
            nametableBaseTile = lm.zoneFeatureProvider.getVdpNametableBase(
                    lm.currentZone, lm.currentAct, camera.getX(), lm.tilemapManager.getBackgroundTilemapWidthTiles());
        } else if (mgzStateEightPerLineTilemap) {
            // MGZ2 state 8 still uses Draw_BG on hardware, but the 64-cell plane is
            // refreshed incrementally as the camera advances. Our rebuild-from-scratch
            // renderer cannot represent that with a single wrapped 512px cache window.
            // Instead, render the full contiguous MGZ BG strip with per-line HScroll
            // applied during the tile pass so clouds and the locked floor band can
            // coexist without cache-window seams.
            bgPeriodWidthPixels = lm.cachedScreenWidth;
            bgTilemapWorldOffsetX = 0;
            shaderScrollMidpoint = 0;
            shaderExtraBuffer = 0;
            perLineScrollActive = true;
            // MGZ2 BG layout rows 0-3 only populate cols 0-7 with the "real"
            // cloud background; rows 4-6 hold the wider fake-floor strip. Wrapping
            // the upper rows inside their populated cloud span avoids exposing empty
            // high-X layout columns while preserving the floor rows below.
            upperBandWrapHeightPx = 4.0f * lm.blockPixelSize;
            upperBandWrapWidthTiles = (8.0f * lm.blockPixelSize) / Pattern.PATTERN_WIDTH;
        }
        // Cap BG period at the scroll handler's required width.
        // Zones with a single BG scroll speed cap at VDP nametable width (512px).
        // Zones with multi-speed parallax (e.g., GHZ) need a wider period to
        // avoid a visible wrap seam where slower and faster layers overlap.
        int bgPeriodCap = lm.parallaxManager.getBgPeriodWidth();
        if (!perLineScrollActive && bgPeriodWidthPixels > bgPeriodCap) {
            bgPeriodWidthPixels = bgPeriodCap;
        }
        int renderWidth = Math.max(lm.cachedScreenWidth, bgPeriodWidthPixels);
        // Add CHUNK_HEIGHT (16px) to cover VScroll range
        // This prevents bottom clipping when VScroll > 0 (max VScroll = 15, max gameY = 223, max fboY = 238 < 272)
        int renderHeight = 256 + LevelConstants.CHUNK_HEIGHT;

        // ROM parity: use the full background plane period and direct wrap sampling.
        // The intro path still uses the same VDP hscroll semantics as normal gameplay.
        // Get pattern renderer's screen height for correct Y coordinate handling
        int screenHeightPixels = lm.cachedScreenHeight;

        // Use zone-specific vertical scroll from parallax manager
        // This ensures zones like MCZ use their act-dependent BG Y calculations
        int actualBgScrollY = lm.parallaxManager.getVscrollFactorBG();

        // 1. Ensure FBO capacity (grow-only, no per-frame reallocation)
        pendingBgRenderWidth = renderWidth;
        pendingBgRenderHeight = renderHeight;
        lm.graphicsManager.registerCommand(bgEnsureCapacityCommand);

        // 2. Begin Tile Pass (Bind FBO)
        // Use water shader in screen-space mode for FBO, with adjusted waterline
        int featureZone = lm.getFeatureZoneId();
        int featureAct = lm.getFeatureActId();
        boolean hasWater = lm.waterSystem.hasWater(featureZone, featureAct);
        boolean suppressUnderwaterPalette = lm.shouldSuppressUnderwaterPalette(featureZone, featureAct);
        // Use visual water level (with oscillation) for background rendering
        int waterLevelWorldY = hasWater ? lm.waterSystem.getVisualWaterLevelY(featureZone, featureAct) : 9999;

        // Calculate chunk-aligned Y for tilemap rendering
        int chunkHeight = LevelConstants.CHUNK_HEIGHT;
        int alignedBgY = (actualBgScrollY / chunkHeight) * chunkHeight;
        if (actualBgScrollY < 0 && actualBgScrollY % chunkHeight != 0) {
            alignedBgY -= chunkHeight; // Handle negative rounding
        }

        // Calculate waterline for FBO - use SCREEN-SPACE waterline PLUS parallax offset
        // The parallax shader shifts the FBO sampling by (actualBgScrollY - alignedBgY)
        // so we must shift the waterline by the same amount to keep it steady on screen
        int vOffset = actualBgScrollY - alignedBgY;
        float fboWaterlineY = (float) ((waterLevelWorldY - camera.getY()) + vOffset);

        // Compute screen-space waterline for BG parallax shimmer
        float bgWaterlineScreenY = (float) (waterLevelWorldY - camera.getY());

        lm.ensureBackgroundTilemapData();
        pendingBgTilePassRenderWidth = renderWidth;
        pendingBgTilePassRenderHeight = renderHeight;
        pendingBgTilePassHasWater = hasWater && !suppressUnderwaterPalette;
        pendingBgTilePassFboWaterlineY = fboWaterlineY;
        pendingBgTilePassAlignedBgY = alignedBgY;
        pendingBgTilePassBgTilemapWorldOffsetX = bgTilemapWorldOffsetX;
        pendingBgTilePassPerLineScroll = perLineScrollActive;
        pendingBgTilePassPerColumnVScroll = vScrollColumnData;
        pendingBgTilePassHScrollData = hScrollData;
        pendingBgTilePassVdpWrapWidth = vdpWrapWidthTiles;
        pendingBgTilePassNametableBase = nametableBaseTile;
        pendingBgTilePassPerLineScrollSampleYOffsetPx = perLineScrollActive ? (float) vOffset : 0.0f;
        pendingBgTilePassUpperBandWrapHeightPx = upperBandWrapHeightPx;
        pendingBgTilePassUpperBandWrapWidthTiles = upperBandWrapWidthTiles;
        lm.graphicsManager.registerCommand(bgTilePassCommand);

        // 5. Set shimmer state on BG renderer for parallax compositing pass
        bgRenderer.setShimmerState(lm.frameCounter, currentShimmerStyle, bgWaterlineScreenY);

        // 6. Render the FBO with Parallax Shader
        if (lm.graphicsManager.getCombinedPaletteTextureId() != null) {
            // Calculate vertical scroll offset (sub-chunk) for shader
            // The FBO is rendered aligned to 16-pixel chunk boundaries
            // The shader needs to shift the view by the remaining offset
            int shaderVOffset = actualBgScrollY % LevelConstants.CHUNK_HEIGHT;
            if (shaderVOffset < 0)
                shaderVOffset += LevelConstants.CHUNK_HEIGHT; // Handle negative modulo

            pendingBgHScrollData = hScrollData;
            pendingBgVScrollData = vScrollData;
            pendingBgVScrollColumnData = vScrollColumnData;
            pendingBgShaderScrollMidpoint = shaderScrollMidpoint;
            pendingBgShaderExtraBuffer = shaderExtraBuffer;
            pendingBgVOffset = shaderVOffset;
            pendingBgPerLineScroll = perLineScrollActive;
            lm.graphicsManager.registerCommand(bgRenderWithScrollCommand);
        }
    }

    /**
     * Renders the shared sprite/object gameplay pass used after tile rendering.
     * This can also be called separately after a full-screen fade to keep
     * sprites/objects visible while the level tiles remain hidden.
     */
    public void renderSpriteObjectPass(SpriteManager spriteManager, boolean includeWaterSurface) {
        // Render ALL sprites in unified bucket order (7→0)
        // Sprite-to-sprite ordering is by bucket number regardless of isHighPriority
        // The sprite priority shader composites sprites with tile priority awareness
        PerformanceProfiler profiler = lm.profiler;
        profiler.beginSection("render.sprites");

        // Priority membership is mutable at runtime (plane switchers, hurt/death,
        // zone event overrides, follower objects mirroring player priority).
        // Rebuild buckets from live state right before drawing the unified pass.
        if (spriteManager != null) {
            spriteManager.invalidateRenderBuckets();
        }
        ObjectManager objectManager = lm.objectManager;
        RingManager ringManager = lm.ringManager;
        GraphicsManager graphicsManager = lm.graphicsManager;
        ZoneFeatureProvider zoneFeatureProvider = lm.zoneFeatureProvider;
        if (objectManager != null) {
            objectManager.invalidateRenderBuckets();
        }

        graphicsManager.setUseSpritePriorityShader(true);
        graphicsManager.setCurrentSpriteHighPriority(false);
        graphicsManager.beginPatternBatch();

        if (ringManager != null) {
            ringManager.draw(lm.frameCounter);
            // ROM: Lost rings (Ring_LostRing) use art_tile with priority bit set,
            // rendering them in front of both playfield layers (including waterfalls).
            graphicsManager.setCurrentSpriteHighPriority(true);
            ringManager.drawLostRings(lm.frameCounter);
            graphicsManager.setCurrentSpriteHighPriority(false);
        }

        boolean bonusStageSpriteSatOrdering = zoneFeatureProvider != null
                && zoneFeatureProvider.useSpriteSatMasking(lm.currentZone);
        boolean useSpriteSatMasking = bonusStageSpriteSatOrdering;
        if (useSpriteSatMasking) {
            graphicsManager.beginSpriteSatCollection();
            // SAT collection must follow sprite-table order, not painter order.
            // Draw_Sprite inserts into Sprite_table_input by ascending priority bucket,
            // and lower sprite slots end up in front later during rasterization.
            // In the Gumball stage the playable sprites must still come after same-bucket
            // machine objects so Sonic/sidekicks remain on top within bucket 2.
            for (int bucket = RenderPriority.MIN; bucket <= RenderPriority.MAX; bucket++) {
                graphicsManager.setCurrentSpriteSatBucket(bucket);
                if (objectManager != null) {
                    objectManager.drawUnifiedBucketWithPriority(bucket, graphicsManager);
                }
                if (spriteManager != null) {
                    spriteManager.drawUnifiedBucketWithPriority(bucket, graphicsManager);
                }
            }
            graphicsManager.endSpriteSatCollectionAndReplay();
        } else {
            for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
                if (bonusStageSpriteSatOrdering) {
                    // In the gumball bonus stage, the player and bonus-stage objects share
                    // the same priority buckets. Draw objects first so lower-slot player
                    // sprites remain on top within a shared bucket.
                    if (objectManager != null) {
                        objectManager.drawUnifiedBucketWithPriority(bucket, graphicsManager);
                    }
                    if (spriteManager != null) {
                        spriteManager.drawUnifiedBucketWithPriority(bucket, graphicsManager);
                    }
                } else {
                    if (spriteManager != null) {
                        int layerBucket = bucket;
                        spriteManager.drawUnifiedBucketWithPriority(
                                bucket,
                                graphicsManager,
                                () -> renderTraceGhostsForLayer(layerBucket, false),
                                () -> renderTraceGhostsForLayer(layerBucket, true));
                    }
                    if (objectManager != null) {
                        objectManager.drawUnifiedBucketWithPriority(bucket, graphicsManager);
                    }
                }
            }
        }
        graphicsManager.flushPatternBatch();
        graphicsManager.setUseSpritePriorityShader(false);
        profiler.endSection("render.sprites");

        if (includeWaterSurface) {
            graphicsManager.registerCommand(disableShimmerCommand);
        }
        if (zoneFeatureProvider != null) {
            zoneFeatureProvider.render(lm.camera, lm.frameCounter);
        }
        dispatchSpecialRenderEffects(SpecialRenderEffectStage.AFTER_SPRITES, lm.frameCounter);

        // Revert to default shader for any following HUD/debug/screen-space rendering.
        graphicsManager.registerCommand(disableWaterShaderCommand);
    }

    private void renderTraceGhostsForLayer(int bucket, boolean highPriority) {
        TraceSessionLauncher traceSession = TraceSessionLauncher.active();
        if (traceSession != null) {
            traceSession.renderGhostsForLayer(bucket, highPriority);
        }
    }

    private void renderSpriteObjectPassFiltered(SpriteManager spriteManager, LevelManager.LevelRenderOptions options) {
        PerformanceProfiler profiler = lm.profiler;
        ObjectManager objectManager = lm.objectManager;
        RingManager ringManager = lm.ringManager;
        GraphicsManager graphicsManager = lm.graphicsManager;
        ZoneFeatureProvider zoneFeatureProvider = lm.zoneFeatureProvider;
        profiler.beginSection("render.sprites");

        if (spriteManager != null && options.includePlayerSprites()) {
            spriteManager.invalidateRenderBuckets();
        }
        if (objectManager != null && options.includeObjectSprites()) {
            objectManager.invalidateRenderBuckets();
        }

        graphicsManager.setUseSpritePriorityShader(true);
        graphicsManager.setCurrentSpriteHighPriority(false);
        graphicsManager.beginPatternBatch();

        if (ringManager != null && options.includeRings()) {
            ringManager.draw(lm.frameCounter);
            graphicsManager.setCurrentSpriteHighPriority(true);
            ringManager.drawLostRings(lm.frameCounter);
            graphicsManager.setCurrentSpriteHighPriority(false);
        }

        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            if (spriteManager != null && options.includePlayerSprites()) {
                spriteManager.drawUnifiedBucketWithPriority(bucket, graphicsManager);
            }
            if (objectManager != null && options.includeObjectSprites()) {
                objectManager.drawUnifiedBucketWithPriority(bucket, graphicsManager);
            }
        }

        graphicsManager.flushPatternBatch();
        graphicsManager.setUseSpritePriorityShader(false);
        profiler.endSection("render.sprites");

        if (options.includeWaterSurface()) {
            graphicsManager.registerCommand(disableShimmerCommand);
        }
        if (zoneFeatureProvider != null) {
            zoneFeatureProvider.render(lm.camera, lm.frameCounter);
        }
        graphicsManager.registerCommand(disableWaterShaderCommand);
    }

    /**
     * Renders the DEZ background during the ending cutscene (1-arg form).
     */
    public void renderEndingBackground(int bgVscroll) {
        renderEndingBackground(bgVscroll, null);
    }

    /**
     * Renders the DEZ star field background for the ending cutscene, with an
     * optional backdrop color override.
     */
    public void renderEndingBackground(int bgVscroll, float[] backdropOverride) {
        if (lm.level == null || lm.level.getMap() == null) {
            return;
        }
        if (!lm.useShaderBackground || lm.graphicsManager.getBackgroundRenderer() == null) {
            return;
        }

        // Update parallax with camera=(0,0) and the ending's BG vscroll
        // This drives SwScrlDez TempArray accumulation for star parallax
        lm.frameCounter++;
        lm.parallaxManager.updateForEnding(lm.currentZone, lm.currentAct, lm.frameCounter, bgVscroll);

        // Force background tilemap FBO re-render every frame during the ending.
        // The cutscene fades palette lines 2-3 from white → sky colors; the tilemap
        // FBO bakes palette colors at render time, so it must be rebuilt each frame
        // to reflect the evolving palette state. Without this, the DEZ star field
        // appears at full color instantly instead of fading in with the palette.
        if (lm.tilemapManager != null) {
            lm.tilemapManager.setBackgroundTilemapDirty(true);
        }

        // Render using the existing shader pipeline
        List<GLCommand> endingCollisionCommands = lm.debugRenderer != null
                ? lm.debugRenderer.getCollisionCommands() : new ArrayList<>();
        renderBackgroundShader(endingCollisionCommands, bgVscroll);

        // Override backdrop color for ending cutscene palette fade.
        // The deferred commands read bgRenderer fields at execution time, so
        // setting the backdrop AFTER renderBackgroundShader but BEFORE flush()
        // ensures the override takes effect.
        if (backdropOverride != null && backdropOverride.length >= 3) {
            BackgroundRenderer bgRenderer = lm.graphicsManager.getBackgroundRenderer();
            if (bgRenderer != null) {
                bgRenderer.setBackdropColor(
                        backdropOverride[0], backdropOverride[1], backdropOverride[2]);
            }
        }
    }

    private void enqueueForegroundTilemapPass(Camera camera, int priorityPass) {
        TilemapGpuRenderer renderer = lm.graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }

        int featureZone = lm.getFeatureZoneId();
        int featureAct = lm.getFeatureActId();
        boolean hasWater = lm.waterSystem.hasWater(featureZone, featureAct);
        boolean suppressUnderwaterPalette = lm.shouldSuppressUnderwaterPalette(featureZone, featureAct);
        int waterLevel = hasWater ? lm.waterSystem.getVisualWaterLevelY(featureZone, featureAct) : 0;

        int screenW = lm.cachedScreenWidth;
        int screenH = lm.cachedScreenHeight;
        // FG tile world offsets.
        // Y: vscrollFactorFG already includes scroll-handler shake (HTZ earthquake,
        //    HCZ2 wall push, MCZ boss, etc.).  Do NOT add getShakeOffsetY() again
        //    — that caused double-amplitude shake on FG tiles.
        float worldOffsetX = camera.getXWithShake();
        float worldOffsetY = lm.parallaxManager.getVscrollFactorFG();
        // Waterline tracks the same Y offset as tile rendering
        float waterlineScreenY = (float) (waterLevel - worldOffsetY);

        Integer atlasId = lm.graphicsManager.getPatternAtlasTextureId();
        Integer paletteId = lm.graphicsManager.getCombinedPaletteTextureId();
        Integer underwaterPaletteId = lm.graphicsManager.getUnderwaterPaletteTextureId();
        boolean useUnderwaterPalette = hasWater && !suppressUnderwaterPalette && underwaterPaletteId != null;

        if (atlasId == null || paletteId == null) {
            return;
        }

        // Use separate pre-allocated commands for low (0) and high (1) priority passes
        // since both are registered in the same frame
        if (priorityPass == 0) {
            pendingFgWorldOffsetX_low = worldOffsetX;
            pendingFgWorldOffsetY_low = worldOffsetY;
            pendingFgScreenW_low = screenW;
            pendingFgScreenH_low = screenH;
            pendingFgPriorityPass_low = priorityPass;
            pendingFgUseUnderwater_low = useUnderwaterPalette;
            pendingFgWaterlineScreenY_low = waterlineScreenY;
            pendingFgAtlasId_low = atlasId;
            pendingFgPaletteId_low = paletteId;
            pendingFgUnderwaterPaletteId_low = underwaterPaletteId;
            lm.graphicsManager.registerCommand(fgTilemapPassLowCommand);
        } else {
            pendingFgWorldOffsetX_high = worldOffsetX;
            pendingFgWorldOffsetY_high = worldOffsetY;
            pendingFgScreenW_high = screenW;
            pendingFgScreenH_high = screenH;
            pendingFgPriorityPass_high = priorityPass;
            pendingFgUseUnderwater_high = useUnderwaterPalette;
            pendingFgWaterlineScreenY_high = waterlineScreenY;
            pendingFgAtlasId_high = atlasId;
            pendingFgPaletteId_high = paletteId;
            pendingFgUnderwaterPaletteId_high = underwaterPaletteId;
            lm.graphicsManager.registerCommand(fgTilemapPassHighCommand);
        }
    }

    /**
     * Render high-priority foreground tiles to the tile priority FBO.
     * This FBO is sampled by the sprite priority shader to determine
     * if low-priority sprites should be hidden behind high-priority tiles.
     */
    private void renderHighPriorityTilesToFBO(Camera camera) {
        TilePriorityFBO fbo = lm.graphicsManager.getTilePriorityFBO(lm.cachedScreenWidth, lm.cachedScreenHeight);
        if (fbo == null || !fbo.isInitialized()) {
            return;
        }

        TilemapGpuRenderer renderer = lm.graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }

        Integer atlasId = lm.graphicsManager.getPatternAtlasTextureId();
        Integer paletteId = lm.graphicsManager.getCombinedPaletteTextureId();
        if (atlasId == null || paletteId == null) {
            return;
        }

        int screenW = lm.cachedScreenWidth;
        int screenH = lm.cachedScreenHeight;
        float fgWorldOffsetX = camera.getXWithShake();
        float fgWorldOffsetY = camera.getYWithShake();

        pendingFboScreenW = screenW;
        pendingFboScreenH = screenH;
        pendingFboFgWorldOffsetX = fgWorldOffsetX;
        pendingFboFgWorldOffsetY = fgWorldOffsetY;
        pendingFboAtlasId = atlasId;
        pendingFboPaletteId = paletteId;
        lm.graphicsManager.registerCommand(highPriorityFboCommand);
    }
}
