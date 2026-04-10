package com.openggf.physics;

import com.openggf.game.GameServices;
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

    /**
     * Optional override for tests. When non-null, getLevelManager() returns
     * this instead of the singleton. Pass null to revert.
     */
    private static LevelManager overrideLevelManager;

    /** Cached singleton — avoids synchronized getInstance() on every scan. */
    private static LevelManager cachedLevelManager;

    // Full-height tile constant
    private static final byte FULL_TILE = 16;

    // Default flagged angle for missing tiles (ROM: odd angles trigger cardinal snap)
    private static final byte FLAGGED_ANGLE = 0x03;

    private final SensorResult reusableResult = new SensorResult();
    private final WallScanResult wallResult1 = new WallScanResult();
    private final WallScanResult wallResult2 = new WallScanResult();

    /**
     * Inject a mock LevelManager for unit tests. Pass null to revert
     * to the default runtime level manager.
     */
    public static void setLevelManager(LevelManager lm) {
        overrideLevelManager = lm;
        cachedLevelManager = null; // force re-fetch
    }

    private static LevelManager getLevelManager() {
        LevelManager override = overrideLevelManager;
        if (override != null) return override;
        if (cachedLevelManager == null) cachedLevelManager = GameServices.level();
        return cachedLevelManager;
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

        // Solidity bit selection must match the ROM's per-routine d5 register:
        //   - Ground attachment (direction=DOWN): topSolidBit in ALL modes.
        //     ROM: Player_AnglePos loads d5=top_solid_bit ONCE (line 18735)
        //     and passes it to WalkSpeed, WalkVertL, WalkCeiling, WalkVertR.
        //   - Ceiling sensors (direction=UP): lrbSolidBit (ceiling HIT, not attachment)
        //   - Push sensors (direction=LEFT/RIGHT): lrbSolidBit (CalcRoomInFront)
        int solidityBit = (direction == Direction.DOWN)
                ? sprite.getTopSolidBit()
                : sprite.getLrbSolidBit();

        if (config.vertical()) {
            return scanVertical(originalX, originalY, solidityBit, globalDirection);
        } else {
            return scanHorizontal(originalX, originalY, solidityBit, globalDirection);
        }
    }

    /**
     * Scan using explicit world-space offsets and direction instead of deriving them
     * from the sprite's current ground mode. This is used for ROM-accurate helper
     * routines like {@code CalcRoomInFront}, which probe from predicted world
     * coordinates rather than reusing the rotated push sensor layout.
     */
    SensorResult scanWorld(Direction globalDirection,
                           short worldOffsetX,
                           short worldOffsetY,
                           short dx,
                           short dy,
                           int solidityBit) {
        short originalX = (short) (sprite.getCentreX() + worldOffsetX + dx);
        short originalY = (short) (sprite.getCentreY() + worldOffsetY + dy);
        if (globalDirection == Direction.UP || globalDirection == Direction.DOWN) {
            return scanVertical(originalX, originalY, solidityBit, globalDirection);
        }
        return scanHorizontal(originalX, originalY, solidityBit, globalDirection);
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
            if (isExtension) {
                // ROM FindFloor2: when the tile IS solid but metric=0, the angle register
                // d3 was already loaded from this tile (move.b (a4,d0.w),d3 runs before
                // the metric check). The ROM branches to loc_1E88A (default distance)
                // which returns d1=15-yInTile and d3=this tile's angle.
                // Engine must return a result (not null) to preserve the extension tile's angle.
                byte distance = calculateVerticalDistance((byte) 0, origY, checkY, direction);
                return createResultWithDistance(tile, desc, distance, direction);
            }
            return null;
        }

        // Handle negative metrics (ROM: FindFloor loc_1E85E / FindFloor2 loc_1E900)
        // Negative metric means collision starts from opposite edge (V-flipped tiles)
        if (metric < 0) {
            // ROM: FindCeiling applies eori.w #$F,d2 (XOR Y's bottom 4 bits) before
            // calling FindFloor, mirroring yInTile within the 16px tile. (s2.asm:43895)
            int yInTile = (direction == Direction.UP)
                    ? ((origY ^ 0x0F) & 0x0F)
                    : (origY & 0x0F);
            int adjusted = metric + yInTile;

            if (adjusted >= 0) {
                if (isExtension) {
                    // ROM FindFloor2: adjusted >= 0 → loc_1E88A (default distance).
                    // d3 already holds this tile's angle from earlier load.
                    byte distance = calculateVerticalDistance((byte) 0, origY, checkY, direction);
                    return createResultWithDistance(tile, desc, distance, direction);
                }
                // ROM FindFloor (first pass): bpl → loc_1E7E2 (extend to next tile)
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

        // Full-height tile handling differs between FindFloor and FindFloor2.
        if (metric == FULL_TILE) {
            if (!isExtension) {
                // FindFloor (first pass, s2.asm:43008-43013 loc_1E86A):
                // Recurse to FindFloor2 on the previous tile.
                // ROM subtracts 16 because FindFloor2's distance is relative to the
                // shifted d2. But the engine's scanTileVertical always calculates
                // distance relative to origY, so no adjustment is needed.
                short prevY = (short) (checkY + (direction == Direction.DOWN ? -16 : 16));
                SensorResult prevResult = scanTileVertical(origX, origY, checkX, prevY, solidityBit, direction, true);

                if (prevResult != null) {
                    return prevResult;
                }

                // prevResult null means FindFloor2 found no solid tile — use default
                // distance. ROM FindFloor2 loc_1E88A: d1 = 15 - yInTile (relative to
                // shifted d2), then FindFloor subtracts 16. Engine equivalent: calculate
                // distance from origY to the surface implied by a blank tile at prevY.
                byte distance = calculateVerticalDistance((byte) 0, origY, prevY, direction);
                return createResultWithDistance(tile, desc, distance, direction);
            }
            // FindFloor2 (second pass): NO special full-tile handling in ROM.
            // Falls through to standard distance calculation below.
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
        // ROM parity: distance is always relative to the PROBE's tile, not
        // the tile being scanned. When the extension pass checks the next
        // tile (tileY = origY ± 16), the distance must still be computed
        // from origY's tile base. Using tileY here caused a 16px offset
        // on extension scans, producing wrong-sign results at tile boundaries.
        // ROM: FindFloor uses d2 (original probe Y) for yInTile:
        //   andi.w #$F,d1  → d1 = origY & 0xF
        //   add.w d1,d0    → d0 = metric + yInTile
        //   move.w #$F,d1  → d1 = 15
        //   sub.w d0,d1    → d1 = 15 - metric - yInTile
        short probeBase = (short) (origY & ~0x0F);
        short checkBase = (short) (tileY & ~0x0F);
        // Use checkBase (the tile where collision was found) for the surface
        // position, but the distance is measured from origY.
        if (direction == Direction.DOWN) {
            return (byte) ((checkBase + 15 - metric) - origY);
        } else {
            return (byte) (origY - (checkBase + metric));
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
            // ROM: tile IS solid, angle register d3 loaded before metric check.
            // Pass tile/desc so EXTEND handler can fall back to this tile's angle
            // if the next tile has no collision (matching ROM d3 preservation).
            return wallResult1.set(WallScanState.EXTEND, 0, tile, desc);
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
        // ROM FindWall: d1 = 15 - metric - xInTile (same formula for both directions)
        // But the engine's getWallMetric already accounts for the ROM's d6-based h-flip toggle,
        // and the engine doesn't XOR the x position like the ROM does for LEFTWALL.
        // The combined effect of these two compensations means:
        //   LEFT:  metric is negated by getWallMetric, x is raw → xInTile - metric
        //   RIGHT: metric is raw, x is raw → 15 - metric - xInTile
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
            // ROM: tile IS solid, d3 was loaded with this tile's angle before the metric
            // check. Default distance path preserves the angle. Pass tile/desc so the
            // caller can use this tile's angle as fallback (matching ROM d3 preservation).
            int dist = 15 - xAdjusted;
            return wallResult2.set(WallScanState.FOUND, dist, tile, desc);
        }

        if (metric < 0) {
            if (metric + xAdjusted >= 0) {
                // ROM: d3 already loaded before negative metric branch.
                // Default distance path preserves angle — pass tile/desc.
                int dist = 15 - xAdjusted;
                return wallResult2.set(WallScanState.FOUND, dist, tile, desc);
            }
            int dist = -1 - xAdjusted;
            return wallResult2.set(WallScanState.FOUND, dist, tile, desc);
        }

        // ROM formula: same as evaluateWallTile (see comments there)
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
