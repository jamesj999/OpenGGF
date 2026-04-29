package com.openggf.trace;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

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

    // Package-private so same-package test fixtures in src/test can
    // construct in-memory instances without going through disk I/O.
    TraceData(TraceMetadata metadata, List<TraceFrame> frames,
              Map<Integer, List<TraceEvent>> eventsByFrame) {
        this.metadata = metadata;
        this.frames = frames;
        this.eventsByFrame = eventsByFrame;
    }

    public static TraceData load(Path traceDirectory) throws IOException {
        Path metadataPath = traceDirectory.resolve("metadata.json");
        Path physicsPath = resolveTraceFile(traceDirectory, "physics.csv");
        Path auxPath = resolveTraceFile(traceDirectory, "aux_state.jsonl");

        TraceMetadata metadata = TraceMetadata.load(metadataPath);
        if (physicsPath == null) {
            throw new NoSuchFileException(traceDirectory.resolve("physics.csv").toString());
        }
        List<TraceFrame> frames = loadPhysicsCsv(physicsPath, metadata);
        Map<Integer, List<TraceEvent>> events = auxPath != null
            ? loadAuxEvents(auxPath)
            : Collections.emptyMap();

        warnIfLegacyExecutionCounters(traceDirectory, metadata, frames);

        return new TraceData(metadata, frames, events);
    }

    public TraceMetadata metadata() { return metadata; }
    public int frameCount() { return frames.size(); }

    /**
     * Returns the playable character names recorded in this trace, in order.
     * The current replay pipeline supports the primary character plus at most one sidekick.
     */
    public List<String> recordedCharacters() {
        return metadata.recordedCharacters();
    }

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

    /**
     * Returns the recorded state for a named playable character on the given frame.
     * The current replay pipeline exposes the primary character plus the first sidekick.
     */
    public TraceCharacterState characterState(int traceFrame, String characterCode) {
        if (characterCode == null || characterCode.isBlank()) {
            return null;
        }

        TraceFrame frame = getFrame(traceFrame);
        List<String> recordedCharacters = metadata.recordedCharacters();
        if (!recordedCharacters.isEmpty()
                && recordedCharacters.getFirst().equalsIgnoreCase(characterCode)) {
            return frame.primaryCharacterState();
        }
        if (recordedCharacters.size() > 1
                && recordedCharacters.get(1).equalsIgnoreCase(characterCode)) {
            return frame.sidekick();
        }
        return null;
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
     * Returns advertised aux schemas that have no matching events in the
     * loaded aux stream.
     *
     * <p><strong>Diagnostic only.</strong> This guards against stale regenerated
     * fixtures where {@code metadata.json} claims per-frame diagnostics exist
     * but {@code aux_state.jsonl(.gz)} does not actually contain the records.
     * The result is used only for reports/tests and never feeds replay state.
     */
    public List<String> missingAdvertisedAuxSchemas() {
        List<String> missing = new ArrayList<>();
        if (metadata.hasPerFrameCageState()
                && !hasEventOfType(TraceEvent.CageState.class)) {
            missing.add("cage_state_per_frame");
        }
        if (metadata.hasPerFrameCageExecution()
                && !hasEventOfType(TraceEvent.CageExecution.class)) {
            missing.add("cage_execution_per_frame");
        }
        if (metadata.hasPerFrameVelocityWrite()
                && !hasEventOfType(TraceEvent.VelocityWrite.class)) {
            missing.add("velocity_write_per_frame");
        }
        return missing;
    }

    public List<TraceEvent.CageState> cageStatesForFrame(int frame) {
        List<TraceEvent.CageState> states = new ArrayList<>();
        for (TraceEvent event : eventsByFrame.getOrDefault(frame, Collections.emptyList())) {
            if (event instanceof TraceEvent.CageState state) {
                states.add(state);
            }
        }
        return states;
    }

    public TraceEvent.CageExecution cageExecutionForFrame(int frame) {
        for (TraceEvent event : eventsByFrame.getOrDefault(frame, Collections.emptyList())) {
            if (event instanceof TraceEvent.CageExecution execution) {
                return execution;
            }
        }
        return null;
    }

    public TraceEvent.Checkpoint latestCheckpointAtOrBefore(int frame) {
        List<Integer> frames = new ArrayList<>(eventsByFrame.keySet());
        frames.sort(Comparator.reverseOrder());
        for (int candidateFrame : frames) {
            if (candidateFrame > frame) {
                continue;
            }
            for (TraceEvent event : eventsByFrame.getOrDefault(candidateFrame, Collections.emptyList())) {
                if (event instanceof TraceEvent.Checkpoint checkpoint) {
                    return checkpoint;
                }
            }
        }
        return null;
    }

    public TraceEvent.ZoneActState latestZoneActStateAtOrBefore(int frame) {
        List<Integer> frames = new ArrayList<>(eventsByFrame.keySet());
        frames.sort(Comparator.reverseOrder());
        for (int candidateFrame : frames) {
            if (candidateFrame > frame) {
                continue;
            }
            for (TraceEvent event : eventsByFrame.getOrDefault(candidateFrame, Collections.emptyList())) {
                if (event instanceof TraceEvent.ZoneActState state) {
                    return state;
                }
            }
        }
        return null;
    }

    private boolean hasEventOfType(Class<? extends TraceEvent> eventType) {
        for (List<TraceEvent> events : eventsByFrame.values()) {
            for (TraceEvent event : events) {
                if (eventType.isInstance(event)) {
                    return true;
                }
            }
        }
        return false;
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

    public TraceEvent.PlayerHistorySnapshot preTracePlayerHistorySnapshot() {
        List<TraceEvent> events = eventsByFrame.getOrDefault(-1, Collections.emptyList());
        for (TraceEvent event : events) {
            if (event instanceof TraceEvent.PlayerHistorySnapshot snapshot) {
                return snapshot;
            }
        }
        return null;
    }

    public TraceEvent.CpuStateSnapshot preTraceCpuStateSnapshot(String characterCode) {
        if (characterCode == null || characterCode.isBlank()) {
            return null;
        }
        List<TraceEvent> events = eventsByFrame.getOrDefault(-1, Collections.emptyList());
        for (TraceEvent event : events) {
            if (event instanceof TraceEvent.CpuStateSnapshot snapshot
                    && characterCode.equalsIgnoreCase(snapshot.character())) {
                return snapshot;
            }
        }
        return null;
    }

    /**
     * Returns the per-frame {@link TraceEvent.CpuState} event for the requested
     * trace frame and character, or {@code null} when the trace was recorded
     * without v6+ per-frame CPU snapshots or when no event is present for that
     * frame/character.
     *
     * <p>Used by the trace replay test to hydrate {@link
     * com.openggf.sprites.playable.SidekickCpuController} state from
     * authoritative ROM values each frame, eliminating CPU-state drift as a
     * divergence source while leaving physics divergences fully visible.
     */
    public TraceEvent.CpuState cpuStateForFrame(int frame, String characterCode) {
        if (characterCode == null || characterCode.isBlank()) {
            return null;
        }
        List<TraceEvent> events = eventsByFrame.getOrDefault(frame, Collections.emptyList());
        for (TraceEvent event : events) {
            if (event instanceof TraceEvent.CpuState state
                    && characterCode.equalsIgnoreCase(state.character())) {
                return state;
            }
        }
        return null;
    }

    /**
     * Returns the per-frame {@link TraceEvent.OscillationState} event for the
     * requested trace frame, or {@code null} when the trace was recorded
     * without v6.1+ per-frame oscillation snapshots or when no event is
     * present for that frame.
     *
     * <p><strong>Diagnostic only.</strong> Used by trace replay tests to
     * compare engine {@code OscillationManager} state against authoritative
     * ROM values per frame. The engine must NOT hydrate its oscillator from
     * these values; it must produce the correct phase natively.
     */
    public TraceEvent.OscillationState oscillationStateForFrame(int frame) {
        List<TraceEvent> events = eventsByFrame.getOrDefault(frame, Collections.emptyList());
        for (TraceEvent event : events) {
            if (event instanceof TraceEvent.OscillationState state) {
                return state;
            }
        }
        return null;
    }

    /**
     * Returns the per-frame {@link TraceEvent.VelocityWrite} event for the
     * requested trace frame and character, or {@code null} when the trace was
     * recorded without v6.4+ per-frame velocity-write snapshots or when no
     * event is present for that frame/character.
     *
     * <p><strong>Diagnostic only.</strong> Captures every M68K write to the
     * sidekick's {@code x_vel}/{@code y_vel} during ROM frame processing,
     * with each writing-instruction PC. Used to root-cause CNZ1 trace F3649
     * where ROM Tails {@code x_speed} jumps from -$48 to -$0A00 in a single
     * frame: the PC list pinpoints which ROM routine writes the value.
     */
    public TraceEvent.VelocityWrite velocityWriteForFrame(int frame, String characterCode) {
        if (characterCode == null || characterCode.isBlank()) {
            return null;
        }
        List<TraceEvent> events = eventsByFrame.getOrDefault(frame, Collections.emptyList());
        for (TraceEvent event : events) {
            if (event instanceof TraceEvent.VelocityWrite vw
                    && characterCode.equalsIgnoreCase(vw.character())) {
                return vw;
            }
        }
        return null;
    }

    private static List<TraceFrame> loadPhysicsCsv(Path csvPath, TraceMetadata metadata)
            throws IOException {
        List<TraceFrame> frames = new ArrayList<>();
        try (BufferedReader reader = openTraceReader(csvPath)) {
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
        try (BufferedReader reader = openTraceReader(auxPath)) {
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

    private static Path resolveTraceFile(Path traceDirectory, String fileName) {
        Path plainPath = traceDirectory.resolve(fileName);
        if (Files.exists(plainPath)) {
            return plainPath;
        }
        Path gzipPath = traceDirectory.resolve(fileName + ".gz");
        return Files.exists(gzipPath) ? gzipPath : null;
    }

    private static BufferedReader openTraceReader(Path path) throws IOException {
        if (path.getFileName().toString().endsWith(".gz")) {
            InputStream input = Files.newInputStream(path);
            try {
                return new BufferedReader(new InputStreamReader(
                        new GZIPInputStream(input), StandardCharsets.UTF_8));
            } catch (IOException e) {
                input.close();
                throw e;
            }
        }
        return Files.newBufferedReader(path);
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
