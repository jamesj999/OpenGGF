package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.physics.TrigLookupTable;

public final class S3kSlotLayoutRenderer {

    private static final int GRID_SIZE = 16;
    private static final int CELL_SIZE = 0x18;
    private static final int GRID_OFFSET = 0xB4;

    public short[] buildPointGrid(int angle, int cameraX, int cameraY) {
        short[] points = new short[GRID_SIZE * GRID_SIZE * 2];
        int byteAngle = angle & 0xFC;
        int sin = TrigLookupTable.sinHex(byteAngle);
        int cos = TrigLookupTable.cosHex(byteAngle);
        int offsetX = -(cameraX % CELL_SIZE) - GRID_OFFSET;
        int offsetY = -(cameraY % CELL_SIZE) - GRID_OFFSET;

        int index = 0;
        int currentOffsetY = offsetY;
        for (int row = 0; row < GRID_SIZE; row++) {
            long rowBaseX = (long) cos * offsetX + (long) (-sin) * currentOffsetY;
            long rowBaseY = (long) sin * offsetX + (long) cos * currentOffsetY;
            for (int col = 0; col < GRID_SIZE; col++) {
                points[index++] = (short) (rowBaseX >> 8);
                points[index++] = (short) (rowBaseY >> 8);
                rowBaseX += (long) cos * CELL_SIZE;
                rowBaseY += (long) sin * CELL_SIZE;
            }
            currentOffsetY += CELL_SIZE;
        }
        return points;
    }
}
