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
import java.util.Comparator;
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
    private static final List<GenesisColour> NATIVE_RAMP = List.of(
            GenesisColour.fromGenesisWord(0x0EEE),
            GenesisColour.fromGenesisWord(0x0AAA),
            GenesisColour.fromGenesisWord(0x00E4),
            GenesisColour.fromGenesisWord(0x0080),
            GenesisColour.fromGenesisWord(0x00AE),
            GenesisColour.fromGenesisWord(0x006E),
            GenesisColour.fromGenesisWord(0x000E),
            GenesisColour.fromGenesisWord(0x044E),
            GenesisColour.fromGenesisWord(0x0EE4),
            GenesisColour.fromGenesisWord(0x0E60),
            GenesisColour.fromGenesisWord(0x0E00),
            GenesisColour.fromGenesisWord(0x0C8E),
            GenesisColour.fromGenesisWord(0x0E4E),
            GenesisColour.fromGenesisWord(0x0C08),
            GenesisColour.fromGenesisWord(0x0444));

    private HostEmeraldPaletteBuilder() {
    }

    /**
     * Builds emerald palette bytes for donated save-card rendering.
     *
     * <p>This entrypoint preserves the older direct host-colour donation path so tests can
     * distinguish it from the newer retint-based presentation seam. Unsupported hosts or
     * extraction failures return an empty array so the renderer can fall back to the native
     * S3K emerald palette.</p>
     */
    static byte[] buildForHostGame(String hostGameCode, Rom hostRom) {
        if (hostRom == null || hostGameCode == null || hostGameCode.isBlank()) {
            return new byte[0];
        }
        try {
            return switch (hostGameCode) {
                case "s1" -> buildLegacyS1Palette(hostRom);
                case "s2" -> buildLegacyS2Palette(hostRom);
                default -> new byte[0];
            };
        } catch (IOException | IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static byte[] buildLegacyS1Palette(Rom rom) throws IOException {
        Sonic1ObjectArt art = new Sonic1ObjectArt(rom, RomByteReader.fromRom(rom));
        Pattern[] patterns = art.loadNemesisPatterns(Sonic1Constants.ART_NEM_SS_RESULT_EM_ADDR);
        byte[] paletteBytes = new Sonic1SpecialStageDataLoader(rom).getSSPalette();
        if (patterns.length == 0 || paletteBytes.length == 0) {
            return new byte[0];
        }

        List<int[]> shades = new ArrayList<>(6);
        shades.add(extractLegacyPatternShades(patterns, paletteBytes, 4, 1));
        shades.add(extractLegacyPatternShades(patterns, paletteBytes, 0, 0));
        shades.add(extractLegacyPatternShades(patterns, paletteBytes, 4, 2));
        shades.add(extractLegacyPatternShades(patterns, paletteBytes, 4, 3));
        shades.add(extractLegacyPatternShades(patterns, paletteBytes, 8, 1));
        shades.add(extractLegacyPatternShades(patterns, paletteBytes, 12, 1));
        return composeLegacyPaletteBytes(shades);
    }

    private static byte[] buildLegacyS2Palette(Rom rom) throws IOException {
        byte[] raw = rom.readBytes(Sonic2SpecialStageConstants.PALETTE_EMERALD_OFFSET,
                Sonic2SpecialStageConstants.PALETTE_EMERALD_SIZE);
        if (raw.length < Sonic2SpecialStageConstants.PALETTE_EMERALD_SIZE) {
            return new byte[0];
        }

        List<int[]> shades = new ArrayList<>(S3K_SAVE_CARD_EMERALD_COUNT);
        for (int emeraldIndex = 0; emeraldIndex < S3K_SAVE_CARD_EMERALD_COUNT; emeraldIndex++) {
            int byteOffset = emeraldIndex * 6;
            int[] colours = new int[]{
                    readGenesisWord(raw, byteOffset),
                    readGenesisWord(raw, byteOffset + 2),
                    readGenesisWord(raw, byteOffset + 4)
            };
            sortByBrightnessDescending(colours);
            shades.add(selectLegacyTwoShades(colours));
        }
        return composeLegacyPaletteBytes(shades);
    }

    static List<GenesisColour> extractS1HostTargets(Rom rom) throws IOException {
        Sonic1ObjectArt art = new Sonic1ObjectArt(rom, RomByteReader.fromRom(rom));
        Pattern[] patterns = art.loadNemesisPatterns(Sonic1Constants.ART_NEM_SS_RESULT_EM_ADDR);
        byte[] paletteBytes = new Sonic1SpecialStageDataLoader(rom).getSSPalette();
        if (patterns.length == 0 || paletteBytes.length == 0) {
            return List.of();
        }

        List<GenesisColour> targets = new ArrayList<>(6);
        targets.add(extractPatternTarget(patterns, paletteBytes, 4, 1));
        targets.add(extractPatternTarget(patterns, paletteBytes, 0, 0));
        targets.add(extractPatternTarget(patterns, paletteBytes, 4, 2));
        targets.add(extractPatternTarget(patterns, paletteBytes, 4, 3));
        targets.add(extractPatternTarget(patterns, paletteBytes, 8, 1));
        targets.add(extractPatternTarget(patterns, paletteBytes, 12, 1));
        return List.copyOf(targets);
    }

    static List<GenesisColour> extractS2HostTargets(Rom rom) throws IOException {
        byte[] raw = rom.readBytes(Sonic2SpecialStageConstants.PALETTE_EMERALD_OFFSET,
                Sonic2SpecialStageConstants.PALETTE_EMERALD_SIZE);
        if (raw.length < Sonic2SpecialStageConstants.PALETTE_EMERALD_SIZE) {
            return List.of();
        }

        List<GenesisColour> targets = new ArrayList<>(S3K_SAVE_CARD_EMERALD_COUNT);
        for (int emeraldIndex = 0; emeraldIndex < S3K_SAVE_CARD_EMERALD_COUNT; emeraldIndex++) {
            int byteOffset = emeraldIndex * 6;
            targets.add(selectRepresentativeColor(List.of(
                    GenesisColour.fromGenesisWord(readGenesisWord(raw, byteOffset)),
                    GenesisColour.fromGenesisWord(readGenesisWord(raw, byteOffset + 2)),
                    GenesisColour.fromGenesisWord(readGenesisWord(raw, byteOffset + 4)))));
        }
        return List.copyOf(targets);
    }

    static List<GenesisColour> nativeRamp() {
        return NATIVE_RAMP;
    }

    static byte[] composeRetintedPaletteBytes(List<GenesisColour> hostTargets, List<GenesisColour> nativeRamp) {
        if (nativeRamp == null || nativeRamp.size() < (S3K_SAVE_CARD_EMERALD_COUNT * COLORS_PER_EMERALD) + 1) {
            throw new IllegalArgumentException("nativeRamp must contain 15 colors");
        }
        List<GenesisColour> targets = hostTargets != null ? hostTargets : List.of();
        byte[] bytes = new byte[S3K_SAVE_CARD_EMERALD_COUNT * COLORS_PER_EMERALD * 2 + 2];
        int writeOffset = 0;
        for (int emeraldIndex = 0; emeraldIndex < S3K_SAVE_CARD_EMERALD_COUNT; emeraldIndex++) {
            GenesisColour target = emeraldIndex < targets.size()
                    ? targets.get(emeraldIndex)
                    : nativeRamp.get(emeraldIndex * 2);
            GenesisColour highlight = retint(nativeRamp.get(emeraldIndex * 2), target);
            GenesisColour shadow = retint(nativeRamp.get(emeraldIndex * 2 + 1), target);
            writeGenesisWord(bytes, writeOffset, highlight.toGenesisWord());
            writeGenesisWord(bytes, writeOffset + 2, shadow.toGenesisWord());
            writeOffset += 4;
        }
        writeGenesisWord(bytes, writeOffset, nativeRamp.get(nativeRamp.size() - 1).toGenesisWord());
        return bytes;
    }

    private static GenesisColour extractPatternTarget(Pattern[] patterns,
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

        List<WeightedColor> colors = new ArrayList<>();
        for (int colorIndex = 1; colorIndex < usage.length; colorIndex++) {
            if (usage[colorIndex] > 0) {
                colors.add(new WeightedColor(
                        GenesisColour.fromGenesisWord(readPaletteColor(paletteBytes, paletteLine, colorIndex)),
                        usage[colorIndex]));
            }
        }
        return selectWeightedRepresentativeColor(colors);
    }

    private static int[] extractLegacyPatternShades(Pattern[] patterns,
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
                    int colourIndex = pattern.getPixel(x, y) & 0x0F;
                    if (colourIndex != 0) {
                        usage[colourIndex]++;
                    }
                }
            }
        }

        List<Integer> colours = new ArrayList<>();
        for (int colourIndex = 1; colourIndex < usage.length; colourIndex++) {
            if (usage[colourIndex] > 0) {
                colours.add(readPaletteColor(paletteBytes, paletteLine, colourIndex));
            }
        }
        if (colours.isEmpty()) {
            return new int[]{0, 0};
        }
        int[] uniqueColours = colours.stream().distinct().mapToInt(Integer::intValue).toArray();
        sortByBrightnessDescending(uniqueColours);
        return selectLegacyTwoShades(uniqueColours);
    }

    private static GenesisColour selectRepresentativeColor(List<GenesisColour> colors) {
        List<WeightedColor> weighted = colors.stream()
                .map(color -> new WeightedColor(color, 1))
                .toList();
        return selectWeightedRepresentativeColor(weighted);
    }

    private static GenesisColour selectWeightedRepresentativeColor(List<WeightedColor> colors) {
        if (colors.isEmpty()) {
            return GenesisColour.black();
        }
        return colors.stream()
                .max(Comparator.comparingInt(WeightedColor::score)
                        .thenComparingInt(weighted -> weighted.color().brightness()))
                .orElseThrow()
                .color();
    }

    private static GenesisColour retint(GenesisColour nativeShade, GenesisColour target) {
        if (target == null || target.value() <= 0.0f) {
            return nativeShade;
        }
        float hue = target.saturation() > 0.0f ? target.hue() : nativeShade.hue();
        float saturation = target.saturation();
        return GenesisColour.fromHsv(hue, saturation, nativeShade.value());
    }

    private static int[] selectLegacyTwoShades(int[] colours) {
        if (colours.length == 0) {
            return new int[]{0, 0};
        }
        int primary = colours[0];
        int secondary = colours.length > 1 ? colours[1] : darken(primary);
        return new int[]{primary, secondary};
    }

    private static byte[] composeLegacyPaletteBytes(List<int[]> shades) {
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

    private static int darken(int colour) {
        int r = Math.max(0, ((colour >> 1) & 0x7) - 2);
        int g = Math.max(0, ((colour >> 5) & 0x7) - 2);
        int b = Math.max(0, ((colour >> 9) & 0x7) - 2);
        return (b << 9) | (g << 5) | (r << 1);
    }

    private static void sortByBrightnessDescending(int[] colours) {
        for (int i = 0; i < colours.length - 1; i++) {
            for (int j = i + 1; j < colours.length; j++) {
                if (brightness(colours[j]) > brightness(colours[i])) {
                    int swap = colours[i];
                    colours[i] = colours[j];
                    colours[j] = swap;
                }
            }
        }
    }

    private static int brightness(int colour) {
        return ((colour >> 1) & 0x7) + ((colour >> 5) & 0x7) + ((colour >> 9) & 0x7);
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

    private record WeightedColor(GenesisColour color, int usage) {
        private int score() {
            return (color.chroma() * 100) + (usage * 10) + color.brightness();
        }
    }

    static final class GenesisColour {
        private final int r;
        private final int g;
        private final int b;

        private GenesisColour(int r, int g, int b) {
            this.r = clampChannel(r);
            this.g = clampChannel(g);
            this.b = clampChannel(b);
        }

        static GenesisColour black() {
            return new GenesisColour(0, 0, 0);
        }

        static GenesisColour fromGenesisWord(int word) {
            return new GenesisColour((word >> 1) & 0x7, (word >> 5) & 0x7, (word >> 9) & 0x7);
        }

        static GenesisColour fromHsv(float hue, float saturation, float value) {
            float h = normalizeHue(hue);
            float s = clampUnit(saturation);
            float v = clampUnit(value);
            if (s == 0.0f) {
                int channel = Math.round(v * 7.0f);
                return new GenesisColour(channel, channel, channel);
            }

            float scaledHue = h * 6.0f;
            int sector = (int) Math.floor(scaledHue);
            float fraction = scaledHue - sector;
            float p = v * (1.0f - s);
            float q = v * (1.0f - (s * fraction));
            float t = v * (1.0f - (s * (1.0f - fraction)));

            return switch (sector % 6) {
                case 0 -> fromUnitRgb(v, t, p);
                case 1 -> fromUnitRgb(q, v, p);
                case 2 -> fromUnitRgb(p, v, t);
                case 3 -> fromUnitRgb(p, q, v);
                case 4 -> fromUnitRgb(t, p, v);
                default -> fromUnitRgb(v, p, q);
            };
        }

        private static GenesisColour fromUnitRgb(float r, float g, float b) {
            return new GenesisColour(Math.round(clampUnit(r) * 7.0f),
                    Math.round(clampUnit(g) * 7.0f),
                    Math.round(clampUnit(b) * 7.0f));
        }

        int toGenesisWord() {
            return (b << 9) | (g << 5) | (r << 1);
        }

        int brightness() {
            return r + g + b;
        }

        int chroma() {
            return maxChannel() - minChannel();
        }

        float hue() {
            float max = maxChannel() / 7.0f;
            float min = minChannel() / 7.0f;
            float delta = max - min;
            if (delta == 0.0f) {
                return 0.0f;
            }

            float rf = r / 7.0f;
            float gf = g / 7.0f;
            float bf = b / 7.0f;
            float hue;
            if (max == rf) {
                hue = ((gf - bf) / delta) % 6.0f;
            } else if (max == gf) {
                hue = ((bf - rf) / delta) + 2.0f;
            } else {
                hue = ((rf - gf) / delta) + 4.0f;
            }
            hue /= 6.0f;
            return normalizeHue(hue);
        }

        float saturation() {
            float max = maxChannel() / 7.0f;
            float min = minChannel() / 7.0f;
            if (max == 0.0f) {
                return 0.0f;
            }
            return (max - min) / max;
        }

        float value() {
            return maxChannel() / 7.0f;
        }

        private int maxChannel() {
            return Math.max(r, Math.max(g, b));
        }

        private int minChannel() {
            return Math.min(r, Math.min(g, b));
        }

        private static int clampChannel(int value) {
            return Math.max(0, Math.min(7, value));
        }

        private static float clampUnit(float value) {
            return Math.max(0.0f, Math.min(1.0f, value));
        }

        private static float normalizeHue(float hue) {
            float normalized = hue % 1.0f;
            return normalized < 0.0f ? normalized + 1.0f : normalized;
        }
    }
}
