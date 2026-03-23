package com.openggf.level;

import com.openggf.game.*;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic2.Sonic2Level;
import com.openggf.game.sonic3k.events.S3kSeamlessMutationExecutor;

import com.openggf.Engine;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Game;
import com.openggf.data.AnimatedPaletteProvider;
import com.openggf.data.AnimatedPatternProvider;
import com.openggf.data.PlayerSpriteArtProvider;
import com.openggf.data.SpindashDustArtProvider;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.PhysicsFeatureSet;
import com.openggf.game.DynamicStartPositionProvider;

import com.openggf.debug.DebugObjectArtViewer;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.debug.DebugOverlayToggle;
import com.openggf.debug.PerformanceProfiler;
import com.openggf.level.objects.HudRenderManager;
import com.openggf.graphics.GLCommand;
import com.openggf.audio.AudioManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.TilemapGpuRenderer;
import com.openggf.graphics.TilePriorityFBO;
import com.openggf.graphics.WaterShaderProgram;
import com.openggf.graphics.RenderPriority;
import com.openggf.graphics.PatternRenderCommand;
import com.openggf.graphics.RenderContext;
import com.openggf.level.render.BackgroundRenderer;
import com.openggf.level.objects.ObjectManager;
import com.openggf.level.objects.ObjectRenderManager;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.level.rings.RingManager;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.level.animation.AnimatedPaletteManager;
import com.openggf.level.animation.AnimatedPatternManager;
import com.openggf.physics.CollisionSystem;
import com.openggf.physics.Direction;
import com.openggf.sprites.Sprite;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.PowerUpObject;
import com.openggf.level.objects.DefaultPowerUpSpawner;
import com.openggf.game.sonic3k.Sonic3kPlayerArt;
import com.openggf.sprites.managers.SpindashDustController;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.managers.TailsTailsController;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.Tails;
import com.openggf.sprites.render.PlayerSpriteRenderer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.SEVERE;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;

/**
 * Manages the loading and rendering of game levels.
 */
public class LevelManager {
    private static final Logger LOGGER = Logger.getLogger(LevelManager.class.getName());
    private static final int OBJECT_PATTERN_BASE = 0x20000;
    private static final int HUD_PATTERN_BASE = 0x28000;
    /** Base for extra sidekick DPLC banks — above water (0x30000) and below title cards (0x40000). */
    private static final int SIDEKICK_PATTERN_BASE = 0x38000;
    private static final Palette.Color BLACK_BACKDROP = new Palette.Color((byte) 0, (byte) 0, (byte) 0);
    private static LevelManager levelManager;
    private Level level;
    private int blockPixelSize = 128;  // cached from level
    private int chunksPerBlockSide = 8;
    // Cached level pixel dimensions (immutable once level loads).
    // Avoids repeated getLayerWidthBlocks()*blockPixelSize in hot-path collision lookups.
    private int cachedFgWidthPx;
    private int cachedFgHeightPx;
    private int cachedBgWidthPx;           // Full map width for BG layer (used for block lookups)
    private int cachedBgContiguousWidthPx; // Contiguous BG data width from column 0 (for bgTilemapBaseX wrapping)
    private int cachedBgHeightPx;
    private Game game;
    private GameModule gameModule;

    public Game getGame() {
        return game;
    }

    public GameModule getGameModule() {
        return gameModule;
    }

    /** Returns the tilemap lifecycle delegate. */
    public LevelTilemapManager getTilemapManager() {
        return tilemapManager;
    }

    private final GraphicsManager graphicsManager = GraphicsManager.getInstance();
    private final SpriteManager spriteManager = SpriteManager.getInstance();
    private final SonicConfigurationService configService = SonicConfigurationService.getInstance();
    private final DebugOverlayManager overlayManager = GameServices.debugOverlay();
    private LevelDebugRenderer debugRenderer;
    private final PerformanceProfiler profiler = PerformanceProfiler.getInstance();
    private final List<List<LevelData>> levels = new ArrayList<>();
    private int currentAct = 0;
    private int currentZone = 0;
    private int frameCounter = 0;
    private int currentShimmerStyle = 0;
    private ObjectManager objectManager;
    private RingManager ringManager;
    private ZoneFeatureProvider zoneFeatureProvider;
    private TouchResponseTable touchResponseTable;
    private ObjectRenderManager objectRenderManager;
    private HudRenderManager hudRenderManager;
    private AnimatedPatternManager animatedPatternManager;
    private AnimatedPaletteManager animatedPaletteManager;
    private RespawnState checkpointState;
    private LevelState levelGamestate;

    // GPU tilemap lifecycle delegate (build/cache/upload/invalidate)
    private LevelTilemapManager tilemapManager;

    // All transition request/consume state lives in the coordinator
    private final LevelTransitionCoordinator transitions = new LevelTransitionCoordinator();

    // ROM: LZ3/SBZ2 vertical wrapping — FG layer wraps Y instead of clamping
    private boolean verticalWrapEnabled = false;

    // Background rendering support
    private final ParallaxManager parallaxManager = ParallaxManager.getInstance();
    private boolean useShaderBackground = true; // Feature flag for shader background


    // Cached screen dimensions (avoids repeated config service lookups)
    private final int cachedScreenWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
    private final int cachedScreenHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);

    // Camera reference for frustum culling
    private final Camera camera = Camera.getInstance();

    // Pre-allocated viewport buffer to avoid per-frame int[4] allocations inside GL commands
    private final int[] viewportBuffer = new int[4];

    // Pre-allocated GLCommand objects to avoid per-frame lambda/command allocations.
    // These are safe to reuse because the command list is cleared each frame in flushWithCamera().

    // Disable shimmer distortion for water surface sprites (no per-frame captures)
    private final GLCommand disableShimmerCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        WaterShaderProgram waterShader = graphicsManager.getWaterShaderProgram();
        if (waterShader != null) {
            waterShader.use();
            waterShader.setShimmerStyle(0);
        }
        WaterShaderProgram instancedWaterShader = graphicsManager.getInstancedWaterShaderProgram();
        if (instancedWaterShader != null) {
            instancedWaterShader.use();
            instancedWaterShader.setShimmerStyle(0);
        }
        if (waterShader != null) {
            waterShader.use();
        }
        PatternRenderCommand.resetFrameState();
    });

    // Revert to default shader for HUD rendering (no per-frame captures)
    private final GLCommand disableWaterShaderCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        graphicsManager.setUseWaterShader(false);
        PatternRenderCommand.resetFrameState();
    });

    // Mutable state for pre-allocated water shader setup command
    private float pendingWaterlineScreenY;
    private int pendingWaterShimmerStyle;
    private final GLCommand waterShaderSetupCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        graphicsManager.setUseWaterShader(true);

        WaterShaderProgram shader = graphicsManager.getWaterShaderProgram();
        shader.use();

        glGetIntegerv(GL_VIEWPORT, viewportBuffer);
        float windowHeight = (float) viewportBuffer[3];
        float screenHeightPixels = (float) configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);

        shader.setWindowHeight(windowHeight);
        shader.setWaterlineScreenY(pendingWaterlineScreenY);
        shader.setFrameCounter(frameCounter);
        shader.setDistortionAmplitude(0.0f);
        shader.setShimmerStyle(pendingWaterShimmerStyle);
        shader.setIndexedTextureWidth(graphicsManager.getPatternAtlasWidth());
        shader.setScreenDimensions((float) configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS),
                screenHeightPixels);

        graphicsManager.setWaterEnabled(true);
        graphicsManager.setWaterlineScreenY(pendingWaterlineScreenY);
        graphicsManager.setWindowHeight(windowHeight);
        graphicsManager.setScreenHeight(screenHeightPixels);

        WaterSystem waterSystem = WaterSystem.getInstance();
        int zoneId = getFeatureZoneId();
        Palette[] underwater = waterSystem.getUnderwaterPalette(zoneId, currentAct);
        if (underwater != null) {
            Palette normalLine0 = (level != null) ? level.getPalette(0) : null;
            graphicsManager.cacheUnderwaterPaletteTexture(underwater, normalLine0);
            Integer texId = graphicsManager.getUnderwaterPaletteTextureId();
            int loc = shader.getUnderwaterPaletteLocation();

            if (texId != null && loc != -1) {
                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, texId);
                glUniform1i(loc, 2);
                glActiveTexture(GL_TEXTURE0);
            }
        }

        TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
        if (tilemapRenderer != null) {
            tilemapRenderer.setShimmerState(frameCounter, pendingWaterShimmerStyle);
        }

        WaterShaderProgram instancedShader = graphicsManager.getInstancedWaterShaderProgram();
        if (instancedShader != null) {
            instancedShader.use();
            instancedShader.cacheUniformLocations();
            instancedShader.setWindowHeight(windowHeight);
            instancedShader.setWaterlineScreenY(pendingWaterlineScreenY);
            instancedShader.setFrameCounter(frameCounter);
            instancedShader.setDistortionAmplitude(0.0f);
            instancedShader.setShimmerStyle(pendingWaterShimmerStyle);
            instancedShader.setIndexedTextureWidth(graphicsManager.getPatternAtlasWidth());
            instancedShader.setScreenDimensions((float) configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS),
                    (float) configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS));

            Palette[] underwaterInstanced = waterSystem.getUnderwaterPalette(zoneId, currentAct);
            if (underwaterInstanced != null) {
                Palette normalLine0Instanced = (level != null) ? level.getPalette(0) : null;
                graphicsManager.cacheUnderwaterPaletteTexture(underwaterInstanced, normalLine0Instanced);
                Integer texId = graphicsManager.getUnderwaterPaletteTextureId();
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

    // Mutable state for pre-allocated BG ensureCapacity command
    private int pendingBgRenderWidth;
    private int pendingBgRenderHeight;
    private final GLCommand bgEnsureCapacityCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        BackgroundRenderer bgRenderer = graphicsManager.getBackgroundRenderer();
        if (bgRenderer != null) {
            bgRenderer.ensureCapacity(pendingBgRenderWidth, pendingBgRenderHeight);
        }
    });

    // Mutable state for pre-allocated BG renderWithScrollWide command
    private int[] pendingBgHScrollData;
    private short[] pendingBgVScrollData;
    private short[] pendingBgVScrollColumnData;
    private int pendingBgShaderScrollMidpoint;
    private int pendingBgShaderExtraBuffer;
    private int pendingBgVOffset;
    private boolean pendingBgPerLineScroll;
    private final GLCommand bgRenderWithScrollCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        BackgroundRenderer bgRenderer = graphicsManager.getBackgroundRenderer();
        if (bgRenderer != null) {
            bgRenderer.renderWithScrollWide(pendingBgHScrollData, pendingBgVScrollData, pendingBgVScrollColumnData,
                    pendingBgShaderScrollMidpoint, pendingBgShaderExtraBuffer,
                    pendingBgVOffset, pendingBgPerLineScroll);
        }
    });

    // Mutable state for pre-allocated FG tilemap pass commands (two instances needed: low + high priority)
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
    private final GLCommand fgTilemapPassLowCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
        if (tilemapRenderer == null) {
            return;
        }
        applyForegroundHeatHaze(tilemapRenderer);
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
                graphicsManager.getPatternAtlasWidth(),
                graphicsManager.getPatternAtlasHeight(),
                pendingFgAtlasId_low,
                pendingFgPaletteId_low,
                pendingFgUnderwaterPaletteId_low != null ? pendingFgUnderwaterPaletteId_low : 0,
                pendingFgPriorityPass_low,
                verticalWrapEnabled,
                false,
                pendingFgUseUnderwater_low,
                pendingFgWaterlineScreenY_low);
    });

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
    private final GLCommand fgTilemapPassHighCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
        if (tilemapRenderer == null) {
            return;
        }
        applyForegroundHeatHaze(tilemapRenderer);
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
                graphicsManager.getPatternAtlasWidth(),
                graphicsManager.getPatternAtlasHeight(),
                pendingFgAtlasId_high,
                pendingFgPaletteId_high,
                pendingFgUnderwaterPaletteId_high != null ? pendingFgUnderwaterPaletteId_high : 0,
                pendingFgPriorityPass_high,
                verticalWrapEnabled,
                false,
                pendingFgUseUnderwater_high,
                pendingFgWaterlineScreenY_high);
    });

    // Mutable state for pre-allocated high-priority FBO command
    private int pendingFboScreenW;
    private int pendingFboScreenH;
    private float pendingFboFgWorldOffsetX;
    private float pendingFboFgWorldOffsetY;
    private Integer pendingFboAtlasId;
    private Integer pendingFboPaletteId;
    private final GLCommand highPriorityFboCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        TilePriorityFBO tileFbo = graphicsManager.getTilePriorityFBO();
        TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
        if (tileFbo == null || tilemapRenderer == null) {
            return;
        }

        tileFbo.begin();

        glEnable(GL_BLEND);
        glBlendEquation(GL_MAX);
        glBlendFunc(GL_ONE, GL_ONE);

        applyForegroundHeatHaze(tilemapRenderer);
        tilemapRenderer.render(
                TilemapGpuRenderer.Layer.FOREGROUND,
                pendingFboScreenW,
                pendingFboScreenH,
                0, 0, pendingFboScreenW, pendingFboScreenH,
                pendingFboFgWorldOffsetX,
                pendingFboFgWorldOffsetY,
                graphicsManager.getPatternAtlasWidth(),
                graphicsManager.getPatternAtlasHeight(),
                pendingFboAtlasId,
                pendingFboPaletteId,
                0, 1, verticalWrapEnabled, true, false, 0.0f);

        glBlendEquation(GL_FUNC_ADD);
        glDisable(GL_BLEND);

        tileFbo.end();
    });

    // Mutable state for pre-allocated BG tile pass command
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
    private final GLCommand bgTilePassCommand = new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
        BackgroundRenderer bgRenderer = graphicsManager.getBackgroundRenderer();
        if (bgRenderer == null) {
            return;
        }
        bgRenderer.beginTilePass(pendingBgTilePassRenderWidth, pendingBgTilePassRenderHeight, true);
        TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
        if (tilemapRenderer != null) {
            int savedShimmerStyle = tilemapRenderer.getShimmerStyle();
            tilemapRenderer.setShimmerState(frameCounter, 0);

            Integer atlasId = graphicsManager.getPatternAtlasTextureId();
            Integer paletteId = graphicsManager.getCombinedPaletteTextureId();
            Integer underwaterPaletteId = graphicsManager.getUnderwaterPaletteTextureId();
            boolean useUnderwaterPalette = pendingBgTilePassHasWater && underwaterPaletteId != null;
            if (atlasId != null && paletteId != null) {
                if (pendingBgTilePassPerLineScroll) {
                    bgRenderer.uploadHScroll(pendingBgTilePassHScrollData);
                    tilemapRenderer.enablePerLineScroll(
                            bgRenderer.getHScrollTextureId(), 224.0f,
                            pendingBgTilePassVdpWrapWidth, pendingBgTilePassNametableBase);
                }
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
                        graphicsManager.getPatternAtlasWidth(),
                        graphicsManager.getPatternAtlasHeight(),
                        atlasId,
                        paletteId,
                        underwaterPaletteId != null ? underwaterPaletteId : 0,
                        -1,
                        true,
                        false,
                        useUnderwaterPalette,
                        pendingBgTilePassFboWaterlineY);
            }

            tilemapRenderer.setShimmerState(frameCounter, savedShimmerStyle);
        }

        bgRenderer.endTilePass();
        graphicsManager.setUseUnderwaterPaletteForBackground(false);
    });

    private void applyForegroundHeatHaze(TilemapGpuRenderer tilemapRenderer) {
        if (zoneFeatureProvider != null
                && zoneFeatureProvider.shouldEnableForegroundHeatHaze(getFeatureZoneId(), getFeatureActId(), camera.getX())) {
            tilemapRenderer.enablePerLineForegroundScroll(parallaxManager.getHScrollForShader());
        }
    }

    /**
     * Private constructor for Singleton pattern.
     * Zone list is lazily initialized from the current GameModule's ZoneRegistry.
     */
    protected LevelManager() {
        // Zones are loaded from ZoneRegistry in refreshZoneList()
    }

    /**
     * Refreshes the zone list from the current GameModule's ZoneRegistry.
     * Called during level loading to ensure zones match the current game.
     */
    private void refreshZoneList() {
        levels.clear();
        levels.addAll(gameModule.getZoneRegistry().getAllZones());
    }

    /**
     * Loads the specified level into memory.
     *
     * @param levelIndex the index of the level to load
     * @throws IOException if an I/O error occurs while loading the level
     */
    public void loadLevel(int levelIndex) throws IOException {
        loadLevel(levelIndex, LevelLoadMode.FULL);
    }

    /**
     * Loads the specified level into memory with explicit load mode.
     *
     * @param levelIndex the index of the level to load
     * @param loadMode   profile execution mode
     * @throws IOException if an I/O error occurs while loading the level
     */
    public void loadLevel(int levelIndex, LevelLoadMode loadMode) throws IOException {
        LevelLoadContext ctx = new LevelLoadContext();
        ctx.setLevelIndex(levelIndex);
        ctx.setLoadMode(loadMode);
        loadLevel(levelIndex, loadMode, ctx);
    }

    /**
     * Loads the specified level into memory with explicit load mode and context.
     * <p>
     * When the context has {@code includePostLoadAssembly} set, the profile will
     * include post-load steps (checkpoint restore, player spawn, camera, etc.).
     *
     * @param levelIndex the index of the level to load
     * @param loadMode   profile execution mode
     * @param ctx        pre-built context with checkpoint snapshot and spawn data
     * @throws IOException if an I/O error occurs while loading the level
     */
    public void loadLevel(int levelIndex, LevelLoadMode loadMode, LevelLoadContext ctx) throws IOException {
        try {
            LevelInitProfile profile = GameModuleRegistry.getCurrent().getLevelInitProfile();
            ctx.setLevelIndex(levelIndex);
            ctx.setLoadMode(loadMode);

            List<InitStep> steps = profile.levelLoadSteps(ctx);
            if (steps.isEmpty()) {
                throw new IllegalStateException(
                    "No level load steps defined for " +
                    GameModuleRegistry.getCurrent().getClass().getSimpleName() +
                    ". All game modules must implement levelLoadSteps().");
            }
            for (InitStep step : steps) {
                long start = System.nanoTime();
                step.execute();
                long elapsed = (System.nanoTime() - start) / 1_000_000;
                LOGGER.fine(() -> String.format("  [%s] %dms — %s", step.name(), elapsed, step.romRoutine()));
            }
            // The LoadLevelData step stores the result in ctx
            if (ctx.getLevel() != null) {
                level = ctx.getLevel();
            }
        } catch (Exception e) {
            // Profile steps wrap checked exceptions in RuntimeException; unwrap if cause is IOException
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioe) {
                LOGGER.log(SEVERE, "Failed to load level " + levelIndex, ioe);
                throw ioe;
            }
            LOGGER.log(SEVERE, "Unexpected error while loading level " + levelIndex, e);
            throw new IOException("Failed to load level due to unexpected error.", e);
        }
    }

    /**
     * Phase A: Initialize ROM access, parallax, game module, and zone registry.
     */
    public void initGameModule(int levelIndex) throws IOException {
        Rom rom = GameServices.rom().getRom();
        parallaxManager.load(rom);
        gameModule = GameModuleRegistry.getCurrent();
        refreshZoneList();
        game = gameModule.createGame(rom);
    }

    /**
     * Phase C/F: Configure audio manager and play level music.
     */
    public void initAudio(int levelIndex) throws IOException {
        AudioManager audioManager = AudioManager.getInstance();
        audioManager.setAudioProfile(gameModule.getAudioProfile());
        audioManager.setRom(GameServices.rom().getRom());
        audioManager.setSoundMap(game.getSoundMap());
        audioManager.resetRingSound();
        if (!transitions.isSuppressNextMusicChange()) {
            audioManager.playMusic(game.getMusicId(levelIndex));
        }
        transitions.setSuppressNextMusicChange(false);
    }

    /**
     * Phase A-C: Initialize game module, configure audio manager, and play level music.
     */
    public void initGameModuleAndAudio(int levelIndex) throws IOException {
        initGameModule(levelIndex);
        initAudio(levelIndex);
    }

    /**
     * Phase E-F: Delegate to Game.loadLevel(), cache level dimensions, and reset dirty flags.
     *
     * @return the loaded Level instance (also assigned to {@code this.level})
     */
    public Level loadLevelData(int levelIndex) throws IOException {
        Level loaded = game.loadLevel(levelIndex);
        level = loaded;
        blockPixelSize = level.getBlockPixelSize();
        chunksPerBlockSide = level.getChunksPerBlockSide();
        debugRenderer = new LevelDebugRenderer(new LevelDebugContext(
                level, blockPixelSize, overlayManager, graphicsManager,
                cachedScreenWidth, cachedScreenHeight));
        cacheLevelDimensions();
        tilemapManager = new LevelTilemapManager(buildGeometry(), graphicsManager);
        return loaded;
    }

    /**
     * Phase E: Initialize animated pattern and palette managers for the loaded level.
     */
    public void initAnimatedContent() {
        initAnimatedPatterns();
        initAnimatedPalettes();
    }

    /**
     * Phase G: Create ObjectManager, TouchResponseTable, and wire CollisionSystem.
     */
    public void initObjectManager() throws IOException {
        Rom rom = GameServices.rom().getRom();
        RomByteReader romReader = RomByteReader.fromRom(rom);
        touchResponseTable = gameModule.createTouchResponseTable(romReader);
        objectManager = new ObjectManager(level.getObjects(),
                gameModule.createObjectRegistry(),
                gameModule.getPlaneSwitcherObjectId(),
                gameModule.getPlaneSwitcherConfig(),
                touchResponseTable);
        // Wire up CollisionSystem with ObjectManager for unified collision pipeline
        CollisionSystem.getInstance().setObjectManager(objectManager);

        // Inject PowerUpSpawner into all playable sprites
        injectPowerUpSpawner();
    }

    /**
     * Injects a {@link DefaultPowerUpSpawner} backed by the current
     * {@link ObjectManager} into the main player and all sidekicks.
     */
    private void injectPowerUpSpawner() {
        DefaultPowerUpSpawner spawner = new DefaultPowerUpSpawner(objectManager);
        Sprite player = spriteManager.getSprite(
                configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        if (player instanceof AbstractPlayableSprite playable) {
            playable.setPowerUpSpawner(spawner);
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            sidekick.setPowerUpSpawner(spawner);
        }
    }

    /**
     * Phase G: Reset camera bounds and initialize object placement window.
     */
    public void initCameraBounds() {
        // Reset camera state from previous level (signpost may have locked it)
        Camera camera = Camera.getInstance();
        camera.setFrozen(false);
        // ROM: LevelSizeLoad sets v_limitleft2 and v_limitright2 from LevelSizeArray.
        // Use the level's ROM boundaries (not map pixel width) so the camera is
        // constrained to the same region as the original hardware.
        camera.setMinX((short) level.getMinX());
        camera.setMaxX((short) level.getMaxX());
        objectManager.reset(camera.getX());
    }

    /**
     * Phase G: Create ObjectManager, wire CollisionSystem, and reset camera bounds.
     */
    public void initObjectSystem() throws IOException {
        initObjectManager();
        initCameraBounds();
    }

    /**
     * Phase H: Reset game-specific object state for the new level.
     */
    public void initGameplayState() {
        gameModule.onLevelLoad();
    }

    /**
     * Phase H: Create RingManager and cache ring patterns.
     */
    public void initRings() {
        RingSpriteSheet ringSpriteSheet = level.getRingSpriteSheet();
        ringManager = new RingManager(level.getRings(), ringSpriteSheet, this, touchResponseTable);
        ringManager.reset(Camera.getInstance().getX());
        ringManager.ensurePatternsCached(graphicsManager, level.getPatternCount());
    }

    /**
     * Phase H: Initialize zone-specific features (CNZ bumpers, CPZ pylon, water surface, etc.).
     */
    public void initZoneFeatures() throws IOException {
        Rom rom = GameServices.rom().getRom();
        Camera camera = Camera.getInstance();
        zoneFeatureProvider = gameModule.getZoneFeatureProvider();
        if (zoneFeatureProvider != null) {
            zoneFeatureProvider.initZoneFeatures(rom, getFeatureZoneId(), getFeatureActId(), camera.getX());
            // Cache zone feature patterns (water surface, etc.)
            int waterPatternBase = 0x30000; // High offset to avoid collision
            zoneFeatureProvider.ensurePatternsCached(graphicsManager, waterPatternBase);
        }
    }

    /**
     * Phase H: Reset game-specific state, create RingManager, and initialize zone features.
     */
    public void initGameState() throws IOException {
        initGameplayState();
        initRings();
        initZoneFeatures();
    }

    /**
     * Phase C: Load object art and player sprite art into the pattern atlas.
     */
    public void initArt() {
        initObjectArt();
        initPlayerSpriteArt();
    }

    /**
     * Phase C: Reset player state, initialize checkpoint, and create level gamestate.
     */
    public void initPlayerAndCheckpoint() {
        resetPlayerState();
        // Initialize checkpoint state for new level
        if (checkpointState == null) {
            checkpointState = gameModule.createRespawnState();
        }
        checkpointState.clear();
        levelGamestate = gameModule.createLevelState();
    }

    /**
     * Phase C: Load object art, player sprite art, reset player state,
     * and initialize checkpoint and level gamestate.
     */
    public void initArtAndPlayer() {
        initArt();
        initPlayerAndCheckpoint();
    }

    /**
     * Phase B: Initialize the water system for the current level.
     */
    public void initWater() throws IOException {
        Rom rom = GameServices.rom().getRom();
        WaterSystem waterSystem = WaterSystem.getInstance();
        WaterDataProvider waterProvider = gameModule != null ? gameModule.getWaterDataProvider() : null;
        if (waterProvider != null) {
            // Use the game-agnostic provider-based loading
            PlayerCharacter character = PlayerCharacter.SONIC_AND_TAILS; // TODO: get from game state
            waterSystem.loadForLevelFromProvider(waterProvider, rom,
                    getFeatureZoneId(), getFeatureActId(), character);
        } else if (zoneFeatureProvider != null && zoneFeatureProvider.hasWater(getFeatureZoneId())) {
            // Fallback for games without a WaterDataProvider (backward compatibility).
            // All three game modules now supply providers, so this path should rarely execute.
            @SuppressWarnings("deprecation")
            Runnable fallback = () -> waterSystem.loadForLevel(rom, getFeatureZoneId(), getFeatureActId(), level.getObjects());
            if (!waterSystem.hasWater(getFeatureZoneId(), getFeatureActId())) {
                fallback.run();
            }
        }
    }

    /**
     * Engine-specific: Pre-allocate BG FBO at the maximum required size.
     */
    public void initBackgroundRenderer() {
        // Pre-allocate the background FBO at maximum required size to avoid
        // mid-frame GPU reallocation hitches (e.g., AIZ intro ocean->beach transition)
        BackgroundRenderer bgRenderer = graphicsManager.getBackgroundRenderer();
        if (bgRenderer != null && bgRenderer.isInitialized()) {
            int maxBgWidth;
            if (zoneFeatureProvider != null && !zoneFeatureProvider.bgWrapsHorizontally()) {
                // S3K uses full-width BG data (e.g., AIZ intro ocean-to-beach transition)
                maxBgWidth = Math.max(cachedScreenWidth, getLayerLevelWidthPx((byte) 1));
            } else {
                // S1/S2 use VDP-width (512px) background periods.
                // Pre-allocating to full level width can exceed GPU max texture size
                // (S2: 128 blocks * 128px = 16384, right at GPU limit).
                maxBgWidth = Math.max(cachedScreenWidth, LevelTilemapManager.VDP_BG_PLANE_WIDTH_PX);
            }
            int fboHeight = 256 + LevelConstants.CHUNK_HEIGHT;
            graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM,
                    (cx, cy, cw, ch) -> bgRenderer.ensureCapacity(maxBgWidth, fboHeight)));
        }
    }

    /**
     * Updates object positions before player physics.
     * This must be called BEFORE spriteManager.update() so that SolidContacts
     * sees the current frame's platform positions, fixing 1-frame lag on
     * fast-moving platforms (SwingingPlatform, CNZ Elevators).
     *
     * <p>Update order is critical:
     * <ol>
     *   <li>OscillationManager - oscillation values first</li>
     *   <li>objectManager - platforms read oscillation, move to new positions</li>
     *   <li>spriteManager - SolidContacts now sees updated positions</li>
     * </ol>
     */
    public void updateObjectPositions() {
        // Update global oscillation values used by moving platforms, water surface, etc.
        // Must run before objects so SwingingPlatform reads current oscillation values.
        OscillationManager.update(frameCounter);

        if (objectManager != null) {
            Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
            List<AbstractPlayableSprite> sidekicks = spriteManager.getSidekicks();
            objectManager.update(Camera.getInstance().getX(), playable, sidekicks, frameCounter + 1);
        }
    }

    /**
     * Advances object streaming/execution without any touch responses.
     * Used by non-interactive ending demo preroll phases so objects can become
     * visible and animate without hurting/collecting from the frozen player.
     */
    public void updateObjectPositionsWithoutTouches() {
        OscillationManager.update(frameCounter);

        if (objectManager != null) {
            Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
            List<AbstractPlayableSprite> sidekicks = spriteManager.getSidekicks();
            objectManager.update(Camera.getInstance().getX(), playable, sidekicks, frameCounter + 1, false);
        }
    }

    /**
     * Runs pre-physics zone feature updates (e.g., LZ water slides and wind tunnels).
     *
     * <p>ROM order: {@code LZWaterFeatures} runs before {@code ExecuteObjects},
     * so water slides set {@code f_slidemode} and {@code obInertia} before
     * {@code Sonic_Move} executes. This method must be called before
     * {@code spriteManager.update()} to match that ordering.
     */
    public void updateZoneFeaturesPrePhysics() {
        if (zoneFeatureProvider != null && level != null) {
            Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
            zoneFeatureProvider.updatePrePhysics(playable, Camera.getInstance().getX(), getFeatureZoneId());
        }
    }

    public void update() {
        // NOTE: OscillationManager and objectManager are now updated via updateObjectPositions()
        // which is called earlier in GameLoop to fix platform riding sync (1-frame lag fix).

        Sprite player = null;
        AbstractPlayableSprite playable = null;
        boolean needsPlayer = ringManager != null || zoneFeatureProvider != null || levelGamestate != null;
        if (needsPlayer) {
            player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
        }
        if (ringManager != null) {
            ringManager.update(Camera.getInstance().getX(), playable, frameCounter + 1);
            // Lost ring physics run once per frame; collection checks run per-player.
            ringManager.updateLostRingPhysics(frameCounter + 1);
            ringManager.checkLostRingCollection(playable);
            // ROM: CPU Tails can also collect rings in 1P mode
            for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
                if (!sidekick.getDead()) {
                    ringManager.update(Camera.getInstance().getX(), sidekick, frameCounter + 1);
                    ringManager.checkLostRingCollection(sidekick);
                }
            }
        }
        // Water movement — ROM order: MoveWater (move toward target) runs BEFORE
        // DynWaterHeight (zone features set new target for next frame).
        // Use effective feature zone/act so S1 SBZ3 (loaded from LZ act 4 slot)
        // resolves to SBZ3 water behavior while retaining LZ tile/object resources.
        WaterSystem waterSystem = WaterSystem.getInstance();
        int featureZone = getFeatureZoneId();
        int featureAct = getFeatureActId();
        if (level != null && waterSystem.hasWater(featureZone, featureAct)) {
            Camera camera = Camera.getInstance();
            waterSystem.updateDynamic(featureZone, featureAct, camera.getX(), camera.getY());
            waterSystem.update();
        }

        // Update zone-specific features (CNZ bumpers, S1 DynWaterHeight, etc.)
        if (zoneFeatureProvider != null && level != null) {
            zoneFeatureProvider.update(playable, Camera.getInstance().getX(), getFeatureZoneId());
        }
        if (levelGamestate != null) {
            if (!isHudSuppressed()) {
                levelGamestate.update();
            }
            if (levelGamestate.isTimeOver() && playable != null && !playable.getDead()) {
                playable.applyHurtOrDeath(0, DamageCause.TIME_OVER, false);
            }
        }

        // Update player water state after both water movement and zone features.
        if (level != null && waterSystem.hasWater(featureZone, featureAct) && playable != null) {
            int waterY = waterSystem.getVisualWaterLevelY(featureZone, featureAct);
            playable.updateWaterState(waterY);
        }
    }

    /**
     * Advances non-player scene systems for ending-demo preroll phases.
     * Keeps water and zone features in sync while player physics/input are frozen.
     */
    public void updateEndingDemoScene() {
        Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;

        if (ringManager != null) {
            ringManager.update(Camera.getInstance().getX(), null, frameCounter + 1);
        }

        // Water movement before zone features (ROM order: MoveWater before DynWaterHeight)
        WaterSystem waterSystem = WaterSystem.getInstance();
        int featureZone = getFeatureZoneId();
        int featureAct = getFeatureActId();
        if (level != null && waterSystem.hasWater(featureZone, featureAct)) {
            Camera camera = Camera.getInstance();
            waterSystem.updateDynamic(featureZone, featureAct, camera.getX(), camera.getY());
            waterSystem.update();
        }

        if (zoneFeatureProvider != null && level != null) {
            zoneFeatureProvider.update(playable, Camera.getInstance().getX(), getFeatureZoneId());
        }

        if (level != null && waterSystem.hasWater(featureZone, featureAct) && playable != null) {
            int waterY = waterSystem.getVisualWaterLevelY(featureZone, featureAct);
            playable.updateWaterState(waterY);
        }
    }

    public void applyPlaneSwitchers(AbstractPlayableSprite player) {
        if (player == null) {
            return;
        }
        if (objectManager != null) {
            objectManager.applyPlaneSwitchers(player);
        }
        // Sonic 1 loop-based plane switching (and any other game-specific plane logic)
        GameModule module = GameModuleRegistry.getCurrent();
        if (module != null) {
            module.applyPlaneSwitching(player);
        }
    }

    public LevelState getLevelGamestate() {
        return levelGamestate;
    }

    private void initPlayerSpriteArt() {
        tailsTailBankCount = 0;
        PlayerSpriteArtProvider artProvider;
        if (CrossGameFeatureProvider.isActive()) {
            artProvider = CrossGameFeatureProvider.getInstance();
        } else if (game instanceof PlayerSpriteArtProvider p) {
            artProvider = p;
        } else {
            return;
        }
        Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        if (!(player instanceof AbstractPlayableSprite playable)) {
            return;
        }
        try {
            SpriteArtSet artSet = artProvider.loadPlayerSpriteArt(playable.getCode());
            if (artSet == null || artSet.bankSize() <= 0 || artSet.mappingFrames().isEmpty()
                    || artSet.dplcFrames().isEmpty()) {
                playable.setSpriteRenderer(null);
                return;
            }
            PlayerSpriteRenderer renderer = new PlayerSpriteRenderer(artSet);
            if (CrossGameFeatureProvider.isActive()) {
                renderer.setRenderContext(
                        CrossGameFeatureProvider.getInstance().getDonorRenderContext());
            }
            renderer.ensureCached(graphicsManager);
            playable.setSpriteRenderer(renderer);
            playable.setMappingFrame(0);
            playable.setAnimationFrameCount(artSet.mappingFrames().size());
            playable.setAnimationProfile(artSet.animationProfile());
            playable.setAnimationSet(artSet.animationSet());
            playable.setAnimationId(0);
            playable.setAnimationFrameIndex(0);
            playable.setAnimationTick(0);
            initSpindashDust(playable);
            initTailsTails(playable, artSet);
            initSuperState(playable);
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load player sprite art.", e);
        }

        // Also initialize art for each sidekick (CPU-controlled Tails etc.)
        // Build character name list and compute VRAM slot assignments so that
        // sidekicks sharing a character type with the main (or each other) get
        // shifted pattern banks to avoid atlas corruption.
        List<AbstractPlayableSprite> sidekicks = spriteManager.getSidekicks();
        String mainCharName = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        List<String> sidekickCharNames = new ArrayList<>(sidekicks.size());
        for (AbstractPlayableSprite sidekick : sidekicks) {
            String name = spriteManager.getSidekickCharacterName(sidekick);
            if (name == null) {
                // Fallback: use the config's single sidekick code
                name = configService.getString(SonicConfiguration.SIDEKICK_CHARACTER_CODE);
            }
            sidekickCharNames.add(name);
        }
        java.util.Map<Integer, Integer> vramSlots = computeVramSlots(mainCharName, sidekickCharNames);
        // Cache loaded art per character type to avoid redundant ROM reads
        java.util.Map<String, SpriteArtSet> artCache = new java.util.HashMap<>();
        // Global running offset in SIDEKICK_PATTERN_BASE range — ensures different
        // character types with the same per-type slot don't collide.
        int sidekickBankOffset = 0;
        for (int i = 0; i < sidekicks.size(); i++) {
            AbstractPlayableSprite sidekick = sidekicks.get(i);
            String sidekickCharName = sidekickCharNames.get(i);
            try {
                SpriteArtSet sidekickArt = artCache.computeIfAbsent(
                        sidekickCharName.toLowerCase(),
                        key -> {
                            try {
                                return artProvider.loadPlayerSpriteArt(key);
                            } catch (IOException e) {
                                LOGGER.log(SEVERE, "Failed to load art for sidekick character: " + key, e);
                                return null;
                            }
                        });
                if (sidekickArt == null || sidekickArt.bankSize() <= 0
                        || sidekickArt.mappingFrames().isEmpty()
                        || sidekickArt.dplcFrames().isEmpty()) {
                    LOGGER.warning("Skipping art init for sidekick " + i
                            + " (" + sidekickCharName + "): art unavailable or empty.");
                    continue;
                }
                // When a sidekick shares a character type with the main or another
                // sidekick, give it a unique pattern bank in the dedicated sidekick
                // range (SIDEKICK_PATTERN_BASE = 0x30000+). Uses a global running
                // offset so different character types with the same per-type slot
                // don't collide (e.g. sonic slot 1 and tails slot 1).
                int slot = vramSlots.get(i);
                if (slot > 0) {
                    int shiftedBase = SIDEKICK_PATTERN_BASE + sidekickBankOffset;
                    sidekickBankOffset += sidekickArt.bankSize();
                    sidekickArt = new SpriteArtSet(
                            sidekickArt.artTiles(),
                            sidekickArt.mappingFrames(),
                            sidekickArt.dplcFrames(),
                            sidekickArt.paletteIndex(),
                            shiftedBase,
                            sidekickArt.frameDelay(),
                            sidekickArt.bankSize(),
                            sidekickArt.animationProfile(),
                            sidekickArt.animationSet());
                }
                PlayerSpriteRenderer sidekickRenderer = new PlayerSpriteRenderer(sidekickArt);
                if (CrossGameFeatureProvider.isActive()) {
                    sidekickRenderer.setRenderContext(
                            CrossGameFeatureProvider.getInstance().getDonorRenderContext());
                }
                sidekickRenderer.ensureCached(graphicsManager);
                sidekick.setSpriteRenderer(sidekickRenderer);
                sidekick.setMappingFrame(0);
                sidekick.setAnimationFrameCount(sidekickArt.mappingFrames().size());
                sidekick.setAnimationProfile(sidekickArt.animationProfile());
                sidekick.setAnimationSet(sidekickArt.animationSet());
                sidekick.setAnimationId(0);
                sidekick.setAnimationFrameIndex(0);
                sidekick.setAnimationTick(0);
                initSpindashDust(sidekick);
                initTailsTails(sidekick, sidekickArt);
                initSuperState(sidekick);
            } catch (Exception e) {
                LOGGER.log(SEVERE, "Failed to load sidekick sprite art for index " + i + ".", e);
            }
        }

        // Upload donor palettes to GPU if cross-game features are active
        if (CrossGameFeatureProvider.isActive()) {
            RenderContext.uploadDonorPalettes(graphicsManager);
        }
    }

    /**
     * Computes VRAM bank slot index for each sidekick.
     * Characters matching the main character start at slot 1 (main is slot 0).
     * Different characters start at slot 0 (no conflict).
     */
    public static java.util.Map<Integer, Integer> computeVramSlots(String mainChar, List<String> sidekickChars) {
        java.util.Map<String, Integer> nextSlot = new java.util.HashMap<>();
        // Main character occupies slot 0 for its type
        nextSlot.put(mainChar.toLowerCase(), 1);
        java.util.Map<Integer, Integer> result = new java.util.HashMap<>();
        for (int i = 0; i < sidekickChars.size(); i++) {
            String charType = sidekickChars.get(i).toLowerCase();
            int slot = nextSlot.getOrDefault(charType, 0);
            result.put(i, slot);
            nextSlot.put(charType, slot + 1);
        }
        return result;
    }

    private void resetPlayerState() {
        Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        if (player instanceof AbstractPlayableSprite playable) {
            playable.resetState();
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            sidekick.resetState();
            if (sidekick.getCpuController() != null) {
                sidekick.getCpuController().reset();
            }
        }
    }

    private void initSpindashDust(AbstractPlayableSprite playable) {
        SpindashDustArtProvider dustProv;
        if (CrossGameFeatureProvider.isActive()) {
            dustProv = CrossGameFeatureProvider.getInstance();
        } else if (game instanceof SpindashDustArtProvider d) {
            dustProv = d;
        } else {
            playable.setSpindashDustController(null);
            return;
        }
        try {
            SpriteArtSet dustArt = dustProv.loadSpindashDustArt(playable.getCode());
            if (dustArt == null || dustArt.bankSize() <= 0 || dustArt.mappingFrames().isEmpty()
                    || dustArt.dplcFrames().isEmpty()) {
                playable.setSpindashDustController(null);
                return;
            }
            PlayerSpriteRenderer dustRenderer = new PlayerSpriteRenderer(dustArt);
            if (CrossGameFeatureProvider.isActive()) {
                dustRenderer.setRenderContext(
                        CrossGameFeatureProvider.getInstance().getDonorRenderContext());
            }
            dustRenderer.ensureCached(graphicsManager);
            playable.setSpindashDustController(new SpindashDustController(playable, dustRenderer));
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load spindash dust art.", e);
            playable.setSpindashDustController(null);
        }
    }

    /** Tracks how many tail appendage DPLC banks have been allocated this level load. */
    private int tailsTailBankCount = 0;

    private void initTailsTails(AbstractPlayableSprite playable, SpriteArtSet artSet) {
        if (!(playable instanceof Tails)) {
            playable.setTailsTailsController(null);
            return;
        }
        // Check donor game first (cross-game donation), then fall back to base game module
        boolean isS3k = CrossGameFeatureProvider.isActive()
                ? CrossGameFeatureProvider.getInstance().hasSeparateTailsTailArt()
                : gameModule.hasSeparateTailsTailArt();
        SpriteArtSet tailsArt;
        if (isS3k) {
            // S3K: Obj05 uses a completely separate art/mapping/DPLC set
            try {
                if (CrossGameFeatureProvider.isActive()) {
                    tailsArt = CrossGameFeatureProvider.getInstance().loadTailsTailArt();
                } else {
                    Rom rom = GameServices.rom().getRom();
                    Sonic3kPlayerArt s3kArt = new Sonic3kPlayerArt(RomByteReader.fromRom(rom));
                    tailsArt = s3kArt.loadTailsTail();
                }
            } catch (Exception e) {
                LOGGER.log(SEVERE, "Failed to load S3K tails tail art.", e);
                playable.setTailsTailsController(null);
                return;
            }
        } else {
            // S2: Obj05 uses same mappings/DPLCs/art as Tails but at a different VRAM base
            tailsArt = new SpriteArtSet(
                    artSet.artTiles(),
                    artSet.mappingFrames(),
                    artSet.dplcFrames(),
                    artSet.paletteIndex(),
                    Sonic2Constants.ART_TILE_TAILS_TAILS,
                    artSet.frameDelay(),
                    artSet.bankSize(),
                    null,
                    null
            );
        }
        // Multiple Tails sidekicks need separate DPLC banks for the tail appendage,
        // just like the main sprite body. The first Tails uses the original base;
        // subsequent ones get shifted into SIDEKICK_PATTERN_BASE range.
        if (tailsTailBankCount > 0) {
            int shiftedBase = SIDEKICK_PATTERN_BASE + 0x1000 + tailsArt.bankSize() * (tailsTailBankCount - 1);
            tailsArt = new SpriteArtSet(
                    tailsArt.artTiles(),
                    tailsArt.mappingFrames(),
                    tailsArt.dplcFrames(),
                    tailsArt.paletteIndex(),
                    shiftedBase,
                    tailsArt.frameDelay(),
                    tailsArt.bankSize(),
                    tailsArt.animationProfile(),
                    tailsArt.animationSet()
            );
        }
        tailsTailBankCount++;
        PlayerSpriteRenderer tailsRenderer = new PlayerSpriteRenderer(tailsArt);
        if (CrossGameFeatureProvider.isActive()) {
            tailsRenderer.setRenderContext(
                    CrossGameFeatureProvider.getInstance().getDonorRenderContext());
        }
        tailsRenderer.ensureCached(graphicsManager);
        playable.setTailsTailsController(new TailsTailsController(playable, tailsRenderer, isS3k));
    }

    private void initSuperState(AbstractPlayableSprite playable) {
        if (gameModule == null) {
            return;
        }
        var superCtrl = gameModule.createSuperStateController(playable);
        playable.setSuperStateController(superCtrl);

        // Load game-specific ROM data (palette cycling, etc.)
        if (superCtrl != null && !superCtrl.isRomDataPreLoaded()) {
            try {
                Rom rom = GameServices.rom().getRom();
                RomByteReader reader = RomByteReader.fromRom(rom);
                superCtrl.loadRomData(reader);
            } catch (Exception e) {
                LOGGER.fine("Could not load Super Sonic ROM data: " + e.getMessage());
            }
        }
    }

    private void initObjectArt() {
        ObjectArtProvider provider = gameModule != null ? gameModule.getObjectArtProvider() : null;
        if (provider == null) {
            objectRenderManager = null;
            return;
        }

        try {
            int zoneIndex = level != null ? level.getZoneIndex() : -1;
            provider.loadArtForZone(zoneIndex);

            objectRenderManager = new ObjectRenderManager(provider);
            LOGGER.info("Initializing Object Art. Base Index: " + OBJECT_PATTERN_BASE);
            objectRenderManager.ensurePatternsCached(graphicsManager, OBJECT_PATTERN_BASE);

            // Register level-tile-based object art (must be after level load)
            provider.registerLevelTileArt(level, zoneIndex);
            objectRenderManager.ensurePatternsCached(graphicsManager, OBJECT_PATTERN_BASE);

            hudRenderManager = new HudRenderManager(graphicsManager);
            hudRenderManager.setHudPalettes(provider.getHudTextPaletteLine(), provider.getHudFlashPaletteLine());
            hudRenderManager.setHudFlashMode(provider.getHudFlashMode());
            // Wire up HUD to unified UI render pipeline
            if (graphicsManager.getUiRenderPipeline() != null) {
                graphicsManager.getUiRenderPipeline().setHudRenderManager(hudRenderManager);
            }

            // HUD uses a fixed pattern base to avoid collisions with dynamically registered object sheets
            int hudBaseIndex = HUD_PATTERN_BASE;
            Pattern[] hudDigits = provider.getHudDigitPatterns();
            if (hudDigits != null) {
                LOGGER.info("Cached " + hudDigits.length + " HUD Digit patterns at index " + hudBaseIndex);
                for (int i = 0; i < hudDigits.length; i++) {
                    graphicsManager.cachePatternTexture(hudDigits[i], hudBaseIndex + i);
                }
                hudRenderManager.setDigitPatternIndex(hudBaseIndex);

                int textBaseIndex = hudBaseIndex + hudDigits.length;
                Pattern[] hudText = provider.getHudTextPatterns();
                if (hudText != null) {
                    LOGGER.info("Cached " + hudText.length + " HUD Text patterns at index " + textBaseIndex);
                    for (int i = 0; i < hudText.length; i++) {
                        graphicsManager.cachePatternTexture(hudText[i], textBaseIndex + i);
                    }
                    hudRenderManager.setTextPatternIndex(textBaseIndex, hudText.length);

                    int livesBaseIndex = textBaseIndex + hudText.length;
                    Pattern[] hudLives = provider.getHudLivesPatterns();
                    if (hudLives != null) {
                        LOGGER.info("Cached " + hudLives.length + " HUD Lives patterns at index " + livesBaseIndex);
                        for (int i = 0; i < hudLives.length; i++) {
                            graphicsManager.cachePatternTexture(hudLives[i], livesBaseIndex + i);
                        }
                        hudRenderManager.setLivesPatternIndex(livesBaseIndex, hudLives.length);

                        int livesNumbersBaseIndex = livesBaseIndex + hudLives.length;
                        Pattern[] hudLivesNumbers = provider.getHudLivesNumbers();
                        if (hudLivesNumbers != null) {
                            LOGGER.info("Cached " + hudLivesNumbers.length + " HUD Lives Numbers patterns at index "
                                    + livesNumbersBaseIndex);
                            for (int i = 0; i < hudLivesNumbers.length; i++) {
                                graphicsManager.cachePatternTexture(hudLivesNumbers[i], livesNumbersBaseIndex + i);
                            }
                            hudRenderManager.setLivesNumbersPatternIndex(livesNumbersBaseIndex);
                        }
                    }
                }
            }

        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load object art.", e);
            objectRenderManager = null;
        }
    }

    private boolean isHudSuppressed() {
        return transitions.isForceHudSuppressed()
                || (zoneFeatureProvider != null
                    && zoneFeatureProvider.shouldSuppressHud(currentZone, currentAct));
    }

    private void initAnimatedPatterns() {
        animatedPatternManager = null;
        if (!(game instanceof AnimatedPatternProvider provider)) {
            return;
        }
        try {
            animatedPatternManager = provider.loadAnimatedPatternManager(level, level.getZoneIndex());
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load animated patterns.", e);
            animatedPatternManager = null;
        }
    }

    private void initAnimatedPalettes() {
        animatedPaletteManager = null;
        if (!(game instanceof AnimatedPaletteProvider provider)) {
            return;
        }
        try {
            animatedPaletteManager = provider.loadAnimatedPaletteManager(level, level.getZoneIndex());
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load animated palettes.", e);
            animatedPaletteManager = null;
        }
    }

    /**
     * Debug Functionality to print each pattern to the screen.
     */
    public void drawAllPatterns() {
        if (debugRenderer != null) {
            debugRenderer.drawAllPatterns();
        }
    }

    /**
     * Renders the current level by processing and displaying collision data.
     * This is currently for debugging purposes to visualize collision areas.
     */
    public void draw() {
        drawWithSpritePriority(null, true);
    }

    public void drawWithSpritePriority(SpriteManager spriteManager) {
        drawWithSpritePriority(spriteManager, true);
    }

    public void drawWithSpritePriority(SpriteManager spriteManager, boolean includeSpritePass) {
        if (level == null) {
            LOGGER.warning("No level loaded to draw.");
            return;
        }

        frameCounter++;
        if (animatedPatternManager != null) {
            animatedPatternManager.update();
        }
        if (animatedPaletteManager != null && animatedPaletteManager != animatedPatternManager) {
            animatedPaletteManager.update();
        }
        Camera camera = Camera.getInstance();

        int bgScrollY = (int) (camera.getY() * 0.1f);
        if (game != null) {
            int levelIdx = levels.get(currentZone).get(currentAct).getLevelIndex();
            int[] scroll = game.getBackgroundScroll(levelIdx, camera.getX(), camera.getY());
            bgScrollY = scroll[1];
        }

        parallaxManager.update(currentZone, currentAct, camera, frameCounter, bgScrollY, level);

        // Propagate shake offsets from parallax manager to camera
        // This allows FG tilemap and sprite rendering to use shake-adjusted positions
        camera.setShakeOffsets(
                parallaxManager.getShakeOffsetX(),
                parallaxManager.getShakeOffsetY());

        List<GLCommand> collisionCommands = debugRenderer != null
                ? debugRenderer.getCollisionCommands() : new ArrayList<>();
        collisionCommands.clear();

        // Update water shader state before rendering level
        updateWaterShaderState(camera);

        // Draw Background (Layer 1)
        profiler.beginSection("render.bg");
        if (useShaderBackground && graphicsManager.getBackgroundRenderer() != null) {
            renderBackgroundShader(collisionCommands, bgScrollY);
        }
        profiler.endSection("render.bg");

        // Draw Foreground (Layer 0) low-priority pass
        profiler.beginSection("render.fg");
        ensureForegroundTilemapData();
        enqueueForegroundTilemapPass(camera, 0);

        // Generate collision debug overlay commands (independent of GPU/CPU path)
        if (debugRenderer != null && overlayManager.isEnabled(DebugOverlayToggle.COLLISION_VIEW)) {
            debugRenderer.generateCollisionDebugCommands(collisionCommands, camera, this::getBlockAtPosition);
        }

        // Render collision debug overlay on top of foreground tiles
        if (!collisionCommands.isEmpty() && overlayManager.isEnabled(DebugOverlayToggle.COLLISION_VIEW)) {
            for (GLCommand cmd : collisionCommands) {
                graphicsManager.registerCommand(cmd);
            }
        }

        // Generate tile priority debug overlay commands (shows high-priority tiles in red)
        if (debugRenderer != null && overlayManager.isEnabled(DebugOverlayToggle.TILE_PRIORITY_VIEW)) {
            List<GLCommand> priorityDebugCommands = debugRenderer.getPriorityDebugCommands();
            priorityDebugCommands.clear();
            debugRenderer.generateTilePriorityDebugCommands(priorityDebugCommands, camera, this::getBlockAtPosition);

            // Render tile priority debug overlay on top of foreground tiles
            if (!priorityDebugCommands.isEmpty()) {
                for (GLCommand cmd : priorityDebugCommands) {
                    graphicsManager.registerCommand(cmd);
                }
            }
        }

        profiler.endSection("render.fg");

        // Render zone features that should appear as part of foreground layer (before sprites)
        // (e.g., CNZ slot machine display that covers corrupted tiles but sprites render on top)
        if (zoneFeatureProvider != null) {
            zoneFeatureProvider.renderAfterForeground(camera);
        }

        // Draw Foreground (Layer 0) high-priority pass to tile priority FBO
        // This captures high-priority tile pixels for the sprite priority shader
        profiler.beginSection("render.fg.priority");
        renderHighPriorityTilesToFBO(camera);
        profiler.endSection("render.fg.priority");

        // HTZ earthquake uses BG high-priority cave tiles as a visual overlay.
        // Our main BG pass renders all BG priorities together behind FG-low, so we
        // draw a BG-high overlay here to match hardware layering in this mode.
        renderHtzEarthquakeBgHighOverlay();

        // Draw Foreground (Layer 0) high-priority pass to screen
        enqueueForegroundTilemapPass(camera, 1);

        if (includeSpritePass) {
            renderSpriteObjectPass(spriteManager, true);
            DebugObjectArtViewer.getInstance().draw(objectRenderManager, camera);
        } else {
            // No sprite/object pass this frame; restore the default shader state for
            // any later screen-space rendering after the level tiles.
            graphicsManager.registerCommand(disableWaterShaderCommand);
        }

        profiler.beginSection("render.hud");
        if (hudRenderManager != null && !isHudSuppressed()) {
            AbstractPlayableSprite focusedPlayer = camera.getFocusedSprite();
            hudRenderManager.draw(levelGamestate, focusedPlayer);
        }
        profiler.endSection("render.hud");

        boolean debugViewEnabled = configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
        boolean overlayEnabled = debugViewEnabled && overlayManager.isEnabled(DebugOverlayToggle.OVERLAY);
        if (debugRenderer != null) {
            debugRenderer.renderDebugOverlays(overlayEnabled, objectManager, ringManager,
                    spriteManager, gameModule, configService, frameCounter);
        }
        graphicsManager.enqueueDefaultShaderState();
    }

    private void updateWaterShaderState(Camera camera) {
        WaterSystem waterSystem = WaterSystem.getInstance();
        int zoneId = getFeatureZoneId();
        int actId = getFeatureActId();

        if (waterSystem.hasWater(zoneId, actId)) {
            // Set uniforms via custom command - this also enables the water shader
            // Use visual water level (with oscillation) for rendering effects
            int waterLevel = waterSystem.getVisualWaterLevelY(zoneId, actId);

            // Determine shimmer style from current game module's physics feature set.
            // 0 = S2/S3K smooth sine wave, 1 = S1 integer-snapped shimmer
            int shimmerStyle = 0;
            PhysicsFeatureSet featureSet = null;
            GameModule currentModule = GameModuleRegistry.getCurrent();
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
            float waterlineScreenY = (float) (waterLevel - camera.getY() + waterlineOffset);
            currentShimmerStyle = shimmerStyle;

            // Set mutable state for pre-allocated water shader setup command
            pendingWaterlineScreenY = waterlineScreenY;
            pendingWaterShimmerStyle = shimmerStyle;
            graphicsManager.registerCommand(waterShaderSetupCommand);
        } else {
            // No water in this zone - disable underwater palette for sprite priority shader
            currentShimmerStyle = 0;
            graphicsManager.setWaterEnabled(false);
        }
        // Note: We don't disable water shader here - that's done later before HUD
        // rendering
    }

    private void renderBackgroundShader(List<GLCommand> commands, int bgScrollY) {
        if (level == null || level.getMap() == null)
            return;

        BackgroundRenderer bgRenderer = graphicsManager.getBackgroundRenderer();
        if (bgRenderer == null)
            return;

        Camera camera = Camera.getInstance();
        Palette.Color backdropColor = resolveLevelBackdropColor();
        bgRenderer.setBackdropColor(
                backdropColor.rFloat(),
                backdropColor.gFloat(),
                backdropColor.bFloat());

        int[] hScrollData = parallaxManager.getHScrollForShader();
        short[] vScrollData = parallaxManager.getVScrollPerLineBGForShader();
        short[] vScrollColumnData = parallaxManager.getVScrollPerColumnBGForShader();

        // For zones with BG maps wider than 512px (e.g., SBZ/FZ with 15360px BG),
        // the 512px tilemap must contain tiles from the correct BG map region.
        // Use the scroll handler's BG camera X (v_bgscreenposx equivalent) to
        // determine which tiles to load, then pass the offset to the shader so
        // it can correctly index into the tilemap via fboWorldOffsetX.
        int bgCameraX = parallaxManager.getBgCameraX();
        if (bgCameraX != Integer.MIN_VALUE
                && zoneFeatureProvider != null && zoneFeatureProvider.bgWrapsHorizontally()) {
            // 16px-aligned base offset. The tilemap is 512px wide, viewport is 320px.
            // This leaves 192px of headroom for the viewport within the tilemap.
            int newBase = Math.floorDiv(bgCameraX, 16) * 16;
            if (newBase != tilemapManager.getBgTilemapBaseX()) {
                tilemapManager.setBgTilemapBaseX(newBase);
                tilemapManager.setBackgroundTilemapDirty(true);
            }
        } else if (tilemapManager.getBgTilemapBaseX() != 0) {
            // Zone doesn't need offset - reset to 0 if previously set
            tilemapManager.setBgTilemapBaseX(0);
            tilemapManager.setBackgroundTilemapDirty(true);
        }

        // Track BG period width changes (e.g., GHZ parallax spread grows with cameraX).
        // When the period widens, the tilemap must be rebuilt at the larger size.
        int newBgPeriodWidth = parallaxManager.getBgPeriodWidth();
        if (newBgPeriodWidth != tilemapManager.getCurrentBgPeriodWidth()) {
            tilemapManager.setCurrentBgPeriodWidth(newBgPeriodWidth);
            tilemapManager.setBackgroundTilemapDirty(true);
        }

        ensureBackgroundTilemapData();

        int bgPeriodWidthPixels = tilemapManager.getBackgroundTilemapWidthTiles() * Pattern.PATTERN_WIDTH;
        // Pass bgTilemapBaseX to the shader so it offsets worldX before wrapping.
        // Shader: fboWorldOffsetX = -ScrollMidpoint - ExtraBuffer
        // We want fboWorldOffsetX = bgTilemapBaseX, so ScrollMidpoint = -bgTilemapBaseX.
        int shaderScrollMidpoint = -tilemapManager.getBgTilemapBaseX();
        int shaderExtraBuffer = 0;
        float bgTilemapWorldOffsetX = 0.0f;
        boolean perLineScrollActive = false;
        float vdpWrapWidthTiles = 0.0f;
        float nametableBaseTile = 0.0f;
        if (zoneFeatureProvider != null && zoneFeatureProvider.isIntroOceanPhaseActive(currentZone, currentAct)) {
            // Per-scanline HScroll in the tilemap shader, matching VDP behavior.
            // Each pixel computes worldX = pixelX - hScroll[scanline] directly,
            // then looks up the correct tile from the full-width tilemap.
            bgPeriodWidthPixels = cachedScreenWidth;
            bgTilemapWorldOffsetX = 0;
            shaderScrollMidpoint = 0;
            shaderExtraBuffer = 0;
            perLineScrollActive = true;

            // VDP nametable ring buffer: overflow count tracks how many positions
            // have been overwritten with beach tiles as the camera advances.
            // Ocean phase (introScrollOffset < 0): overflow=0 (all ocean).
            // Camera tracking: overflow gradually increases, revealing beach tiles.
            vdpWrapWidthTiles = 64.0f;
            nametableBaseTile = zoneFeatureProvider.getVdpNametableBase(
                    currentZone, currentAct, camera.getX(), tilemapManager.getBackgroundTilemapWidthTiles());
        }
        // Cap BG period at the scroll handler's required width.
        // Zones with a single BG scroll speed cap at VDP nametable width (512px).
        // Zones with multi-speed parallax (e.g., GHZ) need a wider period to
        // avoid a visible wrap seam where slower and faster layers overlap.
        int bgPeriodCap = parallaxManager.getBgPeriodWidth();
        if (!perLineScrollActive && bgPeriodWidthPixels > bgPeriodCap) {
            bgPeriodWidthPixels = bgPeriodCap;
        }
        int renderWidth = Math.max(cachedScreenWidth, bgPeriodWidthPixels);
        // Add CHUNK_HEIGHT (16px) to cover VScroll range
        // This prevents bottom clipping when VScroll > 0 (max VScroll = 15, max gameY = 223, max fboY = 238 < 272)
        int renderHeight = 256 + LevelConstants.CHUNK_HEIGHT;

        // ROM parity: use the full background plane period and direct wrap sampling.
        // The intro path still uses the same VDP hscroll semantics as normal gameplay.
        // Get pattern renderer's screen height for correct Y coordinate handling
        int screenHeightPixels = cachedScreenHeight;

        // Use zone-specific vertical scroll from parallax manager
        // This ensures zones like MCZ use their act-dependent BG Y calculations
        int actualBgScrollY = parallaxManager.getVscrollFactorBG();

        // 1. Ensure FBO capacity (grow-only, no per-frame reallocation)
        pendingBgRenderWidth = renderWidth;
        pendingBgRenderHeight = renderHeight;
        graphicsManager.registerCommand(bgEnsureCapacityCommand);

        // 2. Begin Tile Pass (Bind FBO)
        // Use water shader in screen-space mode for FBO, with adjusted waterline
        WaterSystem waterSystem = WaterSystem.getInstance();
        int featureZone = getFeatureZoneId();
        int featureAct = getFeatureActId();
        boolean hasWater = waterSystem.hasWater(featureZone, featureAct);
        // Use visual water level (with oscillation) for background rendering
        int waterLevelWorldY = hasWater ? waterSystem.getVisualWaterLevelY(featureZone, featureAct) : 9999;

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

        ensureBackgroundTilemapData();
        pendingBgTilePassRenderWidth = renderWidth;
        pendingBgTilePassRenderHeight = renderHeight;
        pendingBgTilePassHasWater = hasWater;
        pendingBgTilePassFboWaterlineY = fboWaterlineY;
        pendingBgTilePassAlignedBgY = alignedBgY;
        pendingBgTilePassBgTilemapWorldOffsetX = bgTilemapWorldOffsetX;
        pendingBgTilePassPerLineScroll = perLineScrollActive;
        pendingBgTilePassPerColumnVScroll = vScrollColumnData;
        pendingBgTilePassHScrollData = hScrollData;
        pendingBgTilePassVdpWrapWidth = vdpWrapWidthTiles;
        pendingBgTilePassNametableBase = nametableBaseTile;
        graphicsManager.registerCommand(bgTilePassCommand);

        // 5. Set shimmer state on BG renderer for parallax compositing pass
        bgRenderer.setShimmerState(frameCounter, currentShimmerStyle, bgWaterlineScreenY);

        // 6. Render the FBO with Parallax Shader
        if (graphicsManager.getCombinedPaletteTextureId() != null) {
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
            graphicsManager.registerCommand(bgRenderWithScrollCommand);
        }
    }

    /**
     * Renders the shared sprite/object gameplay pass used after tile rendering.
     * This can also be called separately after a full-screen fade to keep
     * sprites/objects visible while the level tiles remain hidden.
     */
    public void renderSpriteObjectPass(SpriteManager spriteManager, boolean includeWaterSurface) {
        Camera camera = Camera.getInstance();

        // Render ALL sprites in unified bucket order (7→0)
        // Sprite-to-sprite ordering is by bucket number regardless of isHighPriority
        // The sprite priority shader composites sprites with tile priority awareness
        profiler.beginSection("render.sprites");

        graphicsManager.setUseSpritePriorityShader(true);
        graphicsManager.setCurrentSpriteHighPriority(false);
        graphicsManager.beginPatternBatch();

        if (ringManager != null) {
            ringManager.draw(frameCounter);
            ringManager.drawLostRings(frameCounter);
        }

        for (int bucket = RenderPriority.MAX; bucket >= RenderPriority.MIN; bucket--) {
            if (spriteManager != null) {
                spriteManager.drawUnifiedBucketWithPriority(bucket, graphicsManager);
            }
            if (objectManager != null) {
                objectManager.drawUnifiedBucketWithPriority(bucket, graphicsManager);
            }
        }
        graphicsManager.flushPatternBatch();
        graphicsManager.setUseSpritePriorityShader(false);
        profiler.endSection("render.sprites");

        if (includeWaterSurface) {
            graphicsManager.registerCommand(disableShimmerCommand);
            if (zoneFeatureProvider != null) {
                zoneFeatureProvider.render(camera, frameCounter);
            }
        }

        // Revert to default shader for any following HUD/debug/screen-space rendering.
        graphicsManager.registerCommand(disableWaterShaderCommand);
    }

    /**
     * Renders the DEZ background during the ending cutscene.
     * <p>
     * Reuses the existing shader background pipeline with ending-specific parameters:
     * camera at (0,0), BG vertical scroll from the ending provider, and DEZ
     * parallax update via SwScrlDez TempArray accumulation.
     * <p>
     * ROM reference: During the ending, SwScrl_DEZ runs every frame with
     * Camera_X_pos=0, Camera_BG_Y_pos starting at $C8 and incrementing during
     * CAMERA_SCROLL. Stars animate via TempArray addq accumulation independent
     * of camera movement.
     *
     * @param bgVscroll the current background vertical scroll value (ROM: Vscroll_Factor_BG)
     */
    public void renderEndingBackground(int bgVscroll) {
        renderEndingBackground(bgVscroll, null);
    }

    /**
     * Renders the DEZ star field background for the ending cutscene, with an
     * optional backdrop color override.
     * <p>
     * The BG shader normally resolves the backdrop from the level's stored
     * palette. During the ending, the cutscene fades display palettes from
     * white to target independently — the backdrop must track this fade.
     * When {@code backdropOverride} is non-null, it replaces the level's
     * backdrop after the shader pipeline is set up (deferred commands read
     * the override at execution time).
     *
     * @param bgVscroll       current BG vertical scroll (ROM: Vscroll_Factor_BG)
     * @param backdropOverride {r, g, b} in [0..1], or null to use level default
     */
    public void renderEndingBackground(int bgVscroll, float[] backdropOverride) {
        if (level == null || level.getMap() == null) {
            return;
        }
        if (!useShaderBackground || graphicsManager.getBackgroundRenderer() == null) {
            return;
        }

        // Update parallax with camera=(0,0) and the ending's BG vscroll
        // This drives SwScrlDez TempArray accumulation for star parallax
        frameCounter++;
        parallaxManager.updateForEnding(currentZone, currentAct, frameCounter, bgVscroll);

        // Force background tilemap FBO re-render every frame during the ending.
        // The cutscene fades palette lines 2-3 from white → sky colors; the tilemap
        // FBO bakes palette colors at render time, so it must be rebuilt each frame
        // to reflect the evolving palette state. Without this, the DEZ star field
        // appears at full color instantly instead of fading in with the palette.
        if (tilemapManager != null) {
            tilemapManager.setBackgroundTilemapDirty(true);
        }

        // Render using the existing shader pipeline
        List<GLCommand> endingCollisionCommands = debugRenderer != null
                ? debugRenderer.getCollisionCommands() : new ArrayList<>();
        renderBackgroundShader(endingCollisionCommands, bgVscroll);

        // Override backdrop color for ending cutscene palette fade.
        // The deferred commands read bgRenderer fields at execution time, so
        // setting the backdrop AFTER renderBackgroundShader but BEFORE flush()
        // ensures the override takes effect.
        if (backdropOverride != null && backdropOverride.length >= 3) {
            BackgroundRenderer bgRenderer = graphicsManager.getBackgroundRenderer();
            if (bgRenderer != null) {
                bgRenderer.setBackdropColor(
                        backdropOverride[0], backdropOverride[1], backdropOverride[2]);
            }
        }
    }

    private void enqueueForegroundTilemapPass(Camera camera, int priorityPass) {
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }

        WaterSystem waterSystem = WaterSystem.getInstance();
        int featureZone = getFeatureZoneId();
        int featureAct = getFeatureActId();
        boolean hasWater = waterSystem.hasWater(featureZone, featureAct);
        int waterLevel = hasWater ? waterSystem.getVisualWaterLevelY(featureZone, featureAct) : 0;
        // Use shake-adjusted Y for water line calculation
        float waterlineScreenY = (float) (waterLevel - camera.getYWithShake());

        int screenW = cachedScreenWidth;
        int screenH = cachedScreenHeight;
        // Use shake-adjusted camera positions for FG tilemap rendering
        // This makes the foreground tiles shake in sync with sprites
        float worldOffsetX = camera.getXWithShake();
        float worldOffsetY = camera.getYWithShake();

        Integer atlasId = graphicsManager.getPatternAtlasTextureId();
        Integer paletteId = graphicsManager.getCombinedPaletteTextureId();
        Integer underwaterPaletteId = graphicsManager.getUnderwaterPaletteTextureId();
        boolean useUnderwaterPalette = hasWater && underwaterPaletteId != null;

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
            graphicsManager.registerCommand(fgTilemapPassLowCommand);
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
            graphicsManager.registerCommand(fgTilemapPassHighCommand);
        }
    }

    /**
     * Render BG high-priority tiles as an overlay during HTZ earthquake mode.
     *
     * On real hardware the VDP layer order is BG-low → FG-low → BG-high → FG-high.
     * Our main BG pass renders all priorities behind FG, so this method draws only
     * BG high-priority tiles (cave ceiling terrain) between FG-low and FG-high to
     * match hardware layering.
     *
     * In earthquake mode, HTZ horizontal scroll is flat (same for every scanline),
     * so a single tilemap render call with the BG scroll offset suffices.
     */
    private void renderHtzEarthquakeBgHighOverlay() {
        if (!GameServices.gameState().isHtzScreenShakeActive()) {
            return;
        }

        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }

        Integer atlasId = graphicsManager.getPatternAtlasTextureId();
        Integer paletteId = graphicsManager.getCombinedPaletteTextureId();
        if (atlasId == null || paletteId == null) {
            return;
        }

        int[] hScrollData = parallaxManager.getHScrollForShader();
        if (hScrollData == null || hScrollData.length == 0) {
            return;
        }

        short bgScroll = (short) (hScrollData[hScrollData.length - 1] & 0xFFFF);
        float bgWorldOffsetX = -bgScroll;
        float bgWorldOffsetY = parallaxManager.getVscrollFactorBG();
        int screenW = cachedScreenWidth;
        int screenH = cachedScreenHeight;

        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
            if (tilemapRenderer == null) {
                return;
            }
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            // Disable VDP wrap height for the overlay — earthquake priority tiles are
            // at tilemap rows 48+ (outside the 0-31 range that normal BG wrapping uses).
            float savedWrapHeight = tilemapRenderer.getBgVdpWrapHeight();
            tilemapRenderer.setBgVdpWrapHeight(0.0f);
            tilemapRenderer.render(
                    TilemapGpuRenderer.Layer.BACKGROUND,
                    screenW,
                    screenH,
                    viewport[0],
                    viewport[1],
                    viewport[2],
                    viewport[3],
                    bgWorldOffsetX,
                    bgWorldOffsetY,
                    graphicsManager.getPatternAtlasWidth(),
                    graphicsManager.getPatternAtlasHeight(),
                    atlasId,
                    paletteId,
                    0,
                    1,      // priorityPass=1: HIGH-PRIORITY TILES ONLY
                    false,
                    false,
                    false,
                    0.0f);
            tilemapRenderer.setBgVdpWrapHeight(savedWrapHeight);
        }));
    }

    /**
     * Render high-priority foreground tiles to the tile priority FBO.
     * This FBO is sampled by the sprite priority shader to determine
     * if low-priority sprites should be hidden behind high-priority tiles.
     */
    private void renderHighPriorityTilesToFBO(Camera camera) {
        TilePriorityFBO fbo = graphicsManager.getTilePriorityFBO(cachedScreenWidth, cachedScreenHeight);
        if (fbo == null || !fbo.isInitialized()) {
            return;
        }

        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }

        Integer atlasId = graphicsManager.getPatternAtlasTextureId();
        Integer paletteId = graphicsManager.getCombinedPaletteTextureId();
        if (atlasId == null || paletteId == null) {
            return;
        }

        int screenW = cachedScreenWidth;
        int screenH = cachedScreenHeight;
        float fgWorldOffsetX = camera.getXWithShake();
        float fgWorldOffsetY = camera.getYWithShake();

        pendingFboScreenW = screenW;
        pendingFboScreenH = screenH;
        pendingFboFgWorldOffsetX = fgWorldOffsetX;
        pendingFboFgWorldOffsetY = fgWorldOffsetY;
        pendingFboAtlasId = atlasId;
        pendingFboPaletteId = paletteId;
        graphicsManager.registerCommand(highPriorityFboCommand);
    }

    private void ensureBackgroundTilemapData() {
        if (tilemapManager != null) {
            tilemapManager.ensureBackgroundTilemapData(this::getBlockAtPosition,
                    zoneFeatureProvider, currentZone, parallaxManager, verticalWrapEnabled);
        }
    }

    private void ensureForegroundTilemapData() {
        if (tilemapManager != null) {
            tilemapManager.ensureForegroundTilemapData(this::getBlockAtPosition,
                    zoneFeatureProvider, currentZone, parallaxManager, verticalWrapEnabled);
        }
    }

    /**
     * Retrieves the block at a given position.
     *
     * @param layer the layer to retrieve the block from
     * @return the Block at the specified position, or null if not found
     */
    private int getLayerLevelWidthPx(byte layer) {
        if (level == null) {
            return blockPixelSize;
        }
        int widthBlocks = Math.max(1, level.getLayerWidthBlocks(layer));
        return widthBlocks * blockPixelSize;
    }

    private int getLayerLevelHeightPx(byte layer) {
        if (level == null) {
            return blockPixelSize;
        }
        int heightBlocks = Math.max(1, level.getLayerHeightBlocks(layer));
        return heightBlocks * blockPixelSize;
    }

    /**
     * Populates cached FG/BG pixel dimensions from the current level.
     * Must be called after a level is loaded (dimensions are immutable during gameplay).
     */
    private void cacheLevelDimensions() {
        if (level != null) {
            cachedFgWidthPx = getLayerLevelWidthPx((byte) 0);
            cachedFgHeightPx = getLayerLevelHeightPx((byte) 0);
            cachedBgWidthPx = getLayerLevelWidthPx((byte) 1);  // Full map width (matches reference)
            cachedBgContiguousWidthPx = computeActualBgDataWidthPx();  // For bgTilemapBaseX wrapping
            cachedBgHeightPx = getLayerLevelHeightPx((byte) 1);
        } else {
            cachedFgWidthPx = blockPixelSize;
            cachedFgHeightPx = blockPixelSize;
            cachedBgWidthPx = blockPixelSize;
            cachedBgContiguousWidthPx = blockPixelSize;
            cachedBgHeightPx = blockPixelSize;
        }
    }

    /**
     * Builds a LevelGeometry snapshot from the current cached dimensions.
     */
    private LevelGeometry buildGeometry() {
        return new LevelGeometry(level, cachedFgWidthPx, cachedFgHeightPx,
                cachedBgWidthPx, cachedBgContiguousWidthPx, cachedBgHeightPx,
                blockPixelSize, chunksPerBlockSide);
    }

    /**
     * Scan the BG layer (layer 1) to find the contiguous data width.
     * On the Mega Drive, the BG nametable is a 512px-wide ring buffer.
     * The scroll handler fills it from the BG map, wrapping at the map's
     * data width.  The Map stores both FG and BG with the same total width
     * (e.g., 128 blocks = 16384px), but BG data typically only spans a
     * small contiguous region from column 0 (e.g., 8 blocks for HTZ).
     * <p>
     * Using the contiguous BG data width for X wrapping ensures that queries
     * at large camera X positions wrap back to valid BG data rather than
     * reading empty columns in the unused portion of the map.
     * <p>
     * Example: HTZ BG data spans 8 contiguous columns (1024px) within a
     * 128-column map.  Without this fix, bgTilemapBaseX=6144 queries
     * column 48 (empty).  With contiguous width = 1024px wrapping,
     * 6144 mod 1024 = 0 → column 0 (valid).
     */
    private int computeActualBgDataWidthPx() {
        if (level == null || level.getMap() == null) {
            return blockPixelSize;
        }
        Map map = level.getMap();
        int mapWidth = map.getWidth();
        int mapHeight = map.getHeight();

        // Scan left-to-right to find the first all-zero column.
        // This gives the contiguous BG data width starting from column 0,
        // ignoring any stray non-zero blocks at distant columns.
        int contiguousWidth = 0;
        for (int col = 0; col < mapWidth; col++) {
            boolean hasData = false;
            for (int row = 0; row < mapHeight; row++) {
                if ((map.getValue(1, col, row) & 0xFF) != 0) {
                    hasData = true;
                    break;
                }
            }
            if (hasData) {
                contiguousWidth = col + 1;
            } else {
                // Found first empty column - stop here
                break;
            }
        }

        if (contiguousWidth == 0) {
            // No BG data at all — use full map width as fallback
            return mapWidth * blockPixelSize;
        }

        int dataWidthPx = contiguousWidth * blockPixelSize;

        if (dataWidthPx < mapWidth * blockPixelSize) {
            LOGGER.fine("BG contiguous data width: " + contiguousWidth + " blocks ("
                    + dataWidthPx + "px) out of " + mapWidth + " map columns");
        }

        return dataWidthPx;
    }

    /** Fast cached getter for layer pixel width (avoids per-call getLayerWidthBlocks). */
    private int getCachedLayerWidthPx(byte layer) {
        return layer == 0 ? cachedFgWidthPx : cachedBgWidthPx;
    }

    /** Fast cached getter for layer pixel height (avoids per-call getLayerHeightBlocks). */
    private int getCachedLayerHeightPx(byte layer) {
        return layer == 0 ? cachedFgHeightPx : cachedBgHeightPx;
    }

    private Block getBlockAtPosition(byte layer, int x, int y) {
        if (level == null || level.getMap() == null) {
            LOGGER.warning("Level or Map is not initialized.");
            return null;
        }

        int levelWidth = getCachedLayerWidthPx(layer);
        int levelHeight = getCachedLayerHeightPx(layer);

        // Handle wrapping for X
        int wrappedX = ((x % levelWidth) + levelWidth) % levelWidth;

        // Handle wrapping for Y
        int wrappedY = y;
        if (layer == 1) {
            // Background loops vertically
            wrappedY = ((wrappedY % levelHeight) + levelHeight) % levelHeight;
        } else if (verticalWrapEnabled) {
            // ROM: LZ3/SBZ2 — FG also wraps vertically
            wrappedY = ((wrappedY % levelHeight) + levelHeight) % levelHeight;
        } else {
            // Foreground Clamps
            if (wrappedY < 0 || wrappedY >= levelHeight)
                return null;
        }

        Map map = level.getMap();
        int mapX = wrappedX / blockPixelSize;
        int mapY = wrappedY / blockPixelSize;

        byte value = map.getValue(layer, mapX, mapY);

        // Mask the value to treat the byte as unsigned
        int blockIndex = value & 0xFF;

        if (blockIndex >= level.getBlockCount()) {
            return null;
        }

        Block block = level.getBlock(blockIndex);
        if (block == null) {
            LOGGER.warning("Block at index " + blockIndex + " is null.");
        }

        return block;
    }

    /**
     * Returns the raw block index (0-255) at the given pixel position in the foreground layer.
     * Equivalent to the ROM's Level_Layout lookup used by OilSlides.
     *
     * @param x pixel X coordinate
     * @param y pixel Y coordinate
     * @return block index (0-255), or -1 if out of bounds
     */
    public int getBlockIdAt(int x, int y) {
        if (level == null || level.getMap() == null) {
            return -1;
        }
        int levelWidth = cachedFgWidthPx;
        int levelHeight = cachedFgHeightPx;
        int wrappedX = ((x % levelWidth) + levelWidth) % levelWidth;
        int wrappedY = y;
        if (verticalWrapEnabled) {
            wrappedY = ((wrappedY % levelHeight) + levelHeight) % levelHeight;
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return -1;
        }
        Map map = level.getMap();
        int mapX = wrappedX / blockPixelSize;
        int mapY = wrappedY / blockPixelSize;
        return map.getValue(0, mapX, mapY) & 0xFF;
    }

    public ChunkDesc getChunkDescAt(byte layer, int x, int y) {
        if (level == null || level.getMap() == null) {
            return null;
        }

        int levelWidth = getCachedLayerWidthPx(layer);
        int levelHeight = getCachedLayerHeightPx(layer);

        // Wrap X (always wraps)
        int wrappedX = ((x % levelWidth) + levelWidth) % levelWidth;

        // Wrap or clamp Y depending on layer
        int wrappedY = y;
        if (layer == 1) {
            // Background loops vertically
            wrappedY = ((wrappedY % levelHeight) + levelHeight) % levelHeight;
        } else if (verticalWrapEnabled) {
            // ROM: LZ3/SBZ2 — FG also wraps vertically
            wrappedY = ((wrappedY % levelHeight) + levelHeight) % levelHeight;
        } else {
            // Foreground clamps
            if (wrappedY < 0 || wrappedY >= levelHeight)
                return null;
        }

        // Block lookup (inlined from getBlockAtPosition to reuse wrappedX/wrappedY)
        Map map = level.getMap();
        int mapX = wrappedX / blockPixelSize;
        int mapY = wrappedY / blockPixelSize;

        byte value = map.getValue(layer, mapX, mapY);
        int blockIndex = value & 0xFF;

        if (blockIndex >= level.getBlockCount()) {
            return null;
        }

        Block block = level.getBlock(blockIndex);
        if (block == null) {
            return null;
        }

        // Intra-block position (reuses already-wrapped coordinates)
        return block.getChunkDesc((wrappedX % blockPixelSize) / LevelConstants.CHUNK_WIDTH,
                (wrappedY % blockPixelSize) / LevelConstants.CHUNK_HEIGHT);
    }

    /**
     * Returns the ChunkDesc at the given pixel position, optionally resolving
     * Sonic 1 loop collision (low plane uses alternate block index).
     *
     * @param layer        0 = foreground, 1 = background
     * @param x            pixel X
     * @param y            pixel Y
     * @param loopLowPlane if true and layer == 0, resolve collision block index via Level
     * @return the ChunkDesc, or null if out of bounds
     */
    public ChunkDesc getChunkDescAt(byte layer, int x, int y, boolean loopLowPlane) {
        if (!loopLowPlane || layer != 0) {
            return getChunkDescAt(layer, x, y);
        }

        // Loop low plane: resolve collision block via Level.resolveCollisionBlockIndex
        if (level == null || level.getMap() == null) {
            return null;
        }

        int levelWidth = cachedFgWidthPx;
        int levelHeight = cachedFgHeightPx;
        int wrappedX = ((x % levelWidth) + levelWidth) % levelWidth;
        int wrappedY = y;
        if (verticalWrapEnabled) {
            wrappedY = ((wrappedY % levelHeight) + levelHeight) % levelHeight;
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return null;
        }

        Map map = level.getMap();
        int mapX = wrappedX / blockPixelSize;
        int mapY = wrappedY / blockPixelSize;

        int rawBlockIndex = map.getValue(0, mapX, mapY) & 0xFF;
        int resolvedIndex = level.resolveCollisionBlockIndex(rawBlockIndex, mapX, mapY);

        if (resolvedIndex >= level.getBlockCount()) {
            return null;
        }

        Block block = level.getBlock(resolvedIndex);
        if (block == null) {
            return null;
        }

        return block.getChunkDesc(
                (wrappedX % blockPixelSize) / LevelConstants.CHUNK_WIDTH,
                (wrappedY % blockPixelSize) / LevelConstants.CHUNK_HEIGHT);
    }

    public SolidTile getSolidTileForChunkDesc(ChunkDesc chunkDesc, int solidityBitIndex) {
        try {
            if (chunkDesc == null) {
                return null;
            }
            if (!chunkDesc.isSolidityBitSet(solidityBitIndex)) {
                return null;
            }

            Chunk chunk = level.getChunk(chunkDesc.getChunkIndex());
            if (chunk == null) {
                return null;
            }
            // Get collision index - ROM treats index 0 as "no collision"
            // (s2.asm FindFloor line 42963: beq.s loc_1E7E2)
            int collisionIndex = (solidityBitIndex < 0x0E)
                    ? chunk.getSolidTileIndex()
                    : chunk.getSolidTileAltIndex();
            if (collisionIndex == 0) {
                return null; // No collision shape assigned
            }
            return level.getSolidTile(collisionIndex);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Deprecated or convenience method for backward compatibility if needed,
    // but better to remove or update callers.
    // For now, let's overload it to default to Layer 0 (Primary) if not specified,
    // or we can force update. GroundSensor is the main one.
    // I'll leave a deprecated one just in case, or remove it.
    // GroundSensor calls it. I should update GroundSensor.
    // But I can't leave this here without updating GroundSensor first or it won't
    // compile?
    // Wait, I can overload.
    public SolidTile getSolidTileForChunkDesc(ChunkDesc chunkDesc, byte layer) {
        int solidityBitIndex = (layer == 0) ? 0x0C : 0x0E;
        return getSolidTileForChunkDesc(chunkDesc, solidityBitIndex);
    }

    public SolidTile getSolidTileForChunkDesc(ChunkDesc chunkDesc) {
        return getSolidTileForChunkDesc(chunkDesc, (byte) 0);
    }

    /**
     * Returns the current level.
     *
     * @return the current Level object
     */
    public Level getCurrentLevel() {
        return level;
    }

    public int getCurrentZone() {
        return currentZone;
    }

    /**
     * Returns the ROM zone ID for the currently loaded level.
     * Unlike {@link #getCurrentZone()} which returns the zone registry progression
     * index, this returns the game-specific zone identifier from the ROM data
     * (e.g. Sonic1Constants.ZONE_MZ = 2 for Marble Zone regardless of gameplay order).
     * Use this when comparing against game-specific zone constants.
     */
    public int getRomZoneId() {
        return level != null ? level.getZoneIndex() : -1;
    }

    /**
     * Returns the effective zone ID for zone features/water logic.
     *
     * <p>Sonic 1 SBZ3 uses the LZ zone slot ({@code id_LZ act 3}) for map/art data,
     * but gameplay systems treat it as SBZ act 3. For feature systems that are keyed
     * by zone/act (water palettes/heights), map that specific case back to SBZ.
     */
    public int getFeatureZoneId() {
        if (level == null || gameModule == null) {
            return level != null ? level.getZoneIndex() : -1;
        }
        int remapped = gameModule.getRemappedFeatureZone(currentZone, currentAct, level.getZoneIndex());
        return remapped >= 0 ? remapped : level.getZoneIndex();
    }

    /**
     * Returns the effective act index for zone features/water logic.
     */
    public int getFeatureActId() {
        if (level == null || gameModule == null) {
            return currentAct;
        }
        int remapped = gameModule.getRemappedFeatureAct(currentZone, currentAct, level.getZoneIndex());
        return remapped >= 0 ? remapped : currentAct;
    }

    public int getCurrentAct() {
        return currentAct;
    }

    /**
     * Updates a specific palette line with new color data.
     * This is used to load boss palettes during boss fights.
     *
     * @param paletteIndex The palette line to update (0-3)
     * @param paletteData  The raw Sega-format palette data (32 bytes for 16 colors)
     */
    public void updatePalette(int paletteIndex, byte[] paletteData) {
        if (level == null || paletteIndex < 0 || paletteIndex >= 4) {
            return;
        }

        // Create a new palette from the data
        Palette newPalette = new Palette();
        newPalette.fromSegaFormat(paletteData);

        // Update the level's palette object so palette cycling uses the new palette
        // This is critical - without this, palette cycling would re-cache the original
        // level palette, overwriting the boss palette we just loaded
        level.setPalette(paletteIndex, newPalette);

        // Update the graphics manager's cached palette texture
        GraphicsManager graphicsMan = GraphicsManager.getInstance();
        if (graphicsMan.isGlInitialized()) {
            graphicsMan.cachePaletteTexture(newPalette, paletteIndex);
        }

        LOGGER.fine("Updated palette line " + paletteIndex + " with " + paletteData.length + " bytes");
    }

    /**
     * Marks the foreground tilemap as dirty, forcing a rebuild on next render.
     * Call this after modifying the level layout (e.g., placing boss arena walls).
     * This is equivalent to setting Screen_redraw_flag in the original ROM.
     */
    public void invalidateForegroundTilemap() {
        if (tilemapManager != null) {
            tilemapManager.invalidateForegroundTilemap();
        }
    }

    /**
     * Reads the foreground tile descriptor currently represented by level data at world coordinates.
     * This resolves block/chunk indirection plus chunk descriptor flips, matching tilemap build logic.
     */
    public int getForegroundTileDescriptorAtWorld(int worldX, int worldY) {
        return getTileDescriptorAtWorld((byte) 0, worldX, worldY);
    }

    /**
     * Reads the background tile descriptor currently represented by level data at world coordinates.
     * This resolves block/chunk indirection plus chunk descriptor flips, matching tilemap build logic.
     */
    public int getBackgroundTileDescriptorAtWorld(int worldX, int worldY) {
        return getTileDescriptorAtWorld((byte) 1, worldX, worldY);
    }

    private int getTileDescriptorAtWorld(byte layer, int worldX, int worldY) {
        if (level == null || level.getMap() == null) {
            return 0;
        }

        int levelWidth = getLayerLevelWidthPx(layer);
        int levelHeight = getLayerLevelHeightPx(layer);
        if (levelWidth <= 0 || levelHeight <= 0) {
            return 0;
        }

        int wrappedX = Math.floorMod(worldX, levelWidth);
        int wrappedY = worldY;
        if (layer == 1 || verticalWrapEnabled) {
            wrappedY = Math.floorMod(worldY, levelHeight);
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return 0;
        }

        Block block = getBlockAtPosition(layer, wrappedX, wrappedY);
        if (block == null) {
            return 0;
        }

        int xBlockBit = (wrappedX % blockPixelSize) / LevelConstants.CHUNK_WIDTH;
        int yBlockBit = (wrappedY % blockPixelSize) / LevelConstants.CHUNK_HEIGHT;
        ChunkDesc chunkDesc = block.getChunkDesc(xBlockBit, yBlockBit);
        if (chunkDesc == null) {
            return 0;
        }

        int chunkIndex = chunkDesc.getChunkIndex();
        if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
            return 0;
        }

        Chunk chunk = level.getChunk(chunkIndex);
        if (chunk == null) {
            return 0;
        }

        int tileX = (wrappedX & (LevelConstants.CHUNK_WIDTH - 1)) / Pattern.PATTERN_WIDTH;
        int tileY = (wrappedY & (LevelConstants.CHUNK_HEIGHT - 1)) / Pattern.PATTERN_HEIGHT;
        int logicalX = chunkDesc.getHFlip() ? 1 - tileX : tileX;
        int logicalY = chunkDesc.getVFlip() ? 1 - tileY : tileY;
        PatternDesc patternDesc = chunk.getPatternDesc(logicalX, logicalY);
        if (patternDesc == null) {
            return 0;
        }

        int descriptor = patternDesc.get();
        if (chunkDesc.getHFlip()) {
            descriptor ^= 0x800;
        }
        if (chunkDesc.getVFlip()) {
            descriptor ^= 0x1000;
        }
        return descriptor & 0xFFFF;
    }

    /**
     * Overwrites one foreground tile descriptor at world coordinates in the live FG tilemap buffer.
     * Call {@link #uploadForegroundTilemap()} once after batching writes.
     *
     * @return true if tilemap bytes changed
     */
    public boolean setForegroundTileDescriptorAtWorld(int worldX, int worldY, int descriptor) {
        if (tilemapManager == null) {
            return false;
        }
        return tilemapManager.setForegroundTileDescriptorAtWorld(worldX, worldY, descriptor,
                this::getBlockAtPosition, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
    }

    /**
     * Reads a foreground tile descriptor from the live foreground tilemap buffer at world coordinates.
     * Unlike {@link #getForegroundTileDescriptorAtWorld(int, int)}, this returns the currently visible
     * descriptor after runtime tilemap writes.
     */
    public int getForegroundTileDescriptorFromTilemapAtWorld(int worldX, int worldY) {
        if (tilemapManager == null) {
            return 0;
        }
        return tilemapManager.getForegroundTileDescriptorFromTilemapAtWorld(worldX, worldY,
                this::getBlockAtPosition, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
    }

    /**
     * Uploads the current foreground tilemap bytes to the GPU renderer (if active).
     * No-op in headless mode.
     */
    public void uploadForegroundTilemap() {
        if (tilemapManager != null) {
            tilemapManager.uploadForegroundTilemap();
        }
    }

    /**
     * Marks background/foreground tilemaps and pattern lookup as dirty.
     * Use this after runtime terrain art/chunk overlays so the GPU tilemap
     * data is rebuilt on the next render.
     */
    public void invalidateAllTilemaps() {
        if (tilemapManager != null) {
            tilemapManager.invalidateAllTilemaps();
        }
    }

    /**
     * Pre-builds FG and BG tilemap data from the current level state.
     * The pre-built data can later be swapped in via {@link #swapToPrebuiltTilemaps()}
     * to avoid the expensive full-level tilemap rebuild on the transition frame.
     */
    public void prebuildTransitionTilemaps() {
        if (tilemapManager != null) {
            tilemapManager.prebuildTransitionTilemaps(this::getBlockAtPosition,
                    zoneFeatureProvider, currentZone, parallaxManager, verticalWrapEnabled);
        }
    }

    /**
     * Swaps pre-built tilemap data into the live arrays, uploads to GPU,
     * and clears FG/BG dirty flags. Still marks pattern lookup dirty
     * (cheap rebuild, needed if pattern count changed from the overlay).
     *
     * @return true if pre-built data was available and swapped in
     */
    public boolean swapToPrebuiltTilemaps() {
        if (tilemapManager == null) {
            return false;
        }
        return tilemapManager.swapToPrebuiltTilemaps();
    }

    /**
     * Returns whether pre-built transition tilemap data is available.
     */
    public boolean hasPrebuiltTilemaps() {
        return tilemapManager != null && tilemapManager.hasPrebuiltTilemaps();
    }

    /**
     * Gets the music ID for the current level.
     * Returns -1 if no level is loaded or music ID cannot be determined.
     */
    public int getCurrentLevelMusicId() {
        if (game == null || levels == null || levels.isEmpty()) {
            return -1;
        }
        try {
            int levelIdx = levels.get(currentZone).get(currentAct).getLevelIndex();
            return game.getMusicId(levelIdx);
        } catch (Exception e) {
            LOGGER.warning("Failed to get music ID for current level: " + e.getMessage());
            return -1;
        }
    }

    public Collection<ObjectSpawn> getActiveObjectSpawns() {
        if (objectManager == null) {
            return List.of();
        }
        return objectManager.getActiveSpawns();
    }

    public ObjectRenderManager getObjectRenderManager() {
        return objectRenderManager;
    }

    public RingManager getRingManager() {
        return ringManager;
    }

    public ZoneFeatureProvider getZoneFeatureProvider() {
        return zoneFeatureProvider;
    }

    public AnimatedPatternManager getAnimatedPatternManager() {
        return animatedPatternManager;
    }

    public AnimatedPaletteManager getAnimatedPaletteManager() {
        return animatedPaletteManager;
    }

    public boolean areAllRingsCollected() {
        return ringManager != null && ringManager.areAllCollected();
    }

    public ObjectManager getObjectManager() {
        return objectManager;
    }

    public void spawnLostRings(AbstractPlayableSprite player, int frameCounter) {
        if (ringManager == null || player == null) {
            return;
        }
        int count = player.getRingCount();
        if (count <= 0) {
            return;
        }
        ringManager.spawnLostRings(player, count, frameCounter);
    }

    // ── Post-load assembly methods ──────────────────────────────────────
    // Extracted from loadCurrentLevel() so profile steps can delegate to them.
    // Each method corresponds to one post-load InitStep (steps 14-20).

    /**
     * Step 14: Restore checkpoint state after loadLevel() clears it.
     * ROM: S1 Lamp_LoadInfo, S2 Obj79_LoadData, S3K Saved_zone_and_act restore.
     */
    public void restoreCheckpointState(LevelLoadContext ctx) {
        if (!ctx.hasCheckpoint() || checkpointState == null) {
            return;
        }
        checkpointState.restoreFromSaved(
                ctx.getCheckpointX(), ctx.getCheckpointY(),
                ctx.getCheckpointCameraX(), ctx.getCheckpointCameraY(),
                ctx.getCheckpointIndex());
        if (ctx.hasWaterState() && checkpointState instanceof CheckpointState cs) {
            cs.saveWaterState(ctx.getCheckpointWaterLevel(), ctx.getCheckpointWaterRoutine());
        }

        // ROM Lamp_LoadInfo: restore water level and routine after level reload.
        if (ctx.hasWaterState()) {
            int featureZone = getFeatureZoneId();
            int featureAct = getFeatureActId();
            WaterSystem waterSystem = WaterSystem.getInstance();
            if (waterSystem.hasWater(featureZone, featureAct)) {
                waterSystem.setWaterLevelDirect(featureZone, featureAct, ctx.getCheckpointWaterLevel());
                waterSystem.setWaterLevelTarget(featureZone, featureAct, ctx.getCheckpointWaterLevel());
            }
            if (zoneFeatureProvider instanceof com.openggf.game.sonic1.Sonic1ZoneFeatureProvider s1zfp
                    && s1zfp.getWaterEvents() != null) {
                s1zfp.getWaterEvents().setWaterRoutine(ctx.getCheckpointWaterRoutine());
            }
        }
    }

    /**
     * Step 15: Set player position from checkpoint or level start.
     * ROM: S1/S2 StartLocations / Obj79_LoadData, S3K Get_PlayerStart.
     */
    public void spawnPlayerAtStartPosition(LevelLoadContext ctx) {
        Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        LevelData levelData = ctx.getLevelData();
        if (levelData == null) {
            levelData = resolveLevelData();
            if (levelData == null) {
                throw new IllegalStateException(
                    "LevelLoadContext.levelData is null and could not be auto-resolved " +
                    "from the levels map (zone=" + currentZone + ", act=" + currentAct + "). " +
                    "Ensure InitGameModule has run before SpawnPlayer.");
            }
            ctx.setLevelData(levelData);
            LOGGER.info("Auto-resolved levelData from levels map: " + levelData.name());
        }

        int spawnY = -1;
        if (ctx.hasCheckpoint()) {
            player.setCentreX((short) ctx.getCheckpointX());
            player.setCentreY((short) ctx.getCheckpointY());
            spawnY = ctx.getCheckpointY();
            LOGGER.info("Set player position from checkpoint: X=" + ctx.getCheckpointX() +
                    ", Y=" + ctx.getCheckpointY() + " (center coordinates)");
        } else {
            int spawnX = levelData.getStartXPos();
            spawnY = levelData.getStartYPos();

            if (game instanceof DynamicStartPositionProvider dynamicStartProvider) {
                try {
                    int[] dynamicStart = dynamicStartProvider.getStartPosition(currentZone, currentAct);
                    if (dynamicStart != null && dynamicStart.length >= 2) {
                        spawnX = dynamicStart[0];
                        spawnY = dynamicStart[1];
                        LOGGER.info("Set player position from dynamic start provider: X=" + spawnX +
                                ", Y=" + spawnY + " (zone=" + currentZone + ", act=" + currentAct + ")");
                    } else {
                        LOGGER.info("Dynamic start provider unavailable, using levelData fallback for " +
                                levelData.name());
                    }
                } catch (IOException e) {
                    LOGGER.warning("DynamicStartPositionProvider failed, using levelData fallback: " + e.getMessage());
                }
            }

            player.setCentreX((short) spawnX);
            player.setCentreY((short) spawnY);
            LOGGER.info("Set player position from level start: X=" + spawnX +
                    ", Y=" + spawnY + " (center coordinates)" +
                    ", level: " + levelData.name());
        }
        ctx.setSpawnY(spawnY);
    }

    /**
     * Step 16: Reset player state for level start.
     * ROM: S2 InitPlayers state clear, S3K object constructor defaults.
     */
    public void resetPlayerForLevelStart(LevelLoadContext ctx) {
        Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        if (!(player instanceof AbstractPlayableSprite playable)) {
            return;
        }
        playable.resetState();
        playable.setXSpeed((short) 0);
        playable.setYSpeed((short) 0);
        playable.setGSpeed((short) 0);
        // ROM: SBZ3 (spawnY=0) spawns airborne — set air=true so gravity applies.
        playable.setAir(ctx.getSpawnY() == 0);
        LOGGER.info("Player state after loadCurrentLevel: air=" + playable.getAir() +
                ", ySpeed=" + playable.getYSpeed() + ", layer=" + player.getLayer());
        playable.setRolling(false);
        playable.setDead(false);
        playable.setHurt(false);
        playable.setDeathCountdown(0);
        playable.setInvulnerableFrames(0);
        playable.setInvincibleFrames(0);
        playable.setDirection(Direction.RIGHT);
        playable.setAngle((byte) 0);
        player.setLayer((byte) 0);
        playable.setHighPriority(false);
        playable.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);
        playable.setRingCount(0);
        AudioManager.getInstance().getBackend().setSpeedShoes(false);
    }

    /**
     * Step 17: Initialize camera for level start.
     * ROM: S1/S2 SetScreen/InitCameraValues, S3K Get_LevelSizeStart.
     */
    public void initCameraForLevel() {
        Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        if (!(player instanceof AbstractPlayableSprite playable)) {
            return;
        }
        Camera camera = Camera.getInstance();
        camera.setFrozen(false);
        camera.setFocusedSprite(playable);
        camera.updatePosition(true);

        Level currentLevel = getCurrentLevel();
        if (currentLevel != null) {
            camera.setMinX((short) currentLevel.getMinX());
            camera.setMaxX((short) currentLevel.getMaxX());
            camera.setMinY((short) currentLevel.getMinY());
            camera.setMaxY((short) currentLevel.getMaxY());
            verticalWrapEnabled = camera.isVerticalWrapEnabled();
            camera.updatePosition(true);
        }
    }

    /**
     * Step 18: Initialize level events for dynamic boundary updates.
     * All games: LevelEventProvider.initLevel(zone, act).
     */
    public void initLevelEventsForLevel() {
        LevelEventProvider levelEvents = GameModuleRegistry.getCurrent().getLevelEventProvider();
        if (levelEvents != null) {
            levelEvents.initLevel(currentZone, currentAct);
        }
    }

    /**
     * Step 19: Spawn sidekicks (Tails etc.) near the main player.
     * S2: InitPlayers multi-char. S3K: SpawnLevelMainSprites_SpawnPlayers (-$20 X, +4 Y).
     *
     * @param xOffset sidekick X offset from player (negative = behind). S2 uses -40, S3K uses -32.
     * @param yOffset sidekick Y offset from player. S2 uses 0, S3K uses +4.
     */
    public void spawnSidekicks(int xOffset, int yOffset) {
        Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            sidekick.setX((short) (player.getX() + xOffset));
            sidekick.setY((short) (player.getY() + yOffset));
            sidekick.setXSpeed((short) 0);
            sidekick.setYSpeed((short) 0);
            sidekick.setGSpeed((short) 0);
            sidekick.setAir(false);
            sidekick.setDead(false);
            sidekick.setDeathCountdown(0);
            sidekick.setHighPriority(false);
            sidekick.setDirection(Direction.RIGHT);
            if (sidekick.getCpuController() != null) {
                Camera camera = Camera.getInstance();
                sidekick.getCpuController().setLevelBounds(
                        (int) camera.getMinX(),
                        (int) camera.getMaxX(),
                        (int) Math.max(camera.getMaxY(), camera.getMaxYTarget()));
            }
        }
    }

    /**
     * Step 20: Request title card display.
     * Skipped in headless mode and when zone feature provider suppresses it.
     */
    public void requestTitleCardIfNeeded(LevelLoadContext ctx) {
        if (ctx.isShowTitleCard()
                && !graphicsManager.isHeadlessMode()
                && !(zoneFeatureProvider != null && zoneFeatureProvider.shouldSuppressInitialTitleCard(currentZone, currentAct))) {
            requestTitleCard(currentZone, currentAct);
        }
    }

    /**
     * Resolves the {@link LevelData} for the current zone and act from the
     * {@code levels} map.
     * <p>
     * Used as a fallback when {@code LevelLoadContext.levelData} has not been
     * pre-seeded by the caller. Returns {@code null} if the levels map is
     * empty or the current zone/act is out of bounds.
     */
    private LevelData resolveLevelData() {
        if (levels.isEmpty() || currentZone < 0 || currentZone >= levels.size()) {
            return null;
        }
        List<LevelData> acts = levels.get(currentZone);
        if (acts == null || currentAct < 0 || currentAct >= acts.size()) {
            return null;
        }
        return acts.get(currentAct);
    }

    /**
     * Loads the current level with title card.
     * Use this for fresh level starts (zone/act changes).
     */
    public void loadCurrentLevel() {
        loadCurrentLevel(true);
    }

    /**
     * Loads the current level for death respawn (no title card).
     */
    public void respawnPlayer() {
        loadCurrentLevel(false);
    }

    /**
     * Loads the current level with optional title card.
     *
     * @param showTitleCard true to show title card on fresh starts, false for death
     *                      respawns
     */
    private void loadCurrentLevel(boolean showTitleCard) {
        try {
            transitions.setSpecialStageReturnLevelReloadRequested(false);
            transitions.setLevelInactiveForTransition(false);

            if (levels.isEmpty()) {
                gameModule = GameModuleRegistry.getCurrent();
                refreshZoneList();
            }
            LevelData levelData = levels.get(currentZone).get(currentAct);

            LevelLoadContext ctx = new LevelLoadContext();
            ctx.setShowTitleCard(showTitleCard);
            ctx.setLevelData(levelData);
            ctx.setIncludePostLoadAssembly(true);
            ctx.snapshotCheckpoint(checkpointState);

            loadLevel(levelData.getLevelIndex(), LevelLoadMode.FULL, ctx);

            frameCounter = 0;

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void nextAct() throws IOException {
        currentAct++;
        if (currentAct >= levels.get(currentZone).size()) {
            currentAct = 0;
        }
        // Clear checkpoint when manually changing level
        if (checkpointState != null) {
            checkpointState.clear();
        }
        loadCurrentLevel();
    }

    /**
     * Advance to the next level in progression order.
     * Unlike nextAct() which wraps, this advances to next zone when acts are
     * exhausted.
     * Called by results screen after tally completes.
     */
    public void advanceToNextLevel() throws IOException {
        currentAct++;
        if (currentAct >= levels.get(currentZone).size()) {
            // Move to next zone
            currentZone++;
            currentAct = 0;
            if (currentZone >= levels.size()) {
                LOGGER.info("All zones complete!");
                currentZone = 0; // Loop back for now - TODO: end game sequence
            }
        }
        // Clear checkpoint when advancing
        if (checkpointState != null) {
            checkpointState.clear();
        }
        loadCurrentLevel();
    }

    /**
     * Advances zone/act counters without loading the level.
     * Used when entering special stage from big ring - the ROM advances
     * the level counters before entering the special stage (Got_NextLevel).
     */
    public void advanceZoneActOnly() {
        currentAct++;
        if (currentAct >= levels.get(currentZone).size()) {
            currentZone++;
            currentAct = 0;
            if (currentZone >= levels.size()) {
                currentZone = 0;
            }
        }
        if (checkpointState != null) {
            checkpointState.clear();
        }
        transitions.setSpecialStageReturnLevelReloadRequested(true);
    }

    public void loadZoneAndAct(int zone, int act) throws IOException {
        currentAct = act;
        currentZone = zone;
        // Clear checkpoint when manually changing level
        if (checkpointState != null) {
            checkpointState.clear();
        }
        loadCurrentLevel();
    }

    /**
     * Performs a ROM-aligned act transition: reloads layout + collision,
     * resets managers, applies offsets, and restores camera bounds.
     * <p>
     * This bypasses the profile system entirely because act transitions
     * are NOT level loads in the ROM — they are in-place data swaps
     * performed by level event background routines.
     * <p>
     * ROM reference: S3K zone BG event handlers (e.g. AIZ Act 2 transition
     * at sonic3k.asm). Pattern: set zone/act → Load_Level + LoadSolids →
     * Offset_ObjectsDuringTransition → clear managers → restore camera bounds.
     *
     * @param request the transition request with target zone/act, offsets, etc.
     * @throws IOException if level data loading fails
     */
    public void executeActTransition(SeamlessLevelTransitionRequest request) throws IOException {
        if (request == null) {
            return;
        }

        // Use fresh Camera singleton (the cached field can go stale after
        // Camera.resetInstance() in test teardown or level reload)
        Camera cam = Camera.getInstance();

        // Suppress music reload if requested (ROM: music continues through transition)
        if (request.preserveMusic()) {
            setSuppressNextMusicChange(true);
        }

        // 1. Set zone/act (ROM: move.b d0, Current_zone_and_act)
        currentZone = request.targetZone();
        currentAct = request.targetAct();

        // 2. Reload layout + collision only (ROM: Load_Level + LoadSolids)
        if (levels.isEmpty()) {
            gameModule = GameModuleRegistry.getCurrent();
            refreshZoneList();
        }
        LevelData levelData = levels.get(currentZone).get(currentAct);
        loadLevelData(levelData.getLevelIndex());

        // 3. Apply art mutations if requested (ROM: zone-specific art swaps)
        if (request.mutationKey() != null && !request.mutationKey().isBlank()) {
            applySeamlessMutation(request.mutationKey());
        }

        // 4. Reinitialize animated content for the newly loaded zone/act.
        // The ROM dispatches animation logic off Current_zone_and_act every frame;
        // our managers capture act-specific scripts at construction time.
        initAnimatedContent();

        // 4b. Re-register level-art-based object sheets for the new act.
        // Act-specific objects (e.g. cork floor, floating platform) use different art
        // keys per act; without re-registration they resolve to stale AIZ1 keys and
        // appear invisible after the AIZ1→AIZ2 fakeout transition.
        ObjectArtProvider artProvider = gameModule != null ? gameModule.getObjectArtProvider() : null;
        if (artProvider != null) {
            artProvider.registerLevelTileArt(level, currentZone);
            if (objectRenderManager != null) {
                objectRenderManager.ensurePatternsCached(graphicsManager, OBJECT_PATTERN_BASE);
            }
        }

        // 4c. Reinitialize water for the new act.
        // ROM: CheckLevelForWater (s3.asm:5811) runs during every level load,
        // including act transitions. Sets Water_flag, Water_level, Mean_water_level,
        // Target_water_level, Water_speed, and the zone-specific dynamic handler.
        initWater();

        // 5. Rebuild managers with new act's spawn data
        // (ROM: Load_Level swaps obj/ring pointers, then clears Dynamic_object_RAM + Ring_status_table)
        rebuildManagersForActTransition(cam);

        // 6. Apply coordinate offsets (ROM: Offset_ObjectsDuringTransition)
        applySeamlessOffsets(request, cam);

        // 7. Restore camera bounds from new level data
        restoreCameraBoundsForCurrentLevel(cam);
        cam.updatePosition(true);

        // 8. Reinitialize level events for new act
        initLevelEventsForCurrentZoneAct();

        // 9. Music override if specified
        if (request.musicOverrideId() >= 0) {
            AudioManager.getInstance().playMusic(request.musicOverrideId());
        }

        // 10. In-level title card if requested
        if (request.showInLevelTitleCard() && !graphicsManager.isHeadlessMode()) {
            requestInLevelTitleCard(currentZone, currentAct);
        }
    }

    private void restoreCameraBoundsForCurrentLevel(Camera cam) {
        Level currentLevel = getCurrentLevel();
        if (currentLevel == null) {
            return;
        }
        cam.setMinX((short) currentLevel.getMinX());
        cam.setMaxX((short) currentLevel.getMaxX());
        cam.setMinY((short) currentLevel.getMinY());
        cam.setMaxY((short) currentLevel.getMaxY());
        verticalWrapEnabled = cam.isVerticalWrapEnabled();
    }

    private void applySeamlessOffsets(SeamlessLevelTransitionRequest request, Camera cam) {
        if (request == null) {
            return;
        }
        if (cam.getFocusedSprite() instanceof AbstractPlayableSprite playable) {
            int newX = playable.getCentreX() + request.playerOffsetX();
            int newY = playable.getCentreY() + request.playerOffsetY();
            playable.setCentreX((short) newX);
            playable.setCentreY((short) newY);
            // The level reload replaced the pattern buffer; force DPLC re-upload
            // so the player sprite is visible on the next draw.
            if (playable.getSpriteRenderer() != null) {
                playable.getSpriteRenderer().invalidateDplcCache();
            }
            // Persistent insta-shield survives transitions but the ObjectManager was rebuilt
            // (rebuildManagersForActTransition creates a new one). Re-register + invalidate DPLC.
            if (playable.getInstaShieldObject() != null) {
                playable.markInstaShieldForReregistration();
                playable.getInstaShieldObject().invalidateDplcCache();
            }
        }
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            int newX = sidekick.getCentreX() + request.playerOffsetX();
            int newY = sidekick.getCentreY() + request.playerOffsetY();
            sidekick.setCentreX((short) newX);
            sidekick.setCentreY((short) newY);
            if (sidekick.getSpriteRenderer() != null) {
                sidekick.getSpriteRenderer().invalidateDplcCache();
            }
        }
        cam.setX((short) (cam.getX() + request.cameraOffsetX()));
        cam.setY((short) (cam.getY() + request.cameraOffsetY()));
    }

    /**
     * Rebuilds object and ring managers with the new act's spawn data.
     * <p>
     * ROM behavior: {@code Load_Level} swaps the object/ring position index
     * pointers, then clears {@code Dynamic_object_RAM} and
     * {@code Ring_status_table}. Because our managers hold immutable spawn
     * lists from construction, a simple {@code reset()} only clears runtime
     * state without swapping in the new act's spawn sources. We must
     * reconstruct both managers so they reference {@code level.getObjects()}
     * and {@code level.getRings()} from the newly loaded act.
     */
    private void rebuildManagersForActTransition(Camera cam) {
        int cameraX = cam.getX();

        // Rebuild ObjectManager with the new act's object spawns
        objectManager = new ObjectManager(level.getObjects(),
                gameModule.createObjectRegistry(),
                gameModule.getPlaneSwitcherObjectId(),
                gameModule.getPlaneSwitcherConfig(),
                touchResponseTable);
        CollisionSystem.getInstance().setObjectManager(objectManager);
        objectManager.reset(cameraX);

        // Rebuild RingManager with the new act's ring spawns
        RingSpriteSheet ringSpriteSheet = level.getRingSpriteSheet();
        ringManager = new RingManager(level.getRings(), ringSpriteSheet, this, touchResponseTable);
        ringManager.reset(cameraX);
        ringManager.ensurePatternsCached(graphicsManager, level.getPatternCount());

        // Re-register player dynamic objects (shield, invincibility) that were
        // orphaned when the old ObjectManager was replaced.
        // ROM: these live in Dynamic_object_RAM which persists across act transitions.
        reregisterPlayerDynamicObjects(cam.getFocusedSprite());
        for (AbstractPlayableSprite sidekick : spriteManager.getSidekicks()) {
            reregisterPlayerDynamicObjects(sidekick);
        }
    }

    private void reregisterPlayerDynamicObjects(Sprite sprite) {
        if (!(sprite instanceof AbstractPlayableSprite playable)) {
            return;
        }
        // Re-inject spawner since ObjectManager was rebuilt
        playable.setPowerUpSpawner(new DefaultPowerUpSpawner(objectManager));
        PowerUpObject shield = playable.getShieldObject();
        if (shield != null && !shield.isDestroyed()) {
            playable.getPowerUpSpawner().registerObject(shield);
        }
        PowerUpObject invincibility = playable.getInvincibilityObject();
        if (invincibility != null && !invincibility.isDestroyed()) {
            playable.getPowerUpSpawner().registerObject(invincibility);
        }
    }

    private void initLevelEventsForCurrentZoneAct() {
        LevelEventProvider levelEvents = GameModuleRegistry.getCurrent().getLevelEventProvider();
        if (levelEvents != null) {
            levelEvents.initLevel(currentZone, currentAct);
        }
    }

    public void nextZone() throws IOException {
        currentZone++;
        if (currentZone >= levels.size()) {
            currentZone = 0;
        }
        currentAct = 0;
        // Clear checkpoint when manually changing level
        if (checkpointState != null) {
            checkpointState.clear();
        }
        loadCurrentLevel();
    }

    public void loadZone(int zone) throws IOException {
        currentZone = zone;
        currentAct = 0;
        // Clear checkpoint when manually changing level
        if (checkpointState != null) {
            checkpointState.clear();
        }
        loadCurrentLevel();
    }

    public RespawnState getCheckpointState() {
        return checkpointState;
    }

    // ==================== Transition Coordinator Delegation ====================
    // Thin wrappers that delegate to LevelTransitionCoordinator.
    // External callers continue to use LevelManager.getInstance().methodName().

    /** Returns the transition coordinator. */
    public LevelTransitionCoordinator getTransitions() { return transitions; }

    /** @see LevelTransitionCoordinator#requestSpecialStageFromCheckpoint() */
    public void requestSpecialStageFromCheckpoint() { transitions.requestSpecialStageFromCheckpoint(); }

    /** @see LevelTransitionCoordinator#requestSpecialStageEntry() */
    public void requestSpecialStageEntry() { transitions.requestSpecialStageEntry(); }

    /** @see LevelTransitionCoordinator#consumeSpecialStageRequest() */
    public boolean consumeSpecialStageRequest() { return transitions.consumeSpecialStageRequest(); }

    /** @see LevelTransitionCoordinator#consumeSpecialStageReturnLevelReloadRequest() */
    public boolean consumeSpecialStageReturnLevelReloadRequest() { return transitions.consumeSpecialStageReturnLevelReloadRequest(); }

    /** @see LevelTransitionCoordinator#saveBigRingReturnPosition(int, int, int, int) */
    public void saveBigRingReturnPosition(int playerX, int playerY, int cameraX, int cameraY) { transitions.saveBigRingReturnPosition(playerX, playerY, cameraX, cameraY); }

    /** @see LevelTransitionCoordinator#hasBigRingReturnPosition() */
    public boolean hasBigRingReturnPosition() { return transitions.hasBigRingReturnPosition(); }

    /** @see LevelTransitionCoordinator#getBigRingReturnX() */
    public int getBigRingReturnX() { return transitions.getBigRingReturnX(); }
    /** @see LevelTransitionCoordinator#getBigRingReturnY() */
    public int getBigRingReturnY() { return transitions.getBigRingReturnY(); }
    /** @see LevelTransitionCoordinator#getBigRingReturnCameraX() */
    public int getBigRingReturnCameraX() { return transitions.getBigRingReturnCameraX(); }
    /** @see LevelTransitionCoordinator#getBigRingReturnCameraY() */
    public int getBigRingReturnCameraY() { return transitions.getBigRingReturnCameraY(); }

    /** @see LevelTransitionCoordinator#clearBigRingReturnPosition() */
    public void clearBigRingReturnPosition() { transitions.clearBigRingReturnPosition(); }

    /** @see LevelTransitionCoordinator#requestTitleCard(int, int) */
    public void requestTitleCard(int zone, int act) { transitions.requestTitleCard(zone, act); }

    /** @see LevelTransitionCoordinator#requestInLevelTitleCard(int, int) */
    public void requestInLevelTitleCard(int zone, int act) { transitions.requestInLevelTitleCard(zone, act); }

    /** @see LevelTransitionCoordinator#isTitleCardRequested() */
    public boolean isTitleCardRequested() { return transitions.isTitleCardRequested(); }

    /**
     * @return true if vertical wrapping is active (ROM: LZ3/SBZ2 loop sections)
     */
    public boolean isVerticalWrapEnabled() {
        return verticalWrapEnabled;
    }

    /** @see LevelTransitionCoordinator#consumeTitleCardRequest() */
    public boolean consumeTitleCardRequest() { return transitions.consumeTitleCardRequest(); }

    /** @see LevelTransitionCoordinator#consumeInLevelTitleCardRequest() */
    public boolean consumeInLevelTitleCardRequest() { return transitions.consumeInLevelTitleCardRequest(); }

    /** @see LevelTransitionCoordinator#getTitleCardZone() */
    public int getTitleCardZone() { return transitions.getTitleCardZone(); }

    /** @see LevelTransitionCoordinator#getTitleCardAct() */
    public int getTitleCardAct() { return transitions.getTitleCardAct(); }

    /** @see LevelTransitionCoordinator#getInLevelTitleCardZone() */
    public int getInLevelTitleCardZone() { return transitions.getInLevelTitleCardZone(); }

    /** @see LevelTransitionCoordinator#getInLevelTitleCardAct() */
    public int getInLevelTitleCardAct() { return transitions.getInLevelTitleCardAct(); }

    /**
     * Resets mutable state without destroying the singleton instance.
     * Replaces the reflection-based tearDown hacks in test classes.
     */
    public void resetState() {
        level = null;
        game = null;
        gameModule = null;
        objectManager = null;
        ringManager = null;
        zoneFeatureProvider = null;
        objectRenderManager = null;
        hudRenderManager = null;
        animatedPatternManager = null;
        animatedPaletteManager = null;
        checkpointState = null;
        levelGamestate = null;
        if (tilemapManager != null) {
            tilemapManager.resetState();
        }
        tilemapManager = null;
        currentZone = 0;
        currentAct = 0;
        frameCounter = 0;
        transitions.resetState();
        verticalWrapEnabled = false;
        touchResponseTable = null;
        currentShimmerStyle = 0;
        useShaderBackground = true;
        cacheLevelDimensions();
        levels.clear();
    }

    /**
     * Returns the singleton instance of LevelManager.
     *
     * @return the singleton LevelManager instance
     */
    public static synchronized LevelManager getInstance() {
        if (levelManager == null) {
            levelManager = new LevelManager();
        }
        return levelManager;
    }

    /**
     * Reset the frame counter to 0.
     * Used for deterministic visual regression testing to ensure animations
     * are in a consistent state between reference generation and test runs.
     */
    public void resetFrameCounter() {
        this.frameCounter = 0;
    }

    public void setClearColor() {
        if (level == null) {
            glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            return;
        }
        Palette.Color backdrop = resolveLevelBackdropColor();
        glClearColor(backdrop.rFloat(), backdrop.gFloat(), backdrop.bFloat(), 1.0f);
    }

    private Palette.Color resolveLevelBackdropColor() {
        if (level == null) {
            return BLACK_BACKDROP;
        }
        if (isForceBlackBackdrop()) {
            return BLACK_BACKDROP;
        }
        return level.getBackdropColor();
    }

    private boolean isForceBlackBackdrop() {
        if (level instanceof Sonic2Level) {
            int zoneId = ((Sonic2Level) level).getZoneIndex();
            // Zone 11 (0xB) is MCZ
            return zoneId == 11;
        }
        return false;
    }

    /**
     * Reloads the current level's palettes into the graphics manager.
     * Call this after returning from special stage to restore level colors.
     */
    public void reloadLevelPalettes() {
        if (level == null) {
            LOGGER.warning("Cannot reload palettes: no level loaded");
            return;
        }

        int paletteCount = level.getPaletteCount();
        for (int i = 0; i < paletteCount; i++) {
            Palette palette = level.getPalette(i);
            if (palette != null) {
                graphicsManager.cachePaletteTexture(palette, i);
            }
        }
        LOGGER.fine("Reloaded " + paletteCount + " level palettes");
    }

    // ==================== Transition Request Delegation ====================
    // These delegate to LevelTransitionCoordinator so external callers keep working.

    /** @see LevelTransitionCoordinator#requestRespawn() */
    public void requestRespawn() { transitions.requestRespawn(); }

    /** @see LevelTransitionCoordinator#consumeRespawnRequest() */
    public boolean consumeRespawnRequest() { return transitions.consumeRespawnRequest(); }

    /** @see LevelTransitionCoordinator#requestNextAct() */
    public void requestNextAct() { transitions.requestNextAct(); }

    /** @see LevelTransitionCoordinator#consumeNextActRequest() */
    public boolean consumeNextActRequest() { return transitions.consumeNextActRequest(); }

    /** @see LevelTransitionCoordinator#requestNextZone() */
    public void requestNextZone() { transitions.requestNextZone(); }

    /** @see LevelTransitionCoordinator#consumeNextZoneRequest() */
    public boolean consumeNextZoneRequest() { return transitions.consumeNextZoneRequest(); }

    /** @see LevelTransitionCoordinator#requestZoneAndAct(int, int) */
    public void requestZoneAndAct(int zone, int act) { transitions.requestZoneAndAct(zone, act); }

    /** @see LevelTransitionCoordinator#requestZoneAndAct(int, int, boolean) */
    public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) { transitions.requestZoneAndAct(zone, act, deactivateLevelNow); }

    /** @see LevelTransitionCoordinator#requestSeamlessTransition(SeamlessLevelTransitionRequest) */
    public void requestSeamlessTransition(SeamlessLevelTransitionRequest request) { transitions.requestSeamlessTransition(request); }

    /** @see LevelTransitionCoordinator#consumeSeamlessTransitionRequest() */
    public SeamlessLevelTransitionRequest consumeSeamlessTransitionRequest() { return transitions.consumeSeamlessTransitionRequest(); }

    /**
     * Applies a seamless transition immediately.
     * <p>
     * Routes through {@link #executeActTransition} for RELOAD types,
     * which bypasses the profile system and matches ROM behavior.
     */
    public void applySeamlessTransition(SeamlessLevelTransitionRequest request) {
        if (request == null) {
            return;
        }

        try {
            transitions.setSpecialStageReturnLevelReloadRequested(false);
            switch (request.type()) {
                case MUTATE_ONLY -> applySeamlessMutation(request.mutationKey());
                case RELOAD_SAME_LEVEL -> {
                    SeamlessLevelTransitionRequest adjusted = SeamlessLevelTransitionRequest
                            .builder(SeamlessLevelTransitionRequest.TransitionType.RELOAD_TARGET_LEVEL)
                            .targetZoneAct(currentZone, currentAct)
                            .deactivateLevelNow(request.deactivateLevelNow())
                            .preserveMusic(request.preserveMusic())
                            .showInLevelTitleCard(request.showInLevelTitleCard())
                            .playerOffset(request.playerOffsetX(), request.playerOffsetY())
                            .cameraOffset(request.cameraOffsetX(), request.cameraOffsetY())
                            .mutationKey(request.mutationKey())
                            .musicOverrideId(request.musicOverrideId())
                            .build();
                    executeActTransition(adjusted);
                }
                case RELOAD_TARGET_LEVEL -> executeActTransition(request);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to apply seamless transition", e);
        } finally {
            transitions.setLevelInactiveForTransition(false);
        }
    }

    private void applySeamlessMutation(String mutationKey) {
        S3kSeamlessMutationExecutor.apply(this, mutationKey);
    }

    /** @see LevelTransitionCoordinator#consumeZoneActRequest() */
    public boolean consumeZoneActRequest() { return transitions.consumeZoneActRequest(); }

    /** @see LevelTransitionCoordinator#getRequestedZone() */
    public int getRequestedZone() { return transitions.getRequestedZone(); }

    /** @see LevelTransitionCoordinator#getRequestedAct() */
    public int getRequestedAct() { return transitions.getRequestedAct(); }

    /** @see LevelTransitionCoordinator#isLevelInactiveForTransition() */
    public boolean isLevelInactiveForTransition() { return transitions.isLevelInactiveForTransition(); }

    /** @see LevelTransitionCoordinator#requestCreditsTransition() */
    public void requestCreditsTransition() { transitions.requestCreditsTransition(); }

    /** @see LevelTransitionCoordinator#consumeCreditsRequest() */
    public boolean consumeCreditsRequest() { return transitions.consumeCreditsRequest(); }

    /** @see LevelTransitionCoordinator#setForceHudSuppressed(boolean) */
    public void setForceHudSuppressed(boolean suppressed) { transitions.setForceHudSuppressed(suppressed); }

    /** @see LevelTransitionCoordinator#setSuppressNextMusicChange(boolean) */
    public void setSuppressNextMusicChange(boolean suppress) { transitions.setSuppressNextMusicChange(suppress); }

    /**
     * Finds the offset from a reference position to the first pattern within a tile index range.
     * Scans the level chunks around the reference position looking for patterns that use
     * VRAM tile indices within the specified range.
     * <p>
     * This is used by CNZ slot machines to find where the slot display tiles are positioned
     * relative to the cage object, as this varies between CNZ1 (below) and CNZ2 (above).
     *
     * @param refX       Reference X position (world coordinates, typically cage center)
     * @param refY       Reference Y position (world coordinates, typically cage center)
     * @param minTileIdx Minimum VRAM tile index to search for (inclusive)
     * @param maxTileIdx Maximum VRAM tile index to search for (inclusive)
     * @param searchRadius Radius in pixels to search around the reference position
     * @return int[2] with {offsetX, offsetY} from ref to first matching pattern center,
     *         or null if no matching pattern found
     */
    public int[] findPatternOffset(int refX, int refY, int minTileIdx, int maxTileIdx, int searchRadius) {
        if (level == null) {
            return null;
        }

        Map map = level.getMap();
        if (map == null) {
            return null;
        }

        // Calculate search bounds in world coordinates
        int startX = refX - searchRadius;
        int startY = refY - searchRadius;
        int endX = refX + searchRadius;
        int endY = refY + searchRadius;

        // Clamp to level bounds
        startX = Math.max(startX, level.getMinX());
        startY = Math.max(startY, level.getMinY());
        endX = Math.min(endX, level.getMaxX());
        endY = Math.min(endY, level.getMaxY());

        // Scan through patterns (8x8 pixel grid)
        for (int worldY = startY; worldY < endY; worldY += 8) {
            for (int worldX = startX; worldX < endX; worldX += 8) {
                int tileIdx = getPatternIndexAt(worldX, worldY, map);
                if (tileIdx >= minTileIdx && tileIdx <= maxTileIdx) {
                    // Found a matching pattern - snap to actual pattern boundary
                    // Patterns are 8x8 and aligned to 8-pixel grid within the level
                    int patternLeftX = worldX - (Math.floorMod(worldX, 8));
                    int patternTopY = worldY - (Math.floorMod(worldY, 8));
                    // Calculate offset from ref to pattern center
                    int offsetX = (patternLeftX + 4) - refX;
                    int offsetY = (patternTopY + 4) - refY;
                    return new int[]{offsetX, offsetY};
                }
            }
        }

        return null;
    }

    /**
     * Gets the VRAM tile index for the pattern at the given world coordinates.
     * Traverses the map -> block -> chunk -> pattern hierarchy.
     *
     * @param worldX World X coordinate
     * @param worldY World Y coordinate
     * @param map    The level map
     * @return The pattern's VRAM tile index, or -1 if out of bounds
     */
    private int getPatternIndexAt(int worldX, int worldY, Map map) {
        try {
            // Block is 128x128 pixels
            int blockX = worldX / blockPixelSize;
            int blockY = worldY / blockPixelSize;

            if (blockX < 0 || blockX >= map.getWidth() || blockY < 0 || blockY >= map.getHeight()) {
                return -1;
            }

            // Get block index from map (layer 0 = foreground)
            int blockIdx = map.getValue(0, blockX, blockY) & 0xFF;
            if (blockIdx == 0 || blockIdx >= level.getBlockCount()) {
                return -1;
            }

            Block block = level.getBlock(blockIdx);
            if (block == null) {
                return -1;
            }

            // Chunk within block (16x16 pixels each, 8x8 grid of chunks)
            int chunkX = (worldX % blockPixelSize) / 16;
            int chunkY = (worldY % blockPixelSize) / 16;
            ChunkDesc chunkDesc = block.getChunkDesc(chunkX, chunkY);
            if (chunkDesc == null) {
                return -1;
            }

            int chunkIdx = chunkDesc.getChunkIndex();
            if (chunkIdx == 0 || chunkIdx >= level.getChunkCount()) {
                return -1;
            }

            Chunk chunk = level.getChunk(chunkIdx);
            if (chunk == null) {
                return -1;
            }

            // Pattern within chunk (8x8 pixels each, 2x2 grid)
            int patternX = (worldX % 16) / 8;
            int patternY = (worldY % 16) / 8;
            PatternDesc patternDesc = chunk.getPatternDesc(patternX, patternY);
            if (patternDesc == null) {
                return -1;
            }

            return patternDesc.getPatternIndex();
        } catch (Exception e) {
            return -1;
        }
    }
}
