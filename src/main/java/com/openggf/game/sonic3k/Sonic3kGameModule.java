package com.openggf.game.sonic3k;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.CanonicalAnimation;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.DonorCapabilities;
import com.openggf.game.PlayerCharacter;
import com.openggf.game.sonic3k.constants.Sonic3kAnimationIds;
import com.openggf.game.DebugOverlayProvider;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.WaterDataProvider;
import com.openggf.game.LevelState;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.RespawnState;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.CheckpointState;
import com.openggf.game.LevelGamestate;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.sonic3k.events.S3kSeamlessMutationExecutor;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.scroll.Sonic3kScrollHandlerProvider;
import com.openggf.game.sonic3k.specialstage.Sonic3kSpecialStageProvider;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.game.GameId;
import com.openggf.game.OscillationManager;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.PlaneSwitcherConfig;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.sprites.art.SpriteArtSet;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GameModule for Sonic 3 &amp; Knuckles.
 *
 * <p>Provides audio, zone registry, scroll handlers, and level loading.
 * Phase 1: terrain/collision only. Objects, rings, and zone features are deferred.
 */
public class Sonic3kGameModule implements GameModule {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kGameModule.class.getName());
    private final GameAudioProfile audioProfile = new Sonic3kAudioProfile();
    private Sonic3kScrollHandlerProvider scrollHandlerProvider;
    private Sonic3kLevelEventManager levelEventManager;
    private PhysicsProvider physicsProvider;
    private Sonic3kObjectArtProvider objectArtProvider;
    private Sonic3kSpecialStageProvider specialStageProvider;

    @Override
    public String getIdentifier() {
        return "Sonic3k";
    }

    @Override
    public GameId getGameId() {
        return GameId.S3K;
    }

    @Override
    public Game createGame(Rom rom) {
        try {
            return new Sonic3k(rom);
        } catch (java.io.IOException e) {
            LOGGER.severe("Failed to create S3K game: " + e.getMessage());
            return null;
        }
    }

    @Override
    public ObjectRegistry createObjectRegistry() {
        return new Sonic3kObjectRegistry();
    }

    @Override
    public GameAudioProfile getAudioProfile() {
        return audioProfile;
    }

    @Override
    public TouchResponseTable createTouchResponseTable(RomByteReader romReader) {
        return new TouchResponseTable(romReader,
                Sonic3kConstants.TOUCH_SIZES_ADDR,
                Sonic3kConstants.TOUCH_SIZES_COUNT);
    }

    @Override
    public int getPlaneSwitcherObjectId() {
        return Sonic3kObjectIds.PATH_SWAP;
    }

    @Override
    public PlaneSwitcherConfig getPlaneSwitcherConfig() {
        return new PlaneSwitcherConfig((byte) 0x0C, (byte) 0x0D, (byte) 0x0E, (byte) 0x0F);
    }

    @Override
    public LevelEventProvider getLevelEventProvider() {
        if (levelEventManager == null) {
            levelEventManager = Sonic3kLevelEventManager.getInstance();
        }
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
    public ZoneRegistry getZoneRegistry() {
        return Sonic3kZoneRegistry.getInstance();
    }

    @Override
    public ScrollHandlerProvider getScrollHandlerProvider() {
        if (scrollHandlerProvider == null) {
            scrollHandlerProvider = new Sonic3kScrollHandlerProvider();
        }
        return scrollHandlerProvider;
    }

    @Override
    public TitleCardProvider getTitleCardProvider() {
        return Sonic3kTitleCardManager.getInstance();
    }

    @Override
    public ZoneFeatureProvider getZoneFeatureProvider() {
        return new Sonic3kZoneFeatureProvider();
    }

    @Override
    public DebugOverlayProvider getDebugOverlayProvider() {
        return null;
    }

    @Override
    public ObjectArtProvider getObjectArtProvider() {
        if (objectArtProvider == null) {
            objectArtProvider = new Sonic3kObjectArtProvider();
        }
        return objectArtProvider;
    }

    @Override
    public PhysicsProvider getPhysicsProvider() {
        if (physicsProvider == null) {
            physicsProvider = new Sonic3kPhysicsProvider();
        }
        return physicsProvider;
    }

    @Override
    public WaterDataProvider getWaterDataProvider() {
        return new Sonic3kWaterDataProvider();
    }

    private final LevelInitProfile levelInitProfile = new Sonic3kLevelInitProfile();

    @Override
    public LevelInitProfile getLevelInitProfile() {
        return levelInitProfile;
    }

    @Override
    public boolean hasSeparateTailsTailArt() {
        return true;
    }

    @Override
    public SpriteArtSet loadTailsTailArt() {
        try {
            Rom rom = GameServices.rom().getRom();
            Sonic3kPlayerArt s3kArt = new Sonic3kPlayerArt(RomByteReader.fromRom(rom));
            return s3kArt.loadTailsTail();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load S3K tails tail art", e);
            return SpriteArtSet.EMPTY;
        }
    }

    @Override
    public SuperStateController createSuperStateController(
            AbstractPlayableSprite player) {
        if (CrossGameFeatureProvider.isActive()) {
            return CrossGameFeatureProvider.getInstance().createSuperStateController(player);
        }
        return new Sonic3kSuperStateController(player);
    }

    @Override
    public void applySeamlessMutation(LevelManager levelManager, String mutationKey) {
        S3kSeamlessMutationExecutor.apply(levelManager, mutationKey);
    }

    @Override
    public void onLevelLoad() {
        // Reset oscillation values used by moving platforms, etc.
        OscillationManager.reset();
    }

    @Override
    public SpecialStageProvider getSpecialStageProvider() {
        if (specialStageProvider == null) {
            specialStageProvider = new Sonic3kSpecialStageProvider();
        }
        return specialStageProvider;
    }

    @Override
    public boolean supportsSidekick() {
        return true;
    }

    @Override
    public DonorCapabilities getDonorCapabilities() {
        return Sonic3kDonorCapabilities.INSTANCE;
    }

    /** Lazily-constructed singleton holding S3K donation metadata. */
    private static final class Sonic3kDonorCapabilities implements DonorCapabilities {

        static final Sonic3kDonorCapabilities INSTANCE = new Sonic3kDonorCapabilities();

        private static final java.util.Set<PlayerCharacter> CHARACTERS =
                java.util.Set.of(
                        PlayerCharacter.SONIC_ALONE,
                        PlayerCharacter.SONIC_AND_TAILS,
                        PlayerCharacter.TAILS_ALONE,
                        PlayerCharacter.KNUCKLES);

        private static final java.util.Map<CanonicalAnimation, CanonicalAnimation> FALLBACKS =
                buildFallbacks();

        private static java.util.Map<CanonicalAnimation, CanonicalAnimation> buildFallbacks() {
            return DonorCapabilities.buildFallbackMap(
                    Sonic3kAnimationIds.values(), Sonic3kAnimationIds::toCanonical,
                    java.util.Map.ofEntries(
                            // S1-specific animations -> nearest S3K native fallback
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
                            java.util.Map.entry(CanonicalAnimation.GET_AIR,     CanonicalAnimation.BLANK),
                            java.util.Map.entry(CanonicalAnimation.BURNT,       CanonicalAnimation.HURT),
                            java.util.Map.entry(CanonicalAnimation.SHRINK,      CanonicalAnimation.DEATH),
                            java.util.Map.entry(CanonicalAnimation.WATER_SLIDE, CanonicalAnimation.HURT_FALL),
                            java.util.Map.entry(CanonicalAnimation.NULL_ANIM,   CanonicalAnimation.BLANK),
                            // S2-specific animations not in S3K -> nearest S3K native fallback
                            java.util.Map.entry(CanonicalAnimation.SLIDE,       CanonicalAnimation.HURT_FALL),
                            java.util.Map.entry(CanonicalAnimation.HURT2,       CanonicalAnimation.HURT)
                    ));
        }

        @Override
        public java.util.Set<PlayerCharacter> getPlayableCharacters() { return CHARACTERS; }

        @Override
        public boolean hasSpindash() { return true; }

        @Override
        public boolean hasSuperTransform() { return true; }

        @Override
        public boolean hasHyperTransform() { return true; }

        @Override
        public boolean hasInstaShield() { return true; }

        @Override
        public boolean hasElementalShields() { return true; }

        @Override
        public boolean hasSidekick() { return true; }

        @Override
        public java.util.Map<CanonicalAnimation, CanonicalAnimation> getAnimationFallbacks() {
            return FALLBACKS;
        }

        @Override
        public int resolveNativeId(CanonicalAnimation canonical) {
            return Sonic3kAnimationIds.fromCanonical(canonical);
        }

        @Override
        public com.openggf.data.PlayerSpriteArtProvider getPlayerArtProvider(
                com.openggf.data.RomByteReader reader) {
            var art = new Sonic3kPlayerArt(reader);
            return art::loadForCharacter;
        }
    }
}
