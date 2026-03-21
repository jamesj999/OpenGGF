package com.openggf.game.sonic3k;

import com.openggf.data.RomByteReader;
import com.openggf.game.common.CommonSpriteDataLoader;
import com.openggf.level.Pattern;
import com.openggf.level.render.SpriteDplcFrame;
import com.openggf.level.render.SpriteMappingFrame;
import com.openggf.level.render.SpriteMappingPiece;
import com.openggf.level.render.TileLoadRequest;
import com.openggf.sprites.animation.SpriteAnimationScript;
import com.openggf.sprites.animation.SpriteAnimationSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reusable static utilities for loading S3K sprite data from ROM:
 * art tiles, mapping frames, DPLC frames, and animation scripts.
 *
 * <p>S3K uses 6-byte mapping pieces (no 2P tile word) and standard
 * DPLC/animation formats shared across player sprites, shields,
 * and other DPLC-driven objects.
 */
public final class S3kSpriteDataLoader {

    private S3kSpriteDataLoader() {}

    /**
     * Loads uncompressed art tiles from ROM.
     *
     * @param reader ROM byte reader
     * @param artAddr ROM address of art data
     * @param artSize size in bytes (must be multiple of 32)
     * @return array of Pattern tiles
     */
    public static Pattern[] loadArtTiles(RomByteReader reader, int artAddr, int artSize) throws IOException {
        return CommonSpriteDataLoader.loadArtTiles(reader, artAddr, artSize);
    }

    /**
     * Loads S3K mapping frames (6-byte pieces, no 2P tile word).
     * The first word at mappingAddr is the offset table size in bytes.
     *
     * @param reader ROM byte reader
     * @param mappingAddr ROM address of mapping table
     * @return list of mapping frames
     */
    public static List<SpriteMappingFrame> loadMappingFrames(RomByteReader reader, int mappingAddr) {
        int offsetTableSize = reader.readU16BE(mappingAddr);
        int frameCount = offsetTableSize / 2;
        List<SpriteMappingFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameAddr = mappingAddr + reader.readU16BE(mappingAddr + i * 2);
            int pieceCount = reader.readU16BE(frameAddr);
            frameAddr += 2;
            List<SpriteMappingPiece> pieces = new ArrayList<>(pieceCount);
            for (int p = 0; p < pieceCount; p++) {
                int yOffset = (byte) reader.readU8(frameAddr);
                frameAddr += 1;
                int size = reader.readU8(frameAddr);
                frameAddr += 1;
                int tileWord = reader.readU16BE(frameAddr);
                frameAddr += 2;
                // S3K: NO 2P tile word (S2 has +2 skip here)
                int xOffset = (short) reader.readU16BE(frameAddr);
                frameAddr += 2;

                int widthTiles = ((size >> 2) & 0x3) + 1;
                int heightTiles = (size & 0x3) + 1;

                int tileIndex = tileWord & 0x7FF;
                boolean hFlip = (tileWord & 0x800) != 0;
                boolean vFlip = (tileWord & 0x1000) != 0;
                int paletteIndex = (tileWord >> 13) & 0x3;
                boolean priority = (tileWord & 0x8000) != 0;

                pieces.add(new SpriteMappingPiece(
                        xOffset, yOffset, widthTiles, heightTiles, tileIndex, hFlip, vFlip, paletteIndex, priority));
            }
            frames.add(new SpriteMappingFrame(pieces));
        }
        return frames;
    }

    /**
     * Loads DPLC frames. The first word at dplcAddr is the offset table size.
     *
     * @param reader ROM byte reader
     * @param dplcAddr ROM address of DPLC table
     * @return list of DPLC frames
     */
    public static List<SpriteDplcFrame> loadDplcFrames(RomByteReader reader, int dplcAddr) {
        int offsetTableSize = reader.readU16BE(dplcAddr);
        int frameCount = offsetTableSize / 2;
        return loadDplcFrames(reader, dplcAddr, frameCount);
    }

    /**
     * Loads DPLC frames with an explicit frame count instead of reading it from the first word.
     *
     * @param reader ROM byte reader
     * @param dplcAddr ROM address of DPLC table
     * @param frameCount number of frames to read
     * @return list of DPLC frames
     */
    public static List<SpriteDplcFrame> loadDplcFrames(RomByteReader reader, int dplcAddr, int frameCount) {
        List<SpriteDplcFrame> frames = new ArrayList<>(frameCount);
        for (int i = 0; i < frameCount; i++) {
            int frameAddr = dplcAddr + reader.readU16BE(dplcAddr + i * 2);
            int requestCount = reader.readU16BE(frameAddr);
            frameAddr += 2;
            List<TileLoadRequest> requests = new ArrayList<>(requestCount);
            for (int r = 0; r < requestCount; r++) {
                int entry = reader.readU16BE(frameAddr);
                frameAddr += 2;
                int count = ((entry >> 12) & 0xF) + 1;
                int startTile = entry & 0x0FFF;
                requests.add(new TileLoadRequest(startTile, count));
            }
            frames.add(new SpriteDplcFrame(requests));
        }
        return frames;
    }

    /**
     * Computes the DPLC bank size (max tiles needed in VRAM at once).
     *
     * @param dplcFrames DPLC frames
     * @param mappingFrames mapping frames
     * @return bank size in tiles
     */
    public static int resolveBankSize(List<SpriteDplcFrame> dplcFrames, List<SpriteMappingFrame> mappingFrames) {
        return CommonSpriteDataLoader.resolveBankSize(dplcFrames, mappingFrames);
    }

    /**
     * Parses a single animation script from ROM.
     *
     * @param reader ROM byte reader
     * @param scriptAddr ROM address of the script data
     * @return parsed animation script
     */
    public static SpriteAnimationScript parseAnimationScript(RomByteReader reader, int scriptAddr) {
        return CommonSpriteDataLoader.parseAnimationScript(reader, scriptAddr);
    }

    /**
     * Loads a set of animation scripts from a pointer-offset table.
     *
     * @param reader ROM byte reader
     * @param baseAddr ROM address of the offset table
     * @param count number of animation entries
     * @return animation set
     */
    public static SpriteAnimationSet loadAnimationSet(RomByteReader reader, int baseAddr, int count) {
        return CommonSpriteDataLoader.loadAnimationSet(reader, baseAddr, count);
    }
}
