package com.openggf.tests;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.configuration.SonicConfiguration;
import com.openggf.configuration.SonicConfigurationService;
import com.openggf.data.Game;
import com.openggf.data.Rom;
import com.openggf.game.DynamicStartPositionProvider;
import com.openggf.game.sonic3k.Sonic3k;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.Level;
import com.openggf.level.LevelData;
import com.openggf.level.Map;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import org.junit.After;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RequiresRom(SonicGame.SONIC_3K)
public class TestSonic3kLevelLoading {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private Game game;
    private Object oldSkipIntros;

    @Before
    public void setUp() throws Exception {
        SonicConfigurationService config = SonicConfigurationService.getInstance();
        oldSkipIntros = config.getConfigValue(SonicConfiguration.S3K_SKIP_INTROS);
        config.setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, true);
        Rom rom = romRule.rom();
        game = new Sonic3k(rom);
    }

    @After
    public void tearDown() {
        SonicConfigurationService.getInstance().setConfigValue(SonicConfiguration.S3K_SKIP_INTROS, oldSkipIntros != null ? oldSkipIntros : false);
    }

    @Test
    public void aiz1UsesRomStartPosition() throws Exception {
        assertTrue(game instanceof DynamicStartPositionProvider);
        DynamicStartPositionProvider provider = (DynamicStartPositionProvider) game;

        int[] start = provider.getStartPosition(0, 0);
        assertNotNull(start);
        assertEquals(2, start.length);

        // Known AIZ1 Sonic start from skdisasm: X=$13A0, Y=$041A
        assertEquals(0x13A0, start[0]);
        assertEquals(0x041A, start[1]);

        // Ensure we are not using static LevelData fallback values.
        assertNotEquals(LevelData.S3K_ANGEL_ISLAND_1.getStartXPos(), start[0]);
        assertNotEquals(LevelData.S3K_ANGEL_ISLAND_1.getStartYPos(), start[1]);
    }

    @Test
    public void aiz1LoadsWithValidResourceReferences() throws Exception {
        Level level = game.loadLevel(LevelData.S3K_ANGEL_ISLAND_1.getLevelIndex());
        assertTrue("AIZ1 intro-skip bootstrap should use intro-profile vertical bounds",
                level.getMaxY() >= 0x1000);
        assertLevelResourceIntegrity(level, 0, 0);
    }

    @Test
    public void fbz1LoadsWithValidResourceReferences() throws Exception {
        Level level = game.loadLevel(LevelData.S3K_FLYING_BATTERY_1.getLevelIndex());
        assertLevelResourceIntegrity(level, 4, 0);
    }

    private void assertLevelResourceIntegrity(Level level, int zone, int act) throws Exception {
        assertNotNull(level);
        assertNotNull(level.getMap());
        assertTrue(level.getPatternCount() > 0);
        assertTrue(level.getChunkCount() > 0);
        assertTrue(level.getBlockCount() > 0);

        int maxMapBlockIndex = 0;
        for (int layer = 0; layer < level.getMap().getLayerCount(); layer++) {
            for (int y = 0; y < level.getMap().getHeight(); y++) {
                for (int x = 0; x < level.getMap().getWidth(); x++) {
                    int blockIndex = Byte.toUnsignedInt(level.getMap().getValue(layer, x, y));
                    if (blockIndex > maxMapBlockIndex) {
                        maxMapBlockIndex = blockIndex;
                    }
                }
            }
        }

        assertTrue("Map should reference non-empty blocks", maxMapBlockIndex > 0);
        assertTrue("Map references invalid block index " + maxMapBlockIndex,
                maxMapBlockIndex < level.getBlockCount());

        int maxBlockChunkIndex = 0;
        for (int blockIdx = 0; blockIdx < level.getBlockCount(); blockIdx++) {
            Block block = level.getBlock(blockIdx);
            for (int y = 0; y < level.getChunksPerBlockSide(); y++) {
                for (int x = 0; x < level.getChunksPerBlockSide(); x++) {
                    int chunkIndex = block.getChunkDesc(x, y).getChunkIndex();
                    if (chunkIndex > maxBlockChunkIndex) {
                        maxBlockChunkIndex = chunkIndex;
                    }
                }
            }
        }
        assertTrue("Blocks reference invalid chunk index " + maxBlockChunkIndex,
                maxBlockChunkIndex < level.getChunkCount());

        int[] start = null;
        if (game instanceof DynamicStartPositionProvider provider) {
            start = provider.getStartPosition(zone, act);
        }
        if (start == null || start.length < 2) {
            LevelData fallback = LevelData.S3K_ANGEL_ISLAND_1;
            start = new int[]{fallback.getStartXPos(), fallback.getStartYPos()};
        }

        assertValidTilePathAt(level, start[0], start[1]);
    }

    private void assertValidTilePathAt(Level level, int worldX, int worldY) {
        Map map = level.getMap();
        int blockPixelSize = level.getBlockPixelSize();
        int blockX = Math.max(0, Math.min(map.getWidth() - 1, worldX / blockPixelSize));
        int blockY = Math.max(0, Math.min(map.getHeight() - 1, worldY / blockPixelSize));

        int blockIndex = Byte.toUnsignedInt(map.getValue(0, blockX, blockY));
        assertTrue("Spawn region references invalid block index " + blockIndex,
                blockIndex < level.getBlockCount());

        Block block = level.getBlock(blockIndex);
        int chunkSize = 16;
        int chunksPerSide = level.getChunksPerBlockSide();
        int localX = Math.floorMod(worldX, blockPixelSize);
        int localY = Math.floorMod(worldY, blockPixelSize);
        int chunkX = Math.min(chunksPerSide - 1, localX / chunkSize);
        int chunkY = Math.min(chunksPerSide - 1, localY / chunkSize);
        int chunkIndex = block.getChunkDesc(chunkX, chunkY).getChunkIndex();
        assertTrue("Spawn region references invalid chunk index " + chunkIndex,
                chunkIndex < level.getChunkCount());

        Chunk chunk = level.getChunk(chunkIndex);
        int patternX = (localX % chunkSize) / 8;
        int patternY = (localY % chunkSize) / 8;
        int patternIndex = chunk.getPatternDesc(patternX, patternY).getPatternIndex();
        assertTrue("Spawn region references invalid pattern index " + patternIndex,
                patternIndex < level.getPatternCount());
    }
}
