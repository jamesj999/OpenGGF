package uk.co.jamesj999.sonic.game;

import org.junit.Test;
import uk.co.jamesj999.sonic.game.sonic1.Sonic1GameModule;
import uk.co.jamesj999.sonic.game.sonic2.Sonic2GameModule;
import uk.co.jamesj999.sonic.level.objects.ObjectRegistry;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class TestGameModuleProviderLifetimes {

    @Test
    public void sonic1ObjectArtProvider_NotCachedAcrossCalls() {
        Sonic1GameModule module = new Sonic1GameModule();

        ObjectArtProvider first = module.getObjectArtProvider();
        ObjectArtProvider second = module.getObjectArtProvider();

        assertNotSame("Sonic 1 object art provider should be recreated per call", first, second);
    }

    @Test
    public void sonic2ObjectRegistry_CachedAcrossCalls() {
        Sonic2GameModule module = new Sonic2GameModule();

        ObjectRegistry first = module.createObjectRegistry();
        ObjectRegistry second = module.createObjectRegistry();

        assertSame("Sonic 2 object registry should be reused within a module instance", first, second);
    }
}
