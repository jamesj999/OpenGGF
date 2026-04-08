package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
