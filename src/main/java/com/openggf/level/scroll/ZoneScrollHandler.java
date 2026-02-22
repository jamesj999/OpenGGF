package com.openggf.level.scroll;

/**
 * Interface for zone-specific horizontal scroll routines.
 * Each implementation should match the corresponding SwScrl_* routine from the ROM.
 * 
 * Reference: s2.asm DeformBgLayer -> SwScrl_Index dispatch
 */
public interface ZoneScrollHandler {

    /**
     * Fill the horizontal scroll buffer for this zone.
     * 
     * This method should populate the 224-entry buffer with packed scroll values
     * where each entry contains (FG_scroll << 16) | (BG_scroll & 0xFFFF).
     * 
     * @param horizScrollBuf 224-entry buffer to fill with packed (FG,BG) scroll words
     * @param cameraX        Current foreground camera X position (pixels)
     * @param cameraY        Current foreground camera Y position (pixels)
     * @param frameCounter   Current frame number for animations
     * @param actId          Current act (0-based: 0 = Act 1, 1 = Act 2)
     */
    void update(int[] horizScrollBuf,
                int cameraX,
                int cameraY,
                int frameCounter,
                int actId);

    /**
     * Get the VScroll factor for Plane B (background).
     * This value will be written to VSRAM for vertical scrolling.
     * 
     * @return VScroll factor for background plane (16-bit signed)
     */
    short getVscrollFactorBG();

    /**
     * Get minimum BG scroll offset relative to FG for this frame.
     * Used by LevelManager to determine tile loading bounds.
     * 
     * @return Minimum (BG - FG) scroll offset
     */
    int getMinScrollOffset();

    /**
     * Get maximum BG scroll offset relative to FG for this frame.
     * Used by LevelManager to determine tile loading bounds.
     *
     * @return Maximum (BG - FG) scroll offset
     */
    int getMaxScrollOffset();

    /**
     * Get the main BG camera X position (v_bgscreenposx equivalent).
     * Used by LevelManager to determine which region of a wide BG map
     * to render into the 512px VDP nametable tilemap.
     *
     * <p>Zones with BG maps wider than 512px that do NOT tile at 512px
     * (e.g., SBZ with 15360px BG) must override this to return the
     * current BG camera X so the tilemap contains the correct tiles.
     *
     * @return BG camera X in pixels, or Integer.MIN_VALUE if the BG
     *         tiles naturally at 512px (default, no offset needed)
     */
    default int getBgCameraX() {
        return Integer.MIN_VALUE;
    }
}
