package com.openggf.game.rewind;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.RuntimeManager;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.game.rewind.snapshot.LevelSnapshot;
import com.openggf.game.rewind.snapshot.ObjectManagerSnapshot;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.game.session.SessionManager;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Headless benchmark harness for the rewind framework.
 *
 * <p>Runs a real reference trace through the engine in two passes
 * (framework off / framework on) and reports per-phase timing
 * characteristics. Excluded from default CI via the
 * {@code openggf.rewind.benchmark.run} system property — opt in with
 * {@code mvn test "-Dtest=RewindBenchmark" "-Dopenggf.rewind.benchmark.run=true"}.
 */
@RequiresRom(SonicGame.SONIC_2)
@EnabledIfSystemProperty(named = "openggf.rewind.benchmark.run", matches = "true")
public class RewindBenchmark {

    private static final Path TRACE_DIR =
            Path.of("src/test/resources/traces/s2/ehz1_fullrun");
    private static final int KEYFRAME_INTERVAL = 60;

    /** Aggregate phase results, dumped to JSON at end of test. */
    private final Map<String, BenchmarkResults> results = new LinkedHashMap<>();

    /** BK2 movie loaded once per benchmark run for random-access input replay. */
    private Bk2Movie movie;

    @AfterEach
    void cleanup() {
        TestEnvironment.resetAll();
    }

    @Test
    public void runBenchmark() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(TRACE_DIR),
                "Trace directory not found: " + TRACE_DIR);

        // Load BK2 once for random-access input replay across phases.
        movie = loadMovie();

        // Phase 1: forward overhead (framework off + framework on)
        runPhase1ForwardOverhead();

        // Phase 2: capture cost
        runPhase2CaptureCost();

        // Phase 3: restore cost
        runPhase3RestoreCost();

        // Phase 4: cold seek
        runPhase4ColdSeek();

        // Phase 5: hot seek
        runPhase5HotSeek();

        // Phase 6: memory
        runPhase6Memory();

        // Long-tail determinism gate
        runLongTailDeterminismGate();

        // JSON output
        dumpJson();

        // Print human-readable stdout summary
        for (var e : results.entrySet()) {
            System.out.printf("Phase: %s%n", e.getKey());
            BenchmarkResults r = e.getValue();
            BenchmarkResults.PhaseStats s = r.overall();
            System.out.printf(
                    "  samples=%d mean=%dns p50=%dns p95=%dns p99=%dns max=%dns wall=%dns%n",
                    s.sampleCount(), s.meanNs(), s.p50Ns(), s.p95Ns(), s.p99Ns(), s.maxNs(),
                    r.totalWallTimeNs());
        }
    }

    // -------------------------------------------------------------------------
    // Phase 1: forward playback overhead
    // -------------------------------------------------------------------------

    private void runPhase1ForwardOverhead() throws IOException {
        // Pass 1: framework OFF. Drive engine via fixture.stepFrameFromRecording().
        BenchmarkTiming offTiming = new BenchmarkTiming(20_000);
        long offWallStart = System.nanoTime();
        HeadlessTestFixture fixture1 = buildFixture();
        try {
            int frameCount = Math.min(2000, fixture1.runner().getRecordingFramesRemaining());
            for (int i = 0; i < frameCount; i++) {
                long t0 = System.nanoTime();
                fixture1.stepFrameFromRecording();
                offTiming.record(System.nanoTime() - t0);
            }
        } finally {
            TestEnvironment.resetAll();
        }
        long offWall = System.nanoTime() - offWallStart;

        // Pass 2: framework ON. Drive engine via PlaybackController.tick().
        BenchmarkTiming onTiming = new BenchmarkTiming(20_000);
        long onWallStart = System.nanoTime();
        HeadlessTestFixture fixture2 = buildFixture();
        try {
            GameplayModeContext gameplayMode = SessionManager.getCurrentGameplayMode();
            var inputs = new TraceFixtureInputSource(movie);
            var stepper = new TraceFixtureEngineStepper(fixture2);
            gameplayMode.installPlaybackController(inputs, stepper, KEYFRAME_INTERVAL);
            int frameCount = Math.min(2000, fixture2.runner().getRecordingFramesRemaining());
            for (int i = 0; i < frameCount; i++) {
                long t0 = System.nanoTime();
                gameplayMode.getPlaybackController().tick();
                onTiming.record(System.nanoTime() - t0);
            }
        } finally {
            TestEnvironment.resetAll();
        }
        long onWall = System.nanoTime() - onWallStart;

        results.put("phase1.forward.off",
                new BenchmarkResults("phase1.forward.off",
                        offTiming.summarize(), offWall, Map.of()));
        results.put("phase1.forward.on",
                new BenchmarkResults("phase1.forward.on",
                        onTiming.summarize(), onWall, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Phase 2: capture cost
    // -------------------------------------------------------------------------

    private void runPhase2CaptureCost() throws IOException {
        BenchmarkTiming overallTiming = new BenchmarkTiming(1000);
        long wallStart = System.nanoTime();
        HeadlessTestFixture fixture = buildFixture();
        try {
            // Advance to a representative state
            int warmup = Math.min(600, fixture.runner().getRecordingFramesRemaining());
            for (int i = 0; i < warmup; i++) fixture.stepFrameFromRecording();
            GameplayModeContext gm = SessionManager.getCurrentGameplayMode();
            var registry = gm.getRewindRegistry();

            // For 1000 iterations, capture + discard
            for (int i = 0; i < 1000; i++) {
                long t0 = System.nanoTime();
                registry.capture();
                overallTiming.record(System.nanoTime() - t0);
            }
        } finally {
            TestEnvironment.resetAll();
        }
        long wall = System.nanoTime() - wallStart;
        results.put("phase2.capture",
                new BenchmarkResults("phase2.capture",
                        overallTiming.summarize(), wall, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Phase 3: restore cost
    // -------------------------------------------------------------------------

    private void runPhase3RestoreCost() throws IOException {
        BenchmarkTiming overallTiming = new BenchmarkTiming(500);
        long wallStart = System.nanoTime();
        HeadlessTestFixture fixture = buildFixture();
        try {
            int warmup = Math.min(600, fixture.runner().getRecordingFramesRemaining());
            for (int i = 0; i < warmup; i++) fixture.stepFrameFromRecording();
            GameplayModeContext gm = SessionManager.getCurrentGameplayMode();
            var registry = gm.getRewindRegistry();

            // Capture once, then time repeated restores from that snapshot.
            CompositeSnapshot baseline = registry.capture();
            for (int i = 0; i < 500; i++) {
                long t0 = System.nanoTime();
                registry.restore(baseline);
                overallTiming.record(System.nanoTime() - t0);
            }
        } finally {
            TestEnvironment.resetAll();
        }
        long wall = System.nanoTime() - wallStart;
        results.put("phase3.restore",
                new BenchmarkResults("phase3.restore",
                        overallTiming.summarize(), wall, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Phase 4: cold seek
    // -------------------------------------------------------------------------

    private void runPhase4ColdSeek() throws IOException {
        BenchmarkTiming overall = new BenchmarkTiming(50);
        long wallStart = System.nanoTime();
        HeadlessTestFixture fixture = buildFixture();
        try {
            GameplayModeContext gm = SessionManager.getCurrentGameplayMode();
            var inputs = new TraceFixtureInputSource(movie);
            var stepper = new TraceFixtureEngineStepper(fixture);
            gm.installPlaybackController(inputs, stepper, KEYFRAME_INTERVAL);
            var rc = gm.getRewindController();

            // Step forward to populate keyframes
            int stepCount = Math.min(1200, fixture.runner().getRecordingFramesRemaining());
            for (int i = 0; i < stepCount; i++) rc.step();

            // Cold seek: each seekTo invalidates the segment cache internally
            int[] targets = {60, 240, 540, 720, 900, 1080};
            // Iterate 8x to get enough samples per target.
            for (int rep = 0; rep < 8; rep++) {
                for (int target : targets) {
                    long t0 = System.nanoTime();
                    rc.seekTo(target);
                    overall.record(System.nanoTime() - t0);
                }
            }
        } finally {
            TestEnvironment.resetAll();
        }
        long wall = System.nanoTime() - wallStart;
        results.put("phase4.cold-seek",
                new BenchmarkResults("phase4.cold-seek",
                        overall.summarize(), wall, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Phase 5: hot seek
    // -------------------------------------------------------------------------

    private void runPhase5HotSeek() throws IOException {
        BenchmarkTiming withinSegment = new BenchmarkTiming(500);
        BenchmarkTiming acrossSegment = new BenchmarkTiming(50);
        long wallStart = System.nanoTime();
        HeadlessTestFixture fixture = buildFixture();
        try {
            GameplayModeContext gm = SessionManager.getCurrentGameplayMode();
            var inputs = new TraceFixtureInputSource(movie);
            var stepper = new TraceFixtureEngineStepper(fixture);
            gm.installPlaybackController(inputs, stepper, KEYFRAME_INTERVAL);
            var rc = gm.getRewindController();

            // Step forward 1200 frames so we have plenty of segments.
            int stepCount = Math.min(1200, fixture.runner().getRecordingFramesRemaining());
            for (int i = 0; i < stepCount; i++) rc.step();

            // Hot scrub: stepBackward 30 times within the same segment
            for (int i = 0; i < 30; i++) {
                long t0 = System.nanoTime();
                rc.stepBackward();
                withinSegment.record(System.nanoTime() - t0);
            }
            // Crossing a segment boundary
            long t0 = System.nanoTime();
            rc.stepBackward();
            acrossSegment.record(System.nanoTime() - t0);
            // Continue within previous segment
            for (int i = 0; i < 29; i++) {
                long t1 = System.nanoTime();
                rc.stepBackward();
                withinSegment.record(System.nanoTime() - t1);
            }
            // Cross another boundary
            long t2 = System.nanoTime();
            rc.stepBackward();
            acrossSegment.record(System.nanoTime() - t2);
        } finally {
            TestEnvironment.resetAll();
        }
        long wall = System.nanoTime() - wallStart;
        results.put("phase5.hot-seek.within-segment",
                new BenchmarkResults("phase5.hot-seek.within-segment",
                        withinSegment.summarize(), wall, Map.of()));
        results.put("phase5.hot-seek.across-segment",
                new BenchmarkResults("phase5.hot-seek.across-segment",
                        acrossSegment.summarize(), wall, Map.of()));
    }

    // -------------------------------------------------------------------------
    // Phase 6: memory measurement
    // -------------------------------------------------------------------------

    private void runPhase6Memory() throws IOException {
        long wallStart = System.nanoTime();
        Map<String, Long> keyframeBytes = new LinkedHashMap<>();
        int keyframeCount = 0;
        long totalSerializedBytes = 0L;
        HeadlessTestFixture fixture = buildFixture();
        try {
            GameplayModeContext gm = SessionManager.getCurrentGameplayMode();
            var inputs = new TraceFixtureInputSource(movie);
            var stepper = new TraceFixtureEngineStepper(fixture);
            gm.installPlaybackController(inputs, stepper, KEYFRAME_INTERVAL);
            var rc = gm.getRewindController();

            int stepCount = Math.min(1200, fixture.runner().getRecordingFramesRemaining());
            for (int i = 0; i < stepCount; i++) rc.step();

            // Sample one full CompositeSnapshot's serialized size as a proxy
            var registry = gm.getRewindRegistry();
            CompositeSnapshot snap = registry.capture();
            for (var e : snap.entries().entrySet()) {
                long bytes = estimateSerializedSize(e.getValue());
                keyframeBytes.put(e.getKey(), bytes);
                if (bytes > 0) totalSerializedBytes += bytes;
            }
            keyframeCount = stepCount / KEYFRAME_INTERVAL;
        } finally {
            TestEnvironment.resetAll();
        }
        long wall = System.nanoTime() - wallStart;

        System.out.printf("Phase 6 memory:%n  keyframeCount=%d totalBytesPerKeyframe=%d%n",
                keyframeCount, totalSerializedBytes);
        for (var e : keyframeBytes.entrySet()) {
            System.out.printf("    %-30s %10d bytes%s%n",
                    e.getKey(), e.getValue(),
                    e.getValue() < 0 ? " (non-serializable)" : "");
        }
        long projectedTotalBytes = (long) keyframeCount * totalSerializedBytes;
        System.out.printf("  projectedResidentBytes=%d (%.2f MB)%n",
                projectedTotalBytes, projectedTotalBytes / 1_048_576.0);

        // Store the per-key bytes as PhaseStats with bytes-as-meanNs (semantic
        // hack to fit the JSON output schema)
        Map<String, BenchmarkResults.PhaseStats> perKey = new LinkedHashMap<>();
        for (var e : keyframeBytes.entrySet()) {
            long bytes = e.getValue();
            perKey.put(e.getKey(),
                    new BenchmarkResults.PhaseStats(1, bytes, bytes, bytes, bytes, bytes));
        }
        results.put("phase6.memory",
                new BenchmarkResults("phase6.memory",
                        new BenchmarkResults.PhaseStats(keyframeCount, totalSerializedBytes,
                                totalSerializedBytes, totalSerializedBytes, totalSerializedBytes,
                                totalSerializedBytes),
                        wall, perKey));
    }

    private static long estimateSerializedSize(Object obj) {
        if (obj == null) return 0L;
        try {
            var baos = new java.io.ByteArrayOutputStream();
            try (var oos = new java.io.ObjectOutputStream(baos)) {
                oos.writeObject(obj);
            }
            return baos.size();
        } catch (java.io.IOException e) {
            // Snapshot record contains non-serializable references (e.g.,
            // ObjectSpawn refs in ObjectManagerSnapshot). Return -1 as a
            // sentinel for "unknown size".
            return -1L;
        }
    }

    // -------------------------------------------------------------------------
    // Long-tail determinism gate
    // -------------------------------------------------------------------------

    private void runLongTailDeterminismGate() throws IOException {
        long wallStart = System.nanoTime();
        int longestCleanRewind = 0;
        String firstDivergentKey = null;
        int divergenceFrame = -1;

        HeadlessTestFixture fixture = buildFixture();
        try {
            GameplayModeContext gm = SessionManager.getCurrentGameplayMode();
            var inputs = new TraceFixtureInputSource(movie);
            var stepper = new TraceFixtureEngineStepper(fixture);
            gm.installPlaybackController(inputs, stepper, KEYFRAME_INTERVAL);
            var rc = gm.getRewindController();
            var registry = gm.getRewindRegistry();

            // Forward run: capture every-frame snapshots into a baseline list
            int targetFrame = Math.min(1200, fixture.runner().getRecordingFramesRemaining());
            java.util.List<CompositeSnapshot> baseline =
                    new java.util.ArrayList<>(targetFrame + 1);
            baseline.add(registry.capture());
            for (int i = 0; i < targetFrame; i++) {
                rc.step();
                baseline.add(registry.capture());
            }

            // Test increasing scrub distances:
            int[] scrubDistances = {60, 120, 300, 600, 900, 1200};
            for (int n : scrubDistances) {
                int origin = rc.currentFrame();
                int back = Math.max(0, origin - n);
                rc.seekTo(back);
                // Replay forward to origin, checking for divergence at EACH replay step.
                int firstDivergeFrame = -1;
                String firstDivergeKeyPerFrame = null;
                while (rc.currentFrame() < origin) {
                    rc.step();
                    if (firstDivergeFrame < 0) {
                        CompositeSnapshot midSnap = registry.capture();
                        String mid = compareForCoveredKeys(
                                baseline.get(rc.currentFrame()), midSnap);
                        if (mid != null) {
                            firstDivergeFrame = rc.currentFrame();
                            firstDivergeKeyPerFrame = mid;
                        }
                    }
                }
                if (firstDivergeFrame >= 0) {
                    System.out.printf(
                            "  per-frame: first diverge at frame %d (offset +%d from seek-target %d), key='%s'%n",
                            firstDivergeFrame, firstDivergeFrame - back, back,
                            firstDivergeKeyPerFrame);
                }
                CompositeSnapshot afterReplay = registry.capture();
                String mismatch = compareForCoveredKeys(baseline.get(origin), afterReplay);
                if (mismatch != null) {
                    firstDivergentKey = mismatch;
                    divergenceFrame = origin;
                    System.out.printf(
                            "Long-tail determinism gate: divergence after %d-frame rewind" +
                            " on key '%s' at frame %d%n", n, mismatch, origin);
                    break;
                }
                longestCleanRewind = n;
                System.out.printf("Long-tail determinism gate: clean rewind of %d frames%n", n);
            }
        } finally {
            TestEnvironment.resetAll();
        }

        long wall = System.nanoTime() - wallStart;
        System.out.printf(
                "Long-tail result: longestCleanRewind=%d frames (%.2f sec @ 60fps)%n",
                longestCleanRewind, longestCleanRewind / 60.0);
        if (firstDivergentKey != null) {
            System.out.printf("  firstDivergentKey=%s at frame %d%n",
                    firstDivergentKey, divergenceFrame);
        }

        results.put("longtail.determinism",
                new BenchmarkResults("longtail.determinism",
                        new BenchmarkResults.PhaseStats(longestCleanRewind, longestCleanRewind,
                                longestCleanRewind, longestCleanRewind, longestCleanRewind,
                                longestCleanRewind),
                        wall, Map.of()));
    }

    /**
     * Returns the first mismatching key, or null if both snapshots agree on
     * all v1.5-covered keys.
     */
    private static String compareForCoveredKeys(CompositeSnapshot a, CompositeSnapshot b) {
        String[] coveredKeys = {
                "camera", "gamestate", "gamerng", "timermanager", "fademanager",
                "oscillation", "level", "object-manager",
                "parallax", "water", "zone-runtime", "palette-ownership",
                "animated-tile-channels", "special-render", "advanced-render-mode",
                "mutation-pipeline", "solid-execution",
                "level-event", "rings", "s2-plc-art",
                "sprites"
        };
        // Diagnostic: collect ALL divergent keys, not just the first.
        java.util.List<String> divergent = new java.util.ArrayList<>();
        for (String key : coveredKeys) {
            Object av = a.get(key);
            Object bv = b.get(key);
            if (av == null && bv == null) continue;
            if (av == null || bv == null) {
                divergent.add(key + "(one-side-null)");
                continue;
            }
            if (!keyEquals(key, av, bv)) {
                divergent.add(key);
                // Print details for camera so we can see which exact field differs.
                if (key.equals("camera")) {
                    System.out.println("  Camera diff:");
                    System.out.println("    A: " + av);
                    System.out.println("    B: " + bv);
                }
            }
        }
        if (divergent.isEmpty()) return null;
        if (divergent.size() > 1) {
            System.out.println("  Divergent keys (all): " + divergent);
        }
        return divergent.get(0);
    }

    /**
     * Per-key equality. Defaults to .equals(); custom comparators for keys
     * whose snapshot records contain arrays or non-content-equal references.
     */
    private static boolean keyEquals(String key, Object a, Object b) {
        return switch (key) {
            case "level" -> compareLevel(a, b);
            case "object-manager" -> compareObjectManager(a, b);
            default -> java.util.Objects.equals(a, b);
        };
    }

    private static boolean compareLevel(Object a, Object b) {
        if (!(a instanceof LevelSnapshot la) ||
            !(b instanceof LevelSnapshot lb)) {
            return false;
        }
        // After restore, epoch is bumped by +1, so accept epoch within 1.
        if (Math.abs(la.epochAtCapture() - lb.epochAtCapture()) > 1) return false;
        return Arrays.equals(la.blocks(), lb.blocks())
            && Arrays.equals(la.chunks(), lb.chunks())
            && Arrays.equals(la.mapData(), lb.mapData());
    }

    private static boolean compareObjectManager(Object a, Object b) {
        if (!(a instanceof ObjectManagerSnapshot oa) ||
            !(b instanceof ObjectManagerSnapshot ob)) {
            return false;
        }
        if (!Arrays.equals(oa.usedSlotsBits(), ob.usedSlotsBits())) return false;
        if (oa.frameCounter() != ob.frameCounter()) return false;
        if (oa.vblaCounter() != ob.vblaCounter()) return false;
        return java.util.Objects.equals(oa.slots(), ob.slots());
    }

    // -------------------------------------------------------------------------
    // JSON output
    // -------------------------------------------------------------------------

    private void dumpJson() throws IOException {
        Path outputDir = Path.of("target");
        Files.createDirectories(outputDir);
        Path output = outputDir.resolve("rewind-benchmark-results.json");

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"trace\": \"").append(escapeJson(TRACE_DIR.toString())).append("\",\n");
        sb.append("  \"keyframeInterval\": ").append(KEYFRAME_INTERVAL).append(",\n");
        sb.append("  \"phases\": {\n");
        int i = 0;
        for (var e : results.entrySet()) {
            if (i++ > 0) sb.append(",\n");
            BenchmarkResults r = e.getValue();
            sb.append("    \"").append(escapeJson(e.getKey())).append("\": {\n");
            appendPhaseStats(sb, r.overall());
            sb.append(",\n      \"totalWallTimeNs\": ").append(r.totalWallTimeNs());
            if (!r.perSubsystem().isEmpty()) {
                sb.append(",\n      \"perSubsystem\": {\n");
                int j = 0;
                for (var ps : r.perSubsystem().entrySet()) {
                    if (j++ > 0) sb.append(",\n");
                    sb.append("        \"").append(escapeJson(ps.getKey())).append("\": {");
                    BenchmarkResults.PhaseStats s = ps.getValue();
                    sb.append(" \"sampleCount\": ").append(s.sampleCount());
                    sb.append(", \"meanNs\": ").append(s.meanNs());
                    sb.append(", \"p50Ns\": ").append(s.p50Ns());
                    sb.append(", \"p95Ns\": ").append(s.p95Ns());
                    sb.append(", \"p99Ns\": ").append(s.p99Ns());
                    sb.append(", \"maxNs\": ").append(s.maxNs());
                    sb.append(" }");
                }
                sb.append("\n      }");
            }
            sb.append("\n    }");
        }
        sb.append("\n  }\n}\n");

        Files.writeString(output, sb.toString());
        System.out.printf("Benchmark results written to %s%n", output);
    }

    private static void appendPhaseStats(StringBuilder sb, BenchmarkResults.PhaseStats s) {
        sb.append("      \"sampleCount\": ").append(s.sampleCount()).append(",\n");
        sb.append("      \"meanNs\": ").append(s.meanNs()).append(",\n");
        sb.append("      \"p50Ns\": ").append(s.p50Ns()).append(",\n");
        sb.append("      \"p95Ns\": ").append(s.p95Ns()).append(",\n");
        sb.append("      \"p99Ns\": ").append(s.p99Ns()).append(",\n");
        sb.append("      \"maxNs\": ").append(s.maxNs());
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // -------------------------------------------------------------------------
    // Fixture helpers
    // -------------------------------------------------------------------------

    private HeadlessTestFixture buildFixture() throws IOException {
        Path bk2 = findBk2(TRACE_DIR);
        return HeadlessTestFixture.builder()
                .withRecording(bk2)
                .withZoneAndAct(0, 0)
                .build();
    }

    private static Path findBk2(Path traceDir) throws IOException {
        try (var stream = Files.list(traceDir)) {
            return stream.filter(p -> p.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No .bk2 file found in " + traceDir));
        }
    }

    // -------------------------------------------------------------------------
    // Inner adapter classes
    // -------------------------------------------------------------------------

    /**
     * EngineStepper that applies a Bk2FrameInput's recorded button state to the
     * underlying HeadlessTestRunner via stepFrame(...). Bypasses the fixture's
     * one-way BK2 cursor so seek-and-replay paths (e.g. RewindController.seekTo
     * followed by step()) drive the engine with the inputs corresponding to
     * the InputSource's reported frame, not the cursor's current position.
     */
    private static final class TraceFixtureEngineStepper implements EngineStepper {
        private final HeadlessTestFixture fixture;

        TraceFixtureEngineStepper(HeadlessTestFixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public void step(Bk2FrameInput inputs) {
            int p1 = inputs.p1InputMask();
            boolean up    = (p1 & AbstractPlayableSprite.INPUT_UP)    != 0;
            boolean down  = (p1 & AbstractPlayableSprite.INPUT_DOWN)  != 0;
            boolean left  = (p1 & AbstractPlayableSprite.INPUT_LEFT)  != 0;
            boolean right = (p1 & AbstractPlayableSprite.INPUT_RIGHT) != 0;
            boolean jump  = (p1 & AbstractPlayableSprite.INPUT_JUMP)  != 0;
            fixture.runner().stepFrame(up, down, left, right, jump,
                    inputs.p2InputMask(), inputs.p2StartPressed());
        }
    }

    /**
     * Random-access InputSource backed by a loaded Bk2Movie. read(frame) returns
     * the recorded inputs for that exact frame, so seek-and-replay paths feed
     * the engine the original recorded inputs rather than relying on the
     * fixture's one-way BK2 cursor.
     */
    private static final class TraceFixtureInputSource implements InputSource {
        private final Bk2Movie movie;
        private final int frameCount;

        TraceFixtureInputSource(Bk2Movie movie) {
            this.movie = movie;
            this.frameCount = movie.getFrames().size();
        }

        @Override
        public int frameCount() {
            return frameCount;
        }

        @Override
        public Bk2FrameInput read(int frame) {
            if (frame < 0 || frame >= frameCount) {
                return new Bk2FrameInput(frame, 0, 0, false, "benchmark:oor:" + frame);
            }
            return movie.getFrame(frame);
        }
    }

    /** Loads the BK2 movie for this benchmark's trace once. */
    private Bk2Movie loadMovie() throws IOException {
        Path bk2 = findBk2(TRACE_DIR);
        return new Bk2MovieLoader().load(bk2);
    }

}
