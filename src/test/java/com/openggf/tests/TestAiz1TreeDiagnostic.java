package com.openggf.tests;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Level;
import com.openggf.level.LevelManager;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.PatternDesc;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Diagnostic test that dumps pixel values for AIZ1 tree art tiles (0x39-0x3C)
 * and palette data for palette lines 2 and 3, to aid in debugging tree object
 * rendering.
 *
 * Requires S3K ROM: {@code -Ds3k.rom.path="Sonic and Knuckles & Sonic 3 (W) [!].gen"}
 */
@RequiresRom(SonicGame.SONIC_3K)
public class TestAiz1TreeDiagnostic {

    @ClassRule
    public static RequiresRomRule romRule = new RequiresRomRule();

    private static final int ZONE_AIZ = 0;
    private static final int ACT_1 = 0;

    private static Object oldSkipIntros;
    private static SharedLevel sharedLevel;

    @BeforeClass
    public static void loadLevel() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);

        sharedLevel = SharedLevel.load(SonicGame.SONIC_3K, ZONE_AIZ, ACT_1);
    }

    @AfterClass
    public static void cleanup() {
        SonicConfigurationService.getInstance().setConfigValue(
                SonicConfiguration.S3K_SKIP_INTROS,
                oldSkipIntros != null ? oldSkipIntros : false);
        if (sharedLevel != null) sharedLevel.dispose();
    }

    /**
     * Dumps the pixel values of level patterns at indices 0x39, 0x3A, 0x3B, 0x3C
     * (the AIZ1Tree object tiles). Each pattern is 8x8 pixels with 4-bit palette
     * indices.
     */
    @Test
    public void dumpTreeArtTilePixels() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        int patternCount = level.getPatternCount();
        System.out.println("=== AIZ1 Tree Art Tile Pixel Dump ===");
        System.out.println("Total pattern count: " + patternCount + " (0x" + Integer.toHexString(patternCount) + ")");

        int[] tileIndices = {0x39, 0x3A, 0x3B, 0x3C};
        boolean anyNonZero = false;

        for (int tileIndex : tileIndices) {
            System.out.println("\n--- Pattern 0x" + Integer.toHexString(tileIndex)
                    + " (decimal " + tileIndex + ") ---");

            if (tileIndex >= patternCount) {
                System.out.println("  [OUT OF RANGE] patternCount=" + patternCount);
                continue;
            }

            Pattern pattern = level.getPattern(tileIndex);
            if (pattern == null) {
                System.out.println("  [NULL PATTERN]");
                continue;
            }

            boolean tileHasData = false;
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                StringBuilder row = new StringBuilder("  row " + y + ": ");
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    byte pixel = pattern.getPixel(x, y);
                    row.append(String.format("%X ", pixel & 0x0F));
                    if (pixel != 0) {
                        tileHasData = true;
                        anyNonZero = true;
                    }
                }
                System.out.println(row.toString().trim());
            }

            if (!tileHasData) {
                System.out.println("  ** ALL ZEROS (empty/transparent tile) **");
            }
        }

        System.out.println("\n=== Summary ===");
        System.out.println("Any non-zero pixel data in tiles 0x39-0x3C: " + anyNonZero);

        // Also dump a few tiles around the range to see where real data lives
        System.out.println("\n=== Nearby tile occupancy scan (0x30-0x50) ===");
        for (int i = 0x30; i <= 0x50 && i < patternCount; i++) {
            Pattern p = level.getPattern(i);
            boolean hasData = false;
            if (p != null) {
                for (int y = 0; y < Pattern.PATTERN_HEIGHT && !hasData; y++) {
                    for (int x = 0; x < Pattern.PATTERN_WIDTH && !hasData; x++) {
                        if (p.getPixel(x, y) != 0) {
                            hasData = true;
                        }
                    }
                }
            }
            System.out.println("  Pattern 0x" + Integer.toHexString(i)
                    + ": " + (hasData ? "HAS DATA" : "empty"));
        }
    }

    /**
     * Dumps palette colors at lines 2 and 3 for key color indices (0, 5, 10, 15)
     * to verify palette data is loaded correctly for tree rendering.
     */
    @Test
    public void dumpTreePaletteData() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        int paletteCount = level.getPaletteCount();
        System.out.println("=== AIZ1 Palette Dump ===");
        System.out.println("Total palette count: " + paletteCount);

        int[] paletteLines = {2, 3};
        int[] colorIndices = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};

        for (int palLine : paletteLines) {
            System.out.println("\n--- Palette line " + palLine + " ---");

            if (palLine >= paletteCount) {
                System.out.println("  [OUT OF RANGE] paletteCount=" + paletteCount);
                continue;
            }

            Palette palette = level.getPalette(palLine);
            if (palette == null) {
                System.out.println("  [NULL PALETTE]");
                continue;
            }

            for (int ci : colorIndices) {
                Palette.Color color = palette.getColor(ci);
                System.out.printf("  color[%2d]: R=%3d G=%3d B=%3d (0x%02X%02X%02X)%n",
                        ci,
                        color.r & 0xFF, color.g & 0xFF, color.b & 0xFF,
                        color.r & 0xFF, color.g & 0xFF, color.b & 0xFF);
            }
        }

        // Verify at least palette line 2 exists and has non-black colors
        assertTrue("Expected at least 3 palette lines for AIZ1", paletteCount >= 3);
        Palette pal2 = level.getPalette(2);
        assertNotNull("Palette line 2 must not be null", pal2);

        boolean hasNonBlack = false;
        for (int i = 1; i < Palette.PALETTE_SIZE; i++) {
            Palette.Color c = pal2.getColor(i);
            if ((c.r & 0xFF) != 0 || (c.g & 0xFF) != 0 || (c.b & 0xFF) != 0) {
                hasNonBlack = true;
                break;
            }
        }
        assertTrue("Palette line 2 should have at least one non-black color", hasNonBlack);
    }

    /**
     * Examines FG chunks at typical AIZ1Tree positions in the level layout,
     * dumping the palette indices used by the pattern descriptors in those chunks.
     * The AIZ1Tree objects appear around world X=0x2C80-0x2D80 in AIZ1.
     */
    @Test
    public void dumpFgChunkPaletteIndicesAtTreePositions() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        Map map = level.getMap();
        assertNotNull("Map must not be null", map);

        System.out.println("=== FG Chunk Palette Indices at AIZ1 Tree Positions ===");
        System.out.println("Map dimensions: " + map.getWidth() + "x" + map.getHeight()
                + " (layers=" + map.getLayerCount() + ")");
        System.out.println("Block count: " + level.getBlockCount());
        System.out.println("Chunk count: " + level.getChunkCount());

        // Sample several world positions where tree objects typically appear in AIZ1
        // World coords -> block coords: blockX = worldX / 128, blockY = worldY / 128
        int[][] sampleWorldPositions = {
                {0x2C80, 0x300},  // Tree area
                {0x2D00, 0x300},
                {0x2C80, 0x380},
                {0x2D00, 0x380},
                {0x2C80, 0x400},
                {0x2D00, 0x400},
                {0x0680, 0x300},  // Earlier in the level for comparison
                {0x0700, 0x300},
        };

        for (int[] pos : sampleWorldPositions) {
            int worldX = pos[0];
            int worldY = pos[1];
            int blockX = worldX / 128;
            int blockY = worldY / 128;

            System.out.println("\n--- World (" + String.format("0x%04X, 0x%04X", worldX, worldY)
                    + ") -> Block (" + blockX + ", " + blockY + ") ---");

            if (blockX >= map.getWidth() || blockY >= map.getHeight()) {
                System.out.println("  [OUT OF MAP BOUNDS]");
                continue;
            }

            // FG layer is layer 0
            int blockIndex = map.getValue(0, blockX, blockY) & 0xFF;
            System.out.println("  Block index: " + blockIndex + " (0x" + Integer.toHexString(blockIndex) + ")");

            if (blockIndex == 0 || blockIndex >= level.getBlockCount()) {
                System.out.println("  [EMPTY OR OUT OF RANGE]");
                continue;
            }

            Block block = level.getBlock(blockIndex);
            if (block == null) {
                System.out.println("  [NULL BLOCK]");
                continue;
            }

            // Determine which chunk within the block corresponds to our world position
            int chunkWithinBlockX = (worldX % 128) / 16;
            int chunkWithinBlockY = (worldY % 128) / 16;

            System.out.println("  Chunk within block: (" + chunkWithinBlockX + ", " + chunkWithinBlockY + ")");

            // Dump all chunks in this block to see palette usage
            int chunksPerSide = level.getChunksPerBlockSide();
            System.out.println("  Chunks per block side: " + chunksPerSide);

            for (int cy = 0; cy < chunksPerSide; cy++) {
                for (int cx = 0; cx < chunksPerSide; cx++) {
                    ChunkDesc chunkDesc = block.getChunkDesc(cx, cy);
                    int chunkIndex = chunkDesc.getChunkIndex();
                    if (chunkIndex == 0) continue;

                    if (chunkIndex >= level.getChunkCount()) {
                        System.out.println("  Chunk (" + cx + "," + cy + "): index "
                                + chunkIndex + " [OUT OF RANGE]");
                        continue;
                    }

                    Chunk chunk = level.getChunk(chunkIndex);
                    if (chunk == null) continue;

                    // Dump the 4 pattern descriptors in this chunk
                    StringBuilder sb = new StringBuilder();
                    sb.append("  Chunk (").append(cx).append(",").append(cy)
                            .append(") idx=0x").append(Integer.toHexString(chunkIndex)).append(": ");

                    for (int py = 0; py < 2; py++) {
                        for (int px = 0; px < 2; px++) {
                            PatternDesc pd = chunk.getPatternDesc(px, py);
                            sb.append(String.format("[pat=0x%03X pal=%d pri=%b hf=%b vf=%b] ",
                                    pd.getPatternIndex(),
                                    pd.getPaletteIndex(),
                                    pd.getPriority(),
                                    pd.getHFlip(),
                                    pd.getVFlip()));
                        }
                    }
                    System.out.println(sb.toString().trim());
                }
            }
        }
    }

    /**
     * Dumps pixel data for specific FG patterns at tree canopy gap positions.
     * The low-priority FG tiles at canopy gaps should have transparent (index 0)
     * pixels where the sky shows through on real hardware.
     */
    @Test
    public void dumpLowPriorityFgPatternsAtTreePositions() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        Map map = level.getMap();
        assertNotNull("Map must not be null", map);

        System.out.println("=== Low-Priority FG Pattern Pixel Dump at Tree Positions ===");
        System.out.println("Pattern count: " + level.getPatternCount());

        // Collect unique low-priority pattern indices from FG at tree area
        java.util.Set<Integer> lowPriPatterns = new java.util.TreeSet<>();
        int[][] treePositions = {
                {0x2C80, 0x0300}, {0x2C80, 0x0380}, {0x2C80, 0x0400},
                {0x2D00, 0x0300}, {0x2D00, 0x0380}, {0x2D00, 0x0400}
        };

        for (int[] pos : treePositions) {
            int worldX = pos[0], worldY = pos[1];
            int bx = worldX / 128, by = worldY / 128;
            if (bx >= map.getWidth() || by >= map.getHeight()) continue;
            int bi = map.getValue(0, bx, by) & 0xFF;
            if (bi == 0 || bi >= level.getBlockCount()) continue;
            Block block = level.getBlock(bi);
            if (block == null) continue;
            int chunksPerSide = level.getChunksPerBlockSide();
            for (int cy = 0; cy < chunksPerSide; cy++) {
                for (int cx = 0; cx < chunksPerSide; cx++) {
                    ChunkDesc cd = block.getChunkDesc(cx, cy);
                    int ci = cd.getChunkIndex();
                    if (ci == 0 || ci >= level.getChunkCount()) continue;
                    Chunk chunk = level.getChunk(ci);
                    if (chunk == null) continue;
                    for (int py = 0; py < 2; py++) {
                        for (int px = 0; px < 2; px++) {
                            PatternDesc pd = chunk.getPatternDesc(px, py);
                            if (!pd.getPriority() && pd.getPatternIndex() != 0) {
                                lowPriPatterns.add(pd.getPatternIndex());
                            }
                        }
                    }
                }
            }
        }

        System.out.println("Unique low-priority non-empty patterns at tree FG: " + lowPriPatterns.size());
        int nonTransparentCount = 0;
        for (int patIdx : lowPriPatterns) {
            if (patIdx >= level.getPatternCount()) {
                System.out.printf("  Pattern 0x%03X: [OUT OF RANGE] (patternCount=%d)%n",
                        patIdx, level.getPatternCount());
                continue;
            }
            Pattern pattern = level.getPattern(patIdx);
            if (pattern == null) {
                System.out.printf("  Pattern 0x%03X: [NULL]%n", patIdx);
                continue;
            }
            boolean hasData = false;
            boolean hasIdx15 = false;
            StringBuilder rows = new StringBuilder();
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    byte pixel = pattern.getPixel(x, y);
                    int idx = pixel & 0x0F;
                    row.append(String.format("%X", idx));
                    if (idx != 0) hasData = true;
                    if (idx == 15) hasIdx15 = true;
                }
                rows.append("    ").append(row).append("\n");
            }
            if (hasData) {
                nonTransparentCount++;
                System.out.printf("  Pattern 0x%03X: HAS DATA%s%n%s",
                        patIdx, hasIdx15 ? " [CONTAINS INDEX 15 = RED AFTER SWAP]" : "", rows);
            } else {
                System.out.printf("  Pattern 0x%03X: fully transparent%n", patIdx);
            }
        }
        System.out.println("\nTotal non-transparent low-pri FG patterns: " + nonTransparentCount + " / " + lowPriPatterns.size());
    }

    /**
     * Scans all chunks in the level to find any that reference patterns in the
     * 0x39-0x3C range, and reports their palette indices. This helps verify
     * whether the tree tiles are actually used by the level layout.
     */
    @Test
    public void scanChunksReferencingTreePatterns() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        int chunkCount = level.getChunkCount();
        System.out.println("=== Scan for chunks referencing patterns 0x39-0x3C ===");
        System.out.println("Total chunk count: " + chunkCount);

        int matchCount = 0;
        for (int ci = 0; ci < chunkCount; ci++) {
            Chunk chunk = level.getChunk(ci);
            if (chunk == null) continue;

            for (int py = 0; py < 2; py++) {
                for (int px = 0; px < 2; px++) {
                    PatternDesc pd = chunk.getPatternDesc(px, py);
                    int patIdx = pd.getPatternIndex();
                    if (patIdx >= 0x39 && patIdx <= 0x3C) {
                        matchCount++;
                        System.out.printf("  Chunk 0x%03X (%d,%d): pat=0x%03X pal=%d pri=%b hf=%b vf=%b%n",
                                ci, px, py, patIdx, pd.getPaletteIndex(),
                                pd.getPriority(), pd.getHFlip(), pd.getVFlip());
                    }
                }
            }
        }

        System.out.println("\nTotal chunk pattern descriptors referencing 0x39-0x3C: " + matchCount);
        // This is diagnostic -- we want to see results regardless
        assertTrue("Diagnostic scan completed successfully", true);
    }

    /**
     * Dumps BG tile descriptors at various BG Y positions to check priority bits.
     * On real VDP hardware, high-priority BG tiles render ABOVE low-priority FG tiles.
     * This test verifies whether the AIZ BG sky tiles have the priority bit set.
     */
    @Test
    public void dumpBgTilePrioritiesAtSkyPositions() {
        Level level = LevelManager.getInstance().getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        Map map = level.getMap();
        assertNotNull("Map must not be null", map);

        int bgWidth = level.getLayerWidthBlocks(1);
        int bgHeight = level.getLayerHeightBlocks(1);
        System.out.println("=== BG Tile Priority Scan ===");
        System.out.println("BG layout: " + bgWidth + "x" + bgHeight + " blocks");
        System.out.println("BG pixel size: " + (bgWidth * 128) + "x" + (bgHeight * 128));

        // Scan BG tiles at various Y positions (0=sky, lower=mountains/ground)
        // The sky in AIZ is in the upper portion of the BG layout.
        int blockSize = level.getBlockPixelSize();
        int chunksPerSide = level.getChunksPerBlockSide();
        int chunkW = 16;
        int chunkH = 16;

        // Sample BG blocks across a range of positions
        int bgWidthPx = bgWidth * blockSize;
        int bgHeightPx = bgHeight * blockSize;

        System.out.println("\n--- BG block scan (priority summary by row) ---");
        for (int blockY = 0; blockY < bgHeight; blockY++) {
            int highPriCount = 0;
            int lowPriCount = 0;
            int emptyCount = 0;
            StringBuilder sampleDesc = new StringBuilder();

            for (int blockX = 0; blockX < Math.min(bgWidth, 4); blockX++) {
                int blockIndex = map.getValue(1, blockX, blockY) & 0xFF;
                if (blockIndex == 0 || blockIndex >= level.getBlockCount()) {
                    emptyCount += chunksPerSide * chunksPerSide;
                    continue;
                }
                Block block = level.getBlock(blockIndex);
                if (block == null) {
                    emptyCount += chunksPerSide * chunksPerSide;
                    continue;
                }
                for (int cy = 0; cy < chunksPerSide; cy++) {
                    for (int cx = 0; cx < chunksPerSide; cx++) {
                        ChunkDesc cd = block.getChunkDesc(cx, cy);
                        int ci = cd.getChunkIndex();
                        if (ci == 0 || ci >= level.getChunkCount()) {
                            emptyCount++;
                            continue;
                        }
                        Chunk chunk = level.getChunk(ci);
                        if (chunk == null) { emptyCount++; continue; }
                        for (int py = 0; py < 2; py++) {
                            for (int px = 0; px < 2; px++) {
                                PatternDesc pd = chunk.getPatternDesc(px, py);
                                if (pd.getPatternIndex() == 0) {
                                    emptyCount++;
                                } else if (pd.getPriority()) {
                                    highPriCount++;
                                    if (sampleDesc.length() < 200) {
                                        sampleDesc.append(String.format("0x%03X/p%d ", pd.getPatternIndex(), pd.getPaletteIndex()));
                                    }
                                } else {
                                    lowPriCount++;
                                }
                            }
                        }
                    }
                }
            }
            System.out.printf("  BG blockRow %d (Y=%d-%d): high=%d low=%d empty=%d  %s%n",
                    blockY, blockY * blockSize, (blockY + 1) * blockSize - 1,
                    highPriCount, lowPriCount, emptyCount,
                    sampleDesc.length() > 0 ? "samples: " + sampleDesc.toString().trim() : "");
        }
    }
}
