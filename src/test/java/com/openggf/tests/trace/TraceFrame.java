package com.openggf.tests.trace;

import java.util.Objects;

/**
 * One frame of primary trace data from a BizHawk recording.
 * All values match the physics.csv format: positions and speeds are
 * 16-bit values as stored in 68K RAM.
 *
 * <p>Supports v1 (11 columns), v2 (18 columns), v2.1 (19 columns), v2.2 (20 columns),
 * and v3 (22 columns) CSV formats. v2 adds diagnostic fields: x_sub, y_sub, routine,
 * camera_x, camera_y, rings, status_byte. v2.1 adds gameplay_frame_counter (ROM's
 * Level_MainLoop counter). v2.2 adds stand_on_obj (SST slot index of object Sonic is
 * riding on). v3 adds vblank_counter and lag_counter. v5 appends optional first-sidekick
 * state. v6 stores explicit named character blocks for both Sonic and Tails while
 * retaining the same in-memory primary/sidekick representation. Diagnostic fields are
 * not compared by {@link TraceBinder} but appear in divergence report context windows
 * for debugging.
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
    int statusByte,
    // v2.1: ROM gameplay frame counter (Level_MainLoop counter)
    int gameplayFrameCounter,
    // v2.2: SST slot index of object Sonic is standing on (0 = none, -1 = absent)
    int standOnObj,
    // v3: ROM VBlank counter and lag-frame counter
    int vblankCounter,
    int lagCounter,
    // v5: optional first-sidekick state (for Sonic 2 this is Tails)
    TraceCharacterState sidekick
) {

    public TraceFrame(
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
        int xSub,
        int ySub,
        int routine,
        int cameraX,
        int cameraY,
        int rings,
        int statusByte,
        int gameplayFrameCounter,
        int standOnObj,
        int vblankCounter,
        int lagCounter
    ) {
        this(frame, input, x, y, xSpeed, ySpeed, gSpeed, angle, air, rolling, groundMode,
            xSub, ySub, routine, cameraX, cameraY, rings, statusByte, gameplayFrameCounter,
            standOnObj, vblankCounter, lagCounter, null);
    }

    /**
     * Convenience factory for tests: creates a TraceFrame with only the core 11 fields,
     * setting all v2/v2.1/v2.2/v3 diagnostic fields to defaults (-1 = absent).
     */
    public static TraceFrame of(int frame, int input,
            short x, short y, short xSpeed, short ySpeed, short gSpeed,
            byte angle, boolean air, boolean rolling, int groundMode) {
        return new TraceFrame(frame, input, x, y, xSpeed, ySpeed, gSpeed, angle,
            air, rolling, groundMode, 0, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1, null);
    }

    /**
     * Minimal factory for execution-model tests.
     */
    public static TraceFrame executionTestFrame(
            int frame, int vblankCounter, int gameplayFrameCounter, int lagCounter) {
        return new TraceFrame(frame, 0, (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
            (byte) 0, false, false, 0, 0, 0, -1, -1, -1, -1, -1,
            gameplayFrameCounter, -1, vblankCounter, lagCounter, null);
    }

    /** v1 column count (original format). */
    private static final int V1_COLUMNS = 11;

    /** v2 column count (with diagnostic fields). */
    private static final int V2_COLUMNS = 18;

    /** v2.1 column count (v2 + gameplay_frame_counter). */
    private static final int V21_COLUMNS = 19;

    /** v2.2 column count (v2.1 + stand_on_obj). */
    private static final int V22_COLUMNS = 20;

    /** v3 column count (v2.2 + vblank_counter + lag_counter). */
    private static final int V3_COLUMNS = 22;

    /** v5 column count (v3 + first-sidekick state block). */
    private static final int V5_COLUMNS = 37;

    /** v6 column count (shared counters + explicit Sonic/Tails state blocks). */
    private static final int V6_COLUMNS = 38;

    /**
     * Parse a single CSV row (all values in hex).
     * Accepts v1 (11), v2 (18), v2.1 (19), v2.2 (20), v3/v4 (22), v5 (37),
     * and v6 (38) column formats.
     *
     * <p>v1: frame,input,x,y,x_speed,y_speed,g_speed,angle,air,rolling,ground_mode
     * <p>v2: ...same 11...,x_sub,y_sub,routine,camera_x,camera_y,rings,status_byte
     * <p>v2.1: ...same 18...,gameplay_frame_counter
     * <p>v2.2: ...same 19...,stand_on_obj
     * <p>v3/v4: ...same 20...,vblank_counter,lag_counter
     * <p>v5: ...same 22...,sidekick_present,sidekick_x,sidekick_y,sidekick_x_speed,
     * sidekick_y_speed,sidekick_g_speed,sidekick_angle,sidekick_air,sidekick_rolling,
     * sidekick_ground_mode,sidekick_x_sub,sidekick_y_sub,sidekick_routine,
     * sidekick_status_byte,sidekick_stand_on_obj
     * <p>v6: frame,input,camera_x,camera_y,rings,gameplay_frame_counter,vblank_counter,
     * lag_counter,sonic_present,sonic_x,...,sonic_stand_on_obj,tails_present,tails_x,...,
     * tails_stand_on_obj
     */
    public static TraceFrame parseCsvRow(String line) {
        return parseCsvRow(line, null);
    }

    /**
     * Parse a single CSV row, using metadata schema when present to disambiguate newer formats.
     */
    public static TraceFrame parseCsvRow(String line, Integer traceSchema) {
        String[] parts = line.split(",", -1);
        if (parts.length != V1_COLUMNS && parts.length != V2_COLUMNS
                && parts.length != V21_COLUMNS && parts.length != V22_COLUMNS
                && parts.length != V3_COLUMNS && parts.length != V5_COLUMNS
                && parts.length != V6_COLUMNS) {
            throw new IllegalArgumentException(
                "Expected " + V1_COLUMNS + ", " + V2_COLUMNS + ", " + V21_COLUMNS
                + ", " + V22_COLUMNS + ", " + V3_COLUMNS + ", " + V5_COLUMNS
                + ", or " + V6_COLUMNS
                + " CSV columns, got " + parts.length + ": " + line);
        }

        if ((traceSchema != null && traceSchema >= 6) || parts.length >= V6_COLUMNS) {
            if (parts.length < V6_COLUMNS) {
                throw new IllegalArgumentException(
                    "Schema v6 requires " + V6_COLUMNS + " CSV columns, got " + parts.length
                        + ": " + line);
            }
            return parseV6Row(parts);
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

        int xSub = 0;
        int ySub = 0;
        int routine = -1;
        int cameraX = -1;
        int cameraY = -1;
        int rings = -1;
        int statusByte = -1;
        int gameplayFrameCounter = -1;
        int standOnObj = -1;
        int vblankCounter = -1;
        int lagCounter = -1;
        TraceCharacterState sidekick = null;
        if (parts.length >= V2_COLUMNS) {
            xSub = Integer.parseInt(parts[11].trim(), 16);
            ySub = Integer.parseInt(parts[12].trim(), 16);
            routine = Integer.parseInt(parts[13].trim(), 16);
            cameraX = Integer.parseInt(parts[14].trim(), 16);
            cameraY = Integer.parseInt(parts[15].trim(), 16);
            rings = Integer.parseInt(parts[16].trim(), 16);
            statusByte = Integer.parseInt(parts[17].trim(), 16);
        }
        if (parts.length >= V21_COLUMNS) {
            gameplayFrameCounter = Integer.parseInt(parts[18].trim(), 16);
        }
        if (parts.length >= V22_COLUMNS) {
            standOnObj = Integer.parseInt(parts[19].trim(), 16);
        }
        if ((traceSchema != null && traceSchema >= 3) || parts.length >= V3_COLUMNS) {
            if (parts.length < V3_COLUMNS) {
                throw new IllegalArgumentException(
                    "Schema v3 requires " + V3_COLUMNS + " CSV columns, got " + parts.length
                        + ": " + line);
            }
            vblankCounter = Integer.parseInt(parts[20].trim(), 16);
            lagCounter = Integer.parseInt(parts[21].trim(), 16);
        }
        if ((traceSchema != null && traceSchema >= 5) || parts.length >= V5_COLUMNS) {
            if (parts.length < V5_COLUMNS) {
                throw new IllegalArgumentException(
                    "Schema v5 requires " + V5_COLUMNS + " CSV columns, got " + parts.length
                        + ": " + line);
            }
            sidekick = TraceCharacterState.parseCsvColumns(parts, V3_COLUMNS);
        }

        return new TraceFrame(frame, input, x, y, xSpeed, ySpeed, gSpeed, angle,
            air, rolling, groundMode, xSub, ySub, routine, cameraX, cameraY,
            rings, statusByte, gameplayFrameCounter, standOnObj, vblankCounter, lagCounter,
            sidekick);
    }

    private static TraceFrame parseV6Row(String[] parts) {
        int frame = Integer.parseInt(parts[0].trim(), 16);
        int input = Integer.parseInt(parts[1].trim(), 16);
        int cameraX = Integer.parseInt(parts[2].trim(), 16);
        int cameraY = Integer.parseInt(parts[3].trim(), 16);
        int rings = Integer.parseInt(parts[4].trim(), 16);
        int gameplayFrameCounter = Integer.parseInt(parts[5].trim(), 16);
        int vblankCounter = Integer.parseInt(parts[6].trim(), 16);
        int lagCounter = Integer.parseInt(parts[7].trim(), 16);

        TraceCharacterState sonic = TraceCharacterState.parseCsvColumns(parts, 8);
        TraceCharacterState tails = TraceCharacterState.parseCsvColumns(parts, 23);

        return new TraceFrame(frame, input,
            sonic.x(), sonic.y(), sonic.xSpeed(), sonic.ySpeed(), sonic.gSpeed(), sonic.angle(),
            sonic.air(), sonic.rolling(), sonic.groundMode(),
            sonic.xSub(), sonic.ySub(), sonic.routine(),
            cameraX, cameraY, rings, sonic.statusByte(),
            gameplayFrameCounter, sonic.standOnObj(), vblankCounter, lagCounter, tails);
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
            && this.groundMode == other.groundMode
            && Objects.equals(this.sidekick, other.sidekick);
    }

    /**
     * Returns the primary playable character state carried by the core CSV columns.
     * For Sonic 2 traces this is Sonic.
     */
    public TraceCharacterState primaryCharacterState() {
        return new TraceCharacterState(true,
            x, y, xSpeed, ySpeed, gSpeed, angle, air, rolling, groundMode,
            xSub, ySub, routine, statusByte, standOnObj);
    }

    /**
     * Format the v2/v2.1/v2.2/v3 diagnostic fields as a compact string for context windows.
     * Returns empty string if no extended data is available.
     */
    public String formatDiagnostics() {
        if (!hasExtendedData()) return "";
        String base = String.format("sub=(%04X,%04X) rtn=%02X cam=(%04X,%04X) rings=%d status=%02X",
            xSub, ySub, routine, cameraX, cameraY, rings, statusByte);
        if (gameplayFrameCounter >= 0) {
            base += String.format(" gfc=%04X", gameplayFrameCounter);
        }
        if (standOnObj >= 0) {
            base += String.format(" onObj=%02X", standOnObj);
        }
        if (vblankCounter >= 0) {
            base += String.format(" vbc=%04X", vblankCounter);
        }
        if (lagCounter >= 0) {
            base += String.format(" lag=%04X", lagCounter);
        }
        if (sidekick != null) {
            base += " " + sidekick.formatDiagnostics("sidekick");
        }
        return base;
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
