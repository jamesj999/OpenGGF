package com.openggf.physics;

/**
 * Pre-computed lookup tables for trigonometric functions.
 * Uses 256-step angles (byte-based, matching Mega Drive hardware).
 * 
 * The original Sonic games use a 256-step angle system where:
 * - 0x00 = 0° (right)
 * - 0x40 = 90° (down)
 * - 0x80 = 180° (left)
 * - 0xC0 = 270° (up)
 */
public final class TrigLookupTable {

    private static final int TABLE_SIZE = 256;
    private static final double[] SIN_TABLE = new double[TABLE_SIZE];
    private static final double[] COS_TABLE = new double[TABLE_SIZE];

    // Also provide degree-based tables for compatibility with existing code
    private static final double[] SIN_DEG_TABLE = new double[360];
    private static final double[] COS_DEG_TABLE = new double[360];

    /**
     * ROM Angle_Data lookup table from misc/angles.bin (s2.asm:4081).
     * Maps ratio index (0-255) to arctan angle in first quadrant (0-0x20).
     * Used by calcAngle() for ROM-accurate angle calculation.
     */
    private static final int[] ANGLE_DATA = {
            0x00, 0x00, 0x00, 0x00, 0x01, 0x01, 0x01, 0x01, 0x01, 0x01, 0x02, 0x02,
            0x02, 0x02, 0x02, 0x02, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x03, 0x04,
            0x04, 0x04, 0x04, 0x04, 0x04, 0x05, 0x05, 0x05, 0x05, 0x05, 0x05, 0x06,
            0x06, 0x06, 0x06, 0x06, 0x06, 0x06, 0x07, 0x07, 0x07, 0x07, 0x07, 0x07,
            0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x08, 0x09, 0x09, 0x09, 0x09, 0x09,
            0x09, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0a, 0x0b, 0x0b, 0x0b, 0x0b,
            0x0b, 0x0b, 0x0b, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0c, 0x0d, 0x0d,
            0x0d, 0x0d, 0x0d, 0x0d, 0x0d, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e, 0x0e,
            0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x0f, 0x10, 0x10, 0x10, 0x10, 0x10,
            0x10, 0x10, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x12, 0x12,
            0x12, 0x12, 0x12, 0x12, 0x12, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13, 0x13,
            0x13, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x14, 0x15, 0x15, 0x15,
            0x15, 0x15, 0x15, 0x15, 0x15, 0x15, 0x16, 0x16, 0x16, 0x16, 0x16, 0x16,
            0x16, 0x16, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x17, 0x18,
            0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x19, 0x19, 0x19, 0x19,
            0x19, 0x19, 0x19, 0x19, 0x19, 0x19, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a, 0x1a,
            0x1a, 0x1a, 0x1a, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b, 0x1b,
            0x1b, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c, 0x1c,
            0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1d, 0x1e,
            0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1e, 0x1f, 0x1f,
            0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x1f, 0x20, 0x20,
            0x20, 0x20, 0x20, 0x20
    };

    /**
     * Original Sonic engine SINCOSLIST from SPG:Calculations.
     * Returns integer values from -256 to 256 for precision.
     * Divide result by 256 to get typical -1 to 1 decimal result.
     * Index 0 = sin(0), Index 64 = sin(90°), etc.
     */
    private static final short[] SINCOSLIST = {
            // 0-63: 0° to ~90°
            0, 6, 12, 18, 25, 31, 37, 43, 49, 56, 62, 68, 74, 80, 86, 92,
            97, 103, 109, 115, 120, 126, 131, 136, 142, 147, 152, 157, 162, 167, 171, 176,
            181, 185, 189, 193, 197, 201, 205, 209, 212, 216, 219, 222, 225, 228, 231, 234,
            236, 238, 241, 243, 244, 246, 248, 249, 251, 252, 253, 254, 254, 255, 255, 255,
            // 64-127: ~90° to ~180°
            256, 255, 255, 255, 254, 254, 253, 252, 251, 249, 248, 246, 244, 243, 241, 238,
            236, 234, 231, 228, 225, 222, 219, 216, 212, 209, 205, 201, 197, 193, 189, 185,
            181, 176, 171, 167, 162, 157, 152, 147, 142, 136, 131, 126, 120, 115, 109, 103,
            97, 92, 86, 80, 74, 68, 62, 56, 49, 43, 37, 31, 25, 18, 12, 6,
            // 128-191: ~180° to ~270°
            0, -6, -12, -18, -25, -31, -37, -43, -49, -56, -62, -68, -74, -80, -86, -92,
            -97, -103, -109, -117, -120, -126, -131, -136, -142, -147, -152, -157, -162, -167, -171, -176,
            -181, -185, -189, -193, -197, -201, -205, -209, -212, -216, -219, -222, -225, -228, -231, -234,
            -236, -238, -241, -243, -244, -246, -248, -249, -251, -252, -253, -254, -254, -255, -255, -255,
            // 192-255: ~270° to ~360°
            -256, -255, -255, -255, -254, -254, -253, -252, -251, -249, -248, -246, -244, -243, -241, -238,
            -236, -234, -231, -228, -225, -222, -219, -216, -212, -209, -205, -201, -197, -193, -189, -185,
            -181, -176, -171, -167, -162, -157, -152, -147, -142, -136, -131, -126, -120, -117, -109, -103,
            -97, -92, -86, -80, -74, -68, -62, -56, -49, -43, -37, -31, -25, -18, -12, -6
    };

    static {
        // Initialize 256-step tables (native Mega Drive format)
        for (int i = 0; i < TABLE_SIZE; i++) {
            double radians = i * (2.0 * Math.PI / TABLE_SIZE);
            SIN_TABLE[i] = Math.sin(radians);
            COS_TABLE[i] = Math.cos(radians);
        }

        // Initialize degree-based tables for compatibility
        for (int i = 0; i < 360; i++) {
            double radians = Math.toRadians(i);
            SIN_DEG_TABLE[i] = Math.sin(radians);
            COS_DEG_TABLE[i] = Math.cos(radians);
        }
    }

    private TrigLookupTable() {
        // Prevent instantiation
    }

    /**
     * Get sine value for a byte angle (0-255, wraps automatically).
     * 
     * @param byteAngle The angle in 256-step format
     * @return The sine value
     */
    public static double sin(int byteAngle) {
        return SIN_TABLE[byteAngle & 0xFF];
    }

    /**
     * Get cosine value for a byte angle (0-255, wraps automatically).
     * 
     * @param byteAngle The angle in 256-step format
     * @return The cosine value
     */
    public static double cos(int byteAngle) {
        return COS_TABLE[byteAngle & 0xFF];
    }

    /**
     * Get sine value for a degree angle (0-359, wraps to valid range).
     * 
     * @param degrees The angle in degrees
     * @return The sine value
     */
    public static double sinDeg(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        return SIN_DEG_TABLE[normalized];
    }

    /**
     * Get cosine value for a degree angle (0-359, wraps to valid range).
     * 
     * @param degrees The angle in degrees
     * @return The cosine value
     */
    public static double cosDeg(int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        return COS_DEG_TABLE[normalized];
    }

    /**
     * Convert byte angle to degrees (for debugging/display).
     * 
     * @param byteAngle The angle in 256-step format
     * @return The equivalent angle in degrees
     */
    public static double toDegrees(int byteAngle) {
        return (byteAngle & 0xFF) * (360.0 / 256.0);
    }

    /**
     * Convert degrees to byte angle.
     * 
     * @param degrees The angle in degrees
     * @return The equivalent angle in 256-step format
     */
    public static int toByteAngle(double degrees) {
        return (int) Math.round(degrees * (256.0 / 360.0)) & 0xFF;
    }

    /**
     * Get sine value using original Mega Drive SINCOSLIST.
     * Returns an integer from -256 to 256 for precision.
     * Divide by 256.0 to get a -1 to 1 result.
     * 
     * SPG: These are the exact values from the original game.
     * 
     * @param hexAngle The angle in 256-step hex format (0x00-0xFF)
     * @return Integer sine value from -256 to 256
     */
    public static int sinHex(int hexAngle) {
        return SINCOSLIST[hexAngle & 0xFF];
    }

    /**
     * Get cosine value using original Mega Drive SINCOSLIST.
     * Returns an integer from -256 to 256 for precision.
     * Divide by 256.0 to get a -1 to 1 result.
     * 
     * SPG: cos(angle) = sin(angle + 64) due to 256-step circle.
     * 
     * @param hexAngle The angle in 256-step hex format (0x00-0xFF)
     * @return Integer cosine value from -256 to 256
     */
    public static int cosHex(int hexAngle) {
        return SINCOSLIST[(hexAngle + 64) & 0xFF];
    }

    /**
     * Get sine as a normalized double using hex angle lookup.
     * Convenience method that divides by 256 for -1 to 1 result.
     * 
     * @param hexAngle The angle in 256-step hex format
     * @return Sine value from -1.0 to 1.0
     */
    public static double sinHexNormalized(int hexAngle) {
        return SINCOSLIST[hexAngle & 0xFF] / 256.0;
    }

    /**
     * Get cosine as a normalized double using hex angle lookup.
     * Convenience method that divides by 256 for -1 to 1 result.
     *
     * @param hexAngle The angle in 256-step hex format
     * @return Cosine value from -1.0 to 1.0
     */
    public static double cosHexNormalized(int hexAngle) {
        return SINCOSLIST[(hexAngle + 64) & 0xFF] / 256.0;
    }

    /**
     * Calculates the angle from velocity components, matching ROM's CalcAngle routine.
     * ROM: CalcAngle at s2.asm:4033-4076 - converts (xSpeed, ySpeed) to a hex angle.
     *
     * Uses the ANGLE_DATA lookup table and integer math exactly as the ROM does:
     * 1. If both x and y are 0, return 0x40 (down)
     * 2. Calculate abs(x), abs(y)
     * 3. If abs(y) >= abs(x): index = (abs(x) << 8) / abs(y), angle = 0x40 - ANGLE_DATA[index]
     * 4. Else: index = (abs(y) << 8) / abs(x), angle = ANGLE_DATA[index]
     * 5. Adjust quadrant based on signs of x and y
     *
     * The Mega Drive angle convention:
     * - 0x00 = 0° (right)
     * - 0x40 = 90° (down, due to Y+ = down in screen coords)
     * - 0x80 = 180° (left)
     * - 0xC0 = 270° (up)
     *
     * @param xSpeed X velocity (subpixels) - positive = right
     * @param ySpeed Y velocity (subpixels) - positive = down (screen coords)
     * @return Hex angle (0x00-0xFF) representing the movement direction
     */
    public static int calcAngle(short xSpeed, short ySpeed) {
        // ROM: CalcAngle_Zero returns 0x40 (90 degrees / down) for zero velocity
        // s2.asm:4039-4040: or.w d3,d4 / beq.s CalcAngle_Zero
        if (xSpeed == 0 && ySpeed == 0) {
            return 0x40;
        }

        // s2.asm:4043-4044: absw.w d3 / absw.w d4
        int absX = Math.abs(xSpeed);
        int absY = Math.abs(ySpeed);

        int angle;
        // s2.asm:4045-4057: compare and calculate using lookup table
        if (absY >= absX) {
            // s2.asm:4054-4057: lsl.l #8,d3 / divu.w d4,d3 / moveq #$40,d0 / sub.b Angle_Data(pc,d3.w),d0
            int index = (absX << 8) / absY;
            if (index > 255) index = 255;  // Clamp to table size
            angle = 0x40 - ANGLE_DATA[index];
        } else {
            // s2.asm:4048-4051: lsl.l #8,d4 / divu.w d3,d4 / moveq #0,d0 / move.b Angle_Data(pc,d4.w),d0
            int index = (absY << 8) / absX;
            if (index > 255) index = 255;  // Clamp to table size
            angle = ANGLE_DATA[index];
        }

        // s2.asm:4059-4062: Adjust for left half (x < 0)
        // tst.w d1 / bpl.w + / neg.w d0 / addi.w #$80,d0
        if (xSpeed < 0) {
            angle = -angle + 0x80;
        }

        // s2.asm:4064-4067: Adjust for bottom half (y < 0)
        // tst.w d2 / bpl.w + / neg.w d0 / addi.w #$100,d0
        if (ySpeed < 0) {
            angle = -angle + 0x100;
        }

        return angle & 0xFF;
    }

    /**
     * Calculates the movement quadrant from velocity, matching ROM's collision logic.
     * ROM: Sonic_DoLevelCollision (s2.asm:37547-37557)
     *
     * The calculation is: ((angle - 0x20) & 0xC0)
     * This offsets the quadrant boundaries by 32 degrees from the cardinal directions.
     *
     * The quadrant determines which collision path to take:
     * - 0xC0: Moving mostly right (angles 0-31, 224-255) → Sonic_HitRightWall path
     * - 0x00: Moving mostly down (angles 32-95) → default floor/wall check path
     * - 0x40: Moving mostly left (angles 96-159) → Sonic_HitLeftWall path
     * - 0x80: Moving mostly up (angles 160-223) → Sonic_HitCeilingAndWalls path
     *
     * @param xSpeed X velocity (subpixels)
     * @param ySpeed Y velocity (subpixels)
     * @return Movement quadrant (0x00, 0x40, 0x80, or 0xC0)
     */
    public static int calcMovementQuadrant(short xSpeed, short ySpeed) {
        int moveAngle = calcAngle(xSpeed, ySpeed);
        // ROM: subi.b #$20,d0 / andi.b #$C0,d0
        return ((moveAngle - 0x20) & 0xC0) & 0xFF;
    }
}
