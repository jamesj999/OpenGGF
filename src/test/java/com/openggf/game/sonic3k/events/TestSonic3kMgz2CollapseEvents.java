package com.openggf.game.sonic3k.events;

import com.openggf.game.EngineServices;
import com.openggf.game.GameModuleRegistry;
import com.openggf.game.GameServices;
import com.openggf.game.RuntimeManager;
import com.openggf.game.sonic3k.Sonic3kGameModule;
import com.openggf.level.AbstractLevel;
import com.openggf.level.Block;
import com.openggf.level.Chunk;
import com.openggf.level.ChunkDesc;
import com.openggf.level.Map;
import com.openggf.level.Palette;
import com.openggf.level.Pattern;
import com.openggf.level.SolidTile;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the MGZ Act 2 collapse event flow.
 */
class TestSonic3kMgz2CollapseEvents {

    @BeforeEach
    void setUp() {
        RuntimeManager.configureEngineServices(EngineServices.fromLegacySingletonsForBootstrap());
        GameModuleRegistry.setCurrent(new Sonic3kGameModule());
        RuntimeManager.createGameplay();
        GameServices.level().setLevel(new SyntheticMgzCollapseLevel());
    }

    @AfterEach
    void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    void collapseTrigger_clearsOpeningRegionOnFirstUpdate() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        events.triggerCollapseForTest();

        events.update(1, 0);

        SyntheticMgzCollapseLevel level = (SyntheticMgzCollapseLevel) GameServices.level().getCurrentLevel();
        assertTrue(events.isCollapseActive());
        assertEquals(1, events.getCollapseMutationCount());
        assertEquals(4, events.getScreenEventRoutine());
        assertCleared(level.getMap(), 121, 14, 3, 3);
    }

    @Test
    void collapseProgression_finishesAndClearsLowerRegion() {
        Sonic3kMGZEvents events = new Sonic3kMGZEvents();
        events.init(1);
        events.triggerCollapseForTest();

        for (int frame = 0; frame < 512 && !events.isCollapseFinished(); frame++) {
            events.update(1, frame);
        }

        SyntheticMgzCollapseLevel level = (SyntheticMgzCollapseLevel) GameServices.level().getCurrentLevel();
        assertTrue(events.isCollapseFinished());
        assertFalse(events.isCollapseActive());
        assertEquals(8, events.getScreenEventRoutine());
        assertEquals(2, events.getCollapseMutationCount());
        assertCleared(level.getMap(), 121, 11, 3, 3);
    }

    private static void assertCleared(Map map, int startX, int startY, int width, int height) {
        for (int y = startY; y < startY + height; y++) {
            for (int x = startX; x < startX + width; x++) {
                assertEquals(0, map.getValue(0, x, y),
                        "expected cleared foreground block at (" + x + "," + y + ")");
            }
        }
    }

    private static final class SyntheticMgzCollapseLevel extends AbstractLevel {

        private SyntheticMgzCollapseLevel() {
            super(0);
            palettes = new Palette[] { new Palette(), new Palette(), new Palette(), new Palette() };
            patterns = new Pattern[] { new Pattern(), new Pattern(), new Pattern(), new Pattern() };
            patternCount = patterns.length;
            chunks = new Chunk[] { buildChunk() };
            chunkCount = chunks.length;
            blocks = buildBlocks();
            blockCount = blocks.length;
            solidTiles = new SolidTile[] {
                    new SolidTile(0, new byte[SolidTile.TILE_SIZE_IN_ROM],
                            new byte[SolidTile.TILE_SIZE_IN_ROM], (byte) 0)
            };
            solidTileCount = solidTiles.length;
            map = new Map(2, 128, 32);
            fillMap((byte) 1);
            objects = new ArrayList<ObjectSpawn>();
            rings = new ArrayList<RingSpawn>();
            ringSpriteSheet = null;
            minX = 0;
            maxX = 0x8000;
            minY = 0;
            maxY = 0x2000;
        }

        private void fillMap(byte value) {
            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    map.setValue(0, x, y, value);
                    map.setValue(1, x, y, value);
                }
            }
        }

        private static Chunk buildChunk() {
            Chunk chunk = new Chunk();
            chunk.restoreState(new int[] { 0, 1, 2, 3, 0, 0 });
            return chunk;
        }

        private static Block[] buildBlocks() {
            Block[] blocks = new Block[80];
            for (int blockIndex = 0; blockIndex < blocks.length; blockIndex++) {
                Block block = new Block();
                int[] state = new int[64];
                for (int i = 0; i < state.length; i++) {
                    state[i] = new ChunkDesc((blockIndex + i) & 0x03FF).get();
                }
                block.restoreState(state);
                blocks[blockIndex] = block;
            }
            return blocks;
        }

        @Override
        public RingSpriteSheet getRingSpriteSheet() {
            return null;
        }
    }
}
