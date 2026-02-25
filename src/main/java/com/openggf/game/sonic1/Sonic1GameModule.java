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
import com.openggf.game.DebugModeProvider;
import com.openggf.game.DebugOverlayProvider;
import com.openggf.game.GameModule;
import com.openggf.game.LevelEventProvider;
import com.openggf.game.LevelSelectProvider;
import com.openggf.game.LevelState;
import com.openggf.game.ObjectArtProvider;
import com.openggf.game.PhysicsProvider;
import com.openggf.game.RespawnState;
import com.openggf.game.RomOffsetProvider;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.SpecialStageProvider;
import com.openggf.game.TitleCardProvider;
import com.openggf.game.TitleScreenProvider;
import com.openggf.game.ZoneArtProvider;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.ZoneRegistry;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.constants.Sonic1ObjectIds;
import com.openggf.game.sonic1.levelselect.Sonic1LevelSelectManager;
import com.openggf.game.sonic1.objects.Sonic1ObjectRegistry;
import com.openggf.game.sonic1.scroll.Sonic1ScrollHandlerProvider;
import com.openggf.game.CheckpointState;
import com.openggf.game.CrossGameFeatureProvider;
import com.openggf.game.LevelGamestate;
import com.openggf.game.OscillationManager;
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
        return Sonic1LevelEventManager.getInstance();
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
        return new Sonic1ZoneFeatureProvider();
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
        // Keep provider per-load to avoid stale same-zone state carrying across restarts.
        return new Sonic1ObjectArtProvider();
    }

    @Override
    public void applyPlaneSwitching(AbstractPlayableSprite player) {
        Sonic1LevelEventManager.getInstance()
                .getLoopManager().update(player);
    }

    @Override
    public void onLevelLoad() {
        // Reset oscillation values to Sonic 1 settings.
        // S1 differs from S2 in oscillators 8+ (amplitude, initial values).
        // Reference: docs/s1disasm/_inc/Oscillatory Routines.asm
        OscillationManager.resetForSonic1();
        // Reset switch state for new level (Sonic 1 f_switch array)
        Sonic1SwitchManager.getInstance().reset();
        // Reset v_obj6B singleton flag for SBZ3 StomperDoor
        Sonic1StomperDoorObjectInstance.resetSbz3Flag();
        // Reset conveyor belt state for new level (Sonic 1 f_conveyrev + v_obj63)
        Sonic1ConveyorState.getInstance().reset();
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
    public boolean hasTrailInvincibilityStars() {
        return true;
    }

    @Override
    public SuperStateController createSuperStateController(AbstractPlayableSprite player) {
        if (CrossGameFeatureProvider.isActive()) {
            return CrossGameFeatureProvider.getInstance().createSuperStateController(player);
        }
        return null; // Vanilla S1 has no Super Sonic
    }
}
