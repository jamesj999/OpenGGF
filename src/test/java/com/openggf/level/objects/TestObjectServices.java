package com.openggf.level.objects;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameStateManager;
import com.openggf.game.LevelState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.RespawnState;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.rings.RingManager;
import com.openggf.sprites.managers.SpriteManager;

import java.util.List;

/**
 * Lightweight test double for {@link ObjectServices}.
 * Defaults to inert/null behavior and can be selectively wired to real or mocked managers.
 */
public class TestObjectServices implements ObjectServices {

    private LevelManager levelManager;
    private Camera camera;
    private GameStateManager gameState;
    private SpriteManager spriteManager;
    private FadeManager fadeManager;
    private WaterSystem waterSystem;
    private ParallaxManager parallaxManager;
    private GraphicsManager graphicsManager;
    private AudioManager audioManager;
    private Rom rom;
    private RomByteReader romReader;
    private List<PlayableEntity> sidekicks = List.of();

    public TestObjectServices withLevelManager(LevelManager levelManager) {
        this.levelManager = levelManager;
        return this;
    }

    public TestObjectServices withCamera(Camera camera) {
        this.camera = camera;
        return this;
    }

    public TestObjectServices withGameState(GameStateManager gameState) {
        this.gameState = gameState;
        return this;
    }

    public TestObjectServices withSpriteManager(SpriteManager spriteManager) {
        this.spriteManager = spriteManager;
        return this;
    }

    public TestObjectServices withFadeManager(FadeManager fadeManager) {
        this.fadeManager = fadeManager;
        return this;
    }

    public TestObjectServices withWaterSystem(WaterSystem waterSystem) {
        this.waterSystem = waterSystem;
        return this;
    }

    public TestObjectServices withParallaxManager(ParallaxManager parallaxManager) {
        this.parallaxManager = parallaxManager;
        return this;
    }

    public TestObjectServices withGraphicsManager(GraphicsManager graphicsManager) {
        this.graphicsManager = graphicsManager;
        return this;
    }

    public TestObjectServices withAudioManager(AudioManager audioManager) {
        this.audioManager = audioManager;
        return this;
    }

    public TestObjectServices withRom(Rom rom) {
        this.rom = rom;
        return this;
    }

    public TestObjectServices withRomReader(RomByteReader romReader) {
        this.romReader = romReader;
        return this;
    }

    public TestObjectServices withSidekicks(List<? extends PlayableEntity> sidekicks) {
        this.sidekicks = List.copyOf(sidekicks);
        return this;
    }

    @Override
    public ObjectManager objectManager() {
        return levelManager != null ? levelManager.getObjectManager() : null;
    }

    @Override
    public ObjectRenderManager renderManager() {
        return levelManager != null ? levelManager.getObjectRenderManager() : null;
    }

    @Override
    public LevelState levelGamestate() {
        return levelManager != null ? levelManager.getLevelGamestate() : null;
    }

    @Override
    public RespawnState checkpointState() {
        return levelManager != null ? levelManager.getCheckpointState() : null;
    }

    @Override
    public Level currentLevel() {
        return levelManager != null ? levelManager.getCurrentLevel() : null;
    }

    @Override
    public int romZoneId() {
        return levelManager != null ? levelManager.getRomZoneId() : 0;
    }

    @Override
    public int currentAct() {
        return levelManager != null ? levelManager.getCurrentAct() : 0;
    }

    @Override
    public int featureZoneId() {
        return levelManager != null ? levelManager.getFeatureZoneId() : 0;
    }

    @Override
    public int featureActId() {
        return levelManager != null ? levelManager.getFeatureActId() : 0;
    }

    @Override
    public ZoneFeatureProvider zoneFeatureProvider() {
        return levelManager != null ? levelManager.getZoneFeatureProvider() : null;
    }

    @Override
    public RingManager ringManager() {
        return levelManager != null ? levelManager.getRingManager() : null;
    }

    @Override
    public boolean areAllRingsCollected() {
        return levelManager != null && levelManager.areAllRingsCollected();
    }

    @Override
    public Camera camera() {
        return camera;
    }

    @Override
    public GameStateManager gameState() {
        return gameState;
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
    public GraphicsManager graphicsManager() {
        return graphicsManager;
    }

    @Override
    public AudioManager audioManager() {
        return audioManager;
    }

    @Override
    public void playSfx(int soundId) {
    }

    @Override
    public void playSfx(GameSound sound) {
    }

    @Override
    public void playMusic(int musicId) {
    }

    @Override
    public void fadeOutMusic() {
    }

    @Override
    public Rom rom() {
        return rom;
    }

    @Override
    public RomByteReader romReader() {
        return romReader;
    }

    @Override
    public List<PlayableEntity> sidekicks() {
        if (sidekicks.isEmpty() && spriteManager != null) {
            return List.copyOf(spriteManager.getSidekicks());
        }
        return sidekicks;
    }

    @Override
    public void spawnLostRings(PlayableEntity player, int frameCounter) {
    }

    @Override
    public void advanceToNextLevel() {
    }

    @Override
    public void requestCreditsTransition() {
    }

    @Override
    public void requestSpecialStageEntry() {
    }

    @Override
    public void invalidateForegroundTilemap() {
    }

    @Override
    public void updatePalette(int paletteIndex, byte[] paletteData) {
    }

    @Override
    public void advanceZoneActOnly() {
    }

    @Override
    public void requestSpecialStageFromCheckpoint() {
    }

    @Override
    public void requestZoneAndAct(int zone, int act) {
    }

    @Override
    public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {
    }

    @Override
    public int getCurrentLevelMusicId() {
        return levelManager != null ? levelManager.getCurrentLevelMusicId() : -1;
    }

    @Override
    public int[] findPatternOffset(int refX, int refY, int minTileIdx, int maxTileIdx, int searchRadius) {
        return levelManager != null
                ? levelManager.findPatternOffset(refX, refY, minTileIdx, maxTileIdx, searchRadius)
                : null;
    }

    @Override
    public void saveBigRingReturn(com.openggf.level.BigRingReturnState state) {
        if (levelManager != null) {
            levelManager.saveBigRingReturn(state);
        }
    }
}
