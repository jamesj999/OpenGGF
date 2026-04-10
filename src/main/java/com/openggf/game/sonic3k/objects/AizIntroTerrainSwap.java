package com.openggf.game.sonic3k.objects;

import com.openggf.data.Rom;
import com.openggf.game.EngineServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.level.resources.PlcParser.PlcDefinition;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.objects.ObjectServices;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;

import java.io.IOException;
import java.util.List;
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
        preloadOverlayData(null);
    }

    public static synchronized void preloadOverlayData(ObjectServices services) {
        if (cachedOverlayData != null) {
            return;
        }
        try {
            cachedOverlayData = loadOverlayData(services);
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
        precomputeTransitionTilemaps(null);
    }

    public static synchronized void precomputeTransitionTilemaps(ObjectServices services) {
        preloadOverlayData(services);
        OverlayData overlay = cachedOverlayData;
        if (overlay == null) {
            return;
        }

        LevelManager levelManager = levelManager();
        if (levelManager == null) {
            return;
        }
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

    public static synchronized boolean applyMainLevelOverlays() {
        return applyMainLevelOverlays(null);
    }

    public static synchronized boolean applyMainLevelOverlays(ObjectServices services) {
        LevelManager levelManager = levelManager();
        if (levelManager == null) {
            return false;
        }
        Level level = levelManager.getCurrentLevel();
        if (!(level instanceof Sonic3kLevel sonic3kLevel)) {
            return false;
        }

        OverlayData overlay = cachedOverlayData;
        if (overlay == null) {
            try {
                overlay = loadOverlayData(services);
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
        List<Sonic3kPlcLoader.TileRange> modifiedRanges = applyZoneArtOverlays(sonic3kLevel, services);

        // The Nemesis overlays updated Pattern objects in-place (the same objects
        // referenced by object sprite sheets), but the renderers' GPU textures are
        // stale. Use generic tile-range-based refresh to re-upload affected renderers.
        Sonic3kPlcLoader.refreshAffectedRenderers(modifiedRanges, levelManager);

        if (!levelManager.swapToPrebuiltTilemaps()) {
            levelManager.invalidateAllTilemaps();
        }
        return true;
    }

    /**
     * Parses and applies PLC 0x0B (AIZ1 zone art) Nemesis overlays onto
     * the level pattern buffer using the generic PLC loader.
     *
     * @return list of tile ranges that were modified, for GPU refresh
     */
    private static List<Sonic3kPlcLoader.TileRange> applyZoneArtOverlays(
            Sonic3kLevel level, ObjectServices services) {
        try {
            Rom rom = rom(services);
            PlcDefinition plc = Sonic3kPlcLoader.parsePlc(rom, 0x0B);
            return Sonic3kPlcLoader.applyToLevel(plc, level);
        } catch (IOException e) {
            LOG.warning("Failed to apply PLC 0x0B zone art overlays: " + e.getMessage());
            return List.of();
        }
    }

    private static OverlayData loadOverlayData(ObjectServices services) throws IOException {
        Rom rom = rom(services);
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

    private static Rom rom(ObjectServices services) throws IOException {
        if (services != null) {
            return services.rom();
        }
        return engineServices().roms().getRom();
    }

    private static LevelManager levelManager() {
        var runtime = RuntimeManager.getCurrent();
        return runtime != null ? runtime.getLevelManager() : null;
    }

    private static EngineServices engineServices() {
        var runtime = RuntimeManager.getCurrent();
        return runtime != null
                ? runtime.getEngineServices()
                : EngineServices.fromLegacySingletonsForBootstrap();
    }

    private record OverlayData(
            int patternOverlayOffsetBytes,
            int chunkOverlayOffsetBytes,
            byte[] mainLevelTiles8x8,
            byte[] mainLevelBlocks16x16) {
    }
}

