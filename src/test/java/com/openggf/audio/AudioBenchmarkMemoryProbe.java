package com.openggf.audio;

import com.sun.management.ThreadMXBean;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

public final class AudioBenchmarkMemoryProbe {

    private final MemoryMXBean memoryBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    private final ThreadMXBean threadBean;
    private final long threadId;
    private final boolean allocatedBytesSupported;

    private AudioBenchmarkMemoryProbe() {
        memoryBean = ManagementFactory.getMemoryMXBean();
        gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

        java.lang.management.ThreadMXBean rawThreadBean = ManagementFactory.getThreadMXBean();
        ThreadMXBean sunThreadBean = rawThreadBean instanceof ThreadMXBean ? (ThreadMXBean) rawThreadBean : null;
        if (sunThreadBean != null && sunThreadBean.isThreadAllocatedMemorySupported()) {
            try {
                if (!sunThreadBean.isThreadAllocatedMemoryEnabled()) {
                    sunThreadBean.setThreadAllocatedMemoryEnabled(true);
                }
            } catch (SecurityException | UnsupportedOperationException ignored) {
                // Allocation metrics remain available only if the JVM permits them.
            }
        }

        threadBean = sunThreadBean;
        threadId = Thread.currentThread().getId();
        allocatedBytesSupported = threadBean != null
                && threadBean.isThreadAllocatedMemorySupported()
                && threadBean.isThreadAllocatedMemoryEnabled();
    }

    public static AudioBenchmarkMemoryProbe create() {
        return new AudioBenchmarkMemoryProbe();
    }

    public RunResult measureTimedRun(Runnable workload) {
        Snapshot before = snapshot();
        long start = System.nanoTime();
        workload.run();
        long elapsed = System.nanoTime() - start;
        Snapshot after = snapshot();

        long allocatedBytes = -1;
        if (before.allocatedBytesSupported() && after.allocatedBytesSupported()) {
            allocatedBytes = Math.max(0L, after.allocatedBytes() - before.allocatedBytes());
        }

        return new RunResult(
                elapsed,
                allocatedBytes,
                before.allocatedBytesSupported() && after.allocatedBytesSupported(),
                before.heapUsedBytes(),
                after.heapUsedBytes(),
                after.heapUsedBytes() - before.heapUsedBytes(),
                Math.max(0L, after.gcCount() - before.gcCount()),
                Math.max(0L, after.gcTimeMs() - before.gcTimeMs())
        );
    }

    public long measurePeakHeapBytes(Runnable replayStep, int iterations) {
        long peakHeapBytes = snapshot().heapUsedBytes();
        for (int i = 0; i < iterations; i++) {
            replayStep.run();
            peakHeapBytes = Math.max(peakHeapBytes, snapshot().heapUsedBytes());
        }
        return Math.max(0L, peakHeapBytes);
    }

    public Snapshot snapshot() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long heapUsedBytes = heapUsage != null ? heapUsage.getUsed() : 0L;
        long gcCount = 0L;
        long gcTimeMs = 0L;
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            if (count >= 0) {
                gcCount += count;
            }
            if (time >= 0) {
                gcTimeMs += time;
            }
        }

        long allocatedBytes = -1L;
        if (allocatedBytesSupported) {
            allocatedBytes = Math.max(0L, threadBean.getThreadAllocatedBytes(threadId));
        }

        return new Snapshot(heapUsedBytes, gcCount, gcTimeMs, allocatedBytes, allocatedBytesSupported);
    }

    public record Snapshot(
            long heapUsedBytes,
            long gcCount,
            long gcTimeMs,
            long allocatedBytes,
            boolean allocatedBytesSupported
    ) {
    }

    public record RunResult(
            long elapsedNanos,
            long allocatedBytes,
            boolean allocatedBytesSupported,
            long heapUsedBeforeBytes,
            long heapUsedAfterBytes,
            long heapUsedDeltaBytes,
            long gcCountDelta,
            long gcTimeDeltaMs
    ) {
    }
}
