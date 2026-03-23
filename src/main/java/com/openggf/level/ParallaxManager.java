package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Manages parallax scrolling effects.
 * Outputs scroll values compatible with the LevelManager rendering system.
 *
 * <p>All game-specific scroll logic is provided by the current
 * {@link ScrollHandlerProvider} obtained from the active {@link GameModule}.
 * ParallaxManager itself contains no game-specific imports or constants.
 */
public class ParallaxManager {
    private static final Logger LOGGER = Logger.getLogger(ParallaxManager.class.getName());

    public static final int VISIBLE_LINES = 224;

    // Packed as (planeA << 16) | (planeB & 0xFFFF)
    private final int[] hScroll = new int[VISIBLE_LINES];
    // Optional per-line BG VScroll deltas (added on top of vscrollFactorBG).
    private final short[] vScrollPerLineBG = new short[VISIBLE_LINES];
    // Optional per-column BG VScroll deltas (20 columns in H40 mode).
    private static final int BG_VSCROLL_COLUMN_COUNT = 20;
    private final short[] vScrollPerColumnBG = new short[BG_VSCROLL_COLUMN_COUNT];
    private boolean hasPerLineVScrollBG = false;
    private boolean hasPerColumnVScrollBG = false;

    private int minScroll = 0;
    private int maxScroll = 0;

    private short vscrollFactorFG;
    private short vscrollFactorBG;

    // Game-agnostic scroll handler provider (from current GameModule)
    private ScrollHandlerProvider scrollProvider;
    private boolean providerLoaded = false;

    private int currentZone = -1;
    private int currentAct = -1;

    // Screen shake offsets propagated from zone handlers
    private int currentShakeOffsetX = 0;
    private int currentShakeOffsetY = 0;

    // Cached BG camera X from the active scroll handler (Integer.MIN_VALUE = no offset)
    private int cachedBgCameraX = Integer.MIN_VALUE;
    private int cachedBgPeriodWidth = 512;

    private static ParallaxManager instance;

    public static synchronized ParallaxManager getInstance() {
        if (instance == null) {
            instance = new ParallaxManager();
        }
        return instance;
    }

    /**
     * Reset zone state to force reinitialization on next initZone call.
     * Useful for tests to ensure deterministic state.
     */
    public void resetZoneState() {
        currentZone = -1;
        currentAct = -1;
        if (scrollProvider != null) {
            scrollProvider.resetZoneState();
        }
    }

    /**
     * Resets all mutable state without destroying the singleton instance.
     * Clears scroll factors and provider so the next load starts fresh.
     */
    public void resetState() {
        resetZoneState();
        scrollProvider = null;
        providerLoaded = false;
        vscrollFactorFG = 0;
        vscrollFactorBG = 0;
        currentShakeOffsetX = 0;
        currentShakeOffsetY = 0;
        cachedBgCameraX = Integer.MIN_VALUE;
        cachedBgPeriodWidth = 512;
        minScroll = 0;
        maxScroll = 0;
        java.util.Arrays.fill(hScroll, 0);
        java.util.Arrays.fill(vScrollPerLineBG, (short) 0);
        java.util.Arrays.fill(vScrollPerColumnBG, (short) 0);
        hasPerLineVScrollBG = false;
        hasPerColumnVScrollBG = false;
    }

    public void load(Rom rom) {
        if (providerLoaded) {
            return;
        }

        // Get the game-specific scroll handler provider
        GameModule module = GameModuleRegistry.getCurrent();

        if (module != null) {
            scrollProvider = module.getScrollHandlerProvider();
            if (scrollProvider != null) {
                try {
                    scrollProvider.load(rom);
                    providerLoaded = true;
                } catch (IOException e) {
                    LOGGER.warning("Failed to load game scroll provider: " + e.getMessage());
                    scrollProvider = null;
                }
            }
        }
    }

    public void initZone(int zoneId, int actId, int cameraX, int cameraY) {
        if (zoneId != currentZone || actId != currentAct) {
            currentZone = zoneId;
            currentAct = actId;
            if (scrollProvider != null) {
                scrollProvider.initForZone(zoneId, actId, cameraX, cameraY);
            }
        }
    }

    public int[] getHScroll() {
        return hScroll;
    }

    public int getMinScroll() {
        return minScroll;
    }

    public int getMaxScroll() {
        return maxScroll;
    }

    /**
     * Get the raw hScroll buffer for shader-based rendering.
     * This returns the packed (FG << 16 | BG) format array.
     * The BackgroundRenderer extracts BG values during upload.
     */
    public int[] getHScrollForShader() {
        return hScroll;
    }

    public short getVscrollFactorFG() {
        return vscrollFactorFG;
    }

    public short getVscrollFactorBG() {
        return vscrollFactorBG;
    }

    /**
     * Optional per-line BG VScroll values for shader-based heat haze effects.
     *
     * @return 224-entry per-line VScroll array, or null when not active
     */
    public short[] getVScrollPerLineBGForShader() {
        return hasPerLineVScrollBG ? vScrollPerLineBG : null;
    }

    /**
     * Optional per-column BG VScroll values for shader-based column distortion effects.
     *
     * @return 20-entry per-column VScroll array, or null when not active
     */
    public short[] getVScrollPerColumnBGForShader() {
        return hasPerColumnVScrollBG ? vScrollPerColumnBG : null;
    }

    /**
     * Get the BG camera X position from the active scroll handler.
     * Used by LevelManager to determine which region of a wide BG map
     * to render into the 512px VDP nametable tilemap.
     *
     * @return BG camera X in pixels, or Integer.MIN_VALUE if no offset needed
     */
    public int getBgCameraX() {
        return cachedBgCameraX;
    }

    /**
     * Get the required BG tilemap period width from the active scroll handler.
     *
     * @return Period width in pixels (default 512)
     */
    public int getBgPeriodWidth() {
        return cachedBgPeriodWidth;
    }

    /**
     * Get the current horizontal shake offset for this frame.
     * This is propagated from the active zone scroll handler when screen shake is active.
     * Used by LevelManager to set camera shake offsets for FG tiles and sprites.
     *
     * @return Horizontal shake offset in pixels, or 0 if no shake
     */
    public int getShakeOffsetX() {
        return currentShakeOffsetX;
    }

    /**
     * Get the current vertical shake offset for this frame.
     * This is propagated from the active zone scroll handler when screen shake is active.
     * Used by LevelManager to set camera shake offsets for FG tiles and sprites.
     *
     * @return Vertical shake offset in pixels, or 0 if no shake
     */
    public int getShakeOffsetY() {
        return currentShakeOffsetY;
    }

    /**
     * Get the current Tornado X velocity (pixels per frame) for SCZ objects.
     * ROM: loc_36776 adds Tornado_Velocity_X to object x_pos each frame.
     */
    public int getTornadoVelocityX() {
        return scrollProvider != null ? scrollProvider.getTornadoVelocityX() : 0;
    }

    /**
     * Get the current Tornado Y velocity (pixels per frame) for SCZ objects.
     * ROM: loc_36776 adds Tornado_Velocity_Y to object y_pos each frame.
     */
    public int getTornadoVelocityY() {
        return scrollProvider != null ? scrollProvider.getTornadoVelocityY() : 0;
    }

    /**
     * Get the current background camera X offset used by WFZ scripted objects.
     * ROM equivalent: Camera_BG_X_offset.
     */
    public int getCameraBgXOffset() {
        return scrollProvider != null ? scrollProvider.getCameraBgXOffset() : 0;
    }

    /**
     * Set the screen shake flag for MCZ.
     * @deprecated Use GameServices.gameState().setScreenShakeActive() directly.
     *             Screen shake is now a global state that all zones check.
     */
    @Deprecated
    public void setScreenShakeFlag(boolean screenShakeFlag) {
        GameServices.gameState().setScreenShakeActive(screenShakeFlag);
    }

    /**
     * Set the HTZ screen shake mode flag.
     * This sets the HTZ-specific flag (Screen_Shaking_Flag_HTZ) which stays
     * active for the entire earthquake sequence, as well as the general
     * screen shake flag for visual shake effects.
     */
    public void setHtzScreenShake(boolean active) {
        GameServices.gameState().setHtzScreenShakeActive(active);
        GameServices.gameState().setScreenShakeActive(active);
    }

    public void update(int zoneId, int actId, Camera cam, int frameCounter, int bgScrollY) {
        // Clear scroll buffer to ensure deterministic state
        // (some zone handlers intentionally leave lines unwritten)
        java.util.Arrays.fill(hScroll, 0);

        minScroll = Integer.MAX_VALUE;
        maxScroll = Integer.MIN_VALUE;

        // Reset shake offsets at start of frame
        currentShakeOffsetX = 0;
        currentShakeOffsetY = 0;
        java.util.Arrays.fill(vScrollPerLineBG, (short) 0);
        java.util.Arrays.fill(vScrollPerColumnBG, (short) 0);
        hasPerLineVScrollBG = false;
        hasPerColumnVScrollBG = false;

        int cameraX = cam.getX();
        int cameraY = cam.getY();
        vscrollFactorFG = (short) cameraY;

        // Unified provider-based dispatch for all games
        if (scrollProvider != null) {
            initZone(zoneId, actId, cameraX, cameraY);

            ZoneScrollHandler handler = scrollProvider.getHandler(zoneId);
            if (handler != null) {
                handler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                minScroll = handler.getMinScrollOffset();
                maxScroll = handler.getMaxScrollOffset();
                vscrollFactorBG = handler.getVscrollFactorBG();
                currentShakeOffsetX = handler.getShakeOffsetX();
                currentShakeOffsetY = handler.getShakeOffsetY();
                cachedBgCameraX = handler.getBgCameraX();
                cachedBgPeriodWidth = handler.getBgPeriodWidth();
                capturePerLineVScroll(handler);
                capturePerColumnVScroll(handler);

                // FG vscroll: handlers that apply screen shake (MCZ, HTZ earthquake)
                // return a non-zero value including the ripple offset.
                // Handlers that don't override FG vscroll return 0 (the default).
                // SCZ modifies camera position directly during update(), so we
                // re-read cam.getY() for the default case.
                short handlerFgVscroll = handler.getVscrollFactorFG();
                if (handlerFgVscroll != 0) {
                    vscrollFactorFG = handlerFgVscroll;
                } else {
                    vscrollFactorFG = (short) cam.getY();
                }
            } else {
                cachedBgCameraX = Integer.MIN_VALUE;
                cachedBgPeriodWidth = 512;
                fillMinimal(cam);
            }
        } else {
            fillMinimal(cam);
        }
    }

    /**
     * Update parallax scrolling with dynamic art streaming support.
     * This overload handles zone-specific dynamic art updates (HTZ mountains/clouds).
     *
     * @param zoneId Zone identifier
     * @param actId Act identifier
     * @param cam Camera instance
     * @param frameCounter Current frame counter
     * @param bgScrollY Background scroll Y value
     * @param level Level instance for dynamic art updates (may be null)
     */
    public void update(int zoneId, int actId, Camera cam, int frameCounter, int bgScrollY, Level level) {
        // Perform standard parallax update
        update(zoneId, actId, cam, frameCounter, bgScrollY);

        // Update zone-specific dynamic art via the provider
        if (scrollProvider != null && level != null) {
            scrollProvider.updateDynamicArt(level, cam.getX());
        }
    }

    /**
     * Update parallax for the ending cutscene.
     * <p>
     * Uses camera (0,0) and a fixed BG vscroll value from the ending provider.
     * Delegates to the scroll provider's ending handler for the
     * zone-specific scroll behavior during the ending sequence.
     *
     * @param zoneId     zone ID for the ending zone
     * @param actId      act ID
     * @param frameCounter current frame counter
     * @param bgVscroll  ending BG vertical scroll (ROM: Camera_BG_Y_pos)
     */
    public void updateForEnding(int zoneId, int actId, int frameCounter, int bgVscroll) {
        java.util.Arrays.fill(hScroll, 0);
        minScroll = Integer.MAX_VALUE;
        maxScroll = Integer.MIN_VALUE;

        // Camera is (0,0) during ending
        vscrollFactorFG = 0;
        vscrollFactorBG = (short) bgVscroll;

        // Initialize zone if needed
        initZone(zoneId, actId, 0, 0);

        // Delegate to the provider for game-specific ending scroll
        if (scrollProvider != null) {
            if (scrollProvider.updateForEnding(hScroll, zoneId, actId, frameCounter, vscrollFactorBG)) {
                // Provider handled the ending - extract results from the handler
                ZoneScrollHandler handler = scrollProvider.getHandler(zoneId);
                if (handler != null) {
                    minScroll = handler.getMinScrollOffset();
                    maxScroll = handler.getMaxScrollOffset();
                    vscrollFactorBG = handler.getVscrollFactorBG();
                }
            }
        }
    }

    // ========== Helper Methods ==========

    private static int packScrollWords(short fg, short bg) {
        return ((fg & 0xFFFF) << 16) | (bg & 0xFFFF);
    }

    private void fillMinimal(Camera cam) {
        short fgScroll = (short) -cam.getX();
        int offset = cam.getX() >> 1;
        short bgScroll = (short) (fgScroll + offset);
        int packed = packScrollWords(fgScroll, bgScroll);

        int offsetShort = (short) offset;
        minScroll = offsetShort;
        maxScroll = offsetShort;

        for (int line = 0; line < VISIBLE_LINES; line++) {
            hScroll[line] = packed;
        }
    }

    private void capturePerLineVScroll(ZoneScrollHandler handler) {
        short[] perLine = handler.getPerLineVScrollBG();
        if (perLine == null || perLine.length == 0) {
            hasPerLineVScrollBG = false;
            return;
        }
        int count = Math.min(VISIBLE_LINES, perLine.length);
        System.arraycopy(perLine, 0, vScrollPerLineBG, 0, count);
        if (count < VISIBLE_LINES) {
            java.util.Arrays.fill(vScrollPerLineBG, count, VISIBLE_LINES, (short) 0);
        }
        hasPerLineVScrollBG = true;
    }

    private void capturePerColumnVScroll(ZoneScrollHandler handler) {
        short[] perColumn = handler.getPerColumnVScrollBG();
        if (perColumn == null || perColumn.length == 0) {
            hasPerColumnVScrollBG = false;
            return;
        }
        int count = Math.min(BG_VSCROLL_COLUMN_COUNT, perColumn.length);
        System.arraycopy(perColumn, 0, vScrollPerColumnBG, 0, count);
        if (count < BG_VSCROLL_COLUMN_COUNT) {
            java.util.Arrays.fill(vScrollPerColumnBG, count, BG_VSCROLL_COLUMN_COUNT, (short) 0);
        }
        hasPerColumnVScrollBG = true;
    }
}
