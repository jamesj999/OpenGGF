package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.graphics.GraphicsManager;
import com.openggf.level.*;
import com.openggf.level.scroll.SwScrlHtz;
import com.openggf.level.scroll.BackgroundCamera;
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
}
