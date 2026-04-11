package com.openggf.tests.trace;

/**
 * A run of consecutive frames where the same field diverged at the same severity.
 */
public record DivergenceGroup(
    String field,
    Severity severity,
    int startFrame,
    int endFrame,
    String expectedAtStart,
    String actualAtStart,
    boolean cascading
) {
    public int frameSpan() {
        return endFrame - startFrame + 1;
    }
}


