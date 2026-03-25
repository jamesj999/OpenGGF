package com.openggf.game.sonic1.specialstage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.openggf.graphics.GraphicsManager;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static com.openggf.game.sonic1.constants.Sonic1Constants.ARTTILE_RING;
import static com.openggf.game.sonic1.constants.Sonic1Constants.SS_LAYOUT_STRIDE;

public class Sonic1SpecialStageRendererTest {
    private static final int TEST_PATTERN_BASE = 0x10000;

    private GraphicsManager graphicsManager;
    private Sonic1SpecialStageRenderer renderer;

    @Before
    public void setUp() {
        GraphicsManager.getInstance().resetState();
        graphicsManager = GraphicsManager.getInstance();
        graphicsManager.initHeadless();
        renderer = new Sonic1SpecialStageRenderer(graphicsManager);

        renderer.setPatternBases(
                TEST_PATTERN_BASE,
                TEST_PATTERN_BASE + 0x100,
                TEST_PATTERN_BASE + 0x200,
                TEST_PATTERN_BASE + 0x300,
                TEST_PATTERN_BASE + 0x400,
                TEST_PATTERN_BASE + 0x500,
                TEST_PATTERN_BASE + 0x600,
                TEST_PATTERN_BASE + 0x700,
                TEST_PATTERN_BASE + 0x800,
                TEST_PATTERN_BASE + 0x900,
                TEST_PATTERN_BASE + 0xA00,
                TEST_PATTERN_BASE + 0xB00,
                TEST_PATTERN_BASE + 0xC00,
                TEST_PATTERN_BASE + 0xD00,
                TEST_PATTERN_BASE + 0xD20,
                TEST_PATTERN_BASE + 0xD40,
                TEST_PATTERN_BASE + 0xD60,
                TEST_PATTERN_BASE + 0xD80,
                TEST_PATTERN_BASE + 0xDA0,
                TEST_PATTERN_BASE + 0xE00,
                TEST_PATTERN_BASE + 0xE80
        );
    }

    @After
    public void tearDown() {
        if (graphicsManager != null) {
            graphicsManager.cleanup();
        }
        GraphicsManager.getInstance().resetState();
    }

    @Test
    public void testRenderHandlesOutOfBoundsCameraWithoutException() {
        byte[] layout = new byte[SS_LAYOUT_STRIDE];
        layout[0] = 0x01;
        layout[1] = 0x34;
        layout[2] = 0x3A;

        try {
            renderer.render(layout, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
            renderer.render(layout, 0x4000, 0x7FFF, 0x7FFF, 0, 0, 0, 0, 0, 0, 0, false);
            renderer.render(layout, 0x8000, 0x7FFF_FFFF, 0x7FFF_FFFF, 0, 0, 0, 0, 0, 0, 0, true);
        } catch (Exception ex) {
            fail("Renderer should handle extreme camera values without exceptions: " + ex.getMessage());
        }
    }

    @Test
    public void testRenderHandlesEmptyLayoutWithoutException() {
        byte[] emptyLayout = new byte[0];
        try {
            renderer.render(emptyLayout, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
        } catch (Exception ex) {
            fail("Renderer should handle empty layout without exceptions: " + ex.getMessage());
        }
    }

    @Test
    public void testRingArtTileMapsToRingPatternBase() throws Exception {
        Method mappingMethod = Sonic1SpecialStageRenderer.class
                .getDeclaredMethod("getPatternBaseForArtTile", int.class);
        mappingMethod.setAccessible(true);

        int ringBase = (int) mappingMethod.invoke(renderer, ARTTILE_RING);
        assertEquals("Ring art tile should resolve to dedicated ring pattern base",
                TEST_PATTERN_BASE + 0xC00, ringBase);
    }

    @Test
    public void testSpecialStageUsesFullScreenViewport() {
        assertEquals("S1 special stage should use full-width viewport",
                320, Sonic1SpecialStageRenderer.H32_WIDTH);
        assertEquals("S1 special stage should use 224-line visible height",
                224, Sonic1SpecialStageRenderer.H32_HEIGHT);
        assertEquals("S1 special stage should not apply horizontal centering offset",
                0, Sonic1SpecialStageRenderer.SCREEN_CENTER_OFFSET);
    }
}
