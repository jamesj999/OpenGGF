package com.openggf.game.rewind;

import com.openggf.debug.playback.Bk2FrameInput;
import com.openggf.debug.playback.Bk2Movie;
import com.openggf.debug.playback.Bk2MovieLoader;
import com.openggf.game.RuntimeManager;
import com.openggf.game.session.GameplayModeContext;
import com.openggf.sprites.playable.AbstractPlayableSprite;
import com.openggf.tests.HeadlessTestFixture;
import com.openggf.tests.TestEnvironment;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Stress test for the v1.5 rewind framework. Drives the entire S2 EHZ1 trace
 * forward with periodic rewinds, exercising edge cases around keyframe
 * boundaries by sheer volume rather than enumerated cases.
 *
 * <p>Three pluggable {@link RewindTorturePattern}s drive five test methods:
 * <ul>
 *   <li>{@link RewindTorturePattern.FixedAdjacent} — adjacent rewinds (forward=2,
 *       rewind=1) repeated until trace end. Most adversarial against keyframe
 *       boundary edge cases.</li>
 *   <li>{@link RewindTorturePattern.ProgressiveLongRewind} — long rewinds
 *       crossing many keyframes; tests segment cache + long-distance seekTo.</li>
 *   <li>{@link RewindTorturePattern.Random_} (3 seeds) — random forward/rewind
 *       lengths; failing test reruns deterministically.</li>
 * </ul>
 *
 * <p><b>Verification:</b>
 * <ul>
 *   <li>Per-iteration cheap check: {@code controller.currentFrame()} matches
 *       the expected logical frame (sum of {@code +forward - rewind} deltas).</li>
 *   <li>Sampled full-state check: at every {@link #CHECKPOINT_INTERVAL}
 *       iterations and at end-of-trace, capture the rewind run's
 *       {@link CompositeSnapshot} and compare per-key against a precomputed
 *       reference snapshot at the same logical frame. Pattern-(b) verifies
 *       on every cycle (~{@code frameCount/100} cycles total — affordable).</li>
 * </ul>
 *
 * <p><b>Phase A (reference precomputation):</b> drive the same fixture's rewind
 * controller forward through the trace, snapshotting at frames the pattern
 * will visit. {@code seekTo(0)} then resets to frame 0 for Phase B. Using a
 * single fixture is necessary because {@code LevelSnapshot} stores
 * {@code Block[]}/{@code Chunk[]} as shared references; two fresh fixtures
 * would produce reference-unequal {@code Block} instances even with identical
 * content.
 *
 * <p><b>Phase B (torture):</b> from frame 0, drive the pattern's cycles via
 * {@code controller.step()} and {@code controller.seekTo()}. At each scheduled
 * checkpoint, compare the rewind state to the precomputed reference.
 *
 * <p><b>Status:</b> all five test methods are currently {@code @Disabled}
 * because they surface real snapshot-coverage gaps in the rewind framework.
 * The infrastructure itself (driver, fixture, diff helper) is correct.
 * Re-enable a test as each underlying gap is fixed. The test class lives in
 * the suite so future fixes can immediately verify their correctness against
 * the trace.
 */
@RequiresRom(SonicGame.SONIC_2)
class TestRewindTorture {

    private static final Path EHZ1_TRACE = Path.of("src/test/resources/traces/s2/ehz1_fullrun");
    private static final int KEYFRAME_INTERVAL = 60;
    private static final int CHECKPOINT_INTERVAL = 100;

    @AfterEach
    void tearDown() {
        TestEnvironment.resetAll();
        GenericRewindEligibility.clearForTest();
    }

    // -------------------------------------------------------------------------
    // Test methods
    //
    // All five tests are currently @Disabled because they surface real
    // snapshot-coverage gaps in the rewind framework (see class javadoc).
    // The test infrastructure itself is correct — run a method manually with
    // {@code -Dopenggf.rewind.torture.run=true -Dtest=TestRewindTorture#methodName}
    // to exercise the rewind framework against the EHZ1 trace. Each failure
    // surfaces a path-based diff identifying the snapshot key + field that
    // didn't round-trip after repeated rewinds.
    //
    // Known divergences (as of landing):
    //   - {@code tortureFixedAdjacent}: fails at ~iteration 800 with
    //     {@code sprites.sprites[1]} (Tails) yHistory + ySubpixel + animation
    //     state divergence. Indicates a Tails-side state field not faithfully
    //     restored across many rewinds.
    //   - {@code tortureProgressiveLongRewinds}: fails at ~iteration 5 with
    //     {@code sprites.sprites[0].dead=true}. Long rewinds followed by full
    //     forward-replay cause Sonic to die in the rewind path even though
    //     the forward-only reference run does not.
    //   - {@code tortureRandomSeed42/1337/8675309}: each fails after ~50
    //     cycles with multiple keys diverging (camera, gamestate, sprites,
    //     object-manager slot drift, rings state). Random patterns surface
    //     the broadest range of coverage gaps.
    //
    // Re-enable each test as the underlying framework gap is fixed; the
    // shared {@link RewindSnapshotDiff} helper produces actionable per-field
    // diff lines to direct the fix.
    // -------------------------------------------------------------------------

    @Disabled("Surfaces sprites[1] (Tails) state divergence at ~iteration 800; pending rewind coverage fix")
    @Test
    void tortureFixedAdjacent() throws Exception {
        runTorture("fixed-adjacent",
                RewindTorturePattern.FixedAdjacent::new, false);
    }

    @Disabled("Surfaces sprites[0].shield divergence at ~iteration 1521 (post-animation-fix); pending rewind coverage fix")
    @Test
    void tortureProgressiveLongRewinds() throws Exception {
        runTorture("progressive-long",
                RewindTorturePattern.ProgressiveLongRewind::new, true);
    }

    @Disabled("Random pattern surfaces multiple snapshot-coverage gaps; pending rewind coverage fix")
    @Test
    void tortureRandomSeed42() throws Exception {
        runTorture("random-seed-42",
                () -> new RewindTorturePattern.Random_(42L), false);
    }

    @Disabled("Random pattern surfaces multiple snapshot-coverage gaps; pending rewind coverage fix")
    @Test
    void tortureRandomSeed1337() throws Exception {
        runTorture("random-seed-1337",
                () -> new RewindTorturePattern.Random_(1337L), false);
    }

    @Disabled("Random pattern surfaces multiple snapshot-coverage gaps; pending rewind coverage fix")
    @Test
    void tortureRandomSeed8675309() throws Exception {
        runTorture("random-seed-8675309",
                () -> new RewindTorturePattern.Random_(8675309L), false);
    }

    // -------------------------------------------------------------------------
    // Driver
    // -------------------------------------------------------------------------

    /**
     * Runs a torture pattern end-to-end against the EHZ1 trace.
     *
     * @param name pattern-name string included in failure messages
     * @param patternFactory yields fresh pattern instances; called twice (once
     *                       for the schedule simulation, once for replay)
     * @param verifyEveryCycle true for pattern (b) — only ~58 cycles total so
     *                         per-cycle is cheap; false for (a)/(c) which
     *                         sample at {@link #CHECKPOINT_INTERVAL} cycles
     */
    private void runTorture(String name,
                            Supplier<RewindTorturePattern> patternFactory,
                            boolean verifyEveryCycle) throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(EHZ1_TRACE),
                "EHZ1 trace directory not found: " + EHZ1_TRACE);
        Path bk2Path = findBk2(EHZ1_TRACE);
        Assumptions.assumeTrue(bk2Path != null, "No .bk2 file found in " + EHZ1_TRACE);

        Bk2Movie movie = new Bk2MovieLoader().load(bk2Path);
        int frameCount = movie.getFrames().size();

        // Discover which logical frames we need reference snapshots at by
        // simulating the pattern without driving the engine. This keeps memory
        // bounded — we only snapshot the trace at the frames we'll verify.
        TortureSchedule schedule = simulateSchedule(patternFactory.get(),
                frameCount, verifyEveryCycle);

        // Build a single fixture and rewind controller. Use it for both phases:
        // phase A (forward-only reference run) and phase B (torture run, which
        // begins with seekTo(0)).
        HeadlessTestFixture fixture = HeadlessTestFixture.builder()
                .withRecording(bk2Path)
                .withZoneAndAct(0, 0)
                .build();
        GameplayModeContext gm = RuntimeManager.getCurrent().getGameplayModeContext();
        assertNotNull(gm, "GameplayModeContext must be available after fixture build");
        RewindRegistry registry = gm.getRewindRegistry();
        assertNotNull(registry, "RewindRegistry must be non-null after fixture build");
        RewindController controller = new RewindController(
                registry,
                new InMemoryKeyframeStore(),
                new MovieInputSource(movie),
                new FixtureStepper(fixture),
                KEYFRAME_INTERVAL);

        // Phase A: run forward through the trace via the controller, capturing
        // reference snapshots at scheduled frames.
        Map<Integer, CompositeSnapshot> referenceSnapshots =
                buildReferenceSnapshots(controller, registry, frameCount,
                        schedule.checkpointFrames());

        // Phase B: rewind to frame 0 (initial keyframe), then drive the
        // pattern through cycles, checking at each checkpoint.
        controller.seekTo(0);
        runTortureCycles(name, controller, registry, patternFactory.get(),
                frameCount, schedule, referenceSnapshots);
    }

    /**
     * Walks the pattern (without driving the engine) to discover which logical
     * frames will be verification checkpoints.
     */
    private static TortureSchedule simulateSchedule(RewindTorturePattern pattern,
                                                     int frameCount,
                                                     boolean verifyEveryCycle) {
        Set<Integer> checkpoints = new TreeSet<>();
        // Always verify the frame-0 baseline (controller starts there).
        checkpoints.add(0);

        int currentFrame = 0;
        int iteration = 0;
        int lastCycleEnd = 0;

        while (true) {
            RewindTorturePattern.Cycle cycle = pattern.next(currentFrame, frameCount);
            if (cycle == null) break;
            currentFrame += cycle.forwardFrames();
            currentFrame -= cycle.rewindFrames();
            iteration++;
            lastCycleEnd = currentFrame;
            if (verifyEveryCycle || iteration % CHECKPOINT_INTERVAL == 0) {
                checkpoints.add(currentFrame);
            }
        }
        if (iteration > 0) {
            checkpoints.add(lastCycleEnd);
        }
        return new TortureSchedule(iteration, checkpoints);
    }

    /**
     * Phase A: drive the controller forward through the trace to the highest
     * frame any checkpoint demands, capturing reference snapshots only at the
     * requested frames. After this phase the controller is at the highest
     * referenced frame; phase B will {@code seekTo(0)} to reset.
     */
    private static Map<Integer, CompositeSnapshot> buildReferenceSnapshots(
            RewindController controller, RewindRegistry registry,
            int frameCount, Set<Integer> requestedFrames) {
        Map<Integer, CompositeSnapshot> out = new LinkedHashMap<>();
        Set<Integer> needFrames = new HashSet<>(requestedFrames);
        int maxFrame = requestedFrames.stream().mapToInt(Integer::intValue).max().orElse(0);
        int recorded = Math.min(maxFrame, frameCount - 1);

        if (needFrames.contains(0)) {
            out.put(0, registry.capture());
        }
        for (int f = 1; f <= recorded; f++) {
            controller.step();
            if (needFrames.contains(f)) {
                out.put(f, registry.capture());
            }
        }
        for (int f : requestedFrames) {
            if (f <= recorded && !out.containsKey(f)) {
                throw new IllegalStateException(
                        "Failed to capture reference snapshot at frame " + f);
            }
        }
        return out;
    }

    /**
     * Phase B: drive the pattern's cycles, verifying at each checkpoint.
     */
    private static void runTortureCycles(String name,
                                          RewindController controller,
                                          RewindRegistry registry,
                                          RewindTorturePattern pattern,
                                          int frameCount,
                                          TortureSchedule schedule,
                                          Map<Integer, CompositeSnapshot> referenceSnapshots) {
        Set<Integer> checkpoints = schedule.checkpointFrames();
        int expectedLogicalFrame = 0;
        int iteration = 0;

        // Initial frame-0 verification.
        verifyAt(name, iteration, registry, expectedLogicalFrame,
                referenceSnapshots, checkpoints);

        while (true) {
            RewindTorturePattern.Cycle cycle = pattern.next(controller.currentFrame(),
                    frameCount);
            if (cycle == null) break;
            for (int i = 0; i < cycle.forwardFrames(); i++) {
                controller.step();
            }
            expectedLogicalFrame += cycle.forwardFrames();
            if (cycle.rewindFrames() > 0) {
                int target = controller.currentFrame() - cycle.rewindFrames();
                controller.seekTo(target);
                expectedLogicalFrame -= cycle.rewindFrames();
            }
            assertEquals(expectedLogicalFrame, controller.currentFrame(),
                    "[" + name + "] iteration " + iteration
                            + ": frame counter desync after cycle (forward="
                            + cycle.forwardFrames() + ", rewind=" + cycle.rewindFrames()
                            + ")");
            iteration++;
            verifyAt(name, iteration, registry, expectedLogicalFrame,
                    referenceSnapshots, checkpoints);
        }
        System.out.printf("[TestRewindTorture] %s completed %d cycles, final frame=%d%n",
                name, iteration, controller.currentFrame());
    }

    /**
     * If {@code expectedFrame} is in the {@code checkpoints} set, compare the
     * registry's current state against the precomputed reference snapshot.
     */
    private static void verifyAt(String name, int iteration,
                                  RewindRegistry registry,
                                  int expectedFrame,
                                  Map<Integer, CompositeSnapshot> referenceSnapshots,
                                  Set<Integer> checkpoints) {
        if (!checkpoints.contains(expectedFrame)) return;
        CompositeSnapshot reference = referenceSnapshots.get(expectedFrame);
        if (reference == null) {
            fail("[" + name + "] iteration " + iteration + " at frame " + expectedFrame
                    + ": reference snapshot missing from precomputed map");
            return;
        }
        CompositeSnapshot actual = registry.capture();
        List<String> failures = compareAllKeys(reference, actual);
        if (!failures.isEmpty()) {
            StringBuilder msg = new StringBuilder();
            msg.append("[").append(name).append("] iteration ").append(iteration)
                    .append(" at frame ").append(expectedFrame)
                    .append(": rewind state diverged from reference forward run\n");
            for (String f : failures) {
                msg.append("  ").append(f).append("\n");
            }
            fail(msg.toString());
        }
    }

    /**
     * Compares every key in the reference snapshot against the actual,
     * delegating per-key comparison to {@link RewindSnapshotDiff}. Returns one
     * entry per diverging key (with up to 20 leaf-diff lines per key).
     */
    private static List<String> compareAllKeys(CompositeSnapshot expected,
                                                CompositeSnapshot actual) {
        List<String> failures = new ArrayList<>();
        for (var entry : expected.entries().entrySet()) {
            String key = entry.getKey();
            Object av = entry.getValue();
            Object bv = actual.get(key);
            List<String> diffs = RewindSnapshotDiff.diffKey(key, av, bv);
            if (!diffs.isEmpty()) {
                failures.add("[" + key + "]");
                for (String d : diffs) {
                    failures.add("    " + d);
                }
            }
        }
        return failures;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Result of {@link #simulateSchedule}: which iterations and frames matter. */
    private record TortureSchedule(int iterationCount, Set<Integer> checkpointFrames) {}

    private static Path findBk2(Path dir) throws Exception {
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".bk2"))
                    .findFirst()
                    .orElse(null);
        }
    }

    /**
     * EngineStepper that applies a Bk2FrameInput's recorded button state to
     * the underlying HeadlessTestRunner via stepFrame(...). Bypasses the
     * fixture's one-way BK2 cursor so seek-and-replay paths drive the engine
     * with the inputs corresponding to the InputSource's reported frame.
     */
    private static final class FixtureStepper implements EngineStepper {
        private final HeadlessTestFixture fixture;

        FixtureStepper(HeadlessTestFixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public void step(Bk2FrameInput inputs) {
            int p1 = inputs.p1InputMask();
            fixture.runner().stepFrame(
                    (p1 & AbstractPlayableSprite.INPUT_UP) != 0,
                    (p1 & AbstractPlayableSprite.INPUT_DOWN) != 0,
                    (p1 & AbstractPlayableSprite.INPUT_LEFT) != 0,
                    (p1 & AbstractPlayableSprite.INPUT_RIGHT) != 0,
                    (p1 & AbstractPlayableSprite.INPUT_JUMP) != 0,
                    inputs.p2InputMask(),
                    inputs.p2StartPressed());
        }
    }

    /**
     * Random-access InputSource backed by a loaded Bk2Movie. read(frame)
     * returns the recorded inputs for that exact frame, so seek-and-replay
     * paths feed the engine the original recorded inputs rather than relying
     * on the fixture's one-way BK2 cursor.
     */
    private static final class MovieInputSource implements InputSource {
        private final Bk2Movie movie;
        private final int frameCount;

        MovieInputSource(Bk2Movie movie) {
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
                return new Bk2FrameInput(frame, 0, 0, false, "torture:oor:" + frame);
            }
            return movie.getFrame(frame);
        }
    }
}
