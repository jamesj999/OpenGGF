package com.openggf.tests.trace;

/**
 * Comparison result for a single field on a single frame.
 */
public record FieldComparison(
    String fieldName,
    String expected,
    String actual,
    Severity severity,
    int delta
) {
    public boolean isDivergent() {
        return severity != Severity.MATCH;
    }
}


