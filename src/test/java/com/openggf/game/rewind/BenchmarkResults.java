package com.openggf.game.rewind;

import java.util.Map;

/**
 * Per-phase benchmark measurement results. JSON-serialisable record.
 */
public record BenchmarkResults(
        String phaseName,
        PhaseStats overall,
        long totalWallTimeNs,
        Map<String, PhaseStats> perSubsystem
) {
    public BenchmarkResults {
        perSubsystem = perSubsystem == null ? Map.of() : Map.copyOf(perSubsystem);
    }

    public record PhaseStats(
            long sampleCount,
            long meanNs,
            long p50Ns,
            long p95Ns,
            long p99Ns,
            long maxNs
    ) {}
}
