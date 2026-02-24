package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.*;
import com.openggf.level.scroll.SwScrlHtz;
import com.openggf.level.scroll.BackgroundCamera;
import com.openggf.game.GameServices;
import com.openggf.game.sonic2.Sonic2LevelEventManager;
import com.openggf.game.sonic2.DynamicHtz;
import com.openggf.physics.GroundSensor;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.Sonic;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.*;

/**
 * Diagnostic test: dumps the HTZ BG tilemap data at earthquake Y positions
 * to verify whether the CPU-side tilemap contains valid (non-empty) tile
 * descriptors for the lava/cave BG art.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestHtzBgTilemapDiagnostic {

    @Rule public RequiresRomRule romRule = new RequiresRomRule();

    private static final int HTZ_ZONE = 4;
    private static final int HTZ_ACT = 0;

    private LevelManager levelManager;

    @Before
    public void setUp() throws Exception {
        TestEnvironment.resetAll();
        GraphicsManager.getInstance().initHeadless();

        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        Sonic sprite = new Sonic(mainCode, (short) 0x1800, (short) 0x450);
        SpriteManager.getInstance().addSprite(sprite);

        Camera camera = Camera.getInstance();
        camera.setFocusedSprite(sprite);
        camera.setFrozen(false);

        levelManager = LevelManager.getInstance();
        levelManager.loadZoneAndAct(HTZ_ZONE, HTZ_ACT);
        GroundSensor.setLevelManager(levelManager);
        camera.updatePosition(true);
    }

    @Test
    public void vdpWrapHeightDetection() throws Exception {
        // Force the tilemap build that normally happens during rendering
        Method buildBgMethod = LevelManager.class.getDeclaredMethod("buildBackgroundTilemapData");
        buildBgMethod.setAccessible(true);
        buildBgMethod.invoke(levelManager);

        // Check via reflection what LevelManager computed
        Field wrapField = LevelManager.class.getDeclaredField("backgroundVdpWrapHeightTiles");
        wrapField.setAccessible(true);
        int wrapValue = wrapField.getInt(levelManager);

        // Also get the tilemap data for diagnostics
        Method buildMethod = LevelManager.class.getDeclaredMethod("buildTilemapData", byte.class);
        buildMethod.setAccessible(true);
        Object tilemapData = buildMethod.invoke(levelManager, (byte) 1);
        Field dataField = tilemapData.getClass().getDeclaredField("data");
        dataField.setAccessible(true);
        byte[] data = (byte[]) dataField.get(tilemapData);
        Field widthField = tilemapData.getClass().getDeclaredField("widthTiles");
        widthField.setAccessible(true);
        int widthTiles = widthField.getInt(tilemapData);
        Field heightField = tilemapData.getClass().getDeclaredField("heightTiles");
        heightField.setAccessible(true);
        int heightTiles = heightField.getInt(tilemapData);

        System.out.println("=== VDP Wrap Height Detection Diagnostic ===");
        System.out.println("Tilemap: " + widthTiles + "x" + heightTiles + " tiles");

        // Scan rows to show pattern distribution
        int lastRealArtRow = -1;
        for (int y = heightTiles - 1; y >= 0; y--) {
            int maxPat = 0;
            for (int x = 0; x < widthTiles; x++) {
                int offset = (y * widthTiles + x) * 4;
                int r = data[offset] & 0xFF;
                int g = data[offset + 1] & 0xFF;
                int pat = r + ((g & 0x07) << 8);
                if (pat > maxPat) maxPat = pat;
            }
            if (maxPat >= 2 && lastRealArtRow < 0) {
                lastRealArtRow = y;
            }
            if (y <= 35 || y >= 253 || y == lastRealArtRow) {
                System.out.printf("  row %3d: maxPat=%d%n", y, maxPat);
            }
        }

        int actualHeight = (lastRealArtRow >= 0) ? lastRealArtRow + 1 : 0;
        System.out.println("Last row with real art (pat>=2): " + lastRealArtRow);
        System.out.println("Actual data height: " + actualHeight + " tiles");
        System.out.println("backgroundVdpWrapHeightTiles = " + wrapValue);

        assertTrue("VDP wrap height should be 32 for HTZ", wrapValue == 32);
    }

    @Test
    public void bgMapLayerHasValidDataAtEarthquakePositions() {
        Level level = levelManager.getCurrentLevel();
        assertNotNull("Level must be loaded", level);
        Map map = level.getMap();
        assertNotNull("Map must be loaded", map);

        System.out.println("=== HTZ BG Map Layer Diagnostic ===");
        System.out.println("Map dimensions: " + map.getWidth() + "x" + map.getHeight() + " blocks");
        System.out.println("Block count: " + level.getBlockCount());
        System.out.println("Chunk count: " + level.getChunkCount());
        System.out.println("Pattern count: " + level.getPatternCount());
        System.out.println();

        // Check BG layer (layer 1) at various Y positions
        // Earthquake scroll Y ≈ 784 → block row 6 (784/128=6.125)
        int[] testBlockRows = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        int nonEmptyRowCount = 0;

        for (int blockRow : testBlockRows) {
            int nonEmptyBlocks = 0;
            int validChunks = 0;
            int nonZeroPatterns = 0;

            for (int blockCol = 0; blockCol < Math.min(4, map.getWidth()); blockCol++) {
                byte blockValue = map.getValue(1, blockCol, blockRow); // layer 1 = BG
                int blockIndex = blockValue & 0xFF;

                if (blockIndex > 0 && blockIndex < level.getBlockCount()) {
                    nonEmptyBlocks++;
                    Block block = level.getBlock(blockIndex);
                    if (block != null) {
                        // Sample first chunk in the block
                        ChunkDesc cd = block.getChunkDesc(0, 0);
                        int chunkIdx = cd.getChunkIndex();
                        if (chunkIdx >= 0 && chunkIdx < level.getChunkCount()) {
                            validChunks++;
                            Chunk chunk = level.getChunk(chunkIdx);
                            if (chunk != null) {
                                PatternDesc pd = chunk.getPatternDesc(0, 0);
                                if (pd.getPatternIndex() > 0) {
                                    nonZeroPatterns++;
                                }
                            }
                        }
                    }
                }
            }

            boolean hasData = nonEmptyBlocks > 0;
            if (hasData) nonEmptyRowCount++;
            System.out.printf("  BG row %2d: blocks=%d/%d, validChunks=%d, nonZeroPatterns=%d %s%n",
                    blockRow, nonEmptyBlocks, Math.min(4, map.getWidth()),
                    validChunks, nonZeroPatterns,
                    blockRow == 6 ? " <-- earthquake Y" : "");
        }

        System.out.println();
        System.out.println("Non-empty BG rows: " + nonEmptyRowCount + "/" + testBlockRows.length);
        // S2 BG map data only populates the first 2 block rows (256px), matching the VDP
        // plane height. The engine wraps via the tilemap shader (WrapY=true, TilemapHeight=32).
        assertTrue("BG map should have data in first 2 rows (VDP plane height)",
                nonEmptyRowCount >= 2);
    }

    @Test
    public void bgMapXColumnVariation() {
        Level level = levelManager.getCurrentLevel();
        assertNotNull("Level must be loaded", level);
        Map map = level.getMap();
        assertNotNull("Map must be loaded", map);

        System.out.println("=== HTZ BG Map X-Column Variation ===");
        System.out.println("Map width=" + map.getWidth() + " height=" + map.getHeight());

        // Scan BG layer (1) block IDs at rows 0 and 1 across ALL columns
        java.util.Set<Integer> row0Blocks = new java.util.TreeSet<>();
        java.util.Set<Integer> row1Blocks = new java.util.TreeSet<>();
        int highPriorityCount = 0;

        for (int col = 0; col < map.getWidth(); col++) {
            int b0 = map.getValue(1, col, 0) & 0xFF;
            int b1 = map.getValue(1, col, 1) & 0xFF;
            row0Blocks.add(b0);
            row1Blocks.add(b1);

            // Check all chunks in these blocks for high-priority patterns
            for (int bRow = 0; bRow < 2; bRow++) {
                int blockIdx = map.getValue(1, col, bRow) & 0xFF;
                if (blockIdx > 0 && blockIdx < level.getBlockCount()) {
                    Block block = level.getBlock(blockIdx);
                    if (block == null) continue;
                    for (int cy = 0; cy < 8; cy++) {
                        for (int cx = 0; cx < 8; cx++) {
                            ChunkDesc cd = block.getChunkDesc(cx, cy);
                            int chunkIdx = cd.getChunkIndex();
                            if (chunkIdx < 0 || chunkIdx >= level.getChunkCount()) continue;
                            Chunk chunk = level.getChunk(chunkIdx);
                            if (chunk == null) continue;
                            for (int py = 0; py < 2; py++) {
                                for (int px = 0; px < 2; px++) {
                                    PatternDesc pd = chunk.getPatternDesc(px, py);
                                    if (pd.getPriority()) highPriorityCount++;
                                }
                            }
                        }
                    }
                }
            }
        }

        // Print first 20 columns detailed
        System.out.println("\nFirst 20 columns (BG rows 0-1):");
        for (int col = 0; col < Math.min(20, map.getWidth()); col++) {
            int b0 = map.getValue(1, col, 0) & 0xFF;
            int b1 = map.getValue(1, col, 1) & 0xFF;
            System.out.printf("  col %3d: row0=block#%d row1=block#%d%n", col, b0, b1);
        }
        System.out.println("\nColumns around cave area (48-60):");
        for (int col = 48; col <= 60 && col < map.getWidth(); col++) {
            int b0 = map.getValue(1, col, 0) & 0xFF;
            int b1 = map.getValue(1, col, 1) & 0xFF;
            System.out.printf("  col %3d: row0=block#%d row1=block#%d%n", col, b0, b1);
        }

        System.out.println("\nUnique block IDs in row 0: " + row0Blocks);
        System.out.println("Unique block IDs in row 1: " + row1Blocks);
        System.out.println("High-priority pattern count in BG: " + highPriorityCount);

        // Check if row 0 and row 1 use DIFFERENT blocks
        boolean differentContent = !row0Blocks.equals(row1Blocks);
        System.out.println("Row 0 vs Row 1 have different blocks: " + differentContent);

        // Check palette used by row 1 blocks (the "lava" row)
        System.out.println("\nRow 1 palette analysis (first 4 columns):");
        for (int col = 0; col < Math.min(4, map.getWidth()); col++) {
            int blockIdx = map.getValue(1, col, 1) & 0xFF;
            if (blockIdx > 0 && blockIdx < level.getBlockCount()) {
                Block block = level.getBlock(blockIdx);
                if (block == null) continue;
                ChunkDesc cd = block.getChunkDesc(0, 0);
                int chunkIdx = cd.getChunkIndex();
                if (chunkIdx >= 0 && chunkIdx < level.getChunkCount()) {
                    Chunk chunk = level.getChunk(chunkIdx);
                    if (chunk != null) {
                        for (int py = 0; py < 2; py++) {
                            for (int px = 0; px < 2; px++) {
                                PatternDesc pd = chunk.getPatternDesc(px, py);
                                System.out.printf("    col%d chunk(0,0) pat(%d,%d): idx=%d pal=%d pri=%b%n",
                                        col, px, py, pd.getPatternIndex(), pd.getPaletteIndex(), pd.getPriority());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    public void bgTilemapDataContainsValidTilesAtEarthquakeY() throws Exception {
        // Force build of BG tilemap data
        Method buildMethod = LevelManager.class.getDeclaredMethod("buildTilemapData", byte.class);
        buildMethod.setAccessible(true);

        // Get TilemapData (record with data, widthTiles, heightTiles)
        Object tilemapData = buildMethod.invoke(levelManager, (byte) 1);
        assertNotNull("BG tilemap data must be built", tilemapData);

        // Extract fields from the TilemapData record
        Field dataField = tilemapData.getClass().getDeclaredField("data");
        dataField.setAccessible(true);
        byte[] data = (byte[]) dataField.get(tilemapData);

        Field widthField = tilemapData.getClass().getDeclaredField("widthTiles");
        widthField.setAccessible(true);
        int widthTiles = widthField.getInt(tilemapData);

        Field heightField = tilemapData.getClass().getDeclaredField("heightTiles");
        heightField.setAccessible(true);
        int heightTiles = heightField.getInt(tilemapData);

        System.out.println("=== BG Tilemap Data Diagnostic ===");
        System.out.println("Tilemap dimensions: " + widthTiles + "x" + heightTiles + " tiles");
        System.out.println("Data array size: " + data.length + " bytes (expected "
                + (widthTiles * heightTiles * 4) + ")");
        assertEquals(widthTiles * heightTiles * 4, data.length);

        // Check tiles at various Y positions
        // Earthquake alignedBgY ≈ 784 → tile Y = 784/8 = 98
        int[] testTileYPositions = {0, 10, 20, 30, 50, 80, 98, 100, 110, 120, 130, 200, 255};

        System.out.println();
        System.out.println("Tile data at various Y positions (first 10 tiles each row):");

        for (int tileY : testTileYPositions) {
            if (tileY >= heightTiles) continue;

            int nonEmptyTiles = 0;
            int totalTilesChecked = Math.min(10, widthTiles);
            StringBuilder sb = new StringBuilder();

            for (int tileX = 0; tileX < totalTilesChecked; tileX++) {
                int offset = (tileY * widthTiles + tileX) * 4;
                int r = data[offset] & 0xFF;
                int g = data[offset + 1] & 0xFF;
                int alpha = data[offset + 3] & 0xFF;

                int patternIndex = r + ((g & 0x07) << 8);
                int paletteIndex = (g >> 3) & 0x03;
                boolean priority = (g & 0x80) != 0;

                if (alpha > 0 && patternIndex > 0) {
                    nonEmptyTiles++;
                }

                if (tileX < 4) {
                    sb.append(String.format("[pat=%d,pal=%d,pri=%b,a=%d] ",
                            patternIndex, paletteIndex, priority, alpha));
                }
            }

            String marker = "";
            if (tileY >= 96 && tileY <= 100) marker = " <-- earthquake zone";

            System.out.printf("  tileY=%3d: %d/%d non-empty %s %s%n",
                    tileY, nonEmptyTiles, totalTilesChecked, sb.toString().trim(), marker);

            // At earthquake Y positions, the RAW tilemap is correctly empty -
            // the shader's VDPWrapHeight wraps tileY back into valid range.
            // Verify the WRAPPED position has valid tiles instead.
            if (tileY >= 96 && tileY <= 100) {
                int wrappedY = tileY % 32;
                int wrappedNonEmpty = 0;
                for (int tileX = 0; tileX < Math.min(10, widthTiles); tileX++) {
                    int wOffset = (wrappedY * widthTiles + tileX) * 4;
                    int wR = data[wOffset] & 0xFF;
                    int wG = data[wOffset + 1] & 0xFF;
                    int wAlpha = data[wOffset + 3] & 0xFF;
                    int wPatIdx = wR + ((wG & 0x07) << 8);
                    if (wAlpha > 0 && wPatIdx > 0) {
                        wrappedNonEmpty++;
                    }
                }
                assertTrue("VDP-wrapped tileY=" + wrappedY + " (from earthquake Y=" + tileY
                        + ") should have non-empty tiles", wrappedNonEmpty > 0);
            }
        }

        // Also check a random sampling of the FULL tilemap for completeness
        int totalNonEmpty = 0;
        for (int tileY = 0; tileY < heightTiles; tileY++) {
            for (int tileX = 0; tileX < widthTiles; tileX++) {
                int offset = (tileY * widthTiles + tileX) * 4;
                int alpha = data[offset + 3] & 0xFF;
                int r = data[offset] & 0xFF;
                int g = data[offset + 1] & 0xFF;
                int patternIndex = r + ((g & 0x07) << 8);
                if (alpha > 0 && patternIndex > 0) {
                    totalNonEmpty++;
                }
            }
        }

        int totalTiles = widthTiles * heightTiles;
        float fillPercent = (100.0f * totalNonEmpty) / totalTiles;
        System.out.printf("%nTotal non-empty tiles: %d/%d (%.1f%%)%n", totalNonEmpty, totalTiles, fillPercent);
        assertTrue("BG tilemap should have substantial data (>10% fill)", fillPercent > 10.0f);
    }

    @Test
    public void bgPatternIndicesWithinAtlasRange() throws Exception {
        Level level = levelManager.getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        int patternCount = level.getPatternCount();
        System.out.println("=== Pattern Atlas Coverage Check ===");
        System.out.println("Level patternCount: " + patternCount);

        // Build BG tilemap data
        Method buildMethod = LevelManager.class.getDeclaredMethod("buildTilemapData", byte.class);
        buildMethod.setAccessible(true);
        Object tilemapData = buildMethod.invoke(levelManager, (byte) 1);
        assertNotNull(tilemapData);

        Field dataField = tilemapData.getClass().getDeclaredField("data");
        dataField.setAccessible(true);
        byte[] data = (byte[]) dataField.get(tilemapData);

        Field widthField = tilemapData.getClass().getDeclaredField("widthTiles");
        widthField.setAccessible(true);
        int widthTiles = widthField.getInt(tilemapData);

        Field heightField = tilemapData.getClass().getDeclaredField("heightTiles");
        heightField.setAccessible(true);
        int heightTiles = heightField.getInt(tilemapData);

        // Find max pattern index in BG tilemap and count out-of-range patterns
        int maxPatIdx = 0;
        int outOfRangeCount = 0;
        int validTileCount = 0;

        // Only check rows 0-31 (the valid data range)
        for (int tileY = 0; tileY < Math.min(32, heightTiles); tileY++) {
            for (int tileX = 0; tileX < widthTiles; tileX++) {
                int offset = (tileY * widthTiles + tileX) * 4;
                int r = data[offset] & 0xFF;
                int g = data[offset + 1] & 0xFF;
                int alpha = data[offset + 3] & 0xFF;
                int patIdx = r + ((g & 0x07) << 8);

                if (alpha > 0 && patIdx > 0) {
                    validTileCount++;
                    if (patIdx > maxPatIdx) maxPatIdx = patIdx;
                    if (patIdx >= patternCount) {
                        outOfRangeCount++;
                        if (outOfRangeCount <= 5) {
                            System.out.printf("  OUT OF RANGE: tileY=%d tileX=%d patIdx=%d (max=%d)%n",
                                    tileY, tileX, patIdx, patternCount);
                        }
                    }
                }
            }
        }

        System.out.println("Valid BG tiles (rows 0-31): " + validTileCount);
        System.out.println("Max BG pattern index: " + maxPatIdx);
        System.out.println("Out-of-range patterns (idx >= " + patternCount + "): " + outOfRangeCount);

        if (outOfRangeCount > 0) {
            System.out.println("*** WARNING: " + outOfRangeCount + " BG tiles reference patterns beyond atlas range!");
            System.out.println("*** These tiles will appear transparent/invisible in the shader.");
        }

        // This is the key assertion: ALL valid BG pattern indices must be within atlas range
        assertEquals("All BG pattern indices must be within atlas range (patternCount=" + patternCount + ")",
                0, outOfRangeCount);
    }

    @Test
    public void htzDynamicPatternsAreNonEmptyBeforeAndDuringQuake() throws Exception {
        Level level = levelManager.getCurrentLevel();
        assertNotNull("Level must be loaded", level);

        final int start = 0x500;
        final int end = 0x51F;

        System.out.println("=== HTZ Dynamic Pattern Coverage ===");
        System.out.println("Initial coverage:");
        for (int i = start; i <= end; i++) {
            Pattern p = level.getPattern(i);
            int nonZero = countNonZeroPixels(p);
            System.out.printf("  pat %04X: nonZero=%d%n", i, nonZero);
        }

        // Force earthquake mode and run a few frames so Dynamic_HTZ updates execute.
        Sonic2LevelEventManager events = Sonic2LevelEventManager.getInstance();
        events.initLevel(HTZ_ZONE, HTZ_ACT);
        GameServices.gameState().setHtzScreenShakeActive(true);
        GameServices.gameState().setScreenShakeActive(true);

        // Teleport close to earthquake trigger area to mimic in-game conditions.
        Camera camera = Camera.getInstance();
        camera.setX((short) 0x1800);
        camera.setY((short) 0x450);
        camera.updatePosition(true);

        // Run frames through the normal draw path trigger (via test runner style stepping).
        SonicConfigurationService configService = SonicConfigurationService.getInstance();
        String mainCode = configService.getString(SonicConfiguration.MAIN_CHARACTER_CODE);
        Sonic sonic = (Sonic) SpriteManager.getInstance().getSprite(mainCode);
        assertNotNull("Main Sonic sprite must exist", sonic);
        HeadlessTestRunner runner = new HeadlessTestRunner(sonic);
        for (int f = 0; f < 8; f++) {
            runner.stepFrame(false, false, false, false, false);
        }

        // Also force a direct Dynamic_HTZ update to inspect mountain tile writes.
        SwScrlHtz handler = new SwScrlHtz(null, new BackgroundCamera());
        int[] hScroll = new int[224];
        handler.update(hScroll, 6635, 0x440, 0, HTZ_ACT);
        DynamicHtz dyn = new DynamicHtz();
        dyn.init();
        dyn.update(level, 6635, handler);

        System.out.println("Coverage after quake frames:");
        int emptyCount = 0;
        for (int i = start; i <= end; i++) {
            Pattern p = level.getPattern(i);
            int nonZero = countNonZeroPixels(p);
            System.out.printf("  pat %04X: nonZero=%d%n", i, nonZero);
            if (nonZero == 0) {
                emptyCount++;
            }
        }

        assertTrue("Most HTZ dynamic patterns should contain non-zero pixels during quake",
                emptyCount < 8);
    }

    private static int countNonZeroPixels(Pattern pattern) {
        int count = 0;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                if ((pattern.getPixel(x, y) & 0xFF) != 0) {
                    count++;
                }
            }
        }
        return count;
    }

    @Test
    public void dumpCaveCorePatternCoverage() {
        Level level = levelManager.getCurrentLevel();
        assertNotNull(level);
        int[] core = {786, 787, 788, 789, 1300, 1301, 1302, 1303};
        System.out.println("=== HTZ Cave Core Pattern Coverage ===");
        for (int idx : core) {
            Pattern p = level.getPattern(idx);
            int nonZero = countNonZeroPixels(p);
            System.out.printf("  pat %d: nonZero=%d%n", idx, nonZero);
        }
    }

    @Test
    public void dumpChunkRefsToHtzDynamicTileRange() {
        Level level = levelManager.getCurrentLevel();
        assertNotNull(level);

        java.util.Set<Integer> referencedPatterns = new java.util.TreeSet<>();
        java.util.Set<Integer> chunksWithRefs = new java.util.TreeSet<>();
        int refs = 0;

        for (int ci = 0; ci < level.getChunkCount(); ci++) {
            Chunk chunk = level.getChunk(ci);
            boolean chunkHasRef = false;
            for (int y = 0; y < 2; y++) {
                for (int x = 0; x < 2; x++) {
                    int p = chunk.getPatternDesc(x, y).getPatternIndex();
                    if (p >= 0x500 && p <= 0x51F) {
                        refs++;
                        chunkHasRef = true;
                        referencedPatterns.add(p);
                    }
                }
            }
            if (chunkHasRef) {
                chunksWithRefs.add(ci);
            }
        }

        System.out.println("=== HTZ Chunk Refs to Dynamic Tile Range ===");
        System.out.println("Total refs: " + refs);
        System.out.println("Chunks with refs: " + chunksWithRefs.size());
        System.out.println("Patterns referenced: " + referencedPatterns);
        if (!chunksWithRefs.isEmpty()) {
            System.out.println("First chunk IDs: " + chunksWithRefs.stream().limit(20).toList());
        }
    }

    @Test
    public void dumpBgMapCellsUsingDynamicChunkTiles() {
        Level level = levelManager.getCurrentLevel();
        assertNotNull(level);
        Map map = level.getMap();
        assertNotNull(map);

        System.out.println("=== HTZ BG Cells Using Dynamic Tile Range ===");
        int hits = 0;

        for (int by = 0; by < map.getHeight(); by++) {
            for (int bx = 0; bx < map.getWidth(); bx++) {
                int blockIndex = map.getValue(1, bx, by) & 0xFF;
                if (blockIndex < 0 || blockIndex >= level.getBlockCount()) {
                    continue;
                }
                Block block = level.getBlock(blockIndex);
                if (block == null) {
                    continue;
                }

                boolean cellUsesDynamic = false;
                java.util.Set<Integer> dynPats = new java.util.TreeSet<>();
                for (int cy = 0; cy < 8; cy++) {
                    for (int cx = 0; cx < 8; cx++) {
                        ChunkDesc cd = block.getChunkDesc(cx, cy);
                        int chunkIdx = cd.getChunkIndex();
                        if (chunkIdx < 0 || chunkIdx >= level.getChunkCount()) {
                            continue;
                        }
                        Chunk chunk = level.getChunk(chunkIdx);
                        if (chunk == null) {
                            continue;
                        }
                        for (int py = 0; py < 2; py++) {
                            for (int px = 0; px < 2; px++) {
                                int p = chunk.getPatternDesc(px, py).getPatternIndex();
                                if (p >= 0x500 && p <= 0x51F) {
                                    dynPats.add(p);
                                    cellUsesDynamic = true;
                                }
                            }
                        }
                    }
                }

                if (cellUsesDynamic) {
                    hits++;
                    System.out.printf("  BG block cell (%d,%d) block#%d uses patterns %s%n",
                            bx, by, blockIndex, dynPats);
                }
            }
        }

        System.out.println("Total BG cells using dynamic range: " + hits);
    }

    @Test
    public void dumpBgTilemapCoverageAcrossBaseX() throws Exception {
        Method buildMethod = LevelManager.class.getDeclaredMethod("buildTilemapData", byte.class);
        buildMethod.setAccessible(true);
        Field baseField = LevelManager.class.getDeclaredField("bgTilemapBaseX");
        baseField.setAccessible(true);

        int[] bases = {0, 256, 512, 768, 1024, 1536, 2048, 6144, 6635};
        System.out.println("=== HTZ BG Tilemap Coverage vs bgTilemapBaseX ===");
        for (int base : bases) {
            baseField.setInt(levelManager, base);
            Object td = buildMethod.invoke(levelManager, (byte) 1);

            Field dataField = td.getClass().getDeclaredField("data");
            dataField.setAccessible(true);
            byte[] data = (byte[]) dataField.get(td);

            Field widthField = td.getClass().getDeclaredField("widthTiles");
            widthField.setAccessible(true);
            int width = widthField.getInt(td);

            int nonZeroPat = 0;
            int dynRefs = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < width; x++) {
                    int off = (y * width + x) * 4;
                    int r = data[off] & 0xFF;
                    int g = data[off + 1] & 0xFF;
                    int pat = r + ((g & 0x07) << 8);
                    if (pat >= 2) {
                        nonZeroPat++;
                    }
                    if (pat >= 0x500 && pat <= 0x51F) {
                        dynRefs++;
                    }
                }
            }

            System.out.printf("  base=%5d -> rows0-31 realTiles=%4d dynRefs=%3d%n",
                    base, nonZeroPat, dynRefs);
        }
    }
}
