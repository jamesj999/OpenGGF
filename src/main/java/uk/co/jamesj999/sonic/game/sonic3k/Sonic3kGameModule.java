package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.DebugModeProvider;
import uk.co.jamesj999.sonic.game.DebugOverlayProvider;
import uk.co.jamesj999.sonic.game.GameModule;
import uk.co.jamesj999.sonic.game.LevelEventProvider;
import uk.co.jamesj999.sonic.game.PhysicsProvider;
import uk.co.jamesj999.sonic.game.LevelState;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.RespawnState;
import uk.co.jamesj999.sonic.game.RomOffsetProvider;
import uk.co.jamesj999.sonic.game.ScrollHandlerProvider;
import uk.co.jamesj999.sonic.game.ZoneArtProvider;
import uk.co.jamesj999.sonic.game.ZoneFeatureProvider;
import uk.co.jamesj999.sonic.game.ZoneRegistry;
import uk.co.jamesj999.sonic.game.sonic2.CheckpointState;
import uk.co.jamesj999.sonic.game.sonic2.LevelGamestate;
import uk.co.jamesj999.sonic.game.TitleCardProvider;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kAudioProfile;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kObjectIds;
import uk.co.jamesj999.sonic.game.sonic3k.objects.Sonic3kObjectRegistry;
import uk.co.jamesj999.sonic.game.sonic3k.scroll.Sonic3kScrollHandlerProvider;
import uk.co.jamesj999.sonic.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRegistry;
import uk.co.jamesj999.sonic.level.objects.PlaneSwitcherConfig;
import uk.co.jamesj999.sonic.level.objects.TouchResponseTable;

/**
 * GameModule for Sonic 3 &amp; Knuckles.
 *
 * <p>Provides audio, zone registry, scroll handlers, and level loading.
 * Phase 1: terrain/collision only. Objects, rings, and zone features are deferred.
 */
public class Sonic3kGameModule implements GameModule {
    private final GameAudioProfile audioProfile = new Sonic3kAudioProfile();
    private Sonic3kScrollHandlerProvider scrollHandlerProvider;
    private Sonic3kLevelEventManager levelEventManager;
    private PhysicsProvider physicsProvider;

    @Override
    public String getIdentifier() {
        return "Sonic3k";
    }

    @Override
    public Game createGame(Rom rom) {
        try {
            return new Sonic3k(rom);
        } catch (java.io.IOException e) {
            java.util.logging.Logger.getLogger(Sonic3kGameModule.class.getName())
                    .severe("Failed to create S3K game: " + e.getMessage());
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
    public RomOffsetProvider getRomOffsetProvider() {
        return null;
    }

    @Override
    public DebugModeProvider getDebugModeProvider() {
        return null;
    }

    @Override
    public DebugOverlayProvider getDebugOverlayProvider() {
        return null;
    }

    @Override
    public ZoneArtProvider getZoneArtProvider() {
        return null;
    }

    @Override
    public ObjectArtProvider getObjectArtProvider() {
        return new Sonic3kObjectArtProvider();
    }

    @Override
    public PhysicsProvider getPhysicsProvider() {
        if (physicsProvider == null) {
            physicsProvider = new Sonic3kPhysicsProvider();
        }
        return physicsProvider;
    }

    @Override
    public uk.co.jamesj999.sonic.sprites.playable.SuperStateController createSuperStateController(
            uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite player) {
        return new Sonic3kSuperStateController(player);
    }
}
