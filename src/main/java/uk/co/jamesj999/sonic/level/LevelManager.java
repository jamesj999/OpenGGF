package uk.co.jamesj999.sonic.level;

import uk.co.jamesj999.sonic.game.GameServices;

import uk.co.jamesj999.sonic.Engine;
import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.configuration.SonicConfiguration;
import uk.co.jamesj999.sonic.configuration.SonicConfigurationService;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.AnimatedPaletteProvider;
import uk.co.jamesj999.sonic.data.AnimatedPatternProvider;
import uk.co.jamesj999.sonic.data.PlayerSpriteArtProvider;
import uk.co.jamesj999.sonic.data.SpindashDustArtProvider;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.GameModule;
import uk.co.jamesj999.sonic.game.GameModuleRegistry;
import uk.co.jamesj999.sonic.game.PhysicsFeatureSet;
import uk.co.jamesj999.sonic.game.LevelEventProvider;
import uk.co.jamesj999.sonic.game.LevelState;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.RespawnState;
import uk.co.jamesj999.sonic.game.DynamicStartPositionProvider;
import uk.co.jamesj999.sonic.game.ZoneFeatureProvider;

import uk.co.jamesj999.sonic.debug.DebugObjectArtViewer;
import uk.co.jamesj999.sonic.debug.DebugOption;
import uk.co.jamesj999.sonic.debug.DebugOverlayManager;
import uk.co.jamesj999.sonic.debug.DebugOverlayPalette;
import uk.co.jamesj999.sonic.debug.DebugOverlayToggle;
import uk.co.jamesj999.sonic.debug.DebugRenderContext;
import uk.co.jamesj999.sonic.debug.PerformanceProfiler;
import uk.co.jamesj999.sonic.level.objects.ObjectInstance;
import uk.co.jamesj999.sonic.level.objects.HudRenderManager;
import uk.co.jamesj999.sonic.graphics.GLCommand;
import uk.co.jamesj999.sonic.graphics.GLCommandGroup;
import uk.co.jamesj999.sonic.audio.AudioManager;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.graphics.ShaderProgram;
import uk.co.jamesj999.sonic.graphics.TilemapGpuRenderer;
import uk.co.jamesj999.sonic.graphics.TilePriorityFBO;
import uk.co.jamesj999.sonic.graphics.WaterShaderProgram;
import uk.co.jamesj999.sonic.graphics.RenderPriority;
import uk.co.jamesj999.sonic.graphics.PatternRenderCommand;
import uk.co.jamesj999.sonic.graphics.PatternAtlas;
import uk.co.jamesj999.sonic.level.render.SpritePieceRenderer;
import uk.co.jamesj999.sonic.level.render.BackgroundRenderer;
// import uk.co.jamesj999.sonic.level.ParallaxManager; -> Removed unused
import uk.co.jamesj999.sonic.level.objects.ObjectManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpawn;
import uk.co.jamesj999.sonic.level.objects.TouchResponseTable;
import uk.co.jamesj999.sonic.level.rings.RingManager;
import uk.co.jamesj999.sonic.level.rings.RingSpriteSheet;
import uk.co.jamesj999.sonic.level.rings.RingSpawn;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.Pattern;
import uk.co.jamesj999.sonic.level.animation.AnimatedPaletteManager;
import uk.co.jamesj999.sonic.level.animation.AnimatedPatternManager;
import uk.co.jamesj999.sonic.physics.CollisionSystem;
import uk.co.jamesj999.sonic.physics.Direction;
import uk.co.jamesj999.sonic.physics.Sensor;
import uk.co.jamesj999.sonic.physics.SensorResult;
import uk.co.jamesj999.sonic.sprites.Sprite;
import uk.co.jamesj999.sonic.sprites.SensorConfiguration;
import uk.co.jamesj999.sonic.sprites.art.SpriteArtSet;
import uk.co.jamesj999.sonic.game.sonic2.constants.Sonic2Constants;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kGameModule;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kPlayerArt;
import uk.co.jamesj999.sonic.sprites.managers.SpindashDustController;
import uk.co.jamesj999.sonic.sprites.managers.SpriteManager;
import uk.co.jamesj999.sonic.sprites.managers.TailsTailsController;
import uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite;
import uk.co.jamesj999.sonic.sprites.playable.Tails;
import uk.co.jamesj999.sonic.sprites.render.PlayerSpriteRenderer;

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
    private static final float SWITCHER_DEBUG_R = 1.0f;
    private static final float SWITCHER_DEBUG_G = 0.55f;
    private static final float SWITCHER_DEBUG_B = 0.1f;
    private static final float SWITCHER_DEBUG_ALPHA = 0.35f;
    private static final int OBJECT_PATTERN_BASE = 0x20000;
    private static final int HUD_PATTERN_BASE = 0x28000;
    private static final Palette.Color BLACK_BACKDROP = new Palette.Color((byte) 0, (byte) 0, (byte) 0);
    private static LevelManager levelManager;
    private Level level;
    private int blockPixelSize = 128;  // cached from level
    private int chunksPerBlockSide = 8;
    private Game game;
    private GameModule gameModule;

    public Game getGame() {
        return game;
    }

    public GameModule getGameModule() {
        return gameModule;
    }

    private final GraphicsManager graphicsManager = GraphicsManager.getInstance();
    private final SpriteManager spriteManager = SpriteManager.getInstance();
    private final SonicConfigurationService configService = SonicConfigurationService.getInstance();
    private final DebugOverlayManager overlayManager = GameServices.debugOverlay();
    private final PerformanceProfiler profiler = PerformanceProfiler.getInstance();
    private final List<List<LevelData>> levels = new ArrayList<>();
    private int currentAct = 0;
    private int currentZone = 0;
    private int frameCounter = 0;
    private int currentShimmerStyle = 0;
    private ObjectManager objectManager;
    private RingManager ringManager;
    private ZoneFeatureProvider zoneFeatureProvider;
    private ObjectRenderManager objectRenderManager;
    private HudRenderManager hudRenderManager;
    private AnimatedPatternManager animatedPatternManager;
    private AnimatedPaletteManager animatedPaletteManager;
    private RespawnState checkpointState;
    private LevelState levelGamestate;

    // GPU tilemap data (Track B)
    private byte[] backgroundTilemapData;
    private int backgroundTilemapWidthTiles;
    private int backgroundTilemapHeightTiles;
    private boolean backgroundTilemapDirty = true;

    private byte[] foregroundTilemapData;
    private int foregroundTilemapWidthTiles;
    private int foregroundTilemapHeightTiles;
    private boolean foregroundTilemapDirty = true;

    private byte[] patternLookupData;
    private int patternLookupSize;
    private boolean patternLookupDirty = true;
    private boolean multiAtlasWarningLogged = false;

    // Pre-built tilemap data for stutter-free terrain transitions (AIZ intro)
    private byte[] prebuiltFgTilemap;
    private int prebuiltFgWidth;
    private int prebuiltFgHeight;
    private byte[] prebuiltBgTilemap;
    private int prebuiltBgWidth;
    private int prebuiltBgHeight;

    private boolean specialStageRequestedFromCheckpoint;
    private boolean specialStageReturnLevelReloadRequested;
    private boolean titleCardRequested;
    private int titleCardZone = -1;
    private int titleCardAct = -1;

    // Transition request flags (for fade-coordinated transitions)
    private boolean respawnRequested;
    private boolean nextActRequested;
    private boolean nextZoneRequested;
    private boolean specificZoneActRequested;

    // ROM: LZ3/SBZ2 vertical wrapping — FG layer wraps Y instead of clamping
    private boolean verticalWrapEnabled = false;
    private boolean levelInactiveForTransition;
    private int requestedZone = -1;
    private int requestedAct = -1;

    // Background rendering support
    private final ParallaxManager parallaxManager = ParallaxManager.getInstance();
    private boolean useShaderBackground = true; // Feature flag for shader background

    // Pre-allocated lists for debug overlay rendering (avoids per-frame allocations)
    private final List<GLCommand> debugObjectCommands = new ArrayList<>(256);
    private final List<GLCommand> debugSwitcherLineCommands = new ArrayList<>(128);
    private final List<GLCommand> debugSwitcherAreaCommands = new ArrayList<>(128);
    private final List<GLCommand> debugRingCommands = new ArrayList<>(256);
    private final List<GLCommand> debugBoxCommands = new ArrayList<>(512);
    private final List<GLCommand> debugCenterCommands = new ArrayList<>(256);
    private final List<GLCommand> collisionCommands = new ArrayList<>(256);
    private final List<GLCommand> priorityDebugCommands = new ArrayList<>(256);
    private final List<GLCommand> sensorCommands = new ArrayList<>(128);
    private final List<GLCommand> cameraBoundsCommands = new ArrayList<>(64);

    // Reusable PatternDesc to avoid per-iteration allocations in tight loops
    private final PatternDesc reusablePatternDesc = new PatternDesc();

    // Cached screen dimensions (avoids repeated config service lookups)
    private final int cachedScreenWidth = configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS);
    private final int cachedScreenHeight = configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);

    // Camera reference for frustum culling
    private final Camera camera = Camera.getInstance();

    /**
     * Checks if a point is within the visible camera frustum with optional padding.
     * Used to cull debug overlay commands for off-screen objects.
     *
     * @param x       world X coordinate
     * @param y       world Y coordinate
     * @param padding extra pixels around screen edges to include
     * @return true if the point is visible (or near-visible with padding)
     */
    private boolean isInCameraFrustum(int x, int y, int padding) {
        int camX = camera.getX();
        int camY = camera.getY();
        return x >= camX - padding && x <= camX + cachedScreenWidth + padding
                && y >= camY - padding && y <= camY + cachedScreenHeight + padding;
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
        try {
            Rom rom = GameServices.rom().getRom();
            parallaxManager.load(rom);
            gameModule = GameModuleRegistry.getCurrent();
            refreshZoneList();
            game = gameModule.createGame(rom);
            AudioManager audioManager = AudioManager.getInstance();
            audioManager.setAudioProfile(gameModule.getAudioProfile());
            audioManager.setRom(rom);
            audioManager.setSoundMap(game.getSoundMap());
            audioManager.resetRingSound();
            audioManager.playMusic(game.getMusicId(levelIndex));
            level = game.loadLevel(levelIndex);
            blockPixelSize = level.getBlockPixelSize();
            chunksPerBlockSide = level.getChunksPerBlockSide();
        backgroundTilemapDirty = true;
        foregroundTilemapDirty = true;
        patternLookupDirty = true;
        multiAtlasWarningLogged = false;
        backgroundTilemapData = null;
            foregroundTilemapData = null;
            patternLookupData = null;
            prebuiltFgTilemap = null;
            prebuiltBgTilemap = null;
            initAnimatedPatterns();
            initAnimatedPalettes();
            RomByteReader romReader = RomByteReader.fromRom(rom);
            TouchResponseTable touchResponseTable = gameModule.createTouchResponseTable(romReader);
            objectManager = new ObjectManager(level.getObjects(),
                    gameModule.createObjectRegistry(),
                    gameModule.getPlaneSwitcherObjectId(),
                    gameModule.getPlaneSwitcherConfig(),
                    touchResponseTable);
            // Wire up CollisionSystem with ObjectManager for unified collision pipeline
            CollisionSystem.getInstance().setObjectManager(objectManager);
            // Reset switch state for new level (Sonic 1 f_switch array)
            uk.co.jamesj999.sonic.game.sonic1.Sonic1SwitchManager.getInstance().reset();
            // Reset v_obj6B singleton flag for SBZ3 StomperDoor
            uk.co.jamesj999.sonic.game.sonic1.objects.Sonic1StomperDoorObjectInstance.resetSbz3Flag();
            // Reset conveyor belt state for new level (Sonic 1 f_conveyrev + v_obj63)
            uk.co.jamesj999.sonic.game.sonic1.Sonic1ConveyorState.getInstance().reset();
            // Reset camera state from previous level (signpost may have locked it)
            Camera camera = Camera.getInstance();
            camera.setFrozen(false);
            camera.setMinX((short) 0);
            camera.setMaxX((short) (level.getMap().getWidth() * blockPixelSize));
            objectManager.reset(camera.getX());
            // Reset game-specific object state for new level
            gameModule.onLevelLoad();
            RingSpriteSheet ringSpriteSheet = level.getRingSpriteSheet();
            ringManager = new RingManager(level.getRings(), ringSpriteSheet, this, touchResponseTable);
            ringManager.reset(Camera.getInstance().getX());
            ringManager.ensurePatternsCached(graphicsManager, level.getPatternCount());
            // Initialize zone-specific features (CNZ bumpers, CPZ pylon, water surface, etc.)
            zoneFeatureProvider = gameModule.getZoneFeatureProvider();
            if (zoneFeatureProvider != null) {
                zoneFeatureProvider.initZoneFeatures(rom, getFeatureZoneId(), getFeatureActId(), camera.getX());
                // Cache zone feature patterns (water surface, etc.)
                int waterPatternBase = 0x30000; // High offset to avoid collision
                zoneFeatureProvider.ensurePatternsCached(graphicsManager, waterPatternBase);
            }
            initObjectArt();
            initPlayerSpriteArt();
            resetPlayerState();
            // Initialize checkpoint state for new level
            if (checkpointState == null) {
                checkpointState = gameModule.createRespawnState();
            }
            checkpointState.clear();
            levelGamestate = gameModule.createLevelState();

            // Initialize water system for this level.
            // Only attempt water loading for zones the game module declares as water zones,
            // otherwise S3K object IDs (e.g. 0x04 = CollapsingPlatform) get misinterpreted
            // as water surface objects by WaterSystem.extractWaterHeight().
            // S1 water is already loaded by the ZoneFeatureProvider above.
            WaterSystem waterSystem = WaterSystem.getInstance();
            if (zoneFeatureProvider != null && zoneFeatureProvider.hasWater(getFeatureZoneId())) {
                if (!waterSystem.hasWater(getFeatureZoneId(), getFeatureActId())) {
                    waterSystem.loadForLevel(rom, getFeatureZoneId(), getFeatureActId(), level.getObjects());
                }
            }

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
                    maxBgWidth = Math.max(cachedScreenWidth, VDP_BG_PLANE_WIDTH_PX);
                }
                int fboHeight = 256 + LevelConstants.CHUNK_HEIGHT;
                graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM,
                        (cx, cy, cw, ch) -> bgRenderer.ensureCapacity(maxBgWidth, fboHeight)));
            }
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load level " + levelIndex, e);
            throw e;
        } catch (Exception e) {
            LOGGER.log(SEVERE, "Unexpected error while loading level " + levelIndex, e);
            throw new IOException("Failed to load level due to unexpected error.", e);
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
        uk.co.jamesj999.sonic.game.sonic2.OscillationManager.update(frameCounter);

        if (objectManager != null) {
            Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
            AbstractPlayableSprite sidekick = spriteManager.getSidekick();
            objectManager.update(Camera.getInstance().getX(), playable, sidekick, frameCounter + 1);
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

        // Update dynamic water levels (for rising water in CPZ2, etc.)
        WaterSystem.getInstance().update();

        Sprite player = null;
        AbstractPlayableSprite playable = null;
        boolean needsPlayer = ringManager != null || zoneFeatureProvider != null || levelGamestate != null;
        if (needsPlayer) {
            player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            playable = player instanceof AbstractPlayableSprite ? (AbstractPlayableSprite) player : null;
        }
        if (ringManager != null) {
            ringManager.update(Camera.getInstance().getX(), playable, frameCounter + 1);
            ringManager.updateLostRings(playable, frameCounter + 1);
            // ROM: CPU Tails can also collect rings in 1P mode
            AbstractPlayableSprite sidekick = spriteManager.getSidekick();
            if (sidekick != null && !sidekick.getDead()) {
                ringManager.update(Camera.getInstance().getX(), sidekick, frameCounter + 1);
                ringManager.updateLostRings(sidekick, frameCounter + 1);
            }
        }
        // Update zone-specific features (CNZ bumpers, etc.)
        if (zoneFeatureProvider != null && level != null) {
            zoneFeatureProvider.update(playable, Camera.getInstance().getX(), getFeatureZoneId());
        }
        if (levelGamestate != null) {
            if (!isHudSuppressed()) {
                levelGamestate.update();
            }
            if (levelGamestate.isTimeOver() && playable != null && !playable.getDead()) {
                playable.applyHurtOrDeath(0, AbstractPlayableSprite.DamageCause.TIME_OVER, false);
            }
        }

        // Update water state for player.
        // Use effective feature zone/act so S1 SBZ3 (loaded from LZ act 4 slot)
        // resolves to SBZ3 water behavior while retaining LZ tile/object resources.
        WaterSystem waterSystem = WaterSystem.getInstance();
        int featureZone = getFeatureZoneId();
        int featureAct = getFeatureActId();
        if (level != null && playable != null && waterSystem.hasWater(featureZone, featureAct)) {
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
        if (!(game instanceof PlayerSpriteArtProvider provider)) {
            return;
        }
        Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        if (!(player instanceof AbstractPlayableSprite playable)) {
            return;
        }
        try {
            SpriteArtSet artSet = provider.loadPlayerSpriteArt(playable.getCode());
            if (artSet == null || artSet.bankSize() <= 0 || artSet.mappingFrames().isEmpty()
                    || artSet.dplcFrames().isEmpty()) {
                playable.setSpriteRenderer(null);
                return;
            }
            PlayerSpriteRenderer renderer = new PlayerSpriteRenderer(artSet);
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

        // Also initialize art for sidekick (CPU-controlled Tails)
        AbstractPlayableSprite sidekick = spriteManager.getSidekick();
        if (sidekick != null) {
            try {
                SpriteArtSet sidekickArt = provider.loadPlayerSpriteArt(sidekick.getCode());
                if (sidekickArt != null && sidekickArt.bankSize() > 0 && !sidekickArt.mappingFrames().isEmpty()
                        && !sidekickArt.dplcFrames().isEmpty()) {
                    PlayerSpriteRenderer sidekickRenderer = new PlayerSpriteRenderer(sidekickArt);
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
                }
            } catch (IOException e) {
                LOGGER.log(SEVERE, "Failed to load sidekick sprite art.", e);
            }
        }
    }

    private void resetPlayerState() {
        Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
        if (player instanceof AbstractPlayableSprite playable) {
            playable.resetState();
        }
        AbstractPlayableSprite sidekick = spriteManager.getSidekick();
        if (sidekick != null) {
            sidekick.resetState();
            if (sidekick.getCpuController() != null) {
                sidekick.getCpuController().reset();
            }
        }
    }

    private void initSpindashDust(AbstractPlayableSprite playable) {
        if (!(game instanceof SpindashDustArtProvider dustProvider)) {
            playable.setSpindashDustController(null);
            return;
        }
        try {
            SpriteArtSet dustArt = dustProvider.loadSpindashDustArt(playable.getCode());
            if (dustArt == null || dustArt.bankSize() <= 0 || dustArt.mappingFrames().isEmpty()
                    || dustArt.dplcFrames().isEmpty()) {
                playable.setSpindashDustController(null);
                return;
            }
            PlayerSpriteRenderer dustRenderer = new PlayerSpriteRenderer(dustArt);
            dustRenderer.ensureCached(graphicsManager);
            playable.setSpindashDustController(new SpindashDustController(playable, dustRenderer));
        } catch (IOException e) {
            LOGGER.log(SEVERE, "Failed to load spindash dust art.", e);
            playable.setSpindashDustController(null);
        }
    }

    private void initTailsTails(AbstractPlayableSprite playable, SpriteArtSet artSet) {
        if (!(playable instanceof Tails)) {
            playable.setTailsTailsController(null);
            return;
        }
        boolean isS3k = gameModule instanceof Sonic3kGameModule;
        SpriteArtSet tailsArt;
        if (isS3k) {
            // S3K: Obj05 uses a completely separate art/mapping/DPLC set
            try {
                Rom rom = GameServices.rom().getRom();
                Sonic3kPlayerArt s3kArt = new Sonic3kPlayerArt(RomByteReader.fromRom(rom));
                tailsArt = s3kArt.loadTailsTail();
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
        PlayerSpriteRenderer tailsRenderer = new PlayerSpriteRenderer(tailsArt);
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
        if (superCtrl != null) {
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
            if (provider instanceof uk.co.jamesj999.sonic.game.sonic2.Sonic2ObjectArtProvider sonic2Provider) {
                sonic2Provider.registerSmashableGroundSheet(level);
                sonic2Provider.registerSteamSpringPistonSheet(level);
                objectRenderManager.ensurePatternsCached(graphicsManager, OBJECT_PATTERN_BASE);
            }
            if (provider instanceof uk.co.jamesj999.sonic.game.sonic1.Sonic1ObjectArtProvider sonic1Provider) {
                sonic1Provider.registerPlatformSheet(level, zoneIndex);
                sonic1Provider.registerCollapsingLedgeSheet(level, zoneIndex);
                sonic1Provider.registerMzBrickSheet(level, zoneIndex);
                sonic1Provider.registerLargeGrassyPlatformSheet(level, zoneIndex);
                sonic1Provider.registerLavaWallSheet(level, zoneIndex);
                sonic1Provider.registerFloatingBlockSheet(level, zoneIndex);
                sonic1Provider.registerCirclingPlatformSheet(level, zoneIndex);
                sonic1Provider.registerStaircaseSheet(level, zoneIndex);
                sonic1Provider.registerElevatorSheet(level, zoneIndex);
                if (zoneIndex == uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.ZONE_SYZ) {
                    sonic1Provider.registerSpinningLightSheet(level);
                    sonic1Provider.registerBossBlockSheet(level);
                }
                // SBZ3 (LZ zone slot) big diagonal door uses level tile art
                if (zoneIndex == uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.ZONE_LZ) {
                    sonic1Provider.registerSbz3BigDoorSheet(level, zoneIndex);
                }
                objectRenderManager.ensurePatternsCached(graphicsManager, OBJECT_PATTERN_BASE);
            }
            if (provider instanceof uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtProvider s3kProvider) {
                s3kProvider.registerLevelArtSheets(level, zoneIndex);
                objectRenderManager.ensurePatternsCached(graphicsManager, OBJECT_PATTERN_BASE);
            }

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
        return zoneFeatureProvider != null
                && zoneFeatureProvider.shouldSuppressHud(currentZone, currentAct);
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
        if (level == null) {
            LOGGER.warning("No level loaded to draw.");
            return;
        }

        Camera camera = Camera.getInstance();
        int cameraX = camera.getX();
        int cameraY = camera.getY();
        int cameraWidth = camera.getWidth();
        int cameraHeight = camera.getHeight();

        // Calculate drawing bounds, adjusted to include partially visible tiles
        int drawX = cameraX;
        int drawY = cameraY;
        int levelWidth = level.getMap().getWidth() * blockPixelSize;
        int levelHeight = level.getMap().getHeight() * blockPixelSize;

        int xLeftBound = Math.max(drawX, 0);
        int xRightBound = Math.min(cameraX + cameraWidth, levelWidth);
        int yTopBound = Math.max(drawY, 0);
        int yBottomBound = Math.min(cameraY + cameraHeight + LevelConstants.CHUNK_HEIGHT, levelHeight);

        List<GLCommand> commands = new ArrayList<>(256);

        // Iterate over the visible area of the level
        int count = 0;
        int maxCount = level.getPatternCount();

        if (Engine.debugOption.ordinal() > LevelConstants.MAX_PALETTES) {
            Engine.debugOption = DebugOption.A;
        }

        for (int y = yTopBound; y <= yBottomBound; y += Pattern.PATTERN_HEIGHT) {
            for (int x = xLeftBound; x <= xRightBound; x += Pattern.PATTERN_WIDTH) {
                if (count < maxCount) {
                    reusablePatternDesc.setPaletteIndex(Engine.debugOption.ordinal());
                    reusablePatternDesc.setPatternIndex(count);
                    graphicsManager.renderPattern(reusablePatternDesc, x, y);
                    count++;
                }
            }
        }

        // Register all collected drawing commands with the graphics manager
        graphicsManager.registerCommand(new GLCommandGroup(GL_POINTS, commands));

    }

    /**
     * Renders the current level by processing and displaying collision data.
     * This is currently for debugging purposes to visualize collision areas.
     */
    public void draw() {
        drawWithSpritePriority(null);
    }

    public void drawWithSpritePriority(SpriteManager spriteManager) {
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
        if (overlayManager.isEnabled(DebugOverlayToggle.COLLISION_VIEW)) {
            generateCollisionDebugCommands(collisionCommands, camera);
        }

        // Render collision debug overlay on top of foreground tiles
        if (!collisionCommands.isEmpty() && overlayManager.isEnabled(DebugOverlayToggle.COLLISION_VIEW)) {
            for (GLCommand cmd : collisionCommands) {
                graphicsManager.registerCommand(cmd);
            }
        }

        // Generate tile priority debug overlay commands (shows high-priority tiles in red)
        if (overlayManager.isEnabled(DebugOverlayToggle.TILE_PRIORITY_VIEW)) {
            priorityDebugCommands.clear();
            generateTilePriorityDebugCommands(priorityDebugCommands, camera);
        }

        // Render tile priority debug overlay on top of foreground tiles
        if (!priorityDebugCommands.isEmpty() && overlayManager.isEnabled(DebugOverlayToggle.TILE_PRIORITY_VIEW)) {
            for (GLCommand cmd : priorityDebugCommands) {
                graphicsManager.registerCommand(cmd);
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

        // Render ALL sprites in unified bucket order (7→0)
        // Sprite-to-sprite ordering is by bucket number regardless of isHighPriority
        // The sprite priority shader composites sprites with tile priority awareness
        profiler.beginSection("render.sprites");

        // Enable sprite priority shader mode for ROM-accurate sprite-to-tile layering
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

        // Disable sprite priority shader mode after sprite rendering
        graphicsManager.setUseSpritePriorityShader(false);
        profiler.endSection("render.sprites");

        // Disable shimmer distortion for water surface sprites - they should render
        // without horizontal distortion (matching original hardware behavior where the
        // water surface object is not affected by per-scanline scroll offsets).
        // Keep the water shader active for palette swap (underwater palette below waterline).
        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            WaterShaderProgram waterShader = graphicsManager.getWaterShaderProgram();
            if (waterShader != null) {
                waterShader.use();
                waterShader.setShimmerStyle(0);  // S2 mode with DistortionAmplitude=0 → no distortion
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
        }));

        // Draw water surface sprites at the water line
        // Rendered last (after all sprites and tiles) so it appears in front of everything
        if (zoneFeatureProvider != null) {
            zoneFeatureProvider.render(camera, frameCounter);
        }

        DebugObjectArtViewer.getInstance().draw(objectRenderManager, camera);

        // Revert to default shader for HUD rendering to avoid distortion
        // IMPORTANT: Must be queued as a command so it executes AFTER pattern batches
        // Also reset PatternRenderCommand state so next pattern will reinitialize with
        // the default shader
        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            graphicsManager.setUseWaterShader(false);
            PatternRenderCommand.resetFrameState();
        }));

        profiler.beginSection("render.hud");
        if (hudRenderManager != null && !isHudSuppressed()) {
            AbstractPlayableSprite focusedPlayer = camera.getFocusedSprite();
            hudRenderManager.draw(levelGamestate, focusedPlayer);
        }
        profiler.endSection("render.hud");

        boolean debugViewEnabled = configService.getBoolean(SonicConfiguration.DEBUG_VIEW_ENABLED);
        boolean overlayEnabled = debugViewEnabled && overlayManager.isEnabled(DebugOverlayToggle.OVERLAY);
        if (overlayEnabled) {
            graphicsManager.enqueueDebugLineState();
        }

        if (objectManager != null && overlayEnabled) {
            boolean showObjectPoints = overlayManager.isEnabled(DebugOverlayToggle.OBJECT_POINTS);
            boolean showPlaneSwitchers = overlayManager.isEnabled(DebugOverlayToggle.PLANE_SWITCHERS);
            debugObjectCommands.clear();
            debugSwitcherLineCommands.clear();
            debugSwitcherAreaCommands.clear();
            Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            AbstractPlayableSprite playable = player instanceof AbstractPlayableSprite
                    ? (AbstractPlayableSprite) player
                    : null;
            for (ObjectSpawn spawn : objectManager.getActiveSpawns()) {
                // Frustum cull: skip objects outside visible area (with 32px padding for large objects)
                if (!isInCameraFrustum(spawn.x(), spawn.y(), 32)) {
                    continue;
                }
                if (showObjectPoints) {
                    debugObjectCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I,
                            -1,
                            GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                            1f, 0f, 1f,
                            spawn.x(), spawn.y(), 0, 0));
                }
                if (showPlaneSwitchers) {
                    appendPlaneSwitcherDebug(spawn, debugSwitcherLineCommands, debugSwitcherAreaCommands, playable);
                }
            }
            if (showPlaneSwitchers && !debugSwitcherAreaCommands.isEmpty()) {
                graphicsManager.enqueueDebugLineState();
                for (GLCommand command : debugSwitcherAreaCommands) {
                    graphicsManager.registerCommand(command);
                }
            }
            if (showPlaneSwitchers && !debugSwitcherLineCommands.isEmpty()) {
                graphicsManager.enqueueDebugLineState();
                graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, debugSwitcherLineCommands));
            }
            if (showObjectPoints && !debugObjectCommands.isEmpty()) {
                graphicsManager.enqueueDebugLineState();
                graphicsManager.registerCommand(new GLCommandGroup(GL_POINTS, debugObjectCommands));
            }
        }

        // Per-object debug rendering (hitboxes, velocity vectors, AI state labels)
        if (objectManager != null && overlayEnabled
                && overlayManager.isEnabled(DebugOverlayToggle.OBJECT_DEBUG)) {
            DebugRenderContext debugCtx = new DebugRenderContext();
            for (ObjectInstance instance : objectManager.getActiveObjects()) {
                if (!isInCameraFrustum(instance.getX(), instance.getY(), 64)) {
                    continue;
                }
                instance.appendDebugRenderCommands(debugCtx);
            }
            if (debugCtx.hasGeometry()) {
                graphicsManager.enqueueDebugLineState();
                graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, debugCtx.getGeometryCommands()));
            }
            if (debugCtx.hasText()) {
                overlayManager.setObjectDebugTextEntries(new ArrayList<>(debugCtx.getTextEntries()));
            } else {
                overlayManager.clearObjectDebugTextEntries();
            }
        }

        if (ringManager != null && overlayEnabled
                && overlayManager.isEnabled(DebugOverlayToggle.RING_BOUNDS)) {
            Collection<RingSpawn> rings = ringManager.getActiveSpawns();
            if (!rings.isEmpty()) {
                if (!ringManager.hasRenderer()) {
                    debugRingCommands.clear();
                    for (RingSpawn ring : rings) {
                        if (!ringManager.isRenderable(ring, frameCounter)) {
                            continue;
                        }
                        // Frustum cull rings outside visible area
                        if (!isInCameraFrustum(ring.x(), ring.y(), 16)) {
                            continue;
                        }
                        debugRingCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I,
                                -1,
                                GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                                1f, 0.85f, 0.1f,
                                ring.x(), ring.y(), 0, 0));
                    }
                    graphicsManager.enqueueDebugLineState();
                    graphicsManager.registerCommand(new GLCommandGroup(GL_POINTS, debugRingCommands));
                } else {
                    PatternSpriteRenderer.FrameBounds bounds = ringManager.getFrameBounds(frameCounter);
                    debugBoxCommands.clear();
                    debugCenterCommands.clear();
                    int crossHalf = 2;

                    for (RingSpawn ring : rings) {
                        if (!ringManager.isRenderable(ring, frameCounter)) {
                            continue;
                        }
                        // Frustum cull rings outside visible area
                        if (!isInCameraFrustum(ring.x(), ring.y(), 16)) {
                            continue;
                        }
                        int centerX = ring.x();
                        int centerY = ring.y();
                        int left = centerX + bounds.minX();
                        int right = centerX + bounds.maxX();
                        int top = centerY + bounds.minY();
                        int bottom = centerY + bounds.maxY();

                        // Bounding box (4 line segments)
                        debugBoxCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                0.2f, 1f, 0.2f, left, top, 0, 0));
                        debugBoxCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                0.2f, 1f, 0.2f, right, top, 0, 0));

                        debugBoxCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                0.2f, 1f, 0.2f, right, top, 0, 0));
                        debugBoxCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                0.2f, 1f, 0.2f, right, bottom, 0, 0));

                        debugBoxCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                0.2f, 1f, 0.2f, right, bottom, 0, 0));
                        debugBoxCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                0.2f, 1f, 0.2f, left, bottom, 0, 0));

                        debugBoxCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                0.2f, 1f, 0.2f, left, bottom, 0, 0));
                        debugBoxCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                0.2f, 1f, 0.2f, left, top, 0, 0));

                        // Center cross
                        debugCenterCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                1f, 0.85f, 0.1f, centerX - crossHalf, centerY, 0, 0));
                        debugCenterCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                1f, 0.85f, 0.1f, centerX + crossHalf, centerY, 0, 0));
                        debugCenterCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                1f, 0.85f, 0.1f, centerX, centerY - crossHalf, 0, 0));
                        debugCenterCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                                1f, 0.85f, 0.1f, centerX, centerY + crossHalf, 0, 0));
                    }

                    if (!debugBoxCommands.isEmpty()) {
                        graphicsManager.enqueueDebugLineState();
                        graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, debugBoxCommands));
                    }
                    if (!debugCenterCommands.isEmpty()) {
                        graphicsManager.enqueueDebugLineState();
                        graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, debugCenterCommands));
                    }
                }
            }
        }

        if (overlayEnabled) {
            Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));
            if (player instanceof AbstractPlayableSprite playable) {
                if (overlayManager.isEnabled(DebugOverlayToggle.CAMERA_BOUNDS)) {
                    drawCameraBounds();
                }
                if (overlayManager.isEnabled(DebugOverlayToggle.PLAYER_BOUNDS)) {
                    drawPlayableSpriteBounds(playable);
                }
            }
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
            final int capturedShimmerStyle = shimmerStyle;

            graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
                // Enable water shader at execution time
                graphicsManager.setUseWaterShader(true);

                WaterShaderProgram shader = graphicsManager.getWaterShaderProgram();
                shader.use();

                // Query actual window height from GL state to correct shader coordinates
                int[] viewport = new int[4];
                glGetIntegerv(GL_VIEWPORT, viewport);
                float windowHeight = (float) viewport[3];
                float screenHeightPixels = (float) configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS);

                shader.setWindowHeight(windowHeight);
                shader.setWaterlineScreenY(waterlineScreenY);
                shader.setFrameCounter(frameCounter);
                shader.setDistortionAmplitude(0.0f);
                shader.setShimmerStyle(capturedShimmerStyle);
                shader.setIndexedTextureWidth(graphicsManager.getPatternAtlasWidth());
                shader.setScreenDimensions((float) configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS),
                        screenHeightPixels);

                // Cache water state values in GraphicsManager for sprite priority shader
                graphicsManager.setWaterEnabled(true);
                graphicsManager.setWaterlineScreenY(waterlineScreenY);
                graphicsManager.setWindowHeight(windowHeight);
                graphicsManager.setScreenHeight(screenHeightPixels);

                // Underwater Palette
                Palette[] underwater = waterSystem.getUnderwaterPalette(zoneId, currentAct);
                if (underwater != null) {
                    graphicsManager.cacheUnderwaterPaletteTexture(underwater);
                    Integer texId = graphicsManager.getUnderwaterPaletteTextureId();
                    int loc = shader.getUnderwaterPaletteLocation();

                    if (texId != null && loc != -1) {
                        // Bind to TU2
                        glActiveTexture(GL_TEXTURE2);
                        glBindTexture(GL_TEXTURE_2D, texId);
                        glUniform1i(loc, 2);
                        glActiveTexture(GL_TEXTURE0);
                    }
                }

                // Set shimmer state on tilemap renderer so tile rendering also gets distortion
                TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
                if (tilemapRenderer != null) {
                    tilemapRenderer.setShimmerState(frameCounter, capturedShimmerStyle);
                }

                WaterShaderProgram instancedShader = graphicsManager.getInstancedWaterShaderProgram();
                if (instancedShader != null) {
                    instancedShader.use();
                    instancedShader.cacheUniformLocations();
                    instancedShader.setWindowHeight(windowHeight);
                    instancedShader.setWaterlineScreenY(waterlineScreenY);
                    instancedShader.setFrameCounter(frameCounter);
                    instancedShader.setDistortionAmplitude(0.0f);
                    instancedShader.setShimmerStyle(capturedShimmerStyle);
                    instancedShader.setIndexedTextureWidth(graphicsManager.getPatternAtlasWidth());
                    instancedShader.setScreenDimensions((float) configService.getInt(SonicConfiguration.SCREEN_WIDTH_PIXELS),
                            (float) configService.getInt(SonicConfiguration.SCREEN_HEIGHT_PIXELS));

                    Palette[] underwaterInstanced = waterSystem.getUnderwaterPalette(zoneId, currentAct);
                    if (underwaterInstanced != null) {
                        graphicsManager.cacheUnderwaterPaletteTexture(underwaterInstanced);
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
            }));
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

        ensureBackgroundTilemapData();

        int[] hScrollData = parallaxManager.getHScrollForShader();
        int bgPeriodWidthPixels = backgroundTilemapWidthTiles * Pattern.PATTERN_WIDTH;
        int shaderScrollMidpoint = 0;
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
                    currentZone, currentAct, camera.getX(), backgroundTilemapWidthTiles);
        }
        // Cap BG period at VDP nametable width for parallax scroll wrapping.
        // On the Mega Drive, BG art repeats at the 64-tile nametable width.
        // Without this cap, the parallax shader wraps at the full tilemap width,
        // exposing empty tile regions beyond the valid BG art pattern.
        if (!perLineScrollActive && bgPeriodWidthPixels > VDP_BG_PLANE_WIDTH_PX) {
            bgPeriodWidthPixels = VDP_BG_PLANE_WIDTH_PX;
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
        final int finalRenderWidth = renderWidth;
        final int finalRenderHeight = renderHeight;
        final float finalBgTilemapWorldOffsetX = bgTilemapWorldOffsetX;
        final int finalShaderScrollMidpoint = shaderScrollMidpoint;
        final int finalShaderExtraBuffer = shaderExtraBuffer;
        final boolean finalPerLineScroll = perLineScrollActive;
        final float finalVdpWrapWidth = vdpWrapWidthTiles;
        final float finalNametableBase = nametableBaseTile;
        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            bgRenderer.ensureCapacity(finalRenderWidth, finalRenderHeight);
        }));

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
        final int alignedBgYFinal = alignedBgY;

        // Calculate waterline for FBO - use SCREEN-SPACE waterline PLUS parallax offset
        // The parallax shader shifts the FBO sampling by (actualBgScrollY - alignedBgY)
        // so we must shift the waterline by the same amount to keep it steady on screen
        int vOffset = actualBgScrollY - alignedBgY;
        final float fboWaterlineY = (float) ((waterLevelWorldY - camera.getY()) + vOffset);

        // Compute screen-space waterline for BG parallax shimmer
        final float bgWaterlineScreenY = (float) (waterLevelWorldY - camera.getY());
        final int capturedBgShimmerStyle = currentShimmerStyle;

        ensureBackgroundTilemapData();
        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            bgRenderer.beginTilePass(finalRenderWidth, finalRenderHeight, true);
            TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
            if (tilemapRenderer != null) {
                // Disable shimmer for BG FBO tilemap render - shimmer distortion is applied
                // in the parallax compositing pass instead (with different, larger wave params)
                int savedShimmerStyle = tilemapRenderer.getShimmerStyle();
                tilemapRenderer.setShimmerState(frameCounter, 0);

                Integer atlasId = graphicsManager.getPatternAtlasTextureId();
                Integer paletteId = graphicsManager.getCombinedPaletteTextureId();
                Integer underwaterPaletteId = graphicsManager.getUnderwaterPaletteTextureId();
                boolean useUnderwaterPalette = hasWater && underwaterPaletteId != null;
                if (atlasId != null && paletteId != null) {
                    if (finalPerLineScroll) {
                        bgRenderer.uploadHScroll(hScrollData);
                        tilemapRenderer.enablePerLineScroll(
                                bgRenderer.getHScrollTextureId(), 224.0f,
                                finalVdpWrapWidth, finalNametableBase);
                    }
                    int[] viewport = new int[4];
                    glGetIntegerv(GL_VIEWPORT, viewport);
                    tilemapRenderer.render(
                            TilemapGpuRenderer.Layer.BACKGROUND,
                            finalRenderWidth,
                            finalRenderHeight,
                            viewport[0],
                            viewport[1],
                            viewport[2],
                            viewport[3],
                            finalBgTilemapWorldOffsetX,
                            (float) alignedBgYFinal,
                            graphicsManager.getPatternAtlasWidth(),
                            graphicsManager.getPatternAtlasHeight(),
                            atlasId,
                            paletteId,
                            underwaterPaletteId != null ? underwaterPaletteId : 0,
                            -1,
                            true,
                            false,  // maskOutput = false for screen rendering
                            useUnderwaterPalette,
                            fboWaterlineY);
                }

                // Restore shimmer for subsequent FG tilemap renders
                tilemapRenderer.setShimmerState(frameCounter, savedShimmerStyle);
            }
            bgRenderer.endTilePass();
            graphicsManager.setUseUnderwaterPaletteForBackground(false);
        }));

        // 5. Set shimmer state on BG renderer for parallax compositing pass
        bgRenderer.setShimmerState(frameCounter, capturedBgShimmerStyle, bgWaterlineScreenY);

        // 6. Render the FBO with Parallax Shader
        if (graphicsManager.getCombinedPaletteTextureId() != null) {
            // Calculate vertical scroll offset (sub-chunk) for shader
            // The FBO is rendered aligned to 16-pixel chunk boundaries
            // The shader needs to shift the view by the remaining offset
            int shaderVOffset = actualBgScrollY % LevelConstants.CHUNK_HEIGHT;
            if (shaderVOffset < 0)
                shaderVOffset += LevelConstants.CHUNK_HEIGHT; // Handle negative modulo

            final int finalVOffset = shaderVOffset;

            graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx2, cy2, cw2, ch2) -> {
                bgRenderer.renderWithScrollWide(hScrollData, finalShaderScrollMidpoint, finalShaderExtraBuffer,
                        finalVOffset, finalPerLineScroll);
            }));
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

        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
            if (tilemapRenderer == null) {
                return;
            }
            int[] viewport = new int[4];
            glGetIntegerv(GL_VIEWPORT, viewport);
            tilemapRenderer.render(
                    TilemapGpuRenderer.Layer.FOREGROUND,
                    screenW,
                    screenH,
                    viewport[0],
                    viewport[1],
                    viewport[2],
                    viewport[3],
                    worldOffsetX,
                    worldOffsetY,
                    graphicsManager.getPatternAtlasWidth(),
                    graphicsManager.getPatternAtlasHeight(),
                    atlasId,
                    paletteId,
                    underwaterPaletteId != null ? underwaterPaletteId : 0,
                    priorityPass,
                    verticalWrapEnabled,  // ROM: LZ3/SBZ2 FG wraps vertically
                    false,  // maskOutput = false for screen rendering
                    useUnderwaterPalette,
                    waterlineScreenY);
        }));
    }

    /**
     * Render BG high-priority tiles as an overlay during HTZ earthquake mode.
     *
     * In quake mode, HTZ horizontal scroll is flat (same for every scanline), so BG
     * high-priority tiles can be drawn with a single world offset.
     */
    private void renderHtzEarthquakeBgHighOverlay() {
        if (currentZone != ParallaxManager.ZONE_HTZ || !GameServices.gameState().isHtzScreenShakeActive()) {
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
                    1,
                    false,
                    false,
                    false,
                    0.0f);
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

        // Use parallax-computed BG offsets for the BG priority mask pass.
        // This avoids HTZ earthquake desync where BG high-priority mask can drift
        // relative to the actual background rendering and incorrectly hide sprites.
        float bgWorldOffsetY = parallaxManager.getVscrollFactorBG();
        float bgWorldOffsetXMutable = fgWorldOffsetX;
        int[] hScrollData = parallaxManager.getHScrollForShader();
        if (hScrollData != null && hScrollData.length > 0) {
            short bgScroll = (short) (hScrollData[hScrollData.length - 1] & 0xFFFF);
            bgWorldOffsetXMutable = -bgScroll;
        }
        final float bgWorldOffsetX = bgWorldOffsetXMutable;

        // BG high-priority FBO pass is only valid when BG scroll is flat (earthquake mode).
        // In normal mode, per-scanline parallax means a single BG offset can't match the
        // actual on-screen tile positions, causing the sprite priority shader to hide sprites
        // behind misaligned mask pixels.
        final boolean bgFboPassEnabled =
                currentZone == ParallaxManager.ZONE_HTZ && GameServices.gameState().isHtzScreenShakeActive();

        graphicsManager.registerCommand(new GLCommand(GLCommand.CommandType.CUSTOM, (cx, cy, cw, ch) -> {
            TilePriorityFBO tileFbo = graphicsManager.getTilePriorityFBO();
            TilemapGpuRenderer tilemapRenderer = graphicsManager.getTilemapGpuRenderer();
            if (tileFbo == null || tilemapRenderer == null) {
                return;
            }

            // Begin rendering to FBO
            tileFbo.begin();

            // Enable max blending so both layers contribute to priority mask
            // This ensures high-priority tiles from BOTH background AND foreground
            // will occlude low-priority sprites (matching VDP behavior)
            glEnable(GL_BLEND);
            glBlendEquation(GL_MAX);
            glBlendFunc(GL_ONE, GL_ONE);

            // First pass: background high-priority tiles (only when BG scroll is flat)
            if (bgFboPassEnabled) {
                tilemapRenderer.render(
                        TilemapGpuRenderer.Layer.BACKGROUND,
                        screenW,
                        screenH,
                        0,      // viewport X (FBO uses full size)
                        0,      // viewport Y
                        screenW,  // viewport width
                        screenH,  // viewport height
                        bgWorldOffsetX,
                        bgWorldOffsetY,
                        graphicsManager.getPatternAtlasWidth(),
                        graphicsManager.getPatternAtlasHeight(),
                        atlasId,
                        paletteId,
                        0,      // no underwater palette for FBO
                        1,      // priority pass = 1 (high priority only)
                        false,  // no wrap Y
                        true,   // maskOutput = true for priority FBO
                        false,  // no underwater palette
                        0.0f);  // no waterline
            }

            // Second pass: foreground high-priority tiles
            tilemapRenderer.render(
                    TilemapGpuRenderer.Layer.FOREGROUND,
                    screenW,
                    screenH,
                    0,      // viewport X (FBO uses full size)
                    0,      // viewport Y
                    screenW,  // viewport width
                    screenH,  // viewport height
                    fgWorldOffsetX,
                    fgWorldOffsetY,
                    graphicsManager.getPatternAtlasWidth(),
                    graphicsManager.getPatternAtlasHeight(),
                    atlasId,
                    paletteId,
                    0,      // no underwater palette for FBO
                    1,      // priority pass = 1 (high priority only)
                    verticalWrapEnabled,  // ROM: LZ3/SBZ2 FG wraps vertically
                    true,   // maskOutput = true for priority FBO
                    false,  // no underwater palette
                    0.0f);  // no waterline

            // Restore default blend state
            glBlendEquation(GL_FUNC_ADD);
            glDisable(GL_BLEND);

            // End rendering to FBO
            tileFbo.end();
        }));
    }

    private void ensureBackgroundTilemapData() {
        if (!backgroundTilemapDirty && backgroundTilemapData != null && patternLookupData != null) {
            return;
        }
        if (level == null || level.getMap() == null) {
            return;
        }

        buildBackgroundTilemapData();
        backgroundTilemapDirty = false;

        ensurePatternLookupData();
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer != null) {
            renderer.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND, backgroundTilemapData,
                    backgroundTilemapWidthTiles, backgroundTilemapHeightTiles);
            renderer.setPatternLookupData(patternLookupData, patternLookupSize);
        }
    }

    private void ensureForegroundTilemapData() {
        if (!foregroundTilemapDirty && foregroundTilemapData != null && patternLookupData != null) {
            return;
        }
        if (level == null || level.getMap() == null) {
            return;
        }
        buildForegroundTilemapData();
        foregroundTilemapDirty = false;
        ensurePatternLookupData();
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer != null) {
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, foregroundTilemapData,
                    foregroundTilemapWidthTiles, foregroundTilemapHeightTiles);
            renderer.setPatternLookupData(patternLookupData, patternLookupSize);
        }
    }

    private void ensurePatternLookupData() {
        if (!patternLookupDirty && patternLookupData != null) {
            return;
        }
        if (level == null) {
            return;
        }
        int patternCount = level.getPatternCount();
        patternLookupSize = Math.max(1, patternCount);
        patternLookupData = new byte[patternLookupSize * 4];
        for (int i = 0; i < patternCount; i++) {
            PatternAtlas.Entry entry = graphicsManager.getPatternAtlasEntry(i);
            int offset = i * 4;
            if (entry != null) {
                patternLookupData[offset] = (byte) entry.tileX();
                patternLookupData[offset + 1] = (byte) entry.tileY();
                patternLookupData[offset + 2] = (byte) entry.atlasIndex();
                patternLookupData[offset + 3] = (byte) 255;
            } else {
                patternLookupData[offset] = 0;
                patternLookupData[offset + 1] = 0;
                patternLookupData[offset + 2] = 0;
                patternLookupData[offset + 3] = 0;
            }
        }
        PatternAtlas atlas = graphicsManager.getPatternAtlas();
        if (!multiAtlasWarningLogged && atlas != null && atlas.getAtlasCount() > 1) {
            LOGGER.warning("Pattern atlas overflow: using multiple atlases (count="
                    + atlas.getAtlasCount()
                    + ", slotsPerAtlas=" + atlas.getMaxSlotsPerAtlas()
                    + ", atlasSize=" + atlas.getAtlasWidth() + "x" + atlas.getAtlasHeight()
                    + ") for this level.");
            multiAtlasWarningLogged = true;
        }
        patternLookupDirty = false;
    }

    private void buildBackgroundTilemapData() {
        TilemapData data = buildTilemapData((byte) 1);
        backgroundTilemapData = data.data;
        backgroundTilemapWidthTiles = data.widthTiles;
        backgroundTilemapHeightTiles = data.heightTiles;
    }

    private void buildForegroundTilemapData() {
        TilemapData data = buildTilemapData((byte) 0);
        foregroundTilemapData = data.data;
        foregroundTilemapWidthTiles = data.widthTiles;
        foregroundTilemapHeightTiles = data.heightTiles;
    }

    // VDP plane size for Sonic 2 normal levels: 64x32 cells = 512x256 pixels.
    // The background tilemap wraps at this width for Sonic 2's redraw-style pipeline.
    private static final int VDP_BG_PLANE_WIDTH_PX = 512;

    private TilemapData buildTilemapData(byte layerIndex) {
        int layerLevelWidth = getLayerLevelWidthPx(layerIndex);
        int levelHeight = getLayerLevelHeightPx(layerIndex);

        // Keep Sonic 2's 512px BG wrap behavior (VDP plane redraw model).
        // S3K uses a different background data flow in AIZ intro and needs full-width BG data.
        boolean bgWrap = layerIndex == 1
                && zoneFeatureProvider != null
                && zoneFeatureProvider.bgWrapsHorizontally();
        int levelWidth = bgWrap ? VDP_BG_PLANE_WIDTH_PX : layerLevelWidth;

        int widthTiles = levelWidth / Pattern.PATTERN_WIDTH;
        int heightTiles = levelHeight / Pattern.PATTERN_HEIGHT;
        byte[] data = new byte[widthTiles * heightTiles * 4];

        int chunkWidth = LevelConstants.CHUNK_WIDTH;
        int chunkHeight = LevelConstants.CHUNK_HEIGHT;

        for (int y = 0; y < levelHeight; y += chunkHeight) {
            int chunkY = y / chunkHeight;
            for (int x = 0; x < levelWidth; x += chunkWidth) {
                int chunkX = x / chunkWidth;

                Block block = getBlockAtPosition(layerIndex, x, y);
                if (block == null) {
                    writeEmptyChunk(data, widthTiles, heightTiles, chunkX, chunkY);
                    continue;
                }

                int xBlockBit = (x % blockPixelSize) / chunkWidth;
                int yBlockBit = (y % blockPixelSize) / chunkHeight;
                ChunkDesc chunkDesc = block.getChunkDesc(xBlockBit, yBlockBit);
                int chunkIndex = chunkDesc.getChunkIndex();

                if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
                    writeEmptyChunk(data, widthTiles, heightTiles, chunkX, chunkY);
                    continue;
                }

                Chunk chunk = level.getChunk(chunkIndex);
                if (chunk == null) {
                    writeEmptyChunk(data, widthTiles, heightTiles, chunkX, chunkY);
                    continue;
                }

                boolean chunkHFlip = chunkDesc.getHFlip();
                boolean chunkVFlip = chunkDesc.getVFlip();

                for (int cY = 0; cY < 2; cY++) {
                    for (int cX = 0; cX < 2; cX++) {
                        int logicalX = chunkHFlip ? 1 - cX : cX;
                        int logicalY = chunkVFlip ? 1 - cY : cY;

                        PatternDesc patternDesc = chunk.getPatternDesc(logicalX, logicalY);
                        int newIndex = patternDesc.get();
                        if (chunkHFlip) {
                            newIndex ^= 0x800;
                        }
                        if (chunkVFlip) {
                            newIndex ^= 0x1000;
                        }
                        reusablePatternDesc.set(newIndex);

                        int tileX = chunkX * 2 + cX;
                        int tileY = chunkY * 2 + cY;
                        writeTileDescriptor(data, widthTiles, heightTiles, tileX, tileY, reusablePatternDesc);
                    }
                }
            }
        }

        return new TilemapData(data, widthTiles, heightTiles);
    }

    private void writeEmptyChunk(byte[] data, int widthTiles, int heightTiles, int chunkX, int chunkY) {
        for (int cY = 0; cY < 2; cY++) {
            for (int cX = 0; cX < 2; cX++) {
                int tileX = chunkX * 2 + cX;
                int tileY = chunkY * 2 + cY;
                writeEmptyTile(data, widthTiles, heightTiles, tileX, tileY);
            }
        }
    }

    private void writeEmptyTile(byte[] data, int widthTiles, int heightTiles, int tileX, int tileY) {
        if (tileX < 0 || tileY < 0 || tileX >= widthTiles
                || tileY >= heightTiles) {
            return;
        }
        int offset = (tileY * widthTiles + tileX) * 4;
        data[offset] = 0;
        data[offset + 1] = 0;
        data[offset + 2] = 0;
        data[offset + 3] = 0;
    }

    private void writeTileDescriptor(byte[] data, int widthTiles, int heightTiles, int tileX, int tileY,
            PatternDesc desc) {
        if (tileX < 0 || tileY < 0 || tileX >= widthTiles || tileY >= heightTiles) {
            return;
        }
        int offset = (tileY * widthTiles + tileX) * 4;
        int patternIndex = desc.getPatternIndex();
        int paletteIndex = desc.getPaletteIndex();
        boolean hFlip = desc.getHFlip();
        boolean vFlip = desc.getVFlip();
        boolean priority = desc.getPriority();

        int r = patternIndex & 0xFF;
        int g = ((patternIndex >> 8) & 0x7)
                | ((paletteIndex & 0x3) << 3)
                | (hFlip ? 0x20 : 0)
                | (vFlip ? 0x40 : 0)
                | (priority ? 0x80 : 0);

        data[offset] = (byte) r;
        data[offset + 1] = (byte) g;
        data[offset + 2] = 0;
        data[offset + 3] = (byte) 255;
    }

    private record TilemapData(byte[] data, int widthTiles, int heightTiles) {
    }

    /**
     * Generates collision debug overlay commands for visible chunks.
     * This method iterates over visible chunks in the foreground layer (Layer 0)
     * and generates collision debug rendering commands independently of tile rendering.
     *
     * @param commands the list of GLCommands to add collision rectangles to
     * @param camera   the camera for visibility culling
     */
    private void generateCollisionDebugCommands(List<GLCommand> commands, Camera camera) {
        if (level == null || level.getMap() == null) {
            return;
        }
        if (!overlayManager.isEnabled(DebugOverlayToggle.COLLISION_VIEW)) {
            return;
        }

        int cameraX = camera.getX();
        int cameraY = camera.getY();
        int cameraWidth = camera.getWidth();
        int cameraHeight = camera.getHeight();

        int levelWidth = level.getMap().getWidth() * blockPixelSize;
        int levelHeight = level.getMap().getHeight() * blockPixelSize;

        // Calculate visible chunk range (same culling logic as foreground rendering)
        int xStart = cameraX - (cameraX % LevelConstants.CHUNK_WIDTH);
        int xEnd = cameraX + cameraWidth;
        int yStart = cameraY - (cameraY % LevelConstants.CHUNK_HEIGHT);
        int yEnd = cameraY + cameraHeight + LevelConstants.CHUNK_HEIGHT;

        for (int y = yStart; y <= yEnd; y += LevelConstants.CHUNK_HEIGHT) {
            // Foreground clamps vertically (doesn't wrap)
            if (y < 0 || y >= levelHeight) {
                continue;
            }

            for (int x = xStart; x <= xEnd; x += LevelConstants.CHUNK_WIDTH) {
                // Handle X wrapping
                int wrappedX = ((x % levelWidth) + levelWidth) % levelWidth;

                Block block = getBlockAtPosition((byte) 0, wrappedX, y);
                if (block == null) {
                    continue;
                }

                int xBlockBit = (wrappedX % blockPixelSize) / LevelConstants.CHUNK_WIDTH;
                int yBlockBit = (y % blockPixelSize) / LevelConstants.CHUNK_HEIGHT;
                ChunkDesc chunkDesc = block.getChunkDesc(xBlockBit, yBlockBit);

                int chunkIndex = chunkDesc.getChunkIndex();
                if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
                    continue;
                }

                Chunk chunk = level.getChunk(chunkIndex);
                if (chunk == null) {
                    continue;
                }

                // Calculate screen coordinates then convert to render coordinates
                int screenX = x - cameraX;
                int screenY = y - cameraY;
                int renderX = screenX + cameraX;
                int renderY = screenY + cameraY;

                // Generate collision debug for both primary and secondary collision
                processCollisionMode(commands, chunkDesc, chunk, true, renderX, renderY);
                processCollisionMode(commands, chunkDesc, chunk, false, renderX, renderY);
            }
        }
    }

    /**
     * Processes and renders collision modes for a chunk.
     *
     * @param commands  the list of GLCommands to add to
     * @param chunkDesc the description of the chunk
     * @param chunk     the chunk data
     * @param isPrimary whether to process the primary collision mode
     * @param x         the x-coordinate
     * @param y         the y-coordinate
     */
    private void processCollisionMode(
            List<GLCommand> commands,
            ChunkDesc chunkDesc,
            Chunk chunk,
            boolean isPrimary,
            int x,
            int y) {
        if (!overlayManager.isEnabled(DebugOverlayToggle.COLLISION_VIEW)) {
            return;
        }

        boolean hasSolidity = isPrimary
                ? chunkDesc.hasPrimarySolidity()
                : chunkDesc.hasSecondarySolidity();
        if (!hasSolidity) {
            return;
        }

        int solidTileIndex = isPrimary
                ? chunk.getSolidTileIndex()
                : chunk.getSolidTileAltIndex();
        SolidTile solidTile = level.getSolidTile(solidTileIndex);
        if (solidTile == null) {
            LOGGER.warning("SolidTile at index " + solidTileIndex + " is null.");
            return;
        }

        // Determine color based on collision mode
        float r, g, b;
        if (isPrimary) {
            r = 1.0f; // White color for primary collision
            g = 1.0f;
            b = 1.0f;
        } else {
            r = 0.5f; // Gray color for secondary collision
            g = 0.5f;
            b = 0.5f;
        }

        boolean hFlip = chunkDesc.getHFlip();
        boolean yFlip = chunkDesc.getVFlip(); // Using VFlip as per your current code

        // Disable shaders for drawing solid colors (RECTI uses its own debug shader)
        ShaderProgram shaderProgram = graphicsManager.getShaderProgram();
        int shaderProgramId = 0;
        if (shaderProgram != null) {
            shaderProgramId = shaderProgram.getProgramId();
        }
        commands.add(new GLCommand(GLCommand.CommandType.USE_PROGRAM, 0));

        // Iterate over each pixel column in the tile
        for (int i = 0; i < LevelConstants.CHUNK_WIDTH; i++) {
            int tileIndex = hFlip ? (LevelConstants.CHUNK_HEIGHT - 1 - i) : i;
            int height = solidTile.getHeightAt((byte) tileIndex);

            if (height > 0) {
                int drawStartX = x + i;
                int drawEndX = drawStartX + 1;

                int drawStartY;
                int drawEndY;

                // Adjust drawing coordinates based on vertical flip
                // GLCommand constructor handles Y-flip (SCREEN_HEIGHT_PIXELS - y)
                // and execute() applies camera offset (y + cameraY)
                // We add 16 to align with the pattern renderer's coordinate system
                if (yFlip) {
                    // When yFlip is true, collision extends upward from bottom of chunk
                    drawStartY = y - LevelConstants.CHUNK_HEIGHT + 16;
                    drawEndY = drawStartY + height;
                } else {
                    // Normal rendering: collision extends downward from top of chunk
                    drawStartY = y + 16;
                    drawEndY = y - height + 16;
                }

                commands.add(new GLCommand(
                        GLCommand.CommandType.RECTI,
                        GL_2D,
                        r,
                        g,
                        b,
                        drawStartX,
                        drawEndY,
                        drawEndX,
                        drawStartY));
            }
        }
        // Re-enable shader for subsequent rendering
        if (shaderProgramId != 0) {
            commands.add(new GLCommand(GLCommand.CommandType.USE_PROGRAM, shaderProgramId));
        }
    }

    /**
     * Generates tile priority debug overlay commands for visible chunks.
     * This method iterates over visible chunks in the foreground layer (Layer 0)
     * and overlays high-priority tiles with a semi-transparent red tint.
     * Helps diagnose sprite-behind-tile priority issues like the ARZ wall bug.
     *
     * @param commands the list of GLCommands to add priority rectangles to
     * @param camera   the camera for visibility culling
     */
    private void generateTilePriorityDebugCommands(List<GLCommand> commands, Camera camera) {
        if (level == null || level.getMap() == null) {
            return;
        }
        if (!overlayManager.isEnabled(DebugOverlayToggle.TILE_PRIORITY_VIEW)) {
            return;
        }

        int cameraX = camera.getX();
        int cameraY = camera.getY();
        int cameraWidth = camera.getWidth();
        int cameraHeight = camera.getHeight();

        int levelWidth = level.getMap().getWidth() * blockPixelSize;
        int levelHeight = level.getMap().getHeight() * blockPixelSize;

        // Calculate visible chunk range (same culling logic as foreground rendering)
        int xStart = cameraX - (cameraX % LevelConstants.CHUNK_WIDTH);
        int xEnd = cameraX + cameraWidth;
        int yStart = cameraY - (cameraY % LevelConstants.CHUNK_HEIGHT);
        int yEnd = cameraY + cameraHeight + LevelConstants.CHUNK_HEIGHT;

        // Disable shaders for drawing solid colors (RECTI uses its own debug shader)
        ShaderProgram shaderProgram = graphicsManager.getShaderProgram();
        int shaderProgramId = 0;
        if (shaderProgram != null) {
            shaderProgramId = shaderProgram.getProgramId();
        }
        commands.add(new GLCommand(GLCommand.CommandType.USE_PROGRAM, 0));

        for (int y = yStart; y <= yEnd; y += LevelConstants.CHUNK_HEIGHT) {
            // Foreground clamps vertically (doesn't wrap)
            if (y < 0 || y >= levelHeight) {
                continue;
            }

            for (int x = xStart; x <= xEnd; x += LevelConstants.CHUNK_WIDTH) {
                // Handle X wrapping
                int wrappedX = ((x % levelWidth) + levelWidth) % levelWidth;

                Block block = getBlockAtPosition((byte) 0, wrappedX, y);
                if (block == null) {
                    continue;
                }

                int xBlockBit = (wrappedX % blockPixelSize) / LevelConstants.CHUNK_WIDTH;
                int yBlockBit = (y % blockPixelSize) / LevelConstants.CHUNK_HEIGHT;
                ChunkDesc chunkDesc = block.getChunkDesc(xBlockBit, yBlockBit);

                int chunkIndex = chunkDesc.getChunkIndex();
                if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
                    continue;
                }

                Chunk chunk = level.getChunk(chunkIndex);
                if (chunk == null) {
                    continue;
                }

                // Check each of the 4 patterns (8x8 tiles) in this 16x16 chunk
                for (int py = 0; py < 2; py++) {
                    for (int px = 0; px < 2; px++) {
                        PatternDesc desc = chunk.getPatternDesc(px, py);
                        if (desc == null || desc == PatternDesc.EMPTY) {
                            continue;
                        }

                        // Only highlight high-priority tiles (priority bit = 1)
                        if (!desc.getPriority()) {
                            continue;
                        }

                        // Calculate screen coordinates for this 8x8 pattern
                        // We need to account for chunk flip flags
                        int patternX = x + (chunkDesc.getHFlip() ? (1 - px) : px) * Pattern.PATTERN_WIDTH;
                        int patternY = y + (chunkDesc.getVFlip() ? (1 - py) : py) * Pattern.PATTERN_HEIGHT;

                        // Draw a semi-transparent red overlay for high-priority tiles
                        // Use RECTI with alpha blending
                        int renderX = patternX;
                        int renderY = patternY;

                        // Add 16 to align with the pattern renderer's coordinate system (same as collision debug)
                        int drawStartX = renderX;
                        int drawEndX = drawStartX + Pattern.PATTERN_WIDTH;
                        int drawStartY = renderY + 16;
                        int drawEndY = drawStartY - Pattern.PATTERN_HEIGHT;

                        commands.add(new GLCommand(
                                GLCommand.CommandType.RECTI,
                                -1,
                                GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                                1.0f, 0.0f, 0.0f, 0.4f, // Red with 40% opacity
                                drawStartX,
                                drawEndY,
                                drawEndX,
                                drawStartY));
                    }
                }
            }
        }

        // Re-enable shader for subsequent rendering
        if (shaderProgramId != 0) {
            commands.add(new GLCommand(GLCommand.CommandType.USE_PROGRAM, shaderProgramId));
        }
    }

    private void drawPlayableSpriteBounds(AbstractPlayableSprite sprite) {
        PlayerSpriteRenderer renderer = sprite.getSpriteRenderer();
        if (renderer == null) {
            return;
        }

        boolean hFlip = Direction.LEFT.equals(sprite.getDirection());
        SpritePieceRenderer.FrameBounds mappingBounds = renderer.getFrameBounds(sprite.getMappingFrame(), hFlip, false);

        int collisionCenterX = sprite.getCentreX();
        int collisionCenterY = sprite.getCentreY();
        int renderCenterX = sprite.getRenderCentreX();
        int renderCenterY = sprite.getRenderCentreY();
        sensorCommands.clear();

        if (mappingBounds.width() > 0 && mappingBounds.height() > 0) {
            int mapLeft = renderCenterX + mappingBounds.minX();
            int mapRight = renderCenterX + mappingBounds.maxX();
            int mapTop = renderCenterY + mappingBounds.minY();
            int mapBottom = renderCenterY + mappingBounds.maxY();
            appendBox(sensorCommands, mapLeft, mapTop, mapRight, mapBottom, 0.1f, 0.85f, 1f);
        }

        int radiusLeft = collisionCenterX - sprite.getXRadius();
        int radiusRight = collisionCenterX + sprite.getXRadius();
        int radiusTop = collisionCenterY - sprite.getYRadius();
        int radiusBottom = collisionCenterY + sprite.getYRadius();
        appendBox(sensorCommands, radiusLeft, radiusTop, radiusRight, radiusBottom, 1f, 0.8f, 0.1f);

        appendCross(sensorCommands, collisionCenterX, collisionCenterY, 2, 1f, 0.8f, 0.1f);
        appendCross(sensorCommands, renderCenterX, renderCenterY, 2, 0.1f, 0.85f, 1f);

        Sensor[] sensors = sprite.getAllSensors();
        for (int i = 0; i < sensors.length; i++) {
            Sensor sensor = sensors[i];
            if (sensor == null) {
                continue;
            }
            short[] rotatedOffset = sensor.getRotatedOffset();
            int originX = collisionCenterX + rotatedOffset[0];
            int originY = collisionCenterY + rotatedOffset[1];

            float[] color = DebugOverlayPalette.sensorLineColor(i, sensor.isActive());
            appendCross(sensorCommands, originX, originY, 1, color[0], color[1], color[2]);

            if (!sensor.isActive()) {
                continue;
            }
            SensorResult result = sensor.getCurrentResult();
            if (result == null) {
                continue;
            }

            SensorConfiguration sensorConfiguration = SpriteManager
                    .getSensorConfigurationForGroundModeAndDirection(sprite.getGroundMode(), sensor.getDirection());
            Direction globalDirection = sensorConfiguration.direction();

            int dist = result.distance();
            int endX = originX;
            int endY = originY;
            switch (globalDirection) {
                case DOWN -> endY = originY + dist;
                case UP -> endY = originY - dist;
                case LEFT -> endX = originX - dist;
                case RIGHT -> endX = originX + dist;
            }

            appendLine(sensorCommands, originX, originY, endX, endY, color[0], color[1], color[2]);
        }

        if (!sensorCommands.isEmpty()) {
            graphicsManager.enqueueDebugLineState();
            graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, sensorCommands));
        }
    }

    private void drawCameraBounds() {
        Camera camera = Camera.getInstance();
        cameraBoundsCommands.clear();

        int camX = camera.getX();
        int camY = camera.getY();
        int camW = camera.getWidth();
        int camH = camera.getHeight();

        appendBox(cameraBoundsCommands, camX, camY, camX + camW, camY + camH, 0.85f, 0.9f, 1f);
        appendCross(cameraBoundsCommands, camX + (camW / 2), camY + (camH / 2), 4, 0.85f, 0.9f, 1f);

        int minX = camera.getMinX();
        int minY = camera.getMinY();
        int maxX = camera.getMaxX();
        int maxY = camera.getMaxY();
        if (maxX > minX || maxY > minY) {
            appendBox(cameraBoundsCommands, minX, minY, maxX + camW, maxY + camH, 0.2f, 0.9f, 0.9f);
        }

        if (!cameraBoundsCommands.isEmpty()) {
            graphicsManager.enqueueDebugLineState();
            graphicsManager.registerCommand(new GLCommandGroup(GL_LINES, cameraBoundsCommands));
        }
    }

    private void appendLine(
            List<GLCommand> commands,
            int x1,
            int y1,
            int x2,
            int y2,
            float r,
            float g,
            float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x1, y1, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, x2, y2, 0, 0));
    }

    private void appendCross(
            List<GLCommand> commands,
            int centerX,
            int centerY,
            int halfSpan,
            float r,
            float g,
            float b) {
        appendLine(commands, centerX - halfSpan, centerY, centerX + halfSpan, centerY, r, g, b);
        appendLine(commands, centerX, centerY - halfSpan, centerX, centerY + halfSpan, r, g, b);
    }

    private void appendPlaneSwitcherDebug(ObjectSpawn spawn,
            List<GLCommand> lineCommands,
            List<GLCommand> areaCommands,
            AbstractPlayableSprite player) {
        if (gameModule == null || spawn.objectId() != gameModule.getPlaneSwitcherObjectId()) {
            return;
        }
        int subtype = spawn.subtype();
        int halfSpan = ObjectManager.decodePlaneSwitcherHalfSpan(subtype);
        boolean horizontal = ObjectManager.isPlaneSwitcherHorizontal(subtype);
        int x = spawn.x();
        int y = spawn.y();
        int sideState = objectManager != null ? objectManager.getPlaneSwitcherSideState(spawn) : -1;
        if (sideState < 0 && player != null) {
            sideState = horizontal
                    ? (player.getCentreY() >= y ? 1 : 0)
                    : (player.getCentreX() >= x ? 1 : 0);
        }
        if (sideState < 0) {
            sideState = 0;
        }

        int extent = halfSpan;
        if (horizontal) {
            lineCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    SWITCHER_DEBUG_R, SWITCHER_DEBUG_G, SWITCHER_DEBUG_B,
                    x - halfSpan, y, 0, 0));
            lineCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    SWITCHER_DEBUG_R, SWITCHER_DEBUG_G, SWITCHER_DEBUG_B,
                    x + halfSpan, y, 0, 0));

            int top = sideState == 0 ? y - extent : y;
            int bottom = sideState == 0 ? y : y + extent;
            areaCommands.add(new GLCommand(GLCommand.CommandType.RECTI, -1, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                    SWITCHER_DEBUG_R, SWITCHER_DEBUG_G, SWITCHER_DEBUG_B, SWITCHER_DEBUG_ALPHA,
                    x - halfSpan, top,
                    x + halfSpan, bottom));
        } else {
            lineCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    SWITCHER_DEBUG_R, SWITCHER_DEBUG_G, SWITCHER_DEBUG_B,
                    x, y - halfSpan, 0, 0));
            lineCommands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                    SWITCHER_DEBUG_R, SWITCHER_DEBUG_G, SWITCHER_DEBUG_B,
                    x, y + halfSpan, 0, 0));

            int left = sideState == 0 ? x - extent : x;
            int right = sideState == 0 ? x : x + extent;
            areaCommands.add(new GLCommand(GLCommand.CommandType.RECTI, -1, GLCommand.BlendType.ONE_MINUS_SRC_ALPHA,
                    SWITCHER_DEBUG_R, SWITCHER_DEBUG_G, SWITCHER_DEBUG_B, SWITCHER_DEBUG_ALPHA,
                    left, y - halfSpan,
                    right, y + halfSpan));
        }
    }

    private void appendBox(
            List<GLCommand> commands,
            int left,
            int top,
            int right,
            int bottom,
            float r,
            float g,
            float b) {
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, left, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, right, top, 0, 0));

        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, right, top, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, right, bottom, 0, 0));

        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, right, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, left, bottom, 0, 0));

        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, left, bottom, 0, 0));
        commands.add(new GLCommand(GLCommand.CommandType.VERTEX2I, -1, GLCommand.BlendType.SOLID,
                r, g, b, left, top, 0, 0));
    }

    /**
     * Retrieves the block at a given position.
     *
     * @param layer the layer to retrieve the block from
     * @param x     the x-coordinate in pixels
     * @param y     the y-coordinate in pixels
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

    private Block getBlockAtPosition(byte layer, int x, int y) {
        if (level == null || level.getMap() == null) {
            LOGGER.warning("Level or Map is not initialized.");
            return null;
        }

        int levelWidth = getLayerLevelWidthPx(layer);
        int levelHeight = getLayerLevelHeightPx(layer);

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
        int levelWidth = getLayerLevelWidthPx((byte) 0);
        int levelHeight = getLayerLevelHeightPx((byte) 0);
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
        Block block = getBlockAtPosition(layer, x, y);
        if (block == null) {
            return null;
        }

        int levelWidth = getLayerLevelWidthPx(layer);
        int wrappedX = ((x % levelWidth) + levelWidth) % levelWidth;
        int wrappedY = y;

        if (layer == 1 || (layer == 0 && verticalWrapEnabled)) {
            int levelHeight = getLayerLevelHeightPx(layer);
            wrappedY = ((y % levelHeight) + levelHeight) % levelHeight;
        }

        ChunkDesc chunkDesc = block.getChunkDesc((wrappedX % blockPixelSize) / LevelConstants.CHUNK_WIDTH,
                (wrappedY % blockPixelSize) / LevelConstants.CHUNK_HEIGHT);
        return chunkDesc;
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

        int levelWidth = getLayerLevelWidthPx((byte) 0);
        int levelHeight = getLayerLevelHeightPx((byte) 0);
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
        if (isSonic1Sbz3Context()) {
            return uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.ZONE_SBZ;
        }
        return level != null ? level.getZoneIndex() : -1;
    }

    /**
     * Returns the effective act index for zone features/water logic.
     */
    public int getFeatureActId() {
        if (isSonic1Sbz3Context()) {
            return 2;
        }
        return currentAct;
    }

    private boolean isSonic1Sbz3Context() {
        if (level == null || game == null || !"Sonic1".equals(game.getIdentifier())) {
            return false;
        }
        return currentZone == uk.co.jamesj999.sonic.game.sonic1.scroll.Sonic1ZoneConstants.ZONE_SBZ
                && currentAct == 2
                && level.getZoneIndex() == uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants.ZONE_LZ;
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
        foregroundTilemapDirty = true;
    }

    /**
     * Marks background/foreground tilemaps and pattern lookup as dirty.
     * Use this after runtime terrain art/chunk overlays so the GPU tilemap
     * data is rebuilt on the next render.
     */
    public void invalidateAllTilemaps() {
        backgroundTilemapDirty = true;
        foregroundTilemapDirty = true;
        patternLookupDirty = true;
    }

    /**
     * Pre-builds FG and BG tilemap data from the current level state.
     * The pre-built data can later be swapped in via {@link #swapToPrebuiltTilemaps()}
     * to avoid the expensive full-level tilemap rebuild on the transition frame.
     */
    public void prebuildTransitionTilemaps() {
        if (level == null || level.getMap() == null) {
            return;
        }
        TilemapData fg = buildTilemapData((byte) 0);
        prebuiltFgTilemap = fg.data.clone();
        prebuiltFgWidth = fg.widthTiles;
        prebuiltFgHeight = fg.heightTiles;

        TilemapData bg = buildTilemapData((byte) 1);
        prebuiltBgTilemap = bg.data.clone();
        prebuiltBgWidth = bg.widthTiles;
        prebuiltBgHeight = bg.heightTiles;
    }

    /**
     * Swaps pre-built tilemap data into the live arrays, uploads to GPU,
     * and clears FG/BG dirty flags. Still marks pattern lookup dirty
     * (cheap rebuild, needed if pattern count changed from the overlay).
     *
     * @return true if pre-built data was available and swapped in
     */
    public boolean swapToPrebuiltTilemaps() {
        if (prebuiltFgTilemap == null || prebuiltBgTilemap == null) {
            return false;
        }

        foregroundTilemapData = prebuiltFgTilemap;
        foregroundTilemapWidthTiles = prebuiltFgWidth;
        foregroundTilemapHeightTiles = prebuiltFgHeight;
        foregroundTilemapDirty = false;

        backgroundTilemapData = prebuiltBgTilemap;
        backgroundTilemapWidthTiles = prebuiltBgWidth;
        backgroundTilemapHeightTiles = prebuiltBgHeight;
        backgroundTilemapDirty = false;

        patternLookupDirty = true;

        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer != null) {
            ensurePatternLookupData();
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND,
                    foregroundTilemapData, foregroundTilemapWidthTiles, foregroundTilemapHeightTiles);
            renderer.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND,
                    backgroundTilemapData, backgroundTilemapWidthTiles, backgroundTilemapHeightTiles);
            renderer.setPatternLookupData(patternLookupData, patternLookupSize);
        }

        // Release pre-built data (one-shot use)
        prebuiltFgTilemap = null;
        prebuiltBgTilemap = null;
        return true;
    }

    /**
     * Returns whether pre-built transition tilemap data is available.
     */
    public boolean hasPrebuiltTilemaps() {
        return prebuiltFgTilemap != null && prebuiltBgTilemap != null;
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
            specialStageReturnLevelReloadRequested = false;
            levelInactiveForTransition = false;

            // Ensure zone list is populated before accessing it
            if (levels.isEmpty()) {
                gameModule = GameModuleRegistry.getCurrent();
                refreshZoneList();
            }
            LevelData levelData = levels.get(currentZone).get(currentAct);

            // Check if we have an active checkpoint BEFORE reloading
            // (loadLevel clears checkpointState, so we need to save the values first)
            boolean hasCheckpoint = checkpointState != null && checkpointState.isActive();
            int checkpointX = hasCheckpoint ? checkpointState.getSavedX() : 0;
            int checkpointY = hasCheckpoint ? checkpointState.getSavedY() : 0;
            int checkpointCameraX = hasCheckpoint ? checkpointState.getSavedCameraX() : 0;
            int checkpointCameraY = hasCheckpoint ? checkpointState.getSavedCameraY() : 0;
            int checkpointIndex = hasCheckpoint ? checkpointState.getLastCheckpointIndex() : -1;

            loadLevel(levelData.getLevelIndex());

            // Restore checkpoint state if we had an active checkpoint
            // (loadLevel clears it, but we need it for subsequent respawns)
            if (hasCheckpoint && checkpointState != null) {
                checkpointState.restoreFromSaved(checkpointX, checkpointY, checkpointCameraX, checkpointCameraY,
                        checkpointIndex);
            }

            frameCounter = 0;
            Sprite player = spriteManager.getSprite(configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE));

            // Use checkpoint position if available, otherwise level start.
            // ROM start/checkpoint coordinates are center-based.
            int spawnY = -1; // Track spawn Y for airborne detection
            if (hasCheckpoint) {
                player.setCentreX((short) checkpointX);
                player.setCentreY((short) checkpointY);
                spawnY = checkpointY;
                LOGGER.info("Set player position from checkpoint: X=" + checkpointX + ", Y=" + checkpointY +
                        " (center coordinates)");
            } else {
                int spawnX = levelData.getStartXPos();
                spawnY = levelData.getStartYPos();

                if (game instanceof DynamicStartPositionProvider dynamicStartProvider) {
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
                }

                player.setCentreX((short) spawnX);
                player.setCentreY((short) spawnY);
                LOGGER.info("Set player position from level start: X=" + spawnX +
                        ", Y=" + spawnY + " (center coordinates)" +
                        ", level: " + levelData.name());
            }

            if (player instanceof AbstractPlayableSprite) {
                AbstractPlayableSprite playable = (AbstractPlayableSprite) player;
                // Full state reset first
                playable.resetState();
                // Then set specific values
                playable.setXSpeed((short) 0);
                playable.setYSpeed((short) 0);
                playable.setGSpeed((short) 0);
                // ROM: air state is determined by first frame of physics.
                // For most levels, Sonic spawns on solid ground (air=false).
                // For SBZ3 (spawnY=0), Sonic spawns at the top of the level with
                // no ground and must fall — set air=true so gravity applies.
                playable.setAir(spawnY == 0);
                LOGGER.info("Player state after loadCurrentLevel: air=" + playable.getAir() +
                        ", ySpeed=" + playable.getYSpeed() + ", layer=" + player.getLayer());
                playable.setRolling(false);
                playable.setDead(false);
                playable.setHurt(false);
                playable.setDeathCountdown(0);
                playable.setInvulnerableFrames(0);
                playable.setInvincibleFrames(0);
                playable.setDirection(uk.co.jamesj999.sonic.physics.Direction.RIGHT);
                playable.setAngle((byte) 0);
                player.setLayer((byte) 0);
                playable.setHighPriority(false);
                playable.setPriorityBucket(RenderPriority.PLAYER_DEFAULT);

                // Clear rings on spawn (ROM behavior)
                playable.setRingCount(0);

                // Reset speed shoes effect and music tempo
                // Note: resetState is already called which clears speedShoes, but we also need
                // to reset audio
                uk.co.jamesj999.sonic.audio.AudioManager.getInstance().getBackend().setSpeedShoes(false);

                Camera camera = Camera.getInstance();
                camera.setFrozen(false); // Unlock camera after death
                camera.setFocusedSprite(playable);
                camera.updatePosition(true); // Force camera to player position

                Level currentLevel = getCurrentLevel();
                if (currentLevel != null) {
                    camera.setMinX((short) currentLevel.getMinX());
                    camera.setMaxX((short) currentLevel.getMaxX());
                    camera.setMinY((short) currentLevel.getMinY());
                    camera.setMaxY((short) currentLevel.getMaxY());
                    // ROM: LZ3/SBZ2 vertical wrapping — enabled when top boundary is negative
                    verticalWrapEnabled = camera.isVerticalWrapEnabled();
                    // Re-apply camera placement after level bounds are set.
                    // Some starts (notably S3K AIZ1 intro-skip) are far below Y=0 and
                    // must be clamped with the correct maxY before pit checks run.
                    camera.updatePosition(true);
                }

                // Initialize level events for dynamic boundary updates (game-specific)
                LevelEventProvider levelEvents = GameModuleRegistry.getCurrent().getLevelEventProvider();
                if (levelEvents != null) {
                    levelEvents.initLevel(currentZone, currentAct);
                }
            }

            // Reset sidekick (Tails) position near the main player on level load/restart
            AbstractPlayableSprite sidekick = spriteManager.getSidekick();
            if (sidekick != null) {
                sidekick.setX((short) (player.getX() - 40));
                sidekick.setY(player.getY());
                sidekick.setXSpeed((short) 0);
                sidekick.setYSpeed((short) 0);
                sidekick.setGSpeed((short) 0);
                sidekick.setAir(false);
                sidekick.setDead(false);
                sidekick.setDeathCountdown(0);
                sidekick.setHighPriority(false);
                sidekick.setDirection(uk.co.jamesj999.sonic.physics.Direction.RIGHT);
            }

            // Request title card for level starts and death respawns
            // Original Sonic 2 shows title card on all respawns (with or without
            // checkpoint)
            // In headless mode, skip title card by default to avoid control locking
            // during tests
            if (showTitleCard
                    && !graphicsManager.isHeadlessMode()
                    && !(zoneFeatureProvider != null && zoneFeatureProvider.shouldSuppressInitialTitleCard(currentZone, currentAct))) {
                requestTitleCard(currentZone, currentAct);
            }

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
        specialStageReturnLevelReloadRequested = true;
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

    /**
     * Request entry to special stage from a checkpoint star.
     * Called by CheckpointStarInstance when the player touches a star.
     */
    public void requestSpecialStageFromCheckpoint() {
        requestSpecialStageEntry();
    }

    /**
     * Request entry to special stage using the current game's access method.
     */
    public void requestSpecialStageEntry() {
        this.specialStageRequestedFromCheckpoint = true;
    }

    /**
     * Consumes and clears the special stage request flag.
     * 
     * @return true if a special stage was requested since last check
     */
    public boolean consumeSpecialStageRequest() {
        boolean requested = specialStageRequestedFromCheckpoint;
        specialStageRequestedFromCheckpoint = false;
        return requested;
    }

    /**
     * Consumes and clears the pending level-reload request for special-stage
     * return.
     *
     * @return true if the next act should be loaded before resuming gameplay
     */
    public boolean consumeSpecialStageReturnLevelReloadRequest() {
        boolean requested = specialStageReturnLevelReloadRequested;
        specialStageReturnLevelReloadRequested = false;
        return requested;
    }

    /**
     * Requests a title card to be shown for the current zone/act.
     * Called when a new level is loaded.
     *
     * @param zone Zone index (0-10)
     * @param act  Act index (0-2)
     */
    public void requestTitleCard(int zone, int act) {
        this.titleCardRequested = true;
        this.titleCardZone = zone;
        this.titleCardAct = act;
    }

    /**
     * Checks if a title card has been requested.
     *
     * @return true if a title card was requested since last check
     */
    public boolean isTitleCardRequested() {
        return titleCardRequested;
    }

    /**
     * @return true if vertical wrapping is active (ROM: LZ3/SBZ2 loop sections)
     */
    public boolean isVerticalWrapEnabled() {
        return verticalWrapEnabled;
    }

    /**
     * Consumes and clears the title card request flag.
     *
     * @return true if a title card was requested since last check
     */
    public boolean consumeTitleCardRequest() {
        boolean requested = titleCardRequested;
        titleCardRequested = false;
        return requested;
    }

    /**
     * Gets the zone index for the requested title card.
     *
     * @return zone index, or -1 if none requested
     */
    public int getTitleCardZone() {
        return titleCardZone;
    }

    /**
     * Gets the act index for the requested title card.
     *
     * @return act index, or -1 if none requested
     */
    public int getTitleCardAct() {
        return titleCardAct;
    }

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
        backgroundTilemapData = null;
        foregroundTilemapData = null;
        patternLookupData = null;
        prebuiltFgTilemap = null;
        prebuiltBgTilemap = null;
        currentZone = 0;
        currentAct = 0;
        frameCounter = 0;
        backgroundTilemapDirty = true;
        foregroundTilemapDirty = true;
        patternLookupDirty = true;
        specialStageRequestedFromCheckpoint = false;
        specialStageReturnLevelReloadRequested = false;
        titleCardRequested = false;
        titleCardZone = -1;
        titleCardAct = -1;
        respawnRequested = false;
        nextActRequested = false;
        nextZoneRequested = false;
        specificZoneActRequested = false;
        verticalWrapEnabled = false;
        levelInactiveForTransition = false;
        requestedZone = -1;
        requestedAct = -1;
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
        if (level instanceof uk.co.jamesj999.sonic.game.sonic2.Sonic2Level) {
            int zoneId = ((uk.co.jamesj999.sonic.game.sonic2.Sonic2Level) level).getZoneIndex();
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

    // ==================== Transition Request Methods ====================
    // These allow GameLoop to coordinate fades with level transitions

    /**
     * Request a respawn (death). GameLoop will handle the fade transition.
     */
    public void requestRespawn() {
        this.respawnRequested = true;
    }

    /**
     * Check and consume respawn request.
     * 
     * @return true if respawn was requested
     */
    public boolean consumeRespawnRequest() {
        boolean requested = respawnRequested;
        respawnRequested = false;
        return requested;
    }

    /**
     * Request transition to next act. GameLoop will handle the fade transition.
     */
    public void requestNextAct() {
        this.nextActRequested = true;
    }

    /**
     * Check and consume next act request.
     * 
     * @return true if next act was requested
     */
    public boolean consumeNextActRequest() {
        boolean requested = nextActRequested;
        nextActRequested = false;
        return requested;
    }

    /**
     * Request transition to next zone. GameLoop will handle the fade transition.
     */
    public void requestNextZone() {
        this.nextZoneRequested = true;
    }

    /**
     * Check and consume next zone request.
     * 
     * @return true if next zone was requested
     */
    public boolean consumeNextZoneRequest() {
        boolean requested = nextZoneRequested;
        nextZoneRequested = false;
        return requested;
    }

    /**
     * Request transition to a specific zone and act. GameLoop will handle the fade transition.
     *
     * @param zone the zone index (0-based)
     * @param act the act index (0-based)
     */
    public void requestZoneAndAct(int zone, int act) {
        requestZoneAndAct(zone, act, false);
    }

    /**
     * Request transition to a specific zone and act with optional level deactivation
     * during the pending fade.
     *
     * @param zone                the zone index (0-based)
     * @param act                 the act index (0-based)
     * @param deactivateLevelNow  true to freeze level updates until the transition completes
     */
    public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {
        this.requestedZone = zone;
        this.requestedAct = act;
        this.specificZoneActRequested = true;
        this.levelInactiveForTransition = deactivateLevelNow;
    }

    /**
     * Check and consume specific zone/act request.
     *
     * @return true if a specific zone/act was requested
     */
    public boolean consumeZoneActRequest() {
        boolean requested = specificZoneActRequested;
        specificZoneActRequested = false;
        return requested;
    }

    /**
     * Get the requested zone index. Only valid after consumeZoneActRequest() returns true.
     *
     * @return the requested zone index
     */
    public int getRequestedZone() {
        return requestedZone;
    }

    /**
     * Get the requested act index. Only valid after consumeZoneActRequest() returns true.
     *
     * @return the requested act index
     */
    public int getRequestedAct() {
        return requestedAct;
    }

    /**
     * Returns true while the current level should be treated as inactive for a
     * pending zone/act transition.
     */
    public boolean isLevelInactiveForTransition() {
        return levelInactiveForTransition;
    }

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
                    // Found a matching pattern - calculate offset from ref to pattern center
                    // Pattern center is at worldX+4, worldY+4
                    int offsetX = (worldX + 4) - refX;
                    int offsetY = (worldY + 4) - refY;
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

