package com.openggf.tests.trace;

import java.util.List;
import java.util.Map;

/**
 * Comparison result for a single frame: all fields compared.
 */
public record FrameComparison(
    int frame,
    Map<String, FieldComparison> fields
) {
    /** True if any field has a non-MATCH severity. */
    public boolean hasDivergence() {
        return fields.values().stream().anyMatch(FieldComparison::isDivergent);
    }

    /** True if any field is ERROR severity. */
    public boolean hasError() {
        return fields.values().stream()
            .anyMatch(fc -> fc.severity() == Severity.ERROR);
    }

    /** Get all divergent fields. */
    public List<FieldComparison> divergentFields() {
        return fields.values().stream()
            .filter(FieldComparison::isDivergent)
            .toList();
    }
}
