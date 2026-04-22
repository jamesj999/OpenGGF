package com.openggf.tests.trace;

import com.openggf.tests.rules.SonicGame;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TestTraceReplayStartPositionPolicy {

    @Test
    void s3kEndToEndTraceAppliesRecordedStartPositionEvenWithIntroBeginAnchor() throws Exception {
        TraceData trace = TraceData.load(Path.of("src/test/resources/traces/s3k/aiz1_to_hcz_fullrun"));
        TraceMetadata metadata = trace.metadata();

        AbstractTraceReplayTest subject = new AbstractTraceReplayTest() {
            @Override
            protected SonicGame game() {
                return SonicGame.SONIC_3K;
            }

            @Override
            protected int zone() {
                return 0;
            }

            @Override
            protected int act() {
                return 0;
            }

            @Override
            protected Path traceDirectory() {
                return Path.of("unused");
            }
        };

        Method method = AbstractTraceReplayTest.class.getDeclaredMethod(
                "shouldApplyMetadataStartPosition",
                TraceData.class,
                TraceMetadata.class);
        method.setAccessible(true);

        boolean shouldApply = (boolean) method.invoke(subject, trace, metadata);

        assertTrue(
                shouldApply,
                "start_x/start_y record the actual trace start state and must still be applied "
                        + "for the S3K AIZ end-to-end trace even though frame 0 is tagged "
                        + "with the unconditional intro_begin anchor");
    }
}
