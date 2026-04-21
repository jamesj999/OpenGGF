package com.openggf.tests.trace.s3k;

import com.openggf.tests.trace.TraceEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kReplayCheckpointDetector {

    @Test
    void introBeginEmitsAtReplayFrameZero() {
        S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();

        TraceEvent.Checkpoint hit = detector.observe(new S3kCheckpointProbe(
                0, null, null, null, 12, 0, false, false, false, false, false, false, false, false));

        assertNotNull(hit);
        assertEquals("intro_begin", hit.name());
        assertNull(hit.actualZoneId());
    }

    @Test
    void requiredCheckpointsEmitOnceInFixedOrder() {
        S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();

        assertEquals("intro_begin", detector.observe(new S3kCheckpointProbe(
                0, null, null, null, 12, 0, false, false, false, false, false, false, false, false)).name());
        assertEquals("gameplay_start", detector.observe(new S3kCheckpointProbe(
                10, 0, 0, 0, 12, 0, false, false, false, false, false, false, true, false)).name());
        assertEquals("aiz1_fire_transition_begin", detector.observe(new S3kCheckpointProbe(
                1651, 0, 0, 0, 12, 0, false, true, false, false, false, false, true, false)).name());
        assertEquals("aiz2_reload_resume", detector.observe(new S3kCheckpointProbe(
                5610, 0, 1, 0, 12, 5, true, false, false, false, false, false, true, false)).name());

        assertTrue(detector.requiredCheckpointNamesReached().contains("aiz1_fire_transition_begin"));
    }

    @Test
    void optionalCheckpointsAreReportedButNotRequired() {
        S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();
        detector.observe(new S3kCheckpointProbe(
                0, null, null, null, 12, 0, false, false, false, false, false, false, false, false));
        detector.observe(new S3kCheckpointProbe(
                10, 0, 0, 0, 12, 0, false, false, false, false, false, false, true, false));
        detector.observe(new S3kCheckpointProbe(
                260, 0, 1, 0, 12, 0, false, false, false, false, false, false, true, false));
        detector.observe(new S3kCheckpointProbe(
                320, 0, 1, 1, 12, 0, false, true, false, false, false, false, true, false));

        TraceEvent.Checkpoint hit = detector.observe(new S3kCheckpointProbe(
                900, 0, 1, 1, 12, 0, false, false, false, false, true, false, true, false));

        assertNotNull(hit);
        assertEquals("aiz2_signpost_begin", hit.name());
        assertFalse(detector.requiredCheckpointNamesReached().contains("aiz2_signpost_begin"));
    }

    @Test
    void requiredCheckpointWinsWhenOptionalAndRequiredPredicatesOverlap() {
        S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();
        detector.observe(new S3kCheckpointProbe(
                0, null, null, null, 12, 0, false, false, false, false, false, false, false, false));
        detector.observe(new S3kCheckpointProbe(
                10, 0, 0, 0, 12, 0, false, false, false, false, false, false, true, false));

        TraceEvent.Checkpoint hit = detector.observe(new S3kCheckpointProbe(
                260, 0, 1, 0, 12, 0, false, true, false, false, true, false, true, false));

        assertNotNull(hit);
        assertEquals("aiz2_reload_resume", hit.name());
    }

    @Test
    void hczHandoffCompleteRequiresZoneActAndMoveLockClear() {
        S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();
        detector.observe(new S3kCheckpointProbe(
                0, null, null, null, 12, 0, false, false, false, false, false, false, false, false));
        detector.observe(new S3kCheckpointProbe(
                10, 0, 0, 0, 12, 0, false, false, false, false, false, false, true, false));
        detector.observe(new S3kCheckpointProbe(
                260, 0, 1, 0, 12, 10, true, false, false, false, false, false, true, false));
        detector.observe(new S3kCheckpointProbe(
                320, 0, 1, 0, 12, 0, false, false, false, false, false, false, true, false));

        assertNull(detector.observe(new S3kCheckpointProbe(
                1700, 1, 0, 0, 12, 5, false, false, false, false, false, false, true, false)));
        TraceEvent.Checkpoint hit = detector.observe(new S3kCheckpointProbe(
                1701, 1, 0, 0, 12, 0, false, false, false, false, false, false, true, false));

        assertNotNull(hit);
        assertEquals("hcz_handoff_complete", hit.name());
    }

    @Test
    void gameplayStartWaitsForTitleCardOverlayToFinish() {
        S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();

        detector.observe(new S3kCheckpointProbe(
                0, null, null, null, 12, 0, false, false, false, false, false, false, false, false));

        assertNull(detector.observe(new S3kCheckpointProbe(
                1227, 0, 0, 0, 12, 0, false, false, false, false, false, false, true, true)));
        assertEquals("gameplay_start", detector.observe(new S3kCheckpointProbe(
                1334, 0, 0, 0, 12, 0, false, false, false, false, false, false, true, false)).name());
    }

    @Test
    void fireTransitionBeginIsRequiredCheckpoint() {
        S3kReplayCheckpointDetector detector = new S3kReplayCheckpointDetector();

        detector.observe(new S3kCheckpointProbe(
                0, null, null, null, 12, 0, false, false, false, false, false, false, false, false));
        detector.observe(new S3kCheckpointProbe(
                10, 0, 0, 0, 12, 0, false, false, false, false, false, false, true, false));

        TraceEvent.Checkpoint hit = detector.observe(new S3kCheckpointProbe(
                1651, 0, 0, 0, 12, 0, false, true, false, false, false, false, true, false));

        assertNotNull(hit);
        assertEquals("aiz1_fire_transition_begin", hit.name());
        assertTrue(detector.requiredCheckpointNamesReached().contains("aiz1_fire_transition_begin"));
    }
}
