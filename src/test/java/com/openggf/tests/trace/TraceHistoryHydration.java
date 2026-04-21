package com.openggf.tests.trace;

import java.util.Arrays;

final class TraceHistoryHydration {

    private TraceHistoryHydration() {
    }

    static short[] centreHistoryToTopLeft(short[] centreHistory, int spriteExtent) {
        if (centreHistory == null) {
            return null;
        }
        short[] converted = Arrays.copyOf(centreHistory, centreHistory.length);
        int halfExtent = spriteExtent / 2;
        for (int i = 0; i < converted.length; i++) {
            converted[i] = (short) (converted[i] - halfExtent);
        }
        return converted;
    }

    static int romHistoryPosToEngineLatestSlot(int romHistoryPosBytes) {
        int nextFreeSlot = (romHistoryPosBytes >> 2) & 0x3F;
        return (nextFreeSlot + 63) & 0x3F;
    }
}
