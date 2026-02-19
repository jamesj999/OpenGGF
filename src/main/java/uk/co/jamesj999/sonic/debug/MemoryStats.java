package uk.co.jamesj999.sonic.debug;

import com.sun.management.ThreadMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks JVM memory statistics for the debug overlay.
 * Provides heap usage, GC stats, allocation rate estimation,
 * and per-section allocation tracking.
 */
public class MemoryStats {

    private static final MemoryStats INSTANCE = new MemoryStats();

    private final MemoryMXBean memoryBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    private final ThreadMXBean threadBean;
    private final long mainThreadId;

    private long lastHeapUsed;
    private long lastAllocatedBytes;
    private long allocationWindowStartTime;
    private long allocationWindowStartBytes;

    private static final long ALLOCATION_WINDOW_NS = 3_000_000_000L; // 3 seconds
    private double allocationRateBytesPerSec;

    private static final int AVERAGING_FRAMES = 300; // ~5 seconds at 60fps
    private final Map<String, long[]> sectionAllocHistories = new LinkedHashMap<>();
    private final Map<String, Long> sectionAllocSums = new LinkedHashMap<>();
    private final Map<String, Long> currentFrameAllocations = new LinkedHashMap<>();
    private int frameCount = 0;

    private String activeSection = null;
    private long sectionStartAllocBytes = 0;

    private MemoryStats() {
        memoryBean = ManagementFactory.getMemoryMXBean();
        gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        threadBean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        mainThreadId = Thread.currentThread().getId();

        lastHeapUsed = getHeapUsed();
        lastAllocatedBytes = getThreadAllocatedBytes();
        allocationWindowStartTime = System.nanoTime();
        allocationWindowStartBytes = lastAllocatedBytes;
    }

    private long getThreadAllocatedBytes() {
        try {
            return threadBean.getThreadAllocatedBytes(mainThreadId);
        } catch (Exception e) {
            return 0;
        }
    }

    public static MemoryStats getInstance() {
        return INSTANCE;
    }

    /**
     * Update allocation rate tracking. Call once per frame.
     */
    public void update() {
        long now = System.nanoTime();
        long currentAllocatedBytes = getThreadAllocatedBytes();

        // Update heap for display
        lastHeapUsed = getHeapUsed();

        // Calculate rate over rolling window using cumulative thread allocations
        long windowElapsed = now - allocationWindowStartTime;
        if (windowElapsed >= ALLOCATION_WINDOW_NS) {
            long allocatedInWindow = currentAllocatedBytes - allocationWindowStartBytes;
            double elapsedSec = windowElapsed / 1_000_000_000.0;
            allocationRateBytesPerSec = allocatedInWindow / elapsedSec;

            // Slide window forward
            allocationWindowStartTime = now;
            allocationWindowStartBytes = currentAllocatedBytes;
        }

        lastAllocatedBytes = currentAllocatedBytes;

        // Update per-section rolling averages
        int historySlot = frameCount % AVERAGING_FRAMES;

        for (Map.Entry<String, Long> entry : currentFrameAllocations.entrySet()) {
            String name = entry.getKey();
            long bytes = entry.getValue();

            long[] history = sectionAllocHistories.computeIfAbsent(name, k -> new long[AVERAGING_FRAMES]);
            long oldValue = history[historySlot];
            sectionAllocSums.merge(name, bytes - oldValue, Long::sum);
            history[historySlot] = bytes;
        }

        // Zero out sections not recorded this frame
        for (Map.Entry<String, long[]> entry : sectionAllocHistories.entrySet()) {
            String name = entry.getKey();
            if (!currentFrameAllocations.containsKey(name)) {
                long[] history = entry.getValue();
                long oldValue = history[historySlot];
                sectionAllocSums.merge(name, -oldValue, Long::sum);
                history[historySlot] = 0;
            }
        }

        currentFrameAllocations.clear();
        frameCount++;
    }

    /**
     * Begin tracking allocations for a named section.
     * Call this at the start of a profiled section.
     */
    public void beginSection(String name) {
        if (activeSection != null) {
            endSection(activeSection);
        }
        activeSection = name;
        sectionStartAllocBytes = getThreadAllocatedBytes();
    }

    /**
     * End tracking for the named section.
     * Uses cumulative thread allocation bytes - immune to GC.
     */
    public void endSection(String name) {
        if (activeSection == null || !activeSection.equals(name)) {
            return;
        }
        long currentAllocBytes = getThreadAllocatedBytes();
        long delta = currentAllocBytes - sectionStartAllocBytes;
        if (delta > 0) {
            currentFrameAllocations.merge(name, delta, Long::sum);
        }
        activeSection = null;
    }

    // Reusable list for top allocators to avoid per-call allocation
    private final List<SectionAllocation> topAllocatorsCache = new ArrayList<>();

    /**
     * Get top allocating sections sorted by bytes allocated (descending).
     * The returned list is reused across calls — do not hold references.
     */
    public List<SectionAllocation> getTopAllocators(int limit) {
        topAllocatorsCache.clear();
        int effectiveFrames = Math.min(frameCount, AVERAGING_FRAMES);
        if (effectiveFrames == 0) {
            return topAllocatorsCache;
        }

        for (Map.Entry<String, Long> entry : sectionAllocSums.entrySet()) {
            long avgBytes = entry.getValue() / effectiveFrames;
            if (avgBytes > 0) {
                topAllocatorsCache.add(new SectionAllocation(entry.getKey(), avgBytes));
            }
        }

        topAllocatorsCache.sort(Comparator.comparingLong(SectionAllocation::bytesPerFrame).reversed());
        if (topAllocatorsCache.size() > limit) {
            // Trim in-place rather than creating a subList view
            topAllocatorsCache.subList(limit, topAllocatorsCache.size()).clear();
        }
        return topAllocatorsCache;
    }

    public record SectionAllocation(String name, long bytesPerFrame) {
        public double kbPerFrame() {
            return bytesPerFrame / 1024.0;
        }
    }

    public long getHeapUsed() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        return heap.getUsed();
    }

    public long getHeapMax() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        long max = heap.getMax();
        return max > 0 ? max : heap.getCommitted();
    }

    public double getHeapUsedMB() {
        return getHeapUsed() / (1024.0 * 1024.0);
    }

    public double getHeapMaxMB() {
        return getHeapMax() / (1024.0 * 1024.0);
    }

    public int getHeapPercentage() {
        long max = getHeapMax();
        return max > 0 ? (int) ((getHeapUsed() * 100) / max) : 0;
    }

    public long getTotalGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            long count = gc.getCollectionCount();
            if (count >= 0) {
                total += count;
            }
        }
        return total;
    }

    public long getTotalGcTimeMs() {
        long total = 0;
        for (GarbageCollectorMXBean gc : gcBeans) {
            long time = gc.getCollectionTime();
            if (time >= 0) {
                total += time;
            }
        }
        return total;
    }

    public double getAllocationRateMBPerSec() {
        return allocationRateBytesPerSec / (1024.0 * 1024.0);
    }

    // Reusable snapshot to avoid per-frame record allocation
    private final Snapshot reusableSnapshot = new Snapshot();

    /**
     * Returns a reusable snapshot of current memory stats for display.
     * The returned object is reused — do not hold references across frames.
     */
    public Snapshot snapshot() {
        reusableSnapshot.heapUsedMB = getHeapUsedMB();
        reusableSnapshot.heapMaxMB = getHeapMaxMB();
        reusableSnapshot.heapPercentage = getHeapPercentage();
        reusableSnapshot.gcCount = getTotalGcCount();
        reusableSnapshot.gcTimeMs = getTotalGcTimeMs();
        reusableSnapshot.allocationRateMBPerSec = getAllocationRateMBPerSec();
        reusableSnapshot.topAllocators = getTopAllocators(5);
        return reusableSnapshot;
    }

    public static class Snapshot {
        double heapUsedMB;
        double heapMaxMB;
        int heapPercentage;
        long gcCount;
        long gcTimeMs;
        double allocationRateMBPerSec;
        List<SectionAllocation> topAllocators = List.of();

        public double heapUsedMB() { return heapUsedMB; }
        public double heapMaxMB() { return heapMaxMB; }
        public int heapPercentage() { return heapPercentage; }
        public long gcCount() { return gcCount; }
        public long gcTimeMs() { return gcTimeMs; }
        public double allocationRateMBPerSec() { return allocationRateMBPerSec; }
        public List<SectionAllocation> topAllocators() { return topAllocators; }
    }
}
