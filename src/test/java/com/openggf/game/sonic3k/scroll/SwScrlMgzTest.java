package com.openggf.game.sonic3k.scroll;

import com.openggf.data.Rom;
import com.openggf.level.scroll.ZoneScrollHandler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SwScrlMgzTest {

    @Test
    public void act1MatchesExactPackedScrollAndOffsetBounds() {
        SwScrlMgz handler = new SwScrlMgz();
        int[] hScroll = new int[224];

        handler.update(hScroll, 0x1200, 0x0400, 1, 0);

        assertEquals(0, handler.getVscrollFactorBG());
        assertEquals(3456, handler.getMinScrollOffset());
        assertEquals(4356, handler.getMaxScrollOffset());
        assertEquals(-301924604, hScroll[0]);
        assertEquals(-301924604, hScroll[15]);
        assertEquals(-301924676, hScroll[16]);
        assertEquals(-301924676, hScroll[19]);
        assertEquals(-301924748, hScroll[20]);
        assertEquals(-301924748, hScroll[23]);
        assertEquals(-301924820, hScroll[24]);
        assertEquals(-301924820, hScroll[31]);
        assertEquals(-301924892, hScroll[32]);
        assertEquals(-301924892, hScroll[39]);
        assertEquals(-301924928, hScroll[40]);
        assertEquals(-301925504, hScroll[223]);
    }

    @Test
    public void act2MatchesExactScatterFilledPackedScrollAndOffsetBounds() {
        SwScrlMgz handler = new SwScrlMgz();
        int[] hScroll = new int[224];

        handler.update(hScroll, 0x1200, 0x0200, 1, 1);

        assertEquals(96, handler.getVscrollFactorBG());
        assertEquals(2304, handler.getMinScrollOffset());
        assertEquals(4522, handler.getMaxScrollOffset());
        assertEquals(-301924772, hScroll[0]);
        assertEquals(-301924772, hScroll[7]);
        assertEquals(-301924535, hScroll[8]);
        assertEquals(-301924944, hScroll[16]);
        assertEquals(-301924675, hScroll[32]);
        assertEquals(-301924761, hScroll[40]);
        assertEquals(-301924589, hScroll[48]);
        assertEquals(-301924718, hScroll[64]);
        assertEquals(-301924449, hScroll[72]);
        assertEquals(-301924492, hScroll[80]);
        assertEquals(-301924438, hScroll[88]);
        assertEquals(-301924632, hScroll[93]);
        assertEquals(-301924944, hScroll[136]);
        assertEquals(-301925224, hScroll[148]);
        assertEquals(-301925504, hScroll[154]);
        assertEquals(-301925784, hScroll[160]);
        assertEquals(-301926096, hScroll[168]);
        assertEquals(-301926656, hScroll[200]);
        assertEquals(-301926656, hScroll[223]);
    }

    @Test
    public void actStateResetsCloudAccumulatorsOnFrameZeroAndActChange() {
        SwScrlMgz handler = new SwScrlMgz();
        int[] hScroll = new int[224];

        handler.update(hScroll, 0x1200, 0x0400, 1, 0);
        assertEquals(0x500, accumulatorValue(handler, "mgz1CloudAccumulator"));

        handler.update(hScroll, 0x1200, 0x0400, 2, 0);
        assertEquals(0xA00, accumulatorValue(handler, "mgz1CloudAccumulator"));

        handler.update(hScroll, 0x1200, 0x0200, 1, 1);
        assertEquals(0x800, accumulatorValue(handler, "mgz2CloudAccumulator"));

        handler.update(hScroll, 0x1200, 0x0400, 0, 0);
        assertEquals(0x500, accumulatorValue(handler, "mgz1CloudAccumulator"));
    }

    @Test
    public void providerRoutesMgzToDedicatedHandler() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new Rom());

        ZoneScrollHandler handler = provider.getHandler(Sonic3kZoneConstants.ZONE_MGZ);
        assertNotNull(handler);
        assertTrue(handler instanceof SwScrlMgz);
    }

    private int accumulatorValue(SwScrlMgz handler, String fieldName) {
        try {
            Field field = SwScrlMgz.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object accumulator = field.get(handler);
            return (int) accumulator.getClass().getMethod("get").invoke(accumulator);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Unable to inspect MGZ cloud accumulator", e);
        }
    }
}
