package uk.co.jamesj999.sonic.level;

import uk.co.jamesj999.sonic.camera.Camera;
import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic2.DynamicHtz;
import uk.co.jamesj999.sonic.level.scroll.BackgroundCamera;
import uk.co.jamesj999.sonic.level.scroll.ParallaxTables;
import uk.co.jamesj999.sonic.level.scroll.SwScrlArz;
import uk.co.jamesj999.sonic.level.scroll.SwScrlCnz;
import uk.co.jamesj999.sonic.level.scroll.SwScrlCpz;
import uk.co.jamesj999.sonic.level.scroll.SwScrlEhz;
import uk.co.jamesj999.sonic.level.scroll.SwScrlMcz;
import uk.co.jamesj999.sonic.level.scroll.SwScrlHtz;
import uk.co.jamesj999.sonic.level.scroll.SwScrlOoz;

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

    private int minScroll = 0;
    private int maxScroll = 0;

    private short vscrollFactorFG;
    private short vscrollFactorBG;

    private final BackgroundCamera bgCamera = new BackgroundCamera();
    private ParallaxTables tables;
    private boolean loaded = false;

    private SwScrlArz arzHandler;
    private SwScrlCnz cnzHandler;
    private SwScrlEhz ehzHandler;
    private SwScrlCpz cpzHandler;
    private SwScrlHtz htzHandler;
    private SwScrlMcz mczHandler;
    private SwScrlOoz oozHandler;

    // HTZ dynamic art streaming (mountains and clouds)
    private DynamicHtz dynamicHtz;

    private int currentZone = -1;
    private int currentAct = -1;

    // Screen shake offsets propagated from zone handlers
    private int currentShakeOffsetX = 0;
    private int currentShakeOffsetY = 0;

    // Pre-allocated arrays to avoid per-frame allocations
    private final int[] wfzOffsets = new int[4];

    private static ParallaxManager instance;

    public static synchronized ParallaxManager getInstance() {
        if (instance == null) {
            instance = new ParallaxManager();
        }
        return instance;
    }

    public void load(Rom rom) {
        if (loaded)
            return;
        try {
            tables = new ParallaxTables(rom);
            arzHandler = new SwScrlArz(tables);
            cnzHandler = new SwScrlCnz(tables);
            ehzHandler = new SwScrlEhz(tables);
            cpzHandler = new SwScrlCpz(tables);
            htzHandler = new SwScrlHtz(tables, bgCamera);
            mczHandler = new SwScrlMcz(tables);
            oozHandler = new SwScrlOoz(tables);
            dynamicHtz = new DynamicHtz();
            dynamicHtz.init();
            loaded = true;
            LOGGER.info("Parallax tables loaded.");
        } catch (IOException e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed to load parallax data: " + e.getMessage(), e);
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

        if (!loaded) {
            fillMinimal(cam);
            return;
        }

        initZone(zoneId, actId, cam.getX(), cam.getY());

        int cameraX = cam.getX();
        int cameraY = cam.getY();

        vscrollFactorFG = (short) cameraY;
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
                fillMtz(cameraX);
                break;
            case ZONE_SCZ:
                fillScz(cameraX, cameraY);
                break;
            case ZONE_WFZ:
                fillWfz(cameraX, frameCounter);
                break;
            case ZONE_DEZ:
                fillDez(cameraX, frameCounter);
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
    private void fillMtz(int cameraX) {
        short fgScroll = (short) -cameraX;
        int offset = cameraX - (cameraX >> 2);
        for (int line = 0; line < VISIBLE_LINES; line++) {
            setLineWithOffset(line, fgScroll, offset);
        }
    }

    /**
     * SCZ - Sky Chase Zone
     */
    private void fillScz(int cameraX, int cameraY) {
        short fgScroll = (short) -cameraX;

        for (int line = 0; line < VISIBLE_LINES; line++) {
            int offset;
            if (line < 64) {
                offset = cameraX - (cameraX >> 3);
            } else if (line < 144) {
                offset = cameraX - (cameraX >> 2);
            } else {
                offset = cameraX - (cameraX >> 2) - (cameraX >> 3);
            }
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

    /**
     * Returns the parallax tables for use by other managers (e.g., ScreenShakeManager).
     *
     * @return ParallaxTables instance, or null if not yet loaded
     */
    public ParallaxTables getTables() {
        return tables;
    }
}
