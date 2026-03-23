package com.openggf.game;

import com.openggf.data.Rom;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.io.IOException;

/**
 * Interface for providing zone-specific scroll handlers.
 * Each game module can provide its own scroll handlers for parallax effects.
 *
 * <p>Scroll handlers control horizontal and vertical scrolling for background
 * planes, implementing per-line parallax effects specific to each zone.
 */
public interface ScrollHandlerProvider {
    /**
     * Loads any required data for scroll handlers from the ROM.
     * Called once when the ROM is first loaded.
     *
     * @param rom the ROM to load data from
     * @throws IOException if loading fails
     */
    void load(Rom rom) throws IOException;

    /**
     * Gets a scroll handler for the specified zone.
     *
     * @param zoneIndex the zone index
     * @return the scroll handler, or null if the zone uses default scrolling
     */
    ZoneScrollHandler getHandler(int zoneIndex);

    /**
     * Returns the zone constants for this game.
     * Used by ParallaxManager to map zone indices to scroll behavior.
     *
     * @return the zone constants
     */
    ZoneConstants getZoneConstants();

    /**
     * Initializes zone-specific scroll state on level load.
     * Called when entering a zone to set up background camera positions,
     * reset animation counters, and prepare zone-specific scroll data.
     * Implementations should dispatch to the appropriate handler's init method
     * and initialize any shared state (e.g., background camera).
     *
     * @param zoneId  zone index
     * @param actId   current act (0-based: 0 = Act 1, 1 = Act 2)
     * @param cameraX current foreground camera X position (pixels)
     * @param cameraY current foreground camera Y position (pixels)
     */
    default void initForZone(int zoneId, int actId, int cameraX, int cameraY) {
        // no-op by default
    }

    /**
     * Updates zone-specific dynamic art each frame.
     * Called after the main scroll update to handle art streaming (e.g., HTZ
     * mountain and cloud tile updates based on camera position).
     *
     * @param level   the level instance for pattern updates (may be null)
     * @param cameraX current camera X position
     */
    default void updateDynamicArt(com.openggf.level.Level level, int cameraX) {
        // no-op by default
    }

    /**
     * Returns the current Tornado X velocity for SCZ object movement.
     * ROM: Tornado_Velocity_X is added to object x_pos each frame.
     *
     * @return Tornado X velocity in pixels per frame, or 0 if not applicable
     */
    default int getTornadoVelocityX() {
        return 0;
    }

    /**
     * Returns the current Tornado Y velocity for SCZ object movement.
     * ROM: Tornado_Velocity_Y is added to object y_pos each frame.
     *
     * @return Tornado Y velocity in pixels per frame, or 0 if not applicable
     */
    default int getTornadoVelocityY() {
        return 0;
    }

    /**
     * Returns the current background camera X offset for WFZ scripted objects.
     * ROM equivalent: Camera_BG_X_offset.
     *
     * @return BG camera X offset in pixels, or 0 if not applicable
     */
    default int getCameraBgXOffset() {
        return 0;
    }

    /**
     * Resets zone tracking state without destroying handler instances.
     * Used when re-entering a zone that needs fresh initialization
     * (e.g., after death or act transition).
     */
    default void resetZoneState() {
        // no-op by default
    }

    /**
     * Updates parallax for the ending/credits cutscene.
     * Called with camera at (0,0) and a custom BG vscroll value.
     * Only applicable for games with ending sequences that use scroll handlers.
     *
     * @param horizScrollBuf 224-entry buffer to fill with packed scroll words
     * @param zoneId     zone ID for the ending zone
     * @param actId      act ID
     * @param frameCounter current frame counter
     * @param bgVscroll  ending BG vertical scroll value
     * @return true if the ending was handled, false if caller should use default
     */
    default boolean updateForEnding(int[] horizScrollBuf, int zoneId, int actId,
                                     int frameCounter, short bgVscroll) {
        return false;
    }

    /**
     * Interface containing zone index constants.
     */
    interface ZoneConstants {
        int getZoneCount();
        String getZoneName(int index);
    }
}
