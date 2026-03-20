package com.openggf.util;

import com.openggf.data.Rom;
import com.openggf.level.Pattern;
import com.openggf.tools.KosinskiReader;
import com.openggf.tools.NemesisReader;

import java.io.IOException;
import java.util.Arrays;

/**
 * Converts raw tile bytes into {@link Pattern} arrays.
 *
 * <p>Centralises the 7-line loop that was previously copy-pasted in every
 * game-specific art loader (S1, S2, S3K).
 *
 * <p>Convenience methods handle ROM channel setup and decompression for the
 * three compression formats used across the Sonic series: Nemesis, Kosinski,
 * and Kosinski Moduled.
 */
public final class PatternDecompressor {

    private PatternDecompressor() {}

    /**
     * Converts raw tile bytes into an array of {@link Pattern} objects.
     *
     * @param data raw tile data (32 bytes per pattern)
     * @return pattern array, or empty array if data is null/empty
     */
    public static Pattern[] fromBytes(byte[] data) {
        if (data == null || data.length == 0) {
            return new Pattern[0];
        }
        int count = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        return buildPatterns(data, count);
    }

    /**
     * Converts raw tile bytes into an array of {@link Pattern} objects,
     * capped at {@code maxCount} patterns.
     *
     * @param data     raw tile data (32 bytes per pattern)
     * @param maxCount maximum number of patterns to produce
     * @return pattern array, or empty array if data is null/empty
     */
    public static Pattern[] fromBytes(byte[] data, int maxCount) {
        if (data == null || data.length == 0) {
            return new Pattern[0];
        }
        int available = data.length / Pattern.PATTERN_SIZE_IN_ROM;
        int count = Math.min(available, maxCount);
        return buildPatterns(data, count);
    }

    /**
     * Decompresses Nemesis-compressed art from ROM and converts to patterns.
     *
     * @param rom     the ROM to read from
     * @param address ROM address of the Nemesis-compressed data
     * @return pattern array
     * @throws IOException on decompression failure
     */
    public static Pattern[] nemesis(Rom rom, int address) throws IOException {
        var channel = rom.getFileChannel();
        channel.position(address);
        byte[] data = NemesisReader.decompress(channel);
        return fromBytes(data);
    }

    /**
     * Decompresses Kosinski-compressed art from ROM and converts to patterns.
     *
     * @param rom     the ROM to read from
     * @param address ROM address of the Kosinski-compressed data
     * @return pattern array
     * @throws IOException on decompression failure
     */
    public static Pattern[] kosinski(Rom rom, int address) throws IOException {
        var channel = rom.getFileChannel();
        channel.position(address);
        byte[] data = KosinskiReader.decompress(channel);
        return fromBytes(data);
    }

    /**
     * Decompresses Kosinski Moduled art from ROM and converts to patterns.
     *
     * @param rom     the ROM to read from
     * @param address ROM address of the Kosinski Moduled data
     * @return pattern array
     * @throws IOException on decompression failure
     */
    public static Pattern[] kosinskiModuled(Rom rom, int address) throws IOException {
        var channel = rom.getFileChannel();
        channel.position(address);
        byte[] data = KosinskiReader.decompressModuled(channel);
        return fromBytes(data);
    }

    private static Pattern[] buildPatterns(byte[] data, int count) {
        Pattern[] patterns = new Pattern[count];
        for (int i = 0; i < count; i++) {
            patterns[i] = new Pattern();
            byte[] tile = Arrays.copyOfRange(
                    data,
                    i * Pattern.PATTERN_SIZE_IN_ROM,
                    (i + 1) * Pattern.PATTERN_SIZE_IN_ROM);
            patterns[i].fromSegaFormat(tile);
        }
        return patterns;
    }
}
