package com.openggf.tests;

import org.junit.Rule;
import org.junit.Test;
import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic2.Sonic2ObjectArt;
import com.openggf.level.objects.ObjectSpriteSheet;
import com.openggf.tests.rules.RequiresRom;
import com.openggf.tests.rules.RequiresRomRule;
import com.openggf.tests.rules.SonicGame;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ROM-backed regression tests for SCZ object art palette line selection.
 */
@RequiresRom(SonicGame.SONIC_2)
public class TestSczObjectArtPalette {

    @Rule
    public RequiresRomRule romRule = new RequiresRomRule();

    @Test
    public void tornadoAndSczBadnikSheetsUseExpectedPaletteLines() throws Exception {
        Rom rom = romRule.rom();
        Sonic2ObjectArt art = new Sonic2ObjectArt(rom, RomByteReader.fromRom(rom));

        ObjectSpriteSheet tornado = art.loadTornadoSheet();
        assertNotNull("Tornado sheet should load from ROM", tornado);
        assertEquals("Tornado sheet palette should be line 0", 0, tornado.getPaletteIndex());

        ObjectSpriteSheet balkiry = art.loadBalkirySheet();
        assertNotNull("Balkiry sheet should load from ROM", balkiry);
        assertEquals("Balkiry sheet palette should be line 0", 0, balkiry.getPaletteIndex());
        assertTrue(
                "Balkiry frame 0 pieces should keep priority flag set from objAC mappings",
                balkiry.getFrame(0).pieces().stream().allMatch(piece -> piece.priority()));
        assertTrue(
                "Balkiry frame 1 pieces should keep priority flag set from objAC mappings",
                balkiry.getFrame(1).pieces().stream().allMatch(piece -> piece.priority()));

        ObjectSpriteSheet nebula = art.loadNebulaSheet();
        assertNotNull("Nebula sheet should load from ROM", nebula);
        assertEquals("Nebula sheet palette should be line 1", 1, nebula.getPaletteIndex());

        ObjectSpriteSheet turtloid = art.loadTurtloidSheet();
        assertNotNull("Turtloid sheet should load from ROM", turtloid);
        assertEquals("Turtloid base sheet should use line 0 (piece palettes add on top)", 0, turtloid.getPaletteIndex());
        assertTrue(
                "Turtloid body frame pieces should use palette line +1 from mappings",
                turtloid.getFrame(0).pieces().stream().allMatch(piece -> piece.paletteIndex() == 1));
        assertTrue(
                "Turtloid body frame pieces should preserve priority flag from mappings",
                turtloid.getFrame(0).pieces().stream().allMatch(piece -> piece.priority()));
        assertTrue(
                "Turtloid shot frame pieces should use palette line +0 from mappings",
                turtloid.getFrame(4).pieces().stream().allMatch(piece -> piece.paletteIndex() == 0));
        assertTrue(
                "Turtloid shot frame pieces should preserve priority flag from mappings",
                turtloid.getFrame(4).pieces().stream().allMatch(piece -> piece.priority()));
    }
}
