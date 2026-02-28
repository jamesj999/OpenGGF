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
}
