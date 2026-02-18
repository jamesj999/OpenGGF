package uk.co.jamesj999.sonic.game.sonic3k.scroll;

import org.junit.Test;
import uk.co.jamesj999.sonic.level.scroll.ZoneScrollHandler;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SwScrlMgzTest {

    @Test
    public void act1UsesPerLineParallaxAndFixedBgY() {
        SwScrlMgz handler = new SwScrlMgz();
        int[] hScroll = new int[224];

        handler.update(hScroll, 0x1200, 0x0400, 1, 0);

        assertEquals(0, handler.getVscrollFactorBG());
        assertTrue(handler.getMaxScrollOffset() > handler.getMinScrollOffset());
        assertTrue(uniqueBgLines(hScroll) > 1);
    }

    @Test
    public void act2UsesMgzBgYRatioAndPerLineParallax() {
        SwScrlMgz handler = new SwScrlMgz();
        int[] hScroll = new int[224];

        handler.update(hScroll, 0x1200, 0x0200, 1, 1);

        // MGZ2 normal path: Camera_Y_pos_BG_copy = floor(cameraY * 3 / 16)
        assertEquals(96, handler.getVscrollFactorBG());
        assertTrue(handler.getMaxScrollOffset() > handler.getMinScrollOffset());
        assertTrue(uniqueBgLines(hScroll) > 1);
    }

    @Test
    public void providerRoutesMgzToDedicatedHandler() throws Exception {
        Sonic3kScrollHandlerProvider provider = new Sonic3kScrollHandlerProvider();
        provider.load(new uk.co.jamesj999.sonic.data.Rom());

        ZoneScrollHandler handler = provider.getHandler(Sonic3kZoneConstants.ZONE_MGZ);
        assertNotNull(handler);
        assertTrue(handler instanceof SwScrlMgz);
    }

    private int uniqueBgLines(int[] hScroll) {
        Set<Integer> values = new HashSet<>();
        for (int packed : hScroll) {
            values.add((int) (short) (packed & 0xFFFF));
        }
        return values.size();
    }
}
