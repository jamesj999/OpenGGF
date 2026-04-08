package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotRewardResolver {

    private S3kSlotRewardResolver() {
    }

    public static int pickFixedRow(int rolledByte) {
        int remaining = rolledByte & 0xFF;
        for (int row = 0; row < S3kSlotRomData.TARGET_ROWS.length; row += 3) {
            int threshold = S3kSlotRomData.TARGET_ROWS[row] & 0xFF;
            if (threshold == 0xFF) {
                return -1;
            }
            if (remaining < threshold) {
                return row / 3;
            }
            remaining -= threshold;
        }
        return -1;
    }
}
