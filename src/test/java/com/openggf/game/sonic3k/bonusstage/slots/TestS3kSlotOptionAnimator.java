package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestS3kSlotOptionAnimator {

    @Test
    void sourceOffsetMatchesSub4C77CMath() {
        assertEquals(0x0000, S3kSlotOptionAnimator.sourceOffsetFor(0, 0x0000));
        assertEquals(0x0240, S3kSlotOptionAnimator.sourceOffsetFor(1, 0x0080));
        assertEquals(0x0C7C, S3kSlotOptionAnimator.sourceOffsetFor(6, 0x00F8));
    }
}
