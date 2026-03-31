package com.openggf.tests.trace;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Reads and holds the contents of a trace directory:
 * metadata.json, physics.csv, and aux_state.jsonl.
 *
 * The primary CSV is loaded entirely into memory (small: ~100 bytes/frame).
 * Auxiliary events are lazy-loaded and indexed by frame number.
 */
public class TraceData {

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
        List<TraceFrame> frames = loadPhysicsCsv(physicsPath);
        Map<Integer, List<TraceEvent>> events = Files.exists(auxPath)
            ? loadAuxEvents(auxPath)
            : Collections.emptyMap();

        return new TraceData(metadata, frames, events);
    }

    public TraceMetadata metadata() { return metadata; }
    public int frameCount() { return frames.size(); }

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

    /**
     * Returns true if the given trace frame index represents a lag frame —
     * a frame where the ROM did not process physics (the VBlank handler fired
     * but the main game loop hadn't completed its tick yet).
     *
     * <p>Detection requires TWO conditions:
     * <ol>
     *   <li>All physics state fields are identical to the previous frame</li>
     *   <li>Sonic has non-zero speed or is airborne — meaning physics SHOULD
     *       have changed state if the game loop had run</li>
     * </ol>
     *
     * <p>Without condition 2, frames where Sonic is standing still with zero
     * speed are falsely classified as lag frames. The game loop DID run on
     * those frames (advancing oscillation, objects, etc.) but Sonic's state
     * was unchanged because he wasn't moving. Skipping those frames causes
     * oscillation desync and cascading divergences.
     *
     * @param traceFrame 0-based trace frame index
     * @return true if this frame is a lag frame that should skip engine physics
     */
    public boolean isLagFrame(int traceFrame) {
        if (traceFrame <= 0 || traceFrame >= frames.size()) return false;
        TraceFrame current = frames.get(traceFrame);
        TraceFrame previous = frames.get(traceFrame - 1);
        if (!current.stateEquals(previous)) return false;
        // If Sonic has any speed or is airborne, physics should have changed
        // the state. Identical state with non-zero speed = real lag frame.
        // Zero speed on ground = Sonic standing still, not a lag frame.
        return current.xSpeed() != 0 || current.ySpeed() != 0
            || current.gSpeed() != 0 || current.air();
    }

    public List<TraceEvent> getEventsInRange(int startFrame, int endFrame) {
        List<TraceEvent> result = new ArrayList<>();
        for (int f = startFrame; f <= endFrame; f++) {
            result.addAll(getEventsForFrame(f));
        }
        return result;
    }

    private static List<TraceFrame> loadPhysicsCsv(Path csvPath) throws IOException {
        List<TraceFrame> frames = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line = reader.readLine(); // skip header
            if (line == null) return frames;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    frames.add(TraceFrame.parseCsvRow(trimmed));
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
}
