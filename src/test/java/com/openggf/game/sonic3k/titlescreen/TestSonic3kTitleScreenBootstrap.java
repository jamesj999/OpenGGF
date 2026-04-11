package com.openggf.game.sonic3k.titlescreen;

import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class TestSonic3kTitleScreenBootstrap {

    @Before
    public void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        RuntimeManager.createGameplay();
    }

    @After
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
        Sonic3kTitleScreenDataLoader loader = new Sonic3kTitleScreenDataLoader();
        assertFalse(loader.loadData());
    }
}
