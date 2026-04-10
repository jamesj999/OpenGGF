package com.openggf.game.sonic2;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.constants.Sonic2Constants;
import com.openggf.game.sonic2.constants.Sonic2ObjectConstants;
import com.openggf.game.sonic2.constants.Sonic2ObjectIds;
import com.openggf.game.sonic2.credits.Sonic2EndingProvider;
import com.openggf.game.sonic2.debug.Sonic2DebugModeProvider;
import com.openggf.game.sonic2.levelselect.LevelSelectManager;
import com.openggf.game.sonic2.objects.BlueBallsObjectInstance;
import com.openggf.game.sonic2.objects.BombPrizeObjectInstance;
import com.openggf.game.sonic2.objects.BonusBlockObjectInstance;
import com.openggf.game.sonic2.objects.LauncherBallObjectInstance;
import com.openggf.game.sonic2.objects.MTZLongPlatformObjectInstance;
import com.openggf.game.sonic2.objects.SmashableGroundObjectInstance;
import com.openggf.game.sonic2.objects.Sonic2ObjectRegistry;
import com.openggf.game.sonic2.scroll.Sonic2ScrollHandlerProvider;
import com.openggf.game.sonic2.scroll.Sonic2ZoneConstants;
import com.openggf.game.sonic2.titlecard.TitleCardManager;
import com.openggf.game.sonic2.titlescreen.TitleScreenManager;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.DonorCapabilities;
import com.openggf.game.EndingProvider;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic2.constants.Sonic2AnimationIds;
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
import com.openggf.game.GameId;
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
    private final Sonic2LevelEventManager levelEventManager = new Sonic2LevelEventManager();
    private final Sonic2ZoneRegistry zoneRegistry = new Sonic2ZoneRegistry();
    private final com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug specialStageSpriteDebug =
            new com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug();
    private final com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager specialStageManager =
            new com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager(specialStageSpriteDebug);
    private final SpecialStageProvider specialStageProvider = new Sonic2SpecialStageProvider(specialStageManager);
    private final DebugModeProvider debugModeProvider =
            new Sonic2DebugModeProvider(specialStageManager, specialStageSpriteDebug);
    private final LevelInitProfile levelInitProfile = new Sonic2LevelInitProfile(levelEventManager);
    private final TitleCardManager titleCardProvider = new TitleCardManager();
    private final TitleScreenManager titleScreenProvider = new TitleScreenManager();
    private final LevelSelectManager levelSelectProvider = new LevelSelectManager();
    private Sonic2ObjectArtProvider objectArtProvider;
    private Sonic2ZoneFeatureProvider zoneFeatureProvider;
    private PhysicsProvider physicsProvider;
    private ObjectRegistry objectRegistry;

    @Override
    public String getIdentifier() {
        return "Sonic2";
    }

    @Override
    public int getTailsTailVramBase() {
        return Sonic2Constants.ART_TILE_TAILS_TAILS;
    }

    @Override
    public GameId getGameId() {
        return GameId.S2;
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
        return levelEventManager;
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
        return titleCardProvider;
    }

    @Override
    public ZoneRegistry getZoneRegistry() {
        return zoneRegistry;
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

    // Fresh instance per call. Callers store for the level's lifetime. Stateless.
    @Override
    public ScrollHandlerProvider getScrollHandlerProvider() {
        return new Sonic2ScrollHandlerProvider();
    }

    // Lazily cached — same instance across level loads. Reset via module replacement.
    @Override
    public ZoneFeatureProvider getZoneFeatureProvider() {
        if (zoneFeatureProvider == null) {
            zoneFeatureProvider = new Sonic2ZoneFeatureProvider();
        }
        return zoneFeatureProvider;
    }

    // Fresh instance per call. Callers store for the level's lifetime. Stateless.
    @Override
    public WaterDataProvider getWaterDataProvider() {
        return new Sonic2WaterDataProvider();
    }

    // Fresh instance per call. Callers store for the level's lifetime. Stateless.
    @Override
    public RomOffsetProvider getRomOffsetProvider() {
        return new Sonic2RomOffsetProvider();
    }

    // Module-owned instance so special-stage debug/controller state stays coherent.
    @Override
    public DebugModeProvider getDebugModeProvider() {
        return debugModeProvider;
    }

    @Override
    public DebugOverlayProvider getDebugOverlayProvider() {
        // Debug overlay content is currently handled by the generic DebugRenderer
        // Future: Create Sonic2DebugOverlayProvider for game-specific overlay content
        return null;
    }

    // Fresh instance per call. Callers store for the level's lifetime. Stateless.
    @Override
    public ZoneArtProvider getZoneArtProvider() {
        return new Sonic2ZoneArtProvider();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getGameService(Class<T> type) {
        if (type == Sonic2LevelEventManager.class) return (T) levelEventManager;
        if (type == Sonic2ZoneRegistry.class) return (T) zoneRegistry;
        if (type == com.openggf.game.sonic2.debug.Sonic2SpecialStageSpriteDebug.class)
            return (T) specialStageSpriteDebug;
        if (type == com.openggf.game.sonic2.specialstage.Sonic2SpecialStageManager.class)
            return (T) specialStageManager;
        return null;
    }

    @Override
    public void onLevelLoad() {
        // Reset oscillation values used by moving platforms, etc.
        OscillationManager.reset();
        // Reset object-specific static state that persists across load/unload cycles
        BlueBallsObjectInstance.resetGlobalState();
        BonusBlockObjectInstance.resetGroupCounters();
        LauncherBallObjectInstance.clearActiveCaptures();
        ButtonVineTriggerManager.reset();
        SmashableGroundObjectInstance.resetGlobalState();
        MTZLongPlatformObjectInstance.resetGlobalState();
        BombPrizeObjectInstance.resetGlobalState();
    }

    @Override
    public TitleScreenProvider getTitleScreenProvider() {
        return titleScreenProvider;
    }

    @Override
    public LevelSelectProvider getLevelSelectProvider() {
        return levelSelectProvider;
    }

    // Lazily cached — same instance across level loads. Reset via module replacement.
    @Override
    public ObjectArtProvider getObjectArtProvider() {
        if (objectArtProvider == null) {
            objectArtProvider = new Sonic2ObjectArtProvider();
        }
        return objectArtProvider;
    }

    // Lazily cached — same instance across level loads. Reset via module replacement.
    @Override
    public PhysicsProvider getPhysicsProvider() {
        if (physicsProvider == null) {
            physicsProvider = new Sonic2PhysicsProvider();
        }
        return physicsProvider;
    }

    @Override
    public LevelInitProfile getLevelInitProfile() {
        return levelInitProfile;
    }

    @Override
    public SuperStateController createSuperStateController(
            AbstractPlayableSprite player) {
        if (CrossGameFeatureProvider.isActive()) {
            return com.openggf.game.RuntimeManager.getEngineServices().crossGameFeatures().createSuperStateController(player);
        }
        return new Sonic2SuperStateController(player);
    }

    @Override
    public boolean supportsSidekick() {
        return true;
    }

    @Override
    public boolean isSidekickSuppressedForZone(int zoneId) {
        return zoneId == Sonic2ZoneConstants.ZONE_SCZ
            || zoneId == Sonic2ZoneConstants.ZONE_WFZ
            || zoneId == Sonic2ZoneConstants.ZONE_DEZ;
    }

    // Fresh instance per call. Callers store for the level's lifetime. Stateless.
    @Override
    public EndingProvider getEndingProvider() {
        return new Sonic2EndingProvider();
    }

    @Override
    public DonorCapabilities getDonorCapabilities() {
        return Sonic2DonorCapabilities.INSTANCE;
    }

    /** Lazily-constructed singleton holding S2 donation metadata. */
    private static final class Sonic2DonorCapabilities implements DonorCapabilities {

        static final Sonic2DonorCapabilities INSTANCE = new Sonic2DonorCapabilities();

        private static final java.util.Set<PlayerCharacter> CHARACTERS =
                java.util.Set.of(
                        PlayerCharacter.SONIC_ALONE,
                        PlayerCharacter.SONIC_AND_TAILS,
                        PlayerCharacter.TAILS_ALONE);

        private static final java.util.Map<CanonicalAnimation, CanonicalAnimation> FALLBACKS =
                buildFallbacks();

        private static java.util.Map<CanonicalAnimation, CanonicalAnimation> buildFallbacks() {
            return DonorCapabilities.buildFallbackMap(
                    Sonic2AnimationIds.values(), Sonic2AnimationIds::toCanonical,
                    java.util.Map.ofEntries(
                            // S1-specific animations -> nearest S2 native fallback
                            java.util.Map.entry(CanonicalAnimation.STOP,        CanonicalAnimation.SKID),
                            java.util.Map.entry(CanonicalAnimation.WARP1,       CanonicalAnimation.ROLL),
                            java.util.Map.entry(CanonicalAnimation.WARP2,       CanonicalAnimation.ROLL),
                            java.util.Map.entry(CanonicalAnimation.WARP3,       CanonicalAnimation.ROLL),
                            java.util.Map.entry(CanonicalAnimation.WARP4,       CanonicalAnimation.ROLL),
                            java.util.Map.entry(CanonicalAnimation.FLOAT3,      CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.FLOAT4,      CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.LEAP1,       CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.LEAP2,       CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.SURF,        CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.GET_AIR,     CanonicalAnimation.BUBBLE),
                            java.util.Map.entry(CanonicalAnimation.BURNT,       CanonicalAnimation.HURT),
                            java.util.Map.entry(CanonicalAnimation.SHRINK,      CanonicalAnimation.DEATH),
                            java.util.Map.entry(CanonicalAnimation.WATER_SLIDE, CanonicalAnimation.SLIDE),
                            java.util.Map.entry(CanonicalAnimation.NULL_ANIM,   CanonicalAnimation.WAIT),
                            // S3K-specific animations -> nearest S2 native fallback
                            java.util.Map.entry(CanonicalAnimation.BLINK,       CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.GET_UP,      CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.VICTORY,     CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.GLIDE_DROP,  CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.GLIDE_LAND,  CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.GLIDE_SLIDE, CanonicalAnimation.SLIDE),
                            java.util.Map.entry(CanonicalAnimation.BLANK,       CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.HURT_FALL,   CanonicalAnimation.HURT)
                    ));
        }

        @Override
        public java.util.Set<PlayerCharacter> getPlayableCharacters() { return CHARACTERS; }

        @Override
        public boolean hasSpindash() { return true; }

        @Override
        public boolean hasSuperTransform() { return true; }

        @Override
        public boolean hasHyperTransform() { return false; }

        @Override
        public boolean hasInstaShield() { return false; }

        @Override
        public boolean hasElementalShields() { return false; }

        @Override
        public boolean hasSidekick() { return true; }

        @Override
        public java.util.Map<CanonicalAnimation, CanonicalAnimation> getAnimationFallbacks() {
            return FALLBACKS;
        }

        @Override
        public int resolveNativeId(CanonicalAnimation canonical) {
            return Sonic2AnimationIds.fromCanonical(canonical);
        }

        @Override
        public com.openggf.data.PlayerSpriteArtProvider getPlayerArtProvider(
                com.openggf.data.RomByteReader reader) {
            var art = new Sonic2PlayerArt(reader);
            return art::loadForCharacter;
        }
    }
}
