package com.openggf.level;

import com.openggf.camera.Camera;
import com.openggf.data.Rom;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameModule;
import com.openggf.game.GameServices;
import com.openggf.game.ScrollHandlerProvider;
import com.openggf.game.sonic2.DynamicHtz;
import com.openggf.level.scroll.BackgroundCamera;
import com.openggf.level.scroll.ParallaxTables;
import com.openggf.level.scroll.SwScrlArz;
import com.openggf.level.scroll.SwScrlCnz;
import com.openggf.level.scroll.SwScrlCpz;
import com.openggf.level.scroll.SwScrlEhz;
import com.openggf.level.scroll.SwScrlMcz;
import com.openggf.level.scroll.SwScrlHtz;
import com.openggf.level.scroll.SwScrlDez;
import com.openggf.level.scroll.SwScrlOoz;
import com.openggf.level.scroll.SwScrlScz;
import com.openggf.level.scroll.SwScrlWfz;
import com.openggf.level.scroll.ZoneScrollHandler;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Manages parallax scrolling effects.
 * Outputs scroll values compatible with the LevelManager rendering system.
 */
public class ParallaxManager {
    private static final Logger LOGGER = Logger.getLogger(ParallaxManager.class.getName());

    public static final int VISIBLE_LINES = 224;

    // Zone IDs (matching LevelManager list index)
    public static final int ZONE_EHZ = 0;
    public static final int ZONE_CPZ = 1;
    public static final int ZONE_ARZ = 2;
    public static final int ZONE_CNZ = 3;
    public static final int ZONE_HTZ = 4;
    public static final int ZONE_MCZ = 5;
    public static final int ZONE_OOZ = 6;
    public static final int ZONE_MTZ = 7;
    public static final int ZONE_SCZ = 8;
    public static final int ZONE_WFZ = 9;
    public static final int ZONE_DEZ = 10;

    // Background map heights for wrapping

    private static final int CPZ_BG_HEIGHT = 256;
    private static final int ARZ_BG_HEIGHT = 256;
    private static final int CNZ_BG_HEIGHT = 512;
    private static final int OOZ_BG_HEIGHT = 256;
    private static final int MCZ_BG_HEIGHT = 256;

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

    private final BackgroundCamera bgCamera = new BackgroundCamera();
    private ParallaxTables tables;
    private boolean loaded = false;

    // Game-agnostic scroll handler provider (from current GameModule)
    private ScrollHandlerProvider scrollProvider;
    private boolean providerLoaded = false;

    // Sonic 2-specific handlers (used when loaded == true)
    private SwScrlArz arzHandler;
    private SwScrlCnz cnzHandler;
    private SwScrlEhz ehzHandler;
    private SwScrlCpz cpzHandler;
    private SwScrlHtz htzHandler;
    private SwScrlMcz mczHandler;
    private SwScrlDez dezHandler;
    private SwScrlOoz oozHandler;
    private SwScrlScz sczHandler;
    private SwScrlWfz wfzHandler;

    // HTZ dynamic art streaming (mountains and clouds)
    private DynamicHtz dynamicHtz;

    private int currentZone = -1;
    private int currentAct = -1;

    // Screen shake offsets propagated from zone handlers
    private int currentShakeOffsetX = 0;
    private int currentShakeOffsetY = 0;

    // Cached BG camera X from the active scroll handler (Integer.MIN_VALUE = no offset)
    private int cachedBgCameraX = Integer.MIN_VALUE;

    // Pre-allocated arrays to avoid per-frame allocations
    private final int[] wfzOffsets = new int[4];

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
        // Reset HTZ-specific state
        if (htzHandler != null) {
            htzHandler.init();
        }
        if (dynamicHtz != null) {
            dynamicHtz.reset();
        }
    }

    /**
     * Resets all mutable state without destroying the singleton instance.
     * Clears zone handlers, tables, and scroll factors so the next load starts fresh.
     */
    public void resetState() {
        resetZoneState();
        arzHandler = null;
        cnzHandler = null;
        ehzHandler = null;
        cpzHandler = null;
        htzHandler = null;
        mczHandler = null;
        dezHandler = null;
        oozHandler = null;
        sczHandler = null;
        wfzHandler = null;
        dynamicHtz = null;
        scrollProvider = null;
        tables = null;
        loaded = false;
        providerLoaded = false;
        vscrollFactorFG = 0;
        vscrollFactorBG = 0;
        currentShakeOffsetX = 0;
        currentShakeOffsetY = 0;
        cachedBgCameraX = Integer.MIN_VALUE;
        minScroll = 0;
        maxScroll = 0;
        java.util.Arrays.fill(hScroll, 0);
        java.util.Arrays.fill(vScrollPerLineBG, (short) 0);
        java.util.Arrays.fill(vScrollPerColumnBG, (short) 0);
        hasPerLineVScrollBG = false;
        hasPerColumnVScrollBG = false;
    }

    public void load(Rom rom) {
        if (loaded || providerLoaded) {
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

        // Load Sonic 2-specific inline handlers (ParallaxTables, zone-specific post-processing).
        // Only for Sonic 2 - other games use the provider path exclusively.
        if (module != null && module.hasInlineParallaxHandlers()) {
            try {
                tables = new ParallaxTables(rom);
                arzHandler = new SwScrlArz(tables);
                cnzHandler = new SwScrlCnz(tables);
                ehzHandler = new SwScrlEhz(tables);
                cpzHandler = new SwScrlCpz(tables);
                htzHandler = new SwScrlHtz(tables, bgCamera);
                mczHandler = new SwScrlMcz(tables);
                dezHandler = new SwScrlDez(tables);
                oozHandler = new SwScrlOoz(tables);
                sczHandler = new SwScrlScz();
                wfzHandler = new SwScrlWfz(tables, bgCamera);
                dynamicHtz = new DynamicHtz();
                dynamicHtz.init();
                loaded = true;
                LOGGER.info("Parallax tables loaded (Sonic 2).");
            } catch (IOException e) {
                LOGGER.log(java.util.logging.Level.SEVERE,
                        "Failed to load parallax data: " + e.getMessage(), e);
            }
        }
    }

    public void initZone(int zoneId, int actId, int cameraX, int cameraY) {
        if (zoneId != currentZone || actId != currentAct) {
            bgCamera.init(zoneId, actId, cameraX, cameraY);
            currentZone = zoneId;
            currentAct = actId;

            if (zoneId == ZONE_ARZ && arzHandler != null) {
                arzHandler.init(actId, cameraX, cameraY);
            } else if (zoneId == ZONE_CPZ && cpzHandler != null) {
                cpzHandler.init(cameraX, cameraY);
            } else if (zoneId == ZONE_HTZ && htzHandler != null) {
                htzHandler.init();
                if (dynamicHtz != null) {
                    dynamicHtz.reset();
                }
            } else if (zoneId == ZONE_OOZ && oozHandler != null) {
                oozHandler.init(cameraX, cameraY);
            } else if (zoneId == ZONE_SCZ && sczHandler != null) {
                sczHandler.init();
                // SCZ camera is driven by Tornado velocity, not player following.
                // Freeze camera to prevent normal updatePosition() from overriding.
                Camera.getInstance().setFrozen(true);
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
        return sczHandler != null ? sczHandler.getTornadoVelocityX() : 0;
    }

    /**
     * Get the current Tornado Y velocity (pixels per frame) for SCZ objects.
     * ROM: loc_36776 adds Tornado_Velocity_Y to object y_pos each frame.
     */
    public int getTornadoVelocityY() {
        return sczHandler != null ? sczHandler.getTornadoVelocityY() : 0;
    }

    /**
     * Get the current background camera X offset used by WFZ scripted objects.
     * ROM equivalent: Camera_BG_X_offset.
     */
    public int getCameraBgXOffset() {
        return bgCamera.getBgXPos();
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

        // For non-Sonic 2 games, use the game-specific scroll handler provider
        if (!loaded && scrollProvider != null) {
            ZoneScrollHandler handler = scrollProvider.getHandler(zoneId);
            if (handler != null) {
                handler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                minScroll = handler.getMinScrollOffset();
                maxScroll = handler.getMaxScrollOffset();
                vscrollFactorBG = handler.getVscrollFactorBG();
                cachedBgCameraX = handler.getBgCameraX();
                capturePerLineVScroll(handler);
                capturePerColumnVScroll(handler);
            } else {
                cachedBgCameraX = Integer.MIN_VALUE;
                fillMinimal(cam);
            }
            return;
        }

        if (!loaded) {
            fillMinimal(cam);
            return;
        }

        // Sonic 2 path: use dedicated handlers with zone-specific post-processing
        initZone(zoneId, actId, cameraX, cameraY);
        vscrollFactorBG = (short) bgCamera.getBgYPos();

        switch (zoneId) {
            case ZONE_EHZ:
                if (ehzHandler != null) {
                    ehzHandler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                    minScroll = ehzHandler.getMinScrollOffset();
                    maxScroll = ehzHandler.getMaxScrollOffset();
                    vscrollFactorBG = ehzHandler.getVscrollFactorBG();
                } else {
                    // Fallback should normally not happen if loaded
                    // fillEhz(cameraX, frameCounter, bgScrollY);
                }
                break;
            case ZONE_CPZ:
                if (cpzHandler != null) {
                    cpzHandler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                    minScroll = cpzHandler.getMinScrollOffset();
                    maxScroll = cpzHandler.getMaxScrollOffset();
                    vscrollFactorBG = cpzHandler.getVscrollFactorBG();
                } else {
                    fillCpz(cameraX, bgScrollY, frameCounter);
                }
                break;
            case ZONE_ARZ:
                if (arzHandler != null) {
                    arzHandler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                    minScroll = arzHandler.getMinScrollOffset();
                    maxScroll = arzHandler.getMaxScrollOffset();
                    vscrollFactorBG = arzHandler.getVscrollFactorBG();
                    // Capture shake offsets for FG tiles and sprites
                    currentShakeOffsetX = arzHandler.getShakeOffsetX();
                    currentShakeOffsetY = arzHandler.getShakeOffsetY();
                }
                break;
            case ZONE_CNZ:
                if (cnzHandler != null) {
                    cnzHandler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                    minScroll = cnzHandler.getMinScrollOffset();
                    maxScroll = cnzHandler.getMaxScrollOffset();
                    vscrollFactorBG = cnzHandler.getVscrollFactorBG();
                    // Update bgCamera for renderer's vertical scroll
                    bgCamera.setBgYPos(vscrollFactorBG);
                } else {
                    fillCnz(cameraX, bgScrollY);
                }
                break;
            case ZONE_HTZ:
                if (htzHandler != null) {
                    htzHandler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                    minScroll = htzHandler.getMinScrollOffset();
                    maxScroll = htzHandler.getMaxScrollOffset();
                    vscrollFactorBG = htzHandler.getVscrollFactorBG();
                    if (GameServices.gameState().isScreenShakeActive()) {
                        vscrollFactorFG = htzHandler.getVscrollFactorFG();
                        // Capture shake offsets for FG tiles and sprites
                        currentShakeOffsetX = htzHandler.getShakeOffsetX();
                        currentShakeOffsetY = htzHandler.getShakeOffsetY();
                    }
                } else {
                    fillHtz(cameraX, bgScrollY);
                }
                break;
            case ZONE_MCZ:
                if (mczHandler != null) {
                    mczHandler.update(hScroll, cameraX, cameraY, frameCounter, currentAct);
                    minScroll = mczHandler.getMinScrollOffset();
                    maxScroll = mczHandler.getMaxScrollOffset();
                    vscrollFactorBG = mczHandler.getVscrollFactorBG();
                    vscrollFactorFG = mczHandler.getVscrollFactorFG();
                    // Capture shake offsets for FG tiles and sprites
                    currentShakeOffsetX = mczHandler.getShakeOffsetX();
                    currentShakeOffsetY = mczHandler.getShakeOffsetY();
                    // Update bgCamera for renderer's vertical scroll
                    bgCamera.setBgYPos(mczHandler.getBgY());
                }
                break;
            case ZONE_OOZ:
                if (oozHandler != null) {
                    oozHandler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                    minScroll = oozHandler.getMinScrollOffset();
                    maxScroll = oozHandler.getMaxScrollOffset();
                    vscrollFactorBG = oozHandler.getVscrollFactorBG();
                    // Update bgCamera for renderer's vertical scroll
                    bgCamera.setBgYPos(vscrollFactorBG);
                } else {
                    fillOoz(cameraX, bgScrollY, frameCounter);
                }
                break;
            case ZONE_MTZ:
                fillMtz(cameraX, cameraY);
                break;
            case ZONE_SCZ:
                if (sczHandler != null) {
                    sczHandler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                    minScroll = sczHandler.getMinScrollOffset();
                    maxScroll = sczHandler.getMaxScrollOffset();
                    vscrollFactorBG = sczHandler.getVscrollFactorBG();
                    // Re-read camera position after SCZ handler modifies it
                    vscrollFactorFG = (short) cam.getY();
                } else {
                    fillScz(cameraX, cameraY);
                }
                break;
            case ZONE_WFZ:
                if (wfzHandler != null) {
                    wfzHandler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                    minScroll = wfzHandler.getMinScrollOffset();
                    maxScroll = wfzHandler.getMaxScrollOffset();
                    vscrollFactorBG = wfzHandler.getVscrollFactorBG();
                } else {
                    fillWfz(cameraX, frameCounter);
                }
                break;
            case ZONE_DEZ:
                if (dezHandler != null) {
                    dezHandler.setVscrollFactorBG(vscrollFactorBG);
                    dezHandler.update(hScroll, cameraX, cameraY, frameCounter, actId);
                    minScroll = dezHandler.getMinScrollOffset();
                    maxScroll = dezHandler.getMaxScrollOffset();
                    vscrollFactorBG = dezHandler.getVscrollFactorBG();
                } else {
                    fillDez(cameraX, frameCounter);
                }
                break;
            default:
                fillMinimal(cam);
                break;
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

        // Update zone-specific dynamic art
        if (zoneId == ZONE_HTZ && dynamicHtz != null && level != null && htzHandler != null) {
            dynamicHtz.update(level, cam.getX(), htzHandler);
        }
    }

    /**
     * Update parallax for the ending cutscene.
     * <p>
     * Uses camera (0,0) and a fixed BG vscroll value from the ending provider.
     * Only drives the DEZ scroll handler (SwScrlDez) since DEZ is always the
     * zone during the ending.
     *
     * @param zoneId     zone ID (should be ZONE_DEZ=10)
     * @param actId      act ID
     * @param frameCounter current frame counter
     * @param bgVscroll  ending BG vertical scroll (ROM: Camera_BG_Y_pos)
     */
    public void updateForEnding(int zoneId, int actId, int frameCounter, int bgVscroll) {
        java.util.Arrays.fill(hScroll, 0);
        minScroll = Integer.MAX_VALUE;
        maxScroll = Integer.MIN_VALUE;

        // Camera is (0,0) during ending
        int cameraX = 0;
        vscrollFactorFG = 0;
        vscrollFactorBG = (short) bgVscroll;

        // Initialize zone if needed (ensures dezHandler is created)
        initZone(zoneId, actId, cameraX, 0);

        if (dezHandler != null) {
            dezHandler.setVscrollFactorBG(vscrollFactorBG);
            dezHandler.update(hScroll, cameraX, 0, frameCounter, actId);
            minScroll = dezHandler.getMinScrollOffset();
            maxScroll = dezHandler.getMaxScrollOffset();
            vscrollFactorBG = dezHandler.getVscrollFactorBG();
        }
    }

    // ========== Zone-Specific Scroll Routines ==========

    /**
     * EHZ - Emerald Hill Zone
     * Uses bgScrollY to map screen lines to background map positions.
     * Grass scrolls FASTER (smaller offset) toward the bottom.
     */
    /**
     * EHZ - Emerald Hill Zone
     * Pixel-perfect implementation matching SwScrl_EHZ (1P) from S2 disassembly.
     */

    /**
     * CPZ - Chemical Plant Zone
     * Background is blue pipes. Water with shimmer at bottom of BG map.
     * 
     * The shimmer must appear where the water texture is in the CPZ BG.
     * CPZ water shimmer. Adding a fixed offset to move shimmer down on screen.
     */
    private void fillCpz(int cameraX, int bgScrollY, int frameCounter) {
        short fgScroll = (short) -cameraX;

        // CPZ BG scrolls at 1/4 speed for X
        int baseOffset = cameraX - (cameraX >> 2);

        int bgCameraY = bgScrollY;

        // Calculate where water appears on screen
        // Water is at BG map Y 192-255. It appears on screen at line (192 - bgCameraY)
        // Add 96 to push shimmer lower on screen to align with actual water texture
        int shimmerScreenStart = 192 - bgCameraY + 96;
        int shimmerScreenEnd = shimmerScreenStart + 16; // 16 pixels of shimmer (was 32)

        for (int screenLine = 0; screenLine < VISIBLE_LINES; screenLine++) {
            int offset = baseOffset;

            // Apply shimmer only to screen lines that show the water
            if (screenLine >= shimmerScreenStart && screenLine < shimmerScreenEnd && tables != null) {
                int waterLineOffset = screenLine - shimmerScreenStart;
                int rippleIdx = ((frameCounter >> 2) + waterLineOffset) % tables.getRippleDataLength();
                offset += tables.getRippleSigned(rippleIdx);
            }

            setLineWithOffset(screenLine, fgScroll, offset);
        }
    }

    public void dumpArzBuffer() {
        StringBuilder sb = new StringBuilder();
        sb.append("ARZ Scroll Buffer Dump:\n");
        for (int i = 0; i < VISIBLE_LINES; i++) {
            int val = hScroll[i];
            short fg = (short) (val >> 16);
            short bg = (short) (val & 0xFFFF);
            sb.append(String.format("Line %03d: FG=%d, BG=%d\n", i, fg, bg));
        }
        LOGGER.info(sb.toString());
    }

    /**
     * CNZ - Casino Night Zone
     * City skyline with multiple layers.
     */
    private void fillCnz(int cameraX, int bgScrollY) {
        short fgScroll = (short) -cameraX;

        for (int screenLine = 0; screenLine < VISIBLE_LINES; screenLine++) {
            int mapY = (screenLine + bgScrollY) % CNZ_BG_HEIGHT;
            if (mapY < 0)
                mapY += CNZ_BG_HEIGHT;

            int offset;
            // CNZ has tall buildings, graduated parallax
            if (mapY < 128) {
                // Top sky/stars - very slow
                offset = cameraX - (cameraX >> 5);
            } else if (mapY < 320) {
                // Mid buildings - slow
                offset = cameraX - (cameraX >> 4);
            } else {
                // Lower buildings - medium
                offset = cameraX - (cameraX >> 3);
            }

            setLineWithOffset(screenLine, fgScroll, offset);
        }
    }

    /**
     * HTZ - Hill Top Zone
     */
    private void fillHtz(int cameraX, int bgScrollY) {
        short fgScroll = (short) -cameraX;

        for (int screenLine = 0; screenLine < VISIBLE_LINES; screenLine++) {
            int offset;
            if (screenLine < 80) {
                // Sky - static
                offset = cameraX;
            } else if (screenLine < 160) {
                // Mountains - very slow
                offset = cameraX - (cameraX >> 5);
            } else {
                // Hills - slow
                offset = cameraX - (cameraX >> 4);
            }
            setLineWithOffset(screenLine, fgScroll, offset);
        }
    }

    /**
     * OOZ - Oil Ocean Zone
     * Sun, refinery, and oil. Uses bgScrollY for correct positioning.
     */
    private void fillOoz(int cameraX, int bgScrollY, int frameCounter) {
        short fgScroll = (short) -cameraX;

        for (int screenLine = 0; screenLine < VISIBLE_LINES; screenLine++) {
            int mapY = (screenLine + bgScrollY) % OOZ_BG_HEIGHT;
            if (mapY < 0)
                mapY += OOZ_BG_HEIGHT;

            int offset;

            // OOZ layout: sun at top (with shimmer), refinery in middle, oil at bottom
            if (mapY < 48) {
                // Sun/sky with heat shimmer
                offset = cameraX; // Static
                if (tables != null) {
                    int ripple = tables.getRippleSigned((frameCounter + mapY) % tables.getRippleDataLength());
                    offset += ripple;
                }
            } else if (mapY < 144) {
                // Refinery - slow parallax
                offset = cameraX - (cameraX >> 4);
            } else {
                // Oil/lower refinery - medium parallax
                offset = cameraX - (cameraX >> 3);
            }

            setLineWithOffset(screenLine, fgScroll, offset);
        }
    }

    /**
     * MTZ - Metropolis Zone
     */
    private void fillMtz(int cameraX, int cameraY) {
        short fgScroll = (short) -cameraX;
        // SwScrl_MTZ uses Camera_X_pos_diff << 5 which tracks at 1/8 speed
        int offset = cameraX - (cameraX >> 3);
        for (int line = 0; line < VISIBLE_LINES; line++) {
            setLineWithOffset(line, fgScroll, offset);
        }
        // MTZ BG Y scrolls at 1/4 camera speed
        vscrollFactorBG = (short)(cameraY >> 2);
    }

    /**
     * SCZ - Sky Chase Zone
     * No per-scanline parallax. All 224 scanlines use the same BG scroll.
     * BG advances at 0.5 px/frame while FG advances at ~1 px/frame (tornado
     * velocity), so BG scrolls at half the foreground speed.
     */
    private void fillScz(int cameraX, int cameraY) {
        short fgScroll = (short) -cameraX;
        int offset = cameraX - (cameraX >> 1);
        for (int line = 0; line < VISIBLE_LINES; line++) {
            setLineWithOffset(line, fgScroll, offset);
        }
    }

    /**
     * WFZ - Wing Fortress Zone
     */
    private void fillWfz(int cameraX, int frameCounter) {
        short fgScroll = (short) -cameraX;

        int[] offsets = {
                cameraX - (cameraX >> 4),
                cameraX - (cameraX >> 3),
                cameraX - (cameraX >> 2),
                cameraX - (cameraX >> 1)
        };

        int segmentHeight = VISIBLE_LINES / 4;
        for (int line = 0; line < VISIBLE_LINES; line++) {
            int layer = Math.min(line / segmentHeight, 3);
            setLineWithOffset(line, fgScroll, offsets[layer]);
        }
    }

    /**
     * DEZ - Death Egg Zone
     */
    private void fillDez(int cameraX, int frameCounter) {
        short fgScroll = (short) -cameraX;

        for (int line = 0; line < VISIBLE_LINES; line++) {
            int starLayer = (line / 32) % 4;
            int baseOffset = cameraX - (cameraX >> 5);
            int layerVar = starLayer * 2;
            setLineWithOffset(line, fgScroll, baseOffset + layerVar);
        }
    }

    // ========== Helper Methods ==========

    private void setLineWithOffset(int line, short fgScroll, int bgOffset) {
        if (line >= 0 && line < VISIBLE_LINES) {
            short bgScroll = (short) (fgScroll + bgOffset);
            hScroll[line] = packScrollWords(fgScroll, bgScroll);

            short offsetShort = (short) bgOffset;
            if (offsetShort < minScroll)
                minScroll = offsetShort;
            if (offsetShort > maxScroll)
                maxScroll = offsetShort;
        }
    }

    private static int packScrollWords(short fg, short bg) {
        return ((fg & 0xFFFF) << 16) | (bg & 0xFFFF);
    }

    private void fillMinimal(Camera cam) {
        short fgScroll = (short) -cam.getX();
        int offset = cam.getX() >> 1;
        for (int line = 0; line < VISIBLE_LINES; line++) {
            setLineWithOffset(line, fgScroll, offset);
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

    /**
     * Returns the parallax tables for use by other managers (e.g., ScreenShakeManager).
     *
     * @return ParallaxTables instance, or null if not yet loaded
     */
    public ParallaxTables getTables() {
        return tables;
    }
}
