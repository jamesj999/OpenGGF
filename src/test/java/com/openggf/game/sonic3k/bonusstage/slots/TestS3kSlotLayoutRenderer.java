package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
