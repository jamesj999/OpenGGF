package com.openggf.tests.trace;

import com.openggf.trace.*;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestS3kSyntheticV3Fixture {

    private static final Path SYNTHETIC_S3K_V3 =
            Path.of("src/test/resources/traces/synthetic/s3k_execution_v3_2frames");

    @Test
    void parsesS3kMetadataAndExercisesLagCounter() throws IOException {
        TraceData data = TraceData.load(SYNTHETIC_S3K_V3);

        assertEquals(3, data.metadata().traceSchema());
        assertEquals("s3k", data.metadata().game());

        TraceFrame f0 = data.getFrame(0);
        TraceFrame f1 = data.getFrame(1);

        assertEquals(f0.gameplayFrameCounter(), f1.gameplayFrameCounter(), "frame 1 is a lag frame");
        assertTrue(f1.vblankCounter() > f0.vblankCounter());
        assertEquals(0, f0.lagCounter());
        assertTrue(f1.lagCounter() > 0);

        TraceExecutionPhase phase = TraceExecutionModel.forGame("s3k").phaseFor(f0, f1);
        assertEquals(TraceExecutionPhase.VBLANK_ONLY, phase);
    }
}
