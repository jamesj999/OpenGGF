package com.openggf.trace;

/**
 * Per-character trace state used for optional sidekick tracking in schema v5+.
 */
public record TraceCharacterState(
    boolean present,
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
    int statusByte,
    int standOnObj
) {

    public static TraceCharacterState absent() {
        return new TraceCharacterState(false,
            (short) 0, (short) 0, (short) 0, (short) 0, (short) 0,
            (byte) 0, false, false, 0, 0, 0, -1, -1, -1);
    }

    public static TraceCharacterState parseCsvColumns(String[] parts, int offset) {
        boolean present = !parts[offset].trim().equals("0");
        if (!present) {
            return absent();
        }
        return new TraceCharacterState(
            true,
            (short) Integer.parseInt(parts[offset + 1].trim(), 16),
            (short) Integer.parseInt(parts[offset + 2].trim(), 16),
            parseSignedShortHex(parts[offset + 3].trim()),
            parseSignedShortHex(parts[offset + 4].trim()),
            parseSignedShortHex(parts[offset + 5].trim()),
            (byte) Integer.parseInt(parts[offset + 6].trim(), 16),
            !parts[offset + 7].trim().equals("0"),
            !parts[offset + 8].trim().equals("0"),
            Integer.parseInt(parts[offset + 9].trim()),
            Integer.parseInt(parts[offset + 10].trim(), 16),
            Integer.parseInt(parts[offset + 11].trim(), 16),
            Integer.parseInt(parts[offset + 12].trim(), 16),
            Integer.parseInt(parts[offset + 13].trim(), 16),
            Integer.parseInt(parts[offset + 14].trim(), 16));
    }

    public String formatDiagnostics(String label) {
        if (!present) {
            return label + "=absent";
        }
        return String.format(
            "%s=sub=(%04X,%04X) rtn=%02X status=%02X onObj=%02X",
            label, xSub, ySub, routine, statusByte, standOnObj);
    }

    private static short parseSignedShortHex(String hex) {
        int value = Integer.parseInt(hex, 16);
        if (value > 0x7FFF) {
            value -= 0x10000;
        }
        return (short) value;
    }
}
