package com.openggf.tests;

import org.junit.Test;
import com.openggf.tools.KosinskiReader;
import com.openggf.tests.rules.RomRequirementTracker;
import com.openggf.tests.rules.SonicGame;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.*;

/**
 * Tests for Kosinski Moduled (KosM) decompression.
 * Uses binary data files from the skdisasm disassembly if available.
 */
public class TestKosinskiModuled {

    private static final String SKDISASM_PATH = "docs/skdisasm";

    // KosM compressed files from skdisasm (AIZ Act 1 tiles)
    private static final String AIZ1_PRIMARY_KOSM = SKDISASM_PATH + "/Levels/AIZ/Tiles/Act 1 Primary.bin";
    private static final String AIZ1_MAIN_LEVEL_KOSM = SKDISASM_PATH + "/Levels/AIZ/Tiles/Act 1 Main Level.bin";
    private static final String AIZ1_SECONDARY_KOSM = SKDISASM_PATH + "/Levels/AIZ/Tiles/Act 1 Secondary.bin";

    @Test
    public void testDecompressAiz1Primary() throws Exception {
        byte[] compressed = readKosmFile(AIZ1_PRIMARY_KOSM);
        assertTrue("File should have data", compressed.length > 2);

        // Read expected size from big-endian header
        int expectedSize = ((compressed[0] & 0xFF) << 8) | (compressed[1] & 0xFF);
        assertTrue("Header size should be positive", expectedSize > 0);

        byte[] decompressed = KosinskiReader.decompressModuled(compressed, 0);

        assertEquals("Decompressed size should match header", expectedSize, decompressed.length);
        System.out.printf("AIZ1 Primary: %d bytes compressed -> %d bytes decompressed (header: 0x%04X)%n",
                compressed.length, decompressed.length, expectedSize);
    }

    @Test
    public void testDecompressAiz1MainLevel() throws Exception {
        byte[] compressed = readKosmFile(AIZ1_MAIN_LEVEL_KOSM);
        int expectedSize = ((compressed[0] & 0xFF) << 8) | (compressed[1] & 0xFF);

        byte[] decompressed = KosinskiReader.decompressModuled(compressed, 0);

        assertEquals("Decompressed size should match header", expectedSize, decompressed.length);
        System.out.printf("AIZ1 Main Level: %d bytes compressed -> %d bytes decompressed (header: 0x%04X)%n",
                compressed.length, decompressed.length, expectedSize);
    }

    @Test
    public void testDecompressAiz1Secondary() throws Exception {
        byte[] compressed = readKosmFile(AIZ1_SECONDARY_KOSM);
        int expectedSize = ((compressed[0] & 0xFF) << 8) | (compressed[1] & 0xFF);

        byte[] decompressed = KosinskiReader.decompressModuled(compressed, 0);

        assertEquals("Decompressed size should match header", expectedSize, decompressed.length);
        System.out.printf("AIZ1 Secondary: %d bytes compressed -> %d bytes decompressed (header: 0x%04X)%n",
                compressed.length, decompressed.length, expectedSize);
    }

    @Test
    public void testDecompressWithOffset() throws Exception {
        byte[] compressed = readKosmFile(AIZ1_PRIMARY_KOSM);

        // Wrap the data in a larger array with an offset
        byte[] padded = new byte[compressed.length + 100];
        System.arraycopy(compressed, 0, padded, 50, compressed.length);

        byte[] decompressed = KosinskiReader.decompressModuled(padded, 50);

        int expectedSize = ((compressed[0] & 0xFF) << 8) | (compressed[1] & 0xFF);
        assertEquals("Decompressed size should match header", expectedSize, decompressed.length);
    }

    @Test
    public void testMultipleModules() throws Exception {
        // AIZ1 Main Level is large enough to have multiple modules (0x1000 bytes each)
        byte[] compressed = readKosmFile(AIZ1_MAIN_LEVEL_KOSM);
        int expectedSize = ((compressed[0] & 0xFF) << 8) | (compressed[1] & 0xFF);

        // Expected size > 0x1000 means multiple modules
        assertTrue("Expected multi-module data (size > 0x1000)", expectedSize > 0x1000);

        byte[] decompressed = KosinskiReader.decompressModuled(compressed, 0);
        assertEquals("Decompressed size should match header", expectedSize, decompressed.length);

        int moduleCount = (expectedSize + 0xFFF) / 0x1000;
        System.out.printf("AIZ1 Main Level: %d modules (0x%04X = %d bytes decompressed)%n",
                moduleCount, expectedSize, expectedSize);
    }

    @Test
    public void testEmptyHeader() throws Exception {
        // A header of 0x0000 should return empty array
        byte[] data = {0x00, 0x00};
        byte[] result = KosinskiReader.decompressModuled(data, 0);
        assertEquals("Empty header should produce empty output", 0, result.length);
    }

    @Test(expected = java.io.IOException.class)
    public void testTruncatedHeader() throws Exception {
        // Only 1 byte - not enough for the header
        byte[] data = {0x10};
        KosinskiReader.decompressModuled(data, 0);
    }

    private static byte[] readKosmFile(String path) throws Exception {
        File kosmFile = new File(path);
        RomRequirementTracker.requireDependencyOrSkip(
                SonicGame.SONIC_3K,
                kosmFile.exists(),
                "skdisasm not available at " + kosmFile.getPath());
        return Files.readAllBytes(kosmFile.toPath());
    }
}
