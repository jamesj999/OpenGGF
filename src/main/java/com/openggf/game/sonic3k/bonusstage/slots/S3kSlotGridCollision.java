package com.openggf.game.sonic3k.bonusstage.slots;

/**
 * Grid-based 2x2 tile collision for the slot bonus stage.
 *
 * <p>ROM sub_4BD5A (lines 99076-99133): Checks 4 tiles in a 2x2 footprint.
 * ROM sub_4BDCA (lines 99139-99183): Ring pickup check with different offsets.
 */
public final class S3kSlotGridCollision {

    static final int LAYOUT_STRIDE = 32;  // Our Java layout is 32-wide (ROM uses 0x80 but same concept)
    static final int CELL_SIZE = 0x18;

    // sub_4BD5A offsets
    static final int COLLISION_Y_OFFSET = 0x44;
    static final int COLLISION_X_OFFSET = 0x14;

    // sub_4BDCA offsets
    static final int RING_Y_OFFSET = 0x50;
    static final int RING_X_OFFSET = 0x20;

    private S3kSlotGridCollision() {
    }

    /**
     * 2x2 tile collision check matching sub_4BD5A.
     * Checks 4 tiles at player's position and returns the first solid hit.
     *
     * @param layout 32x32 byte layout array
     * @param xPixel player X position (pixel, not fixed-point)
     * @param yPixel player Y position (pixel, not fixed-point)
     * @return collision result
     */
    public static Result check(byte[] layout, int xPixel, int yPixel) {
        if (layout == null || layout.length < LAYOUT_STRIDE * LAYOUT_STRIDE) {
            return Result.NONE;
        }

        int baseRow = (yPixel + COLLISION_Y_OFFSET) / CELL_SIZE;
        int baseCol = (xPixel + COLLISION_X_OFFSET) / CELL_SIZE;

        // Check 2x2 footprint matching ROM order:
        // (row, col), (row, col+1), (row+1, col), (row+1, col+1)
        for (int dr = 0; dr <= 1; dr++) {
            for (int dc = 0; dc <= 1; dc++) {
                int r = baseRow + dr;
                int c = baseCol + dc;
                if (r < 0 || r >= LAYOUT_STRIDE || c < 0 || c >= LAYOUT_STRIDE) {
                    continue;
                }
                int index = r * LAYOUT_STRIDE + c;
                int tileId = layout[index] & 0xFF;
                if (isSolid(tileId)) {
                    boolean special = isSpecial(tileId);
                    return new Result(true, special, tileId, index);
                }
            }
        }
        return Result.NONE;
    }

    /**
     * Ring pickup check matching sub_4BDCA. Uses different offsets from collision check.
     * Only detects tile ID 8 (ring tiles).
     */
    public static RingCheck checkRingPickup(byte[] layout, int xPixel, int yPixel) {
        if (layout == null || layout.length < LAYOUT_STRIDE * LAYOUT_STRIDE) {
            return RingCheck.NONE;
        }

        int row = (yPixel + RING_Y_OFFSET) / CELL_SIZE;
        int col = (xPixel + RING_X_OFFSET) / CELL_SIZE;

        if (row < 0 || row >= LAYOUT_STRIDE || col < 0 || col >= LAYOUT_STRIDE) {
            return RingCheck.NONE;
        }

        int index = row * LAYOUT_STRIDE + col;
        int tileId = layout[index] & 0xFF;
        if (tileId == 8) {
            return new RingCheck(true, index);
        }
        if (tileId != 0) {
            return new RingCheck(false, index, tileId);
        }
        return RingCheck.NONE;
    }

    /**
     * sub_4BDA2: tiles 1-15 (except 0 and 8) are solid. Tiles >= 16 and tile 0 are passable.
     */
    static boolean isSolid(int tileId) {
        if (tileId == 0 || tileId == 8) return false;
        return tileId >= 1 && tileId <= 15;
    }

    /**
     * Tiles 1-6 are "special" — they trigger interactions (bumper, goal, spike, slot reels).
     * Tile 7 and 9+ are just solid with no special interaction.
     */
    static boolean isSpecial(int tileId) {
        return tileId >= 1 && tileId <= 6;
    }

    public record Result(boolean solid, boolean special, int tileId, int layoutIndex) {
        public static final Result NONE = new Result(false, false, 0, -1);
    }

    public record RingCheck(boolean foundRing, int layoutIndex, int tileId) {
        public RingCheck(boolean foundRing, int layoutIndex) {
            this(foundRing, layoutIndex, foundRing ? 8 : 0);
        }
        public static final RingCheck NONE = new RingCheck(false, -1, 0);
    }
}
