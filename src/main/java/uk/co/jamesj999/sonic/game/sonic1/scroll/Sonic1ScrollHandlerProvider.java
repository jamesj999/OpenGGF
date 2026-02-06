package uk.co.jamesj999.sonic.game.sonic1.scroll;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.ScrollHandlerProvider;
import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Scroll handler provider for Sonic the Hedgehog 1.
 * Provides zone-specific scroll handlers matching the original Deform_* routines
 * from the s1disasm DeformLayers.asm.
 *
 * <p>Sonic 1 scroll handlers do not require shared ROM data (no ParallaxTables).
 * Each handler manages its own BG camera state using frame deltas.
 */
public class Sonic1ScrollHandlerProvider implements ScrollHandlerProvider {
    private static final Logger LOGGER = Logger.getLogger(Sonic1ScrollHandlerProvider.class.getName());

    private SwScrlGhz ghzHandler;
    private SwScrlLz lzHandler;
    private SwScrlMz mzHandler;
    private SwScrlSlz slzHandler;
    private SwScrlSyz syzHandler;
    private SwScrlSbz sbzHandler;
    private SwScrlFz fzHandler;

    private boolean loaded = false;

    @Override
    public void load(Rom rom) throws IOException {
        if (loaded) {
            return;
        }

        // Sonic 1 scroll handlers don't need ROM data - they compute
        // everything from camera positions and frame deltas.
        ghzHandler = new SwScrlGhz();
        lzHandler = new SwScrlLz();
        mzHandler = new SwScrlMz();
        slzHandler = new SwScrlSlz();
        syzHandler = new SwScrlSyz();
        sbzHandler = new SwScrlSbz();
        fzHandler = new SwScrlFz();

        loaded = true;
        LOGGER.info("Sonic 1 scroll handlers loaded.");
    }

    @Override
    public ZoneScrollHandler getHandler(int zoneIndex) {
        if (!loaded) {
            return null;
        }

        return switch (zoneIndex) {
            case Sonic1ZoneConstants.ZONE_GHZ -> ghzHandler;
            case Sonic1ZoneConstants.ZONE_LZ -> lzHandler;
            case Sonic1ZoneConstants.ZONE_MZ -> mzHandler;
            case Sonic1ZoneConstants.ZONE_SLZ -> slzHandler;
            case Sonic1ZoneConstants.ZONE_SYZ -> syzHandler;
            case Sonic1ZoneConstants.ZONE_SBZ -> sbzHandler;
            case Sonic1ZoneConstants.ZONE_FZ -> fzHandler;
            default -> null;
        };
    }

    @Override
    public ZoneConstants getZoneConstants() {
        return Sonic1ZoneConstants.INSTANCE;
    }
}
