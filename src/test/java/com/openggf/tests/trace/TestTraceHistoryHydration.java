package com.openggf.tests.trace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TestTraceHistoryHydration {

    @Test
    void convertsRomCentreHistoryToEngineTopLeftHistory() {
        short[] romCentres = {(short) 0x0040, (short) 0x0060, (short) 0x0293};

        short[] converted = TraceHistoryHydration.centreHistoryToTopLeft(romCentres, 20);

        assertArrayEquals(new short[]{(short) 0x0036, (short) 0x0056, (short) 0x0289}, converted);
        assertNotSame(romCentres, converted);
        assertArrayEquals(new short[]{(short) 0x0040, (short) 0x0060, (short) 0x0293}, romCentres);
    }

    @Test
    void convertsRomNextFreeHistorySlotToEngineLatestWrittenSlot() {
        assertEquals(25, TraceHistoryHydration.romHistoryPosToEngineLatestSlot(104));
        assertEquals(63, TraceHistoryHydration.romHistoryPosToEngineLatestSlot(0));
    }
}
