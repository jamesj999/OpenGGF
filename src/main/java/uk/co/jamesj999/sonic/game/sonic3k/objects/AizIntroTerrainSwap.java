package uk.co.jamesj999.sonic.game.sonic3k.objects;

import uk.co.jamesj999.sonic.data.Rom;
import uk.co.jamesj999.sonic.game.GameServices;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kLevel;
import uk.co.jamesj999.sonic.game.sonic3k.Sonic3kObjectArtKeys;
import uk.co.jamesj999.sonic.game.sonic3k.constants.Sonic3kConstants;
import uk.co.jamesj999.sonic.graphics.GraphicsManager;
import uk.co.jamesj999.sonic.level.Level;
import uk.co.jamesj999.sonic.level.LevelManager;
import uk.co.jamesj999.sonic.level.objects.ObjectRenderManager;
import uk.co.jamesj999.sonic.level.objects.ObjectSpriteSheet;
import uk.co.jamesj999.sonic.level.render.PatternSpriteRenderer;
import uk.co.jamesj999.sonic.level.resources.CompressionType;
import uk.co.jamesj999.sonic.level.resources.LoadOp;
import uk.co.jamesj999.sonic.level.resources.ResourceLoader;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Runtime terrain swap helper for AIZ1 intro.
 *
 * ROM behavior: once camera X reaches $1400, AIZ loads "Main Level" overlays
 * for both 16x16 and 8x8 terrain data, and the zone art PLCs (PLC 0x0B)
 * become active for objects like foreground plants, zipline pegs, etc.
 */
public final class AizIntroTerrainSwap {
    private static final Logger LOG = Logger.getLogger(AizIntroTerrainSwap.class.getName());

    // LevelLoadBlock entry field offsets (24-byte entries, 4-byte pointers)
    private static final int LLB_PRIMARY_ART = 0;
    private static final int LLB_SECONDARY_ART = 4;
    private static final int LLB_PRIMARY_BLOCKS = 8;
    private static final int LLB_SECONDARY_BLOCKS = 12;

    private static OverlayData cachedOverlayData;

    private AizIntroTerrainSwap() {
    }

    /**
     * Pre-decompress the overlay data during level load so that the
     * transition frame doesn't pay the Kosinski decompression cost.
     */
    public static synchronized void preloadOverlayData() {
        if (cachedOverlayData != null) {
            return;
        }
        try {
            cachedOverlayData = loadOverlayData();
            LOG.info("AIZ intro overlay data pre-loaded successfully");
        } catch (IOException e) {
            LOG.warning("AIZ intro overlay preload failed (will retry on transition): " + e.getMessage());
        }
    }

    /**
     * Pre-computes the post-transition tilemap data by temporarily applying the
     * chunk overlay, building tilemaps, then restoring the original chunks.
     * This moves the expensive tilemap rebuild from the transition frame to level load.
     */
    public static synchronized void precomputeTransitionTilemaps() {
        preloadOverlayData();
        OverlayData overlay = cachedOverlayData;
        if (overlay == null) {
            return;
        }

        LevelManager levelManager = LevelManager.getInstance();
        Level level = levelManager.getCurrentLevel();
        if (!(level instanceof Sonic3kLevel sonic3kLevel)) {
            return;
        }

        // Snapshot current chunk state
        int[][] snapshot = sonic3kLevel.snapshotChunks();

        // Temporarily apply the chunk overlay
        sonic3kLevel.applyChunkOverlay(overlay.mainLevelBlocks16x16(), overlay.chunkOverlayOffsetBytes());

        // Build and cache the post-transition tilemaps
        levelManager.prebuildTransitionTilemaps();

        // Restore original chunks
        sonic3kLevel.restoreChunks(snapshot);

        LOG.info("AIZ intro transition tilemaps pre-computed successfully");
    }

    static synchronized boolean applyMainLevelOverlays() {
        LevelManager levelManager = LevelManager.getInstance();
        Level level = levelManager.getCurrentLevel();
        if (!(level instanceof Sonic3kLevel sonic3kLevel)) {
            return false;
        }

        OverlayData overlay = cachedOverlayData;
        if (overlay == null) {
            try {
                overlay = loadOverlayData();
                cachedOverlayData = overlay;
            } catch (IOException e) {
                LOG.warning("AIZ intro terrain swap failed to load overlay data: " + e.getMessage());
                return false;
            }
        }

        sonic3kLevel.applyChunkOverlay(overlay.mainLevelBlocks16x16(), overlay.chunkOverlayOffsetBytes());
        sonic3kLevel.applyPatternOverlay(overlay.mainLevelTiles8x8(), overlay.patternOverlayOffsetBytes());

        // Apply PLC 0x0B (zone art) Nemesis overlays. During the intro, Load_PLC_2
        // clears the PLC queue before PLC 0x0B is decompressed, so zone object art
        // (foreground plants, zipline pegs, vines, etc.) isn't available until now.
        applyZoneArtOverlays(sonic3kLevel);

        // The Nemesis overlays updated Pattern objects in-place (the same objects
        // referenced by object sprite sheets), but the renderers' GPU textures are
        // stale — applyPatternOverlay only re-uploads at the level's pattern indices,
        // not at the renderer's separate patternBase. Re-upload now.
        refreshObjectRendererTextures(levelManager);

        if (!levelManager.swapToPrebuiltTilemaps()) {
            levelManager.invalidateAllTilemaps();
        }
        return true;
    }

    /**
     * Decompresses and applies PLC 0x0B (AIZ1 zone art) Nemesis overlays onto
     * the level pattern buffer. Matches the entries in sonic3k.asm PLC_0B.
     */
    private static void applyZoneArtOverlays(Sonic3kLevel level) {
        try {
            Rom rom = GameServices.rom().getRom();
            ResourceLoader loader = new ResourceLoader(rom);

            applyNemesisOverlay(loader, level,
                    Sonic3kConstants.ARTTILE_AIZ_SWING_VINE,
                    Sonic3kConstants.ART_NEM_AIZ_SWING_VINE_ADDR);
            applyNemesisOverlay(loader, level,
                    Sonic3kConstants.ARTTILE_AIZ_SLIDE_ROPE,
                    Sonic3kConstants.ART_NEM_AIZ_SLIDE_ROPE_ADDR);
            applyNemesisOverlay(loader, level,
                    Sonic3kConstants.ARTTILE_AIZ_MISC1,
                    Sonic3kConstants.ART_NEM_AIZ_MISC1_ADDR);
            applyNemesisOverlay(loader, level,
                    Sonic3kConstants.ARTTILE_AIZ_FALLING_LOG,
                    Sonic3kConstants.ART_NEM_AIZ_FALLING_LOG_ADDR);
            applyNemesisOverlay(loader, level,
                    Sonic3kConstants.ARTTILE_BUBBLES,
                    Sonic3kConstants.ART_NEM_BUBBLES_ADDR);
            applyNemesisOverlay(loader, level,
                    Sonic3kConstants.ARTTILE_AIZ_FLOATING_PLATFORM,
                    Sonic3kConstants.ART_NEM_AIZ_CORK_FLOOR_ADDR);

            LOG.info("AIZ1 zone art (PLC 0x0B) Nemesis overlays applied");
        } catch (IOException e) {
            LOG.warning("Failed to apply PLC 0x0B zone art overlays: " + e.getMessage());
        }
    }

    /**
     * Re-uploads GPU textures for object renderers whose backing Pattern data
     * was modified by the PLC 0x0B Nemesis overlays.
     */
    private static void refreshObjectRendererTextures(LevelManager levelManager) {
        GraphicsManager gfx = GraphicsManager.getInstance();
        if (gfx == null || !gfx.isGlInitialized()) {
            return;
        }
        ObjectRenderManager renderManager = levelManager.getObjectRenderManager();
        if (renderManager == null) {
            return;
        }

        refreshRenderer(gfx, renderManager, Sonic3kObjectArtKeys.AIZ_FOREGROUND_PLANT);
        refreshRenderer(gfx, renderManager, Sonic3kObjectArtKeys.AIZ1_ZIPLINE_PEG);
    }

    private static void refreshRenderer(GraphicsManager gfx, ObjectRenderManager renderManager, String key) {
        PatternSpriteRenderer renderer = renderManager.getRenderer(key);
        if (renderer != null && renderer.isReady()) {
            ObjectSpriteSheet sheet = renderManager.getSheet(key);
            if (sheet != null) {
                renderer.updatePatternRange(gfx, 0, sheet.getPatterns().length);
            }
        }
    }

    private static void applyNemesisOverlay(ResourceLoader loader, Sonic3kLevel level,
                                            int tileIndex, int romAddr) throws IOException {
        byte[] data = loader.loadSingle(
                LoadOp.overlay(romAddr, CompressionType.NEMESIS, 0));
        level.applyPatternOverlay(data, tileIndex * 32);
    }

    private static OverlayData loadOverlayData() throws IOException {
        Rom rom = GameServices.rom().getRom();
        ResourceLoader loader = new ResourceLoader(rom);

        int baseEntryAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR;
        int introEntryAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + Sonic3kConstants.LEVEL_LOAD_BLOCK_AIZ1_INTRO_INDEX * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;

        int baseWord0 = rom.read32BitAddr(baseEntryAddr + LLB_PRIMARY_ART);
        int baseWord2 = rom.read32BitAddr(baseEntryAddr + LLB_PRIMARY_BLOCKS);

        int introWord1 = rom.read32BitAddr(introEntryAddr + LLB_SECONDARY_ART);
        int introWord3 = rom.read32BitAddr(introEntryAddr + LLB_SECONDARY_BLOCKS);

        int primaryArtAddr = baseWord0 & 0x00FFFFFF;
        int primaryBlocksAddr = baseWord2 & 0x00FFFFFF;
        int mainLevelArtAddr = introWord1 & 0x00FFFFFF;
        int mainLevelBlocksAddr = introWord3 & 0x00FFFFFF;

        int patternOffset = loader.loadSingle(LoadOp.kosinskiMBase(primaryArtAddr)).length;
        int chunkOffset = loader.loadSingle(LoadOp.kosinskiBase(primaryBlocksAddr)).length;
        byte[] mainLevelTiles8x8 = loader.loadSingle(LoadOp.kosinskiMBase(mainLevelArtAddr));
        byte[] mainLevelBlocks16x16 = loader.loadSingle(LoadOp.kosinskiBase(mainLevelBlocksAddr));

        LOG.info(String.format(
                "AIZ intro terrain swap data: patternOffset=0x%04X chunkOffset=0x%04X main8x8=%d main16x16=%d",
                patternOffset, chunkOffset, mainLevelTiles8x8.length, mainLevelBlocks16x16.length));

        return new OverlayData(
                patternOffset,
                chunkOffset,
                mainLevelTiles8x8,
                mainLevelBlocks16x16);
    }

    private record OverlayData(
            int patternOverlayOffsetBytes,
            int chunkOverlayOffsetBytes,
            byte[] mainLevelTiles8x8,
            byte[] mainLevelBlocks16x16) {
    }
}

