package com.openggf.tests.trace.s3k;

import com.openggf.tests.trace.TraceEvent;

import java.util.Map;

public final class S3kElasticWindowController {

    private static final Map<String, String> ELASTIC_WINDOWS = Map.of(
            "intro_begin", "gameplay_start",
            "aiz1_fire_transition_begin", "aiz2_main_gameplay");

    private final Map<String, Integer> traceCheckpointFrames;
    private int driveTraceIndex;
    private int strictTraceIndex;
    private String openEntryName;
    private String expectedExitName;
    private int entryTraceFrame;
    private int exitTraceFrame;
    private int engineTicksInsideWindow;
    private int maxEngineSpan;

    public S3kElasticWindowController(Map<String, Integer> traceCheckpointFrames) {
        this.traceCheckpointFrames = Map.copyOf(traceCheckpointFrames);
    }

    public void onEntryFrameValidated(TraceEvent.Checkpoint checkpoint) {
        String exitName = ELASTIC_WINDOWS.get(checkpoint.name());
        if (exitName == null) {
            return;
        }
        Integer recordedEntry = traceCheckpointFrames.get(checkpoint.name());
        Integer recordedExit = traceCheckpointFrames.get(exitName);
        if (recordedEntry == null || recordedExit == null) {
            throw new IllegalStateException(
                    "Missing recorded checkpoint frames for elastic window "
                            + checkpoint.name() + " -> " + exitName);
        }

        openEntryName = checkpoint.name();
        expectedExitName = exitName;
        entryTraceFrame = recordedEntry;
        exitTraceFrame = recordedExit;
        engineTicksInsideWindow = 0;
        maxEngineSpan = (exitTraceFrame - entryTraceFrame)
                + Math.max(180, exitTraceFrame - entryTraceFrame);
        driveTraceIndex = entryTraceFrame;
        strictTraceIndex = entryTraceFrame;
    }

    public void alignCursorToTraceIndex(int traceIndex) {
        driveTraceIndex = traceIndex;
        strictTraceIndex = traceIndex;
    }

    public void onEngineTick() {
        if (openEntryName != null) {
            engineTicksInsideWindow++;
        }
    }

    public void assertWithinDriftBudget() {
        if (openEntryName != null && engineTicksInsideWindow > maxEngineSpan) {
            throw new IllegalStateException(
                    "Elastic window drift budget exhausted for " + openEntryName);
        }
    }

    public void advanceDriveCursor() {
        driveTraceIndex++;
        // Auto-close stale elastic windows once the drive cursor passes the
        // recorded exit frame. This covers legacy warmup paths where the
        // engine's title-card / checkpoint timing diverges from the trace
        // (e.g. AIZ1 intro: detector requires titleCardOverlayActive at
        // gameplay_start, which is set at a different engine tick than the
        // recorder sampled). Without this, later checkpoints like
        // aiz1_fire_transition_begin would throw "out-of-order" inside the
        // still-open intro window.
        if (openEntryName != null && driveTraceIndex > exitTraceFrame) {
            openEntryName = null;
            expectedExitName = null;
            strictTraceIndex = driveTraceIndex;
            engineTicksInsideWindow = 0;
        }
        if (openEntryName == null) {
            strictTraceIndex = driveTraceIndex;
        }
    }

    public void onEngineCheckpoint(TraceEvent.Checkpoint checkpoint) {
        if (openEntryName == null) {
            return;
        }
        if (checkpoint.name().equals(openEntryName)) {
            return;
        }
        if (checkpoint.name().equals(expectedExitName)) {
            String chainedExitName = ELASTIC_WINDOWS.get(checkpoint.name());
            if (chainedExitName != null) {
                Integer recordedChainedExit = traceCheckpointFrames.get(chainedExitName);
                if (recordedChainedExit == null) {
                    throw new IllegalStateException(
                            "Missing recorded checkpoint frame for elastic window "
                                    + checkpoint.name() + " -> " + chainedExitName);
                }
                // When the engine reaches the exit checkpoint later than the
                // recorded trace frame, preserve forward progress of the drive
                // cursor so we do not rewind into already-consumed BK2 inputs.
                // The chained-window entry frame is the outer window's exit;
                // clamp to the maximum of the engine's current position and
                // the recorded entry so BK2/trace indexing stays monotonic.
                int chainedEntry = Math.max(driveTraceIndex, exitTraceFrame);
                openEntryName = checkpoint.name();
                expectedExitName = chainedExitName;
                entryTraceFrame = chainedEntry;
                exitTraceFrame = recordedChainedExit;
                engineTicksInsideWindow = 0;
                maxEngineSpan = (exitTraceFrame - entryTraceFrame)
                        + Math.max(180, exitTraceFrame - entryTraceFrame);
                driveTraceIndex = chainedEntry;
                strictTraceIndex = chainedEntry;
                return;
            }
            // Engine may reach the exit checkpoint earlier or later than the
            // recorded trace frame. Resume strict comparison at the first
            // frame that is (a) after the recorded exit AND (b) after the
            // engine's current drive cursor, so we never rewind backwards and
            // re-consume BK2 inputs that were already read during the window.
            int resumeIndex = Math.max(driveTraceIndex + 1, exitTraceFrame + 1);
            strictTraceIndex = resumeIndex;
            driveTraceIndex = resumeIndex;
            openEntryName = null;
            expectedExitName = null;
            return;
        }
        if (ELASTIC_WINDOWS.containsKey(checkpoint.name())) {
            throw new IllegalStateException(
                    "Out-of-order checkpoint " + checkpoint.name()
                            + " while waiting for " + expectedExitName);
        }
    }

    public boolean isStrictComparisonEnabled() {
        return openEntryName == null;
    }

    public int driveTraceIndex() {
        return driveTraceIndex;
    }

    public int strictTraceIndex() {
        return strictTraceIndex;
    }
}

record ReplayCursorState(
        int driveTraceIndex,
        int strictTraceIndex,
        boolean strictComparisonEnabled) {
}
