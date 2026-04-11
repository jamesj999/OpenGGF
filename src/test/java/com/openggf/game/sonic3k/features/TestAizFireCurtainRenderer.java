package com.openggf.game.sonic3k.features;

import com.openggf.game.sonic3k.events.FireCurtainRenderState;
import com.openggf.game.sonic3k.events.FireCurtainStage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
            assertTrue(coversBottom, "Column " + column.columnIndex() + " left a bottom gap");
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
            assertTrue(highestTileTop >= column.topY() - 8, "Column " + column.columnIndex() + " should keep at most one clip-padding tile above the top edge");
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
        assertEquals(144, wavyPlan.columns().get(4).topY());
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
                assertEquals(3, (draw.descriptor() >> 13) & 0x3, "Curtain tiles must always use palette line 4");
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
        assertFalse(plan.columns().isEmpty(), "Post-mutation should produce tiles from fire overlay");
        for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
            for (AizFireCurtainRenderer.TileDraw draw : column.draws()) {
                assertTrue(draw.renderPatternId() >= 0x500 && draw.renderPatternId() < 0x500 + 121, "Pattern index should be in fire overlay range");
                assertEquals(3, (draw.descriptor() >> 13) & 0x3, "Fire palette line");
            }
        }
    }

    @Test
    public void negativeWaveOffsetDoesNotCreateGapAtCurtainTop() {
        AizFireCurtainRenderer renderer = rendererWithSampler();
        int[] waveOffsets = new int[20];
        for (int i = 0; i < 20; i++) {
            waveOffsets[i] = -15;
        }
        FireCurtainRenderState state = new FireCurtainRenderState(
                true, 224, 0, 8, 0x1000, 0x0180,
                waveOffsets, FireCurtainStage.AIZ1_RISING);

        AizFireCurtainRenderer.CurtainCompositionPlan plan =
                renderer.buildCompositionPlan(state, 320, 224);

        for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
            assertTrue(column.topY() <= 0, "Column " + column.columnIndex()
                            + " has gap at top: topY=" + column.topY());
        }
    }

    @Test
    public void fireTilesWrapBeyondOriginalZoneBoundaries() {
        AizFireCurtainRenderer renderer = rendererWithSampler();
        FireCurtainRenderState state = new FireCurtainRenderState(
                true, 224, 0, 8, 0x1000, 0x0400,
                new int[20], FireCurtainStage.AIZ1_REFRESH,
                0x500, 121);

        AizFireCurtainRenderer.CurtainCompositionPlan plan =
                renderer.buildCompositionPlan(state, 320, 224);

        assertFalse(plan.columns().isEmpty(), "Should still produce fire tiles via wrapping when bgY > 0x310");
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
        assertFalse(plan.columns().isEmpty(), "Act 2 continuation should produce tiles");
        for (AizFireCurtainRenderer.ColumnRenderPlan column : plan.columns()) {
            for (AizFireCurtainRenderer.TileDraw draw : column.draws()) {
                assertTrue(draw.renderPatternId() >= 0x500 && draw.renderPatternId() < 0x500 + 121, "Pattern index should be in fire overlay range");
            }
        }
    }
}


