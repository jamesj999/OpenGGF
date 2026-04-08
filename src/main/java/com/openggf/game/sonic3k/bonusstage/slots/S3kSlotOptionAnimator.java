package com.openggf.game.sonic3k.bonusstage.slots;

public final class S3kSlotOptionAnimator {

    private S3kSlotOptionAnimator() {
    }

    public static int sourceOffsetFor(int symbol, int reelWord) {
        return ((symbol & 0x07) << 9) + ((reelWord & 0x00F8) >> 1);
    }
}
