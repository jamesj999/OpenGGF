package com.openggf.tests.trace;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Reads and holds the contents of a trace directory:
 * metadata.json, physics.csv, and aux_state.jsonl.
 *
 * The primary CSV is loaded entirely into memory (small: ~100 bytes/frame).
 * Auxiliary events are lazy-loaded and indexed by frame number.
 */
public class TraceData {

    private static final Logger LOGGER = Logger.getLogger(TraceData.class.getName());
    private static final Set<Path> LEGACY_TRACE_WARNINGS = ConcurrentHashMap.newKeySet();

    private final TraceMetadata metadata;
    private final List<TraceFrame> frames;
    private final Map<Integer, List<TraceEvent>> eventsByFrame;

    private TraceData(TraceMetadata metadata, List<TraceFrame> frames,
                      Map<Integer, List<TraceEvent>> eventsByFrame) {
        this.metadata = metadata;
        this.frames = frames;
        this.eventsByFrame = eventsByFrame;
    }

    public static TraceData load(Path traceDirectory) throws IOException {
        Path metadataPath = traceDirectory.resolve("metadata.json");
        Path physicsPath = traceDirectory.resolve("physics.csv");
        Path auxPath = traceDirectory.resolve("aux_state.jsonl");

        TraceMetadata metadata = TraceMetadata.load(metadataPath);
        List<TraceFrame> frames = loadPhysicsCsv(physicsPath, metadata);
        Map<Integer, List<TraceEvent>> events = Files.exists(auxPath)
            ? loadAuxEvents(auxPath)
            : Collections.emptyMap();

        warnIfLegacyExecutionCounters(traceDirectory, metadata, frames);

        return new TraceData(metadata, frames, events);
    }

    public TraceMetadata metadata() { return metadata; }
    public int frameCount() { return frames.size(); }

    /**
     * Returns the ROM VBlank counter value that corresponds to trace frame 0.
     *
     * <p>Schema v3 traces record the real ROM VBlank counter per frame. Older
     * traces do not, so fall back to the historical BK2 frame offset metadata.
     * That fallback preserves legacy replay behaviour until all fixtures carry
     * explicit execution counters.
     */
    public int initialVblankCounter() {
        if (!frames.isEmpty()) {
            int recorded = frames.get(0).vblankCounter();
            if (recorded >= 0) {
                return recorded;
            }
        }
        return metadata.bk2FrameOffset();
    }

    public TraceFrame getFrame(int traceFrame) {
        if (traceFrame < 0 || traceFrame >= frames.size()) {
            throw new IndexOutOfBoundsException(
                "Frame " + traceFrame + " out of range [0, " + frames.size() + ")");
        }
        return frames.get(traceFrame);
    }

    public List<TraceEvent> getEventsForFrame(int traceFrame) {
        return eventsByFrame.getOrDefault(traceFrame, Collections.emptyList());
    }

    public List<TraceEvent> getEventsInRange(int startFrame, int endFrame) {
        List<TraceEvent> result = new ArrayList<>();
        for (int f = startFrame; f <= endFrame; f++) {
            result.addAll(getEventsForFrame(f));
        }
        return result;
    }

    /**
     * Returns the pre-trace ROM object snapshots emitted by the Lua recorder
     * at the moment gameplay begins but before trace frame 0 is written.
     *
     * <p>Schema v4+ aux files include one {@code object_state_snapshot} event
     * per occupied SST slot, stored at frame {@code -1}. Older schemas return
     * an empty list.
     */
    public List<TraceEvent.ObjectStateSnapshot> preTraceObjectSnapshots() {
        List<TraceEvent> events = eventsByFrame.getOrDefault(-1, Collections.emptyList());
        List<TraceEvent.ObjectStateSnapshot> snapshots = new ArrayList<>();
        for (TraceEvent event : events) {
            if (event instanceof TraceEvent.ObjectStateSnapshot snapshot) {
                snapshots.add(snapshot);
            }
        }
        return snapshots;
    }

    private static List<TraceFrame> loadPhysicsCsv(Path csvPath, TraceMetadata metadata)
            throws IOException {
        List<TraceFrame> frames = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line = reader.readLine(); // skip header
            if (line == null) return frames;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    frames.add(TraceFrame.parseCsvRow(trimmed, metadata.traceSchema()));
                }
            }
        }
        return frames;
    }

    private static Map<Integer, List<TraceEvent>> loadAuxEvents(Path auxPath)
            throws IOException {
        Map<Integer, List<TraceEvent>> map = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader reader = Files.newBufferedReader(auxPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    TraceEvent event = TraceEvent.parseJsonLine(trimmed, mapper);
                    map.computeIfAbsent(event.frame(), k -> new ArrayList<>()).add(event);
                }
            }
        }
        return map;
    }

    private static void warnIfLegacyExecutionCounters(Path traceDirectory,
            TraceMetadata metadata, List<TraceFrame> frames) {
        Integer traceSchema = metadata.traceSchema();
        if (traceSchema != null && traceSchema >= 3) {
            return;
        }
        if (!frames.isEmpty() && frames.get(0).vblankCounter() >= 0) {
            return;
        }
        Path normalized = traceDirectory.toAbsolutePath().normalize();
        if (LEGACY_TRACE_WARNINGS.add(normalized)) {
            LOGGER.info(() -> "Trace " + normalized
                    + " is pre-v3; replay is using the legacy lag heuristic.");
        }
    }
}
