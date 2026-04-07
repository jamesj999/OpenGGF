package com.openggf.level.objects;

import com.openggf.audio.AudioManager;
import com.openggf.audio.GameSound;
import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.GameStateManager;
import com.openggf.game.GameModule;
import com.openggf.game.LevelState;
import com.openggf.game.PlayableEntity;
import com.openggf.game.RespawnState;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.session.WorldSession;
import com.openggf.graphics.FadeManager;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.Level;
import com.openggf.level.ParallaxManager;
import com.openggf.level.WaterSystem;
import com.openggf.level.rings.RingManager;
import com.openggf.sprites.managers.SpriteManager;

import java.util.List;

/**
 * Minimal stub implementation of {@link ObjectServices} for unit tests.
 * All methods return null/0/no-op defaults. Override specific methods as needed.
 */
public class StubObjectServices implements ObjectServices {
    @Override public ObjectManager objectManager() { return null; }
    @Override public ObjectRenderManager renderManager() { return null; }
    @Override public LevelState levelGamestate() { return null; }
    @Override public RespawnState checkpointState() { return null; }
    @Override public Level currentLevel() { return null; }
    @Override public int romZoneId() { return 0; }
    @Override public int currentAct() { return 0; }
    @Override public int featureZoneId() { return 0; }
    @Override public int featureActId() { return 0; }
    @Override public ZoneFeatureProvider zoneFeatureProvider() { return null; }
    @Override public void playSfx(int soundId) {}
    @Override public void playSfx(GameSound sound) {}
    @Override public void playMusic(int musicId) {}
    @Override public void fadeOutMusic() {}
    @Override public AudioManager audioManager() { return null; }
    @Override public void spawnLostRings(PlayableEntity player, int frameCounter) {}
    @Override public Camera camera() { return null; }
    @Override public GameStateManager gameState() { return null; }
    @Override public WorldSession worldSession() { return null; }
    @Override public GameModule gameModule() { return null; }
    @Override public List<PlayableEntity> sidekicks() { return List.of(); }
    @Override public SpriteManager spriteManager() { return null; }
    @Override public GraphicsManager graphicsManager() { return null; }
    @Override public FadeManager fadeManager() { return null; }
    @Override public Rom rom() { return null; }
    @Override public RomByteReader romReader() { return null; }
    @Override public WaterSystem waterSystem() { return null; }
    @Override public ParallaxManager parallaxManager() { return null; }
    @Override public void advanceToNextLevel() {}
    @Override public void requestCreditsTransition() {}
    @Override public void requestSpecialStageEntry() {}
    @Override public void invalidateForegroundTilemap() {}
    @Override public boolean areAllRingsCollected() { return false; }
    @Override public void updatePalette(int paletteIndex, byte[] paletteData) {}
    @Override public RingManager ringManager() { return null; }
    @Override public void advanceZoneActOnly() {}
    @Override public void setApparentAct(int act) {}
    @Override public void requestSpecialStageFromCheckpoint() {}
    @Override public void requestBonusStageEntry(com.openggf.game.BonusStageType type) {}
    @Override public void requestBonusStageExit() {}
    @Override public void addBonusStageRings(int count) {}
    @Override public void setBonusStageShield(com.openggf.game.ShieldType type) {}
    @Override public void requestZoneAndAct(int zone, int act) {}
    @Override public void requestZoneAndAct(int zone, int act, boolean deactivateLevelNow) {}
    @Override public int getCurrentLevelMusicId() { return 0; }
    @Override public int[] findPatternOffset(int refX, int refY, int minTileIdx, int maxTileIdx, int searchRadius) { return null; }
    @Override public void saveBigRingReturn(com.openggf.level.BigRingReturnState state) {}
}
