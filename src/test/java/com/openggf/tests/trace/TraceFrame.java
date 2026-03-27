package com.openggf.tests.trace;

/**
 * One frame of primary trace data from a BizHawk recording.
 * All values match the physics.csv format: positions and speeds are
 * 16-bit values as stored in 68K RAM.
 */
public record TraceFrame(
    int frame,
    int input,
    short x,
    short y,
    short xSpeed,
    short ySpeed,
    short gSpeed,
    byte angle,
    boolean air,
    boolean rolling,
    int groundMode
) {

    /**
     * Parse a single CSV row (all values in hex).
     * Expected format: frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode
     */
    public static TraceFrame parseCsvRow(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length != 11) {
            throw new IllegalArgumentException(
                "Expected 11 CSV columns, got " + parts.length + ": " + line);
        }
        return new TraceFrame(
            Integer.parseInt(parts[0].trim(), 16),
            Integer.parseInt(parts[1].trim(), 16),
            (short) Integer.parseInt(parts[2].trim(), 16),
            (short) Integer.parseInt(parts[3].trim(), 16),
            parseSignedShortHex(parts[4].trim()),
            parseSignedShortHex(parts[5].trim()),
            parseSignedShortHex(parts[6].trim()),
            (byte) Integer.parseInt(parts[7].trim(), 16),
            !parts[8].trim().equals("0"),
            !parts[9].trim().equals("0"),
            Integer.parseInt(parts[10].trim())
        );
    }

    /**
     * Returns true if this frame has identical physics state to another frame.
     * Used to detect lag frames (consecutive frames with no state change).
     * Compares all state fields except frame number and input.
     */
    public boolean stateEquals(TraceFrame other) {
        return this.x == other.x && this.y == other.y
            && this.xSpeed == other.xSpeed && this.ySpeed == other.ySpeed
            && this.gSpeed == other.gSpeed && this.angle == other.angle
            && this.air == other.air && this.rolling == other.rolling
            && this.groundMode == other.groundMode;
    }

    /**
     * Parse a hex string as a signed 16-bit value.
     * Handles both positive ("0380") and negative ("FC00" -> -1024) values.
     */
    private static short parseSignedShortHex(String hex) {
        int value = Integer.parseInt(hex, 16);
        if (value > 0x7FFF) {
            value -= 0x10000;
        }
        return (short) value;
    }
}
