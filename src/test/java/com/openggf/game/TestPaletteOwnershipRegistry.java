package com.openggf.game;

import com.openggf.game.palette.PaletteOwnershipRegistry;
import com.openggf.game.palette.PaletteSurface;
import com.openggf.game.palette.PaletteWrite;
import com.openggf.level.Palette;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestPaletteOwnershipRegistry {

    @Test
    void higherPriorityWriteOverridesOverlappingColorsOnly() {
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] normal = blankPalettes();

        registry.beginFrame();
        registry.submit(PaletteWrite.normal("zone.low", 100, 3, 1, new byte[] {
                0x00, 0x22, 0x00, 0x44
        }));
        registry.submit(PaletteWrite.normal("zone.high", 200, 3, 2, new byte[] {
                0x00, 0x66
        }));

        registry.resolveInto(normal, null, null, null);

        assertColorWord(normal[3], 1, 0x0022);
        assertColorWord(normal[3], 2, 0x0066);
    }

    @Test
    void mirrorWriteCopiesToUnderwaterSurface() {
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] normal = blankPalettes();
        Palette[] underwater = blankPalettes();

        registry.beginFrame();
        registry.submit(PaletteWrite.normal("hcz.water", 100, 2, 3, new byte[] {
                0x00, 0x22, 0x00, 0x44, 0x00, 0x66, 0x00, (byte) 0x88
        }).mirrorToUnderwater());

        registry.resolveInto(normal, underwater, null, null);

        assertColorWord(normal[2], 3, 0x0022);
        assertColorWord(normal[2], 6, 0x0088);
        assertColorWord(underwater[2], 3, 0x0022);
        assertColorWord(underwater[2], 6, 0x0088);
    }

    @Test
    void beginFrameClearsPreviousClaims() {
        PaletteOwnershipRegistry registry = new PaletteOwnershipRegistry();
        Palette[] normal = blankPalettes();

        registry.beginFrame();
        registry.submit(PaletteWrite.normal("frame.one", 100, 1, 0, new byte[] { 0x00, 0x0E }));
        registry.resolveInto(normal, null, null, null);
        assertColorWord(normal[1], 0, 0x000E);

        registry.beginFrame();
        registry.resolveInto(normal, null, null, null);
        assertColorWord(normal[1], 0, 0x000E);
        assertEquals("none", registry.ownerAt(PaletteSurface.NORMAL, 1, 0));
    }

    private static Palette[] blankPalettes() {
        Palette[] palettes = new Palette[4];
        for (int i = 0; i < palettes.length; i++) {
            palettes[i] = new Palette();
        }
        return palettes;
    }

    private static void assertColorWord(Palette palette, int colorIndex, int segaWord) {
        // Convert the segaWord to big-endian bytes and use the same logic as Palette.Color.fromSegaFormat
        byte highByte = (byte) ((segaWord >> 8) & 0xFF);
        byte lowByte = (byte) (segaWord & 0xFF);
        int r3 = (lowByte >> 1) & 0x07;
        int g3 = (lowByte >> 5) & 0x07;
        int b3 = (highByte >> 1) & 0x07;
        int expectedR = (r3 * 255 + 3) / 7;
        int expectedG = (g3 * 255 + 3) / 7;
        int expectedB = (b3 * 255 + 3) / 7;
        assertEquals(expectedR, palette.getColor(colorIndex).r & 0xFF,
                "Red for color " + colorIndex);
        assertEquals(expectedG, palette.getColor(colorIndex).g & 0xFF,
                "Green for color " + colorIndex);
        assertEquals(expectedB, palette.getColor(colorIndex).b & 0xFF,
                "Blue for color " + colorIndex);
    }
}
