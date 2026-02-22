package com.openggf.game.sonic3k.objects;

import org.junit.Test;
import static org.junit.Assert.*;

public class TestAizIntroPaletteCycler {

    @Test
    public void initialPaletteFrameIs0x24() {
        var cycler = new AizIntroPaletteCycler();
        cycler.init();
        assertEquals(0x24, cycler.getPaletteFrame());
    }

    @Test
    public void timerCountsDownFrom6() {
        var cycler = new AizIntroPaletteCycler();
        cycler.init();
        // Advance 5 frames - timer should decrement but not trigger
        for (int i = 0; i < 5; i++) {
            cycler.advance();
        }
        assertEquals(0x24, cycler.getPaletteFrame()); // Not advanced yet
    }

    @Test
    public void paletteAdvancesAfter6Frames() {
        var cycler = new AizIntroPaletteCycler();
        cycler.init();
        // Advance 7 frames (timer starts at 6, fires on reaching 0)
        for (int i = 0; i < 7; i++) {
            cycler.advance();
        }
        assertEquals(0x2A, cycler.getPaletteFrame()); // 0x24 + 6
    }

    @Test
    public void paletteWrapsFrom0x36BackTo0x24() {
        var cycler = new AizIntroPaletteCycler();
        cycler.init();
        cycler.setPaletteFrame(0x36);
        // Force one more cycle
        for (int i = 0; i < 7; i++) {
            cycler.advance();
        }
        // 0x36 + 6 = 0x3C > 0x36, should wrap to 0x24
        assertEquals(0x24, cycler.getPaletteFrame());
    }

    @Test
    public void mappingFrameAlternatesOnVblankParity() {
        var cycler = new AizIntroPaletteCycler();
        assertEquals(0x21, cycler.getMappingFrame(0)); // even frame
        assertEquals(0x22, cycler.getMappingFrame(1)); // odd frame
        assertEquals(0x21, cycler.getMappingFrame(2)); // even frame
    }
}
