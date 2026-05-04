package com.openggf.tests.trace;

import com.openggf.trace.ToleranceConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class TestTraceReplayInvariantGuard {

    @Test
    void traceReplayCodeDoesNotWriteRecordedStateBackIntoEngine()
            throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : replaySources()) {
            List<String> lines = Files.readAllLines(source);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (isForbiddenTraceHydration(line)) {
                    violations.add(source + ":" + (i + 1) + " - " + line.trim());
                }
            }
        }

        if (!violations.isEmpty()) {
            fail("Trace replay must compare trace rows, not write them "
                    + "back into engine state:\n"
                    + String.join("\n", violations));
        }
    }

    @Test
    void defaultTraceReplayToleranceIsStrict() {
        ToleranceConfig defaults = ToleranceConfig.DEFAULT;

        assertEquals(1, defaults.positionError(), "position delta of one must be an error");
        assertEquals(1, defaults.speedError(), "speed delta of one must be an error");
        assertEquals(1, defaults.angleError(), "angle delta of one must be an error");
        assertEquals(1, defaults.cameraError(), "camera delta of one must be an error");
        assertEquals(ToleranceConfig.RingCountMode.FORCE_ERROR, defaults.ringCountMode(),
                "ring count mismatch must default to error; opt into WARN_ONLY explicitly");
    }

    private static List<Path> replaySources() throws IOException {
        List<Path> roots = List.of(
                Path.of("src/main/java/com/openggf/trace"),
                Path.of("src/test/java/com/openggf/tests/trace"));
        List<Path> sources = new ArrayList<>();
        for (Path root : roots) {
            if (!Files.exists(root)) {
                continue;
            }
            try (var stream = Files.walk(root)) {
                stream.filter(path -> path.toString().endsWith(".java"))
                        .filter(path -> !path.endsWith("TestTraceReplayInvariantGuard.java"))
                        .forEach(sources::add);
            }
        }
        sources.add(Path.of("src/main/java/com/openggf/sprites/playable/SidekickCpuController.java"));
        return sources;
    }

    /**
     * Catches setter calls whose argument expression reads from a trace
     * snapshot/frame field. The pattern below matches a leading dot then
     * a setter, then anywhere inside the parenthesised argument list one of
     * the well-known trace-side identifiers ({@code state}, {@code frame},
     * {@code snapshot}, {@code sn}, {@code fields.get}). This is a conservative
     * heuristic but it would have caught
     * {@code applyFrameZeroPlayerSnapshot}'s
     * {@code player.setControlLocked(controlLocked)} chain because each value
     * is parsed via {@code parseBoolean(fields.get(...))} earlier in the same
     * method, and the fields-derived locals are detected via the dedicated
     * {@code fields.get(} check below.
     */
    private static final Pattern SETTER_FROM_TRACE_FIELD = Pattern.compile(
            "\\.set[A-Z]\\w*\\([^)]*\\b(state|frame|snapshot|sn)\\.\\w+");

    private static boolean isForbiddenTraceHydration(String line) {
        return line.contains("applyRecordedFirstSidekickState(")
                || line.contains("applyRecordedFrameState(")
                || line.contains("applySeededFirstSidekickState(")
                || line.contains("applySidekickFollowDelayOverride(")
                || line.contains("applyFrameZeroPlayerSnapshot(")
                || line.contains("applyCustomRadii(")
                || line.contains("hydrateFromRomCpuStatePerFrame(")
                || line.contains("hydrateRecordedHistory(")
                || line.contains("sidekickFollowDelayOverrideForTraceReplay(")
                || line.contains("setTraceReplayFollowDelayFrames(")
                || line.contains("traceReplayFollowDelayFrames")
                || line.contains("S3kElasticWindowController")
                || line.contains(".advanceRecordingCursor(")
                || line.contains(".isStrictComparisonEnabled()")
                || line.contains(".strictTraceIndex()")
                || line.contains(".driveTraceIndex()")
                || line.contains(".hydrateFromRomSnapshot(")
                // Direct setter-from-snapshot patterns. Kept as fast-path
                // string checks for readability; the regex below catches
                // less obvious variants.
                || line.contains("setCentreX(state.")
                || line.contains("setCentreY(state.")
                || line.contains("setXSpeed(state.")
                || line.contains("setYSpeed(state.")
                || line.contains("setGSpeed(state.")
                || line.contains("setCentreX(frame.")
                || line.contains("setCentreY(frame.")
                || line.contains("setXSpeed(frame.")
                || line.contains("setYSpeed(frame.")
                || line.contains("setGSpeed(frame.")
                || line.contains("setCentreX((short) snapshot.xPos())")
                || line.contains("setCentreY((short) snapshot.yPos())")
                || line.contains("setXSpeed((short) snapshot.xVel())")
                || line.contains("setYSpeed((short) snapshot.yVel())")
                // Snapshot-field reads used to derive a setter argument
                // (e.g. parseBoolean(fields.get("control_locked"), false)).
                // A {@code fields.get(} call only ever appears in trace-replay
                // code when binding a {@link com.openggf.trace.TraceEvent.StateSnapshot}
                // record's {@code fields()} map, so flag any line that pulls
                // from it in committed test code.
                || line.contains("fields.get(\"")
                // Local variables that bind a frame-zero (or any trace
                // frame) into engine setup. These names always feed
                // setRingCount/camera.setX/.../engine state in the offending
                // bootstrap path; comparison-only code reads frames straight
                // into binder.compareFrame instead. Catching the assignment
                // is more robust than chasing the downstream setters.
                || line.contains("frameZero != null && frameZero.")
                || line.contains("recordedRings = frameZero")
                || line.contains("recordedCamera")
                // Generic regex catch-all: setter on any reference where the
                // argument expression directly reads a trace-side field.
                || SETTER_FROM_TRACE_FIELD.matcher(line).find();
    }
}
