package com.openggf.level.objects;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.data.RomManager;
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
import java.io.IOException;
import java.util.List;

/**
 * Production implementation of {@link ObjectServices} backed by existing singletons.
 * A single instance is held by {@link ObjectManager} and shared across all objects.
 *
 * <p>The preferred constructor accepts a {@link LevelManager} reference so that
 * every call to a level-backed method avoids the overhead of a synchronized
 * {@code getInstance()} lookup. The no-arg constructor is provided for test
 * contexts where the LevelManager-backed methods are not exercised.</p>
 */
public class DefaultObjectServices implements ObjectServices {

    private static final java.util.logging.Logger LOG =
        java.util.logging.Logger.getLogger(DefaultObjectServices.class.getName());

    private final LevelManager levelManager;

    public DefaultObjectServices(LevelManager levelManager) {
        this.levelManager = levelManager;
    }

    /** No-arg constructor for test contexts that do not invoke LevelManager-backed methods. */
    public DefaultObjectServices() {
        this.levelManager = null;
    }

    private LevelManager lm() {
        return levelManager != null ? levelManager : LevelManager.getInstance();
    }

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
    public void playSfx(int soundId) {
        AudioManager.getInstance().playSfx(soundId);
    }

    @Override
    public void playSfx(GameSound sound) {
        AudioManager.getInstance().playSfx(sound);
    }

    @Override
    public void playMusic(int musicId) {
        AudioManager.getInstance().playMusic(musicId);
    }

    @Override
    public void fadeOutMusic() {
        AudioManager.getInstance().fadeOutMusic();
    }

    @Override
    public void spawnLostRings(PlayableEntity player, int frameCounter) {
        if (player instanceof com.openggf.sprites.playable.AbstractPlayableSprite aps) {
            lm().spawnLostRings(aps, frameCounter);
        } else {
            LOG.warning("spawnLostRings: player is not AbstractPlayableSprite, rings not spawned");
        }
    }

    @Override
    public Camera camera() {
        return Camera.getInstance();
    }

    @Override
    public GameStateManager gameState() {
        return GameStateManager.getInstance();
    }

    @Override
    public List<PlayableEntity> sidekicks() {
        return List.copyOf(SpriteManager.getInstance().getSidekicks());
    }

    @Override
    public SpriteManager spriteManager() {
        return SpriteManager.getInstance();
    }

    @Override
    public GraphicsManager graphicsManager() {
        return GraphicsManager.getInstance();
    }

    @Override
    public FadeManager fadeManager() {
        return FadeManager.getInstance();
    }

    @Override
    public Rom rom() throws IOException {
        return RomManager.getInstance().getRom();
    }

    @Override
    public RomByteReader romReader() throws IOException {
        return RomByteReader.fromRom(RomManager.getInstance().getRom());
    }

    @Override
    public WaterSystem waterSystem() {
        return WaterSystem.getInstance();
    }

    @Override
    public ParallaxManager parallaxManager() {
        return ParallaxManager.getInstance();
    }

    @Override
    public AudioManager audioManager() {
        return AudioManager.getInstance();
    }

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

    @Override
    public RingManager ringManager() {
        return lm().getRingManager();
    }

    @Override
    public boolean areAllRingsCollected() {
        return lm().areAllRingsCollected();
    }
}
