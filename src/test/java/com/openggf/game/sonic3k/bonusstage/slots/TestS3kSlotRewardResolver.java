package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TestS3kSlotRewardResolver {

    @Test
    void fixedTargetThresholdsMatchByte4C8B4() {
        assertEquals(0, S3kSlotRewardResolver.pickFixedRow(0x00));
        assertEquals(1, S3kSlotRewardResolver.pickFixedRow(0x04));
        assertEquals(3, S3kSlotRewardResolver.pickFixedRow(0x11));
        assertEquals(6, S3kSlotRewardResolver.pickFixedRow(0x49));
        assertEquals(-1, S3kSlotRewardResolver.pickFixedRow(0x4A));
    }
}
