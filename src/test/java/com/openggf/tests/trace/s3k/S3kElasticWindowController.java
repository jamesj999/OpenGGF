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
                openEntryName = checkpoint.name();
                expectedExitName = chainedExitName;
                entryTraceFrame = exitTraceFrame;
                exitTraceFrame = recordedChainedExit;
                engineTicksInsideWindow = 0;
                maxEngineSpan = (exitTraceFrame - entryTraceFrame)
                        + Math.max(180, exitTraceFrame - entryTraceFrame);
                driveTraceIndex = entryTraceFrame;
                strictTraceIndex = entryTraceFrame;
                return;
            }
            strictTraceIndex = exitTraceFrame + 1;
            driveTraceIndex = exitTraceFrame + 1;
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
