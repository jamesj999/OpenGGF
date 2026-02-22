package com.openggf.physics;

import com.openggf.level.ChunkDesc;
import com.openggf.level.LevelManager;
import com.openggf.level.SolidTile;
import com.openggf.sprites.SensorConfiguration;
import com.openggf.sprites.managers.SpriteManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;

/**
 * Sensor implementation for ground/floor collision detection.
 * Handles both vertical (floor/ceiling) and horizontal (wall) scanning
 * with ground mode rotation for slopes.
 */
public class GroundSensor extends Sensor {
    private static LevelManager levelManager;

    // Full-height tile constant
    private static final byte FULL_TILE = 16;

    // Default flagged angle for missing tiles (ROM: odd angles trigger cardinal snap)
    private static final byte FLAGGED_ANGLE = 0x03;

    private final SensorResult reusableResult = new SensorResult();
    private final WallScanResult wallResult1 = new WallScanResult();
    private final WallScanResult wallResult2 = new WallScanResult();

    public static void setLevelManager(LevelManager lm) {
        levelManager = lm;
    }

    private static LevelManager getLevelManager() {
        if (levelManager == null) {
            levelManager = LevelManager.getInstance();
        }
        return levelManager;
    }

    public GroundSensor(AbstractPlayableSprite sprite, Direction direction, byte x, byte y, boolean active) {
        super(sprite, direction, x, y, active);
    }

    @Override
    protected SensorResult doScan(short dx, short dy) {
        if (!active) {
            return null;
        }

        SensorConfiguration config = SpriteManager.getSensorConfigurationForGroundModeAndDirection(
                sprite.getGroundMode(), direction);
        Direction globalDirection = config.direction();

        computeRotatedOffset();
        short originalX = (short) (sprite.getCentreX() + getRotatedX() + dx);
        short originalY = (short) (sprite.getCentreY() + getRotatedY() + dy);

        // Solidity bit: floor sensors (DOWN) use top_solid_bit, others use lrb_solid_bit
        // ROM: AnglePos uses top_solid_bit; CalcRoomInFront/OverHead use lrb_solid_bit
        int solidityBit = (direction == Direction.DOWN)
                ? sprite.getTopSolidBit()
                : sprite.getLrbSolidBit();

        if (config.vertical()) {
            return scanVertical(originalX, originalY, solidityBit, globalDirection);
        } else {
            return scanHorizontal(originalX, originalY, solidityBit, globalDirection);
        }
    }

    // ========================================
    // VERTICAL SCANNING (Floor/Ceiling)
    // ========================================

    private SensorResult scanVertical(short x, short y, int solidityBit, Direction direction) {
        // Check current tile (ROM: FindFloor - first pass)
        SensorResult result = scanTileVertical(x, y, x, y, solidityBit, direction, false);
        if (result != null) {
            return result;
        }

        // Extend to next tile in scan direction (ROM: FindFloor2 - second pass)
        short nextY = (short) (y + (direction == Direction.DOWN ? 16 : -16));
        result = scanTileVertical(x, y, x, nextY, solidityBit, direction, true);
        if (result != null) {
            return result;
        }

        // No collision found - return empty result with max distance
        byte distance = calculateVerticalDistance((byte) 0, y, nextY, direction);
        return reusableResult.set(FLAGGED_ANGLE, distance, 0, direction);
    }

    /**
     * @param isExtension true for second-pass (FindFloor2) behavior, false for first-pass (FindFloor).
     *                    ROM difference: FindFloor2's negfloor with adjusted < 0 returns ~yInTile
     *                    instead of recursing to the previous tile.
     */
    private SensorResult scanTileVertical(short origX, short origY, short checkX, short checkY,
                                          int solidityBit, Direction direction, boolean isExtension) {
        ChunkDesc desc = getLevelManager().getChunkDescAt((byte) 0, checkX, checkY, sprite.isLoopLowPlane());
        SolidTile tile = getSolidTile(desc, solidityBit);
        if (tile == null) {
            return null;
        }

        byte metric = getHeightMetric(tile, desc, checkX, direction);
        if (metric == 0) {
            return null;
        }

        // Handle negative metrics (ROM: FindFloor loc_1E85E / FindFloor2 loc_1E900)
        // Negative metric means collision starts from opposite edge (V-flipped tiles)
        if (metric < 0) {
            int yInTile = origY & 0x0F;  // Position within tile (0-15)
            int adjusted = metric + yInTile;

            if (adjusted >= 0) {
                // Both passes: no collision in this tile, extend
                // ROM FindFloor: bpl → loc_1E7E2 (extend)
                // ROM FindFloor2: bpl → loc_1E88A (default distance)
                return null;
            }

            if (isExtension) {
                // Second pass (FindFloor2): return ~yInTile distance
                // ROM: loc_1E900 → not.w d1 where d1 = yInTile
                byte distance = (byte) ~yInTile;
                return createResultWithDistance(tile, desc, distance, direction);
            }

            // First pass (FindFloor): regress to previous tile via FindFloor2
            short prevCheckY = (short) (checkY + (direction == Direction.DOWN ? -16 : 16));
            SensorResult prevResult = scanTileVertical(origX, origY, checkX, prevCheckY, solidityBit, direction, true);

            if (prevResult != null) {
                // Adjust distance by -16 (ROM: subi.w #$10,d1)
                // prevResult is reusableResult, so mutate in place
                return reusableResult.set(
                    prevResult.angle(),
                    (byte) (prevResult.distance() - 16),
                    prevResult.tileId(),
                    prevResult.direction()
                );
            }
            return null;
        }

        // Full-height tile: check previous tile for edge detection
        if (metric == FULL_TILE) {
            short prevY = (short) (checkY + (direction == Direction.DOWN ? -16 : 16));
            ChunkDesc prevDesc = getLevelManager().getChunkDescAt((byte) 0, checkX, prevY, sprite.isLoopLowPlane());
            SolidTile prevTile = getSolidTile(prevDesc, solidityBit);
            byte prevMetric = getHeightMetric(prevTile, prevDesc, checkX, direction);

            if (prevMetric > 0 && prevMetric < FULL_TILE) {
                return createVerticalResult(prevTile, prevDesc, checkX, origY, prevY, direction);
            }

            // Use current full tile
            byte distance = calculateVerticalDistance(metric, origY, checkY, direction);
            return createResultWithDistance(tile, desc, distance, direction);
        }

        return createVerticalResult(tile, desc, checkX, origY, checkY, direction);
    }

    private SensorResult createVerticalResult(SolidTile tile, ChunkDesc desc,
                                              short checkX, short origY, short tileY, Direction direction) {
        byte metric = getHeightMetric(tile, desc, checkX, direction);
        byte distance = calculateVerticalDistance(metric, origY, tileY, direction);
        return createResultWithDistance(tile, desc, distance, direction);
    }

    // ROM-accurate: distance is a signed byte (move.b), limiting sensor range to -128..+127 pixels.
    // The 68000 uses the same signed byte range for FindFloor/FindCeiling distances.
    private byte calculateVerticalDistance(byte metric, short origY, short tileY, Direction direction) {
        short tileBase = (short) (tileY & ~0x0F);
        if (direction == Direction.DOWN) {
            // Floor: ROM formula (FindFloor s2.asm:42994-42999)
            // distance = 15 - metric - (origY & 0xF), adjusted for tile offset
            return (byte) ((tileBase + 15 - metric) - origY);
        } else {
            // Ceiling: surface = tileBase + metric
            return (byte) (origY - (tileBase + metric));
        }
    }

    // ========================================
    // HORIZONTAL SCANNING (Walls)
    // ========================================

    private SensorResult scanHorizontal(short x, short y, int solidityBit, Direction direction) {
        WallScanResult result = evaluateWallTile(x, y, solidityBit, direction);

        switch (result.state) {
            case FOUND:
                return createResultWithDistance(result.tile, result.desc, (byte) result.distance, direction);

            case REGRESS:
                // Check previous tile (opposite direction)
                // ROM behavior: if adjacent tile has no collision, preserve angle from current tile
                int prevX = x + (direction == Direction.LEFT ? 16 : -16);
                WallScanResult prev = scanWallTileSimple(prevX, y, solidityBit, direction);
                // Use current tile's angle if adjacent tile has no collision (ROM: angle buffer not modified)
                SolidTile prevTile = prev.tile != null ? prev.tile : result.tile;
                ChunkDesc prevDesc = prev.tile != null ? prev.desc : result.desc;
                return createResultWithDistance(prevTile, prevDesc, (byte) (prev.distance - 16), direction);

            case EXTEND:
            default:
                // Check next tile (same direction)
                // ROM behavior: if adjacent tile has no collision, preserve angle from current tile
                int nextX = x + (direction == Direction.LEFT ? -16 : 16);
                WallScanResult next = scanWallTileSimple(nextX, y, solidityBit, direction);
                // Use current tile's angle if adjacent tile has no collision (ROM: angle buffer not modified)
                SolidTile nextTile = next.tile != null ? next.tile : result.tile;
                ChunkDesc nextDesc = next.tile != null ? next.desc : result.desc;
                return createResultWithDistance(nextTile, nextDesc, (byte) (next.distance + 16), direction);
        }
    }

    private WallScanResult evaluateWallTile(int x, int y, int solidityBit, Direction direction) {
        ChunkDesc desc = getLevelManager().getChunkDescAt((byte) 0, x, y, sprite.isLoopLowPlane());
        SolidTile tile = getSolidTile(desc, solidityBit);

        if (tile == null) {
            return wallResult1.set(WallScanState.EXTEND, 0, null, null);
        }

        int metric = getWallMetric(tile, desc, y, direction);

        if (metric == 0) {
            return wallResult1.set(WallScanState.EXTEND, 0, null, null);
        }

        int xInTile = x & 0x0F;
        int xAdjusted = (direction == Direction.LEFT) ? (15 - xInTile) : xInTile;

        // Negative metric: partial collision from opposite side
        if (metric < 0) {
            boolean extend = (metric + xAdjusted >= 0);
            // Pass tile/desc so angle can be preserved if adjacent tile has no collision
            return extend
                    ? wallResult1.set(WallScanState.EXTEND, 0, tile, desc)
                    : wallResult1.set(WallScanState.REGRESS, 0, tile, desc);
        }

        // Full-width tile: need to check previous tile
        if (metric == FULL_TILE) {
            // Pass tile/desc so angle can be preserved if adjacent tile has no collision
            return wallResult1.set(WallScanState.REGRESS, 0, tile, desc);
        }

        // Standard case: calculate distance to wall surface
        // ROM formula (s2.asm:43246-43250): distance = 15 - metric - xInTile
        // For LEFT scans, xInTile is XORed, so: 15 - metric - (15 - origX) = origX - metric
        int distance = (direction == Direction.LEFT)
                ? (xInTile - metric)
                : (15 - metric - xInTile);
        return wallResult1.set(WallScanState.FOUND, distance, tile, desc);
    }

    private WallScanResult scanWallTileSimple(int x, int y, int solidityBit, Direction direction) {
        ChunkDesc desc = getLevelManager().getChunkDescAt((byte) 0, x, y, sprite.isLoopLowPlane());
        SolidTile tile = getSolidTile(desc, solidityBit);
        int xInTile = x & 0x0F;
        int xAdjusted = (direction == Direction.LEFT) ? (15 - xInTile) : xInTile;

        if (tile == null) {
            int dist = 15 - xAdjusted;
            return wallResult2.set(WallScanState.FOUND, dist, null, null);
        }

        int metric = getWallMetric(tile, desc, y, direction);

        if (metric == 0) {
            int dist = 15 - xAdjusted;
            return wallResult2.set(WallScanState.FOUND, dist, null, null);
        }

        if (metric < 0) {
            if (metric + xAdjusted >= 0) {
                int dist = 15 - xAdjusted;
                return wallResult2.set(WallScanState.FOUND, dist, null, null);
            }
            int dist = -1 - xAdjusted;
            return wallResult2.set(WallScanState.FOUND, dist, tile, desc);
        }

        // ROM formula: distance = 15 - metric - xInTile (or xInTile - metric for LEFT)
        int distance = (direction == Direction.LEFT)
                ? (xInTile - metric)
                : (15 - metric - xInTile);
        return wallResult2.set(WallScanState.FOUND, distance, tile, desc);
    }

    // ========================================
    // METRIC CALCULATIONS
    // ========================================

    private byte getHeightMetric(SolidTile tile, ChunkDesc desc, int x, Direction direction) {
        if (tile == null) return 0;

        int index = x & 0x0F;
        if (desc != null && desc.getHFlip()) {
            index = 15 - index;
        }

        byte metric = tile.getHeightAt((byte) index);
        if (metric != 0 && metric != FULL_TILE) {
            // ROM: eor.w d6,d4 / btst #$C,d4 / neg.w d0
            // V-flip (for floor scan) or non-V-flip (for ceiling scan) negates
            // the height, producing a negative metric processed through the
            // negative-height path (scanTileVertical's metric < 0 branch).
            boolean negate = (desc != null && desc.getVFlip()) ^ (direction == Direction.UP);
            if (negate) {
                metric = (byte) -metric;
            }
        }
        return metric;
    }

    private int getWallMetric(SolidTile tile, ChunkDesc desc, int y, Direction direction) {
        if (tile == null) return 0;

        int rawIndex = y & 0x0F;
        int index = rawIndex;
        boolean vFlip = desc != null && desc.getVFlip();
        if (vFlip) {
            index = 15 - index;
        }

        int metric = tile.getWidthAt((byte) index);
        boolean hFlip = desc != null && desc.getHFlip();
        boolean xMirror = hFlip ^ (direction == Direction.LEFT);
        if (xMirror) {
            metric = -metric;
        }

        return metric;
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private SolidTile getSolidTile(ChunkDesc desc, int solidityBit) {
        if (desc == null || !desc.isSolidityBitSet(solidityBit)) {
            return null;
        }
        return getLevelManager().getSolidTileForChunkDesc(desc, solidityBit);
    }

    private SensorResult createResultWithDistance(SolidTile tile, ChunkDesc desc, byte distance, Direction direction) {
        byte angle = FLAGGED_ANGLE;
        int tileIndex = 0;

        if (tile != null) {
            boolean hFlip = desc != null && desc.getHFlip();
            boolean vFlip = desc != null && desc.getVFlip();
            angle = tile.getAngle(hFlip, vFlip);
            tileIndex = tile.getIndex();
        }

        return reusableResult.set(angle, distance, tileIndex, direction);
    }

    // ========================================
    // WALL SCAN RESULT
    // ========================================

    private enum WallScanState { FOUND, EXTEND, REGRESS }

    private static final class WallScanResult {
        WallScanState state;
        int distance;
        SolidTile tile;
        ChunkDesc desc;

        WallScanResult set(WallScanState state, int distance, SolidTile tile, ChunkDesc desc) {
            this.state = state;
            this.distance = distance;
            this.tile = tile;
            this.desc = desc;
            return this;
        }
    }
}
