package uk.co.jamesj999.sonic.game.sonic3k;

import uk.co.jamesj999.sonic.audio.GameAudioProfile;
import uk.co.jamesj999.sonic.data.Game;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.data.RomByteReader;
import uk.co.jamesj999.sonic.game.BonusStageProvider;
import uk.co.jamesj999.sonic.game.DebugModeProvider;
import uk.co.jamesj999.sonic.game.DebugOverlayProvider;
import uk.co.jamesj999.sonic.game.GameModule;
import uk.co.jamesj999.sonic.game.LevelEventProvider;
import uk.co.jamesj999.sonic.game.LevelState;
import uk.co.jamesj999.sonic.game.ObjectArtProvider;
import uk.co.jamesj999.sonic.game.RespawnState;
import uk.co.jamesj999.sonic.game.RomOffsetProvider;
import uk.co.jamesj999.sonic.game.ScrollHandlerProvider;
import uk.co.jamesj999.sonic.game.SpecialStageProvider;
import uk.co.jamesj999.sonic.game.TitleCardProvider;
import uk.co.jamesj999.sonic.game.ZoneArtProvider;
import uk.co.jamesj999.sonic.game.ZoneFeatureProvider;
import uk.co.jamesj999.sonic.game.ZoneRegistry;
import uk.co.jamesj999.sonic.game.sonic2.CheckpointState;
import uk.co.jamesj999.sonic.game.sonic2.LevelGamestate;
import uk.co.jamesj999.sonic.game.sonic3k.audio.Sonic3kAudioProfile;
import uk.co.jamesj999.sonic.level.objects.ObjectRegistry;
import uk.co.jamesj999.sonic.level.objects.PlaneSwitcherConfig;
import uk.co.jamesj999.sonic.level.objects.TouchResponseTable;

/**
 * Minimal GameModule stub for Sonic 3 &amp; Knuckles.
 *
 * <p>Currently only provides the audio profile for sound test functionality.
 * Other providers return null or minimal stubs until S3K gameplay is implemented.
 */
public class Sonic3kGameModule implements GameModule {
    private final GameAudioProfile audioProfile = new Sonic3kAudioProfile();

    @Override
    public String getIdentifier() {
        return "Sonic3k";
    }

    @Override
    public Game createGame(Rom rom) {
        // S3K gameplay not yet implemented
        return null;
    }

    @Override
    public ObjectRegistry createObjectRegistry() {
        return null;
    }

    @Override
    public GameAudioProfile getAudioProfile() {
        return audioProfile;
    }

    @Override
    public TouchResponseTable createTouchResponseTable(RomByteReader romReader) {
        return new TouchResponseTable(romReader, 0, 1);
    }

    @Override
    public int getPlaneSwitcherObjectId() {
        return 0;
    }

    @Override
    public PlaneSwitcherConfig getPlaneSwitcherConfig() {
        return new PlaneSwitcherConfig((byte) 0, (byte) 0, (byte) 0, (byte) 0);
    }

    @Override
    public LevelEventProvider getLevelEventProvider() {
        return null;
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
        return null;
    }

    @Override
    public ZoneRegistry getZoneRegistry() {
        return null;
    }

    @Override
    public SpecialStageProvider getSpecialStageProvider() {
        return null;
    }

    @Override
    public BonusStageProvider getBonusStageProvider() {
        return null;
    }

    @Override
    public ScrollHandlerProvider getScrollHandlerProvider() {
        return null;
    }

    @Override
    public ZoneFeatureProvider getZoneFeatureProvider() {
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
    public ObjectArtProvider getObjectArtProvider() {
        return null;
    }
}
