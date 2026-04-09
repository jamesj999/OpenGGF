package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSlotRenderBuffers {

    @Test
    void slotWallAnimationCommitsNextPermanentTileIntoCompactAndExpandedLayout() {
        S3kSlotRenderBuffers buffers = S3kSlotRenderBuffers.fromRomData();
        int layoutIndex = findFirstTile(buffers.layout(), 0x01);
        int expandedIndex = buffers.compactToExpandedIndex(layoutIndex);

        assertTrue(layoutIndex >= 0);
        assertTrue(buffers.startSlotWallAnimationAt(layoutIndex, 0x02));
        assertEquals(0x0D, buffers.layout()[layoutIndex] & 0xFF);
        assertEquals(0x0D, buffers.expandedLayout()[expandedIndex] & 0xFF);

        for (int i = 0; i < S3kSlotRomData.SLOT_WALL_COLOR_FRAMES.length; i++) {
            buffers.tickTransientAnimations();
        }

        assertEquals(0x02, buffers.layout()[layoutIndex] & 0xFF);
        assertEquals(0x02, buffers.expandedLayout()[expandedIndex] & 0xFF);
    }

    private static int findFirstTile(byte[] layout, int tileId) {
        for (int i = 0; i < layout.length; i++) {
            if ((layout[i] & 0xFF) == tileId) {
                return i;
            }
        }
        return -1;
    }
}
