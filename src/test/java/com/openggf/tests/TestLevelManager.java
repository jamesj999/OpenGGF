package com.openggf.tests;

import com.openggf.camera.Camera;
import com.openggf.game.GameServices;
import com.openggf.game.ZoneFeatureProvider;
import com.openggf.game.render.SpecialRenderEffect;
import com.openggf.game.render.SpecialRenderEffectContext;
import com.openggf.game.render.SpecialRenderEffectRegistry;
import com.openggf.game.render.SpecialRenderEffectStage;
import com.openggf.level.*;
import com.openggf.level.rings.RingSpawn;
import com.openggf.graphics.GraphicsManager;
import com.openggf.sprites.managers.SpriteManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import com.openggf.level.objects.ObjectSpawn;
import com.openggf.level.rings.RingSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestLevelManager {

    @BeforeEach
    public void setUp() {
        TestEnvironment.resetAll();
    }

    @Test
    public void testGetBlockAtPositionWithLargeIndex() throws Exception {
        // Setup a mock level
        MockLevel level = new MockLevel();

        // Inject into LevelManager
        LevelManager levelManager = GameServices.level();
        Field levelField = LevelManager.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(levelManager, level);

        // Try to access the block at (0,0) which is mapped to index 128 (0x80)
        // LevelManager.getChunkDescAt calls getBlockAtPosition
        ChunkDesc chunkDesc = levelManager.getChunkDescAt((byte) 0, 0, 0);

        assertNotNull(chunkDesc, "ChunkDesc should not be null for block index 128");
    }

    @Test
    public void testProcessDirtyRegionsConsumesSolidTileDirtySet() throws Exception {
        LevelManager levelManager = GameServices.level();
        MutableLevel mutableLevel = MutableLevel.snapshot(new MutableMockLevel());

        byte[] heights = new byte[SolidTile.TILE_SIZE_IN_ROM];
        byte[] widths = new byte[SolidTile.TILE_SIZE_IN_ROM];
        heights[0] = 7;
        widths[0] = 4;
        mutableLevel.setSolidTile(0, new SolidTile(0, heights, widths, (byte) 12));

        Field levelField = LevelManager.class.getDeclaredField("level");
        levelField.setAccessible(true);
        levelField.set(levelManager, mutableLevel);

        levelManager.processDirtyRegions();

        assertTrue(mutableLevel.consumeDirtySolidTiles().isEmpty(), "Solid-tile dirty set should be consumed by the frame pipeline");
    }

    @Test
    public void dispatchSpecialRenderEffectsInvokesRegisteredStageEffects() throws Exception {
        LevelManager levelManager = GameServices.level();
        SpecialRenderEffectRegistry registry = GameServices.specialRenderEffectRegistry();
        registry.clear();
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger frameCounter = new AtomicInteger(-1);

        registry.register(new SpecialRenderEffect() {
            @Override
            public SpecialRenderEffectStage stage() {
                return SpecialRenderEffectStage.AFTER_FOREGROUND;
            }

            @Override
            public void render(SpecialRenderEffectContext context) {
                calls.incrementAndGet();
                frameCounter.set(context.frameCounter());
            }
        });

        Method dispatch = LevelManager.class.getDeclaredMethod(
                "dispatchSpecialRenderEffects", SpecialRenderEffectStage.class, int.class);
        dispatch.setAccessible(true);
        dispatch.invoke(levelManager, SpecialRenderEffectStage.AFTER_FOREGROUND, 77);

        assertEquals(1, calls.get(), "LevelManager should dispatch registered stage effects");
        assertEquals(77, frameCounter.get(), "Stage dispatch should preserve frame counter");
    }

    @Test
    public void seamlessReloadFrameCounterBridgeAdvancesStoredLevelAndSpriteCounters() throws Exception {
        LevelManager levelManager = GameServices.level();
        SpriteManager spriteManager = GameServices.sprites();
        Field levelCounter = LevelManager.class.getDeclaredField("frameCounter");
        levelCounter.setAccessible(true);
        levelCounter.setInt(levelManager, 0x153F);
        spriteManager.setFrameCounter(0x153F);

        Method advance = LevelManager.class.getDeclaredMethod("advanceFrameCounterAcrossSeamlessReload");
        advance.setAccessible(true);
        advance.invoke(levelManager);

        assertEquals(0x1540, levelManager.getFrameCounter(),
                "S3K Tails CPU reads the stored Level_frame_counter cadence after seamless reloads");
        assertEquals(0x1540, spriteManager.getFrameCounter(),
                "SpriteManager's gameplay counter should stay aligned across skipped reload frames");
    }

    @Nested
    @RequiresRom(SonicGame.SONIC_2)
    class RomBackedZoneFeatureTests {
        @Test
        public void initializeZoneFeatureProviderRegistersSpecialRenderEffects() throws Exception {
            LevelManager levelManager = GameServices.level();
            SpecialRenderEffectRegistry registry = GameServices.specialRenderEffectRegistry();
            registry.clear();

            Method initProvider = LevelManager.class.getDeclaredMethod(
                    "initializeZoneFeatureProvider", ZoneFeatureProvider.class);
            initProvider.setAccessible(true);
            initProvider.invoke(levelManager, new RegisteringZoneFeatureProvider());

            assertFalse(registry.isEmpty(), "Zone feature init should register special render effects");
        }

        @Test
        public void reinitializeZoneFeaturesClearsExistingRenderRegistriesBeforeReregisteringProvider() throws Exception {
            LevelManager levelManager = GameServices.level();
            SpecialRenderEffectRegistry registry = GameServices.specialRenderEffectRegistry();
            registry.clear();
            GameServices.advancedRenderModeController().clear();

            registry.register(new SpecialRenderEffect() {
                @Override
                public SpecialRenderEffectStage stage() {
                    return SpecialRenderEffectStage.AFTER_BACKGROUND;
                }

                @Override
                public void render(SpecialRenderEffectContext context) {
                    // no-op
                }
            });
            GameServices.advancedRenderModeController().register(new com.openggf.game.render.AdvancedRenderMode() {
                @Override
                public String id() {
                    return "preexisting-test-mode";
                }

                @Override
                public void contribute(
                        com.openggf.game.render.AdvancedRenderModeContext context,
                        com.openggf.game.render.AdvancedRenderFrameState.Builder builder) {
                    builder.enableForegroundHeatHaze();
                }
            });

            Field providerField = LevelManager.class.getDeclaredField("zoneFeatureProvider");
            providerField.setAccessible(true);
            providerField.set(levelManager, new RegisteringZoneFeatureProvider());

            Method reinitialize = LevelManager.class.getDeclaredMethod("reinitializeZoneFeaturesForActTransition");
            reinitialize.setAccessible(true);
            reinitialize.invoke(levelManager);

            assertEquals(1, registry.size(SpecialRenderEffectStage.AFTER_BACKGROUND),
                    "Existing effects should be cleared before zone features re-register current ones");
            assertEquals(1, GameServices.advancedRenderModeController().size(),
                    "Existing advanced render modes should be cleared before zone features re-register current ones");
        }
    }

    private static class MockLevel implements Level {
        private final Map map;
        private final Block validBlock;

        public MockLevel() {
            // Map 1x1, 1 layer
            map = new Map(1, 1, 1);
            // Set value at (0,0) to -128 (which is index 128)
            map.setValue(0, 0, 0, (byte) -128);

            validBlock = new Block();
        }

        @Override public int getPaletteCount() { return 0; }
        @Override public Palette getPalette(int index) { return null; }
        @Override public int getPatternCount() { return 0; }
        @Override public Pattern getPattern(int index) { return null; }
        @Override public int getChunkCount() { return 0; }
        @Override public Chunk getChunk(int index) { return null; }
        @Override public int getBlockCount() { return 256; }

        @Override
        public Block getBlock(int index) {
            if (index == 128) {
                return validBlock;
            }
            return null;
        }

        @Override public SolidTile getSolidTile(int index) { return null; }
        @Override public Map getMap() { return map; }
        @Override public java.util.List<ObjectSpawn> getObjects() { return java.util.Collections.emptyList(); }
        @Override public java.util.List<RingSpawn> getRings() { return java.util.Collections.emptyList(); }
        @Override public RingSpriteSheet getRingSpriteSheet() { return null; }
        @Override public int getMinX() { return 0; }
        @Override public int getMaxX() { return 0; }
        @Override public int getMinY() { return 0; }
        @Override public int getMaxY() { return 0; }
        @Override public int getZoneIndex() { return 0; }
        public short getStartX() { return 0; }
        public short getStartY() { return 0; }
    }

    private static class MutableMockLevel extends MockLevel {
        private final SolidTile solidTile;

        MutableMockLevel() {
            byte[] heights = new byte[SolidTile.TILE_SIZE_IN_ROM];
            byte[] widths = new byte[SolidTile.TILE_SIZE_IN_ROM];
            heights[0] = 5;
            widths[0] = 3;
            solidTile = new SolidTile(0, heights, widths, (byte) 42);
        }

        @Override
        public int getSolidTileCount() {
            return 1;
        }

        @Override
        public SolidTile getSolidTile(int index) {
            if (index != 0) {
                return null;
            }
            return solidTile;
        }

        @Override
        public int getPatternCount() {
            return 1;
        }

        @Override
        public Pattern getPattern(int index) {
            return new Pattern();
        }

        @Override
        public int getChunkCount() {
            return 1;
        }

        @Override
        public Chunk getChunk(int index) {
            Chunk chunk = new Chunk();
            int[] state = new int[Chunk.PATTERNS_PER_CHUNK + 2];
            state[0] = 1;
            chunk.restoreState(state);
            return chunk;
        }

        @Override
        public Block getBlock(int index) {
            Block block = new Block();
            block.setChunkDesc(0, 0, new ChunkDesc(0));
            return block;
        }

        @Override
        public int getPaletteCount() {
            return 4;
        }

        @Override
        public Palette getPalette(int index) {
            return new Palette();
        }
    }

    private static final class RegisteringZoneFeatureProvider implements ZoneFeatureProvider {
        @Override
        public void initZoneFeatures(com.openggf.data.Rom rom, int zoneIndex, int actIndex, int cameraX) {
            // No-op
        }

        @Override
        public void update(com.openggf.sprites.playable.AbstractPlayableSprite player, int cameraX, int zoneIndex) {
            // No-op
        }

        @Override
        public void reset() {
            // No-op
        }

        @Override
        public boolean hasCollisionFeatures(int zoneIndex) {
            return false;
        }

        @Override
        public boolean hasWater(int zoneIndex) {
            return false;
        }

        @Override
        public int getWaterLevel(int zoneIndex, int actIndex) {
            return Integer.MAX_VALUE;
        }

        @Override
        public void render(Camera camera, int frameCounter) {
            // No-op
        }

        @Override
        public int ensurePatternsCached(GraphicsManager graphicsManager, int baseIndex) {
            return baseIndex;
        }

        @Override
        public void registerSpecialRenderEffects(SpecialRenderEffectRegistry registry, int zoneIndex, int actIndex) {
            registry.register(new SpecialRenderEffect() {
                @Override
                public SpecialRenderEffectStage stage() {
                    return SpecialRenderEffectStage.AFTER_BACKGROUND;
                }

                @Override
                public void render(SpecialRenderEffectContext context) {
                    // No-op
                }
            });
        }

        @Override
        public void registerAdvancedRenderModes(
                com.openggf.game.render.AdvancedRenderModeController controller, int zoneIndex, int actIndex) {
            controller.register(new com.openggf.game.render.AdvancedRenderMode() {
                @Override
                public String id() {
                    return "registering-zone-feature-provider";
                }

                @Override
                public void contribute(
                        com.openggf.game.render.AdvancedRenderModeContext context,
                        com.openggf.game.render.AdvancedRenderFrameState.Builder builder) {
                    builder.enablePerLineForegroundScroll();
                }
            });
        }
    }
}


