package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotRomData {
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

    private S3kSlotRomData() {
    }
}
