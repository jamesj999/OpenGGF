package com.openggf.data;

import com.openggf.level.Palette;

import java.io.IOException;

/**
 * Utility for loading Mega Drive palette data from ROM or raw byte arrays.
 * <p>
 * A single palette line is 32 bytes (16 colors x 2 bytes each in Sega format).
 * A full palette is 4 lines = 128 bytes.
 */
public final class PaletteLoader {
    private static final int PALETTE_LINE_SIZE = 32; // 16 colors x 2 bytes
    private static final int FULL_PALETTE_SIZE = PALETTE_LINE_SIZE * 4;

    private PaletteLoader() {}

    /**
     * Load 4 palette lines (128 bytes) from ROM at the given address.
     */
    public static Palette[] loadFullPalette(Rom rom, int address) throws IOException {
        byte[] data = rom.readBytes(address, FULL_PALETTE_SIZE);
        return fromBytes(data);
    }

    /**
     * Load 4 palette lines from a raw byte array (128 bytes).
     * If the array is shorter than 128 bytes, remaining lines are blank.
     */
    public static Palette[] fromBytes(byte[] paletteData) {
        int lineCount = paletteData.length / PALETTE_LINE_SIZE;
        if (lineCount > 4) {
            lineCount = 4;
        }
        Palette[] palettes = new Palette[4];
        for (int i = 0; i < 4; i++) {
            palettes[i] = new Palette();
            if (i < lineCount) {
                byte[] lineData = new byte[PALETTE_LINE_SIZE];
                System.arraycopy(paletteData, i * PALETTE_LINE_SIZE, lineData, 0, PALETTE_LINE_SIZE);
                palettes[i].fromSegaFormat(lineData);
            }
        }
        return palettes;
    }

    /**
     * Load a single palette line (32 bytes) from ROM.
     */
    public static Palette loadPaletteLine(Rom rom, int address) throws IOException {
        byte[] data = rom.readBytes(address, PALETTE_LINE_SIZE);
        return fromLineBytes(data);
    }

    /**
     * Load a single palette line from raw bytes.
     */
    public static Palette fromLineBytes(byte[] lineData) {
        Palette palette = new Palette();
        palette.fromSegaFormat(lineData);
        return palette;
    }
}
