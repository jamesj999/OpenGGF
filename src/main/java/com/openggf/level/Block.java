package com.openggf.level;

import com.openggf.tools.LevelDataFactory;

import java.util.Arrays;

/**
 * Represents a block of chunks in a level.
 * Sonic 2 uses 128x128 blocks (8x8 grid of 16x16 chunks).
 * Sonic 1 uses 256x256 blocks (16x16 grid of 16x16 chunks).
 */
public class Block {

    private final int gridSide;
    private ChunkDesc[] chunkDescs;

    // Default constructor (8x8 grid for Sonic 2 backward compatibility)
    public Block() {
        this(8);
    }

    public Block(int gridSide) {
        this.gridSide = gridSide;
        this.chunkDescs = new ChunkDesc[gridSide * gridSide];
        // Initialize array with references to empty ChunkDesc instance (to save on pointlessly making objects)
        Arrays.setAll(this.chunkDescs, i -> ChunkDesc.EMPTY);
    }

    // Parses a block of data from Sega's format (big-endian 16-bit values)
    public void fromSegaFormat(byte[] blockBuffer) {
        this.chunkDescs = LevelDataFactory.chunksFromSegaByteArray(blockBuffer);
    }

    public void fromSegaFormat(byte[] blockBuffer, int chunksPerBlock) {
        this.chunkDescs = LevelDataFactory.chunksFromSegaByteArray(blockBuffer, chunksPerBlock);
    }

    // Retrieves a chunk descriptor based on x and y coordinates
    public ChunkDesc getChunkDesc(int x, int y) {
        if (x >= gridSide || y >= gridSide) {
            throw new IllegalArgumentException("Invalid chunk index: (" + x + ", " + y + ") for gridSide " + gridSide);
        }

        return chunkDescs[y * gridSide + x];
    }
}
