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

    // --- deriveUnderwaterPalette tests ---
    // Uses GLOBAL average per-channel ratio (not per-index), so donor sprites
    // with different palette layouts (e.g., Tails in S1) get a consistent tint.

    @Test
    public void deriveUnderwaterPalette_appliesGlobalAverageRatio() {
        // Base: 2 non-transparent colors. Normal avg R=(200+100)/2=150, UW avg R=(100+50)/2=75
        // Global ratio R = 75/150 = 0.5
        uk.co.jamesj999.sonic.level.Palette normalBase = new uk.co.jamesj999.sonic.level.Palette();
        normalBase.setColor(1, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 200, (byte) 100, (byte) 50));
        normalBase.setColor(2, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 100, (byte) 100, (byte) 50));

        uk.co.jamesj999.sonic.level.Palette underwaterBase = new uk.co.jamesj999.sonic.level.Palette();
        underwaterBase.setColor(1, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 100, (byte) 50, (byte) 25));
        underwaterBase.setColor(2, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 50, (byte) 50, (byte) 25));

        // Donor: color 3 has completely different meaning than base colors 1-2
        uk.co.jamesj999.sonic.level.Palette donorNormal = new uk.co.jamesj999.sonic.level.Palette();
        donorNormal.setColor(3, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 180, (byte) 80, (byte) 40));

        uk.co.jamesj999.sonic.level.Palette result = RenderContext.deriveUnderwaterPalette(
                donorNormal, normalBase, underwaterBase);

        // Global ratios: R=(100+50)/(200+100)=150/300=0.5, G=(50+50)/(100+100)=100/200=0.5, B=(25+25)/(50+50)=50/100=0.5
        // Donor color 3: (180*128/256, 80*128/256, 40*128/256) = (90, 40, 20)
        uk.co.jamesj999.sonic.level.Palette.Color c = result.getColor(3);
        assertEquals(90, Byte.toUnsignedInt(c.r));
        assertEquals(40, Byte.toUnsignedInt(c.g));
        assertEquals(20, Byte.toUnsignedInt(c.b));
    }

    @Test
    public void deriveUnderwaterPalette_appliesUniformTintAcrossAllDonorColors() {
        // Same base palettes — ratio ~0.5 across all channels
        uk.co.jamesj999.sonic.level.Palette normalBase = new uk.co.jamesj999.sonic.level.Palette();
        normalBase.setColor(1, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 200, (byte) 200, (byte) 200));

        uk.co.jamesj999.sonic.level.Palette underwaterBase = new uk.co.jamesj999.sonic.level.Palette();
        underwaterBase.setColor(1, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 100, (byte) 100, (byte) 100));

        // Donor has Tails-like orange at index 4 AND blue at index 8
        uk.co.jamesj999.sonic.level.Palette donorNormal = new uk.co.jamesj999.sonic.level.Palette();
        donorNormal.setColor(4, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 200, (byte) 100, (byte) 0));
        donorNormal.setColor(8, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 0, (byte) 0, (byte) 200));

        uk.co.jamesj999.sonic.level.Palette result = RenderContext.deriveUnderwaterPalette(
                donorNormal, normalBase, underwaterBase);

        // Both should get the same 0.5 factor: orange→(100,50,0), blue→(0,0,100)
        uk.co.jamesj999.sonic.level.Palette.Color orange = result.getColor(4);
        assertEquals(100, Byte.toUnsignedInt(orange.r));
        assertEquals(50, Byte.toUnsignedInt(orange.g));
        assertEquals(0, Byte.toUnsignedInt(orange.b));

        uk.co.jamesj999.sonic.level.Palette.Color blue = result.getColor(8);
        assertEquals(0, Byte.toUnsignedInt(blue.r));
        assertEquals(0, Byte.toUnsignedInt(blue.g));
        assertEquals(100, Byte.toUnsignedInt(blue.b));
    }

    @Test
    public void deriveUnderwaterPalette_clampsTo255() {
        // Ratio > 1 (underwater brighter than normal on average)
        uk.co.jamesj999.sonic.level.Palette normalBase = new uk.co.jamesj999.sonic.level.Palette();
        normalBase.setColor(1, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 50, (byte) 50, (byte) 50));

        uk.co.jamesj999.sonic.level.Palette underwaterBase = new uk.co.jamesj999.sonic.level.Palette();
        underwaterBase.setColor(1, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 200, (byte) 200, (byte) 200));

        uk.co.jamesj999.sonic.level.Palette donorNormal = new uk.co.jamesj999.sonic.level.Palette();
        donorNormal.setColor(1, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 200, (byte) 200, (byte) 200));

        uk.co.jamesj999.sonic.level.Palette result = RenderContext.deriveUnderwaterPalette(
                donorNormal, normalBase, underwaterBase);

        // ratio=4.0, 200*4=800, clamped to 255
        uk.co.jamesj999.sonic.level.Palette.Color c = result.getColor(1);
        assertEquals(255, Byte.toUnsignedInt(c.r));
    }

    @Test
    public void deriveUnderwaterPalette_allZeroBase_preservesDonorColors() {
        // All base colors are black — ratio defaults to 1.0 (no shift)
        uk.co.jamesj999.sonic.level.Palette normalBase = new uk.co.jamesj999.sonic.level.Palette();
        uk.co.jamesj999.sonic.level.Palette underwaterBase = new uk.co.jamesj999.sonic.level.Palette();

        uk.co.jamesj999.sonic.level.Palette donorNormal = new uk.co.jamesj999.sonic.level.Palette();
        donorNormal.setColor(1, new uk.co.jamesj999.sonic.level.Palette.Color(
                (byte) 120, (byte) 60, (byte) 30));

        uk.co.jamesj999.sonic.level.Palette result = RenderContext.deriveUnderwaterPalette(
                donorNormal, normalBase, underwaterBase);

        // No valid base colors to compute ratio from — donor colors pass through unchanged
        uk.co.jamesj999.sonic.level.Palette.Color c = result.getColor(1);
        assertEquals(120, Byte.toUnsignedInt(c.r));
        assertEquals(60, Byte.toUnsignedInt(c.g));
        assertEquals(30, Byte.toUnsignedInt(c.b));
    }
}
