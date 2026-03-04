package com.openggf.game.sonic2;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.constants.Sonic2ObjectConstants;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.credits.Sonic2EndingProvider;
import com.openggf.game.sonic2.debug.Sonic2DebugModeProvider;
import com.openggf.game.sonic2.levelselect.LevelSelectManager;
import com.openggf.game.sonic2.objects.BlueBallsObjectInstance;
import com.openggf.game.sonic2.objects.BonusBlockObjectInstance;
import com.openggf.game.sonic2.objects.LauncherBallObjectInstance;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic2.scroll.Sonic2ScrollHandlerProvider;
import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.game.sonic2.titlescreen.TitleScreenManager;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.EndingProvider;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelGamestate;
import com.openggf.game.OscillationManager;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.LevelSelectProvider;
import com.openggf.game.LevelState;
import com.openggf.game.RespawnState;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.RomOffsetProvider;
import com.openggf.game.DebugModeProvider;
import com.openggf.game.DebugOverlayProvider;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.ZoneArtProvider;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.sonic2.audio.Sonic2AudioProfile;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.PlaneSwitcherConfig;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;

public class Sonic2GameModule implements GameModule {
    private final GameAudioProfile audioProfile = new Sonic2AudioProfile();
    private final SpecialStageProvider specialStageProvider = new Sonic2SpecialStageProvider();
    private Sonic2ObjectArtProvider objectArtProvider;
    private Sonic2ZoneFeatureProvider zoneFeatureProvider;
    private PhysicsProvider physicsProvider;
    private ObjectRegistry objectRegistry;

    @Override
    public String getIdentifier() {
        return "Sonic2";
    }

    @Override
    public Game createGame(Rom rom) {
        return new Sonic2(rom);
    }

    @Override
    public ObjectRegistry createObjectRegistry() {
        if (objectRegistry == null) {
            objectRegistry = new Sonic2ObjectRegistry();
        }
        return objectRegistry;
    }

    @Override
    public GameAudioProfile getAudioProfile() {
        return audioProfile;
    }

    @Override
    public TouchResponseTable createTouchResponseTable(RomByteReader romReader) {
        return new TouchResponseTable(romReader,
                Sonic2ObjectConstants.TOUCH_SIZES_ADDR,
                Sonic2ObjectConstants.TOUCH_ENTRY_COUNT);
    }

    @Override
    public int getPlaneSwitcherObjectId() {
        return Sonic2ObjectIds.LAYER_SWITCHER;
    }

    @Override
    public int getCheckpointObjectId() {
        return Sonic2ObjectIds.CHECKPOINT;
    }

    @Override
    public PlaneSwitcherConfig getPlaneSwitcherConfig() {
        return new PlaneSwitcherConfig(
                Sonic2ObjectConstants.PATH0_TOP_SOLID_BIT,
                Sonic2ObjectConstants.PATH0_LRB_SOLID_BIT,
                Sonic2ObjectConstants.PATH1_TOP_SOLID_BIT,
                Sonic2ObjectConstants.PATH1_LRB_SOLID_BIT);
    }

    @Override
    public LevelEventProvider getLevelEventProvider() {
        return Sonic2LevelEventManager.getInstance();
    }

    @Override
    public RespawnState createRespawnState() {
        return new CheckpointState();
    }

    @Override
    public LevelState createLevelState() {
        return new LevelGamestate();
    }

    @Override
    public TitleCardProvider getTitleCardProvider() {
        return TitleCardManager.getInstance();
    }

    @Override
    public ZoneRegistry getZoneRegistry() {
        return Sonic2ZoneRegistry.getInstance();
    }

    @Override
    public SpecialStageProvider getSpecialStageProvider() {
        return specialStageProvider;
    }

    @Override
    public int getSpecialStageCycleCount() {
        return 7;
    }

    @Override
    public int getChaosEmeraldCount() {
        return 7;
    }

    @Override
    public ScrollHandlerProvider getScrollHandlerProvider() {
        return new Sonic2ScrollHandlerProvider();
    }

    @Override
    public ZoneFeatureProvider getZoneFeatureProvider() {
        if (zoneFeatureProvider == null) {
            zoneFeatureProvider = new Sonic2ZoneFeatureProvider();
        }
        return zoneFeatureProvider;
    }

    @Override
    public WaterDataProvider getWaterDataProvider() {
        return new Sonic2WaterDataProvider();
    }

    @Override
    public RomOffsetProvider getRomOffsetProvider() {
        return new Sonic2RomOffsetProvider();
    }

    @Override
    public DebugModeProvider getDebugModeProvider() {
        return new Sonic2DebugModeProvider();
    }

    @Override
    public DebugOverlayProvider getDebugOverlayProvider() {
        // Debug overlay content is currently handled by the generic DebugRenderer
        // Future: Create Sonic2DebugOverlayProvider for game-specific overlay content
        return null;
    }

    @Override
    public ZoneArtProvider getZoneArtProvider() {
        return new Sonic2ZoneArtProvider();
    }

    @Override
    public void onLevelLoad() {
        // Reset oscillation values used by moving platforms, etc.
        OscillationManager.reset();
        // Reset object-specific static state that persists across load/unload cycles
        BlueBallsObjectInstance.resetGlobalState();
        BonusBlockObjectInstance.resetGroupCounters();
        LauncherBallObjectInstance.clearActiveCaptures();
    }

    @Override
    public TitleScreenProvider getTitleScreenProvider() {
        return TitleScreenManager.getInstance();
    }

    @Override
    public LevelSelectProvider getLevelSelectProvider() {
        return LevelSelectManager.getInstance();
    }

    @Override
    public ObjectArtProvider getObjectArtProvider() {
        if (objectArtProvider == null) {
            objectArtProvider = new Sonic2ObjectArtProvider();
        }
        return objectArtProvider;
    }

    @Override
    public PhysicsProvider getPhysicsProvider() {
        if (physicsProvider == null) {
            physicsProvider = new Sonic2PhysicsProvider();
        }
        return physicsProvider;
    }

    private final LevelInitProfile levelInitProfile = new Sonic2LevelInitProfile();

    @Override
    public LevelInitProfile getLevelInitProfile() {
        return levelInitProfile;
    }

    @Override
    public boolean hasInlineParallaxHandlers() {
        return true;
    }

    @Override
    public SuperStateController createSuperStateController(
            AbstractPlayableSprite player) {
        if (CrossGameFeatureProvider.isActive()) {
            return CrossGameFeatureProvider.getInstance().createSuperStateController(player);
        }
        return new Sonic2SuperStateController(player);
    }

    @Override
    public boolean supportsSidekick() {
        return true;
    }

    @Override
    public EndingProvider getEndingProvider() {
        return new Sonic2EndingProvider();
    }
}
