package com.openggf.debug;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mutable snapshot of profiling data, designed for reuse across frames.
 * The profiler populates this in-place via {@link #populate} each frame,
 * and the renderer reads from it synchronously on the same thread.
 */
public class ProfileSnapshot {

    private static final Comparator<SectionStats> BY_TIME_DESC =
            (a, b) -> Double.compare(b.timeMs(), a.timeMs());

    private final Map<String, SectionStats> sections = new LinkedHashMap<>();
    private double totalFrameTimeMs;
    private double fps;
    private float[] frameHistory;
    private int historyIndex;
    private int frameCount;

    // Cached sorted list — invalidated on populate()
    private final List<SectionStats> sortedByTimeCache = new ArrayList<>();
    private boolean sortedCacheDirty = true;

    public ProfileSnapshot() {
        this.frameHistory = new float[120];
    }

    /**
     * Updates this snapshot in-place with new profiling data.
     * Avoids all map/array allocation after the first frame that establishes the section set.
     */
    public void populate(Map<String, Long> rollingSums, int effectiveFrames,
                         float[] sourceHistory, int historyIndex, int frameCount,
                         long actualFrameTimeSum) {
        sections.clear();

        long totalSectionNanos = 0;
        for (Map.Entry<String, Long> entry : rollingSums.entrySet()) {
            String name = entry.getKey();
            long sumNanos = entry.getValue();
            double avgNanos = (double) sumNanos / effectiveFrames;
            double avgMs = avgNanos / 1_000_000.0;
            sections.put(name, new SectionStats(name, avgMs, 0));
            totalSectionNanos += sumNanos;
        }

        double totalMs = (double) totalSectionNanos / effectiveFrames / 1_000_000.0;
        if (totalMs > 0) {
            for (Map.Entry<String, SectionStats> entry : sections.entrySet()) {
                SectionStats stats = entry.getValue();
                double pct = (stats.timeMs() / totalMs) * 100.0;
                entry.setValue(new SectionStats(stats.name(), stats.timeMs(), pct));
            }
        }

        this.totalFrameTimeMs = totalMs;

        // Reuse or resize the history array
        if (this.frameHistory.length != sourceHistory.length) {
            this.frameHistory = new float[sourceHistory.length];
        }
        System.arraycopy(sourceHistory, 0, this.frameHistory, 0, sourceHistory.length);
        this.historyIndex = historyIndex;
        this.frameCount = frameCount;

        // FPS from actual frame-to-frame time
        if (effectiveFrames > 0 && actualFrameTimeSum > 0) {
            double avgActualFrameNanos = (double) actualFrameTimeSum / effectiveFrames;
            this.fps = 1_000_000_000.0 / avgActualFrameNanos;
        } else {
            this.fps = 0;
        }

        sortedCacheDirty = true;
    }

    // Direct accessors — no defensive copies needed since this is consumed
    // synchronously by the renderer on the same thread.

    public Map<String, SectionStats> sections() {
        return sections;
    }

    public double totalFrameTimeMs() {
        return totalFrameTimeMs;
    }

    public double fps() {
        return fps;
    }

    public float[] frameHistory() {
        return frameHistory;
    }

    public int historyIndex() {
        return historyIndex;
    }

    public int frameCount() {
        return frameCount;
    }

    /**
     * Returns sections sorted by time descending. The returned list is cached
     * and reused across calls within the same frame.
     */
    public List<SectionStats> getSectionsSortedByTime() {
        if (sortedCacheDirty) {
            sortedByTimeCache.clear();
            sortedByTimeCache.addAll(sections.values());
            sortedByTimeCache.sort(BY_TIME_DESC);
            sortedCacheDirty = false;
        }
        return sortedByTimeCache;
    }

    public boolean hasData() {
        return frameCount > 0 && !sections.isEmpty();
    }
}
