package com.openggf.game.sonic1.specialstage;

import com.openggf.graphics.WaterShaderProgram;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.openggf.graphics.GraphicsManager;
import com.openggf.graphics.PatternAtlas;
import com.openggf.level.Palette;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static com.openggf.game.sonic1.constants.Sonic1Constants.SS_BLOCK_SIZE_PX;
import static com.openggf.game.sonic1.constants.Sonic1Constants.SS_LAYOUT_STRIDE;

@RequiresRom(SonicGame.SONIC_1)
public class Sonic1SpecialStageManagerTest {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    private GraphicsManager graphicsManager;
    private Sonic1SpecialStageManager manager;

    @Before
    public void setUp() {
        GraphicsManager.getInstance().resetState();
        graphicsManager = GraphicsManager.getInstance();
        graphicsManager.initHeadless();
        manager = new Sonic1SpecialStageManager();
    }

    @After
    public void tearDown() {
        if (manager != null) {
            manager.reset();
        }
        if (graphicsManager != null) {
            graphicsManager.cleanup();
        }
        GraphicsManager.getInstance().resetState();
    }

    @Test
    public void testInitializeLoadsZonePatternBases() throws Exception {
        manager.initialize(0);
        assertTrue("Special stage manager should initialize", manager.isInitialized());

        Field rendererField = Sonic1SpecialStageManager.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        Sonic1SpecialStageRenderer renderer = (Sonic1SpecialStageRenderer) rendererField.get(manager);
        assertNotNull("Renderer should be created during initialization", renderer);

        Field zoneBasesField = Sonic1SpecialStageRenderer.class.getDeclaredField("zonePatternBases");
        zoneBasesField.setAccessible(true);
        int[] zoneBases = (int[]) zoneBasesField.get(renderer);
        assertNotNull("Zone pattern bases should be set", zoneBases);
        assertTrue("Should have 6 zone pattern bases", zoneBases.length == 6);

        int previousBase = -1;
        for (int i = 0; i < zoneBases.length; i++) {
            int base = zoneBases[i];
            assertTrue("Zone pattern base should be positive for zone " + (i + 1), base > 0);
            assertTrue("Zone pattern bases should increase monotonically", base > previousBase);
            previousBase = base;

            PatternAtlas.Entry entry = graphicsManager.getPatternAtlasEntry(base);
            assertNotNull("Pattern atlas entry missing for zone " + (i + 1) + " base " + base, entry);
        }
    }

    @Test
    public void testInitialDrawRendersStageBlocks() throws Exception {
        manager.initialize(0);
        manager.draw();

        Field rendererField = Sonic1SpecialStageManager.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        Sonic1SpecialStageRenderer renderer = (Sonic1SpecialStageRenderer) rendererField.get(manager);
        assertNotNull("Renderer should be created during initialization", renderer);

        assertTrue("Special stage should render at least one block on first draw",
                renderer.getLastRenderedBlocks() > 0);
        assertTrue("Special stage should detect valid block cells on first draw",
                renderer.getLastValidBlockCells() > 0);
    }

    @Test
    public void testUpdateDoesNotRunAwayFromSpawnImmediately() throws Exception {
        manager.initialize(0);
        for (int i = 0; i < 120; i++) {
            manager.update();
        }
        manager.draw();

        Field rendererField = Sonic1SpecialStageManager.class.getDeclaredField("renderer");
        rendererField.setAccessible(true);
        Sonic1SpecialStageRenderer renderer = (Sonic1SpecialStageRenderer) rendererField.get(manager);
        assertNotNull("Renderer should be created during initialization", renderer);
        assertTrue("Special stage should still render blocks after updates",
                renderer.getLastRenderedBlocks() > 0);
    }

    @Test
    public void testBackdropColorUsesResolvedSpecialPalette() throws Exception {
        manager.initialize(0);
        Palette.Color backdrop = manager.getBackdropColor();
        assertNotNull("Backdrop color should be available after initialization", backdrop);
        assertEquals("Backdrop red should match S1 special-stage palette", 0, backdrop.r & 0xFF);
        assertEquals("Backdrop green should match S1 special-stage palette", 0, backdrop.g & 0xFF);
        assertEquals("Backdrop blue should match S1 special-stage palette", 73, backdrop.b & 0xFF);
    }

    @Test
    public void testSonicAnimationFrameAdvancesDuringStage() throws Exception {
        manager.initialize(0);

        Field frameField = Sonic1SpecialStageManager.class.getDeclaredField("sonicSpriteFrame");
        frameField.setAccessible(true);

        Set<Integer> seenFrames = new HashSet<>();
        for (int i = 0; i < 180; i++) {
            manager.update();
            seenFrames.add(frameField.getInt(manager));
        }

        assertTrue("Sonic special-stage roll animation should advance through multiple frames",
                seenFrames.size() > 1);
    }

    @Test
    public void testSpecialStagePaletteCycleMutatesPaletteEntries() throws Exception {
        manager.initialize(0);

        Field palettesField = Sonic1SpecialStageManager.class.getDeclaredField("ssPalettes");
        palettesField.setAccessible(true);
        Palette[] palettes = (Palette[]) palettesField.get(manager);
        assertNotNull("Special-stage palettes should be loaded", palettes);
        assertTrue("Special-stage palette lines should be present", palettes.length == 4);

        Set<String> observedColors = new HashSet<>();
        for (int i = 0; i < 120; i++) {
            manager.update();
            Palette.Color c = palettes[2].getColor(7); // v_palette+$4E cycle target
            observedColors.add((c.r & 0xFF) + "," + (c.g & 0xFF) + "," + (c.b & 0xFF));
        }

        assertTrue("Special-stage palette cycle should change cycled colors over time",
                observedColors.size() > 1);
    }

    @Test
    public void testAnimCountersMatchRomStartupPhaseAfterFirstUpdate() throws Exception {
        manager.initialize(0);
        manager.update();

        Field ringAnimFrameField = Sonic1SpecialStageManager.class.getDeclaredField("ringAnimFrame");
        Field wallVramAnimFrameField = Sonic1SpecialStageManager.class.getDeclaredField("wallVramAnimFrame");
        Field ani2FrameField = Sonic1SpecialStageManager.class.getDeclaredField("ani2Frame");
        Field ani3FrameField = Sonic1SpecialStageManager.class.getDeclaredField("ani3Frame");
        ringAnimFrameField.setAccessible(true);
        wallVramAnimFrameField.setAccessible(true);
        ani2FrameField.setAccessible(true);
        ani3FrameField.setAccessible(true);

        assertEquals("ani1 should advance to frame 1 on first tick (ROM parity)",
                1, ringAnimFrameField.getInt(manager));
        assertEquals("ani0 should wrap to frame 7 on first tick (ROM parity)",
                7, wallVramAnimFrameField.getInt(manager));
        assertEquals("ani2 should advance to frame 1 on first tick (ROM parity)",
                1, ani2FrameField.getInt(manager));
        assertEquals("ani3 should advance to frame 1 on first tick (ROM parity)",
                1, ani3FrameField.getInt(manager));
    }

    @Test
    public void testDrawForcesSpecialStageShaderState() throws Exception {
        manager.initialize(0);

        graphicsManager.setUseWaterShader(true);
        graphicsManager.setUseSpritePriorityShader(true);
        graphicsManager.setCurrentSpriteHighPriority(true);
        graphicsManager.setWaterEnabled(true);
        graphicsManager.setUseUnderwaterPaletteForBackground(true);

        manager.draw();

        assertTrue("S1 special stage draw should disable water shader",
                !(graphicsManager.getShaderProgram() instanceof WaterShaderProgram));
        assertTrue("S1 special stage draw should disable sprite priority mode",
                !graphicsManager.isUseSpritePriorityShader());
        assertTrue("S1 special stage draw should clear sprite high-priority state",
                !graphicsManager.getCurrentSpriteHighPriority());
        assertTrue("S1 special stage draw should clear water-enabled state",
                !graphicsManager.isWaterEnabled());
        assertTrue("S1 special stage draw should disable underwater background palette mode",
                !graphicsManager.isUseUnderwaterPaletteForBackground());
    }

    @Test
    public void testResetClearsSpecialStageShaderState() throws Exception {
        manager.initialize(0);

        graphicsManager.setUseWaterShader(true);
        graphicsManager.setUseSpritePriorityShader(true);
        graphicsManager.setCurrentSpriteHighPriority(true);
        graphicsManager.setWaterEnabled(true);
        graphicsManager.setUseUnderwaterPaletteForBackground(true);

        manager.reset();

        assertTrue("S1 special stage reset should disable water shader",
                !(graphicsManager.getShaderProgram() instanceof WaterShaderProgram));
        assertTrue("S1 special stage reset should disable sprite priority mode",
                !graphicsManager.isUseSpritePriorityShader());
        assertTrue("S1 special stage reset should clear sprite high-priority state",
                !graphicsManager.getCurrentSpriteHighPriority());
        assertTrue("S1 special stage reset should clear water-enabled state",
                !graphicsManager.isWaterEnabled());
        assertTrue("S1 special stage reset should disable underwater background palette mode",
                !graphicsManager.isUseUnderwaterPaletteForBackground());
    }

    @Test
    public void testCollectingEmeraldTriggersExitSequence() throws Exception {
        manager.initialize(0);

        Field sonicPosXField = Sonic1SpecialStageManager.class.getDeclaredField("sonicPosX");
        Field sonicPosYField = Sonic1SpecialStageManager.class.getDeclaredField("sonicPosY");
        Field layoutField = Sonic1SpecialStageManager.class.getDeclaredField("layout");
        Field exitTriggeredField = Sonic1SpecialStageManager.class.getDeclaredField("exitTriggered");
        Method checkItemsMethod = Sonic1SpecialStageManager.class.getDeclaredMethod("checkItems");
        sonicPosXField.setAccessible(true);
        sonicPosYField.setAccessible(true);
        layoutField.setAccessible(true);
        exitTriggeredField.setAccessible(true);
        checkItemsMethod.setAccessible(true);

        long sonicPosX = sonicPosXField.getLong(manager);
        long sonicPosY = sonicPosYField.getLong(manager);
        byte[] layout = (byte[]) layoutField.get(manager);

        int posX = (int) (sonicPosX >> 16);
        int posY = (int) (sonicPosY >> 16);
        int gridCol = (posX + 0x20) / SS_BLOCK_SIZE_PX;
        int gridRow = (posY + 0x50) / SS_BLOCK_SIZE_PX;
        int layoutIndex = gridRow * SS_LAYOUT_STRIDE + gridCol;
        layout[layoutIndex] = 0x3B;

        checkItemsMethod.invoke(manager);

        assertTrue("Emerald collection flag should be set", manager.isEmeraldCollected());
        assertTrue("Collecting an emerald should trigger special-stage exit",
                exitTriggeredField.getBoolean(manager));
    }

    @Test
    public void testGlassBlockUsesAnimationCooldownBeforeAdvancingState() throws Exception {
        manager.initialize(0);

        Field layoutField = Sonic1SpecialStageManager.class.getDeclaredField("layout");
        Field lastCollisionBlockIdField = Sonic1SpecialStageManager.class.getDeclaredField("lastCollisionBlockId");
        Field lastCollisionRowField = Sonic1SpecialStageManager.class.getDeclaredField("lastCollisionRow");
        Field lastCollisionColField = Sonic1SpecialStageManager.class.getDeclaredField("lastCollisionCol");
        Method processItemInteractionMethod = Sonic1SpecialStageManager.class.getDeclaredMethod("processItemInteraction");
        Method updateItemAnimationsMethod = Sonic1SpecialStageManager.class.getDeclaredMethod("updateItemAnimations");

        layoutField.setAccessible(true);
        lastCollisionBlockIdField.setAccessible(true);
        lastCollisionRowField.setAccessible(true);
        lastCollisionColField.setAccessible(true);
        processItemInteractionMethod.setAccessible(true);
        updateItemAnimationsMethod.setAccessible(true);

        byte[] layout = (byte[]) layoutField.get(manager);
        int row = 0;
        int col = 0;
        int layoutIndex = row * SS_LAYOUT_STRIDE + col;

        layout[layoutIndex] = 0x2D;
        lastCollisionBlockIdField.setInt(manager, 0x2D);
        lastCollisionRowField.setInt(manager, row);
        lastCollisionColField.setInt(manager, col);

        processItemInteractionMethod.invoke(manager);
        assertEquals("Glass should not advance immediately before animation tick",
                0x2D, layout[layoutIndex] & 0xFF);

        updateItemAnimationsMethod.invoke(manager);
        assertEquals("First animation tick should move glass into transitional state",
                0x4B, layout[layoutIndex] & 0xFF);

        for (int i = 0; i < 32; i++) {
            updateItemAnimationsMethod.invoke(manager);
        }

        assertEquals("After one full glass animation, block should advance by one hit state",
                0x2E, layout[layoutIndex] & 0xFF);
    }
}
