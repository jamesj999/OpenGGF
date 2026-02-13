package uk.co.jamesj999.sonic.game.sonic1;

import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.DebugModeProvider;
import uk.co.jamesj999.sonic.game.DebugOverlayProvider;
import uk.co.jamesj999.sonic.game.GameModule;
import uk.co.jamesj999.sonic.game.LevelEventProvider;
import uk.co.jamesj999.sonic.game.LevelSelectProvider;
import uk.co.jamesj999.sonic.game.LevelState;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.PhysicsProvider;
import uk.co.jamesj999.sonic.game.RespawnState;
import uk.co.jamesj999.sonic.game.RomOffsetProvider;
import uk.co.jamesj999.sonic.game.ScrollHandlerProvider;
import uk.co.jamesj999.sonic.game.SpecialStageProvider;
import uk.co.jamesj999.sonic.game.TitleCardProvider;
import uk.co.jamesj999.sonic.game.TitleScreenProvider;
import uk.co.jamesj999.sonic.game.ZoneArtProvider;
import uk.co.jamesj999.sonic.game.ZoneFeatureProvider;
import uk.co.jamesj999.sonic.game.ZoneRegistry;
import uk.co.jamesj999.sonic.game.sonic1.audio.Sonic1AudioProfile;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1Constants;
import uk.co.jamesj999.sonic.game.sonic1.constants.Sonic1ObjectIds;
import uk.co.jamesj999.sonic.game.sonic1.levelselect.Sonic1LevelSelectManager;
import uk.co.jamesj999.sonic.game.sonic1.titlescreen.Sonic1TitleScreenManager;
import uk.co.jamesj999.sonic.game.sonic1.objects.Sonic1ObjectRegistry;
import uk.co.jamesj999.sonic.game.sonic1.scroll.Sonic1ScrollHandlerProvider;
import uk.co.jamesj999.sonic.game.sonic1.specialstage.Sonic1SpecialStageProvider;
import uk.co.jamesj999.sonic.game.sonic2.CheckpointState;
import uk.co.jamesj999.sonic.game.sonic2.LevelGamestate;
import uk.co.jamesj999.sonic.game.sonic1.titlecard.Sonic1TitleCardManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRegistry;
import uk.co.jamesj999.sonic.level.objects.PlaneSwitcherConfig;
import uk.co.jamesj999.sonic.level.objects.TouchResponseTable;

/**
 * GameModule implementation for Sonic the Hedgehog 1 (Mega Drive/Genesis).
 */
public class Sonic1GameModule implements GameModule {
    private final GameAudioProfile audioProfile = new Sonic1AudioProfile();
    private final SpecialStageProvider specialStageProvider = new Sonic1SpecialStageProvider();
    private PhysicsProvider physicsProvider;

    @Override
    public String getIdentifier() {
        return "Sonic1";
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
        return uk.co.jamesj999.sonic.game.sonic1.events.Sonic1LevelEventManager.getInstance();
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
        return Sonic1ZoneRegistry.getInstance();
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
        // No zone-specific features implemented yet (water in LZ, etc.)
        return null;
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
    public LevelSelectProvider getLevelSelectProvider() {
        return Sonic1LevelSelectManager.getInstance();
    }

    @Override
    public ObjectArtProvider getObjectArtProvider() {
        return new Sonic1ObjectArtProvider();
    }

    @Override
    public void applyPlaneSwitching(uk.co.jamesj999.sonic.sprites.playable.AbstractPlayableSprite player) {
        uk.co.jamesj999.sonic.game.sonic1.events.Sonic1LevelEventManager.getInstance()
                .getLoopManager().update(player);
    }

    @Override
    public PhysicsProvider getPhysicsProvider() {
        if (physicsProvider == null) {
            physicsProvider = new Sonic1PhysicsProvider();
        }
        return physicsProvider;
    }
}
