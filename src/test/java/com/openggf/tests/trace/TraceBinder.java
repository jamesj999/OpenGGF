package com.openggf.tests.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Comparison engine: compares engine sprite state against expected trace state
 * for a single frame, applying configurable tolerance thresholds.
 */
public class TraceBinder {

    private final ToleranceConfig tolerances;
    private final List<FrameComparison> allComparisons = new ArrayList<>();

    public TraceBinder(ToleranceConfig tolerances) {
        this.tolerances = tolerances;
    }

    /**
     * Compare a single frame's expected trace values against actual engine values.
     * Accepts raw values extracted from the sprite to keep this class decoupled
     * from AbstractPlayableSprite.
     */
    public FrameComparison compareFrame(
            TraceFrame expected,
            short actualX, short actualY,
            short actualXSpeed, short actualYSpeed, short actualGSpeed,
            byte actualAngle, boolean actualAir, boolean actualRolling,
            int actualGroundMode) {
        return compareFrame(expected, actualX, actualY, actualXSpeed, actualYSpeed,
            actualGSpeed, actualAngle, actualAir, actualRolling, actualGroundMode, null);
    }

    /**
     * Compare a single frame with optional engine-side diagnostic context.
     * The diagnostics are display-only (not compared for pass/fail) but appear
     * in the context window alongside ROM trace diagnostics for cross-referencing.
     */
    public FrameComparison compareFrame(
            TraceFrame expected,
            short actualX, short actualY,
            short actualXSpeed, short actualYSpeed, short actualGSpeed,
            byte actualAngle, boolean actualAir, boolean actualRolling,
            int actualGroundMode,
            EngineDiagnostics engineDiag) {

        Map<String, FieldComparison> fields = new LinkedHashMap<>();

        // Position comparisons
        fields.put("x", compareNumeric("x", expected.x(), actualX,
            tolerances.positionWarn(), tolerances.positionError(), false));
        fields.put("y", compareNumeric("y", expected.y(), actualY,
            tolerances.positionWarn(), tolerances.positionError(), false));

        // Speed comparisons
        fields.put("x_speed", compareNumeric("x_speed", expected.xSpeed(), actualXSpeed,
            tolerances.speedWarn(), tolerances.speedError(), tolerances.speedSignChangeIsError()));
        fields.put("y_speed", compareNumeric("y_speed", expected.ySpeed(), actualYSpeed,
            tolerances.speedWarn(), tolerances.speedError(), tolerances.speedSignChangeIsError()));
        fields.put("g_speed", compareNumeric("g_speed", expected.gSpeed(), actualGSpeed,
            tolerances.speedWarn(), tolerances.speedError(), tolerances.speedSignChangeIsError()));

        // Angle comparison (circular: 0xFC and 0x04 are 8 apart, not 248)
        fields.put("angle", compareAngle("angle",
            expected.angle() & 0xFF, actualAngle & 0xFF,
            tolerances.angleWarn(), tolerances.angleError()));

        // Boolean/enum flags: any mismatch is ERROR
        fields.put("air", compareFlag("air", expected.air(), actualAir));
        fields.put("rolling", compareFlag("rolling", expected.rolling(), actualRolling));

        // Derive ground_mode from angle for BOTH sides. The ROM has no stored ground_mode
        // variable; the Lua script and engine both compute it from angle. During airborne
        // frames the engine doesn't call updateGroundMode(), so the stored value is stale.
        // Deriving from angle makes the comparison symmetric and correct.
        int expectedGroundMode = deriveGroundMode(expected.angle() & 0xFF);
        int derivedActualGroundMode = deriveGroundMode(actualAngle & 0xFF);
        fields.put("ground_mode", compareEnum("ground_mode",
            expectedGroundMode, derivedActualGroundMode));

        // Store diagnostic context (ROM trace + engine side) for the context window.
        // These are NOT compared for pass/fail — they're for human debugging only.
        String romDiag = expected.hasExtendedData() ? expected.formatDiagnostics() : "";
        String engDiag = engineDiag != null ? engineDiag.format() : "";

        FrameComparison result = new FrameComparison(expected.frame(), fields, romDiag, engDiag);
        allComparisons.add(result);
        return result;
    }

    /**
     * Build a divergence report from all comparisons accumulated so far.
     */
    public DivergenceReport buildReport() {
        return new DivergenceReport(allComparisons);
    }

    /**
     * Validate that BK2-derived input matches trace-embedded input.
     * Returns true if they match, false on mismatch (alignment error).
     */
    public boolean validateInput(TraceFrame frame, int bk2Input) {
        return frame.input() == bk2Input;
    }

    private FieldComparison compareNumeric(String name, int expected, int actual,
            int warn, int error, boolean signChangeIsError) {
        int delta = Math.abs(expected - actual);

        // Check sign change (both nonzero, different signs)
        if (signChangeIsError && expected != 0 && actual != 0) {
            boolean expectedNeg = (expected < 0) || (expected > 0x7FFF);
            boolean actualNeg = (actual < 0) || (actual > 0x7FFF);
            if (expectedNeg != actualNeg) {
                return new FieldComparison(name,
                    formatHex(expected), formatHex(actual), Severity.ERROR, delta);
            }
        }

        Severity severity = tolerances.classify(delta, warn, error);
        return new FieldComparison(name,
            formatHex(expected), formatHex(actual), severity, delta);
    }

    private FieldComparison compareFlag(String name, boolean expected, boolean actual) {
        if (expected == actual) {
            return new FieldComparison(name,
                String.valueOf(expected ? 1 : 0), String.valueOf(actual ? 1 : 0),
                Severity.MATCH, 0);
        }
        return new FieldComparison(name,
            String.valueOf(expected ? 1 : 0), String.valueOf(actual ? 1 : 0),
            Severity.ERROR, 1);
    }

    private FieldComparison compareEnum(String name, int expected, int actual) {
        if (expected == actual) {
            return new FieldComparison(name,
                String.valueOf(expected), String.valueOf(actual), Severity.MATCH, 0);
        }
        return new FieldComparison(name,
            String.valueOf(expected), String.valueOf(actual),
            Severity.ERROR, Math.abs(expected - actual));
    }

    private FieldComparison compareAngle(String name, int expected, int actual,
            int warn, int error) {
        // Circular distance on a 256-unit ring
        int rawDelta = Math.abs(expected - actual);
        int delta = Math.min(rawDelta, 256 - rawDelta);

        Severity severity = tolerances.classify(delta, warn, error);
        return new FieldComparison(name,
            formatHex(expected), formatHex(actual), severity, delta);
    }

    /**
     * Derive ground mode from angle using ROM quadrant thresholds.
     * Floor wraps: 0xE0-0xFF and 0x00-0x1F are both mode 0.
     */
    static int deriveGroundMode(int angle) {
        if (angle <= 0x1F || angle >= 0xE0) return 0;  // floor
        if (angle <= 0x5F) return 1;                     // right wall
        if (angle <= 0x9F) return 2;                     // ceiling
        return 3;                                         // left wall
    }

    private static String formatHex(int value) {
        if (value < 0) {
            return String.format("-%04X", -value);
        }
        return String.format("0x%04X", value & 0xFFFF);
    }
}
