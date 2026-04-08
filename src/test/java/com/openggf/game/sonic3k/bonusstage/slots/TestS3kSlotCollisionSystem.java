package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotCollisionSystem {

    @Test
    void collisionReadsExpandedStrideAndStoresCompactLayoutIndex() {
        S3kSlotStageState state = S3kSlotStageState.bootstrap();
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();
        buffers.expandedLayout()[2 * buffers.layoutStrideBytes()] = 5;
        S3kSlotCollisionSystem system = new S3kSlotCollisionSystem(buffers, state);

        S3kSlotCollisionSystem.Collision collision = system.checkCollision(0, 0);

        assertTrue(collision.solid());
        assertTrue(collision.special());
        assertEquals(5, collision.tileId());
        assertEquals(2 * 0x20, collision.layoutIndex());
        assertEquals(2 * buffers.layoutStrideBytes(), collision.expandedLayoutIndex());
        assertEquals(5, state.lastCollisionTileId());
        assertEquals(2 * 0x20, state.lastCollisionIndex());
    }

    @Test
    void ringPickupConsumesCompactAndExpandedLayoutViews() {
        S3kSlotStageState state = S3kSlotStageState.bootstrap();
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();
        buffers.layout()[0] = 8;
        buffers.expandedLayout()[0] = 8;
        S3kSlotCollisionSystem system = new S3kSlotCollisionSystem(buffers, state);

        S3kSlotCollisionSystem.RingCheck ring = system.checkRingPickup(-0x20, -0x50);
        assertTrue(ring.foundRing());
        assertEquals(0, ring.layoutIndex());
        assertEquals(0, ring.expandedLayoutIndex());

        system.consumeRing(ring);

        assertEquals(0, buffers.layout()[0]);
        assertEquals(0, buffers.expandedLayout()[0]);
    }

    @Test
    void spikeTileReversesScalarOnlyOncePerThrottleWindow() {
        S3kSlotStageState state = S3kSlotStageState.bootstrap();
        S3kSlotCollisionSystem system = new S3kSlotCollisionSystem(
                S3kSlotRenderBuffers.fromRomData(), state);

        S3kSlotCollisionSystem.TileResponse response = system.resolveTileResponse(
                6, (short) 0, (short) 0, (short) 0, (short) 0);

        assertEquals(S3kSlotCollisionSystem.Effect.SPIKE_REVERSAL, response.effect());
        assertEquals(-0x40, state.scalarIndex1());

        S3kSlotCollisionSystem.TileResponse throttled = system.resolveTileResponse(
                6, (short) 0, (short) 0, (short) 0, (short) 0);

        assertEquals(S3kSlotCollisionSystem.Effect.NONE, throttled.effect());
        assertEquals(-0x40, state.scalarIndex1());
    }
}
