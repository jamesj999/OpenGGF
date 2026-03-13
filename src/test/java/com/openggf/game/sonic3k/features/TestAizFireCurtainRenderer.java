package com.openggf.game.sonic3k.features;

import com.openggf.game.sonic3k.events.FireCurtainRenderState;
import com.openggf.game.sonic3k.events.FireCurtainStage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TestAizFireCurtainRenderer {
    private static final int SAMPLE_PATTERN_MASK = 0x7FF;

    private static AizFireCurtainRenderer rendererWithSampler() {
        return new AizFireCurtainRenderer((worldX, worldY) -> {
            int pattern = ((worldX / 8) + (worldY / 8)) & SAMPLE_PATTERN_MASK;
            return (3 << 13) | pattern;
        });
    }

    @Test
    public void compositionFillsBottomBandForEveryVisibleColumn() {
        AizFireCurtainRenderer renderer = rendererWithSampler();
        FireCurtainRenderState state = new FireCurtainRenderState(
                true,
                96,
                24,
                12,
                0x1000,
                0x0180,
                new int[] {0, -1, -2, -3, -4, -5, -6, -7, -8, -7, -6, -5, -4, -3, -2, -1, 0, -1, -2, -3},
                FireCurtainStage.AIZ1_RISING);

        AizFireCurtainRenderer.CurtainCompositionPlan plan = renderer.buildCompositionPlan(state, 320, 224);

        for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
            boolean coversBottom = false;
            for (AizFireCurtainRenderer.TileDraw draw : column.draws()) {
                if (draw.screenY() <= 223 && draw.screenY() + 8 > 223) {
                    coversBottom = true;
                    break;
                }
            }
            assertTrue("Column " + column.columnIndex() + " left a bottom gap", coversBottom);
        }
    }

    @Test
    public void compositionDoesNotEmitColumnsAboveTheirVisibleRegion() {
        AizFireCurtainRenderer renderer = rendererWithSampler();
        FireCurtainRenderState state = new FireCurtainRenderState(
                true,
                80,
                0,
                6,
                0x1000,
                0x0180,
                new int[20],
                FireCurtainStage.AIZ1_RISING);

        AizFireCurtainRenderer.CurtainCompositionPlan plan = renderer.buildCompositionPlan(state, 320, 224);

        for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
            assertFalse(column.draws().isEmpty());
            int highestTileTop = Integer.MAX_VALUE;
            for (AizFireCurtainRenderer.TileDraw draw : column.draws()) {
                highestTileTop = Math.min(highestTileTop, draw.screenY());
            }
            assertTrue("Column " + column.columnIndex() + " should keep at most one clip-padding tile above the top edge",
                    highestTileTop >= column.topY() - 8);
        }
    }

    @Test
    public void waveOffsetsChangeColumnTopDeterministically() {
        AizFireCurtainRenderer renderer = rendererWithSampler();
        FireCurtainRenderState flat = new FireCurtainRenderState(
                true,
                72,
                0,
                4,
                0x1000,
                0x0180,
                new int[20],
                FireCurtainStage.AIZ1_RISING);
        FireCurtainRenderState wavy = new FireCurtainRenderState(
                true,
                72,
                0,
                4,
                0x1000,
                0x0180,
                new int[] {0, -2, -4, -6, -8, -6, -4, -2, 0, -1, -3, -5, -7, -5, -3, -1, 0, -2, -4, -6},
                FireCurtainStage.AIZ1_RISING);

        AizFireCurtainRenderer.CurtainCompositionPlan flatPlan = renderer.buildCompositionPlan(flat, 320, 224);
        AizFireCurtainRenderer.CurtainCompositionPlan wavyPlan = renderer.buildCompositionPlan(wavy, 320, 224);

        assertEquals(flatPlan.columns().size(), wavyPlan.columns().size());
        assertEquals(152, flatPlan.columns().get(0).topY());
        assertEquals(160, wavyPlan.columns().get(4).topY());
    }

    @Test
    public void compositionSamplesConfiguredSourceStripCoordinates() {
        AizFireCurtainRenderer renderer = new AizFireCurtainRenderer((worldX, worldY) ->
                (3 << 13) | (((worldX / 8) ^ (worldY / 8)) & SAMPLE_PATTERN_MASK));
        FireCurtainRenderState state = new FireCurtainRenderState(
                true,
                224,
                18,
                30,
                0x0200,
                0x01A0,
                new int[20],
                FireCurtainStage.AIZ1_RISING);

        AizFireCurtainRenderer.CurtainCompositionPlan plan = renderer.buildCompositionPlan(state, 320, 224);
        AizFireCurtainRenderer.ColumnRenderPlan firstColumn = plan.columns().get(0);
        AizFireCurtainRenderer.TileDraw bottomTile = firstColumn.draws().get(0);
        int sourceY = state.sourceWorldY() + bottomTile.screenY();
        int expected = (3 << 13) | ((((state.sourceWorldX() + bottomTile.screenX()) / 8)
                ^ (sourceY / 8)) & SAMPLE_PATTERN_MASK);
        assertEquals(expected, bottomTile.descriptor());
    }

    @Test
    public void compositionForcesFirePaletteLineRegardlessOfSampledDescriptorPalette() {
        AizFireCurtainRenderer renderer = new AizFireCurtainRenderer((worldX, worldY) ->
                (2 << 13) | (((worldX / 8) + (worldY / 8)) & SAMPLE_PATTERN_MASK));
        FireCurtainRenderState state = new FireCurtainRenderState(
                true,
                96,
                8,
                12,
                0x1000,
                0x0180,
                new int[20],
                FireCurtainStage.AIZ1_RISING);

        AizFireCurtainRenderer.CurtainCompositionPlan plan = renderer.buildCompositionPlan(state, 320, 224);

        for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
            for (AizFireCurtainRenderer.TileDraw draw : column.draws()) {
                assertEquals("Curtain tiles must always use palette line 4", 3, (draw.descriptor() >> 13) & 0x3);
            }
        }
    }

    @Test
    public void postMutationStagesUseFireOverlayTilesLive() {
        AizFireCurtainRenderer renderer = new AizFireCurtainRenderer();
        FireCurtainRenderState refresh = new FireCurtainRenderState(
                true,
                224,
                8,
                13,
                0x0200,
                0x0210,
                new int[20],
                FireCurtainStage.AIZ1_REFRESH,
                0x500,
                121);

        AizFireCurtainRenderer.CurtainCompositionPlan plan = renderer.buildCompositionPlan(refresh, 320, 224);
        assertFalse("Post-mutation should produce tiles from fire overlay", plan.columns().isEmpty());
        for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
            for (AizFireCurtainRenderer.TileDraw draw : column.draws()) {
                assertTrue("Pattern index should be in fire overlay range",
                        draw.renderPatternId() >= 0x500 && draw.renderPatternId() < 0x500 + 121);
                assertEquals("Fire palette line", 3, (draw.descriptor() >> 13) & 0x3);
            }
        }
    }

    @Test
    public void act2ContinuationUsesFireOverlayTilesLive() {
        AizFireCurtainRenderer renderer = new AizFireCurtainRenderer();
        FireCurtainRenderState redraw = new FireCurtainRenderState(
                true,
                224,
                8,
                16,
                0x0200,
                0x0210,
                new int[20],
                FireCurtainStage.AIZ2_REDRAW,
                0x500,
                121);

        AizFireCurtainRenderer.CurtainCompositionPlan plan = renderer.buildCompositionPlan(redraw, 320, 224);
        assertFalse("Act 2 continuation should produce tiles", plan.columns().isEmpty());
        for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
            for (AizFireCurtainRenderer.TileDraw draw : column.draws()) {
                assertTrue("Pattern index should be in fire overlay range",
                        draw.renderPatternId() >= 0x500 && draw.renderPatternId() < 0x500 + 121);
            }
        }
    }
}
