package com.openggf.tests.trace;

import com.openggf.trace.ToleranceConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    private static boolean isForbiddenTraceHydration(String line) {
        return line.contains("applyRecordedFirstSidekickState(")
                || line.contains("applyRecordedFrameState(")
                || line.contains("applySeededFirstSidekickState(")
                || line.contains("applySidekickFollowDelayOverride(")
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
                || line.contains("setYSpeed((short) snapshot.yVel())");
    }
}
