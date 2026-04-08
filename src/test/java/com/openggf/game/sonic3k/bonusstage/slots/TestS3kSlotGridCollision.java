package com.openggf.game.sonic3k.bonusstage.slots;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TestS3kSlotGridCollision {

    private static byte[] emptyLayout() {
        return new byte[32 * 32];
    }

    // Pixel coords chosen so (y+0x44)/0x18 = 15 and (x+0x14)/0x18 = 15 (mid-grid, safe for 2x2).
    // x = 15*0x18 - 0x14 = 0x154, y = 15*0x18 - 0x44 = 0x124
    private static final int X = 0x154;
    private static final int Y = 0x124;

    @Test
    void emptyTileIsPassable() {
        byte[] layout = emptyLayout();
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(layout, X, Y);
        assertFalse(result.solid());
    }

    @Test
    void wallTile1IsSolidAndSpecial() {
        byte[] layout = emptyLayout();
        int row = (Y + 0x44) / 0x18;
        int col = (X + 0x14) / 0x18;
        layout[row * 32 + col] = 1;
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(layout, X, Y);
        assertTrue(result.solid());
        assertTrue(result.special());
        assertEquals(1, result.tileId());
    }

    @Test
    void bumperTile5IsSolidAndSpecial() {
        byte[] layout = emptyLayout();
        int row = (Y + 0x44) / 0x18;
        int col = (X + 0x14) / 0x18;
        layout[row * 32 + col] = 5;
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(layout, X, Y);
        assertTrue(result.solid());
        assertTrue(result.special());
        assertEquals(5, result.tileId());
    }

    @Test
    void tile7IsSolidButNotSpecial() {
        byte[] layout = emptyLayout();
        int row = (Y + 0x44) / 0x18;
        int col = (X + 0x14) / 0x18;
        layout[row * 32 + col] = 7;
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(layout, X, Y);
        assertTrue(result.solid());
        assertFalse(result.special());
    }

    @Test
    void ringTile8IsPassable() {
        byte[] layout = emptyLayout();
        int row = (Y + 0x44) / 0x18;
        int col = (X + 0x14) / 0x18;
        layout[row * 32 + col] = 8;
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(layout, X, Y);
        assertFalse(result.solid());
    }

    @Test
    void tile9IsSolidNotSpecial() {
        byte[] layout = emptyLayout();
        int row = (Y + 0x44) / 0x18;
        int col = (X + 0x14) / 0x18;
        layout[row * 32 + col] = 9;
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(layout, X, Y);
        assertTrue(result.solid());
        assertFalse(result.special());
    }

    @Test
    void tilesAbove15ArePassable() {
        byte[] layout = emptyLayout();
        int row = (Y + 0x44) / 0x18;
        int col = (X + 0x14) / 0x18;
        layout[row * 32 + col] = 0x10;
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(layout, X, Y);
        assertFalse(result.solid());
    }

    @Test
    void secondRowDetectsSolidInFootprint() {
        byte[] layout = emptyLayout();
        // Place solid tile in second row of 2x2 footprint
        int row = (Y + 0x44) / 0x18 + 1;
        int col = (X + 0x14) / 0x18;
        layout[row * 32 + col] = 3;
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(layout, X, Y);
        assertTrue(result.solid());
        assertEquals(3, result.tileId());
    }

    @Test
    void ringPickupDetectsRingTile() {
        byte[] layout = emptyLayout();
        int row = (Y + 0x50) / 0x18;
        int col = (X + 0x20) / 0x18;
        layout[row * 32 + col] = 8;
        S3kSlotGridCollision.RingCheck result = S3kSlotGridCollision.checkRingPickup(layout, X, Y);
        assertTrue(result.foundRing());
        assertEquals(row * 32 + col, result.layoutIndex());
    }

    @Test
    void ringPickupIgnoresNonRingTiles() {
        byte[] layout = emptyLayout();
        int row = (Y + 0x50) / 0x18;
        int col = (X + 0x20) / 0x18;
        layout[row * 32 + col] = 5; // bumper, not ring
        S3kSlotGridCollision.RingCheck result = S3kSlotGridCollision.checkRingPickup(layout, X, Y);
        assertFalse(result.foundRing());
    }

    @Test
    void outOfBoundsReturnsPassable() {
        byte[] layout = emptyLayout();
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(layout, -100, -100);
        assertFalse(result.solid());
    }

    @Test
    void nullLayoutReturnsPassable() {
        S3kSlotGridCollision.Result result = S3kSlotGridCollision.check(null, 0x460, 0x430);
        assertFalse(result.solid());
    }
}
