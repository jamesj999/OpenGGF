package com.openggf.trace;

import java.util.Arrays;

public final class TraceHistoryHydration {

    private TraceHistoryHydration() {
    }

    public static short[] centreHistoryToTopLeft(short[] centreHistory, int spriteExtent) {
        if (centreHistory == null) {
            return null;
        }
        return Arrays.copyOf(centreHistory, centreHistory.length);
    }

    public static int romHistoryPosToEngineLatestSlot(int romHistoryPosBytes) {
        int nextFreeSlot = (romHistoryPosBytes >> 2) & 0x3F;
        return (nextFreeSlot + 63) & 0x3F;
    }
}
