package com.openggf.level.resources;

import com.openggf.data.Rom;
import com.openggf.tools.KosinskiReader;
import com.openggf.tools.NemesisReader;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Loads level resources from ROM with support for overlay composition.
 *
 * <p>Overlay loading works by:
 * <ol>
 *   <li>Allocating a destination buffer large enough for all operations</li>
 *   <li>Decompressing/copying each LoadOp's data into the buffer at its destOffset</li>
 *   <li>Operations are applied in order, so overlays overwrite base data</li>
 * </ol>
 *
 * <p>This class does NOT cache results. Callers should cache the returned
 * byte arrays if reuse is needed. This ensures that loading HTZ doesn't
 * accidentally mutate cached EHZ data.
 *
 * <p>Example:
 * <pre>{@code
 * ResourceLoader loader = new ResourceLoader(rom);
 *
 * // Load HTZ patterns with overlay
 * List<LoadOp> patternOps = htzPlan.getPatternOps();
 * byte[] patterns = loader.loadWithOverlays(patternOps, 0x10000);
 * }</pre>
 */
public class ResourceLoader {

    private static final Logger LOG = Logger.getLogger(ResourceLoader.class.getName());
    private static final boolean KOS_DEBUG_LOG = false;

    private final Rom rom;

    public ResourceLoader(Rom rom) {
        this.rom = rom;
    }

    /**
     * Loads and composes data from multiple LoadOps into a single buffer.
     *
     * <p>The final buffer size is the maximum of all operations (base + overlays),
     * ensuring all data is included. For proper alignment (e.g., for 128-byte blocks),
     * callers should either use aligned base data or handle alignment themselves.
     *
     * @param ops            List of load operations to apply in order.
     *                       The first op should be the base (destOffset=0).
     * @param initialBufferSize Initial buffer size hint.
     * @return The composed byte array with all operations applied
     * @throws IOException if decompression or ROM reading fails
     */
    public byte[] loadWithOverlays(List<LoadOp> ops, int initialBufferSize) throws IOException {
        if (ops == null || ops.isEmpty()) {
            throw new IllegalArgumentException("At least one LoadOp is required");
        }

        // Start with the initial buffer size
        byte[] buffer = new byte[initialBufferSize];
        int usedLength = 0;

        for (LoadOp op : ops) {
            byte[] decompressed = decompress(op);
            int destOffset = op.destOffsetBytes();
            int requiredSize = destOffset + decompressed.length;

            // Expand buffer if needed to accommodate this operation
            if (requiredSize > buffer.length) {
                buffer = Arrays.copyOf(buffer, requiredSize);
            }

            // Copy decompressed data into buffer at destOffset
            System.arraycopy(decompressed, 0, buffer, destOffset, decompressed.length);

            // Track the maximum extent of data
            usedLength = Math.max(usedLength, requiredSize);

            if (op.destOffsetBytes() > 0) {
                LOG.fine(String.format("Applied overlay: ROM 0x%06X -> offset 0x%04X (%d bytes)",
                        op.romAddr(), op.destOffsetBytes(), decompressed.length));
            } else {
                LOG.fine(String.format("Loaded base: ROM 0x%06X (%d bytes)",
                        op.romAddr(), decompressed.length));
            }
        }

        // Trim buffer to actual used size
        if (usedLength < buffer.length) {
            buffer = Arrays.copyOf(buffer, usedLength);
        }

        return buffer;
    }

    /**
     * Loads and composes data from multiple LoadOps, with alignment enforcement.
     *
     * <p>Similar to {@link #loadWithOverlays(List, int)}, but the final buffer size
     * is rounded UP to the nearest multiple of the specified alignment.
     *
     * @param ops            List of load operations to apply in order.
     * @param initialBufferSize Initial buffer size hint.
     * @param alignment      Required alignment in bytes (e.g., 128 for blocks).
     * @return The composed byte array, sized to a multiple of alignment
     * @throws IOException if decompression or ROM reading fails
     */
    public byte[] loadWithOverlaysAligned(List<LoadOp> ops, int initialBufferSize, int alignment) throws IOException {
        byte[] buffer = loadWithOverlays(ops, initialBufferSize);

        // Round up to alignment boundary
        int remainder = buffer.length % alignment;
        if (remainder != 0) {
            int alignedSize = buffer.length + (alignment - remainder);
            buffer = Arrays.copyOf(buffer, alignedSize);
        }

        return buffer;
    }

    /**
     * Loads a single LoadOp without overlay composition.
     * Equivalent to loadWithOverlays with a single-element list.
     */
    public byte[] loadSingle(LoadOp op) throws IOException {
        return decompress(op);
    }

    /**
     * Decompresses data from ROM based on the compression type.
     */
    private byte[] decompress(LoadOp op) throws IOException {
        return switch (op.compressionType()) {
            case KOSINSKI -> decompressKosinski(op.romAddr());
            case KOSINSKI_MODULED -> decompressKosinskiModuled(op.romAddr());
            case NEMESIS -> decompressNemesis(op.romAddr());
            case UNCOMPRESSED -> throw new UnsupportedOperationException(
                    "Uncompressed loading requires a size parameter. Use loadUncompressed() instead.");
        };
    }

    /**
     * Decompresses Kosinski-compressed data from the specified ROM address.
     */
    private byte[] decompressKosinski(int romAddr) throws IOException {
        FileChannel channel = rom.getFileChannel();
        // Rom exposes a shared FileChannel; lock around seek+decode so concurrent
        // readers cannot move the channel position mid-stream.
        synchronized (rom) {
            channel.position(romAddr);
            return KosinskiReader.decompress(channel, KOS_DEBUG_LOG);
        }
    }

    /**
     * Decompresses Kosinski Moduled data from the specified ROM address.
     * KosM data has a 2-byte BE header giving the total uncompressed size,
     * followed by standard Kosinski modules at 16-byte aligned boundaries.
     */
    private byte[] decompressKosinskiModuled(int romAddr) throws IOException {
        // Read KosM 2-byte BE header to get total decompressed size
        byte[] header = rom.readBytes(romAddr, 2);
        if (header.length < 2) {
            throw new IOException(String.format(
                    "Insufficient ROM data for KosM header at 0x%X: got %d bytes", romAddr, header.length));
        }
        int fullSize = ((header[0] & 0xFF) << 8) | (header[1] & 0xFF);

        // Compressed data is smaller than decompressed, so fullSize is a safe upper bound
        // for input. Add extra for module alignment padding. Cap at 256KB to prevent issues.
        int inputSize = Math.min(Math.max(fullSize + 256, 0x10000), 0x40000);
        byte[] romData = rom.readBytes(romAddr, inputSize);
        if (romData.length < inputSize) {
            throw new IOException("Short read for KosM data at 0x" + Integer.toHexString(romAddr));
        }
        return KosinskiReader.decompressModuled(romData, 0);
    }

    /**
     * Decompresses Nemesis-compressed data from the specified ROM address.
     */
    private byte[] decompressNemesis(int romAddr) throws IOException {
        FileChannel channel = rom.getFileChannel();
        // Rom exposes a shared FileChannel; lock around seek+decode so concurrent
        // readers cannot move the channel position mid-stream.
        synchronized (rom) {
            channel.position(romAddr);
            return NemesisReader.decompress(channel);
        }
    }

    /**
     * Loads uncompressed data from ROM.
     *
     * @param romAddr ROM address to read from
     * @param size    Number of bytes to read
     * @return The raw bytes from ROM
     * @throws IOException if reading fails
     */
    public byte[] loadUncompressed(int romAddr, int size) throws IOException {
        return rom.readBytes(romAddr, size);
    }
}
