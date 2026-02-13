package uk.co.jamesj999.sonic.game.profile.scanner;

import java.util.function.Predicate;

/**
 * Defines a byte pattern to search for in a ROM, along with how to read
 * and validate the value found at the match location.
 *
 * @param category       address category (e.g. "level", "audio")
 * @param name           address name within the category (e.g. "LEVEL_SIZES_ADDR")
 * @param signature      byte sequence to search for in the ROM
 * @param pointerOffset  offset from the match start to the address value to read
 * @param readMode       how to interpret the bytes at the pointer offset
 * @param validator      optional validation predicate for the read value (may be null)
 */
public record ScanPattern(
        String category,
        String name,
        byte[] signature,
        int pointerOffset,
        ReadMode readMode,
        Predicate<Integer> validator
) {

    /**
     * How to read the value at the match location.
     */
    public enum ReadMode {
        /** Single byte (unsigned). */
        BYTE,
        /** Big-endian 16-bit word. */
        BIG_ENDIAN_16,
        /** Big-endian 32-bit longword. */
        BIG_ENDIAN_32
    }

    /**
     * Returns the composite key for this pattern: "category.name".
     *
     * @return the composite key
     */
    public String key() {
        return category + "." + name;
    }
}
