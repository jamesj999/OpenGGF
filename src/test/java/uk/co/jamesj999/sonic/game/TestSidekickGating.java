package uk.co.jamesj999.sonic.game;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1GameModule;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2GameModule;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kGameModule;

import static org.junit.Assert.*;

/**
 * Tests sidekick capability gating per game module.
 * S1: no sidekick support (no Tails art/logic).
 * S2/S3K: sidekick support (Tails available).
 */
public class TestSidekickGating {

    @Before
    public void setUp() {
        GameModuleRegistry.setCurrent(new Sonic2GameModule());
    }

    @After
    public void tearDown() {
        GameModuleRegistry.reset();
    }

    @Test
    public void testSonic1Module_SidekickNotSupported() {
        Sonic1GameModule module = new Sonic1GameModule();
        assertFalse("S1 should not support sidekick", module.supportsSidekick());
    }

    @Test
    public void testSonic2Module_SidekickSupported() {
        Sonic2GameModule module = new Sonic2GameModule();
        assertTrue("S2 should support sidekick", module.supportsSidekick());
    }

    @Test
    public void testSonic3kModule_SidekickSupported() {
        Sonic3kGameModule module = new Sonic3kGameModule();
        assertTrue("S3K should support sidekick", module.supportsSidekick());
    }

    @Test
    public void testDefaultMethod_ReturnsFalse() {
        // Verify the default interface method returns false
        GameModule anonymousModule = new GameModule() {
            public String getIdentifier() { return "test"; }
            public uk.co.jamesj999.sonic.data.Game createGame(uk.co.jamesj999.sonic.data.Rom rom) { return null; }
            public uk.co.jamesj999.sonic.level.objects.ObjectRegistry createObjectRegistry() { return null; }
            public uk.co.jamesj999.sonic.audio.GameAudioProfile getAudioProfile() { return null; }
            public uk.co.jamesj999.sonic.level.objects.TouchResponseTable createTouchResponseTable(
                    uk.co.jamesj999.sonic.data.RomByteReader r) { return null; }
            public int getPlaneSwitcherObjectId() { return 0; }
            public uk.co.jamesj999.sonic.level.objects.PlaneSwitcherConfig getPlaneSwitcherConfig() { return null; }
            public LevelEventProvider getLevelEventProvider() { return null; }
            public RespawnState createRespawnState() { return null; }
            public LevelState createLevelState() { return null; }
            public ZoneRegistry getZoneRegistry() { return null; }
            public ScrollHandlerProvider getScrollHandlerProvider() { return null; }
            public ZoneFeatureProvider getZoneFeatureProvider() { return null; }
            public RomOffsetProvider getRomOffsetProvider() { return null; }
            public DebugModeProvider getDebugModeProvider() { return null; }
            public DebugOverlayProvider getDebugOverlayProvider() { return null; }
            public ZoneArtProvider getZoneArtProvider() { return null; }
            public ObjectArtProvider getObjectArtProvider() { return null; }
        };
        assertFalse("Default supportsSidekick() should be false", anonymousModule.supportsSidekick());
    }
}
