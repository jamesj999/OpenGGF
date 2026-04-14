package com.openggf.game.sonic3k.dataselect;

import com.openggf.data.Rom;
import com.openggf.data.RomByteReader;
import com.openggf.game.sonic3k.audio.Sonic3kMusic;
import com.openggf.game.sonic3k.constants.Sonic3kConstants;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.tests.RomTestUtils;
import com.openggf.tools.EnigmaReader;
import com.openggf.tools.KosinskiReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.Channels;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TestS3kDataSelectDataLoader {
    private static final Path SAVE_MENU_DIR = resolveSaveMenuReferenceDir();

    @Test
    void loader_rejectsOddByteWordPayloads() throws Exception {
        S3kDataSelectDataLoader loader = new S3kDataSelectDataLoader(new RomByteReader(new byte[0]));
        Method method = S3kDataSelectDataLoader.class.getDeclaredMethod("wordsFromBytes", byte[].class);
        method.setAccessible(true);

        InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> method.invoke(loader, (Object) new byte[]{0x12, 0x34, 0x56}));

        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("odd"));
    }

    @Test
    void copyRegion_readsRowsFromPackedSourceBlockStride() throws Exception {
        S3kDataSelectDataLoader loader = new S3kDataSelectDataLoader(new RomByteReader(new byte[0]));
        Method method = S3kDataSelectDataLoader.class.getDeclaredMethod(
                "copyRegion", int[].class, int.class, int.class, int.class, int.class, int[].class);
        method.setAccessible(true);

        int[] sourceWords = new int[18];
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 3; col++) {
                sourceWords[row * 3 + col] = (row << 8) | col;
            }
        }
        int[] planeWords = new int[128 * 32];

        method.invoke(loader, sourceWords, (2 * 2), 0x0102, 3, 2, planeWords);

        int firstRowDestIndex = 0x0102 / 2;
        int secondRowDestIndex = firstRowDestIndex + 128;
        assertArrayEquals(new int[]{0x0002, 0x0100, 0x0101},
                java.util.Arrays.copyOfRange(planeWords, firstRowDestIndex, firstRowDestIndex + 3));
        assertArrayEquals(new int[]{0x0102, 0x0200, 0x0201},
                java.util.Arrays.copyOfRange(planeWords, secondRowDestIndex, secondRowDestIndex + 3));
    }

    @Test
    void layoutOriginal_matchesDisassemblyWorldCoordinates() {
        S3kDataSelectLayout layout = S3kDataSelectLayout.original();

        assertEquals(0xB0, layout.noSaveWorldX());
        assertEquals(0x448, layout.deleteWorldX());
        assertEquals(0x110, layout.slotWorldXStart());
        assertEquals(0x68, layout.slotWorldXStep());
        assertEquals(0x108, layout.slotWorldY());
        assertEquals(0x110, layout.slotWorldX(0));
        assertEquals(0x178, layout.slotWorldX(1));
    }

    @Test
    void saveScreenLayoutEnigmaBase_matchesDisassemblyPriorityBit() {
        assertEquals(Sonic3kConstants.ARTTILE_SAVE_MISC | 0x8000,
                Sonic3kConstants.ENIGMA_BASE_SAVE_SCREEN_LAYOUT);
    }

    @Test
    void loader_readsSaveScreenAssetsAndMusicMetadata_fromRealS3kRom() throws Exception {
        File romFile = RomTestUtils.ensureSonic3kRomAvailable();
        assumeTrue(romFile != null, "S3K ROM not available");
        assumeTrue(Files.isDirectory(SAVE_MENU_DIR), "Save Menu reference assets not available");

        try (Rom rom = new Rom()) {
            assertTrue(rom.open(romFile.getPath()), "Failed to open S3K ROM");

            S3kDataSelectDataLoader loader = new S3kDataSelectDataLoader(RomByteReader.fromRom(rom));
            loader.loadData();

            assertArrayEquals(
                    decompressEnigmaWords("Enigma Map/Save Screen Layout.eni",
                            Sonic3kConstants.ENIGMA_BASE_SAVE_SCREEN_LAYOUT),
                    loader.getLayoutWords());
            assertArrayEquals(readWords("Uncompressed Map/NEW.bin"), loader.getNewLayoutWords());
            assertArrayEquals(
                    decompressEnigmaWords("Enigma Map/Menu BG.eni",
                            Sonic3kConstants.ENIGMA_BASE_S3_MENU_BG),
                    loader.getMenuBackgroundLayoutWords());

            assertPatternsEqual(decompressKosinskiPatterns("Kosinski Art/Menu BG.bin"), loader.getMenuBackgroundPatterns());
            assertPatternsEqual(decompressKosinskiPatterns("Kosinski Art/Misc.bin"), loader.getMiscPatterns());
            assertPatternsEqual(decompressKosinskiPatterns("Kosinski Art/SK Extra.bin"), loader.getExtraPatterns());
            assertPatternsEqual(decompressKosinskiPatterns("Kosinski Art/SK Zone Art.bin"), loader.getSkZonePatterns());
            assertPatternsEqual(decompressKosinskiPatterns("Kosinski Art/Portraits.bin"), loader.getPortraitPatterns());
            assertPatternsEqual(decompressKosinskiPatterns("Kosinski Art/Zone Art.bin"), loader.getS3ZonePatterns());
            assertEquals(70, loader.getSlotIconPatterns(0).length);
            assertEquals(70, loader.getSlotIconPatterns(14).length);

            assertMappingsEqual(
                    parseSaveScreenMappingsAsm("Map - Save Screen General.asm"),
                    loader.getSaveScreenMappings());

            assertArrayEquals(readBytes("Palettes/BG.bin"), loader.getMenuBackgroundPaletteBytes());
            assertArrayEquals(readBytes("Palettes/Chars.bin"), loader.getCharacterPaletteBytes());
            assertArrayEquals(readBytes("Palettes/Emeralds.bin"), loader.getEmeraldPaletteBytes());
            assertEquals(3, loader.getFinishCardPalettes().length);
            assertArrayEquals(readBytes("Palettes/Finish Card 1.bin"), loader.getFinishCardPalettes()[0]);
            assertArrayEquals(readBytes("Palettes/Finish Card 2.bin"), loader.getFinishCardPalettes()[1]);
            assertArrayEquals(readBytes("Palettes/Finish Card 3.bin"), loader.getFinishCardPalettes()[2]);
            assertEquals(15, loader.getZoneCardPalettes().length);
            String[] zoneCardNames = {
                    "Zone Card 1.bin", "Zone Card 2.bin", "Zone Card 3.bin", "Zone Card 4.bin", "Zone Card 5.bin",
                    "Zone Card 6.bin", "Zone Card 7.bin", "Zone Card 8.bin", "Zone Card 9.bin", "Zone Card A.bin",
                    "Zone Card B.bin", "Zone Card C.bin", "Zone Card D.bin", "Zone Card E.bin", "Zone Card F.bin"
            };
            for (int i = 0; i < zoneCardNames.length; i++) {
                assertArrayEquals(readBytes("Palettes/" + zoneCardNames[i]), loader.getZoneCardPalettes()[i]);
            }
            assertArrayEquals(readBytes("Palettes/Zone Card 8 S3.bin"), loader.getS3ZoneCard8PaletteBytes());
            assertEquals(4, loader.getStaticLayouts().length);
            assertArrayEquals(readWords("Uncompressed Map/Static 1.bin"), loader.getStaticLayouts()[0]);
            assertArrayEquals(readWords("Uncompressed Map/Static 2.bin"), loader.getStaticLayouts()[1]);
            assertArrayEquals(readWords("Uncompressed Map/Static 3.bin"), loader.getStaticLayouts()[2]);
            assertArrayEquals(readWords("Uncompressed Map/Static 4.bin"), loader.getStaticLayouts()[3]);
            assertEquals(Sonic3kMusic.DATA_SELECT.id, loader.getMusicId());
        }
    }

    private static void assertPatternsEqual(Pattern[] expected, Pattern[] actual) {
        assertEquals(expected.length, actual.length, "pattern count");
        for (int i = 0; i < expected.length; i++) {
            for (int y = 0; y < Pattern.PATTERN_HEIGHT; y++) {
                for (int x = 0; x < Pattern.PATTERN_WIDTH; x++) {
                    assertEquals(expected[i].getPixel(x, y), actual[i].getPixel(x, y),
                            "pattern " + i + " pixel (" + x + "," + y + ")");
                }
            }
        }
    }

    private static void assertMappingsEqual(List<SpriteMappingFrame> expected, List<SpriteMappingFrame> actual) {
        assertEquals(expected.size(), actual.size(), "mapping frame count");
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i), "mapping frame " + i);
        }
    }

    private static Pattern[] decompressKosinskiPatterns(String relativePath) throws Exception {
        byte[] compressed = readBytes(relativePath);
        try (ByteArrayInputStream input = new ByteArrayInputStream(compressed)) {
            return patternsFromSegaBytes(KosinskiReader.decompress(Channels.newChannel(input)));
        }
    }

    private static int[] decompressEnigmaWords(String relativePath, int startingArtTile) throws Exception {
        byte[] compressed = readBytes(relativePath);
        try (ByteArrayInputStream input = new ByteArrayInputStream(compressed)) {
            return wordsFromBytes(EnigmaReader.decompress(Channels.newChannel(input), startingArtTile));
        }
    }

    private static Pattern[] patternsFromSegaBytes(byte[] bytes) {
        int count = bytes.length / Pattern.PATTERN_SIZE_IN_ROM;
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            Pattern pattern = new Pattern();
            pattern.fromSegaFormat(slice(bytes, i * Pattern.PATTERN_SIZE_IN_ROM, Pattern.PATTERN_SIZE_IN_ROM));
            patterns[i] = pattern;
        }
        return patterns;
    }

    private static int[] readWords(String relativePath) throws Exception {
        return wordsFromBytes(readBytes(relativePath));
    }

    private static int[] wordsFromBytes(byte[] bytes) {
        assertEquals(0, bytes.length % 2, "reference bytes must have even length");
        int[] words = new int[bytes.length / 2];
        for (int i = 0; i < words.length; i++) {
            int base = i * 2;
            words[i] = ((bytes[base] & 0xFF) << 8) | (bytes[base + 1] & 0xFF);
        }
        return words;
    }

    private static byte[] readBytes(String relativePath) throws Exception {
        return Files.readAllBytes(SAVE_MENU_DIR.resolve(relativePath));
    }

    private static List<SpriteMappingFrame> parseSaveScreenMappingsAsm(String relativePath) throws Exception {
        List<String> lines = Files.readAllLines(SAVE_MENU_DIR.resolve(relativePath));
        List<SpriteMappingFrame> frames = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (!line.startsWith("word_") || !line.contains(":")) {
                continue;
            }
            int labelEnd = line.indexOf(':');
            String remainder = line.substring(labelEnd + 1).trim();
            if (!remainder.startsWith("dc.w")) {
                continue;
            }

            int pieceCount = parseAsmNumber(remainder.substring(4).trim());
            List<SpriteMappingPiece> pieces = new ArrayList<>(pieceCount);
            for (int p = 0; p < pieceCount; p++) {
                i++;
                String pieceLine = lines.get(i).trim();
                if (!pieceLine.startsWith("dc.b")) {
                    throw new IllegalArgumentException("Expected dc.b after frame label, got: " + pieceLine);
                }
                pieces.add(parseMappingPiece(pieceLine.substring(4).trim()));
            }
            frames.add(new SpriteMappingFrame(List.copyOf(pieces)));
        }
        return List.copyOf(frames);
    }

    private static SpriteMappingPiece parseMappingPiece(String body) {
        String[] parts = body.split(",");
        if (parts.length != 6) {
            throw new IllegalArgumentException("Expected 6 mapping bytes, got " + parts.length + ": " + body);
        }

        int yOffset = (byte) parseAsmNumber(parts[0].trim());
        int size = parseAsmNumber(parts[1].trim()) & 0xFF;
        int tileWord = ((parseAsmNumber(parts[2].trim()) & 0xFF) << 8)
                | (parseAsmNumber(parts[3].trim()) & 0xFF);
        int xOffset = (short) (((parseAsmNumber(parts[4].trim()) & 0xFF) << 8)
                | (parseAsmNumber(parts[5].trim()) & 0xFF));

        int widthTiles = ((size >> 2) & 0x3) + 1;
        int heightTiles = (size & 0x3) + 1;
        int tileIndex = tileWord & 0x7FF;
        boolean hFlip = (tileWord & 0x800) != 0;
        boolean vFlip = (tileWord & 0x1000) != 0;
        int paletteIndex = (tileWord >> 13) & 0x3;
        boolean priority = (tileWord & 0x8000) != 0;

        return new SpriteMappingPiece(
                xOffset, yOffset, widthTiles, heightTiles, tileIndex, hFlip, vFlip, paletteIndex, priority);
    }

    private static int parseAsmNumber(String token) {
        String value = token.trim();
        if (value.startsWith("$")) {
            return Integer.parseInt(value.substring(1), 16);
        }
        return Integer.parseInt(value);
    }

    private static byte[] slice(byte[] bytes, int start, int length) {
        byte[] copy = new byte[length];
        System.arraycopy(bytes, start, copy, 0, length);
        return copy;
    }

    private static Path resolveSaveMenuReferenceDir() {
        Path local = Path.of("docs", "skdisasm", "General", "Save Menu");
        if (Files.isDirectory(local)) {
            return local;
        }
        Path parentRepo = Path.of("..", "..", "docs", "skdisasm", "General", "Save Menu").normalize();
        return parentRepo;
    }
}
