package com.openggf.tests;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Rom;
import com.openggf.game.GameServices;
import com.openggf.game.sonic3k.Sonic3kLevel;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.*;
import com.openggf.level.resources.LoadOp;
import com.openggf.level.resources.ResourceLoader;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.util.*;

/**
 * Diagnostic test that simulates the FULL runtime state for AIZ1:
 * 1. Load level (SKIP_INTRO, entry 26)
 * 2. Apply terrain swap (same overlay as AizIntroTerrainSwap)
 * 3. Load Pal_AIZ palette
 * 4. Scan tree-area tiles for red pixels
 *
 * This replicates the exact runtime state to find red-block sources.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestAizPostSwapDiagnostic {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    private static Object oldSkipIntros;
    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, 0, 0);
    }

    @AfterClass
    public static void cleanup() {
        SonicConfigurationService.getInstance().setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        if (sharedLevel != null) sharedLevel.dispose();
    }

    @Test
    public void scanAfterTerrainSwap() throws Exception {
        Level level = GameServices.level().getCurrentLevel();
        if (!(level instanceof Sonic3kLevel sonic3kLevel)) {
            System.out.println("Not a Sonic3kLevel, skipping");
            return;
        }

        Rom rom = GameServices.rom().getRom();
        ResourceLoader loader = new ResourceLoader(rom);

        // --- Record pre-swap state ---
        System.out.println("=== PRE-SWAP STATE ===");
        System.out.println("Pattern count: " + level.getPatternCount());
        System.out.println("Chunk count: " + level.getChunkCount());
        dumpPaletteColors(level, "Pre-swap");
        Set<String> preSwapRedSources = scanForRed(level, "Pre-swap");

        // --- Apply terrain swap (same logic as AizIntroTerrainSwap) ---
        int baseEntryAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR;
        int introEntryAddr = Sonic3kConstants.LEVEL_LOAD_BLOCK_ADDR
                + Sonic3kConstants.LEVEL_LOAD_BLOCK_AIZ1_INTRO_INDEX
                * Sonic3kConstants.LEVEL_LOAD_BLOCK_ENTRY_SIZE;

        int baseWord0 = rom.read32BitAddr(baseEntryAddr);
        int baseWord2 = rom.read32BitAddr(baseEntryAddr + 8);
        // Fix #7: read entry 0's secondary art/blocks (not entry 26's)
        int baseWord1 = rom.read32BitAddr(baseEntryAddr + 4);
        int baseWord3 = rom.read32BitAddr(baseEntryAddr + 12);

        int primaryArtAddr = baseWord0 & 0x00FFFFFF;
        int primaryBlocksAddr = baseWord2 & 0x00FFFFFF;
        int mainLevelArtAddr = baseWord1 & 0x00FFFFFF;
        int mainLevelBlocksAddr = baseWord3 & 0x00FFFFFF;

        int patternOffset = loader.loadSingle(LoadOp.kosinskiMBase(primaryArtAddr)).length;
        int chunkOffset = loader.loadSingle(LoadOp.kosinskiBase(primaryBlocksAddr)).length;
        byte[] mainLevelTiles8x8 = loader.loadSingle(LoadOp.kosinskiMBase(mainLevelArtAddr));
        byte[] mainLevelBlocks16x16 = loader.loadSingle(LoadOp.kosinskiBase(mainLevelBlocksAddr));

        int startPatternIndex = patternOffset / 32;
        int overlayPatternCount = mainLevelTiles8x8.length / 32;
        int requiredPatternCount = startPatternIndex + overlayPatternCount;

        System.out.println("\n=== TERRAIN SWAP INFO ===");
        System.out.printf("Pattern overlay: offset=0x%04X (%d bytes) startIdx=%d overlayCount=%d required=%d%n",
                patternOffset, patternOffset, startPatternIndex, overlayPatternCount, requiredPatternCount);
        System.out.printf("Chunk overlay: offset=0x%04X (%d bytes) startIdx=%d%n",
                chunkOffset, chunkOffset, chunkOffset / 8);
        System.out.printf("clearTrailing would clear patterns %d-%d (%d patterns)%n",
                requiredPatternCount, level.getPatternCount() - 1,
                level.getPatternCount() - requiredPatternCount);

        // Apply overlays WITH clearTrailing=true (OLD behavior, to test if clearing causes issues)
        sonic3kLevel.applyChunkOverlay(mainLevelBlocks16x16, chunkOffset, true);
        sonic3kLevel.applyPatternOverlay(mainLevelTiles8x8, patternOffset, true);

        System.out.println("\n=== POST-SWAP STATE (clearTrailing=true) ===");
        System.out.println("Pattern count: " + level.getPatternCount());

        // Check if any patterns in the cleared range have non-zero pixels
        int nonZeroCleared = 0;
        for (int i = requiredPatternCount; i < level.getPatternCount(); i++) {
            Pattern pat = level.getPattern(i);
            if (pat != null) {
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        if ((pat.getPixel(x, y) & 0x0F) != 0) {
                            nonZeroCleared++;
                            break;
                        }
                    }
                    if (nonZeroCleared > 0) break;
                }
            }
        }
        System.out.println("Non-zero cleared patterns: " + nonZeroCleared);

        Set<String> postSwapRedSources = scanForRed(level, "Post-swap (clearTrailing=true)");

        // --- Now apply palette swap to Pal_AIZ ---
        System.out.println("\n=== APPLYING PAL_AIZ SWAP ===");
        int palAizEntryAddr = Sonic3kConstants.PAL_POINTERS_ADDR + 0x2A * 8;
        int palAizRomAddr = rom.read32BitAddr(palAizEntryAddr) & 0x00FFFFFF;
        int palAizRamDest = rom.read16BitAddr(palAizEntryAddr + 4) & 0xFFFF;
        int palAizCount = (rom.read16BitAddr(palAizEntryAddr + 6) & 0xFFFF) + 1;
        int startLine = (palAizRamDest & 0xFF) / 32;

        System.out.printf("Pal_AIZ: addr=0x%06X ramDest=0x%04X lines=%d startLine=%d%n",
                palAizRomAddr, palAizRamDest, palAizCount, startLine);

        for (int line = 0; line < palAizCount; line++) {
            int enginePalIndex = startLine + line;
            if (enginePalIndex >= level.getPaletteCount()) {
                System.out.printf("  Skipping line %d (engine index %d >= paletteCount %d)%n",
                        line, enginePalIndex, level.getPaletteCount());
                continue;
            }
            Palette pal = level.getPalette(enginePalIndex);
            if (pal == null) continue;
            for (int c = 0; c < 16; c++) {
                int colorAddr = palAizRomAddr + line * 32 + c * 2;
                int cramWord = rom.read16BitAddr(colorAddr) & 0xFFFF;
                int b = (cramWord >> 9) & 7;
                int g = (cramWord >> 5) & 7;
                int r = (cramWord >> 1) & 7;
                pal.setColor(c, new Palette.Color((byte)(r * 36), (byte)(g * 36), (byte)(b * 36)));
            }
        }

        dumpPaletteColors(level, "Post Pal_AIZ swap");
        Set<String> postPaletteRedSources = scanForRed(level, "Post Pal_AIZ swap");

        // --- Summary ---
        System.out.println("\n=== COMPARISON SUMMARY ===");
        System.out.println("Pre-swap red occurrences: " + preSwapRedSources.size());
        System.out.println("Post-swap (clearTrailing) red occurrences: " + postSwapRedSources.size());
        System.out.println("Post Pal_AIZ swap red occurrences: " + postPaletteRedSources.size());

        if (!postPaletteRedSources.isEmpty()) {
            System.out.println("\n=== RED SOURCES AFTER FULL SIMULATION ===");
            for (String src : postPaletteRedSources) {
                System.out.println("  " + src);
            }
        }
    }

    /**
     * Scans the level as loaded (no manual overlay) after applying Pal_AIZ.
     * With skip-intro now using entry 0, this tests the actual runtime state.
     */
    @Test
    public void scanLoadedLevelWithPalAiz() throws Exception {
        Level level = GameServices.level().getCurrentLevel();
        Rom rom = GameServices.rom().getRom();

        System.out.println("=== LOADED LEVEL STATE (entry 0, no overlay) ===");
        System.out.println("Pattern count: " + level.getPatternCount());
        System.out.println("Chunk count: " + level.getChunkCount());

        Set<String> preSwapRed = scanForRed(level, "Pre Pal_AIZ (Pal_AIZIntro)");

        // Apply Pal_AIZ
        int palAizEntryAddr = Sonic3kConstants.PAL_POINTERS_ADDR + 0x2A * 8;
        int palAizRomAddr = rom.read32BitAddr(palAizEntryAddr) & 0x00FFFFFF;
        int palAizRamDest = rom.read16BitAddr(palAizEntryAddr + 4) & 0xFFFF;
        int palAizCount = (rom.read16BitAddr(palAizEntryAddr + 6) & 0xFFFF) + 1;
        int startLine = (palAizRamDest & 0xFF) / 32;
        for (int line = 0; line < palAizCount; line++) {
            int enginePalIndex = startLine + line;
            if (enginePalIndex >= level.getPaletteCount()) continue;
            Palette pal = level.getPalette(enginePalIndex);
            if (pal == null) continue;
            for (int c = 0; c < 16; c++) {
                int colorAddr = palAizRomAddr + line * 32 + c * 2;
                int cramWord = rom.read16BitAddr(colorAddr) & 0xFFFF;
                int b = (cramWord >> 9) & 7;
                int g = (cramWord >> 5) & 7;
                int r = (cramWord >> 1) & 7;
                pal.setColor(c, new Palette.Color((byte)(r * 36), (byte)(g * 36), (byte)(b * 36)));
            }
        }

        dumpPaletteColors(level, "After Pal_AIZ");
        Set<String> postSwapRed = scanForRed(level, "Post Pal_AIZ on entry 0 data");

        System.out.println("\n=== ENTRY 0 SUMMARY ===");
        System.out.println("Pre Pal_AIZ red: " + preSwapRed.size());
        System.out.println("Post Pal_AIZ red: " + postSwapRed.size());
        if (!postSwapRed.isEmpty()) {
            System.out.println("Red sources:");
            for (String s : postSwapRed) System.out.println("  " + s);
        }
    }

    private void dumpPaletteColors(Level level, String label) {
        System.out.println("\n--- " + label + " palette 2 (all 16 colors) ---");
        if (level.getPaletteCount() > 2) {
            Palette pal = level.getPalette(2);
            if (pal != null) {
                for (int c = 0; c < 16; c++) {
                    Palette.Color col = pal.getColor(c);
                    int r = col.r & 0xFF, g = col.g & 0xFF, b = col.b & 0xFF;
                    String flag = (r > 200 && g < 80 && b < 80) ? " [RED!]" : "";
                    System.out.printf("  color[%2d]: R=%3d G=%3d B=%3d%s%n", c, r, g, b, flag);
                }
            }
        }
    }

    private Set<String> scanForRed(Level level, String label) {
        com.openggf.level.Map map = level.getMap();
        int blockSize = level.getBlockPixelSize();
        Set<String> redSources = new TreeSet<>();
        int redPixelCount = 0;

        // Scan tree area: worldX 0x2400-0x3200, worldY 0x100-0x600
        for (int worldY = 0x100; worldY < 0x600; worldY += 16) {
            for (int worldX = 0x2400; worldX < 0x3200; worldX += 16) {
                int blockX = worldX / blockSize;
                int blockY = worldY / blockSize;
                if (blockX >= map.getWidth() || blockY >= map.getHeight()) continue;

                int blockIndex = map.getValue(0, blockX, blockY) & 0xFF;
                if (blockIndex >= level.getBlockCount()) continue;
                Block block = level.getBlock(blockIndex);
                if (block == null) continue;

                int cxInBlock = (worldX % blockSize) / 16;
                int cyInBlock = (worldY % blockSize) / 16;
                ChunkDesc chunkDesc = block.getChunkDesc(cxInBlock, cyInBlock);
                int chunkIndex = chunkDesc.getChunkIndex();

                if (chunkIndex >= level.getChunkCount()) continue;
                Chunk chunk = level.getChunk(chunkIndex);
                if (chunk == null) continue;

                boolean chunkHFlip = chunkDesc.getHFlip();
                boolean chunkVFlip = chunkDesc.getVFlip();

                for (int py = 0; py < 2; py++) {
                    for (int px = 0; px < 2; px++) {
                        int lx = chunkHFlip ? 1 - px : px;
                        int ly = chunkVFlip ? 1 - py : py;
                        PatternDesc pd = chunk.getPatternDesc(lx, ly);

                        int patIdx = pd.getPatternIndex();
                        int palIdx = pd.getPaletteIndex();
                        boolean priority = pd.getPriority();

                        if (patIdx >= level.getPatternCount()) continue;
                        Pattern pattern = level.getPattern(patIdx);
                        if (pattern == null) continue;

                        for (int y = 0; y < 8; y++) {
                            for (int x = 0; x < 8; x++) {
                                int pixIdx = pattern.getPixel(x, y) & 0x0F;
                                if (pixIdx == 0) continue;

                                if (palIdx < level.getPaletteCount()) {
                                    Palette pal = level.getPalette(palIdx);
                                    if (pal == null) continue;
                                    Palette.Color c = pal.getColor(pixIdx);
                                    int r = c.r & 0xFF;
                                    int g = c.g & 0xFF;
                                    int b = c.b & 0xFF;

                                    if (r > 200 && g < 80 && b < 80) {
                                        redPixelCount++;
                                        if (redSources.size() < 50) {
                                            redSources.add(String.format(
                                                    "world(%04X,%04X) chunk=%d pat=0x%03X pal=%d pixIdx=%d pri=%b -> R=%d G=%d B=%d",
                                                    worldX + px * 8, worldY + py * 8,
                                                    chunkIndex, patIdx, palIdx, pixIdx, priority, r, g, b));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("\n--- " + label + ": Red pixel count = " + redPixelCount + " ---");
        return redSources;
    }
}
