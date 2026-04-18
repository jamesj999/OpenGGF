package com.openggf.tests;

import com.openggf.debug.PerformanceProfiler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPerformanceProfilerGating {

    @AfterEach
    void tearDown() {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        profiler.reset();
        profiler.setEnabled(false);
    }

    @Test
    void disabledProfilerAcceptsBeginEndCallsWithoutRecordingSections() {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        profiler.reset();
        profiler.setEnabled(false);

        assertDoesNotThrow(() -> {
            profiler.beginFrame();
            profiler.beginSection("render");
            profiler.endSection("render");
            profiler.endFrame();
        });

        assertFalse(profiler.getSnapshot().hasData());
        assertTrue(profiler.memoryStats().snapshot().topAllocators().isEmpty());
    }

    @Test
    void enabledProfilerRecordsSectionTiming() {
        PerformanceProfiler profiler = PerformanceProfiler.getInstance();
        profiler.reset();
        profiler.setEnabled(true);

        profiler.beginFrame();
        profiler.beginSection("render");
        profiler.endSection("render");
        profiler.endFrame();

        assertTrue(profiler.getSnapshot().sections().containsKey("render"));
    }
}
