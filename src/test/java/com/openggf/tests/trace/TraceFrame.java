package com.openggf.tests.trace;

/**
 * One frame of primary trace data from a BizHawk recording.
 * All values match the physics.csv format: positions and speeds are
 * 16-bit values as stored in 68K RAM.
 *
 * <p>Supports both v1 (11 columns) and v2 (18 columns) CSV formats.
 * v2 adds diagnostic fields: x_sub, y_sub, routine, camera_x, camera_y,
 * rings, status_byte. These are not compared by {@link TraceBinder} but
 * appear in divergence report context windows for debugging.
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
    int groundMode,
    // v2 diagnostic fields (display-only, not compared by TraceBinder)
    int xSub,
    int ySub,
    int routine,
    int cameraX,
    int cameraY,
    int rings,
    int statusByte
) {

    /**
     * Convenience factory for tests: creates a TraceFrame with only the core 11 fields,
     * setting all v2 diagnostic fields to defaults (-1 = absent).
     */
    public static TraceFrame of(int frame, int input,
            short x, short y, short xSpeed, short ySpeed, short gSpeed,
            byte angle, boolean air, boolean rolling, int groundMode) {
        return new TraceFrame(frame, input, x, y, xSpeed, ySpeed, gSpeed, angle,
            air, rolling, groundMode, 0, 0, -1, -1, -1, -1, -1);
    }

    /** v1 column count (original format). */
    private static final int V1_COLUMNS = 11;

    /** v2 column count (with diagnostic fields). */
    private static final int V2_COLUMNS = 18;

    /**
     * Parse a single CSV row (all values in hex).
     * Accepts both v1 (11 columns) and v2 (18 columns) format.
     *
     * <p>v1: frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode
     * <p>v2: ...same 11...,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte
     */
    public static TraceFrame parseCsvRow(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length != V1_COLUMNS && parts.length != V2_COLUMNS) {
            throw new IllegalArgumentException(
                "Expected " + V1_COLUMNS + " or " + V2_COLUMNS
                + " CSV columns, got " + parts.length + ": " + line);
        }

        int frame = Integer.parseInt(parts[0].trim(), 16);
        int input = Integer.parseInt(parts[1].trim(), 16);
        short x = (short) Integer.parseInt(parts[2].trim(), 16);
        short y = (short) Integer.parseInt(parts[3].trim(), 16);
        short xSpeed = parseSignedShortHex(parts[4].trim());
        short ySpeed = parseSignedShortHex(parts[5].trim());
        short gSpeed = parseSignedShortHex(parts[6].trim());
        byte angle = (byte) Integer.parseInt(parts[7].trim(), 16);
        boolean air = !parts[8].trim().equals("0");
        boolean rolling = !parts[9].trim().equals("0");
        int groundMode = Integer.parseInt(parts[10].trim());

        // v2 diagnostic fields (default to -1/0 when absent)
        int xSub = 0, ySub = 0, routine = -1, cameraX = -1, cameraY = -1;
        int rings = -1, statusByte = -1;
        if (parts.length >= V2_COLUMNS) {
            xSub = Integer.parseInt(parts[11].trim(), 16);
            ySub = Integer.parseInt(parts[12].trim(), 16);
            routine = Integer.parseInt(parts[13].trim(), 16);
            cameraX = Integer.parseInt(parts[14].trim(), 16);
            cameraY = Integer.parseInt(parts[15].trim(), 16);
            rings = Integer.parseInt(parts[16].trim(), 16);
            statusByte = Integer.parseInt(parts[17].trim(), 16);
        }

        return new TraceFrame(frame, input, x, y, xSpeed, ySpeed, gSpeed, angle,
            air, rolling, groundMode, xSub, ySub, routine, cameraX, cameraY,
            rings, statusByte);
    }

    /** Whether this frame has v2 diagnostic data. */
    public boolean hasExtendedData() {
        return routine >= 0;
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
     * Format the v2 diagnostic fields as a compact string for context windows.
     * Returns empty string if no extended data is available.
     */
    public String formatDiagnostics() {
        if (!hasExtendedData()) return "";
        return String.format("sub=(%04X,%04X) rtn=%02X cam=(%04X,%04X) rings=%d status=%02X",
            xSub, ySub, routine, cameraX, cameraY, rings, statusByte);
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
