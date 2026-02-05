package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.ScrollHandlerProvider;
import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import java.io.IOException;

/**
 * Scroll handler provider for Sonic the Hedgehog 1.
 * Minimal implementation - returns null for all zones (uses default scrolling).
 */
public class Sonic1ScrollHandlerProvider implements ScrollHandlerProvider {

    @Override
    public void load(Rom rom) throws IOException {
        // No scroll data to load yet
    }

    @Override
    public ZoneScrollHandler getHandler(int zoneIndex) {
        // All zones use default scrolling for now
        return null;
    }

    @Override
    public ZoneConstants getZoneConstants() {
        return Sonic1ZoneConstants.INSTANCE;
    }
}
