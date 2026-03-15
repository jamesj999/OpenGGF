package com.openggf.game.sonic3k.features;

import com.openggf.camera.Camera;
import com.openggf.game.sonic3k.events.FireCurtainRenderState;
import com.openggf.game.sonic3k.events.FireCurtainStage;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.PatternDesc;
import com.openggf.level.LevelManager;

import java.util.ArrayList;
import java.util.List;

final class AizFireCurtainRenderer {
    private static final int COLUMN_COUNT = 20;
    private static final int TILE_SIZE = 8;
    private static final int EMPTY_DESCRIPTOR = 0;
    private static final int PALETTE_MASK = 0x3 << 13;
    private static final int FIRE_PALETTE_INDEX = 3;
    /** BG Y coordinate where fire tiles begin in the AIZ BG layout. */
    private static final int FIRE_TILE_START_BG_Y = 0x100;
    /** BG Y coordinate where fire tiles end (exclusive). */
    private static final int FIRE_TILE_END_BG_Y = 0x310;
    /** Number of BG tile rows in the fire zone. */
    private static final int FIRE_ZONE_ROWS = (FIRE_TILE_END_BG_Y - FIRE_TILE_START_BG_Y) / TILE_SIZE;
    /** Screen-width tile columns (320 / 8). */
    private static final int SCREEN_TILE_COLS = 40;
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(AizFireCurtainRenderer.class.getName());

    private final PatternDesc reusableDesc = new PatternDesc();
    private final TileDescriptorSampler sampler;

    /**
     * Cached fire tile descriptors sampled from the BG layout during RISING
     * (before mutation overwrites the BG data).  Indexed as
     * {@code [fireZoneRow][screenTileCol]} where fireZoneRow =
     * (bgTileY - FIRE_TILE_START_BG_Y) / TILE_SIZE.
     */
    private int[][] cachedFireDescriptors;
    private boolean fireDescriptorsCached;
    AizFireCurtainRenderer() {
        this(null);
    }

    AizFireCurtainRenderer(TileDescriptorSampler sampler) {
        this.sampler = sampler;
    }

    void reset() {
        fireDescriptorsCached = false;
        cachedFireDescriptors = null;
    }

    void render(Camera camera, FireCurtainRenderState state, int screenWidth, int screenHeight) {
        if (camera == null || state == null || !state.active() || state.coverHeightPx() <= 0) {
            return;
        }

        CurtainCompositionPlan plan = buildCompositionPlan(state, screenWidth, screenHeight);
        if (plan.columns().isEmpty()) {
            return;
        }

        GraphicsManager graphicsManager = GraphicsManager.getInstance();
        int cameraX = camera.getX();
        int cameraY = camera.getY();
        for (ColumnRenderPlan column : plan.columns()) {
            for (TileDraw draw : column.draws()) {
                reusableDesc.set(draw.descriptor());
                graphicsManager.renderPatternWithId(draw.renderPatternId(),
                        reusableDesc,
                        cameraX + draw.screenX(),
                        cameraY + draw.screenY());
            }
        }
    }

    CurtainCompositionPlan buildCompositionPlan(FireCurtainRenderState state, int screenWidth, int screenHeight) {
        if (state == null || !state.active() || state.coverHeightPx() <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return new CurtainCompositionPlan(screenWidth, screenHeight, List.of());
        }
        // During RISING, sample from the BG level layout (fire tiles are in-place)
        // and cache the descriptors for use after mutation.
        if (state.stage() == FireCurtainStage.AIZ1_RISING) {
            if (sampler != null) {
                return buildSampledPlan(state, screenWidth, screenHeight);
            }
            CurtainCompositionPlan bgPlan = buildBackgroundSampledPlan(state, screenWidth, screenHeight);
            if (bgPlan.columns().isEmpty() && state.fireOverlayTileCount() > 0) {
                return buildFireOverlayTilePlan(state, screenWidth, screenHeight);
            }
            return bgPlan;
        }
        // Post-mutation: use cached BG descriptors if available (preserves real
        // fire tile layout across the mutation).  Falls back to synthetic overlay
        // tiles if the cache wasn't populated during RISING.
        if (fireDescriptorsCached) {
            return buildCachedPlan(state, screenWidth, screenHeight);
        }
        if (state.fireOverlayTileCount() > 0) {
            return buildFireOverlayTilePlan(state, screenWidth, screenHeight);
        }
        if (sampler != null) {
            return buildSampledPlan(state, screenWidth, screenHeight);
        }
        return buildBackgroundSampledPlan(state, screenWidth, screenHeight);
    }

    private CurtainCompositionPlan buildSampledPlan(FireCurtainRenderState state, int screenWidth, int screenHeight) {
        int[] columnWaveOffsets = state.columnWaveOffsetsPx();
        List<ColumnRenderPlan> columns = new ArrayList<>(COLUMN_COUNT);
        int baseTop = clamp(screenHeight - state.coverHeightPx(), 0, screenHeight);
        int bgY = state.sourceWorldY();

        for (int columnIndex = 0; columnIndex < COLUMN_COUNT; columnIndex++) {
            int columnLeft = (columnIndex * screenWidth) / COLUMN_COUNT;
            int columnRight = ((columnIndex + 1) * screenWidth) / COLUMN_COUNT;
            int columnWidth = Math.max(1, columnRight - columnLeft);
            int waveOffset = columnIndex < columnWaveOffsets.length ? columnWaveOffsets[columnIndex] : 0;
            int clipTop = clamp(baseTop + waveOffset, 0, screenHeight);
            if (clipTop >= screenHeight) {
                continue;
            }

            int columnVScroll = bgY + waveOffset;
            int bgAtBottom = columnVScroll + screenHeight;
            int bgAtClipTop = columnVScroll + clipTop;
            int bgRowBottom = Math.floorDiv(bgAtBottom - 1, TILE_SIZE);
            int bgRowTop = Math.floorDiv(bgAtClipTop - TILE_SIZE, TILE_SIZE);

            List<TileDraw> draws = new ArrayList<>();
            int subColumns = Math.max(1, (columnWidth + TILE_SIZE - 1) / TILE_SIZE);
            for (int bgRow = bgRowBottom; bgRow >= bgRowTop; bgRow--) {
                int bgTileY = bgRow * TILE_SIZE;
                int drawY = bgTileY - columnVScroll;
                if (drawY >= screenHeight || drawY + TILE_SIZE <= clipTop) {
                    continue;
                }
                for (int subColumn = 0; subColumn < subColumns; subColumn++) {
                    int drawX = columnLeft + subColumn * TILE_SIZE;
                    if (drawX >= columnRight) {
                        continue;
                    }
                    int sourceX = state.sourceWorldX() + drawX;
                    int descriptor = sampler.sample(sourceX, bgTileY);
                    if ((descriptor & 0x7FF) == EMPTY_DESCRIPTOR) {
                        continue;
                    }
                    int patternIndex = descriptor & 0x7FF;
                    draws.add(new TileDraw(forceFirePalette(descriptor), patternIndex, drawX, drawY));
                }
            }
            if (!draws.isEmpty()) {
                columns.add(new ColumnRenderPlan(columnIndex, columnLeft, columnWidth, clipTop, screenHeight, draws));
            }
        }
        return new CurtainCompositionPlan(screenWidth, screenHeight, columns);
    }

    /**
     * Samples the BG level layout to build the fire curtain plan.
     * Also populates the fire descriptor cache for use after mutation.
     */
    private CurtainCompositionPlan buildBackgroundSampledPlan(FireCurtainRenderState state, int screenWidth, int screenHeight) {
        int[] columnWaveOffsets = state.columnWaveOffsetsPx();
        List<ColumnRenderPlan> columns = new ArrayList<>(COLUMN_COUNT);
        int baseTop = clamp(screenHeight - state.coverHeightPx(), 0, screenHeight);
        int bgY = state.sourceWorldY();
        int tileBase = state.fireOverlayTileBase();
        int tileCount = state.fireOverlayTileCount();

        // Populate cache on first call with valid fire overlay tiles.
        if (!fireDescriptorsCached && tileCount > 0) {
            populateFireDescriptorCache(state.sourceWorldX(), tileBase, tileCount);
        }

        for (int columnIndex = 0; columnIndex < COLUMN_COUNT; columnIndex++) {
            int columnLeft = (columnIndex * screenWidth) / COLUMN_COUNT;
            int columnRight = ((columnIndex + 1) * screenWidth) / COLUMN_COUNT;
            int columnWidth = Math.max(1, columnRight - columnLeft);
            int waveOffset = columnIndex < columnWaveOffsets.length ? columnWaveOffsets[columnIndex] : 0;
            int clipTop = clamp(baseTop + waveOffset, 0, screenHeight);
            if (clipTop >= screenHeight) {
                continue;
            }

            int columnVScroll = bgY + waveOffset;
            int bgAtBottom = columnVScroll + screenHeight;
            int bgAtClipTop = columnVScroll + clipTop;
            int bgRowBottom = Math.floorDiv(bgAtBottom - 1, TILE_SIZE);
            int bgRowTop = Math.floorDiv(bgAtClipTop - TILE_SIZE, TILE_SIZE);

            List<TileDraw> draws = new ArrayList<>();
            int subColumns = Math.max(1, (columnWidth + TILE_SIZE - 1) / TILE_SIZE);
            for (int bgRow = bgRowBottom; bgRow >= bgRowTop; bgRow--) {
                int bgTileY = bgRow * TILE_SIZE;
                int drawY = bgTileY - columnVScroll;
                if (drawY >= screenHeight || drawY + TILE_SIZE <= clipTop) {
                    continue;
                }
                if (bgTileY < FIRE_TILE_START_BG_Y || bgTileY >= FIRE_TILE_END_BG_Y) {
                    continue;
                }
                for (int subColumn = 0; subColumn < subColumns; subColumn++) {
                    int drawX = columnLeft + subColumn * TILE_SIZE;
                    if (drawX >= columnRight) {
                        continue;
                    }
                    int sourceX = state.sourceWorldX() + drawX;
                    int descriptor = sampleBackgroundStripDescriptor(sourceX, bgTileY);
                    int patternIndex = descriptor & 0x7FF;

                    // Skip empty tiles — fire zone boundary rows contain
                    // transparent buffer tiles (pattern=0) that the ROM
                    // renders as invisible via VDP priority.  Drawing a
                    // synthetic fire tile here creates a garbage row.
                    if (patternIndex == EMPTY_DESCRIPTOR) {
                        continue;
                    }
                    if (patternIndex >= tileBase && patternIndex < tileBase + tileCount) {
                        draws.add(new TileDraw(forceFirePalette(descriptor), patternIndex, drawX, drawY));
                    } else if (tileCount > 0) {
                        int fallbackIdx = ((drawX / TILE_SIZE) + (bgRow & 0x7F)) % tileCount;
                        int fallbackPattern = tileBase + fallbackIdx;
                        int fallbackDesc = (FIRE_PALETTE_INDEX << 13) | (fallbackPattern & 0x7FF);
                        draws.add(new TileDraw(fallbackDesc, fallbackPattern, drawX, drawY));
                    }
                }
            }
            if (!draws.isEmpty()) {
                columns.add(new ColumnRenderPlan(columnIndex, columnLeft, columnWidth, clipTop, screenHeight, draws));
            }
        }

        return new CurtainCompositionPlan(screenWidth, screenHeight, columns);
    }

    /**
     * Populates the fire descriptor cache by sampling the entire fire zone
     * from the BG layout at a fixed X offset.  Called once during RISING
     * before the mutation overwrites the BG data.
     */
    private void populateFireDescriptorCache(int sourceWorldX, int tileBase, int tileCount) {
        cachedFireDescriptors = new int[FIRE_ZONE_ROWS][SCREEN_TILE_COLS];
        for (int row = 0; row < FIRE_ZONE_ROWS; row++) {
            int bgTileY = FIRE_TILE_START_BG_Y + row * TILE_SIZE;
            for (int col = 0; col < SCREEN_TILE_COLS; col++) {
                int worldX = sourceWorldX + col * TILE_SIZE;
                int descriptor = sampleBackgroundStripDescriptor(worldX, bgTileY);
                int patternIndex = descriptor & 0x7FF;
                // Leave empty tiles as 0 in the cache — they are transparent
                // buffer rows that must not become synthetic fire tiles.
                if (patternIndex == EMPTY_DESCRIPTOR) {
                    continue;
                }
                if (patternIndex >= tileBase && patternIndex < tileBase + tileCount) {
                    cachedFireDescriptors[row][col] = forceFirePalette(descriptor);
                } else if (tileCount > 0) {
                    int fallbackIdx = (col + (row & 0x7F)) % tileCount;
                    int fallbackPattern = tileBase + fallbackIdx;
                    cachedFireDescriptors[row][col] = (FIRE_PALETTE_INDEX << 13) | (fallbackPattern & 0x7FF);
                }
            }
        }
        fireDescriptorsCached = true;
        LOG.info("Cached fire tile descriptors: " + FIRE_ZONE_ROWS + " rows × " + SCREEN_TILE_COLS + " cols");
    }

    /**
     * Builds a fire curtain plan from cached BG descriptors.
     * Used after mutation when the BG has been overwritten with AIZ2 data
     * but the VRAM fire tiles (0x500+) still exist.
     */
    private CurtainCompositionPlan buildCachedPlan(FireCurtainRenderState state,
                                                    int screenWidth, int screenHeight) {
        int[] columnWaveOffsets = state.columnWaveOffsetsPx();
        int bgY = state.sourceWorldY();
        List<ColumnRenderPlan> columns = new ArrayList<>(COLUMN_COUNT);
        int baseTop = clamp(screenHeight - state.coverHeightPx(), 0, screenHeight);

        for (int columnIndex = 0; columnIndex < COLUMN_COUNT; columnIndex++) {
            int columnLeft = (columnIndex * screenWidth) / COLUMN_COUNT;
            int columnRight = ((columnIndex + 1) * screenWidth) / COLUMN_COUNT;
            int columnWidth = Math.max(1, columnRight - columnLeft);
            int waveOffset = columnIndex < columnWaveOffsets.length ? columnWaveOffsets[columnIndex] : 0;
            int clipTop = clamp(baseTop + waveOffset, 0, screenHeight);
            if (clipTop >= screenHeight) {
                continue;
            }

            int columnVScroll = bgY + waveOffset;
            int bgAtBottom = columnVScroll + screenHeight;
            int bgAtClipTop = columnVScroll + clipTop;
            int bgRowBottom = Math.floorDiv(bgAtBottom - 1, TILE_SIZE);
            int bgRowTop = Math.floorDiv(bgAtClipTop - TILE_SIZE, TILE_SIZE);

            List<TileDraw> draws = new ArrayList<>();
            int subColumns = Math.max(1, (columnWidth + TILE_SIZE - 1) / TILE_SIZE);
            for (int bgRow = bgRowBottom; bgRow >= bgRowTop; bgRow--) {
                int bgTileY = bgRow * TILE_SIZE;
                if (bgTileY < FIRE_TILE_START_BG_Y || bgTileY >= FIRE_TILE_END_BG_Y) {
                    continue;
                }
                int fireRow = (bgTileY - FIRE_TILE_START_BG_Y) / TILE_SIZE;
                int drawY = bgTileY - columnVScroll;
                if (drawY >= screenHeight || drawY + TILE_SIZE <= clipTop) {
                    continue;
                }
                for (int subColumn = 0; subColumn < subColumns; subColumn++) {
                    int drawX = columnLeft + subColumn * TILE_SIZE;
                    if (drawX >= columnRight) {
                        continue;
                    }
                    int cacheCol = drawX / TILE_SIZE;
                    if (fireRow < 0 || fireRow >= FIRE_ZONE_ROWS
                            || cacheCol < 0 || cacheCol >= SCREEN_TILE_COLS) {
                        continue;
                    }
                    int descriptor = cachedFireDescriptors[fireRow][cacheCol];
                    if ((descriptor & 0x7FF) == EMPTY_DESCRIPTOR) {
                        continue;
                    }
                    int patternIndex = descriptor & 0x7FF;
                    draws.add(new TileDraw(descriptor, patternIndex, drawX, drawY));
                }
            }
            if (!draws.isEmpty()) {
                columns.add(new ColumnRenderPlan(columnIndex, columnLeft, columnWidth, clipTop, screenHeight, draws));
            }
        }
        return new CurtainCompositionPlan(screenWidth, screenHeight, columns);
    }

    /**
     * Builds a fire curtain plan using the fire overlay tiles directly.
     * Used as a fallback when cached descriptors aren't available.
     */
    private CurtainCompositionPlan buildFireOverlayTilePlan(FireCurtainRenderState state,
                                                             int screenWidth, int screenHeight) {
        int tileBase = state.fireOverlayTileBase();
        int tileCount = state.fireOverlayTileCount();
        if (tileCount <= 0) {
            return new CurtainCompositionPlan(screenWidth, screenHeight, List.of());
        }

        int[] columnWaveOffsets = state.columnWaveOffsetsPx();
        int bgY = state.sourceWorldY();
        List<ColumnRenderPlan> columns = new ArrayList<>(COLUMN_COUNT);
        int baseTop = clamp(screenHeight - state.coverHeightPx(), 0, screenHeight);

        for (int columnIndex = 0; columnIndex < COLUMN_COUNT; columnIndex++) {
            int columnLeft = (columnIndex * screenWidth) / COLUMN_COUNT;
            int columnRight = ((columnIndex + 1) * screenWidth) / COLUMN_COUNT;
            int columnWidth = Math.max(1, columnRight - columnLeft);
            int waveOffset = columnIndex < columnWaveOffsets.length ? columnWaveOffsets[columnIndex] : 0;
            int clipTop = clamp(baseTop + waveOffset, 0, screenHeight);
            if (clipTop >= screenHeight) {
                continue;
            }

            int columnVScroll = bgY + waveOffset;
            int bgAtBottom = columnVScroll + screenHeight;
            int bgAtClipTop = columnVScroll + clipTop;
            int bgRowBottom = Math.floorDiv(bgAtBottom - 1, TILE_SIZE);
            int bgRowTop = Math.floorDiv(bgAtClipTop - TILE_SIZE, TILE_SIZE);

            List<TileDraw> draws = new ArrayList<>();
            int subColumns = Math.max(1, (columnWidth + TILE_SIZE - 1) / TILE_SIZE);
            for (int bgRow = bgRowBottom; bgRow >= bgRowTop; bgRow--) {
                int bgTileY = bgRow * TILE_SIZE;
                // Only skip tiles BELOW the fire zone start; no upper bound
                // so synthetic fire tiles extend indefinitely as BG scrolls.
                if (bgTileY < FIRE_TILE_START_BG_Y) {
                    continue;
                }
                int drawY = bgTileY - columnVScroll;
                if (drawY >= screenHeight || drawY + TILE_SIZE <= clipTop) {
                    continue;
                }
                int bgRowIndex = bgTileY / TILE_SIZE;
                for (int subColumn = 0; subColumn < subColumns; subColumn++) {
                    int drawX = columnLeft + subColumn * TILE_SIZE;
                    if (drawX >= columnRight) {
                        continue;
                    }
                    int tileIndex = ((drawX / TILE_SIZE) + (bgRowIndex & 0x7F)) % tileCount;
                    int patternIndex = tileBase + tileIndex;
                    int descriptor = (FIRE_PALETTE_INDEX << 13) | (patternIndex & 0x7FF);
                    draws.add(new TileDraw(descriptor, patternIndex, drawX, drawY));
                }
            }
            if (!draws.isEmpty()) {
                columns.add(new ColumnRenderPlan(columnIndex, columnLeft, columnWidth, clipTop, screenHeight, draws));
            }
        }
        return new CurtainCompositionPlan(screenWidth, screenHeight, columns);
    }

    private static int sampleBackgroundStripDescriptor(int sourceX, int sourceY) {
        LevelManager levelManager = LevelManager.getInstance();
        if (levelManager == null || levelManager.getCurrentLevel() == null) {
            return 0;
        }
        return levelManager.getBackgroundTileDescriptorAtWorld(sourceX, sourceY);
    }

    /**
     * Wraps a BG tile Y coordinate into the fire zone [FIRE_TILE_START_BG_Y, FIRE_TILE_END_BG_Y).
     * Emulates VDP BG plane wrapping so fire tiles repeat beyond the original range.
     * Returns -1 if the input is below the fire zone start (pre-fire region).
     */
    private static int wrapFireTileY(int bgTileY) {
        if (bgTileY < FIRE_TILE_START_BG_Y) {
            return -1;
        }
        int offset = bgTileY - FIRE_TILE_START_BG_Y;
        int fireHeight = FIRE_TILE_END_BG_Y - FIRE_TILE_START_BG_Y;
        int wrapped = ((offset % fireHeight) + fireHeight) % fireHeight;
        return FIRE_TILE_START_BG_Y + wrapped;
    }

    private static int forceFirePalette(int descriptor) {
        return (descriptor & ~PALETTE_MASK) | (FIRE_PALETTE_INDEX << 13);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @FunctionalInterface
    interface TileDescriptorSampler {
        int sample(int worldX, int worldY);
    }

    record TileDraw(int descriptor, int renderPatternId, int screenX, int screenY) {
    }

    record ColumnRenderPlan(int columnIndex, int screenX, int widthPx, int topY, int bottomY, List<TileDraw> draws) {
    }

    record CurtainCompositionPlan(int screenWidth,
                                  int screenHeight,
                                  List<ColumnRenderPlan> columns) {
    }
}
