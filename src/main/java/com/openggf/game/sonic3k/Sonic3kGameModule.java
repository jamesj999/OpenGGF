package com.openggf.game.sonic3k;

import com.openggf.audio.GameAudioProfile;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.audio.Sonic3kAudioProfile;
import com.openggf.game.DebugModeProvider;
import com.openggf.game.DebugOverlayProvider;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelInitProfile;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.LevelState;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.RespawnState;
import com.openggf.game.RomOffsetProvider;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.ZoneArtProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.CheckpointState;
import com.openggf.game.LevelGamestate;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.game.sonic3k.constants.Sonic3kObjectIds;
import com.openggf.game.sonic3k.objects.Sonic3kObjectRegistry;
import com.openggf.game.sonic3k.scroll.Sonic3kScrollHandlerProvider;
import com.openggf.game.sonic3k.titlecard.Sonic3kTitleCardManager;
import com.openggf.level.objects.ObjectRegistry;
import com.openggf.level.objects.PlaneSwitcherConfig;
import com.openggf.level.objects.TouchResponseTable;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.sprites.playable.SuperStateController;

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
    private Sonic3kObjectArtProvider objectArtProvider;

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
    public SuperStateController createSuperStateController(
            AbstractPlayableSprite player) {
        return new Sonic3kSuperStateController(player);
    }

    @Override
    public boolean supportsSidekick() {
        return true;
    }
}
