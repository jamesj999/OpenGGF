package com.openggf.tests;
import org.junit.jupiter.api.Test;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.SonicGame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ROM-backed regression tests for SCZ object art palette line selection.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestSczObjectArtPalette {
    @Test
    public void tornadoAndSczBadnikSheetsUseExpectedPaletteLines() throws Exception {
        Rom rom = com.openggf.tests.TestEnvironment.currentRom();
        Sonic2ObjectArt art = new Sonic2ObjectArt(rom, RomByteReader.fromRom(rom));

        ObjectSpriteSheet tornado = art.loadTornadoSheet();
        assertNotNull(tornado, "Tornado sheet should load from ROM");
        assertEquals(0, tornado.getPaletteIndex(), "Tornado sheet palette should be line 0");

        ObjectSpriteSheet balkiry = art.loadBalkirySheet();
        assertNotNull(balkiry, "Balkiry sheet should load from ROM");
        assertEquals(0, balkiry.getPaletteIndex(), "Balkiry sheet palette should be line 0");
        assertTrue(balkiry.getFrame(0).pieces().stream().allMatch(piece -> piece.priority()), "Balkiry frame 0 pieces should keep priority flag set from objAC mappings");
        assertTrue(balkiry.getFrame(1).pieces().stream().allMatch(piece -> piece.priority()), "Balkiry frame 1 pieces should keep priority flag set from objAC mappings");

        ObjectSpriteSheet nebula = art.loadNebulaSheet();
        assertNotNull(nebula, "Nebula sheet should load from ROM");
        assertEquals(1, nebula.getPaletteIndex(), "Nebula sheet palette should be line 1");

        ObjectSpriteSheet turtloid = art.loadTurtloidSheet();
        assertNotNull(turtloid, "Turtloid sheet should load from ROM");
        assertEquals(0, turtloid.getPaletteIndex(), "Turtloid base sheet should use line 0 (piece palettes add on top)");
        assertTrue(turtloid.getFrame(0).pieces().stream().allMatch(piece -> piece.paletteIndex() == 1), "Turtloid body frame pieces should use palette line +1 from mappings");
        assertTrue(turtloid.getFrame(0).pieces().stream().allMatch(piece -> piece.priority()), "Turtloid body frame pieces should preserve priority flag from mappings");
        assertTrue(turtloid.getFrame(4).pieces().stream().allMatch(piece -> piece.paletteIndex() == 0), "Turtloid shot frame pieces should use palette line +0 from mappings");
        assertTrue(turtloid.getFrame(4).pieces().stream().allMatch(piece -> piece.priority()), "Turtloid shot frame pieces should preserve priority flag from mappings");
    }
}


