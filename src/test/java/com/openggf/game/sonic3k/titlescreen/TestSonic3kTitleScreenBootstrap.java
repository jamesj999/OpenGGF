package com.openggf.game.sonic3k.titlescreen;

import com.openggf.data.RomManager;
import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestSonic3kTitleScreenBootstrap {

    @BeforeEach
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void titleScreenManagerConstructsWithConfiguredEngineServices() {
        Sonic3kTitleScreenManager manager = new Sonic3kTitleScreenManager();
        assertNotNull(manager);
        manager.reset();
    }

    @Test
    public void titleScreenDataLoaderReturnsFalseWithoutRom() {
        RomManager.getInstance().setRom(null);
        Sonic3kTitleScreenDataLoader loader = new Sonic3kTitleScreenDataLoader();
        assertFalse(loader.loadData());
    }
}


