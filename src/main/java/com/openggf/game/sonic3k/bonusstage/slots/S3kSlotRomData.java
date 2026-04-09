package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotRomData {
    public static final short SLOT_BONUS_PLAYER_START_X = 0x0460;
    public static final short SLOT_BONUS_PLAYER_START_Y = 0x0360;
    public static final short SLOT_BONUS_CAGE_CENTER_X = 0x0460;
    public static final short SLOT_BONUS_CAGE_CENTER_Y = 0x0430;
    public static final int TRANSIENT_SLOT_COUNT = 0x20;
    public static final int SLOT_LAYOUT_SIZE = 0x20;
    public static final int SLOT_EXPANDED_STRIDE = 0x80;
    public static final int SLOT_LAYOUT_WORLD_OFFSET = 0x20;

    public static final short[] REWARD_VALUES = {100, 30, 20, 25, -1, 10, 8, 200};
    public static final byte[] TARGET_ROWS = {
            4, 0, 0,
            9, 1, 0x11,
            4, 3, 0x33,
            0x12, 4, 0x44,
            9, 2, 0x22,
            0x0F, 5, 0x55,
            0x0F, 6, 0x66,
            (byte) 0xFF, 0x0F, (byte) 0xFF
    };
    public static final byte[] REEL_SEQUENCE_A = {0, 1, 2, 5, 4, 6, 5, 3};
    public static final byte[] REEL_SEQUENCE_B = {0, 1, 2, 5, 4, 6, 1, 3};
    public static final byte[] REEL_SEQUENCE_C = {0, 1, 2, 5, 4, 6, 3, 5};

    private static final String[] SLOT_BONUS_LAYOUT_ROWS = {
            "00000000161000000000000044444000",
            "00000007000111000011170010001000",
            "00000070000000111100007110001000",
            "41151100000000000000000000005000",
            "40000000000000000000000000001000",
            "40000000000000000000000000001000",
            "40000000000050000005000000000700",
            "41100000070007000070007000000070",
            "00100000700000000000000700000001",
            "00700007000800000000800070000006",
            "07000000008800000000880000000001",
            "01000000088807000070888000000010",
            "01000050000005000050000005000010",
            "01000007000750000005700070000010",
            "00100000000000000000000000000100",
            "00100000000000000000000000000100",
            "00100000000000000000000000000100",
            "00100000000000000000000000000100",
            "01000007000750000005700070000010",
            "01000050000005000050000005000010",
            "01000000088807000070888000000010",
            "10000000008800000000880000000070",
            "60000007000800000000800070000700",
            "10000000700000000000000700000100",
            "07000000070007000070007000000114",
            "00700000000050000005000000000004",
            "00010000000000000000000000000004",
            "00010000000000000000000000000004",
            "00050000000000000000000000115114",
            "00010001170000111100000007000000",
            "00010001007111000011100070000000",
            "00044444000000000000016100000000"
    };

    public static final byte[] SLOT_BONUS_LAYOUT = decodeLayoutRows();
    public static final byte[] RING_SPARKLE_FRAMES = {0x10, 0x11, 0x12, 0x13};
    public static final int RING_SPARKLE_DELAY = 5;
    public static final byte[] BUMPER_BOUNCE_FRAMES = {0x0A, 0x0B};
    public static final int BUMPER_BOUNCE_DELAY = 1;
    public static final byte[] SPIKE_ANIMATION_FRAMES = {0x0C, 0x06, 0x0C};
    public static final int SPIKE_ANIMATION_DELAY = 7;

    private S3kSlotRomData() {
    }

    public static byte[] buildExpandedLayoutBuffer() {
        byte[] expandedLayout = new byte[SLOT_EXPANDED_STRIDE * SLOT_EXPANDED_STRIDE];
        for (int row = 0; row < SLOT_LAYOUT_SIZE; row++) {
            int expandedRow = row + SLOT_LAYOUT_WORLD_OFFSET;
            int expandedIndex = expandedRow * SLOT_EXPANDED_STRIDE + SLOT_LAYOUT_WORLD_OFFSET;
            System.arraycopy(SLOT_BONUS_LAYOUT, row * SLOT_LAYOUT_SIZE, expandedLayout, expandedIndex, SLOT_LAYOUT_SIZE);
        }
        return expandedLayout;
    }

    private static byte[] decodeLayoutRows() {
        byte[] layout = new byte[SLOT_BONUS_LAYOUT_ROWS.length * 32];
        int index = 0;
        for (String row : SLOT_BONUS_LAYOUT_ROWS) {
            for (int i = 0; i < row.length(); i++) {
                layout[index++] = (byte) Character.digit(row.charAt(i), 16);
            }
        }
        return layout;
    }
}
