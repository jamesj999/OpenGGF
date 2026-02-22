package uk.co.jamesj999.sonic.graphics;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import uk.co.jamesj999.sonic.game.GameId;

import static org.junit.Assert.*;

/**
 * Tests for {@link RenderContext} static registry and instance behavior.
 */
public class TestRenderContext {

    @Before
    public void setUp() {
        RenderContext.reset();
    }

    @After
    public void tearDown() {
        RenderContext.reset();
    }

    @Test
    public void baseGameOccupiesLines0Through3() {
        // With no donors, total lines should be 4 (base game only)
        assertEquals(4, RenderContext.getTotalPaletteLines());
    }

    @Test
    public void firstDonorGetsLines4Through7() {
        RenderContext ctx = RenderContext.getOrCreateDonor(GameId.S2);
        assertEquals(4, ctx.getPaletteLineBase());
        assertEquals(8, RenderContext.getTotalPaletteLines());
    }

    @Test
    public void secondDonorGetsLines8Through11() {
        RenderContext.getOrCreateDonor(GameId.S2);
        RenderContext ctx = RenderContext.getOrCreateDonor(GameId.S3K);
        assertEquals(8, ctx.getPaletteLineBase());
        assertEquals(12, RenderContext.getTotalPaletteLines());
    }

    @Test
    public void getOrCreateReturnsSameInstanceForSameGame() {
        RenderContext first = RenderContext.getOrCreateDonor(GameId.S2);
        RenderContext second = RenderContext.getOrCreateDonor(GameId.S2);
        assertSame(first, second);
        // Still only 8 total lines (not 12)
        assertEquals(8, RenderContext.getTotalPaletteLines());
    }

    @Test
    public void getEffectivePaletteLineRemapsLogicalLine() {
        RenderContext ctx = RenderContext.getOrCreateDonor(GameId.S2);
        // Logical line 0 -> effective line 4
        assertEquals(4, ctx.getEffectivePaletteLine(0));
        // Logical line 1 -> effective line 5
        assertEquals(5, ctx.getEffectivePaletteLine(1));
        // Logical line 3 -> effective line 7
        assertEquals(7, ctx.getEffectivePaletteLine(3));
    }

    @Test
    public void resetClearsAllDonorContexts() {
        RenderContext.getOrCreateDonor(GameId.S2);
        RenderContext.getOrCreateDonor(GameId.S3K);
        assertEquals(12, RenderContext.getTotalPaletteLines());

        RenderContext.reset();

        assertEquals(4, RenderContext.getTotalPaletteLines());

        // After reset, creating S2 again gets lines 4-7 (not 12-15)
        RenderContext ctx = RenderContext.getOrCreateDonor(GameId.S2);
        assertEquals(4, ctx.getPaletteLineBase());
    }

    @Test
    public void gameIdReturnsCorrectValue() {
        RenderContext ctx = RenderContext.getOrCreateDonor(GameId.S3K);
        assertEquals(GameId.S3K, ctx.getGameId());
    }

    @Test
    public void paletteStorageAndRetrieval() {
        RenderContext ctx = RenderContext.getOrCreateDonor(GameId.S2);
        assertNull(ctx.getPalette(0));

        uk.co.jamesj999.sonic.level.Palette palette = new uk.co.jamesj999.sonic.level.Palette();
        ctx.setPalette(0, palette);
        assertSame(palette, ctx.getPalette(0));
        assertNull(ctx.getPalette(1));
    }
}
