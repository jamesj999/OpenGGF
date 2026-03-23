package com.openggf.level;

import com.openggf.game.GameServices;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlas;
import com.openggf.graphics.TilemapGpuRenderer;

import java.util.logging.Logger;

/**
 * Manages the build, cache, upload, and invalidation lifecycle of GPU tilemap data
 * (foreground and background layers) extracted from LevelManager.
 * <p>
 * This class owns all tilemap byte arrays, dirty flags, pattern lookup data,
 * and prebuilt transition tilemaps.  LevelManager delegates tilemap operations
 * here and reads back data via getters for its GL command lambdas.
 */
public class LevelTilemapManager {
    private static final Logger LOGGER = Logger.getLogger(LevelTilemapManager.class.getName());

    // VDP plane size for Sonic 2 normal levels: 64x32 cells = 512x256 pixels.
    // The background tilemap wraps at this width for Sonic 2's redraw-style pipeline.
    static final int VDP_BG_PLANE_WIDTH_PX = 512;
    private static final int VDP_BG_PLANE_HEIGHT_TILES = 32; // VDP 64x32 nametable

    // --- Dependencies ---
    private LevelGeometry geometry;
    private final GraphicsManager graphicsManager;

    // --- Background tilemap data ---
    private byte[] backgroundTilemapData;
    private int backgroundTilemapWidthTiles;
    private int backgroundTilemapHeightTiles;
    private boolean backgroundTilemapDirty = true;
    private int backgroundVdpWrapHeightTiles = 0; // 0 = disabled
    // X offset (in pixels, 512-aligned) for BG tilemap building.
    // Wide BG maps (> 512px) need tiles from the correct region, not always from position 0.
    private int bgTilemapBaseX = 0;
    private int currentBgPeriodWidth = VDP_BG_PLANE_WIDTH_PX;

    // --- Foreground tilemap data ---
    private byte[] foregroundTilemapData;
    private int foregroundTilemapWidthTiles;
    private int foregroundTilemapHeightTiles;
    private boolean foregroundTilemapDirty = true;

    // --- Pattern lookup data ---
    private byte[] patternLookupData;
    private int patternLookupSize;
    private boolean patternLookupDirty = true;
    private boolean multiAtlasWarningLogged = false;

    // --- Pre-built tilemap data for stutter-free terrain transitions (AIZ intro) ---
    private byte[] prebuiltFgTilemap;
    private int prebuiltFgWidth;
    private int prebuiltFgHeight;
    private byte[] prebuiltBgTilemap;
    private int prebuiltBgWidth;
    private int prebuiltBgHeight;

    // Reusable PatternDesc to avoid per-iteration allocations in tight loops
    private final PatternDesc reusablePatternDesc = new PatternDesc();

    /**
     * Functional interface for block lookups, bridging to LevelManager's getBlockAtPosition.
     */
    @FunctionalInterface
    public interface BlockLookup {
        Block lookup(byte layer, int x, int y);
    }

    /**
     * Immutable record holding the data produced by {@link #buildTilemapData}.
     */
    record TilemapData(byte[] data, int widthTiles, int heightTiles) {
    }

    /**
     * Creates a new LevelTilemapManager.
     *
     * @param geometry        level geometry snapshot (dimensions, level reference)
     * @param graphicsManager graphics manager for pattern atlas access
     */
    public LevelTilemapManager(LevelGeometry geometry, GraphicsManager graphicsManager) {
        this.geometry = geometry;
        this.graphicsManager = graphicsManager;
    }

    // -----------------------------------------------------------------------
    // Geometry updates
    // -----------------------------------------------------------------------

    /**
     * Updates the geometry reference after a seamless level transition.
     */
    public void updateGeometry(LevelGeometry geometry) {
        this.geometry = geometry;
    }

    // -----------------------------------------------------------------------
    // Ensure methods — lazy build + GPU upload
    // -----------------------------------------------------------------------

    /**
     * Ensures background tilemap data is built and uploaded to the GPU renderer.
     *
     * @param blockLookup       block lookup function
     * @param zoneFeatureProvider zone feature provider (may be null)
     * @param currentZone       current zone index
     * @param parallaxManager   parallax manager (may be null)
     * @param verticalWrapEnabled whether vertical wrap is enabled
     */
    public void ensureBackgroundTilemapData(BlockLookup blockLookup,
                                            ZoneFeatureProvider zoneFeatureProvider,
                                            int currentZone,
                                            ParallaxManager parallaxManager,
                                            boolean verticalWrapEnabled) {
        if (!backgroundTilemapDirty && backgroundTilemapData != null && patternLookupData != null) {
            // Tilemap data already up to date — but still push VDP wrap height
            // to the renderer in case it was null during the initial build.
            TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
            if (renderer != null && backgroundVdpWrapHeightTiles > 0) {
                renderer.setBgVdpWrapHeight(backgroundVdpWrapHeightTiles);
            }
            return;
        }
        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return;
        }

        buildBackgroundTilemapData(blockLookup, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
        backgroundTilemapDirty = false;

        ensurePatternLookupData();
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer != null) {
            renderer.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND, backgroundTilemapData,
                    backgroundTilemapWidthTiles, backgroundTilemapHeightTiles);
            renderer.setBgVdpWrapHeight(backgroundVdpWrapHeightTiles);
            renderer.setPatternLookupData(patternLookupData, patternLookupSize);
        }
    }

    /**
     * Ensures foreground tilemap data is built and uploaded to the GPU renderer.
     *
     * @param blockLookup       block lookup function
     * @param zoneFeatureProvider zone feature provider (may be null)
     * @param currentZone       current zone index
     * @param parallaxManager   parallax manager (may be null)
     * @param verticalWrapEnabled whether vertical wrap is enabled
     */
    public void ensureForegroundTilemapData(BlockLookup blockLookup,
                                            ZoneFeatureProvider zoneFeatureProvider,
                                            int currentZone,
                                            ParallaxManager parallaxManager,
                                            boolean verticalWrapEnabled) {
        if (!foregroundTilemapDirty && foregroundTilemapData != null && patternLookupData != null) {
            return;
        }
        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return;
        }
        buildForegroundTilemapData(blockLookup, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
        foregroundTilemapDirty = false;
        ensurePatternLookupData();
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer != null) {
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, foregroundTilemapData,
                    foregroundTilemapWidthTiles, foregroundTilemapHeightTiles);
            renderer.setPatternLookupData(patternLookupData, patternLookupSize);
        }
    }

    /**
     * Ensures the pattern lookup table is built from the pattern atlas.
     */
    public void ensurePatternLookupData() {
        if (!patternLookupDirty && patternLookupData != null) {
            return;
        }
        Level level = geometry.level();
        if (level == null) {
            return;
        }
        int patternCount = level.getPatternCount();
        patternLookupSize = Math.max(1, patternCount);
        patternLookupData = new byte[patternLookupSize * 4];
        for (int i = 0; i < patternCount; i++) {
            PatternAtlas.Entry entry = graphicsManager.getPatternAtlasEntry(i);
            int offset = i * 4;
            if (entry != null) {
                patternLookupData[offset] = (byte) entry.tileX();
                patternLookupData[offset + 1] = (byte) entry.tileY();
                patternLookupData[offset + 2] = (byte) entry.atlasIndex();
                patternLookupData[offset + 3] = (byte) 255;
            } else {
                patternLookupData[offset] = 0;
                patternLookupData[offset + 1] = 0;
                patternLookupData[offset + 2] = 0;
                patternLookupData[offset + 3] = 0;
            }
        }
        PatternAtlas atlas = graphicsManager.getPatternAtlas();
        if (!multiAtlasWarningLogged && atlas != null && atlas.getAtlasCount() > 1) {
            LOGGER.warning("Pattern atlas overflow: using multiple atlases (count="
                    + atlas.getAtlasCount()
                    + ", slotsPerAtlas=" + atlas.getMaxSlotsPerAtlas()
                    + ", atlasSize=" + atlas.getAtlasWidth() + "x" + atlas.getAtlasHeight()
                    + ") for this level.");
            multiAtlasWarningLogged = true;
        }
        patternLookupDirty = false;
    }

    // -----------------------------------------------------------------------
    // Build methods
    // -----------------------------------------------------------------------

    private void buildBackgroundTilemapData(BlockLookup blockLookup,
                                            ZoneFeatureProvider zoneFeatureProvider,
                                            int currentZone,
                                            ParallaxManager parallaxManager,
                                            boolean verticalWrapEnabled) {
        TilemapData data = buildTilemapData((byte) 1, blockLookup, zoneFeatureProvider,
                currentZone, parallaxManager, verticalWrapEnabled);
        backgroundTilemapData = data.data;
        backgroundTilemapWidthTiles = data.widthTiles;
        backgroundTilemapHeightTiles = data.heightTiles;

        // For VDP wrap height detection, only scan the contiguous BG data region (columns 0-N).
        // HTZ has earthquake cave data at distant columns (54+, rows 48+) that must not
        // inflate the data height beyond 32 — the normal sky BG wraps at 32 rows.
        int scanWidthTiles = Math.min(backgroundTilemapWidthTiles,
                geometry.bgContiguousWidthPx() / Pattern.PATTERN_WIDTH);
        int actualHeightTiles = findActualBgTilemapDataHeight(backgroundTilemapData,
                backgroundTilemapWidthTiles, backgroundTilemapHeightTiles, scanWidthTiles);
        backgroundVdpWrapHeightTiles = (actualHeightTiles > 0
                && actualHeightTiles <= VDP_BG_PLANE_HEIGHT_TILES)
                ? VDP_BG_PLANE_HEIGHT_TILES : 0;
        LOGGER.fine("BG tilemap " + backgroundTilemapWidthTiles + "x" + backgroundTilemapHeightTiles
                + " actualDataHeight=" + actualHeightTiles
                + " VDPWrapHeight=" + backgroundVdpWrapHeightTiles);
    }

    /**
     * Scan the BG tilemap data bottom-up to find the last row containing
     * actual art (pattern index >= 2).  Pattern 0 is VDP-transparent and
     * pattern 1 is the default fill tile produced by block 0, so both are
     * excluded.  Real level art starts at pattern index 2+.
     * <p>
     * Only scans the first {@code scanWidthTiles} columns.  This excludes
     * distant earthquake columns (e.g., HTZ BG column 54+) from inflating
     * the detected height beyond the VDP plane size.
     * <p>
     * This lets us distinguish HTZ (real art in rows 0-31 only, fill beyond)
     * from MCZ (real art extending to row 85+).
     */
    private int findActualBgTilemapDataHeight(byte[] data, int widthTiles, int heightTiles,
            int scanWidthTiles) {
        int scanW = Math.min(scanWidthTiles, widthTiles);
        for (int y = heightTiles - 1; y >= 0; y--) {
            for (int x = 0; x < scanW; x++) {
                int offset = (y * widthTiles + x) * 4;
                int r = data[offset] & 0xFF;
                int g = data[offset + 1] & 0xFF;
                int patternIndex = r + ((g & 0x07) << 8);
                if (patternIndex >= 2) {
                    return y + 1;
                }
            }
        }
        return 0;
    }

    private void buildForegroundTilemapData(BlockLookup blockLookup,
                                            ZoneFeatureProvider zoneFeatureProvider,
                                            int currentZone,
                                            ParallaxManager parallaxManager,
                                            boolean verticalWrapEnabled) {
        TilemapData data = buildTilemapData((byte) 0, blockLookup, zoneFeatureProvider,
                currentZone, parallaxManager, verticalWrapEnabled);
        foregroundTilemapData = data.data;
        foregroundTilemapWidthTiles = data.widthTiles;
        foregroundTilemapHeightTiles = data.heightTiles;
    }

    private TilemapData buildTilemapData(byte layerIndex, BlockLookup blockLookup,
                                         ZoneFeatureProvider zoneFeatureProvider,
                                         int currentZone,
                                         ParallaxManager parallaxManager,
                                         boolean verticalWrapEnabled) {
        Level level = geometry.level();
        int blockPixelSize = geometry.blockPixelSize();
        int layerLevelWidth = getLayerLevelWidthPx(layerIndex);
        int levelHeight = getLayerLevelHeightPx(layerIndex);

        // Keep Sonic 2's 512px BG wrap behavior (VDP plane redraw model).
        // S3K uses a different background data flow in AIZ intro and needs full-width BG data.
        // HTZ earthquake needs full-width BG data because high-priority BG tiles (cave ceiling)
        // are rendered as a direct overlay between FG-low and FG-high passes, and they span the
        // full BG map. The FBO/parallax path still caps its period at 512px.
        boolean bgWrap = layerIndex == 1
                && zoneFeatureProvider != null
                && zoneFeatureProvider.bgWrapsHorizontally()
                && !GameServices.gameState().isHtzScreenShakeActive();
        // Use the scroll handler's required period width (may be wider than 512px
        // for zones with multi-speed parallax like GHZ).
        int bgPeriodWidth = parallaxManager != null ? parallaxManager.getBgPeriodWidth()
                : VDP_BG_PLANE_WIDTH_PX;
        int levelWidth = bgWrap ? bgPeriodWidth : layerLevelWidth;

        // For BG layers wider than 512px (e.g., SBZ 15360px), the 64-tile tilemap
        // must contain tiles from the correct BG map region, not always from position 0.
        // bgTilemapBaseX is the 16px-aligned offset into the BG map (matching the
        // BG camera X from the scroll handler). The shader uses this same offset
        // (via ScrollMidpoint → fboWorldOffsetX) to index into the tilemap correctly.
        // When using the 512px window, wrap the base offset at the contiguous BG data extent
        // so that large camera X positions map back to valid BG columns (not empty map regions).
        int bgXQueryOffset = 0;
        int bgContiguousWidthPx = geometry.bgContiguousWidthPx();
        if (layerIndex == 1 && bgWrap && bgContiguousWidthPx > 0) {
            bgXQueryOffset = ((bgTilemapBaseX % bgContiguousWidthPx) + bgContiguousWidthPx)
                    % bgContiguousWidthPx;
        }

        int widthTiles = levelWidth / Pattern.PATTERN_WIDTH;
        int heightTiles = levelHeight / Pattern.PATTERN_HEIGHT;
        byte[] data = new byte[widthTiles * heightTiles * 4];

        int chunkWidth = LevelConstants.CHUNK_WIDTH;
        int chunkHeight = LevelConstants.CHUNK_HEIGHT;

        for (int y = 0; y < levelHeight; y += chunkHeight) {
            int chunkY = y / chunkHeight;
            for (int x = 0; x < levelWidth; x += chunkWidth) {
                int chunkX = x / chunkWidth;

                // Query the BG map at the offset position (wrapping handled by blockLookup)
                int queryX = x + bgXQueryOffset;
                Block block = blockLookup.lookup(layerIndex, queryX, y);
                if (block == null) {
                    writeEmptyChunk(data, widthTiles, heightTiles, chunkX, chunkY);
                    continue;
                }

                // xBlockBit uses the query position to select the correct chunk within the block.
                int xBlockBit = (queryX % blockPixelSize) / chunkWidth;
                int yBlockBit = (y % blockPixelSize) / chunkHeight;
                ChunkDesc chunkDesc = block.getChunkDesc(xBlockBit, yBlockBit);
                int chunkIndex = chunkDesc.getChunkIndex();

                if (chunkIndex < 0 || chunkIndex >= level.getChunkCount()) {
                    writeEmptyChunk(data, widthTiles, heightTiles, chunkX, chunkY);
                    continue;
                }

                Chunk chunk = level.getChunk(chunkIndex);
                if (chunk == null) {
                    writeEmptyChunk(data, widthTiles, heightTiles, chunkX, chunkY);
                    continue;
                }

                boolean chunkHFlip = chunkDesc.getHFlip();
                boolean chunkVFlip = chunkDesc.getVFlip();

                for (int cY = 0; cY < 2; cY++) {
                    for (int cX = 0; cX < 2; cX++) {
                        int logicalX = chunkHFlip ? 1 - cX : cX;
                        int logicalY = chunkVFlip ? 1 - cY : cY;

                        PatternDesc patternDesc = chunk.getPatternDesc(logicalX, logicalY);
                        int newIndex = patternDesc.get();
                        if (chunkHFlip) {
                            newIndex ^= 0x800;
                        }
                        if (chunkVFlip) {
                            newIndex ^= 0x1000;
                        }
                        reusablePatternDesc.set(newIndex);

                        int tileX = chunkX * 2 + cX;
                        int tileY = chunkY * 2 + cY;
                        writeTileDescriptor(data, widthTiles, heightTiles, tileX, tileY, reusablePatternDesc);
                    }
                }
            }
        }

        return new TilemapData(data, widthTiles, heightTiles);
    }

    // -----------------------------------------------------------------------
    // Tile write helpers
    // -----------------------------------------------------------------------

    private void writeEmptyChunk(byte[] data, int widthTiles, int heightTiles, int chunkX, int chunkY) {
        for (int cY = 0; cY < 2; cY++) {
            for (int cX = 0; cX < 2; cX++) {
                int tileX = chunkX * 2 + cX;
                int tileY = chunkY * 2 + cY;
                writeEmptyTile(data, widthTiles, heightTiles, tileX, tileY);
            }
        }
    }

    private void writeEmptyTile(byte[] data, int widthTiles, int heightTiles, int tileX, int tileY) {
        if (tileX < 0 || tileY < 0 || tileX >= widthTiles
                || tileY >= heightTiles) {
            return;
        }
        int offset = (tileY * widthTiles + tileX) * 4;
        data[offset] = 0;
        data[offset + 1] = 0;
        data[offset + 2] = 0;
        data[offset + 3] = 0;
    }

    private void writeTileDescriptor(byte[] data, int widthTiles, int heightTiles, int tileX, int tileY,
            PatternDesc desc) {
        if (tileX < 0 || tileY < 0 || tileX >= widthTiles || tileY >= heightTiles) {
            return;
        }
        int offset = (tileY * widthTiles + tileX) * 4;
        int patternIndex = desc.getPatternIndex();
        int paletteIndex = desc.getPaletteIndex();
        boolean hFlip = desc.getHFlip();
        boolean vFlip = desc.getVFlip();
        boolean priority = desc.getPriority();

        int r = patternIndex & 0xFF;
        int g = ((patternIndex >> 8) & 0x7)
                | ((paletteIndex & 0x3) << 3)
                | (hFlip ? 0x20 : 0)
                | (vFlip ? 0x40 : 0)
                | (priority ? 0x80 : 0);

        data[offset] = (byte) r;
        data[offset + 1] = (byte) g;
        data[offset + 2] = 0;
        data[offset + 3] = (byte) 255;
    }

    // -----------------------------------------------------------------------
    // Invalidation
    // -----------------------------------------------------------------------

    /**
     * Marks the foreground tilemap as dirty, forcing a rebuild on next render.
     * Call this after modifying the level layout (e.g., placing boss arena walls).
     * This is equivalent to setting Screen_redraw_flag in the original ROM.
     */
    public void invalidateForegroundTilemap() {
        foregroundTilemapDirty = true;
    }

    /**
     * Marks background/foreground tilemaps and pattern lookup as dirty.
     * Use this after runtime terrain art/chunk overlays so the GPU tilemap
     * data is rebuilt on the next render.
     */
    public void invalidateAllTilemaps() {
        backgroundTilemapDirty = true;
        foregroundTilemapDirty = true;
        patternLookupDirty = true;
    }

    // -----------------------------------------------------------------------
    // Upload
    // -----------------------------------------------------------------------

    /**
     * Uploads the current foreground tilemap bytes to the GPU renderer (if active).
     * No-op in headless mode.
     */
    public void uploadForegroundTilemap() {
        if (foregroundTilemapData == null) {
            return;
        }
        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer == null) {
            return;
        }
        ensurePatternLookupData();
        renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND, foregroundTilemapData,
                foregroundTilemapWidthTiles, foregroundTilemapHeightTiles);
        renderer.setPatternLookupData(patternLookupData, patternLookupSize);
    }

    // -----------------------------------------------------------------------
    // Foreground tile descriptor access (world-coordinate tilemap writes)
    // -----------------------------------------------------------------------

    /**
     * Overwrites one foreground tile descriptor at world coordinates in the live FG tilemap buffer.
     * Call {@link #uploadForegroundTilemap()} once after batching writes.
     *
     * @param blockLookup       block lookup function (for ensureForegroundTilemapData)
     * @param zoneFeatureProvider zone feature provider (may be null)
     * @param currentZone       current zone index
     * @param parallaxManager   parallax manager (may be null)
     * @param verticalWrapEnabled whether vertical wrap is enabled
     * @return true if tilemap bytes changed
     */
    public boolean setForegroundTileDescriptorAtWorld(int worldX, int worldY, int descriptor,
                                                      BlockLookup blockLookup,
                                                      ZoneFeatureProvider zoneFeatureProvider,
                                                      int currentZone,
                                                      ParallaxManager parallaxManager,
                                                      boolean verticalWrapEnabled) {
        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return false;
        }

        ensureForegroundTilemapData(blockLookup, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
        if (foregroundTilemapData == null) {
            return false;
        }

        int levelWidth = getLayerLevelWidthPx((byte) 0);
        int levelHeight = getLayerLevelHeightPx((byte) 0);
        if (levelWidth <= 0 || levelHeight <= 0) {
            return false;
        }

        int wrappedX = Math.floorMod(worldX, levelWidth);
        int wrappedY = worldY;
        if (verticalWrapEnabled) {
            wrappedY = Math.floorMod(worldY, levelHeight);
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return false;
        }

        int tileX = wrappedX / Pattern.PATTERN_WIDTH;
        int tileY = wrappedY / Pattern.PATTERN_HEIGHT;
        if (tileX < 0 || tileY < 0
                || tileX >= foregroundTilemapWidthTiles
                || tileY >= foregroundTilemapHeightTiles) {
            return false;
        }

        int offset = (tileY * foregroundTilemapWidthTiles + tileX) * 4;
        int patternIndex = descriptor & 0x7FF;
        int paletteIndex = (descriptor >> 13) & 0x3;
        int g = ((patternIndex >> 8) & 0x7)
                | ((paletteIndex & 0x3) << 3)
                | ((descriptor & 0x800) != 0 ? 0x20 : 0)
                | ((descriptor & 0x1000) != 0 ? 0x40 : 0)
                | ((descriptor & 0x8000) != 0 ? 0x80 : 0);
        byte rByte = (byte) (patternIndex & 0xFF);
        byte gByte = (byte) g;

        if (foregroundTilemapData[offset] == rByte
                && foregroundTilemapData[offset + 1] == gByte
                && foregroundTilemapData[offset + 2] == 0
                && foregroundTilemapData[offset + 3] == (byte) 0xFF) {
            return false;
        }

        foregroundTilemapData[offset] = rByte;
        foregroundTilemapData[offset + 1] = gByte;
        foregroundTilemapData[offset + 2] = 0;
        foregroundTilemapData[offset + 3] = (byte) 0xFF;
        return true;
    }

    /**
     * Reads a foreground tile descriptor from the live foreground tilemap buffer at world coordinates.
     * Unlike level-data-based descriptor reads, this returns the currently visible
     * descriptor after runtime tilemap writes.
     *
     * @param blockLookup       block lookup function (for ensureForegroundTilemapData)
     * @param zoneFeatureProvider zone feature provider (may be null)
     * @param currentZone       current zone index
     * @param parallaxManager   parallax manager (may be null)
     * @param verticalWrapEnabled whether vertical wrap is enabled
     */
    public int getForegroundTileDescriptorFromTilemapAtWorld(int worldX, int worldY,
                                                             BlockLookup blockLookup,
                                                             ZoneFeatureProvider zoneFeatureProvider,
                                                             int currentZone,
                                                             ParallaxManager parallaxManager,
                                                             boolean verticalWrapEnabled) {
        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return 0;
        }

        ensureForegroundTilemapData(blockLookup, zoneFeatureProvider, currentZone,
                parallaxManager, verticalWrapEnabled);
        if (foregroundTilemapData == null) {
            return 0;
        }

        int levelWidth = getLayerLevelWidthPx((byte) 0);
        int levelHeight = getLayerLevelHeightPx((byte) 0);
        if (levelWidth <= 0 || levelHeight <= 0) {
            return 0;
        }

        int wrappedX = Math.floorMod(worldX, levelWidth);
        int wrappedY = worldY;
        if (verticalWrapEnabled) {
            wrappedY = Math.floorMod(worldY, levelHeight);
        } else if (wrappedY < 0 || wrappedY >= levelHeight) {
            return 0;
        }

        int tileX = wrappedX / Pattern.PATTERN_WIDTH;
        int tileY = wrappedY / Pattern.PATTERN_HEIGHT;
        if (tileX < 0 || tileY < 0
                || tileX >= foregroundTilemapWidthTiles
                || tileY >= foregroundTilemapHeightTiles) {
            return 0;
        }

        int offset = (tileY * foregroundTilemapWidthTiles + tileX) * 4;
        int r = foregroundTilemapData[offset] & 0xFF;
        int g = foregroundTilemapData[offset + 1] & 0xFF;
        int patternIndex = r | ((g & 0x7) << 8);
        int paletteIdx = (g >> 3) & 0x3;

        int desc = patternIndex | (paletteIdx << 13);
        if ((g & 0x20) != 0) {
            desc |= 0x800;
        }
        if ((g & 0x40) != 0) {
            desc |= 0x1000;
        }
        if ((g & 0x80) != 0) {
            desc |= 0x8000;
        }
        return desc & 0xFFFF;
    }

    // -----------------------------------------------------------------------
    // Prebuilt transition tilemaps
    // -----------------------------------------------------------------------

    /**
     * Pre-builds FG and BG tilemap data from the current level state.
     * The pre-built data can later be swapped in via {@link #swapToPrebuiltTilemaps()}
     * to avoid the expensive full-level tilemap rebuild on the transition frame.
     */
    public void prebuildTransitionTilemaps(BlockLookup blockLookup,
                                           ZoneFeatureProvider zoneFeatureProvider,
                                           int currentZone,
                                           ParallaxManager parallaxManager,
                                           boolean verticalWrapEnabled) {
        Level level = geometry.level();
        if (level == null || level.getMap() == null) {
            return;
        }
        TilemapData fg = buildTilemapData((byte) 0, blockLookup, zoneFeatureProvider,
                currentZone, parallaxManager, verticalWrapEnabled);
        prebuiltFgTilemap = fg.data.clone();
        prebuiltFgWidth = fg.widthTiles;
        prebuiltFgHeight = fg.heightTiles;

        TilemapData bg = buildTilemapData((byte) 1, blockLookup, zoneFeatureProvider,
                currentZone, parallaxManager, verticalWrapEnabled);
        prebuiltBgTilemap = bg.data.clone();
        prebuiltBgWidth = bg.widthTiles;
        prebuiltBgHeight = bg.heightTiles;
    }

    /**
     * Swaps pre-built tilemap data into the live arrays, uploads to GPU,
     * and clears FG/BG dirty flags. Still marks pattern lookup dirty
     * (cheap rebuild, needed if pattern count changed from the overlay).
     *
     * @return true if pre-built data was available and swapped in
     */
    public boolean swapToPrebuiltTilemaps() {
        if (prebuiltFgTilemap == null || prebuiltBgTilemap == null) {
            return false;
        }

        foregroundTilemapData = prebuiltFgTilemap;
        foregroundTilemapWidthTiles = prebuiltFgWidth;
        foregroundTilemapHeightTiles = prebuiltFgHeight;
        foregroundTilemapDirty = false;

        backgroundTilemapData = prebuiltBgTilemap;
        backgroundTilemapWidthTiles = prebuiltBgWidth;
        backgroundTilemapHeightTiles = prebuiltBgHeight;
        backgroundTilemapDirty = false;

        patternLookupDirty = true;

        TilemapGpuRenderer renderer = graphicsManager.getTilemapGpuRenderer();
        if (renderer != null) {
            ensurePatternLookupData();
            renderer.setTilemapData(TilemapGpuRenderer.Layer.FOREGROUND,
                    foregroundTilemapData, foregroundTilemapWidthTiles, foregroundTilemapHeightTiles);
            renderer.setTilemapData(TilemapGpuRenderer.Layer.BACKGROUND,
                    backgroundTilemapData, backgroundTilemapWidthTiles, backgroundTilemapHeightTiles);
            renderer.setPatternLookupData(patternLookupData, patternLookupSize);
        }

        // Release pre-built data (one-shot use)
        prebuiltFgTilemap = null;
        prebuiltBgTilemap = null;
        return true;
    }

    /**
     * Returns whether pre-built transition tilemap data is available.
     */
    public boolean hasPrebuiltTilemaps() {
        return prebuiltFgTilemap != null && prebuiltBgTilemap != null;
    }

    // -----------------------------------------------------------------------
    // State reset
    // -----------------------------------------------------------------------

    /**
     * Clears all tilemap arrays, dirty flags, and prebuilt data.
     */
    public void resetState() {
        backgroundTilemapData = null;
        foregroundTilemapData = null;
        patternLookupData = null;
        prebuiltFgTilemap = null;
        prebuiltBgTilemap = null;
        backgroundTilemapDirty = true;
        bgTilemapBaseX = 0;
        currentBgPeriodWidth = VDP_BG_PLANE_WIDTH_PX;
        foregroundTilemapDirty = true;
        patternLookupDirty = true;
        multiAtlasWarningLogged = false;
    }

    // -----------------------------------------------------------------------
    // Getters (used by LevelManager's pre-allocated GL command lambdas)
    // -----------------------------------------------------------------------

    public byte[] getBackgroundTilemapData() {
        return backgroundTilemapData;
    }

    public int getBackgroundTilemapWidthTiles() {
        return backgroundTilemapWidthTiles;
    }

    public int getBackgroundTilemapHeightTiles() {
        return backgroundTilemapHeightTiles;
    }

    public int getBackgroundVdpWrapHeightTiles() {
        return backgroundVdpWrapHeightTiles;
    }

    public byte[] getForegroundTilemapData() {
        return foregroundTilemapData;
    }

    public int getForegroundTilemapWidthTiles() {
        return foregroundTilemapWidthTiles;
    }

    public int getForegroundTilemapHeightTiles() {
        return foregroundTilemapHeightTiles;
    }

    public byte[] getPatternLookupData() {
        return patternLookupData;
    }

    public int getPatternLookupSize() {
        return patternLookupSize;
    }

    public int getBgTilemapBaseX() {
        return bgTilemapBaseX;
    }

    public void setBgTilemapBaseX(int bgTilemapBaseX) {
        this.bgTilemapBaseX = bgTilemapBaseX;
    }

    public int getCurrentBgPeriodWidth() {
        return currentBgPeriodWidth;
    }

    public void setCurrentBgPeriodWidth(int currentBgPeriodWidth) {
        this.currentBgPeriodWidth = currentBgPeriodWidth;
    }

    public boolean isBackgroundTilemapDirty() {
        return backgroundTilemapDirty;
    }

    public void setBackgroundTilemapDirty(boolean dirty) {
        this.backgroundTilemapDirty = dirty;
    }

    public boolean isForegroundTilemapDirty() {
        return foregroundTilemapDirty;
    }

    public void setForegroundTilemapDirty(boolean dirty) {
        this.foregroundTilemapDirty = dirty;
    }

    public void setPatternLookupDirty(boolean dirty) {
        this.patternLookupDirty = dirty;
    }

    public void setMultiAtlasWarningLogged(boolean logged) {
        this.multiAtlasWarningLogged = logged;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private int getLayerLevelWidthPx(byte layer) {
        Level level = geometry.level();
        if (level == null) {
            return geometry.blockPixelSize();
        }
        int widthBlocks = Math.max(1, level.getLayerWidthBlocks(layer));
        return widthBlocks * geometry.blockPixelSize();
    }

    private int getLayerLevelHeightPx(byte layer) {
        Level level = geometry.level();
        if (level == null) {
            return geometry.blockPixelSize();
        }
        int heightBlocks = Math.max(1, level.getLayerHeightBlocks(layer));
        return heightBlocks * geometry.blockPixelSize();
    }
}
