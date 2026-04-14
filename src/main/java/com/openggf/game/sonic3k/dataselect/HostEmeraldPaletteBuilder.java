package com.openggf.game.sonic3k.dataselect;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic1.Sonic1ObjectArt;
import com.openggf.game.sonic1.constants.Sonic1Constants;
import com.openggf.game.sonic1.specialstage.Sonic1SpecialStageDataLoader;
import com.openggf.game.sonic2.specialstage.Sonic2SpecialStageConstants;
import com.openggf.level.Pattern;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapts host-game emerald colors to the S3K save-card emerald palette contract.
 *
 * <p>The donated save screen still renders the native S3K emerald mappings and layout.
 * Host games therefore cannot provide an arbitrary raw palette line here, because the
 * S3K save-card renderer expects line-2 color 0 to stay reserved for the character art
 * and expects emerald shades to begin at color 1 in S3K's ordering.</p>
 *
 * <p>This builder extracts representative host emerald shades and repacks them into the
 * byte layout consumed by {@link S3kDataSelectRenderer}. That is an adaptation layer,
 * not a guarantee that host palette slots are directly interchangeable with S3K's.</p>
 */
final class HostEmeraldPaletteBuilder {
    private static final int S3K_SAVE_CARD_EMERALD_COUNT = 7;
    private static final int COLORS_PER_EMERALD = 2;

    private HostEmeraldPaletteBuilder() {
    }

    /**
     * Builds emerald palette bytes for donated save-card rendering.
     *
     * <p>The returned data is already normalized into the S3K save-card overlay format:
     * two colors per emerald, packed for insertion starting at palette-line color 1.
     * Unsupported hosts or extraction failures return an empty array so the renderer
     * can fall back to the native S3K emerald palette.</p>
     */
    static byte[] buildForHostGame(String hostGameCode, Rom hostRom) {
        if (hostRom == null || hostGameCode == null || hostGameCode.isBlank()) {
            return new byte[0];
        }
        try {
            return switch (hostGameCode) {
                case "s1" -> buildS1Palette(hostRom);
                case "s2" -> buildS2Palette(hostRom);
                default -> new byte[0];
            };
        } catch (IOException | IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static byte[] buildS1Palette(Rom rom) throws IOException {
        Sonic1ObjectArt art = new Sonic1ObjectArt(rom, RomByteReader.fromRom(rom));
        Pattern[] patterns = art.loadNemesisPatterns(Sonic1Constants.ART_NEM_SS_RESULT_EM_ADDR);
        byte[] paletteBytes = new Sonic1SpecialStageDataLoader(rom).getSSPalette();
        if (patterns.length == 0 || paletteBytes.length == 0) {
            return new byte[0];
        }

        List<int[]> shades = new ArrayList<>(6);
        shades.add(extractPatternShades(patterns, paletteBytes, 4, 1));
        shades.add(extractPatternShades(patterns, paletteBytes, 0, 0));
        shades.add(extractPatternShades(patterns, paletteBytes, 4, 2));
        shades.add(extractPatternShades(patterns, paletteBytes, 4, 3));
        shades.add(extractPatternShades(patterns, paletteBytes, 8, 1));
        shades.add(extractPatternShades(patterns, paletteBytes, 12, 1));
        return composePaletteBytes(shades);
    }

    private static byte[] buildS2Palette(Rom rom) throws IOException {
        byte[] raw = rom.readBytes(Sonic2SpecialStageConstants.PALETTE_EMERALD_OFFSET,
                Sonic2SpecialStageConstants.PALETTE_EMERALD_SIZE);
        if (raw.length < Sonic2SpecialStageConstants.PALETTE_EMERALD_SIZE) {
            return new byte[0];
        }

        List<int[]> shades = new ArrayList<>(S3K_SAVE_CARD_EMERALD_COUNT);
        for (int emeraldIndex = 0; emeraldIndex < S3K_SAVE_CARD_EMERALD_COUNT; emeraldIndex++) {
            int byteOffset = emeraldIndex * 6;
            int[] colors = new int[]{
                    readGenesisWord(raw, byteOffset),
                    readGenesisWord(raw, byteOffset + 2),
                    readGenesisWord(raw, byteOffset + 4)
            };
            sortByBrightnessDescending(colors);
            shades.add(selectTwoShades(colors));
        }
        return composePaletteBytes(shades);
    }

    private static int[] extractPatternShades(Pattern[] patterns,
                                              byte[] paletteBytes,
                                              int tileIndex,
                                              int paletteLine) {
        int[] usage = new int[16];
        for (int tileOffset = 0; tileOffset < 4; tileOffset++) {
            int patternIndex = tileIndex + tileOffset;
            if (patternIndex < 0 || patternIndex >= patterns.length) {
                continue;
            }
            Pattern pattern = patterns[patternIndex];
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    int colorIndex = pattern.getPixel(x, y) & 0x0F;
                    if (colorIndex != 0) {
                        usage[colorIndex]++;
                    }
                }
            }
        }

        List<Integer> colors = new ArrayList<>();
        for (int colorIndex = 1; colorIndex < usage.length; colorIndex++) {
            if (usage[colorIndex] > 0) {
                colors.add(readPaletteColor(paletteBytes, paletteLine, colorIndex));
            }
        }
        if (colors.isEmpty()) {
            return new int[]{0, 0};
        }
        int[] uniqueColors = colors.stream().distinct().mapToInt(Integer::intValue).toArray();
        sortByBrightnessDescending(uniqueColors);
        return selectTwoShades(uniqueColors);
    }

    private static int[] selectTwoShades(int[] colors) {
        if (colors.length == 0) {
            return new int[]{0, 0};
        }
        int primary = colors[0];
        int secondary = colors.length > 1 ? colors[1] : darken(primary);
        return new int[]{primary, secondary};
    }

    private static byte[] composePaletteBytes(List<int[]> shades) {
        byte[] bytes = new byte[S3K_SAVE_CARD_EMERALD_COUNT * COLORS_PER_EMERALD * 2 + 2];
        int writeOffset = 0;
        for (int emeraldIndex = 0; emeraldIndex < S3K_SAVE_CARD_EMERALD_COUNT; emeraldIndex++) {
            int[] emeraldShades = emeraldIndex < shades.size() ? shades.get(emeraldIndex) : new int[]{0, 0};
            writeGenesisWord(bytes, writeOffset, emeraldShades[0]);
            writeGenesisWord(bytes, writeOffset + 2, emeraldShades[1]);
            writeOffset += 4;
        }
        return bytes;
    }

    private static int readPaletteColor(byte[] paletteBytes, int paletteLine, int colorIndex) {
        int byteOffset = (paletteLine * 32) + (colorIndex * 2);
        if (byteOffset < 0 || byteOffset + 1 >= paletteBytes.length) {
            return 0;
        }
        return readGenesisWord(paletteBytes, byteOffset);
    }

    private static int readGenesisWord(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xFF) << 8) | (bytes[offset + 1] & 0xFF);
    }

    private static void writeGenesisWord(byte[] bytes, int offset, int color) {
        bytes[offset] = (byte) ((color >> 8) & 0xFF);
        bytes[offset + 1] = (byte) (color & 0xFF);
    }

    private static int darken(int color) {
        int r = Math.max(0, ((color >> 1) & 0x7) - 2);
        int g = Math.max(0, ((color >> 5) & 0x7) - 2);
        int b = Math.max(0, ((color >> 9) & 0x7) - 2);
        return (b << 9) | (g << 5) | (r << 1);
    }

    private static void sortByBrightnessDescending(int[] colors) {
        for (int i = 0; i < colors.length - 1; i++) {
            for (int j = i + 1; j < colors.length; j++) {
                if (brightness(colors[j]) > brightness(colors[i])) {
                    int swap = colors[i];
                    colors[i] = colors[j];
                    colors[j] = swap;
                }
            }
        }
    }

    private static int brightness(int color) {
        return ((color >> 1) & 0x7) + ((color >> 5) & 0x7) + ((color >> 9) & 0x7);
    }
}
