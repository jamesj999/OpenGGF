package com.openggf.tests.trace;

/**
 * Per-field tolerance thresholds for trace comparison.
 * Warn and error thresholds define the boundaries between MATCH, WARNING, and ERROR.
 * Boolean/enum fields (air, rolling, ground_mode) always ERROR on any mismatch.
 */
public record ToleranceConfig(
    int positionWarn,
    int positionError,
    int speedWarn,
    int speedError,
    boolean speedSignChangeIsError,
    int angleWarn,
    int angleError
) {
    /**
     * Default tolerances:
     * - Position: warn at 1 subpixel, error at 256 (1 full pixel)
     * - Speed: warn at 1 subpixel, error at 128 (half pixel/frame), sign change = error
     * - Angle: warn at 1, error at 4
     * - Flags: any mismatch = error (hardcoded, not configurable)
     */
    public static final ToleranceConfig DEFAULT = new ToleranceConfig(
        1, 256,       // position
        1, 128, true, // speed
        1, 4          // angle
    );

    /** Classify a numeric difference against warn/error thresholds. */
    public Severity classify(int absDelta, int warn, int error) {
        if (absDelta == 0) return Severity.MATCH;
        if (absDelta >= error) return Severity.ERROR;
        if (absDelta >= warn) return Severity.WARNING;
        return Severity.MATCH;
    }
}
