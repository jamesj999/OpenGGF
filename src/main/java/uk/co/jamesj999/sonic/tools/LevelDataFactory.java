package uk.co.jamesj999.sonic.tools;

import uk.co.jamesj999.sonic.level.ChunkDesc;
import uk.co.jamesj999.sonic.level.LevelConstants;

public class LevelDataFactory {
    public static ChunkDesc[] chunksFromSegaByteArray(byte[] blockBuffer) {
        return chunksFromSegaByteArray(blockBuffer, LevelConstants.CHUNKS_PER_BLOCK);
    }

    public static ChunkDesc[] chunksFromSegaByteArray(byte[] blockBuffer, int chunksPerBlock) {
        ChunkDesc[] chunkDescs = new ChunkDesc[chunksPerBlock];

        if (blockBuffer.length != chunksPerBlock * LevelConstants.BYTES_PER_CHUNK) {
            throw new IllegalArgumentException("Buffer size " + blockBuffer.length
                    + " does not match expected " + (chunksPerBlock * LevelConstants.BYTES_PER_CHUNK)
                    + " bytes for " + chunksPerBlock + " chunks");
        }
        for (int i = 0; i < chunksPerBlock; i++) {
            int index = ((blockBuffer[i * 2] & 0xFF) << 8) | (blockBuffer[i * 2 + 1] & 0xFF); // Big-endian
            chunkDescs[i] = new ChunkDesc(index);
        }

        return chunkDescs;
    }
}
