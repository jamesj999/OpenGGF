package com.openggf.trace;

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
    int angleError,
    boolean compareRingCount
) {
    public ToleranceConfig(int positionWarn,
                           int positionError,
                           int speedWarn,
                           int speedError,
                           boolean speedSignChangeIsError,
                           int angleWarn,
                           int angleError) {
        this(positionWarn, positionError, speedWarn, speedError,
                speedSignChangeIsError, angleWarn, angleError, false);
    }

    /**
     * Default replay policy is exact: any numeric mismatch is an error.
     * Non-zero tolerance configs must be opt-in and justified by the caller.
     * - Flags: any mismatch = error (hardcoded, not configurable)
     * - Ring counts: opt-in because enabling this exposes existing trace parity
     *   gaps that were previously diagnostic-only.
     */
    public static final ToleranceConfig DEFAULT = new ToleranceConfig(
        1, 1,         // position
        1, 1, true,   // speed
        1, 1,         // angle
        false          // ring count
    );

    /** Classify a numeric difference against warn/error thresholds. */
    public Severity classify(int absDelta, int warn, int error) {
        if (absDelta == 0) return Severity.MATCH;
        if (absDelta >= error) return Severity.ERROR;
        if (absDelta >= warn) return Severity.WARNING;
        return Severity.MATCH;
    }
}


