package com.openggf.game.sonic3k.bonusstage.slots;

import com.openggf.sprites.playable.Sonic;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotPlayerRuntime {

    @Test
    void initializeUsesRomBootstrapState() {
        S3kSlotStageState state = S3kSlotStageState.bootstrap();
        S3kSlotCollisionSystem collisionSystem = new S3kSlotCollisionSystem(
                S3kSlotRenderBuffers.fromRomData(), state);
        S3kSlotPlayerRuntime runtime = new S3kSlotPlayerRuntime(state, collisionSystem);
        Sonic player = new Sonic("sonic", (short) 0x460, (short) 0x430);

        state.setLastCollision(5, 12);
        runtime.initialize(player);

        assertTrue(player.getAir());
        assertTrue(player.getRolling());
        assertEquals(0, player.getGSpeed());
        assertEquals(0, player.getXSpeed());
        assertEquals(0, player.getYSpeed());
        assertEquals(0, state.lastCollisionTileId());
        assertEquals(-1, state.lastCollisionIndex());
    }

    @Test
    void advanceRotationUsesStageStateScalar() {
        S3kSlotStageState state = S3kSlotStageState.bootstrap();
        S3kSlotCollisionSystem collisionSystem = new S3kSlotCollisionSystem(
                S3kSlotRenderBuffers.fromRomData(), state);
        S3kSlotPlayerRuntime runtime = new S3kSlotPlayerRuntime(state, collisionSystem);

        runtime.advanceRotation(false);
        assertEquals(0x40, state.statTable());

        runtime.advanceRotation(true);
        assertEquals(0x440, state.statTable());
    }

    @Test
    void tickRecordsCollisionThroughCollisionSystem() {
        S3kSlotStageState state = S3kSlotStageState.bootstrap();
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();
        buffers.expandedLayout()[2 * buffers.layoutStrideBytes()] = 5;
        S3kSlotCollisionSystem collisionSystem = new S3kSlotCollisionSystem(buffers, state);
        S3kSlotPlayerRuntime runtime = new S3kSlotPlayerRuntime(state, collisionSystem);
        Sonic player = new Sonic("sonic", (short) 0x0000, (short) 0x0000);

        runtime.initialize(player);
        player.setAir(false);

        runtime.tick(player, false, false, false, 0);

        assertEquals(5, state.lastCollisionTileId());
        assertEquals(2 * 0x20, state.lastCollisionIndex());
        assertFalse(player.getAir());
    }
}
