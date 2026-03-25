package com.openggf.level.objects;

import com.openggf.physics.ObjectTerrainUtils;
import com.openggf.physics.TerrainCheckResult;

/**
 * Shared patrol movement logic for badniks that walk back and forth on platforms.
 * <p>
 * Encapsulates the common ROM pattern:
 * <ol>
 *   <li>SpeedToPos / ObjectMove: apply subpixel velocity to X position</li>
 *   <li>ObjCheckFloorDist: check floor distance at new position</li>
 *   <li>Edge detection: if floor distance outside [minFloor, maxFloor), signal reversal</li>
 *   <li>Floor snap: otherwise adjust Y to match terrain surface</li>
 * </ol>
 * <p>
 * Callers handle the reversal action themselves (state transition, velocity negate, etc.)
 * since each badnik responds differently to reaching an edge.
 */
public final class PatrolMovementHelper {

    private PatrolMovementHelper() {}

    /**
     * Result of a patrol movement update.
     *
     * @param newX     updated X position (integer part)
     * @param newXSub  updated X subpixel accumulator (0-255)
     * @param newY     updated Y position (snapped to floor, or unchanged if reversed)
     * @param reversed true if an edge was detected (no valid floor within thresholds)
     */
    public record PatrolResult(int newX, int newXSub, int newY, boolean reversed) {}

    /**
     * Apply subpixel velocity and check for floor edge.
     * <p>
     * Standard badnik "walk + ObjFloorDist" pattern from the ROM.
     * The velocity is applied using 16.8 fixed-point arithmetic (SpeedToPos equivalent).
     * Floor is checked at the new X position using {@link ObjectTerrainUtils#checkFloorDist(int, int, int)}.
     *
     * @param x        current X position (integer)
     * @param xSub     current X subpixel accumulator (0-255)
     * @param y        current Y position (integer)
     * @param velocity subpixel velocity (e.g. 0x100 = 1px/frame, 0x40 = 0.25px/frame)
     * @param yRadius  collision Y radius for floor check
     * @param minFloor minimum acceptable floor distance (typically -8 or -1)
     * @param maxFloor maximum acceptable floor distance (typically 0x0C or 12); values >= this are rejected
     * @return PatrolResult with updated position and whether direction should be reversed
     */
    public static PatrolResult updatePatrol(
            int x, int xSub, int y, int velocity,
            int yRadius, int minFloor, int maxFloor) {
        // SpeedToPos: apply subpixel velocity
        int pos24 = (x << 8) | (xSub & 0xFF);
        pos24 += velocity;
        int newX = pos24 >> 8;
        int newXSub = pos24 & 0xFF;

        // ObjCheckFloorDist: check floor at new X position
        TerrainCheckResult floor = ObjectTerrainUtils.checkFloorDist(newX, y, yRadius);

        boolean reversed = false;
        int newY = y;

        if (!floor.foundSurface() || floor.distance() < minFloor || floor.distance() >= maxFloor) {
            // Edge detected: no valid floor surface within acceptable range
            reversed = true;
        } else {
            // Valid floor: snap Y to terrain surface
            newY = y + floor.distance();
        }
        return new PatrolResult(newX, newXSub, newY, reversed);
    }

    /**
     * Apply subpixel velocity without floor check (for timer-based patrol).
     * <p>
     * Returns the updated X position and subpixel accumulator only.
     * Useful for badniks that reverse based on a timer rather than edge detection.
     *
     * @param x        current X position (integer)
     * @param xSub     current X subpixel accumulator (0-255)
     * @param velocity subpixel velocity
     * @return PatrolResult with updated X position; newY is 0 and reversed is false
     */
    public static PatrolResult applyVelocity(int x, int xSub, int velocity) {
        int pos24 = (x << 8) | (xSub & 0xFF);
        pos24 += velocity;
        return new PatrolResult(pos24 >> 8, pos24 & 0xFF, 0, false);
    }
}
