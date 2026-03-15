package com.openggf.game.sonic1;

import org.junit.Assume;
import org.junit.Test;
import com.openggf.tools.EnigmaReader;
import com.openggf.tools.KosinskiReader;

import com.openggf.tools.NemesisReader;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Diagnostic test: decompress GHZ chunks and 256x256 blocks to trace waterfall tile references.
 */
public class TestGhzChunkDiagnostic {

    private static final int WATERFALL_TILE_START = 0x378;
    private static final int WATERFALL_TILE_END = 0x37F;
    // Waterfall chunk indices from Enigma decompression
    private static final Set<Integer> WATERFALL_CHUNK_INDICES = Set.of(0x1A9, 0x1AA, 0x1B5, 0x1B6);

    @Test
    public void dumpWaterfallChunkReferences() throws IOException {
        Path eniPath = Path.of("docs/s1disasm/map16/GHZ.eni");
        Assume.assumeTrue("Skipping: " + eniPath + " not found", Files.exists(eniPath));

        byte[] decompressed;
        try (RandomAccessFile raf = new RandomAccessFile(eniPath.toFile(), "r");
             FileChannel channel = raf.getChannel()) {
            decompressed = EnigmaReader.decompress(channel, 0);
        }

        int chunkCount = decompressed.length / 8; // 4 words (8 bytes) per chunk
        System.out.println("GHZ chunks decompressed: " + decompressed.length + " bytes = " + chunkCount + " chunks");
        assertTrue("Decompressed chunk data should not be empty", decompressed.length > 0);
        assertEquals("Decompressed size should be a multiple of 8 (4 words per chunk)",
                0, decompressed.length % 8);

        int waterfallChunks = 0;
        for (int c = 0; c < chunkCount; c++) {
            boolean hasWaterfallRef = false;
            StringBuilder sb = new StringBuilder();
            sb.append("Chunk ").append(c).append(" (0x").append(Integer.toHexString(c)).append("): ");

            for (int p = 0; p < 4; p++) {
                int offset = c * 8 + p * 2;
                int word = ((decompressed[offset] & 0xFF) << 8) | (decompressed[offset + 1] & 0xFF);
                int patternIndex = word & 0x7FF;
                boolean hFlip = (word & 0x800) != 0;
                boolean vFlip = (word & 0x1000) != 0;
                int palette = (word >> 13) & 0x3;
                boolean priority = (word & 0x8000) != 0;

                sb.append(String.format("[pat=0x%03X pal=%d h=%b v=%b pri=%b] ",
                        patternIndex, palette, hFlip, vFlip, priority));

                if (patternIndex >= WATERFALL_TILE_START && patternIndex <= WATERFALL_TILE_END) {
                    hasWaterfallRef = true;
                }
            }

            if (hasWaterfallRef) {
                System.out.println(">>> WATERFALL: " + sb);
                waterfallChunks++;
            }
        }

        System.out.println("\nTotal chunks with waterfall refs (0x378-0x37F): " + waterfallChunks);
        assertTrue("GHZ should contain at least one chunk referencing waterfall tiles",
                waterfallChunks > 0);

        // Now decompress the 256x256 blocks and find which blocks use waterfall chunks
        System.out.println("\n===== 256x256 BLOCK ANALYSIS =====");
        Path kosPath = Path.of("docs/s1disasm/map256/GHZ.kos");
        Assume.assumeTrue("Skipping: " + kosPath + " not found", Files.exists(kosPath));

        byte[] blockData;
        try (RandomAccessFile raf = new RandomAccessFile(kosPath.toFile(), "r");
             FileChannel channel = raf.getChannel()) {
            blockData = KosinskiReader.decompress(channel, false);
        }

        // Each 256x256 block = 16x16 grid of chunk references = 256 words = 512 bytes
        int blockSize = 512;
        int blockCount = blockData.length / blockSize;
        System.out.println("GHZ blocks decompressed: " + blockData.length + " bytes = " + blockCount + " blocks");
        assertTrue("Decompressed block data should not be empty", blockData.length > 0);
        assertEquals("Decompressed block size should be a multiple of 512",
                0, blockData.length % blockSize);

        // Check PLC tile coverage: decompress GHZ art files to get tile counts
        System.out.println("\n===== PLC TILE COVERAGE =====");
        Path ghz1Path = Path.of("docs/s1disasm/artnem/8x8 - GHZ1.nem");
        Path ghz2Path = Path.of("docs/s1disasm/artnem/8x8 - GHZ2.nem");
        Path stalkPath = Path.of("docs/s1disasm/artnem/GHZ Flower Stalk.nem");
        Path pplRockPath = Path.of("docs/s1disasm/artnem/GHZ Purple Rock.nem");

        int[][] plcEntries = {{0, 0}, {0x1CD, 0}, {0x358, 0}, {0x3D0, 0}};
        Path[] plcPaths = {ghz1Path, ghz2Path, stalkPath, pplRockPath};
        String[] plcNames = {"GHZ1", "GHZ2", "Stalk", "PplRock"};

        for (int i = 0; i < plcPaths.length; i++) {
            if (!Files.exists(plcPaths[i])) {
                System.out.println("SKIP: " + plcPaths[i]);
                continue;
            }
            try (RandomAccessFile raf = new RandomAccessFile(plcPaths[i].toFile(), "r");
                 FileChannel channel = raf.getChannel()) {
                byte[] artData = NemesisReader.decompress(channel);
                int tileCount = artData.length / 32; // 32 bytes per 8x8 tile
                int startTile = plcEntries[i][0];
                int endTile = startTile + tileCount - 1;
                System.out.printf("  %s: tiles 0x%03X - 0x%03X (%d tiles, %d bytes)%n",
                        plcNames[i], startTile, endTile, tileCount, artData.length);

                // Check if waterfall body tiles are in range
                if (startTile <= 0x248 && endTile >= 0x26E) {
                    System.out.println("    -> COVERS waterfall body tiles 0x248-0x26E");
                }
            } catch (Exception e) {
                System.out.println("  " + plcNames[i] + ": FAILED - " + e.getMessage());
            }
        }

        // Dump waterfall body chunks - what patterns do they reference?
        System.out.println("\n--- Waterfall body chunk details ---");
        int[] bodyChunks = {0x07C, 0x07D, 0x0F1, 0x0F2};
        for (int c : bodyChunks) {
            if (c >= chunkCount) continue;
            StringBuilder sb = new StringBuilder();
            sb.append("Chunk 0x").append(Integer.toHexString(c)).append(": ");
            for (int p = 0; p < 4; p++) {
                int offset = c * 8 + p * 2;
                int word = ((decompressed[offset] & 0xFF) << 8) | (decompressed[offset + 1] & 0xFF);
                int patternIndex = word & 0x7FF;
                boolean hFlip = (word & 0x800) != 0;
                boolean vFlip = (word & 0x1000) != 0;
                int palette = (word >> 13) & 0x3;
                boolean priority = (word & 0x8000) != 0;
                sb.append(String.format("[pat=0x%03X pal=%d h=%b v=%b pri=%b] ",
                        patternIndex, palette, hFlip, vFlip, priority));
            }
            System.out.println(sb);
        }

        for (int b = 0; b < blockCount; b++) {
            // Scan this block for waterfall chunk references
            Set<Integer> waterfallCols = new HashSet<>();
            boolean hasWaterfall = false;

            for (int cy = 0; cy < 16; cy++) {
                for (int cx = 0; cx < 16; cx++) {
                    int wordOffset = b * blockSize + (cy * 16 + cx) * 2;
                    int word = ((blockData[wordOffset] & 0xFF) << 8) | (blockData[wordOffset + 1] & 0xFF);

                    // S1 block format: 0SSY X0II IIII IIII
                    int chunkIdx = word & 0x03FF;
                    boolean xFlip = (word & 0x0800) != 0;
                    boolean yFlip = (word & 0x1000) != 0;
                    int solidity = (word >> 13) & 0x3;

                    if (WATERFALL_CHUNK_INDICES.contains(chunkIdx)) {
                        hasWaterfall = true;
                        waterfallCols.add(cx);
                    }
                }
            }

            if (hasWaterfall) {
                System.out.println("\n--- Block " + (b + 1) + " (1-based, stored at index " + (b + 1) + ") contains waterfall chunks ---");
                // Print the full block grid showing which chunks are waterfall
                for (int cy = 0; cy < 16; cy++) {
                    StringBuilder row = new StringBuilder();
                    row.append(String.format("  Row %2d: ", cy));
                    for (int cx = 0; cx < 16; cx++) {
                        int wordOffset = b * blockSize + (cy * 16 + cx) * 2;
                        int word = ((blockData[wordOffset] & 0xFF) << 8) | (blockData[wordOffset + 1] & 0xFF);
                        int chunkIdx = word & 0x03FF;
                        boolean xFlip = (word & 0x0800) != 0;
                        boolean yFlip = (word & 0x1000) != 0;

                        if (WATERFALL_CHUNK_INDICES.contains(chunkIdx)) {
                            row.append(String.format("*%03X", chunkIdx));
                        } else {
                            row.append(String.format(" %03X", chunkIdx));
                        }
                        if (xFlip) row.append("h");
                        else row.append(" ");
                        if (yFlip) row.append("v");
                        else row.append(" ");
                        row.append(" ");
                    }
                    System.out.println(row);
                }
            }
        }
    }
}
