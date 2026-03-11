package com.openggf.debug;

public final class DebugOverlayPalette {
    private static final DebugColor[] SENSOR_COLORS = new DebugColor[] {
            new DebugColor(0, 240, 0),   // A
            new DebugColor(56, 255, 162),// B
            new DebugColor(0, 174, 239), // C
            new DebugColor(255, 242, 56),// D
            new DebugColor(255, 56, 255),// E
            new DebugColor(255, 84, 84)  // F
    };
    private static final DebugColor SENSOR_INACTIVE = new DebugColor(140, 140, 140);
    private static final float[] SENSOR_INACTIVE_LINE = new float[] { 0.55f, 0.55f, 0.55f };
    private static final float[][] SENSOR_LINE_COLORS = new float[][] {
            new float[] { 0f, 0.94f, 0f },
            new float[] { 0.22f, 1f, 0.64f },
            new float[] { 0f, 0.68f, 0.94f },
            new float[] { 1f, 0.95f, 0.22f },
            new float[] { 1f, 0.22f, 1f },
            new float[] { 1f, 0.33f, 0.33f }
    };

    private DebugOverlayPalette() {
    }

    public static DebugColor sensorLabelColor(int index, boolean active) {
        if (!active) {
            return SENSOR_INACTIVE;
        }
        if (index < 0 || index >= SENSOR_COLORS.length) {
            return DebugColor.WHITE;
        }
        return SENSOR_COLORS[index];
    }

    public static float[] sensorLineColor(int index, boolean active) {
        if (!active) {
            return SENSOR_INACTIVE_LINE;
        }
        if (index < 0 || index >= SENSOR_LINE_COLORS.length) {
            return new float[] { 1f, 1f, 1f };
        }
        return SENSOR_LINE_COLORS[index];
    }
}
