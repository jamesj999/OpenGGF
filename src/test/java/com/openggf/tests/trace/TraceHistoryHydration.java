package com.openggf.tests.trace;

import java.util.Arrays;

final class TraceHistoryHydration {

    private TraceHistoryHydration() {
    }

    static short[] centreHistoryToTopLeft(short[] centreHistory, int spriteExtent) {
        if (centreHistory == null) {
            return null;
        }
        return Arrays.copyOf(centreHistory, centreHistory.length);
    }

    static int romHistoryPosToEngineLatestSlot(int romHistoryPosBytes) {
        int nextFreeSlot = (romHistoryPosBytes >> 2) & 0x3F;
        return (nextFreeSlot + 63) & 0x3F;
    }
}
