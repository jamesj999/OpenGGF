package com.openggf.level.objects;

/**
 * Shared waypoint path-following utilities used by conveyor-belt platform objects
 * across S1 and S2.
 * <p>
 * These objects follow closed-loop waypoint paths. The velocity calculation
 * (LCon_ChangeDir in the S1 disassembly) picks the dominant axis (whichever
 * distance is larger), sets that axis to +/-speed, and calculates proportional
 * velocity on the minor axis via 68000-style signed division.
 * <p>
 * Waypoint index wrapping mirrors the ROM's unsigned-byte comparison logic:
 * overflow wraps to 0, underflow wraps to the last waypoint.
 * <p>
 * <b>Disassembly references:</b>
 * <ul>
 *   <li>S1: docs/s1disasm/_incObj/63 LZ Conveyor.asm, LCon_ChangeDir (lines 226-280)</li>
 *   <li>S2: s2.asm lines 54345-54398, loc_281DA</li>
 * </ul>
 */
public final class WaypointPathFollower {

    private WaypointPathFollower() {
        // Static utility class
    }

    /**
     * Result of the dominant-axis velocity calculation.
     *
     * @param xVel X velocity in 8.8 fixed point
     * @param yVel Y velocity in 8.8 fixed point
     * @param xSub X subpixel remainder from division (initializes fractional accumulator)
     * @param ySub Y subpixel remainder from division (initializes fractional accumulator)
     */
    public record VelocityResult(int xVel, int yVel, int xSub, int ySub) {}

    /**
     * Calculates velocity components to move from the current position toward a
     * target waypoint using the LCon_ChangeDir dominant-axis algorithm.
     * <p>
     * Algorithm: Determine which axis has the greater absolute distance.
     * The dominant axis gets a fixed speed of {@code speed} (typically 0x100 = 1 pixel/frame
     * in 8.8 fixed point). The minor axis gets a proportionally scaled speed via
     * 68000-style signed division. The division remainder initializes the subpixel
     * fractional accumulator for ROM-accurate interpolation.
     * <p>
     * From disassembly LCon_ChangeDir:
     * <pre>
     * d0 = |x - targetX|,  d2 = sign(x - targetX) * -speed
     * d1 = |y - targetY|,  d3 = sign(y - targetY) * -speed
     * if |dy| >= |dx|:  (Y dominant)
     *   xVel = -((x - targetX) &lt;&lt; 8) / |dy|,  yVel = d3
     * else:  (X dominant)
     *   yVel = -((y - targetY) &lt;&lt; 8) / |dx|,  xVel = d2
     * </pre>
     *
     * @param x      current X position
     * @param y      current Y position
     * @param targetX target waypoint X
     * @param targetY target waypoint Y
     * @param speed  velocity magnitude (typically 0x100)
     * @return velocity and subpixel remainder for both axes
     */
    public static VelocityResult calculateWaypointVelocity(int x, int y,
                                                            int targetX, int targetY,
                                                            int speed) {
        int dx = (short) (x - targetX);
        int dy = (short) (y - targetY);

        int absDx = Math.abs(dx);
        int absDy = Math.abs(dy);

        // Sign for velocity on dominant axis: move toward target
        // d2 = -speed initially; if dx < 0 (target is to the right), negate to +speed
        int speedDirX = (dx >= 0) ? -speed : speed;
        int speedDirY = (dy >= 0) ? -speed : speed;

        if (absDy >= absDx) {
            // Y-axis dominant (or equal)
            int yVel = speedDirY;
            int xVel;
            int xSub;

            if (absDy == 0 || dx == 0) {
                xVel = 0;
                xSub = 0;
            } else {
                // ext.l d0 / asl.l #8,d0 / divs.w d1,d0 / neg.w d0
                int numerator = dx << 8;
                int quotient = numerator / absDy;
                int remainder = numerator % absDy;
                xVel = (short) (-quotient);
                xSub = remainder & 0xFFFF;
            }

            return new VelocityResult(xVel, yVel, xSub, 0);
        } else {
            // X-axis dominant
            int xVel = speedDirX;
            int yVel;
            int ySub;

            if (absDx == 0 || dy == 0) {
                yVel = 0;
                ySub = 0;
            } else {
                // ext.l d1 / asl.l #8,d1 / divs.w d0,d1 / neg.w d1
                int numerator = dy << 8;
                int quotient = numerator / absDx;
                int remainder = numerator % absDx;
                yVel = (short) (-quotient);
                ySub = remainder & 0xFFFF;
            }

            return new VelocityResult(xVel, yVel, 0, ySub);
        }
    }

    /**
     * Wraps a waypoint byte index around the path length, matching the ROM's
     * unsigned-byte comparison logic.
     * <p>
     * When advancing past the end, wraps to 0. When going before 0, wraps to
     * the last valid waypoint (pathLength - step).
     * <p>
     * From disassembly:
     * <pre>
     * add.b   objoff_3A(a0),d1        ; d1 = current offset + delta
     * cmp.b   objoff_39(a0),d1        ; compare with path byte length
     * blo.s   use_it                  ; if d1 &lt; length (unsigned), use it
     * tst.b   d0                      ; check if went negative
     * bpl.s   use_zero                ; positive overflow -&gt; wrap to 0
     * move.b  objoff_39(a0),d1        ; negative underflow -&gt; wrap to last
     * subq.b  #4,d1
     * </pre>
     *
     * @param index      the new waypoint byte index (may be out of bounds)
     * @param pathLength total path length in bytes (waypointCount * 4)
     * @param step       waypoint step size in bytes (typically 4)
     * @return the wrapped waypoint byte index
     */
    public static int wrapWaypointIndex(int index, int pathLength, int step) {
        if (index >= 0 && index < pathLength) {
            return index;
        }

        if (index < 0) {
            // Underflow: wrap to last waypoint
            return pathLength - step;
        } else {
            // Overflow: wrap to first waypoint
            return 0;
        }
    }
}
