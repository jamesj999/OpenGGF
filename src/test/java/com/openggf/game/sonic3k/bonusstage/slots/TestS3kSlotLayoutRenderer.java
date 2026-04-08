package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotLayoutRenderer {

    @Test
    void zeroAngleBuildsStable16x16PointGrid() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();

        short[] points = renderer.buildPointGrid(0, 0, 0);

        assertEquals(16 * 16 * 2, points.length);
        assertArrayEquals(new short[] {(short) -0xB4, (short) -0xB4}, new short[] {points[0], points[1]});
        assertArrayEquals(new short[] {(short) -0x9C, (short) -0xB4}, new short[] {points[2], points[3]});
        int secondRowIndex = 16 * 2;
        assertArrayEquals(new short[] {(short) -0xB4, (short) -0x9C},
                new short[] {points[secondRowIndex], points[secondRowIndex + 1]});
    }

    @Test
    void quarterTurnRotatesGridBasisClockwise() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();

        short[] points = renderer.buildPointGrid(0x40, 0, 0);

        assertArrayEquals(new short[] {(short) 0xB4, (short) -0xB4}, new short[] {points[0], points[1]});
        assertArrayEquals(new short[] {(short) 0xB4, (short) -0x9C}, new short[] {points[2], points[3]});
        int secondRowIndex = 16 * 2;
        assertArrayEquals(new short[] {(short) 0x9C, (short) -0xB4},
                new short[] {points[secondRowIndex], points[secondRowIndex + 1]});
    }

    @Test
    void visibleCellsIncludeSemanticSlotStagePieces() {
        S3kSlotLayoutRenderer renderer = new S3kSlotLayoutRenderer();

        List<S3kSlotLayoutRenderer.VisibleCell> cells = renderer.buildVisibleCells(
                S3kSlotRomData.SLOT_BONUS_LAYOUT,
                0,
                0,
                0);

        assertFalse(cells.isEmpty());
        assertTrue(cells.stream().anyMatch(cell -> cell.cellId() == 1));
        assertTrue(cells.stream().anyMatch(cell -> cell.cellId() == 5));
        assertTrue(cells.stream().anyMatch(cell -> cell.cellId() == 7));
        assertTrue(cells.stream().allMatch(cell -> cell.screenX() >= 0x70 && cell.screenX() < 0x1D0));
        assertTrue(cells.stream().allMatch(cell -> cell.screenY() >= 0x70 && cell.screenY() < 0x170));
    }
}
