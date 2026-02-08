package uk.co.jamesj999.sonic.level.resources;

/**
 * Compression types used in Sonic 2 ROM data.
 */
public enum CompressionType {
    /**
     * Kosinski compression - used for level tiles, blocks, chunks, layouts.
     */
    KOSINSKI,

    /**
     * Kosinski Moduled compression - used for S3K 8x8 pattern tile art.
     * Has a 2-byte BE header (total uncompressed size), followed by
     * standard Kosinski modules at 16-byte aligned boundaries.
     */
    KOSINSKI_MODULED,

    /**
     * Nemesis compression - used for sprite art.
     */
    NEMESIS,

    /**
     * Uncompressed raw data.
     */
    UNCOMPRESSED
}
