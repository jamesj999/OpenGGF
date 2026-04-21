package com.openggf.tests.trace.s3k;

import com.openggf.tests.trace.TraceEvent;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kElasticWindowController {

    @Test
    void gameplayStartClosesFirstWindowAndResumesStrictReplay() {
        S3kElasticWindowController controller = new S3kElasticWindowController(Map.of(
                "intro_begin", 0,
                "gameplay_start", 12,
                "aiz1_fire_transition_begin", 40,
                "aiz2_main_gameplay", 260));

        controller.onEntryFrameValidated(new TraceEvent.Checkpoint(
                0, "intro_begin", null, null, null, 12, null));
        controller.onEngineCheckpoint(new TraceEvent.Checkpoint(
                12, "gameplay_start", 0, 0, 0, 12, null));

        assertTrue(controller.isStrictComparisonEnabled());
        assertEquals(13, controller.strictTraceIndex());
        assertEquals(13, controller.driveTraceIndex());
    }

    @Test
    void fireTransitionCheckpointOpensSecondWindowAfterStrictGap() {
        S3kElasticWindowController controller = new S3kElasticWindowController(Map.of(
                "intro_begin", 0,
                "gameplay_start", 12,
                "aiz1_fire_transition_begin", 40,
                "aiz2_main_gameplay", 260));

        controller.onEntryFrameValidated(new TraceEvent.Checkpoint(
                40, "aiz1_fire_transition_begin", 0, 0, 0, 12, null));

        assertFalse(controller.isStrictComparisonEnabled());
        assertEquals(40, controller.strictTraceIndex());
        assertEquals(40, controller.driveTraceIndex());
    }

    @Test
    void signpostCheckpointRemainsDiagnosticsOnly() {
        S3kElasticWindowController controller = new S3kElasticWindowController(Map.of(
                "aiz1_fire_transition_begin", 40,
                "aiz2_main_gameplay", 260));

        controller.onEntryFrameValidated(new TraceEvent.Checkpoint(
                40, "aiz1_fire_transition_begin", 0, 0, 0, 12, null));
        controller.onEngineCheckpoint(new TraceEvent.Checkpoint(
                215, "aiz2_signpost_begin", 0, 1, 1, 12, null));

        assertFalse(controller.isStrictComparisonEnabled());
        assertEquals(40, controller.strictTraceIndex());
    }

    @Test
    void secondWindowClosesAtMainGameplayCheckpoint() {
        S3kElasticWindowController controller = new S3kElasticWindowController(Map.of(
                "aiz1_fire_transition_begin", 40,
                "aiz2_main_gameplay", 260));

        controller.onEntryFrameValidated(new TraceEvent.Checkpoint(
                40, "aiz1_fire_transition_begin", 0, 0, 0, 12, null));
        controller.onEngineCheckpoint(new TraceEvent.Checkpoint(
                260, "aiz2_main_gameplay", 0, 1, 0, 12, null));

        assertTrue(controller.isStrictComparisonEnabled());
        assertEquals(261, controller.strictTraceIndex());
        assertEquals(261, controller.driveTraceIndex());
    }

    @Test
    void driftBudgetFailureTriggersStructuralDivergence() {
        S3kElasticWindowController controller = new S3kElasticWindowController(Map.of(
                "intro_begin", 0,
                "gameplay_start", 10));

        controller.onEntryFrameValidated(new TraceEvent.Checkpoint(
                0, "intro_begin", null, null, null, 12, null));
        for (int i = 0; i <= 190; i++) {
            controller.onEngineTick();
        }

        IllegalStateException ex = assertThrows(
                IllegalStateException.class, controller::assertWithinDriftBudget);
        assertTrue(ex.getMessage().contains("intro_begin"));
    }

}
