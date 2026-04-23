package com.openggf.tests.trace.s3k;

import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;
import com.openggf.tests.trace.AbstractTraceReplayTest;

import java.nio.file.Path;

@RequiresRom(SonicGame.SONIC_3K)
public class TestS3kCnzTraceReplay extends AbstractTraceReplayTest {

    @Override
    protected SonicGame game() {
        return SonicGame.SONIC_3K;
    }

    @Override
    protected int zone() {
        return 0x03; // CNZ
    }

    @Override
    protected int act() {
        // 0-based: 0 = Act 1. The recorder's metadata.json writes "act": 1
        // (1-based). AbstractTraceReplayTest.validateMetadata does not
        // cross-check the act, so the asymmetry is harmless; documenting
        // here so the next reader doesn't chase the off-by-one.
        return 0x00;
    }

    @Override
    protected Path traceDirectory() {
        return Path.of("src/test/resources/traces/s3k/cnz");
    }
}
