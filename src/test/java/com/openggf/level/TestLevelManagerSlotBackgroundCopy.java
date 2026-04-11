package com.openggf.level;

import com.openggf.game.GameRuntime;
import com.openggf.game.RuntimeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestLevelManagerSlotBackgroundCopy {
    private GameRuntime runtime;

    @BeforeEach
    public void setUp() {
        runtime = RuntimeManager.createGameplay();
    }

    @AfterEach
    public void tearDown() {
        RuntimeManager.destroyCurrent();
    }

    @Test
    public void slotBackgroundRowCopyUpdatesBothEightPixelRowsOfSixteenPixelBlockRow() throws Exception {
        TestLevelManager levelManager = new TestLevelManager(runtime);
        RecordingTilemapManager tilemapManager = new RecordingTilemapManager();
        setTilemapManager(levelManager, tilemapManager);

        levelManager.copyBackgroundTileRowFromWorldToVdpPlane(0x40, 0x20, 0xE000, 1);

        assertEquals(levelManager.descriptorFor(0x40, 0x20), tilemapManager.descriptorAt(0, 0));
        assertEquals(levelManager.descriptorFor(0x48, 0x20), tilemapManager.descriptorAt(1, 0));
        assertEquals(levelManager.descriptorFor(0x40, 0x28), tilemapManager.descriptorAt(0, 1));
        assertEquals(levelManager.descriptorFor(0x48, 0x28), tilemapManager.descriptorAt(1, 1));
    }

    @Test
    public void slotBackgroundRowCopySnapsSourceXToSixteenPixelBlockBoundary() throws Exception {
        TestLevelManager levelManager = new TestLevelManager(runtime);
        RecordingTilemapManager tilemapManager = new RecordingTilemapManager();
        setTilemapManager(levelManager, tilemapManager);

        levelManager.copyBackgroundTileRowFromWorldToVdpPlane(0x48, 0x20, 0xE000, 1);

        assertEquals(levelManager.descriptorFor(0x40, 0x20), tilemapManager.descriptorAt(0, 0));
        assertEquals(levelManager.descriptorFor(0x48, 0x20), tilemapManager.descriptorAt(1, 0));
        assertEquals(levelManager.descriptorFor(0x40, 0x28), tilemapManager.descriptorAt(0, 1));
        assertEquals(levelManager.descriptorFor(0x48, 0x28), tilemapManager.descriptorAt(1, 1));
    }

    private static void setTilemapManager(LevelManager levelManager, LevelTilemapManager tilemapManager)
            throws Exception {
        Field field = LevelManager.class.getDeclaredField("tilemapManager");
        field.setAccessible(true);
        field.set(levelManager, tilemapManager);
    }

    private static final class TestLevelManager extends LevelManager {
        private TestLevelManager(GameRuntime runtime) {
            super(runtime.getCamera(), runtime.getSpriteManager(), runtime.getParallaxManager(),
                    runtime.getCollisionSystem(), runtime.getWaterSystem(), runtime.getGameState(),
                    runtime.getEngineServices());
        }

        @Override
        public int getBackgroundTileDescriptorAtWorld(int worldX, int worldY) {
            return descriptorFor(worldX, worldY);
        }

        int descriptorFor(int worldX, int worldY) {
            return ((worldY & 0xFF) << 8) | (worldX & 0xFF);
        }
    }

    private static final class RecordingTilemapManager extends LevelTilemapManager {
        private final Map<Integer, Integer> writes = new HashMap<>();

        RecordingTilemapManager() {
            super(null, null);
        }

        @Override
        public void ensureBackgroundTilemapData(BlockLookup blockLookup, com.openggf.game.ZoneFeatureProvider zoneFeatureProvider,
                                                int currentZone, ParallaxManager parallaxManager,
                                                boolean verticalWrapEnabled) {
        }

        @Override
        public int getBackgroundTilemapWidthTiles() {
            return 64;
        }

        @Override
        public int getBackgroundTilemapHeightTiles() {
            return 32;
        }

        @Override
        public boolean setBackgroundTileDescriptorAtTilemapCell(int tileX, int tileY, int descriptor) {
            writes.put(key(tileX, tileY), descriptor);
            return true;
        }

        int descriptorAt(int tileX, int tileY) {
            return writes.getOrDefault(key(tileX, tileY), -1);
        }

        private static int key(int tileX, int tileY) {
            return (tileY << 8) | tileX;
        }
    }
}


