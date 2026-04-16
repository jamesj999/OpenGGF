package com.openggf.game.sonic3k;

import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class TestSonic3kLivesHudPaletteOverride {

    @Test
    void buildS3kLivesHudPaletteOverride_usesHudTextColoursForLifeName() {
        Palette iconPalette = new Palette();
        setColor(iconPalette, 4, 36, 36, 146);
        setColor(iconPalette, 5, 0, 146, 0);
        setColor(iconPalette, 14, 109, 109, 146);

        Palette hudTextPalette = new Palette();
        setColor(hudTextPalette, 5, 255, 182, 0);
        setColor(hudTextPalette, 14, 73, 73, 73);

        Palette override = Sonic3kObjectArtProvider.buildS3kLivesHudPaletteOverride(iconPalette, hudTextPalette);

        assertNotSame(iconPalette, override);
        assertColor(override, 4, 36, 36, 146);
        assertColor(override, 5, 255, 182, 0);
        assertColor(override, 14, 73, 73, 73);
    }

    private static void setColor(Palette palette, int index, int r, int g, int b) {
        Palette.Color color = palette.getColor(index);
        color.r = (byte) r;
        color.g = (byte) g;
        color.b = (byte) b;
    }

    private static void assertColor(Palette palette, int index, int r, int g, int b) {
        Palette.Color color = palette.getColor(index);
        assertEquals(r, color.r & 0xFF);
        assertEquals(g, color.g & 0xFF);
        assertEquals(b, color.b & 0xFF);
    }
}
