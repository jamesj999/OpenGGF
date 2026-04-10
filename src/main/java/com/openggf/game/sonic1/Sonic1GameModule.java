package com.openggf.game.sonic1;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic1.audio.Sonic1AudioProfile;
import com.openggf.game.sonic1.events.Sonic1LevelEventManager;
import com.openggf.game.sonic1.objects.Sonic1StomperDoorObjectInstance;
import com.openggf.game.sonic1.scroll.Sonic1ZoneConstants;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageProvider;
import com.openggf.game.sonic1.titlescreen.Sonic1TitleScreenManager;
import com.openggf.game.DebugOverlayProvider;
import com.openggf.game.EndingProvider;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.LevelSelectProvider;
import com.openggf.game.LevelState;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.RespawnState;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.credits.Sonic1EndingProvider;
import com.openggf.game.sonic1.levelselect.Sonic1LevelSelectManager;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.scroll.Sonic1ScrollHandlerProvider;
import com.openggf.game.CheckpointState;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.DonorCapabilities;
import com.openggf.game.GameId;
import com.openggf.game.LevelGamestate;
import com.openggf.game.OscillationManager;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic1.constants.Sonic1AnimationIds;
import com.openggf.game.sonic1.titlecard.Sonic1TitleCardManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.PlaneSwitcherConfig;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;

/**
 * GameModule implementation for Sonic the Hedgehog 1 (Mega Drive/Genesis).
 */
public class Sonic1GameModule implements GameModule {
    private final GameAudioProfile audioProfile = new Sonic1AudioProfile();
    private final SpecialStageProvider specialStageProvider = new Sonic1SpecialStageProvider();
    private final Sonic1LevelEventManager levelEventManager = new Sonic1LevelEventManager();
    private final Sonic1ZoneRegistry zoneRegistry = new Sonic1ZoneRegistry();
    private final Sonic1SwitchManager switchManager = new Sonic1SwitchManager();
    private final Sonic1ConveyorState conveyorState = new Sonic1ConveyorState();
    private final LevelInitProfile levelInitProfile =
            new Sonic1LevelInitProfile(levelEventManager, switchManager, conveyorState);
    private PhysicsProvider physicsProvider;

    @Override
    public String getIdentifier() {
        return "Sonic1";
    }

    @Override
    public GameId getGameId() {
        return GameId.S1;
    }

    @Override
    public Game createGame(Rom rom) {
        return new Sonic1(rom);
    }

    @Override
    public ObjectRegistry createObjectRegistry() {
        return new Sonic1ObjectRegistry();
    }

    @Override
    public GameAudioProfile getAudioProfile() {
        return audioProfile;
    }

    @Override
    public TouchResponseTable createTouchResponseTable(RomByteReader romReader) {
        // S1 ReactToItem .sizes table: 36 entries at 0x1B5E4.
        // S1 uses `lea .sizes-2(pc,d0.w)` so effective base is .sizes-2 = 0x1B5E2.
        return new TouchResponseTable(romReader,
                Sonic1Constants.TOUCH_SIZES_ADDR, Sonic1Constants.TOUCH_ENTRY_COUNT);
    }

    @Override
    public int getPlaneSwitcherObjectId() {
        // Sonic 1 does not have a dedicated plane switcher object
        return 0;
    }

    @Override
    public int getCheckpointObjectId() {
        return Sonic1ObjectIds.LAMPPOST;
    }

    @Override
    public PlaneSwitcherConfig getPlaneSwitcherConfig() {
        return new PlaneSwitcherConfig((byte) 0, (byte) 0, (byte) 0, (byte) 0);
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
        return Sonic1TitleCardManager.getInstance();
    }

    @Override
    public TitleScreenProvider getTitleScreenProvider() {
        return Sonic1TitleScreenManager.getInstance();
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
        return 6;
    }

    @Override
    public int getChaosEmeraldCount() {
        return 6;
    }

    @Override
    public ScrollHandlerProvider getScrollHandlerProvider() {
        return new Sonic1ScrollHandlerProvider();
    }

    @Override
    public ZoneFeatureProvider getZoneFeatureProvider() {
        return new Sonic1ZoneFeatureProvider();
    }

    @Override
    public WaterDataProvider getWaterDataProvider() {
        return new Sonic1WaterDataProvider();
    }

    @Override
    public DebugOverlayProvider getDebugOverlayProvider() {
        return null;
    }

    @Override
    public LevelSelectProvider getLevelSelectProvider() {
        return Sonic1LevelSelectManager.getInstance();
    }

    @Override
    public ObjectArtProvider getObjectArtProvider() {
        // Keep provider per-load to avoid stale same-zone state carrying across restarts.
        return new Sonic1ObjectArtProvider();
    }

    @Override
    public void applyPlaneSwitching(AbstractPlayableSprite player) {
        levelEventManager.getLoopManager().update(player);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getGameService(Class<T> type) {
        if (type == Sonic1LevelEventManager.class) return (T) levelEventManager;
        if (type == Sonic1ZoneRegistry.class) return (T) zoneRegistry;
        if (type == Sonic1SwitchManager.class) return (T) switchManager;
        if (type == Sonic1ConveyorState.class) return (T) conveyorState;
        return null;
    }

    @Override
    public void onLevelLoad() {
        // Reset oscillation values to Sonic 1 settings.
        // S1 differs from S2 in oscillators 8+ (amplitude, initial values).
        // Reference: docs/s1disasm/_inc/Oscillatory Routines.asm
        OscillationManager.resetForSonic1();
        // Reset switch state for new level (Sonic 1 f_switch array)
        switchManager.reset();
        // Reset v_obj6B singleton flag for SBZ3 StomperDoor
        Sonic1StomperDoorObjectInstance.resetSbz3Flag();
        // Reset conveyor belt state for new level (Sonic 1 f_conveyrev + v_obj63)
        conveyorState.reset();
    }

    @Override
    public PhysicsProvider getPhysicsProvider() {
        if (physicsProvider == null) {
            physicsProvider = new Sonic1PhysicsProvider();
        }
        return physicsProvider;
    }

    @Override
    public int getRemappedFeatureZone(int logicalZone, int act, int levelZoneIndex) {
        // S1 SBZ act 3 is loaded from LZ zone data; remap to SBZ for feature lookups
        if (logicalZone == Sonic1ZoneConstants.ZONE_SBZ
                && act == 2
                && levelZoneIndex == Sonic1Constants.ZONE_LZ) {
            return Sonic1Constants.ZONE_SBZ;
        }
        return -1;
    }

    @Override
    public int getRemappedFeatureAct(int logicalZone, int act, int levelZoneIndex) {
        if (logicalZone == Sonic1ZoneConstants.ZONE_SBZ
                && act == 2
                && levelZoneIndex == Sonic1Constants.ZONE_LZ) {
            return 2;
        }
        return -1;
    }

    @Override
    public EndingProvider getEndingProvider() {
        return new Sonic1EndingProvider();
    }

    @Override
    public LevelInitProfile getLevelInitProfile() {
        return levelInitProfile;
    }

    @Override
    public boolean hasTrailInvincibilityStars() {
        return true;
    }

    @Override
    public SuperStateController createSuperStateController(AbstractPlayableSprite player) {
        if (CrossGameFeatureProvider.isActive()) {
            return com.openggf.game.RuntimeManager.getEngineServices().crossGameFeatures().createSuperStateController(player);
        }
        return null; // Vanilla S1 has no Super Sonic
    }

    @Override
    public DonorCapabilities getDonorCapabilities() {
        return Sonic1DonorCapabilities.INSTANCE;
    }

    /** Lazily-constructed singleton holding S1 donation metadata. */
    private static final class Sonic1DonorCapabilities implements DonorCapabilities {

        static final Sonic1DonorCapabilities INSTANCE = new Sonic1DonorCapabilities();

        private static final java.util.Set<PlayerCharacter> CHARACTERS =
                java.util.Set.of(PlayerCharacter.SONIC_ALONE);

        private static final java.util.Map<CanonicalAnimation, CanonicalAnimation> FALLBACKS =
                buildFallbacks();

        private static java.util.Map<CanonicalAnimation, CanonicalAnimation> buildFallbacks() {
            return DonorCapabilities.buildFallbackMap(
                    Sonic1AnimationIds.values(), Sonic1AnimationIds::toCanonical,
                    java.util.Map.ofEntries(
                            // Non-native animations -> nearest native fallback
                            java.util.Map.entry(CanonicalAnimation.SPINDASH,        CanonicalAnimation.DUCK),
                            java.util.Map.entry(CanonicalAnimation.SKID,            CanonicalAnimation.STOP),
                            java.util.Map.entry(CanonicalAnimation.SLIDE,           CanonicalAnimation.ROLL),
                            java.util.Map.entry(CanonicalAnimation.BLINK,           CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.GET_UP,          CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.VICTORY,         CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.BLANK,           CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.GLIDE_DROP,      CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.GLIDE_LAND,      CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.GLIDE_SLIDE,     CanonicalAnimation.PUSH),
                            java.util.Map.entry(CanonicalAnimation.HANG2,           CanonicalAnimation.HANG),
                            java.util.Map.entry(CanonicalAnimation.BALANCE2,        CanonicalAnimation.BALANCE),
                            java.util.Map.entry(CanonicalAnimation.BALANCE3,        CanonicalAnimation.BALANCE),
                            java.util.Map.entry(CanonicalAnimation.BALANCE4,        CanonicalAnimation.BALANCE),
                            java.util.Map.entry(CanonicalAnimation.FLY,             CanonicalAnimation.SPRING),
                            java.util.Map.entry(CanonicalAnimation.SUPER_TRANSFORM, CanonicalAnimation.WAIT),
                            java.util.Map.entry(CanonicalAnimation.BUBBLE,          CanonicalAnimation.GET_AIR),
                            java.util.Map.entry(CanonicalAnimation.HURT2,           CanonicalAnimation.HURT),
                            java.util.Map.entry(CanonicalAnimation.HURT_FALL,       CanonicalAnimation.HURT)
                    ));
        }

        @Override
        public java.util.Set<PlayerCharacter> getPlayableCharacters() { return CHARACTERS; }

        @Override
        public boolean hasSpindash() { return false; }

        @Override
        public boolean hasSuperTransform() { return false; }

        @Override
        public boolean hasHyperTransform() { return false; }

        @Override
        public boolean hasInstaShield() { return false; }

        @Override
        public boolean hasElementalShields() { return false; }

        @Override
        public boolean hasSidekick() { return false; }

        @Override
        public java.util.Map<CanonicalAnimation, CanonicalAnimation> getAnimationFallbacks() {
            return FALLBACKS;
        }

        @Override
        public int resolveNativeId(CanonicalAnimation canonical) {
            return Sonic1AnimationIds.fromCanonical(canonical);
        }

        @Override
        public com.openggf.data.PlayerSpriteArtProvider getPlayerArtProvider(
                com.openggf.data.RomByteReader reader) {
            var art = new Sonic1PlayerArt(reader);
            return art::loadForCharacter;
        }
    }
}
