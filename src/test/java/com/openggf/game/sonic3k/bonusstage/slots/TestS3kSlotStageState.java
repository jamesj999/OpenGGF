package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestS3kSlotStageState {

    @Test
    void bootstrapUsesRomInitialState() {
        S3kSlotStageState state = S3kSlotStageState.bootstrap();

        assertEquals(0, state.statTable());
        assertEquals(0x40, state.scalarIndex1());
        assertFalse(state.paletteCycleEnabled());
        assertEquals(0, state.lastCollisionTileId());
    }

    @Test
    void renderBuffersUseExpandedLayoutStride() {
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();

        assertEquals(0x80, buffers.layoutStrideBytes());
        assertEquals(0x20, buffers.layoutRows());
        assertEquals(0x20, buffers.layoutColumns());
    }

    @Test
    void expandedLayoutCopiesRowsIntoStrideSlots() {
        byte[] expandedLayout = S3kSlotRomData.buildExpandedLayoutBuffer();

        assertEquals(0x20 * 0x80, expandedLayout.length);
        assertArrayEquals(row(0), Arrays.copyOfRange(expandedLayout, 0, 0x20));
        assertArrayEquals(row(1), Arrays.copyOfRange(expandedLayout, 0x80, 0x80 + 0x20));
        assertArrayEquals(row(0x1F), Arrays.copyOfRange(expandedLayout, 0x1F * 0x80, 0x1F * 0x80 + 0x20));
    }

    private static byte[] row(int rowIndex) {
        int start = rowIndex * 0x20;
        return Arrays.copyOfRange(S3kSlotRomData.SLOT_BONUS_LAYOUT, start, start + 0x20);
    }
}
