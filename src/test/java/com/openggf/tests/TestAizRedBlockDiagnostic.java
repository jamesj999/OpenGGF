package com.openggf.tests;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.*;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.util.*;

/**
 * Diagnostic test to find the exact cause of bright red blocks at AIZ1 tree positions.
 * Scans ALL FG tiles (including high-priority and chunk 0) and checks
 * what palette color each pixel would produce.
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestAizRedBlockDiagnostic {

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

    /**
     * Scan FG tiles in the tree area and find ALL tiles where ANY pixel would
     * produce a bright red color (R>200, G<50, B<50) with the current palette.
     * Does NOT skip chunk 0 or high-priority tiles.
     */
    @Test
    public void findRedPixelSources() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        com.openggf.level.Map map = level.getMap();

        System.out.println("=== AIZ1 Red Block Diagnostic ===");
        System.out.println("Pattern count: " + level.getPatternCount());
        System.out.println("Chunk count: " + level.getChunkCount());
        System.out.println("Block count: " + level.getBlockCount());
        System.out.println("FG map: " + map.getWidth() + "x" + map.getHeight());

        // Dump all 4 palette lines, color 15
        System.out.println("\n=== Palette color 15 for all lines ===");
        for (int palLine = 0; palLine < level.getPaletteCount(); palLine++) {
            Palette pal = level.getPalette(palLine);
            if (pal == null) continue;
            Palette.Color c = pal.getColor(15);
            System.out.printf("  Palette %d color 15: R=%d G=%d B=%d%n",
                    palLine, c.r & 0xFF, c.g & 0xFF, c.b & 0xFF);
        }

        // Check what pattern 0 looks like
        System.out.println("\n=== Pattern 0 pixel dump ===");
        Pattern pat0 = level.getPattern(0);
        if (pat0 != null) {
            boolean allZero = true;
            for (int y = 0; y < 8; y++) {
                StringBuilder row = new StringBuilder("  ");
                for (int x = 0; x < 8; x++) {
                    int px = pat0.getPixel(x, y) & 0x0F;
                    row.append(String.format("%X", px));
                    if (px != 0) allZero = false;
                }
                System.out.println(row);
            }
            System.out.println("  All zero: " + allZero);
        }

        // Scan tree area: worldX from 0x2400 to 0x3000, worldY from 0x100 to 0x500
        // These cover the palm tree and hollow tree areas
        int blockSize = level.getBlockPixelSize();
        int chunksPerSide = level.getChunksPerBlockSide();
        int redTileCount = 0;
        Set<String> uniqueRedSources = new TreeSet<>();

        // Track red-producing palette/index combos
        Set<String> redPaletteCombos = new TreeSet<>();

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

                        if (patIdx >= level.getPatternCount()) {
                            // OUT OF RANGE pattern!
                            redTileCount++;
                            String key = String.format("OUT_OF_RANGE pat=0x%03X pal=%d pri=%b chunk=%d",
                                    patIdx, palIdx, priority, chunkIndex);
                            uniqueRedSources.add(key);
                            continue;
                        }

                        Pattern pattern = level.getPattern(patIdx);
                        if (pattern == null) continue;

                        // Check each pixel for red color
                        for (int y = 0; y < 8; y++) {
                            for (int x = 0; x < 8; x++) {
                                int pixIdx = pattern.getPixel(x, y) & 0x0F;
                                if (pixIdx == 0) continue; // transparent

                                if (palIdx < level.getPaletteCount()) {
                                    Palette pal = level.getPalette(palIdx);
                                    if (pal == null) continue;
                                    Palette.Color c = pal.getColor(pixIdx);
                                    int r = c.r & 0xFF;
                                    int g = c.g & 0xFF;
                                    int b = c.b & 0xFF;

                                    // Check for bright red (R>200, G<80, B<80)
                                    if (r > 200 && g < 80 && b < 80) {
                                        redTileCount++;
                                        String combo = String.format("pal=%d idx=%d -> R=%d G=%d B=%d",
                                                palIdx, pixIdx, r, g, b);
                                        redPaletteCombos.add(combo);
                                        String key = String.format(
                                                "world(%04X,%04X) blk=%d chunk=%d pat=0x%03X pal=%d pixIdx=%d pri=%b -> R=%d G=%d B=%d",
                                                worldX + px * 8, worldY + py * 8,
                                                blockIndex, chunkIndex, patIdx, palIdx, pixIdx, priority, r, g, b);
                                        if (uniqueRedSources.size() < 50) {
                                            uniqueRedSources.add(key);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        System.out.println("\n=== Results ===");
        System.out.println("Red tile/pixel occurrences: " + redTileCount);
        System.out.println("Red palette combos found:");
        for (String combo : redPaletteCombos) {
            System.out.println("  " + combo);
        }
        System.out.println("\nSample red sources (up to 50):");
        for (String src : uniqueRedSources) {
            System.out.println("  " + src);
        }

        // Also scan for chunk 0 usage in tree blocks
        System.out.println("\n=== Chunk 0 usage at tree positions ===");
        int chunk0Count = 0;
        for (int worldY = 0x100; worldY < 0x600; worldY += 16) {
            for (int worldX = 0x2400; worldX < 0x3200; worldX += 16) {
                int bx = worldX / blockSize;
                int by = worldY / blockSize;
                if (bx >= map.getWidth() || by >= map.getHeight()) continue;
                int bi = map.getValue(0, bx, by) & 0xFF;
                if (bi == 0 || bi >= level.getBlockCount()) continue;
                Block block = level.getBlock(bi);
                if (block == null) continue;
                int cxInBlock = (worldX % blockSize) / 16;
                int cyInBlock = (worldY % blockSize) / 16;
                ChunkDesc cd = block.getChunkDesc(cxInBlock, cyInBlock);
                if (cd.getChunkIndex() == 0) {
                    chunk0Count++;
                }
            }
        }
        System.out.println("FG positions using chunk 0 in tree area: " + chunk0Count);

        // Check what chunk 0 contains
        System.out.println("\n=== Chunk 0 pattern descriptors ===");
        if (level.getChunkCount() > 0) {
            Chunk chunk0 = level.getChunk(0);
            if (chunk0 != null) {
                for (int py = 0; py < 2; py++) {
                    for (int px = 0; px < 2; px++) {
                        PatternDesc pd = chunk0.getPatternDesc(px, py);
                        System.out.printf("  (%d,%d): pat=0x%03X pal=%d pri=%b hFlip=%b vFlip=%b%n",
                                px, py, pd.getPatternIndex(), pd.getPaletteIndex(),
                                pd.getPriority(), pd.getHFlip(), pd.getVFlip());
                    }
                }
            }
        }
    }
}
