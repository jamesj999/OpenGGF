package com.openggf.level.objects;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
import com.openggf.debug.DebugOverlayManager;
import com.openggf.game.BonusStageType;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.EngineServices;
import com.openggf.game.GameModule;
import com.openggf.game.GameRng;
import com.openggf.game.GameRuntime;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.RespawnState;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.BigRingReturnState;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.rings.RingManager;
import com.openggf.sprites.managers.SpriteManager;
import java.io.IOException;
import java.util.Objects;
import java.util.List;

/**
 * Production implementation of {@link ObjectServices} backed by {@link GameRuntime}.
 * A single instance is held by {@link ObjectManager} and shared across all objects.
 *
 * <p>The constructor accepts a {@link GameRuntime} reference so that
 * every runtime-owned method reads from the runtime container.</p>
 */
public class DefaultObjectServices implements ObjectServices {

    private static final java.util.logging.Logger LOG =
        java.util.logging.Logger.getLogger(DefaultObjectServices.class.getName());

    private final LevelManager levelManager;
    private final Camera camera;
    private final GameStateManager gameState;
    private final SpriteManager spriteManager;
    private final FadeManager fadeManager;
    private final WaterSystem waterSystem;
    private final ParallaxManager parallaxManager;
    private final WorldSession worldSession;
    private final GameRng rng;
    private final EngineServices engineServices;

    /**
     * Primary constructor backed by a GameRuntime.
     */
    public DefaultObjectServices(GameRuntime runtime) {
        this(Objects.requireNonNull(runtime, "runtime").getLevelManager(),
                runtime.getCamera(),
                runtime.getGameState(),
                runtime.getSpriteManager(),
                runtime.getFadeManager(),
                runtime.getWaterSystem(),
                runtime.getParallaxManager(),
                runtime.getWorldSession(),
                runtime.getRng(),
                runtime.getEngineServices());
    }

    public DefaultObjectServices(LevelManager levelManager,
                                 Camera camera,
                                 GameStateManager gameState,
                                 SpriteManager spriteManager,
                                 FadeManager fadeManager,
                                 WaterSystem waterSystem,
                                 ParallaxManager parallaxManager) {
        this(levelManager, camera, gameState, spriteManager, fadeManager, waterSystem,
                parallaxManager, null, new GameRng(GameRng.Flavour.S1_S2),
                EngineServices.fromLegacySingletonsForBootstrap());
    }

    private DefaultObjectServices(LevelManager levelManager,
                                 Camera camera,
                                 GameStateManager gameState,
                                 SpriteManager spriteManager,
                                 FadeManager fadeManager,
                                 WaterSystem waterSystem,
                                 ParallaxManager parallaxManager,
                                 WorldSession worldSession,
                                 GameRng rng,
                                 EngineServices engineServices) {
        this.levelManager = Objects.requireNonNull(levelManager, "levelManager");
        this.camera = Objects.requireNonNull(camera, "camera");
        this.gameState = Objects.requireNonNull(gameState, "gameState");
        this.spriteManager = Objects.requireNonNull(spriteManager, "spriteManager");
        this.fadeManager = Objects.requireNonNull(fadeManager, "fadeManager");
        this.waterSystem = Objects.requireNonNull(waterSystem, "waterSystem");
        this.parallaxManager = Objects.requireNonNull(parallaxManager, "parallaxManager");
        this.worldSession = worldSession;
        this.rng = Objects.requireNonNull(rng, "rng");
        this.engineServices = Objects.requireNonNull(engineServices, "engineServices");
    }

    private LevelManager lm() {
        return levelManager;
    }

    // ── Level state ─────────────────────────────────────────────────────

    @Override
    public ObjectManager objectManager() {
        return lm().getObjectManager();
    }

    @Override
    public ObjectRenderManager renderManager() {
        return lm().getObjectRenderManager();
    }

    @Override
    public LevelState levelGamestate() {
        return lm().getLevelGamestate();
    }

    @Override
    public RespawnState checkpointState() {
        return lm().getCheckpointState();
    }

    @Override
    public Level currentLevel() {
        return lm().getCurrentLevel();
    }

    @Override
    public int romZoneId() {
        return lm().getRomZoneId();
    }

    @Override
    public int currentAct() {
        return lm().getCurrentAct();
    }

    @Override
    public int featureZoneId() {
        return lm().getFeatureZoneId();
    }

    @Override
    public int featureActId() {
        return lm().getFeatureActId();
    }

    @Override
    public ZoneFeatureProvider zoneFeatureProvider() {
        return lm().getZoneFeatureProvider();
    }

    @Override
    public RingManager ringManager() {
        return lm().getRingManager();
    }

    @Override
    public boolean areAllRingsCollected() {
        return lm().areAllRingsCollected();
    }

    // ── Direct from runtime ─────────────────────────────────────────────

    @Override
    public Camera camera() {
        return camera;
    }

    @Override
    public GameStateManager gameState() {
        return gameState;
    }

    @Override
    public WorldSession worldSession() {
        return worldSession;
    }

    @Override
    public GameModule gameModule() {
        if (worldSession != null) {
            return worldSession.getGameModule();
        }
        return levelManager.getGameModule();
    }

    @Override
    public SpriteManager spriteManager() {
        return spriteManager;
    }

    @Override
    public FadeManager fadeManager() {
        return fadeManager;
    }

    @Override
    public WaterSystem waterSystem() {
        return waterSystem;
    }

    @Override
    public ParallaxManager parallaxManager() {
        return parallaxManager;
    }

    @Override
    public GameRng rng() {
        return rng;
    }

    // ── Engine globals (not runtime-owned) ──────────────────────────────

    @Override
    public GraphicsManager graphicsManager() {
        return engineServices.graphics();
    }

    @Override
    public AudioManager audioManager() {
        return engineServices.audio();
    }

    @Override
    public EngineServices engineServices() {
        return engineServices;
    }

    @Override
    public SonicConfigurationService configuration() {
        return engineServices.configuration();
    }

    @Override
    public DebugOverlayManager debugOverlay() {
        return engineServices.debugOverlay();
    }

    @Override
    public RomManager romManager() {
        return engineServices.roms();
    }

    @Override
    public CrossGameFeatureProvider crossGameFeatures() {
        return engineServices.crossGameFeatures();
    }

    // ── Audio convenience ───────────────────────────────────────────────

    @Override
    public void playSfx(int soundId) {
        audioManager().playSfx(soundId);
    }

    @Override
    public void playSfx(GameSound sound) {
        audioManager().playSfx(sound);
    }

    @Override
    public void playMusic(int musicId) {
        audioManager().playMusic(musicId);
    }

    @Override
    public void fadeOutMusic() {
        audioManager().fadeOutMusic();
    }

    // ── ROM (engine global) ─────────────────────────────────────────────

    @Override
    public Rom rom() throws IOException {
        return romManager().getRom();
    }

    @Override
    public RomByteReader romReader() throws IOException {
        return RomByteReader.fromRom(rom());
    }

    // ── Sidekicks ───────────────────────────────────────────────────────

    @Override
    public List<PlayableEntity> sidekicks() {
        return List.copyOf(spriteManager.getSidekicks());
    }

    // ── Lost rings ──────────────────────────────────────────────────────

    @Override
    public void spawnLostRings(PlayableEntity player, int frameCounter) {
        if (player instanceof com.openggf.sprites.playable.AbstractPlayableSprite aps) {
            lm().spawnLostRings(aps, frameCounter);
        } else {
            LOG.warning("spawnLostRings: player is not AbstractPlayableSprite, rings not spawned");
        }
    }

    // ── Level actions ───────────────────────────────────────────────────

    @Override
    public void advanceToNextLevel() {
        try {
            lm().advanceToNextLevel();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("Failed to advance to next level", e);
        }
    }

    @Override
    public void requestCreditsTransition() {
        lm().requestCreditsTransition();
    }

    @Override
    public void requestSpecialStageEntry() {
        lm().requestSpecialStageEntry();
    }

    @Override
    public void invalidateForegroundTilemap() {
        lm().invalidateForegroundTilemap();
    }

    @Override
    public void updatePalette(int paletteIndex, byte[] paletteData) {
        lm().updatePalette(paletteIndex, paletteData);
    }

    // ── Level transition actions ───────────────────────────────────────

    @Override
    public void advanceZoneActOnly() {
        lm().advanceZoneActOnly();
    }

    @Override
    public void setApparentAct(int act) {
        lm().setApparentAct(act);
    }

    @Override
    public void requestSpecialStageFromCheckpoint() {
        lm().requestSpecialStageFromCheckpoint();
    }

    @Override
    public void requestBonusStageEntry(BonusStageType type) {
        lm().requestBonusStageEntry(type);
    }

    @Override
    public void requestBonusStageExit() {
        try {
            com.openggf.game.GameServices.bonusStage().requestExit();
        } catch (Exception e) {
            LOG.warning("requestBonusStageExit failed: " + e.getMessage());
        }
    }

    @Override
    public void addBonusStageRings(int count) {
        try {
            com.openggf.game.GameServices.bonusStage().addRings(count);
        } catch (Exception e) {
            LOG.warning("addBonusStageRings failed: " + e.getMessage());
        }
    }

    @Override
    public void setBonusStageShield(com.openggf.game.ShieldType type) {
        try {
            com.openggf.game.GameServices.bonusStage().setAwardedShield(type);
        } catch (Exception e) {
            LOG.warning("setBonusStageShield failed: " + e.getMessage());
        }
    }

    @Override
    public void requestZoneAndAct(int zone, int act) {
        lm().requestZoneAndAct(zone, act);
    }

    @Override
    public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {
        lm().requestZoneAndAct(zone, act, deactivateLevelNow);
    }

    // ── Level queries ──────────────────────────────────────────────────

    @Override
    public int getCurrentLevelMusicId() {
        return lm().getCurrentLevelMusicId();
    }

    @Override
    public int[] findPatternOffset(int refX, int refY, int minTileIdx, int maxTileIdx, int searchRadius) {
        return lm().findPatternOffset(refX, refY, minTileIdx, maxTileIdx, searchRadius);
    }

    @Override
    public void saveBigRingReturn(BigRingReturnState state) {
        lm().saveBigRingReturn(state);
    }

    // ── Game-specific providers ─────────────────────────────────────────

    @Override
    public LevelEventProvider levelEventProvider() {
        GameModule gm = gameModule();
        return gm != null ? gm.getLevelEventProvider() : null;
    }

    @Override
    public TitleCardProvider titleCardProvider() {
        GameModule gm = gameModule();
        return gm != null ? gm.getTitleCardProvider() : null;
    }

    @Override
    public <T> T gameService(Class<T> type) {
        GameModule gm = gameModule();
        return gm != null ? gm.getGameService(type) : null;
    }
}
