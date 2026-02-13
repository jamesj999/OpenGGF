package uk.co.jamesj999.sonic.tools.introspector;

/**
 * Shared big-endian read utilities for introspection chains.
 * All methods include bounds checking to safely handle truncated or malformed ROMs.
 */
final class RomReadUtil {

    private RomReadUtil() {
    }

    /**
     * Reads a big-endian 32-bit value from the ROM.
     *
     * @param rom    the ROM byte array
     * @param offset the byte offset to read from
     * @return the 32-bit value, or -1 if out of bounds
     */
    static int readBigEndian32(byte[] rom, int offset) {
        if (offset < 0 || offset + 4 > rom.length) {
            return -1;
        }
        return ((rom[offset] & 0xFF) << 24)
                | ((rom[offset + 1] & 0xFF) << 16)
                | ((rom[offset + 2] & 0xFF) << 8)
                | (rom[offset + 3] & 0xFF);
    }

    /**
     * Reads a big-endian 16-bit value from the ROM.
     *
     * @param rom    the ROM byte array
     * @param offset the byte offset to read from
     * @return the 16-bit value, or -1 if out of bounds
     */
    static int readBigEndian16(byte[] rom, int offset) {
        if (offset < 0 || offset + 2 > rom.length) {
            return -1;
        }
        return ((rom[offset] & 0xFF) << 8) | (rom[offset + 1] & 0xFF);
    }

    /**
     * Computes a safe search end offset, clamping to avoid negative values
     * when the ROM is smaller than the expected table size.
     *
     * @param maxEnd    the maximum search end offset
     * @param romLength the ROM length in bytes
     * @param tableSize the minimum bytes needed at each search position
     * @return a non-negative search end, or -1 if the ROM is too small
     */
    static int safeSearchEnd(int maxEnd, int romLength, int tableSize) {
        if (romLength < tableSize) {
            return -1;
        }
        return Math.min(maxEnd, romLength - tableSize);
    }
}
