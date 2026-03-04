package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Scroll handler provider for Sonic 3 &amp; Knuckles.
 * Provides zone-specific scroll handlers for parallax effects.
 */
public class Sonic3kScrollHandlerProvider implements ScrollHandlerProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic3kScrollHandlerProvider.class.getName());

    private boolean loaded = false;

    private SwScrlAiz aizHandler;
    private SwScrlMgz mgzHandler;
    private SwScrlS3kDefault defaultHandler;

    @Override
    public void load(Rom rom) throws IOException {
        if (loaded) {
            return;
        }
        aizHandler = new SwScrlAiz();
        mgzHandler = new SwScrlMgz();
        defaultHandler = new SwScrlS3kDefault();
        loaded = true;
        LOGGER.info("Sonic 3K scroll handlers loaded.");
    }

    @Override
    public ZoneScrollHandler getHandler(int zoneIndex) {
        if (!loaded) {
            return null;
        }

        return switch (zoneIndex) {
            case Sonic3kZoneConstants.ZONE_AIZ -> aizHandler;
            case Sonic3kZoneConstants.ZONE_MGZ -> mgzHandler;
            default -> defaultHandler;
        };
    }

    @Override
    public ZoneConstants getZoneConstants() {
        return Sonic3kZoneConstants.INSTANCE;
    }
}
