package com.openggf.game.sonic3k.events;

import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.Sonic3kPlcLoader;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Pattern;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Deterministic mutation handler for S3K seamless in-place transitions.
 */
public final class S3kSeamlessMutationExecutor {
    private static final Logger LOG = Logger.getLogger(S3kSeamlessMutationExecutor.class.getName());

    public static final String MUTATION_AIZ1_FIRE_TRANSITION_STAGE = "s3k.aiz1.fire_transition_stage";
    public static final String MUTATION_AIZ1_POST_RELOAD_ACT2 = "s3k.aiz1.post_reload_act2";
    private static final int LLB_PRIMARY_ART = 0;
    private static final int LLB_SECONDARY_ART = 4;
    private static final int LLB_PRIMARY_BLOCKS = 8;
    private static final int LLB_SECONDARY_BLOCKS = 12;
    private static final int LLB_PRIMARY_CHUNKS = 16;
    private static final int AIZ2_LEVEL_LOAD_BLOCK_INDEX = 1;
    private static final int AIZ_SECONDARY_BLOCKS_DEST_OFFSET_BYTES = 0x0AB8;
    private static final int AIZ_SECONDARY_ART_DEST_TILE = 0x01FC;
    private static final int PAL_POINTER_AIZ_FIRE_INDEX = 0x0B;
    private static final int PLC_SPIKES_SPRINGS = 0x4E;

    private static volatile AizFireOverlayData cachedAizFireOverlay;

    private S3kSeamlessMutationExecutor() {
    }

    public static void apply(LevelManager levelManager, String mutationKey) {
        if (mutationKey == null || mutationKey.isBlank() || levelManager == null) {
            return;
        }
        switch (mutationKey) {
            case MUTATION_AIZ1_FIRE_TRANSITION_STAGE -> applyAiz1FireTransitionStage(levelManager);
            case MUTATION_AIZ1_POST_RELOAD_ACT2 -> applyAiz1PostReloadAct2(levelManager);
            default -> LOG.warning("Unknown S3K seamless mutation key: " + mutationKey);
        }
    }

    private static void applyAiz1FireTransitionStage(LevelManager levelManager) {
        Level level = levelManager.getCurrentLevel();
        if (!(level instanceof Sonic3kLevel sonic3kLevel)) {
            return;
        }

        try {
            Rom rom = GameServices.rom().getRom();
            if (rom == null) {
                return;
            }

            AizFireOverlayData overlay = loadAizFireOverlayData(rom);
            if (overlay == null) {
                return;
            }

            // AIZ1BGE_FireTransition:
            // Queue_Kos(AIZ2_128x128), Queue_Kos(AIZ2_16x16_Primary),
            // Queue_Kos(AIZ2_16x16_Secondary @ +$AB8),
            // Queue_Kos_Module(AIZ2_8x8_Primary @ tile 0),
            // Queue_Kos_Module(AIZ2_8x8_Secondary @ tile $1FC).
            sonic3kLevel.applyBlockOverlay(overlay.primaryBlocks128x128(), 0, false);
            sonic3kLevel.applyChunkOverlay(overlay.primaryBlocks16x16(), 0, false);
            sonic3kLevel.applyChunkOverlay(
                    overlay.secondaryBlocks16x16(),
                    AIZ_SECONDARY_BLOCKS_DEST_OFFSET_BYTES,
                    false);
            sonic3kLevel.applyPatternOverlay(overlay.primaryTiles8x8(), 0, false);
            sonic3kLevel.applyPatternOverlay(
                    overlay.secondaryTiles8x8(),
                    AIZ_SECONDARY_ART_DEST_TILE * Pattern.PATTERN_SIZE_IN_ROM,
                    false);

            // Fire transition stage requests only the fire palette and spikes/springs PLC.
            Sonic3kZoneEvents.loadPaletteFromPalPointers(PAL_POINTER_AIZ_FIRE_INDEX);
            Sonic3kAIZEvents.applyFireTransitionPaletteLine4(levelManager);
            Sonic3kZoneEvents.applyPlc(PLC_SPIKES_SPRINGS);

            // Refresh object renderers whose backing patterns were changed by the
            // 8x8 overlays. Without this, level-art object sprites (spikes, springs,
            // vines, etc.) retain stale GPU textures from the pre-fire pattern data.
            List<Sonic3kPlcLoader.TileRange> overlayRanges = new ArrayList<>();
            int primaryTileCount = overlay.primaryTiles8x8().length / Pattern.PATTERN_SIZE_IN_ROM;
            if (primaryTileCount > 0) {
                overlayRanges.add(new Sonic3kPlcLoader.TileRange(0, primaryTileCount));
            }
            int secondaryTileCount = overlay.secondaryTiles8x8().length / Pattern.PATTERN_SIZE_IN_ROM;
            if (secondaryTileCount > 0) {
                overlayRanges.add(new Sonic3kPlcLoader.TileRange(
                        AIZ_SECONDARY_ART_DEST_TILE, secondaryTileCount));
            }
            Sonic3kPlcLoader.refreshAffectedRenderers(overlayRanges, levelManager);

            levelManager.invalidateAllTilemaps();
            LOG.info("Applied AIZ1 fire transition overlays (128x128/16x16/8x8) and fire palette");
        } catch (Exception e) {
            LOG.warning("Failed to apply AIZ1 fire transition mutation: " + e.getMessage());
        }
    }

    private static void applyAiz1PostReloadAct2(LevelManager levelManager) {
        Level level = levelManager.getCurrentLevel();
        if (!(level instanceof Sonic3kLevel)) {
            return;
        }

        AizAct2LayoutAdjuster.apply(level.getMap());
        // Load the full fire palette (PalPointers #$0B) first, then overlay fire line 4.
        // Without PalPointers the act 2 reload keeps the normal AIZ2 palette, leaving
        // palette line 3 with green vegetation colors instead of fire colors.
        Sonic3kZoneEvents.loadPaletteFromPalPointers(PAL_POINTER_AIZ_FIRE_INDEX);
        Sonic3kAIZEvents.applyFireTransitionPaletteLine4(levelManager);
        levelManager.invalidateAllTilemaps();
        LOG.info("Applied AIZ1 post-reload act 2 layout adjustment and fire palette");
    }

    private static synchronized AizFireOverlayData loadAizFireOverlayData(Rom rom) throws IOException {
        if (cachedAizFireOverlay != null) {
            return cachedAizFireOverlay;
        }

        int entryAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + AIZ2_LEVEL_LOAD_BLOCK_INDEX * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;
        int primaryArtAddr = rom.read32BitAddr(entryAddr + LLB_PRIMARY_ART) & 0x00FFFFFF;
        int secondaryArtAddr = rom.read32BitAddr(entryAddr + LLB_SECONDARY_ART) & 0x00FFFFFF;
        int primaryBlocksAddr = rom.read32BitAddr(entryAddr + LLB_PRIMARY_BLOCKS) & 0x00FFFFFF;
        int secondaryBlocksAddr = rom.read32BitAddr(entryAddr + LLB_SECONDARY_BLOCKS) & 0x00FFFFFF;
        int primaryChunksAddr = rom.read32BitAddr(entryAddr + LLB_PRIMARY_CHUNKS) & 0x00FFFFFF;

        ResourceLoader loader = new ResourceLoader(rom);
        byte[] primaryTiles8x8 = loader.loadSingle(LoadOp.kosinskiMBase(primaryArtAddr));
        byte[] secondaryTiles8x8 = loader.loadSingle(LoadOp.kosinskiMBase(secondaryArtAddr));
        byte[] primaryBlocks16x16 = loader.loadSingle(LoadOp.kosinskiBase(primaryBlocksAddr));
        byte[] secondaryBlocks16x16 = loader.loadSingle(LoadOp.kosinskiBase(secondaryBlocksAddr));
        byte[] primaryBlocks128x128 = loader.loadSingle(LoadOp.kosinskiBase(primaryChunksAddr));

        cachedAizFireOverlay = new AizFireOverlayData(
                primaryTiles8x8,
                secondaryTiles8x8,
                primaryBlocks16x16,
                secondaryBlocks16x16,
                primaryBlocks128x128);
        return cachedAizFireOverlay;
    }

    private record AizFireOverlayData(
            byte[] primaryTiles8x8,
            byte[] secondaryTiles8x8,
            byte[] primaryBlocks16x16,
            byte[] secondaryBlocks16x16,
            byte[] primaryBlocks128x128) {
    }

    private static final class AizAct2LayoutAdjuster {
        private static final int LAYER_FOREGROUND = 0;
        private static final int LAYER_BACKGROUND = 1;

        private AizAct2LayoutAdjuster() {
        }

        private static void apply(com.openggf.level.Map map) {
            if (map == null) {
                return;
            }
            copyCell(map, LAYER_FOREGROUND, 127, 1, 99, 14);
            copyRowPrefix(map, LAYER_BACKGROUND, 0, 5, 4);
            copyRowPrefix(map, LAYER_BACKGROUND, 1, 6, 4);
            copyRowPrefix(map, LAYER_BACKGROUND, 2, 7, 4);
        }

        private static void copyCell(com.openggf.level.Map map,
                                     int layer,
                                     int sourceX,
                                     int sourceY,
                                     int targetX,
                                     int targetY) {
            if (!isInBounds(map, sourceX, sourceY) || !isInBounds(map, targetX, targetY)) {
                return;
            }
            map.setValue(layer, targetX, targetY, map.getValue(layer, sourceX, sourceY));
        }

        private static void copyRowPrefix(com.openggf.level.Map map,
                                          int layer,
                                          int sourceY,
                                          int targetY,
                                          int count) {
            if (count <= 0 || sourceY < 0 || targetY < 0
                    || sourceY >= map.getHeight() || targetY >= map.getHeight()) {
                return;
            }
            int width = Math.min(count, map.getWidth());
            for (int x = 0; x < width; x++) {
                map.setValue(layer, x, targetY, map.getValue(layer, x, sourceY));
            }
        }

        private static boolean isInBounds(com.openggf.level.Map map, int x, int y) {
            return x >= 0 && y >= 0 && x < map.getWidth() && y < map.getHeight();
        }
    }
}
