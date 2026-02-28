package com.openggf.game;

import com.openggf.game.sonic1.Sonic1GameModule;
import com.openggf.game.sonic1.Sonic1LevelInitProfile;
import com.openggf.game.sonic2.Sonic2GameModule;
import com.openggf.game.sonic2.Sonic2LevelInitProfile;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.game.sonic3k.Sonic3kLevelInitProfile;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestGameModuleProfiles {

    @Test
    public void sonic2ModuleReturnsSonic2Profile() {
        GameModule module = new Sonic2GameModule();
        assertTrue(module.getLevelInitProfile() instanceof Sonic2LevelInitProfile);
    }

    @Test
    public void sonic1ModuleReturnsSonic1Profile() {
        GameModule module = new Sonic1GameModule();
        assertTrue(module.getLevelInitProfile() instanceof Sonic1LevelInitProfile);
    }

    @Test
    public void sonic3kModuleReturnsSonic3kProfile() {
        GameModule module = new Sonic3kGameModule();
        assertTrue(module.getLevelInitProfile() instanceof Sonic3kLevelInitProfile);
    }

    @Test
    public void profilesAreNotNull() {
        assertNotNull(new Sonic1GameModule().getLevelInitProfile());
        assertNotNull(new Sonic2GameModule().getLevelInitProfile());
        assertNotNull(new Sonic3kGameModule().getLevelInitProfile());
    }

    @Test
    public void defaultReturnsEmptyProfile() {
        // Anonymous GameModule gets the safe empty default
        GameModule anon = new GameModule() {
            @Override
            public String getIdentifier() { return "test"; }
            @Override
            public com.openggf.data.Game createGame(com.openggf.data.Rom rom) { return null; }
            @Override
            public com.openggf.level.objects.ObjectRegistry createObjectRegistry() { return null; }
            @Override
            public com.openggf.audio.GameAudioProfile getAudioProfile() { return null; }
            @Override
            public com.openggf.level.objects.TouchResponseTable createTouchResponseTable(
                    com.openggf.data.RomByteReader r) { return null; }
            @Override
            public int getPlaneSwitcherObjectId() { return 0; }
            @Override
            public com.openggf.level.objects.PlaneSwitcherConfig getPlaneSwitcherConfig() { return null; }
            @Override
            public LevelEventProvider getLevelEventProvider() { return null; }
            @Override
            public RespawnState createRespawnState() { return null; }
            @Override
            public LevelState createLevelState() { return null; }
            @Override
            public ZoneRegistry getZoneRegistry() { return null; }
            @Override
            public ScrollHandlerProvider getScrollHandlerProvider() { return null; }
            @Override
            public ZoneFeatureProvider getZoneFeatureProvider() { return null; }
            @Override
            public RomOffsetProvider getRomOffsetProvider() { return null; }
            @Override
            public DebugModeProvider getDebugModeProvider() { return null; }
            @Override
            public DebugOverlayProvider getDebugOverlayProvider() { return null; }
            @Override
            public ZoneArtProvider getZoneArtProvider() { return null; }
            @Override
            public ObjectArtProvider getObjectArtProvider() { return null; }
        };
        LevelInitProfile profile = anon.getLevelInitProfile();
        assertNotNull(profile);
        assertTrue(profile.levelLoadSteps().isEmpty());
        assertTrue(profile.levelTeardownSteps().isEmpty());
        assertTrue(profile.perTestResetSteps().isEmpty());
        assertTrue(profile.postTeardownFixups().isEmpty());
    }
}
